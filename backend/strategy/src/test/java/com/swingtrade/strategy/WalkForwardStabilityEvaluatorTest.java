package com.swingtrade.strategy;

import com.swingtrade.domain.StrategyConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalkForwardStabilityEvaluatorTest {
    private static final LocalDate D = LocalDate.of(2025, 1, 1);

    @Test
    void expandsDeterministicallyAndRejectsAnUnboundedGrid() {
        assertThat(BoundedParameterGrid.expand(Map.of("z", List.of(1, 2), "a", List.of("x", "y")), 4))
            .containsExactly(Map.of("a", "x", "z", 1), Map.of("a", "x", "z", 2),
                Map.of("a", "y", "z", 1), Map.of("a", "y", "z", 2));
        assertThatThrownBy(() -> BoundedParameterGrid.expand(Map.of("p", java.util.stream.IntStream.range(0, 65)
            .boxed().toList()), 64)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCandidates");
    }

    @Test
    void selectsUsingTrainOnlyPerFoldAndReportsOosStability() {
        StrategyConfig base = StrategyConfig.create("TEST", 1, "TEST", Map.of("threshold", 10), Map.of(),
            StrategyConfig.Mode.BACKTEST_ONLY, BigDecimal.ONE, false, "test", D.atStartOfDay());
        BacktestConfig backtest = BacktestConfig.defaults();
        List<WalkForwardStabilityEvaluator.Window> windows = List.of(
            new WalkForwardStabilityEvaluator.Window(D, D.plusDays(9), D.plusDays(10), D.plusDays(19)),
            new WalkForwardStabilityEvaluator.Window(D.plusDays(20), D.plusDays(29), D.plusDays(30), D.plusDays(39)));
        AtomicReference<BacktestConfig> receivedConfig = new AtomicReference<>();

        WalkForwardStabilityEvaluator.Result result = new WalkForwardStabilityEvaluator().evaluate(
            base, backtest, Map.of("threshold", List.of(1, 2)), windows, (config, configUsed, start, end) -> {
                receivedConfig.set(configUsed);
                boolean train = start.equals(D) || start.equals(D.plusDays(20));
                boolean first = config.params().get("threshold").equals(1);
                return result(first, train ? (first ? 2.0 : 1.0) : (first ? 0.6 : 0.8),
                    train ? 0.0 : (first ? 0.04 : 0.03), train ? 1.0 : 3.0);
            });

        assertThat(receivedConfig.get()).isSameAs(backtest);
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.folds()).allMatch(f -> f.selectedParameters().containsKey("threshold"));
        assertThat(result.selectedConfig().params()).containsEntry("threshold", 2);
        assertThat(result.validationSharpeStdDev()).isLessThan(1.0);
        assertThat(result.deflatedSharpePValue()).isBetween(0.0, 1.0);
        assertThat(result.limitations()).isNotEmpty();
    }

    @Test
    void rejectsAHighTrainSharpeThatDoesNotSurviveValidation() {
        StrategyConfig base = StrategyConfig.create("TEST", 1, "TEST", Map.of(), Map.of(),
            StrategyConfig.Mode.BACKTEST_ONLY, BigDecimal.ONE, false, null, D.atStartOfDay());
        List<WalkForwardStabilityEvaluator.Window> windows = List.of(
            new WalkForwardStabilityEvaluator.Window(D, D.plusDays(9), D.plusDays(10), D.plusDays(19)),
            new WalkForwardStabilityEvaluator.Window(D.plusDays(20), D.plusDays(29), D.plusDays(30), D.plusDays(39)));

        WalkForwardStabilityEvaluator.Result result = new WalkForwardStabilityEvaluator().evaluate(base,
            BacktestConfig.defaults(), Map.of("p", List.of(1, 2)), windows, (config, ignored, start, end) -> {
                boolean winner = config.params().get("p").equals(1);
                boolean train = start.equals(D) || start.equals(D.plusDays(20));
                return result(winner, train ? (winner ? 4.0 : 0.0) : (winner ? 0.4 : 0.1),
                    0.0, train ? 1.0 : 0.0);
            });

        assertThat(result.decision()).isEqualTo(WalkForwardStabilityEvaluator.Decision.REJECT_SPIKY_OPTIMUM);
    }

    private static BacktestResult result(boolean preferred, double sharpe, double totalReturn,
                                         double maxDrawdown) {
        return new BacktestResult("TEST", 20, 12, 8, 60, 2, 1, maxDrawdown, sharpe,
            totalReturn, 0.5, List.of());
    }
}
