package com.liu.eemrsagent.reporttrend;

import com.liu.eemrsagent.trace.AgentTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import com.liu.eemrsagent.trace.TraceStepScope;
import com.liu.eemrsagent.trace.TraceStepType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportTrendContextService {
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "发热", "咳嗽", "咽痛", "乏力", "胸痛", "胸闷", "呼吸困难", "头痛", "腹痛", "腹泻", "呕吐", "尿痛", "皮疹"
    );
    private static final List<String> RED_FLAG_KEYWORDS = List.of("高热", "呼吸困难", "胸痛", "意识障碍", "抽搐", "大出血");
    private static final List<String> CHRONIC_DISEASE_KEYWORDS = List.of(
            "糖尿病", "高血压", "冠心病", "慢性肾病", "哮喘", "慢阻肺", "肝炎", "肿瘤"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AgentTraceRecorder traceRecorder;
    private final TraceRedactor traceRedactor;

    public ReportTrendContextService(JdbcTemplate jdbcTemplate, AgentTraceRecorder traceRecorder, TraceRedactor traceRedactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.traceRecorder = traceRecorder;
        this.traceRedactor = traceRedactor;
    }

    public ReportTrendContext load(ReportTrendAnalysisRequest request) {
        if (!request.shouldIncludePreconsultationContext() && !request.shouldIncludeLongTermHealthContext()) {
            return ReportTrendContext.empty();
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CONTEXT_QUERY, "query report trend context", null,
                Map.of(
                        "patient_hash", traceRedactor.stableHash(request.patientId()),
                        "has_session_id", request.sessionId() != null && !request.sessionId().isBlank(),
                        "include_preconsultation", request.shouldIncludePreconsultationContext(),
                        "include_long_term_health", request.shouldIncludeLongTermHealthContext()
                ))) {
            DraftContext draft = request.shouldIncludePreconsultationContext() ? loadDraftContext(request) : DraftContext.empty();
            LongTermContext longTerm = request.shouldIncludeLongTermHealthContext() ? loadLongTermContext(request.patientId()) : LongTermContext.empty();
            ReportTrendContext context = fuse(draft, longTerm);
            step.success(Map.of(
                    "context_used", context.contextUsed(),
                    "symptom_tag_count", context.symptomTags().size(),
                    "chronic_disease_tag_count", context.chronicDiseaseTags().size(),
                    "recommended_department", safe(context.recommendedDepartment())
            ));
            return context;
        } catch (RuntimeException e) {
            return ReportTrendContext.empty();
        }
    }

    private DraftContext loadDraftContext(ReportTrendAnalysisRequest request) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.PRECONSULTATION_CONTEXT_LOAD, "load preconsultation context", null,
                Map.of("patient_hash", traceRedactor.stableHash(request.patientId()), "session_hash", safeHash(request.sessionId())))) {
            if (!tableExists("agent_medical_record_draft")) {
                step.skip(Map.of("context_available", false));
                return DraftContext.empty();
            }
            List<Map<String, Object>> rows = queryDraftRows(request);
            if (rows.isEmpty()) {
                step.success(Map.of("context_available", false));
                return DraftContext.empty();
            }
            Map<String, Object> row = rows.get(0);
            String combined = safe(row.get("chief_complaint")) + "\n"
                    + safe(row.get("present_illness_history")) + "\n"
                    + safe(row.get("consultation_summary")) + "\n"
                    + safe(row.get("record_json"));
            List<String> symptoms = tags(combined, SYMPTOM_KEYWORDS);
            List<String> redFlags = tags(combined, RED_FLAG_KEYWORDS);
            String recommendedDepartment = safe(row.get("recommended_department"));
            step.success(Map.of(
                    "context_available", true,
                    "symptom_tag_count", symptoms.size(),
                    "red_flag_count", redFlags.size(),
                    "recommended_department", recommendedDepartment
            ));
            return new DraftContext(true, symptoms, redFlags, recommendedDepartment, !recommendedDepartment.isBlank(), durationBucket(combined));
        }
    }

    private List<Map<String, Object>> queryDraftRows(ReportTrendAnalysisRequest request) {
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            List<Map<String, Object>> bySession = jdbcTemplate.queryForList("""
                    SELECT chief_complaint, present_illness_history, recommended_department, consultation_summary, record_json
                    FROM agent_medical_record_draft
                    WHERE session_id = ?
                      AND deleted = 0
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, request.sessionId());
            if (!bySession.isEmpty()) {
                return bySession;
            }
        }
        return jdbcTemplate.queryForList("""
                SELECT chief_complaint, present_illness_history, recommended_department, consultation_summary, record_json
                FROM agent_medical_record_draft
                WHERE (patient_id_number = ? OR CAST(patient_id AS CHAR) = ?)
                  AND deleted = 0
                ORDER BY created_at DESC
                LIMIT 1
                """, request.patientId(), request.patientId());
    }

    private LongTermContext loadLongTermContext(String patientId) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.LONG_TERM_HEALTH_CONTEXT_LOAD, "load long term health context", null,
                Map.of("patient_hash", traceRedactor.stableHash(patientId)))) {
            if (!tableExists("user_long_term_memory")) {
                step.skip(Map.of("context_available", false));
                return LongTermContext.empty();
            }
            List<String> hashes = patientHashCandidates(patientId);
            String placeholders = String.join(",", hashes.stream().map(ignored -> "?").toList());
            List<String> values = jdbcTemplate.queryForList("""
                    SELECT CONCAT(COALESCE(memory_key, ''), ' ', COALESCE(memory_value, ''))
                    FROM user_long_term_memory
                    WHERE active = 1
                      AND memory_type IN ('past_history', 'chronic_disease', 'allergy', 'special_status', 'medication')
                      AND patient_id_hash IN (%s)
                    ORDER BY updated_at DESC
                    LIMIT 20
                    """.formatted(placeholders), String.class, hashes.toArray());
            if (values.isEmpty()) {
                step.success(Map.of("context_available", false));
                return LongTermContext.empty();
            }
            String joined = String.join("\n", values);
            List<String> chronic = tags(joined, CHRONIC_DISEASE_KEYWORDS);
            boolean allergyKnown = joined.contains("过敏");
            step.success(Map.of("context_available", true, "chronic_disease_tag_count", chronic.size(), "allergy_known", allergyKnown));
            return new LongTermContext(true, chronic, allergyKnown, tags(joined, List.of("孕", "老人", "儿童", "免疫低下")));
        }
    }

    private ReportTrendContext fuse(DraftContext draft, LongTermContext longTerm) {
        try (TraceStepScope ignored = traceRecorder.startStep(TraceStepType.TRIAGE_CONTEXT_LOAD, "load triage context", null,
                Map.of("context_available", draft.available(), "recommended_department", draft.recommendedDepartment()))) {
            ignored.success(Map.of("registration_suggested", draft.registrationSuggested()));
        }
        try (TraceStepScope ignored = traceRecorder.startStep(TraceStepType.CONTEXT_REDACT, "redact context summary", null, null)) {
            ignored.success(Map.of("redacted", true, "raw_text_saved", false));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.CONTEXT_FUSION, "fuse report trend context", null, null)) {
            Map<String, Object> symptom = new LinkedHashMap<>();
            symptom.put("available", draft.available());
            symptom.put("chief_complaint_tags", draft.symptomTags());
            symptom.put("symptom_duration_bucket", draft.durationBucket());
            symptom.put("red_flag_tags", draft.redFlagTags());
            symptom.put("current_symptom_category", symptomCategory(draft.symptomTags()));

            Map<String, Object> health = new LinkedHashMap<>();
            health.put("available", longTerm.available());
            health.put("chronic_disease_tags", longTerm.chronicDiseaseTags());
            health.put("allergy_known", longTerm.allergyKnown());
            health.put("special_population_tags", longTerm.specialPopulationTags());

            Map<String, Object> triage = new LinkedHashMap<>();
            triage.put("available", draft.available() && !draft.recommendedDepartment().isBlank());
            triage.put("recommended_department", draft.recommendedDepartment());
            triage.put("registration_suggested", draft.registrationSuggested());

            ContextUsed used = new ContextUsed(draft.available(), longTerm.available(), Boolean.TRUE.equals(triage.get("available")));
            ReportTrendContext context = new ReportTrendContext(symptom, health, triage, used,
                    draft.symptomTags(), longTerm.chronicDiseaseTags(), draft.recommendedDepartment());
            step.success(Map.of(
                    "context_payload_hash", traceRedactor.stableHash(context.toString()),
                    "context_used", used
            ));
            return context;
        }
    }

    private List<String> patientHashCandidates(String patientId) {
        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        hashes.add(patientId);
        hashes.add(traceRedactor.stableHash(patientId));
        hashes.add(traceRedactor.stableHash(patientId).replace("sha256:", ""));
        hashes.add("patient:" + traceRedactor.stableHash(patientId).replace("sha256:", ""));
        return new ArrayList<>(hashes);
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """, Integer.class, tableName);
            return count != null && count > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private List<String> tags(String text, List<String> candidates) {
        String source = text == null ? "" : text;
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private String durationBucket(String text) {
        String source = text == null ? "" : text;
        if (source.matches("(?s).*([1-3一二三两])\\s*(天|日).*")) {
            return "1-3天";
        }
        if (source.matches("(?s).*([4-7四五六七])\\s*(天|日).*")) {
            return "4-7天";
        }
        if (source.contains("周") || source.contains("月")) {
            return "超过1周";
        }
        return "";
    }

    private String symptomCategory(List<String> symptoms) {
        if (symptoms.stream().anyMatch(item -> item.contains("咳") || item.contains("咽") || item.contains("呼吸") || item.contains("胸"))) {
            return "respiratory";
        }
        if (symptoms.stream().anyMatch(item -> item.contains("腹") || item.contains("泻") || item.contains("吐"))) {
            return "digestive";
        }
        return symptoms.isEmpty() ? "" : "general";
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeHash(String value) {
        String hash = traceRedactor.stableHash(value);
        return hash == null ? "" : hash;
    }

    private record DraftContext(boolean available, List<String> symptomTags, List<String> redFlagTags,
                                String recommendedDepartment, boolean registrationSuggested, String durationBucket) {
        static DraftContext empty() {
            return new DraftContext(false, List.of(), List.of(), "", false, "");
        }
    }

    private record LongTermContext(boolean available, List<String> chronicDiseaseTags, boolean allergyKnown,
                                   List<String> specialPopulationTags) {
        static LongTermContext empty() {
            return new LongTermContext(false, List.of(), false, List.of());
        }
    }
}
