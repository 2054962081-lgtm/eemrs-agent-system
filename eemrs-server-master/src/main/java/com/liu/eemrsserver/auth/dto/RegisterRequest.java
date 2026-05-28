package com.liu.eemrsserver.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String type;
    private String idNumber;
    private String userName;
    private String password;
    private String department;
}
