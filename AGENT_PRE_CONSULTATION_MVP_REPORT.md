# 用户预问诊智能体 MVP 接入报告

## 1. Java8 后端智能体预留接口检查

现有 Java8 后端 `eemrs-server-master` 中没有真实的智能体 Controller，也没有实现 `/api/agent/**` 接口。

已发现的智能体相关预留只存在于安全配置中：

- `POST /api/ai/pre-consultations`
- `POST /api/ai/report-interpretations`
- `POST /api/ai/record-drafts`

位置：

- `eemrs-server-master/src/main/java/com/liu/eemrsserver/security/SecurityConfig.java`

这些 `/api/ai/**` 只是权限规则预留，未发现对应 Controller 实现。

## 2. 接口路径冲突情况

本次新增 Java17 智能体服务使用以下接口：

- `GET /api/agent/health`
- `POST /api/agent/pre-consultation`

Java8 后端没有 `/api/agent/**` 路径，因此不存在接口冲突。

本次未改动 Java8 后端功能，避免影响原有 `8080` 服务。

## 3. Java17 智能体服务新增文件

新增独立服务目录：

- `agent-server`

主要新增文件：

- `agent-server/pom.xml`
- `agent-server/src/main/resources/application.yml`
- `agent-server/src/main/java/com/liu/eemrsagent/AgentServerApplication.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/AgentController.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationService.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationRequest.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationResponse.java`
- `agent-server/src/main/java/com/liu/eemrsagent/common/ApiResponse.java`
- `agent-server/src/main/java/com/liu/eemrsagent/common/GlobalExceptionHandler.java`
- `agent-server/src/main/java/com/liu/eemrsagent/config/OllamaProperties.java`
- `agent-server/src/main/java/com/liu/eemrsagent/config/CorsConfig.java`

最终验收时已将预问诊请求 DTO 收敛为只接收 `question` 字段，避免 `message`、`symptoms` 等字段混用。

服务配置：

```yaml
server:
  port: 8081

agent:
  ollama:
    base-url: http://localhost:11434
    model: qwen3-vl:8b
    timeout-seconds: 120
```

## 4. 前端预留页面复用情况

前端已存在患者端 “AI 预问诊” 占位入口，本次已复用并启用：

- 患者侧栏菜单：`frontend-vue/src/layouts/PatientLayout.vue`
- 患者首页卡片：`frontend-vue/src/views/patient/PatientDashboard.vue`

新增页面：

- `frontend-vue/src/views/patient/PreConsultation.vue`

新增路由：

- `/patient/pre-consultation`

路由位置：

- `frontend-vue/src/router/index.ts`

新增前端 API 封装：

- `frontend-vue/src/api/agent.ts`

前端预问诊请求统一发送：

```json
{
  "question": "用户输入的症状或问题"
}
```

与 Java17 后端 `PreConsultationRequest.question` 保持一致。

## 5. 前端代理配置

代理配置文件：

- `frontend-vue/vite.config.ts`

当前代理规则：

```ts
proxy: {
  '/api/agent': {
    target: 'http://localhost:8081',
    changeOrigin: true,
  },
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

`/api/agent` 位于 `/api` 前面，保证智能体请求优先代理到 Java17 服务，其他业务接口继续代理到 Java8 后端。

最终验收确认该代理顺序保持不变。

## 6. Java17 服务启动方式

需要本机 JDK 17。

```powershell
cd D:\各类文件管理\研究生学习\研究\电子医疗系统\代码\agent-server
mvn spring-boot:run
```

启动后服务监听：

- `http://localhost:8081`

Java8 后端继续运行在：

- `http://localhost:8080`

前端 Vite 开发服务继续运行在：

- `http://localhost:5173`

## 7. 本地 Ollama 调用测试方式

健康检查：

```powershell
Invoke-RestMethod http://localhost:8081/api/agent/health
```

预问诊测试：

```powershell
$body = @{
  question = "最近三天咳嗽、喉咙痛，晚上有低烧，需要注意什么？"
} | ConvertTo-Json

Invoke-RestMethod `
  http://localhost:8081/api/agent/pre-consultation `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

本次实际验证结果：

- `GET /api/agent/health` 成功返回 `status=UP`
- `POST /api/agent/pre-consultation` 成功调用 Ollama
- Ollama 使用模型 `qwen3-vl:8b` 返回了中文预问诊回复
- 空 `question` 会被后端拒绝，返回 400，不会继续调用 Ollama

说明：PowerShell 输出中可能出现中文乱码，这是终端编码显示问题，不代表接口链路失败。

## 7.1 前端稳定性修复

前端全局 Axios 配置仍保持：

```ts
timeout: 15000
```

为了避免影响登录、病历等普通业务接口，没有把全局超时时间改长。

仅对智能体预问诊接口单独配置更长超时：

```ts
request.post('/agent/pre-consultation', data, {
  timeout: 120000,
})
```

位置：

- `frontend-vue/src/api/agent.ts`

页面 `frontend-vue/src/views/patient/PreConsultation.vue` 已完成以下稳定性处理：

- 输入为空时不发送请求
- 发送时显示 loading
- 成功后展示 AI 回复
- 请求失败时捕获异常，不再抛出到组件事件 handler 外
- 超时时显示友好提示：“AI 回复生成时间较长，请稍后重试，或确认 Ollama 模型是否已加载完成。”
- `finally` 中关闭 loading
- 失败时不清空用户输入，方便重新发送

## 7.2 后端稳定性修整

Java17 后端最终保持以下接口：

- `GET /api/agent/health`
- `POST /api/agent/pre-consultation`

请求 DTO：

```java
public record PreConsultationRequest(String question) {
}
```

Ollama 调用异常时，后端会返回稳定错误响应，不会导致服务进程崩溃。

返回结构仍保持：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "reply": "...",
    "model": "qwen3-vl:8b"
  }
}
```

## 8. 失败时优先检查项

如果链路失败，优先检查：

1. Ollama 是否已启动。
2. `http://localhost:11434` 是否可访问。
3. `ollama list` 中是否存在 `qwen3-vl:8b`。
4. Java17 服务是否成功启动在 `8081`。
5. `8081` 端口是否被其他进程占用。
6. 前端是否通过 Vite dev server 访问，而不是直接打开构建文件。
7. `vite.config.ts` 中 `/api/agent` 是否仍位于 `/api` 前面。
8. Java8 后端是否仍运行在 `8080`，以保证其他 `/api/**` 正常。

## 9. 验证记录

已执行并通过：

```powershell
cd agent-server
mvn -q -DskipTests compile
```

已执行并通过：

```powershell
cd frontend-vue
npm.cmd run build
```

前端构建存在非阻断警告：

- 当前 Node.js 为 `22.10.0`，Vite 建议 `20.19+` 或 `22.12+`
- 部分依赖注释和 chunk size 警告

这些警告未阻断构建。

最终验收补充测试：

- 字段一致性扫描：预问诊请求不存在 `message/question` 混用，统一为 `question`
- Java17 编译：通过
- 前端构建：通过
- `GET /api/agent/health`：通过，返回 `UP`
- 空 `question`：通过，返回 400
- `POST /api/agent/pre-consultation`：通过，成功调用 Ollama 并返回 `qwen3-vl:8b`
- 8081 临时测试进程已停止

## 10. 当前边界

本阶段只完成用户预问诊 MVP 链路稳定性修整，未实现以下能力：

- 数据库存储
- RAG
- SSE
- 报告分析
- 病历生成
- 多轮会话存储

Java8 后端未被修改，原有登录、患者首页、医生病历等功能不受本次智能体 MVP 影响。
