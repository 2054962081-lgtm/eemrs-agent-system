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
                        ai_record_json LONGTEXT NULL,
                        edited_record_json LONGTEXT NULL,
                        raw_model_reply LONGTEXT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
                        doctor_id_number VARCHAR(64) NULL,
                        appointment_id VARCHAR(100) NULL,
                        model_name VARCHAR(100) NULL,
                        prompt_version VARCHAR(100) NULL,
                        trace_id VARCHAR(100) NULL,
                        first_reviewed_at DATETIME NULL,
                        completed_at DATETIME NULL,
                        applied_at DATETIME NULL,
                        applied_record_hash VARCHAR(128) NULL,
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
            addColumnIfMissing("agent_medical_record_draft", "doctor_id_number", "VARCHAR(64) NULL");
            addColumnIfMissing("agent_medical_record_draft", "appointment_id", "VARCHAR(100) NULL");
            addColumnIfMissing("agent_medical_record_draft", "ai_record_json", "LONGTEXT NULL");
            addColumnIfMissing("agent_medical_record_draft", "edited_record_json", "LONGTEXT NULL");
            addColumnIfMissing("agent_medical_record_draft", "model_name", "VARCHAR(100) NULL");
            addColumnIfMissing("agent_medical_record_draft", "prompt_version", "VARCHAR(100) NULL");
            addColumnIfMissing("agent_medical_record_draft", "trace_id", "VARCHAR(100) NULL");
            addColumnIfMissing("agent_medical_record_draft", "first_reviewed_at", "DATETIME NULL");
            addColumnIfMissing("agent_medical_record_draft", "completed_at", "DATETIME NULL");
            addColumnIfMissing("agent_medical_record_draft", "applied_at", "DATETIME NULL");
            addColumnIfMissing("agent_medical_record_draft", "applied_record_hash", "VARCHAR(128) NULL");
            jdbcTemplate.update("""
                    UPDATE agent_medical_record_draft
                    SET status = 'GENERATED'
                    WHERE status = 'DRAFT'
                    """);
            jdbcTemplate.update("""
                    UPDATE agent_medical_record_draft
                    SET ai_record_json = record_json
                    WHERE ai_record_json IS NULL
                    """);
            jdbcTemplate.update("""
                    UPDATE agent_medical_record_draft
                    SET edited_record_json = record_json
                    WHERE edited_record_json IS NULL
                    """);
            addIndexIfMissing(
                    "agent_medical_record_draft",
                    "idx_agent_draft_patient_id_number_created",
                    "CREATE INDEX idx_agent_draft_patient_id_number_created ON agent_medical_record_draft (patient_id_number, consultation_mode, deleted, created_at)"
            );
            addIndexIfMissing(
                    "agent_medical_record_draft",
                    "idx_agent_draft_doctor_status_created",
                    "CREATE INDEX idx_agent_draft_doctor_status_created ON agent_medical_record_draft (doctor_id_number, status, created_at)"
            );
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS agent_medical_record_draft_audit (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        draft_id BIGINT NOT NULL,
                        doctor_id_number VARCHAR(64) NOT NULL,
                        action VARCHAR(40) NOT NULL,
                        before_json LONGTEXT NULL,
                        after_json LONGTEXT NULL,
                        reject_reason VARCHAR(1000) NULL,
                        comment VARCHAR(1000) NULL,
                        action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        trace_id VARCHAR(100) NULL,
                        INDEX idx_agent_draft_audit_draft_time (draft_id, action_time),
                        INDEX idx_agent_draft_audit_doctor_action (doctor_id_number, action, action_time)
                    )
                    """);
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
