package com.swingtrade.api.config;

// Upstox is unconfigured — the Upstox controller import and its mock bean are
// commented out (kept for future re-enablement).
// import com.swingtrade.api.controller.UpstoxAuthController;
// import com.swingtrade.data.service.UpstoxAuthService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Import;

@SpringBootApplication
// @Import(UpstoxAuthController.class)
public class TestConfiguration {

    // @Bean
    // public UpstoxAuthService upstoxAuthService() {
    //     return org.mockito.Mockito.mock(UpstoxAuthService.class);
    // }
}