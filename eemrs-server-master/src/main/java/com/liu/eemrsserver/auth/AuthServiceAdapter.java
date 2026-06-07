package com.liu.eemrsserver.auth;

import com.liu.eemrsserver.auth.dto.LoginRequest;
import com.liu.eemrsserver.auth.dto.LoginResponse;
import com.liu.eemrsserver.auth.dto.RegisterRequest;
import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.security.JwtTokenProvider;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.service.UserOpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceAdapter {
    @Autowired
    private UserOpService userOpService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notBlank(request.getType(), "type");
        RequestValidator.notBlank(request.getIdNumber(), "idNumber");
        RequestValidator.notBlank(request.getPassword(), "password");
        Role role = Role.fromType(request.getType());
        String idNumber = normalizeIdNumber(request.getIdNumber());
        String department = normalizeDepartment(request.getDepartment());
        if (role == Role.DOCTOR) {
            RequestValidator.notBlank(department, "department");
        }

        UserLog userLog = new UserLog(request.getType(), idNumber, null,
                request.getPassword(), null, department, null);
        boolean success = userOpService.loginUser(userLog);
        if (!success) {
            throw new BadRequestException("Invalid idNumber, password or type; patient login allows empty department");
        }
        UserPrincipal principal = new UserPrincipal(idNumber, idNumber,
                request.getType(), role, department);
        String token = jwtTokenProvider.createToken(principal);
        return new LoginResponse(token, "Bearer", idNumber, request.getType(),
                role.name(), department, jwtTokenProvider.getExpirationMillis() / 1000);
    }

    public boolean register(RegisterRequest request) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notBlank(request.getType(), "type");
        RequestValidator.notBlank(request.getIdNumber(), "idNumber");
        RequestValidator.notBlank(request.getUserName(), "userName");
        RequestValidator.notBlank(request.getPassword(), "password");
        String idNumber = normalizeIdNumber(request.getIdNumber());

        UserLog userLog = new UserLog(request.getType(), idNumber, request.getUserName(),
                request.getPassword(), null, request.getDepartment(), "0");
        return userOpService.insertUser(userLog);
    }

    private String normalizeDepartment(String department) {
        return department == null || department.trim().isEmpty() ? null : department.trim();
    }

    private String normalizeIdNumber(String idNumber) {
        return idNumber == null ? null : idNumber.trim().toUpperCase();
    }
}
