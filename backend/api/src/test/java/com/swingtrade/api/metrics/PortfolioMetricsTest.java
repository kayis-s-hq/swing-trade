package com.swingtrade.api.metrics;

import com.swingtrade.domain.service.TradingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioMetricsTest {

    @Test
    void registersGaugesBackedByLivePaperState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TradingService engine = mock(TradingService.class);
        when(engine.getOpenPositions()).thenReturn(List.of());
        when(engine.getTotalPnL()).thenReturn(BigDecimal.valueOf(2_500));
        when(engine.getInitialCapital()).thenReturn(BigDecimal.valueOf(100_000));
        when(engine.getTotalValue()).thenReturn(BigDecimal.valueOf(100_000));

        new PortfolioMetrics(registry, engine);

        assertThat(registry.get("portfolio.active_positions").gauge().value()).isZero();
        assertThat(registry.get("portfolio.total_pnl").gauge().value()).isEqualTo(2_500.0);
        assertThat(registry.get("portfolio.value").gauge().value()).isEqualTo(100_000.0);
        assertThat(registry.get("portfolio.daily_return").gauge().value()).isEqualTo(2.5);
    }
}
