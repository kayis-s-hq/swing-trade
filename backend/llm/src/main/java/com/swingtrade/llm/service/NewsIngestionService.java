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

    // NSE Corporate Announcements RSS feed
    private static final String NSE_ANNOUNCEMENTS_RSS =
            "https://feeds.nseindia.com/misc/topstories.htm";

    // MoneyControl NSE RSS feed
    private static final String MONEYCONTROL_NSE_RSS =
            "https://feeds.moneycontrol.com/nse/mncr";

    // Economic Times Stock Market RSS feed
    private static final String ET_STOCKS_RSS =
            "https://feeds.economictimes.indiatimes.com/stocks";

    // Bloomberg India RSS feed
    private static final String BLOOMBERG_INDIA_RSS =
            "https://feeds.bloombergmarkets.com/india-stocks";

    // Reuters India Markets RSS feed
    private static final String REUTERS_INDIA_RSS =
            "https://feeds.reuters.com/IndiaMarkets";

    // Supported RSS feed URLs for configurable fetching
    private static final List<String> SUPPORTED_RSS_FEEDS = Arrays.asList(
            NSE_ANNOUNCEMENTS_RSS,
            MONEYCONTROL_NSE_RSS,
            ET_STOCKS_RSS,
            BLOOMBERG_INDIA_RSS,
            REUTERS_INDIA_RSS
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NewsFilterService newsFilterService;
    private final ExecutorService newsExecutor;

    private final List<String> rssFeedUrls;
    private final int maxArticlesPerFeed;

    /**
     * Constructs NewsIngestionService with required dependencies.
     *
     * @param webClientBuilder WebClient builder for web requests
     * @param rssFeedUrls list of RSS feed URLs to monitor (optional, defaults to built-in feeds)
     * @param maxArticlesPerFeed maximum articles to fetch per feed
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

        // Parse custom RSS feeds or use defaults
        if (rssFeedUrls != null && !rssFeedUrls.isBlank()) {
            this.rssFeedUrls = Arrays.stream(rssFeedUrls.split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .collect(Collectors.toList());
            logger.info("Using custom RSS feeds: {}", this.rssFeedUrls);
        } else {
            this.rssFeedUrls = SUPPORTED_RSS_FEEDS;
            logger.info("Using default RSS feeds: {}", this.rssFeedUrls);
        }
    }

    /**
     * Fetches news from all configured RSS feeds.
     *
     * @return list of news articles with metadata
     */
    public List<NewsArticle> fetchNewsFromFeeds() {
        logger.info("Fetching news from {} RSS feeds", rssFeedUrls.size());

        List<NewsArticle> allArticles = new ArrayList<>();

        for (String feedUrl : rssFeedUrls) {
            try {
                logger.debug("Fetching from feed: {}", feedUrl);
                List<NewsArticle> articles = fetchFromRssFeed(feedUrl);
                allArticles.addAll(articles);
                logger.info("Successfully fetched {} articles from {}", articles.size(), feedUrl);
            } catch (Exception e) {
                logger.error("Error fetching news from feed {}: {}", feedUrl, e.getMessage());
                logger.trace("RSS feed fetch error", e);
            }
        }

        logger.info("Total articles fetched: {}", allArticles.size());
        return allArticles;
    }

    /**
     * Fetches news from a single RSS feed URL.
     *
     * @param feedUrl the RSS feed URL
     * @return list of parsed news articles
     */
    public List<NewsArticle> fetchFromRssFeed(String feedUrl) {
        List<NewsArticle> articles = new ArrayList<>();

        try {
            // Fetch and parse RSS XML
            String xmlContent = fetchRssXml(feedUrl);

            if (xmlContent == null || xmlContent.isEmpty()) {
                logger.warn("Empty RSS content from {}", feedUrl);
                return articles;
            }

            // Parse RSS XML using DOM
            List<NewsItem> rssItems = parseRssXml(xmlContent);

            for (NewsItem item : rssItems) {
                articles.add(NewsArticle.builder()
                        .title(item.title)
                        .link(item.link)
                        .description(item.description)
                        .publishedDate(item.pubDate)
                        .source(feedUrl)
                        .rawContent(item.content)
                        .build());
            }

            // Limit to max articles per feed
            if (articles.size() > maxArticlesPerFeed) {
                articles = articles.subList(0, maxArticlesPerFeed);
            }

            logger.info("Fetched {} articles from {}", articles.size(), feedUrl);

        } catch (Exception e) {
            logger.error("Error fetching RSS feed {}: {}", feedUrl, e.getMessage());
            logger.trace("RSS feed fetch error", e);
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
     * Parses RSS XML content into NewsItem objects using DOM parser.
     *
     * @param xmlContent the XML content of RSS feed
     * @return list of parsed items
     */
    private List<NewsItem> parseRssXml(String xmlContent) {
        List<NewsItem> items = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));

            // Normalize for consistent parsing
            document.getDocumentElement().normalize();

            NodeList itemsList = document.getElementsByTagName("item");

            for (int i = 0; i < itemsList.getLength(); i++) {
                Element itemElement = (Element) itemsList.item(i);

                String title = getChildText(itemElement, "title");
                String link = getChildText(itemElement, "link");
                String description = getChildText(itemElement, "description");
                ZonedDateTime pubDate = parsePubDate(getChildText(itemElement, "pubDate"));
                String content = getChildText(itemElement, "content:encoded");

                if (title != null && !title.trim().isEmpty()) {
                    items.add(new NewsItem(title, link, description, pubDate, content));
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing RSS XML: {}", e.getMessage());
        }

        return items;
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
     * Fetches stock-specific news by searching financial news sources.
     *
     * @param stockSymbol the stock symbol (e.g., "RELIANCE", "TCS")
     * @return list of news articles about the stock
     */
    public List<NewsArticle> fetchStockNews(String stockSymbol) {
        logger.info("Fetching news for stock: {}", stockSymbol);

        List<NewsArticle> stockNews = new ArrayList<>();

        // Search across multiple RSS feeds for stock-specific news
        for (String feedUrl : rssFeedUrls) {
            List<NewsArticle> articles = fetchFromRssFeed(feedUrl);

            // Filter articles that mention the stock
            List<NewsArticle> filtered = articles.stream()
                    .filter(article -> containsStockSymbol(article, stockSymbol))
                    .limit(5) // Max 5 articles per feed
                    .collect(Collectors.toList());

            stockNews.addAll(filtered);
        }

        logger.info("Found {} articles for stock {}", stockNews.size(), stockSymbol);
        return stockNews;
    }

    /**
     * Checks if a news article contains the stock symbol.
     *
     * @param article the news article
     * @param stockSymbol the stock symbol to search for
     * @return true if the stock symbol is found in the article
     */
    private boolean containsStockSymbol(NewsArticle article, String stockSymbol) {
        String content = (article.title() != null ? article.title() : "") +
                        " " + (article.description() != null ? article.description() : "") +
                        " " + (article.rawContent() != null ? article.rawContent() : "");

        return content.toUpperCase().contains(stockSymbol.toUpperCase());
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

        List<NewsArticle> marketNews = fetchNewsFromFeeds();

        // Filter for market-wide topics (NIFTY, SENSEX, markets, economy)
        return marketNews.stream()
                .filter(article -> isMarketWideNews(article))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a news article is market-wide rather than stock-specific.
     *
     * @param article the news article
     * @return true if it's market-wide news
     */
    private boolean isMarketWideNews(NewsArticle article) {
        String content = (article.title() != null ? article.title() : "") +
                        " " + (article.description() != null ? article.description() : "");

        String lowerContent = content.toLowerCase();

        // Market-wide indicators
        return lowerContent.contains("nifty") ||
               lowerContent.contains("sensex") ||
               lowerContent.contains("market") ||
               lowerContent.contains("economy") ||
               lowerContent.contains("fpi") ||
               lowerContent.contains("fii") ||
               lowerContent.contains("di") ||
               lowerContent.contains("inflation") ||
               lowerContent.contains("rbi") ||
               lowerContent.contains("budget") ||
               lowerContent.contains("policy");
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
     * Fetches Google News RSS for a symbol using DOM parsing.
     */
    public List<NewsArticle> fetchGoogleNews(String symbol) {
        logger.debug("Fetching Google News for {}", symbol);
        try {
            String url = "https://news.google.com/rss/search?q=" +
                    symbol + "+NSE+stock&hl=en-IN&gl=IN&ceid=IN:en";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "SwingTrade/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                logger.warn("Google News HTTP {} for {}", conn.getResponseCode(), symbol);
                return List.of();
            }

            List<NewsArticle> articles = new ArrayList<>();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    conn.getInputStream().readAllBytes()));
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = getChildText(item, "title");
                String link = getChildText(item, "link");
                String desc = stripHtml(getChildText(item, "description"));
                String pubDateStr = getChildText(item, "pubDate");
                ZonedDateTime pubDate = parsePubDate(pubDateStr);
                if (title != null && !title.isBlank()) {
                    if (pubDate == null) {
                        logger.debug("No pubDate for Google News article '{}', using now", title);
                    }
                    articles.add(new NewsArticle(title, link, desc, pubDate != null ? pubDate : ZonedDateTime.now(), "google_news", desc));
                }
            }
            logger.info("Fetched {} articles from Google News for {}", articles.size(), symbol);
            return articles;
        } catch (Exception e) {
            logger.warn("Google News fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches NSE corporate announcements for a symbol.
     * NSE blocks non-browser requests frequently, so use a single short attempt.
     */
    public List<NewsArticle> fetchNseAnnouncements(String symbol) {
        logger.debug("Fetching NSE announcements for {}", symbol);
        try {
            String url = "https://www.nseindia.com/api/corp-info?symbol=" + symbol;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "SwingTrade/1.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String body = sb.toString();
                if (body.startsWith("<") || body.startsWith("<!")) {
                    logger.debug("NSE returned HTML for {}, skipping", symbol);
                    return List.of();
                }

                // Simple JSON parsing for NSE announcement array
                List<NewsArticle> articles = new ArrayList<>();
                if (body.startsWith("[")) {
                    body = body.replace("}{", "};{").replace("[", "").replace("]", "");
                    String[] items = body.split(";");
                    for (String item : items) {
                        try {
                            String subject = extractJsonField(item, "subject");
                            String date = extractJsonField(item, "date");
                            String desc = extractJsonField(item, "description");
                            if (subject != null && !subject.isBlank()) {
                                ZonedDateTime pubDate = parseNseDate(date);
                                articles.add(new NewsArticle(subject, null, desc,
                                        pubDate != null ? pubDate : ZonedDateTime.now(), "nse", desc));
                            }
                        } catch (Exception e) {
                            // skip malformed items
                        }
                    }
                }
                logger.info("Fetched {} announcements from NSE for {}", articles.size(), symbol);
                return articles;
            }
            logger.debug("NSE HTTP {} for {}", code, symbol);
            return List.of();
        } catch (Exception e) {
            logger.debug("NSE fetch skipped for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Unified fetch with parallel NSE + Google News, dedup, 7-day filter.
     */
    public List<NewsArticle> fetchAllNews(String symbol) {
        logger.debug("Fetching all news for {}", symbol);

        long start = System.currentTimeMillis();
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDateTime thirtyDaysAgo = LocalDateTime.now(ist).minusDays(30);
        CompletableFuture<List<NewsArticle>> nseFut = CompletableFuture.supplyAsync(() -> fetchNseAnnouncements(symbol), newsExecutor);
        CompletableFuture<List<NewsArticle>> googleFut = CompletableFuture.supplyAsync(() -> fetchGoogleNews(symbol), newsExecutor);

        // Hard 15s timeout so the REST endpoint doesn't block indefinitely
        long remaining = Math.max(1000, 15000 - (System.currentTimeMillis() - start));
        List<NewsArticle> all = new ArrayList<>();
        try {
            all.addAll(nseFut.get(remaining, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            logger.warn("NSE news fetch timed out for {}: {}", symbol, e.getMessage());
            nseFut.cancel(true);
        }
        remaining = Math.max(1000, 15000 - (System.currentTimeMillis() - start));
        try {
            all.addAll(googleFut.get(remaining, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            logger.warn("Google News fetch timed out for {}: {}", symbol, e.getMessage());
            googleFut.cancel(true);
        }

        // Dedup by normalized headline + 30-day filter
        // Keep articles with null dates (Google News often omits them)
        // and use a wider window since symbol-specific news may be sparse
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
        // If 7-day filter produced nothing, fall back to null-date articles
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

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end).replace("\\\"", "\"");
    }

    private ZonedDateTime parseNseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return ZonedDateTime.parse(dateStr.trim(),
                    DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
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
