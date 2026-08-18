package com.zhan.auth;

import com.zhan.auth.dto.LoginRequest;
import com.zhan.auth.dto.LoginResponse;
import com.zhan.auth.dto.RegisterRequest;
import com.zhan.auth.dto.UserDto;
import com.zhan.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.register(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }
}
