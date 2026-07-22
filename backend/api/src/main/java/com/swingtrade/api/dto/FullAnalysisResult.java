package com.swingtrade.api.dto;

import java.util.List;

public record FullAnalysisResult(
    CompositeAnalysis composite,
    List<AnalysisProgress> progress,
    long durationMs,
    String symbol
) {}