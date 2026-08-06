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

import com.swingtrade.domain.store.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the daily cron job: iterates over all symbols with candle data,
 * runs both the primary swing strategy and the price-action strategy for each.
 *
 * <p>Each symbol's generation runs in its own transaction (via {@code SignalPipeline}),
 * so a failure for one symbol does not roll back signals for previous symbols.</p>
 */
@Component
public class DailySignalOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(DailySignalOrchestrator.class);

    private final SignalPipeline pipeline;
    private final CandleStore candleStore;

    public DailySignalOrchestrator(SignalPipeline pipeline, CandleStore candleStore) {
        this.pipeline = pipeline;
        this.candleStore = candleStore;
    }

    /**
     * Runs daily signal generation across all symbols.
     *
     * @return a result summarizing success, failure, and total processed counts
     */
    public DailySignalResult runDailyGeneration() {
        logger.info("Starting daily signal generation at {}",
                LocalDate.now(ZoneId.of("Asia/Kolkata")));

        List<String> symbols;
        try {
            symbols = candleStore.findAllDistinctSymbols();
        } catch (Exception e) {
            logger.error("Failed to fetch distinct symbols: {}", e.getMessage());
            return new DailySignalResult(0, 0, 0, e.getMessage());
        }

        logger.info("Found {} symbols with candle data", symbols.size());

        int processed = 0;
        AtomicInteger success = new AtomicInteger(0);
        int[] failures = {0};

        for (String symbol : symbols) {
            try {
                pipeline.generatePrimarySignal(symbol).ifPresent(s -> success.incrementAndGet());
            } catch (Exception e) {
                logger.error("Error generating primary signal for {}: {}", symbol, e.getMessage());
                failures[0]++;
            }

            try {
                pipeline.generatePriceActionSignal(symbol).ifPresent(s -> success.incrementAndGet());
            } catch (Exception e) {
                logger.error("Error generating price-action signal for {}: {}", symbol, e.getMessage());
            }

            processed++;

            if (processed % 10 == 0) {
                logger.info("Progress: {}/{} symbols processed", processed, symbols.size());
            }
        }

        logger.info("Signal generation completed: {}/{} success, {} failures",
                success.get(), processed, failures[0]);

        return new DailySignalResult(success.get(), processed, failures[0], null);
    }

    /**
     * Summary of a daily signal generation run.
     *
     * @param success number of signals successfully generated
     * @param processed total symbols processed
     * @param failures number of symbols that failed
     * @param error error message if the run failed early, or null
     */
    public record DailySignalResult(
        int success,
        int processed,
        int failures,
        String error
    ) {}
}