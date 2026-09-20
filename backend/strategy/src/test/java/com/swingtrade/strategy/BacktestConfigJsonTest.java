package com.swingtrade.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BacktestConfigJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void plainJsonNumbersBindToBigDecimalKnobs() throws Exception {
        BacktestConfig config = mapper.readValue("""
                {"slippagePct":0.002,"brokeragePerTrade":15,"riskPerTradePct":0.02,
                 "initialCapital":250000,"maxConcurrentPositions":3,"atrMultiplierStop":1.5,
                 "rewardRiskRatio":3,"maxHoldingDays":10,"signalExitEnabled":true,"trendBreakStreakDays":5}
                """, BacktestConfig.class);

        assertThat(config.slippagePct()).isEqualByComparingTo("0.002");
        assertThat(config.brokeragePerTrade()).isEqualByComparingTo("15");
        assertThat(config.riskPerTradePct()).isEqualByComparingTo("0.02");
        assertThat(config.initialCapital()).isEqualByComparingTo("250000");
        assertThat(config.atrMultiplierStop()).isEqualTo(1.5);
        assertThat(config.riskManagementPolicy()).isNotNull();
    }

    @Test
    void absentMoneyKnobsDefaultToZeroLikeThePrimitiveDoublesDid() throws Exception {
        BacktestConfig config = mapper.readValue("""
                {"maxConcurrentPositions":3,"atrMultiplierStop":2,"rewardRiskRatio":2,"maxHoldingDays":10,
                 "signalExitEnabled":false,"trendBreakStreakDays":5}
                """, BacktestConfig.class);

        assertThat(config.initialCapital()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(config.slippagePct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void defaultsAreUnchanged() {
        BacktestConfig defaults = BacktestConfig.defaults();

        assertThat(defaults.slippagePct()).isEqualByComparingTo("0.001");
        assertThat(defaults.brokeragePerTrade()).isEqualByComparingTo("20");
        assertThat(defaults.riskPerTradePct()).isEqualByComparingTo("0.01");
        assertThat(defaults.initialCapital()).isEqualByComparingTo("500000");
    }
}
