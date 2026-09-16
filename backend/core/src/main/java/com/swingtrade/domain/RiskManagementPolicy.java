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
            BigDecimal highestCloseBeforeBar,
            int holdingDays
    ) {
        public RiskManagementContext {
            if (entryPrice == null || initialStop == null || target == null
                    || currentClose == null || currentLow == null || highestCloseBeforeBar == null
                    || holdingDays < 0) {
                throw new IllegalArgumentException("Risk-management context is invalid");
            }
        }
    }

    record RiskManagementDecision(boolean exit, BigDecimal stopPrice, String reason) {
        public RiskManagementDecision {
            if (exit && (stopPrice == null || reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("An exit decision requires a stop price and reason");
            }
        }

        public static RiskManagementDecision hold() {
            return new RiskManagementDecision(false, null, "");
        }

        public static RiskManagementDecision exit(BigDecimal stopPrice, String reason) {
            return new RiskManagementDecision(true, stopPrice, reason);
        }
    }
}
