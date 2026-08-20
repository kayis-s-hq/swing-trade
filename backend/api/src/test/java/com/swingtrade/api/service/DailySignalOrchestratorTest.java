package com.swingtrade.api.service;

import com.swingtrade.domain.Signal;
import com.swingtrade.domain.store.CandleStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("DailySignalOrchestrator tests")
class DailySignalOrchestratorTest {

    private DailySignalOrchestrator orchestrator;

    @Mock
    private SignalPipeline pipeline;

    @Mock
    private CandleStore candleStore;

    private DailySignalOrchestrator.DailySignalResult makeResult(int success, int processed, int failures, String error) {
        return new DailySignalOrchestrator.DailySignalResult(success, processed, failures, error);
    }

    private Signal makeSignal(String symbol) {
        return Signal.create(symbol, java.time.LocalDate.now(), Signal.SignalType.BUY,
                java.math.BigDecimal.ONE, "test reasoning");
    }

    // ==================== RunDailyGeneration ====================

    @Nested
    @DisplayName("runDailyGeneration")
    class RunDailyGeneration {

        @BeforeEach
        void setUp() {
            orchestrator = new DailySignalOrchestrator(pipeline, candleStore);
        }

        @Test
        @DisplayName("Empty symbols returns zero counts")
        void testRunDailyGeneration_EmptySymbols_ReturnsZeroCounts() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of());

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.success()).isEqualTo(0);
            assertThat(result.processed()).isEqualTo(0);
            assertThat(result.failures()).isEqualTo(0);
            assertThat(result.error()).isNull();
        }

        @Test
        @DisplayName("Single symbol with primary signal succeeds")
        void testRunDailyGeneration_SingleSymbolSuccess_PrimarySignal() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("RELIANCE"));
            when(pipeline.generatePrimarySignal("RELIANCE")).thenReturn(Optional.of(makeSignal("RELIANCE")));
            when(pipeline.generatePriceActionSignal("RELIANCE")).thenReturn(Optional.empty());

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.success()).isEqualTo(1);
            assertThat(result.processed()).isEqualTo(1);
            assertThat(result.failures()).isEqualTo(0);
            assertThat(result.error()).isNull();
        }

        @Test
        @DisplayName("Single symbol with both signals generated")
        void testRunDailyGeneration_SingleSymbolSuccess_BothSignals() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("RELIANCE"));
            when(pipeline.generatePrimarySignal("RELIANCE")).thenReturn(Optional.of(makeSignal("RELIANCE")));
            when(pipeline.generatePriceActionSignal("RELIANCE")).thenReturn(Optional.of(makeSignal("RELIANCE")));

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.success()).isEqualTo(2);
            assertThat(result.processed()).isEqualTo(1);
            assertThat(result.failures()).isEqualTo(0);
        }

        @Test
        @DisplayName("Multiple symbols with partial failures")
        void testRunDailyGeneration_MultipleSymbolsPartialFailures() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("A", "B", "C", "D", "E"));

            // 3 succeed, 2 throw on primary
            when(pipeline.generatePrimarySignal("A")).thenReturn(Optional.of(makeSignal("A")));
            when(pipeline.generatePrimarySignal("B")).thenReturn(Optional.of(makeSignal("B")));
            when(pipeline.generatePrimarySignal("C")).thenReturn(Optional.of(makeSignal("C")));
            when(pipeline.generatePrimarySignal("D")).thenThrow(new RuntimeException("fail"));
            when(pipeline.generatePrimarySignal("E")).thenThrow(new RuntimeException("fail"));

            when(pipeline.generatePriceActionSignal("A")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("B")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("C")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("D")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("E")).thenReturn(Optional.empty());

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.success()).isEqualTo(3);
            assertThat(result.processed()).isEqualTo(5);
            assertThat(result.failures()).isEqualTo(2);
        }

        @Test
        @DisplayName("Exception on first symbol continues with others")
        void testRunDailyGeneration_ExceptionOnFirstSymbol_ContinuesWithOthers() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("A", "B", "C", "D", "E"));

            when(pipeline.generatePrimarySignal("A")).thenThrow(new RuntimeException("first fail"));
            when(pipeline.generatePrimarySignal("B")).thenReturn(Optional.of(makeSignal("B")));
            when(pipeline.generatePrimarySignal("C")).thenReturn(Optional.of(makeSignal("C")));
            when(pipeline.generatePrimarySignal("D")).thenReturn(Optional.of(makeSignal("D")));
            when(pipeline.generatePrimarySignal("E")).thenReturn(Optional.of(makeSignal("E")));

            when(pipeline.generatePriceActionSignal("A")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("B")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("C")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("D")).thenReturn(Optional.empty());
            when(pipeline.generatePriceActionSignal("E")).thenReturn(Optional.empty());

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.processed()).isEqualTo(5);
            assertThat(result.failures()).isEqualTo(1);
            assertThat(result.success()).isEqualTo(4);
        }

        @Test
        @DisplayName("Exception on price-action does not affect primary signal result")
        void testRunDailyGeneration_ExceptionOnPriceAction_ContinuesWithNextSymbol() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("A"));

            when(pipeline.generatePrimarySignal("A")).thenReturn(Optional.of(makeSignal("A")));
            doThrow(new RuntimeException("price-action fail")).when(pipeline).generatePriceActionSignal("A");

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            // price-action catch block only logs, does not increment failures
            assertThat(result.processed()).isEqualTo(1);
            assertThat(result.failures()).isEqualTo(0);
            assertThat(result.success()).isEqualTo(1);
        }

        @Test
        @DisplayName("Exception fetching symbols returns error result")
        void testRunDailyGeneration_CatchesSymbolFetchException_ReturnsErrorResult() {
            when(candleStore.findAllDistinctSymbols()).thenThrow(new RuntimeException("DB error"));

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.error()).contains("DB error");
            assertThat(result.processed()).isEqualTo(0);
            assertThat(result.success()).isEqualTo(0);
            assertThat(result.failures()).isEqualTo(0);
        }

        @Test
        @DisplayName("Progress logging every 10 symbols")
        void testRunDailyGeneration_ProgressLogging_Every10Symbols() {
            when(candleStore.findAllDistinctSymbols()).thenReturn(List.of("A", "B", "C", "D", "E",
                    "F", "G", "H", "I", "J", "K", "L", "M", "N", "O"));

            for (String symbol : List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O")) {
                when(pipeline.generatePrimarySignal(symbol)).thenReturn(Optional.of(makeSignal(symbol)));
                when(pipeline.generatePriceActionSignal(symbol)).thenReturn(Optional.empty());
            }

            DailySignalOrchestrator.DailySignalResult result = orchestrator.runDailyGeneration();

            assertThat(result.processed()).isEqualTo(15);
            assertThat(result.success()).isEqualTo(15);
            verify(pipeline, times(15)).generatePrimarySignal(anyString());
            verify(pipeline, times(15)).generatePriceActionSignal(anyString());
            verify(candleStore, times(1)).findAllDistinctSymbols();
        }
    }
}