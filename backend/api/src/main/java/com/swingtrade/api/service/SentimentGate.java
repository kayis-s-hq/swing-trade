/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.api.service;

import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.llm.service.SentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Decides whether to allow, suppress, or flag a BUY signal based on LLM sentiment analysis.
 *
 * <p>Sentiment check failures are non-blocking: the signal is saved anyway with a
 * {@code WARNING_NONE} flag. This ensures that LLM outages never prevent signal generation.</p>
 */
@Component
public class SentimentGate {

    private static final Logger logger = LoggerFactory.getLogger(SentimentGate.class);

    private final SentimentService sentimentService;
    private final SentimentStore sentimentStore;
    private final SignalStore signalStore;

    @Autowired(required = false)
    private GateEffectivenessAuditService effectivenessAuditService;

    public SentimentGate(SentimentService sentimentService, SentimentStore sentimentStore) {
        this(sentimentService, sentimentStore, null);
    }

    public SentimentGate(SentimentService sentimentService, SentimentStore sentimentStore,
                         SignalStore signalStore) {
        this.sentimentService = sentimentService;
        this.sentimentStore = sentimentStore;
        this.signalStore = signalStore;
    }

    /**
     * Evaluates sentiment for a BUY signal, running a fresh LLM analysis unconditionally.
     * Used by the price-action manual/dashboard path, which persists its signal in the same
     * call and has no later stage to defer the check to.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @return a {@link SentimentVerdict} indicating whether to suppress, flag, or allow
     */
    public SentimentVerdict evaluate(String symbol, LocalDate date) {
        try {
            return classify(sentimentService.analyzeStockSentiment(symbol, date));
        } catch (Exception e) {
            logger.warn("Failed to check sentiment for {} on {}: {}, saving signal anyway",
                    symbol, date, e.getMessage());
            return SentimentVerdict.allowGraceful(e.getMessage());
        }
    }

    /**
     * Evaluates sentiment for a BUY signal using only what the SENTIMENT stage already
     * persisted - never triggers a second, independent LLM call for the same symbol/date.
     * Used by the orchestrated pipeline's PAPER_TRADE stage, which runs after SENTIMENT and
     * should read its verdict, not recompute it (recomputing risked disagreeing with the
     * SENTIMENT stage's own recorded result, since the LLM isn't deterministic call-to-call).
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @return {@link SentimentVerdict#pending()} if nothing has been persisted yet (the safe
     *     default is to not trade an unvetted signal), otherwise the classified verdict
     */
    public SentimentVerdict evaluatePersisted(String symbol, LocalDate date) {
        SentimentVerdict verdict = sentimentStore.findBySymbolAndDate(symbol, date)
            .map(this::classify)
            .orElseGet(SentimentVerdict::pending);
        if (effectivenessAuditService != null) {
            var strategies = signalStore == null ? java.util.List.<String>of()
                : signalStore.findStrategiesBySymbolAndDate(symbol, date);
            if (strategies.isEmpty()) {
                effectivenessAuditService.recordSentimentVerdict(symbol, date, verdict);
            } else {
                strategies.forEach(strategy -> effectivenessAuditService
                    .recordSentimentVerdict(symbol, date, verdict, strategy));
            }
        }
        return verdict;
    }

    private SentimentVerdict classify(SentimentResult sentiment) {
        if (sentiment.isNegative()) {
            logger.info("Blocking BUY trade due to NEGATIVE sentiment (reasoning: {})", sentiment.summary());
            return SentimentVerdict.suppress(sentiment.summary());
        }
        if (sentiment.isNeutral() || sentiment.isUnknown()) {
            logger.info("Flagging NEUTRAL sentiment (reasoning: {})", sentiment.summary());
            return SentimentVerdict.flagNeutral(sentiment.summary());
        }
        return SentimentVerdict.allowWithSummary(sentiment.summary());
    }

    /**
     * Immutable verdict for a sentiment gate evaluation.
     *
     * @param action {@code ALLOW}, {@code SUPPRESS}, or {@code FLAG_NEUTRAL}
     * @param reason the sentiment reasoning/summary (if available)
     * @param errorMessage the error message if sentiment check failed (if applicable)
     */
    public record SentimentVerdict(
        Action action,
        String reason,
        String errorMessage
    ) {
        public enum Action {
            ALLOW,
            SUPPRESS,
            FLAG_NEUTRAL,
            ALLOW_GRACEFUL,
            /** No sentiment has been persisted for this symbol/date yet. */
            PENDING
        }

        public static SentimentVerdict allow() {
            return new SentimentVerdict(Action.ALLOW, null, null);
        }

        public static SentimentVerdict allowWithSummary(String summary) {
            return new SentimentVerdict(Action.ALLOW, summary, null);
        }

        public static SentimentVerdict suppress(String reason) {
            return new SentimentVerdict(Action.SUPPRESS, reason, null);
        }

        public static SentimentVerdict flagNeutral(String reason) {
            return new SentimentVerdict(Action.FLAG_NEUTRAL, reason, null);
        }

        public static SentimentVerdict allowGraceful(String errorMessage) {
            return new SentimentVerdict(Action.ALLOW_GRACEFUL, null, errorMessage);
        }

        public static SentimentVerdict pending() {
            return new SentimentVerdict(Action.PENDING, null, null);
        }
    }
}
