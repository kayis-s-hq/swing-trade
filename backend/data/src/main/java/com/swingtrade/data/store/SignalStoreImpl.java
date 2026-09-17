package com.swingtrade.data.store;

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.SignalStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SignalStoreImpl implements SignalStore {

    private final SignalRepository repository;

    public SignalStoreImpl(SignalRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Signal> findAll() {
        return repository.findAll().stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findBySymbol(String symbol) {
        return repository.findBySymbolOrderByDateDesc(symbol, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findBySymbolOrderByDateDesc(String symbol) {
        return repository.findBySymbolOrderByDateDesc(symbol, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findBySymbolAndDate(String symbol, LocalDate date) {
        return repository.findBySymbolAndDate(symbol, date).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findByType(Signal.SignalType type) {
        return repository.findByDateRangeAndSignalType(
                LocalDate.MIN, LocalDate.MAX, type.name(), org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findUnprocessed() {
        return repository.findUnprocessedBuySignalsSince(LocalDate.now().minusDays(30)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findUnprocessedByStrategy(String strategy) {
        return repository.findUnprocessedBuySignalsSinceAndStrategy(LocalDate.now().minusDays(30), strategy).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public int markProcessedExcludingStrategy(String symbol, String strategy) {
        return repository.markProcessedExcludingStrategy(symbol, strategy);
    }

    @Override
    public Signal save(Signal signal) {
        return repository.save(SignalEntity.fromDomain(signal)).toDomain();
    }

    @Override
    public Signal save(Signal signal, String warningFlag) {
        return repository.save(SignalEntity.fromDomain(signal, warningFlag)).toDomain();
    }

    @Override
    public Signal save(Signal signal, String warningFlag, String strategy) {
        SignalEntity entity = SignalEntity.fromDomain(signal, warningFlag);
        entity.setStrategy(strategy);
        return repository.save(entity).toDomain();
    }

    @Override
    public Signal saveVariantSignal(Signal signal, String warningFlag, String strategy, int strategyVersion,
                                     BigDecimal strategyScore, List<Map<String, Object>> ruleOutcomes,
                                     List<Map<String, Object>> gateOutcomes) {
        SignalEntity entity = SignalEntity.fromDomain(signal, warningFlag);
        entity.setStrategy(strategy);
        entity.setStrategyVersion(strategyVersion);
        entity.setStrategyScore(strategyScore);
        entity.setRuleOutcomes(ruleOutcomes);
        entity.setGateOutcomes(gateOutcomes);
        return repository.save(entity).toDomain();
    }

    @Override
    public void markProcessed(Long signalId) {
        Optional<SignalEntity> entity = repository.findById(signalId);
        entity.ifPresent(e -> {
            e.setProcessed(true);
            repository.save(e);
        });
    }

    @Override
    public Optional<Signal> findLatestBySymbol(String symbol) {
        List<SignalEntity> results = repository.findLatestBySymbol(symbol,
            org.springframework.data.domain.PageRequest.of(0, 1));
        return results.stream().findFirst().map(SignalEntity::toDomain);
    }

    @Override
    public Optional<Signal> findLatestBySymbolAndStrategy(String symbol, String strategy) {
        return repository.findLatestBySymbolAndStrategy(symbol, strategy,
            org.springframework.data.domain.PageRequest.of(0, 1)).stream()
            .findFirst().map(SignalEntity::toDomain);
    }

    @Override
    public List<Signal> findLatestSignalPerSymbol() {
        return repository.findLatestSignalPerSymbol().stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<String> findAllDistinctSymbols() {
        return repository.findAllDistinctSymbols();
    }

    @Override
    public long countBySymbolAndDate(String symbol, LocalDate date) {
        return repository.countBySymbolAndDate(symbol, date);
    }

    @Override
    public List<Signal> findBuySignalsSince(LocalDate sinceDate) {
        return repository.findBuySignalsSince(sinceDate,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return repository.findByDateRange(
                startDate, endDate,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findByDateAndStrategy(LocalDate date, String strategy) {
        return repository.findByDateAndStrategy(date, strategy).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findByDateRangeAndType(LocalDate startDate, LocalDate endDate, Signal.SignalType type) {
        return repository.findByDateRangeAndSignalType(
                startDate, endDate, type.name(),
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    public List<Signal> findByMinConfidence(double minConfidence) {
        return repository.findByMinConfidence(
                BigDecimal.valueOf(minConfidence),
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SignalEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public int deleteBySymbolAndDate(String symbol, LocalDate date) {
        return repository.deleteBySymbolAndDate(symbol, date);
    }

    @Override
    @Transactional
    public int deleteBySymbolAndDateAndStrategy(String symbol, LocalDate date, String strategy) {
        return repository.deleteBySymbolAndDateAndStrategy(symbol, date, strategy);
    }

    @Override
    @Transactional
    public int deleteBySymbolAndDateAndStrategyAndVersion(String symbol, LocalDate date, String strategy,
                                                            int strategyVersion) {
        return repository.deleteBySymbolAndDateAndStrategyAndVersion(symbol, date, strategy, strategyVersion);
    }

    @Override
    @Transactional
    public int deleteByDate(LocalDate date) {
        return repository.deleteByDate(date);
    }

    @Override
    @Transactional
    public int deleteAllSignals() {
        return repository.deleteAllSignals();
    }
}
