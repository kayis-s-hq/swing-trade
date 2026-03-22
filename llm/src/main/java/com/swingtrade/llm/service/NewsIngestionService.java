package com.swingtrade.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

                NewsItem newsItem = new NewsItem();
                newsItem.title = getChildText(itemElement, "title");
                newsItem.link = getChildText(itemElement, "link");
                newsItem.description = getChildText(itemElement, "description");
                newsItem.pubDate = parsePubDate(getChildText(itemElement, "pubDate"));
                newsItem.content = getChildText(itemElement, "content:encoded");

                if (newsItem.title != null && !newsItem.title.trim().isEmpty()) {
                    items.add(newsItem);
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
     * Parses RSS publication date.
     *
     * @param pubDateStr the date string in RSS format
     * @return ZonedDateTime, or null if parsing fails
     */
    private ZonedDateTime parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Try RFC 822 format (common for RSS)
            DateTimeFormatter rfc822Formatter = DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
            return ZonedDateTime.parse(pubDateStr.trim(), rfc822Formatter);
        } catch (Exception e) {
            logger.trace("Error parsing pubDate with RFC 822: {}", e.getMessage());
        }

        try {
            // Try ISO 8601 format
            DateTimeFormatter isoFormatter = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return ZonedDateTime.parse(pubDateStr.trim(), isoFormatter);
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
        String content = (article.getTitle() != null ? article.getTitle() : "") +
                        " " + (article.getDescription() != null ? article.getDescription() : "") +
                        " " + (article.getRawContent() != null ? article.getRawContent() : "");

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
        String content = (article.getTitle() != null ? article.getTitle() : "") +
                        " " + (article.getDescription() != null ? article.getDescription() : "");

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
        if (article.getTitle() != null && !article.getTitle().isEmpty()) {
            cleanedText.append(article.getTitle()).append(" ");
        }

        // Add description
        if (article.getDescription() != null && !article.getDescription().isEmpty()) {
            cleanedText.append(article.getDescription()).append(" ");
        }

        // Add raw content if available
        if (article.getRawContent() != null && !article.getRawContent().isEmpty()) {
            cleanedText.append(article.getRawContent()).append(" ");
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

        // In production, this would use Redis or other cache
        // For now, always fetch fresh
        return fetchNewsFromFeeds();
    }

    /**
     * Represents a news article from RSS feed or other sources.
     */
    public static class NewsArticle {
        private String title;
        private String link;
        private String description;
        private ZonedDateTime publishedDate;
        private String source;
        private String rawContent;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ZonedDateTime getPublishedDate() {
            return publishedDate;
        }

        public void setPublishedDate(ZonedDateTime publishedDate) {
            this.publishedDate = publishedDate;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getRawContent() {
            return rawContent;
        }

        public void setRawContent(String rawContent) {
            this.rawContent = rawContent;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final NewsArticle article = new NewsArticle();

            public Builder title(String title) {
                article.setTitle(title);
                return this;
            }

            public Builder link(String link) {
                article.setLink(link);
                return this;
            }

            public Builder description(String description) {
                article.setDescription(description);
                return this;
            }

            public Builder publishedDate(ZonedDateTime publishedDate) {
                article.setPublishedDate(publishedDate);
                return this;
            }

            public Builder source(String source) {
                article.setSource(source);
                return this;
            }

            public Builder rawContent(String rawContent) {
                article.setRawContent(rawContent);
                return this;
            }

            public NewsArticle build() {
                return article;
            }
        }

        @Override
        public String toString() {
            return "NewsArticle{" +
                    "title='" + title + '\'' +
                    ", link='" + link + '\'' +
                    ", source='" + source + '\'' +
                    ", publishedDate=" + publishedDate +
                    '}';
        }
    }

    /**
     * Simple class for storing parsed RSS item data.
     */
    private static class NewsItem {
        String title;
        String link;
        String description;
        ZonedDateTime pubDate;
        String content;

        NewsItem() {}
    }
}
