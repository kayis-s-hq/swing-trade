package com.swingtrade.broker.model;

import com.swingtrade.domain.Exchange;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.TradeDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OrderResponse covering construction, getters/setters,
 * validation, equality, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class OrderResponseTest {

    // ==================== Creation ====================

    @Nested
    class Creation {

        @Test
        void defaultConstructor_setsDefaults() {
            // Given: A new OrderResponse via default constructor
            OrderResponse response = new OrderResponse();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getTimestamp()).isNotNull();
            assertThat(response.getTransactionCharge()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getStt()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getOtherCharges()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getBrokerCharges()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getBrokerOrderId()).isNull();
            assertThat(response.getInternalOrderId()).isNull();
            assertThat(response.getSymbol()).isNull();
            assertThat(response.getExchange()).isNull();
            assertThat(response.getType()).isNull();
            assertThat(response.getDirection()).isNull();
            assertThat(response.getQuantity()).isNull();
            assertThat(response.getPrice()).isNull();
            assertThat(response.getLimitPrice()).isNull();
            assertThat(response.getStopPrice()).isNull();
            assertThat(response.getExchangeOrderId()).isNull();
            assertThat(response.getMessage()).isNull();
        }

        @Test
        void fullConstructor_allFieldsSet() {
            // Given: A fully populated OrderResponse
            LocalDateTime now = LocalDateTime.of(2026, 8, 7, 10, 30, 0);
            BigDecimal price = new BigDecimal("2500.50");
            BigDecimal quantity = new BigDecimal("10");
            BigDecimal limitPrice = new BigDecimal("2499.00");
            BigDecimal stopPrice = new BigDecimal("2480.00");

            OrderResponse response = new OrderResponse();
            response.setBrokerOrderId("BRK_001");
            response.setInternalOrderId("INT_001");
            response.setSymbol("RELIANCE-EQ");
            response.setExchange(Exchange.NSE);
            response.setType(OrderType.LIMIT);
            response.setDirection(TradeDirection.LONG);
            response.setQuantity(quantity);
            response.setPrice(price);
            response.setLimitPrice(limitPrice);
            response.setStopPrice(stopPrice);
            response.setStatus(OrderStatus.ACCEPTED);
            response.setTimestamp(now);
            response.setExchangeOrderId("EXG_001");
            response.setTransactionCharge(new BigDecimal("0.01"));
            response.setStt(new BigDecimal("0.05"));
            response.setOtherCharges(BigDecimal.ZERO);
            response.setBrokerCharges(BigDecimal.ZERO);
            response.setMessage("Order accepted");

            // Then
            assertThat(response.getBrokerOrderId()).isEqualTo("BRK_001");
            assertThat(response.getInternalOrderId()).isEqualTo("INT_001");
            assertThat(response.getSymbol()).isEqualTo("RELIANCE-EQ");
            assertThat(response.getExchange()).isEqualTo(Exchange.NSE);
            assertThat(response.getType()).isEqualTo(OrderType.LIMIT);
            assertThat(response.getDirection()).isEqualTo(TradeDirection.LONG);
            assertThat(response.getQuantity()).isEqualTo(quantity);
            assertThat(response.getPrice()).isEqualTo(price);
            assertThat(response.getLimitPrice()).isEqualTo(limitPrice);
            assertThat(response.getStopPrice()).isEqualTo(stopPrice);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(response.getTimestamp()).isEqualTo(now);
            assertThat(response.getExchangeOrderId()).isEqualTo("EXG_001");
            assertThat(response.getTransactionCharge()).isEqualTo(new BigDecimal("0.01"));
            assertThat(response.getStt()).isEqualTo(new BigDecimal("0.05"));
            assertThat(response.getOtherCharges()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getBrokerCharges()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getMessage()).isEqualTo("Order accepted");
        }

        @Test
        void partialFields_onlySymbolAndDirectionSet() {
            // Given: A response with only minimal fields set
            OrderResponse response = new OrderResponse();
            response.setSymbol("TCS-EQ");
            response.setDirection(TradeDirection.SHORT);

            // Then
            assertThat(response.getSymbol()).isEqualTo("TCS-EQ");
            assertThat(response.getDirection()).isEqualTo(TradeDirection.SHORT);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getQuantity()).isNull();
        }

        @Test
        void allOrderTypes_supported() {
            // Given: Responses for each order type
            OrderType[] types = OrderType.values();

            for (OrderType type : types) {
                // When
                OrderResponse response = new OrderResponse();
                response.setType(type);

                // Then
                assertThat(response.getType()).isEqualTo(type);
            }
        }

        @Test
        void allOrderStatuses_supported() {
            // Given: Responses for each order status
            OrderStatus[] statuses = OrderStatus.values();

            for (OrderStatus status : statuses) {
                // When
                OrderResponse response = new OrderResponse();
                response.setStatus(status);

                // Then
                assertThat(response.getStatus()).isEqualTo(status);
            }
        }

        @Test
        void allExchanges_supported() {
            // Given: Responses for each exchange
            Exchange[] exchanges = Exchange.values();

            for (Exchange exchange : exchanges) {
                // When
                OrderResponse response = new OrderResponse();
                response.setExchange(exchange);

                // Then
                assertThat(response.getExchange()).isEqualTo(exchange);
            }
        }
    }

    // ==================== Getters/Setters ====================

    @Nested
    class GettersSetters {

        @Test
        void setSymbol_getSymbol() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setSymbol("INFY-EQ");

            // Then
            assertThat(response.getSymbol()).isEqualTo("INFY-EQ");
        }

        @Test
        void setDirection_getDirection() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setDirection(TradeDirection.LONG);

            // Then
            assertThat(response.getDirection()).isEqualTo(TradeDirection.LONG);
        }

        @Test
        void setQuantity_getQuantity() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal qty = new BigDecimal("100");

            // When
            response.setQuantity(qty);

            // Then
            assertThat(response.getQuantity()).isEqualTo(qty);
        }

        @Test
        void setPrice_getPrice() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal price = new BigDecimal("1500.75");

            // When
            response.setPrice(price);

            // Then
            assertThat(response.getPrice()).isEqualTo(price);
        }

        @Test
        void setStatus_getStatus() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setStatus(OrderStatus.FILLED);

            // Then
            assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void setOrderId_getBrokerOrderId() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setBrokerOrderId("BROKER_123");

            // Then
            assertThat(response.getBrokerOrderId()).isEqualTo("BROKER_123");
        }

        @Test
        void setInternalOrderId_getInternalOrderId() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setInternalOrderId("INTERNAL_456");

            // Then
            assertThat(response.getInternalOrderId()).isEqualTo("INTERNAL_456");
        }

        @Test
        void setExchange_getExchange() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setExchange(Exchange.BSE);

            // Then
            assertThat(response.getExchange()).isEqualTo(Exchange.BSE);
        }

        @Test
        void setType_getType() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setType(OrderType.STOP_LOSS);

            // Then
            assertThat(response.getType()).isEqualTo(OrderType.STOP_LOSS);
        }

        @Test
        void setLimitPrice_getLimitPrice() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal limit = new BigDecimal("1200.00");

            // When
            response.setLimitPrice(limit);

            // Then
            assertThat(response.getLimitPrice()).isEqualTo(limit);
        }

        @Test
        void setStopPrice_getStopPrice() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal stop = new BigDecimal("1150.00");

            // When
            response.setStopPrice(stop);

            // Then
            assertThat(response.getStopPrice()).isEqualTo(stop);
        }

        @Test
        void setTimestamp_getTimestamp() {
            // Given
            OrderResponse response = new OrderResponse();
            LocalDateTime ts = LocalDateTime.of(2026, 1, 15, 9, 0, 0);

            // When
            response.setTimestamp(ts);

            // Then
            assertThat(response.getTimestamp()).isEqualTo(ts);
        }

        @Test
        void setExchangeOrderId_getExchangeOrderId() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setExchangeOrderId("NSE_789");

            // Then
            assertThat(response.getExchangeOrderId()).isEqualTo("NSE_789");
        }

        @Test
        void setTransactionCharge_getTransactionCharge() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal charge = new BigDecimal("0.02");

            // When
            response.setTransactionCharge(charge);

            // Then
            assertThat(response.getTransactionCharge()).isEqualTo(charge);
        }

        @Test
        void setStt_getStt() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal stt = new BigDecimal("0.025");

            // When
            response.setStt(stt);

            // Then
            assertThat(response.getStt()).isEqualTo(stt);
        }

        @Test
        void setOtherCharges_getOtherCharges() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal other = new BigDecimal("0.005");

            // When
            response.setOtherCharges(other);

            // Then
            assertThat(response.getOtherCharges()).isEqualTo(other);
        }

        @Test
        void setBrokerCharges_getBrokerCharges() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal broker = new BigDecimal("0.01");

            // When
            response.setBrokerCharges(broker);

            // Then
            assertThat(response.getBrokerCharges()).isEqualTo(broker);
        }

        @Test
        void setMessage_getMessage() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setMessage("Order placed successfully");

            // Then
            assertThat(response.getMessage()).isEqualTo("Order placed successfully");
        }
    }

    // ==================== Validation ====================

    @Nested
    class Validation {

        @Test
        void nullSymbol_allowed() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setSymbol(null);

            // Then
            assertThat(response.getSymbol()).isNull();
        }

        @Test
        void zeroQuantity_allowed() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setQuantity(BigDecimal.ZERO);

            // Then
            assertThat(response.getQuantity()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void negativeQuantity_allowed() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setQuantity(new BigDecimal("-10"));

            // Then
            assertThat(response.getQuantity()).isEqualTo(new BigDecimal("-10"));
        }

        @Test
        void nullPrice_allowed() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setPrice(null);

            // Then
            assertThat(response.getPrice()).isNull();
        }

        @Test
        void nullQuantity_allowed() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setQuantity(null);

            // Then
            assertThat(response.getQuantity()).isNull();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void nullValues_allFieldsNull() {
            // Given: A response with all fields null except defaults
            OrderResponse response = new OrderResponse();
            response.setBrokerOrderId(null);
            response.setInternalOrderId(null);
            response.setSymbol(null);
            response.setExchange(null);
            response.setType(null);
            response.setDirection(null);
            response.setQuantity(null);
            response.setPrice(null);
            response.setLimitPrice(null);
            response.setStopPrice(null);
            response.setExchangeOrderId(null);
            response.setMessage(null);

            // Then
            assertThat(response.getBrokerOrderId()).isNull();
            assertThat(response.getInternalOrderId()).isNull();
            assertThat(response.getSymbol()).isNull();
            assertThat(response.getExchange()).isNull();
            assertThat(response.getType()).isNull();
            assertThat(response.getDirection()).isNull();
            assertThat(response.getQuantity()).isNull();
            assertThat(response.getPrice()).isNull();
            assertThat(response.getLimitPrice()).isNull();
            assertThat(response.getStopPrice()).isNull();
            assertThat(response.getExchangeOrderId()).isNull();
            assertThat(response.getMessage()).isNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getTransactionCharge()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void maxBigDecimalValues() {
            // Given
            OrderResponse response = new OrderResponse();
            BigDecimal maxPrice = new BigDecimal("999999999.99");
            BigDecimal maxQuantity = new BigDecimal("999999999");

            // When
            response.setPrice(maxPrice);
            response.setQuantity(maxQuantity);
            response.setLimitPrice(maxPrice);
            response.setStopPrice(maxPrice);

            // Then
            assertThat(response.getPrice()).isEqualTo(maxPrice);
            assertThat(response.getQuantity()).isEqualTo(maxQuantity);
            assertThat(response.getLimitPrice()).isEqualTo(maxPrice);
            assertThat(response.getStopPrice()).isEqualTo(maxPrice);
        }

        @Test
        void specialCharactersInSymbol() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setSymbol("SUNPHARMA-EQ");
            response.setBrokerOrderId("BRK-ORDER_2026!@#");
            response.setMessage("Order for SUNPHARMA-EQ placed at 10:30 AM!");

            // Then
            assertThat(response.getSymbol()).isEqualTo("SUNPHARMA-EQ");
            assertThat(response.getBrokerOrderId()).isEqualTo("BRK-ORDER_2026!@#");
            assertThat(response.getMessage()).isEqualTo("Order for SUNPHARMA-EQ placed at 10:30 AM!");
        }

        @Test
        void emptyStringMessage() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setMessage("");

            // Then
            assertThat(response.getMessage()).isEmpty();
        }

        @Test
        void whitespaceSymbol() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setSymbol("   ");

            // Then
            assertThat(response.getSymbol()).isEqualTo("   ");
        }

        @Test
        void negativePrice() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setPrice(new BigDecimal("-100.00"));

            // Then
            assertThat(response.getPrice()).isEqualTo(new BigDecimal("-100.00"));
        }

        @Test
        void veryLargeQuantity() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setQuantity(new BigDecimal("1000000000"));

            // Then
            assertThat(response.getQuantity()).isEqualTo(new BigDecimal("1000000000"));
        }

        @Test
        void allChargesSetToNonZero() {
            // Given
            OrderResponse response = new OrderResponse();

            // When
            response.setTransactionCharge(new BigDecimal("0.01"));
            response.setStt(new BigDecimal("0.025"));
            response.setOtherCharges(new BigDecimal("0.005"));
            response.setBrokerCharges(new BigDecimal("0.02"));

            // Then
            assertThat(response.getTransactionCharge()).isEqualTo(new BigDecimal("0.01"));
            assertThat(response.getStt()).isEqualTo(new BigDecimal("0.025"));
            assertThat(response.getOtherCharges()).isEqualTo(new BigDecimal("0.005"));
            assertThat(response.getBrokerCharges()).isEqualTo(new BigDecimal("0.02"));
        }
    }
}
