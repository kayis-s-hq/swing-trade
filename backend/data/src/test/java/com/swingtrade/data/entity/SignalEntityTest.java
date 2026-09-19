package com.swingtrade.data.entity;

import com.swingtrade.domain.Signal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalEntityTest {
    @Test
    void mapsSignalDomainAndPreservesStrategyProvenance() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        Signal signal = new Signal(7L, "ABC", date, Signal.SignalType.BUY,
            BigDecimal.valueOf(.8), "breakout", BigDecimal.TEN, BigDecimal.valueOf(9),
            BigDecimal.valueOf(13), BigDecimal.valueOf(1.5), "rsi=60", date,
            "0.4", "positive");
        SignalEntity entity = SignalEntity.fromDomain(signal, SignalEntity.WARNING_NEUTRAL_SENTIMENT,
            "PRICE_ACTION", 3);

        assertThat(entity.getSymbol()).isEqualTo("ABC");
        assertThat(entity.getDate()).isEqualTo(date);
        assertThat(entity.getSignalType()).isEqualTo("BUY");
        assertThat(entity.getConfidenceScore()).isEqualByComparingTo(".8");
        assertThat(entity.getEntryPrice()).isEqualByComparingTo("10");
        assertThat(entity.getStopLoss()).isEqualByComparingTo("9");
        assertThat(entity.getTarget()).isEqualByComparingTo("13");
        assertThat(entity.getRiskReward()).isEqualByComparingTo("1.5");
        assertThat(entity.getWarningFlag()).isEqualTo(SignalEntity.WARNING_NEUTRAL_SENTIMENT);
        assertThat(entity.getStrategy()).isEqualTo("PRICE_ACTION");
        assertThat(entity.getStrategyVersion()).isEqualTo(3);
        assertThat(entity.toDomain().symbol()).isEqualTo("ABC");
        assertThat(entity.toDomain().type()).isEqualTo(Signal.SignalType.BUY);
    }

    @Test
    void normalizesInvalidStrategyMetadataAndExposesAllMutableFields() {
        Signal signal = Signal.create("ABC", LocalDate.now(), Signal.SignalType.SELL,
            BigDecimal.ONE, "reason");
        SignalEntity entity = SignalEntity.fromDomain(signal, null, " ", 0);
        entity.setId(9L); entity.setIndicators("ema"); entity.setSentimentScore("neutral");
        entity.setSentimentReasoning("mixed"); entity.setGeneratedAt(LocalDate.now());
        entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now());
        entity.setProcessed(true);
        assertThat(entity.getId()).isEqualTo(9L);
        assertThat(entity.getIndicators()).isEqualTo("ema");
        assertThat(entity.getSentimentScore()).isEqualTo("neutral");
        assertThat(entity.getSentimentReasoning()).isEqualTo("mixed");
        assertThat(entity.getGeneratedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getProcessed()).isTrue();
        assertThat(entity.getStrategy()).isEqualTo(SignalEntity.STRATEGY_DEFAULT);
        assertThat(entity.getStrategyVersion()).isEqualTo(1);
        assertThat(SignalEntity.WarningFlag.NONE.code()).isEmpty();
        assertThat(SignalEntity.WarningFlag.PENDING_SENTIMENT.code()).isEqualTo("PENDING_SENTIMENT");
    }
}
