package com.swingtrade.data.service;

import com.swingtrade.data.config.UpstoxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UpstoxAuthServiceTest {

    @TempDir
    Path tempDir;

    private UpstoxConfig config;

    @BeforeEach
    void setUp() {
        config = new UpstoxConfig();
        UpstoxConfig.Api api = new UpstoxConfig.Api();
        api.setBaseUrl("https://api.upstox.com");
        config.setApi(api);
        config.setClientId("test-client-id");
        config.setClientSecret("test-secret");
        config.setRedirectUri("http://localhost:8080/api/auth/upstox/callback");
        config.setTokenStorePath(tempDir.resolve("tokens.json").toString());
    }

    @Test
    void bootstrapsTokenFromConfigAccessToken() {
        config.setAccessToken("env-token-xyz");
        UpstoxAuthService service = new UpstoxAuthService(config);
        service.init();

        assertThat(service.getAccessToken()).isEqualTo("env-token-xyz");
    }

    @Test
    void returnsNullWhenNoTokenConfigured() {
        UpstoxAuthService service = new UpstoxAuthService(config);
        service.init();

        assertThat(service.getAccessToken()).isNull();
    }

    @Test
    void setAccessTokenUpdatesCache() {
        UpstoxAuthService service = new UpstoxAuthService(config);
        service.init();
        service.setAccessToken("manually-set-token");

        assertThat(service.getAccessToken()).isEqualTo("manually-set-token");
    }

    @Test
    void clearTokensRemovesCache() {
        config.setAccessToken("some-token");
        UpstoxAuthService service = new UpstoxAuthService(config);
        service.init();
        service.clearTokens();

        assertThat(service.getAccessToken()).isNull();
    }

    @Test
    void generateLoginUrlContainsRequiredParams() {
        UpstoxAuthService service = new UpstoxAuthService(config);
        String url = service.generateLoginUrl();

        assertThat(url).startsWith("https://api.upstox.com/v2/login/authorization/dialog");
        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("redirect_uri=");
    }

    @Test
    void persistsTokenToFileAndLoadsOnNextInit() {
        UpstoxAuthService service = new UpstoxAuthService(config);
        service.init();
        service.setAccessToken("persisted-token-abc");

        // New service instance with same config (simulates restart)
        UpstoxAuthService service2 = new UpstoxAuthService(config);
        service2.init();

        assertThat(service2.getAccessToken()).isEqualTo("persisted-token-abc");
    }
}
