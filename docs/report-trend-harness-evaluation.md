# 报告纵向分析 Harness V2 评测接入说明

## 一、功能目标

本次接入让报告纵向分析 Agent 能像预问诊 Agent 一样，被标准 case 回放、Trace evidence 解析、规则指标评分、Bad Case 自动归因和报告输出。

该能力只服务评测，不改变报告分析业务流程、前端展示、预问诊流程、挂号流程或登录权限。

## 二、为什么报告纵向分析需要接入 Harness V2

报告纵向分析包含本地解密、脱敏、结构化、指标标准化、异常检测、趋势计算、上下文融合、云端 JSON 输出和结果加密存储。仅靠接口成功无法判断每个环节是否可信，因此需要通过 Trace evidence 对关键步骤进行规则化检查。

## 三、与预问诊 Harness V2 的关系

本实现复用现有 Harness V2 的 `EvalTraceDetail`、`EvalTraceStep`、Trace detail 结构和报告输出思路，但新增独立的 `ReportTrend*` 类，避免影响预问诊评测。

预问诊 Harness V2 不需要改动，报告纵向分析使用独立 runner 和独立输出文件。

## 四、评测集 schema

评测集位于：

```text
agent-server/src/test/resources/eval/report_trend_eval_cases.json
```

每条 case 包含：

- `case_id`
- `source`
- `scenario`
- `category`
- `report_type`
- `input`
- `expected`
- `tags`

`expected` 中支持期望异常指标、期望趋势方向、期望上下文关联、期望建议科室、必需摘要字段、隐私要求、云端响应有效性要求和 Trace 完整性要求。

## 五、指标定义

当前支持以下指标：

- `abnormal_detection_correct`：期望异常指标和异常标志是否命中。
- `trend_direction_correct`：期望趋势方向是否命中。
- `context_link_correct`：症状或病史标签与指标代码是否在上下文关联中体现。
- `suggested_department_correct`：建议科室是否等于期望科室。
- `privacy_pass`：输出和 Trace evidence 中是否未出现身份信息、密文、密钥和完整原文。
- `cloud_response_valid`：云端 JSON 是否包含必要结构。
- `doctor_summary_present`：医生端摘要是否非空。
- `patient_explanation_present`：患者端解释是否非空。
- `contextual_interpretation_present`：上下文解释是否非空。
- `trace_complete`：报告分析关键 Trace step 是否完整且 sequence 不重复。

## 六、上下文融合指标说明

上下文融合指标使用规则匹配，不使用语义相似度或大模型裁判。

如果期望中包含症状标签和指标代码，例如“发热、咳嗽”和 “WBC、CRP”，实际结果只要在 `contextLinks` 或 `contextualInterpretation` 中体现至少一个症状标签和至少一个指标代码，即认为上下文关联命中。

## 七、Trace evidence 来源

`ReportTrendTraceParser` 从 Trace detail 中提取：

- `analysis_id`
- `trace_run_id`
- report count
- indicator count
- abnormal count
- trend item count
- context availability
- context used
- symptom tag count
- chronic disease tag count
- recommended department
- payload hash
- response hash
- model name
- cloud response validation status
- error code
- step status map
- sequence duplicate
- latency
- token usage
- privacy guard status

解析器不依赖完整明文报告或完整问诊原文。

## 八、Bad Case 归因规则

当前支持的失败阶段包括：

- `REPORT_DECRYPT_ERROR`
- `REPORT_PARSE_ERROR`
- `INDICATOR_NORMALIZE_ERROR`
- `ABNORMAL_DETECTION_ERROR`
- `TREND_ANALYSIS_ERROR`
- `CONTEXT_LOAD_ERROR`
- `CONTEXT_FUSION_ERROR`
- `CLOUD_PAYLOAD_PRIVACY_ERROR`
- `CLOUD_MODEL_ERROR`
- `CLOUD_RESPONSE_INVALID`
- `RESULT_ENCRYPT_ERROR`
- `TRACE_INCOMPLETE`
- `UNKNOWN`
- `NONE`

归因器优先识别 Trace 不完整、解密失败、解析失败、隐私问题、模型失败和响应无效，再根据异常识别、趋势判断和上下文融合指标定位具体阶段。

## 九、输出报告路径

运行 runner 后输出：

```text
docs/eval/report_trend_eval_results.csv
docs/eval/report_trend_badcase_analysis.json
docs/eval/report_trend_eval_report.md
```

报告使用中文摘要，不包含完整报告原文和完整问诊原文。

## 十、隐私边界

评测输出只允许包含模拟 case 编号、场景描述、指标代码、趋势方向、上下文标签、Trace 元数据和脱敏哈希。

禁止输出：

- 身份证号
- 手机号
- patientId
- doctorId
- visitId
- Authorization
- Cookie
- API Key
- ciphertext
- rawReportText
- 完整报告原文
- 完整问诊原文

## 十一、为什么不使用 LLM-as-judge

本阶段目标是可重复、可解释、可追溯的规则评测。LLM-as-judge 会引入模型波动、成本和隐私边界风险，因此暂不使用。

## 十二、当前不做的内容

- OCR 报告评测
- 影像报告评测
- 自动诊断评测
- 自动开药评测
- 语义相似度评分
- LLM-as-judge
- CI 自动评测
- 前端评测页面
- 复杂趋势图表

## 十三、后续如何接入 CI

后续可在 CI 中执行报告纵向分析专属测试集合，并将三份输出文件作为构建产物保存。真实云端 integration profile 应单独配置，默认测试继续使用 mock runner，避免真实 API 调用和真实患者数据进入自动化评测。
