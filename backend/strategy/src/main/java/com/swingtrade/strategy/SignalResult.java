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

package com.swingtrade.strategy;

import com.swingtrade.domain.Signal.SignalType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Result of the Phase 2 price-action strategy engine's analysis for a single symbol.
 * Carries the raw indicator readings alongside the resulting signal so downstream
 * consumers (persistence, dashboards, backtests) don't need to recompute them.
 *
 * @param symbol    the stock symbol analyzed
 * @param date      the date of the latest candle used for analysis
 * @param type      the resulting signal type (BUY, SELL, or HOLD)
 * @param rsi       the 14-period RSI value at the latest candle (all indicator values are
 *                  normalized to {@link FinancialScale#INDICATOR_SCALE} decimal places)
 * @param ema20     the 20-period EMA value at the latest candle
 * @param ema50     the 50-period EMA value at the latest candle
 * @param atr       the 14-period ATR value at the latest candle
 * @param reasoning human-readable explanation of which entry rules passed or failed
 */
public record SignalResult(
    String symbol,
    LocalDate date,
    SignalType type,
    BigDecimal rsi,
    BigDecimal ema20,
    BigDecimal ema50,
    BigDecimal atr,
    String reasoning
) {
    /** Normalizes indicator readings to {@link FinancialScale#INDICATOR_SCALE} (HALF_UP). */
    public SignalResult {
        rsi = FinancialScale.indicator(rsi);
        ema20 = FinancialScale.indicator(ema20);
        ema50 = FinancialScale.indicator(ema50);
        atr = FinancialScale.indicator(atr);
    }
}
