package com.liu.eemrsserver.memory;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.mapper.UserHealthProfileMapper;
import com.liu.eemrsserver.mapper.UserLongTermMemoryMapper;
import com.liu.eemrsserver.memory.domain.UserHealthProfile;
import com.liu.eemrsserver.memory.domain.UserLongTermMemory;
import com.liu.eemrsserver.memory.dto.HealthProfileDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryCandidateDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryConfirmRequest;
import com.liu.eemrsserver.memory.dto.LongTermMemoryCreateRequest;
import com.liu.eemrsserver.memory.dto.LongTermMemoryDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryUpdateRequest;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class LongTermMemoryService {
    private static final Logger logger = Logger.getLogger(LongTermMemoryService.class);
    private static final String SOURCE_TYPE_LONG_TERM = "long_term_memory";

    @Autowired
    private UserHealthProfileMapper healthProfileMapper;
    @Autowired
    private UserLongTermMemoryMapper longTermMemoryMapper;
    @Autowired
    private UserMemoryVectorService userMemoryVectorService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Map<String, Object> getLongTermMemory(MemoryUserScopeDTO scope) {
        return getLongTermMemoryContext(scope);
    }

    public Map<String, Object> getLongTermMemoryContext(MemoryUserScopeDTO scope) {
        requireScope(scope);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("profile", profileToMap(healthProfileMapper.findByPatientIdHash(scope.getPatientIdHash())));
        List<UserLongTermMemory> items = longTermMemoryMapper.findActiveByPatientIdHash(scope.getPatientIdHash());
        context.put("allergyHistory", summarize(items, "allergy"));
        context.put("pastHistory", summarize(items, "past_history"));
        context.put("chronicDiseaseHistory", summarize(items, "chronic_disease"));
        context.put("familyHistory", summarize(items, "family_history"));
        context.put("surgeryHistory", summarize(items, "surgery_history"));
        context.put("longTermMedication", summarize(items, "medication"));
        context.put("specialStatus", summarize(items, "special_status"));
        context.put("items", items.stream().map(this::toDTO).collect(Collectors.toList()));
        return context;
    }

    public HealthProfileDTO getHealthProfile(MemoryUserScopeDTO scope) {
        requireScope(scope);
        return toDTO(healthProfileMapper.findByPatientIdHash(scope.getPatientIdHash()));
    }

    @Transactional
    public HealthProfileDTO saveOrUpdateHealthProfile(MemoryUserScopeDTO scope, HealthProfileDTO request) {
        requireScope(scope);
        if (request == null) {
            throw new BadRequestException("profile request is required");
        }
        UserHealthProfile profile = toEntity(request);
        profile.setPatientIdHash(scope.getPatientIdHash());
        profile.setIdNumberHash(scope.getPatientIdHash());
        profile.setActive(defaultInt(profile.getActive(), 1));
        profile.setConfirmed(defaultInt(profile.getConfirmed(), 1));
        if (isBlank(profile.getSource())) {
            profile.setSource("user_confirmed");
        }
        UserHealthProfile existing = healthProfileMapper.findByPatientIdHash(scope.getPatientIdHash());
        if (existing == null) {
            healthProfileMapper.insert(profile);
        } else {
            healthProfileMapper.updateByPatientIdHash(profile);
        }
        logger.info("Long-term health profile saved, patientHashPresent=true");
        return getHealthProfile(scope);
    }

    @Transactional
    public void deleteHealthProfile(MemoryUserScopeDTO scope) {
        requireScope(scope);
        healthProfileMapper.softDeleteByPatientIdHash(scope.getPatientIdHash());
        logger.info("Long-term health profile soft deleted, patientHashPresent=true");
    }

    public List<LongTermMemoryDTO> listLongTermMemories(MemoryUserScopeDTO scope, String memoryType) {
        requireScope(scope);
        List<UserLongTermMemory> items = isBlank(memoryType)
                ? longTermMemoryMapper.findActiveByPatientIdHash(scope.getPatientIdHash())
                : longTermMemoryMapper.findActiveByPatientIdHashAndType(scope.getPatientIdHash(), memoryType.trim());
        return items.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LongTermMemoryDTO addLongTermMemory(MemoryUserScopeDTO scope, LongTermMemoryCreateRequest request) {
        requireScope(scope);
        UserLongTermMemory memory = toEntity(request);
        memory.setPatientIdHash(scope.getPatientIdHash());
        validateMemory(memory);
        memory.setActive(1);
        memory.setConfirmed(defaultInt(memory.getConfirmed(), 1));
        if (isBlank(memory.getSource())) {
            memory.setSource("user_confirmed");
        }
        int similarCount = longTermMemoryMapper.existsSimilarMemory(
                scope.getPatientIdHash(),
                memory.getMemoryType(),
                memory.getMemoryKey(),
                memory.getMemoryValue()
        );
        if (similarCount > 0) {
            throw new BadRequestException("Similar long-term memory already exists");
        }
        longTermMemoryMapper.insert(memory);
        syncLongTermMemoryToMilvus(scope, memory, request == null ? null : request.getDepartment());
        logger.info("Long-term memory inserted, memoryType=" + memory.getMemoryType() + ", id=" + memory.getId());
        return toDTO(memory);
    }

    @Transactional
    public LongTermMemoryDTO updateLongTermMemory(MemoryUserScopeDTO scope, Long id, LongTermMemoryUpdateRequest request) {
        requireScope(scope);
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        UserLongTermMemory memory = toEntity(request);
        memory.setId(id);
        memory.setPatientIdHash(scope.getPatientIdHash());
        validateMemory(memory);
        memory.setActive(defaultInt(memory.getActive(), 1));
        memory.setConfirmed(defaultInt(memory.getConfirmed(), 1));
        if (isBlank(memory.getSource())) {
            memory.setSource("user_confirmed");
        }
        int updated = longTermMemoryMapper.updateByIdAndPatientIdHash(memory);
        if (updated <= 0) {
            throw new BadRequestException("Long-term memory not found");
        }
        syncLongTermMemoryToMilvus(scope, memory, request == null ? null : request.getDepartment());
        logger.info("Long-term memory updated, memoryType=" + memory.getMemoryType() + ", id=" + id);
        return toDTO(memory);
    }

    @Transactional
    public void deleteLongTermMemory(MemoryUserScopeDTO scope, Long id) {
        requireScope(scope);
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        int updated = longTermMemoryMapper.softDeleteByIdAndPatientIdHash(id, scope.getPatientIdHash());
        if (updated <= 0) {
            throw new BadRequestException("Long-term memory not found");
        }
        userMemoryVectorService.deleteMemoryBySourceId(scope, SOURCE_TYPE_LONG_TERM, String.valueOf(id));
        logger.info("Long-term memory soft deleted, id=" + id);
    }

    public LongTermMemoryCandidateDTO saveCandidate(MemoryUserScopeDTO scope, String sessionId, LongTermMemoryCandidateDTO candidate) {
        requireScope(scope);
        if (isBlank(sessionId)) {
            throw new BadRequestException("sessionId is required");
        }
        if (candidate == null) {
            throw new BadRequestException("candidate is required");
        }
        if (isBlank(candidate.getCandidateId())) {
            candidate.setCandidateId(UUID.randomUUID().toString());
        }
        candidate.setSessionId(sessionId.trim());
        candidate.setNeedConfirm(true);
        List<LongTermMemoryCandidateDTO> candidates = getCandidates(scope, sessionId);
        candidates.removeIf(item -> candidate.getCandidateId().equals(item.getCandidateId()));
        candidates.add(candidate);
        stringRedisTemplate.opsForValue().set(candidateKey(scope, sessionId), JSON.toJSONString(candidates), 24, TimeUnit.HOURS);
        logger.info("Long-term memory candidate saved, sessionIdPresent=true");
        return candidate;
    }

    public List<LongTermMemoryCandidateDTO> getCandidates(MemoryUserScopeDTO scope, String sessionId) {
        requireScope(scope);
        if (isBlank(sessionId)) {
            return new ArrayList<>();
        }
        String json = stringRedisTemplate.opsForValue().get(candidateKey(scope, sessionId));
        if (isBlank(json)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(json, LongTermMemoryCandidateDTO.class);
    }

    @Transactional
    public LongTermMemoryDTO confirmCandidate(MemoryUserScopeDTO scope,
                                              String candidateId,
                                              LongTermMemoryConfirmRequest request) {
        requireScope(scope);
        String sessionId = request == null ? null : request.getSessionId();
        if (isBlank(candidateId) || isBlank(sessionId)) {
            throw new BadRequestException("candidateId and sessionId are required");
        }
        List<LongTermMemoryCandidateDTO> candidates = getCandidates(scope, sessionId);
        LongTermMemoryCandidateDTO candidate = candidates.stream()
                .filter(item -> candidateId.equals(item.getCandidateId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("candidate not found"));
        LongTermMemoryCreateRequest createRequest = new LongTermMemoryCreateRequest();
        createRequest.setMemoryType(candidate.getMemoryType());
        createRequest.setMemoryKey(candidate.getMemoryKey());
        createRequest.setMemoryValue(candidate.getMemoryValue());
        createRequest.setSeverity(candidate.getSeverity());
        createRequest.setRelation(candidate.getRelation());
        createRequest.setEvidence(candidate.getEvidence());
        createRequest.setSource("user_confirmed");
        createRequest.setConfirmed(1);
        createRequest.setDepartment(request.getDepartment());
        LongTermMemoryDTO saved = addLongTermMemory(scope, createRequest);
        candidates.removeIf(item -> candidateId.equals(item.getCandidateId()));
        stringRedisTemplate.opsForValue().set(candidateKey(scope, sessionId), JSON.toJSONString(candidates), 24, TimeUnit.HOURS);
        return saved;
    }

    public void rejectCandidate(MemoryUserScopeDTO scope, String sessionId, String candidateId) {
        requireScope(scope);
        if (isBlank(sessionId) || isBlank(candidateId)) {
            throw new BadRequestException("sessionId and candidateId are required");
        }
        List<LongTermMemoryCandidateDTO> candidates = getCandidates(scope, sessionId);
        candidates.removeIf(item -> candidateId.equals(item.getCandidateId()));
        stringRedisTemplate.opsForValue().set(candidateKey(scope, sessionId), JSON.toJSONString(candidates), 24, TimeUnit.HOURS);
        logger.info("Long-term memory candidate rejected, sessionIdPresent=true");
    }

    private void syncLongTermMemoryToMilvus(MemoryUserScopeDTO scope, UserLongTermMemory memory, String department) {
        userMemoryVectorService.upsertMemoryVector(
                scope,
                buildVectorText(memory),
                "long",
                SOURCE_TYPE_LONG_TERM,
                String.valueOf(memory.getId()),
                isBlank(department) ? "general" : department.trim(),
                System.currentTimeMillis()
        );
    }

    private String buildVectorText(UserLongTermMemory memory) {
        StringBuilder builder = new StringBuilder();
        builder.append("长期健康档案：");
        builder.append(memoryTypeLabel(memory.getMemoryType())).append("，");
        if (!isBlank(memory.getMemoryKey())) {
            builder.append("关键词为").append(memory.getMemoryKey()).append("，");
        }
        builder.append("内容为").append(memory.getMemoryValue());
        if (!isBlank(memory.getSeverity())) {
            builder.append("，严重程度为").append(memory.getSeverity());
        }
        if (!isBlank(memory.getRelation())) {
            builder.append("，关系为").append(memory.getRelation());
        }
        return builder.toString();
    }

    private Map<String, Object> profileToMap(UserHealthProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gender", valueOrUnknown(profile == null ? null : profile.getGender()));
        map.put("birthDate", profile == null || profile.getBirthDate() == null ? "未记录" : new SimpleDateFormat("yyyy-MM-dd").format(profile.getBirthDate()));
        map.put("heightCm", profile == null || profile.getHeightCm() == null ? "未记录" : profile.getHeightCm());
        map.put("weightKg", profile == null || profile.getWeightKg() == null ? "未记录" : profile.getWeightKg());
        map.put("bloodType", valueOrUnknown(profile == null ? null : profile.getBloodType()));
        map.put("specialStatus", valueOrUnknown(profile == null ? null : profile.getSpecialStatus()));
        return map;
    }

    private String summarize(List<UserLongTermMemory> items, String memoryType) {
        String summary = items.stream()
                .filter(item -> memoryType.equals(item.getMemoryType()))
                .map(this::memoryLine)
                .collect(Collectors.joining("；"));
        return isBlank(summary) ? "未记录" : summary;
    }

    private String memoryLine(UserLongTermMemory item) {
        StringBuilder builder = new StringBuilder();
        if (!isBlank(item.getMemoryKey())) {
            builder.append(item.getMemoryKey()).append("：");
        }
        builder.append(item.getMemoryValue());
        if (!isBlank(item.getSeverity())) {
            builder.append("（").append(item.getSeverity()).append("）");
        }
        return builder.toString();
    }

    private UserLongTermMemory toEntity(LongTermMemoryCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("long-term memory request is required");
        }
        UserLongTermMemory memory = new UserLongTermMemory();
        memory.setMemoryType(trim(request.getMemoryType()));
        memory.setMemoryKey(trim(request.getMemoryKey()));
        memory.setMemoryValue(trim(request.getMemoryValue()));
        memory.setSeverity(trim(request.getSeverity()));
        memory.setRelation(trim(request.getRelation()));
        memory.setEvidence(trim(request.getEvidence()));
        memory.setSource(trim(request.getSource()));
        memory.setConfirmed(request.getConfirmed());
        return memory;
    }

    private UserLongTermMemory toEntity(LongTermMemoryUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("long-term memory request is required");
        }
        UserLongTermMemory memory = new UserLongTermMemory();
        memory.setMemoryType(trim(request.getMemoryType()));
        memory.setMemoryKey(trim(request.getMemoryKey()));
        memory.setMemoryValue(trim(request.getMemoryValue()));
        memory.setSeverity(trim(request.getSeverity()));
        memory.setRelation(trim(request.getRelation()));
        memory.setEvidence(trim(request.getEvidence()));
        memory.setSource(trim(request.getSource()));
        memory.setConfirmed(request.getConfirmed());
        return memory;
    }

    private UserHealthProfile toEntity(HealthProfileDTO dto) {
        UserHealthProfile profile = new UserHealthProfile();
        profile.setGender(trim(dto.getGender()));
        profile.setBirthDate(dto.getBirthDate());
        profile.setHeightCm(dto.getHeightCm());
        profile.setWeightKg(dto.getWeightKg());
        profile.setBloodType(trim(dto.getBloodType()));
        profile.setSpecialStatus(trim(dto.getSpecialStatus()));
        profile.setSource(trim(dto.getSource()));
        profile.setConfirmed(dto.getConfirmed());
        profile.setActive(dto.getActive());
        return profile;
    }

    private LongTermMemoryDTO toDTO(UserLongTermMemory memory) {
        if (memory == null) {
            return null;
        }
        LongTermMemoryDTO dto = new LongTermMemoryDTO();
        dto.setId(memory.getId());
        dto.setPatientIdHash(memory.getPatientIdHash());
        dto.setMemoryType(memory.getMemoryType());
        dto.setMemoryKey(memory.getMemoryKey());
        dto.setMemoryValue(memory.getMemoryValue());
        dto.setSeverity(memory.getSeverity());
        dto.setRelation(memory.getRelation());
        dto.setEvidence(memory.getEvidence());
        dto.setSource(memory.getSource());
        dto.setConfirmed(memory.getConfirmed());
        dto.setActive(memory.getActive());
        dto.setCreatedAt(memory.getCreatedAt());
        dto.setUpdatedAt(memory.getUpdatedAt());
        return dto;
    }

    private HealthProfileDTO toDTO(UserHealthProfile profile) {
        HealthProfileDTO dto = new HealthProfileDTO();
        if (profile == null) {
            return dto;
        }
        dto.setId(profile.getId());
        dto.setPatientIdHash(profile.getPatientIdHash());
        dto.setIdNumberHash(profile.getIdNumberHash());
        dto.setGender(profile.getGender());
        dto.setBirthDate(profile.getBirthDate());
        dto.setHeightCm(profile.getHeightCm());
        dto.setWeightKg(profile.getWeightKg());
        dto.setBloodType(profile.getBloodType());
        dto.setSpecialStatus(profile.getSpecialStatus());
        dto.setSource(profile.getSource());
        dto.setConfirmed(profile.getConfirmed());
        dto.setActive(profile.getActive());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());
        return dto;
    }

    private void validateMemory(UserLongTermMemory memory) {
        if (isBlank(memory.getMemoryType())) {
            throw new BadRequestException("memoryType is required");
        }
        if (isBlank(memory.getMemoryValue())) {
            throw new BadRequestException("memoryValue is required");
        }
    }

    private String candidateKey(MemoryUserScopeDTO scope, String sessionId) {
        return "memory:long:candidate:" + scope.getPatientIdHash() + ":" + sessionId.trim();
    }

    private void requireScope(MemoryUserScopeDTO scope) {
        if (scope == null || isBlank(scope.getPatientIdHash())) {
            throw new BadRequestException("Cannot resolve current patient memory scope");
        }
    }

    private String memoryTypeLabel(String memoryType) {
        if ("allergy".equals(memoryType)) {
            return "过敏史";
        }
        if ("family_history".equals(memoryType)) {
            return "家族史";
        }
        if ("past_history".equals(memoryType)) {
            return "既往史";
        }
        if ("chronic_disease".equals(memoryType)) {
            return "慢性病史";
        }
        if ("surgery_history".equals(memoryType)) {
            return "手术史";
        }
        if ("medication".equals(memoryType)) {
            return "长期用药";
        }
        if ("special_status".equals(memoryType)) {
            return "特殊状态";
        }
        return valueOrUnknown(memoryType);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String valueOrUnknown(String value) {
        return isBlank(value) ? "未记录" : value.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
