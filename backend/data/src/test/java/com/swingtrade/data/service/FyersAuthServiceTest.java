package com.swingtrade.data.service;

import com.swingtrade.data.config.FyersConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FyersAuthServiceTest {

    private MockWebServer mockWebServer;
    private FyersConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        config = new FyersConfig();
        config.setClientId("test-fyers-client");
        config.setSecretKey("test-secret");
        config.setRedirectUrl("http://localhost:8080/api/fyers/callback");
        config.setTokenStorePath(tempDir.resolve("fyers-tokens.json").toString());
        config.setFrontendUrl("http://localhost:3003");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private FyersAuthService createService() {
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        return new FyersAuthService(config, builder);
    }

    @Test
    void bootstrapsTokenFromConfigAccessToken() {
        config.setAccessToken("env-token-xyz");
        config.setRefreshToken("env-refresh-abc");
        FyersAuthService service = createService();
        service.init();

        assertThat(service.getAccessToken()).isEqualTo("env-token-xyz");
        assertThat(service.validateToken()).isTrue();
    }

    @Test
    void returnsNullWhenNoTokenConfigured() {
        FyersAuthService service = createService();
        service.init();

        assertThat(service.getAccessToken()).isNull();
        assertThat(service.validateToken()).isFalse();
    }

    @Test
    void generateAuthorizationUrlContainsRequiredParams() {
        FyersAuthService service = createService();
        String url = service.getAuthorizationUrl();

        assertThat(url).startsWith("https://api-t1.fyers.in/api/v3/generate-authcode");
        assertThat(url).contains("client_id=test-fyers-client");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=swingtrade");
    }

    @Test
    void exchangesAuthCodeForTokens() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"access_token\":\"new-access-123\",\"refresh_token\":\"new-refresh-456\"}")
            .addHeader("Content-Type", "application/json")
        );

        FyersAuthService service = createService();
        service.init();
        service.exchangeAuthCodeForTokens("test-auth-code");

        assertThat(service.getAccessToken()).isEqualTo("new-access-123");
        assertThat(service.validateToken()).isTrue();

        // Verify request body
        var request = mockWebServer.takeRequest(); // may throw InterruptedException
        String body = request.getBody().readUtf8();
        assertThat(body).contains("test-auth-code");
        assertThat(body).contains("test-fyers-client");
    }

    @Test
    void persistsTokensToFileAndLoadsOnRestart() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"access_token\":\"persisted-access\",\"refresh_token\":\"persisted-refresh\"}")
            .addHeader("Content-Type", "application/json")
        );

        FyersAuthService service = createService();
        service.init();
        service.exchangeAuthCodeForTokens("auth-code-1");

        // Simulate restart — new instance, no env token
        config.setAccessToken(null);
        config.setRefreshToken(null);
        FyersAuthService service2 = createService();
        service2.init();

        assertThat(service2.getAccessToken()).isEqualTo("persisted-access");
        assertThat(service2.validateToken()).isTrue();
    }

    @Test
    void refreshesTokenWhenExpired() {
        // First set a refresh token
        config.setRefreshToken("valid-refresh-token");
        config.setPin("1234");

        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"access_token\":\"refreshed-access\",\"refresh_token\":\"new-refresh\"}")
            .addHeader("Content-Type", "application/json")
        );

        FyersAuthService service = createService();
        service.init();
        service.refreshToken();

        assertThat(service.getAccessToken()).isEqualTo("refreshed-access");
    }

    @Test
    void refreshRequestBodyContainsPin() throws InterruptedException {
        config.setRefreshToken("valid-refresh-token");
        config.setPin("1234");

        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"s\":\"success\",\"access_token\":\"refreshed-access\",\"refresh_token\":\"new-refresh\"}")
            .addHeader("Content-Type", "application/json")
        );

        FyersAuthService service = createService();
        service.init();
        service.refreshToken();

        var request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"pin\":\"1234\"");
    }

    @Test
    void refreshSkippedWithWarningWhenPinMissing() {
        config.setRefreshToken("valid-refresh-token");
        config.setPin(null);

        FyersAuthService service = createService();
        service.init();
        service.refreshToken();

        // No request should have been made — access token stays null
        assertThat(mockWebServer.getRequestCount()).isZero();
        assertThat(service.getAccessToken()).isNull();
    }

    @Test
    void invalidatesRefreshTokenOn401() {
        config.setRefreshToken("bad-refresh-token");

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"s\":\"error\",\"message\":\"Invalid refresh token\"}")
            .addHeader("Content-Type", "application/json")
        );

        FyersAuthService service = createService();
        service.init();
        service.refreshToken();

        assertThat(service.getAccessToken()).isNull();
        assertThat(service.validateToken()).isFalse();
    }

    @Test
    void getClientIdReturnsConfigValue() {
        FyersAuthService service = createService();
        assertThat(service.getClientId()).isEqualTo("test-fyers-client");
    }

    @Test
    void exchangeAuthCodeFailureDoesNotCrash() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error")
        );

        FyersAuthService service = createService();
        service.init();
        service.exchangeAuthCodeForTokens("bad-code");

        // Should not throw — logs error, token remains null
        assertThat(service.getAccessToken()).isNull();
    }
}
