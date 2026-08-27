# RAG 知识库（第一阶段）

该目录用于医疗智能体第一阶段 RAG 的本地结构化知识文件。当前只存放 JSON 知识模板和说明文档，不包含向量化、检索、Milvus 写入或业务流程调用逻辑。

## 知识分类

当前包含 5 类知识：

- 症状问诊模板库：`01_symptom_inquiry/`
- 红旗风险规则库：`02_red_flags/`
- 特殊人群问诊库：`03_special_population/`
- 科室分诊映射库：`04_department_triage/`
- 病历预生成模板库：`05_medical_record_templates/`

这些文件仅用于预问诊辅助、风险提示、分诊建议和医生端病历草稿生成辅助，不用于最终诊断，也不能替代医生面诊判断。

## JSON 字段约定

每个 JSON 文件均为对象格式，字段名使用英文 snake_case，核心字段包括：`doc_id`、`doc_type`、`title`、`version`、`language`、`source_type`、`applicable_population`、`related_symptoms`、`related_departments`、`urgency_level`、`must_ask`、`red_flags`、`triage_rules`、`forbidden_actions`、`expected_response_points`、`doctor_record_fields`、`chunk_text`。

`doc_type` 只能使用以下枚举值：

- `symptom_inquiry`
- `red_flag`
- `special_population`
- `department_triage`
- `medical_record_template`

`urgency_level` 推荐使用以下枚举值：

- `普通门诊`
- `尽快就医`
- `急诊`
- `立即拨打120`
- `根据红旗信号判断`

## 后续流程

后续可在独立流程中读取 JSON、校验字段、生成或更新 `chunk_text`、向量化、写入 Milvus，并在检索后辅助生成追问、分诊建议和病历摘要。当前阶段不执行这些步骤。

## 安全约束

- 禁止写入真实患者姓名、身份证号、手机号、住址、病历号等隐私信息。
- 禁止伪造具体指南、论文、医院规范或外部来源。
- 禁止生成具体药物处方和剂量。
- 禁止建议用户自行停用、加量、减量或替换处方药。
- 对胸痛、卒中、呼吸困难、孕产妇急症、儿童危重表现、消化道出血、严重过敏、精神心理危机等场景，应优先提示急诊或立即拨打 120。
