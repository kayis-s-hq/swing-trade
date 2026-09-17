package com.swingtrade.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.swingtrade.strategy.PromotionEligibilityChecker.PromotionStatus.ELIGIBLE;
import static com.swingtrade.strategy.PromotionEligibilityChecker.PromotionStatus.INSUFFICIENT_SAMPLE;
import static com.swingtrade.strategy.PromotionEligibilityChecker.PromotionStatus.NOT_ELIGIBLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan §7.4: champion/challenger promotion eligibility checklist. Fixtures use round-number
 * trade P&Ls so expectancy/MaxDD comparisons are easy to hand-verify.
 */
@DisplayName("PromotionEligibilityChecker")
class PromotionEligibilityCheckerTest {

    @Test
    @DisplayName("all four conditions met -> ELIGIBLE")
    void allConditionsMetIsEligible() {
        List<PortfolioTrade> challengerTrades = tradesWithPnl(35, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            challengerTrades, championTrades,
            5.0, 10.0,
            1.2, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(ELIGIBLE);
        assertThat(result.tenureAndSampleSize().met()).isTrue();
        assertThat(result.expectancyVsChampion().met()).isTrue();
        assertThat(result.drawdownGuard().met()).isTrue();
        assertThat(result.walkForwardAndOverfitting().met()).isTrue();
    }

    @Test
    @DisplayName("condition 1 fails alone (tenure/trade count) -> INSUFFICIENT_SAMPLE even if other 3 pass")
    void tenureConditionFailsGivesInsufficientSample() {
        List<PortfolioTrade> challengerTrades = tradesWithPnl(35, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            30, // < 60 days
            challengerTrades, championTrades,
            5.0, 10.0,
            1.2, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(INSUFFICIENT_SAMPLE);
        assertThat(result.tenureAndSampleSize().met()).isFalse();
    }

    @Test
    @DisplayName("too few closed trades (< 30) -> INSUFFICIENT_SAMPLE")
    void tooFewTradesGivesInsufficientSample() {
        List<PortfolioTrade> challengerTrades = tradesWithPnl(10, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            challengerTrades, championTrades,
            5.0, 10.0,
            1.2, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(INSUFFICIENT_SAMPLE);
        assertThat(result.tenureAndSampleSize().met()).isFalse();
    }

    @Test
    @DisplayName("condition 2 fails (challenger expectancy not better) -> NOT_ELIGIBLE")
    void expectancyConditionFailsGivesNotEligible() {
        List<PortfolioTrade> challengerTrades = tradesWithPnl(35, 50.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 500.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            challengerTrades, championTrades,
            5.0, 10.0,
            1.2, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(NOT_ELIGIBLE);
        assertThat(result.expectancyVsChampion().met()).isFalse();
    }

    @Test
    @DisplayName("condition 3 fails (drawdown guard breached) -> NOT_ELIGIBLE")
    void drawdownConditionFailsGivesNotEligible() {
        List<PortfolioTrade> challengerTrades = tradesWithPnl(35, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            challengerTrades, championTrades,
            15.0, 10.0, // 15% > 1.2 * 10% = 12%
            1.2, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(NOT_ELIGIBLE);
        assertThat(result.drawdownGuard().met()).isFalse();
        assertThat(result.drawdownGuard().actualValue()).contains("15.00%").contains("10.00%");
    }

    @Test
    @DisplayName("drawdown guard exactly at 1.2x boundary is met (<=)")
    void drawdownConditionAtExactBoundaryIsMet() {
        var result = PromotionEligibilityChecker.evaluate(
            90,
            tradesWithPnl(35, 500.0), tradesWithPnl(35, 100.0),
            12.0, 10.0, // exactly 1.2x
            1.2, 0.5,
            0.05
        );

        assertThat(result.drawdownGuard().met()).isTrue();
    }

    @Test
    @DisplayName("condition 4 fails (OOS Sharpe <= 0) -> NOT_ELIGIBLE")
    void walkForwardSharpeNotPositiveGivesNotEligible() {
        var result = PromotionEligibilityChecker.evaluate(
            90,
            tradesWithPnl(35, 500.0), tradesWithPnl(35, 100.0),
            5.0, 10.0,
            -0.1, 0.5,
            0.05
        );

        assertThat(result.status()).isEqualTo(NOT_ELIGIBLE);
        assertThat(result.walkForwardAndOverfitting().met()).isFalse();
    }

    @Test
    @DisplayName("condition 4 fails (DSR p-value too high) -> NOT_ELIGIBLE")
    void walkForwardDsrTooHighGivesNotEligible() {
        var result = PromotionEligibilityChecker.evaluate(
            90,
            tradesWithPnl(35, 500.0), tradesWithPnl(35, 100.0),
            5.0, 10.0,
            1.2, 0.5,
            0.25 // >= 0.1
        );

        assertThat(result.status()).isEqualTo(NOT_ELIGIBLE);
        assertThat(result.walkForwardAndOverfitting().met()).isFalse();
    }

    @Test
    @DisplayName("no walk-forward data for challenger -> condition 4 reports not-met/no-data without throwing")
    void noWalkForwardDataReportsNotMetWithoutThrowing() {
        var result = PromotionEligibilityChecker.evaluate(
            90,
            tradesWithPnl(35, 500.0), tradesWithPnl(35, 100.0),
            5.0, 10.0,
            null, 0.5,
            null
        );

        assertThat(result.walkForwardAndOverfitting().met()).isFalse();
        assertThat(result.walkForwardAndOverfitting().actualValue()).contains("no walk-forward");
        // Overall status still resolves (no exception), and isn't wrongly ELIGIBLE.
        assertThat(result.status()).isNotEqualTo(ELIGIBLE);
    }

    @Test
    @DisplayName("bootstrap-CI-too-few-trades fallback: below threshold on either side falls back to "
        + "walk-forward OOS Sharpe sign consistency, and is met when challenger's OOS Sharpe is higher")
    void fallsBackToWalkForwardSignConsistencyWhenTradeCountTooLow() {
        // 30 trades each satisfies condition 1's tenure/count gate, but is below
        // MIN_TRADES_FOR_BOOTSTRAP_CI... actually 30 >= 20, so use exactly the boundary count that
        // still satisfies condition 1 (30) while being clearly under the bootstrap threshold is not
        // possible since MIN_TRADES_FOR_BOOTSTRAP_CI (20) < MIN_CLOSED_TRADES (30). To exercise the
        // fallback, keep challenger trades below 20 and rely on condition 1 alone driving
        // INSUFFICIENT_SAMPLE being bypassed by directly asserting the expectancy condition text.
        List<PortfolioTrade> fewChallengerTrades = tradesWithPnl(15, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            fewChallengerTrades, championTrades,
            5.0, 10.0,
            1.2, 0.5, // challenger OOS Sharpe (1.2) > champion's (0.5) -> fallback met
            0.05
        );

        assertThat(result.expectancyVsChampion().actualValue()).contains("trade count too low for bootstrap CI");
        assertThat(result.expectancyVsChampion().met()).isTrue();
        // Overall is still INSUFFICIENT_SAMPLE because condition 1 (< 30 challenger trades) fails,
        // demonstrating the tri-state takes precedence, but the fallback itself was exercised above.
        assertThat(result.status()).isEqualTo(INSUFFICIENT_SAMPLE);
    }

    @Test
    @DisplayName("bootstrap fallback: challenger OOS Sharpe not favoured -> fallback condition not met")
    void fallbackNotMetWhenChallengerSharpeNotHigher() {
        List<PortfolioTrade> fewChallengerTrades = tradesWithPnl(15, 500.0);
        List<PortfolioTrade> championTrades = tradesWithPnl(35, 100.0);

        var result = PromotionEligibilityChecker.evaluate(
            90,
            fewChallengerTrades, championTrades,
            5.0, 10.0,
            0.3, 0.5, // challenger OOS Sharpe (0.3) <= champion's (0.5) -> fallback not met
            0.05
        );

        assertThat(result.expectancyVsChampion().met()).isFalse();
    }

    @Test
    @DisplayName("tri-state: ELIGIBLE, NOT_ELIGIBLE and INSUFFICIENT_SAMPLE are mutually distinguishable")
    void triStateDistinction() {
        List<PortfolioTrade> goodChallenger = tradesWithPnl(35, 500.0);
        List<PortfolioTrade> champion = tradesWithPnl(35, 100.0);

        var eligible = PromotionEligibilityChecker.evaluate(90, goodChallenger, champion, 5.0, 10.0, 1.2, 0.5, 0.05);
        var notEligible = PromotionEligibilityChecker.evaluate(90, goodChallenger, champion, 20.0, 10.0, 1.2, 0.5, 0.05);
        var insufficientSample = PromotionEligibilityChecker.evaluate(10, goodChallenger, champion, 5.0, 10.0, 1.2, 0.5, 0.05);

        assertThat(eligible.status()).isEqualTo(ELIGIBLE);
        assertThat(notEligible.status()).isEqualTo(NOT_ELIGIBLE);
        assertThat(insufficientSample.status()).isEqualTo(INSUFFICIENT_SAMPLE);
    }

    private static List<PortfolioTrade> tradesWithPnl(int count, double pnlPerTrade) {
        List<PortfolioTrade> trades = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            trades.add(new PortfolioTrade(
                "SYM", date.plusDays(i), date.plusDays(i + 3),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100 + pnlPerTrade / 10.0), BigDecimal.valueOf(95),
                BigDecimal.valueOf(110), 10,
                ExitReason.TARGET_HIT,
                BigDecimal.valueOf(5), BigDecimal.valueOf(pnlPerTrade), pnlPerTrade / 100.0, 3,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(80)
            ));
        }
        return trades;
    }
}
