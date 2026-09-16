package com.swingtrade.domain.policy;

import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.OhlcvCandle;

import java.util.List;

/** Contract for an opt-in policy that gates entries using broad-market candles. */
public interface MarketRegimePolicy {

    MarketRegimeAssessment assess(List<OhlcvCandle> indexCandles);
}
