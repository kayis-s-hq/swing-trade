package com.swingtrade.domain.store;

import com.swingtrade.domain.SentimentAccuracy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SentimentAccuracyStore {

    boolean existsBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate);

    Optional<SentimentAccuracy> findBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate);

    List<SentimentAccuracy> findBySymbolOrderByAnalysisDateDesc(String symbol);

    SentimentAccuracy save(SentimentAccuracy accuracy);

    long countAll();

    long countCorrect();
}