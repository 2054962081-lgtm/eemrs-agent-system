# 医生端预问诊病历草稿查看功能更新记录

## 1. 本次目标

在患者完成深度预问诊并生成预问诊病历草稿后，医生在接诊或病历书写时可以只读查看该患者的最新预问诊病历草稿。

功能边界：

- 仅展示深度问诊生成的病历草稿。
- 快速问诊不展示病历草稿。
- 医生端只读展示，不编辑、不确认、不驳回、不同步正式病历。
- 不自动填充医生病历表单。
- 不返回 `raw_model_reply`。
- 不做 RAG、报告分析、处方推荐、医生审核流。
- 不修改 Java8 后端已有登录、患者、医生、正式病历等核心业务逻辑。

## 2. 已检查的现有结构

已检查医生端相关文件：

- `frontend-vue/src/layouts/DoctorLayout.vue`
- `frontend-vue/src/views/doctor/Consultation.vue`
- `frontend-vue/src/views/doctor/MedicalRecordEditor.vue`
- `frontend-vue/src/views/doctor/WaitingList.vue`
- `frontend-vue/src/router/index.ts`
- `frontend-vue/src/api/agent.ts`
- `frontend-vue/src/api/medicalRecord.ts`
- `frontend-vue/src/api/doctor.ts`
- `frontend-vue/src/api/types.ts`

结论：

- 当前已有医生接诊页：`frontend-vue/src/views/doctor/Consultation.vue`
- 当前已有医生病历书写页：`frontend-vue/src/views/doctor/MedicalRecordEditor.vue`
- 本次优先在现有页面增加入口，没有新增无关路由页面。

## 3. 后端新增查询接口

Java17 `agent-server` 新增只读查询接口：

```http
GET /api/agent/medical-record-drafts/latest?patientId=1
```

用途：

- 按患者 ID 查询最新一条深度预问诊病历草稿。
- 按 `created_at DESC` 排序取最新一条。

查询限制：

```sql
WHERE patient_id = ?
  AND consultation_mode = 'deep'
  AND deleted = 0
ORDER BY created_at DESC
LIMIT 1
```

另一个详情接口：

```http
GET /api/agent/medical-record-drafts/{draftId}
```

用途：

- 按草稿 ID 查询详情。

查询限制：

```sql
WHERE id = ?
  AND consultation_mode = 'deep'
  AND deleted = 0
LIMIT 1
```

说明：

- 接口只读，不修改草稿状态。
- 不返回 `raw_model_reply`。
- 数据库连接失败时返回友好错误，不让服务崩溃。
- `patientId` / `draftId` 为空或非法时返回参数错误。

## 4. 后端响应结构

有草稿：

```json
{
  "success": true,
  "hasDraft": true,
  "draft": {
    "id": 1001,
    "patientId": 1,
    "sessionId": "xxx",
    "consultationMode": "deep",
    "sourceType": "DEEP_PRE_CONSULTATION",
    "chiefComplaint": "...",
    "presentIllnessHistory": "...",
    "recommendedDepartment": "...",
    "urgency": "normal",
    "consultationSummary": "...",
    "recordJson": {},
    "status": "DRAFT",
    "createdAt": "2026-05-31T10:00:00",
    "updatedAt": "2026-05-31T10:00:00",
    "parseError": false
  },
  "message": "ok",
  "error": null
}
```

无草稿：

```json
{
  "success": true,
  "hasDraft": false,
  "draft": null,
  "message": "暂无预问诊病历草稿",
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
    "hasDraft": false,
    "draft": null,
    "message": "暂无预问诊病历草稿"
  }
}
```

## 5. 后端文件变更

新增：

- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftDetail.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftQueryResponse.java`

修改：

- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftController.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftService.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftRepository.java`

核心实现：

- `MedicalRecordDraftController.latest`
- `MedicalRecordDraftController.detail`
- `MedicalRecordDraftService.findLatestByPatientId`
- `MedicalRecordDraftService.findById`
- `MedicalRecordDraftRepository.findLatestByPatientId`
- `MedicalRecordDraftRepository.findById`

## 6. record_json 处理

数据库中 `record_json` 为 `LONGTEXT`。

后端返回前会尝试解析：

- 解析成功：`recordJson` 返回 JSON 对象，`parseError=false`
- 解析失败：`recordJson` 返回原始字符串，`parseError=true`

目的：

- 医生端优先展示结构化字段。
- 如果历史数据或模型输出异常，仍可查看原始 `record_json`，但不会暴露 `raw_model_reply`。

## 7. 前端 API 变更

修改文件：

- `frontend-vue/src/api/agent.ts`

新增类型：

```ts
export interface MedicalRecordDraftDetail {
  id: number
  patientId?: number
  sessionId?: string
  consultationMode?: string
  sourceType?: string
  chiefComplaint?: string
  presentIllnessHistory?: string
  recommendedDepartment?: string
  urgency?: string
  consultationSummary?: string
  recordJson?: any
  status?: string
  createdAt?: string
  updatedAt?: string
  parseError?: boolean
}
```

```ts
export interface LatestMedicalRecordDraftResponse {
  success: boolean
  hasDraft: boolean
  draft?: MedicalRecordDraftDetail | null
  message?: string
  error?: string
}
```

新增方法：

```ts
export function getLatestMedicalRecordDraft(patientId: number | string) {
  return request.get<LatestMedicalRecordDraftResponse, LatestMedicalRecordDraftResponse>(
    '/agent/medical-record-drafts/latest',
    {
      params: { patientId },
      timeout: 30000,
    },
  )
}
```

```ts
export function getMedicalRecordDraftById(draftId: number | string) {
  return request.get<LatestMedicalRecordDraftResponse, LatestMedicalRecordDraftResponse>(
    `/agent/medical-record-drafts/${draftId}`,
    {
      timeout: 30000,
    },
  )
}
```

说明：

- 查询接口 timeout 使用 `30000ms`。
- 不影响原有 `sendPreConsultationMessage`。
- 不影响原有 `generateMedicalRecordDraft`。

## 8. 前端组件

新增组件：

```text
frontend-vue/src/components/doctor/MedicalRecordDraftViewer.vue
```

组件职责：

- 接收 `patientId`。
- 显示“查看预问诊病历草稿”按钮。
- 点击后调用 `getLatestMedicalRecordDraft(patientId)`。
- loading 时显示按钮加载状态。
- 有草稿时打开 Drawer 展示。
- 无草稿时显示“暂无预问诊病历草稿”。
- 查询失败时显示友好错误。
- 提供“刷新”按钮。
- 提供“复制完整 JSON”按钮。

组件明确不做：

- 不编辑草稿。
- 不删除草稿。
- 不审核草稿。
- 不同步到正式病历。
- 不自动填入医生病历输入框。

## 9. 医生端入口位置

入口加入两个已有页面：

1. 医生接诊页：

```text
frontend-vue/src/views/doctor/Consultation.vue
```

位置：

- 患者基础信息卡片下方，与“进入病历书写”按钮并列。

2. 医生病历书写页：

```text
frontend-vue/src/views/doctor/MedicalRecordEditor.vue
```

位置：

- 页面顶部 toolbar 右侧。

说明：

- 医生接诊时可以先查看草稿。
- 医生写病历时也可以随时查看草稿。
- 草稿内容不会自动覆盖或填充医生正在录入的正式病历。

## 10. 医生端展示内容

Drawer 顶部提示：

```text
该内容由智能体根据患者深度预问诊信息自动生成，仅供医生接诊参考，不能替代医生诊断，需由医生审核后方可采纳。
```

基础信息：

- 草稿 ID
- 生成时间
- 状态
- 来源：深度预问诊
- 会话 ID
- 就诊优先级

核心病历：

- 主诉
- 现病史
- 推荐科室
- 深度问诊总结

结构化分组：

- 患者基础信息
- 主诉与现病史
- 既往史、用药史、过敏史
- 风险评估
- 可能相关方向
- 建议检查
- 居家和就医建议
- 医生复核提示
- 完整 JSON

展示方式：

- `el-descriptions` 展示基础字段。
- `el-collapse` 展示分组结构。
- 数组内容使用 tag 或列表展示。
- 完整 JSON 放在折叠区域中，便于排查。

## 11. 有草稿和无草稿展示

有草稿：

- Drawer 打开。
- 显示基础字段、核心病历字段、结构化 JSON 分组。
- 显示 `Draft ID`、生成时间、状态、来源。
- 可复制完整 JSON。

无草稿：

- Drawer 打开。
- 显示空状态文案：

```text
暂无预问诊病历草稿
```

查询失败：

- Drawer 打开。
- 显示友好错误：

```text
预问诊病历草稿查询失败，请稍后重试。
```

## 12. 安全与边界

已满足：

- 医生端入口仅出现在医生页面。
- 患者端未新增医生查看入口。
- 接口仍在 `/api/agent/**` 下。
- 不返回 `raw_model_reply`。
- 不打印完整患者隐私信息。
- 不暴露数据库错误堆栈到前端。
- 只展示 `consultation_mode='deep'` 的草稿。
- 不提供“保存为正式病历”“审核通过”“自动填充病历”按钮。

## 13. 验证记录

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

构建说明：

- 普通沙箱下前端构建仍会遇到 `node_modules/.tmp` 写入权限问题。
- 提权运行后构建通过。
- Vite 仍提示当前 Node.js `22.10.0` 建议升级到 `20.19+` 或 `22.12+`。
- Rolldown 注释和 chunk size 警告未阻断构建。

数据库验证：

- 已执行/确认建表 SQL。
- 已确认 `agent_medical_record_draft` 表存在。

查询接口验证：

```http
GET http://localhost:8081/api/agent/medical-record-drafts/latest?patientId=1
```

当前本地表内暂无记录，返回：

```json
{
  "success": true,
  "hasDraft": false,
  "draft": null,
  "message": "暂无预问诊病历草稿"
}
```

该结果符合无草稿场景预期。

## 14. 尚未完成或需人工验证

仍建议人工验证：

1. 数据库准备一条 `patient_id` 与医生端当前接诊患者一致的草稿记录。
2. 登录医生端。
3. 进入接诊页或病历书写页。
4. 点击“查看预问诊病历草稿”。
5. 验证有草稿时 Drawer 正常展示结构化字段。
6. 验证无草稿时显示“暂无预问诊病历草稿”。
7. 验证不会影响医生原有病历书写和提交流程。

## 15. 未做事项

本次未做：

- 医生审核流
- 正式病历写入
- 草稿编辑
- 草稿删除
- RAG
- 报告分析
- 处方建议
- 自动填充医生病历
- 权限系统大改
- Java8 后端大改
- 大规模前端重构

## 16. 建议 commit message

```text
feat: show pre-consultation draft in doctor workflow
```
