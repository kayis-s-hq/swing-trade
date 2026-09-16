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
        List<GateEffectivenessAuditEntity> audits = symbol == null
            ? repository.findByGateNameAndSignalDateBetweenOrderBySignalDateAsc(SENTIMENT_GATE, from, to)
            : repository.findByGateNameAndSymbolAndSignalDateBetweenOrderBySignalDateAsc(
                SENTIMENT_GATE, symbol, from, to);

        Map<String, MutableBucket> buckets = new LinkedHashMap<>();
        for (GateEffectivenessAuditEntity audit : audits) {
            MutableBucket bucket = buckets.computeIfAbsent(audit.getVerdict(), ignored -> new MutableBucket());
            bucket.count++;
            for (int horizon : new int[]{1, 5, 20}) {
                forwardReturn(audit.getSymbol(), audit.getSignalDate(), horizon).ifPresent(value -> {
                    bucket.returnSums.merge(horizon, value, BigDecimal::add);
                    bucket.returnCounts.merge(horizon, 1, Integer::sum);
                });
            }
        }
        Map<String, VerdictSummary> summaries = new LinkedHashMap<>();
        buckets.forEach((verdict, bucket) -> summaries.put(verdict, bucket.summary()));
        return new EffectivenessReport(from, to, symbol, audits.size(), summaries);
    }

    private java.util.Optional<BigDecimal> forwardReturn(String symbol, LocalDate date, int horizon) {
        var base = candleStore.findBySymbolAndDate(symbol, date);
        if (base.isEmpty() || base.get().close() == null) return java.util.Optional.empty();
        var future = candleStore.findNthBySymbolAndDateAfterOrderByDateAsc(symbol, date, horizon);
        if (future.isEmpty() || future.get().close() == null) return java.util.Optional.empty();
        BigDecimal start = base.get().close();
        return java.util.Optional.of(future.get().close().subtract(start)
            .divide(start, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
    }

    public record EffectivenessReport(LocalDate from, LocalDate to, String symbol, int auditCount,
                                      Map<String, VerdictSummary> verdicts) { }
    public record VerdictSummary(int count, Map<Integer, BigDecimal> meanForwardReturnPct) { }

    private static final class MutableBucket {
        int count;
        Map<Integer, BigDecimal> returnSums = new LinkedHashMap<>();
        Map<Integer, Integer> returnCounts = new LinkedHashMap<>();
        VerdictSummary summary() {
            Map<Integer, BigDecimal> means = new LinkedHashMap<>();
            returnSums.forEach((horizon, sum) -> means.put(horizon,
                sum.divide(BigDecimal.valueOf(returnCounts.get(horizon)), 4, RoundingMode.HALF_UP)));
            return new VerdictSummary(count, means);
        }
    }
}
