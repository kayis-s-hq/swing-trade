package com.swingtrade.domain.policy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RelativeStrengthAssessment;

import java.util.List;

/** Contract for an opt-in policy that compares a stock with a broad-market index. */
public interface RelativeStrengthPolicy {

    RelativeStrengthAssessment assess(List<OhlcvCandle> stockCandles, List<OhlcvCandle> indexCandles);
}
