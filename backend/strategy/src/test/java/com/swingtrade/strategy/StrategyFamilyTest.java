package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
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
        var confluence = new PriceActionConfluenceStrategy();
        var registry = new StrategyRegistry(java.util.List.of(
            priceAction,
            confluence,
            new PullbackInUptrendStrategy(),
            new VolatilitySqueezeStrategy(),
            new FiftyTwoWeekHighBreakoutStrategy(),
            new Rsi2MeanReversionStrategy()), priceAction);

        assertThat(registry.find(FiftyTwoWeekHighBreakoutStrategy.NAME)).isPresent();
        assertThat(registry.find(Rsi2MeanReversionStrategy.NAME)).isPresent();
        assertThat(registry.find(PriceActionConfluenceStrategy.NAME)).isPresent();
    }

    @Test
    void priceActionConfluence_acceptsThreeOfFourAndConfiguredRsiRange() {
        var strategy = new PriceActionConfluenceStrategy(new BigDecimal("45"), new BigDecimal("60"));

        assertThat(strategy.isEntrySignal(indicators("103", "100", "95", "55", "100", "100", "105")))
            .isTrue(); // trend, RSI, and high pass; volume does not
        assertThat(strategy.isEntrySignal(indicators("103", "100", "95", "61", "100", "100", "105")))
            .isFalse(); // only trend and high pass
        assertThat(strategy.rsiBelowLowerBound(indicators("103", "100", "95", "44", "100", "100", "105")))
            .isTrue();
        assertThat(strategy.entryRsiDescription()).isEqualTo("RSI between 45-60");
    }

    @Test
    void priceActionConfluence_readsExistingStrategyConfigParameters() {
        var strategy = PriceActionConfluenceStrategy.fromParameters(
            java.util.Map.of("rsiLower", "48", "rsiUpper", 62));

        assertThat(strategy.rsiInEntryRange(indicators("101", "100", "95", "62", "100", "100", "105")))
            .isTrue();
        assertThat(strategy.entryRsiDescription()).isEqualTo("RSI between 48-62");
    }

    @Test
    void registryResolvesConfiguredParametersPerVariant() {
        var priceAction = new PriceActionStrategy();
        var confluence = new PriceActionConfluenceStrategy();
        var registry = new StrategyRegistry(java.util.List.of(priceAction, confluence), priceAction);
        var config = StrategyConfig.create("WIDE_RSI", 1, PriceActionConfluenceStrategy.NAME,
            java.util.Map.of("rsiLower", "45", "rsiUpper", "72"), java.util.Map.of(),
            StrategyConfig.Mode.SHADOW, new BigDecimal("100000"), true, null,
            java.time.LocalDateTime.now());

        var resolved = registry.resolve(config);

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().entryRsiDescription()).isEqualTo("RSI between 45-72");
        assertThat(registry.resolve(null)).isEmpty();
    }
}
