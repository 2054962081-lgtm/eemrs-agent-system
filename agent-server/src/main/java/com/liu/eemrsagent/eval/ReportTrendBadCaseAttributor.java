package com.liu.eemrsagent.eval;

public class ReportTrendBadCaseAttributor {
    public Attribution attribute(ReportTrendEvalCase evalCase, ReportTrendTraceEvidence evidence, ReportTrendEvalResult result) {
        String error = evidence.getErrorCode();
        if (Boolean.FALSE.equals(result.getTraceComplete())) return attr(ReportTrendFailureStage.TRACE_INCOMPLETE, "Trace 缺失关键步骤或 sequence 重复。");
        if ("REPORT_DECRYPT_FAILED".equals(error) || failed(evidence, "LOCAL_DECRYPT")) return attr(ReportTrendFailureStage.REPORT_DECRYPT_ERROR, "本地解密步骤失败。");
        if ("REPORT_PARSE_FAILED".equals(error) || failed(evidence, "REPORT_STRUCTURING")) return attr(ReportTrendFailureStage.REPORT_PARSE_ERROR, "报告结构化解析失败。");
        if (Boolean.FALSE.equals(result.getPrivacyPass()) || "CLOUD_PAYLOAD_PRIVACY_VIOLATION".equals(error)) return attr(ReportTrendFailureStage.CLOUD_PAYLOAD_PRIVACY_ERROR, "隐私检查失败或输出疑似泄露身份信息。");
        if ("CLOUD_MODEL_FAILED".equals(error) || failed(evidence, "CLOUD_MODEL_REQUEST") || failed(evidence, "CLOUD_MODEL_RESPONSE")) return attr(ReportTrendFailureStage.CLOUD_MODEL_ERROR, "云端模型调用失败。");
        if (Boolean.FALSE.equals(result.getCloudResponseValid()) || "CLOUD_RESPONSE_INVALID".equals(error)) return attr(ReportTrendFailureStage.CLOUD_RESPONSE_INVALID, "云端 JSON 响应缺少必要字段。");
        if ("RESULT_ENCRYPT_FAILED".equals(error) || failed(evidence, "RESULT_ENCRYPT_STORE")) return attr(ReportTrendFailureStage.RESULT_ENCRYPT_ERROR, "分析结果加密存储失败。");
        if (Boolean.FALSE.equals(result.getAbnormalDetectionCorrect())) return attr(ReportTrendFailureStage.ABNORMAL_DETECTION_ERROR, "期望异常指标未被正确识别。");
        if (Boolean.FALSE.equals(result.getTrendDirectionCorrect())) return attr(ReportTrendFailureStage.TREND_ANALYSIS_ERROR, "期望趋势方向与实际结果不一致。");
        if (expectsContext(evalCase) && evidence.getSymptomTagCount() == 0 && evidence.getChronicDiseaseTagCount() == 0) {
            return attr(ReportTrendFailureStage.CONTEXT_LOAD_ERROR, "Case 期望上下文，但 Trace 未显示上下文标签。");
        }
        if (Boolean.FALSE.equals(result.getContextLinkCorrect())) return attr(ReportTrendFailureStage.CONTEXT_FUSION_ERROR, "期望症状/病史与指标关联未体现。");
        if (Boolean.FALSE.equals(result.getDoctorSummaryPresent()) || Boolean.FALSE.equals(result.getPatientExplanationPresent())) return attr(ReportTrendFailureStage.CLOUD_RESPONSE_INVALID, "医生摘要或患者解释缺失。");
        return attr(ReportTrendFailureStage.NONE, "");
    }

    private boolean failed(ReportTrendTraceEvidence evidence, String step) {
        return "FAILED".equals(evidence.getStepStatusMap().get(step));
    }

    private boolean expectsContext(ReportTrendEvalCase evalCase) {
        return !evalCase.getExpected().getExpectedContextLinks().isEmpty()
                || evalCase.getExpected().isContextualInterpretationRequired();
    }

    private Attribution attr(ReportTrendFailureStage stage, String reason) {
        return new Attribution(stage, reason);
    }

    public record Attribution(ReportTrendFailureStage stage, String reason) {}
}
