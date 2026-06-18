package com.mydata.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenAuthenticationInterceptor implements HandlerInterceptor {
    private final String adminToken;

    public AdminTokenAuthenticationInterceptor(@Value("${my-data.admin-token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        if (!adminToken.equals(request.getHeader("X-Admin-Token"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid admin token");
            return false;
        }

        return true;
    }
}
