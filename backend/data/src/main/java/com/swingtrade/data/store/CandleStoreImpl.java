package com.swingtrade.data.store;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.store.CandleStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class CandleStoreImpl implements CandleStore {

    private final OhlcvCandleRepository repository;

    public CandleStoreImpl(OhlcvCandleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OhlcvCandle> findBySymbolAndDate(String symbol, LocalDate date) {
        return repository.findAllBySymbolOrderByDateDesc(symbol).stream()
            .filter(c -> c.getDate().equals(date))
            .findFirst()
            .map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public List<OhlcvCandle> findBySymbol(String symbol) {
        return repository.findAllBySymbolOrderByDateDesc(symbol).stream()
            .map(OhlcvCandleEntity::toDomain)
            .toList();
    }

    @Override
    public List<OhlcvCandle> findBySymbolAndDateRange(String symbol, LocalDate from, LocalDate to) {
        return repository.findBySymbolAndDateRange(symbol, from, to, PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(OhlcvCandleEntity::toDomain)
            .toList();
    }

    @Override
    public List<OhlcvCandle> findTopBySymbolOrderByDateDesc(String symbol, int limit) {
        return repository.findTopBySymbolOrderByDateDesc(symbol, PageRequest.of(0, limit)).stream()
            .map(OhlcvCandleEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<OhlcvCandle> findLatestBySymbol(String symbol) {
        return repository.findLatestBySymbol(symbol).map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public void save(OhlcvCandle candle) {
        repository.save(OhlcvCandleEntity.fromDomain(candle));
    }

    @Override
    public boolean existsBySymbolAndDate(String symbol, LocalDate date) {
        return repository.existsBySymbolAndDate(symbol, date);
    }

    @Override
    public List<String> findAllDistinctSymbols() {
        return repository.findAllDistinctSymbols();
    }

    @Override
    public Optional<OhlcvCandle> findEarliestBySymbol(String symbol) {
        return repository.findEarliestBySymbol(symbol).map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public long countBySymbol(String symbol) {
        return repository.countBySymbol(symbol);
    }

    @Override
    public void deleteBySymbol(String symbol) {
        repository.deleteBySymbol(symbol);
    }

    @Override
    public Optional<OhlcvCandle> findLatestBySymbolBeforeDate(String symbol, LocalDate date) {
        return repository.findLatestBySymbolBeforeDate(symbol, date.atStartOfDay())
            .map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public Optional<OhlcvCandle> findFirstBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate date) {
        return repository.findFirstBySymbolAndDateAfterOrderByDateAsc(symbol, date)
            .map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public Optional<OhlcvCandle> findNthBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate after, int n) {
        return repository.findNthBySymbolAndDateAfterOrderByDateAsc(symbol, after, n)
            .map(OhlcvCandleEntity::toDomain);
    }

    @Override
    public List<OhlcvCandle> findAllBySymbolOrderByDateDesc(String symbol) {
        return repository.findAllBySymbolOrderByDateDesc(symbol).stream()
            .map(OhlcvCandleEntity::toDomain)
            .toList();
    }

    @Override
    public List<OhlcvCandle> findLastNBySymbolBeforeDateAsc(String symbol, LocalDate before, int n) {
        return repository.findLastNBySymbolBeforeDateAsc(symbol, before, n).stream()
            .map(OhlcvCandleEntity::toDomain)
            .toList();
    }
}