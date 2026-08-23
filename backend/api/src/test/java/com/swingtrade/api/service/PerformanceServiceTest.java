package com.swingtrade.api.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.model.Portfolio;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceServiceTest {

    @Nested
    class GetPortfolioPerformance {

        @Test
        void whenNoPortfolio_totalValueIsNull() {
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(null);
            when(engine.getTotalPnL()).thenReturn(null);
            when(engine.getInitialCapital()).thenReturn(BigDecimal.ZERO);

            PositionRepository positionRepo = mock(PositionRepository.class);

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getTotalValue()).isNull();
        }

        @Test
        void whenPortfolioExists_totalValueIsComputed() {
            Portfolio portfolio = new Portfolio("test", BigDecimal.valueOf(1000000));
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(portfolio);
            // PerformanceService depends on the TradingService interface (not the concrete
            // PaperTradingEngine), so it can only reach totalValue via
            // TradingService.getTotalValue() — getPortfolio() isn't on that interface and
            // can't be, since core (where TradingService lives) can't depend on broker's
            // Portfolio type. In production PaperTradingEngine.getTotalValue() delegates to
            // portfolio.getTotalValue(), which for a fresh portfolio with no positions equals
            // the initial capital — stub that directly instead of the unreachable
            // getPortfolio().
            when(engine.getTotalValue()).thenReturn(portfolio.getTotalValue());
            when(engine.getTotalPnL()).thenReturn(BigDecimal.valueOf(50000));
            when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(1000000));

            PositionRepository positionRepo = mock(PositionRepository.class);
            when(positionRepo.findByStatus("CLOSED")).thenReturn(List.of());
            when(positionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(positionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getTotalValue()).isEqualTo(BigDecimal.valueOf(1000000));
            assertThat(response.getTotalPnL()).isEqualTo(BigDecimal.valueOf(50000));
        }

        @Test
        void whenPortfolioExists_totalPnLFromEngine() {
            Portfolio portfolio = new Portfolio("test", BigDecimal.valueOf(1000000));
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(portfolio);
            when(engine.getTotalPnL()).thenReturn(BigDecimal.valueOf(75000));
            when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(1000000));

            PositionRepository positionRepo = mock(PositionRepository.class);
            when(positionRepo.findByStatus("CLOSED")).thenReturn(List.of());
            when(positionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(positionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getTotalPnL()).isEqualTo(BigDecimal.valueOf(75000));
        }

        @Test
        void whenPortfolioExists_averageWinFromClosedPositions() {
            Portfolio portfolio = new Portfolio("test", BigDecimal.valueOf(1000000));
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(portfolio);
            when(engine.getTotalPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(1000000));

            PositionEntity winPos = mock(PositionEntity.class);
            when(winPos.getRealizedPnL()).thenReturn(BigDecimal.valueOf(3000));
            when(winPos.getBrokerType()).thenReturn("PAPER");

            PositionRepository positionRepo = mock(PositionRepository.class);
            when(positionRepo.findByStatus("CLOSED")).thenReturn(List.of(winPos));
            when(positionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(positionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getAverageWin()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        void whenPortfolioExists_averageLossFromClosedPositions() {
            Portfolio portfolio = new Portfolio("test", BigDecimal.valueOf(1000000));
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(portfolio);
            when(engine.getTotalPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(1000000));

            PositionEntity lossPos = mock(PositionEntity.class);
            when(lossPos.getRealizedPnL()).thenReturn(BigDecimal.valueOf(-2000));
            when(lossPos.getBrokerType()).thenReturn("PAPER");

            PositionRepository positionRepo = mock(PositionRepository.class);
            when(positionRepo.findByStatus("CLOSED")).thenReturn(List.of(lossPos));
            when(positionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(positionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getAverageLoss()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }

        @Test
        void whenPortfolioExists_profitFactorComputed() {
            Portfolio portfolio = new Portfolio("test", BigDecimal.valueOf(1000000));
            PaperTradingEngine engine = mock(PaperTradingEngine.class);
            when(engine.getPortfolio()).thenReturn(portfolio);
            when(engine.getTotalPnL()).thenReturn(BigDecimal.ZERO);
            when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(1000000));

            PositionEntity winPos = mock(PositionEntity.class);
            when(winPos.getRealizedPnL()).thenReturn(BigDecimal.valueOf(3000));
            when(winPos.getBrokerType()).thenReturn("PAPER");

            PositionEntity lossPos = mock(PositionEntity.class);
            when(lossPos.getRealizedPnL()).thenReturn(BigDecimal.valueOf(-2000));
            when(lossPos.getBrokerType()).thenReturn("PAPER");

            PositionRepository positionRepo = mock(PositionRepository.class);
            when(positionRepo.findByStatus("CLOSED")).thenReturn(List.of(winPos, lossPos));
            when(positionRepo.findByStatus("STOPPED")).thenReturn(List.of());
            when(positionRepo.findByStatus("TARGET_HIT")).thenReturn(List.of());

            PerformanceService service = new PerformanceService(engine, positionRepo);

            var response = service.getPortfolioPerformance();

            assertThat(response.getProfitFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.5));
        }
    }
}