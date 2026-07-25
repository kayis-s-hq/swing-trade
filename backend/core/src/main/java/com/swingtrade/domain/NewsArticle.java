package com.swingtrade.domain;

import java.time.ZonedDateTime;

/**
 * Represents a news article from RSS feed or other sources.
 */
public record NewsArticle(
    String symbol,
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
        private String symbol;
        private String title;
        private String link;
        private String description;
        private ZonedDateTime publishedDate;
        private String source;
        private String rawContent;

        public NewsArticleBuilder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

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
            return new NewsArticle(symbol, title, link, description, publishedDate, source, rawContent);
        }
    }
}