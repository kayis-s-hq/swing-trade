/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.swingtrade.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdvancedTechnicalIndicatorsTest {

    private AdvancedTechnicalIndicators indicators;

    @BeforeEach
    void setUp() {
        indicators = new AdvancedTechnicalIndicators();
    }

    @Test
    void stochasticValidatesInputAndCalculatesValues() {
        assertThat(indicators.calculateStochastic(null, 3, 2)).isNull();
        assertThat(indicators.calculateStochastic(List.of(), 3, 2)).isNull();
        assertThat(indicators.calculateStochastic(candles(2), 3, 2)).isNull();
        assertThatThrownBy(() -> indicators.calculateStochastic(candles(3), 0, 2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> indicators.calculateStochastic(candles(3), 3, -1))
            .isInstanceOf(IllegalArgumentException.class);

        AdvancedTechnicalIndicators.StochasticValues values =
            indicators.calculateStochastic(candles(20), 3, 2);
        assertThat(values).isNotNull();
        assertThat(values.k()).isBetween(0.0, 100.0);
        assertThat(values.d()).isBetween(0.0, 100.0);
    }

    @Test
    void bollingerBandsValidatesInputAndUsesMeanAndDeviation() {
        assertThat(indicators.calculateBollingerBands(null, 3, 2.0)).isNull();
        assertThat(indicators.calculateBollingerBands(List.of(), 3, 2.0)).isNull();
        assertThat(indicators.calculateBollingerBands(List.of(BigDecimal.ONE), 3, 2.0)).isNull();
        assertThatThrownBy(() -> indicators.calculateBollingerBands(List.of(BigDecimal.ONE), 0, 2.0))
            .isInstanceOf(IllegalArgumentException.class);

        AdvancedTechnicalIndicators.BollingerBands bands = indicators.calculateBollingerBands(
            List.of(BigDecimal.valueOf(1), BigDecimal.valueOf(2), BigDecimal.valueOf(3)), 3, 2.0);
        assertThat(bands.middle()).isEqualTo(2.0);
        assertThat(bands.upper()).isGreaterThan(bands.middle());
        assertThat(bands.lower()).isLessThan(bands.middle());
    }

    @Test
    void vwapReturnsVolumeWeightedValueOrNullWhenNoVolume() {
        assertThat(indicators.calculateVWAP(null)).isNull();
        assertThat(indicators.calculateVWAP(List.of())).isNull();
        assertThat(indicators.calculateVWAP(candlesWithVolume(2, 0.0))).isNull();

        double vwap = indicators.calculateVWAP(List.of(
            candle(10, 12, 8, 11, 2), candle(20, 22, 18, 21, 1)));
        double expected = ((12 + 8 + 11) / 3.0 * 2 + (22 + 18 + 21) / 3.0) / 3.0;
        assertThat(vwap).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void adxValidatesInputAndCalculatesDirectionalSeries() {
        assertThat(indicators.calculateADX(null, 3)).isNull();
        assertThat(indicators.calculateADX(List.of(), 3)).isNull();
        assertThat(indicators.calculateADX(candles(3), 3)).isNull();
        assertThatThrownBy(() -> indicators.calculateADX(candles(4), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(indicators.calculateADX(flatCandles(10), 3)).isNull();
        assertThat(indicators.calculateADX(candles(10), 3)).isNotNull();
    }

    private List<TechnicalIndicators.CandleWithPrices> candles(int count) {
        return candles(count, 100.0);
    }

    private List<TechnicalIndicators.CandleWithPrices> candles(int count, double base) {
        List<TechnicalIndicators.CandleWithPrices> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(candle(base + i, base + i + 2, base + i - 1, base + i + 1, 100 + i));
        }
        return result;
    }

    private List<TechnicalIndicators.CandleWithPrices> candlesWithVolume(int count, double volume) {
        List<TechnicalIndicators.CandleWithPrices> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(candle(10 + i, 11 + i, 9 + i, 10.5 + i, volume));
        }
        return result;
    }

    private List<TechnicalIndicators.CandleWithPrices> flatCandles(int count) {
        List<TechnicalIndicators.CandleWithPrices> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(candle(100, 100, 100, 100, 100));
        }
        return result;
    }

    private TechnicalIndicators.CandleWithPrices candle(double open, double high, double low,
                                                         double close, double volume) {
        return new TechnicalIndicators.CandleWithPrices(
            BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
            BigDecimal.valueOf(close), BigDecimal.valueOf(volume));
    }
}
