package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.domain.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches stock-specific news from Finnhub API.
 * Free tier: 60 calls/min, 500k messages/month.
 * API docs: https://finnhub.io/docs/api/stock-news
 */
@Service
public class FinnhubNewsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(FinnhubNewsSource.class);
    private static final String NEWS_API = "https://finnhub.io/api/v1/news?category=general&symbol=";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX");

    private final String apiKey;
    private final int maxArticles;
    private final WebClient webClient;

    public FinnhubNewsSource(
            @Value("${news.source.finnhub.api-key:}") String apiKey,
            @Value("${news.source.finnhub.max-articles:15}") int maxArticles,
            WebClient.Builder webClientBuilder) {

        this.apiKey = apiKey;
        this.maxArticles = maxArticles;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String type() {
        return "finnhub";
    }

    @Override
    public List<NewsArticle> fetch(String symbol) {
        if (apiKey.isBlank()) {
            log.debug("Finnhub API key not configured — skipping");
            return List.of();
        }

        String url = NEWS_API + symbol + "&token=" + apiKey;

        try {
            String json = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(8));

            if (json == null || json.isBlank()) {
                return List.of();
            }

            JsonNode root = new ObjectMapper().readTree(json);
            if (!root.isArray()) return List.of();

            Set<String> seen = new HashSet<>();
            List<NewsArticle> articles = new ArrayList<>();

            for (JsonNode article : root) {
                String headline = article.path("headline").asText().trim();
                if (headline.isBlank()) continue;

                String key = headline.toLowerCase(Locale.ENGLISH);
                if (!seen.add(key) || articles.size() >= maxArticles) continue;

                String urlVal = article.path("url").asText("");
                String summary = article.path("summary").asText("").trim();
                long timestamp = article.path("datetime").asLong(0);

                ZonedDateTime pubDate = timestamp > 0
                        ? ZonedDateTime.ofInstant(
                                java.time.Instant.ofEpochSecond(timestamp),
                                java.time.ZoneOffset.UTC)
                        : ZonedDateTime.now();

                articles.add(NewsArticle.builder()
                        .symbol(symbol)
                        .title(headline)
                        .link(urlVal)
                        .description(summary.length() > 1000 ? summary.substring(0, 1000) : summary)
                        .publishedDate(pubDate)
                        .source("finnhub")
                        .rawContent(summary)
                        .build());
            }

            log.debug("Finnhub: {} articles for {}", articles.size(), symbol);
            return articles;

        } catch (Exception e) {
            log.warn("Finnhub fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }
}