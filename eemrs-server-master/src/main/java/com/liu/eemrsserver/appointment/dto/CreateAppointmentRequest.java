package com.liu.eemrsserver.appointment.dto;

import lombok.Data;

@Data
public class CreateAppointmentRequest {
    private String department;
    private String idNumber;
    private String userName;
    private String doctorIdNumber;
}
