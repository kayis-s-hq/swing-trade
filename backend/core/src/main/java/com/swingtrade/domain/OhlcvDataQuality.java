package com.swingtrade.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, read-only quality checks for candles used by analytical consumers.
 * Raw candles are never changed; callers receive the candles that are safe to use
 * and a bounded list of the candles quarantined from that analytical input.
 */
public final class OhlcvDataQuality {

    private OhlcvDataQuality() {
    }

    public record Assessment(List<OhlcvCandle> accepted, List<QuarantinedCandle> quarantined) {
        public Assessment {
            accepted = List.copyOf(accepted);
            quarantined = List.copyOf(quarantined);
        }
    }

    public record QuarantinedCandle(OhlcvCandle candle, String reason) {
    }

    /**
     * Removes candles whose adjusted opening price is an unexplained large jump from
     * the previous adjusted close. The first candle is always retained. Missing or
     * non-positive open/close values are quarantined as well. A candle is compared only with the last accepted
     * candle, preventing one bad row from poisoning the rest of the series.
     */
    public static Assessment quarantineUnexplainedGaps(List<OhlcvCandle> chronologicalCandles,
                                                        BigDecimal maximumGapRatio) {
        if (chronologicalCandles == null) {
            throw new IllegalArgumentException("Candles cannot be null");
        }
        if (maximumGapRatio == null || maximumGapRatio.signum() < 0) {
            throw new IllegalArgumentException("Maximum gap ratio cannot be null or negative");
        }

        List<OhlcvCandle> accepted = new ArrayList<>();
        List<QuarantinedCandle> quarantined = new ArrayList<>();
        OhlcvCandle previous = null;
        for (OhlcvCandle candle : chronologicalCandles) {
            if (candle == null || candle.date() == null || candle.open() == null
                    || candle.close() == null || candle.open().signum() <= 0 || candle.close().signum() <= 0) {
                quarantined.add(new QuarantinedCandle(candle, "invalid prices"));
                continue;
            }
            OhlcvCandle analytical = candle.adjustedForAnalysis();
            if (previous != null) {
                BigDecimal gapRatio = analytical.open().subtract(previous.close()).abs()
                    .divide(previous.close(), 12, RoundingMode.HALF_UP);
                if (gapRatio.compareTo(maximumGapRatio) > 0) {
                    quarantined.add(new QuarantinedCandle(candle,
                        "adjusted open/previous close gap ratio " + gapRatio));
                    continue;
                }
            }
            accepted.add(candle);
            previous = candle.adjustedForAnalysis();
        }
        return new Assessment(accepted, quarantined);
    }

}
