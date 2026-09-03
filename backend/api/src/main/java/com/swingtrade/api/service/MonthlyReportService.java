package com.swingtrade.api.service;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.domain.Signal;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private final PositionRepository positionRepository;
    private final SignalRepository signalRepository;
    private final boolean schedulerEnabled;

    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");

    public MonthlyReportService(
            PositionRepository positionRepository,
            SignalRepository signalRepository,
            @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.positionRepository = positionRepository;
        this.signalRepository = signalRepository;
        this.schedulerEnabled = schedulerEnabled;
    }

    @PostConstruct
    public void init() {
        log.info("MonthlyReportService initialized with IST timezone");
    }

    @Scheduled(cron = "0 0 3 1-7 * SUN", zone = "Asia/Kolkata")
    public void generateMonthlyReport() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping monthly report");
            return;
        }

        log.info("Generating monthly strategy review report");

        try {
            LocalDate now = LocalDate.now(istZone);
            LocalDate firstDay = now.withDayOfMonth(1);
            LocalDate lastDay = now.withDayOfMonth(now.lengthOfMonth());

            List<PositionEntity> monthlyPositions = positionRepository.findAll();
            List<SignalEntity> monthlySignalEntities = signalRepository.findAll();
            List<Signal> monthlySignals = monthlySignalEntities.stream()
                .map(com.swingtrade.data.entity.SignalEntity::toDomain)
                .toList();

            Map<String, Object> report = buildReport(now, monthlyPositions, monthlySignals);

            String reportMessage = formatMonthlyReport(report);
            log.info("Monthly report: {}", reportMessage);

            log.info("Monthly report generated for period: {} to {}", firstDay, lastDay);

        } catch (Exception e) {
            log.error("Failed to generate monthly report", e);
        }
    }

    Map<String, Object> buildReport(LocalDate reportDate, List<PositionEntity> positions, List<Signal> signals) {
        Map<String, Object> report = new ConcurrentHashMap<>();

        // Period info
        report.put("reportDate", reportDate.toString());

        // Get monthly stats
        List<PositionEntity> closedPositions = positions.stream()
            .filter(p -> !"OPEN".equals(p.getStatus()))
            .toList();

        report.put("totalTrades", closedPositions.size());
        report.put("totalSignals", signals.size());

        // Trade metrics
        double winRate = calculateWinRate(closedPositions);
        double avgWinAmount = calculateAvgWinAmount(closedPositions);
        double avgLossAmount = calculateAvgLossAmount(closedPositions);
        double totalPnL = calculateTotalPnL(closedPositions);
        double profitFactor = calculateProfitFactor(closedPositions);

        report.put("winRate", String.format("%.2f%%", winRate));
        report.put("totalPnL", String.format("Rs. %.2f", totalPnL));
        report.put("avgWinAmount", String.format("Rs. %.2f", avgWinAmount));
        report.put("avgLossAmount", String.format("Rs. %.2f", avgLossAmount));
        report.put("profitFactor", String.format("%.2f", profitFactor));

        // Exit reason distribution
        long stopLossCount = positions.stream()
            .filter(p -> "STOPPED".equals(p.getStatus()))
            .count();
        long targetHitCount = positions.stream()
            .filter(p -> "TARGET_HIT".equals(p.getStatus()))
            .count();
        long manualCount = positions.stream()
            .filter(p -> "MANUAL".equals(p.getStatus()))
            .count();

        Map<String, Long> exitReasons = new ConcurrentHashMap<>();
        exitReasons.put("STOP_LOSS", stopLossCount);
        exitReasons.put("TARGET_HIT", targetHitCount);
        exitReasons.put("MANUAL", manualCount);
        report.put("exitReasons", exitReasons);

        // Signal distribution
        long buySignals = signals.stream()
            .filter(s -> com.swingtrade.domain.Signal.SignalType.BUY == s.type())
            .count();
        long sellSignals = signals.stream()
            .filter(s -> com.swingtrade.domain.Signal.SignalType.SELL == s.type())
            .count();
        long holdSignals = signals.stream()
            .filter(s -> com.swingtrade.domain.Signal.SignalType.HOLD == s.type())
            .count();

        Map<String, Long> signalDistribution = new ConcurrentHashMap<>();
        signalDistribution.put("BUY", buySignals);
        signalDistribution.put("SELL", sellSignals);
        signalDistribution.put("HOLD", holdSignals);
        report.put("signalDistribution", signalDistribution);

        // Performance stats
        report.put("totalReturn", "N/A");
        report.put("sharpeRatio", "N/A");
        report.put("maxDrawdown", "N/A");

        return report;
    }

    private double calculateWinRate(List<PositionEntity> positions) {
        if (positions.isEmpty()) return 0.0;
        long profitable = positions.stream()
            .filter(p -> "TARGET_HIT".equals(p.getStatus()) || p.getRealizedPnL() != null && p.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .count();
        return (profitable * 100.0) / positions.size();
    }

    private double calculateAvgWinAmount(List<PositionEntity> positions) {
        return positions.stream()
            .filter(p -> p.getRealizedPnL() != null && p.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .mapToDouble(p -> p.getRealizedPnL().doubleValue())
            .average()
            .orElse(0.0);
    }

    private double calculateAvgLossAmount(List<PositionEntity> positions) {
        return positions.stream()
            .filter(p -> p.getRealizedPnL() != null && p.getRealizedPnL().compareTo(BigDecimal.ZERO) < 0)
            .mapToDouble(p -> Math.abs(p.getRealizedPnL().doubleValue()))
            .average()
            .orElse(0.0);
    }

    private double calculateTotalPnL(List<PositionEntity> positions) {
        return positions.stream()
            .mapToDouble(p -> p.getRealizedPnL() != null ? p.getRealizedPnL().doubleValue() : 0.0)
            .sum();
    }

    private double calculateProfitFactor(List<PositionEntity> positions) {
        double totalWins = positions.stream()
            .filter(p -> p.getRealizedPnL() != null && p.getRealizedPnL().compareTo(BigDecimal.ZERO) > 0)
            .mapToDouble(p -> p.getRealizedPnL().doubleValue())
            .sum();

        double totalLosses = positions.stream()
            .filter(p -> p.getRealizedPnL() != null && p.getRealizedPnL().compareTo(BigDecimal.ZERO) < 0)
            .mapToDouble(p -> Math.abs(p.getRealizedPnL().doubleValue()))
            .sum();

        return totalLosses > 0 ? totalWins / totalLosses : totalWins > 0 ? 999.99 : 0.0;
    }

    private String formatMonthlyReport(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>MONTHLY STRATEGY REVIEW</b>\n\n");
        sb.append("📅 <b>Period:</b> ")
          .append(report.get("reportDate"))
          .append("\n\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📈 <b>PERFORMANCE METRICS</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 <b>Total Trades:</b> <code>")
          .append(report.get("totalTrades"))
          .append("</code>\n");
        sb.append("📊 <b>Win Rate:</b> <b>")
          .append(report.get("winRate"))
          .append("</b>\n");
        sb.append("💰 <b>Total P&L:</b> <b>")
          .append(report.get("totalPnL"))
          .append("</b>\n");
        sb.append("✅ <b>Avg Win:</b> <code>")
          .append(report.get("avgWinAmount"))
          .append("</code>\n");
        sb.append("❌ <b>Avg Loss:</b> <code>")
          .append(report.get("avgLossAmount"))
          .append("</code>\n");
        sb.append("📉 <b>Profit Factor:</b> <b>")
          .append(report.get("profitFactor"))
          .append("</b>\n\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🎯 <b>EXIT REASONS</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> exitReasons = (Map<String, Long>) report.get("exitReasons");
        exitReasons.forEach((reason, count) -> {
            sb.append("🔸 <b>").append(reason).append(":</b> <code>")
              .append(count)
              .append("</code>\n");
        });
        sb.append("\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📡 <b>SIGNAL DISTRIBUTION</b>\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> signalDist = (Map<String, Long>) report.get("signalDistribution");
        signalDist.forEach((type, count) -> {
            sb.append("🔸 <b>").append(type).append(":</b> <code>")
              .append(count)
              .append("</code>\n");
        });

        return sb.toString();
    }
}
