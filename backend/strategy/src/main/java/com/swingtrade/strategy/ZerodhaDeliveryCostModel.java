package com.swingtrade.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conservative delivery-equity cost model for Indian cash-market backtests.
 * Rates are expressed as fractions of turnover and intentionally kept explicit
 * so a broker-specific model can replace this default later.
 */
public final class ZerodhaDeliveryCostModel implements BacktestCostModel {

    private static final BigDecimal STT_RATE = new BigDecimal("0.001");
    private static final BigDecimal EXCHANGE_RATE = new BigDecimal("0.0000297");
    private static final BigDecimal SEBI_RATE = new BigDecimal("0.000001");
    private static final BigDecimal STAMP_RATE = new BigDecimal("0.00015");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");
    private static final BigDecimal DP_CHARGE = new BigDecimal("15.93");

    @Override
    public BigDecimal roundTripCost(BigDecimal entryPrice, BigDecimal exitPrice, int quantity,
                                    BigDecimal brokerage) {
        BigDecimal buyTurnover = entryPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal sellTurnover = exitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal exchangeAndRegulatory = buyTurnover.add(sellTurnover)
            .multiply(EXCHANGE_RATE.add(SEBI_RATE));
        BigDecimal stt = buyTurnover.add(sellTurnover).multiply(STT_RATE);
        BigDecimal stamp = buyTurnover.multiply(STAMP_RATE);
        BigDecimal gst = brokerage.add(exchangeAndRegulatory).multiply(GST_RATE);
        return brokerage.add(stt).add(exchangeAndRegulatory).add(stamp).add(gst).add(DP_CHARGE)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
