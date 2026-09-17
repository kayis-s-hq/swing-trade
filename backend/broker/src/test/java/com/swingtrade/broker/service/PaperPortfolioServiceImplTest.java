package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.entity.ShadowPositionEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.broker.repository.ShadowPositionRepository;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperPortfolioServiceImplTest {

    @Mock
    private PaperTradingPortfolioRepository portfolioRepo;
    @Mock
    private PaperTradingSnapshotRepository snapshotRepo;
    @Mock
    private PaperTradingStateService defaultPortfolioStateService;
    @Mock
    private PaperTradingOrderRepository orderRepo;
    @Mock
    private ShadowPositionRepository shadowPositionRepo;

    private PaperPortfolioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaperPortfolioServiceImpl(portfolioRepo, snapshotRepo, defaultPortfolioStateService, orderRepo,
            shadowPositionRepo);
    }

    private ShadowPositionEntity openPosition(String portfolioId, String symbol, BigDecimal entryPrice,
                                               BigDecimal stopLoss, BigDecimal target, int quantity) {
        ShadowPositionEntity entity = new ShadowPositionEntity();
        entity.setId(1L);
        entity.setPortfolioId(portfolioId);
        entity.setSymbol(symbol);
        entity.setEntryDate(java.time.LocalDate.of(2026, 1, 1));
        entity.setEntryPrice(entryPrice);
        entity.setStopLoss(stopLoss);
        entity.setTarget(target);
        entity.setQuantity(quantity);
        entity.setHighWaterMark(entryPrice);
        entity.setStatus(ShadowPositionEntity.STATUS_OPEN);
        return entity;
    }

    @Nested
    class ExecuteVariantExit {

        @Test
        void closesOpenPositionAndCreditsCapital() {
            ShadowPositionEntity position = openPosition("PULLBACK_B", "TCS",
                new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("110.00"), 100);
            when(shadowPositionRepo.findByPortfolioIdAndSymbolAndStatus("PULLBACK_B", "TCS",
                ShadowPositionEntity.STATUS_OPEN)).thenReturn(Optional.of(position));

            PaperTradingPortfolioEntity portfolio = new PaperTradingPortfolioEntity();
            portfolio.setPortfolioId("PULLBACK_B");
            portfolio.setCurrentCapital(new BigDecimal("240000.00"));
            portfolio.setTotalRealizedPnl(BigDecimal.ZERO);
            portfolio.setOpenPositionCount(1);
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(portfolio));

            boolean closed = service.executeVariantExit("PULLBACK_B", "TCS", new BigDecimal("94.00"), "STOP_LOSS");

            assertThat(closed).isTrue();
            assertThat(position.getStatus()).isEqualTo(ShadowPositionEntity.STATUS_CLOSED);
            assertThat(position.getExitReason()).isEqualTo("STOP_LOSS");

            ArgumentCaptor<PaperTradingPortfolioEntity> portfolioCaptor =
                ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(portfolioCaptor.capture());
            // proceeds = 94*100=9400, commission=100*0.05=5, net=9395; capital 240000+9395=249395
            assertThat(portfolioCaptor.getValue().getCurrentCapital()).isEqualByComparingTo("249395.00");
            assertThat(portfolioCaptor.getValue().getOpenPositionCount()).isEqualTo(0);
            // pnl = net(9395) - entryValue(100*100=10000) = -605
            assertThat(position.getPnl()).isEqualByComparingTo("-605.00");

            verify(orderRepo).save(any(PaperTradingOrderEntity.class));
        }

        @Test
        void isIdempotentWhenPositionAlreadyClosed() {
            when(shadowPositionRepo.findByPortfolioIdAndSymbolAndStatus("PULLBACK_B", "TCS",
                ShadowPositionEntity.STATUS_OPEN)).thenReturn(Optional.empty());

            boolean closed = service.executeVariantExit("PULLBACK_B", "TCS", new BigDecimal("94.00"), "STOP_LOSS");

            assertThat(closed).isFalse();
            verify(portfolioRepo, never()).save(any());
            verify(orderRepo, never()).save(any());
        }

        @Test
        void twoVariantsExitIndependentlyWithoutCrossContamination() {
            ShadowPositionEntity positionA = openPosition("VARIANT_A", "TCS",
                new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("110.00"), 100);
            ShadowPositionEntity positionB = openPosition("VARIANT_B", "TCS",
                new BigDecimal("200.00"), new BigDecimal("190.00"), new BigDecimal("220.00"), 50);
            when(shadowPositionRepo.findByPortfolioIdAndSymbolAndStatus("VARIANT_A", "TCS",
                ShadowPositionEntity.STATUS_OPEN)).thenReturn(Optional.of(positionA));

            PaperTradingPortfolioEntity portfolioA = new PaperTradingPortfolioEntity();
            portfolioA.setPortfolioId("VARIANT_A");
            portfolioA.setCurrentCapital(new BigDecimal("240000.00"));
            portfolioA.setTotalRealizedPnl(BigDecimal.ZERO);
            portfolioA.setOpenPositionCount(1);
            when(portfolioRepo.findByPortfolioId("VARIANT_A")).thenReturn(Optional.of(portfolioA));

            boolean closedA = service.executeVariantExit("VARIANT_A", "TCS", new BigDecimal("120.00"), "TARGET_HIT");

            assertThat(closedA).isTrue();
            assertThat(positionA.getStatus()).isEqualTo(ShadowPositionEntity.STATUS_CLOSED);
            // VARIANT_B's position must remain untouched by VARIANT_A's exit.
            assertThat(positionB.getStatus()).isEqualTo(ShadowPositionEntity.STATUS_OPEN);
        }
    }

    @Nested
    class FindClosedTrades {

        @Test
        void returnsClosedRoundTripsForPortfolio() {
            ShadowPositionEntity closed = openPosition("PULLBACK_B", "TCS",
                new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("110.00"), 100);
            closed.setStatus(ShadowPositionEntity.STATUS_CLOSED);
            closed.setExitDate(java.time.LocalDate.of(2026, 1, 10));
            closed.setExitPrice(new BigDecimal("112.00"));
            closed.setExitReason("TARGET_HIT");
            closed.setPnl(new BigDecimal("1195.00"));
            when(shadowPositionRepo.findByPortfolioIdAndStatusOrderByExitDateDesc(
                "PULLBACK_B", ShadowPositionEntity.STATUS_CLOSED)).thenReturn(List.of(closed));

            List<ShadowClosedTrade> trades = service.findClosedTrades("PULLBACK_B");

            assertThat(trades).hasSize(1);
            ShadowClosedTrade trade = trades.get(0);
            assertThat(trade.symbol()).isEqualTo("TCS");
            assertThat(trade.exitReason()).isEqualTo("TARGET_HIT");
            assertThat(trade.pnl()).isEqualByComparingTo("1195.00");
            assertThat(trade.isWin()).isTrue();
        }
    }

    @Nested
    class ExecuteVariantBuyPersistsShadowPosition {

        @Test
        void persistsOpenShadowPositionOnSuccessfulBuy() {
            PaperTradingPortfolioEntity portfolio = new PaperTradingPortfolioEntity();
            portfolio.setPortfolioId("PULLBACK_B");
            portfolio.setCurrentCapital(new BigDecimal("500000.00"));
            portfolio.setOpenPositionCount(0);
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(portfolio));
            when(orderRepo.findByPortfolioIdOrderByCreatedAtDesc("PULLBACK_B")).thenReturn(List.of());

            Signal signal = Signal.createWithLevels("TCS", java.time.LocalDate.of(2026, 1, 1),
                Signal.SignalType.BUY, new BigDecimal("0.8"), "test",
                new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("110.00"));
            Signal signalWithId = new Signal(42L, signal.symbol(), signal.date(), signal.type(), signal.confidence(),
                signal.reasoning(), signal.entryPrice(), signal.stopLoss(), signal.target(), signal.riskReward(),
                signal.indicators(), signal.generatedAt(), signal.sentimentScore(), signal.sentimentReasoning());

            boolean success = service.executeVariantBuy("PULLBACK_B", signalWithId, new BigDecimal("100.00"));

            assertThat(success).isTrue();
            ArgumentCaptor<ShadowPositionEntity> captor = ArgumentCaptor.forClass(ShadowPositionEntity.class);
            verify(shadowPositionRepo).save(captor.capture());
            ShadowPositionEntity saved = captor.getValue();
            assertThat(saved.getSymbol()).isEqualTo("TCS");
            assertThat(saved.getStopLoss()).isEqualByComparingTo("95.00");
            assertThat(saved.getTarget()).isEqualByComparingTo("110.00");
            assertThat(saved.getStatus()).isEqualTo(ShadowPositionEntity.STATUS_OPEN);
        }
    }

    @Nested
    class EnsurePortfolio {

        @Test
        void createsNewPortfolioRowWhenNoneExists() {
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.empty());
            when(portfolioRepo.findMaxId()).thenReturn(1L);

            service.ensurePortfolio("PULLBACK_B", new BigDecimal("250000"));

            ArgumentCaptor<PaperTradingPortfolioEntity> captor = ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            PaperTradingPortfolioEntity saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(2L);
            assertThat(saved.getPortfolioId()).isEqualTo("PULLBACK_B");
            assertThat(saved.getInitialCapital()).isEqualByComparingTo("250000");
            assertThat(saved.getCurrentCapital()).isEqualByComparingTo("250000");
            assertThat(saved.getDailyLossThresholdPct()).isEqualByComparingTo("0.04");
        }

        @Test
        void isIdempotentWhenPortfolioAlreadyExists() {
            when(portfolioRepo.findByPortfolioId("PULLBACK_B"))
                .thenReturn(Optional.of(new PaperTradingPortfolioEntity()));

            service.ensurePortfolio("PULLBACK_B", new BigDecimal("250000"));
            service.ensurePortfolio("PULLBACK_B", new BigDecimal("250000"));

            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void defaultsPaperCapitalWhenNull() {
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.empty());
            when(portfolioRepo.findMaxId()).thenReturn(0L);

            service.ensurePortfolio("PULLBACK_B", null);

            ArgumentCaptor<PaperTradingPortfolioEntity> captor = ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(captor.capture());
            assertThat(captor.getValue().getInitialCapital()).isEqualByComparingTo("500000");
        }
    }

    @Nested
    class DailyLossBreach {

        @Test
        void notBreachedWhenPortfolioDoesNotExist() {
            when(portfolioRepo.findByPortfolioId("UNKNOWN")).thenReturn(Optional.empty());

            assertThat(service.isDailyLossBreached("UNKNOWN")).isFalse();
        }

        @Test
        void breachedWhenLossExceedsThreshold() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("100000"));
            entity.setCurrentCapital(new BigDecimal("94000")); // -6% vs initial baseline (no prior snapshot)
            entity.setTotalUnrealizedPnL(BigDecimal.ZERO);
            entity.setDailyLossThresholdPct(new BigDecimal("0.04"));
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));
            when(snapshotRepo.findFirstByPortfolioIdAndSnapshotTimeBeforeOrderBySnapshotTimeDesc(eq("PULLBACK_B"), any()))
                .thenReturn(Optional.empty());

            assertThat(service.isDailyLossBreached("PULLBACK_B")).isTrue();
        }

        @Test
        void notBreachedWhenLossWithinThreshold() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("100000"));
            entity.setCurrentCapital(new BigDecimal("99000")); // -1%
            entity.setTotalUnrealizedPnL(BigDecimal.ZERO);
            entity.setDailyLossThresholdPct(new BigDecimal("0.04"));
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));
            when(snapshotRepo.findFirstByPortfolioIdAndSnapshotTimeBeforeOrderBySnapshotTimeDesc(eq("PULLBACK_B"), any()))
                .thenReturn(Optional.empty());

            assertThat(service.isDailyLossBreached("PULLBACK_B")).isFalse();
        }

        @Test
        void notBreachedWhenPortfolioIsUp() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("100000"));
            entity.setCurrentCapital(new BigDecimal("105000"));
            entity.setTotalUnrealizedPnL(BigDecimal.ZERO);
            entity.setDailyLossThresholdPct(new BigDecimal("0.04"));
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));
            when(snapshotRepo.findFirstByPortfolioIdAndSnapshotTimeBeforeOrderBySnapshotTimeDesc(eq("PULLBACK_B"), any()))
                .thenReturn(Optional.empty());

            assertThat(service.isDailyLossBreached("PULLBACK_B")).isFalse();
        }
    }

    @Nested
    class SnapshotAllPortfolios {

        @Test
        void snapshotsDefaultPortfolioThroughStateServiceAndShadowsDirectly() {
            PaperTradingPortfolioEntity defaultPortfolio = new PaperTradingPortfolioEntity();
            defaultPortfolio.setPortfolioId("default");
            PaperTradingPortfolioEntity shadow = new PaperTradingPortfolioEntity();
            shadow.setPortfolioId("PULLBACK_B");
            shadow.setInitialCapital(new BigDecimal("250000"));
            shadow.setCurrentCapital(new BigDecimal("250000"));
            shadow.setTotalRealizedPnl(BigDecimal.ZERO);
            shadow.setTotalUnrealizedPnL(BigDecimal.ZERO);
            when(portfolioRepo.findAllByOrderByPortfolioIdAsc()).thenReturn(List.of(defaultPortfolio, shadow));

            service.snapshotAllPortfolios();

            verify(defaultPortfolioStateService, times(1)).saveSnapshot();
            ArgumentCaptor<PaperTradingSnapshotEntity> captor = ArgumentCaptor.forClass(PaperTradingSnapshotEntity.class);
            verify(snapshotRepo).save(captor.capture());
            assertThat(captor.getValue().getPortfolioId()).isEqualTo("PULLBACK_B");
            assertThat(captor.getValue().getTotalValue()).isEqualByComparingTo("250000");
        }

        @Test
        void continuesSnapshottingRemainingPortfoliosWhenOneFails() {
            PaperTradingPortfolioEntity broken = new PaperTradingPortfolioEntity();
            broken.setPortfolioId("BROKEN");
            PaperTradingPortfolioEntity ok = new PaperTradingPortfolioEntity();
            ok.setPortfolioId("OK");
            ok.setInitialCapital(new BigDecimal("100000"));
            ok.setCurrentCapital(new BigDecimal("100000"));
            when(portfolioRepo.findAllByOrderByPortfolioIdAsc()).thenReturn(List.of(broken, ok));
            when(snapshotRepo.save(any())).thenThrow(new RuntimeException("db down")).thenAnswer(inv -> inv.getArgument(0));

            service.snapshotAllPortfolios();

            verify(snapshotRepo, times(2)).save(any());
        }
    }

    @Nested
    class ExecuteVariantBuy {

        private final Signal signal = new Signal(
            1L, "RELIANCE", java.time.LocalDate.now(), Signal.SignalType.BUY,
            new BigDecimal("0.8"), "setup", new BigDecimal("100"), new BigDecimal("95"),
            new BigDecimal("110"), new BigDecimal("2"), "{}", java.time.LocalDate.now(), null, null);

        @Test
        void debitsPortfolioCapitalAndPersistsAPortfolioTaggedOrder() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("500000"));
            entity.setCurrentCapital(new BigDecimal("500000"));
            entity.setOpenPositionCount(0);
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));
            when(orderRepo.findByPortfolioIdOrderByCreatedAtDesc("PULLBACK_B")).thenReturn(List.of());

            boolean result = service.executeVariantBuy("PULLBACK_B", signal, new BigDecimal("100"));

            assertThat(result).isTrue();
            ArgumentCaptor<PaperTradingPortfolioEntity> portfolioCaptor =
                ArgumentCaptor.forClass(PaperTradingPortfolioEntity.class);
            verify(portfolioRepo).save(portfolioCaptor.capture());
            assertThat(portfolioCaptor.getValue().getCurrentCapital()).isLessThan(new BigDecimal("500000"));
            assertThat(portfolioCaptor.getValue().getOpenPositionCount()).isEqualTo(1);

            ArgumentCaptor<PaperTradingOrderEntity> orderCaptor = ArgumentCaptor.forClass(PaperTradingOrderEntity.class);
            verify(orderRepo).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getPortfolioId()).isEqualTo("PULLBACK_B");
            assertThat(orderCaptor.getValue().getSymbol()).isEqualTo("RELIANCE");
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo("FILLED");
        }

        @Test
        void rejectsWhenPortfolioDoesNotExist() {
            when(portfolioRepo.findByPortfolioId("UNKNOWN")).thenReturn(Optional.empty());

            boolean result = service.executeVariantBuy("UNKNOWN", signal, new BigDecimal("100"));

            assertThat(result).isFalse();
            verify(orderRepo, never()).save(any());
        }

        @Test
        void rejectsWhenCapitalDepleted() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("500000"));
            entity.setCurrentCapital(BigDecimal.ZERO);
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));

            boolean result = service.executeVariantBuy("PULLBACK_B", signal, new BigDecimal("100"));

            assertThat(result).isFalse();
            verify(orderRepo, never()).save(any());
            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void isIdempotentForAnAlreadyExecutedSignal() {
            PaperTradingPortfolioEntity entity = new PaperTradingPortfolioEntity();
            entity.setPortfolioId("PULLBACK_B");
            entity.setInitialCapital(new BigDecimal("500000"));
            entity.setCurrentCapital(new BigDecimal("500000"));
            when(portfolioRepo.findByPortfolioId("PULLBACK_B")).thenReturn(Optional.of(entity));
            PaperTradingOrderEntity existing = new PaperTradingOrderEntity();
            existing.setSignalId("1");
            when(orderRepo.findByPortfolioIdOrderByCreatedAtDesc("PULLBACK_B")).thenReturn(List.of(existing));

            boolean result = service.executeVariantBuy("PULLBACK_B", signal, new BigDecimal("100"));

            assertThat(result).isTrue();
            verify(orderRepo, never()).save(any());
            verify(portfolioRepo, never()).save(any());
        }

        @Test
        void twoDifferentPortfoliosExecuteIndependentlyWithoutSharedState() {
            PaperTradingPortfolioEntity championPortfolio = new PaperTradingPortfolioEntity();
            championPortfolio.setPortfolioId("CHAMPION_X");
            championPortfolio.setInitialCapital(new BigDecimal("500000"));
            championPortfolio.setCurrentCapital(new BigDecimal("500000"));
            PaperTradingPortfolioEntity shadowPortfolio = new PaperTradingPortfolioEntity();
            shadowPortfolio.setPortfolioId("SHADOW_Y");
            shadowPortfolio.setInitialCapital(new BigDecimal("300000"));
            shadowPortfolio.setCurrentCapital(new BigDecimal("300000"));
            when(portfolioRepo.findByPortfolioId("CHAMPION_X")).thenReturn(Optional.of(championPortfolio));
            when(portfolioRepo.findByPortfolioId("SHADOW_Y")).thenReturn(Optional.of(shadowPortfolio));
            when(orderRepo.findByPortfolioIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

            boolean championResult = service.executeVariantBuy("CHAMPION_X", signal, new BigDecimal("100"));
            boolean shadowResult = service.executeVariantBuy("SHADOW_Y", signal, new BigDecimal("100"));

            assertThat(championResult).isTrue();
            assertThat(shadowResult).isTrue();
            // Each portfolio's own capital was debited independently - neither call affected the
            // other portfolio's entity.
            assertThat(championPortfolio.getCurrentCapital()).isLessThan(new BigDecimal("500000"));
            assertThat(shadowPortfolio.getCurrentCapital()).isLessThan(new BigDecimal("300000"));
            verify(portfolioRepo).save(championPortfolio);
            verify(portfolioRepo).save(shadowPortfolio);
        }
    }
}
