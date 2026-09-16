package com.swingtrade.llm.service;

import tools.jackson.databind.ObjectMapper;
import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;
import com.swingtrade.domain.store.NewsArticleStore;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for ingesting news from multiple Indian market sources.
 * Orchestrates parallel fetching from Moneycontrol, Economic Times, Google News,
 * NSE/BSE announcements, and Reddit r/IndiaInvestments.
 * Provides unified interface for fetching stock-related news.
 */
@Service
public class NewsIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(NewsIngestionService.class);

    // Google News RSS base URL
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

    // News sources
    private final MoneycontrolNewsSource moneycontrolSource;
    private final EconomicTimesNewsSource economicTimesSource;
    private final GoogleNewsSource googleNewsSource;
    private final NseAnnouncementsSource nseSource;
    private final BseAnnouncementsSource bseSource;
    private final RedditIndiaInvestmentsSource redditSource;
    private final FinnhubNewsSource finnhubSource;
    private final NewsArticleStore newsArticleStore;
    private final int timeoutSeconds;
    private final int maxMoneycontrolArticles;
    private final int maxEtArticles;
    private final int maxGoogleArticles;
    private final int maxRedditArticles;
    private final int maxFinnhubArticles;

    /**
     * Constructs NewsIngestionService with all news sources and article store.
     */
    @Autowired
    public NewsIngestionService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            NewsFilterService newsFilterService,
            MoneycontrolNewsSource moneycontrolSource,
            EconomicTimesNewsSource economicTimesSource,
            GoogleNewsSource googleNewsSource,
            NseAnnouncementsSource nseSource,
            BseAnnouncementsSource bseSource,
            RedditIndiaInvestmentsSource redditSource,
            FinnhubNewsSource finnhubSource,
            NewsArticleStore newsArticleStore,
            @Value("${news.source.timeout:10}") int timeoutSeconds,
            @Value("${news.source.moneycontrol.max-articles:15}") int maxMoneycontrolArticles,
            @Value("${news.source.et.max-articles:15}") int maxEtArticles,
            @Value("${news.source.google.max-articles:20}") int maxGoogleArticles,
            @Value("${news.source.reddit.max-articles:15}") int maxRedditArticles,
            @Value("${news.source.finnhub.max-articles:15}") int maxFinnhubArticles,
            @Value("${news.rss.feeds:#{null}}") String rssFeedUrls,
            @Value("${news.rss.max-articles-per-feed:10}") int maxArticlesPerFeed) {

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofSeconds(timeoutSeconds))))
                .build();
        this.objectMapper = objectMapper;
        this.newsFilterService = newsFilterService;
        this.maxArticlesPerFeed = maxArticlesPerFeed;
        this.newsExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "news-fetcher");
            t.setDaemon(true);
            return t;
        });

        this.moneycontrolSource = moneycontrolSource;
        this.economicTimesSource = economicTimesSource;
        this.googleNewsSource = googleNewsSource;
        this.nseSource = nseSource;
        this.bseSource = bseSource;
        this.redditSource = redditSource;
        this.finnhubSource = finnhubSource;
        this.newsArticleStore = newsArticleStore;
        this.timeoutSeconds = timeoutSeconds;
        this.maxMoneycontrolArticles = maxMoneycontrolArticles;
        this.maxEtArticles = maxEtArticles;
        this.maxGoogleArticles = maxGoogleArticles;
        this.maxRedditArticles = maxRedditArticles;
        this.maxFinnhubArticles = maxFinnhubArticles;

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
                        .source(item.content)
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
        try {
            URL url = new URL(feedUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while (true) {
                        line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        response.append(line);
                    }
                    return response.toString();
                }
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
        String text = html.replaceAll("<[^>]+>", "");
        return StringEscapeUtils.unescapeHtml4(text).replaceAll("\\s+", " ").trim();
    }

    /**
     * Parses RSS publication date.
     */
    private ZonedDateTime parsePubDate(String pubDateStr) {
        if (pubDateStr == null || pubDateStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = pubDateStr.trim();
        try {
            DateTimeFormatter rfc822Formatter = DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
            return ZonedDateTime.parse(trimmed, rfc822Formatter);
        } catch (Exception e) {
            logger.trace("Error parsing pubDate with RFC 822: {}", e.getMessage());
        }

        try {
            DateTimeFormatter isoFormatter = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return ZonedDateTime.parse(trimmed, isoFormatter);
        } catch (Exception e) {
            logger.trace("Error parsing pubDate with ISO 8601: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Fetches stock-specific news from all configured sources in parallel.
     */
    public List<NewsArticle> fetchStockNews(String stockSymbol) {
        logger.info("Fetching news for stock: {} from all sources", stockSymbol);

        List<CompletableFuture<List<NewsArticle>>> futures = new ArrayList<>();

        // Moneycontrol
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return moneycontrolSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("Moneycontrol fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("Moneycontrol timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // Economic Times
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return economicTimesSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("Economic Times fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("Economic Times timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // Google News
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return googleNewsSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("Google News fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("Google News timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // NSE announcements (as articles)
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return nseSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("NSE fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("NSE timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // BSE announcements (as articles)
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return bseSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("BSE fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("BSE timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // Reddit
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return redditSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("Reddit fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("Reddit timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // Finnhub
        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return finnhubSource.fetch(stockSymbol);
            } catch (Exception e) {
                logger.warn("Finnhub fetch failed for {}: {}", stockSymbol, e.getMessage());
                return List.<NewsArticle>of();
            }
        }, newsExecutor).exceptionally(ex -> {
            logger.warn("Finnhub timed out/failed for {}: {}", stockSymbol, ex.getMessage());
            return List.<NewsArticle>of();
        }));

        // Wait for all to complete
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.get(timeoutSeconds * 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Parallel fetch interrupted for {}: {}", stockSymbol, e.getMessage());
        }

        // Collect results
        List<NewsArticle> results = new ArrayList<>();
        for (CompletableFuture<List<NewsArticle>> fut : futures) {
            try {
                results.addAll(fut.getNow(List.of()));
            } catch (Exception e) {
                logger.debug("Failed to collect result from future for {}: {}", stockSymbol, e.getMessage());
            }
        }

        // Dedup by normalized headline
        Set<String> seen = new HashSet<>();
        List<NewsArticle> deduped = results.stream()
                .filter(a -> a.title() != null && seen.add(a.title().toLowerCase(Locale.ENGLISH).trim()))
                .collect(Collectors.toList());

        // Persist to DB
        try {
            newsArticleStore.saveAll(deduped);
        } catch (Exception e) {
            logger.warn("Failed to persist articles for {}: {}", stockSymbol, e.getMessage());
        }

        logger.info("Fetched {} unique headlines for {}", deduped.size(), stockSymbol);
        return deduped;
    }

    /**
     * Returns news admissible for a decision date. Historical dates are read only from the
     * persisted, bounded window; they never fall back to current live feeds.
     */
    public List<NewsArticle> fetchStockNewsForDecisionDate(String stockSymbol, LocalDate decisionDate) {
        if (decisionDate == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        if (!decisionDate.isBefore(today)) {
            return fetchStockNews(stockSymbol);
        }
        OffsetDateTime from = decisionDate.minusDays(7).atStartOfDay(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        OffsetDateTime through = decisionDate.atTime(15, 30).atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        return newsArticleStore.findBySymbolAndPublishedAtBetween(stockSymbol, from, through);
    }

    /** Same decision-date contract as above, retaining persisted IDs for audit evidence. */
    public List<PersistedNewsArticle> fetchPersistedStockNewsForDecisionDate(
            String stockSymbol, LocalDate decisionDate) {
        if (decisionDate == null) return List.of();
        ZoneId marketZone = ZoneId.of("Asia/Kolkata");
        OffsetDateTime from = decisionDate.minusDays(7).atStartOfDay(marketZone).toOffsetDateTime();
        OffsetDateTime through = decisionDate.atTime(15, 30).atZone(marketZone).toOffsetDateTime();
        if (decisionDate.isBefore(LocalDate.now(marketZone))) {
            return newsArticleStore
                .findPersistedBySymbolAndPublishedAtBetweenAndFirstSeenAtBeforeOrEqual(
                    stockSymbol, from, through, through);
        }

        List<NewsArticle> fetched = fetchStockNews(stockSymbol);
        Set<String> fetchedIdentities = fetched.stream().map(this::articleIdentity).collect(Collectors.toSet());
        return newsArticleStore.findPersistedBySymbolAndPublishedAtBetween(stockSymbol, from, through).stream()
            .filter(article -> fetchedIdentities.contains(articleIdentity(article.article())))
            .toList();
    }

    private String articleIdentity(NewsArticle article) {
        if (article.link() != null && !article.link().isBlank()) return article.link().trim();
        return String.join("|", article.source() == null ? "" : article.source().trim(),
            article.title() == null ? "" : article.title().trim(),
            article.publishedDate() == null ? "" : article.publishedDate().toInstant().toString());
    }

    /**
     * Fetches both articles and structured filings in parallel.
     */
    public NewsAndFilings fetchArticlesAndFilings(String symbol) {
        List<NewsArticle> articles = fetchStockNews(symbol);
        List<StructuredFiling> filings = List.of();

        try {
            List<StructuredFiling> nseFilings = nseSource.fetchFilings(symbol);
            List<StructuredFiling> bseFilings = bseSource.fetchFilings(symbol);
            filings = new ArrayList<>(nseFilings);
            filings.addAll(bseFilings);
        } catch (Exception e) {
            logger.warn("Failed to fetch filings for {}: {}", symbol, e.getMessage());
        }

        return new NewsAndFilings(articles, filings);
    }

    /**
     * Formats filings as text for LLM prompt injection.
     */
    public String formatFilingsPrompt(List<StructuredFiling> filings) {
        if (filings == null || filings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Corporate Announcements & Filings ===\n");
        for (StructuredFiling f : filings) {
            sb.append(String.format("[%s] %s: %s (%s)\n", f.date(), f.typeLabel(), f.title(), f.link()));
            if (f.description() != null && !f.description().isBlank()) {
                sb.append("  ").append(f.description()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Fetches news for multiple stocks.
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
     */
    public List<NewsArticle> fetchMarketNews() {
        logger.info("Fetching market-wide news");
        return fetchNewsFromFeeds();
    }

    /**
     * Cleans and normalizes news article text.
     */
    public String cleanNewsText(NewsArticle article) {
        StringBuilder cleanedText = new StringBuilder();

        if (article.title() != null && !article.title().isEmpty()) {
            cleanedText.append(article.title()).append(" ");
        }

        if (article.description() != null && !article.description().isEmpty()) {
            cleanedText.append(article.description()).append(" ");
        }

        if (article.rawContent() != null && !article.rawContent().isEmpty()) {
            cleanedText.append(article.rawContent()).append(" ");
        }

        return cleanedText.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Aggregates news from all sources for analysis.
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
     */
    public List<NewsArticle> filterRelevantNews(List<NewsArticle> articles) {
        return newsFilterService.filterRelevantArticles(articles);
    }

    /**
     * Fetches news with caching support.
     */
    public List<NewsArticle> fetchNewsWithCache(String cacheKey, int maxAgeMinutes) {
        logger.debug("Fetching news with cache key: {}", cacheKey);
        return fetchNewsFromFeeds();
    }

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
                                symbol, item.title, item.link, desc,
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
            String key = a.title().toLowerCase(Locale.ENGLISH).trim();
            if (seen.add(key)) {
                deduped.add(a);
            }
        }
        if (deduped.isEmpty() && !nullDateArticles.isEmpty()) {
            Set<String> seen2 = new HashSet<>();
            for (NewsArticle a : nullDateArticles) {
                String key = a.title().toLowerCase(Locale.ENGLISH).trim();
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
     * Simple record for storing parsed RSS item data.
     * Private — only used internally by parseGoogleNewsXml.
     */
    private record NewsItem(
        String title,
        String link,
        String description,
        ZonedDateTime pubDate,
        String content
    ) {}

    /**
     * Holds combined news articles and structured filings.
     */
    public record NewsAndFilings(
        List<NewsArticle> articles,
        List<StructuredFiling> filings
    ) {}
}
