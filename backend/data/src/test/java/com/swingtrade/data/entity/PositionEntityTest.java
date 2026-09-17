package com.swingtrade.data.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionEntityTest {
    @Test
    void persistsAndRestoresPositionFields() {
        PositionEntity entity = new PositionEntity();
        LocalDate date = LocalDate.of(2026, 1, 5);
        LocalDateTime now = LocalDateTime.now();
        entity.setId(4L); entity.setSymbol("ABC"); entity.setBrokerType("PAPER");
        entity.setEntryPrice(BigDecimal.TEN); entity.setEntryDate(date); entity.setQuantity(10);
        entity.setStopLoss(BigDecimal.valueOf(8)); entity.setTarget(BigDecimal.valueOf(14));
        entity.setStatus("OPEN"); entity.setEntryReason("breakout"); entity.setCurrentPrice(BigDecimal.valueOf(11));
        entity.setCreatedAt(now); entity.setUpdatedAt(now); entity.setPositionId("POS_4"); entity.setSignalId(9L);
        entity.setBrokerPositionId("broker-4"); entity.setExchange("NSE"); entity.setDirection("LONG");
        entity.setAveragePrice(BigDecimal.TEN); entity.setUnrealizedPnL(BigDecimal.ONE);
        entity.setRealizedPnL(BigDecimal.ZERO); entity.setMarginUtilized(BigDecimal.valueOf(100));
        entity.setPartialExitTaken(true); entity.setEntryTime(now); entity.setExitTime(null);
        entity.setExitReason(null);
        assertThat(entity.getId()).isEqualTo(4L); assertThat(entity.getSymbol()).isEqualTo("ABC");
        assertThat(entity.getBrokerType()).isEqualTo("PAPER"); assertThat(entity.getEntryPrice()).isEqualByComparingTo("10");
        assertThat(entity.getEntryDate()).isEqualTo(date); assertThat(entity.getQuantity()).isEqualTo(10);
        assertThat(entity.getStopLoss()).isEqualByComparingTo("8"); assertThat(entity.getTarget()).isEqualByComparingTo("14");
        assertThat(entity.getStatus()).isEqualTo("OPEN"); assertThat(entity.getEntryReason()).isEqualTo("breakout");
        assertThat(entity.getCurrentPrice()).isEqualByComparingTo("11"); assertThat(entity.getPositionId()).isEqualTo("POS_4");
        assertThat(entity.getSignalId()).isEqualTo(9L); assertThat(entity.getBrokerPositionId()).isEqualTo("broker-4");
        assertThat(entity.getExchange()).isEqualTo("NSE"); assertThat(entity.getDirection()).isEqualTo("LONG");
        assertThat(entity.getAveragePrice()).isEqualByComparingTo("10"); assertThat(entity.getUnrealizedPnL()).isEqualByComparingTo("1");
        assertThat(entity.getRealizedPnL()).isEqualByComparingTo("0"); assertThat(entity.getMarginUtilized()).isEqualByComparingTo("100");
        assertThat(entity.isPartialExitTaken()).isTrue(); assertThat(entity.getEntryTime()).isEqualTo(now);

        var domain = entity.toDomain();
        assertThat(domain.symbol()).isEqualTo("ABC");
        assertThat(domain.quantity()).isEqualTo(10);
        assertThat(domain.direction().name()).isEqualTo("LONG");
    }
}
