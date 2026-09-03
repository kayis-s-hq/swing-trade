package com.swingtrade.api.scheduler;

import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for weekly sector sentiment digest delivery via Discord.
 * Runs every Sunday at 18:00 IST to send the previous week's sector sentiment analysis.
 */
@Component
public class WeeklySectorDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySectorDigestScheduler.class);

    private final SentimentService sentimentService;
    private final DiscordNotificationService discordService;
    private final boolean schedulerEnabled;

    public WeeklySectorDigestScheduler(
            SentimentService sentimentService,
            DiscordNotificationService discordService,
            @Value("${app.features.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.sentimentService = sentimentService;
        this.discordService = discordService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 0 18 * * SUN", zone = "Asia/Kolkata")
    public void sendWeeklySectorDigest() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled (app.features.scheduler.enabled=false) — skipping weekly sector digest");
            return;
        }

        log.info("Starting weekly sector digest delivery to Discord");

        try {
            String digest = sentimentService.generateSectorDigestForLastWeek();
            boolean sent = discordService.sendEmbed(
                    "Weekly Sector Digest", digest, DiscordNotificationService.COLOR_BLURPLE);

            if (sent) {
                log.info("Weekly sector digest sent successfully to Discord");
            } else {
                log.warn("Weekly sector digest delivery returned false (Discord disabled or webhook not configured)");
            }

        } catch (Exception e) {
            log.error("Error during weekly sector digest delivery: {}", e.getMessage(), e);
        }
    }
}
