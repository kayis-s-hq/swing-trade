package com.swingtrade.api.service;

import com.swingtrade.domain.EligibilityAssessment;
import com.swingtrade.domain.EligibilityPolicy;
import com.swingtrade.domain.OhlcvCandle;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.PriceBandPolicy;
import com.swingtrade.domain.store.CandleStore;
import com.swingtrade.domain.store.PriceBandStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds live trade-eligibility decisions from explicitly persisted facts.
 *
 * <p>The current API has no source for exchange surveillance flags or corporate
 * event windows. Those facts are therefore reported as unavailable and never
 * defaulted to a clear result. Signal generation remains an audit operation;
 * this service is used only before a live BUY is queued.</p>
 */
@Service
public class LiveEligibilityService {
    private final CandleStore candleStore;
    private final PriceBandStore priceBandStore;
    private final EligibilityPolicy policy;

    public LiveEligibilityService(CandleStore candleStore, PriceBandStore priceBandStore) {
        this(candleStore, priceBandStore, new EligibilityPolicy());
    }

    LiveEligibilityService(CandleStore candleStore, PriceBandStore priceBandStore,
                           EligibilityPolicy policy) {
        this.candleStore = candleStore;
        this.priceBandStore = priceBandStore;
        this.policy = policy;
    }

    public EligibilityDecision assess(String symbol, LocalDate date, BigDecimal entryPrice) {
        List<String> unavailable = new ArrayList<>();
        // These are required policy inputs, but no live source currently supplies them.
        unavailable.add("surveillance flags");
        unavailable.add("results event window");
        unavailable.add("board meeting event window");

        List<OhlcvCandle> observations = candleStore.findTopBySymbolOrderByDateDesc(symbol, 100).stream()
            .filter(candle -> candle.date() != null && !candle.date().isAfter(date))
            .filter(candle -> candle.close() != null && candle.close().signum() > 0
                && candle.volume() != null && candle.volume() >= 0)
            .limit(EligibilityPolicy.DEFAULT_MINIMUM_LIQUIDITY_OBSERVATIONS)
            .toList();
        if (observations.size() < EligibilityPolicy.DEFAULT_MINIMUM_LIQUIDITY_OBSERVATIONS) {
            unavailable.add("20 valid liquidity observations");
        }

        PriceBand band = priceBandStore.findBySymbolAndDate(symbol, date).orElse(null);
        if (band == null) unavailable.add("exchange price band for " + date);

        if (!unavailable.isEmpty()) {
            return EligibilityDecision.blocked(unavailable);
        }

        BigDecimal averageTradedValue = observations.stream()
            .map(candle -> candle.close().multiply(BigDecimal.valueOf(candle.volume())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(observations.size()), 2, RoundingMode.HALF_UP);
        boolean priceBandEligible = entryPrice != null && !PriceBandPolicy.blocksLongEntry(band, entryPrice);
        EligibilityAssessment assessment = policy.assess(new com.swingtrade.domain.EligibilityInputs(
            symbol, averageTradedValue, observations.size(), false, false, false,
            priceBandEligible,
            com.swingtrade.domain.EligibilityInputs.EventWindow.noneFound(),
            com.swingtrade.domain.EligibilityInputs.EventWindow.noneFound()));
        return new EligibilityDecision(assessment.eligible(), List.of(), assessment.rejectionReasons());
    }

    public record EligibilityDecision(boolean eligible, List<String> unavailableInputs,
                                      List<EligibilityAssessment.RejectionReason> rejectionReasons) {
        public EligibilityDecision {
            unavailableInputs = List.copyOf(unavailableInputs);
            rejectionReasons = List.copyOf(rejectionReasons);
        }

        static EligibilityDecision blocked(List<String> unavailableInputs) {
            return new EligibilityDecision(false, unavailableInputs, List.of());
        }
    }
}
