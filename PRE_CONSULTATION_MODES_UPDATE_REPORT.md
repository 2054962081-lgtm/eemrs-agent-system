# 用户预问诊快速/深度模式增强记录

## 1. 本次目标

在现有“用户预问诊 Agent MVP”基础上增强问诊能力，增加两种问诊模式：

- 快速问诊：面向快速分诊场景，3 轮内给出初步推荐科室。
- 深度问诊：面向复杂或不明确症状，按结构化问诊逻辑逐步收集病情信息，并在信息相对充分时生成总结和建议。

本次仍保持 Java17 `agent-server` 独立服务结构，继续调用本地 Ollama，不改动 Java8 后端业务逻辑，不引入 RAG、数据库、报告分析、病历生成或复杂架构。

## 2. 修改文件

后端：

- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationRequest.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationResponse.java`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationService.java`

前端：

- `frontend-vue/src/api/agent.ts`
- `frontend-vue/src/views/patient/PreConsultation.vue`
- `frontend-vue/src/views/patient/PatientDashboard.vue`

说明：

- `AgentController`、`OllamaProperties`、`application.yml`、`vite.config.ts` 已检查，接口路径、Ollama 配置和代理规则保持现有结构。
- `/api/agent` 仍由 Vite 代理到 Java17 `agent-server` 的 `8081`。
- 其他 `/api/**` 仍代理到 Java8 后端 `8080`。

## 3. 后端请求体结构

接口保持不变：

```http
POST /api/agent/pre-consultation
```

请求体已扩展为：

```json
{
  "mode": "quick",
  "sessionId": "optional-session-id",
  "question": "用户本轮输入内容",
  "round": 1,
  "history": [
    {
      "role": "user",
      "content": "我最近头疼咳嗽"
    },
    {
      "role": "assistant",
      "content": "请问持续几天，是否发热？"
    }
  ]
}
```

字段说明：

- `mode`：问诊模式，支持 `quick` 和 `deep`，为空时默认 `quick`。
- `sessionId`：前端维护的会话标识，MVP 阶段不落库。
- `question`：用户本轮输入，不能为空。
- `round`：当前轮次；快速问诊用于 3 轮限制。
- `history`：前端维护的历史消息，只接受 `user` 和 `assistant` 两类角色。

## 4. 后端响应体结构

响应体已扩展为：

```json
{
  "success": true,
  "mode": "quick",
  "reply": "AI 回复内容",
  "finished": false,
  "round": 1,
  "recommendedDepartment": "",
  "urgency": "normal",
  "model": "qwen3-vl:8b",
  "error": null
}
```

实际接口外层仍沿用项目统一响应：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "success": true,
    "mode": "quick",
    "reply": "...",
    "finished": false,
    "round": 1,
    "recommendedDepartment": "",
    "urgency": "normal",
    "model": "qwen3-vl:8b",
    "error": null
  }
}
```

Ollama 调用失败时，`data.success=false`，并返回友好提示：

```json
{
  "success": false,
  "reply": "智能体服务暂时不可用，请稍后再试。",
  "error": "具体错误信息"
}
```

## 5. 快速问诊实现

快速问诊通过后端 system prompt 和轮次控制共同实现。

核心规则：

- 当前模式为 `quick` 时，后端构建快速问诊 prompt。
- prompt 明确要求不超过三轮对话。
- 第 1 轮信息不足时，可追问持续时间、严重程度、伴随症状、危险信号等。
- 第 2 轮信息仍不足时，只追问少量关键问题。
- 第 3 轮或以上必须结束问诊并给出推荐科室。
- 如果模型回复中已经出现推荐科室，也会被视为快速问诊已完成。

后端完成状态 MVP 判定：

```java
if ("quick".equals(mode)) {
    return round >= 3 || containsRecommendation(reply);
}
```

前端表现：

- 选择“快速问诊”后进入聊天界面。
- 显示当前模式“快速问诊”。
- 显示轮次，例如“第 1 / 3 轮”。
- 后端返回 `finished=true` 后，输入框禁用。
- 页面提示“本次快速问诊已完成，可重新开始或切换深度问诊。”

## 6. 深度问诊实现

深度问诊通过结构化 system prompt 实现，不输出模型隐藏思维链。

后端 prompt 要求关注：

- 主诉
- 起病时间和持续时间
- 症状部位、性质、程度、频率
- 诱因
- 缓解或加重因素
- 伴随症状
- 既往史
- 用药史
- 过敏史
- 近期接触史、饮食、外伤、旅行等
- 儿童、孕妇、老人、基础疾病患者等特殊人群情况
- 危险信号
- 用户希望解决的问题

深度问诊阶段输出：

- `【目前已了解】`
- `【下一步需要了解】`
- `【为什么需要这些信息】`

深度问诊总结阶段输出：

- `【病情信息整理】`
- `【可能相关方向】`
- `【推荐科室】`
- `【就诊优先级】`
- `【建议进一步检查或准备的信息】`
- `【居家注意事项】`
- `【危险信号】`
- `【重要提示】`

前端提供“结束并生成总结”按钮。MVP 阶段没有新增单独接口，而是发送特殊问题：

```text
请根据以上信息生成深度问诊总结和科室建议
```

后端根据用户输入中包含“总结”“结束”“给出建议”等关键词，将深度问诊标记为完成。

## 7. 医学安全约束

医学安全约束已加入后端 system prompt，适用于快速问诊和深度问诊。

主要约束：

- 回答必须基于用户提供的信息和常见医学常识，不得编造检查结果、化验指标、影像结果或病史。
- 不得做确定性诊断。
- 不得声称“你一定是某病”。
- 不得给出具体处方药用药方案、剂量、疗程。
- 不得建议用户停止医生已开的药。
- 不得因用户诱导而改变医学判断。
- 用户错误排除高风险问题时，必须温和提示需要排除风险。
- 信息不足时必须说明“不足以判断”，并追问关键信息。
- 对胸痛、呼吸困难、意识障碍、抽搐、大出血、严重过敏、持续高热、剧烈腹痛、严重外伤、疑似卒中等危险信号，优先提示急诊或尽快线下就医。
- 对儿童、孕妇、老人、免疫低下、慢性病患者，更保守地建议线下就医。
- 推荐科室仅为初步分诊建议，不是最终诊断。
- 回复必须包含“仅供预问诊参考，不能替代医生诊断”。
- 不输出隐藏思维链，只展示问诊依据摘要、分析依据和建议。

## 8. Ollama 调用调整

仍使用本地 Ollama：

```yaml
agent:
  ollama:
    base-url: http://localhost:11434
    model: qwen3-vl:8b
    timeout-seconds: 120
```

请求体新增低温生成参数，降低模型随意发挥：

```json
{
  "options": {
    "temperature": 0.2,
    "top_p": 0.8
  }
}
```

本次没有要求模型强制输出复杂 JSON，仍以自然语言回复为主，减少本地模型格式不稳定风险。

## 9. 前端页面调整

继续复用：

- `frontend-vue/src/views/patient/PreConsultation.vue`

页面新增模式选择区域：

- 标题：请选择问诊模式
- 快速问诊卡片：适合快速描述症状，3 轮内给出初步推荐科室。
- 深度问诊卡片：适合较复杂或不明确的情况，系统将更全面地了解病情并给出分析建议。

聊天界面新增：

- 当前模式展示
- 快速问诊轮次展示
- 深度问诊“结构化问诊中”状态
- 用户输入框
- 发送按钮
- loading 状态
- AI 回复气泡
- 错误提示
- 重新开始按钮
- 切换模式按钮
- 深度问诊“结束并生成总结”按钮

患者首页入口文案同步调整为：

```text
选择快速或深度问诊，获取本地大模型预问诊回复
```

## 10. 前端 API 调整

`frontend-vue/src/api/agent.ts` 新增类型：

```ts
export interface AgentMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface PreConsultationRequest {
  mode: 'quick' | 'deep'
  sessionId?: string
  question: string
  round?: number
  history?: AgentMessage[]
}
```

新增请求函数：

```ts
export function sendPreConsultationMessage(data: PreConsultationRequest) {
  return request.post<PreConsultationResponse, PreConsultationResponse>('/agent/pre-consultation', data, {
    timeout: 120000,
  })
}
```

说明：

- 智能体接口单独保持 `120000ms` timeout。
- 没有修改全局 Axios `15000ms` timeout。
- 不影响登录、患者首页、医生病历等普通接口。

## 11. 验证记录

后端编译：

```powershell
cd agent-server
mvn -q -DskipTests compile
```

结果：通过。

前端构建：

```powershell
cd frontend-vue
npm.cmd run build
```

结果：通过。

构建警告：

- 当前 Node.js 为 `22.10.0`，Vite 提示建议使用 `20.19+` 或 `22.12+`。
- Rolldown 对部分依赖中的 `/* #__PURE__ */` 注释位置给出警告。
- chunk size 有体积警告。

这些警告未阻断构建。

本地服务验证：

- Java17 `agent-server` 可启动在 `8081`。
- `GET /api/agent/health` 返回 `status=UP`。
- 本机 Ollama 可访问。
- Ollama 模型列表中存在 `qwen3-vl:8b`。
- 已进行一次快速问诊第 3 轮接口验证，返回：
  - `success=true`
  - `mode=quick`
  - `finished=true`
  - `round=3`
  - `model=qwen3-vl:8b`

说明：

- PowerShell 中直接显示 Ollama 中文回复时可能出现乱码，这是终端编码显示问题，不代表接口链路失败。
- 浏览器插件在当前 Windows sandbox 中启动失败，未完成 in-app browser 交互截图；但 Vite dev server 曾返回 HTTP 200，前端构建和类型检查已通过。

## 12. 明确未做事项

本次未做：

- RAG
- 向量数据库
- 数据库存储
- SSE
- 报告分析
- 病历正式生成
- 医生审核
- 处方推荐
- 文件上传
- 影像或报告分析
- 复杂权限系统
- Java8 后端业务逻辑改动
- 大规模前端重构

## 13. 建议 commit message

```text
feat: add quick and deep pre-consultation modes
```
