package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.domain.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches stock discussions from r/IndiaInvestments Reddit.
 */
@Service
public class RedditIndiaInvestmentsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(RedditIndiaInvestmentsSource.class);
    private static final String SUBREDDIT = "IndiaInvestments";
    private static final String SEARCH_URL = "https://www.reddit.com/r/" + SUBREDDIT + "/search.json?q=";
    private static final String LOGIN_URL = "https://www.reddit.com/api/v1/access_token";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final int maxArticles;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public RedditIndiaInvestmentsSource(
            @Value("${reddit.client.id:}") String clientId,
            @Value("${reddit.client.secret:}") String clientSecret,
            @Value("${reddit.username:}") String username,
            @Value("${reddit.password:}") String password,
            @Value("${news.source.reddit.max-articles:15}") int maxArticles,
            ObjectMapper objectMapper) {

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.maxArticles = maxArticles;
        this.objectMapper = objectMapper;

        // Try OAuth if credentials provided
        String token = null;
        if (!clientId.isBlank() && !clientSecret.isBlank()) {
            token = fetchOAuthToken(objectMapper, clientId, clientSecret);
        }
        this.accessToken = token;
    }

    @Override
    public String type() {
        return "reddit";
    }

    @Override
    public List<NewsArticle> fetch(String symbol) {
        String query = symbol + " India stock";
        String url = SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=25";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "SwingTrade/1.0");

            if (accessToken != null) {
                req.header("Authorization", "Bearer " + accessToken);
            }

            HttpResponse<String> resp = client.send(
                    req.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("Reddit HTTP {} for {}", resp.statusCode(), symbol);
                return List.of();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode children = root.path("data").path("children");

            Set<String> seen = new HashSet<>();
            List<NewsArticle> articles = new ArrayList<>();

            for (JsonNode child : children) {
                JsonNode post = child.path("data");
                String title = post.path("title").asText().trim();
                String link = "https://www.reddit.com" + post.path("permalink").asText();
                String selfText = post.path("selftext").asText().trim();
                long created = post.path("created_utc").asLong(0);

                if (title.isBlank()) continue;
                if (!title.toLowerCase(Locale.ENGLISH).contains(symbol.toLowerCase(Locale.ENGLISH))) continue;

                String key = title.toLowerCase(Locale.ENGLISH);
                if (seen.add(key) && articles.size() < maxArticles) {
                    ZonedDateTime pubDate = created > 0
                            ? ZonedDateTime.ofInstant(
                                    java.time.Instant.ofEpochSecond(created),
                                    java.time.ZoneOffset.UTC)
                            : ZonedDateTime.now();

                    articles.add(NewsArticle.builder()
                            .symbol(symbol)
                            .title(title)
                            .link(link)
                            .description(selfText.length() > 500 ? selfText.substring(0, 500) : selfText)
                            .publishedDate(pubDate)
                            .source("reddit")
                            .rawContent(selfText)
                            .build());
                }
            }

            log.debug("Reddit: {} articles for {}", articles.size(), symbol);
            return articles;

        } catch (Exception e) {
            log.warn("Reddit fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private String fetchOAuthToken(ObjectMapper objectMapper, String clientId, String clientSecret) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String auth = java.util.Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(LOGIN_URL))
                            .header("Authorization", "Basic " + auth)
                            .header("User-Agent", "SwingTrade/1.0")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "grant_type=password&username=" + username + "&password=" + password))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String token = root.path("access_token").asText();
                log.info("Reddit OAuth successful for {}", SUBREDDIT);
                return token;
            }
        } catch (Exception e) {
            log.warn("Reddit OAuth failed, falling back to unauthenticated: {}", e.getMessage());
        }
        return null;
    }
}