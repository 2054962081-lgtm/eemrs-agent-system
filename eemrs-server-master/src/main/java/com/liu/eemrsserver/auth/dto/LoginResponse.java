package com.liu.eemrsserver.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private String idNumber;
    private String type;
    private String role;
    private String department;
    private Long expiresIn;
}
