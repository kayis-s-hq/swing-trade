package com.swingtrade.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralized broker, risk, and notification configuration.
 * Replaces scattered @Value annotations across broker module classes.
 */
@Component
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    // Position limits
    private int maxConcurrentPositions = 3;
    private BigDecimal maxCapitalPerPosition = BigDecimal.valueOf(200000);

    // Trade sizing
    private BigDecimal maxCapitalPerTrade = BigDecimal.valueOf(50000);
    private BigDecimal initialCapital = BigDecimal.valueOf(1000000);
    private BigDecimal maxPositionSizePercentage = BigDecimal.valueOf(10);
    private BigDecimal minPositionSizePercentage = BigDecimal.valueOf(1);

    // Kill switch
    private boolean killSwitchEnabled = true;
    private boolean killSwitchActive = false;

    // Circuit breaker
    private BigDecimal dailyLossCircuitBreaker = BigDecimal.valueOf(2.0);

    // Mode
    private String mode = "paper";

    // Telegram
    private final Telegram telegram = new Telegram();

    // Signal (Signl4)
    private final Signal signal = new Signal();

    // Kite (Zerodha)
    private final Kite kite = new Kite();

    // Discord
    private final Discord discord = new Discord();

    // --- Position limits ---

    public int getMaxConcurrentPositions() { return maxConcurrentPositions; }
    public void setMaxConcurrentPositions(int maxConcurrentPositions) { this.maxConcurrentPositions = maxConcurrentPositions; }

    public BigDecimal getMaxCapitalPerPosition() { return maxCapitalPerPosition; }
    public void setMaxCapitalPerPosition(BigDecimal maxCapitalPerPosition) { this.maxCapitalPerPosition = maxCapitalPerPosition; }

    // --- Trade sizing ---

    public BigDecimal getMaxCapitalPerTrade() { return maxCapitalPerTrade; }
    public void setMaxCapitalPerTrade(BigDecimal maxCapitalPerTrade) { this.maxCapitalPerTrade = maxCapitalPerTrade; }

    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }

    public BigDecimal getMaxPositionSizePercentage() { return maxPositionSizePercentage; }
    public void setMaxPositionSizePercentage(BigDecimal maxPositionSizePercentage) { this.maxPositionSizePercentage = maxPositionSizePercentage; }

    public BigDecimal getMinPositionSizePercentage() { return minPositionSizePercentage; }
    public void setMinPositionSizePercentage(BigDecimal minPositionSizePercentage) { this.minPositionSizePercentage = minPositionSizePercentage; }

    // --- Kill switch ---

    public boolean isKillSwitchEnabled() { return killSwitchEnabled; }
    public void setKillSwitchEnabled(boolean killSwitchEnabled) { this.killSwitchEnabled = killSwitchEnabled; }

    public boolean isKillSwitchActive() { return killSwitchActive; }
    public void setKillSwitchActive(boolean killSwitchActive) { this.killSwitchActive = killSwitchActive; }

    // --- Circuit breaker ---

    public BigDecimal getDailyLossCircuitBreaker() { return dailyLossCircuitBreaker; }
    public void setDailyLossCircuitBreaker(BigDecimal dailyLossCircuitBreaker) { this.dailyLossCircuitBreaker = dailyLossCircuitBreaker; }

    // --- Mode ---

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    // --- Telegram ---

    public Telegram getTelegram() { return telegram; }

    // --- Signal (Signl4) ---

    public Signal getSignal() { return signal; }

    // --- Kite (Zerodha) ---

    public Kite getKite() { return kite; }

    // --- Discord ---

    public Discord getDiscord() { return discord; }

    // =================================================================
    // Inner Types (must be last per Checkstyle InnerTypeLast rule)
    // =================================================================

    public static class Telegram {
        private String botToken;
        private boolean botEnabled = true;

        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public boolean isBotEnabled() { return botEnabled; }
        public void setBotEnabled(boolean botEnabled) { this.botEnabled = botEnabled; }
    }

    public static class Signal {
        private boolean enabled = false;
        private final Api api = new Api();
        private boolean notifyOnTrades = true;
        private boolean notifyOnSignals = false;
        private boolean notifyOnErrors = false;
        private int maxMessageLength = 4096;
        private final QuietHours quietHours = new QuietHours();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Api getApi() { return api; }
        public boolean isNotifyOnTrades() { return notifyOnTrades; }
        public void setNotifyOnTrades(boolean notifyOnTrades) { this.notifyOnTrades = notifyOnTrades; }
        public boolean isNotifyOnSignals() { return notifyOnSignals; }
        public void setNotifyOnSignals(boolean notifyOnSignals) { this.notifyOnSignals = notifyOnSignals; }
        public boolean isNotifyOnErrors() { return notifyOnErrors; }
        public void setNotifyOnErrors(boolean notifyOnErrors) { this.notifyOnErrors = notifyOnErrors; }
        public int getMaxMessageLength() { return maxMessageLength; }
        public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }
        public QuietHours getQuietHours() { return quietHours; }
    }

    public static class Api {
        private String token;
        private String chatId;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
    }

    public static class QuietHours {
        private boolean enabled = false;
        private int start = 22;
        private int end = 7;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getStart() { return start; }
        public void setStart(int start) { this.start = start; }
        public int getEnd() { return end; }
        public void setEnd(int end) { this.end = end; }
    }

    public static class Kite {
        private String apiKey;
        private String accessToken;
        private String environment = "live";
        private String proxyHost;
        private int proxyPort = 0;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        public String getProxyHost() { return proxyHost; }
        public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
        public int getProxyPort() { return proxyPort; }
        public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
        public boolean isConfigured() { return apiKey != null && !apiKey.trim().isEmpty(); }
        public boolean isSandbox() { return "sandbox".equalsIgnoreCase(environment); }
        public boolean isLive() { return "live".equalsIgnoreCase(environment); }
    }

    public static class Discord {
        private String webhookUrl;
        private boolean webhookEnabled = false;

        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        public boolean isWebhookEnabled() { return webhookEnabled; }
        public void setWebhookEnabled(boolean webhookEnabled) { this.webhookEnabled = webhookEnabled; }
    }
}