package com.swingtrade.broker.kite;

import com.swingtrade.broker.config.BrokerProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Zerodha Kite Connect integration.
 * Delegates to centralized BrokerProperties.
 */
@Configuration
public class KiteConfig {

    private final BrokerProperties.Kite kite;

    public KiteConfig(BrokerProperties props) {
        this.kite = props.getKite();
    }

    /**
     * Get API key for Kite Connect authentication.
     */
    public String getApiKey() {
        return kite.getApiKey();
    }

    public void setApiKey(String apiKey) {
        kite.setApiKey(apiKey);
    }

    /**
     * Get access token for authenticated requests.
     * This is obtained after OAuth authorization flow.
     */
    public String getAccessToken() {
        return kite.getAccessToken();
    }

    public void setAccessToken(String accessToken) {
        kite.setAccessToken(accessToken);
    }

    /**
     * Get Kite environment (live or sandbox).
     * Sandbox is used for testing before going live.
     */
    public String getEnvironment() {
        return kite.getEnvironment();
    }

    public void setEnvironment(String environment) {
        kite.setEnvironment(environment);
    }

    /**
     * Get proxy host for network configuration.
     */
    public String getProxyHost() {
        return kite.getProxyHost();
    }

    public void setProxyHost(String proxyHost) {
        kite.setProxyHost(proxyHost);
    }

    /**
     * Get proxy port for network configuration.
     */
    public int getProxyPort() {
        return kite.getProxyPort();
    }

    public void setProxyPort(int proxyPort) {
        kite.setProxyPort(proxyPort);
    }

    /**
     * Check if Kite Connect is configured.
     */
    public boolean isConfigured() {
        return kite.isConfigured();
    }

    /**
     * Check if using sandbox environment.
     */
    public boolean isSandbox() {
        return kite.isSandbox();
    }

    /**
     * Check if using live environment.
     */
    public boolean isLive() {
        return kite.isLive();
    }
}