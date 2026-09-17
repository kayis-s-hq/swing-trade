package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
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

    private PaperPortfolioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaperPortfolioServiceImpl(portfolioRepo, snapshotRepo, defaultPortfolioStateService, orderRepo);
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
