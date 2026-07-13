# 产品评测指标汇总

生成时间：2026-07-13 21:26:57（Asia/Shanghai）

代码版本：`440bdb0`

工作分支：`codex-product-loop-upgrade`

## 评测目标

本报告面向 AI 产品经理面试展示，汇总当前项目中能够从真实评测文件、Trace Harness 输出和现有结果文件中直接计算的产品指标，覆盖：

- 患者预问诊效果；
- RAG / Milvus 开关对照；
- 报告纵向分析 MVP；
- AI 病历草稿医生审核闭环的数据采集能力。

所有无法从现有文件真实计算的指标均标记为：

`Not available / 尚无足够真实数据`

## 数据来源

| 模块 | 数据集或结果文件 | 样本数 | 模型 | RAG 状态 |
|---|---|---:|---|---|
| 预问诊 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` | 50 | 输出中记录为 `deepseek-v4-flash` | 单样本未统一记录 |
| RAG 对照 | `evaluation/reports/medical_rag_ab_test_report_20260606_102121.md` | 90 | 历史批跑结果 | 开启 / 关闭对照 |
| 报告纵向分析 | `docs/eval/report_trend_eval_results.csv` | 12 | `mock-model` | 测试环境 mock harness |
| 病历草稿审核 | `agent_medical_record_draft_audit` | 0 条生产审核数据 | N/A | N/A |

## 指标定义

- 成功完成率：`finalResponse.data.success == true` 的比例。
- 平均问诊轮次：`finalResponse.data.round` 的平均值。
- Must-Ask 覆盖率：`mustAskHitCount / mustAskTotal`。
- 关键病史遗漏率：`1 - Must-Ask 覆盖率`。
- 科室推荐命中率：`departmentHits` 非空的比例。
- 模型调用失败率：最终响应失败或存在 error 的比例。
- RAG 得分变化：RAG 开启平均分减去 RAG 关闭平均分。
- 报告纵向分析成功率：Trace Harness CSV 中布尔通过字段的平均值。
- 病历草稿审核指标：等待真实医生审核日志产生后，从审计表统计。

## 真实结果表

| 指标 | 数值 | 样本数 | 数据来源 |
|---|---:|---:|---|
| 预问诊评测样本数量 | 50 | 50 | `evaluation/reports/eval_report_extended_refined_20260603_223235.json` |
| 成功完成率 | 1.0000 | 50 | 同上 |
| 平均问诊轮次 | 1.0000 | 50 | 同上 |
| 平均响应时间 | Not available / 尚无足够真实数据 | 50 | 历史 JSON 未保存逐样本 latency |
| Must-Ask 平均覆盖率 | 0.3645 | 50 | 同上 |
| 关键病史遗漏率 | 0.6355 | 50 | 同上 |
| red flag 召回率 | Not available / 尚无足够真实数据 | 0 | 当前 refined JSON 未提供结构化 red flag 标签 |
| 科室推荐命中率 | 1.0000 | 50 | 同上 |
| 输出格式成功率 | Not available / 尚无足够真实数据 | 0 | 当前 refined JSON 不是病历 JSON 格式评测 |
| 模型调用失败率 | 0.0000 | 50 | 同上 |

## RAG 开启 / 关闭对照

数据来源：`evaluation/reports/medical_rag_ab_test_report_20260606_102121.md`

| 指标 | 数值 |
|---|---:|
| 唯一 case 数 | 90 |
| RAG 服务可访问 | True |
| Milvus collection 存在 | True |
| Milvus 向量实体数 | 62 |
| RAG 关闭平均总分 | 7.8511 |
| RAG 开启平均总分 | 8.7411 |
| 平均总分变化 | +0.8900 |
| RAG 开启 / 关闭 Must-Ask 覆盖率变化 | Not available / 尚无足够真实数据 |
| RAG 开启 / 关闭安全指标变化 | Not available / 尚无足够真实数据 |
| 平均检索耗时 | Not available / 尚无足够真实数据 |

## 报告纵向分析 MVP

数据来源：`docs/eval/report_trend_eval_results.csv`

说明：这 12 条是 mock runner / 测试环境数据，不是生产医生使用数据。

| 指标 | 数值 |
|---|---:|
| 指标提取成功率 | 1.0000 |
| 同名指标对齐成功率 | Not available / 尚无足够真实数据 |
| 趋势判断成功率 | 1.0000 |
| 脱敏成功率 | 1.0000 |
| 分析接口成功率 | 1.0000 |
| 平均处理时延 | 200 ms |

## 病历草稿审核指标

本轮已经实现审核状态和审计日志采集，但仓库中尚无真实医生生产审核数据导出。因此以下指标只完成采集能力，不填充猜测值。

| 指标 | 当前结果 |
|---|---|
| 完全采纳率 | Not available / 尚无足够真实数据 |
| 部分采纳率 | Not available / 尚无足够真实数据 |
| 拒绝率 | Not available / 尚无足够真实数据 |
| 写入正式病历成功率 | Not available / 尚无足够真实数据 |
| 平均审核时长 | Not available / 尚无足够真实数据 |
| 平均修改字段数 | Not available / 尚无足够真实数据 |
| 常见拒绝原因 | Not available / 尚无足够真实数据 |
| 最常被修改字段 | Not available / 尚无足够真实数据 |

## 面试可用摘要

在 50 个预问诊评测样本上，当前批跑成功完成率为 100.00%，Must-Ask 平均覆盖率为 36.45%，科室推荐命中率为 100.00%，模型调用失败率为 0.00%。在 90 个 RAG A/B 对照样本上，开启 RAG 后平均总分从 7.8511 提升到 8.7411，提升 0.8900。医生审核 AI 病历草稿的采纳率、拒绝率和平均审核时长已经完成采集链路，但尚无真实医生生产审核数据，因此不填充虚构指标。

## 当前局限

- 预问诊历史结果未保存逐样本响应时延，无法计算平均响应时间。
- 当前 refined 结果没有结构化 red flag 标签，不能可靠计算 red flag 召回率。
- RAG A/B 报告记录的是总分对照，没有拆出 Must-Ask、安全性和检索耗时。
- 报告纵向分析指标来自 mock harness，应明确标记为测试环境数据。
- 病历草稿审核指标需要医生真实使用后从审计表导出。

## 下一步优化建议

- 在评测输出中统一记录 latency、RAG 开关、模型名、trace id。
- 为 red flag case 增加结构化标签，避免从文本猜测召回率。
- 将 RAG 总分拆解为检索成功、有效召回、Must-Ask、安全性和科室推荐维度。
- 使用真实医生审核日志生成采纳率、拒绝率、字段修改率和常见拒绝原因。
