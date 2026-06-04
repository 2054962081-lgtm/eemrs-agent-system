package com.liu.eemrsagent.medicalrecord;

import com.liu.eemrsagent.common.ApiResponse;
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

    public MedicalRecordDraftController(MedicalRecordDraftService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ApiResponse<MedicalRecordDraftGenerateResponse> generate(
            @RequestBody MedicalRecordDraftGenerateRequest request
    ) {
        return ApiResponse.ok(service.generate(request));
    }

    @GetMapping("/latest")
    public ApiResponse<MedicalRecordDraftQueryResponse> latest(@RequestParam String patientId) {
        return ApiResponse.ok(service.findLatestByPatientId(patientId));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<MedicalRecordDraftQueryResponse> detail(@PathVariable String draftId) {
        return ApiResponse.ok(service.findById(draftId));
    }
}
