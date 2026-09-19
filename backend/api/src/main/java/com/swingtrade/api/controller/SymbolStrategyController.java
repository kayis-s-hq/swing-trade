package com.swingtrade.api.controller;

import com.swingtrade.api.service.SignalArbiter;
import com.swingtrade.data.entity.SignalSelectionEntity;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.ShadowPositionView;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One symbol seen through every strategy: latest signal, shadow positions/results and tournament history. */
@RestController
@RequestMapping("/api/symbols")
public class SymbolStrategyController {

    private static final String SELECTED_PORTFOLIO_ID = "selected";

    private final StrategyConfigStore strategyConfigStore;
    private final SignalStore signalStore;
    private final PaperPortfolioService paperPortfolioService;
    private final SignalArbiter signalArbiter;

    public SymbolStrategyController(StrategyConfigStore strategyConfigStore, SignalStore signalStore,
                                    PaperPortfolioService paperPortfolioService, SignalArbiter signalArbiter) {
        this.strategyConfigStore = strategyConfigStore;
        this.signalStore = signalStore;
        this.paperPortfolioService = paperPortfolioService;
        this.signalArbiter = signalArbiter;
    }

    @GetMapping("/{symbol}/strategy-matrix")
    public StrategyMatrixResponse matrix(@PathVariable String symbol) {
        List<ShadowPositionView> positions = paperPortfolioService.listShadowPositionsForSymbol(symbol);
        List<StrategyRow> rows = new ArrayList<>();
        for (StrategyConfig config : strategyConfigStore.findAllCurrent()) {
            rows.add(row(config.variantId(), config.strategyType(), config.mode().name(), symbol, positions));
        }
        rows.add(row(SELECTED_PORTFOLIO_ID, "TOURNAMENT", "SELECTED", symbol, positions));
        List<SelectionRow> history = signalArbiter.findForSymbol(symbol).stream()
            .limit(20).map(SelectionRow::from).toList();
        return new StrategyMatrixResponse(symbol, rows, history);
    }

    private StrategyRow row(String variantId, String type, String mode, String symbol,
                            List<ShadowPositionView> allPositions) {
        Signal signal = SELECTED_PORTFOLIO_ID.equals(variantId) ? null
            : signalStore.findLatestBySymbolAndStrategy(symbol, variantId).orElse(null);
        List<ShadowPositionView> mine = allPositions.stream()
            .filter(p -> variantId.equals(p.portfolioId())).toList();
        ShadowPositionView open = mine.stream().filter(p -> "OPEN".equals(p.status())).findFirst().orElse(null);
        List<ShadowPositionView> closed = mine.stream().filter(p -> "CLOSED".equals(p.status())).toList();
        BigDecimal realized = closed.stream().map(p -> p.pnl() == null ? BigDecimal.ZERO : p.pnl())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long wins = closed.stream().filter(p -> p.pnl() != null && p.pnl().signum() > 0).count();
        return new StrategyRow(variantId, type, mode,
            signal == null ? null : signal.type().name(),
            signal == null ? null : signal.confidence(),
            signal == null ? null : signal.date(),
            open, closed.size(), realized, closed.isEmpty() ? null : (double) wins / closed.size());
    }

    public record StrategyRow(String variantId, String strategyType, String mode, String latestSignal,
                              BigDecimal latestConfidence, LocalDate latestSignalDate, ShadowPositionView openPosition,
                              int closedTrades, BigDecimal realizedPnl, Double winRate) {
    }

    public record SelectionRow(LocalDate selectionDate, String winnerVariantId, BigDecimal winnerConfidence,
                               String status) {
        static SelectionRow from(SignalSelectionEntity e) {
            return new SelectionRow(e.getSelectionDate(), e.getWinnerVariantId(), e.getWinnerConfidence(),
                e.getStatus());
        }
    }

    public record StrategyMatrixResponse(String symbol, List<StrategyRow> strategies, List<SelectionRow> tournaments) {
    }
}
