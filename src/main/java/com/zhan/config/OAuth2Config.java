package com.zhan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * OAuth2 客户端注册（opt-in，默认关闭）。
 * 开启方式：设置 OAUTH2_ENABLED=true 与 OAUTH2_CLIENT_ID/CLIENT_SECRET。
 * 当前以 GitHub 为例（标准 OAuth2），企业生产可替换为企业微信/钉钉/通用 OIDC。
 */
@Configuration
@ConditionalOnProperty(name = "app.oauth2.enabled", havingValue = "true")
public class OAuth2Config {

    @Value("${app.oauth2.client-id:}")
    private String clientId;
    @Value("${app.oauth2.client-secret:}")
    private String clientSecret;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .clientName("GitHub")
                .build();
        return new InMemoryClientRegistrationRepository(github);
    }
}
