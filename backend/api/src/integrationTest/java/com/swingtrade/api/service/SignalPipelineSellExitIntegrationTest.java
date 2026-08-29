/*
 * Copyright 2026 Swing Trade
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swingtrade.api.service;

import com.swingtrade.api.app.SwingTradeApiApplication;
import com.swingtrade.api.dto.PositionResponse;
import com.swingtrade.api.dto.TradeRequest;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.OrderType;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Trade;
import com.swingtrade.domain.TradeDirection;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.TradeStore;
import com.swingtrade.strategy.ExitReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack (real DB, no mocks) verification that a SELL signal for a held position
 * actually closes the position in the database, exercising the wiring added in
 * Phase 3 of the sell-exit-signal-pipeline plan:
 * {@code SignalPipeline.generatePrimarySignal} -&gt; {@code PositionStore.findBySymbol}
 * -&gt; {@code PositionService.closePosition}.
 */
@SpringBootTest(classes = SwingTradeApiApplication.class)
@Testcontainers
@ActiveProfiles("test")
class SignalPipelineSellExitIntegrationTest {

    private static final String SYMBOL = "TESTCO";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("swing_trade_test")
        .withUsername("swing_trade_test")
        .withPassword("swing_trade_test");

    @Autowired
    private SignalPipeline signalPipeline;

    @Autowired
    private PositionService positionService;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private CandleStore candleStore;

    @Autowired
    private SignalStore signalStore;

    @Autowired
    private TradeStore tradeStore;

    @Test
    void sellSignal_forHeldPosition_actuallyClosesPositionInDb() {
        // 1. Seed candles that deterministically satisfy the exit confluence for the latest
        // candle: a zigzag uptrend (keeps RSI in the healthy band and EMA20 > EMA50 going in,
        // same construction proven in PriceActionSignalEngineTest.ExitRules) followed by a
        // single final candle whose close drops ~3%, tripping condition A (close < EMA20) and
        // typically condition C (RSI < 50) too. SignalPipeline.generatePrimarySignal() only
        // ever fetches the most recent 100 candles (findTopBySymbolOrderByDateDesc(symbol,
        // 100)), so exactly 100 rows (99 zigzag + 1 final) are seeded here to guarantee the
        // fetched window itself reproduces the SELL confluence.
        List<OhlcvCandle> candles = buildZigzagUptrendCandles(99, 100.0, 0.5, 0.75, 1_000_000L);
        OhlcvCandle lastCandle = candles.get(candles.size() - 1);
        candles.add(buildFinalCandle(lastCandle, 0.97, 1_000_000L));
        candles.forEach(candleStore::save);

        BigDecimal entryPrice = lastCandle.close();

        // 2. Open a real position for TESTCO via the same order pipeline production code uses.
        TradeRequest request = new TradeRequest(SYMBOL, 10, TradeDirection.LONG, OrderType.MARKET);
        request.setPrice(entryPrice);
        PositionResponse opened = positionService.createPosition(request);

        // 3. Capture the DB position id.
        Long positionId = opened.getId();
        assertThat(positionId).isNotNull();
        assertThat(positionRepository.findById(positionId)).isPresent();
        assertThat(positionRepository.findById(positionId).orElseThrow().getStatus()).isEqualTo("OPEN");

        // 4. Run the real signal-generation pipeline.
        signalPipeline.generatePrimarySignal(SYMBOL);

        // 5. Assert the real DB row for the position is now CLOSED.
        PositionEntity closedEntity = positionRepository.findById(positionId).orElseThrow();
        assertThat(closedEntity.getStatus()).isEqualTo("CLOSED");

        // 5a. Bug 1 (exit reason discarded): the persisted exitReason must be the real
        // signal-driven reason, not a hardcoded "manual".
        assertThat(closedEntity.getExitReason()).isEqualTo(ExitReason.SIGNAL_EXIT.name());

        // 5b. Bug 3 (stale exit price): the persisted currentPrice must reflect the real
        // market close that triggered the exit, not the never-refreshed entry price.
        BigDecimal expectedExitPrice = candleStore.findLatestBySymbol(SYMBOL).orElseThrow().close();
        assertThat(closedEntity.getCurrentPrice()).isEqualByComparingTo(expectedExitPrice);
        assertThat(closedEntity.getCurrentPrice()).isNotEqualByComparingTo(entryPrice);

        // 5c. Bug 2 (no Trade audit record): a closed Trade row must exist for this position,
        // with matching entry/exit prices and reason.
        List<Trade> trades = tradeStore.findBySymbol(SYMBOL);
        assertThat(trades).isNotEmpty();
        Trade trade = trades.stream()
            .filter(t -> t.positionId().equals(positionId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No Trade record found for position " + positionId));
        assertThat(trade.tradeStatus()).isNotEqualTo(Trade.TradeStatus.OPEN);
        assertThat(trade.entryPrice()).isEqualByComparingTo(entryPrice);
        assertThat(trade.exitPrice()).isEqualByComparingTo(expectedExitPrice);
        assertThat(trade.exitReason()).isEqualTo(ExitReason.SIGNAL_EXIT.name());

        // 6. Assert a SELL signal row was persisted for TESTCO.
        List<Signal> signals = signalStore.findBySymbolOrderByDateDesc(SYMBOL);
        assertThat(signals).isNotEmpty();
        Optional<Signal> sellSignal = signals.stream()
            .filter(s -> s.type() == Signal.SignalType.SELL)
            .findFirst();
        assertThat(sellSignal).isPresent();
    }

    private List<OhlcvCandle> buildZigzagUptrendCandles(int count, double startPrice,
                                                          double upPercent, double downPercent, long volume) {
        List<OhlcvCandle> candles = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.now().minusDays(count + 1);
        for (int i = 0; i < count; i++) {
            double changePercent = (i % 3 == 2) ? -downPercent : upPercent;
            double open = price;
            double close = price * (1 + changePercent / 100.0);
            double high = Math.max(open, close) * 1.001;
            double low = Math.min(open, close) * 0.999;

            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, date,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), volume);

            candles.add(candle);
            price = close;
            date = date.plusDays(1);
        }
        return candles;
    }

    private OhlcvCandle buildFinalCandle(OhlcvCandle previous, double closeMultiplier, long volume) {
        BigDecimal previousClose = previous.close();
        BigDecimal close = previousClose.multiply(BigDecimal.valueOf(closeMultiplier));
        BigDecimal high = previousClose.max(close).multiply(BigDecimal.valueOf(1.001));
        BigDecimal low = previousClose.min(close).multiply(BigDecimal.valueOf(0.999));

        return OhlcvCandle.of(SYMBOL, previous.date().plusDays(1),
            previousClose, high, low, close, volume);
    }
}
