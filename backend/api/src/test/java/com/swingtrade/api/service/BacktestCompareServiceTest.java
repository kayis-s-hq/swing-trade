package com.swingtrade.api.service;

import com.swingtrade.api.dto.strategy.BacktestCompareRequest;
import com.swingtrade.api.dto.strategy.BacktestCompareResponse;
import com.swingtrade.data.entity.StrategyBacktestResultEntity;
import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import com.swingtrade.data.repository.StrategyBacktestResultRepository;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.LegacyPriceActionAdapter;
import com.swingtrade.strategy.PriceActionStrategy;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestCompareServiceTest {

    private static final String SYMBOL = "SYM";

    @Mock
    private StrategyConfigStore configStore;
    @Mock
    private CandleStore candleStore;
    @Mock
    private StrategyExperimentLogRepository experimentLogRepository;
    @Mock
    private StrategyBacktestResultRepository resultRepository;

    private BacktestCompareService service;

    @BeforeEach
    void setUp() {
        SignalStrategy breakout = new LegacyPriceActionAdapter(new PriceActionStrategy());
        StrategyTypeRegistry registry = new StrategyTypeRegistry(List.of(breakout));
        service = new BacktestCompareService(configStore, registry, candleStore, experimentLogRepository,
            resultRepository);
    }

    private StrategyConfig config(String variantId, int version) {
        return new StrategyConfig(1L, variantId, version, "BREAKOUT", defaultBreakoutParams(), Map.of(),
            "hash-" + variantId + "-" + version, StrategyMode.SHADOW,
            BigDecimal.valueOf(1_000_000), true, null, null, LocalDateTime.now());
    }

    /** Mirrors {@link LegacyPriceActionAdapter#defaultParams()} as a plain map, for building a {@link StrategyConfig} fixture. */
    private Map<String, Object> defaultBreakoutParams() {
        Map<String, Object> values = new HashMap<>();
        values.put("emaFast", 20);
        values.put("emaSlow", 50);
        values.put("rsiPeriod", 14);
        values.put("rsiMin", BigDecimal.valueOf(50));
        values.put("rsiMax", BigDecimal.valueOf(65));
        values.put("volumeMaPeriod", 20);
        values.put("volMult", BigDecimal.valueOf(1.5));
        values.put("weeklyHighPeriod", 252);
        values.put("highProximity", BigDecimal.valueOf(0.97));
        values.put("entryScoreThreshold", BigDecimal.ONE);
        values.put("atrPeriod", 14);
        values.put("atrStopMult", BigDecimal.valueOf(2));
        values.put("rewardRisk", BigDecimal.valueOf(2));
        values.put("maxHoldDays", 20);
        values.put("trailAtrMult", BigDecimal.ZERO);
        return values;
    }

    @Test
    @DisplayName("runs a plain compare, persists a fold result and an experiment log row, and computes a bounded DSR")
    void runsAPlainCompareAndPersistsResults() {
        List<OhlcvCandle> candles = buildEntrySetupCandles();
        when(configStore.findVersion(SYMBOL + "_V1", 1)).thenReturn(Optional.of(config(SYMBOL + "_V1", 1)));
        when(candleStore.findBySymbol(SYMBOL)).thenReturn(candles);
        when(experimentLogRepository.countDistinctParamsHashByStrategyType("BREAKOUT")).thenReturn(1L);

        BacktestCompareRequest request = new BacktestCompareRequest(
            List.of(new BacktestCompareRequest.VariantRef(SYMBOL + "_V1", 1)),
            candles.get(0).date().plusDays(200), candles.get(candles.size() - 1).date(),
            List.of(SYMBOL), null, true);

        BacktestCompareResponse response = service.compare(request);

        assertThat(response.variants()).hasSize(1);
        BacktestCompareResponse.VariantResult result = response.variants().get(0);
        assertThat(result.variantId()).isEqualTo(SYMBOL + "_V1");
        assertThat(result.folds()).hasSize(1);
        assertThat(result.folds().get(0).fold()).isEqualTo(0);
        assertThat(result.folds().get(0).metrics()).containsKey("sharpeRatio");
        assertThat(result.deflatedSharpeRatio()).isBetween(0.0, 1.0);

        org.mockito.Mockito.verify(resultRepository).save(any(StrategyBacktestResultEntity.class));
        org.mockito.Mockito.verify(experimentLogRepository).save(any(StrategyExperimentLogEntity.class));
    }

    @Test
    @DisplayName("rejects an unknown variant/version with 404")
    void rejectsUnknownVariant() {
        when(configStore.findVersion("MISSING", 1)).thenReturn(Optional.empty());
        List<OhlcvCandle> candles = buildEntrySetupCandles();
        when(candleStore.findBySymbol(SYMBOL)).thenReturn(candles);

        BacktestCompareRequest request = new BacktestCompareRequest(
            List.of(new BacktestCompareRequest.VariantRef("MISSING", 1)),
            candles.get(0).date().plusDays(200), candles.get(candles.size() - 1).date(),
            List.of(SYMBOL), null, true);

        assertThatThrownBy(() -> service.compare(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("rejects an empty variants list with 400")
    void rejectsEmptyVariantList() {
        BacktestCompareRequest request = new BacktestCompareRequest(List.of(),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), null, null, true);

        assertThatThrownBy(() -> service.compare(request)).isInstanceOf(ResponseStatusException.class);
    }

    private List<OhlcvCandle> buildEntrySetupCandles() {
        List<OhlcvCandle> candles = buildZigzagUptrendCandles(299, 100.0, 0.5, 0.75, 1_000_000L);
        OhlcvCandle lastZigzag = candles.get(candles.size() - 1);
        OhlcvCandle bump = buildFinalCandle(lastZigzag, 1.005, 2_000_000L);
        candles.add(bump);

        BigDecimal bumpClose = bump.close();
        candles.add(OhlcvCandle.of(SYMBOL, bump.date().plusDays(1), bumpClose,
            bumpClose.multiply(BigDecimal.valueOf(1.001)), bumpClose.multiply(BigDecimal.valueOf(0.999)),
            bumpClose, 1_000_000L));
        return candles;
    }

    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice, double upPercent,
                                                          double downPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(2023, 1, 2);
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;
            candles.add(OhlcvCandle.of(SYMBOL, date, bd(open), bd(high), bd(low), bd(close), volume));
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private OhlcvCandle buildFinalCandle(OhlcvCandle previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.close();
        BigDecimal open = previousClose;
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = close.multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = open.multiply(BigDecimal.valueOf(0.999));
        return OhlcvCandle.of(SYMBOL, previous.date().plusDays(1), open, high, low, close, volume);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
