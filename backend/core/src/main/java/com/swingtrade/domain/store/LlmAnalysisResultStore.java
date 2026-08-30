package com.swingtrade.domain.store;

import com.swingtrade.domain.LlmAnalysisResult;
import java.time.LocalDate;
import java.util.Optional;

public interface LlmAnalysisResultStore {
    Optional<LlmAnalysisResult> findBySymbolAndDate(String symbol, LocalDate date);
    LlmAnalysisResult save(LlmAnalysisResult result);
    LlmAnalysisResult saveOrUpdate(LlmAnalysisResult result);
}
