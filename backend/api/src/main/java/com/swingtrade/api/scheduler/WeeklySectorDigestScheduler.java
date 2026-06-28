package com.swingtrade.api.scheduler;

import com.swingtrade.broker.telegram.TelegramNotificationService;
import com.swingtrade.llm.service.SentimentAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for weekly sector sentiment digest delivery via Telegram.
 * Runs every Sunday at 17:00 IST to send the previous week's sector sentiment analysis.
 *
 * This scheduled bean bridges the SentimentAnalysisService (llm module) and
 * TelegramNotificationService (broker module), avoiding circular dependencies
 * by placing the orchestration in the api module which depends on both.
 */
@Component
public class WeeklySectorDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySectorDigestScheduler.class);

    private final SentimentAnalysisService sentimentAnalysisService;
    private final TelegramNotificationService telegramService;

    /**
     * Constructs the scheduler with required dependencies.
     *
     * @param sentimentAnalysisService for generating sector digest
     * @param telegramService for sending digest via Telegram
     */
    @Autowired
    public WeeklySectorDigestScheduler(
            SentimentAnalysisService sentimentAnalysisService,
            TelegramNotificationService telegramService) {
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.telegramService = telegramService;
    }

    /**
     * Scheduled method to send weekly sector digest every Sunday at 17:00 IST.
     * This method:
     * 1. Fetches the weekly sector sentiment digest from SentimentAnalysisService
     * 2. Sends the digest via Telegram to all configured chat IDs
     * 3. Logs delivery status and handles exceptions gracefully
     *
     * Cron: "0 0 17 * * SUN" = 17:00 IST on Sundays
     * Zone: "Asia/Kolkata" = Indian Standard Time
     */
    @Scheduled(cron = "0 0 17 * * SUN", zone = "Asia/Kolkata")
    public void sendWeeklySectorDigest() {
        log.info("Starting weekly sector digest delivery to Telegram");

        try {
            // Generate the weekly sector sentiment digest
            String digest = sentimentAnalysisService.generateSectorDigestForLastWeek();

            // Send digest via Telegram to all configured chat IDs
            // The sendMessage(String) method broadcasts to all configured chats
            boolean sent = telegramService.sendMessage(digest);

            if (sent) {
                log.info("Weekly sector digest sent successfully to Telegram");
            } else {
                log.warn("Weekly sector digest delivery returned false (possibly no configured chat IDs)");
            }

        } catch (Exception e) {
            log.error("Error during weekly sector digest delivery: {}", e.getMessage(), e);
            // Do not rethrow - scheduler resilience: digest failure should not crash the scheduler
        }
    }
}
