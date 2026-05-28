package com.liu.eemrsserver.auth;

import com.liu.eemrsserver.auth.dto.LoginRequest;
import com.liu.eemrsserver.auth.dto.LoginResponse;
import com.liu.eemrsserver.auth.dto.RegisterRequest;
import com.liu.eemrsserver.common.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthServiceAdapter authServiceAdapter;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok("login success", authServiceAdapter.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Boolean> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok("register handled", authServiceAdapter.register(request));
    }
}
