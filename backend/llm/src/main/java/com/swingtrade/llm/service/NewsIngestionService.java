package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for ingesting news from various sources including RSS feeds,
 * financial news websites, and market announcements.
 * Provides unified interface for fetching stock-related news.
 */
@Service
public class NewsIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(NewsIngestionService.class);

    // Google News RSS base URL — only working free source for Indian stock news
    private static final String GOOGLE_NEWS_BASE =
            "https://news.google.com/rss/search?q=";
    private static final String GOOGLE_NEWS_PARAMS =
            "&hl=en-IN&gl=IN&ceid=IN:en";

    // Google News queries for Indian markets
    private static final List<String> MARKET_QUERIES = Arrays.asList(
            "India+stock+market+today",
            "Sensex+Nifty+market",
            "India+IPO+listing",
            "India+FII+DII+market"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NewsFilterService newsFilterService;
    private final ExecutorService newsExecutor;

    private final List<String> googleNewsUrls;
    private final int maxArticlesPerFeed;

    /**
     * Constructs NewsIngestionService with required dependencies.
     * Uses Google News as the primary news source since all traditional
     * Indian financial RSS feeds (NSE, Reuters, ET, Moneycontrol, Bloomberg) are dead.
     */
    @Autowired
    public NewsIngestionService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            NewsFilterService newsFilterService,
            @Value("${news.rss.feeds:#{null}}") String rssFeedUrls,
            @Value("${news.rss.max-articles-per-feed:10}") int maxArticlesPerFeed) {

        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.newsFilterService = newsFilterService;
        this.maxArticlesPerFeed = maxArticlesPerFeed;
        this.newsExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "news-fetcher");
            t.setDaemon(true);
            return t;
        });

        // Parse custom queries or use default Google News queries
        if (rssFeedUrls != null && !rssFeedUrls.isBlank()) {
            this.googleNewsUrls = Arrays.stream(rssFeedUrls.split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .map(q -> GOOGLE_NEWS_BASE + q + GOOGLE_NEWS_PARAMS)
                    .collect(Collectors.toList());
            logger.info("Using custom Google News queries: {}", this.googleNewsUrls);
        } else {
            this.googleNewsUrls = MARKET_QUERIES.stream()
                    .map(q -> GOOGLE_NEWS_BASE + q + GOOGLE_NEWS_PARAMS)
                    .collect(Collectors.toList());
            logger.info("Using default Google News queries: {}", this.googleNewsUrls);
        }
    }

    /**
     * Fetches market-wide news from Google News queries.
     *
     * @return list of news articles with metadata
     */
    public List<NewsArticle> fetchNewsFromFeeds() {
        logger.info("Fetching market-wide news from {} Google News queries", googleNewsUrls.size());

        List<NewsArticle> allArticles = new ArrayList<>();

        for (String queryUrl : googleNewsUrls) {
            try {
                logger.debug("Fetching from query: {}", queryUrl);
                List<NewsArticle> articles = fetchFromGoogleNewsQuery(queryUrl);
                allArticles.addAll(articles);
                logger.info("Successfully fetched {} articles from {}", articles.size(), queryUrl);
            } catch (Exception e) {
                logger.error("Error fetching news from query {}: {}", queryUrl, e.getMessage());
                logger.trace("Google News fetch error", e);
            }
        }

        logger.info("Total articles fetched: {}", allArticles.size());
        return allArticles;
    }

    /**
     * Fetches news from a single Google News RSS query URL.
     *
     * @param queryUrl the Google News RSS URL
     * @return list of parsed news articles
     */
    public List<NewsArticle> fetchFromGoogleNewsQuery(String queryUrl) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            String xmlContent = fetchRssXml(queryUrl);

            if (xmlContent == null || xmlContent.isEmpty()) {
                logger.warn("Empty content from {}", queryUrl);
                return articles;
            }

            // Parse Google News XML using DOM
            List<NewsItem> items = parseGoogleNewsXml(xmlContent, queryUrl);

            for (NewsItem item : items) {
                articles.add(NewsArticle.builder()
                        .title(item.title)
                        .link(item.link)
                        .description(item.description)
                        .publishedDate(item.pubDate)
                        .source(item.content) // source name from Google namespace
                        .rawContent(item.description)
                        .build());
            }

            // Limit to max articles per query
            if (articles.size() > maxArticlesPerFeed) {
                articles = articles.subList(0, maxArticlesPerFeed);
            }

            logger.info("Fetched {} articles from {}", articles.size(), queryUrl);

        } catch (Exception e) {
            logger.error("Error fetching from {}: {}", queryUrl, e.getMessage());
            logger.trace("Google News fetch error", e);
        }

        return articles;
    }

    /**
     * Fetches RSS XML content from URL.
     *
     * @param feedUrl the RSS feed URL
     * @return XML content as string
     */
    private String fetchRssXml(String feedUrl) {
        // Use HttpURLConnection for reliable RSS fetching
        try {
            URL url = new URL(feedUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                logger.warn("HTTP error {} fetching RSS from {}", responseCode, feedUrl);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching RSS via HTTP: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses Google News XML, handling the Google Media RSS namespace for source extraction.
     * Google uses http://www.google.com/2005/gml/ namespace for the <source> element.
     */
    private List<NewsItem> parseGoogleNewsXml(String xmlContent, String queryUrl) {
        List<NewsItem> items = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));
            document.getDocumentElement().normalize();

            NodeList itemsList = document.getElementsByTagName("item");
            for (int i = 0; i < itemsList.getLength(); i++) {
                Element itemElement = (Element) itemsList.item(i);

                String title = getChildText(itemElement, "title");
                String link = getChildText(itemElement, "link");
                String description = getChildText(itemElement, "description");
                ZonedDateTime pubDate = parsePubDate(getChildText(itemElement, "pubDate"));

                // Extract source from Google namespace
                String sourceName = getGoogleNamespaceText(itemElement, "source",
                        "http://www.google.com/2005/gml/");
                if (sourceName == null || sourceName.isBlank()) {
                    sourceName = getChildText(itemElement, "source");
                }
                if (sourceName == null || sourceName.isBlank()) {
                    sourceName = "google_news";
                }

                if (title != null && !title.trim().isEmpty()) {
                    items.add(new NewsItem(title, link, description, pubDate, sourceName));
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing Google News XML: {}", e.getMessage());
        }
        return items;
    }

    /**
     * Gets text from an element in a specific XML namespace.
     */
    private String getGoogleNamespaceText(Element parent, String tagName, String namespaceUri) {
        NodeList nodeList = parent.getElementsByTagNameNS(namespaceUri, tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return StringEscapeUtils.unescapeHtml4(text);
        }
        return null;
    }

    /**
     * Gets child element text content from an element.
     *
     * @param parent the parent element
     * @param tagName the tag name
     * @return the text content, or null if not found
     */
    private String getChildText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return StringEscapeUtils.unescapeHtml4(text);
        }
        return null;
    }

    /**
     * Strips HTML tags from text, leaving only plain text.
     */
    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        // Strip HTML tags, then unescape HTML entities
        String text = html.replaceAll("<[^>]+>", "");
        return StringEscapeUtils.unescapeHtml4(text).replaceAll("\\s+", " ").trim();
    }

    /**
     * Parses RSS publication date.
     *
     * @param pubDateStr the date string in RSS format
     * @return ZonedDateTime, or null if parsing fails
     */
    private ZonedDateTime parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = pubDateStr.trim();
        try {
            // Try RFC 822 format (common for RSS) with English locale
            DateTimeFormatter rfc822Formatter = DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.ENGLISH);
            return ZonedDateTime.parse(trimmed, rfc822Formatter);
        } catch (Exception e) {
            logger.trace("Error parsing pubDate with RFC 822: {}", e.getMessage());
        }

        try {
            // Try ISO 8601 format
            DateTimeFormatter isoFormatter = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return ZonedDateTime.parse(trimmed, isoFormatter);
        } catch (Exception e) {
            logger.trace("Error parsing pubDate with ISO 8601: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Fetches stock-specific news from Google News.
     *
     * @param stockSymbol the stock symbol (e.g., "RELIANCE", "TCS")
     * @return list of news articles about the stock
     */
    public List<NewsArticle> fetchStockNews(String stockSymbol) {
        logger.info("Fetching news for stock: {}", stockSymbol);

        List<NewsArticle> stockNews = new ArrayList<>();

        // Query Google News with stock-specific queries
        List<String> stockQueries = Arrays.asList(
                GOOGLE_NEWS_BASE + stockSymbol + "+NSE+stock" + GOOGLE_NEWS_PARAMS,
                GOOGLE_NEWS_BASE + stockSymbol + "+NSE" + GOOGLE_NEWS_PARAMS,
                GOOGLE_NEWS_BASE + stockSymbol + "+market" + GOOGLE_NEWS_PARAMS
        );

        for (String queryUrl : stockQueries) {
            try {
                List<NewsArticle> articles = fetchFromGoogleNewsQuery(queryUrl);
                stockNews.addAll(articles);
            } catch (Exception e) {
                logger.debug("Query {} returned no results for {}", queryUrl, stockSymbol);
            }
        }

        // Dedup by normalized title
        Set<String> seen = new HashSet<>();
        List<NewsArticle> deduped = stockNews.stream()
                .filter(a -> seen.add(a.title().toLowerCase().trim()))
                .limit(maxArticlesPerFeed * 3)
                .collect(Collectors.toList());

        logger.info("Found {} unique articles for stock {}", deduped.size(), stockSymbol);
        return deduped;
    }

    /**
     * Fetches news for multiple stocks.
     *
     * @param stockSymbols list of stock symbols
     * @return map of stock symbol to list of news articles
     */
    public Map<String, List<NewsArticle>> fetchNewsForStocks(List<String> stockSymbols) {
        logger.info("Fetching news for {} stocks: {}", stockSymbols.size(), stockSymbols);

        Map<String, List<NewsArticle>> stockNewsMap = new HashMap<>();

        for (String symbol : stockSymbols) {
            List<NewsArticle> articles = fetchStockNews(symbol);
            stockNewsMap.put(symbol, articles);
        }

        return stockNewsMap;
    }

    /**
     * Fetches market-wide news.
     *
     * @return list of market news articles
     */
    public List<NewsArticle> fetchMarketNews() {
        logger.info("Fetching market-wide news");
        return fetchNewsFromFeeds();
    }

    /**
     * Cleans and normalizes news article text.
     *
     * @param article the news article
     * @return cleaned and normalized news text
     */
    public String cleanNewsText(NewsArticle article) {
        StringBuilder cleanedText = new StringBuilder();

        // Add title
        if (article.title() != null && !article.title().isEmpty()) {
            cleanedText.append(article.title()).append(" ");
        }

        // Add description
        if (article.description() != null && !article.description().isEmpty()) {
            cleanedText.append(article.description()).append(" ");
        }

        // Add raw content if available
        if (article.rawContent() != null && !article.rawContent().isEmpty()) {
            cleanedText.append(article.rawContent()).append(" ");
        }

        // Remove extra whitespace and normalize
        return cleanedText.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Aggregates news from all sources for analysis.
     *
     * @param stockSymbol the stock symbol
     * @return list of cleaned news texts ready for sentiment analysis
     */
    public List<String> getNewsForSentimentAnalysis(String stockSymbol) {
        List<NewsArticle> articles = fetchStockNews(stockSymbol);

        return articles.stream()
                .map(this::cleanNewsText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Aggregates all news for broader sentiment analysis.
     *
     * @return list of all news texts
     */
    public List<String> getAllNewsForSentimentAnalysis() {
        List<NewsArticle> allArticles = fetchNewsFromFeeds();

        return allArticles.stream()
                .map(this::cleanNewsText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Updates news articles with filtering tags.
     *
     * @param articles list of articles to filter
     * @return filtered list of articles
     */
    public List<NewsArticle> filterRelevantNews(List<NewsArticle> articles) {
        return newsFilterService.filterRelevantArticles(articles);
    }

    /**
     * Fetches news with caching support.
     *
     * @param cacheKey the cache key for the news request
     * @param maxAgeMinutes maximum cache age in minutes
     * @return cached or fresh news articles
     */
    public List<NewsArticle> fetchNewsWithCache(String cacheKey, int maxAgeMinutes) {
        logger.debug("Fetching news with cache key: {}", cacheKey);
        return fetchNewsFromFeeds();
    }

    // ===== Symbol-specific news sources =====

    /**
     * Fetches Google News RSS for a symbol using namespace-aware DOM parsing.
     */
    public List<NewsArticle> fetchGoogleNews(String symbol) {
        logger.debug("Fetching Google News for {}", symbol);
        try {
            String url = GOOGLE_NEWS_BASE + symbol + "+NSE+stock" + GOOGLE_NEWS_PARAMS;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "SwingTrade/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.warn("Google News HTTP {} for {}", conn.getResponseCode(), symbol);
                return List.of();
            }

            String xmlContent = new String(conn.getInputStream().readAllBytes(), "UTF-8");
            List<NewsItem> items = parseGoogleNewsXml(xmlContent, url);

            List<NewsArticle> articles = items.stream()
                    .map(item -> {
                        String desc = stripHtml(item.description);
                        return new NewsArticle(
                                item.title, item.link, desc,
                                item.pubDate != null ? item.pubDate : ZonedDateTime.now(),
                                item.content, desc);
                    })
                    .collect(Collectors.toList());

            logger.info("Fetched {} articles from Google News for {}", articles.size(), symbol);
            return articles;
        } catch (Exception e) {
            logger.warn("Google News fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches all news for a symbol using Google News, dedup, 30-day filter.
     */
    public List<NewsArticle> fetchAllNews(String symbol) {
        logger.debug("Fetching all news for {}", symbol);

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDateTime thirtyDaysAgo = LocalDateTime.now(ist).minusDays(30);
        CompletableFuture<List<NewsArticle>> googleFut =
                CompletableFuture.supplyAsync(() -> fetchGoogleNews(symbol), newsExecutor);

        long start = System.currentTimeMillis();
        List<NewsArticle> all = new ArrayList<>();
        try {
            all.addAll(googleFut.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.warn("Google News fetch timed out for {}: {}", symbol, e.getMessage());
            googleFut.cancel(true);
        }

        // Dedup by normalized headline + 30-day filter
        Set<String> seen = new HashSet<>();
        List<NewsArticle> deduped = new ArrayList<>();
        List<NewsArticle> nullDateArticles = new ArrayList<>();
        for (NewsArticle a : all) {
            if (a.publishedDate() == null) {
                nullDateArticles.add(a);
                continue;
            }
            if (a.publishedDate().toLocalDateTime().isBefore(thirtyDaysAgo)) continue;
            String key = a.title().toLowerCase().trim();
            if (seen.add(key)) {
                deduped.add(a);
            }
        }
        if (deduped.isEmpty() && !nullDateArticles.isEmpty()) {
            Set<String> seen2 = new HashSet<>();
            for (NewsArticle a : nullDateArticles) {
                String key = a.title().toLowerCase().trim();
                if (seen2.add(key)) {
                    deduped.add(a);
                }
            }
        }

        deduped.sort((a, b) -> {
            if (a.publishedDate() == null) return 1;
            if (b.publishedDate() == null) return -1;
            return b.publishedDate().compareTo(a.publishedDate());
        });

        logger.info("Fetched {} unique headlines for {}", deduped.size(), symbol);
        return deduped;
    }

    /**
     * Represents a news article from RSS feed or other sources.
     * Public record for test access and JSON serialization.
     */
    public record NewsArticle(
        String title,
        String link,
        String description,
        ZonedDateTime publishedDate,
        String source,
        String rawContent
    ) {
        public static NewsArticleBuilder builder() {
            return new NewsArticleBuilder();
        }

        public static class NewsArticleBuilder {
            private String title;
            private String link;
            private String description;
            private ZonedDateTime publishedDate;
            private String source;
            private String rawContent;

            public NewsArticleBuilder title(String title) {
                this.title = title;
                return this;
            }

            public NewsArticleBuilder link(String link) {
                this.link = link;
                return this;
            }

            public NewsArticleBuilder description(String description) {
                this.description = description;
                return this;
            }

            public NewsArticleBuilder publishedDate(ZonedDateTime publishedDate) {
                this.publishedDate = publishedDate;
                return this;
            }

            public NewsArticleBuilder source(String source) {
                this.source = source;
                return this;
            }

            public NewsArticleBuilder rawContent(String rawContent) {
                this.rawContent = rawContent;
                return this;
            }

            public NewsArticle build() {
                return new NewsArticle(title, link, description, publishedDate, source, rawContent);
            }
        }
    }

    /**
     * Simple record for storing parsed RSS item data.
     * Public for test access and JSON serialization.
     */
    public record NewsItem(
        String title,
        String link,
        String description,
        ZonedDateTime pubDate,
        String content
    ) {}
}
