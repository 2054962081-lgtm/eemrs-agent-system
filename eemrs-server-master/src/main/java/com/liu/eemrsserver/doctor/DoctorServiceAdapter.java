package com.liu.eemrsserver.doctor;

import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.utils.NewPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DoctorServiceAdapter {
    @Autowired
    private DataOpService dataOpService;
    @Autowired
    private GuahaoService guahaoService;

    public List<DoctorInfo> listByDepartment(String department) {
        RequestValidator.notBlank(department, "department");
        return dataOpService.getDocName(department);
    }

    public List<Waiting> waitingList(String department, String doctorIdNumber, UserPrincipal currentUser) {
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only doctors can query waiting list");
        }
        RequestValidator.notBlank(department, "department");
        if (doctorIdNumber != null && !doctorIdNumber.trim().isEmpty()
                && !currentUser.getIdNumber().equals(doctorIdNumber)) {
            throw new ForbiddenException("Cannot query another doctor's waiting list");
        }
        NewPair pair = new NewPair(department, currentUser.getIdNumber());
        return guahaoService.query(pair);
    }

    public DoctorInfo getDoctorInfo(UserPrincipal currentUser) {
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only doctors can query doctor profile");
        }
        return dataOpService.sendDocInfo(currentUser.getIdNumber());
    }
}
