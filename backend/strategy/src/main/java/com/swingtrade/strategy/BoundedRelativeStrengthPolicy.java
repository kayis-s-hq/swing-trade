package com.swingtrade.strategy;

import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.RelativeStrengthAssessment;
import com.swingtrade.domain.policy.RelativeStrengthPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Compares equal bounded lookback returns using adjusted analytical closes.
 * Index data is mandatory: missing or insufficient index data is ineligible.
 */
public final class BoundedRelativeStrengthPolicy implements RelativeStrengthPolicy {

    public static final int LOOKBACK_DAYS = 63;

    @Override
    public RelativeStrengthAssessment assess(List<OhlcvCandle> stockCandles, List<OhlcvCandle> indexCandles) {
        List<OhlcvCandle> stock = validChronological(stockCandles);
        List<OhlcvCandle> index = validChronological(indexCandles);
        if (stock.size() < LOOKBACK_DAYS || index.size() < LOOKBACK_DAYS) {
            return RelativeStrengthAssessment.unavailable("INDEX_DATA_UNAVAILABLE");
        }

        OhlcvCandle stockLast = stock.get(stock.size() - 1);
        OhlcvCandle indexLast = index.get(index.size() - 1);
        BigDecimal stockReturn = returnOverLookback(stock);
        BigDecimal indexReturn = returnOverLookback(index);
        BigDecimal excess = stockReturn.subtract(indexReturn);
        boolean eligible = excess.signum() > 0;
        return new RelativeStrengthAssessment(eligible,
                pct(stockReturn), pct(indexReturn), pct(excess),
                minDate(stockLast.date(), indexLast.date()),
                eligible ? "STOCK_OUTPERFORMING_INDEX" : "STOCK_NOT_OUTPERFORMING_INDEX");
    }

    private BigDecimal returnOverLookback(List<OhlcvCandle> candles) {
        BigDecimal start = analysisClose(candles.get(candles.size() - LOOKBACK_DAYS));
        BigDecimal end = analysisClose(candles.get(candles.size() - 1));
        return end.subtract(start).divide(start, 12, RoundingMode.HALF_UP);
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

    private double pct(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private LocalDate minDate(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
