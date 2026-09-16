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
    void familyNamesAreStableAndProductionStrategyIsUnchanged() {
        assertThat(new PullbackInUptrendStrategy().name()).isEqualTo("PULLBACK_UPTREND");
        assertThat(new VolatilitySqueezeStrategy().name()).isEqualTo("VOLATILITY_SQUEEZE");
        assertThat(new PriceActionStrategy().name()).isEqualTo(PriceActionStrategy.NAME);
    }
}
