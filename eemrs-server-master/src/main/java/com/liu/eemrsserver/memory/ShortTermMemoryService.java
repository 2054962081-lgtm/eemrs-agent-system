package com.liu.eemrsserver.memory;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import com.liu.eemrsserver.memory.dto.ShortTermMemoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ShortTermMemoryService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MemoryProperties memoryProperties;

    public ShortTermMemoryDTO createSession(MemoryUserScopeDTO scope, String sessionId) {
        return createSession(scope, sessionId, null);
    }

    public ShortTermMemoryDTO createSession(MemoryUserScopeDTO scope, String sessionId, String chiefComplaint) {
        requireScope(scope);
        String normalizedSessionId = normalizeSessionId(sessionId);
        ShortTermMemoryDTO existing = getSession(scope, normalizedSessionId);
        if (existing != null) {
            if (chiefComplaint != null && !chiefComplaint.trim().isEmpty()
                    && (existing.getChiefComplaint() == null || existing.getChiefComplaint().trim().isEmpty())) {
                existing.setChiefComplaint(chiefComplaint.trim());
                return updateSession(scope, existing);
            }
            return existing;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = ttlMillis();
        ShortTermMemoryDTO dto = new ShortTermMemoryDTO();
        dto.setSessionId(normalizedSessionId);
        dto.setPatientIdHash(scope.getPatientIdHash());
        if (chiefComplaint != null && !chiefComplaint.trim().isEmpty()) {
            dto.setChiefComplaint(chiefComplaint.trim());
        }
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);
        dto.setExpireAt(now + ttlMillis);
        save(scope, dto);
        return dto;
    }

    public ShortTermMemoryDTO getSession(MemoryUserScopeDTO scope, String sessionId) {
        requireScope(scope);
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(buildKey(scope.getPatientIdHash(), sessionId.trim()));
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return JSON.parseObject(json, ShortTermMemoryDTO.class);
    }

    public ShortTermMemoryDTO updateSession(MemoryUserScopeDTO scope, ShortTermMemoryDTO dto) {
        requireScope(scope);
        if (dto == null || dto.getSessionId() == null || dto.getSessionId().trim().isEmpty()) {
            throw new BadRequestException("sessionId must not be blank");
        }
        dto.setPatientIdHash(scope.getPatientIdHash());
        dto.setUpdatedAt(System.currentTimeMillis());
        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(dto.getUpdatedAt());
        }
        if (dto.getExpireAt() == null) {
            dto.setExpireAt(dto.getUpdatedAt() + ttlMillis());
        }
        save(scope, dto);
        return dto;
    }

    public ShortTermMemoryDTO appendQuestionAnswer(MemoryUserScopeDTO scope,
                                                   String sessionId,
                                                   String question,
                                                   String answer,
                                                   Integer round,
                                                   String temporaryConclusion) {
        ShortTermMemoryDTO dto = createSession(scope, sessionId);
        if (question != null && !question.trim().isEmpty()) {
            dto.getAskedQuestions().add(question.trim());
        }
        if (answer != null && !answer.trim().isEmpty()) {
            dto.getAnswers().add(answer.trim());
        }
        if (round != null) {
            dto.setCurrentRound(round);
        }
        if (temporaryConclusion != null && !temporaryConclusion.trim().isEmpty()) {
            dto.setTemporaryConclusion(temporaryConclusion.trim());
        }
        return updateSession(scope, dto);
    }

    public void clearSession(MemoryUserScopeDTO scope, String sessionId) {
        requireScope(scope);
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            stringRedisTemplate.delete(buildKey(scope.getPatientIdHash(), sessionId.trim()));
        }
    }

    public ShortTermMemoryDTO completeSession(MemoryUserScopeDTO scope, String sessionId, String summary) {
        ShortTermMemoryDTO dto = createSession(scope, sessionId);
        dto.setCompleted(true);
        if (summary != null && !summary.trim().isEmpty()) {
            dto.setTemporaryConclusion(summary.trim());
        } else if (dto.getTemporaryConclusion() == null || dto.getTemporaryConclusion().trim().isEmpty()) {
            dto.setTemporaryConclusion(buildSessionSummary(dto));
        }
        return updateSession(scope, dto);
    }

    public String buildSessionSummary(ShortTermMemoryDTO dto) {
        if (dto == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("本次预问诊摘要：");
        if (dto.getChiefComplaint() != null && !dto.getChiefComplaint().trim().isEmpty()) {
            builder.append("主诉为").append(dto.getChiefComplaint().trim()).append("；");
        }
        int questionCount = dto.getAskedQuestions() == null ? 0 : dto.getAskedQuestions().size();
        int answerCount = dto.getAnswers() == null ? 0 : dto.getAnswers().size();
        int count = Math.min(questionCount, answerCount);
        for (int i = 0; i < count; i++) {
            builder.append("第").append(i + 1).append("轮，用户描述：")
                    .append(truncate(dto.getAskedQuestions().get(i), 180))
                    .append("；系统建议：")
                    .append(truncate(dto.getAnswers().get(i), 260))
                    .append("。");
        }
        if (count == 0) {
            builder.append("暂无完整问答记录。");
        }
        return builder.toString();
    }

    public String buildKey(String patientIdHash, String sessionId) {
        return "memory:short:" + patientIdHash + ":" + sessionId;
    }

    private void save(MemoryUserScopeDTO scope, ShortTermMemoryDTO dto) {
        stringRedisTemplate.opsForValue().set(
                buildKey(scope.getPatientIdHash(), dto.getSessionId()),
                JSON.toJSONString(dto),
                memoryProperties.getShortTerm().getTtlHours(),
                TimeUnit.HOURS
        );
    }

    private String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.trim().isEmpty() ? UUID.randomUUID().toString() : sessionId.trim();
    }

    private long ttlMillis() {
        return TimeUnit.HOURS.toMillis(memoryProperties.getShortTerm().getTtlHours());
    }

    private void requireScope(MemoryUserScopeDTO scope) {
        if (scope == null || scope.getPatientIdHash() == null || scope.getPatientIdHash().trim().isEmpty()) {
            throw new BadRequestException("Cannot resolve current patient memory scope");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
