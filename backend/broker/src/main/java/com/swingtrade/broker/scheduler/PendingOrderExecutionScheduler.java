package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.PriceBandPolicy;
import com.swingtrade.domain.store.PriceBandStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Fills eligible paper orders at the session open from the latest EOD candle. */
@Service
public class PendingOrderExecutionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PendingOrderExecutionScheduler.class);
    private final PaperTradingEngine engine;
    private final OhlcvCandleRepository candleRepository;
    private final PriceBandStore priceBandStore;
    @Value("${broker.slippage.percentage:0.05}")
    private double slippagePercentage;

    @org.springframework.beans.factory.annotation.Autowired
    public PendingOrderExecutionScheduler(PaperTradingEngine engine, OhlcvCandleRepository candleRepository,
                                          PriceBandStore priceBandStore) {
        this.engine = engine;
        this.candleRepository = candleRepository;
        this.priceBandStore = priceBandStore;
    }

    /** Compatibility constructor for callers that do not provide band data. */
    public PendingOrderExecutionScheduler(PaperTradingEngine engine, OhlcvCandleRepository candleRepository) {
        this(engine, candleRepository, new PriceBandStore() {
            @Override public java.util.Optional<PriceBand> findBySymbolAndDate(String symbol, java.time.LocalDate date) {
                return java.util.Optional.empty();
            }
            @Override public void save(PriceBand priceBand) {}
        });
    }

    @Scheduled(cron = "${paper.trading.pending-order-cron:0 35 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void executePendingOrders() {
        engine.getPendingOrders().forEach(order -> {
            candleRepository.findLatestBySymbol(order.getSymbol()).ifPresent(candle -> {
                BigDecimal price = candle.getOpenPrice();
                PriceBand band = priceBandStore.findBySymbolAndDate(order.getSymbol(), candle.getDate())
                    .orElse(null);
                if (isEligible(order, price) && !PriceBandPolicy.blocksLongEntry(band, price)) {
                    engine.executePendingOrder(order.getOrderId(), applySlippage(order, price));
                    logger.info("Executed pending {} order {} for {} at {}",
                        order.getType(), order.getOrderId(), order.getSymbol(), price);
                }
            });
        });
    }

    private BigDecimal applySlippage(Order order, BigDecimal openPrice) {
        if (slippagePercentage == 0.0) return openPrice;
        BigDecimal fraction = BigDecimal.valueOf(slippagePercentage / 100.0);
        return order.getDirection() == TradeDirection.LONG
            ? openPrice.multiply(BigDecimal.ONE.add(fraction))
            : openPrice.multiply(BigDecimal.ONE.subtract(fraction));
    }

    private boolean isEligible(Order order, BigDecimal price) {
        if (order.getType() == OrderType.MARKET) return true;
        if (order.getType() != OrderType.LIMIT || order.getLimitPrice() == null) return false;
        return order.getDirection() == TradeDirection.LONG
            ? price.compareTo(order.getLimitPrice()) <= 0
            : price.compareTo(order.getLimitPrice()) >= 0;
    }
}
