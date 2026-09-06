package com.liu.eemrsserver.appointment;

import com.liu.eemrsserver.appointment.dto.AcceptAppointmentResponse;
import com.liu.eemrsserver.appointment.dto.AgentAppointmentRequest;
import com.liu.eemrsserver.appointment.dto.AgentAppointmentResponse;
import com.liu.eemrsserver.appointment.dto.CreateAppointmentRequest;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.service.GuahaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentServiceAdapter {
    @Autowired
    private GuahaoService guahaoService;
    @Autowired
    private DataOpService dataOpService;

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

    public AgentAppointmentResponse createByAgent(AgentAppointmentRequest request, UserPrincipal currentUser) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.PATIENT) {
            throw new ForbiddenException("Only patients can create appointments");
        }
        RequestValidator.notBlank(request.getDepartment(), "department");
        RequestValidator.notBlank(request.getDoctorId(), "doctorId");

        String department = request.getDepartment().trim();
        String doctorId = request.getDoctorId().trim();
        DoctorInfo doctorInfo = dataOpService.sendDocInfo(doctorId);
        if (doctorInfo == null || doctorInfo.getIdNumber() == null || doctorInfo.getIdNumber().trim().isEmpty()) {
            throw new BadRequestException("医生不存在，无法挂号");
        }
        if (doctorInfo.getDepartment() == null || !department.equals(doctorInfo.getDepartment().trim())) {
            throw new BadRequestException("医生科室与推荐科室不匹配，已拒绝挂号");
        }

        PatientInfo patientInfo = guahaoService.getPatientInfo(currentUser.getIdNumber());
        if (patientInfo == null || patientInfo.getIdNumber() == null || patientInfo.getIdNumber().trim().isEmpty()) {
            throw new BadRequestException("当前登录患者信息不存在，无法挂号");
        }
        String patientName = patientInfo.getUserName() == null || patientInfo.getUserName().trim().isEmpty()
                ? "患者"
                : patientInfo.getUserName().trim();

        GuahaoInfo guahaoInfo = new GuahaoInfo(department, currentUser.getIdNumber(), patientName, null, doctorId);
        boolean created = guahaoService.add(guahaoInfo);
        if (!created) {
            throw new BadRequestException("挂号记录写入失败，请稍后重试");
        }
        long visitTime = System.currentTimeMillis();
        String source = request.getSource() == null || request.getSource().trim().isEmpty()
                ? "DEEP_INQUIRY"
                : request.getSource().trim();
        return new AgentAppointmentResponse(true, department, doctorId, doctorInfo.getUserName(),
                currentUser.getIdNumber(), patientName, "待就诊", visitTime, source);
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
