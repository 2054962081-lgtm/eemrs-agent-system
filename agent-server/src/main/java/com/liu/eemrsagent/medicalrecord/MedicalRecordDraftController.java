package com.liu.eemrsagent.medicalrecord;

import com.liu.eemrsagent.common.ApiResponse;
import com.liu.eemrsagent.security.AgentUserPrincipal;
import com.liu.eemrsagent.security.JwtAgentUserResolver;
import com.liu.eemrsagent.trace.TraceHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/medical-record-drafts")
public class MedicalRecordDraftController {

    private final MedicalRecordDraftService service;
    private final JwtAgentUserResolver userResolver;

    public MedicalRecordDraftController(MedicalRecordDraftService service, JwtAgentUserResolver userResolver) {
        this.service = service;
        this.userResolver = userResolver;
    }

    @PostMapping("/generate")
    public ApiResponse<MedicalRecordDraftGenerateResponse> generate(
            @RequestBody MedicalRecordDraftGenerateRequest request
    ) {
        return ApiResponse.ok(service.generate(request));
    }

    @GetMapping("/latest")
    public ApiResponse<MedicalRecordDraftQueryResponse> latest(@RequestParam String patientId,
                                                               HttpServletRequest request) {
        return ApiResponse.ok(service.findLatestByPatientIdForDoctor(patientId, currentUser(request), traceId(request)));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<MedicalRecordDraftQueryResponse> detail(@PathVariable String draftId,
                                                               HttpServletRequest request) {
        return ApiResponse.ok(service.findByIdForDoctor(draftId, currentUser(request), traceId(request)));
    }

    @PostMapping("/{draftId}/edits")
    public ApiResponse<MedicalRecordDraftActionResponse> saveEdit(@PathVariable String draftId,
                                                                   @RequestBody MedicalRecordDraftEditRequest body,
                                                                   HttpServletRequest request) throws Exception {
        return ApiResponse.ok(service.saveEdit(draftId, body, currentUser(request), traceId(request)));
    }

    @PostMapping("/{draftId}/accept")
    public ApiResponse<MedicalRecordDraftActionResponse> accept(@PathVariable String draftId,
                                                                @RequestBody(required = false) MedicalRecordDraftReviewRequest body,
                                                                HttpServletRequest request) throws Exception {
        return ApiResponse.ok(service.accept(draftId, body, currentUser(request), traceId(request)));
    }

    @PostMapping("/{draftId}/partial-accept")
    public ApiResponse<MedicalRecordDraftActionResponse> partialAccept(@PathVariable String draftId,
                                                                       @RequestBody MedicalRecordDraftReviewRequest body,
                                                                       HttpServletRequest request) throws Exception {
        return ApiResponse.ok(service.partialAccept(draftId, body, currentUser(request), traceId(request)));
    }

    @PostMapping("/{draftId}/reject")
    public ApiResponse<MedicalRecordDraftActionResponse> reject(@PathVariable String draftId,
                                                                @RequestBody MedicalRecordDraftReviewRequest body,
                                                                HttpServletRequest request) throws Exception {
        return ApiResponse.ok(service.reject(draftId, body, currentUser(request), traceId(request)));
    }

    @PostMapping("/{draftId}/apply")
    public ApiResponse<MedicalRecordDraftActionResponse> apply(@PathVariable String draftId,
                                                               @RequestBody(required = false) MedicalRecordDraftApplyRequest body,
                                                               HttpServletRequest request) throws Exception {
        return ApiResponse.ok(service.apply(draftId, body, currentUser(request),
                request.getHeader("Authorization"), traceId(request)));
    }

    @GetMapping("/{draftId}/history")
    public ApiResponse<MedicalRecordDraftHistoryResponse> history(@PathVariable String draftId,
                                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.history(draftId, currentUser(request)));
    }

    @GetMapping("/{draftId}/status")
    public ApiResponse<MedicalRecordDraftStatusResponse> status(@PathVariable String draftId,
                                                                HttpServletRequest request) {
        return ApiResponse.ok(service.status(draftId, currentUser(request)));
    }

    private AgentUserPrincipal currentUser(HttpServletRequest request) {
        return userResolver.resolve(request.getHeader("Authorization"));
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader(TraceHeaders.TRACE_ID);
        return traceId == null || traceId.isBlank() ? null : traceId.trim();
    }
}
