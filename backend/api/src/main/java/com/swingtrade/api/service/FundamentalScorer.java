package com.swingtrade.api.service;

import com.swingtrade.domain.CompositeAnalysis;
import com.swingtrade.domain.FundamentalData;
import com.swingtrade.domain.source.FundamentalDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FundamentalScorer {

    private static final Logger logger = LoggerFactory.getLogger(FundamentalScorer.class);
    private final FundamentalDataSource fundamentalDataSource;

    public FundamentalScorer(FundamentalDataSource fundamentalDataSource) {
        this.fundamentalDataSource = fundamentalDataSource;
    }

    /** Scores only sourced company fundamentals; unavailable data is neutral and explicit. */
    public CompositeAnalysis.FundamentalScore compute(String symbol) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (sym.isBlank()) return unavailable("Invalid symbol");
        try {
            return fundamentalDataSource.findBySymbol(sym)
                .map(this::score)
                .orElseGet(() -> unavailable("Fundamentals unavailable"));
        } catch (RuntimeException e) {
            logger.warn("Fundamentals unavailable for {}: {}", sym, e.getMessage());
            return unavailable("Fundamentals unavailable");
        }
    }

    private CompositeAnalysis.FundamentalScore score(FundamentalData data) {
        List<String> factors = new ArrayList<>();
        int score = 0;

        if (data.marketCap() != null && data.marketCap() > 0) {
            factors.add(String.format("Market capitalization available: %d", data.marketCap()));
        } else {
            factors.add("Market capitalization unavailable");
        }

        BigDecimal pe = data.peRatio();
        if (pe == null) {
            factors.add("P/E ratio unavailable = 0");
        } else if (pe.signum() <= 0) {
            factors.add(String.format("P/E ratio not positive (%.2f) = 0", pe));
        } else if (pe.compareTo(BigDecimal.valueOf(15)) <= 0) {
            score += 25;
            factors.add(String.format("P/E ratio attractive (%.2f) = +25", pe));
        } else if (pe.compareTo(BigDecimal.valueOf(25)) <= 0) {
            factors.add(String.format("P/E ratio moderate (%.2f) = 0", pe));
        } else if (pe.compareTo(BigDecimal.valueOf(40)) <= 0) {
            score -= 10;
            factors.add(String.format("P/E ratio elevated (%.2f) = -10", pe));
        } else {
            score -= 25;
            factors.add(String.format("P/E ratio high (%.2f) = -25", pe));
        }
        return new CompositeAnalysis.FundamentalScore(score, factors);
    }

    private CompositeAnalysis.FundamentalScore unavailable(String reason) {
        return new CompositeAnalysis.FundamentalScore(0, List.of(reason));
    }
}
