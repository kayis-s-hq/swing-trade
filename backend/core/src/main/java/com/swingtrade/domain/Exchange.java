package com.swingtrade.domain;

/**
 * Enum representing Indian stock exchanges.
 */
public enum Exchange {
    NSE("NSE"),
    BSE("BSE"),
    NSE_FO("NSE_FO"),
    NCEI("NSE_CEG");

    private final String exchangeCode;

    Exchange(String exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    public String getExchangeCode() { return exchangeCode; }

    public static Exchange fromCode(String code) {
        for (Exchange exchange : values()) {
            if (exchange.getExchangeCode().equalsIgnoreCase(code)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange code: " + code);
    }
}