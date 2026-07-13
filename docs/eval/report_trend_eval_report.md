# 报告纵向分析 Harness V2 评测报告

- 评测时间：2026-06-19
- 数据集版本：report-trend-v1
- case 文件：agent-server/src/test/resources/eval/report_trend_eval_cases.json
- 总 case 数：12
- 通过数：12
- 失败数：0

## 指标概览

- 异常识别准确率：1.0000
- 趋势判断准确率：1.0000
- 上下文关联准确率：1.0000
- 建议科室准确率：1.0000
- 隐私通过率：1.0000
- 云端响应有效率：1.0000
- 医生摘要存在率：1.0000
- 患者解释存在率：1.0000
- 上下文解释存在率：1.0000
- Trace 完整率：1.0000

## 失败阶段分布

当前 mock runner 数据集无失败 case。

## Top Bad Cases

当前 mock runner 数据集无 bad case。

## 后续建议

- 使用真实 Trace detail 时，应优先检查 `REPORT_STRUCTURING`、`TREND_ANALYSIS`、`CONTEXT_FUSION` 和 `CLOUD_RESPONSE_VALIDATE` 的 evidence。
- 隐私指标继续保持规则扫描，不使用完整报告原文或完整问诊原文。

## 当前非目标

- LLM-as-judge
- 语义相似度匹配
- 真实云端 integration profile
- CI 自动评测
- 前端评测页面
- 复杂趋势图表
- OCR 报告评测
- 影像报告评测
