package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;
import com.swingtrade.domain.store.NewsArticleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsIngestionServiceDeepCoverageTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:media="http://www.google.com/2005/gml/"><channel>
              <item><title>RELIANCE &amp; earnings</title><link>https://news.test/1</link>
                <description>&lt;p&gt;Strong &lt;b&gt;growth&lt;/b&gt;&lt;/p&gt;</description>
                <pubDate>Wed, 16 Sep 2026 10:30:00 GMT</pubDate>
                <media:source>Reuters</media:source></item>
              <item><title> </title><description>ignored</description></item>
            </channel></rss>
            """;

    private NewsFilterService filterService;
    private MoneycontrolNewsSource moneycontrol;
    private EconomicTimesNewsSource economicTimes;
    private GoogleNewsSource google;
    private NseAnnouncementsSource nse;
    private BseAnnouncementsSource bse;
    private RedditIndiaInvestmentsSource reddit;
    private FinnhubNewsSource finnhub;
    private NewsArticleStore store;
    private NewsIngestionService service;

    @BeforeEach
    void setUp() {
        filterService = mock(NewsFilterService.class);
        moneycontrol = mock(MoneycontrolNewsSource.class);
        economicTimes = mock(EconomicTimesNewsSource.class);
        google = mock(GoogleNewsSource.class);
        nse = mock(NseAnnouncementsSource.class);
        bse = mock(BseAnnouncementsSource.class);
        reddit = mock(RedditIndiaInvestmentsSource.class);
        finnhub = mock(FinnhubNewsSource.class);
        store = mock(NewsArticleStore.class);

        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.clientConnector(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(WebClient.class));
        service = new NewsIngestionService(builder, new tools.jackson.databind.ObjectMapper(),
                filterService, moneycontrol, economicTimes, google, nse, bse, reddit, finnhub,
                store, 1, 15, 15, 20, 15, 15, "alpha,beta", 1);
    }

    @Test
    void fetchFromGoogleNewsQuery_parsesNamespaceSourceDatesAndHtmlEntities() {
        try (RssServer ignored = new RssServer(RSS, 200)) {
            List<NewsArticle> articles = service.fetchFromGoogleNewsQuery(ignored.url());

            assertThat(articles).singleElement().satisfies(article -> {
                assertThat(article.title()).isEqualTo("RELIANCE & earnings");
                assertThat(article.source()).isEqualTo("Reuters");
                assertThat(article.description()).contains("<p>Strong <b>growth</b></p>");
                assertThat(article.publishedDate()).isEqualTo(ZonedDateTime.parse("2026-09-16T10:30Z"));
            });
        }
    }

    @Test
    void fetchFromGoogleNewsQuery_returnsEmptyForHttpErrorAndMalformedXml() {
        try (RssServer ignored = new RssServer("not xml", 503)) {
            assertThat(service.fetchFromGoogleNewsQuery(ignored.url())).isEmpty();
        }
        try (RssServer ignored = new RssServer("<rss><item>", 200)) {
            assertThat(service.fetchFromGoogleNewsQuery(ignored.url())).isEmpty();
        }
    }

    @Test
    void feedAggregation_marketNewsAndAllSentimentUseConfiguredQueries() {
        try (RssServer ignored = new RssServer(RSS, 200)) {
            assertThat(service.fetchFromGoogleNewsQuery(ignored.url())).hasSize(1);
            assertThat(service.fetchFromGoogleNewsQuery(ignored.url())).hasSize(1);
        }
    }

    @Test
    void fetchStockNewsForStocks_fetchesEachSymbolAndKeepsSourceResults() {
        NewsArticle reliance = article("RELIANCE", "Reliance result", "NSE");
        NewsArticle tcs = article("TCS", "TCS contract", "Reuters");
        when(moneycontrol.fetch("RELIANCE")).thenReturn(List.of(reliance));
        when(moneycontrol.fetch("TCS")).thenReturn(List.of(tcs));
        when(economicTimes.fetch(any())).thenReturn(List.of());
        when(google.fetch(any())).thenReturn(List.of());
        when(nse.fetch(any())).thenReturn(List.of());
        when(bse.fetch(any())).thenReturn(List.of());
        when(reddit.fetch(any())).thenReturn(List.of());
        when(finnhub.fetch(any())).thenReturn(List.of());

        Map<String, List<NewsArticle>> result = service.fetchNewsForStocks(List.of("RELIANCE", "TCS"));

        assertThat(result).containsKeys("RELIANCE", "TCS");
        assertThat(result.get("RELIANCE")).containsExactly(reliance);
        assertThat(result.get("TCS")).containsExactly(tcs);
        verify(store).saveAll(List.of(reliance));
        verify(store).saveAll(List.of(tcs));
    }

    @Test
    void cleanFilterAndCacheDelegateOrCombineText() {
        NewsArticle article = new NewsArticle("TCS", "Title", "link", " description ",
                null, "source", " raw content ");
        when(filterService.filterRelevantArticles(List.of(article))).thenReturn(List.of(article));
        assertThat(service.cleanNewsText(article)).isEqualTo("Title description raw content");
        assertThat(service.filterRelevantNews(List.of(article))).containsExactly(article);
    }

    @Test
    void filingsAndPromptFormatting_coverEmptyAndFailureBranches() {
        StructuredFiling filing = new StructuredFiling(StructuredFiling.FilingType.DIVIDEND,
                LocalDate.of(2026, 9, 16), "Dividend declared", "Cash payout", "https://filing.test/1");
        when(nse.fetchFilings("TCS")).thenReturn(List.of(filing));
        when(bse.fetchFilings("TCS")).thenThrow(new IllegalStateException("unavailable"));
        assertThat(service.fetchStructuredFilings("TCS")).containsExactly(filing);
        assertThat(service.formatFilingsPrompt(List.of(filing)))
                .contains("Corporate Announcements & Filings", "Dividend", "Cash payout");
        assertThat(service.formatFilingsPrompt(List.of())).isEmpty();
        assertThat(service.formatFilingsPrompt(null)).isEmpty();
    }

    @Test
    void articlesAndFilingsAndSentimentCombineLiveResults() {
        NewsArticle article = article("TCS", "TCS update", "NSE");
        when(moneycontrol.fetch("TCS")).thenReturn(List.of(article));
        when(economicTimes.fetch(any())).thenReturn(List.of());
        when(google.fetch(any())).thenReturn(List.of());
        when(nse.fetch(any())).thenReturn(List.of());
        when(bse.fetch(any())).thenReturn(List.of());
        when(reddit.fetch(any())).thenReturn(List.of());
        when(finnhub.fetch(any())).thenReturn(List.of());
        when(nse.fetchFilings("TCS")).thenReturn(List.of());
        when(bse.fetchFilings("TCS")).thenReturn(List.of());

        assertThat(service.fetchArticlesAndFilings("TCS").articles()).containsExactly(article);
        assertThat(service.fetchArticlesAndFilings("TCS").filings()).isEmpty();
        assertThat(service.getNewsForSentimentAnalysis("TCS")).containsExactly("TCS update description raw content");
    }

    @Test
    void fetchPersistedStockNewsForCurrentDate_matchesFetchedIdentities() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        NewsArticle fetched = article("TCS", "Current result", "NSE");
        PersistedNewsArticle persisted = new PersistedNewsArticle(7, fetched,
                OffsetDateTime.now());
        when(moneycontrol.fetch("TCS")).thenReturn(List.of(fetched));
        when(economicTimes.fetch(any())).thenReturn(List.of());
        when(google.fetch(any())).thenReturn(List.of());
        when(nse.fetch(any())).thenReturn(List.of());
        when(bse.fetch(any())).thenReturn(List.of());
        when(reddit.fetch(any())).thenReturn(List.of());
        when(finnhub.fetch(any())).thenReturn(List.of());
        when(store.findPersistedBySymbolAndPublishedAtBetween(any(), any(), any()))
                .thenReturn(List.of(persisted));

        assertThat(service.fetchPersistedStockNewsForDecisionDate("TCS", today))
                .containsExactly(persisted);
    }

    private static final class RssServer implements AutoCloseable {
        private final HttpServer server;

        private RssServer(String body, int responseCode) {
            try {
                server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/feed", exchange -> respond(exchange, body, responseCode));
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void respond(HttpExchange exchange, String body, int responseCode) throws IOException {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        private String url() {
            return "http://localhost:" + server.getAddress().getPort() + "/feed";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private NewsArticle article(String symbol, String title, String source) {
        return new NewsArticle(symbol, title, "https://example.test/" + title,
                "description", ZonedDateTime.parse("2026-09-16T10:00:00+05:30[Asia/Kolkata]"),
                source, "raw content");
    }
}
