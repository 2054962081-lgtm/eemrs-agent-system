package com.liu.eemrsagent.medicalrecord;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordDraftSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public MedicalRecordDraftSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS agent_medical_record_draft (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        patient_id BIGINT NULL,
                        patient_id_number VARCHAR(64) NULL,
                        session_id VARCHAR(100) NULL,
                        consultation_mode VARCHAR(20) NOT NULL DEFAULT 'deep',
                        source_type VARCHAR(50) NOT NULL DEFAULT 'DEEP_PRE_CONSULTATION',
                        chief_complaint VARCHAR(1000) NULL,
                        present_illness_history TEXT NULL,
                        recommended_department VARCHAR(255) NULL,
                        urgency VARCHAR(50) NULL,
                        consultation_summary LONGTEXT NULL,
                        record_json LONGTEXT NOT NULL,
                        raw_model_reply LONGTEXT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                        created_by VARCHAR(100) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        INDEX idx_agent_draft_patient_mode_deleted_created (patient_id, consultation_mode, deleted, created_at),
                        INDEX idx_agent_draft_patient_id_number_created (patient_id_number, consultation_mode, deleted, created_at),
                        INDEX idx_agent_draft_session (session_id)
                    )
                    """);
            addColumnIfMissing("agent_medical_record_draft", "patient_id_number", "VARCHAR(64) NULL");
            addIndexIfMissing(
                    "agent_medical_record_draft",
                    "idx_agent_draft_patient_id_number_created",
                    "CREATE INDEX idx_agent_draft_patient_id_number_created ON agent_medical_record_draft (patient_id_number, consultation_mode, deleted, created_at)"
            );
        } catch (Exception ignored) {
            // Health check reports database status; pre-consultation should still start if MySQL is temporarily unavailable.
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
        }
    }
}
