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
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MedicalRecordDraftService {

    private static final String SOURCE_TYPE = "DEEP_PRE_CONSULTATION";
    private static final String RECORD_TYPE = "pre_consultation_draft";

    private final LlmClientFactory llmClientFactory;
    private final ObjectMapper objectMapper;
    private final MedicalRecordDraftRepository repository;
    private final RagRetrievalClient ragRetrievalClient;
    private final RagContextFormatter ragContextFormatter;
    private final RagPromptBuilder ragPromptBuilder;
    private final RagProperties ragProperties;

    public MedicalRecordDraftService(
            LlmClientFactory llmClientFactory,
            ObjectMapper objectMapper,
            MedicalRecordDraftRepository repository,
            RagRetrievalClient ragRetrievalClient,
            RagContextFormatter ragContextFormatter,
            RagPromptBuilder ragPromptBuilder,
            RagProperties ragProperties
    ) {
        this.llmClientFactory = llmClientFactory;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.ragRetrievalClient = ragRetrievalClient;
        this.ragContextFormatter = ragContextFormatter;
        this.ragPromptBuilder = ragPromptBuilder;
        this.ragProperties = ragProperties;
    }

    public MedicalRecordDraftGenerateResponse generate(MedicalRecordDraftGenerateRequest request) {
        try {
            request.validate();
            String rawReply = callLlm(request);
            JsonNode record = parseAndValidate(rawReply);
            String recordJson = objectMapper.writeValueAsString(record);
            MedicalRecordDraftEntity entity = buildEntity(request, record, recordJson, rawReply);
            Long draftId = repository.save(entity);
            return MedicalRecordDraftGenerateResponse.ok(draftId, record);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (DataAccessException e) {
            return MedicalRecordDraftGenerateResponse.fail("数据库连接或写入失败，请检查 AGENT_DB_URL、AGENT_DB_USERNAME、AGENT_DB_PASSWORD 是否正确，并确认 agent_medical_record_draft 表已创建。");
        } catch (Exception e) {
            return MedicalRecordDraftGenerateResponse.fail(e.getMessage());
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
                row.sessionId(),
                row.consultationMode(),
                row.sourceType(),
                row.chiefComplaint(),
                row.presentIllnessHistory(),
                row.recommendedDepartment(),
                row.urgency(),
                row.consultationSummary(),
                parsedRecordJson.recordJson(),
                row.status(),
                row.createdAt(),
                row.updatedAt(),
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
            throw new IllegalStateException(e.getMessage(), e);
        }

        String content = response.content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned empty medical record draft");
        }
        return content.trim();
    }

    private String retrieveRagContext(MedicalRecordDraftGenerateRequest request) {
        String query = (request.normalizedConclusion() + "\n" + formatHistory(request.history())).trim();
        return ragContextFormatter.format(
                ragRetrievalClient.retrieve(query, RagRetrievalClient.SCENE_MEDICAL_RECORD),
                ragProperties.getMaxContextChars()
        );
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
                rawReply,
                "DRAFT",
                createdBy
        );
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
