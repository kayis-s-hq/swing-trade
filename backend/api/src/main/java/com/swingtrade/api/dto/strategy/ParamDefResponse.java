package com.swingtrade.api.dto.strategy;

import com.swingtrade.strategy.ParamDef;

import java.math.BigDecimal;

/** DTO for {@link ParamDef}, drives UI form generation (plan §4.4). */
public record ParamDefResponse(
    String name,
    String type,
    BigDecimal min,
    BigDecimal max,
    Object defaultValue,
    String description,
    String group
) {
    public static ParamDefResponse from(ParamDef def) {
        return new ParamDefResponse(
            def.name(), def.type().name(), def.min(), def.max(), def.defaultValue(), def.description(), def.group());
    }
}
