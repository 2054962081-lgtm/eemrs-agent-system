package com.liu.eemrsagent.reporttrend;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportTrendSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public ReportTrendSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS lab_report_analysis_result (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        analysis_id VARCHAR(80) NOT NULL UNIQUE,
                        patient_id_hash VARCHAR(128) NULL,
                        report_type VARCHAR(50) NULL,
                        report_count INT NOT NULL DEFAULT 0,
                        date_range_start DATE NULL,
                        date_range_end DATE NULL,
                        input_payload_hash VARCHAR(128) NULL,
                        cloud_payload_hash VARCHAR(128) NULL,
                        result_ciphertext LONGTEXT NULL,
                        result_summary VARCHAR(1200) NULL,
                        model_name VARCHAR(120) NULL,
                        prompt_version VARCHAR(80) NULL,
                        status VARCHAR(30) NOT NULL,
                        error_code VARCHAR(80) NULL,
                        error_message VARCHAR(600) NULL,
                        trace_run_id VARCHAR(80) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_report_analysis_patient_hash (patient_id_hash),
                        INDEX idx_report_analysis_trace_run (trace_run_id)
                    )
                    """);
        } catch (RuntimeException ignored) {
            // Health check surfaces database problems; the service can still start for mock tests.
        }
    }
}
