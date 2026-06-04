# 医疗预问诊智能体评测集

本目录存放医疗预问诊智能体的种子评测集、评分规则和本地自测脚本。评测材料只用于本地开发验证，不包含真实患者隐私数据。

## 为什么主格式使用 JSONL

主数据源是 `evaluation/data/pre_consultation_eval_cases.jsonl`。JSONL 每行都是一个完整样本，适合 Codex、脚本和版本管理反复读取、筛选和扩展；多轮对话、预期结果、评分项和一票否决规则也能保留嵌套结构。

`evaluation/data/pre_consultation_eval_cases.sample.xlsx` 只是人工查看和编辑的辅助版本，不作为唯一或主测试数据源。

## 目录结构

```text
evaluation/
  README.md
  schema/
    eval_case_schema.json
  data/
    pre_consultation_eval_cases.jsonl
    pre_consultation_eval_cases.sample.xlsx
  rubric/
    scoring_rubric.md
  scripts/
    run_pre_consultation_eval.py
    export_jsonl_to_excel.py
  reports/
    .gitkeep
```

## 运行自测

只读取样本、不调用接口：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --dry-run
```

运行前 5 条：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --limit 5
```

只运行 `quick_common`：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --case-type quick_common
```

指定服务地址：

```bash
python evaluation/scripts/run_pre_consultation_eval.py --base-url http://localhost:8081
```

报告输出到 `evaluation/reports/`，包括 `eval_report_YYYYMMDD_HHMMSS.json` 和 `eval_report_YYYYMMDD_HHMMSS.csv`。

## 导出 Excel

```bash
python evaluation/scripts/export_jsonl_to_excel.py
```

导出的 Excel 字段会展平主要信息，便于人工查看。编辑后如需作为自动化评测数据，应回写 JSONL 并校验每行 JSON 对象完整性。

## 自动评分覆盖范围

当前脚本只做规则化粗评：

- `mustAskOrMention` 关键词是否部分出现。
- `mustNotSay` 禁用表达是否出现。
- `recommendedDepartments` 是否出现。
- `urgency` 相关表达是否粗略匹配。
- `record_generation` 的 JSON 是否可解析。
- `hardFailRules` 对应的明显违规表达是否出现。

医学语义准确性、复杂分诊路径、特殊人群风险、用药建议合理性和病历草稿完整性仍需要人工复核。医学安全是一票否决项：严重安全错误应直接判为 fail。

## 不修改业务功能

本目录只新增评测集、说明文档和可重复运行的自测脚本；不修改智能体业务代码、prompt、前端页面或数据库表结构。
