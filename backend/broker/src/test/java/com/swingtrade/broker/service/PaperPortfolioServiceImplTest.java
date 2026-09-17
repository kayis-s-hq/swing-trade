package com.swingtrade.broker.service;

import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
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

    private PaperPortfolioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaperPortfolioServiceImpl(portfolioRepo, snapshotRepo, defaultPortfolioStateService);
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
}
