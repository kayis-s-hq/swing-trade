package com.swingtrade.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionEligibilityCheckerTest {

    private final PromotionEligibilityChecker checker = new PromotionEligibilityChecker(new Random(42));

    private static List<BigDecimal> constantTrades(int count, double value) {
        return IntStream.range(0, count).mapToObj(i -> BigDecimal.valueOf(value)).collect(Collectors.toList());
    }

    private static List<BigDecimal> jitteredTrades(int count, double base, double jitter, long seed) {
        Random r = new Random(seed);
        return IntStream.range(0, count)
            .mapToObj(i -> BigDecimal.valueOf(base + (r.nextDouble() - 0.5) * jitter))
            .collect(Collectors.toList());
    }

    @Test
    void insufficientSampleWhenTenureBelowSixtyDays() {
        var input = new PromotionEligibilityChecker.Input(59, constantTrades(30, 10), constantTrades(30, 5),
            5.0, 5.0, 1.0, 0.01, 0.5);
        var result = checker.evaluate(input);
        assertThat(result.status()).isEqualTo(PromotionEligibilityChecker.Status.INSUFFICIENT_SAMPLE);
    }

    @Test
    void insufficientSampleWhenTooFewClosedTrades() {
        var input = new PromotionEligibilityChecker.Input(90, constantTrades(29, 10), constantTrades(30, 5),
            5.0, 5.0, 1.0, 0.01, 0.5);
        var result = checker.evaluate(input);
        assertThat(result.status()).isEqualTo(PromotionEligibilityChecker.Status.INSUFFICIENT_SAMPLE);
    }

    @Test
    void eligibleWhenAllFourConditionsHold() {
        List<BigDecimal> challenger = jitteredTrades(30, 20.0, 4.0, 1);
        List<BigDecimal> champion = jitteredTrades(30, 5.0, 4.0, 2);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            8.0, 10.0, 1.5, 0.02, 0.5);
        var result = checker.evaluate(input);
        assertThat(result.status()).isEqualTo(PromotionEligibilityChecker.Status.ELIGIBLE);
        assertThat(result.conditions()).hasSize(4);
        assertThat(result.conditions()).allMatch(PromotionEligibilityChecker.ConditionResult::met);
    }

    @Test
    void notEligibleWhenDrawdownExceedsMultiplier() {
        List<BigDecimal> challenger = jitteredTrades(30, 20.0, 4.0, 3);
        List<BigDecimal> champion = jitteredTrades(30, 5.0, 4.0, 4);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            15.0, 10.0, 1.5, 0.02, 0.5);
        var result = checker.evaluate(input);
        assertThat(result.status()).isEqualTo(PromotionEligibilityChecker.Status.NOT_ELIGIBLE);
        var drawdown = result.conditions().stream()
            .filter(c -> c.name().equals("max_drawdown")).findFirst().orElseThrow();
        assertThat(drawdown.met()).isFalse();
    }

    @Test
    void notEligibleWhenWalkForwardNotSignificant() {
        List<BigDecimal> challenger = jitteredTrades(30, 20.0, 4.0, 5);
        List<BigDecimal> champion = jitteredTrades(30, 5.0, 4.0, 6);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            8.0, 10.0, 0.5, 0.5, 0.5);
        var result = checker.evaluate(input);
        assertThat(result.status()).isEqualTo(PromotionEligibilityChecker.Status.NOT_ELIGIBLE);
    }

    @Test
    void walkForwardConditionReportsNoDataYetWithoutThrowing() {
        List<BigDecimal> challenger = jitteredTrades(30, 20.0, 4.0, 7);
        List<BigDecimal> champion = jitteredTrades(30, 5.0, 4.0, 8);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            8.0, 10.0, null, null, 0.5);
        var result = checker.evaluate(input);
        var walkForward = result.conditions().stream()
            .filter(c -> c.name().equals("walk_forward_significance")).findFirst().orElseThrow();
        assertThat(walkForward.met()).isFalse();
        assertThat(walkForward.note()).contains("No walk-forward run exists yet");
    }

    @Test
    void expectancyFallsBackToSharpeSignWhenTooFewTradesForBootstrap() {
        List<BigDecimal> challenger = constantTrades(10, 20.0);
        List<BigDecimal> champion = constantTrades(30, 5.0);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            8.0, 10.0, 1.5, 0.02, 0.5);
        var result = checker.evaluate(input);
        var expectancy = result.conditions().stream()
            .filter(c -> c.name().equals("expectancy_vs_champion")).findFirst().orElseThrow();
        assertThat(expectancy.note()).contains("bootstrap CI is unreliable");
        assertThat(expectancy.met()).isTrue();
    }

    @Test
    void expectancyFallbackNotMetWhenChallengerSharpeNotHigher() {
        List<BigDecimal> challenger = constantTrades(10, 20.0);
        List<BigDecimal> champion = constantTrades(30, 5.0);
        var input = new PromotionEligibilityChecker.Input(90, challenger, champion,
            8.0, 10.0, 0.2, 0.02, 0.5);
        var result = checker.evaluate(input);
        var expectancy = result.conditions().stream()
            .filter(c -> c.name().equals("expectancy_vs_champion")).findFirst().orElseThrow();
        assertThat(expectancy.met()).isFalse();
    }
}
