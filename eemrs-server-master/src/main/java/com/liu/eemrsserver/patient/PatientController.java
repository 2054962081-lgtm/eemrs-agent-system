package com.liu.eemrsserver.patient;

import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.security.CurrentUser;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    @Autowired
    private PatientServiceAdapter patientServiceAdapter;

    @GetMapping("/{idNumber}")
    public ApiResponse<PatientInfo> getByIdNumber(@PathVariable("idNumber") String idNumber) {
        return ApiResponse.ok(patientServiceAdapter.getByIdNumber(idNumber));
    }

    @PutMapping("/me")
    public ApiResponse<Boolean> update(@RequestBody PatientInfo patientInfo, @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok("patient info handled", patientServiceAdapter.update(patientInfo, currentUser));
    }
}
