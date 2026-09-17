package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.store.NewsArticleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsIngestionServiceCoverageTest {

    @Mock private NewsFilterService newsFilterService;
    @Mock private MoneycontrolNewsSource moneycontrolSource;
    @Mock private EconomicTimesNewsSource economicTimesSource;
    @Mock private GoogleNewsSource googleNewsSource;
    @Mock private NseAnnouncementsSource nseSource;
    @Mock private BseAnnouncementsSource bseSource;
    @Mock private RedditIndiaInvestmentsSource redditSource;
    @Mock private FinnhubNewsSource finnhubSource;
    @Mock private NewsArticleStore newsArticleStore;

    private NewsIngestionService service;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.clientConnector(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(WebClient.class));
        service = new NewsIngestionService(builder, new tools.jackson.databind.ObjectMapper(),
                newsFilterService, moneycontrolSource, economicTimesSource, googleNewsSource,
                nseSource, bseSource, redditSource, finnhubSource, newsArticleStore,
                1, 15, 15, 20, 15, 15, null, 10);
    }

    @Test
    void fetchStockNews_deduplicatesHeadlinesPersistsResultsAndHandlesSourceFailure() {
        NewsArticle preferred = article("RELIANCE", "Reliance reports strong earnings", "NSE India");
        NewsArticle duplicate = article("RELIANCE", "Reliance reports strong earnings", "Google News");
        NewsArticle other = article("RELIANCE", "Reliance raises guidance", "Reuters");
        when(moneycontrolSource.fetch("RELIANCE")).thenReturn(List.of(preferred));
        when(economicTimesSource.fetch("RELIANCE")).thenThrow(new IllegalStateException("fixture failure"));
        when(googleNewsSource.fetch("RELIANCE")).thenReturn(List.of(duplicate));
        when(nseSource.fetch("RELIANCE")).thenReturn(List.of(other));
        when(bseSource.fetch("RELIANCE")).thenReturn(List.of());
        when(redditSource.fetch("RELIANCE")).thenReturn(List.of());
        when(finnhubSource.fetch("RELIANCE")).thenReturn(List.of());

        List<NewsArticle> result = service.fetchStockNews("RELIANCE");

        assertThat(result).containsExactly(preferred, other);
        verify(newsArticleStore).saveAll(result);
    }

    @Test
    void fetchStockNewsForDecisionDate_readsHistoricalWindowWithoutCallingLiveSources() {
        LocalDate decisionDate = LocalDate.of(2026, 9, 10);
        NewsArticle historical = article("TCS", "TCS wins contract", "NSE India");
        OffsetDateTime from = decisionDate.minusDays(7).atStartOfDay(ZoneId.of("Asia/Kolkata"))
                .toOffsetDateTime();
        OffsetDateTime through = decisionDate.atTime(15, 30).atZone(ZoneId.of("Asia/Kolkata"))
                .toOffsetDateTime();
        when(newsArticleStore.findBySymbolAndPublishedAtBetween("TCS", from, through))
                .thenReturn(List.of(historical));

        assertThat(service.fetchStockNewsForDecisionDate("TCS", decisionDate))
                .containsExactly(historical);
        verify(newsArticleStore).findBySymbolAndPublishedAtBetween("TCS", from, through);
    }

    @Test
    void fetchStockNewsForDecisionDate_returnsEmptyForMissingDecisionDate() {
        assertThat(service.fetchStockNewsForDecisionDate("TCS", null)).isEmpty();
    }

    private NewsArticle article(String symbol, String title, String source) {
        return new NewsArticle(symbol, title, "https://example.test/" + title.hashCode(),
                "description", ZonedDateTime.parse("2026-09-16T10:00:00+05:30[Asia/Kolkata]"),
                source, "raw content");
    }
}
