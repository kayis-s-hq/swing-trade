package com.swingtrade.api.service;

import com.swingtrade.api.dto.strategy.PromotionEligibilityResponse;
import com.swingtrade.data.entity.StrategyExperimentLogEntity;
import com.swingtrade.data.repository.StrategyExperimentLogRepository;
import com.swingtrade.domain.ShadowClosedTrade;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.service.PaperPortfolioService;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEligibilityServiceTest {

    @Mock
    private StrategyConfigStore configStore;
    @Mock
    private PaperPortfolioService paperPortfolioService;
    @Mock
    private StrategyExperimentLogRepository experimentLogRepository;

    private PromotionEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new PromotionEligibilityService(configStore, paperPortfolioService, experimentLogRepository);
    }

    private StrategyConfig config(String variantId, int version, StrategyMode mode, LocalDateTime createdAt) {
        return new StrategyConfig(1L, variantId, version, "BREAKOUT", Map.of(), Map.of(),
            "hash-" + variantId + "-" + version, mode, new BigDecimal("500000"), true, null, null, createdAt);
    }

    private ShadowClosedTrade trade(String portfolioId, String symbol, LocalDate entry, LocalDate exit,
                                     BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal pnl) {
        return new ShadowClosedTrade(portfolioId, symbol, entry, exit, entryPrice,
            entryPrice.add(BigDecimal.TEN), stopLoss, entryPrice.add(BigDecimal.valueOf(20)), 10,
            "TARGET_HIT", pnl);
    }

    private List<ShadowClosedTrade> manyTrades(String portfolioId, int count, double pnlEach) {
        List<ShadowClosedTrade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(trade(portfolioId, "SYM" + i, LocalDate.of(2026, 1, 1).plusDays(i),
                LocalDate.of(2026, 1, 5).plusDays(i), BigDecimal.valueOf(100), BigDecimal.valueOf(90),
                BigDecimal.valueOf(pnlEach)));
        }
        return trades;
    }

    @Test
    void noChampionSet_returns404() {
        when(configStore.findCurrentChampion()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate("CHALLENGER"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No champion is currently set");
    }

    @Test
    void unknownChallenger_returns404() {
        StrategyConfig champion = config("CHAMPION", 1, StrategyMode.CHAMPION, LocalDateTime.now());
        when(configStore.findCurrentChampion()).thenReturn(Optional.of(champion));
        when(configStore.findCurrent("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate("UNKNOWN"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown variant");
    }

    @Test
    void insufficientTenure_shortCircuitsToInsufficientSample_andPassesNullsThrough() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(5);
        StrategyConfig champion = config("CHAMPION", 1, StrategyMode.CHAMPION, LocalDateTime.now().minusDays(400));
        StrategyConfig challenger = config("CHALLENGER", 3, StrategyMode.SHADOW, createdAt);

        when(configStore.findCurrentChampion()).thenReturn(Optional.of(champion));
        when(configStore.findCurrent("CHALLENGER")).thenReturn(Optional.of(challenger));
        when(paperPortfolioService.findClosedTrades("CHALLENGER")).thenReturn(List.of());
        when(paperPortfolioService.findClosedTrades("CHAMPION")).thenReturn(List.of());
        when(experimentLogRepository.findTopByVariantIdAndVersionOrderByCreatedAtDesc("CHALLENGER", 3))
            .thenReturn(Optional.empty());
        when(experimentLogRepository.findTopByVariantIdAndVersionOrderByCreatedAtDesc("CHAMPION", 1))
            .thenReturn(Optional.empty());

        PromotionEligibilityResponse response = service.evaluate("CHALLENGER");

        assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
        assertThat(response.challengerVariantId()).isEqualTo("CHALLENGER");
        assertThat(response.championVariantId()).isEqualTo("CHAMPION");
        assertThat(response.tenureCalendarDays()).isEqualTo(5);
        // Condition 4 (walk-forward) has no data yet: not met, but no exception.
        assertThat(response.walkForwardAndOverfitting().met()).isFalse();
        assertThat(response.walkForwardAndOverfitting().actualValue()).contains("no walk-forward/DSR data available yet");
    }

    @Test
    void enoughTenureAndTrades_readsWalkForwardStatsFromExperimentLog() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(90);
        StrategyConfig champion = config("CHAMPION", 1, StrategyMode.CHAMPION, LocalDateTime.now().minusDays(400));
        StrategyConfig challenger = config("CHALLENGER", 1, StrategyMode.SHADOW, createdAt);

        when(configStore.findCurrentChampion()).thenReturn(Optional.of(champion));
        when(configStore.findCurrent("CHALLENGER")).thenReturn(Optional.of(challenger));
        when(paperPortfolioService.findClosedTrades("CHALLENGER")).thenReturn(manyTrades("CHALLENGER", 35, 500.0));
        when(paperPortfolioService.findClosedTrades("CHAMPION")).thenReturn(manyTrades("CHAMPION", 35, 100.0));

        StrategyExperimentLogEntity challengerLog = new StrategyExperimentLogEntity();
        Map<String, Object> challengerMetrics = new LinkedHashMap<>();
        challengerMetrics.put("oosSharpe", 1.2);
        challengerMetrics.put("dsrPValue", 0.05);
        challengerLog.setMetrics(challengerMetrics);

        StrategyExperimentLogEntity championLog = new StrategyExperimentLogEntity();
        Map<String, Object> championMetrics = new LinkedHashMap<>();
        championMetrics.put("oosSharpe", 0.5);
        championMetrics.put("dsrPValue", 0.2);
        championLog.setMetrics(championMetrics);

        when(experimentLogRepository.findTopByVariantIdAndVersionOrderByCreatedAtDesc("CHALLENGER", 1))
            .thenReturn(Optional.of(challengerLog));
        when(experimentLogRepository.findTopByVariantIdAndVersionOrderByCreatedAtDesc("CHAMPION", 1))
            .thenReturn(Optional.of(championLog));

        PromotionEligibilityResponse response = service.evaluate("CHALLENGER");

        assertThat(response.tenureCalendarDays()).isEqualTo(90);
        assertThat(response.status()).isIn("ELIGIBLE", "NOT_ELIGIBLE");
        assertThat(response.walkForwardAndOverfitting().actualValue()).contains("oosSharpe=1.200", "dsrPValue=0.050");
        assertThat(response.walkForwardAndOverfitting().met()).isTrue();
    }
}
