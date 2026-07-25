package com.swingtrade.broker.telegram;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.Position;
import com.swingtrade.broker.risk.RiskControlsService;
import com.swingtrade.domain.NotificationService;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Trade;
import jakarta.annotation.PostConstruct;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending notifications via Telegram bot.
 * Provides methods to send trade signals, position updates, daily summaries, and error alerts.
 * Uses the official Telegram Bot API via HTTP REST calls.
 *
 * @author SwingTrade Team
 */
@Service
public class TelegramNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final BrokerProperties props;
    private String botToken;
    private boolean enabled;

    /**
     * Configured chat IDs for notifications (comma-separated from application properties)
     */
    private List<String> chatIds;

    private final TelegramMessageFormatter messageFormatter;
    private final RiskControlsService riskControlsService;

    private RestTemplate restTemplate;

    // Kill switch state
    private volatile boolean killSwitchActive = false;
    private LocalDateTime killSwitchActivatedTime;
    private String killSwitchReason;

    // Command handlers
    private final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();

    @Autowired
    public TelegramNotificationService(TelegramMessageFormatter messageFormatter,
                                       RiskControlsService riskControlsService,
                                       BrokerProperties props) {
        this.messageFormatter = messageFormatter;
        this.riskControlsService = riskControlsService;
        this.props = props;
        initializeCommandHandlers();
    }

    /**
     * Initialize the service after Spring beans are created.
     */
    @PostConstruct
    public void init() {
        BrokerProperties.Telegram telegram = props.getTelegram();
        this.botToken = telegram.getBotToken();
        this.enabled = telegram.isBotEnabled();
        initRestTemplate();
        initializeChatIds();
    }

    /**
     * Initialize the RestTemplate for HTTP requests to Telegram API.
     * This should be called after bean creation.
     */
    public void initRestTemplate() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Initialize the chat IDs from configuration.
     * Must be called after bean initialization.
     */
    public void initializeChatIds() {
        if (!enabled) {
            log.info("Telegram notifications are disabled by configuration");
            this.chatIds = Collections.emptyList();
            return;
        }

        if (botToken == null || botToken.trim().isEmpty()) {
            log.warn("Telegram bot token is not configured. Notifications will be disabled.");
            this.chatIds = Collections.emptyList();
            return;
        }

        // Default admin chat IDs for demo purposes
        // In production, these should be set via TELEGRAM_CHAT_IDS environment variable
        String chatIdsConfig = System.getenv("TELEGRAM_CHAT_IDS");
        if (chatIdsConfig == null || chatIdsConfig.trim().isEmpty()) {
            log.warn("No TELEGRAM_CHAT_IDS environment variable set. Using default chat IDs.");
            this.chatIds = new ArrayList<>();
            // Add placeholder chat IDs - users should replace with their actual chat IDs
            this.chatIds.add("123456789"); // Replace with actual chat ID
        } else {
            this.chatIds = new ArrayList<>();
            for (String chatId : chatIdsConfig.split(",")) {
                String trimmed = chatId.trim();
                if (!trimmed.isEmpty()) {
                    this.chatIds.add(trimmed);
                }
            }
        }

        if (!this.chatIds.isEmpty()) {
            log.info("Initialized Telegram notifications for {} chat(s). Bot enabled: {}, Token configured: {}",
                this.chatIds.size(), enabled, botToken != null && !botToken.isEmpty());
        }
    }

    /**
     * Sends a message to all configured chat IDs.
     *
     * @param message the message to send
     * @return true if message was queued for sending, false otherwise
     */
    public boolean sendMessage(String message) {
        if (!enabled) {
            log.debug("Telegram notifications are disabled, skipping message");
            return false;
        }

        if (chatIds == null || chatIds.isEmpty()) {
            log.debug("No chat IDs configured, skipping message");
            return false;
        }

        if (botToken == null || botToken.trim().isEmpty()) {
            log.warn("Bot token not configured, cannot send Telegram messages");
            return false;
        }

        String botUrl = "https://api.telegram.org/bot" + botToken;

        for (String chatId : chatIds) {
            try {
                sendMessageToChat(botUrl, chatId.trim(), message);
                log.debug("Message sent to chat: {}", chatId.trim());
            } catch (Exception e) {
                log.error("Failed to send message to chat {}: {}", chatId, e.getMessage());
            }
        }
        return true;
    }

    /**
     * Sends a message to a specific chat ID.
     *
     * @param botUrl the bot API URL
     * @param chatId the chat ID
     * @param message the message to send
     */
    private void sendMessageToChat(String botUrl, String chatId, String message) {
        // Telegram message size limit is 4096 characters
        if (message.length() > 4096) {
            message = message.substring(0, 4093) + "...";
        }

        String url = botUrl + "/sendMessage";

        // Prepare request body
        String requestBody = String.format(
            "{\"chat_id\":%s,\"text\":\"%s\",\"parse_mode\":\"Markdown\",\"disable_web_page_preview\":true}",
            chatId,
            escapeJson(message)
        );

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create request entity
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Send request
        restTemplate.postForObject(url, entity, TelegramResponse.class);
    }

    /**
     * Escape special characters in JSON string.
     *
     * @param input the input string
     * @return escaped string
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Response object from Telegram API.
     */
    private static class TelegramResponse {
        private boolean ok;
        private Result result;

        public boolean isOk() { return ok; }
        public Result getResult() { return result; }

        public static class Result {
            private long message_id;
            private Message message;

            public long getMessageId() { return message_id; }
            public Message getMessage() { return message; }

            public static class Message {
                private long chat_id;
                private String text;

                public long getChatId() { return chat_id; }
                public String getText() { return text; }
            }
        }
    }

    /**
     * Sends a BUY signal notification.
     *
     * @param signal the trading signal to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendBuySignal(Signal signal) {
        String message = messageFormatter.formatBuySignal(signal);
        return sendMessage(message);
    }

    /**
     * Sends a SELL signal notification.
     *
     * @param signal the trading signal to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendSellSignal(Signal signal) {
        String message = messageFormatter.formatSellSignal(signal);
        return sendMessage(message);
    }

    /**
     * Sends a HOLD signal notification.
     *
     * @param signal the trading signal to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendHoldSignal(Signal signal) {
        String message = messageFormatter.formatHoldSignal(signal);
        return sendMessage(message);
    }

    /**
     * Sends a signal notification based on the signal type.
     *
     * @param signal the trading signal to send
     * @return true if message was sent, false otherwise
     */
    public boolean sendSignal(Signal signal) {
        if (signal.isBuySignal()) {
            return sendBuySignal(signal);
        } else if (signal.isSellSignal()) {
            return sendSellSignal(signal);
        } else {
            return sendHoldSignal(signal);
        }
    }

    /**
     * Sends a position entry notification when a new position is opened.
     *
     * @param position the position that was entered
     * @return true if message was sent, false otherwise
     */
    public boolean sendPositionEntry(Position position) {
        String message = messageFormatter.formatPositionEntry(position);
        return sendMessage(message);
    }

    /**
     * Sends a position update notification with current P&L.
     *
     * @param position the position to update
     * @return true if message was sent, false otherwise
     */
    public boolean sendPositionUpdate(Position position) {
        String message = messageFormatter.formatPositionUpdate(position);
        return sendMessage(message);
    }

    /**
     * Sends a position exit notification when a position is closed.
     *
     * @param position the position that was exited
     * @param trade the trade that was completed
     * @return true if message was sent, false otherwise
     */
    public boolean sendPositionExit(Position position, Trade trade) {
        String message = messageFormatter.formatPositionExit(position, trade);
        return sendMessage(message);
    }

    /**
     * Sends a stop loss hit notification.
     *
     * @param position the position that hit stop loss
     * @param exitPrice the price at which the position was stopped
     * @param pnl the profit/loss from the stop loss
     * @return true if message was sent, false otherwise
     */
    public boolean sendStopLossHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        String message = messageFormatter.formatStopLossHit(position, exitPrice, pnl);
        return sendMessage(message);
    }

    /**
     * Sends a target hit notification.
     *
     * @param position the position that hit target
     * @param exitPrice the price at which the position was exited
     * @param pnl the profit/loss from the target hit
     * @return true if message was sent, false otherwise
     */
    public boolean sendTargetHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        String message = messageFormatter.formatTargetHit(position, exitPrice, pnl);
        return sendMessage(message);
    }

    /**
     * Sends an order execution notification.
     *
     * @param order the executed order
     * @return true if message was sent, false otherwise
     */
    public boolean sendOrderExecution(Order order) {
        String message = messageFormatter.formatOrderExecution(order);
        return sendMessage(message);
    }

    /**
     * Sends an order fill notification.
     *
     * @param order the filled order
     * @return true if message was sent, false otherwise
     */
    public boolean sendOrderFill(Order order) {
        String message = messageFormatter.formatOrderFill(order);
        return sendMessage(message);
    }

    /**
     * Sends an order cancellation notification.
     *
     * @param order the cancelled order
     * @return true if message was sent, false otherwise
     */
    public boolean sendOrderCancellation(Order order) {
        String message = messageFormatter.formatOrderCancellation(order);
        return sendMessage(message);
    }

    /**
     * Sends a daily summary report notification.
     *
     * @param summary the daily summary details
     * @return true if message was sent, false otherwise
     */
    public boolean sendDailySummary(DailySummary summary) {
        String message = messageFormatter.formatDailySummary(summary);
        return sendMessage(message);
    }

    /**
     * Sends an error alert notification.
     *
     * @param errorType the type of error
     * @param message the error message
     * @param severity the severity level (LOW, MEDIUM, HIGH, CRITICAL)
     * @return true if message was sent, false otherwise
     */
    public boolean sendErrorAlert(String errorType, String message, ErrorSeverity severity) {
        String notification = messageFormatter.formatErrorAlert(errorType, message, severity);
        return sendMessage(notification);
    }

    /**
     * Sends a position size alert when a position is approaching size limits.
     *
     * @param symbol the stock symbol
     * @param currentSize the current position size
     * @param maxSize the maximum allowed position size
     * @param percentage the percentage of maximum size
     * @return true if message was sent, false otherwise
     */
    public boolean sendPositionSizeAlert(String symbol, BigDecimal currentSize, BigDecimal maxSize, BigDecimal percentage) {
        String message = messageFormatter.formatPositionSizeAlert(symbol, currentSize, maxSize, percentage);
        return sendMessage(message);
    }

    /**
     * Sends a risk warning notification.
     *
     * @param symbol the stock symbol
     * @param riskPercent the risk percentage
     * @return true if message was sent, false otherwise
     */
    public boolean sendRiskWarning(String symbol, BigDecimal riskPercent) {
        String message = messageFormatter.formatRiskWarning(symbol, riskPercent);
        return sendMessage(message);
    }

    /**
     * Sends a system status notification.
     *
     * @param status the system status message
     * @return true if message was sent, false otherwise
     */
    public boolean sendSystemStatus(String status) {
        String message = messageFormatter.formatSystemStatus(status);
        return sendMessage(message);
    }

    /**
     * Sends a market open/close notification.
     *
     * @param isOpening whether the market is opening
     * @return true if message was sent, false otherwise
     */
    public boolean sendMarketStatus(boolean isOpening) {
        String message = messageFormatter.formatMarketStatus(isOpening);
        return sendMessage(message);
    }

    /**
     * Initialize command handlers for Telegram bot commands.
     */
    private void initializeCommandHandlers() {
        // /stop command - activate kill switch
        commandHandlers.put("/stop", (args, chatId) -> handleStopCommand(chatId));

        // /resume command - deactivate kill switch
        commandHandlers.put("/resume", (args, chatId) -> handleResumeCommand(chatId));

        // /status command - show current system status
        commandHandlers.put("/status", (args, chatId) -> handleStatusCommand(chatId));

        // /help command - show available commands
        commandHandlers.put("/help", (args, chatId) -> handleHelpCommand(chatId));
    }

    /**
     * Process a Telegram command message.
     *
     * @param command the command text (e.g., "/stop")
     * @param chatId the chat ID
     * @param args any arguments to the command
     * @return true if command was handled
     */
    public boolean processCommand(String command, String chatId, String args) {
        if (command == null || command.isEmpty()) {
            return false;
        }

        CommandHandler handler = commandHandlers.get(command);
        if (handler != null) {
            handler.handle(args, chatId);
            return true;
        }

        sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId,
                "Unknown command: " + command + "\nUse /help for available commands");
        return false;
    }

    /**
     * Handle /stop command - activate kill switch.
     */
    private void handleStopCommand(String chatId) {
        if (!killSwitchEnabled()) {
            sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId,
                    "⚠️ *KILL SWITCH ACTIVATED*\n\n" +
                    "All trading has been halted.\n" +
                    "No new orders will be placed.\n" +
                    "Use /resume to reactivate trading.");

            killSwitchActive = true;
            killSwitchActivatedTime = LocalDateTime.now();
            killSwitchReason = "Manual activation via Telegram /stop command";

            // Also update risk controls
            if (riskControlsService != null) {
                riskControlsService.setKillSwitchActive(true);
            }

            log.warn("Kill switch activated via Telegram from chat: {}", chatId);
        } else {
            sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId,
                    "ℹ️ Kill switch is already active.\n" +
                    "Trading is currently halted.\n" +
                    "Use /resume to reactivate.");
        }
    }

    /**
     * Handle /resume command - deactivate kill switch.
     */
    private void handleResumeCommand(String chatId) {
        if (killSwitchActive) {
            sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId,
                    "✅ *KILL SWITCH DEACTIVATED*\n\n" +
                    "Trading has been resumed.\n" +
                    "All risk controls are active.");

            killSwitchActive = false;
            killSwitchActivatedTime = null;
            killSwitchReason = null;

            // Also update risk controls
            if (riskControlsService != null) {
                riskControlsService.setKillSwitchActive(false);
            }

            log.info("Kill switch deactivated via Telegram from chat: {}", chatId);
        } else {
            sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId,
                    "ℹ️ Kill switch is already inactive.\n" +
                    "Trading is currently active.");
        }
    }

    /**
     * Handle /status command - show system status.
     */
    private void handleStatusCommand(String chatId) {
        StringBuilder status = new StringBuilder();
        status.append("📊 *SYSTEM STATUS*\n\n");

        // Kill switch status
        status.append(killSwitchActive ? "⛔ Kill Switch: ACTIVE\n" : "✅ Kill Switch: INACTIVE\n");

        // Position count
        if (riskControlsService != null) {
            status.append(String.format("📍 Positions: %d / %d\n",
                    riskControlsService.getCurrentPositionCount(),
                    riskControlsService.getRemainingPositionCapacity() +
                    riskControlsService.getCurrentPositionCount()));

            BigDecimal dailyPnL = riskControlsService.getCurrentDailyPnL();
            status.append(String.format("📈 Daily P&L: ₹%,.2f (%,.2f%%)\n",
                    dailyPnL, riskControlsService.getDailyLossPercent()));
        }

        // Circuit breaker status
        if (riskControlsService != null && riskControlsService.getDailyLossPercent() != null) {
            BigDecimal lossPercent = riskControlsService.getDailyLossPercent();
            if (lossPercent.compareTo(new BigDecimal("-2.0")) < 0) {
                status.append("⚠️ Daily loss circuit: OPEN\n");
            } else {
                status.append("✅ Daily loss circuit: CLOSED\n");
            }
        }

        sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId, status.toString());
    }

    /**
     * Handle /help command - show available commands.
     */
    private void handleHelpCommand(String chatId) {
        String help = "📖 *AVAILABLE COMMANDS*\n\n" +
                "/stop - Activate kill switch (halt all trading)\n" +
                "/resume - Deactivate kill switch (resume trading)\n" +
                "/status - Show current system status\n" +
                "/help - Show this help message";

        sendMessageToChat("https://api.telegram.org/bot" + botToken, chatId, help);
    }

    /**
     * Check if kill switch is enabled in configuration.
     */
    public boolean killSwitchEnabled() {
        return killSwitchActive;
    }

    /**
     * Get kill switch activation time.
     */
    public LocalDateTime getKillSwitchActivatedTime() {
        return killSwitchActivatedTime;
    }

    /**
     * Get kill switch reason.
     */
    public String getKillSwitchReason() {
        return killSwitchReason;
    }

    /**
     * Records to send a message (for potential batching or delayed delivery).
     *
     * @param messageType the type of message
     * @param content the message content
     */
    public void queueMessage(MessageType messageType, String content) {
        // Current implementation sends immediately
        // This can be extended for batched delivery
        sendMessage(content);
    }

    /**
     * Functional interface for command handlers.
     */
    @FunctionalInterface
    public interface CommandHandler {
        void handle(String args, String chatId);
    }

    /**
     * Enum for message types.
     */
    public enum MessageType {
        SIGNAL,
        POSITION_ENTRY,
        POSITION_UPDATE,
        POSITION_EXIT,
        ORDER_EXECUTION,
        DAILY_SUMMARY,
        ERROR_ALERT,
        SYSTEM_STATUS,
        RISK_WARNING
    }

    /**
     * Enum for error severity levels.
     */
    public enum ErrorSeverity {
        LOW("low", "ℹ️"),
        MEDIUM("medium", "⚠️"),
        HIGH("high", "🔴"),
        CRITICAL("critical", "🚨");

        private final String value;
        private final String emoji;

        ErrorSeverity(String value, String emoji) {
            this.value = value;
            this.emoji = emoji;
        }

        public String getValue() {
            return value;
        }

        public String getEmoji() {
            return emoji;
        }
    }

    /**
     * DTO for daily summary reports.
     */
    public static class DailySummary {
        private final String date;
        private final int totalSignals;
        private final int buySignals;
        private final int sellSignals;
        private final int holdSignals;
        private final int positionsOpened;
        private final int positionsClosed;
        private final BigDecimal totalPnL;
        private final BigDecimal winningTrades;
        private final BigDecimal losingTrades;
        private final double winRate;
        private final int activePositions;
        private final String marketSentiment;

        public DailySummary(
            String date,
            int totalSignals,
            int buySignals,
            int sellSignals,
            int holdSignals,
            int positionsOpened,
            int positionsClosed,
            BigDecimal totalPnL,
            BigDecimal winningTrades,
            BigDecimal losingTrades,
            double winRate,
            int activePositions,
            String marketSentiment
        ) {
            this.date = date;
            this.totalSignals = totalSignals;
            this.buySignals = buySignals;
            this.sellSignals = sellSignals;
            this.holdSignals = holdSignals;
            this.positionsOpened = positionsOpened;
            this.positionsClosed = positionsClosed;
            this.totalPnL = totalPnL;
            this.winningTrades = winningTrades;
            this.losingTrades = losingTrades;
            this.winRate = winRate;
            this.activePositions = activePositions;
            this.marketSentiment = marketSentiment;
        }

        // Getters
        public String getDate() { return date; }
        public int getTotalSignals() { return totalSignals; }
        public int getBuySignals() { return buySignals; }
        public int getSellSignals() { return sellSignals; }
        public int getHoldSignals() { return holdSignals; }
        public int getPositionsOpened() { return positionsOpened; }
        public int getPositionsClosed() { return positionsClosed; }
        public BigDecimal getTotalPnL() { return totalPnL; }
        public BigDecimal getWinningTrades() { return winningTrades; }
        public BigDecimal getLosingTrades() { return losingTrades; }
        public double getWinRate() { return winRate; }
        public int getActivePositions() { return activePositions; }
        public String getMarketSentiment() { return marketSentiment; }
    }
}
