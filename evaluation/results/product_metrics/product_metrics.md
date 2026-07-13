# 产品指标汇总

- generated_at: 2026-07-13T21:36:43
- git_commit: 440bdb0
- model: deepseek-v4-flash where captured in eval output; mock-model for docs/eval mock harness
- rag: RAG comparison uses medical_rag_ab_test_report_20260606_102121.md; pre-consultation batch output includes live response payloads but not a uniform RAG flag per case.

| 模块 | 指标 | 数值 | 样本数 | 数据来源 |
|---|---:|---:|---:|---|
| pre_consultation | 评测样本数量 | 50 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 成功完成率 | 1.0 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 平均问诊轮次 | 1.0 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 平均响应时间 | Not available / 尚无足够真实数据 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | Must-Ask 平均覆盖率 | 0.3645 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 关键病史遗漏率 | 0.6355 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | red flag 召回率 | Not available / 尚无足够真实数据 | 0 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 科室推荐命中率 | 1.0 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 输出格式成功率 | Not available / 尚无足够真实数据 | 0 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| pre_consultation | 模型调用失败率 | 0.0 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| rag | RAG 对照样本数量 | 90 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 检索成功率 | 1.0 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | 有效知识召回率 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | 空召回率 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | 无关片段率 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 开启总分 | 8.7411 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 关闭总分 | 7.8511 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 开启/关闭总分变化 | 0.89 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 开启与关闭 Must-Ask 覆盖率变化 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | RAG 开启与关闭安全指标变化 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| rag | 平均检索耗时 | Not available / 尚无足够真实数据 | 90 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` |
| report_trend | 指标提取成功率 | 1.0 | 12 | `docs/eval/report_trend_eval_results.csv` |
| report_trend | 同名指标对齐成功率 | Not available / 尚无足够真实数据 | 12 | `docs/eval/report_trend_eval_results.csv` |
| report_trend | 趋势判断成功率 | 1.0 | 12 | `docs/eval/report_trend_eval_results.csv` |
| report_trend | 脱敏成功率 | 1.0 | 12 | `docs/eval/report_trend_eval_results.csv` |
| report_trend | 分析接口成功率 | 1.0 | 12 | `docs/eval/report_trend_eval_results.csv` |
| report_trend | 平均处理时延 | 200.0 | 12 | `docs/eval/report_trend_eval_results.csv` |
| draft_review | 完全采纳率 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 部分采纳率 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 拒绝率 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 写入正式病历成功率 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 平均审核时长 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 平均修改字段数 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 字段修改率 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 常见拒绝原因 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |
| draft_review | 最常被修改的字段 | Not available / 尚无足够真实数据 | 0 | `agent_medical_record_draft_audit table / no production export found` |

## 说明

- 所有不可获得的指标均显式标记为 `Not available / 尚无足够真实数据`，不做推断。
- `docs/eval` 中的 report_trend 行来自 mock runner，不能描述为生产医生使用数据。
