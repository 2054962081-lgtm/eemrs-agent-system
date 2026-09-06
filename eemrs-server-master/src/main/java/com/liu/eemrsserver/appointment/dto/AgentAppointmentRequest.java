package com.liu.eemrsserver.appointment.dto;

import lombok.Data;

@Data
public class AgentAppointmentRequest {
    private String department;
    private String doctorId;
    private String source;
}
