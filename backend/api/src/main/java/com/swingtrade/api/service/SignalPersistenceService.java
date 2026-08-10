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

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RiskCalculator;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Constructs {@link Signal} records with risk parameters and persists them.
 *
 * <p>Runs in its own {@code REQUIRES_NEW} transaction so a bad symbol's
 * persistence failure does not roll back signals generated for previous symbols.</p>
 */
@Service
public class SignalPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(SignalPersistenceService.class);

    private final SignalStore signalStore;
    private final CandleStore candleStore;

    public SignalPersistenceService(SignalStore signalStore, CandleStore candleStore) {
        this.signalStore = signalStore;
        this.candleStore = candleStore;
    }

    /**
     * Builds and saves a signal with entry price, stop-loss, target, and risk-reward.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @param type the signal type
     * @param confidence the confidence (0-1)
     * @param reasoning the human-readable reasoning
     * @param indicators the technical indicators that triggered the signal
     * @param atr the ATR value for risk calculation
     * @return the saved signal
     */
    public Signal buildAndSave(String symbol, LocalDate date, Signal.SignalType type,
                               BigDecimal confidence, String reasoning,
                               String indicators, BigDecimal atr) {
        return buildAndSave(symbol, date, type, confidence, reasoning, indicators, atr, null, null);
    }

    public Signal buildAndSave(String symbol, LocalDate date, Signal.SignalType type,
                               BigDecimal confidence, String reasoning,
                               String indicators, BigDecimal atr, String sentimentScore, String sentimentReasoning) {
        Signal baseSignal = Signal.create(symbol, date, type, confidence, reasoning);

        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(symbol).orElse(null);
        Signal toSave;
        if (latestCandle != null && latestCandle.close() != null) {
            BigDecimal closePrice = latestCandle.close();
            RiskCalculator.RiskParams riskParams = RiskCalculator.computeAll(closePrice, atr != null ? atr : BigDecimal.ZERO);
            toSave = new Signal(
                    baseSignal.id(),
                    baseSignal.symbol(),
                    baseSignal.date(),
                    baseSignal.type(),
                    baseSignal.confidence(),
                    baseSignal.reasoning(),
                    closePrice,
                    riskParams.stopLoss(),
                    riskParams.target(),
                    riskParams.riskReward(),
                    indicators,
                    baseSignal.generatedAt(),
                    sentimentScore,
                    sentimentReasoning
            );
        } else {
            toSave = new Signal(
                    baseSignal.id(),
                    baseSignal.symbol(),
                    baseSignal.date(),
                    baseSignal.type(),
                    baseSignal.confidence(),
                    baseSignal.reasoning(),
                    null, null, null, null,
                    indicators,
                    baseSignal.generatedAt(),
                    sentimentScore,
                    sentimentReasoning
            );
        }

        return signalStore.save(toSave);
    }

    /**
     * Builds and saves a signal with explicit risk parameters (no ATR needed).
     *
     * @param baseSignal the base signal
     * @param entryPrice the entry price
     * @param stopLoss the stop-loss price
     * @param target the target price
     * @param riskReward the risk-reward ratio
     * @param indicators the indicator string
     * @return the saved signal
     */
    public Signal buildAndSave(Signal baseSignal, BigDecimal entryPrice,
                               BigDecimal stopLoss, BigDecimal target,
                               BigDecimal riskReward, String indicators) {
        return buildAndSave(baseSignal, entryPrice, stopLoss, target, riskReward, indicators, null, null);
    }

    public Signal buildAndSave(Signal baseSignal, BigDecimal entryPrice,
                               BigDecimal stopLoss, BigDecimal target,
                               BigDecimal riskReward, String indicators, String sentimentScore, String sentimentReasoning) {
        Signal toSave = new Signal(
                baseSignal.id(),
                baseSignal.symbol(),
                baseSignal.date(),
                baseSignal.type(),
                baseSignal.confidence(),
                baseSignal.reasoning(),
                entryPrice,
                stopLoss,
                target,
                riskReward,
                indicators,
                baseSignal.generatedAt(),
                sentimentScore,
                sentimentReasoning
        );
        return signalStore.save(toSave);
    }

    /**
     * Builds and saves a signal with a warning flag.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @param type the signal type
     * @param confidence the confidence (0-1)
     * @param reasoning the human-readable reasoning
     * @param indicators the technical indicators that triggered the signal
     * @param atr the ATR value for risk calculation
     * @param warningFlag the warning flag (e.g., NEUTRAL_SENTIMENT)
     * @return the saved signal
     */
    public Signal buildAndSaveWithWarning(String symbol, LocalDate date, Signal.SignalType type,
                                          BigDecimal confidence, String reasoning,
                                          String indicators, BigDecimal atr, String warningFlag, String sentimentScore, String sentimentReasoning) {
        Signal baseSignal = Signal.create(symbol, date, type, confidence, reasoning);

        OhlcvCandle latestCandle = candleStore.findLatestBySymbol(symbol).orElse(null);
        Signal toSave;
        if (latestCandle != null && latestCandle.close() != null) {
            BigDecimal closePrice = latestCandle.close();
            RiskCalculator.RiskParams riskParams = RiskCalculator.computeAll(closePrice, atr != null ? atr : BigDecimal.ZERO);
            toSave = new Signal(
                    baseSignal.id(),
                    baseSignal.symbol(),
                    baseSignal.date(),
                    baseSignal.type(),
                    baseSignal.confidence(),
                    baseSignal.reasoning(),
                    closePrice,
                    riskParams.stopLoss(),
                    riskParams.target(),
                    riskParams.riskReward(),
                    indicators,
                    baseSignal.generatedAt(),
                    sentimentScore,
                    sentimentReasoning
            );
        } else {
            toSave = new Signal(
                    baseSignal.id(),
                    baseSignal.symbol(),
                    baseSignal.date(),
                    baseSignal.type(),
                    baseSignal.confidence(),
                    baseSignal.reasoning(),
                    null, null, null, null,
                    indicators,
                    baseSignal.generatedAt(),
                    sentimentScore,
                    sentimentReasoning
            );
        }

        return signalStore.save(toSave, warningFlag);
    }

    /**
     * Checks whether a signal already exists for this symbol and date.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @return true if a signal already exists
     */
    public boolean existsForDate(String symbol, LocalDate date) {
        List<Signal> existing = signalStore.findBySymbolAndDate(symbol, date);
        return !existing.isEmpty();
    }

    /**
     * Deletes all signals for a given symbol and date.
     *
     * @param symbol the stock symbol
     * @param date the signal date
     * @return number of signals deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBySymbolAndDate(String symbol, LocalDate date) {
        return signalStore.deleteBySymbolAndDate(symbol, date);
    }

    /**
     * Saves a signal directly.
     *
     * @param signal the signal to save
     * @return the saved signal
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Signal save(Signal signal) {
        return signalStore.save(signal);
    }
}