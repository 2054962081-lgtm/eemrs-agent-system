package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CloudPayloadPrivacyGuard {
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "patientid", "doctorid", "visitid", "name", "idcard", "phone", "address",
            "rawreporttext", "rawcomplainttext", "rawdialogue", "rawmedicalrecord", "ciphertext", "key", "token", "cookie", "authorization", "apikey"
    );
    private static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of(
            "analysis_task", "report_type", "date_range", "coarse_patient_context", "normalized_items",
            "trend_results", "abnormal_summary", "symptom_context_summary", "health_context_summary",
            "triage_context_summary", "output_requirements"
    );
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final ObjectMapper objectMapper;

    public CloudPayloadPrivacyGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void assertSafe(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_PAYLOAD_PRIVACY_VIOLATION, "Cloud payload is not serializable", e);
        }
        String normalized = json.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        for (String key : FORBIDDEN_KEYS) {
            if (normalized.contains("\"" + key + "\"") || normalized.contains(key + ":")) {
                throw new ReportTrendException(ReportTrendErrorCode.CLOUD_PAYLOAD_PRIVACY_VIOLATION, "Forbidden cloud payload field: " + key);
            }
        }
        if (payload instanceof java.util.Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key == null || !ALLOWED_TOP_LEVEL_KEYS.contains(String.valueOf(key))) {
                    throw new ReportTrendException(ReportTrendErrorCode.CLOUD_PAYLOAD_PRIVACY_VIOLATION, "Cloud payload top-level field is not allowlisted: " + key);
                }
            }
        }
        if (ID_CARD.matcher(json).find() || MOBILE.matcher(json).find()) {
            throw new ReportTrendException(ReportTrendErrorCode.CLOUD_PAYLOAD_PRIVACY_VIOLATION, "Cloud payload contains direct identifier pattern");
        }
    }
}
