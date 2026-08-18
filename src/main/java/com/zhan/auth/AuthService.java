package com.zhan.auth;

import com.zhan.audit.AuditService;
import com.zhan.auth.dto.LoginRequest;
import com.zhan.auth.dto.LoginResponse;
import com.zhan.auth.dto.RegisterRequest;
import com.zhan.auth.dto.UserDto;
import com.zhan.common.BusinessException;
import com.zhan.entity.User;
import com.zhan.repository.UserRepository;
import com.zhan.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Transactional
    public UserDto register(RegisterRequest request, String ip) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.conflict("用户名已存在");
        }
        User user = User.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .build();
        user = userRepository.save(user);
        auditService.log(user.getId(), "REGISTER", "USER", user.getId(), "用户注册: " + user.getUsername(), ip);
        return toDto(user);
    }

    public LoginResponse login(LoginRequest request, String ip) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        auditService.log(user.getId(), "LOGIN", "USER", user.getId(), "用户登录", ip);
        return LoginResponse.builder()
                .token(token)
                .user(toDto(user))
                .build();
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
