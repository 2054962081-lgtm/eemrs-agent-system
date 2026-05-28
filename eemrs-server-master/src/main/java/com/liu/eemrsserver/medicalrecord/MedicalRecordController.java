package com.liu.eemrsserver.medicalrecord;

import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordQueryRequest;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordRequest;
import com.liu.eemrsserver.security.CurrentUser;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {
    @Autowired
    private MedicalRecordServiceAdapter medicalRecordServiceAdapter;

    @PostMapping
    public ApiResponse<Boolean> create(@RequestBody MedicalRecordRequest request,
                                       @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok("medical record handled", medicalRecordServiceAdapter.create(request, currentUser));
    }

    @GetMapping
    public ApiResponse<List<VisitInfo>> query(@RequestParam(value = "startTime", required = false) BigInteger startTime,
                                              @RequestParam(value = "endTime", required = false) BigInteger endTime,
                                              @RequestParam(value = "minAge", required = false) BigInteger minAge,
                                              @RequestParam(value = "maxAge", required = false) BigInteger maxAge,
                                              @RequestParam(value = "patientIdNumber", required = false) String patientIdNumber,
                                              @RequestParam(value = "doctorIdNumber", required = false) String doctorIdNumber,
                                              @RequestParam(value = "doctorName", required = false) String doctorName,
                                              @RequestParam(value = "department", required = false) String department,
                                              @CurrentUser UserPrincipal currentUser) {
        MedicalRecordQueryRequest request = new MedicalRecordQueryRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setMinAge(minAge);
        request.setMaxAge(maxAge);
        request.setPatientIdNumber(patientIdNumber);
        request.setDoctorIdNumber(doctorIdNumber);
        request.setDoctorName(doctorName);
        request.setDepartment(department);
        return ApiResponse.ok(medicalRecordServiceAdapter.query(request, currentUser));
    }
}
