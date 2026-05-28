package com.liu.eemrsserver.appointment.dto;

import com.liu.eemrsserver.domain.PatientInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AcceptAppointmentResponse {
    private String idNumber;
    private PatientInfo patientInfo;
}
