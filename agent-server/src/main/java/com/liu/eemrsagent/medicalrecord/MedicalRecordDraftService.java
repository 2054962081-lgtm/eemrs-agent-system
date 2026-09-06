package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.liu.eemrsagent.llm.LlmChatRequest;
import com.liu.eemrsagent.llm.LlmChatResponse;
import com.liu.eemrsagent.llm.LlmClientFactory;
import com.liu.eemrsagent.llm.LlmException;
import com.liu.eemrsagent.llm.LlmMessage;
import com.liu.eemrsagent.rag.RagContextFormatter;
import com.liu.eemrsagent.rag.RagPromptBuilder;
import com.liu.eemrsagent.rag.RagProperties;
import com.liu.eemrsagent.rag.RagRetrievalClient;
import com.liu.eemrsagent.rag.RagRetrievalResult;
import com.liu.eemrsagent.security.AgentUserPrincipal;
import com.liu.eemrsagent.security.ForbiddenException;
import com.liu.eemrsagent.trace.AgentTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import com.liu.eemrsagent.trace.TraceRunScope;
import com.liu.eemrsagent.trace.TraceRunStart;
import com.liu.eemrsagent.trace.TraceStepData;
import com.liu.eemrsagent.trace.TraceStepScope;
import com.liu.eemrsagent.trace.TraceStepType;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MedicalRecordDraftService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordDraftService.class);
    private static final String SOURCE_TYPE = "DEEP_PRE_CONSULTATION";
    private static final String RECORD_TYPE = "pre_consultation_draft";

    private final LlmClientFactory llmClientFactory;
    private final ObjectMapper objectMapper;
    private final MedicalRecordDraftRepository repository;
    private final RagRetrievalClient ragRetrievalClient;
    private final RagContextFormatter ragContextFormatter;
    private final RagPromptBuilder ragPromptBuilder;
    private final RagProperties ragProperties;
    private final CoreMedicalRecordClient coreMedicalRecordClient;
    private final AgentTraceRecorder traceRecorder;
    private final TraceRedactor traceRedactor;

    public MedicalRecordDraftService(
            LlmClientFactory llmClientFactory,
            ObjectMapper objectMapper,
            MedicalRecordDraftRepository repository,
            RagRetrievalClient ragRetrievalClient,
            RagContextFormatter ragContextFormatter,
            RagPromptBuilder ragPromptBuilder,
            RagProperties ragProperties,
            CoreMedicalRecordClient coreMedicalRecordClient,
            AgentTraceRecorder traceRecorder,
            TraceRedactor traceRedactor
    ) {
        this.llmClientFactory = llmClientFactory;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.ragRetrievalClient = ragRetrievalClient;
        this.ragContextFormatter = ragContextFormatter;
        this.ragPromptBuilder = ragPromptBuilder;
        this.ragProperties = ragProperties;
        this.coreMedicalRecordClient = coreMedicalRecordClient;
        this.traceRecorder = traceRecorder;
        this.traceRedactor = traceRedactor;
    }

    public MedicalRecordDraftGenerateResponse generate(MedicalRecordDraftGenerateRequest request) {
        OffsetDateTime requestStart = OffsetDateTime.now();
        try (TraceRunScope run = traceRecorder.startRun(new TraceRunStart(
                request == null ? null : request.sessionId(),
                request == null ? null : request.normalizedPatientIdNumber(),
                "medical-record-draft-agent",
                "medical-record-draft-generate",
                "medical-record-draft-v1",
                "medical-rag-v1",
                null,
                Map.of(
                        "session_id", request == null ? "" : request.sessionId(),
                        "patient_id", request == null ? "" : String.valueOf(request.patientId()),
                        "request_start", requestStart.toString()
                )
        ))) {
            try {
                request.validate();
                String rawReply = callLlm(request);
                JsonNode record = parseAndValidate(rawReply);
                String recordJson = objectMapper.writeValueAsString(record);
                MedicalRecordDraftEntity entity = buildEntity(request, record, recordJson, rawReply);
                Long draftId = repository.save(entity);
                run.success(Map.of(
                        "draft_id", draftId,
                        "success", true,
                        "jvm_request_status", "SUCCESS",
                        "response_status", "SUCCESS",
                        "request_start", requestStart.toString(),
                        "request_end", OffsetDateTime.now().toString()
                ), null, null, null);
                return MedicalRecordDraftGenerateResponse.ok(draftId, record);
            } catch (IllegalArgumentException e) {
                run.fail("BAD_REQUEST", e.getMessage());
                throw e;
            } catch (DataAccessException e) {
                String message = "数据库连接或写入失败，请检查 AGENT_DB_URL、AGENT_DB_USERNAME、AGENT_DB_PASSWORD 是否正确，并确认 agent_medical_record_draft 表已创建。";
                run.fail("DB_WRITE_FAILED", e.getMessage());
                log.warn("medical_record_draft_request_failed request_start={} request_end={} exception_stage=db exception_class={} response_status=FAILED",
                        requestStart, OffsetDateTime.now(), e.getClass().getName());
                return MedicalRecordDraftGenerateResponse.fail("DB_WRITE_FAILED", message);
            } catch (JsonProcessingException e) {
                String errorCode = "MODEL_RESPONSE_JSON_INVALID";
                run.fail(errorCode, e.getOriginalMessage());
                log.warn("medical_record_draft_request_failed request_start={} request_end={} exception_stage=upstream_model exception_class={} response_status=FAILED raw_error={}",
                        requestStart, OffsetDateTime.now(), e.getClass().getName(), e.getMessage());
                return MedicalRecordDraftGenerateResponse.fail(errorCode, safeDraftErrorMessage(errorCode));
            } catch (LlmException e) {
                String errorCode = classifyDraftModelError(e);
                run.fail(errorCode, e.getMessage());
                log.warn("medical_record_draft_request_failed request_start={} request_end={} exception_stage=upstream_model exception_class={} response_status=FAILED raw_error={}",
                        requestStart, OffsetDateTime.now(), e.getClass().getName(), e.getMessage());
                return MedicalRecordDraftGenerateResponse.fail(errorCode, safeDraftErrorMessage(errorCode));
            } catch (ModelResponseEmptyException e) {
                String errorCode = "MODEL_RESPONSE_EMPTY";
                run.fail(errorCode, e.getMessage());
                log.warn("medical_record_draft_request_failed request_start={} request_end={} exception_stage=upstream_model exception_class={} response_status=FAILED",
                        requestStart, OffsetDateTime.now(), e.getClass().getName());
                return MedicalRecordDraftGenerateResponse.fail(errorCode, safeDraftErrorMessage(errorCode));
            } catch (Exception e) {
                String errorCode = classifyDraftError(e);
                run.fail(errorCode, e.getMessage());
                log.warn("medical_record_draft_request_failed request_start={} request_end={} exception_stage={} exception_class={} response_status=FAILED",
                        requestStart, OffsetDateTime.now(), exceptionStage(errorCode), e.getClass().getName());
                return MedicalRecordDraftGenerateResponse.fail(errorCode, safeDraftErrorMessage(errorCode));
            }
        }
    }

    public MedicalRecordDraftQueryResponse findLatestByPatientId(String patientId) {
        String normalizedPatientId = patientId == null ? "" : patientId.trim();
        if (normalizedPatientId.isBlank()) {
            throw new IllegalArgumentException("patientId is required");
        }
        try {
            Optional<MedicalRecordDraftRepository.MedicalRecordDraftRow> row = repository.findLatestByPatientIdNumber(normalizedPatientId);
            if (row.isEmpty() && isPositiveLong(normalizedPatientId)) {
                row = repository.findLatestByPatientId(Long.parseLong(normalizedPatientId));
            }
            return row.map(value -> MedicalRecordDraftQueryResponse.found(toDetail(value)))
                    .orElseGet(MedicalRecordDraftQueryResponse::empty);
        } catch (DataAccessException e) {
            return MedicalRecordDraftQueryResponse.fail("预问诊病历草稿查询失败，请稍后重试。", e.getMessage());
        }
    }

    @Transactional
    public MedicalRecordDraftQueryResponse findLatestByPatientIdForDoctor(String patientId, AgentUserPrincipal currentUser, String traceId) {
        requireDoctor(currentUser);
        MedicalRecordDraftQueryResponse response = findLatestByPatientId(patientId);
        if (!response.success() || !response.hasDraft() || response.draft() == null) {
            return response;
        }
        MedicalRecordDraftRepository.MedicalRecordDraftRow row = requireDraft(response.draft().id());
        ensureDoctorCanAccess(row, currentUser);
        if (MedicalRecordDraftStatus.parse(row.status()) == MedicalRecordDraftStatus.GENERATED) {
            repository.lockForDoctorIfUnassigned(row.id(), currentUser.idNumber());
            insertAudit(row.id(), currentUser.idNumber(), MedicalRecordDraftAction.OPEN,
                    row.editedRecordJson(), row.editedRecordJson(), null, "doctor opened latest draft", traceId);
            row = requireDraft(row.id());
        }
        return MedicalRecordDraftQueryResponse.found(toDetail(row));
    }

    public MedicalRecordDraftQueryResponse findById(String draftId) {
        Long parsedDraftId = parsePositiveLong(draftId, "draftId");
        try {
            Optional<MedicalRecordDraftRepository.MedicalRecordDraftRow> row = repository.findById(parsedDraftId);
            return row.map(value -> MedicalRecordDraftQueryResponse.found(toDetail(value)))
                    .orElseGet(MedicalRecordDraftQueryResponse::empty);
        } catch (DataAccessException e) {
            return MedicalRecordDraftQueryResponse.fail("预问诊病历草稿查询失败，请稍后重试。", e.getMessage());
        }
    }

    @Transactional
    public MedicalRecordDraftQueryResponse findByIdForDoctor(String draftId, AgentUserPrincipal currentUser, String traceId) {
        requireDoctor(currentUser);
        Long parsedDraftId = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow row = repository.findById(parsedDraftId)
                .orElseThrow(() -> new IllegalArgumentException("draft not found"));
        ensureDoctorCanAccess(row, currentUser);
        MedicalRecordDraftStatus status = MedicalRecordDraftStatus.parse(row.status());
        if (status == MedicalRecordDraftStatus.GENERATED) {
            repository.lockForDoctorIfUnassigned(row.id(), currentUser.idNumber());
            insertAudit(row.id(), currentUser.idNumber(), MedicalRecordDraftAction.OPEN,
                    row.editedRecordJson(), row.editedRecordJson(), null, "doctor opened draft", traceId);
            row = repository.findById(parsedDraftId).orElse(row);
        }
        return MedicalRecordDraftQueryResponse.found(toDetail(row));
    }

    @Transactional
    public MedicalRecordDraftActionResponse saveEdit(String draftId, MedicalRecordDraftEditRequest request,
                                                     AgentUserPrincipal currentUser, String traceId) throws JsonProcessingException {
        requireDoctor(currentUser);
        Long id = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow before = requireDraft(id);
        ensureDoctorCanAccess(before, currentUser);
        MedicalRecordDraftStatus status = MedicalRecordDraftStatus.parse(before.status());
        if (!status.canEdit()) {
            throw new IllegalArgumentException("Draft in status " + status + " cannot be edited");
        }
        JsonNode edited = request == null ? null : request.editedRecordJson();
        if (edited == null || edited.isNull()) {
            throw new IllegalArgumentException("editedRecordJson is required");
        }
        String afterJson = objectMapper.writeValueAsString(edited);
        int updated = repository.saveEdit(id, currentUser.idNumber(), afterJson, traceId);
        if (updated == 0) {
            throw new ForbiddenException("No permission to edit this draft or status changed");
        }
        insertAudit(id, currentUser.idNumber(), MedicalRecordDraftAction.SAVE_EDIT,
                before.editedRecordJson(), afterJson, null, trim(request.comment()), traceId);
        return MedicalRecordDraftActionResponse.ok("draft saved", toDetail(requireDraft(id)));
    }

    @Transactional
    public MedicalRecordDraftActionResponse accept(String draftId, MedicalRecordDraftReviewRequest request,
                                                   AgentUserPrincipal currentUser, String traceId) throws JsonProcessingException {
        return review(draftId, request, currentUser, traceId, MedicalRecordDraftStatus.ACCEPTED, MedicalRecordDraftAction.ACCEPT);
    }

    @Transactional
    public MedicalRecordDraftActionResponse partialAccept(String draftId, MedicalRecordDraftReviewRequest request,
                                                          AgentUserPrincipal currentUser, String traceId) throws JsonProcessingException {
        return review(draftId, request, currentUser, traceId, MedicalRecordDraftStatus.PARTIALLY_ACCEPTED, MedicalRecordDraftAction.PARTIAL_ACCEPT);
    }

    @Transactional
    public MedicalRecordDraftActionResponse reject(String draftId, MedicalRecordDraftReviewRequest request,
                                                   AgentUserPrincipal currentUser, String traceId) throws JsonProcessingException {
        if (request == null || request.rejectReason() == null || request.rejectReason().isBlank()) {
            throw new IllegalArgumentException("rejectReason is required");
        }
        return review(draftId, request, currentUser, traceId, MedicalRecordDraftStatus.REJECTED, MedicalRecordDraftAction.REJECT);
    }

    @Transactional
    public MedicalRecordDraftActionResponse apply(String draftId, MedicalRecordDraftApplyRequest request,
                                                  AgentUserPrincipal currentUser, String authorizationHeader,
                                                  String traceId) throws JsonProcessingException {
        requireDoctor(currentUser);
        Long id = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow before = requireDraft(id);
        ensureDoctorCanAccess(before, currentUser);
        MedicalRecordDraftStatus status = MedicalRecordDraftStatus.parse(before.status());
        if (status == MedicalRecordDraftStatus.APPLIED) {
            return MedicalRecordDraftActionResponse.idempotent("draft already applied", toDetail(before));
        }
        if (!status.canApply()) {
            throw new IllegalArgumentException("Only ACCEPTED or PARTIALLY_ACCEPTED drafts can be applied");
        }
        JsonNode appliedRecord = parseRecordJson(firstNonBlank(before.editedRecordJson(), before.aiRecordJson(), before.recordJson())).recordJson();
        coreMedicalRecordClient.createFromDraft(before, request == null ? emptyApplyRequest() : request, appliedRecord, authorizationHeader);
        String hash = sha256(objectMapper.writeValueAsString(appliedRecord));
        int updated = repository.markApplied(id, currentUser.idNumber(), hash, traceId);
        if (updated == 0) {
            MedicalRecordDraftRepository.MedicalRecordDraftRow latest = requireDraft(id);
            if (MedicalRecordDraftStatus.parse(latest.status()) == MedicalRecordDraftStatus.APPLIED) {
                return MedicalRecordDraftActionResponse.idempotent("draft already applied", toDetail(latest));
            }
            throw new IllegalArgumentException("Draft status changed before apply");
        }
        insertAudit(id, currentUser.idNumber(), MedicalRecordDraftAction.APPLY,
                before.editedRecordJson(), before.editedRecordJson(), null, "applied to formal medical record", traceId);
        return MedicalRecordDraftActionResponse.ok("draft applied", toDetail(requireDraft(id)));
    }

    public MedicalRecordDraftHistoryResponse history(String draftId, AgentUserPrincipal currentUser) {
        requireDoctor(currentUser);
        Long id = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow row = requireDraft(id);
        ensureDoctorCanAccess(row, currentUser);
        return new MedicalRecordDraftHistoryResponse(id, repository.findAuditLogs(id));
    }

    public MedicalRecordDraftStatusResponse status(String draftId, AgentUserPrincipal currentUser) {
        requireDoctor(currentUser);
        Long id = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow row = requireDraft(id);
        ensureDoctorCanAccess(row, currentUser);
        return new MedicalRecordDraftStatusResponse(row.id(), MedicalRecordDraftStatus.parse(row.status()).name(),
                row.firstReviewedAt(), row.completedAt(), row.appliedAt());
    }

    private MedicalRecordDraftActionResponse review(String draftId, MedicalRecordDraftReviewRequest request,
                                                    AgentUserPrincipal currentUser, String traceId,
                                                    MedicalRecordDraftStatus target,
                                                    MedicalRecordDraftAction action) throws JsonProcessingException {
        requireDoctor(currentUser);
        Long id = parsePositiveLong(draftId, "draftId");
        MedicalRecordDraftRepository.MedicalRecordDraftRow before = requireDraft(id);
        ensureDoctorCanAccess(before, currentUser);
        MedicalRecordDraftStatus current = MedicalRecordDraftStatus.parse(before.status());
        if (!current.canTransitionTo(target)) {
            if (current == target) {
                return MedicalRecordDraftActionResponse.idempotent("draft already " + target.name(), toDetail(before));
            }
            throw new IllegalArgumentException("Illegal draft status transition: " + current + " -> " + target);
        }
        JsonNode edited = request == null ? null : request.editedRecordJson();
        String afterJson = edited == null || edited.isNull()
                ? firstNonBlank(before.editedRecordJson(), before.aiRecordJson(), before.recordJson())
                : objectMapper.writeValueAsString(edited);
        int updated = repository.transition(id, currentUser.idNumber(), current, target, afterJson, traceId);
        if (updated == 0) {
            throw new ForbiddenException("No permission to review this draft or status changed");
        }
        insertAudit(id, currentUser.idNumber(), action, before.editedRecordJson(), afterJson,
                trim(request == null ? null : request.rejectReason()),
                trim(request == null ? null : request.comment()),
                traceId);
        return MedicalRecordDraftActionResponse.ok("draft " + target.name().toLowerCase(), toDetail(requireDraft(id)));
    }

    private Long parsePositiveLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number");
        }
    }

    private MedicalRecordDraftDetail toDetail(MedicalRecordDraftRepository.MedicalRecordDraftRow row) {
        ParsedRecordJson parsedRecordJson = parseRecordJson(row.recordJson());
        return new MedicalRecordDraftDetail(
                row.id(),
                row.patientId(),
                row.patientIdNumber(),
                row.doctorIdNumber(),
                row.appointmentId(),
                row.sessionId(),
                row.consultationMode(),
                row.sourceType(),
                row.chiefComplaint(),
                row.presentIllnessHistory(),
                row.recommendedDepartment(),
                row.urgency(),
                row.consultationSummary(),
                parseRecordJson(firstNonBlank(row.aiRecordJson(), row.recordJson())).recordJson(),
                parseRecordJson(firstNonBlank(row.editedRecordJson(), row.recordJson())).recordJson(),
                parsedRecordJson.recordJson(),
                MedicalRecordDraftStatus.parse(row.status()).name(),
                row.modelName(),
                row.promptVersion(),
                row.traceId(),
                row.createdAt(),
                row.updatedAt(),
                row.firstReviewedAt(),
                row.completedAt(),
                row.appliedAt(),
                parsedRecordJson.parseError()
        );
    }

    private ParsedRecordJson parseRecordJson(String recordJson) {
        if (recordJson == null || recordJson.isBlank()) {
            return new ParsedRecordJson(TextNode.valueOf(""), true);
        }
        try {
            return new ParsedRecordJson(objectMapper.readTree(recordJson), false);
        } catch (JsonProcessingException e) {
            return new ParsedRecordJson(TextNode.valueOf(recordJson), true);
        }
    }

    private String callLlm(MedicalRecordDraftGenerateRequest request) {
        String purpose = LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT;
        String ragContext = retrieveRagContext(request);
        LlmChatRequest chatRequest = new LlmChatRequest(
                List.of(
                        new LlmMessage("system", ragPromptBuilder.mergeSystemPrompt(buildSystemPrompt(), ragContext)),
                        new LlmMessage("user", buildUserPrompt(request))
                ),
                purpose,
                0.2,
                0.8,
                null,
                true,
                false
        );

        LlmChatResponse response;
        try {
            response = llmClientFactory.chatForPurpose(purpose, chatRequest);
        } catch (LlmException e) {
            throw e;
        }

        String content = response.content();
        if (content == null || content.isBlank()) {
            throw new ModelResponseEmptyException("LLM returned empty medical record draft");
        }
        return content.trim();
    }

    private String classifyDraftError(Exception e) {
        Throwable current = e;
        while (current != null) {
            String name = current.getClass().getName();
            String message = current.getMessage() == null ? "" : current.getMessage();
            if (name.contains("LlmException") || message.contains("DeepSeek")) {
                return classifyDraftModelError(current);
            }
            current = current.getCause();
        }
        return "RUNTIME_ERROR";
    }

    private String classifyDraftModelError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage() == null ? "" : current.getMessage();
            if (message.contains("返回内容为空") || message.toLowerCase().contains("empty")) {
                return "MODEL_RESPONSE_EMPTY";
            }
            current = current.getCause();
        }
        return "MODEL_CALL_FAILED";
    }

    private String safeDraftErrorMessage(String errorCode) {
        return switch (errorCode) {
            case "MODEL_CALL_FAILED" -> "云端模型调用失败，请稍后重试。";
            case "MODEL_RESPONSE_EMPTY" -> "云端模型返回为空，请稍后重试。";
            case "MODEL_RESPONSE_JSON_INVALID" -> "云端模型返回内容格式异常，请稍后重试。";
            case "DB_WRITE_FAILED" -> "病历草稿写入失败，请稍后重试。";
            default -> "病历草稿生成失败，请稍后重试。";
        };
    }

    private String exceptionStage(String errorCode) {
        return errorCode.startsWith("MODEL_") ? "upstream_model" : "jvm_request";
    }

    private static class ModelResponseEmptyException extends RuntimeException {
        private ModelResponseEmptyException(String message) {
            super(message);
        }
    }

    private String retrieveRagContext(MedicalRecordDraftGenerateRequest request) {
        String query = (request.normalizedConclusion() + "\n" + formatHistory(request.history())).trim();
        RagRetrievalResult result;
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RAG_REQUEST, "call rag retrieval service",
                null, Map.of(
                        "agent", "doctor_draft",
                        "scene", RagRetrievalClient.SCENE_MEDICAL_RECORD,
                        "query_length", query.length(),
                        "query_hash", traceRedactor.stableHash(query),
                        "top_k", Math.max(ragProperties.getTopK(), 10),
                        "service_url", ragProperties.getServiceUrl()
                ))) {
            result = ragRetrievalClient.retrieveWithMetadata(query, RagRetrievalClient.SCENE_MEDICAL_RECORD);
            step.success(TraceStepData.of(null, Map.of(
                    "result_count", result.chunks().size(),
                    "used_query_expansion", result.usedQueryExpansion(),
                    "expanded_query", result.expandedQuery()
            ), Map.of("doc_type_counts", result.docTypeCounts())));
        }
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RAG_RETRIEVAL, "rag retrieval results",
                null, Map.of("agent", "doctor_draft", "scene", RagRetrievalClient.SCENE_MEDICAL_RECORD))) {
            step.success(TraceStepData.of(null, retrievalEvidence(result), Map.of(
                    "result_count", result.chunks().size(),
                    "doc_type_counts", result.docTypeCounts(),
                    "retrieval_selection", result.traceMeta().getOrDefault("retrieval_selection", Map.of()),
                    "retrieval_mode", retrievalMode(result)
            )));
        }
        RagContextFormatter.FormattedContext formattedContext = ragContextFormatter.formatWithTrace(
                result.chunks(), ragProperties.getMaxContextChars());
        recordFinalRagContext(result, formattedContext);
        return formattedContext.context();
    }

    private List<Map<String, Object>> retrievalEvidence(RagRetrievalResult result) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        int rank = 1;
        for (var chunk : result.chunks()) {
            evidence.add(Map.of(
                    "rank", rank++,
                    "document_id", blankToDefault(chunk.docId(), ""),
                    "chunk_id", blankToDefault(chunk.chunkId(), ""),
                    "doc_type", blankToDefault(chunk.docType(), ""),
                    "retrieval_score", chunk.score() == null ? 0.0 : chunk.score(),
                    "rerank_score", chunk.finalScore() == null ? (chunk.score() == null ? 0.0 : chunk.score()) : chunk.finalScore(),
                    "title_hash", traceRedactor.stableHash(chunk.title()),
                    "content_hash", traceRedactor.stableHash(chunk.chunkText())
            ));
        }
        return evidence;
    }

    private void recordFinalRagContext(RagRetrievalResult result, RagContextFormatter.FormattedContext formattedContext) {
        try (TraceStepScope step = traceRecorder.startStep(TraceStepType.RAG_RETRIEVAL, "final rag context",
                null, Map.of("agent", "doctor_draft", "scene", RagRetrievalClient.SCENE_MEDICAL_RECORD))) {
            step.success(TraceStepData.of(null, Map.of(
                    "retrieval_used", !result.chunks().isEmpty(),
                    "selected_chunks", selectedChunkIds(result),
                    "final_context_chunk_order", formattedContext.finalContextChunkOrder(),
                    "final_context_length", safeLength(formattedContext.context()),
                    "final_context_hash", traceRedactor.stableHash(formattedContext.context()),
                    "context_transform", Map.of(
                            "retrieved_count", result.chunks().size(),
                            "selected_count", result.chunks().size(),
                            "final_context_count", formattedContext.finalContextChunkOrder().size(),
                            "dropped_chunks", formattedContext.droppedChunkIds(),
                            "duplicate_chunks", formattedContext.duplicateChunkIds(),
                            "drop_reason", formattedContext.dropReason()
                    )
            ), Map.of(
                    "retrieval_mode", retrievalMode(result),
                    "trace_id", result.traceMeta().getOrDefault("trace_id", ""),
                    "run_id", result.traceMeta().getOrDefault("run_id", ""),
                    "step_id", result.traceMeta().getOrDefault("step_id", ""),
                    "latency", retrievalLatency(result)
            )));
        }
    }

    private List<String> selectedChunkIds(RagRetrievalResult result) {
        return result.chunks().stream()
                .map(chunk -> blankToDefault(chunk.chunkId(), ""))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Object retrievalLatency(RagRetrievalResult result) {
        Object hybrid = result.traceMeta().get("hybrid_retrieval");
        if (hybrid instanceof Map<?, ?> hybridMap) {
            Object latency = hybridMap.get("latency");
            return latency == null ? Map.of() : latency;
        }
        Object bm25 = result.traceMeta().get("bm25_retrieval");
        if (bm25 instanceof Map<?, ?> bm25Map) {
            Object latency = bm25Map.get("latency");
            return latency == null ? Map.of() : latency;
        }
        return Map.of();
    }

    private String retrievalMode(RagRetrievalResult result) {
        Object hybrid = result.traceMeta().get("hybrid_retrieval");
        if (hybrid instanceof Map<?, ?> hybridMap) {
            Object mode = hybridMap.get("retrieval_mode");
            return mode == null ? "" : String.valueOf(mode);
        }
        Object bm25 = result.traceMeta().get("bm25_retrieval");
        if (bm25 instanceof Map<?, ?>) {
            return "bm25";
        }
        return "dense";
    }

    private JsonNode parseAndValidate(String rawReply) throws JsonProcessingException {
        JsonNode record;
        try {
            record = objectMapper.readTree(rawReply);
        } catch (JsonProcessingException directError) {
            int start = rawReply.indexOf('{');
            int end = rawReply.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw directError;
            }
            record = objectMapper.readTree(rawReply.substring(start, end + 1));
        }

        String recordType = textAt(record, "/recordType");
        if (!RECORD_TYPE.equals(recordType)) {
            throw new IllegalArgumentException("Invalid recordType: " + recordType);
        }
        return record;
    }

    private MedicalRecordDraftEntity buildEntity(
            MedicalRecordDraftGenerateRequest request,
            JsonNode record,
            String recordJson,
            String rawReply
    ) throws JsonProcessingException {
        String chiefComplaint = textAt(record, "/chiefComplaint/text");
        String presentIllnessHistory = objectMapper.writeValueAsString(record.path("presentIllnessHistory"));
        String recommendedDepartment = textAt(record, "/visitInfo/recommendedDepartment/primary");
        String urgency = textAt(record, "/visitInfo/urgency/level");
        String createdBy = request.normalizedPatientIdNumber() != null
                ? request.normalizedPatientIdNumber()
                : request.patientId() == null ? null : String.valueOf(request.patientId());
        return new MedicalRecordDraftEntity(
                request.patientId(),
                request.normalizedPatientIdNumber(),
                request.normalizedSessionId(),
                "deep",
                SOURCE_TYPE,
                emptyToNull(chiefComplaint),
                emptyToNull(presentIllnessHistory),
                emptyToNull(recommendedDepartment),
                emptyToNull(urgency),
                request.normalizedConclusion(),
                recordJson,
                recordJson,
                recordJson,
                rawReply,
                MedicalRecordDraftStatus.GENERATED.name(),
                llmClientFactory.providerForPurpose(LlmClientFactory.PURPOSE_MEDICAL_RECORD_DRAFT).toString(),
                "medical-record-draft-v1",
                com.liu.eemrsagent.trace.TraceContext.current().map(com.liu.eemrsagent.trace.TraceContext.State::traceId).orElse(null),
                createdBy
        );
    }

    private MedicalRecordDraftRepository.MedicalRecordDraftRow requireDraft(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("draft not found"));
    }

    private void requireDoctor(AgentUserPrincipal currentUser) {
        if (currentUser == null || !currentUser.isDoctor()) {
            throw new ForbiddenException("Only doctors can review medical record drafts");
        }
    }

    private void ensureDoctorCanAccess(MedicalRecordDraftRepository.MedicalRecordDraftRow row, AgentUserPrincipal currentUser) {
        String owner = row.doctorIdNumber();
        if (owner != null && !owner.isBlank() && !owner.equalsIgnoreCase(currentUser.idNumber())) {
            throw new ForbiddenException("Doctor cannot access another doctor's draft");
        }
    }

    private MedicalRecordDraftApplyRequest emptyApplyRequest() {
        return new MedicalRecordDraftApplyRequest(null, null, null, null, null, null, null, null, null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash applied record", e);
        }
    }

    private void insertAudit(Long draftId, String doctorIdNumber, MedicalRecordDraftAction action,
                             String beforeJson, String afterJson, String rejectReason, String comment, String traceId) {
        repository.insertAudit(draftId, doctorIdNumber, action,
                redactSensitiveJson(beforeJson), redactSensitiveJson(afterJson),
                rejectReason, comment, traceId);
    }

    private String redactSensitiveJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json).deepCopy();
            JsonNode basic = root.path("patientBasicInfo");
            if (basic instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                objectNode.put("name", "[REDACTED]");
                objectNode.put("contact", "[REDACTED]");
                objectNode.put("idNumber", "[REDACTED]");
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "[REDACTED_UNPARSEABLE_JSON]";
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一个医疗预问诊病历草稿生成助手。你的任务是根据患者深度问诊历史和深度问诊总结，生成结构化病历草稿 JSON。你必须严格遵守：
                1. 只能基于用户在深度问诊中提供的信息和问诊总结生成病历草稿。
                2. 不得编造患者未提供的信息。
                3. 对未提供的信息，必须填写空字符串、空数组、null 或“未提供”，不要猜测。
                4. 不得做确定性诊断。
                5. 不得把“可能相关方向”写成“确诊疾病”。
                6. 不得生成处方药具体用药剂量、疗程。
                7. 不得建议患者停止医生已开的药。
                8. 如存在胸痛、呼吸困难、意识障碍、抽搐、大出血、严重过敏、持续高热、剧烈腹痛、严重外伤、疑似卒中等危险信号，必须在 riskAssessment.redFlags 和 careAdvice.whenToSeekEmergencyCare 中体现。
                9. 推荐科室只能作为初步分诊建议。
                10. 输出必须是合法 JSON。
                11. 不要输出 Markdown。
                12. 不要输出 ```json 代码块。
                13. 不要输出解释文字。
                14. 不要输出隐藏推理过程。
                15. 所有字段必须符合给定 JSON 模板。
                16. notice 和 limitations 必须保留。
                17. 如果信息不足，要在 doctorReviewTips.missingInformation 中列出需要医生进一步确认的信息。
                """;
    }

    private String buildUserPrompt(MedicalRecordDraftGenerateRequest request) {
        return """
                请根据以下深度问诊信息生成预问诊病历草稿 JSON。

                【输出要求】
                只能输出合法 JSON，不要输出 Markdown，不要输出解释。

                【病历 JSON 模板】
                """ + recordTemplate(request) + """

                【深度问诊总结】
                """ + request.normalizedConclusion() + """

                【深度问诊历史】
                """ + formatHistory(request.history()) + """

                【补充要求】
                - 如果某字段没有信息，填写空字符串、空数组、null 或“未提供”。
                - 不得编造未提供的检查结果、病史、诊断。
                - 生成内容必须可被后端 JSON 解析。
                """;
    }

    private String formatHistory(List<AgentMessage> history) {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (AgentMessage message : history) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.role()) ? "助手" : "用户";
            lines.add(index + ". " + role + "：" + message.content().trim());
            index++;
        }
        return String.join("\n", lines);
    }

    private String recordTemplate(MedicalRecordDraftGenerateRequest request) {
        String patientId = request.patientId() == null ? "null" : String.valueOf(request.patientId());
        String sessionId = request.normalizedSessionId() == null ? "" : escapeJson(request.normalizedSessionId());
        String generatedAt = escapeJson(OffsetDateTime.now().toString());
        return """
                {
                  "recordType": "pre_consultation_draft",
                  "version": "1.0",
                  "notice": "本病历由智能体根据患者预问诊信息自动生成，仅作为病历草稿，不能替代医生诊断，需由医生审核确认后方可作为正式病历。",
                  "patientBasicInfo": {
                    "patientId": %s,
                    "name": "",
                    "gender": "",
                    "age": "",
                    "contact": "",
                    "specialPopulation": {
                      "isChild": false,
                      "isPregnant": false,
                      "isElderly": false,
                      "hasChronicDisease": false,
                      "description": ""
                    }
                  },
                  "visitInfo": {
                    "visitType": "outpatient_pre_consultation",
                    "source": "deep_pre_consultation_agent",
                    "sessionId": "%s",
                    "generatedAt": "%s",
                    "recommendedDepartment": {
                      "primary": "",
                      "alternatives": [],
                      "reason": ""
                    },
                    "urgency": {
                      "level": "normal",
                      "description": ""
                    }
                  },
                  "chiefComplaint": {
                    "text": "",
                    "duration": ""
                  },
                  "presentIllnessHistory": {
                    "onsetTime": "",
                    "mainSymptoms": [],
                    "symptomLocation": "",
                    "symptomNature": "",
                    "severity": "",
                    "frequency": "",
                    "duration": "",
                    "inducingFactors": "",
                    "aggravatingFactors": "",
                    "relievingFactors": "",
                    "accompanyingSymptoms": [],
                    "negativeSymptoms": [],
                    "progression": "",
                    "selfTreatment": "",
                    "impactOnLife": ""
                  },
                  "pastHistory": {
                    "diseases": [],
                    "surgeryOrTrauma": "",
                    "infectiousDiseaseHistory": "",
                    "chronicDiseaseHistory": "",
                    "description": ""
                  },
                  "medicationHistory": {
                    "currentMedications": [],
                    "recentMedications": [],
                    "description": ""
                  },
                  "allergyHistory": {
                    "allergens": [],
                    "reaction": "",
                    "description": ""
                  },
                  "personalAndExposureHistory": {
                    "smoking": "",
                    "alcohol": "",
                    "diet": "",
                    "sleep": "",
                    "exercise": "",
                    "travelHistory": "",
                    "contactHistory": "",
                    "occupationalExposure": "",
                    "description": ""
                  },
                  "familyHistory": {
                    "relatedDiseases": [],
                    "description": ""
                  },
                  "riskAssessment": {
                    "redFlags": [],
                    "emergencyAdvice": "",
                    "riskLevel": "normal",
                    "reason": ""
                  },
                  "preliminaryAssessment": {
                    "possibleDirections": [
                      {
                        "name": "",
                        "basis": "",
                        "confidence": "uncertain"
                      }
                    ],
                    "excludedOrLessLikelyDirections": [
                      {
                        "name": "",
                        "basis": ""
                      }
                    ],
                    "limitations": "以上分析仅基于患者预问诊自述信息，缺少体格检查、实验室检查和影像学检查，不能作为确定诊断。"
                  },
                  "suggestedExaminations": [],
                  "careAdvice": {
                    "generalAdvice": [],
                    "followUpAdvice": "",
                    "whenToSeekEmergencyCare": []
                  },
                  "doctorReviewTips": {
                    "keyPointsToConfirm": [],
                    "missingInformation": [],
                    "suggestedQuestions": []
                  },
                  "rawSummary": {
                    "consultationConclusion": "",
                    "sourceHistoryBrief": ""
                  }
                }
                """.formatted(patientId, sessionId, generatedAt);
    }

    private String textAt(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isPositiveLong(String value) {
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ParsedRecordJson(JsonNode recordJson, boolean parseError) {
    }
}
