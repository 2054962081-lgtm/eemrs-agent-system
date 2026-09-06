package com.liu.eemrsserver.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentAppointmentResponse {
    private boolean success;
    private String department;
    private String doctorId;
    private String doctorName;
    private String patientIdNumber;
    private String patientName;
    private String status;
    private Long visitTime;
    private String source;
}
