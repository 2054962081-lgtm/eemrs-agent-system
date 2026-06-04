# 预问诊病历草稿功能更新记录

## 1. 本次目标

在现有用户预问诊 Agent 的快速问诊、深度问诊基础上，新增“病历预生成”能力。

功能边界：

- 仅允许深度问诊结束后生成“预问诊病历草稿”。
- 快速问诊不生成病历草稿。
- 病历草稿不是正式病历，必须提示需由医生审核确认。
- 病历草稿写入新表 `agent_medical_record_draft`。
- 不写入 Java8 后端原有正式病历表。
- 不做 RAG、报告分析、医生审核流、处方推荐或复杂多表设计。

## 2. 已检查的现有结构

已检查：

- `agent-server/pom.xml`
- `agent-server/src/main/resources/application.yml`
- `agent-server/src/main/java/com/liu/eemrsagent/agent/PreConsultationService.java`
- `frontend-vue/src/api/agent.ts`
- `frontend-vue/src/views/patient/PreConsultation.vue`
- `frontend-vue/vite.config.ts`
- `eemrs-server-master/src/main/resources/application.yml`

Java8 后端当前本地数据库配置文件为：

```yaml
spring:
  datasource:
    username: root
    password: ${AGENT_DB_PASSWORD:}
    url: jdbc:mysql:///eemrs?serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
```

说明：

- Java8 后端业务数据库名为 `eemrs`。
- Java8 后端本地配置文件已在 `.gitignore` 中，不应提交真实密码。
- Java17 `agent-server` 新增数据库配置时使用环境变量，不硬编码真实密码。

## 3. 新增文件

后端新增：

- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/AgentMessage.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftController.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftService.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftRepository.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftGenerateRequest.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftGenerateResponse.java`
- `agent-server/src/main/java/com/liu/eemrsagent/medicalrecord/MedicalRecordDraftEntity.java`
- `agent-server/src/main/resources/sql/create_agent_medical_record_draft.sql`

文档新增：

- `MEDICAL_RECORD_DRAFT_UPDATE_REPORT.md`

## 4. 修改文件

后端修改：

- `agent-server/pom.xml`
- `agent-server/src/main/resources/application.yml`

前端修改：

- `frontend-vue/src/api/agent.ts`
- `frontend-vue/src/views/patient/PreConsultation.vue`

说明：

- 未修改 Java8 后端业务代码。
- 未修改 Java8 正式病历表写入逻辑。
- 未新增独立前端页面，继续复用患者端 `PreConsultation.vue`。

## 5. 新建表 SQL

SQL 文件位置：

```text
agent-server/src/main/resources/sql/create_agent_medical_record_draft.sql
```

建表 SQL：

```sql
CREATE TABLE IF NOT EXISTS agent_medical_record_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    patient_id BIGINT NULL COMMENT '患者ID，MVP阶段可为空',
    session_id VARCHAR(100) NULL COMMENT '预问诊会话ID',
    consultation_mode VARCHAR(20) NOT NULL DEFAULT 'deep' COMMENT '问诊模式，只允许 deep 生成病历草稿',
    source_type VARCHAR(50) NOT NULL DEFAULT 'DEEP_PRE_CONSULTATION' COMMENT '来源类型',
    chief_complaint VARCHAR(1000) NULL COMMENT '主诉',
    present_illness_history TEXT NULL COMMENT '现病史',
    recommended_department VARCHAR(255) NULL COMMENT '推荐科室',
    urgency VARCHAR(50) NULL COMMENT '就诊优先级',
    consultation_summary LONGTEXT NULL COMMENT '深度问诊总结原文',
    record_json LONGTEXT NOT NULL COMMENT '结构化病历草稿JSON',
    raw_model_reply LONGTEXT NULL COMMENT '模型原始回复，便于排查格式问题',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿状态：DRAFT/CONFIRMED/ARCHIVED',
    created_by VARCHAR(100) NULL COMMENT '创建人，MVP阶段可为空',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='智能体预问诊病历草稿表';
```

说明：

- `record_json` 使用 `LONGTEXT`，避免 MySQL 版本兼容问题。
- `raw_model_reply` 保存模型原始输出，便于排查 JSON 格式问题。
- 当前未使用 Flyway 或 Liquibase，因此需要手动执行该 SQL。

## 6. 数据库连接配置

`agent-server/src/main/resources/application.yml` 新增：

```yaml
spring:
  datasource:
    url: ${AGENT_DB_URL:jdbc:mysql://localhost:3306/eemrs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true}
    username: ${AGENT_DB_USERNAME:root}
    password: ${AGENT_DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

依赖新增：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

说明：

- 默认连接 Java8 后端同名业务库 `eemrs`。
- 不提交真实数据库密码。
- 本地运行时应通过环境变量 `AGENT_DB_PASSWORD` 提供密码，或使用本地忽略配置。

## 7. 新增接口

接口路径：

```http
POST /api/agent/medical-record-drafts/generate
```

Controller：

```text
MedicalRecordDraftController
```

Service：

```text
MedicalRecordDraftService
```

Repository：

```text
MedicalRecordDraftRepository
```

## 8. 请求体

```json
{
  "sessionId": "deep-consultation-session-id",
  "patientId": 1,
  "mode": "deep",
  "consultationConclusion": "深度问诊总结文本",
  "history": [
    {
      "role": "user",
      "content": "我最近胸闷..."
    },
    {
      "role": "assistant",
      "content": "请问持续多久..."
    }
  ]
}
```

校验规则：

- `mode` 必须为 `deep`。
- `consultationConclusion` 不能为空。
- `history` 必须至少包含一条用户输入。
- 快速问诊不会显示前端入口，也会被后端拒绝。

## 9. 响应体

成功：

```json
{
  "success": true,
  "draftId": 1001,
  "message": "病历草稿生成成功",
  "record": {
    "recordType": "pre_consultation_draft",
    "chiefComplaint": {
      "text": "...",
      "duration": "..."
    }
  },
  "error": null
}
```

失败：

```json
{
  "success": false,
  "draftId": null,
  "message": "病历草稿生成失败，请稍后重试。",
  "record": null,
  "error": "具体错误"
}
```

实际接口外层仍沿用项目统一响应：

```json
{
  "success": true,
  "message": "ok",
  "data": {
    "success": true,
    "draftId": 1001,
    "message": "病历草稿生成成功",
    "record": {}
  }
}
```

## 10. 病历 JSON 模板

已在 `MedicalRecordDraftService` 的 user prompt 中加入完整病历 JSON 模板。

模板核心结构包括：

- `recordType`
- `version`
- `notice`
- `patientBasicInfo`
- `visitInfo`
- `chiefComplaint`
- `presentIllnessHistory`
- `pastHistory`
- `medicationHistory`
- `allergyHistory`
- `personalAndExposureHistory`
- `familyHistory`
- `riskAssessment`
- `preliminaryAssessment`
- `suggestedExaminations`
- `careAdvice`
- `doctorReviewTips`
- `rawSummary`

其中：

- `recordType` 必须为 `pre_consultation_draft`。
- `notice` 必须保留。
- `preliminaryAssessment.limitations` 必须保留。

## 11. 防止编造信息的措施

后端单独为病历草稿生成构建 system prompt，不复用问诊 prompt。

关键约束：

- 只能基于深度问诊历史和深度问诊总结生成。
- 不得编造患者未提供的信息。
- 未提供的信息填写空字符串、空数组、`null` 或“未提供”。
- 不得做确定性诊断。
- 不得将“可能相关方向”写成“确诊疾病”。
- 不得生成处方药剂量和疗程。
- 不得建议停止医生已开的药。
- 危险信号必须写入 `riskAssessment.redFlags` 和 `careAdvice.whenToSeekEmergencyCare`。
- 输出必须是合法 JSON。
- 不输出 Markdown、代码块、解释文字或隐藏推理过程。

## 12. JSON 解析和校验

后端解析策略：

1. 优先直接 `ObjectMapper.readTree(rawReply)`。
2. 如果失败，截取第一个 `{` 到最后一个 `}` 之间的内容再解析。
3. 如果仍失败，返回生成失败，不写入数据库。
4. 校验 `recordType == pre_consultation_draft`。
5. `record_json` 只保存合法 JSON。
6. `raw_model_reply` 保存模型原始回复，便于排查。

## 13. 写库字段映射

写入表：

```text
agent_medical_record_draft
```

字段映射：

- `patient_id`：`request.patientId`
- `session_id`：`request.sessionId`
- `consultation_mode`：`deep`
- `source_type`：`DEEP_PRE_CONSULTATION`
- `chief_complaint`：`record.chiefComplaint.text`
- `present_illness_history`：`record.presentIllnessHistory` JSON 字符串
- `recommended_department`：`record.visitInfo.recommendedDepartment.primary`
- `urgency`：`record.visitInfo.urgency.level`
- `consultation_summary`：`request.consultationConclusion`
- `record_json`：完整合法 JSON 字符串
- `raw_model_reply`：Ollama 原始回复
- `status`：`DRAFT`
- `created_by`：MVP 阶段使用 `patientId` 字符串或空
- `deleted`：`0`

## 14. 前端改动

`frontend-vue/src/api/agent.ts` 新增：

- `MedicalRecordDraftGenerateRequest`
- `MedicalRecordDraftGenerateResponse`
- `generateMedicalRecordDraft`

`frontend-vue/src/views/patient/PreConsultation.vue` 新增：

- 深度问诊完成总结后显示“生成病历草稿”按钮。
- 快速问诊不显示病历草稿按钮。
- 生成中显示 loading。
- 成功后展示 `draftId`。
- 成功后展示病历草稿重点字段。
- 提供完整 JSON 折叠展示。
- 提供复制 JSON 按钮。
- 显示提示：“该内容为智能体根据预问诊信息生成的病历草稿，仅供医生参考，不能替代医生诊断，需由医生审核确认后方可作为正式病历。”

展示字段：

- 主诉
- 现病史
- 推荐科室
- 就诊优先级
- 可能相关方向
- 危险信号
- 建议进一步确认的信息
- 居家和就医建议

## 15. 已完成验证

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

数据库环境初步检查：

- 本机 `localhost:3306` 可连通。
- 本机存在 MySQL CLI：`C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`。

## 16. 尚未完成的验证

由于用户在数据库写入验证前要求更新文档，以下项目尚未最终执行或确认：

- 是否已手动执行建表 SQL。
- `agent_medical_record_draft` 表是否已实际创建。
- 是否已通过新接口生成并插入一条真实病历草稿。
- 是否已确认数据库中的 `record_json` 可被 JSON 解析。
- 是否已通过前端完整点击链路完成一次“深度问诊总结 -> 生成病历草稿 -> 展示 draftId”。

代码层面已实现上述链路，但数据库写入成功仍取决于：

- MySQL 中是否存在 `eemrs` 数据库。
- 是否已创建 `agent_medical_record_draft` 表。
- `AGENT_DB_USERNAME` / `AGENT_DB_PASSWORD` 是否正确。
- Ollama 是否正常运行并能返回合法 JSON。

## 17. 未做事项

本次未做：

- RAG
- 向量数据库
- 报告分析
- 处方推荐
- 医生审核流
- 正式病历写入
- 权限系统大改
- Java8 后端业务逻辑改动
- 大规模前端重构
- 复杂多表设计

## 18. 建议 commit message

```text
feat: add pre-consultation medical record draft generation
```
