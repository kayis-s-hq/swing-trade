package com.swingtrade.data.store;

import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.SignalStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
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
    public Signal save(Signal signal) {
        return repository.save(SignalEntity.fromDomain(signal)).toDomain();
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
        return repository.findLatestBySymbol(symbol).map(SignalEntity::toDomain);
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
}