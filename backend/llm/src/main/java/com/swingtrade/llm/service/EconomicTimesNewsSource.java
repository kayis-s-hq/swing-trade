package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches stock news from Economic Times RSS feed.
 */
@Service
public class EconomicTimesNewsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(EconomicTimesNewsSource.class);
    private static final String RSS_URL = "https://feeds.economictimes.com/search?query=";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final int maxArticles;

    public EconomicTimesNewsSource(@Value("${news.source.et.max-articles:15}") int maxArticles) {
        this.maxArticles = maxArticles;
    }

    @Override
    public String type() {
        return "economic_times";
    }

    @Override
    public List<NewsArticle> fetch(String symbol) {
        String url = RSS_URL + symbol;
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(8000)
                    .ignoreContentType(true)
                    .get();

            List<NewsArticle> articles = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Element item : doc.select("item")) {
                String title = safeText(item.selectFirst("title"));
                String link = safeText(item.selectFirst("link"));
                String desc = safeText(item.selectFirst("description"));
                String pubDateStr = safeText(item.selectFirst("pubDate"));

                if (title == null || title.isBlank()) continue;
                if (!title.toLowerCase(Locale.ENGLISH).contains(symbol.toLowerCase(Locale.ENGLISH))) continue;
                if (link == null || link.isBlank()) link = "https://economictimes.indiatimes.com";

                ZonedDateTime pubDate = null;
                if (pubDateStr != null && !pubDateStr.isBlank()) {
                    try {
                        pubDate = ZonedDateTime.parse(pubDateStr, DATE_FMT);
                    } catch (Exception ignored) {
                    }
                }

                if (desc != null) desc = desc.replaceAll("<[^>]+>", "").trim();

                String key = title.toLowerCase(Locale.ENGLISH);
                if (seen.add(key) && articles.size() < maxArticles) {
                    articles.add(NewsArticle.builder()
                            .symbol(symbol)
                            .title(title)
                            .link(link)
                            .description(desc)
                            .publishedDate(pubDate != null ? pubDate : ZonedDateTime.now())
                            .source("economic_times")
                            .rawContent(desc)
                            .build());
                }
            }

            log.debug("Economic Times: {} articles for {}", articles.size(), symbol);
            return articles;

        } catch (Exception e) {
            log.warn("Economic Times fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private static String safeText(Element el) {
        return el != null ? el.text().trim() : null;
    }
}