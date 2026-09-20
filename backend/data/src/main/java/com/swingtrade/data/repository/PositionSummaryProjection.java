package com.swingtrade.data.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring Data interface projection for {@link PositionRepository#findOpenSummaries()}.
 * Property types mirror the {@code PositionEntity} column types (status and direction are
 * stored as strings); conversion to the domain enums happens in the store, not in JPQL.
 */
public interface PositionSummaryProjection {

    Long getId();

    String getSymbol();

    String getStatus();

    String getDirection();

    BigDecimal getEntryPrice();

    Integer getQuantity();

    BigDecimal getCurrentPrice();

    BigDecimal getUnrealizedPnL();

    BigDecimal getStopLoss();

    BigDecimal getTarget();

    String getBrokerType();

    LocalDate getEntryDate();

    String getEntryReason();
}
