package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.common.ApiResponse;
import com.liu.eemrsagent.llm.LlmProperties;
import com.liu.eemrsagent.reporttrend.ReportTrendAnalysisRequest;
import com.liu.eemrsagent.reporttrend.ReportTrendAnalysisResponse;
import com.liu.eemrsagent.reporttrend.ReportTrendAnalysisService;
import com.liu.eemrsagent.reporttrend.ReportAnalysisResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final PreConsultationService preConsultationService;
    private final ReportTrendAnalysisService reportTrendAnalysisService;
    private final ReportAnalysisResultRepository reportAnalysisResultRepository;
    private final LlmProperties llmProperties;
    private final JdbcTemplate jdbcTemplate;

    public AgentController(
            PreConsultationService preConsultationService,
            ReportTrendAnalysisService reportTrendAnalysisService,
            ReportAnalysisResultRepository reportAnalysisResultRepository,
            LlmProperties llmProperties,
            JdbcTemplate jdbcTemplate
    ) {
        this.preConsultationService = preConsultationService;
        this.reportTrendAnalysisService = reportTrendAnalysisService;
        this.reportAnalysisResultRepository = reportAnalysisResultRepository;
        this.llmProperties = llmProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "time", OffsetDateTime.now().toString(),
                "llm", Map.of(
                        "defaultProvider", llmProperties.getDefaultProvider(),
                        "preConsultationProvider", llmProperties.getRouting().getPreConsultationProvider(),
                        "medicalRecordDraftProvider", llmProperties.getRouting().getMedicalRecordDraftProvider(),
                        "deepseekConfigured", llmProperties.getDeepseek().getApiKey() != null
                                && !llmProperties.getDeepseek().getApiKey().isBlank(),
                        "ollamaEnabled", llmProperties.getOllama().isEnabled()
                ),
                "database", databaseStatus()
        ));
    }

    @PostMapping("/pre-consultation")
    public ApiResponse<PreConsultationResponse> preConsultation(@RequestBody PreConsultationRequest request) {
        return ApiResponse.ok(preConsultationService.ask(request));
    }

    @PostMapping("/report-trend/analyze")
    public ApiResponse<ReportTrendAnalysisResponse> reportTrendAnalyze(@RequestBody ReportTrendAnalysisRequest request) {
        return ApiResponse.ok(reportTrendAnalysisService.analyze(request));
    }

    @GetMapping("/report-trend/{analysisId}")
    public ApiResponse<ReportTrendAnalysisResponse> getReportTrendAnalysis(@PathVariable String analysisId) {
        return reportAnalysisResultRepository.findResponseByAnalysisId(analysisId)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("REPORT_TREND_ANALYSIS_NOT_FOUND"));
    }

    private Map<String, Object> databaseStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer draftTableExists = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = 'agent_medical_record_draft'
                    """, Integer.class);
            return Map.of(
                    "connected", true,
                    "medicalRecordDraftTable", draftTableExists != null && draftTableExists > 0
            );
        } catch (Exception e) {
            return Map.of(
                    "connected", false,
                    "medicalRecordDraftTable", false,
                    "error", "数据库连接失败，请检查 AGENT_DB_URL、AGENT_DB_USERNAME、AGENT_DB_PASSWORD。"
            );
        }
    }
}
