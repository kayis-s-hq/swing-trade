package com.swingtrade.domain;

import java.time.LocalDateTime;

/** Immutable lifecycle completion details for a position. */
public record PositionExit(
    LocalDateTime exitTime,
    String exitReason
) {}
