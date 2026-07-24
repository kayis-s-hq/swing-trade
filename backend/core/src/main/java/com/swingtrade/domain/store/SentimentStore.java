package com.swingtrade.domain.store;

import com.swingtrade.domain.SentimentResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SentimentStore {

    List<SentimentResult> findBySymbol(String symbol);

    List<SentimentResult> findAllBySymbolOrderByDateDesc(String symbol);

    Optional<SentimentResult> findBySymbolAndDate(String symbol, LocalDate date);

    SentimentResult save(SentimentResult result);

    List<SentimentResult> findAllByDateBetween(LocalDate start, LocalDate end);

    long countByDateBetweenAndScore(LocalDate start, LocalDate end, SentimentResult.SentimentScore score);

    List<String> findAllDistinctSymbols();

    Optional<SentimentResult> findLatestBySymbol(String symbol);

    List<SentimentResult> findAll();

    List<SentimentResult> findAllByDateBeforeOrderByDateAsc(LocalDate before);
}