package com.zhan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhan.common.BusinessException;
import com.zhan.entity.User;
import com.zhan.repository.UserRepository;
import com.zhan.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth2 登录成功后：签发 JWT 并重定向到前端落地页，
 * 由该页面把 token 和用户信息写入 localStorage。
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            response.sendRedirect("/");
            return;
        }
        String providerId = token.getAuthorizedClientRegistrationId();
        String login = token.getPrincipal().getName();
        String username = providerId + "_" + login;
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("OAuth2 用户未创建"));

        String jwt = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        Map<String, Object> userMap = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().name());
        String userJson = objectMapper.writeValueAsString(userMap);

        String redirect = "/oauth2-redirect.html?token="
                + URLEncoder.encode(jwt, StandardCharsets.UTF_8)
                + "&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8);
        response.sendRedirect(redirect);
    }
}
