package com.swingtrade.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the API boundary interceptor for all API routes. */
@Configuration
public class ApiSecurityWebConfig implements WebMvcConfigurer {

    private final ApiSecurityInterceptor securityInterceptor;

    public ApiSecurityWebConfig(ApiSecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor).addPathPatterns("/api/**");
    }
}
