package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFilterServiceTest {
    private final NewsFilterService service = new NewsFilterService();
    private final ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

    @Test
    void filtersSpamOldFutureAndLowRelevanceArticles() {
        NewsArticle useful = article("Earnings beat and revenue growth drive a strong outlook", now.minusHours(2), "Reuters");
        NewsArticle spam = article("Guaranteed no risk buy now", now.minusHours(1), "Blog");
        NewsArticle old = article("Earnings beat", now.minusDays(8), "Reuters");
        NewsArticle future = article("Earnings beat", now.plusHours(2), "Reuters");
        NewsArticle irrelevant = article("Company office renovation update", now.minusHours(1), "Blog");

        assertThat(service.filterRelevantArticles(List.of(useful, spam, old, future, irrelevant)))
            .containsExactly(useful);
    }

    @Test
    void ranksTradingArticlesAndSummarizesSentiment() {
        NewsArticle positive = article("upgrade and bullish expansion", now.minusHours(1), "NSE India");
        NewsArticle negative = article("downgrade and investigation", now.minusHours(1), "Blog");
        NewsArticle mixed = article("bullish but lawsuit concerns", now.minusHours(1), "Blog");
        NewsArticle neutral = article("quarterly results", now.minusHours(1), "Blog");

        assertThat(service.filterRelevantArticles(List.of(positive, negative))).containsExactly(positive, negative);
        assertThat(service.analyzeArticleSentiment(List.of(positive, negative, mixed, neutral)))
            .extracting(NewsFilterService.ArticleSentimentAnalysis::positiveCount,
                NewsFilterService.ArticleSentimentAnalysis::negativeCount,
                NewsFilterService.ArticleSentimentAnalysis::neutralCount)
            .containsExactly(1, 1, 2);
        assertThat(service.getSentimentSummary(List.of(positive, negative, mixed, neutral)))
            .contains("Positive: 25%", "Negative: 25%", "Neutral: 50%");
        assertThat(service.getSentimentSummary(List.of())).isEqualTo("No articles available for analysis");
    }

    private NewsArticle article(String content, ZonedDateTime published, String source) {
        return NewsArticle.builder().symbol("TCS").title(content).description(content)
            .publishedDate(published).source(source).rawContent(content).build();
    }
}
