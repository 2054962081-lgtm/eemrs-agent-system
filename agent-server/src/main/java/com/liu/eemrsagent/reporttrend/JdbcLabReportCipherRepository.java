package com.liu.eemrsagent.reporttrend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class JdbcLabReportCipherRepository implements LabReportCipherRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLabReportCipherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<EncryptedLabReportRecord> findEncryptedReports(String patientId, String reportType, LocalDate startDate, LocalDate endDate) {
        if (!tableExists("tb_lab_report")) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, report_payload_cipher, report_type_cipher, created_at
                    FROM tb_lab_report
                    WHERE patient_id_hash_code = ?
                    ORDER BY created_at ASC
                    """, (rs, rowNum) -> new EncryptedLabReportRecord(
                    String.valueOf(rs.getLong("id")),
                    rs.getTimestamp("created_at") == null ? LocalDate.now() : rs.getTimestamp("created_at").toLocalDateTime().toLocalDate(),
                    reportType,
                    rs.getString("report_payload_cipher")
            ), patientId).stream()
                    .filter(report -> !report.reportDate().isBefore(startDate) && !report.reportDate().isAfter(endDate))
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
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
}
