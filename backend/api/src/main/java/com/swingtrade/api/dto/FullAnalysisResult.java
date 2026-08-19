package com.swingtrade.api.dto;

import com.swingtrade.domain.CompositeAnalysis;

import java.util.List;

public record FullAnalysisResult(
    CompositeAnalysis composite,
    List<AnalysisProgress> progress,
    long durationMs,
    String symbol
) {}