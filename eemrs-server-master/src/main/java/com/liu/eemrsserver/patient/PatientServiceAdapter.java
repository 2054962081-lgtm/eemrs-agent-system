package com.liu.eemrsserver.patient;

import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.service.PatientInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PatientServiceAdapter {
    @Autowired
    private GuahaoService guahaoService;
    @Autowired
    private PatientInfoService patientInfoService;

    public PatientInfo getByIdNumber(String idNumber) {
        RequestValidator.notBlank(idNumber, "idNumber");
        return guahaoService.sendInfo(idNumber);
    }

    public boolean update(PatientInfo patientInfo, UserPrincipal currentUser) {
        RequestValidator.notNull(patientInfo, "patientInfo");
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.PATIENT) {
            throw new ForbiddenException("Only patients can update patient profile");
        }
        if (patientInfo.getIdNumber() != null && !currentUser.getIdNumber().equals(patientInfo.getIdNumber())) {
            throw new ForbiddenException("Cannot update another patient's profile");
        }
        patientInfo.setIdNumber(currentUser.getIdNumber());
        return patientInfoService.modifyInfo(patientInfo);
    }
}
