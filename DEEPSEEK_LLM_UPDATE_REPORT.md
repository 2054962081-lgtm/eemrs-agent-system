# DeepSeek LLM 改造修改说明

## 一、修改目标

本次改造将 `agent-server` 中原先直接调用本地 Ollama 的预问诊与病历草稿生成能力，调整为通过统一 LLM Client 抽象层调用模型服务。

当前默认 provider 为：

```text
deepseek
```

当前默认模型为：

```text
deepseek-v4-flash
```

同时保留 Ollama 本地模型能力，后续可通过环境变量切回本地模型。

## 二、新增文件

### 1. 统一 LLM 抽象层

新增目录：

```text
agent-server/src/main/java/com/liu/eemrsagent/llm/
```

新增文件：

```text
LlmClient.java
LlmMessage.java
LlmChatRequest.java
LlmChatResponse.java
LlmProviderType.java
LlmClientFactory.java
LlmException.java
LlmProperties.java
DeepSeekClient.java
OllamaClient.java
```

作用说明：

| 文件 | 作用 |
|---|---|
| `LlmClient.java` | 统一模型调用接口 |
| `LlmMessage.java` | 统一消息结构 |
| `LlmChatRequest.java` | 统一聊天请求结构 |
| `LlmChatResponse.java` | 统一聊天响应结构 |
| `LlmProviderType.java` | provider 枚举，目前支持 `deepseek`、`ollama` |
| `LlmClientFactory.java` | 根据业务用途选择 provider，并处理可选 fallback |
| `LlmException.java` | 统一 LLM 调用异常 |
| `LlmProperties.java` | 统一读取 `llm.*` 配置 |
| `DeepSeekClient.java` | DeepSeek OpenAI 兼容 Chat Completions 调用实现 |
| `OllamaClient.java` | Ollama `/api/chat` 调用实现 |

### 2. Agent Server README

新增文件：

```text
agent-server/README.md
```

内容包括：

- DeepSeek 默认 provider 说明
- `DEEPSEEK_API_KEY` 配置方式
- PowerShell / CMD 环境变量示例
- 启动方式
- 健康检查接口
- 如何切回 Ollama
- fallback 配置说明
- API key 安全提醒
- 云端模型合规提醒

## 三、修改文件

### 1. 预问诊服务

修改文件：

```text
agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationService.java
```

修改内容：

- 移除业务服务中直接构造 Ollama 请求的逻辑。
- 改为通过 `LlmClientFactory.chatForPurpose(...)` 调用统一 LLM Client。
- 预问诊用途使用：

```text
pre_consultation
```

- 默认路由到：

```text
deepseek
```

- 保留 quick / deep 模式逻辑。
- 保留医学安全 prompt。
- 返回结果中继续保留原有字段，并补充模型来源信息。

### 2. 预问诊响应 DTO

修改文件：

```text
agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationResponse.java
```

修改内容：

- 新增 `provider` 字段。
- 继续保留 `model` 字段。
- 响应可展示：

```json
{
  "model": "deepseek-v4-flash",
  "provider": "deepseek"
}
```

### 3. 病历草稿生成服务

修改文件：

```text
agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftService.java
```

修改内容：

- 移除直接调用 Ollama 的逻辑。
- 改为通过统一 LLM Client 调用模型。
- 病历草稿用途使用：

```text
medical_record_draft
```

- 默认路由到：

```text
deepseek
```

- 病历草稿生成启用 `jsonMode=true`。
- DeepSeek 调用时会发送：

```json
{
  "response_format": {
    "type": "json_object"
  }
}
```

- Ollama 调用时会发送：

```json
{
  "format": "json"
}
```

### 4. Agent 健康检查接口

修改文件：

```text
agent-server/src/main/java/com/liu/eemrsagent/agent/AgentController.java
```

修改内容：

- `GET /api/agent/health` 返回中新增 LLM 配置信息。
- 不返回 API key 内容。

示例：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "status": "UP",
    "llm": {
      "defaultProvider": "deepseek",
      "preConsultationProvider": "deepseek",
      "medicalRecordDraftProvider": "deepseek",
      "deepseekConfigured": true,
      "ollamaEnabled": true
    }
  }
}
```

### 5. 应用配置

修改文件：

```text
agent-server/src/main/resources/application.yml
```

修改内容：

- 删除旧的 `agent.ollama` 配置。
- 新增统一 `llm.*` 配置。

核心配置：

```yaml
llm:
  default-provider: ${LLM_DEFAULT_PROVIDER:deepseek}

  deepseek:
    enabled: ${DEEPSEEK_ENABLED:true}
    base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
    api-key: ${DEEPSEEK_API_KEY:}
    model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
    timeout-seconds: ${DEEPSEEK_TIMEOUT_SECONDS:90}
    max-tokens: ${DEEPSEEK_MAX_TOKENS:4096}
    temperature: ${DEEPSEEK_TEMPERATURE:0.2}
    top-p: ${DEEPSEEK_TOP_P:0.8}

  ollama:
    enabled: ${OLLAMA_ENABLED:true}
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:qwen3-vl:8b}
    timeout-seconds: ${OLLAMA_TIMEOUT_SECONDS:120}

  routing:
    pre-consultation-provider: ${PRE_CONSULTATION_LLM_PROVIDER:deepseek}
    medical-record-draft-provider: ${MEDICAL_RECORD_DRAFT_LLM_PROVIDER:deepseek}
    privacy-provider: ${PRIVACY_LLM_PROVIDER:ollama}

  fallback:
    enabled: ${LLM_FALLBACK_ENABLED:false}
    fallback-provider: ${LLM_FALLBACK_PROVIDER:ollama}
```

### 6. 旧配置类删除

删除文件：

```text
agent-server/src/main/java/com/liu/eemrsagent/config/OllamaProperties.java
```

原因：

- 原配置类只服务于旧的 `agent.ollama` 直接调用方式。
- 新实现统一使用 `LlmProperties` 管理 DeepSeek、Ollama、routing、fallback 配置。

## 四、DeepSeek 调用方式

DeepSeek 使用 OpenAI 兼容 Chat Completions 接口：

```text
POST {base-url}/chat/completions
```

默认：

```text
https://api.deepseek.com/chat/completions
```

请求头：

```text
Authorization: Bearer ${DEEPSEEK_API_KEY}
Content-Type: application/json
```

响应解析：

```text
choices[0].message.content
```

如返回 `usage`，会读取：

```text
prompt_tokens
completion_tokens
total_tokens
```

## 五、错误处理

`DeepSeekClient` 中已处理：

| 情况 | 返回信息 |
|---|---|
| API key 为空 | `DeepSeek API key 未配置，请设置环境变量 DEEPSEEK_API_KEY。` |
| 401 | `DeepSeek API key 无效或未授权。` |
| 429 | `DeepSeek 请求频率过高或额度不足，请稍后重试。` |
| 5xx | `DeepSeek 服务暂时不可用，请稍后重试。` |
| timeout | `DeepSeek 响应超时，请稍后重试。` |
| content 为空 | `DeepSeek 返回内容为空。` |

默认不自动 fallback。

如果开启：

```text
LLM_FALLBACK_ENABLED=true
```

则 DeepSeek 调用失败后会尝试：

```text
LLM_FALLBACK_PROVIDER=ollama
```

## 六、环境变量配置

### PowerShell

```powershell
$env:DEEPSEEK_API_KEY="你的key"
$env:LLM_DEFAULT_PROVIDER="deepseek"
$env:PRE_CONSULTATION_LLM_PROVIDER="deepseek"
$env:MEDICAL_RECORD_DRAFT_LLM_PROVIDER="deepseek"
```

### CMD

```bat
set DEEPSEEK_API_KEY=你的key
set LLM_DEFAULT_PROVIDER=deepseek
set PRE_CONSULTATION_LLM_PROVIDER=deepseek
set MEDICAL_RECORD_DRAFT_LLM_PROVIDER=deepseek
```

## 七、切回 Ollama

### PowerShell

```powershell
$env:PRE_CONSULTATION_LLM_PROVIDER="ollama"
$env:MEDICAL_RECORD_DRAFT_LLM_PROVIDER="ollama"
```

### CMD

```bat
set PRE_CONSULTATION_LLM_PROVIDER=ollama
set MEDICAL_RECORD_DRAFT_LLM_PROVIDER=ollama
```

## 八、安全确认

本次修改确认：

- 未将真实 DeepSeek API key 写入 `application.yml`。
- 未将真实 DeepSeek API key 写入 Java 代码。
- 未将真实 DeepSeek API key 写入前端代码。
- 前端不直接调用 DeepSeek。
- 所有 DeepSeek 调用均在 Java 17 `agent-server` 后端完成。
- 健康检查只返回 `deepseekConfigured` 布尔值，不返回 key。
- `.gitignore` 已包含 `.env`、`.env.*`、`application-local.yml`、`application-local.yaml`。

## 九、验证结果

后端编译：

```powershell
cd agent-server
mvn -q -DskipTests compile
```

结果：

```text
通过
```

前端构建：

```powershell
cd frontend-vue
npm.cmd run build
```

结果：

```text
通过
```

说明：

- 首次前端构建因沙箱权限无法写入 `node_modules/.tmp/*.tsbuildinfo` 失败。
- 提权重跑后构建成功。
- Vite 输出了 Node 版本和 chunk size 警告，但不影响构建结果。

## 十、建议提交信息

```text
feat(agent): add DeepSeek-backed unified LLM client
```
