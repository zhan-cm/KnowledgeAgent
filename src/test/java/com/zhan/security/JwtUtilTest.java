package com.zhan.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void generateAndParseRoundtrip() {
        String token = jwtUtil.generateToken(1L, "alice", "USER");

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(1L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil expired = new JwtUtil(SECRET, -1_000L);
        String token = expired.generateToken(1L, "alice", "USER");

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtUtil other = new JwtUtil(SECRET + "-different", 3_600_000L);
        String token = other.generateToken(1L, "alice", "USER");

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(SignatureException.class);
    }
}
