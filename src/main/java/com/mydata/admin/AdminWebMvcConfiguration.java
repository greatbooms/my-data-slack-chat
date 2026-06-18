package com.mydata.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebMvcConfiguration implements WebMvcConfigurer {
    private final AdminTokenAuthenticationInterceptor adminTokenAuthentication;

    public AdminWebMvcConfiguration(AdminTokenAuthenticationInterceptor adminTokenAuthentication) {
        this.adminTokenAuthentication = adminTokenAuthentication;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenAuthentication)
            .addPathPatterns("/admin", "/admin/**");
    }
}
