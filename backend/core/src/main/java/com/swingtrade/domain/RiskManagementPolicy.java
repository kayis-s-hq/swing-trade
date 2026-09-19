package com.swingtrade.domain;

import java.math.BigDecimal;

/**
 * Contract for bar-by-bar management of an already-open long position.
 * Implementations must not use information after the supplied bar.
 */
@FunctionalInterface
public interface RiskManagementPolicy {

    RiskManagementDecision evaluate(RiskManagementContext context);

    static RiskManagementPolicy none() {
        return context -> RiskManagementDecision.hold();
    }

    record RiskManagementContext(
            BigDecimal entryPrice,
            BigDecimal initialStop,
            BigDecimal target,
            BigDecimal currentClose,
            BigDecimal currentLow,
            BigDecimal currentHigh,
            BigDecimal highestCloseBeforeBar,
            int holdingDays,
            boolean partialExitTaken,
            BigDecimal currentAtr
    ) {
        public RiskManagementContext(BigDecimal entryPrice, BigDecimal initialStop, BigDecimal target,
                                     BigDecimal currentClose, BigDecimal currentLow,
                                     BigDecimal highestCloseBeforeBar, int holdingDays) {
            this(entryPrice, initialStop, target, currentClose, currentLow, currentClose,
                    highestCloseBeforeBar, holdingDays, false, null);
        }

        public RiskManagementContext(BigDecimal entryPrice, BigDecimal initialStop, BigDecimal target,
                                     BigDecimal currentClose, BigDecimal currentLow, BigDecimal currentHigh,
                                     BigDecimal highestCloseBeforeBar, int holdingDays, boolean partialExitTaken) {
            this(entryPrice, initialStop, target, currentClose, currentLow, currentHigh,
                    highestCloseBeforeBar, holdingDays, partialExitTaken, null);
        }

        public RiskManagementContext {
            if (entryPrice == null || initialStop == null || target == null
                    || currentClose == null || currentLow == null || currentHigh == null
                    || highestCloseBeforeBar == null
                    || holdingDays < 0) {
                throw new IllegalArgumentException("Risk-management context is invalid");
            }
        }
    }

    record RiskManagementDecision(boolean exit, BigDecimal stopPrice, String reason,
                                  BigDecimal partialExitRatio) {
        public RiskManagementDecision(boolean exit, BigDecimal stopPrice, String reason) {
            this(exit, stopPrice, reason, null);
        }

        public RiskManagementDecision {
            if (exit && (stopPrice == null || reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("An exit decision requires a stop price and reason");
            }
            if (partialExitRatio != null && (partialExitRatio.signum() <= 0
                    || partialExitRatio.compareTo(BigDecimal.ONE) > 0)) {
                throw new IllegalArgumentException("Partial exit ratio must be between 0 and 1");
            }
        }

        public static RiskManagementDecision hold() {
            return new RiskManagementDecision(false, null, "", null);
        }

        public static RiskManagementDecision exit(BigDecimal stopPrice, String reason) {
            return new RiskManagementDecision(true, stopPrice, reason, null);
        }

        public static RiskManagementDecision partialExit(BigDecimal exitPrice, BigDecimal ratio) {
            return new RiskManagementDecision(false, exitPrice, "PARTIAL_TARGET", ratio);
        }
    }
}
