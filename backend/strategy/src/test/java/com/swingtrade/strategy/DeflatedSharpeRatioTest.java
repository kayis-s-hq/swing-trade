package com.swingtrade.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan §6.4/§6.7: DSR exact reference values are hard to source independently, so this tests its
 * monotonicity properties instead - more trials for the same observed Sharpe must lower the DSR -
 * plus edge cases (N=1, very high N).
 */
@DisplayName("DeflatedSharpeRatio")
class DeflatedSharpeRatioTest {

    @Test
    @DisplayName("more trials (higher N) lowers DSR for the same observed Sharpe, all else equal")
    void moreTrialsLowersDsr() {
        double observedSharpe = 0.6;
        int observations = 250;
        double stdError = 0.5;

        double dsrN1 = DeflatedSharpeRatio.compute(observedSharpe, 1, observations, stdError);
        double dsrN2 = DeflatedSharpeRatio.compute(observedSharpe, 2, observations, stdError);
        double dsrN5 = DeflatedSharpeRatio.compute(observedSharpe, 5, observations, stdError);
        double dsrN10 = DeflatedSharpeRatio.compute(observedSharpe, 10, observations, stdError);

        assertThat(dsrN1).isGreaterThan(dsrN2);
        assertThat(dsrN2).isGreaterThan(dsrN5);
        assertThat(dsrN5).isGreaterThan(dsrN10);
    }

    @Test
    @DisplayName("N=1 (single trial) applies no deflation: DSR reduces to the plain Sharpe significance")
    void nEqualsOneAppliesNoDeflation() {
        double dsr = DeflatedSharpeRatio.compute(1.2, 1, 300, 0.3);
        double zOnlySharpe = DeflatedSharpeRatio.normalCdf(1.2 * Math.sqrt(299));
        assertThat(dsr).isCloseTo(zOnlySharpe, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("very high N drives DSR toward 0 (near-certain overfitting suspicion) for a modest Sharpe")
    void veryHighNDrivesDsrTowardZero() {
        double dsr = DeflatedSharpeRatio.compute(1.0, 1_000_000, 500, 0.3);
        assertThat(dsr).isLessThan(0.05);
    }

    @Test
    @DisplayName("DSR is bounded in [0,1]")
    void dsrIsBounded() {
        for (int n : new int[] {1, 5, 50, 5000}) {
            double dsr = DeflatedSharpeRatio.compute(2.5, n, 1000, 0.5);
            assertThat(dsr).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("rejects invalid inputs")
    void rejectsInvalidInputs() {
        assertThat(catchException(() -> DeflatedSharpeRatio.compute(1.0, 0, 100, 0.3))).isNotNull();
        assertThat(catchException(() -> DeflatedSharpeRatio.compute(1.0, 1, 1, 0.3))).isNotNull();
    }

    private static Exception catchException(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
