package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for filtering and ranking news articles by relevance.
 * Provides intelligent filtering based on stock mentions, recency, and source quality.
 */
@Service
public class NewsFilterService {

    private static final Logger logger = LoggerFactory.getLogger(NewsFilterService.class);

    // Time thresholds for article freshness
    private static final int RECENT_HOURS = 24;
    private static final int FRESH_HOURS = 48;
    private static final int MAX_HOURS = 168; // 1 week max

    // Source quality weights
    private static final List<String> HIGH_QUALITY_SOURCES = List.of(
            "Economic Times", "MoneyControl", "Reuters", "Bloomberg",
            "Business Standard", "Financial Express", "Livemint",
            "NSE India", "BSE India", "PTI"
    );

    // Keyword patterns for filtering
    private static final List<Pattern> SPAM_PATTERNS = List.of(
            Pattern.compile("\\b(congrats|congratulations|celebrate|party)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(free|guaranteed|100%|no risk)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(invest now|buy now|call of the day)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(unconfirmed|rumor|speculation|gossip)\\b", Pattern.CASE_INSENSITIVE)
    );

    // Trading signal keywords with weights
    private static final List<TradingKeyword> POSITIVE_KEYWORDS = List.of(
            new TradingKeyword("earnings beat", 3),
            new TradingKeyword("revenue growth", 3),
            new TradingKeyword("profit increase", 3),
            new TradingKeyword("upgrade", 3),
            new TradingKeyword("bullish", 3),
            new TradingKeyword("surge", 2),
            new TradingKeyword("strong", 2),
            new TradingKeyword("record", 2),
            new TradingKeyword("milestone", 2),
            new TradingKeyword("expansion", 2),
            new TradingKeyword("contract", 2),
            new TradingKeyword("partnership", 2),
            new TradingKeyword("positive outlook", 3),
            new TradingKeyword("guidance raise", 3),
            new TradingKeyword("dividend increase", 2),
            new TradingKeyword("buyback", 2)
    );

    private static final List<TradingKeyword> NEGATIVE_KEYWORDS = List.of(
            new TradingKeyword("earnings miss", 3),
            new TradingKeyword("revenue decline", 3),
            new TradingKeyword("loss", 3),
            new TradingKeyword("downgrade", 3),
            new TradingKeyword("bearish", 3),
            new TradingKeyword("drop", 2),
            new TradingKeyword("weak", 2),
            new TradingKeyword("concern", 2),
            new TradingKeyword("investigation", 3),
            new TradingKeyword("lawsuit", 2),
            new TradingKeyword("fraud", 3),
            new TradingKeyword("scandal", 3),
            new TradingKeyword("negative outlook", 3),
            new TradingKeyword("guidance cut", 3),
            new TradingKeyword("layoff", 2),
            new TradingKeyword("delay", 2)
    );

    private static final List<TradingKeyword> NEUTRAL_KEYWORDS = List.of(
            new TradingKeyword("quarterly results", 1),
            new TradingKeyword("board meeting", 1),
            new TradingKeyword("annual general meeting", 1),
            new TradingKeyword("management change", 1),
            new TradingKeyword("product launch", 1),
            new TradingKeyword("partnership announcement", 1)
    );

    /**
     * Filters a list of news articles by relevance and quality.
     *
     * @param articles list of articles to filter
     * @return filtered list of relevant articles
     */
    public List<NewsArticle> filterRelevantArticles(
            List<NewsArticle> articles) {

        logger.debug("Filtering {} articles for relevance", articles.size());

        List<FilteredArticle> filtered = new ArrayList<>();

        for (NewsArticle article : articles) {
            FilteredArticle filteredArticle = evaluateArticle(article);
            if (filteredArticle != null) {
                filtered.add(filteredArticle);
            }
        }

        // Sort by relevance score (descending)
        filtered.sort((a, b) -> Double.compare(b.score(), a.score()));

        logger.debug("Filtered to {} relevant articles with scores: {}-{}",
                filtered.size(),
                filtered.isEmpty() ? 0 : filtered.get(filtered.size() - 1).score(),
                filtered.isEmpty() ? 0 : filtered.get(0).score());

        // Return original article objects (not filtered wrapper)
        return filtered.stream()
                .map(FilteredArticle::article)
                .collect(Collectors.toList());
    }

    /**
     * Evaluates a single article and returns a score if it's relevant.
     *
     * @param article the article to evaluate
     * @return FilteredArticle with score, or null if article should be filtered out
     */
    private FilteredArticle evaluateArticle(NewsArticle article) {
        String content = getArticleContent(article);

        // Check for spam patterns
        if (containsSpamPattern(content)) {
            logger.trace("Article filtered out: spam pattern detected");
            return null;
        }

        // Check recency
        long ageHours = calculateAgeHours(article.publishedDate());
        if (ageHours > MAX_HOURS) {
            logger.trace("Article filtered out: too old ({} hours)", ageHours);
            return null;
        }

        // A generic short article from an untrusted source is not actionable market
        // information, even though the source/content baseline alone would otherwise
        // keep its combined score above the historical threshold.
        if (calculateTradingRelevanceScore(content) <= 0.2
                && !isHighQualitySource(article.source()) && !hasSubstance(article)) {
            logger.trace("Article filtered out: no trading relevance");
            return null;
        }

        // Calculate relevance score
        double score = calculateRelevanceScore(article, ageHours);

        // Minimum score threshold
        if (score < 0.3) {
            logger.trace("Article filtered out: low relevance score ({})", score);
            return null;
        }

        return new FilteredArticle(article, score);
    }

    /**
     * Calculates relevance score for an article.
     *
     * @param article the article
     * @param ageHours age in hours
     * @return relevance score between 0 and 1
     */
    private double calculateRelevanceScore(
            NewsArticle article,
            long ageHours) {

        double score = 1.0;

        // Age factor: newer is better
        if (ageHours <= RECENT_HOURS) {
            score *= 1.0; // Fresh
        } else if (ageHours <= FRESH_HOURS) {
            score *= 0.8; // Moderately fresh
        } else {
            score *= 0.5; // Older content
        }

        // Source quality factor
        if (isHighQualitySource(article.source())) {
            score *= 1.2; // Boost from high-quality source
        }

        // Content quality factor
        if (hasSubstance(article)) {
            score *= 1.1;
        }

        // Trading relevance factor
        double tradingScore = calculateTradingRelevanceScore(getArticleContent(article));
        score = (score * 0.4) + (tradingScore * 0.6); // 40% source/content, 60% trading relevance

        return Math.min(1.0, score);
    }

    /**
     * Calculates trading relevance score based on keywords.
     *
     * @param content the article content
     * @return trading relevance score between 0 and 1
     */
    private double calculateTradingRelevanceScore(String content) {
        String lowerContent = content.toLowerCase();

        // Count keyword matches with weights
        double positiveScore = 0;
        double negativeScore = 0;
        int positiveCount = 0;
        int negativeCount = 0;

        for (TradingKeyword keyword : POSITIVE_KEYWORDS) {
            if (lowerContent.contains(keyword.text.toLowerCase())) {
                positiveScore += keyword.weight;
                positiveCount++;
            }
        }

        for (TradingKeyword keyword : NEGATIVE_KEYWORDS) {
            if (lowerContent.contains(keyword.text.toLowerCase())) {
                negativeScore += keyword.weight;
                negativeCount++;
            }
        }

        // Normalize scores
        positiveScore = Math.min(1.0, positiveScore / 10);
        negativeScore = Math.min(1.0, negativeScore / 10);

        // Calculate final trading score
        // Articles with either positive or negative signals are more relevant than neutral ones
        if (positiveCount > 0 || negativeCount > 0) {
            return (positiveScore + negativeScore) / 2.0;
        }

        // Check for neutral trading-related content
        for (TradingKeyword keyword : NEUTRAL_KEYWORDS) {
            if (lowerContent.contains(keyword.text.toLowerCase())) {
                return 0.4; // Moderate relevance for neutral trading news
            }
        }

        return 0.2; // Low relevance - no trading signals
    }

    /**
     * Gets combined content from article.
     *
     * @param article the article
     * @return combined title, description, and raw content
     */
    private String getArticleContent(NewsArticle article) {
        StringBuilder sb = new StringBuilder();
        if (article.title() != null) {
            sb.append(article.title()).append(" ");
        }
        if (article.description() != null) {
            sb.append(article.description()).append(" ");
        }
        if (article.rawContent() != null) {
            sb.append(article.rawContent());
        }
        return sb.toString();
    }

    /**
     * Checks if content contains spam patterns.
     *
     * @param content the content to check
     * @return true if spam patterns found
     */
    private boolean containsSpamPattern(String content) {
        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a source is high quality.
     *
     * @param source the source name
     * @return true if high quality
     */
    private boolean isHighQualitySource(String source) {
        if (source == null) {
            return false;
        }

        String lowerSource = source.toLowerCase();
        return HIGH_QUALITY_SOURCES.stream()
                .anyMatch(hqSource -> lowerSource.contains(hqSource.toLowerCase()));
    }

    /**
     * Checks if article has substance (not just a headline).
     *
     * @param article the article
     * @return true if article has sufficient content
     */
    private boolean hasSubstance(NewsArticle article) {
        String content = getArticleContent(article);
        String[] words = content.trim().split("\\s+");

        // Must have at least 20 words to have substance
        return words.length >= 20;
    }

    /**
     * Calculates age of article in hours.
     *
     * @param publishedDate the publication date
     * @return age in hours
     */
    private long calculateAgeHours(ZonedDateTime publishedDate) {
        if (publishedDate == null) {
            return MAX_HOURS + 1; // Treat as too old
        }

        ZonedDateTime now = ZonedDateTime.now(publishedDate.getZone());
        if (publishedDate.isAfter(now)) {
            return MAX_HOURS + 1; // Future-dated content cannot be used safely
        }
        return ChronoUnit.HOURS.between(publishedDate, now);
    }

    /**
     * Analyzes sentiment of filtered articles.
     *
     * @param articles list of articles to analyze
     * @return ArticleSentimentAnalysis with positive/negative ratio
     */
    public ArticleSentimentAnalysis analyzeArticleSentiment(
            List<NewsArticle> articles) {

        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;

        for (NewsArticle article : articles) {
            String content = getArticleContent(article).toLowerCase();

            boolean hasPositive = POSITIVE_KEYWORDS.stream()
                    .anyMatch(kw -> content.contains(kw.text.toLowerCase()));

            boolean hasNegative = NEGATIVE_KEYWORDS.stream()
                    .anyMatch(kw -> content.contains(kw.text.toLowerCase()));

            if (hasPositive && !hasNegative) {
                positiveCount++;
            } else if (hasNegative && !hasPositive) {
                negativeCount++;
            } else if (hasPositive && hasNegative) {
                neutralCount++;
            } else {
                neutralCount++;
            }
        }

        return new ArticleSentimentAnalysis(
                positiveCount,
                negativeCount,
                neutralCount,
                articles.size()
        );
    }

    /**
     * Gets sentiment summary for articles.
     *
     * @param articles list of articles
     * @return sentiment summary string
     */
    public String getSentimentSummary(List<NewsArticle> articles) {
        ArticleSentimentAnalysis analysis = analyzeArticleSentiment(articles);

        if (articles.isEmpty()) {
            return "No articles available for analysis";
        }

        int positiveRatio = (int) ((analysis.positiveCount() / (double) analysis.totalCount) * 100);
        int negativeRatio = (int) ((analysis.negativeCount() / (double) analysis.totalCount) * 100);
        int neutralRatio = (int) ((analysis.neutralCount() / (double) analysis.totalCount) * 100);

        StringBuilder summary = new StringBuilder();
        summary.append("Article Sentiment Analysis: ");
        summary.append("Positive: ").append(positiveRatio).append("%, ");
        summary.append("Negative: ").append(negativeRatio).append("%, ");
        summary.append("Neutral: ").append(neutralRatio).append("%");

        return summary.toString();
    }

    // ============ Inner Classes ============

    /**
     * Record representing a filtered article with score.
     */
    private record FilteredArticle(
            NewsArticle article,
            double score
    ) {}

    /**
     * Record representing keyword for trading sentiment.
     */
    private record TradingKeyword(
            String text,
            int weight
    ) {}

    /**
     * Record representing article sentiment analysis.
     */
    public record ArticleSentimentAnalysis(
            int positiveCount,
            int negativeCount,
            int neutralCount,
            int totalCount
    ) {}
}
