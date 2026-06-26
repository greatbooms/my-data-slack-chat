package com.mydata.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminSecurityConfiguration {
    @Bean
    SecurityFilterChain adminSecurityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository
    ) throws Exception {
        http.securityMatcher("/admin/**", "/admin-ui/**");
        http.securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository));
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(
            AdminSecurityConfiguration::handleAuthenticationRequired
        ));
        http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/admin-ui/login", "/admin-ui/assets/**", "/admin/auth/login", "/admin/auth/csrf")
            .permitAll()
            .requestMatchers("/admin-ui/**", "/admin/graphql", "/admin/auth/logout", "/admin/**")
            .hasRole("ADMIN")
            .anyRequest()
            .permitAll()
        );
        return http.build();
    }

    private static void handleAuthenticationRequired(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        if (isAdminUiRequest(request)) {
            response.sendRedirect(request.getContextPath() + "/admin-ui/login");
            return;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static boolean isAdminUiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isBlank()
            ? requestUri
            : requestUri.substring(contextPath.length());
        return path.equals("/admin-ui") || path.startsWith("/admin-ui/");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }
}
