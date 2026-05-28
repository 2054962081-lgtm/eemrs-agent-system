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

        UserLog userLog = new UserLog(request.getType(), request.getIdNumber(), null,
                request.getPassword(), null, request.getDepartment(), null);
        boolean success = userOpService.loginUser(userLog);
        if (!success) {
            throw new BadRequestException("Invalid idNumber, password or type");
        }
        Role role = Role.fromType(request.getType());
        UserPrincipal principal = new UserPrincipal(request.getIdNumber(), request.getIdNumber(),
                request.getType(), role, request.getDepartment());
        String token = jwtTokenProvider.createToken(principal);
        return new LoginResponse(token, "Bearer", request.getIdNumber(), request.getType(),
                role.name(), request.getDepartment(), jwtTokenProvider.getExpirationMillis() / 1000);
    }

    public boolean register(RegisterRequest request) {
        RequestValidator.notNull(request, "request");
        RequestValidator.notBlank(request.getType(), "type");
        RequestValidator.notBlank(request.getIdNumber(), "idNumber");
        RequestValidator.notBlank(request.getUserName(), "userName");
        RequestValidator.notBlank(request.getPassword(), "password");

        UserLog userLog = new UserLog(request.getType(), request.getIdNumber(), request.getUserName(),
                request.getPassword(), null, request.getDepartment(), "0");
        return userOpService.insertUser(userLog);
    }
}
