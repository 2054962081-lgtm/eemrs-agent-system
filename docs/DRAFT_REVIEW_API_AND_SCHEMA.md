# AI 病历草稿审核接口与表结构说明

## 数据库变更

自动初始化入口：

`agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftSchemaInitializer.java`

SQL 参考文件：

`agent-server/src/main/resources/sql/create_agent_medical_record_draft.sql`

### 草稿主表

`agent_medical_record_draft` 在原有字段基础上补充：

- `ai_record_json`：AI 原始结构化草稿；
- `edited_record_json`：医生当前编辑稿；
- `status`：草稿状态；
- `doctor_id_number`：从 JWT 解析得到的审核医生身份；
- `appointment_id`：预约 / 挂号记录引用，当前预留；
- `model_name`：生成草稿的模型；
- `prompt_version`：草稿生成 prompt 版本；
- `trace_id`：Trace Harness 链路 ID；
- `first_reviewed_at`：首次审核时间；
- `completed_at`：采纳、部分采纳或拒绝完成时间；
- `applied_at`：写入正式病历时间；
- `applied_record_hash`：写入内容 hash，用于幂等和审计。

状态枚举：

- `GENERATED`：AI 已生成，尚未审核；
- `REVIEWING`：医生已打开或开始编辑；
- `ACCEPTED`：完全采纳；
- `PARTIALLY_ACCEPTED`：修改后采纳；
- `REJECTED`：拒绝使用；
- `APPLIED`：已写入正式病历。

启动时会重复执行兼容迁移：

- 旧 `DRAFT` 状态自动迁移为 `GENERATED`；
- 缺失的 `ai_record_json` 和 `edited_record_json` 从旧 `record_json` 回填；
- 不删除任何历史草稿。

### 审计表

新增 `agent_medical_record_draft_audit`：

- `draft_id`；
- `doctor_id_number`；
- `action`；
- `before_json`；
- `after_json`；
- `reject_reason`；
- `comment`；
- `action_time`；
- `trace_id`。

写入审计日志前会脱敏：

- `patientBasicInfo.name`；
- `patientBasicInfo.contact`；
- `patientBasicInfo.idNumber`。

## 权限策略

agent-server 解析 `eemrs-server-master` 颁发的 Bearer JWT。

服务端规则：

- 只有 `role=DOCTOR` 可以审核、编辑、采纳、拒绝或写入草稿；
- 不信任前端传入的 doctorId；
- 医生身份只从 JWT claims 中读取；
- 草稿首次被医生打开时会锁定 `doctor_id_number`；
- 已锁定草稿不允许其他医生修改；
- 患者 token 调用审核接口返回 `403`；
- 已拒绝草稿不能写入正式病历；
- 已写入草稿重复写入时保持幂等。

## 接口清单

基础路径：

`/api/agent/medical-record-drafts`

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/generate` | 根据深度预问诊生成 AI 草稿 |
| `GET` | `/latest?patientId=...` | 查询当前医生有权查看的最新草稿 |
| `GET` | `/{draftId}` | 查询草稿详情，并记录首次打开 |
| `POST` | `/{draftId}/edits` | 保存医生编辑稿 |
| `POST` | `/{draftId}/accept` | 完全采纳 |
| `POST` | `/{draftId}/partial-accept` | 修改后采纳 |
| `POST` | `/{draftId}/reject` | 拒绝草稿，`rejectReason` 必填 |
| `POST` | `/{draftId}/apply` | 将已采纳草稿写入正式病历 |
| `GET` | `/{draftId}/history` | 查询审核历史 |
| `GET` | `/{draftId}/status` | 查询当前状态与关键时间 |

`/{draftId}/apply` 会调用核心后端：

`POST /api/medical-records`

调用时透传医生 `Authorization` header，由核心后端继续完成医生角色、签名和正式病历写入校验。

## 本地验证命令

后端测试：

```powershell
cd agent-server
mvn -q test
```

前端类型检查：

```powershell
cd frontend-vue
npx.cmd vue-tsc --noEmit -p tsconfig.app.json
```
