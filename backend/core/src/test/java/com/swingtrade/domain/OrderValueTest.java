package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValueTest {
    @Test
    void defaultOrderHasPendingStatusAndCanRoundTripAllProperties() {
        Order order = new Order("o-1", "TCS", OrderType.LIMIT, TradeDirection.LONG,
            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(1.1), BigDecimal.valueOf(.9));
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 15, 9, 0);
        LocalDateTime execution = timestamp.plusSeconds(2);
        Map<String, Object> properties = Map.of("source", "test");

        order.setTimestamp(timestamp);
        order.setExecutionTime(execution);
        order.setCommission(BigDecimal.valueOf(2));
        order.setAdditionalProperties(properties);
        order.setStatus(OrderStatus.FILLED);

        assertThat(order.getOrderId()).isEqualTo("o-1");
        assertThat(order.getSymbol()).isEqualTo("TCS");
        assertThat(order.getType()).isEqualTo(OrderType.LIMIT);
        assertThat(order.getDirection()).isEqualTo(TradeDirection.LONG);
        assertThat(order.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(order.getPrice()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(order.getLimitPrice()).isEqualByComparingTo("1.1");
        assertThat(order.getStopPrice()).isEqualByComparingTo("0.9");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getTimestamp()).isEqualTo(timestamp);
        assertThat(order.getExecutionTime()).isEqualTo(execution);
        assertThat(order.getCommission()).isEqualByComparingTo("2");
        assertThat(order.getAdditionalProperties()).containsEntry("source", "test");
    }
}
