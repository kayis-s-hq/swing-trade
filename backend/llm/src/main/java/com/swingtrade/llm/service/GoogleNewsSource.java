package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Fetches stock news from Google News RSS with 3 queries per symbol.
 */
@Service
public class GoogleNewsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsSource.class);
    private static final String BASE = "https://news.google.com/rss/search?q=";
    private static final String PARAMS = "&hl=en-IN&gl=IN&ceid=IN:en";

    private HttpClient httpClient;

    @Value("${news.source.google.max-articles:20}")
    private int maxArticles;

    public GoogleNewsSource() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Package-private constructor for testing with an injectable HttpClient.
     */
    GoogleNewsSource(int maxArticles, HttpClient httpClient) {
        this.maxArticles = maxArticles;
        this.httpClient = httpClient;
    }

    @Override
    public String type() {
        return "google_news";
    }

    @Override
    public List<NewsArticle> fetch(String symbol) {
        List<String> queries = List.of(
                symbol + "+NSE+stock",
                symbol + "+India+market",
                symbol + "+earnings+result",
                symbol + "+share+price",
                symbol + "+NSE+BSE"
        );

        Set<String> seen = new HashSet<>();
        List<NewsArticle> articles = new ArrayList<>();

        for (String q : queries) {
            String url = BASE + URLEncoder.encode(q, StandardCharsets.UTF_8) + PARAMS;
            try {
                List<GoogleItem> items = parseXml(fetchXmlContent(url));
                for (GoogleItem item : items) {
                    if (item.title == null || item.title.isBlank()) continue;
                    String key = item.title.toLowerCase(Locale.ENGLISH).trim();
                    if (seen.add(key) && articles.size() < maxArticles) {
                        seen.add(key);
                        articles.add(NewsArticle.builder()
                                .symbol(symbol)
                                .title(item.title)
                                .link(item.link)
                                .description(item.description)
                                .publishedDate(item.pubDate != null ? item.pubDate : ZonedDateTime.now())
                                .source("google_news")
                                .rawContent(item.description)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.debug("Google News query failed for '{}': {}", q, e.getMessage());
            }
        }

        log.debug("Google News: {} articles for {}", articles.size(), symbol);
        return articles;
    }

    private List<GoogleItem> parseXml(String xml) throws Exception {
        if (xml == null || xml.isBlank()) return List.of();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        NodeList items = doc.getElementsByTagName("item");
        List<GoogleItem> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element el = (Element) items.item(i);
            String title = getChildText(el, "title");
            String link = getChildText(el, "link");
            String desc = getChildText(el, "description");
            ZonedDateTime pubDate = parseDate(getChildText(el, "pubDate"));

            String source = getNsText(el, "source", "http://www.google.com/2005/gml/");
            if (source == null || source.isBlank()) source = getChildText(el, "source");
            if (source == null || source.isBlank()) source = "google_news";

            result.add(new GoogleItem(title, link, desc, pubDate, source));
        }
        return result;
    }

    private String fetchXmlContent(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) return null;
            return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private String getChildText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() > 0) return StringEscapeUtils.unescapeHtml4(nl.item(0).getTextContent());
        return null;
    }

    private String getNsText(Element parent, String tag, String ns) {
        NodeList nl = parent.getElementsByTagNameNS(ns, tag);
        if (nl.getLength() > 0) return StringEscapeUtils.unescapeHtml4(nl.item(0).getTextContent());
        return null;
    }

    private ZonedDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ZonedDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH));
        } catch (Exception e) {
            return null;
        }
    }

    private record GoogleItem(String title, String link, String description, ZonedDateTime pubDate, String source) {}
}