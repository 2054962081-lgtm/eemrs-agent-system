package com.liu.eemrsagent.reporttrend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.trace.TraceRedactor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

@Repository
public class ReportAnalysisResultRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TraceRedactor traceRedactor;
    private final SecureRandom secureRandom = new SecureRandom();

    public ReportAnalysisResultRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TraceRedactor traceRedactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceRedactor = traceRedactor;
    }

    public void saveSuccess(String analysisId, String patientId, String reportType, int reportCount,
                            LocalDate startDate, LocalDate endDate, Object cloudPayload, ReportTrendAnalysisResponse response,
                            String modelName, String promptVersion, String traceRunId) {
        String resultCipher = encryptJson(response);
        String summary = response.doctorSummary() == null ? "" : response.doctorSummary();
        jdbcTemplate.update("""
                INSERT INTO lab_report_analysis_result (
                    analysis_id, patient_id_hash, report_type, report_count, date_range_start, date_range_end,
                    input_payload_hash, cloud_payload_hash, result_ciphertext, result_summary, model_name,
                    prompt_version, status, trace_run_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                analysisId,
                traceRedactor.stableHash(patientId),
                reportType,
                reportCount,
                startDate,
                endDate,
                traceRedactor.stableHash(patientId + ":" + reportType + ":" + startDate + ":" + endDate),
                traceRedactor.stableHash(safeJson(cloudPayload)),
                resultCipher,
                summary.length() > 1000 ? summary.substring(0, 1000) : summary,
                modelName,
                promptVersion,
                "SUCCESS",
                traceRunId);
    }

    public void saveFailure(String analysisId, String patientId, String reportType, ReportTrendErrorCode errorCode, String errorMessage, String traceRunId) {
        jdbcTemplate.update("""
                INSERT INTO lab_report_analysis_result (
                    analysis_id, patient_id_hash, report_type, report_count, result_ciphertext, result_summary,
                    status, error_code, error_message, trace_run_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                analysisId,
                traceRedactor.stableHash(patientId),
                reportType,
                0,
                encryptJson(errorCode.name()),
                "",
                "FAILED",
                errorCode.name(),
                errorMessage == null ? "" : errorMessage.substring(0, Math.min(errorMessage.length(), 500)),
                traceRunId);
    }

    public Optional<ReportTrendAnalysisResponse> findResponseByAnalysisId(String analysisId) {
        try {
            return jdbcTemplate.query("""
                    SELECT result_ciphertext
                    FROM lab_report_analysis_result
                    WHERE analysis_id = ?
                    LIMIT 1
                    """, (rs, rowNum) -> decryptJson(rs.getString("result_ciphertext")), analysisId).stream().findFirst();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String encryptJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(localResultKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return "aes-gcm:" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new ReportTrendException(ReportTrendErrorCode.RESULT_ENCRYPT_FAILED, "Failed to encrypt result", e);
        }
    }

    private byte[] localResultKey() throws Exception {
        String secret = System.getenv("REPORT_TREND_RESULT_KEY");
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("REPORT_TREND_RESULT_KEY", "eemrs-agent-report-trend-local-dev-key");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }

    private ReportTrendAnalysisResponse decryptJson(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith("aes-gcm:")) {
                throw new IllegalArgumentException("Unsupported result ciphertext");
            }
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring("aes-gcm:".length()));
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(localResultKey(), "AES"), new GCMParameterSpec(128, iv));
            String json = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, ReportTrendAnalysisResponse.class);
        } catch (Exception e) {
            throw new ReportTrendException(ReportTrendErrorCode.RESULT_ENCRYPT_FAILED, "Failed to decrypt result", e);
        }
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
