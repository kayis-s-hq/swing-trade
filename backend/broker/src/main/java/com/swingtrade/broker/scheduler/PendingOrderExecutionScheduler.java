package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Fills eligible paper orders at the first available market-data price. */
@Service
public class PendingOrderExecutionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PendingOrderExecutionScheduler.class);
    private final PaperTradingEngine engine;
    private final OhlcvCandleRepository candleRepository;

    public PendingOrderExecutionScheduler(PaperTradingEngine engine, OhlcvCandleRepository candleRepository) {
        this.engine = engine;
        this.candleRepository = candleRepository;
    }

    @Scheduled(cron = "${paper.trading.pending-order-cron:0 15 9 * * MON-FRI}", zone = "Asia/Kolkata")
    public void executePendingOrders() {
        engine.getPendingOrders().forEach(order -> {
            candleRepository.findLatestBySymbol(order.getSymbol()).ifPresent(candle -> {
                BigDecimal price = candle.getClosePrice();
                if (isEligible(order, price)) {
                    engine.executePendingOrder(order.getOrderId(), price);
                    logger.info("Executed pending {} order {} for {} at {}",
                        order.getType(), order.getOrderId(), order.getSymbol(), price);
                }
            });
        });
    }

    private boolean isEligible(Order order, BigDecimal price) {
        if (order.getType() == OrderType.MARKET) return true;
        if (order.getType() != OrderType.LIMIT || order.getLimitPrice() == null) return false;
        return order.getDirection() == TradeDirection.LONG
            ? price.compareTo(order.getLimitPrice()) <= 0
            : price.compareTo(order.getLimitPrice()) >= 0;
    }
}
