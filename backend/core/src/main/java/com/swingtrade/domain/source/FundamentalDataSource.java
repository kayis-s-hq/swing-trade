package com.swingtrade.domain.source;

import com.swingtrade.domain.FundamentalData;

import java.util.Optional;

/** Source of company fundamentals independent of market-price history. */
public interface FundamentalDataSource {

    Optional<FundamentalData> findBySymbol(String symbol);
}
