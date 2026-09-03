package com.swingtrade.domain;

import java.time.LocalDate;
import java.util.List;

public record LlmAnalysisResult(
    Long id, String runId, String symbol, LocalDate analysisDate, String recommendation,
    Double confidence, String narrative, List<String> keyDrivers, List<String> bullishFactors,
    List<String> bearishFactors, Integer compositeScore, String compositeSignal,
    boolean success, boolean fallbackUsed, String errorMessage) {}
