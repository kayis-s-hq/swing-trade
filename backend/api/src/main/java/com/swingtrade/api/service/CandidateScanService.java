package com.swingtrade.api.service;

import com.swingtrade.data.entity.CandidateScanResultEntity;
import com.swingtrade.data.entity.CandidateScanRunEntity;
import com.swingtrade.data.repository.CandidateScanResultRepository;
import com.swingtrade.data.repository.CandidateScanRunRepository;
import com.swingtrade.data.repository.FyersSymbolRepository;
import com.swingtrade.data.service.DataIngestionService;
import com.swingtrade.data.service.WatchlistService;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.strategy.BacktestConfig;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.strategy.BacktestResult;
import com.swingtrade.strategy.PriceActionSignalEngine;
import com.swingtrade.strategy.SignalResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CandidateScanService {
    private static final Logger logger = LoggerFactory.getLogger(CandidateScanService.class);
    private static final int MIN_CANDLES = 60;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private final FyersSymbolRepository symbolRepository;
    private final CandidateScanRunRepository runRepository;
    private final CandidateScanResultRepository resultRepository;
    private final DataIngestionService ingestionService;
    private final WatchlistService watchlistService;
    private final CandleStore candleStore;
    private final PriceActionSignalEngine signalEngine;
    private final BacktestEngine backtestEngine;
    private final int backfillYears;
    private final long delayMs;
    private final int maxConcurrent;
    private final Semaphore semaphore;
    private final ExecutorService executor;
    private final AtomicReference<UUID> activeRun = new AtomicReference<>();
    private final ConcurrentHashMap<UUID, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Deque<ScanLogEvent>> logHistory = new ConcurrentHashMap<>();
    private static final int MAX_LOG_HISTORY = 500;

    public record ScanLogEvent(
        String eventType,
        UUID runId,
        String symbol,
        String level,
        String message,
        int completedSymbols,
        int totalSymbols,
        int failedSymbols,
        int qualifiedSymbols,
        LocalDateTime timestamp
    ) {}

    public CandidateScanService(FyersSymbolRepository symbolRepository,
                                CandidateScanRunRepository runRepository,
                                CandidateScanResultRepository resultRepository,
                                DataIngestionService ingestionService,
                                WatchlistService watchlistService,
                                CandleStore candleStore,
                                PriceActionSignalEngine signalEngine,
                                BacktestEngine backtestEngine,
                                @Value("${candidate-scan.backfill-years:3}") int backfillYears,
                                @Value("${candidate-scan.delay-ms:1000}") long delayMs,
                                @Value("${candidate-scan.max-concurrent:3}") int maxConcurrent) {
        this.symbolRepository = symbolRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.ingestionService = ingestionService;
        this.watchlistService = watchlistService;
        this.candleStore = candleStore;
        this.signalEngine = signalEngine;
        this.backtestEngine = backtestEngine;
        this.backfillYears = backfillYears;
        this.delayMs = Math.max(0, delayMs);
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.semaphore = new Semaphore(this.maxConcurrent);
        this.executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("candidate-scan-", 0).factory());
    }

    @Transactional
    public CandidateScanRunEntity start() {
        UUID existing = activeRun.get();
        if (existing != null || runRepository.existsByStatus("RUNNING")) {
            throw new IllegalStateException("Candidate scan already running: " + existing);
        }

        // Candidate Explorer is a current-run view, not a scan archive. Remove the
        // previous run and its child results before creating the replacement snapshot.
        resultRepository.deleteAllInBatch();
        runRepository.deleteAllInBatch();

        List<String> symbols = symbolRepository.findByExchangeIgnoreCaseOrderByTradingSymbolAsc("NSE")
            .stream()
            .map(s -> s.getTradingSymbol() == null ? "" : s.getTradingSymbol().trim().toUpperCase())
            .filter(s -> !s.isBlank() && s.matches("[A-Z0-9]+"))
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

        CandidateScanRunEntity run = new CandidateScanRunEntity();
        run.setRunId(UUID.randomUUID());
        run.setStatus("RUNNING");
        run.setTotalSymbols(symbols.size());
        run.setStartedAt(LocalDateTime.now(MARKET_ZONE));
        runRepository.save(run);
        activeRun.set(run.getRunId());
        cancellations.put(run.getRunId(), new AtomicBoolean(false));
        logHistory.put(run.getRunId(), new ConcurrentLinkedDeque<>());
        publish(run.getRunId(), "RUN_STARTED", null, "INFO",
            "Scanning " + symbols.size() + " NSE symbols with up to " + maxConcurrent + " workers.");
        executor.submit(() -> execute(run.getRunId(), symbols));
        return run;
    }

    public SseEmitter stream(UUID runId) {
        if (getRun(runId) == null) return null;
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> runEmitters = emitters.computeIfAbsent(runId,
            ignored -> new java.util.concurrent.CopyOnWriteArrayList<>());
        runEmitters.add(emitter);
        emitter.onCompletion(() -> runEmitters.remove(emitter));
        emitter.onTimeout(() -> runEmitters.remove(emitter));
        emitter.onError(ignored -> runEmitters.remove(emitter));
        CandidateScanRunEntity run = getRun(runId);
        send(emitter, new ScanLogEvent(
            "RUN_SNAPSHOT", runId, null, "INFO",
            "Connected to candidate scan (" + run.getStatus() + ").",
            run.getCompletedSymbols(), run.getTotalSymbols(), run.getFailedSymbols(),
            run.getQualifiedSymbols(), LocalDateTime.now(MARKET_ZONE)));
        for (ScanLogEvent event : logHistory.getOrDefault(runId, new ConcurrentLinkedDeque<>())) {
            send(emitter, event);
        }
        if (!"RUNNING".equals(run.getStatus())) emitter.complete();
        return emitter;
    }

    public CandidateScanRunEntity getRun(UUID runId) {
        return runRepository.findByRunId(runId).orElse(null);
    }

    public List<CandidateScanResultEntity> getResults(UUID runId, int offset, int limit) {
        List<CandidateScanResultEntity> all = resultRepository.findByRunIdOrderBySymbolAsc(runId);
        int from = Math.max(0, Math.min(offset, all.size()));
        int to = Math.min(all.size(), from + Math.max(1, Math.min(limit, 500)));
        return all.subList(from, to);
    }

    public List<CandidateScanRunEntity> getHistory() {
        return runRepository.findTop20ByOrderByStartedAtDesc();
    }

    @Transactional
    public boolean cancel(UUID runId) {
        CandidateScanRunEntity run = getRun(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())) return false;
        AtomicBoolean cancellation = cancellations.get(runId);
        if (cancellation != null) cancellation.set(true);
        run.setStatus("CANCELLED");
        run.setCompletedAt(LocalDateTime.now(MARKET_ZONE));
        runRepository.save(run);
        if (runId.equals(activeRun.get())) activeRun.compareAndSet(runId, null);
        publish(runId, "RUN_CANCELLED", null, "WARN", "Scan cancellation requested.");
        completeStreams(runId);
        return true;
    }

    private void execute(UUID runId, List<String> symbols) {
        List<CompletableFuture<Void>> futures = symbols.stream()
            .map(symbol -> CompletableFuture.runAsync(() -> processSymbol(runId, symbol), executor))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, error) -> {
            finalizeRun(runId, error);
        });
    }

    private void processSymbol(UUID runId, String symbol) {
        if (isCancelled(runId)) return;
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            if (isCancelled(runId)) return;
            publish(runId, "SYMBOL_STARTED", symbol, "INFO", "Processing " + symbol + ".");
            boolean qualified = scanSymbol(runId, symbol);
            increment(runId, false, qualified);
            publish(runId, "SYMBOL_COMPLETED", symbol, qualified ? "SUCCESS" : "INFO",
                qualified ? symbol + " qualified and was activated." : symbol + " did not pass the gate.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warn("Candidate scan failed for {}: {}", symbol, e.getMessage());
            saveFailure(runId, symbol, e);
        } finally {
            if (acquired) semaphore.release();
        }
        if (delayMs > 0 && !isCancelled(runId)) {
            try { Thread.sleep(delayMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void finalizeRun(UUID runId, Throwable error) {
        CandidateScanRunEntity run = getRun(runId);
        if (run != null && "RUNNING".equals(run.getStatus())) {
            run.setStatus(isCancelled(runId) || error != null ? "CANCELLED" : "COMPLETED");
            run.setCompletedAt(LocalDateTime.now(MARKET_ZONE));
            if (error != null) run.setErrorMessage(error.getMessage());
            runRepository.save(run);
            publish(runId, run.getStatus().equals("COMPLETED") ? "RUN_COMPLETED" : "RUN_CANCELLED",
                null, error == null ? "SUCCESS" : "WARN",
                error == null ? "Candidate scan completed." : "Candidate scan stopped: " + error.getMessage());
        }
        activeRun.compareAndSet(runId, null);
        cancellations.remove(runId);
        completeStreams(runId);
    }

    private boolean scanSymbol(UUID runId, String symbol) {
        int candles = (int) candleStore.countBySymbol(symbol);
        boolean fetched = false;
        publish(runId, "STAGE_STARTED", symbol, "INFO", "Data: checking OHLCV history.");
        if (candles < MIN_CANDLES) {
            publish(runId, "STAGE_STARTED", symbol, "INFO",
                "Data: fetching " + backfillYears + " years of OHLCV history.");
            ingestionService.backfillStockData(symbol, backfillYears);
            fetched = true;
            candles = (int) candleStore.countBySymbol(symbol);
        }
        publish(runId, "STAGE_COMPLETED", symbol, candles >= MIN_CANDLES ? "SUCCESS" : "WARN",
            "Data: " + candles + " candles available" + (fetched ? " after fetch." : "."));
        CandidateScanResultEntity result = new CandidateScanResultEntity();
        result.setRunId(runId);
        result.setSymbol(symbol);
        result.setCandleCount(candles);
        if (candles < MIN_CANDLES) {
            result.setDataStatus("INSUFFICIENT");
            result.setReason("Insufficient OHLCV history (" + candles + " candles)");
            resultRepository.save(result);
            return false;
        }
        result.setDataStatus("READY");
        publish(runId, "STAGE_STARTED", symbol, "INFO", "Signal: generating technical signal.");
        SignalResult signal = signalEngine.generateSignal(symbol);
        result.setSignalType(signal.type().name());
        publish(runId, "STAGE_COMPLETED", symbol, signal.type().name().equals("BUY") ? "SUCCESS" : "INFO",
            "Signal: " + signal.type() + " generated.");
        publish(runId, "STAGE_STARTED", symbol, "INFO", "Backtest: running default strategy.");
        BacktestResult backtest = backtestEngine.runBacktest(symbol, "NSE", BacktestConfig.defaults());
        result.setTotalTrades(backtest.totalTrades());
        result.setWinRate(backtest.winRate());
        result.setTotalReturn(backtest.totalReturn());
        result.setMaxDrawdownPct(backtest.maxDrawdownPct());
        publish(runId, "STAGE_COMPLETED", symbol, "INFO",
            "Backtest: " + backtest.totalTrades() + " trades, "
                + String.format(java.util.Locale.ROOT, "%.1f%% win rate, %.2f%% return.",
                    backtest.winRate(), backtest.totalReturn()));
        boolean qualified = signal.type() == com.swingtrade.domain.Signal.SignalType.BUY
            && backtest.winRate() >= 45.0
            && backtest.totalReturn() > 0.0;
        result.setQualified(qualified);
        result.setReason(qualified ? "BUY and backtest gate passed" : qualificationReason(signal, backtest));
        if (qualified) {
            watchlistService.addToWatchlist(symbol, symbol, "NSE");
            result.setActivated(true);
        }
        resultRepository.save(result);
        return qualified;
    }

    private String qualificationReason(SignalResult signal, BacktestResult backtest) {
        if (signal.type() != com.swingtrade.domain.Signal.SignalType.BUY) return "Current signal is " + signal.type();
        if (backtest.winRate() < 45.0) return "BUY rejected: win rate below 45%";
        return "BUY rejected: backtest return is not positive";
    }

    private void saveFailure(UUID runId, String symbol, Exception error) {
        CandidateScanResultEntity result = new CandidateScanResultEntity();
        result.setRunId(runId);
        result.setSymbol(symbol);
        result.setDataStatus("ERROR");
        result.setReason("Scan failed");
        result.setErrorMessage(error.getMessage());
        resultRepository.save(result);
        increment(runId, true, false);
        publish(runId, "SYMBOL_FAILED", symbol, "ERROR", "Scan failed: " + error.getMessage());
    }

    private synchronized void increment(UUID runId, boolean failed, boolean qualified) {
        CandidateScanRunEntity run = getRun(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())) return;
        run.setCompletedSymbols(run.getCompletedSymbols() + 1);
        if (failed) run.setFailedSymbols(run.getFailedSymbols() + 1);
        if (qualified) run.setQualifiedSymbols(run.getQualifiedSymbols() + 1);
        runRepository.save(run);
    }

    private boolean isCancelled(UUID runId) {
        AtomicBoolean value = cancellations.get(runId);
        return value != null && value.get();
    }

    private void publish(UUID runId, String eventType, String symbol, String level, String message) {
        CandidateScanRunEntity run = getRun(runId);
        if (run == null) return;
        ScanLogEvent event = new ScanLogEvent(eventType, runId, symbol, level, message,
            run.getCompletedSymbols(), run.getTotalSymbols(), run.getFailedSymbols(),
            run.getQualifiedSymbols(), LocalDateTime.now(MARKET_ZONE));
        Deque<ScanLogEvent> history = logHistory.computeIfAbsent(runId, ignored -> new ConcurrentLinkedDeque<>());
        history.addLast(event);
        while (history.size() > MAX_LOG_HISTORY) history.pollFirst();
        for (SseEmitter emitter : emitters.getOrDefault(runId, List.of())) send(emitter, event);
    }

    private void send(SseEmitter emitter, ScanLogEvent event) {
        try {
            emitter.send(SseEmitter.event().name("log").data(event));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void completeStreams(UUID runId) {
        List<SseEmitter> runEmitters = emitters.remove(runId);
        if (runEmitters != null) runEmitters.forEach(SseEmitter::complete);
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
