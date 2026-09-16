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

package com.swingtrade.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure functions for risk parameter calculation: ATR, stop-loss, target price,
 * and risk-reward ratio.
 *
 * <p>Uses Wilder's true-range smoothing over 14 periods, including overnight
 * gaps through the prior close, matching the ATR semantics used by TA4J.</p>
 *
 * <p>This class is stateless and thread-safe. All methods are static.</p>
 */
public final class RiskCalculator {

    private RiskCalculator() {}

    private static final int DEFAULT_ATR_CANDLES = 14;
    private static final BigDecimal ATR_MULTIPLIER = BigDecimal.valueOf(2);
    private static final BigDecimal RISK_MULTIPLIER = BigDecimal.valueOf(2.5);

    /**
     * Calculates Average True Range using Wilder's true range and smoothing.
     *
     * @param candles chronologically-ordered candles (oldest first)
     * @return Wilder ATR, or 2% of latest close as fallback
     */
    public static BigDecimal calculateATR(List<OhlcvCandle> candles) {
        if (candles == null || candles.size() < DEFAULT_ATR_CANDLES + 1) {
            OhlcvCandle latest = candles == null || candles.isEmpty() ? null : candles.get(candles.size() - 1);
            if (latest != null && latest.close() != null) {
                return BigDecimal.valueOf(0.02).multiply(latest.close());
            }
            return BigDecimal.ZERO;
        }

        BigDecimal atr = null;
        int count = 0;
        for (int i = 1; i < candles.size(); i++) {
            OhlcvCandle current = candles.get(i);
            OhlcvCandle previous = candles.get(i - 1);
            if (current.high() == null || current.low() == null || previous.close() == null) continue;

            BigDecimal range = current.high().subtract(current.low());
            BigDecimal highGap = current.high().subtract(previous.close()).abs();
            BigDecimal lowGap = current.low().subtract(previous.close()).abs();
            BigDecimal trueRange = range.max(highGap).max(lowGap);
            count++;
            if (atr == null) {
                if (count < DEFAULT_ATR_CANDLES) continue;
                // Reconstruct the initial Wilder average from the first 14 TR values.
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - DEFAULT_ATR_CANDLES + 1; j <= i; j++) {
                    OhlcvCandle c = candles.get(j);
                    OhlcvCandle p = candles.get(j - 1);
                    BigDecimal r = c.high().subtract(c.low())
                        .max(c.high().subtract(p.close()).abs())
                        .max(c.low().subtract(p.close()).abs());
                    sum = sum.add(r);
                }
                atr = sum.divide(BigDecimal.valueOf(DEFAULT_ATR_CANDLES), 8, RoundingMode.HALF_UP);
            } else {
                atr = atr.multiply(BigDecimal.valueOf(DEFAULT_ATR_CANDLES - 1)).add(trueRange)
                    .divide(BigDecimal.valueOf(DEFAULT_ATR_CANDLES), 8, RoundingMode.HALF_UP);
            }
        }
        return atr != null ? atr : BigDecimal.ZERO;
    }

    /**
     * Calculates the stop-loss price from the close price and ATR.
     *
     * @param closePrice the current close price
     * @param atr the ATR value
     * @return stop-loss = closePrice - (ATR * 2)
     */
    public static BigDecimal calculateStopLoss(BigDecimal closePrice, BigDecimal atr) {
        return closePrice.subtract(atr.multiply(ATR_MULTIPLIER));
    }

    /**
     * Calculates the target price using a risk-based approach.
     *
     * <p>Formula: target = close + (risk * 2.5) where risk = close - stopLoss.</p>
     *
     * @param closePrice the current close price
     * @param atr the ATR value
     * @return target price
     */
    public static BigDecimal calculateTarget(BigDecimal closePrice, BigDecimal atr) {
        BigDecimal risk = closePrice.subtract(calculateStopLoss(closePrice, atr));
        return closePrice.add(risk.multiply(RISK_MULTIPLIER));
    }

    /**
     * Calculates the risk-reward ratio.
     *
     * @param stopLoss the stop-loss price
     * @param target the target price
     * @param closePrice the entry (close) price
     * @return (target - close) / (close - stopLoss), or zero if risk is zero
     */
    public static BigDecimal calculateRiskReward(BigDecimal stopLoss, BigDecimal target, BigDecimal closePrice) {
        BigDecimal risk = closePrice.subtract(stopLoss);
        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return target.subtract(closePrice).divide(risk, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Convenience method: computes all risk parameters in one call.
     *
     * @param closePrice the entry price
     * @param atr the ATR value
     * @return a {@link RiskParams} record with stop-loss, target, and risk-reward
     */
    public static RiskParams computeAll(BigDecimal closePrice, BigDecimal atr) {
        BigDecimal stopLoss = calculateStopLoss(closePrice, atr);
        BigDecimal target = calculateTarget(closePrice, atr);
        BigDecimal riskReward = calculateRiskReward(stopLoss, target, closePrice);
        return new RiskParams(stopLoss, target, riskReward);
    }

    /**
     * Immutable record holding computed risk parameters.
     *
     * @param stopLoss the stop-loss price
     * @param target the target price
     * @param riskReward the risk-reward ratio
     */
    public record RiskParams(
        BigDecimal stopLoss,
        BigDecimal target,
        BigDecimal riskReward
    ) {}
}
