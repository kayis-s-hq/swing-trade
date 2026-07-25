package com.swingtrade.api.dto;

import java.util.List;

public record SynthesisResult(
    String narrative,
    String recommendation,
    double confidence,
    List<String> keyDrivers,
    List<String> bullishFactors,
    List<String> bearishFactors
) {}