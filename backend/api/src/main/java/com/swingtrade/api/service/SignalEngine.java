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

import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.SignalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Entry point for signal generation. Delegates pipeline logic to
 * {@link DailySignalOrchestrator} and {@link SignalPipeline}.
 *
 * <p>Public query methods are cached via {@code @Cacheable}. Generation methods
 * evict the cache via {@code @CacheEvict}. The manual trigger methods no longer
 * carry {@code @Cacheable} — the old annotation caused stale data to be returned
 * because the actual generation ran inside the same transactional proxy.</p>
 */
@Component
public class SignalEngine {

    private static final Logger logger = LoggerFactory.getLogger(SignalEngine.class);

    private final DailySignalOrchestrator orchestrator;
    private final SignalPipeline pipeline;
    private final SignalStore signalStore;

    public SignalEngine(DailySignalOrchestrator orchestrator,
                        SignalPipeline pipeline,
                        SignalStore signalStore) {
        this.orchestrator = orchestrator;
        this.pipeline = pipeline;
        this.signalStore = signalStore;
    }

    /**
     * Scheduled job to generate signals for all stocks with data.
     * Runs at 17:00 IST (30 min after market close) on weekdays.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Kolkata")
    public void generateDailySignals() {
        orchestrator.runDailyGeneration();
    }

    /**
     * Manually triggers signal generation for a specific symbol.
     *
     * @param symbol the stock symbol
     */
    @CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")
    public void generateSignalForSymbolNow(String symbol) {
        logger.info("Manually generating signal for {}", symbol);
        pipeline.generatePrimarySignal(symbol);
    }

    /**
     * Manually triggers price-action signal generation for a specific symbol.
     *
     * @param symbol the stock symbol
     */
    @CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")
    public void generatePriceActionSignalForSymbolNow(String symbol) {
        logger.info("Manually generating price-action signal for {}", symbol);
        pipeline.generatePriceActionSignal(symbol);
    }

    /**
     * Gets the latest signal for a symbol.
     *
     * @param symbol the stock symbol
     * @return latest signal or empty
     */
    @Cacheable(value = "latestSignal", key = "#symbol")
    public java.util.Optional<Signal> getLatestSignal(String symbol) {
        return signalStore.findLatestBySymbol(symbol);
    }

    /**
     * Gets all signals for a symbol.
     *
     * @param symbol the stock symbol
     * @return list of signals
     */
    @Cacheable(value = "signals", key = "#symbol")
    public List<Signal> getSignalsForSymbol(String symbol) {
        return signalStore.findBySymbol(symbol);
    }

    /**
     * Gets all buy signals generated since a date.
     *
     * @param sinceDate the start date
     * @return list of buy signals
     */
    public List<Signal> getBuySignalsSince(LocalDate sinceDate) {
        return signalStore.findBuySignalsSince(sinceDate);
    }
}