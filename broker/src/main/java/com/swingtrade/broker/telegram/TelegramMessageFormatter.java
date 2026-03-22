package com.swingtrade.broker.telegram;

import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.model.OrderStatus;
import com.swingtrade.broker.model.OrderType;
import com.swingtrade.broker.model.Position;
import com.swingtrade.broker.model.PositionStatus;
import com.swingtrade.broker.model.TradeDirection;
import com.swingtrade.broker.telegram.TelegramNotificationService.DailySummary;
import com.swingtrade.broker.telegram.TelegramNotificationService.ErrorSeverity;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.Trade;
import com.swingtrade.domain.Trade.TradeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatter for creating Telegram notification messages.
 * Provides formatted messages with emojis and markdown styling for trading notifications.
 *
 * @author SwingTrade Team
 */
@Component
@Slf4j
public class TelegramMessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DateTimeFormatter DATE_FULL_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    /**
     * Format a BUY signal notification.
     *
     * @param signal the BUY signal to format
     * @return formatted message string
     */
    public String formatBuySignal(Signal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("🟢 <b>BUY SIGNAL</b>\n\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(signal.symbol())
          .append("</code>\n");
        sb.append("📅 <b>Date:</b> ")
          .append(signal.date().format(DATE_FORMATTER))
          .append("\n");
        sb.append("📊 <b>Confidence:</b> <b>")
          .append(formatPercentage(signal.confidence()))
          .append("</b>\n");
        sb.append("💰 <b>Entry Price:</b> <code>")
          .append(formatPrice(signal.entryPrice()))
          .append("</code>\n");
        sb.append("🛑 <b>Stop Loss:</b> <code>")
          .append(formatPrice(signal.stopLoss()))
          .append("</code>\n");
        sb.append("🎯 <b>Target:</b> <code>")
          .append(formatPrice(signal.target()))
          .append("</code>\n");
        if (signal.riskReward() != null) {
            sb.append("📉 <b>R:R Ratio:</b> <b>")
              .append(signal.riskReward())
              .append("</b>\n");
        }
        sb.append("\nℹ️ <b>Reasoning:</b>\n<code>")
          .append(signal.reasoning())
          .append("</code>\n");
        if (signal.indicators() != null && !signal.indicators().isEmpty()) {
            sb.append("\n🔍 <b>Indicators:</b> <code>")
              .append(signal.indicators())
              .append("</code>");
        }
        return sb.toString();
    }

    /**
     * Format a SELL signal notification.
     *
     * @param signal the SELL signal to format
     * @return formatted message string
     */
    public String formatSellSignal(Signal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔴 <b>SELL SIGNAL</b>\n\n");
        sb.append("📉 <b>Symbol:</b> <code>")
          .append(signal.symbol())
          .append("</code>\n");
        sb.append("📅 <b>Date:</b> ")
          .append(signal.date().format(DATE_FORMATTER))
          .append("\n");
        sb.append("📊 <b>Confidence:</b> <b>")
          .append(formatPercentage(signal.confidence()))
          .append("</b>\n");
        sb.append("💰 <b>Entry Price:</b> <code>")
          .append(formatPrice(signal.entryPrice()))
          .append("</code>\n");
        sb.append("🛑 <b>Stop Loss:</b> <code>")
          .append(formatPrice(signal.stopLoss()))
          .append("</code>\n");
        sb.append("🎯 <b>Target:</b> <code>")
          .append(formatPrice(signal.target()))
          .append("</code>\n");
        if (signal.riskReward() != null) {
            sb.append("📉 <b>R:R Ratio:</b> <b>")
              .append(signal.riskReward())
              .append("</b>\n");
        }
        sb.append("\nℹ️ <b>Reasoning:</b>\n<code>")
          .append(signal.reasoning())
          .append("</code>");
        return sb.toString();
    }

    /**
     * Format a HOLD signal notification.
     *
     * @param signal the HOLD signal to format
     * @return formatted message string
     */
    public String formatHoldSignal(Signal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("🟡 <b>HOLD SIGNAL</b>\n\n");
        sb.append("⏸️ <b>Symbol:</b> <code>")
          .append(signal.symbol())
          .append("</code>\n");
        sb.append("📅 <b>Date:</b> ")
          .append(signal.date().format(DATE_FORMATTER))
          .append("\n");
        sb.append("📊 <b>Confidence:</b> <b>")
          .append(formatPercentage(signal.confidence()))
          .append("</b>\n");
        sb.append("\nℹ️ <b>Reasoning:</b>\n<code>")
          .append(signal.reasoning())
          .append("</code>");
        return sb.toString();
    }

    /**
     * Format a position entry notification.
     *
     * @param position the position that was entered
     * @return formatted message string
     */
    public String formatPositionEntry(Position position) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 <b>POSITION ENTERED</b>\n\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(position.getSymbol())
          .append("</code>\n");
        sb.append("🚩 <b>Direction:</b> ")
          .append(formatDirection(position.getDirection()))
          .append("\n");
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(position.getQuantity())
          .append("</code>\n");
        sb.append("💰 <b>Entry Price:</b> <code>")
          .append(formatPrice(position.getEntryPrice()))
          .append("</code>\n");
        sb.append("💵 <b>Total Value:</b> <code>")
          .append(formatPrice(position.getEntryPrice().multiply(
              position.getQuantity())))
          .append("</code>\n");
        sb.append("🛑 <b>Stop Loss:</b> <code>")
          .append(formatPrice(position.getSlPrice()))
          .append("</code>\n");
        sb.append("🎯 <b>Target:</b> <code>")
          .append(formatPrice(position.getTargetPrice()))
          .append("</code>\n");
        sb.append("📅 <b>Entry Time:</b> ")
          .append(position.getEntryTime().format(DATE_TIME_FORMATTER))
          .append("\n");
        if (position.getProfitLoss() != null) {
            sb.append("📈 <b>Initial P&L:</b> <code>")
              .append(formatCurrency(position.getProfitLoss()))
              .append("</code>\n");
        }
        return sb.toString();
    }

    /**
     * Format a position update notification with current P&L.
     *
     * @param position the position to update
     * @return formatted message string
     */
    public String formatPositionUpdate(Position position) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>POSITION UPDATE</b>\n\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(position.getSymbol())
          .append("</code>\n");
        sb.append("💵 <b>Current Price:</b> <code>")
          .append(formatPrice(position.getCurrentPrice()))
          .append("</code>\n");
        sb.append("💰 <b>Entry Price:</b> <code>")
          .append(formatPrice(position.getEntryPrice()))
          .append("</code>\n");
        sb.append("📉 <b>Change:</b> <code>")
          .append(formatPrice(position.getCurrentPrice().subtract(position.getEntryPrice())))
          .append("</code>\n");
        sb.append("📈 <b>P&L:</b> <code>")
          .append(formatCurrency(position.getProfitLoss()))
          .append("</code>\n");

        BigDecimal pnlPercent = calculatePnLPercent(position);
        String pnlPercentStr = formatPercentage(pnlPercent);
        sb.append("📊 <b>P&L %:</b> <b>")
          .append(pnlPercentStr)
          .append("</b>\n");

        BigDecimal stopLossPct = calculateDistanceToStopLoss(pnlPercent);
        BigDecimal targetPct = calculateDistanceToTarget(pnlPercent);

        if (stopLossPct != null) {
            sb.append("📉 <b>Stop Loss:</b> <code>")
              .append(formatPrice(position.getSlPrice()))
              .append("</code> (")
              .append(formatDistancePercentage(stopLossPct))
              .append(")\n");
        }
        if (targetPct != null) {
            sb.append("🎯 <b>Target:</b> <code>")
              .append(formatPrice(position.getTargetPrice()))
              .append("</code> (")
              .append(formatDistancePercentage(targetPct))
              .append(")\n");
        }
        sb.append("⏱️ <b>Time:</b> ")
          .append(position.getEntryTime().format(TIME_FORMATTER));
        return sb.toString();
    }

    /**
     * Format a position exit notification.
     *
     * @param position the position that was exited
     * @param trade the completed trade
     * @return formatted message string
     */
    public String formatPositionExit(Position position, Trade trade) {
        StringBuilder sb = new StringBuilder();

        boolean isProfit = trade.isProfitable();
        String emoji = isProfit ? "✅" : "❌";
        String status = isProfit ? "TARGET HIT / PROFIT" : "STOP LOSS / LOSS";

        sb.append(emoji).append(" <b>").append(status)
          .append("</b>\n\n");
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
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(trade.quantity())
          .append("</code>\n");
        sb.append("💰 <b>P&L:</b> <b>")
          .append(formatCurrency(trade.totalPnL()))
          .append("</b>\n");
        sb.append("📊 <b>P&L %:</b> <b>")
          .append(formatPercentage(calculateTradePnLPercent(trade)))
          .append("</b>\n");
        sb.append("💸 <b>Fees:</b> <code>")
          .append(formatCurrency(trade.fees()))
          .append("</code>\n");
        sb.append("\n📝 <b>Exit Reason:</b>\n<code>")
          .append(trade.exitReason())
          .append("</code>");
        return sb.toString();
    }

    /**
     * Format a stop loss hit notification.
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

    /**
     * Format an order execution notification.
     *
     * @param order the executed order
     * @return formatted message string
     */
    public String formatOrderExecution(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("🚀 <b>ORDER EXECUTED</b>\n\n");
        sb.append("📋 <b>Order ID:</b> <code>")
          .append(order.getOrderId())
          .append("</code>\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(order.getSymbol())
          .append("</code>\n");
        sb.append("📊 <b>Type:</b> <code>")
          .append(order.getType())
          .append("</code>\n");
        sb.append("🚩 <b>Direction:</b> ")
          .append(formatDirection(order.getDirection()))
          .append("\n");
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(order.getQuantity())
          .append("</code>\n");

        if (order.getLimitPrice() != null) {
            sb.append("💰 <b>Limit Price:</b> <code>")
              .append(formatPrice(order.getLimitPrice()))
              .append("</code>\n");
        }
        if (order.getStopPrice() != null) {
            sb.append("🛑 <b>Stop Price:</b> <code>")
              .append(formatPrice(order.getStopPrice()))
              .append("</code>\n");
        }
        sb.append("📅 <b>Timestamp:</b> ")
          .append(order.getTimestamp().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("⚡ <b>Status:</b> <code>")
          .append(order.getStatus())
          .append("</code>");
        return sb.toString();
    }

    /**
     * Format an order fill notification.
     *
     * @param order the filled order
     * @return formatted message string
     */
    public String formatOrderFill(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>ORDER FILLED</b>\n\n");
        sb.append("📋 <b>Order ID:</b> <code>")
          .append(order.getOrderId())
          .append("</code>\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(order.getSymbol())
          .append("</code>\n");
        sb.append("💵 <b>Fill Price:</b> <code>")
          .append(formatPrice(order.getPrice()))
          .append("</code>\n");
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(order.getQuantity())
          .append("</code>\n");
        sb.append("💰 <b>Total Value:</b> <code>")
          .append(formatPrice(order.getPrice().multiply(order.getQuantity())))
          .append("</code>\n");

        if (order.getCommission() != null && order.getCommission().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("💸 <b>Commission:</b> <code>")
              .append(formatCurrency(order.getCommission()))
              .append("</code>\n");
        }

        sb.append("⏱️ <b>Execution Time:</b> ")
          .append(order.getExecutionTime() != null
              ? order.getExecutionTime().format(DATE_TIME_FORMATTER)
              : LocalDateTime.now().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("✅ <b>Status:</b> <code>FILLED</code>");
        return sb.toString();
    }

    /**
     * Format an order cancellation notification.
     *
     * @param order the cancelled order
     * @return formatted message string
     */
    public String formatOrderCancellation(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ <b>ORDER CANCELLED</b>\n\n");
        sb.append("📋 <b>Order ID:</b> <code>")
          .append(order.getOrderId())
          .append("</code>\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(order.getSymbol())
          .append("</code>\n");
        sb.append("📊 <b>Type:</b> <code>")
          .append(order.getType())
          .append("</code>\n");
        sb.append("🚩 <b>Direction:</b> ")
          .append(formatDirection(order.getDirection()))
          .append("\n");
        sb.append("🔢 <b>Quantity:</b> <code>")
          .append(order.getQuantity())
          .append("</code>\n");
        sb.append("💰 <b>Price:</b> <code>")
          .append(formatPrice(order.getPrice()))
          .append("</code>\n");
        sb.append("📅 <b>Cancelled At:</b> ")
          .append(LocalDateTime.now().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("✅ <b>Status:</b> <code>CANCELLED</code>");
        return sb.toString();
    }

    /**
     * Format a daily summary report notification.
     *
     * @param summary the daily summary details
     * @return formatted message string
     */
    public String formatDailySummary(DailySummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>DAILY TRADING SUMMARY</b>\n");
        sb.append("📅 <b>Date:</b> <code>")
          .append(summary.getDate().equals("TODAY") ? "Today" : summary.getDate())
          .append("</code>\n\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📈 <b>SIGNALS</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 <b>Total Signals:</b> <code>")
          .append(summary.getTotalSignals())
          .append("</code>\n");
        sb.append("🟢 <b>BUY:</b> <code>")
          .append(summary.getBuySignals())
          .append("</code>\n");
        sb.append("🔴 <b>SELL:</b> <code>")
          .append(summary.getSellSignals())
          .append("</code>\n");
        sb.append("🟡 <b>HOLD:</b> <code>")
          .append(summary.getHoldSignals())
          .append("</code>\n");

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📝 <b>POSITIONS</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📝 <b>Opened:</b> <code>")
          .append(summary.getPositionsOpened())
          .append("</code>\n");
        sb.append("🚪 <b>Closed:</b> <code>")
          .append(summary.getPositionsClosed())
          .append("</code>\n");
        sb.append("📊 <b>Active:</b> <code>")
          .append(summary.getActivePositions())
          .append("</code>\n");

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("💰 <b>P&L</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("💵 <b>Total P&L:</b> <b>")
          .append(formatCurrency(summary.getTotalPnL()))
          .append("</b>\n");
        sb.append("✅ <b>Winning Trades:</b> <code>")
          .append(formatCurrency(summary.getWinningTrades()))
          .append("</code>\n");
        sb.append("❌ <b>Losing Trades:</b> <code>")
          .append(formatCurrency(summary.getLosingTrades()))
          .append("</code>\n");
        sb.append("📊 <b>Win Rate:</b> <b>")
          .append(String.format("%.1f%%", summary.getWinRate()))
          .append("</b>\n");

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        if (summary.getMarketSentiment() != null && !summary.getMarketSentiment().isEmpty()) {
            sb.append("🌐 <b>Market Sentiment:</b> <code>")
              .append(summary.getMarketSentiment())
              .append("</code>\n");
            sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        }
        sb.append("⏰ <b>Generated At:</b> ")
          .append(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return sb.toString();
    }

    /**
     * Format an error alert notification.
     *
     * @param errorType the type of error
     * @param message the error message
     * @param severity the severity level
     * @return formatted message string
     */
    public String formatErrorAlert(String errorType, String message, ErrorSeverity severity) {
        StringBuilder sb = new StringBuilder();
        sb.append(severity.getEmoji()).append(" <b>")
          .append(severity.getValue().toUpperCase())
          .append(" ERROR ALERT</b>\n\n");
        sb.append("🚨 <b>Error Type:</b> <code>")
          .append(errorType)
          .append("</code>\n");
        sb.append("⚠️ <b>Severity:</b> <b>")
          .append(severity.getValue().toUpperCase())
          .append("</b>\n");
        sb.append("📅 <b>Time:</b> ")
          .append(LocalDateTime.now().format(DATE_TIME_FORMATTER))
          .append("\n");
        sb.append("💬 <b>Message:</b>\n<code>")
          .append(message)
          .append("</code>");
        return sb.toString();
    }

    /**
     * Format a position size alert notification.
     *
     * @param symbol the stock symbol
     * @param currentSize the current position size
     * @param maxSize the maximum allowed position size
     * @param percentage the percentage of maximum size
     * @return formatted message string
     */
    public String formatPositionSizeAlert(String symbol, BigDecimal currentSize, BigDecimal maxSize, BigDecimal percentage) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ <b>POSITION SIZE WARNING</b>\n\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(symbol)
          .append("</code>\n");
        sb.append("💵 <b>Current Size:</b> <code>")
          .append(formatCurrency(currentSize))
          .append("</code>\n");
        sb.append("📊 <b>Max Allowed:</b> <code>")
          .append(formatCurrency(maxSize))
          .append("</code>\n");
        sb.append("📈 <b>Utilization:</b> <b>")
          .append(formatPercentage(percentage))
          .append("</b>\n");
        if (percentage.compareTo(new BigDecimal("80")) >= 0) {
            sb.append("\n🔴 <b>This position is at ").append(formatPercentage(percentage))
              .append(" of maximum allocation!</b>");
        } else if (percentage.compareTo(new BigDecimal("60")) >= 0) {
            sb.append("\n⚠️ <b>Approaching maximum position size.</b>");
        }
        return sb.toString();
    }

    /**
     * Format a risk warning notification.
     *
     * @param symbol the stock symbol
     * @param riskPercent the risk percentage
     * @return formatted message string
     */
    public String formatRiskWarning(String symbol, BigDecimal riskPercent) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ <b>RISK WARNING</b>\n\n");
        sb.append("📈 <b>Symbol:</b> <code>")
          .append(symbol)
          .append("</code>\n");
        sb.append("📉 <b>Risk to Entry:</b> <b>")
          .append(formatPercentage(riskPercent))
          .append("</b>\n");
        if (riskPercent.compareTo(new BigDecimal("2")) >= 0) {
            sb.append("\n🔴 <b>This position carries HIGH RISK!</b>\n");
            sb.append("💡 <b>Consider reducing position size.</b>");
        } else if (riskPercent.compareTo(new BigDecimal("1.5")) >= 0) {
            sb.append("\n⚠️ <b>Medium-High risk level.</b>");
        } else {
            sb.append("\n✅ <b>Risk is within acceptable limits.</b>");
        }
        return sb.toString();
    }

    /**
     * Format a system status notification.
     *
     * @param status the system status message
     * @return formatted message string
     */
    public String formatSystemStatus(String status) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 <b>SYS</b>TEM STATUS\n\n");
        sb.append("💬 <b>Message:</b>\n<code>")
          .append(status)
          .append("</code>\n");
        sb.append("⏰ <b>Time:</b> ")
          .append(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return sb.toString();
    }

    /**
     * Format a market status notification.
     *
     * @param isOpening whether the market is opening
     * @return formatted message string
     */
    public String formatMarketStatus(boolean isOpening) {
        if (isOpening) {
            return "🌅 <b>MARKET OPEN</b>\n\n" +
                   "📈 <b>NSE/BSE markets are now open.</b>\n" +
                   "⏰ <b>Time:</b> " + LocalDateTime.now().format(TIME_FORMATTER) + "\n" +
                   "🚀 <b>Trading activities enabled.</b>";
        } else {
            return "🌇 <b>MARKET CLOSE</b>\n\n" +
                   "📉 <b>NSE/BSE markets are now closed.</b>\n" +
                   "⏰ <b>Time:</b> " + LocalDateTime.now().format(TIME_FORMATTER) + "\n" +
                   "💤 <b>Trading activities paused.</b>";
        }
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
     * Format a currency value for display.
     *
     * @param amount the amount to format
     * @return formatted currency string
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }

        String formatted = String.format("%.2f", amount);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + formatted + " 🟢";
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return formatted + " 🔴";
        }
        return formatted + " 🟡";
    }

    /**
     * Format a percentage value for display.
     *
     * @param percentage the percentage to format (0-1 scale)
     * @return formatted percentage string
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
     * Format a percentage value for display (already in percentage scale).
     *
     * @param percentage the percentage to format (0-100 scale)
     * @return formatted percentage string
     */
    private String formatPercentage(double percentage) {
        String formatted = String.format("%.2f%%", percentage);
        if (percentage > 0) {
            return "🟢 " + formatted;
        } else if (percentage < 0) {
            return "🔴 " + formatted;
        }
        return "🟡 " + formatted;
    }

    /**
     * Format the distance percentage with direction indicator.
     *
     * @param percentage the percentage
     * @return formatted distance string
     */
    private String formatDistancePercentage(BigDecimal percentage) {
        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            return "🟢 +" + String.format("%.2f%%", percentage.doubleValue() * 100);
        } else if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return "🔴 " + String.format("%.2f%%", percentage.doubleValue() * 100);
        }
        return "🟡 0.00%";
    }

    /**
     * Format the trade direction.
     *
     * @param direction the direction to format
     * @return formatted direction string
     */
    private String formatDirection(TradeDirection direction) {
        if (direction == null) {
            return "N/A";
        }

        if (direction == TradeDirection.LONG) {
            return "📈 LONG (Buy)";
        } else if (direction == TradeDirection.SHORT) {
            return "📉 SHORT (Sell)";
        }
        return direction.toString();
    }

    /**
     * Calculate the P&L percentage for a position.
     *
     * @param position the position to calculate
     * @return the P&L percentage (0-1 scale)
     */
    private BigDecimal calculatePnLPercent(Position position) {
        if (position == null || position.getEntryPrice() == null || position.getEntryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceChange = position.getCurrentPrice().subtract(position.getEntryPrice());
        return priceChange.divide(position.getEntryPrice(), 4, BigDecimal.ROUND_HALF_UP);
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

    /**
     * Calculate the distance to stop loss as a percentage.
     *
     * @param currentPnLPercent the current P&L percentage
     * @return the distance to stop loss (0-1 scale), or null if SL not set
     */
    private BigDecimal calculateDistanceToStopLoss(BigDecimal currentPnLPercent) {
        return null; // Could be implemented if needed
    }

    /**
     * Calculate the distance to target as a percentage.
     *
     * @param currentPnLPercent the current P&L percentage
     * @return the distance to target (0-1 scale), or null if target not set
     */
    private BigDecimal calculateDistanceToTarget(BigDecimal currentPnLPercent) {
        return null; // Could be implemented if needed
    }
}
