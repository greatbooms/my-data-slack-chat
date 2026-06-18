package com.mydata.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminTokenAuthenticationInterceptor implements HandlerInterceptor {
    private final byte[] adminToken;

    public AdminTokenAuthenticationInterceptor(@Value("${my-data.admin-token}") String adminToken) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new IllegalArgumentException("관리자 토큰은 비어 있을 수 없습니다");
        }
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        String requestToken = request.getHeader("X-Admin-Token");
        if (requestToken == null || !MessageDigest.isEqual(adminToken, requestToken.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "관리자 토큰이 올바르지 않습니다");
            return false;
        }

        return true;
    }
}
