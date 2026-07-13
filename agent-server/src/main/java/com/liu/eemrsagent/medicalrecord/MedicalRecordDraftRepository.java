package com.liu.eemrsagent.medicalrecord;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MedicalRecordDraftRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalRecordDraftRow> rowMapper = new MedicalRecordDraftRowMapper();
    private final RowMapper<MedicalRecordDraftAuditLog> auditMapper = new MedicalRecordDraftAuditMapper();

    public MedicalRecordDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(MedicalRecordDraftEntity entity) {
        String sql = """
                INSERT INTO agent_medical_record_draft (
                    patient_id, patient_id_number, session_id, consultation_mode, source_type,
                    chief_complaint, present_illness_history, recommended_department, urgency,
                    consultation_summary, record_json, ai_record_json, edited_record_json, raw_model_reply,
                    status, model_name, prompt_version, trace_id, created_by, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, entity.patientId());
            ps.setString(2, entity.patientIdNumber());
            ps.setString(3, entity.sessionId());
            ps.setString(4, entity.consultationMode());
            ps.setString(5, entity.sourceType());
            ps.setString(6, entity.chiefComplaint());
            ps.setString(7, entity.presentIllnessHistory());
            ps.setString(8, entity.recommendedDepartment());
            ps.setString(9, entity.urgency());
            ps.setString(10, entity.consultationSummary());
            ps.setString(11, entity.recordJson());
            ps.setString(12, entity.aiRecordJson());
            ps.setString(13, entity.editedRecordJson());
            ps.setString(14, entity.rawModelReply());
            ps.setString(15, entity.status());
            ps.setString(16, entity.modelName());
            ps.setString(17, entity.promptVersion());
            ps.setString(18, entity.traceId());
            ps.setString(19, entity.createdBy());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to get generated draft id");
        }
        return key.longValue();
    }

    public Optional<MedicalRecordDraftRow> findLatestByPatientId(Long patientId) {
        return jdbcTemplate.query(baseSelect() + """
                  AND patient_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, rowMapper, patientId).stream().findFirst();
    }

    public Optional<MedicalRecordDraftRow> findLatestByPatientIdNumber(String patientIdNumber) {
        return jdbcTemplate.query(baseSelect() + """
                  AND patient_id_number = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, rowMapper, patientIdNumber).stream().findFirst();
    }

    public Optional<MedicalRecordDraftRow> findById(Long id) {
        return jdbcTemplate.query(baseSelect() + """
                  AND id = ?
                LIMIT 1
                """, rowMapper, id).stream().findFirst();
    }

    public void lockForDoctorIfUnassigned(Long id, String doctorIdNumber) {
        jdbcTemplate.update("""
                UPDATE agent_medical_record_draft
                SET doctor_id_number = ?,
                    status = CASE WHEN status = 'GENERATED' THEN 'REVIEWING' ELSE status END,
                    first_reviewed_at = COALESCE(first_reviewed_at, CURRENT_TIMESTAMP)
                WHERE id = ?
                  AND deleted = 0
                  AND (doctor_id_number IS NULL OR doctor_id_number = '')
                """, doctorIdNumber, id);
    }

    public int saveEdit(Long id, String doctorIdNumber, String editedJson, String commentTraceId) {
        return jdbcTemplate.update("""
                UPDATE agent_medical_record_draft
                SET edited_record_json = ?,
                    record_json = ?,
                    doctor_id_number = COALESCE(NULLIF(doctor_id_number, ''), ?),
                    status = CASE WHEN status = 'GENERATED' THEN 'REVIEWING' ELSE status END,
                    first_reviewed_at = COALESCE(first_reviewed_at, CURRENT_TIMESTAMP),
                    trace_id = COALESCE(NULLIF(trace_id, ''), ?)
                WHERE id = ?
                  AND deleted = 0
                  AND status IN ('GENERATED', 'REVIEWING')
                  AND (doctor_id_number IS NULL OR doctor_id_number = '' OR doctor_id_number = ?)
                """, editedJson, editedJson, doctorIdNumber, commentTraceId, id, doctorIdNumber);
    }

    public int transition(Long id, String doctorIdNumber, MedicalRecordDraftStatus from, MedicalRecordDraftStatus to,
                          String editedJson, String traceId) {
        return jdbcTemplate.update("""
                UPDATE agent_medical_record_draft
                SET status = ?,
                    edited_record_json = COALESCE(?, edited_record_json, ai_record_json, record_json),
                    record_json = COALESCE(?, edited_record_json, ai_record_json, record_json),
                    doctor_id_number = COALESCE(NULLIF(doctor_id_number, ''), ?),
                    first_reviewed_at = COALESCE(first_reviewed_at, CURRENT_TIMESTAMP),
                    completed_at = CASE WHEN ? IN ('ACCEPTED','PARTIALLY_ACCEPTED','REJECTED') THEN COALESCE(completed_at, CURRENT_TIMESTAMP) ELSE completed_at END,
                    trace_id = COALESCE(NULLIF(trace_id, ''), ?)
                WHERE id = ?
                  AND deleted = 0
                  AND status = ?
                  AND (doctor_id_number IS NULL OR doctor_id_number = '' OR doctor_id_number = ?)
                """, to.name(), editedJson, editedJson, doctorIdNumber, to.name(), traceId, id, from.name(), doctorIdNumber);
    }

    public int markApplied(Long id, String doctorIdNumber, String appliedRecordHash, String traceId) {
        return jdbcTemplate.update("""
                UPDATE agent_medical_record_draft
                SET status = 'APPLIED',
                    applied_at = COALESCE(applied_at, CURRENT_TIMESTAMP),
                    applied_record_hash = COALESCE(applied_record_hash, ?),
                    trace_id = COALESCE(NULLIF(trace_id, ''), ?)
                WHERE id = ?
                  AND deleted = 0
                  AND status IN ('ACCEPTED', 'PARTIALLY_ACCEPTED')
                  AND doctor_id_number = ?
                """, appliedRecordHash, traceId, id, doctorIdNumber);
    }

    public void insertAudit(Long draftId, String doctorIdNumber, MedicalRecordDraftAction action,
                            String beforeJson, String afterJson, String rejectReason, String comment, String traceId) {
        jdbcTemplate.update("""
                INSERT INTO agent_medical_record_draft_audit (
                    draft_id, doctor_id_number, action, before_json, after_json, reject_reason, comment, trace_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, draftId, doctorIdNumber, action.name(), beforeJson, afterJson, rejectReason, comment, traceId);
    }

    public List<MedicalRecordDraftAuditLog> findAuditLogs(Long draftId) {
        return jdbcTemplate.query("""
                SELECT id, draft_id, doctor_id_number, action, before_json, after_json,
                       reject_reason, comment, action_time, trace_id
                FROM agent_medical_record_draft_audit
                WHERE draft_id = ?
                ORDER BY action_time ASC, id ASC
                """, auditMapper, draftId);
    }

    private String baseSelect() {
        return """
                SELECT
                    id, patient_id, patient_id_number, doctor_id_number, appointment_id, session_id,
                    consultation_mode, source_type, chief_complaint, present_illness_history,
                    recommended_department, urgency, consultation_summary, record_json, ai_record_json,
                    edited_record_json, status, model_name, prompt_version, trace_id, created_at,
                    updated_at, first_reviewed_at, completed_at, applied_at, applied_record_hash
                FROM agent_medical_record_draft
                WHERE consultation_mode = 'deep'
                  AND deleted = 0
                """;
    }

    public record MedicalRecordDraftRow(
            Long id,
            Long patientId,
            String patientIdNumber,
            String doctorIdNumber,
            String appointmentId,
            String sessionId,
            String consultationMode,
            String sourceType,
            String chiefComplaint,
            String presentIllnessHistory,
            String recommendedDepartment,
            String urgency,
            String consultationSummary,
            String recordJson,
            String aiRecordJson,
            String editedRecordJson,
            String status,
            String modelName,
            String promptVersion,
            String traceId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime firstReviewedAt,
            LocalDateTime completedAt,
            LocalDateTime appliedAt,
            String appliedRecordHash
    ) {
    }

    private static class MedicalRecordDraftRowMapper implements RowMapper<MedicalRecordDraftRow> {
        @Override
        public MedicalRecordDraftRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalRecordDraftRow(
                    rs.getLong("id"),
                    nullableLong(rs, "patient_id"),
                    safeString(rs, "patient_id_number"),
                    safeString(rs, "doctor_id_number"),
                    safeString(rs, "appointment_id"),
                    safeString(rs, "session_id"),
                    safeString(rs, "consultation_mode"),
                    safeString(rs, "source_type"),
                    safeString(rs, "chief_complaint"),
                    safeString(rs, "present_illness_history"),
                    safeString(rs, "recommended_department"),
                    safeString(rs, "urgency"),
                    safeString(rs, "consultation_summary"),
                    safeString(rs, "record_json"),
                    safeString(rs, "ai_record_json"),
                    safeString(rs, "edited_record_json"),
                    safeString(rs, "status"),
                    safeString(rs, "model_name"),
                    safeString(rs, "prompt_version"),
                    safeString(rs, "trace_id"),
                    timestamp(rs, "created_at"),
                    timestamp(rs, "updated_at"),
                    timestamp(rs, "first_reviewed_at"),
                    timestamp(rs, "completed_at"),
                    timestamp(rs, "applied_at"),
                    safeString(rs, "applied_record_hash")
            );
        }

        private Long nullableLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }

    private static class MedicalRecordDraftAuditMapper implements RowMapper<MedicalRecordDraftAuditLog> {
        @Override
        public MedicalRecordDraftAuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalRecordDraftAuditLog(
                    rs.getLong("id"),
                    rs.getLong("draft_id"),
                    safeString(rs, "doctor_id_number"),
                    MedicalRecordDraftAction.valueOf(safeString(rs, "action")),
                    safeString(rs, "before_json"),
                    safeString(rs, "after_json"),
                    safeString(rs, "reject_reason"),
                    safeString(rs, "comment"),
                    timestamp(rs, "action_time"),
                    safeString(rs, "trace_id")
            );
        }
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static String safeString(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }
}
