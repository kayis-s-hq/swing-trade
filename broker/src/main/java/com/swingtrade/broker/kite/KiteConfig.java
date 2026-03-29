package com.swingtrade.broker.kite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Zerodha Kite Connect integration.
 * Holds API credentials and other Kite-specific settings.
 */
@Configuration
public class KiteConfig {

    @Value("${kite.api-key:}")
    private String apiKey = "";

    @Value("${kite.access-token:}")
    private String accessToken = "";

    @Value("${kite.environment:live}")
    private String environment = "live";

    @Value("${kite.proxy-host:}")
    private String proxyHost = "";

    @Value("${kite.proxy-port:0}")
    private int proxyPort = 0;

    /**
     * Get API key for Kite Connect authentication.
     */
    public String getApiKey() {
        return apiKey != null ? apiKey : "";
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Get access token for authenticated requests.
     * This is obtained after OAuth authorization flow.
     */
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Get Kite environment (live or sandbox).
     * Sandbox is used for testing before going live.
     */
    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * Get proxy host for network configuration.
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * Get proxy port for network configuration.
     */
    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Check if Kite Connect is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Check if using sandbox environment.
     */
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }

    /**
     * Check if using live environment.
     */
    public boolean isLive() {
        return "live".equalsIgnoreCase(environment);
    }
}
