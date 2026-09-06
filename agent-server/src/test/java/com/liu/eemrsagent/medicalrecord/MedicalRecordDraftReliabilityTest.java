package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.rag.RagContextFormatter;
import com.liu.eemrsagent.rag.RagPromptBuilder;
import com.liu.eemrsagent.rag.RagProperties;
import com.liu.eemrsagent.rag.RagRetrievalClient;
import com.liu.eemrsagent.rag.RagRetrievalResult;
import com.liu.eemrsagent.trace.TraceRedactor;
import com.liu.eemrsagent.trace.NoopTraceRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicalRecordDraftReliabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void modelSuccessCreatesDraft() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenReturn(successResponse());
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);
        when(repository.save(any())).thenReturn(101L);

        MedicalRecordDraftGenerateResponse response = service(llm, repository).generate(request());

        assertThat(response.success()).isTrue();
        assertThat(response.draftId()).isEqualTo(101L);
        assertThat(response.errorCode()).isNull();
    }

    @Test
    void upstream502ReturnsControlledFailureAndNextRequestCanSucceed() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenThrow(new LlmException("DeepSeek 服务暂时不可用，请稍后重试。 502"))
                .thenReturn(successResponse());
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);
        when(repository.save(any())).thenReturn(102L);

        MedicalRecordDraftGenerateResponse failed = service(llm, repository).generate(request());
        MedicalRecordDraftGenerateResponse recovered = service(llm, repository).generate(request());

        assertThat(failed.success()).isFalse();
        assertThat(failed.errorCode()).isEqualTo("MODEL_CALL_FAILED");
        assertThat(recovered.success()).isTrue();
        assertThat(recovered.draftId()).isEqualTo(102L);
    }

    @Test
    void connectionResetReturnsControlledFailureAndNextRequestCanSucceed() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenThrow(new LlmException("[WinError 10054] 远程主机强迫关闭了一个现有的连接。"))
                .thenReturn(successResponse());
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);
        when(repository.save(any())).thenReturn(103L);

        MedicalRecordDraftGenerateResponse failed = service(llm, repository).generate(request());
        MedicalRecordDraftGenerateResponse recovered = service(llm, repository).generate(request());

        assertThat(failed.success()).isFalse();
        assertThat(failed.errorCode()).isEqualTo("MODEL_CALL_FAILED");
        assertThat(recovered.success()).isTrue();
    }

    @Test
    void sequentialSuccessSuccess502SuccessSuccessDoesNotPoisonService() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenReturn(successResponse())
                .thenReturn(successResponse())
                .thenThrow(new LlmException("DeepSeek 服务暂时不可用，请稍后重试。 502"))
                .thenReturn(successResponse())
                .thenReturn(successResponse());
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);
        when(repository.save(any())).thenReturn(1L, 2L, 4L, 5L);
        MedicalRecordDraftService service = service(llm, repository);

        assertThat(service.generate(request()).success()).isTrue();
        assertThat(service.generate(request()).success()).isTrue();
        assertThat(service.generate(request()).errorCode()).isEqualTo("MODEL_CALL_FAILED");
        assertThat(service.generate(request()).success()).isTrue();
        assertThat(service.generate(request()).success()).isTrue();
    }

    @Test
    void databaseResourceFailureIsControlled() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenReturn(successResponse());
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);
        when(repository.save(any())).thenThrow(new TransientDataAccessResourceException("pool exhausted"));

        MedicalRecordDraftGenerateResponse response = service(llm, repository).generate(request());

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("DB_WRITE_FAILED");
    }

    @Test
    void malformedModelJsonReturnsControlledFailureWithoutRawJacksonMessage() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenReturn(new LlmChatResponse("{\"recordType\":\"pre_consultation_draft\"", "fake-model", "fake", 1, 1, 2, "{}"));
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);

        MedicalRecordDraftGenerateResponse response = service(llm, repository).generate(request());

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MODEL_RESPONSE_JSON_INVALID");
        assertThat(response.error()).doesNotContain("Unexpected end-of-input");
    }

    @Test
    void emptyModelResponseHasSpecificFailureCode() {
        LlmClientFactory llm = mock(LlmClientFactory.class);
        MedicalRecordDraftRepository repository = mock(MedicalRecordDraftRepository.class);
        when(llm.chatForPurpose(eq(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT), any()))
                .thenReturn(new LlmChatResponse(" ", "fake-model", "fake", 1, 1, 2, "{}"));
        when(llm.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT))
                .thenReturn(com.liu.eemrsagent.llm.LlmProviderType.DEEPSEEK);

        MedicalRecordDraftGenerateResponse response = service(llm, repository).generate(request());

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MODEL_RESPONSE_EMPTY");
    }

    private MedicalRecordDraftService service(LlmClientFactory llm, MedicalRecordDraftRepository repository) {
        RagRetrievalClient rag = mock(RagRetrievalClient.class);
        when(rag.retrieveWithMetadata(any(), any())).thenReturn(RagRetrievalResult.empty());
        return new MedicalRecordDraftService(
                llm,
                objectMapper,
                repository,
                rag,
                new RagContextFormatter(),
                new RagPromptBuilder(),
                new RagProperties(),
                mock(CoreMedicalRecordClient.class),
                new NoopTraceRecorder(),
                new TraceRedactor()
        );
    }

    private MedicalRecordDraftGenerateRequest request() {
        return new MedicalRecordDraftGenerateRequest(
                "eval-D-test",
                1L,
                "patient-1",
                "deep",
                "患者腹痛三天，中等程度，无明显危险信号。",
                List.of(new AgentMessage("user", "腹痛三天。"))
        );
    }

    private LlmChatResponse successResponse() {
        return new LlmChatResponse("""
                {"recordType":"pre_consultation_draft","version":"1.0","notice":"本病历由智能体根据患者预问诊信息自动生成，仅作为病历草稿，不能替代医生诊断，需由医生审核确认后方可作为正式病历。","patientBasicInfo":{"patientId":1,"name":"","gender":"","age":"","contact":"","specialPopulation":{"isChild":false,"isPregnant":false,"isElderly":false,"hasChronicDisease":false,"description":""}},"visitInfo":{"visitType":"outpatient_pre_consultation","source":"deep_pre_consultation_agent","sessionId":"eval-D-test","generatedAt":"2026-08-26T00:00:00+08:00","recommendedDepartment":{"primary":"消化内科","alternatives":[],"reason":"腹痛待查"},"urgency":{"level":"normal","description":"普通门诊"}},"chiefComplaint":{"text":"腹痛","duration":"三天"},"presentIllnessHistory":{"onsetTime":"三天前","mainSymptoms":["腹痛"],"symptomLocation":"","symptomNature":"","severity":"中等","frequency":"","duration":"三天","inducingFactors":"","aggravatingFactors":"","relievingFactors":"","accompanyingSymptoms":[],"negativeSymptoms":[],"progression":"","selfTreatment":"","impactOnLife":""},"pastHistory":{"diseases":[],"surgeryOrTrauma":"","infectiousDiseaseHistory":"","chronicDiseaseHistory":"","description":"未提供"},"medicationHistory":{"currentMedications":[],"recentMedications":[],"description":""},"allergyHistory":{"allergens":[],"reaction":"","description":""},"personalAndExposureHistory":{"smoking":"","alcohol":"","diet":"","sleep":"","exercise":"","travelHistory":"","contactHistory":"","occupationalExposure":"","description":""},"familyHistory":{"relatedDiseases":[],"description":""},"riskAssessment":{"redFlags":[],"emergencyAdvice":"","riskLevel":"normal","reason":"未见明确危险信号"},"preliminaryAssessment":{"possibleDirections":[{"name":"腹痛待查","basis":"患者自述腹痛三天","confidence":"uncertain"}],"excludedOrLessLikelyDirections":[],"limitations":"以上分析仅基于患者预问诊自述信息，缺少体格检查、实验室检查和影像学检查，不能作为确定诊断。"},"suggestedExaminations":[],"careAdvice":{"generalAdvice":[],"followUpAdvice":"","whenToSeekEmergencyCare":[]},"doctorReviewTips":{"keyPointsToConfirm":[],"missingInformation":["腹痛部位"],"suggestedQuestions":[]},"rawSummary":{"consultationConclusion":"患者腹痛三天，中等程度，无明显危险信号。","sourceHistoryBrief":"用户主诉腹痛。"}}
                """, "fake-model", "fake", 1, 1, 2, "{}");
    }
}
