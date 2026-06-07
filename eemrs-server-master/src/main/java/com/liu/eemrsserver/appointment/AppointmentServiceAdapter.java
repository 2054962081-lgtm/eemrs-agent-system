package com.liu.eemrsserver.appointment;

import com.liu.eemrsserver.appointment.dto.AcceptAppointmentResponse;
import com.liu.eemrsserver.appointment.dto.CreateAppointmentRequest;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.GuahaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentServiceAdapter {
    @Autowired
    private GuahaoService guahaoService;

    public boolean create(CreateAppointmentRequest request, UserPrincipal currentUser) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.PATIENT) {
            throw new ForbiddenException("Only patients can create appointments");
        }
        RequestValidator.notBlank(request.getDepartment(), "department");
        RequestValidator.notBlank(request.getUserName(), "userName");
        RequestValidator.notBlank(request.getDoctorIdNumber(), "doctorIdNumber");
        if (request.getIdNumber() != null && !currentUser.getIdNumber().equals(request.getIdNumber())) {
            throw new ForbiddenException("Cannot create appointment for another patient");
        }

        GuahaoInfo guahaoInfo = new GuahaoInfo(request.getDepartment(), currentUser.getIdNumber(),
                request.getUserName(), null, request.getDoctorIdNumber());
        return guahaoService.add(guahaoInfo);
    }

    @Transactional
    public AcceptAppointmentResponse accept(String idNumber, UserPrincipal currentUser) {
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only doctors can accept appointments");
        }
        RequestValidator.notBlank(idNumber, "idNumber");
        RequestValidator.notBlank(currentUser.getIdNumber(), "doctorIdNumber");
        RequestValidator.notBlank(currentUser.getDepartment(), "doctor department");
        String patientIdNumber = idNumber.trim();
        String department = currentUser.getDepartment().trim();
        Waiting waiting = guahaoService.findWaitingAppointment(department, currentUser.getIdNumber(), patientIdNumber);
        if (waiting == null || waiting.getIdNumber() == null) {
            throw new BadRequestException("未找到该患者的候诊挂号记录");
        }
        PatientInfo patientInfo = guahaoService.getPatientInfo(idNumber);
        if (patientInfo == null || patientInfo.getIdNumber() == null) {
            throw new BadRequestException("Patient appointment not found");
        }
        return new AcceptAppointmentResponse(patientIdNumber, patientInfo);
    }
}
