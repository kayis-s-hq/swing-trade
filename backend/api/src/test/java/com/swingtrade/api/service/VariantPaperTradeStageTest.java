package com.swingtrade.api.service;

import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.service.VariantTradingService;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.strategy.StrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("VariantPaperTradeStage selected-book text (E5)")
class VariantPaperTradeStageTest {

    private static final String SYMBOL = "TCS";

    private VariantPaperTradeStage stage;

    @BeforeEach
    void setUp() {
        var arbiter = mock(SignalArbiter.class);
        var sentimentGate = mock(SentimentGate.class);
        var candles = mock(CandleStore.class);
        when(candles.findLatestBySymbol(SYMBOL)).thenReturn(Optional.of(OhlcvCandle.of(SYMBOL, LocalDate.now(),
            BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1L)));
        var signals = mock(SignalStore.class);
        var buy = new Signal(9L, SYMBOL, LocalDate.now(), Signal.SignalType.BUY, new BigDecimal("0.9"), "r",
            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null, LocalDate.now(), null, null);
        when(signals.findLatestBySymbolAndStrategy(anyString(), anyString())).thenReturn(Optional.of(buy));
        when(sentimentGate.evaluatePersisted(any(), any())).thenReturn(new SentimentGate.SentimentVerdict(
            SentimentGate.SentimentVerdict.Action.PENDING, null, null));
        stage = new VariantPaperTradeStage(mock(VariantTradingService.class), candles, signals,
            mock(StrategyResolver.class), arbiter, sentimentGate, null, null, false, true);
        var selection = new SignalSelectionEntity(SYMBOL, LocalDate.now(), "pullback-v1", 1, 9L,
            new BigDecimal("0.9"), List.of(), "winner");
        when(arbiter.findLatest(SYMBOL, SignalSelectionEntity.PENDING)).thenReturn(Optional.of(selection));
    }

    @Test
    void namesTheWinnerOnlyWhenItWasEvaluatedThisRun() {
        String text = stage.selectedTrade(SYMBOL, "selected", BigDecimal.TEN, 5, Set.of("pullback-v1"));

        assertThat(text).isEqualTo("deferred pullback-v1 (awaiting sentiment)");
    }

    @Test
    void neverNamesAVariantThatWasSkippedOrNotEvaluated() {
        String text = stage.selectedTrade(SYMBOL, "selected", BigDecimal.TEN, 5, Set.of("breakout-v1"));

        assertThat(text).doesNotContain("pullback-v1").contains("prior selection");
    }
}
