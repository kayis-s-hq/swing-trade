package com.swingtrade.api.service;

import com.swingtrade.api.dto.strategy.BacktestCompareRequest;
import com.swingtrade.api.dto.strategy.BacktestCompareResponse;
import com.swingtrade.data.entity.StrategyBacktestResultEntity;
import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import com.swingtrade.data.repository.StrategyBacktestResultRepository;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.StrategyConfigStore;
import com.swingtrade.strategy.BacktestCostModel;
import com.swingtrade.strategy.DeflatedSharpeRatio;
import com.swingtrade.strategy.PortfolioBacktestConfig;
import com.swingtrade.strategy.PortfolioBacktestEngine;
import com.swingtrade.strategy.PortfolioBacktestResult;
import com.swingtrade.strategy.PortfolioMetrics;
import com.swingtrade.strategy.SignalStrategy;
import com.swingtrade.strategy.StrategyParamsView;
import com.swingtrade.strategy.StrategyTypeRegistry;
import com.swingtrade.strategy.ZerodhaDeliveryCostModel;
import com.swingtrade.strategy.WalkForwardConfig;
import com.swingtrade.strategy.WalkForwardFold;
import com.swingtrade.strategy.WalkForwardResult;
import com.swingtrade.strategy.WalkForwardRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates {@code POST /api/backtest/compare} (plan §6.8): looks up each requested
 * variant/version via {@link StrategyConfigStore}, runs {@link PortfolioBacktestEngine} (plain or
 * walk-forward per {@link BacktestCompareRequest#walkForward()}), persists every fold's result to
 * {@code strategy_backtest_results} and logs the run to {@code strategy_experiment_log}, then
 * computes the Deflated Sharpe Ratio (plan §6.4) using N = distinct params_hash tested for that
 * strategy type.
 *
 * <p><b>Sync-vs-async judgment call:</b> this phase runs the compare endpoint synchronously
 * rather than through the existing {@code JobOrchestratorService}/{@code job_runs} pipeline. That
 * pipeline is built around the daily SCAN/SIGNAL/SENTIMENT/PAPER_TRADE stage sequence over the
 * live watchlist; wiring an ad hoc, operator-triggered, arbitrary-date-range multi-variant
 * backtest into it would mean adding a new job type/stage with none of that machinery actually
 * reused, which is a bigger change than this phase's scope. A synchronous controller keeps the
 * implementation proportionate; if compare runs grow slow/frequent enough to need
 * async/progress-reporting, that's a natural candidate for wiring into job_runs properly in a
 * later phase.
 */
@Service
public class BacktestCompareService {

    private static final Logger log = LoggerFactory.getLogger(BacktestCompareService.class);
    private static final double ANNUAL_RISK_FREE_RATE_PCT = 6.5;

    private final StrategyConfigStore configStore;
    private final StrategyTypeRegistry typeRegistry;
    private final CandleStore candleStore;
    private final StrategyExperimentLogRepository experimentLogRepository;
    private final StrategyBacktestResultRepository resultRepository;
    private final PortfolioBacktestEngine engine;
    private final WalkForwardRunner walkForwardRunner;

    public BacktestCompareService(StrategyConfigStore configStore, StrategyTypeRegistry typeRegistry,
                                   CandleStore candleStore, StrategyExperimentLogRepository experimentLogRepository,
                                   StrategyBacktestResultRepository resultRepository) {
        this.configStore = configStore;
        this.typeRegistry = typeRegistry;
        this.candleStore = candleStore;
        this.experimentLogRepository = experimentLogRepository;
        this.resultRepository = resultRepository;
        this.engine = new PortfolioBacktestEngine();
        this.walkForwardRunner = new WalkForwardRunner(engine);
    }

    @Transactional
    public BacktestCompareResponse compare(BacktestCompareRequest request) {
        validate(request);
        Map<String, List<OhlcvCandle>> candlesBySymbol = loadCandles(request.symbols());

        List<BacktestCompareResponse.VariantResult> results = new ArrayList<>();
        for (BacktestCompareRequest.VariantRef ref : request.variants()) {
            results.add(runOneVariant(ref, request, candlesBySymbol));
        }
        return new BacktestCompareResponse(results);
    }

    private BacktestCompareResponse.VariantResult runOneVariant(BacktestCompareRequest.VariantRef ref,
                                                                  BacktestCompareRequest request,
                                                                  Map<String, List<OhlcvCandle>> candlesBySymbol) {
        StrategyConfig config = configStore.findVersion(ref.id(), ref.version())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Unknown variant/version: " + ref.id() + "@" + ref.version()));
        SignalStrategy strategy = typeRegistry.findByType(config.strategyType())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown strategy type: " + config.strategyType()));
        StrategyParamsView params = StrategyParamsView.of(config.params());
        PortfolioBacktestConfig backtestConfig = buildBacktestConfig(config, request.costsEnabled());

        List<BacktestCompareResponse.FoldResult> foldResults = new ArrayList<>();
        double[] foldSharpes;
        Double sharpeStdDev = null;
        boolean unstable = false;

        if (request.walkForward() != null) {
            WalkForwardConfig wfConfig = toWalkForwardConfig(request.walkForward());
            WalkForwardResult wf = walkForwardRunner.run(candlesBySymbol, strategy, params, backtestConfig,
                wfConfig, request.start(), request.end(), ANNUAL_RISK_FREE_RATE_PCT);
            int foldNumber = 1;
            for (WalkForwardFold fold : wf.folds()) {
                foldResults.add(persistFold(config, foldNumber, fold.testStart(), fold.testEnd(), fold.result()));
                foldNumber++;
            }
            sharpeStdDev = wf.sharpeStdDev();
            unstable = wf.unstable();
        } else {
            PortfolioBacktestResult result = engine.run(candlesBySymbol, strategy, params, backtestConfig,
                request.start(), request.end(), ANNUAL_RISK_FREE_RATE_PCT);
            foldResults.add(persistFold(config, 0, request.start(), request.end(), result));
        }

        long trials = Math.max(1, experimentLogRepository.countDistinctParamsHashByStrategyType(config.strategyType()));
        double lastFoldSharpe = foldResults.isEmpty() ? 0.0
            : ((Number) foldResults.get(foldResults.size() - 1).metrics().getOrDefault("sharpeRatio", 0.0)).doubleValue();
        int observations = foldResults.isEmpty() ? 2
            : Math.max(2, foldResults.get(foldResults.size() - 1).equityCurve().size());
        double dsr = DeflatedSharpeRatio.compute(lastFoldSharpe, (int) Math.min(trials, Integer.MAX_VALUE),
            observations, 0.0);

        // Fold in the OOS Sharpe (aliased for the promotion-eligibility lookup, plan §7.4) and this
        // run's DSR p-value before persisting, so a later findTopByVariantIdAndVersionOrderByCreatedAtDesc
        // read can recover both without recomputing anything.
        Map<String, Object> loggedMetrics = foldResults.isEmpty() ? new LinkedHashMap<>()
            : new LinkedHashMap<>(foldResults.get(foldResults.size() - 1).metrics());
        loggedMetrics.put("oosSharpe", lastFoldSharpe);
        loggedMetrics.put("dsrPValue", dsr);
        logExperiment(config, request.start(), request.end(), loggedMetrics);

        return new BacktestCompareResponse.VariantResult(config.variantId(), config.version(), config.strategyType(),
            foldResults, dsr, trials, unstable, sharpeStdDev);
    }

    private BacktestCompareResponse.FoldResult persistFold(StrategyConfig config, int fold, LocalDate windowStart,
                                                             LocalDate windowEnd, PortfolioBacktestResult result) {
        Map<String, Object> metrics = metricsToMap(result.metrics());
        List<Map<String, Object>> equityCurve = new ArrayList<>();
        for (var point : result.equityCurve()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", point.date().toString());
            p.put("equity", point.equity());
            equityCurve.add(p);
        }

        StrategyBacktestResultEntity entity = new StrategyBacktestResultEntity();
        entity.setVariantId(config.variantId());
        entity.setVersion(config.version());
        entity.setFold(fold);
        entity.setWindowStart(windowStart);
        entity.setWindowEnd(windowEnd);
        entity.setMetrics(metrics);
        entity.setEquityCurve(equityCurve);
        entity.setCreatedAt(LocalDateTime.now());
        resultRepository.save(entity);

        return new BacktestCompareResponse.FoldResult(fold, windowStart, windowEnd, metrics, equityCurve);
    }

    private void logExperiment(StrategyConfig config, LocalDate start, LocalDate end, Map<String, Object> metrics) {
        StrategyExperimentLogEntity entity = new StrategyExperimentLogEntity();
        entity.setVariantId(config.variantId());
        entity.setVersion(config.version());
        entity.setStrategyType(config.strategyType());
        entity.setParamsHash(config.paramsHash());
        entity.setWindowStart(start);
        entity.setWindowEnd(end);
        entity.setMetrics(metrics);
        entity.setCreatedAt(LocalDateTime.now());
        experimentLogRepository.save(entity);
    }

    private Map<String, Object> metricsToMap(PortfolioMetrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cagrPct", m.cagrPct());
        map.put("totalReturnPct", m.totalReturnPct());
        map.put("annualizedVolatilityPct", m.annualizedVolatilityPct());
        map.put("sharpeRatio", m.sharpeRatio());
        map.put("sortinoRatio", m.sortinoRatio());
        map.put("maxDrawdownPct", m.maxDrawdownPct());
        map.put("maxDrawdownDurationDays", m.maxDrawdownDurationDays());
        map.put("calmarRatio", m.calmarRatio());
        map.put("totalTrades", m.totalTrades());
        map.put("winRatePct", m.winRatePct());
        map.put("winRateCiLowPct", m.winRateCiLowPct());
        map.put("winRateCiHighPct", m.winRateCiHighPct());
        map.put("avgWinPct", m.avgWinPct());
        map.put("avgLossPct", m.avgLossPct());
        map.put("payoffRatio", m.payoffRatio());
        map.put("expectancyR", m.expectancyR());
        map.put("expectancyRCiLow", m.expectancyRCiLow());
        map.put("expectancyRCiHigh", m.expectancyRCiHigh());
        map.put("expectancyRupees", m.expectancyRupees());
        map.put("profitFactor", m.profitFactor());
        map.put("exposurePct", m.exposurePct());
        map.put("avgHoldingDays", m.avgHoldingDays());
        map.put("turnover", m.turnover());
        map.put("costDragPct", m.costDragPct());
        return map;
    }

    private PortfolioBacktestConfig buildBacktestConfig(StrategyConfig config, boolean costsOn) {
        BacktestCostModel costModel = costsOn ? new ZerodhaDeliveryCostModel()
            : (entryPrice, exitPrice, quantity, brokerage) -> BigDecimal.ZERO;
        double slippage = costsOn ? 0.001 : 0.0;
        BigDecimal brokerage = costsOn ? BigDecimal.valueOf(20) : BigDecimal.ZERO;
        return new PortfolioBacktestConfig(config.paperCapital(), 12, BigDecimal.valueOf(0.10),
            BigDecimal.valueOf(0.01), slippage, costModel, 14, brokerage);
    }

    private WalkForwardConfig toWalkForwardConfig(BacktestCompareRequest.WalkForwardRequest wf) {
        int trainM = wf.trainM() == null ? 24 : wf.trainM();
        int testM = wf.testM() == null ? 6 : wf.testM();
        int stepM = wf.stepM() == null ? 6 : wf.stepM();
        int holdoutM = wf.holdoutM() == null ? 6 : wf.holdoutM();
        boolean unlock = Boolean.TRUE.equals(wf.unlockHoldout());
        if (unlock) {
            log.warn("Walk-forward hold-out unlocked via API request for compare run (train={} test={} step={})",
                trainM, testM, stepM);
        }
        return new WalkForwardConfig(trainM, testM, stepM, holdoutM, unlock);
    }

    private Map<String, List<OhlcvCandle>> loadCandles(List<String> requestedSymbols) {
        List<String> symbols = requestedSymbols == null || requestedSymbols.isEmpty()
            ? candleStore.findAllDistinctSymbols()
            : requestedSymbols;
        Map<String, List<OhlcvCandle>> result = new HashMap<>();
        for (String symbol : symbols) {
            List<OhlcvCandle> candles = candleStore.findBySymbol(symbol);
            if (!candles.isEmpty()) {
                result.put(symbol, candles);
            }
        }
        if (result.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No candle history for any requested symbol");
        }
        return result;
    }

    private void validate(BacktestCompareRequest request) {
        if (request.variants() == null || request.variants().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "variants must not be empty");
        }
        if (request.start() == null || request.end() == null || !request.start().isBefore(request.end())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be before end");
        }
    }
}
