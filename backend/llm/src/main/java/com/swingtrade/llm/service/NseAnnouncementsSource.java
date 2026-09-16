package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.llm.service.StructuredFiling.FilingType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Fetches corporate announcements from NSE India.
 * The public API requires {@code index=equities}; it returns a JSON array for a
 * symbol query and does not require a browser cookie session.
 */
@Service
public class NseAnnouncementsSource implements NewsSource {

    private static final Logger log = LoggerFactory.getLogger(NseAnnouncementsSource.class);
    private static final String API_URL = "https://www.nseindia.com/api/corporate-announcements?index=equities&symbol=";
    private static final String HTML_URL = "https://www.nseindia.com/corporates/announcements";
    private static final String HOME_URL = "https://www.nseindia.com/";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final int maxArticles;
    public NseAnnouncementsSource(@Value("${news.source.nse.max-articles:10}") int maxArticles) {
        this.maxArticles = maxArticles;
    }

    @Override
    public String type() {
        return "nse";
    }

    @Override
    public List<NewsArticle> fetch(String symbol) {
        List<StructuredFiling> filings = fetchFilings(symbol);
        return filings.stream().map(f -> NewsArticle.builder()
                .symbol(symbol)
                .title(f.title())
                .link(f.link())
                .description(f.description())
                .publishedDate(f.date() != null ? f.date().atStartOfDay(IST) : ZonedDateTime.now(IST))
                .source("nse")
                .rawContent(f.description())
                .build()).limit(maxArticles).collect(Collectors.toList());
    }

    @Override
    public List<StructuredFiling> fetchFilings(String symbol) {
        List<StructuredFiling> filings = new ArrayList<>();
        try {
            // Try API first
            filings = fetchApi(symbol);
        } catch (Exception e) {
            log.debug("NSE API failed for '{}': {}", symbol, e.getMessage());
        }

        // Fallback to HTML scraping
        if (filings.isEmpty()) {
            try {
                filings = fetchHtml(symbol);
            } catch (Exception e) {
                log.warn("NSE HTML fetch failed for '{}': {}", symbol, e.getMessage());
            }
        }

        return filings;
    }

    private List<StructuredFiling> fetchApi(String symbol) {
        List<StructuredFiling> filings = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(API_URL + symbol)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("X-Index", "nseindia.com")
                    .header("Referer", "https://www.nseindia.com/corporates/announcements")
                    .timeout(8000)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .get();

            String body = doc.body().text();
            filings = parseJsonResponse(body);
            log.debug("NSE API returned {} filings for {}", filings.size(), symbol);
        } catch (Exception e) {
            log.debug("NSE API fetch failed for '{}': {}", symbol, e.getMessage());
        }
        return filings;
    }

    private List<StructuredFiling> fetchHtml(String symbol) {
        List<StructuredFiling> filings = new ArrayList<>();
        try {
            // Warmup: visit homepage first to get session cookies
            try {
                Jsoup.connect(HOME_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(8000)
                        .followRedirects(true)
                        .get();
            } catch (Exception e) {
                log.debug("NSE warmup skipped: {}", e.getMessage());
            }

            Document doc = Jsoup.connect(HTML_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(8000)
                    .header("Referer", HOME_URL)
                    .followRedirects(true)
                    .get();

            filings = parseHtmlAnnouncements(doc);
            log.debug("NSE HTML returned {} filings for {}", filings.size(), symbol);
        } catch (Exception e) {
            log.debug("NSE HTML fetch failed for '{}': {}", symbol, e.getMessage());
        }
        return filings;
    }

    private List<StructuredFiling> parseJsonResponse(String body) {
        List<StructuredFiling> filings = new ArrayList<>();
        if (body == null || body.isBlank()) return filings;

        try {
            tools.jackson.databind.JsonNode root =
                    new tools.jackson.databind.ObjectMapper().readTree(body);
            // Symbol-filtered NSE responses are an array. Keep accepting the
            // historic { data: [...] } shape to remain tolerant of API changes.
            tools.jackson.databind.JsonNode data = root.isArray() ? root : root.path("data");
            if (!data.isArray()) return filings;

            for (tools.jackson.databind.JsonNode item : data) {
                String company = item.path("sm_name").asText();
                String announcement = item.path("desc").asText();
                String title = company.isBlank() ? announcement
                        : announcement.isBlank() ? company : company + " - " + announcement;
                if (title.isBlank()) continue;

                String dateField = item.path("an_dt").asText(
                        item.path("sort_date").asText(
                                item.path("event_date").asText(
                                        item.path("announcement_date").asText(""))));
                LocalDate ld = parseLocalDate(dateField);
                if (ld == null) continue;

                String link = item.path("attchmntFile").asText(
                        item.path("target_url").asText(item.path("link").asText("#")));
                String description = item.path("attchmntText").asText(announcement);

                filings.add(new StructuredFiling(classify(title), ld, title, description, link));
            }
        } catch (Exception e) {
            log.debug("NSE JSON parse failed: {}", e.getMessage());
        }
        return filings;
    }

    private List<StructuredFiling> parseHtmlAnnouncements(Document doc) {
        List<StructuredFiling> filings = new ArrayList<>();

        for (Element row : doc.select("table tr, .announcement-table tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            String dateStr = cells.get(0).text().trim();
            Element titleEl = cells.get(1).selectFirst("a");
            String title = titleEl != null ? titleEl.text().trim() : cells.get(1).text().trim();
            String link = titleEl != null && !titleEl.attr("href").isEmpty()
                    ? titleEl.attr("href")
                    : "#";

            if (title.isBlank()) continue;
            LocalDate ld = parseLocalDate(dateStr);
            if (ld == null) continue;

            filings.add(new StructuredFiling(
                    classify(title), ld, title, "",
                    link.startsWith("http") ? link : "https://www.nseindia.com" + link));
        }
        return filings;
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String normalized = s.trim();
            if (normalized.length() >= 11) {
                normalized = normalized.substring(0, 11);
            }
            return LocalDate.parse(normalized,
                    java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
        } catch (Exception e) {
            return null;
        }
    }

    private FilingType classify(String title) {
        String t = title.toLowerCase(Locale.ENGLISH);
        if (t.contains("board meeting") || t.contains("boardmeet")) return FilingType.BOARD_MEETING;
        if (t.contains("shareholding") || t.contains("share holding")) return FilingType.SHAREHOLDING;
        if (t.contains("dividend")) return FilingType.DIVIDEND;
        if (t.contains("split") || t.contains("bonus") || t.contains("right")) return FilingType.CORPORATE_ACTION;
        if (t.contains("result") || t.contains("quarterly") || t.contains("quarter")) return FilingType.PERFORMANCE_RESULT;
        return FilingType.OTHER;
    }

}
