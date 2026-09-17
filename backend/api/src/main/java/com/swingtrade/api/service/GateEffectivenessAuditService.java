package com.swingtrade.api.service;

import com.swingtrade.data.entity.GateEffectivenessAuditEntity;
import com.swingtrade.data.repository.GateEffectivenessAuditRepository;
import com.swingtrade.domain.store.CandleStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GateEffectivenessAuditService {
    public static final String SENTIMENT_GATE = "SENTIMENT";
    public static final String DEFAULT_STRATEGY = "DEFAULT";
    public static final String UNKNOWN_DIMENSION = "UNKNOWN";

    private final GateEffectivenessAuditRepository repository;
    private final CandleStore candleStore;

    public GateEffectivenessAuditService(GateEffectivenessAuditRepository repository, CandleStore candleStore) {
        this.repository = repository;
        this.candleStore = candleStore;
    }

    @Transactional
    public void recordSentimentVerdict(String symbol, LocalDate signalDate,
                                       SentimentGate.SentimentVerdict verdict) {
        if (verdict.action() == SentimentGate.SentimentVerdict.Action.PENDING) return;
        GateEffectivenessAuditEntity entity = repository
            .findBySymbolAndSignalDateAndGateName(symbol, signalDate, SENTIMENT_GATE)
            .orElseGet(() -> new GateEffectivenessAuditEntity(symbol, signalDate, SENTIMENT_GATE,
                verdict.action().name(), verdict.reason(), OffsetDateTime.now()));
        // Keep the first decision: overwriting it on a retry would bias the audit.
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public EffectivenessReport report(LocalDate from, LocalDate to, String symbol) {
        return report(from, to, symbol, null, null);
    }

    @Transactional(readOnly = true)
    public EffectivenessReport report(LocalDate from, LocalDate to, String symbol,
                                     String strategy, String regime) {
        List<GateEffectivenessAuditEntity> audits = symbol == null
            ? repository.findByGateNameAndSignalDateBetweenOrderBySignalDateAsc(SENTIMENT_GATE, from, to)
            : repository.findByGateNameAndSymbolAndSignalDateBetweenOrderBySignalDateAsc(
                SENTIMENT_GATE, symbol, from, to);

        Map<String, MutableBucket> buckets = new LinkedHashMap<>();
        Map<String, MutableBucket> strategyBuckets = new LinkedHashMap<>();
        Map<String, MutableBucket> regimeBuckets = new LinkedHashMap<>();
        for (GateEffectivenessAuditEntity audit : audits) {
            String auditStrategy = DEFAULT_STRATEGY;
            String auditRegime = marketRegime(audit.getSymbol(), audit.getSignalDate());
            if (strategy != null && !strategy.equalsIgnoreCase(auditStrategy)) continue;
            if (regime != null && !regime.equalsIgnoreCase(auditRegime)) continue;

            MutableBucket bucket = buckets.computeIfAbsent(audit.getVerdict(), ignored -> new MutableBucket());
            addOutcome(bucket, audit);
            addOutcome(strategyBuckets.computeIfAbsent(auditStrategy, ignored -> new MutableBucket()), audit);
            addOutcome(regimeBuckets.computeIfAbsent(auditRegime, ignored -> new MutableBucket()), audit);
        }
        Map<String, VerdictSummary> summaries = new LinkedHashMap<>();
        buckets.forEach((verdict, bucket) -> summaries.put(verdict, bucket.summary()));
        return new EffectivenessReport(from, to, symbol, strategy, regime, totalCount(buckets), summaries,
            summarize(strategyBuckets), summarize(regimeBuckets));
    }

    private void addOutcome(MutableBucket bucket, GateEffectivenessAuditEntity audit) {
        bucket.count++;
        for (int horizon : new int[]{1, 5, 20}) {
            forwardReturn(audit.getSymbol(), audit.getSignalDate(), horizon).ifPresent(value -> {
                bucket.returnSums.merge(horizon, value, BigDecimal::add);
                bucket.returnCounts.merge(horizon, 1, Integer::sum);
            });
        }
    }

    private String marketRegime(String symbol, LocalDate date) {
        List<com.swingtrade.domain.OhlcvCandle> candles = candleStore
            .findLastNBySymbolBeforeDateAsc(symbol, date, 20);
        if (candles.size() < 2) return UNKNOWN_DIMENSION;
        BigDecimal first = candles.get(0).close();
        BigDecimal last = candles.get(candles.size() - 1).close();
        if (first == null || last == null || first.signum() <= 0 || last.signum() <= 0) {
            return UNKNOWN_DIMENSION;
        }
        BigDecimal changePct = last.subtract(first)
            .divide(first, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        if (changePct.compareTo(BigDecimal.valueOf(2)) >= 0) return "BULL";
        if (changePct.compareTo(BigDecimal.valueOf(-2)) <= 0) return "BEAR";
        return "SIDEWAYS";
    }

    private static int totalCount(Map<String, MutableBucket> buckets) {
        return buckets.values().stream().mapToInt(bucket -> bucket.count).sum();
    }

    private static Map<String, VerdictSummary> summarize(Map<String, MutableBucket> buckets) {
        Map<String, VerdictSummary> result = new LinkedHashMap<>();
        buckets.forEach((dimension, bucket) -> result.put(dimension, bucket.summary()));
        return result;
    }

    private java.util.Optional<BigDecimal> forwardReturn(String symbol, LocalDate date, int horizon) {
        var base = candleStore.findBySymbolAndDate(symbol, date);
        if (base.isEmpty() || base.get().close() == null || base.get().close().signum() <= 0) {
            return java.util.Optional.empty();
        }
        var future = candleStore.findNthBySymbolAndDateAfterOrderByDateAsc(symbol, date, horizon);
        if (future.isEmpty() || future.get().close() == null || future.get().close().signum() <= 0) {
            return java.util.Optional.empty();
        }
        BigDecimal start = base.get().close();
        return java.util.Optional.of(future.get().close().subtract(start)
            .divide(start, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
    }

    public record EffectivenessReport(LocalDate from, LocalDate to, String symbol,
                                      String strategy, String regime, int auditCount,
                                      Map<String, VerdictSummary> verdicts,
                                      Map<String, VerdictSummary> byStrategy,
                                      Map<String, VerdictSummary> byRegime) {
        public EffectivenessReport(LocalDate from, LocalDate to, String symbol, int auditCount,
                                    Map<String, VerdictSummary> verdicts) {
            this(from, to, symbol, null, null, auditCount, verdicts, Map.of(), Map.of());
        }
    }

    public record VerdictSummary(int count, Map<Integer, BigDecimal> meanForwardReturnPct,
                                 Map<Integer, Integer> returnObservationCounts) {
        public VerdictSummary(int count, Map<Integer, BigDecimal> meanForwardReturnPct) {
            this(count, meanForwardReturnPct, Map.of());
        }
    }

    private static final class MutableBucket {
        int count;
        Map<Integer, BigDecimal> returnSums = new LinkedHashMap<>();
        Map<Integer, Integer> returnCounts = new LinkedHashMap<>();
        VerdictSummary summary() {
            Map<Integer, BigDecimal> means = new LinkedHashMap<>();
            returnSums.forEach((horizon, sum) -> means.put(horizon,
                sum.divide(BigDecimal.valueOf(returnCounts.get(horizon)), 4, RoundingMode.HALF_UP)));
            return new VerdictSummary(count, means, new LinkedHashMap<>(returnCounts));
        }
    }
}
