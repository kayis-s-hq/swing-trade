package com.swingtrade.data.entity;

import com.swingtrade.domain.Trade;
import com.swingtrade.domain.TradeDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeEntityTest {
    @Test
    void mapsClosedTradeToAndFromPersistence() {
        LocalDate entry = LocalDate.of(2026, 1, 5);
        LocalDate exit = LocalDate.of(2026, 1, 8);
        Trade trade = new Trade(11L, 4L, "ABC", entry, exit, BigDecimal.TEN,
            BigDecimal.valueOf(12), 20, TradeDirection.LONG, BigDecimal.valueOf(40),
            3, Trade.TradeStatus.CLOSED, "breakout", "target", BigDecimal.ONE);
        TradeEntity entity = TradeEntity.fromDomain(trade);

        assertThat(entity.getId()).isEqualTo(11L);
        assertThat(entity.getPositionId()).isEqualTo(4L);
        assertThat(entity.getSymbol()).isEqualTo("ABC");
        assertThat(entity.getEntryDate()).isEqualTo(entry);
        assertThat(entity.getExitDate()).isEqualTo(exit);
        assertThat(entity.getEntryPrice()).isEqualByComparingTo("10");
        assertThat(entity.getExitPrice()).isEqualByComparingTo("12");
        assertThat(entity.getQuantity()).isEqualTo(20);
        assertThat(entity.getTotalPnL()).isEqualByComparingTo("40");
        assertThat(entity.getDurationDays()).isEqualTo(3);
        assertThat(entity.getTradeStatus()).isEqualTo("CLOSED");
        assertThat(entity.getStatus()).isEqualTo("CLOSED");
        assertThat(entity.getEntryReason()).isEqualTo("breakout");
        assertThat(entity.getExitReason()).isEqualTo("target");
        assertThat(entity.getFees()).isEqualByComparingTo("1");
        assertThat(entity.toDomain()).isEqualTo(trade);
    }

    @Test
    void settersMaintainPersistedValuesAndDefaultDirection() {
        TradeEntity entity = new TradeEntity();
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        entity.setId(1L); entity.setPositionId(2L); entity.setSymbol("XYZ");
        entity.setEntryDate(date); entity.setExitDate(date.plusDays(1)); entity.setEntryPrice(BigDecimal.ONE);
        entity.setExitPrice(BigDecimal.TWO); entity.setQuantity(3); entity.setTotalPnL(BigDecimal.TEN);
        entity.setDurationDays(1); entity.setStatus("OPEN"); entity.setEntryReason("entry");
        entity.setExitReason("manual"); entity.setFees(BigDecimal.ZERO); entity.setCreatedAt(now); entity.setUpdatedAt(now);
        assertThat(entity.getId()).isEqualTo(1L); assertThat(entity.getPositionId()).isEqualTo(2L);
        assertThat(entity.getSymbol()).isEqualTo("XYZ"); assertThat(entity.getEntryDate()).isEqualTo(date);
        assertThat(entity.getExitDate()).isEqualTo(date.plusDays(1)); assertThat(entity.getQuantity()).isEqualTo(3);
        assertThat(entity.getTotalPnL()).isEqualByComparingTo("10"); assertThat(entity.getDurationDays()).isEqualTo(1);
        assertThat(entity.getEntryReason()).isEqualTo("entry"); assertThat(entity.getExitReason()).isEqualTo("manual");
        assertThat(entity.getCreatedAt()).isEqualTo(now); assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }
}
