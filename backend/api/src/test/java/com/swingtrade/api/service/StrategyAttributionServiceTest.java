package com.swingtrade.api.service;

import com.swingtrade.api.service.StrategyAttributionService.Inputs;
import com.swingtrade.api.service.StrategyAttributionService.Report;
import com.swingtrade.api.service.StrategyAttributionService.SignalRow;
import com.swingtrade.api.service.StrategyAttributionService.VariantInfo;
import com.swingtrade.api.service.StrategyAttributionService.VariantReport;
import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.Signal.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyAttributionServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 14);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 12);
    private static final LocalDate TO = LocalDate.of(2026, 9, 19);

    private static final List<VariantInfo> VARIANTS = List.of(
        new VariantInfo("breakout-v1", "BREAKOUT", "SHADOW"),
        new VariantInfo("pullback-v1", "PULLBACK", "SHADOW"));

    private static Map<String, Object> cand(String id, String signal, boolean selected) {
        return Map.of("variantId", id, "signal", signal, "selected", selected);
    }

    private static SignalSelectionEntity selection(String symbol, String winner, String status, String detail,
                                                   List<Map<String, Object>> slate) {
        var e = new SignalSelectionEntity(symbol, D1, winner, 1, 1L, new BigDecimal("0.80"), slate, "r");
        if (!SignalSelectionEntity.PENDING.equals(status)) e.markStatus(status, detail);
        return e;
    }

    private static ShadowClosedTrade trade(String portfolio, String symbol, LocalDate entry, String pnl) {
        return new ShadowClosedTrade(portfolio, symbol, entry, entry.plusDays(3), new BigDecimal("100"),
            new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("110"), 10, "TARGET_HIT", new BigDecimal(pnl));
    }

    private static VariantReport variant(Report r, String id) {
        return r.variants().stream().filter(v -> v.variantId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void countsSelectionsBlocksAndVetoesPerWinner() {
        var slate = List.of(cand("breakout-v1", "BUY", true), cand("pullback-v1", "BUY", false));
        var sels = List.of(
            selection("SBIN", "breakout-v1", SignalSelectionEntity.EXECUTED, null, slate),
            selection("INFY", "breakout-v1", SignalSelectionEntity.BLOCKED, "SENTIMENT_BLOCK: bad news", slate),
            selection("BSE", "pullback-v1", SignalSelectionEntity.BLOCKED, "LLM_BLOCK: no", slate),
            selection("GOKEX", "pullback-v1", SignalSelectionEntity.PENDING, null, slate));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, sels, List.of(),
            Map.of(), List.of()));

        assertThat(r.totals().tournaments()).isEqualTo(4);
        assertThat(r.totals().executed()).isEqualTo(1);
        assertThat(r.totals().blocked()).isEqualTo(2);
        assertThat(r.totals().pending()).isEqualTo(1);
        VariantReport b = variant(r, "breakout-v1");
        assertThat(b.timesSelected()).isEqualTo(2);
        assertThat(b.selectionRatePct()).isEqualTo(50.0);
        assertThat(b.sentimentVetoes()).isEqualTo(1);
        assertThat(variant(r, "pullback-v1").llmVetoes()).isEqualTo(1);
    }

    @Test
    void attributesSelectedBookTradesToLatestExecutedSelection() {
        var slate = List.of(cand("breakout-v1", "BUY", true));
        var sels = List.of(selection("SBIN", "breakout-v1", SignalSelectionEntity.EXECUTED, null, slate));
        var selectedTrades = List.of(
            trade("selected", "SBIN", D1.plusDays(1), "100"),
            trade("selected", "SBIN", D1.plusDays(2), "-40"));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, sels, List.of(),
            Map.of(), selectedTrades));

        VariantReport b = variant(r, "breakout-v1");
        assertThat(b.selectedTrades()).isEqualTo(2);
        assertThat(b.selectedWins()).isEqualTo(1);
        assertThat(b.selectedWinRatePct()).isEqualTo(50.0);
        assertThat(b.selectedPnl()).isEqualByComparingTo("60");
        assertThat(r.totals().selectedPnl()).isEqualByComparingTo("60");
    }

    @Test
    void shadowBookStatsIgnoreTradesClosedOutsideRange() {
        var inRange = trade("breakout-v1", "SBIN", D1, "50");
        var outOfRange = trade("breakout-v1", "SBIN", LocalDate.of(2026, 1, 5), "999");
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, List.of(), List.of(),
            Map.of("breakout-v1", List.of(inRange, outOfRange)), List.of()));

        VariantReport b = variant(r, "breakout-v1");
        assertThat(b.shadowTrades()).isEqualTo(1);
        assertThat(b.shadowPnl()).isEqualByComparingTo("50");
        assertThat(b.shadowWinRatePct()).isEqualTo(100.0);
        assertThat(variant(r, "pullback-v1").shadowWinRatePct()).isNull();
    }

    @Test
    void agreementRateIsShareOfOtherActiveVariantsAlsoBuying() {
        var signals = List.of(
            new SignalRow("breakout-v1", "SBIN", D1, SignalType.BUY),
            new SignalRow("pullback-v1", "SBIN", D1, SignalType.BUY),
            new SignalRow("breakout-v1", "INFY", D1, SignalType.BUY),
            new SignalRow("pullback-v1", "BSE", D1, SignalType.SELL));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, List.of(), signals,
            Map.of(), List.of()));

        VariantReport b = variant(r, "breakout-v1");
        assertThat(b.signalsGenerated()).isEqualTo(2);
        assertThat(b.buySignals()).isEqualTo(2);
        assertThat(b.agreementRatePct()).isEqualTo(50.0); // agreed on SBIN (1.0), alone on INFY (0.0)
        assertThat(variant(r, "pullback-v1").agreementRatePct()).isEqualTo(100.0);
    }

    @Test
    void agreementIsNullWithASingleActiveVariant() {
        var signals = List.of(new SignalRow("breakout-v1", "SBIN", D1, SignalType.BUY));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO,
            List.of(VARIANTS.get(0)), List.of(), signals, Map.of(), List.of()));
        assertThat(r.variants().get(0).agreementRatePct()).isNull();
    }

    @Test
    void regretIsGapToBestRejectedBuyOnlyWhenBothHaveClosedTrades() {
        var slate = List.of(cand("breakout-v1", "BUY", true), cand("pullback-v1", "BUY", false));
        var sels = List.of(selection("SBIN", "breakout-v1", SignalSelectionEntity.EXECUTED, null, slate));
        // pick lost 2% (-20 on 100*10), rejected alternative won 5% (+50)
        Map<String, List<ShadowClosedTrade>> shadow = Map.of(
            "breakout-v1", List.of(trade("breakout-v1", "SBIN", D1, "-20")),
            "pullback-v1", List.of(trade("pullback-v1", "SBIN", D1.plusDays(1), "50")));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, sels, List.of(),
            shadow, List.of()));

        assertThat(variant(r, "breakout-v1").avgRegretPct()).isEqualTo(7.0);
        assertThat(r.totals().avgRegretPct()).isEqualTo(7.0);

        Report noAlt = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, sels, List.of(),
            Map.of("breakout-v1", shadow.get("breakout-v1")), List.of()));
        assertThat(variant(noAlt, "breakout-v1").avgRegretPct()).isNull();
    }

    @Test
    void regretNeverGoesNegativeWhenPickBeatsAlternatives() {
        var slate = List.of(cand("breakout-v1", "BUY", true), cand("pullback-v1", "BUY", false));
        var sels = List.of(selection("SBIN", "breakout-v1", SignalSelectionEntity.EXECUTED, null, slate));
        Map<String, List<ShadowClosedTrade>> shadow = Map.of(
            "breakout-v1", List.of(trade("breakout-v1", "SBIN", D1, "80")),
            "pullback-v1", List.of(trade("pullback-v1", "SBIN", D1, "10")));
        Report r = StrategyAttributionService.compute(new Inputs(FROM, TO, VARIANTS, sels, List.of(),
            shadow, List.of()));
        assertThat(variant(r, "breakout-v1").avgRegretPct()).isEqualTo(0.0);
    }
}
