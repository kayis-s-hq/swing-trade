package com.swingtrade.api.dto;

import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TradeRequestTest {

    private TradeRequest request(String symbol, Integer quantity, TradeDirection direction, OrderType orderType) {
        TradeRequest r = new TradeRequest();
        r.setSymbol(symbol);
        r.setQuantity(quantity);
        r.setDirection(direction);
        r.setOrderType(orderType);
        return r;
    }

    @Nested
    class StopOrderValidation {

        @Test
        void shouldRejectStopOrderWithNullStopPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP);
            r.setStopPrice(null);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldAcceptStopOrderWithStopPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP);
            r.setStopPrice(BigDecimal.valueOf(100.00));

            assertThat(r.isValid()).isTrue();
        }
    }

    @Nested
    class StopLimitOrderValidation {

        @Test
        void shouldRejectStopLimitWithNullStopPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP_LIMIT);
            r.setStopPrice(null);
            r.setLimitPrice(BigDecimal.valueOf(105.00));

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldRejectStopLimitWithNullLimitPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP_LIMIT);
            r.setStopPrice(BigDecimal.valueOf(100.00));
            r.setLimitPrice(null);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldRejectStopLimitWithBothPricesNull() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP_LIMIT);
            r.setStopPrice(null);
            r.setLimitPrice(null);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldAcceptStopLimitWithBothPrices() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.STOP_LIMIT);
            r.setStopPrice(BigDecimal.valueOf(100.00));
            r.setLimitPrice(BigDecimal.valueOf(105.00));

            assertThat(r.isValid()).isTrue();
        }
    }

    @Nested
    class MarketOrderValidation {

        @Test
        void shouldAcceptMarketOrder() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.MARKET);
            r.setPrice(BigDecimal.valueOf(100.00));

            assertThat(r.isValid()).isTrue();
        }
    }

    @Nested
    class LimitOrderValidation {

        @Test
        void shouldRejectLimitWithNullLimitPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.LIMIT);
            r.setPrice(BigDecimal.valueOf(100.00));
            r.setLimitPrice(null);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldAcceptLimitWithLimitPrice() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, OrderType.LIMIT);
            r.setPrice(BigDecimal.valueOf(100.00));
            r.setLimitPrice(BigDecimal.valueOf(95.00));

            assertThat(r.isValid()).isTrue();
        }
    }

    @Nested
    class RequiredFields {

        @Test
        void shouldRejectNullQuantity() {
            TradeRequest r = request("RELIANCE", null, TradeDirection.LONG, OrderType.MARKET);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldRejectZeroQuantity() {
            TradeRequest r = request("RELIANCE", 0, TradeDirection.LONG, OrderType.MARKET);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldRejectNullDirection() {
            TradeRequest r = new TradeRequest();
            r.setSymbol("RELIANCE");
            r.setQuantity(10);
            r.setDirection(null);
            r.setOrderType(OrderType.MARKET);

            assertThat(r.isValid()).isFalse();
        }

        @Test
        void shouldRejectNullOrderType() {
            TradeRequest r = request("RELIANCE", 10, TradeDirection.LONG, null);

            assertThat(r.isValid()).isFalse();
        }
    }
}
