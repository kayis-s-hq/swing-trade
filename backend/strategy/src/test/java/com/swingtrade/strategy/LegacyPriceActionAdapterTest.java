package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal.SignalType;
import com.swingtrade.domain.StrategyParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LegacyPriceActionAdapter")
class LegacyPriceActionAdapterTest {

    private final LegacyPriceActionAdapter adapter = new LegacyPriceActionAdapter(new PriceActionStrategy());

    @Test
    void type_isBreakout() {
        assertThat(adapter.type()).isEqualTo("BREAKOUT");
    }

    @Test
    void defaultParams_matchStrategyParamsConstants() {
        StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

        assertThat(params.getInt("emaFast")).isEqualTo(StrategyParams.EMA_FAST);
        assertThat(params.getInt("emaSlow")).isEqualTo(StrategyParams.EMA_SLOW);
        assertThat(params.getDecimal("rsiMin")).isEqualTo(StrategyParams.RSI_LOWER);
        assertThat(params.getDecimal("rsiMax")).isEqualTo(StrategyParams.RSI_UPPER);
        assertThat(params.getDecimal("volMult")).isEqualTo(StrategyParams.VOLUME_MULTIPLIER);
        assertThat(params.getDecimal("highProximity")).isEqualTo(StrategyParams.HIGH_PROXIMITY);
        assertThat(params.getDecimal("entryScoreThreshold")).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void warmupBars_isMaxOfEmaSlowAndWeeklyHighPeriod() {
        StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

        assertThat(adapter.warmupBars(params))
            .isEqualTo(Math.max(StrategyParams.EMA_SLOW, StrategyParams.FIFTY_TWO_WEEK_TRADING_DAYS));
    }

    @Test
    void requiredIndicators_isNonEmpty() {
        assertThat(adapter.requiredIndicators(LegacyPriceActionAdapter.defaultParams())).isNotEmpty();
    }

    @Test
    void classifyLegacyStyle_reproducesSignalExitWiring() {
        // Sharp, sustained markdown: by the last bar, close<ema20, ema20<ema50 and rsi<50 all
        // hold, so both the legacy 1-of-3 SELL confluence and the adapter's wiring through
        // legacyRules.isSignalExit must agree on SELL - directly exercising the wiring the
        // golden parity fixture doesn't happen to trigger.
        String symbol = "CRASH";
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 80; i++) {
            double open = price;
            double close = price * 0.985;
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;
            candles.add(OhlcvCandle.of(symbol, date, BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), 1_000_000L));
            price = close;
            date = date.plusDays(1);
        }

        MarketContext ctx = MarketContext.of(symbol, candles);
        int lastBar = ctx.barCount() - 1;
        StrategyParamsView params = LegacyPriceActionAdapter.defaultParams();

        assertThat(adapter.classifyLegacyStyle(ctx, lastBar, params)).isEqualTo(SignalType.SELL);
    }

    @Test
    void paramSchema_describesEveryDefaultParam() {
        ParamSchema schema = adapter.paramSchema();

        assertThat(schema.params()).isNotEmpty();
        assertThat(schema.params()).extracting(ParamDef::name).contains(
            "emaFast", "emaSlow", "rsiMin", "rsiMax", "volMult", "highProximity",
            "entryScoreThreshold", "atrStopMult", "rewardRisk", "maxHoldDays", "trailAtrMult");
    }
}
