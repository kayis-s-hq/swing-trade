package com.swingtrade.broker.telegram;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.model.Position;
import com.swingtrade.domain.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending notifications via Signal messaging app (Signl4 integration).
 * Provides methods to send trade event notifications only (trade open/close).
 * Per project decision, Signal notifications are limited to trade events only,
 * not signals, position updates, or system status (these features are deferred).
 *
 * Uses REST API integration pattern to send notifications to Signal/Signl4 platform.
 * Signal is open source and free, aligning with project requirements.
 *
 * @author SwingTrade Team
 */
@Service
public class SignalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SignalNotificationService.class);

    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final BrokerProperties props;
    private boolean enabled;
    private String apiToken;
    private String chatId;
    private boolean notifyOnTrades;
    private boolean notifyOnSignals;
    private boolean notifyOnErrors;
    private int maxMessageLength;
    private boolean quietHoursEnabled;
    private int quietHoursStart;
    private int quietHoursEnd;

    private final SignalMessageFormatter messageFormatter;
    private RestTemplate restTemplate;

    // Quiet hours state
    private volatile boolean inQuietHours = false;

    @Autowired
    public SignalNotificationService(SignalMessageFormatter messageFormatter, BrokerProperties props) {
        this.messageFormatter = messageFormatter;
        this.props = props;
    }

    /**
     * Initialize the service after Spring beans are created.
     */
    public void init() {
        BrokerProperties.Signal sig = props.getSignal();
        this.enabled = sig.isEnabled();
        this.apiToken = sig.getApi().getToken();
        this.chatId = sig.getApi().getChatId();
        this.notifyOnTrades = sig.isNotifyOnTrades();
        this.notifyOnSignals = sig.isNotifyOnSignals();
        this.notifyOnErrors = sig.isNotifyOnErrors();
        this.maxMessageLength = sig.getMaxMessageLength();
        BrokerProperties.QuietHours qh = sig.getQuietHours();
        this.quietHoursEnabled = qh.isEnabled();
        this.quietHoursStart = qh.getStart();
        this.quietHoursEnd = qh.getEnd();
        initRestTemplate();
        log.info("Signal notifications initialized: enabled={}, chatId={}, apiTokenConfigured={}",
                enabled, chatId, apiToken != null && !apiToken.isEmpty());
    }

    /**
     * Initialize the RestTemplate for HTTP requests to Signal API.
     */
    public void initRestTemplate() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if current time is within quiet hours.
     *
     * @return true if currently in quiet hours
     */
    private boolean isQuietHours() {
        if (!quietHoursEnabled) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        // Handle overnight quiet hours (e.g., 22:00 to 07:00)
        if (quietHoursStart > quietHoursEnd) {
            return currentHour >= quietHoursStart || currentHour < quietHoursEnd;
        } else {
            return currentHour >= quietHoursStart && currentHour < quietHoursEnd;
        }
    }

    /**
     * Sends a message via Signal API.
     *
     * @param message the message to send
     * @return true if message was queued for sending, false otherwise
     */
    public boolean sendMessage(String message) {
        if (!enabled) {
            log.debug("Signal notifications are disabled by configuration");
            return false;
        }

        if (chatId == null || chatId.trim().isEmpty()) {
            log.warn("Signal chat ID is not configured. Notifications will be disabled.");
            return false;
        }

        if (apiToken == null || apiToken.trim().isEmpty()) {
            log.warn("Signal API token is not configured. Notifications will be disabled.");
            return false;
        }

        // Check quiet hours
        if (isQuietHours()) {
            log.debug("Skipping message during quiet hours ({}-{}:00)",
                    String.format("%02d", quietHoursStart), String.format("%02d", quietHoursEnd));
            return false;
        }

        // Truncate message if too long
        if (message.length() > maxMessageLength) {
            message = message.substring(0, maxMessageLength - 3) + "...";
        }

        try {
            sendToSignalApi(message);
            log.debug("Message sent to Signal chat: {}", chatId.trim());
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to Signal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sends a message to the Signal API endpoint.
     *
     * @param message the message to send
     */
    private void sendToSignalApi(String message) {
        // Signal/Signl4 API endpoint - configurable via environment
        String apiUrl = System.getenv("SIGNAL_API_URL");
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            // Default Signl4 webhook endpoint pattern
            apiUrl = "https://your-signl4-webhook-url.com/webhook";
        }

        String url = apiUrl;

        // Prepare request body for Signal/Signl4 API
        // Adapted from Telegram format to Signal API structure
        Map<String, Object> requestBody = new ConcurrentHashMap<>();
        requestBody.put("title", "Trade Notification");
        requestBody.put("message", message);
        requestBody.put("priority", "normal");
        requestBody.put("source", "SwingTrade System");

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set("X-Chat-Id", chatId.trim());

        // Create request entity
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Send request
        restTemplate.postForObject(url, entity, SignalResponse.class);
    }

    /**
     * Response object from Signal API.
     */
    private static class SignalResponse {
        private boolean success;
        private String messageId;
        private String status;

        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
    }

    // =================================================================
    // Trade Event Notification Methods
    // =================================================================

    /**
     * Sends a trade open notification when a new position is entered.
     * Includes full details: symbol, price, quantity, direction, stop loss, target.
     *
     * @param position the position that was entered
     * @param signal the trading signal that triggered this position
     * @return true if message was sent, false otherwise
     */
    public boolean sendTradeOpen(Position position, com.swingtrade.domain.Signal signal) {
        if (!notifyOnTrades) {
            log.debug("Trade notifications disabled, skipping trade open");
            return false;
        }

        String message = messageFormatter.formatTradeOpen(position, signal);
        log.info("Sending trade open notification for {}", position.getSymbol());
        return sendMessage(message);
    }

    /**
     * Sends a trade close notification when a position is closed.
     * Includes full details: symbol, entry/exit price, quantity, P&L, duration, exit reason.
     *
     * @param position the position that was exited
     * @param trade the completed trade
     * @return true if message was sent, false otherwise
     */
    public boolean sendTradeClose(Position position, Trade trade) {
        if (!notifyOnTrades) {
            log.debug("Trade notifications disabled, skipping trade close");
            return false;
        }

        String message = messageFormatter.formatTradeClose(position, trade);
        log.info("Sending trade close notification for {}", trade.symbol());
        return sendMessage(message);
    }

    /**
     * Sends a stop loss hit notification.
     * Includes symbol, exit price, expected SL, loss amount, loss percentage.
     *
     * @param position the position that hit stop loss
     * @param exitPrice the price at which the position was stopped
     * @param pnl the profit/loss from the stop loss
     * @return true if message was sent, false otherwise
     */
    public boolean sendStopLossHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        if (!notifyOnTrades) {
            log.debug("Trade notifications disabled, skipping stop loss");
            return false;
        }

        String message = messageFormatter.formatStopLossHit(position, exitPrice, pnl);
        log.warn("Sending stop loss hit notification for {}", position.getSymbol());
        return sendMessage(message);
    }

    /**
     * Sends a target hit notification.
     * Includes symbol, exit price, target price, profit amount, profit percentage.
     *
     * @param position the position that hit target
     * @param exitPrice the price at which the position was exited
     * @param pnl the profit/loss from the target hit
     * @return true if message was sent, false otherwise
     */
    public boolean sendTargetHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        if (!notifyOnTrades) {
            log.debug("Trade notifications disabled, skipping target hit");
            return false;
        }

        String message = messageFormatter.formatTargetHit(position, exitPrice, pnl);
        log.info("Sending target hit notification for {}", position.getSymbol());
        return sendMessage(message);
    }

    // =================================================================
    // Configuration Methods
    // =================================================================

    /**
     * Check if Signal notifications are enabled.
     *
     * @return true if notifications are enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set the Signal chat ID via setter (useful for dynamic configuration).
     *
     * @param chatId the chat ID to set
     */
    public void setChatId(String chatId) {
        this.chatId = chatId;
        log.info("Signal chat ID updated: {}", chatId);
    }

    /**
     * Set the Signal API token via setter (useful for dynamic configuration).
     *
     * @param token the API token to set
     */
    public void setApiToken(String token) {
        this.apiToken = token;
        log.info("Signal API token updated");
    }

    /**
     * Enable or disable trade notifications.
     *
     * @param enabled true to enable, false to disable
     */
    public void setNotifyOnTrades(boolean enabled) {
        this.notifyOnTrades = enabled;
        log.info("Trade notifications {}d", enabled ? "enable" : "disable");
    }

    /**
     * Enable or disable signal notifications (currently deferred).
     *
     * @param enabled true to enable, false to disable
     */
    public void setNotifyOnSignals(boolean enabled) {
        this.notifyOnSignals = enabled;
        log.info("Signal notifications {}d (deferred feature)", enabled ? "enable" : "disable");
    }

    /**
     * Enable or disable error notifications (currently deferred).
     *
     * @param enabled true to enable, false to disable
     */
    public void setNotifyOnErrors(boolean enabled) {
        this.notifyOnErrors = enabled;
        log.info("Error notifications {}d (deferred feature)", enabled ? "enable" : "disable");
    }

    /**
     * Enable or disable quiet hours.
     *
     * @param enabled true to enable, false to disable
     */
    public void setQuietHoursEnabled(boolean enabled) {
        this.quietHoursEnabled = enabled;
        log.info("Quiet hours {}d", enabled ? "enable" : "disable");
    }

    /**
     * Get the configured chat ID.
     *
     * @return the chat ID
     */
    public String getChatId() {
        return chatId;
    }

    /**
     * Get the number of configured chat IDs (always 1 for Signal).
     *
     * @return the number of chat IDs
     */
    public int getChatCount() {
        return (chatId != null && !chatId.trim().isEmpty()) ? 1 : 0;
    }

    /**
     * Check if notifications should be sent for trades.
     *
     * @return true if trade notifications are enabled
     */
    public boolean shouldNotifyOnTrades() {
        return notifyOnTrades;
    }

    /**
     * Check if notifications should be sent for signals.
     *
     * @return true if signal notifications are enabled
     */
    public boolean shouldNotifyOnSignals() {
        return notifyOnSignals;
    }

    /**
     * Check if notifications should be sent for positions.
     *
     * @return true if position notifications are enabled
     */
    public boolean shouldNotifyOnPositions() {
        return notifyOnTrades; // Position updates are part of trade notifications
    }

    /**
     * Check if notifications should be sent for errors.
     *
     * @return true if error notifications are enabled
     */
    public boolean shouldNotifyOnErrors() {
        return notifyOnErrors;
    }
}
