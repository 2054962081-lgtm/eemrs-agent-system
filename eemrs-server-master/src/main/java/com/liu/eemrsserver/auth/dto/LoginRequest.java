package com.liu.eemrsserver.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String type;
    private String idNumber;
    private String password;
    private String department;
}
