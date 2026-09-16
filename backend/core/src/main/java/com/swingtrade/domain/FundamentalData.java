package com.swingtrade.domain;

import java.math.BigDecimal;

/** Company fundamentals used by analysis, independent of price history. */
public record FundamentalData(String symbol, Long marketCap, BigDecimal peRatio) {
}
