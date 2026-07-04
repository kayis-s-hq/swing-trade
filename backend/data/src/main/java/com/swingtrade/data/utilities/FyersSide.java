package com.swingtrade.data.utilities;

/**
 * Fyers order side.
 * 1=Buy, -1=Sell.
 */
public enum FyersSide {
    BUY(1),
    SELL(-1);

    private final int code;

    FyersSide(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FyersSide fromCode(int code) {
        for (FyersSide s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
