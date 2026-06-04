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
import java.util.Optional;

@Repository
public class MedicalRecordDraftRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalRecordDraftRow> rowMapper = new MedicalRecordDraftRowMapper();

    public MedicalRecordDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(MedicalRecordDraftEntity entity) {
        String sql = """
                INSERT INTO agent_medical_record_draft (
                    patient_id,
                    patient_id_number,
                    session_id,
                    consultation_mode,
                    source_type,
                    chief_complaint,
                    present_illness_history,
                    recommended_department,
                    urgency,
                    consultation_summary,
                    record_json,
                    raw_model_reply,
                    status,
                    created_by,
                    deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
            ps.setString(12, entity.rawModelReply());
            ps.setString(13, entity.status());
            ps.setString(14, entity.createdBy());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to get generated draft id");
        }
        return key.longValue();
    }

    public Optional<MedicalRecordDraftRow> findLatestByPatientId(Long patientId) {
        String sql = """
                SELECT
                    id,
                    patient_id,
                    patient_id_number,
                    session_id,
                    consultation_mode,
                    source_type,
                    chief_complaint,
                    present_illness_history,
                    recommended_department,
                    urgency,
                    consultation_summary,
                    record_json,
                    status,
                    created_at,
                    updated_at
                FROM agent_medical_record_draft
                WHERE patient_id = ?
                  AND consultation_mode = 'deep'
                  AND deleted = 0
                ORDER BY created_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rowMapper, patientId).stream().findFirst();
    }

    public Optional<MedicalRecordDraftRow> findLatestByPatientIdNumber(String patientIdNumber) {
        String sql = """
                SELECT
                    id,
                    patient_id,
                    patient_id_number,
                    session_id,
                    consultation_mode,
                    source_type,
                    chief_complaint,
                    present_illness_history,
                    recommended_department,
                    urgency,
                    consultation_summary,
                    record_json,
                    status,
                    created_at,
                    updated_at
                FROM agent_medical_record_draft
                WHERE patient_id_number = ?
                  AND consultation_mode = 'deep'
                  AND deleted = 0
                ORDER BY created_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rowMapper, patientIdNumber).stream().findFirst();
    }

    public Optional<MedicalRecordDraftRow> findById(Long id) {
        String sql = """
                SELECT
                    id,
                    patient_id,
                    patient_id_number,
                    session_id,
                    consultation_mode,
                    source_type,
                    chief_complaint,
                    present_illness_history,
                    recommended_department,
                    urgency,
                    consultation_summary,
                    record_json,
                    status,
                    created_at,
                    updated_at
                FROM agent_medical_record_draft
                WHERE id = ?
                  AND consultation_mode = 'deep'
                  AND deleted = 0
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rowMapper, id).stream().findFirst();
    }

    public record MedicalRecordDraftRow(
            Long id,
            Long patientId,
            String patientIdNumber,
            String sessionId,
            String consultationMode,
            String sourceType,
            String chiefComplaint,
            String presentIllnessHistory,
            String recommendedDepartment,
            String urgency,
            String consultationSummary,
            String recordJson,
            String status,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {
    }

    private static class MedicalRecordDraftRowMapper implements RowMapper<MedicalRecordDraftRow> {
        @Override
        public MedicalRecordDraftRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalRecordDraftRow(
                    rs.getLong("id"),
                    nullableLong(rs, "patient_id"),
                    safeString(rs, "patient_id_number"),
                    rs.getString("session_id"),
                    rs.getString("consultation_mode"),
                    rs.getString("source_type"),
                    rs.getString("chief_complaint"),
                    rs.getString("present_illness_history"),
                    rs.getString("recommended_department"),
                    rs.getString("urgency"),
                    rs.getString("consultation_summary"),
                    rs.getString("record_json"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }

        private Long nullableLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }

        private String safeString(ResultSet rs, String column) throws SQLException {
            try {
                return rs.getString(column);
            } catch (SQLException e) {
                return null;
            }
        }
    }
}
