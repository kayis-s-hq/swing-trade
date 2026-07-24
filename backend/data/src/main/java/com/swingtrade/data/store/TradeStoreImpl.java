package com.swingtrade.data.store;

import com.swingtrade.data.entity.TradeEntity;
import com.swingtrade.data.repository.TradeRepository;
import com.swingtrade.domain.Trade;
import com.swingtrade.domain.store.TradeStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TradeStoreImpl implements TradeStore {

    private final TradeRepository repository;

    public TradeStoreImpl(TradeRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Trade> findBySymbol(String symbol) {
        return repository.findBySymbol(symbol).stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findByEntryDateBetween(LocalDate start, LocalDate end) {
        return repository.findByEntryDateBetween(start, end).stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findByExitDateBetween(LocalDate start, LocalDate end) {
        return repository.findByExitDateBetween(start, end).stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findByStatus(Trade.TradeStatus status) {
        return repository.findByStatus(status.name()).stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findAllOpen() {
        return repository.findAllOpenTrades().stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findAllClosed() {
        return repository.findAllClosedTrades().stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public List<Trade> findAll() {
        return repository.findAll().stream()
            .map(TradeEntity::toDomain)
            .toList();
    }

    @Override
    public Trade save(Trade trade) {
        return repository.save(TradeEntity.fromDomain(trade)).toDomain();
    }

    @Override
    public long countBySymbol(String symbol) {
        return repository.countBySymbol(symbol);
    }

    @Override
    public long countOpenTrades() {
        return repository.countOpenTrades();
    }
}