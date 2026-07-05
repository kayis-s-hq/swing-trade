package com.swingtrade.api.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.model.Order;
import com.swingtrade.broker.service.DiscordNotificationService;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Signal;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Filters signals through sentiment analysis before executing trades.
 * POSITIVE → proceed, NEUTRAL → proceed with warning, NEGATIVE → suppress.
 */
@Service
public class SignalFilterService {

    private static final Logger log = LoggerFactory.getLogger(SignalFilterService.class);

    private final SentimentService sentimentService;
    private final NewsIngestionService newsService;
    private final PaperTradingEngine paperTradingEngine;
    private final DiscordNotificationService discordService;

    public SignalFilterService(SentimentService sentimentService,
                               NewsIngestionService newsService,
                               PaperTradingEngine paperTradingEngine,
                               DiscordNotificationService discordService) {
        this.sentimentService = sentimentService;
        this.newsService = newsService;
        this.paperTradingEngine = paperTradingEngine;
        this.discordService = discordService;
    }

    /**
     * Filters a signal through sentiment analysis. Returns null if suppressed.
     */
    public Order filterAndProcess(Signal signal, BigDecimal currentPrice) {
        List<NewsIngestionService.NewsArticle> news = newsService.fetchAllNews(signal.symbol());
        List<String> headlines = news.stream()
                .map(newsService::cleanNewsText)
                .toList();

        SentimentResult sentiment = sentimentService.analyseSentiment(
                signal.symbol(), headlines, null);

        return switch (sentiment.score()) {
            case POSITIVE -> {
                log.info("Signal {} POSITIVE sentiment — proceeding to order", signal.symbol());
                yield paperTradingEngine.executeSignal(signal, currentPrice);
            }
            case NEUTRAL -> {
                log.warn("Signal {} NEUTRAL sentiment — proceeding with warning", signal.symbol());
                discordService.sendEmbed(
                        "Neutral Sentiment — " + signal.symbol(),
                        "Sentiment is NEUTRAL. Proceeding with caution.",
                        DiscordNotificationService.COLOR_YELLOW);
                yield paperTradingEngine.executeSignal(signal, currentPrice);
            }
            case NEGATIVE -> {
                log.warn("Signal {} NEGATIVE sentiment — SUPPRESSED. Reason: {}",
                        signal.symbol(), sentiment.summary());
                discordService.sendEmbed(
                        "Negative Sentiment — " + signal.symbol(),
                        "Sentiment is NEGATIVE. Signal suppressed.\n" + sentiment.summary(),
                        DiscordNotificationService.COLOR_RED);
                yield null;
            }
        };
    }

    /**
     * Daily re-analysis of open positions.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void reanalysePending() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        List<?> openPositions = paperTradingEngine.getOpenPositions();
        for (var pos : openPositions) {
            if (!(pos instanceof com.swingtrade.broker.model.Position p)) continue;
            try {
                SentimentResult sentiment = sentimentService.analyzeStockSentiment(
                        p.getSymbol(), LocalDate.now(ist));
                if (sentiment.isNegative()) {
                    discordService.sendEmbed(
                            "Position Alert — " + p.getSymbol(),
                            "Sentiment turned NEGATIVE. Position: " + p.getPositionId() +
                            "\nRecommendation: Review position manually.",
                            DiscordNotificationService.COLOR_YELLOW);
                }
            } catch (Exception e) {
                log.warn("Reanalysis failed for {}: {}", p.getSymbol(), e.getMessage());
            }
        }
    }
}