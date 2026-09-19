package com.swingtrade.llm.service;

import com.swingtrade.llm.SentimentOutput;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the evidence boundary to model-generated sentiment details.
 *
 * <p>Red flags and catalysts are only actionable when they cite one or more
 * of the numbered articles supplied in the prompt, for example {@code [2]}.
 * Unsupported details are rejected rather than being persisted as facts.</p>
 */
public final class SentimentOutputValidator {

    private static final Pattern ARTICLE_CITATION = Pattern.compile("\\[(\\d+)]");

    private SentimentOutputValidator() {
    }

    /**
     * Returns an output containing only flags and catalysts grounded in the
     * supplied article range. The original output is never mutated.
     */
    public static SentimentOutput validate(SentimentOutput output, int articleCount) {
        Objects.requireNonNull(output, "output");
        return new SentimentOutput(
                output.getSentiment(),
                output.getReasoning(),
                output.getConfidence(),
                groundedItems(output.getRedFlags(), articleCount),
                groundedItems(output.getCatalysts(), articleCount),
                output.getSource());
    }

    private static List<String> groundedItems(List<String> items, int articleCount) {
        if (items == null || items.isEmpty() || articleCount < 1) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && !item.isBlank())
                .filter(item -> citesOnlyAvailableArticle(item, articleCount))
                .toList();
    }

    private static boolean citesOnlyAvailableArticle(String item, int articleCount) {
        Matcher matcher = ARTICLE_CITATION.matcher(item);
        boolean foundCitation = false;
        while (matcher.find()) {
            foundCitation = true;
            try {
                int articleIndex = Integer.parseInt(matcher.group(1));
                if (articleIndex < 1 || articleIndex > articleCount) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return foundCitation;
    }
}
