package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegime;
import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.policy.MarketRegimePolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded index trend policy. It is deliberately not wired into live signal generation;
 * callers opt in by invoking this policy.
 */
@Component
public final class BoundedMarketRegimePolicy implements MarketRegimePolicy {

    public static final int LOOKBACK_DAYS = 200;

    @Override
    public MarketRegimeAssessment assess(List<OhlcvCandle> indexCandles) {
        List<OhlcvCandle> candles = validChronological(indexCandles);
        if (candles.size() < LOOKBACK_DAYS) {
            return MarketRegimeAssessment.unavailable("INDEX_DATA_UNAVAILABLE");
        }

        OhlcvCandle latest = candles.get(candles.size() - 1);
        BigDecimal latestClose = analysisClose(latest);
        BigDecimal longAverage = candles.subList(candles.size() - LOOKBACK_DAYS, candles.size()).stream()
                .map(this::analysisClose)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(LOOKBACK_DAYS), 8, java.math.RoundingMode.HALF_UP);
        boolean bullish = latestClose.compareTo(longAverage) > 0;
        return new MarketRegimeAssessment(
                bullish ? MarketRegime.BULLISH : MarketRegime.BEARISH,
                bullish,
                latest.date(),
                bullish ? "INDEX_ABOVE_200_DAY_AVERAGE" : "INDEX_BELOW_200_DAY_AVERAGE");
    }

    private List<OhlcvCandle> validChronological(List<OhlcvCandle> candles) {
        if (candles == null) {
            return List.of();
        }
        return candles.stream()
                .filter(c -> c != null && c.date() != null && analysisClose(c) != null)
                .sorted(Comparator.comparing(OhlcvCandle::date))
                .toList();
    }

    private BigDecimal analysisClose(OhlcvCandle candle) {
        if (candle == null || candle.close() == null || candle.close().signum() <= 0) {
            return null;
        }
        BigDecimal adjusted = candle.adjustedForAnalysis().close();
        return adjusted != null && adjusted.signum() > 0 ? adjusted : null;
    }
}
