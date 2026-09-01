package com.swingtrade.broker.scheduler;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PendingOrderExecutionSchedulerTest {

    @Test
    void executesEligibleLimitOrderAtLatestClose() {
        PaperTradingEngine engine = mock(PaperTradingEngine.class);
        OhlcvCandleRepository candles = mock(OhlcvCandleRepository.class);
        Order order = new Order("ORD-1", "TCS", OrderType.LIMIT, TradeDirection.LONG,
            BigDecimal.ONE, null, BigDecimal.valueOf(100), null);
        OhlcvCandleEntity candle = mock(OhlcvCandleEntity.class);
        when(engine.getPendingOrders()).thenReturn(List.of(order));
        when(candles.findLatestBySymbol("TCS")).thenReturn(Optional.of(candle));
        when(candle.getClosePrice()).thenReturn(BigDecimal.valueOf(95));

        new PendingOrderExecutionScheduler(engine, candles).executePendingOrders();

        verify(engine).executePendingOrder("ORD-1", BigDecimal.valueOf(95));
    }

    @Test
    void leavesUnfilledLimitOrderPending() {
        PaperTradingEngine engine = mock(PaperTradingEngine.class);
        OhlcvCandleRepository candles = mock(OhlcvCandleRepository.class);
        Order order = new Order("ORD-1", "TCS", OrderType.LIMIT, TradeDirection.LONG,
            BigDecimal.ONE, null, BigDecimal.valueOf(100), null);
        OhlcvCandleEntity candle = mock(OhlcvCandleEntity.class);
        when(engine.getPendingOrders()).thenReturn(List.of(order));
        when(candles.findLatestBySymbol("TCS")).thenReturn(Optional.of(candle));
        when(candle.getClosePrice()).thenReturn(BigDecimal.valueOf(105));

        new PendingOrderExecutionScheduler(engine, candles).executePendingOrders();

        verify(engine, never()).executePendingOrder(anyString(), any());
    }

    @Test
    void hasNineFifteenWeekdaySchedule() throws NoSuchMethodException {
        Scheduled scheduled = PendingOrderExecutionScheduler.class
            .getMethod("executePendingOrders").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("${paper.trading.pending-order-cron:0 15 9 * * MON-FRI}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Kolkata");
    }
}
