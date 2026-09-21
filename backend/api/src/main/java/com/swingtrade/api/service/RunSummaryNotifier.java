package com.swingtrade.api.service;

import com.swingtrade.api.service.JobOrchestratorService.JobRunSummary;
import com.swingtrade.broker.service.DiscordNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Posts a job-run summary to Discord when a run did not finish cleanly: it lists the degraded
 * stages and skipped strategies so a green-looking run can no longer hide fallbacks. Clean
 * COMPLETED runs stay quiet. Delivery failures never affect the run.
 */
@Component
public class RunSummaryNotifier {

    private static final Logger log = LoggerFactory.getLogger(RunSummaryNotifier.class);
    private static final int MAX_DESCRIPTION = 3500;

    private final DiscordNotificationService discord;

    public RunSummaryNotifier(DiscordNotificationService discord) {
        this.discord = discord;
    }

    /** True when this summary deserves a notification (warnings or failure). */
    static boolean shouldNotify(JobRunSummary summary) {
        return "COMPLETED_WITH_WARNINGS".equals(summary.status()) || "FAILED".equals(summary.status())
            || summary.degradedStages() > 0 || summary.skippedStrategies() > 0;
    }

    public void notifyRun(JobRunSummary summary) {
        if (summary == null || !shouldNotify(summary)) {
            return;
        }
        try {
            int color = "FAILED".equals(summary.status())
                ? DiscordNotificationService.COLOR_RED : DiscordNotificationService.COLOR_YELLOW;
            discord.sendEmbed("Job run " + summary.status(), format(summary), color);
        } catch (RuntimeException e) {
            log.warn("Run summary notification failed for {}: {}", summary.runId(), e.getMessage());
        }
    }

    static String format(JobRunSummary summary) {
        StringBuilder text = new StringBuilder();
        text.append("Run ").append(summary.runId()).append('\n')
            .append(summary.completedSymbols()).append('/').append(summary.totalSymbols())
            .append(" symbols processed, ").append(summary.failedSymbols()).append(" failed\n");
        if (!summary.degradedStageBreakdown().isEmpty()) {
            text.append("\nDegraded stages (").append(summary.degradedStages()).append("):\n");
            summary.degradedStageBreakdown().forEach(d -> text.append("- ").append(d.stage()).append(" (")
                .append(d.reason()).append("): ").append(d.count()).append('\n'));
        }
        if (!summary.skippedStrategyBreakdown().isEmpty()) {
            text.append("\nSkipped strategies:\n");
            summary.skippedStrategyBreakdown().forEach(s -> text.append("- ").append(s.variantId()).append(" ")
                .append(s.outcome().toLowerCase()).append(": ").append(s.reason()).append(" (")
                .append(s.symbols()).append(" symbol(s))\n"));
        }
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) + "..." : text.toString();
    }
}
