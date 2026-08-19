package com.swingtrade.domain;

import java.util.List;

public record SynthesisResult(
    String narrative,
    String recommendation,
    double confidence,
    List<String> keyDrivers,
    List<String> bullishFactors,
    List<String> bearishFactors,
    boolean success
) {
    public SynthesisResult(String narrative, String recommendation, double confidence,
                           List<String> keyDrivers, List<String> bullishFactors, List<String> bearishFactors) {
        this(narrative, recommendation, confidence, keyDrivers, bullishFactors, bearishFactors, false);
    }
}