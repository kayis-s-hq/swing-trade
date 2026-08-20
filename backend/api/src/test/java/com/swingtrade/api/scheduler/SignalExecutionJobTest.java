package com.swingtrade.api.scheduler;

import com.swingtrade.api.service.SignalFilterService;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.SignalStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SignalExecutionJob tests")
class SignalExecutionJobTest {

    private SignalExecutionJob job;

    @Mock
    private SignalStore signalStore;

    @Mock
    private CandleStore candleStore;

    @Mock
    private SignalFilterService signalFilterService;

    private Signal makeBuySignal(long id, String symbol) {
        return new Signal(id, symbol, LocalDate.now(), Signal.SignalType.BUY,
                BigDecimal.ONE, "test", null, null, null, null, null,
                LocalDate.now(), null, null);
    }

    private Signal makeSellSignal(long id, String symbol) {
        return new Signal(id, symbol, LocalDate.now(), Signal.SignalType.SELL,
                BigDecimal.ONE, "test", null, null, null, null, null,
                LocalDate.now(), null, null);
    }

    private OhlcvCandle makeCandle(String symbol, BigDecimal close) {
        return new OhlcvCandle(symbol, LocalDate.now(), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, close, 0L, close);
    }

    // ==================== executePendingSignals ====================

    @Nested
    @DisplayName("executePendingSignals")
    class ExecutePendingSignals {

        @BeforeEach
        void setUp() {
            job = new SignalExecutionJob(signalStore, candleStore, signalFilterService);
        }

        @Test
        @DisplayName("No pending signals logs and returns")
        void testExecutePendingSignals_NoPendingSignals_LogsAndReturns() {
            when(signalStore.findUnprocessed()).thenReturn(List.of());

            job.executePendingSignals();

            verify(signalStore, times(1)).findUnprocessed();
            verify(signalStore, never()).markProcessed(any());
            verify(candleStore, never()).findLatestBySymbolBeforeDate(anyString(), any());
            verify(signalFilterService, never()).filterAndProcess(any(), any());
        }

        @Test
        @DisplayName("No buy signals skips all")
        void testExecutePendingSignals_NoBuySignals_SkipsAll() {
            when(signalStore.findUnprocessed()).thenReturn(List.of(
                    makeSellSignal(1L, "RELIANCE"),
                    makeSellSignal(2L, "TCS")
            ));

            job.executePendingSignals();

            verify(signalStore, times(1)).findUnprocessed();
            verify(signalStore, never()).markProcessed(any());
            verify(candleStore, never()).findLatestBySymbolBeforeDate(anyString(), any());
            verify(signalFilterService, never()).filterAndProcess(any(), any());
        }

        @Test
        @DisplayName("Buy signal executed and marked processed")
        void testExecutePendingSignals_BuySignalExecuted_MarkedProcessed() {
            Signal buySignal = makeBuySignal(1L, "RELIANCE");
            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbolBeforeDate("RELIANCE", buySignal.date()))
                    .thenReturn(Optional.of(makeCandle("RELIANCE", new BigDecimal("500"))));
            Order order = mock(Order.class);
            when(signalFilterService.filterAndProcess(buySignal, new BigDecimal("500"))).thenReturn(order);

            job.executePendingSignals();

            verify(signalFilterService, times(1)).filterAndProcess(eq(buySignal), any());
            verify(signalStore, times(1)).markProcessed(1L);
        }

        @Test
        @DisplayName("Null candle skips signal")
        void testExecutePendingSignals_NullCandle_SkipsSignal() {
            Signal buySignal = makeBuySignal(2L, "INFY");
            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbolBeforeDate("INFY", buySignal.date()))
                    .thenReturn(Optional.empty());

            job.executePendingSignals();

            verify(signalStore, never()).markProcessed(any());
            verify(signalFilterService, never()).filterAndProcess(any(), any());
        }

        @Test
        @DisplayName("Zero price skips signal")
        void testExecutePendingSignals_ZeroPrice_SkipsSignal() {
            Signal buySignal = makeBuySignal(3L, "TCS");
            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbolBeforeDate("TCS", buySignal.date()))
                    .thenReturn(Optional.of(makeCandle("TCS", BigDecimal.ZERO)));

            job.executePendingSignals();

            verify(signalStore, never()).markProcessed(any());
            verify(signalFilterService, never()).filterAndProcess(any(), any());
        }

        @Test
        @DisplayName("Null filter result (suppressed) still marked processed")
        void testExecutePendingSignals_FilterReturnsNull_SuppressedSignal_MarkedProcessed() {
            Signal buySignal = makeBuySignal(4L, "HDFC");
            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbolBeforeDate("HDFC", buySignal.date()))
                    .thenReturn(Optional.of(makeCandle("HDFC", new BigDecimal("1500"))));
            when(signalFilterService.filterAndProcess(buySignal, new BigDecimal("1500"))).thenReturn(null);

            job.executePendingSignals();

            verify(signalStore, times(1)).markProcessed(4L);
        }

        @Test
        @DisplayName("Exception on execution does not mark processed — retry next run")
        void testExecutePendingSignals_ExceptionOnExecution_RetryNextRun() {
            Signal buySignal = makeBuySignal(5L, "WIPRO");
            when(signalStore.findUnprocessed()).thenReturn(List.of(buySignal));
            when(candleStore.findLatestBySymbolBeforeDate("WIPRO", buySignal.date()))
                    .thenReturn(Optional.of(makeCandle("WIPRO", new BigDecimal("400"))));
            when(signalFilterService.filterAndProcess(buySignal, new BigDecimal("400")))
                    .thenThrow(new RuntimeException("execution failed"));

            job.executePendingSignals();

            verify(signalStore, never()).markProcessed(any());
        }

        @Test
        @DisplayName("Multiple signals with mixed results")
        void testExecutePendingSignals_MultipleSignals_MixedResults() {
            Signal signal1 = makeBuySignal(10L, "A");
            Signal signal2 = makeBuySignal(11L, "B");
            Signal signal3 = makeBuySignal(12L, "C");
            when(signalStore.findUnprocessed()).thenReturn(List.of(signal1, signal2, signal3));

            // Signal 1: executes successfully
            when(candleStore.findLatestBySymbolBeforeDate("A", signal1.date()))
                    .thenReturn(Optional.of(makeCandle("A", new BigDecimal("100"))));
            when(signalFilterService.filterAndProcess(signal1, new BigDecimal("100")))
                    .thenReturn(mock(Order.class));

            // Signal 2: suppressed (null)
            when(candleStore.findLatestBySymbolBeforeDate("B", signal2.date()))
                    .thenReturn(Optional.of(makeCandle("B", new BigDecimal("200"))));
            when(signalFilterService.filterAndProcess(signal2, new BigDecimal("200")))
                    .thenReturn(null);

            // Signal 3: throws exception
            when(candleStore.findLatestBySymbolBeforeDate("C", signal3.date()))
                    .thenReturn(Optional.of(makeCandle("C", new BigDecimal("300"))));
            when(signalFilterService.filterAndProcess(signal3, new BigDecimal("300")))
                    .thenThrow(new RuntimeException("failed"));

            job.executePendingSignals();

            verify(signalStore, times(1)).markProcessed(10L);
            verify(signalStore, times(1)).markProcessed(11L);
            verify(signalStore, never()).markProcessed(12L);
        }
    }
}