package com.zhan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhan.common.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(10_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();

        Bucket bucket = resolveBucket(path, ip);
        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(429, "请求过于频繁，请稍后再试")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Bucket resolveBucket(String path, String ip) {
        if (path.startsWith("/api/auth/")) {
            // 登录/注册：每分钟 10 次，防暴力破解
            return buckets.get("auth:" + ip, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                    .build());
        }
        if (path.contains("/messages")) {
            // 问答接口：每分钟 20 次，防刷爆 LLM 配额
            return buckets.get("ask:" + ip, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1))))
                    .build());
        }
        if (path.startsWith("/api/")) {
            // 其他业务接口：每分钟 300 次
            return buckets.get("api:" + ip, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(300, Refill.greedy(300, Duration.ofMinutes(1))))
                    .build());
        }
        return null;
    }
}
