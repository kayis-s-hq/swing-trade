package com.swingtrade.broker.telegram;

import com.swingtrade.broker.model.Position;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Formatter for creating Signal notification messages.
 * Provides formatted messages with emojis and markdown styling for trade event notifications.
 *
 * Per project decision, Signal notifications are limited to trade events only:
 * - Trade open (position entry)
 * - Trade close (position exit with P&L)
 * - Stop loss hit
 * - Target hit
 *
 * Signal notifications do NOT include:
 * - Signal alerts (BUY/SELL/HOLD) - deferred
 * - Position updates - deferred
 * - System status - deferred
 *
 * Message format includes full details:
 * - Symbol
 * - Price (entry/exit)
 * - Quantity
 * - P&L (with currency formatting and color indicators)
 * - Position reasoning
 * - Signal confidence and reasoning
 * - Risk-reward ratio
 *
 * @author SwingTrade Team
 */
@Component
@Slf4j
public class SignalMessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * Format a trade open notification.
     * Includes symbol, direction, quantity, entry price, stop loss, target,
     * signal confidence, reasoning, and risk-reward ratio.
     *
     * @param position the position that was entered
     * @param signal the trading signal that triggered this position
     * @return formatted message string
     */
    public String formatTradeOpen(Position position, Signal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 <b>TRADE OPENED</b>\n\n");

        // Symbol and direction
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(position.getSymbol())
          .append("</code>\n");
        sb.append("🚩 <b>Direction:</b> ")
          .append(formatDirection(position.getDirection()))
          .append("\n");

        // Quantity and price
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(position.getQuantity())
          .append("</code>\n");
        sb.append("💰 <b>Entry Price:</b> <code>")
          .append(formatPrice(position.getEntryPrice()))
          .append("</code>\n");
        sb.append("💵 <b>Total Value:</b> <code>")
          .append(formatPrice(position.getEntryPrice().multiply(
              new BigDecimal(position.getQuantity()))))
          .append("</code>\n");

        // Stop loss and target
        sb.append("🛑 <b>Stop Loss:</b> <code>")
          .append(formatPrice(position.getSlPrice()))
          .append("</code>\n");
        sb.append("🎯 <b>Target:</b> <code>")
          .append(formatPrice(position.getTargetPrice()))
          .append("</code>\n");

        // Signal information
        if (signal != null) {
            sb.append("\n📊 <b>Signal Confidence:</b> <b>")
              .append(formatPercentage(signal.confidence()))
              .append("</b>\n");

            if (signal.riskReward() != null) {
                sb.append("📉 <b>Risk:Reward:</b> <b>")
                  .append(signal.riskReward())
                  .append("</b>\n");
            }

            sb.append("\nℹ️ <b>Signal Reasoning:</b>\n<code>")
              .append(signal.reasoning() != null ? signal.reasoning() : "N/A")
              .append("</code>\n");

            if (signal.indicators() != null && !signal.indicators().isEmpty()) {
                sb.append("🔍 <b>Indicators:</b> <code>")
                  .append(signal.indicators())
                  .append("</code>\n");
            }
        }

        // Entry time
        sb.append("\n⏰ <b>Entry Time:</b> ")
          .append(position.getEntryTime().format(DATE_TIME_FORMATTER));

        return sb.toString();
    }

    /**
     * Format a trade close notification.
     * Includes symbol, entry/exit price, quantity, P&L (with color), duration,
     * exit reason, and fees.
     *
     * @param position the position that was exited
     * @param trade the completed trade
     * @return formatted message string
     */
    public String formatTradeClose(Position position, Trade trade) {
        StringBuilder sb = new StringBuilder();

        boolean isProfit = trade.isProfitable();
        String emoji = isProfit ? "✅" : "❌";
        String status = isProfit ? "TARGET HIT / PROFIT" : "STOP LOSS / LOSS";

        sb.append(emoji).append(" <b>").append(status)
          .append("</b>\n\n");

        // Symbol and exit details
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(trade.symbol())
          .append("</code>\n");
        sb.append("🚪 <b>Exit Price:</b> <code>")
          .append(formatPrice(trade.exitPrice()))
          .append("</code>\n");
        sb.append("📅 <b>Exit Date:</b> ")
          .append(trade.exitDate().format(DATE_FORMATTER))
          .append("\n");
        sb.append("⏱️ <b>Duration:</b> <code>")
          .append(trade.durationDays())
          .append(" day(s)</code>\n");

        // Quantity and P&L
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(trade.quantity())
          .append("</code>\n");
        sb.append("💰 <b>P&L:</b> <b>")
          .append(formatCurrency(trade.totalPnL()))
          .append("</b>\n");
        sb.append("📊 <b>P&L %:</b> <b>")
          .append(formatPercentage(calculateTradePnLPercent(trade)))
          .append("</b>\n");

        // Fees
        if (trade.fees() != null && trade.fees().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 <b>Fees:</b> <code>")
              .append(formatCurrency(trade.fees()))
              .append("</code>\n");
        }

        // Exit reason
        sb.append("\n📝 <b>Exit Reason:</b>\n<code>")
          .append(trade.exitReason() != null ? trade.exitReason() : "N/A")
          .append("</code>");

        return sb.toString();
    }

    /**
     * Format a stop loss hit notification.
     * Includes symbol, exit price, expected SL, loss amount, loss percentage.
     *
     * @param position the position that hit stop loss
     * @param exitPrice the price at which the position was stopped
     * @param pnl the profit/loss from the stop loss
     * @return formatted message string
     */
    public String formatStopLossHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔴 <b>STOP LOSS HIT</b>\n\n");

        sb.append("📉 <b>Symbol:</b> <code>")
          .append(position.getSymbol())
          .append("</code>\n");
        sb.append("💥 <b>Exit Price:</b> <code>")
          .append(formatPrice(exitPrice))
          .append("</code>\n");
        sb.append("🛑 <b>Expected SL:</b> <code>")
          .append(formatPrice(position.getSlPrice()))
          .append("</code>\n");
        sb.append("📅 <b>Exit Time:</b> ")
          .append(position.getExitTime() != null
              ? position.getExitTime().format(DATE_TIME_FORMATTER)
              : LocalDateTime.now().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("❌ <b>Loss:</b> <code>")
          .append(formatCurrency(pnl))
          .append("</code>\n");
        sb.append("📊 <b>Loss %:</b> <b>")
          .append(formatPercentage(calculatePnLPercentForPrice(position.getEntryPrice(), exitPrice, position.getQuantity())))
          .append("</b>\n");

        return sb.toString();
    }

    /**
     * Format a target hit notification.
     * Includes symbol, exit price, target price, profit amount, profit percentage.
     *
     * @param position the position that hit target
     * @param exitPrice the price at which the position was exited
     * @param pnl the profit/loss from the target hit
     * @return formatted message string
     */
    public String formatTargetHit(Position position, BigDecimal exitPrice, BigDecimal pnl) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>TARGET HIT!</b>\n\n");

        sb.append("📈 <b>Symbol:</b> <code>")
          .append(position.getSymbol())
          .append("</code>\n");
        sb.append("🎯 <b>Exit Price:</b> <code>")
          .append(formatPrice(exitPrice))
          .append("</code>\n");
        sb.append("🎯 <b>Expected Target:</b> <code>")
          .append(formatPrice(position.getTargetPrice()))
          .append("</code>\n");
        sb.append("📅 <b>Exit Time:</b> ")
          .append(position.getExitTime() != null
              ? position.getExitTime().format(DATE_TIME_FORMATTER)
              : LocalDateTime.now().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("💰 <b>Profit:</b> <code>")
          .append(formatCurrency(pnl))
          .append("</code>\n");
        sb.append("📊 <b>Profit %:</b> <b>")
          .append(formatPercentage(calculatePnLPercentForPrice(position.getEntryPrice(), exitPrice, position.getQuantity())))
          .append("</b>\n");

        return sb.toString();
    }

    // =================================================================
    // Helper Formatting Methods
    // =================================================================

    /**
     * Format a price value for display.
     *
     * @param price the price to format
     * @return formatted price string
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }
        return String.format("%.2f", price);
    }

    /**
     * Format a currency value for display with color indicators.
     *
     * @param amount the amount to format
     * @return formatted currency string with emoji
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }

        String formatted = String.format("%.2f", amount);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "🟢 +" + formatted;
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "🔴 " + formatted;
        }
        return "🟡 " + formatted;
    }

    /**
     * Format a percentage value for display (0-1 scale).
     *
     * @param percentage the percentage to format (0-1 scale)
     * @return formatted percentage string with emoji
     */
    private String formatPercentage(BigDecimal percentage) {
        if (percentage == null) {
            return "N/A";
        }

        double percentValue = percentage.doubleValue() * 100;
        String formatted = String.format("%.2f%%", percentValue);

        if (percentValue > 0) {
            return "🟢 " + formatted;
        } else if (percentValue < 0) {
            return "🔴 " + formatted;
        }
        return "🟡 " + formatted;
    }

    /**
     * Format the trade direction.
     *
     * @param direction the direction to format
     * @return formatted direction string
     */
    private String formatDirection(com.swingtrade.broker.model.TradeDirection direction) {
        if (direction == null) {
            return "N/A";
        }

        if (direction == com.swingtrade.broker.model.TradeDirection.LONG) {
            return "📈 LONG (Buy)";
        } else if (direction == com.swingtrade.broker.model.TradeDirection.SHORT) {
            return "📉 SHORT (Sell)";
        }
        return direction.toString();
    }

    /**
     * Calculate the P&L percentage for a trade.
     *
     * @param trade the trade to calculate
     * @return the P&L percentage (0-1 scale)
     */
    private BigDecimal calculateTradePnLPercent(Trade trade) {
        if (trade.entryPrice() == null || trade.entryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceChange = trade.exitPrice().subtract(trade.entryPrice());
        return priceChange.divide(trade.entryPrice(), 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate the P&L percentage for a specific entry and exit price.
     *
     * @param entryPrice the entry price
     * @param exitPrice the exit price
     * @param quantity the quantity
     * @return the P&L percentage (0-1 scale)
     */
    private BigDecimal calculatePnLPercentForPrice(BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal quantity) {
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceChange = exitPrice.subtract(entryPrice);
        return priceChange.divide(entryPrice, 4, BigDecimal.ROUND_HALF_UP);
    }
}
