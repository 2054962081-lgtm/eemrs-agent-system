package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.databind.JsonNode;
import com.liu.eemrsagent.trace.AgentTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import com.liu.eemrsagent.trace.TraceRunScope;
import com.liu.eemrsagent.trace.TraceRunStart;
import com.liu.eemrsagent.trace.TraceStepData;
import com.liu.eemrsagent.trace.TraceStepScope;
import com.liu.eemrsagent.trace.TraceStepType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportTrendAnalysisService {
    private final LabReportCipherRepository labReportCipherRepository;
    private final LocalReportDecryptor decryptor;
    private final LocalPiiRedactor piiRedactor;
    private final LocalReportStructuringService structuringService;
    private final TrendAnalysisService trendAnalysisService;
    private final ReportTrendContextService contextService;
    private final CloudPayloadBuilder cloudPayloadBuilder;
    private final CloudPayloadPrivacyGuard privacyGuard;
    private final CloudReportAnalysisClient cloudClient;
    private final ReportAnalysisResultRepository resultRepository;
    private final AgentTraceRecorder traceRecorder;
    private final TraceRedactor traceRedactor;

    public ReportTrendAnalysisService(LabReportCipherRepository labReportCipherRepository,
                                      LocalReportDecryptor decryptor,
                                      LocalPiiRedactor piiRedactor,
                                      LocalReportStructuringService structuringService,
                                      TrendAnalysisService trendAnalysisService,
                                      ReportTrendContextService contextService,
                                      CloudPayloadBuilder cloudPayloadBuilder,
                                      CloudPayloadPrivacyGuard privacyGuard,
                                      CloudReportAnalysisClient cloudClient,
                                      ReportAnalysisResultRepository resultRepository,
                                      AgentTraceRecorder traceRecorder,
                                      TraceRedactor traceRedactor) {
        this.labReportCipherRepository = labReportCipherRepository;
        this.decryptor = decryptor;
        this.piiRedactor = piiRedactor;
        this.structuringService = structuringService;
        this.trendAnalysisService = trendAnalysisService;
        this.contextService = contextService;
        this.cloudPayloadBuilder = cloudPayloadBuilder;
        this.privacyGuard = privacyGuard;
        this.cloudClient = cloudClient;
        this.resultRepository = resultRepository;
        this.traceRecorder = traceRecorder;
        this.traceRedactor = traceRedactor;
    }

    public ReportTrendAnalysisResponse analyze(ReportTrendAnalysisRequest request) {
        String analysisId = "analysis_" + UUID.randomUUID().toString().replace("-", "");
        LocalDate startDate = request.startDate() == null ? LocalDate.now().minusMonths(6) : request.startDate();
        LocalDate endDate = request.endDate() == null ? LocalDate.now() : request.endDate();
        ReportTrendAnalysisRequest normalizedRequest = new ReportTrendAnalysisRequest(
                request.patientId(), request.sessionId(), request.includePreconsultationContext(), request.includeLongTermHealthContext(),
                request.normalizedReportType(), startDate, endDate,
                request.targetItems(), request.outputMode()
        );
        try (TraceRunScope run = traceRecorder.startRun(new TraceRunStart(
                null,
                request.patientId(),
                "report-trend-analysis-agent",
                "report-trend-analysis",
                CloudReportAnalysisClient.PROMPT_VERSION,
                null,
                null,
                Map.of("analysis_id", analysisId, "report_type", normalizedRequest.normalizedReportType(), "date_range", startDate + "/" + endDate)
        ))) {
            try {
                recordRequest(normalizedRequest);
                List<EncryptedLabReportRecord> encryptedReports = queryReports(normalizedRequest);
                if (encryptedReports.isEmpty()) {
                    throw new ReportTrendException(ReportTrendErrorCode.REPORT_NOT_FOUND, "No encrypted lab report found");
                }
                List<StructuredLabReport> structuredReports = encryptedReports.stream()
                        .map(report -> decryptRedactAndStructure(report, normalizedRequest.normalizedReportType()))
                        .toList();
                if (structuredReports.size() < 2) {
                    throw new ReportTrendException(ReportTrendErrorCode.INSUFFICIENT_REPORT_DATA, "At least two reports are required for trend analysis");
                }
                List<TrendItem> trendItems = analyzeTrends(structuredReports, normalizedRequest.targetItems());
                ReportTrendContext context = contextService.load(normalizedRequest);
                Map<String, Object> cloudPayload = buildCloudPayload(normalizedRequest, structuredReports, trendItems, context);
                CloudReportAnalysisClient.CloudResult cloudResult;
                try {
                    cloudResult = callCloud(cloudPayload);
                } catch (ReportTrendException e) {
                    resultRepository.saveFailure(analysisId, request.patientId(), request.normalizedReportType(), e.errorCode(), e.getMessage(), run.runId());
                    run.fail(e.errorCode().name(), e.getMessage());
                    return ReportTrendAnalysisResponse.fail(analysisId, run.runId(), e.errorCode(), e.getMessage(), trendItems);
                }
                ReportTrendAnalysisResponse response = toResponse(analysisId, run.runId(), cloudResult.response(), trendItems, context.contextUsed());
                storeSuccess(analysisId, normalizedRequest, structuredReports.size(), cloudPayload, response, cloudResult.modelName(), run.runId());
                recordFinal(response, trendItems);
                run.updateModel(cloudResult.modelName());
                run.success(Map.of(
                        "analysis_id", analysisId,
                        "report_count", structuredReports.size(),
                        "trend_item_count", trendItems.size(),
                        "abnormal_count", response.abnormalItems().size(),
                        "context_used", context.contextUsed(),
                        "status", "SUCCESS"
                ), cloudResult.promptTokens(), cloudResult.completionTokens(), cloudResult.totalTokens());
                return response;
            } catch (ReportTrendException e) {
                resultRepository.saveFailure(analysisId, request.patientId(), request.normalizedReportType(), e.errorCode(), e.getMessage(), run.runId());
                run.fail(e.errorCode().name(), e.getMessage());
                return ReportTrendAnalysisResponse.fail(analysisId, run.runId(), e.errorCode(), e.getMessage());
            } catch (RuntimeException e) {
                resultRepository.saveFailure(analysisId, request.patientId(), request.normalizedReportType(), ReportTrendErrorCode.UNKNOWN_ERROR, e.getMessage(), run.runId());
                run.fail(ReportTrendErrorCode.UNKNOWN_ERROR.name(), e.getMessage());
                return ReportTrendAnalysisResponse.fail(analysisId, run.runId(), ReportTrendErrorCode.UNKNOWN_ERROR, "Report trend analysis failed");
            }
        }
    }

    private void recordRequest(ReportTrendAnalysisRequest request) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.REPORT_ANALYSIS_REQUEST, "report trend request", null,
                Map.of("patient_hash", traceRedactor.stableHash(request.patientId()), "report_type", request.normalizedReportType()))) {
            step.success(Map.of("target_item_count", request.targetItems() == null ? 0 : request.targetItems().size(), "capture_level", "METADATA_ONLY"));
        }
    }

    private List<EncryptedLabReportRecord> queryReports(ReportTrendAnalysisRequest request) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.REPORT_CIPHER_QUERY, "query encrypted lab reports", null,
                Map.of("patient_hash", traceRedactor.stableHash(request.patientId()), "date_range", request.startDate() + "/" + request.endDate()))) {
            List<EncryptedLabReportRecord> reports = labReportCipherRepository.findEncryptedReports(request.patientId(), request.normalizedReportType(), request.startDate(), request.endDate());
            step.success(Map.of("report_count", reports.size()));
            return reports;
        }
    }

    private StructuredLabReport decryptRedactAndStructure(EncryptedLabReportRecord report, String reportType) {
        String plain;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.LOCAL_DECRYPT, "local decrypt report", null,
                Map.of("report_id_hash", traceRedactor.stableHash(report.reportId())))) {
            plain = decryptor.decrypt(report);
            step.success(Map.of("plain_length", plain.length(), "plain_hash", traceRedactor.stableHash(plain)));
        }
        String redacted;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.LOCAL_PII_REDACT, "local pii redact", null,
                Map.of("report_id_hash", traceRedactor.stableHash(report.reportId())))) {
            redacted = piiRedactor.redact(plain);
            step.success(Map.of("redacted_length", redacted.length(), "redacted_hash", traceRedactor.stableHash(redacted)));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.REPORT_STRUCTURING, "structure lab report", null,
                Map.of("report_id_hash", traceRedactor.stableHash(report.reportId())))) {
            StructuredLabReport structured = structuringService.structure(report.reportId(), report.reportDate(), reportType, redacted);
            step.success(Map.of("indicator_count", structured.items().size()));
            return structured;
        }
    }

    private List<TrendItem> analyzeTrends(List<StructuredLabReport> reports, List<String> targetItems) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.INDICATOR_NORMALIZE, "normalize indicators", null,
                Map.of("report_count", reports.size()))) {
            int count = reports.stream().mapToInt(report -> report.items().size()).sum();
            List<String> canonicalCodes = reports.stream()
                    .flatMap(report -> report.items().stream())
                    .map(LabIndicatorItem::standardCode)
                    .filter(code -> code != null && !code.startsWith("UNKNOWN_"))
                    .distinct()
                    .toList();
            step.success(Map.of(
                    "indicator_count", count,
                    "normalized_indicator_count", canonicalCodes.size(),
                    "canonical_indicator_codes", canonicalCodes
            ));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.ABNORMAL_DETECTION, "detect abnormal indicators", null, null)) {
            long abnormal = reports.stream().flatMap(report -> report.items().stream())
                    .filter(item -> item.abnormalFlag() == AbnormalFlag.HIGH || item.abnormalFlag() == AbnormalFlag.LOW)
                    .count();
            step.success(Map.of("abnormal_count", abnormal));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.TREND_ANALYSIS, "calculate local trend", null, null)) {
            List<TrendItem> trends = trendAnalysisService.analyze(reports);
            if (targetItems != null && !targetItems.isEmpty()) {
                LinkedHashSet<String> targets = new LinkedHashSet<>(targetItems);
                trends = trends.stream().filter(item -> targets.contains(item.code())).toList();
            }
            step.success(TraceStepData.of(null, trends.stream()
                    .map(this::trendTracePayload)
                    .toList(), Map.of(
                    "trend_item_count", trends.size(),
                    "canonical_indicator_codes", trends.stream().map(TrendItem::code).toList()
            )));
            return trends;
        }
    }

    private Map<String, Object> trendTracePayload(TrendItem item) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("indicator_code", item.code());
        out.put("indicator_name", item.name());
        out.put("unit", item.unit());
        out.put("point_count", item.pointCount());
        out.put("first_date", item.firstDate());
        out.put("latest_date", item.latestDate());
        out.put("first_value", item.firstValue());
        out.put("latest_value", item.latestValue());
        out.put("change_absolute", item.changeAbsolute());
        out.put("change_percent", item.changePercent());
        out.put("trend_direction", item.trendDirection());
        return out;
    }

    private Map<String, Object> buildCloudPayload(ReportTrendAnalysisRequest request, List<StructuredLabReport> reports,
                                                  List<TrendItem> trendItems, ReportTrendContext context) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CLOUD_PAYLOAD_BUILD, "build desensitized cloud payload", null, null)) {
            Map<String, Object> payload = cloudPayloadBuilder.build(request, reports, trendItems, context);
            privacyGuard.assertSafe(payload);
            step.success(Map.of(
                    "payload_hash", traceRedactor.stableHash(payload.toString()),
                    "trend_item_count", trendItems.size(),
                    "context_used", context.contextUsed()
            ));
            return payload;
        }
    }

    private CloudReportAnalysisClient.CloudResult callCloud(Map<String, Object> payload) {
        CloudReportAnalysisClient.RawCloudResult raw;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CLOUD_MODEL_REQUEST, "call cloud model", null,
                Map.of("payload_hash", traceRedactor.stableHash(payload.toString())))) {
            try {
                raw = cloudClient.request(payload);
                step.success(new TraceStepData(null, Map.of("response_hash", traceRedactor.stableHash(raw.content()), "model_name", safeModelName(raw.modelName())),
                        Map.of("model_name", safeModelName(raw.modelName())), raw.modelName(), null, raw.promptTokens(), raw.completionTokens(), raw.totalTokens()));
            } catch (ReportTrendException e) {
                step.fail(e.errorCode().name(), e.getMessage());
                throw e;
            }
        }
        JsonNode parsed = parseCloudResponse(raw);
        CloudReportResponse response = validateCloudResponse(parsed);
        return new CloudReportAnalysisClient.CloudResult(response, raw.modelName(), raw.promptTokens(), raw.completionTokens(), raw.totalTokens());
    }

    private JsonNode parseCloudResponse(CloudReportAnalysisClient.RawCloudResult raw) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CLOUD_RESPONSE_PARSE, "parse cloud json response", null,
                Map.of("model_name", safeModelName(raw.modelName()), "raw_response_hash", traceRedactor.stableHash(raw.content())))) {
            try {
                JsonNode response = cloudClient.parseJson(raw.content());
                step.success(Map.of("json_syntax_valid", true));
                return response;
            } catch (ReportTrendException e) {
                step.fail(e.errorCode().name(), e.getMessage());
                throw e;
            }
        }
    }

    private String safeModelName(String modelName) {
        return modelName == null || modelName.isBlank() ? "UNKNOWN" : modelName;
    }

    private CloudReportResponse validateCloudResponse(JsonNode json) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CLOUD_SCHEMA_VALIDATE, "validate cloud response schema", null, null)) {
            CloudReportResponse response;
            try {
                response = cloudClient.toResponse(json);
            } catch (ReportTrendException e) {
                step.fail(e.errorCode().name(), e.getMessage());
                throw e;
            }
            boolean contextLinksValid = response.contextLinks() == null || response.contextLinks().stream().allMatch(link -> link != null);
            boolean riskNotesValid = response.riskNotes() == null || response.riskNotes().stream().allMatch(note -> note != null);
            boolean summariesPresent = response.doctorSummary() != null && !response.doctorSummary().isBlank()
                    && response.patientExplanation() != null && !response.patientExplanation().isBlank();
            if (!summariesPresent || !contextLinksValid || !riskNotesValid) {
                ReportTrendException exception = new ReportTrendException(
                        ReportTrendErrorCode.CLOUD_RESPONSE_SCHEMA_MISMATCH,
                        "Cloud response schema mismatch"
                );
                step.fail(exception.errorCode().name(), exception.getMessage());
                throw exception;
            }
            step.success(Map.of(
                    "doctor_summary_present", true,
                    "patient_explanation_present", true,
                    "context_links_type", "array",
                    "risk_notes_type", "array"
            ));
            return response;
        }
    }

    private void storeSuccess(String analysisId, ReportTrendAnalysisRequest request, int reportCount, Object cloudPayload,
                              ReportTrendAnalysisResponse response, String modelName, String traceRunId) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RESULT_ENCRYPT_STORE, "encrypt and store report trend result", null,
                Map.of("analysis_id", analysisId))) {
            resultRepository.saveSuccess(analysisId, request.patientId(), request.normalizedReportType(), reportCount,
                    request.startDate(), request.endDate(), cloudPayload, response, modelName, CloudReportAnalysisClient.PROMPT_VERSION, traceRunId);
            step.success(Map.of("analysis_id", analysisId, "status", "SUCCESS"));
        }
    }

    private void recordFinal(ReportTrendAnalysisResponse response, List<TrendItem> trendItems) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.FINAL_REPORT_SUMMARY, "final report trend summary", null,
                Map.of("analysis_id", response.analysisId()))) {
            step.success(Map.of(
                    "doctor_summary_hash", traceRedactor.stableHash(response.doctorSummary()),
                    "patient_explanation_hash", traceRedactor.stableHash(response.patientExplanation()),
                    "trend_item_count", trendItems.size(),
                    "abnormal_count", response.abnormalItems().size()
            ));
        }
    }

    private ReportTrendAnalysisResponse toResponse(String analysisId, String traceRunId, CloudReportResponse cloudResponse,
                                                   List<TrendItem> trendItems, ContextUsed contextUsed) {
        List<LabIndicatorItem> abnormalItems = trendItems.stream()
                .filter(item -> item.latestAbnormalFlag() == AbnormalFlag.HIGH || item.latestAbnormalFlag() == AbnormalFlag.LOW)
                .map(item -> new LabIndicatorItem(item.name(), item.code(), item.name(), item.latestValue(), "", null, null, item.latestAbnormalFlag()))
                .toList();
        return new ReportTrendAnalysisResponse(
                analysisId,
                traceRunId,
                "SUCCESS",
                cloudResponse.doctorSummary(),
                cloudResponse.patientExplanation(),
                cloudResponse.contextualInterpretation() == null ? "" : cloudResponse.contextualInterpretation(),
                cloudResponse.contextLinks() == null ? List.of() : cloudResponse.contextLinks(),
                abnormalItems,
                trendItems,
                cloudResponse.followUpQuestions() == null ? List.of() : cloudResponse.followUpQuestions(),
                new Recommendation(cloudResponse.suggestedDepartment(), cloudResponse.suggestedAction()),
                contextUsed,
                null,
                null
        );
    }
}
