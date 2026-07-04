package com.swingtrade.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fyers")
public class FyersConfig {
    private String clientId;
    private String secretKey;
    private String accessToken;
    private String refreshToken;
    private String redirectUrl;
    private String pin;
    private String tokenStorePath = "data/fyers-tokens.json";
    private String frontendUrl = "http://localhost:3003";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getTokenStorePath() { return tokenStorePath; }
    public void setTokenStorePath(String tokenStorePath) { this.tokenStorePath = tokenStorePath; }

    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
}
