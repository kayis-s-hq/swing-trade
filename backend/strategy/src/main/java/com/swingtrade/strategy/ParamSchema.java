package com.swingtrade.strategy;

import java.util.List;

/** The full set of configurable parameters a {@link SignalStrategy} type accepts. */
public record ParamSchema(List<ParamDef> params) {
    public ParamSchema {
        params = params == null ? List.of() : List.copyOf(params);
    }
}
