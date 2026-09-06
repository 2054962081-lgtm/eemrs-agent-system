package com.liu.eemrsagent.eval;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("eval")
public class ReportFixtureService {

    private final JdbcTemplate jdbcTemplate;

    public ReportFixtureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PreparedFixture prepare(ReportFixtureRequest request) {
        validate(request);
        ensureLabReportTable();
        cleanup(request.evalRunId(), request.caseId());
        List<String> reportTokens = new ArrayList<>();
        for (int index = 0; index < request.records().size(); index++) {
            ReportFixtureRequest.Record record = request.records().get(index);
            String token = token(request.evalRunId(), request.caseId(), record.recordId(), index);
            String payload = "plain:" + toReportText(record);
            jdbcTemplate.update("""
                    INSERT INTO tb_lab_report (
                        patient_id_hash_code, report_token, report_payload_cipher, department_cipher,
                        report_time_ope, report_type_cipher, image_cipher_url, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.patientId(),
                    token,
                    payload,
                    "plain:eval",
                    0,
                    "plain:" + normalizedReportType(request.reportType()),
                    null,
                    Timestamp.valueOf(toDateTime(record.date())),
                    Timestamp.valueOf(toDateTime(record.date()))
            );
            reportTokens.add(token);
        }
        return new PreparedFixture(request.evalRunId(), request.caseId(), request.patientId(), reportTokens.size(), reportTokens);
    }

    public int cleanup(String evalRunId, String caseId) {
        if (blank(evalRunId) || blank(caseId)) {
            return 0;
        }
        ensureLabReportTable();
        return jdbcTemplate.update("DELETE FROM tb_lab_report WHERE report_token LIKE ?", tokenPrefix(evalRunId, caseId) + "%");
    }

    private void ensureLabReportTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tb_lab_report (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    patient_id_hash_code VARCHAR(255) NOT NULL,
                    report_token VARCHAR(255) NOT NULL,
                    report_payload_cipher TEXT,
                    department_cipher VARCHAR(512),
                    report_time_ope DECIMAL(65, 0),
                    report_type_cipher VARCHAR(512),
                    image_cipher_url TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_lab_report_token (report_token),
                    KEY idx_lab_report_patient_hash (patient_id_hash_code),
                    KEY idx_lab_report_time_ope (report_time_ope)
                )
                """);
    }

    private void validate(ReportFixtureRequest request) {
        if (request == null || blank(request.evalRunId()) || blank(request.caseId()) || blank(request.patientId())) {
            throw new IllegalArgumentException("evalRunId, caseId and patientId are required");
        }
        if (request.records() == null || request.records().size() < 2) {
            throw new IllegalArgumentException("At least two report records are required for report trend fixture");
        }
    }

    private String toReportText(ReportFixtureRequest.Record record) {
        StringBuilder builder = new StringBuilder();
        builder.append(record.indicator()).append(": ").append(record.value());
        if (!blank(record.unit())) {
            builder.append(" ").append(record.unit());
        }
        if (Boolean.TRUE.equals(record.referenceRangeEvaluable())) {
            if (record.referenceLow() != null && record.referenceHigh() != null) {
                builder.append(" 参考 ").append(record.referenceLow()).append("-").append(record.referenceHigh());
            } else if (record.referenceLow() != null) {
                builder.append(" 参考 >").append(record.referenceLow());
            } else if (record.referenceHigh() != null) {
                builder.append(" 参考 <").append(record.referenceHigh());
            }
        }
        return builder.toString();
    }

    private String token(String evalRunId, String caseId, String recordId, int index) {
        String suffix = blank(recordId) ? String.valueOf(index) : recordId.replaceAll("[^A-Za-z0-9_-]", "_");
        return tokenPrefix(evalRunId, caseId) + suffix;
    }

    private String tokenPrefix(String evalRunId, String caseId) {
        return "eval_" + sanitize(evalRunId) + "_" + sanitize(caseId) + "_";
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String normalizedReportType(String reportType) {
        return blank(reportType) ? "LAB" : reportType.trim().toUpperCase();
    }

    private LocalDateTime toDateTime(LocalDate date) {
        return (date == null ? LocalDate.now() : date).atStartOfDay();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PreparedFixture(
            String evalRunId,
            String caseId,
            String patientId,
            int recordCount,
            List<String> reportTokens
    ) {
    }
}
