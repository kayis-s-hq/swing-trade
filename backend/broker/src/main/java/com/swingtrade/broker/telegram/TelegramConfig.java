package com.swingtrade.broker.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Telegram Bot settings.
 * Binds to properties prefixed with "telegram." in application.properties
 *
 * @author SwingTrade Team
 */
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramConfig {

    /**
     * Whether the Telegram bot is enabled
     */
    private boolean enabled = true;

    /**
     * Telegram Bot API Token
     */
    private String token;

    /**
     * Telegram Bot Username
     */
    private String username;

    /**
     * Chat IDs for notifications (can be multiple, comma-separated)
     */
    private List<String> chatIds = new ArrayList<>();

    /**
     * Notification settings
     */
    private NotifyConfig notify = new NotifyConfig();

    /**
     * Whether to send trade notifications
     */
    private boolean notifyOnTrades = true;

    /**
     * Whether to send signal notifications
     */
    private boolean notifyOnSignals = true;

    /**
     * Whether to send error notifications
     */
    private boolean notifyOnErrors = true;

    /**
     * Whether to send position notifications
     */
    private boolean notifyOnPositions = true;

    /**
     * Maximum message length for Telegram
     */
    private int maxMessageLength = 4096;

    /**
     * Rate limit in seconds between notifications
     */
    private int rateLimitSeconds = 60;

    /**
     * Admin user IDs
     */
    private List<String> adminUserIds = new ArrayList<>();

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getChatIds() {
        return chatIds;
    }

    public void setChatIds(List<String> chatIds) {
        this.chatIds = chatIds;
    }

    public NotifyConfig getNotify() {
        return notify;
    }

    public void setNotify(NotifyConfig notify) {
        this.notify = notify;
    }

    public boolean isNotifyOnTrades() {
        return notifyOnTrades;
    }

    public void setNotifyOnTrades(boolean notifyOnTrades) {
        this.notifyOnTrades = notifyOnTrades;
    }

    public boolean isNotifyOnSignals() {
        return notifyOnSignals;
    }

    public void setNotifyOnSignals(boolean notifyOnSignals) {
        this.notifyOnSignals = notifyOnSignals;
    }

    public boolean isNotifyOnErrors() {
        return notifyOnErrors;
    }

    public void setNotifyOnErrors(boolean notifyOnErrors) {
        this.notifyOnErrors = notifyOnErrors;
    }

    public boolean isNotifyOnPositions() {
        return notifyOnPositions;
    }

    public void setNotifyOnPositions(boolean notifyOnPositions) {
        this.notifyOnPositions = notifyOnPositions;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getRateLimitSeconds() {
        return rateLimitSeconds;
    }

    public void setRateLimitSeconds(int rateLimitSeconds) {
        this.rateLimitSeconds = rateLimitSeconds;
    }

    public List<String> getAdminUserIds() {
        return adminUserIds;
    }

    public void setAdminUserIds(List<String> adminUserIds) {
        this.adminUserIds = adminUserIds;
    }

    /**
     * Nested configuration class for notification settings
     */
    public static class NotifyConfig {
        private boolean onTrades = true;
        private boolean onSignals = true;
        private boolean onErrors = true;
        private boolean onPositions = true;
        private int maxMessageLength = 4096;
        private int rateLimitSeconds = 60;

        // Getters and setters
        public boolean isOnTrades() { return onTrades; }
        public void setOnTrades(boolean onTrades) { this.onTrades = onTrades; }

        public boolean isOnSignals() { return onSignals; }
        public void setOnSignals(boolean onSignals) { this.onSignals = onSignals; }

        public boolean isOnErrors() { return onErrors; }
        public void setOnErrors(boolean onErrors) { this.onErrors = onErrors; }

        public boolean isOnPositions() { return onPositions; }
        public void setOnPositions(boolean onPositions) { this.onPositions = onPositions; }

        public int getMaxMessageLength() { return maxMessageLength; }
        public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }

        public int getRateLimitSeconds() { return rateLimitSeconds; }
        public void setRateLimitSeconds(int rateLimitSeconds) { this.rateLimitSeconds = rateLimitSeconds; }
    }
}
