package com.swingtrade.api.config;

import com.swingtrade.api.controller.UpstoxAuthController;
import com.swingtrade.data.service.UpstoxAuthService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(UpstoxAuthController.class)
public class TestConfiguration {

    @Bean
    public UpstoxAuthService upstoxAuthService() {
        return org.mockito.Mockito.mock(UpstoxAuthService.class);
    }
}