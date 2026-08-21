package com.zhan.config;

import com.zhan.entity.Role;
import com.zhan.entity.User;
import com.zhan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * OAuth2 登录成功后，把第三方用户映射（或自动创建）到本地 User。
 * 用户名格式：{provider}_{login}，密码随机生成（OAuth 登录不使用密码）。
 */
@Component
@RequiredArgsConstructor
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String providerId = userRequest.getClientRegistration().getRegistrationId();
        String login = oauth2User.getAttribute("login");
        if (login == null || login.isBlank()) {
            login = oauth2User.getAttribute("name");
        }
        if (login == null || login.isBlank()) {
            login = oauth2User.getName();
        }
        String username = providerId + "_" + login;
        userRepository.findByUsername(username).orElseGet(() -> userRepository.save(
                User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(Role.USER)
                        .build()));
        return oauth2User;
    }
}
