package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.security.AgentRole;
import com.liu.eemrsagent.security.AgentUserPrincipal;
import com.liu.eemrsagent.security.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalRecordDraftServiceReviewTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MedicalRecordDraftRepository repository;
    private CoreMedicalRecordClient coreClient;
    private MedicalRecordDraftService service;
    private AgentUserPrincipal doctor;
    private AgentUserPrincipal patient;

    @BeforeEach
    void setUp() {
        repository = mock(MedicalRecordDraftRepository.class);
        coreClient = mock(CoreMedicalRecordClient.class);
        service = new MedicalRecordDraftService(null, objectMapper, repository, null, null, null, null, coreClient);
        doctor = new AgentUserPrincipal("D001", "doctor", AgentRole.DOCTOR, "内科");
        patient = new AgentUserPrincipal("P001", "patient", AgentRole.PATIENT, null);
    }

    @Test
    void doctorCanSaveEdit() throws Exception {
        var before = row(MedicalRecordDraftStatus.GENERATED, null);
        var after = row(MedicalRecordDraftStatus.REVIEWING, "D001");
        when(repository.findById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.saveEdit(eq(1L), eq("D001"), any(), eq("trace-1"))).thenReturn(1);

        var response = service.saveEdit("1", new MedicalRecordDraftEditRequest(objectMapper.readTree("{\"chiefComplaint\":{\"text\":\"头痛\"}}"), "edit"), doctor, "trace-1");

        assertEquals("REVIEWING", response.draft().status());
        verify(repository).insertAudit(eq(1L), eq("D001"), eq(MedicalRecordDraftAction.SAVE_EDIT), any(), any(), eq(null), eq("edit"), eq("trace-1"));
    }

    @Test
    void doctorCanAcceptDraft() throws Exception {
        var before = row(MedicalRecordDraftStatus.GENERATED, null);
        var after = row(MedicalRecordDraftStatus.ACCEPTED, "D001");
        when(repository.findById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.transition(eq(1L), eq("D001"), eq(MedicalRecordDraftStatus.GENERATED),
                eq(MedicalRecordDraftStatus.ACCEPTED), any(), eq("trace-1"))).thenReturn(1);

        var response = service.accept("1", new MedicalRecordDraftReviewRequest(null, null, "ok"), doctor, "trace-1");

        assertEquals("ACCEPTED", response.draft().status());
    }

    @Test
    void doctorCanPartiallyAcceptDraft() throws Exception {
        var before = row(MedicalRecordDraftStatus.REVIEWING, "D001");
        var after = row(MedicalRecordDraftStatus.PARTIALLY_ACCEPTED, "D001");
        when(repository.findById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.transition(eq(1L), eq("D001"), eq(MedicalRecordDraftStatus.REVIEWING),
                eq(MedicalRecordDraftStatus.PARTIALLY_ACCEPTED), any(), eq("trace-1"))).thenReturn(1);

        var response = service.partialAccept("1",
                new MedicalRecordDraftReviewRequest(objectMapper.readTree("{\"recordType\":\"pre_consultation_draft\"}"), null, "changed"),
                doctor, "trace-1");

        assertEquals("PARTIALLY_ACCEPTED", response.draft().status());
    }

    @Test
    void rejectReasonIsRequired() {
        when(repository.findById(1L)).thenReturn(Optional.of(row(MedicalRecordDraftStatus.GENERATED, null)));

        assertThrows(IllegalArgumentException.class,
                () -> service.reject("1", new MedicalRecordDraftReviewRequest(null, "", null), doctor, "trace-1"));
    }

    @Test
    void acceptedDraftCanApplyAndRepeatedApplyIsIdempotent() throws Exception {
        var accepted = row(MedicalRecordDraftStatus.ACCEPTED, "D001");
        var applied = row(MedicalRecordDraftStatus.APPLIED, "D001");
        when(repository.findById(1L)).thenReturn(Optional.of(accepted), Optional.of(applied), Optional.of(applied));
        when(repository.markApplied(eq(1L), eq("D001"), any(), eq("trace-1"))).thenReturn(1);
        doReturn(true).when(coreClient).createFromDraft(any(), any(), any(), eq("Bearer token"));

        var response = service.apply("1", null, doctor, "Bearer token", "trace-1");
        assertEquals("APPLIED", response.draft().status());

        var idempotent = service.apply("1", null, doctor, "Bearer token", "trace-1");
        assertEquals(true, idempotent.idempotent());
    }

    @Test
    void otherDoctorCannotAccessOwnedDraft() {
        when(repository.findById(1L)).thenReturn(Optional.of(row(MedicalRecordDraftStatus.REVIEWING, "D002")));

        assertThrows(ForbiddenException.class,
                () -> service.saveEdit("1", new MedicalRecordDraftEditRequest(objectMapper.createObjectNode(), null), doctor, "trace-1"));
    }

    @Test
    void patientCannotReviewDraft() {
        assertThrows(ForbiddenException.class,
                () -> service.accept("1", null, patient, "trace-1"));
    }

    @Test
    void rejectedDraftCannotApply() {
        when(repository.findById(1L)).thenReturn(Optional.of(row(MedicalRecordDraftStatus.REJECTED, "D001")));

        assertThrows(IllegalArgumentException.class,
                () -> service.apply("1", null, doctor, "Bearer token", "trace-1"));
    }

    private MedicalRecordDraftRepository.MedicalRecordDraftRow row(MedicalRecordDraftStatus status, String doctorIdNumber) {
        String json = "{\"recordType\":\"pre_consultation_draft\",\"chiefComplaint\":{\"text\":\"头痛\"},\"patientBasicInfo\":{\"name\":\"张三\",\"contact\":\"13800000000\"}}";
        return new MedicalRecordDraftRepository.MedicalRecordDraftRow(
                1L, null, "P001", doctorIdNumber, null, "S001", "deep", "DEEP_PRE_CONSULTATION",
                "头痛", "{}", "内科", "normal", "summary", json, json, json,
                status.name(), "mock-model", "prompt-v1", "trace-1",
                null, null, null, null, status == MedicalRecordDraftStatus.APPLIED ? java.time.LocalDateTime.now() : null,
                status == MedicalRecordDraftStatus.APPLIED ? "hash" : null
        );
    }
}
