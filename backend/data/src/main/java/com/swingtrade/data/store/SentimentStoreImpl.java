package com.swingtrade.data.store;

import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.store.SentimentStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SentimentStoreImpl implements SentimentStore {

    private final SentimentResultRepository repository;

    public SentimentStoreImpl(SentimentResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SentimentResult> findBySymbol(String symbol) {
        return repository.findAllBySymbol(symbol, PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SentimentResultEntity::toDomain)
            .toList();
    }

    @Override
    public List<SentimentResult> findAllBySymbolOrderByDateDesc(String symbol) {
        return repository.findAllBySymbol(symbol, PageRequest.of(0, Integer.MAX_VALUE)).stream()
            .map(SentimentResultEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<SentimentResult> findBySymbolAndDate(String symbol, LocalDate date) {
        return repository.findBySymbolAndDate(symbol, date).map(SentimentResultEntity::toDomain);
    }

    @Override
    public SentimentResult save(SentimentResult result) {
        return repository.save(SentimentResultEntity.fromDomain(result)).toDomain();
    }

    @Override
    public List<SentimentResult> findAllByDateBetween(LocalDate start, LocalDate end) {
        return repository.findAllByDateBetween(start, end).stream()
            .map(SentimentResultEntity::toDomain)
            .toList();
    }

    @Override
    public long countByDateBetweenAndScore(LocalDate start, LocalDate end, SentimentResult.SentimentScore score) {
        return repository.countByDateBetweenAndSentimentScore(start, end, score.name());
    }

    @Override
    public List<String> findAllDistinctSymbols() {
        return repository.findAllDistinctSymbols();
    }

    @Override
    public Optional<SentimentResult> findLatestBySymbol(String symbol) {
        return repository.findLatestBySymbol(symbol).map(SentimentResultEntity::toDomain);
    }

    @Override
    public List<SentimentResult> findAllByDateBeforeOrderByDateAsc(LocalDate before) {
        return repository.findAllByDateBeforeOrderByDateAsc(before).stream()
            .map(SentimentResultEntity::toDomain)
            .toList();
    }

    @Override
    public List<SentimentResult> findAll() {
        return repository.findAll().stream()
            .map(SentimentResultEntity::toDomain)
            .toList();
    }
}