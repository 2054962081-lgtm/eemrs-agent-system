package com.liu.eemrsserver.memory;

import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.memory.dto.CompleteSessionRequest;
import com.liu.eemrsserver.memory.dto.HealthProfileDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryCandidateDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryConfirmRequest;
import com.liu.eemrsserver.memory.dto.LongTermMemoryCreateRequest;
import com.liu.eemrsserver.memory.dto.LongTermMemoryDTO;
import com.liu.eemrsserver.memory.dto.LongTermMemoryUpdateRequest;
import com.liu.eemrsserver.memory.dto.MemoryContextDTO;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import com.liu.eemrsserver.memory.dto.ShortTermMemoryDTO;
import com.liu.eemrsserver.memory.dto.ShortTermQuestionAnswerRequest;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private static final Logger logger = Logger.getLogger(MemoryController.class);

    @Autowired
    private PatientIdentityResolver patientIdentityResolver;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private ShortTermMemoryService shortTermMemoryService;
    @Autowired
    private UserMemoryVectorService userMemoryVectorService;
    @Autowired
    private LongTermMemoryService longTermMemoryService;

    @GetMapping("/scope")
    public ApiResponse<MemoryUserScopeDTO> scope() {
        return ApiResponse.ok(patientIdentityResolver.resolveCurrentPatientScope());
    }

    @GetMapping("/context")
    public ApiResponse<MemoryContextDTO> context(@RequestParam(value = "sessionId", required = false) String sessionId,
                                                 @RequestParam(value = "query", required = false) String query) {
        return ApiResponse.ok(memoryService.getContext(sessionId, query));
    }

    @PostMapping("/short/session")
    public ApiResponse<ShortTermMemoryDTO> createSession(@RequestBody(required = false) ShortTermMemoryDTO request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        String sessionId = request == null ? null : request.getSessionId();
        String chiefComplaint = request == null ? null : request.getChiefComplaint();
        return ApiResponse.ok(shortTermMemoryService.createSession(scope, sessionId, chiefComplaint));
    }

    @PostMapping("/short/session/{sessionId}/qa")
    public ApiResponse<ShortTermMemoryDTO> appendQuestionAnswer(@PathVariable("sessionId") String sessionId,
                                                               @RequestBody ShortTermQuestionAnswerRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(shortTermMemoryService.appendQuestionAnswer(
                scope,
                sessionId,
                request == null ? null : request.getQuestion(),
                request == null ? null : request.getAnswer(),
                request == null ? null : request.getRound(),
                request == null ? null : request.getTemporaryConclusion()
        ));
    }

    @PostMapping("/short/session/{sessionId}/complete")
    public ApiResponse<ShortTermMemoryDTO> completeSession(@PathVariable("sessionId") String sessionId,
                                                          @RequestBody(required = false) CompleteSessionRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        ShortTermMemoryDTO completed = shortTermMemoryService.completeSession(
                scope,
                sessionId,
                request == null ? null : request.getSummary()
        );
        userMemoryVectorService.upsertMemoryVector(
                scope,
                completed.getTemporaryConclusion(),
                "short_archive",
                "pre_inquiry_summary",
                request == null || request.getSourceId() == null || request.getSourceId().trim().isEmpty()
                        ? sessionId
                        : request.getSourceId(),
                request == null ? null : request.getDepartment(),
                System.currentTimeMillis()
        );
        logger.info("Short-term memory session completed, sessionIdPresent=" + (sessionId != null && !sessionId.trim().isEmpty()));
        return ApiResponse.ok(completed);
    }

    @GetMapping("/long/profile")
    public ApiResponse<HealthProfileDTO> getLongProfile() {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.getHealthProfile(scope));
    }

    @PostMapping("/long/profile")
    public ApiResponse<HealthProfileDTO> saveLongProfile(@RequestBody HealthProfileDTO request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.saveOrUpdateHealthProfile(scope, request));
    }

    @DeleteMapping("/long/profile")
    public ApiResponse<Boolean> deleteLongProfile() {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        longTermMemoryService.deleteHealthProfile(scope);
        return ApiResponse.ok(true);
    }

    @GetMapping("/long/items")
    public ApiResponse<List<LongTermMemoryDTO>> listLongItems(
            @RequestParam(value = "memoryType", required = false) String memoryType) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.listLongTermMemories(scope, memoryType));
    }

    @PostMapping("/long/items")
    public ApiResponse<LongTermMemoryDTO> addLongItem(@RequestBody LongTermMemoryCreateRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.addLongTermMemory(scope, request));
    }

    @PutMapping("/long/items/{id}")
    public ApiResponse<LongTermMemoryDTO> updateLongItem(@PathVariable("id") Long id,
                                                        @RequestBody LongTermMemoryUpdateRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.updateLongTermMemory(scope, id, request));
    }

    @DeleteMapping("/long/items/{id}")
    public ApiResponse<Boolean> deleteLongItem(@PathVariable("id") Long id) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        longTermMemoryService.deleteLongTermMemory(scope, id);
        return ApiResponse.ok(true);
    }

    @PostMapping("/long/candidates")
    public ApiResponse<LongTermMemoryCandidateDTO> saveCandidate(
            @RequestParam("sessionId") String sessionId,
            @RequestBody LongTermMemoryCandidateDTO request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.saveCandidate(scope, sessionId, request));
    }

    @GetMapping("/long/candidates")
    public ApiResponse<List<LongTermMemoryCandidateDTO>> getCandidates(@RequestParam("sessionId") String sessionId) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.getCandidates(scope, sessionId));
    }

    @PostMapping("/long/candidates/{candidateId}/confirm")
    public ApiResponse<LongTermMemoryDTO> confirmCandidate(@PathVariable("candidateId") String candidateId,
                                                          @RequestBody LongTermMemoryConfirmRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        return ApiResponse.ok(longTermMemoryService.confirmCandidate(scope, candidateId, request));
    }

    @PostMapping("/long/candidates/{candidateId}/reject")
    public ApiResponse<Boolean> rejectCandidate(@PathVariable("candidateId") String candidateId,
                                                @RequestBody LongTermMemoryConfirmRequest request) {
        MemoryUserScopeDTO scope = patientIdentityResolver.resolveCurrentPatientScope();
        longTermMemoryService.rejectCandidate(scope, request == null ? null : request.getSessionId(), candidateId);
        return ApiResponse.ok(true);
    }
}
