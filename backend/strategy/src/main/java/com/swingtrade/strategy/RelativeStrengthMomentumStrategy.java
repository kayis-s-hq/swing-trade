package com.swingtrade.strategy;

import com.swingtrade.domain.MarketRegimeAssessment;
import com.swingtrade.domain.RelativeStrengthAssessment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Relative-strength-vs-index momentum setup (plan §5.4): entry is driven primarily by a
 * stock's bounded-lookback outperformance against the Nifty index rather than by
 * price-action confluence, with close &gt; EMA50 as a mandatory trend filter and an
 * optional 52-week-high proximity check.
 *
 * <p><b>Scope note:</b> the plan describes ranking RS by percentile within the scan
 * universe on each date (cross-sectional context). That requires computing RS for every
 * symbol in the universe on a given date and ranking them together, which is a new
 * subsystem beyond a single-symbol {@link TradingStrategy} evaluation and beyond what
 * {@code UniverseSnapshot}/{@code UniverseSnapshotStore} currently expose (point-in-time
 * membership only, no stored per-symbol RS values to rank against). This implementation
 * therefore uses the simpler, honestly-scoped RS-vs-Nifty <b>ratio threshold</b>
 * ({@link #rsThresholdPct}) instead of a percentile rank. Cross-sectional percentile
 * ranking is a documented follow-up, not a silent omission.
 *
 * <p><b>Survivorship-bias note:</b> the relative-strength assessment is computed from
 * whichever stock/index candle history the caller supplies, keyed to symbols that exist
 * in the system today. If historical universe membership (which symbols were actually
 * tradable/listed on a past date) is unavailable, backtests over delisted or renamed
 * symbols are not represented here - see plan §5.4's survivorship-bias caveat.
 *
 * <p>Relative strength is computed by the existing {@link com.swingtrade.domain.policy.RelativeStrengthPolicy}
 * (via {@link BoundedRelativeStrengthPolicy}) and supplied by the caller as a
 * {@link RelativeStrengthAssessment}; this strategy does not recompute or reimplement
 * that math. Because relative strength is the primary signal here (not merely an
 * eligibility overlay), {@link #isEntryEligible(Indicators, MarketRegimeAssessment,
 * RelativeStrengthAssessment)} is overridden directly instead of relying on the
 * interface's default overlay composition, and a {@code null}/unavailable assessment
 * fails closed.
 */
@Component
public class RelativeStrengthMomentumStrategy implements TradingStrategy {

    public static final String NAME = "RS_NIFTY_MOMENTUM";
    public static final String RS_THRESHOLD_PARAM = "rsThresholdPct";
    public static final String REQUIRE_HIGH_PROXIMITY_PARAM = "requireHighProximity";
    public static final String HIGH_PROXIMITY_PARAM = "highProximityPct";

    /** Default: any non-negative excess return over the index qualifies (threshold is inclusive). */
    public static final BigDecimal DEFAULT_RS_THRESHOLD_PCT = BigDecimal.ZERO;
    public static final BigDecimal DEFAULT_HIGH_PROXIMITY_PCT = new BigDecimal("0.90");

    private final BigDecimal rsThresholdPct;
    private final boolean requireHighProximity;
    private final BigDecimal highProximityPct;

    public RelativeStrengthMomentumStrategy() {
        this(DEFAULT_RS_THRESHOLD_PCT, false, DEFAULT_HIGH_PROXIMITY_PCT);
    }

    public RelativeStrengthMomentumStrategy(BigDecimal rsThresholdPct, boolean requireHighProximity,
                                            BigDecimal highProximityPct) {
        if (rsThresholdPct == null) {
            throw new IllegalArgumentException("RS threshold percentage is required");
        }
        if (highProximityPct == null
            || highProximityPct.compareTo(BigDecimal.ZERO) <= 0
            || highProximityPct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("High-proximity fraction must be in (0, 1]");
        }
        this.rsThresholdPct = rsThresholdPct;
        this.requireHighProximity = requireHighProximity;
        this.highProximityPct = highProximityPct;
    }

    /** Creates a backtest variant from the existing strategy-config parameter map. */
    public static RelativeStrengthMomentumStrategy fromParameters(Map<String, ?> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Strategy parameters cannot be null");
        }
        return new RelativeStrengthMomentumStrategy(
            decimalParameter(parameters, RS_THRESHOLD_PARAM, DEFAULT_RS_THRESHOLD_PCT),
            boolParameter(parameters, REQUIRE_HIGH_PROXIMITY_PARAM, false),
            decimalParameter(parameters, HIGH_PROXIMITY_PARAM, DEFAULT_HIGH_PROXIMITY_PCT));
    }

    private static BigDecimal decimalParameter(Map<String, ?> parameters, String key, BigDecimal defaultValue) {
        Object value = parameters.get(key);
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value instanceof String string) {
            try {
                return new BigDecimal(string.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Strategy parameter " + key + " must be numeric", e);
            }
        }
        throw new IllegalArgumentException("Strategy parameter " + key + " must be numeric");
    }

    private static boolean boolParameter(Map<String, ?> parameters, String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String string) return Boolean.parseBoolean(string.trim());
        throw new IllegalArgumentException("Strategy parameter " + key + " must be boolean");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean regimeFilterEnabled() {
        // Plan §5.4: "regime gate recommended on" for the RS-vs-Nifty momentum family.
        return true;
    }

    @Override
    public boolean relativeStrengthFilterEnabled() {
        return true;
    }

    @Override
    public String entryConfluenceDescription() {
        return "Close > EMA50 and RS-vs-Nifty excess return >= " + rsThresholdPct.stripTrailingZeros().toPlainString() + "%";
    }

    @Override
    public String entryRsiDescription() {
        return "RSI not used by this strategy";
    }

    /**
     * Technical portion only: mandatory trend filter (close &gt; EMA50), plus the optional
     * 52-week-high proximity check when configured. The relative-strength primary signal
     * is evaluated separately in {@link #isEntryEligible(Indicators, MarketRegimeAssessment,
     * RelativeStrengthAssessment)} because {@link Indicators} carries no RS data.
     */
    @Override
    public boolean isEntrySignal(Indicators i) {
        return trendAligned(i) && (!requireHighProximity || nearWeeklyHigh(i));
    }

    /**
     * Overridden directly (rather than relying on the interface's default overlay
     * composition) because relative strength is this strategy's primary signal, evaluated
     * against a configurable threshold rather than the policy's own fixed eligibility bar.
     * A missing/unavailable assessment fails closed.
     */
    @Override
    public boolean isEntryEligible(Indicators indicators, MarketRegimeAssessment regime,
                                   RelativeStrengthAssessment relativeStrength) {
        if (!isEntrySignal(indicators)) {
            return false;
        }
        if (relativeStrength == null
            || BigDecimal.valueOf(relativeStrength.excessReturnPct()).compareTo(rsThresholdPct) < 0) {
            return false;
        }
        return regime != null && regime.eligible() && allowedMarketRegimes().contains(regime.regime());
    }

    @Override
    public boolean trendAligned(Indicators i) {
        return i.price().compareTo(i.ema50()) > 0;
    }

    @Override
    public boolean rsiInEntryRange(Indicators i) {
        return true;
    }

    @Override
    public boolean volumeSurge(Indicators i) {
        return true;
    }

    @Override
    public boolean nearWeeklyHigh(Indicators i) {
        return i.price().compareTo(i.weeklyHigh().multiply(highProximityPct)) >= 0;
    }

    @Override
    public boolean closeBelowEma20(Indicators i) {
        return i.price().compareTo(i.ema20()) < 0;
    }

    @Override
    public boolean ema20BelowEma50(Indicators i) {
        return i.ema50() != null && i.price().compareTo(i.ema50()) < 0;
    }

    @Override
    public boolean rsiBelowLowerBound(Indicators i) {
        return false;
    }
}
