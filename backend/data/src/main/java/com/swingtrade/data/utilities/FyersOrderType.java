package com.swingtrade.data.utilities;

/**
 * Fyers order types matching SDK constants.
 * 1=Limit, 2=Market, 3=SL-Market, 4=SL-Limit.
 */
public enum FyersOrderType {
    LIMIT(1),
    MARKET(2),
    SL_MARKET(3),
    SL_LIMIT(4);

    private final int code;

    FyersOrderType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FyersOrderType fromCode(int code) {
        for (FyersOrderType t : values()) {
            if (t.code == code) return t;
        }
        return null;
    }
}
