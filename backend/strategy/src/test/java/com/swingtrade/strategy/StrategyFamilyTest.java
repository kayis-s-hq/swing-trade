package com.swingtrade.strategy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyFamilyTest {

    private static Indicators indicators(String price, String ema20, String ema50,
                                         String rsi, String volume, String volumeMa,
                                         String weeklyHigh) {
        return new Indicators(new BigDecimal(price), new BigDecimal(ema20), new BigDecimal(ema50),
            new BigDecimal(rsi), new BigDecimal(volume), new BigDecimal(volumeMa),
            new BigDecimal(weeklyHigh));
    }

    @Test
    void pullbackInUptrend_acceptsControlledPullback() {
        var strategy = new PullbackInUptrendStrategy();

        assertThat(strategy.isEntrySignal(indicators("98", "100", "95", "52", "110", "100", "105")))
            .isTrue();
        assertThat(strategy.isEntrySignal(indicators("92", "100", "95", "52", "110", "100", "105")))
            .isFalse();
    }

    @Test
    void volatilitySqueeze_requiresContractedVolumeAndBreakoutContext() {
        var strategy = new VolatilitySqueezeStrategy();

        assertThat(strategy.isEntrySignal(indicators("99", "98", "95", "60", "75", "100", "102")))
            .isTrue();
        assertThat(strategy.isEntrySignal(indicators("99", "98", "95", "60", "85", "100", "102")))
            .isFalse();
    }

    @Test
    void fiftyTwoWeekHighBreakout_requiresHighCloseAndTwoTimesVolume() {
        var strategy = new FiftyTwoWeekHighBreakoutStrategy();

        assertThat(strategy.isEntrySignal(indicators("106", "100", "95", "70", "200", "100", "105")))
            .isTrue();
        assertThat(strategy.isEntrySignal(indicators("106", "100", "95", "70", "199", "100", "105")))
            .isFalse();
        assertThat(strategy.isEntrySignal(indicators("105", "100", "95", "70", "200", "100", "105")))
            .isFalse();
        assertThat(strategy.isEntrySignal(indicators("104", "100", "95", "70", "200", "100", "105")))
            .isFalse();
    }

    @Test
    void rsi2MeanReversion_requiresOversoldReadingAboveLongTermTrend() {
        var strategy = new Rsi2MeanReversionStrategy();

        assertThat(strategy.isEntrySignal(indicators("101", "100", "95", "9", "100", "100", "110")))
            .isTrue();
        assertThat(strategy.isEntrySignal(indicators("101", "100", "95", "10", "100", "100", "110")))
            .isFalse();
        assertThat(strategy.isSignalExit(indicators("101", "100", "95", "9", "100", "100", "110")))
            .isTrue();
    }

    @Test
    void familyNamesAreStableAndProductionStrategyIsUnchanged() {
        assertThat(new PullbackInUptrendStrategy().name()).isEqualTo("PULLBACK_UPTREND");
        assertThat(new VolatilitySqueezeStrategy().name()).isEqualTo("VOLATILITY_SQUEEZE");
        assertThat(new FiftyTwoWeekHighBreakoutStrategy().name()).isEqualTo("52W_HIGH_BREAKOUT");
        assertThat(new Rsi2MeanReversionStrategy().name()).isEqualTo("RSI2_MEAN_REVERSION");
        assertThat(new PriceActionStrategy().name()).isEqualTo(PriceActionStrategy.NAME);
    }

    @Test
    void registryExplicitlyIncludesTheStandardSetups() {
        var priceAction = new PriceActionStrategy();
        var registry = new StrategyRegistry(java.util.List.of(
            priceAction,
            new PullbackInUptrendStrategy(),
            new VolatilitySqueezeStrategy(),
            new FiftyTwoWeekHighBreakoutStrategy(),
            new Rsi2MeanReversionStrategy()), priceAction);

        assertThat(registry.find(FiftyTwoWeekHighBreakoutStrategy.NAME)).isPresent();
        assertThat(registry.find(Rsi2MeanReversionStrategy.NAME)).isPresent();
    }
}
