package com.swingtrade.api.service;

import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.service.OrderService;
import com.swingtrade.domain.service.TradingService;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PositionStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.domain.store.TradeStore;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON contract of the {@code GET /api/positions} list items (serialized
 * {@link PositionResponse}) field by field, so the list can change its data source
 * (full aggregate vs. lightweight projection) without changing what the dashboard receives.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionListContractTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Mock private PositionStore positionStore;
    @Mock private StockStore stockStore;
    @Mock private PositionRepository positionRepository;
    @Mock private TradingService tradingService;
    @Mock private OrderService orderService;
    @Mock private CandleStore candleStore;
    @Mock private TradeStore tradeStore;
    @Mock private EntityManager entityManager;

    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionService(positionStore, stockStore, positionRepository,
                tradingService, orderService, candleStore, tradeStore, entityManager);
    }

    @Test
    void openListJsonIsStableForLongShortAndNullCurrentPrice() {
        PositionListFixtures.stubOpen(positionStore,
                PositionListFixtures.longOpen(),
                PositionListFixtures.shortOpen(),
                PositionListFixtures.noCurrentPrice());

        List<PositionResponse> list = positionService.getOpenPositions();

        assertThat(list).hasSize(3);
        assertJson(list.get(0), fields(
                "id", "1", "symbol", "\"TCS\"", "entryPrice", "100.0000",
                "entryDate", "\"2026-01-15\"", "quantity", "10", "stopLoss", "95.00",
                "target", "110.00", "status", "\"OPEN\"", "entryReason", "\"Breakout\"",
                "currentPrice", "104.00", "unrealizedPnL", "40.0000",
                "unrealizedPnLPercent", "4.0000", "averagePrice", "100.0000",
                "totalValue", "1040.00"));
        assertJson(list.get(1), fields(
                "id", "2", "symbol", "\"INFY\"", "entryPrice", "200.0000",
                "entryDate", "\"2026-02-01\"", "quantity", "5", "stopLoss", "210.00",
                "target", "180.00", "status", "\"OPEN\"", "entryReason", "\"Short setup\"",
                "currentPrice", "190.00", "unrealizedPnL", "50.0000",
                "unrealizedPnLPercent", "5.0000", "averagePrice", "200.0000",
                "totalValue", "950.00"));
        assertJson(list.get(2), fields(
                "id", "3", "symbol", "\"WIPRO\"", "entryPrice", "50.0000",
                "entryDate", "\"2026-03-01\"", "quantity", "20", "stopLoss", "45.00",
                "target", "60.00", "status", "\"OPEN\"", "entryReason", "null",
                "currentPrice", "null", "unrealizedPnL", "null",
                "unrealizedPnLPercent", "null", "averagePrice", "50.0000",
                "totalValue", "null"));
    }

    @Test
    void closedPositionResponseJsonIsStable() {
        Position closed = PositionListFixtures.closed();

        assertJson(new PositionResponse(closed), fields(
                "id", "4", "symbol", "\"HDFC\"", "entryPrice", "100.0000",
                "entryDate", "\"2026-01-05\"", "quantity", "10", "stopLoss", "90.00",
                "target", "120.00", "status", "\"TARGET_HIT\"", "entryReason", "\"Trend\"",
                "currentPrice", "120.00", "unrealizedPnL", "200.0000",
                "unrealizedPnLPercent", "20.0000", "averagePrice", "100.0000",
                "totalValue", "1200.00"));
    }

    private static Map<String, String> fields(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static void assertJson(PositionResponse response, Map<String, String> expected) {
        JsonNode node = MAPPER.valueToTree(response);
        assertThat(node.propertyNames()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((name, json) ->
                assertThat(node.get(name).toString()).as(name).isEqualTo(json));
    }

    /** Position fixtures shared by the contract tests. */
    static final class PositionListFixtures {

        private PositionListFixtures() {
        }

        static void stubOpen(PositionStore store, Position... positions) {
            org.mockito.Mockito.when(store.findAllOpen()).thenReturn(List.of(positions));
        }

        static Position longOpen() {
            return position(1L, "TCS", "100.0000", LocalDate.of(2026, 1, 15), 10, "95.00", "110.00",
                    PositionStatus.OPEN, "Breakout", "104.00", TradeDirection.LONG);
        }

        static Position shortOpen() {
            return position(2L, "INFY", "200.0000", LocalDate.of(2026, 2, 1), 5, "210.00", "180.00",
                    PositionStatus.OPEN, "Short setup", "190.00", TradeDirection.SHORT);
        }

        static Position noCurrentPrice() {
            return position(3L, "WIPRO", "50.0000", LocalDate.of(2026, 3, 1), 20, "45.00", "60.00",
                    PositionStatus.OPEN, null, null, TradeDirection.LONG);
        }

        static Position closed() {
            return position(4L, "HDFC", "100.0000", LocalDate.of(2026, 1, 5), 10, "90.00", "120.00",
                    PositionStatus.TARGET_HIT, "Trend", "120.00", TradeDirection.LONG);
        }

        private static Position position(Long id, String symbol, String entry, LocalDate entryDate,
                                         int qty, String stop, String target, PositionStatus status,
                                         String reason, String current, TradeDirection direction) {
            return Position.of(id, "PAPER", symbol, new BigDecimal(entry), entryDate, qty,
                    new BigDecimal(stop), new BigDecimal(target), status, reason,
                    current == null ? null : new BigDecimal(current),
                    "POS_" + id, null, null, direction, new BigDecimal(entry),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDateTime.of(2026, 1, 1, 9, 30), null, null, null);
        }
    }
}
