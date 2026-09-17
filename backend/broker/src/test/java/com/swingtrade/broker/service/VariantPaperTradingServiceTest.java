package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VariantPaperTradingService covering portfolio provisioning,
 * position opening/closing, stop/target trigger evaluation, and closed-trade
 * retrieval for per-variant paper trading.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VariantPaperTradingServiceTest {

    @Mock
    private PaperTradingPortfolioRepository portfolioRepo;

    @Mock
    private PositionRepository positionRepo;

    private VariantPaperTradingService service;

    @BeforeEach
    void setUp() {
        service = new VariantPaperTradingService(portfolioRepo, positionRepo);
    }

    private Signal buySignal(String symbol, BigDecimal stopLoss, BigDecimal target) {
        return new Signal(1L, symbol, LocalDate.now(), Signal.SignalType.BUY,
                BigDecimal.valueOf(0.8), "breakout", BigDecimal.TEN, stopLoss, target,
                BigDecimal.valueOf(2.5), "RSI", LocalDate.now(), null, null);
    }

    private PaperTradingPortfolioEntity portfolio(String id, BigDecimal capital) {
        PaperTradingPortfolioEntity p = new PaperTradingPortfolioEntity();
        p.setId(2L);
        p.setPortfolioId(id);
        p.setInitialCapital(capital);
        p.setCurrentCapital(capital);
        p.setTotalRealizedPnl(BigDecimal.ZERO);
        p.setTotalUnrealizedPnL(BigDecimal.ZERO);
        p.setOpenPositionCount(0);
        return p;
    }

    // ==================== ensurePortfolio ====================

    @Nested
    class EnsurePortfolio {

        @Test
        void blankVariantId_doesNothing() {
            service.ensurePortfolio(null, BigDecimal.TEN);
            service.ensurePortfolio("  ", BigDecimal.TEN);
            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void alreadyExists_doesNothing() {
            when(portfolioRepo.findByPortfolioId("variant-1"))
                    .thenReturn(Optional.of(portfolio("variant-1", BigDecimal.valueOf(100000))));

            service.ensurePortfolio("variant-1", BigDecimal.valueOf(50000));

            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void createsNewPortfolio_withGivenCapital() {
            when(portfolioRepo.findByPortfolioId("variant-2")).thenReturn(Optional.empty());
            when(portfolioRepo.findAll()).thenReturn(List.of());
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            service.ensurePortfolio("variant-2", BigDecimal.valueOf(75000));

            ArgumentCaptor<PaperTradingPortfolioEntity> captor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            assertThat(captor.getValue().getPortfolioId()).isEqualTo("variant-2");
            assertThat(captor.getValue().getInitialCapital()).isEqualByComparingTo("75000");
            assertThat(captor.getValue().getCurrentCapital()).isEqualByComparingTo("75000");
        }

        @Test
        void nullOrNonPositiveCapital_defaultsTo100000() {
            when(portfolioRepo.findByPortfolioId("variant-3")).thenReturn(Optional.empty());
            when(portfolioRepo.findAll()).thenReturn(List.of());
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            service.ensurePortfolio("variant-3", null);

            ArgumentCaptor<PaperTradingPortfolioEntity> captor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            assertThat(captor.getValue().getInitialCapital()).isEqualByComparingTo("100000");
        }

        @Test
        void negativeCapital_defaultsTo100000() {
            when(portfolioRepo.findByPortfolioId("variant-3b")).thenReturn(Optional.empty());
            when(portfolioRepo.findAll()).thenReturn(List.of());
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            service.ensurePortfolio("variant-3b", BigDecimal.valueOf(-10));

            ArgumentCaptor<PaperTradingPortfolioEntity> captor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            assertThat(captor.getValue().getInitialCapital()).isEqualByComparingTo("100000");
        }

        @Test
        void nextPortfolioId_usesMaxExistingIdPlusOne() {
            when(portfolioRepo.findByPortfolioId("variant-4")).thenReturn(Optional.empty());
            PaperTradingPortfolioEntity existing = portfolio("default", BigDecimal.TEN);
            existing.setId(5L);
            when(portfolioRepo.findAll()).thenReturn(List.of(existing));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            service.ensurePortfolio("variant-4", BigDecimal.valueOf(1000));

            ArgumentCaptor<PaperTradingPortfolioEntity> captor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(6L);
        }

        @Test
        void duplicateKeyRace_swallowedWhenPortfolioNowExists() {
            when(portfolioRepo.findByPortfolioId("variant-5"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(portfolio("variant-5", BigDecimal.TEN)));
            when(portfolioRepo.findAll()).thenReturn(List.of());
            when(portfolioRepo.save(any())).thenThrow(new OptimisticLockingFailureException("dup"));

            // Should not throw: the postcondition (portfolio exists) is satisfied.
            service.ensurePortfolio("variant-5", BigDecimal.valueOf(1000));
        }

        @Test
        void failureAndPortfolioStillMissing_rethrows() {
            when(portfolioRepo.findByPortfolioId("variant-6")).thenReturn(Optional.empty());
            when(portfolioRepo.findAll()).thenReturn(List.of());
            when(portfolioRepo.save(any())).thenThrow(new RuntimeException("boom"));

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.ensurePortfolio("variant-6", BigDecimal.valueOf(1000)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ==================== openPosition ====================

    @Nested
    class OpenPosition {

        @Test
        void nullArgs_returnsFalse() {
            assertThat(service.openPosition(null, buySignal("TCS", null, null), BigDecimal.TEN)).isFalse();
            assertThat(service.openPosition("v1", null, BigDecimal.TEN)).isFalse();
            assertThat(service.openPosition("v1", buySignal("TCS", null, null), null)).isFalse();
            assertThat(service.openPosition("v1", buySignal("TCS", null, null), BigDecimal.ZERO)).isFalse();
        }

        @Test
        void alreadyHoldsOpenPosition_skipsAndReturnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS"))
                    .thenReturn(List.of(new PositionEntity()));

            boolean result = service.openPosition("v1", buySignal("TCS", null, null), BigDecimal.valueOf(100));

            assertThat(result).isFalse();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void noPortfolioForVariant_returnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v2", "TCS")).thenReturn(List.of());
            when(portfolioRepo.findByPortfolioId("v2")).thenReturn(Optional.empty());

            boolean result = service.openPosition("v2", buySignal("TCS", null, null), BigDecimal.valueOf(100));

            assertThat(result).isFalse();
        }

        @Test
        void insufficientCapital_returnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v3", "TCS")).thenReturn(List.of());
            when(portfolioRepo.findByPortfolioId("v3"))
                    .thenReturn(Optional.of(portfolio("v3", BigDecimal.valueOf(1))));

            boolean result = service.openPosition("v3", buySignal("TCS", null, null), BigDecimal.valueOf(100));

            assertThat(result).isFalse();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void opensPosition_withExplicitStopAndTarget() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v4", "TCS")).thenReturn(List.of());
            PaperTradingPortfolioEntity fresh = portfolio("v4", BigDecimal.valueOf(100000));
            when(portfolioRepo.findByPortfolioId("v4")).thenReturn(Optional.of(fresh));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Signal signal = buySignal("TCS", BigDecimal.valueOf(90), BigDecimal.valueOf(130));
            boolean result = service.openPosition("v4", signal, BigDecimal.valueOf(100));

            assertThat(result).isTrue();
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            PositionEntity saved = captor.getValue();
            assertThat(saved.getPortfolioId()).isEqualTo("v4");
            assertThat(saved.getSymbol()).isEqualTo("TCS");
            assertThat(saved.getStopLoss()).isEqualByComparingTo("90");
            assertThat(saved.getTarget()).isEqualByComparingTo("130");
            assertThat(saved.getStatus()).isEqualTo("OPEN");
            assertThat(saved.getPositionId()).startsWith("VPOS_v4_");

            ArgumentCaptor<PaperTradingPortfolioEntity> portfolioCaptor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(portfolioCaptor.capture());
            assertThat(portfolioCaptor.getValue().getOpenPositionCount()).isEqualTo(1);
        }

        @Test
        void opensPosition_withDefaultStopAndTarget_whenSignalOmitsThem() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v5", "INFY")).thenReturn(List.of());
            when(portfolioRepo.findByPortfolioId("v5"))
                    .thenReturn(Optional.of(portfolio("v5", BigDecimal.valueOf(100000))));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            Signal signal = buySignal("INFY", null, null);
            boolean result = service.openPosition("v5", signal, BigDecimal.valueOf(1000));

            assertThat(result).isTrue();
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            // default stop = referencePrice * (1 - 0.03), default target from risk/reward ratio
            assertThat(captor.getValue().getStopLoss()).isEqualByComparingTo("970.00");
        }

        @Test
        void zeroOrNegativeStopDistance_fallsBackToDefaultStopPct() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v6", "WIPRO")).thenReturn(List.of());
            when(portfolioRepo.findByPortfolioId("v6"))
                    .thenReturn(Optional.of(portfolio("v6", BigDecimal.valueOf(100000))));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // stopLoss above referencePrice => riskPerShare <= 0 => fallback path
            Signal signal = buySignal("WIPRO", BigDecimal.valueOf(200), null);
            boolean result = service.openPosition("v6", signal, BigDecimal.valueOf(100));

            assertThat(result).isTrue();
        }

        @Test
        void quantityRoundsToZero_returnsFalseWithoutSaving() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v7", "RELIANCE")).thenReturn(List.of());
            // Tiny capital relative to reference price rounds quantity down to 0.
            when(portfolioRepo.findByPortfolioId("v7"))
                    .thenReturn(Optional.of(portfolio("v7", BigDecimal.valueOf(5))));

            Signal signal = buySignal("RELIANCE", BigDecimal.valueOf(2400), BigDecimal.valueOf(2700));
            boolean result = service.openPosition("v7", signal, BigDecimal.valueOf(2500));

            assertThat(result).isFalse();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void freshPortfolioCapitalDropsBelowCost_returnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v8", "TCS")).thenReturn(List.of());
            PaperTradingPortfolioEntity initial = portfolio("v8", BigDecimal.valueOf(100000));
            // The re-fetched "fresh" portfolio inside the retry has insufficient capital.
            PaperTradingPortfolioEntity fresh = portfolio("v8", BigDecimal.valueOf(1));
            when(portfolioRepo.findByPortfolioId("v8")).thenReturn(Optional.of(initial), Optional.of(fresh));

            boolean result = service.openPosition("v8", buySignal("TCS", null, null), BigDecimal.valueOf(100));

            assertThat(result).isFalse();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void repositoryException_caughtAndReturnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v9", "TCS")).thenReturn(List.of());
            when(portfolioRepo.findByPortfolioId("v9"))
                    .thenReturn(Optional.of(portfolio("v9", BigDecimal.valueOf(100000))));
            when(positionRepo.save(any())).thenThrow(new RuntimeException("db down"));

            boolean result = service.openPosition("v9", buySignal("TCS", null, null), BigDecimal.valueOf(100));

            assertThat(result).isFalse();
        }
    }

    // ==================== closePosition ====================

    @Nested
    class ClosePosition {

        private PositionEntity openPosition(long id, String symbol, int qty, BigDecimal entryPrice) {
            PositionEntity e = new PositionEntity();
            e.setId(id);
            e.setSymbol(symbol);
            e.setPortfolioId("v1");
            e.setQuantity(qty);
            e.setEntryPrice(entryPrice);
            e.setStatus("OPEN");
            e.setPositionId("VPOS_v1_" + id);
            return e;
        }

        @Test
        void nullArgs_returnsFalse() {
            assertThat(service.closePosition(null, "TCS", BigDecimal.TEN, "r")).isFalse();
            assertThat(service.closePosition("v1", null, BigDecimal.TEN, "r")).isFalse();
            assertThat(service.closePosition("v1", "TCS", null, "r")).isFalse();
        }

        @Test
        void noOpenPositions_returnsFalse() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of());

            assertThat(service.closePosition("v1", "TCS", BigDecimal.TEN, "manual")).isFalse();
        }

        @Test
        void closesOpenPosition_updatesPortfolioCapitalAndPnl() {
            PositionEntity open = openPosition(1L, "TCS", 10, BigDecimal.valueOf(100));
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(1L)).thenReturn(Optional.of(open));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            PaperTradingPortfolioEntity portfolioEntity = portfolio("v1", BigDecimal.valueOf(50000));
            portfolioEntity.setOpenPositionCount(1);
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolioEntity));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "Target Hit");

            assertThat(result).isTrue();
            ArgumentCaptor<PositionEntity> posCaptor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(posCaptor.capture());
            assertThat(posCaptor.getValue().getStatus()).isEqualTo("CLOSED");
            assertThat(posCaptor.getValue().getRealizedPnL()).isEqualByComparingTo("200");
            assertThat(posCaptor.getValue().getExitReason()).isEqualTo("Target Hit");

            ArgumentCaptor<PaperTradingPortfolioEntity> pfCaptor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(pfCaptor.capture());
            assertThat(pfCaptor.getValue().getCurrentCapital()).isEqualByComparingTo("51200");
            assertThat(pfCaptor.getValue().getOpenPositionCount()).isEqualTo(0);
        }

        @Test
        void positionNotFoundOnRefetch_skipsButReturnsFalseSafely() {
            PositionEntity open = openPosition(2L, "TCS", 10, BigDecimal.valueOf(100));
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(2L)).thenReturn(Optional.empty());

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "manual");

            assertThat(result).isFalse();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void alreadyClosedOnRefetch_isSkipped() {
            PositionEntity open = openPosition(3L, "TCS", 10, BigDecimal.valueOf(100));
            PositionEntity alreadyClosed = openPosition(3L, "TCS", 10, BigDecimal.valueOf(100));
            alreadyClosed.setStatus("CLOSED");
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(3L)).thenReturn(Optional.of(alreadyClosed));

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "manual");

            assertThat(result).isFalse();
        }

        @Test
        void noMatchingPortfolio_stillClosesPositionButSkipsPortfolioUpdate() {
            PositionEntity open = openPosition(4L, "TCS", 10, BigDecimal.valueOf(100));
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(4L)).thenReturn(Optional.of(open));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.empty());

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "manual");

            assertThat(result).isTrue();
            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void exceptionDuringClose_isCaughtPerPosition_returnsFalseWhenAllFail() {
            PositionEntity open = openPosition(5L, "TCS", 10, BigDecimal.valueOf(100));
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(5L)).thenReturn(Optional.of(open));
            when(positionRepo.save(any())).thenThrow(new RuntimeException("db failure"));

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "manual");

            assertThat(result).isFalse();
        }

        @Test
        void portfolioWithNullTotalRealizedPnl_treatedAsZero() {
            PositionEntity open = openPosition(6L, "TCS", 10, BigDecimal.valueOf(100));
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(open));
            when(positionRepo.findById(6L)).thenReturn(Optional.of(open));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            PaperTradingPortfolioEntity portfolioEntity = portfolio("v1", BigDecimal.valueOf(50000));
            portfolioEntity.setTotalRealizedPnl(null);
            portfolioEntity.setOpenPositionCount(0);
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolioEntity));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = service.closePosition("v1", "TCS", BigDecimal.valueOf(120), "manual");

            assertThat(result).isTrue();
            ArgumentCaptor<PaperTradingPortfolioEntity> pfCaptor =
                    ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(pfCaptor.capture());
            assertThat(pfCaptor.getValue().getTotalRealizedPnl()).isEqualByComparingTo("200");
            // openPositionCount floors at 0, never negative
            assertThat(pfCaptor.getValue().getOpenPositionCount()).isEqualTo(0);
        }
    }

    // ==================== evaluateOpenPositions ====================

    @Nested
    class EvaluateOpenPositions {

        private OhlcvCandle candle(BigDecimal open, BigDecimal high, BigDecimal low) {
            return new OhlcvCandle("TCS", LocalDate.now(), open, high, low,
                    open, 1000L, open);
        }

        @Test
        void nullArgs_returnsZero() {
            assertThat(service.evaluateOpenPositions(null, "TCS", candle(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)))
                    .isZero();
            assertThat(service.evaluateOpenPositions("v1", null, candle(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)))
                    .isZero();
            assertThat(service.evaluateOpenPositions("v1", "TCS", null)).isZero();
        }

        @Test
        void noOpenPositions_returnsZero() {
            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of());

            int closed = service.evaluateOpenPositions("v1", "TCS", candle(BigDecimal.TEN, BigDecimal.valueOf(11), BigDecimal.valueOf(9)));

            assertThat(closed).isZero();
        }

        @Test
        void stopLossHit_closesAtStopWhenOpenAboveStop() {
            PositionEntity pos = new PositionEntity();
            pos.setId(10L);
            pos.setSymbol("TCS");
            pos.setPortfolioId("v1");
            pos.setQuantity(5);
            pos.setEntryPrice(BigDecimal.valueOf(100));
            pos.setStatus("OPEN");
            pos.setStopLoss(BigDecimal.valueOf(90));
            pos.setTarget(BigDecimal.valueOf(130));
            pos.setPositionId("VPOS_v1_10");

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));
            when(positionRepo.findById(10L)).thenReturn(Optional.of(pos));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolio("v1", BigDecimal.valueOf(1000))));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // open (95) is above stop (90) -> fill = stop
            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(95), BigDecimal.valueOf(96), BigDecimal.valueOf(85)));

            assertThat(closed).isEqualTo(1);
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("STOPPED");
            assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo("90");
        }

        @Test
        void stopLossHit_fillsAtOpenWhenOpenBelowStop() {
            PositionEntity pos = new PositionEntity();
            pos.setId(11L);
            pos.setSymbol("TCS");
            pos.setPortfolioId("v1");
            pos.setQuantity(5);
            pos.setEntryPrice(BigDecimal.valueOf(100));
            pos.setStatus("OPEN");
            pos.setStopLoss(BigDecimal.valueOf(90));
            pos.setTarget(BigDecimal.valueOf(130));
            pos.setPositionId("VPOS_v1_11");

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));
            when(positionRepo.findById(11L)).thenReturn(Optional.of(pos));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolio("v1", BigDecimal.valueOf(1000))));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            // gaps down: open (80) below stop (90) -> fill = open
            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(80), BigDecimal.valueOf(82), BigDecimal.valueOf(78)));

            assertThat(closed).isEqualTo(1);
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo("80");
        }

        @Test
        void targetHit_fillsAtTargetWhenOpenBelowTarget() {
            PositionEntity pos = new PositionEntity();
            pos.setId(12L);
            pos.setSymbol("TCS");
            pos.setPortfolioId("v1");
            pos.setQuantity(5);
            pos.setEntryPrice(BigDecimal.valueOf(100));
            pos.setStatus("OPEN");
            pos.setStopLoss(BigDecimal.valueOf(90));
            pos.setTarget(BigDecimal.valueOf(130));
            pos.setPositionId("VPOS_v1_12");

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));
            when(positionRepo.findById(12L)).thenReturn(Optional.of(pos));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolio("v1", BigDecimal.valueOf(1000))));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(110), BigDecimal.valueOf(135), BigDecimal.valueOf(105)));

            assertThat(closed).isEqualTo(1);
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("TARGET_HIT");
            assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo("130");
        }

        @Test
        void targetHit_fillsAtOpenWhenGapsAboveTarget() {
            PositionEntity pos = new PositionEntity();
            pos.setId(13L);
            pos.setSymbol("TCS");
            pos.setPortfolioId("v1");
            pos.setQuantity(5);
            pos.setEntryPrice(BigDecimal.valueOf(100));
            pos.setStatus("OPEN");
            pos.setStopLoss(BigDecimal.valueOf(90));
            pos.setTarget(BigDecimal.valueOf(130));
            pos.setPositionId("VPOS_v1_13");

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));
            when(positionRepo.findById(13L)).thenReturn(Optional.of(pos));
            when(positionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(portfolioRepo.findByPortfolioId("v1")).thenReturn(Optional.of(portfolio("v1", BigDecimal.valueOf(1000))));
            when(portfolioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(140), BigDecimal.valueOf(150), BigDecimal.valueOf(138)));

            assertThat(closed).isEqualTo(1);
            ArgumentCaptor<PositionEntity> captor = ArgumentCaptor.forClass(PositionEntity.class);
            verify(positionRepo).save(captor.capture());
            assertThat(captor.getValue().getCurrentPrice()).isEqualByComparingTo("140");
        }

        @Test
        void neitherStopNorTargetHit_returnsZero() {
            PositionEntity pos = new PositionEntity();
            pos.setId(14L);
            pos.setSymbol("TCS");
            pos.setStatus("OPEN");
            pos.setStopLoss(BigDecimal.valueOf(90));
            pos.setTarget(BigDecimal.valueOf(130));

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));

            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(105), BigDecimal.valueOf(110), BigDecimal.valueOf(95)));

            assertThat(closed).isZero();
            verify(positionRepo, never()).save(any());
        }

        @Test
        void nullStopAndTarget_returnsZero() {
            PositionEntity pos = new PositionEntity();
            pos.setId(15L);
            pos.setSymbol("TCS");
            pos.setStatus("OPEN");
            pos.setStopLoss(null);
            pos.setTarget(null);

            when(positionRepo.findOpenByPortfolioIdAndSymbol("v1", "TCS")).thenReturn(List.of(pos));

            int closed = service.evaluateOpenPositions("v1", "TCS",
                    candle(BigDecimal.valueOf(105), BigDecimal.valueOf(200), BigDecimal.valueOf(1)));

            assertThat(closed).isZero();
        }
    }

    // ==================== findClosedTrades ====================

    @Nested
    class FindClosedTrades {

        @Test
        void nullVariantId_returnsEmptyList() {
            assertThat(service.findClosedTrades(null)).isEmpty();
        }

        @Test
        void mapsClosedPositionsToDomain() {
            PositionEntity closed = new PositionEntity();
            closed.setId(1L);
            closed.setSymbol("TCS");
            closed.setBrokerType("PAPER");
            closed.setEntryPrice(BigDecimal.TEN);
            closed.setEntryDate(LocalDate.now());
            closed.setQuantity(5);
            closed.setStatus("CLOSED");
            closed.setPositionId("VPOS_v1_1");
            closed.setDirection("LONG");
            closed.setAveragePrice(BigDecimal.TEN);
            closed.setUnrealizedPnL(BigDecimal.ZERO);
            closed.setRealizedPnL(BigDecimal.valueOf(50));
            closed.setMarginUtilized(BigDecimal.ZERO);

            when(positionRepo.findClosedByPortfolioId("v1")).thenReturn(List.of(closed));

            List<Position> result = service.findClosedTrades("v1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).symbol()).isEqualTo("TCS");
        }

        @Test
        void noClosedTrades_returnsEmptyList() {
            when(positionRepo.findClosedByPortfolioId("v1")).thenReturn(List.of());

            assertThat(service.findClosedTrades("v1")).isEmpty();
        }
    }
}
