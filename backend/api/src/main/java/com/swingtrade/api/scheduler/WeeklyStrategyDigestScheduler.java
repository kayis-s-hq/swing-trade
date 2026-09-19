package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.StrategyAttributionService;
import com.swingtrade.api.service.StrategyAttributionService.Report;
import com.swingtrade.api.service.StrategyAttributionService.VariantReport;
import com.swingtrade.broker.service.DiscordNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Weekly strategy attribution digest to Discord: which variants won the signal tournament and how
 * their picks performed over the last 7 days. Off by default
 * ({@code strategy.report.weekly-digest.enabled=false}).
 */
@Component
public class WeeklyStrategyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyStrategyDigestScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final StrategyAttributionService attributionService;
    private final DiscordNotificationService discordService;
    private final boolean enabled;

    public WeeklyStrategyDigestScheduler(
            StrategyAttributionService attributionService,
            DiscordNotificationService discordService,
            @Value("${strategy.report.weekly-digest.enabled:false}") boolean enabled) {
        this.attributionService = attributionService;
        this.discordService = discordService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${strategy.report.weekly-digest.cron:0 30 18 * * SUN}", zone = "Asia/Kolkata")
    public void sendWeeklyStrategyDigest() {
        if (!enabled) {
            log.debug("Weekly strategy digest disabled (strategy.report.weekly-digest.enabled=false)");
            return;
        }
        try {
            LocalDate to = LocalDate.now(IST);
            Report report = attributionService.report(to.minusDays(7), to);
            boolean sent = discordService.sendEmbed("Weekly Strategy Report", formatDigest(report),
                DiscordNotificationService.COLOR_BLURPLE);
            if (!sent) {
                log.warn("Weekly strategy digest not delivered (Discord disabled or webhook not configured)");
            }
        } catch (Exception e) {
            log.error("Weekly strategy digest failed: {}", e.getMessage(), e);
        }
    }

    static String formatDigest(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(report.from()).append(" to ").append(report.to()).append('\n');
        sb.append(report.totals().tournaments()).append(" tournaments, ")
            .append(report.totals().executed()).append(" executed, ")
            .append(report.totals().blocked()).append(" blocked, selected-book P&L ")
            .append(report.totals().selectedPnl().toPlainString()).append('\n');
        if (report.variants().isEmpty()) {
            return sb.append("No active variants.").toString();
        }
        for (VariantReport v : report.variants()) {
            sb.append("\n**").append(v.variantId()).append("** (").append(v.strategyType()).append("): won ")
                .append(v.timesSelected()).append(", ")
                .append(v.buySignals()).append(" BUYs, shadow ")
                .append(v.shadowTrades()).append(" trades");
            if (v.shadowWinRatePct() != null) sb.append(" / ").append(v.shadowWinRatePct()).append("% win");
            sb.append(", P&L ").append(v.shadowPnl().toPlainString());
        }
        return sb.toString();
    }
}
