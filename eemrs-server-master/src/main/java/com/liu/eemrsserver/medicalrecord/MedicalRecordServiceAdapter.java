package com.liu.eemrsserver.medicalrecord;

import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordQueryRequest;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordRequest;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.utils.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

@Service
public class MedicalRecordServiceAdapter {
    @Autowired
    private DataOpService dataOpService;
    @Autowired
    private GuahaoService guahaoService;

    public boolean create(MedicalRecordRequest request, UserPrincipal currentUser) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notNull(currentUser, "currentUser");
        if (currentUser.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only doctors can create medical records");
        }
        RequestValidator.notBlank(request.getDepartment(), "department");
        RequestValidator.notBlank(request.getConditionDescription(), "conditionDescription");
        RequestValidator.notBlank(request.getPatientIdNumber(), "patientIdNumber");
        RequestValidator.notBlank(request.getDPk(), "dPk");
        RequestValidator.notBlank(request.getSignature(), "signature");
        if (request.getDoctorIdNumber() != null && !currentUser.getIdNumber().equals(request.getDoctorIdNumber())) {
            throw new ForbiddenException("Cannot create medical record as another doctor");
        }
        PatientInfo patientInfo = guahaoService.getPatientInfo(request.getPatientIdNumber());
        if (patientInfo == null || patientInfo.getIdNumber() == null) {
            throw new ForbiddenException("Patient does not exist or is not available for consultation");
        }
        request.setDoctorIdNumber(currentUser.getIdNumber());
        ensureVisitTime(request);

        VisitInfo visitInfo = toVisitInfo(request);
        boolean inserted = dataOpService.insertInto(visitInfo);
        if (inserted) {
            guahaoService.delectGuaHao(request.getPatientIdNumber());
        }
        return inserted;
    }

    public List<VisitInfo> query(MedicalRecordQueryRequest request, UserPrincipal currentUser) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notNull(currentUser, "currentUser");
        applyQueryScope(request, currentUser);
        QueryConditions queryConditions = new QueryConditions();
        queryConditions.setPatientIdNumber(blankToNull(request.getPatientIdNumber()));
        queryConditions.setDoctorIdNumber(blankToNull(request.getDoctorIdNumber()));
        queryConditions.setDoctorName(blankToNull(request.getDoctorName()));
        queryConditions.setDepartment(blankToNull(request.getDepartment()));
        queryConditions.setTimeInterval(toInterval(request.getStartTime(), request.getEndTime()));
        queryConditions.setAgeInterval(toInterval(request.getMinAge(), request.getMaxAge()));
        return dataOpService.query(queryConditions);
    }

    private void applyQueryScope(MedicalRecordQueryRequest request, UserPrincipal currentUser) {
        if (currentUser.getRole() == Role.PATIENT) {
            if (request.getPatientIdNumber() != null
                    && !request.getPatientIdNumber().trim().isEmpty()
                    && !currentUser.getIdNumber().equals(request.getPatientIdNumber())) {
                throw new ForbiddenException("Patients can only query their own medical records");
            }
            request.setPatientIdNumber(currentUser.getIdNumber());
            request.setDoctorIdNumber(null);
            return;
        }
        if (currentUser.getRole() == Role.DOCTOR) {
            if (request.getDoctorIdNumber() != null
                    && !request.getDoctorIdNumber().trim().isEmpty()
                    && !currentUser.getIdNumber().equals(request.getDoctorIdNumber())) {
                throw new ForbiddenException("Doctors cannot query as another doctor");
            }
            request.setDoctorIdNumber(currentUser.getIdNumber());
        }
    }

    private VisitInfo toVisitInfo(MedicalRecordRequest request) {
        VisitInfo visitInfo = new VisitInfo();
        visitInfo.setDepartment(request.getDepartment());
        visitInfo.setMedication(request.getMedication());
        visitInfo.setConditionDescription(request.getConditionDescription());
        visitInfo.setCost(request.getCost());
        visitInfo.setVisitTime(request.getVisitTime());
        visitInfo.setPatientName(request.getPatientName());
        visitInfo.setPatientIdNumber(request.getPatientIdNumber());
        visitInfo.setAge(request.getAge());
        visitInfo.setDoctorName(request.getDoctorName());
        visitInfo.setDoctorIdNumber(request.getDoctorIdNumber());
        visitInfo.setDPk(request.getDPk());
        visitInfo.setSignature(request.getSignature());
        visitInfo.setGender(request.getGender());
        return visitInfo;
    }

    private void ensureVisitTime(MedicalRecordRequest request) {
        if (request.getVisitTime() == null) {
            request.setVisitTime(BigInteger.valueOf(System.currentTimeMillis()));
        }
    }

    private Pair<BigInteger, BigInteger> toInterval(BigInteger start, BigInteger end) {
        if (start == null && end == null) {
            return null;
        }
        RequestValidator.notNull(start, "interval start");
        RequestValidator.notNull(end, "interval end");
        return new Pair<>(start, end);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
