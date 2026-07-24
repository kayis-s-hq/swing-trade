package com.swingtrade.data.store;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import com.swingtrade.domain.SentimentAccuracy;
import com.swingtrade.domain.store.SentimentAccuracyStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SentimentAccuracyStoreImpl implements SentimentAccuracyStore {

    private final SentimentAccuracyRepository repository;

    public SentimentAccuracyStoreImpl(SentimentAccuracyRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate) {
        return repository.existsBySymbolAndAnalysisDate(symbol, analysisDate);
    }

    @Override
    public Optional<SentimentAccuracy> findBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate) {
        return repository.findBySymbolAndAnalysisDate(symbol, analysisDate).map(SentimentAccuracyEntity::toDomain);
    }

    @Override
    public List<SentimentAccuracy> findBySymbolOrderByAnalysisDateDesc(String symbol) {
        return repository.findBySymbolOrderByAnalysisDateDesc(symbol).stream()
            .map(SentimentAccuracyEntity::toDomain)
            .toList();
    }

    @Override
    public SentimentAccuracy save(SentimentAccuracy accuracy) {
        return repository.save(SentimentAccuracyEntity.fromDomain(accuracy)).toDomain();
    }

    @Override
    public long countAll() {
        return repository.countAll();
    }

    @Override
    public long countCorrect() {
        return repository.countCorrect();
    }
}