package com.swingtrade.broker.model;

/**
 * Enum representing Indian stock exchanges.
 * Zerodha Kite Connect supports NSE and BSE exchanges.
 */
public enum Exchange {
    /**
     * National Stock Exchange of India
     */
    NSE("NSE"),

    /**
     * Bombay Stock Exchange
     */
    BSE("BSE"),

    /**
     * National Stock Exchange - Futures & Options
     */
    NSE_FO("NSE_FO"),

    /**
     * National Stock Exchange - Currency
     */
    NCEI("NSE_CEG");

    private final String exchangeCode;

    Exchange(String exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    public String getExchangeCode() {
        return exchangeCode;
    }

    /**
     * Parse exchange from string.
     * @param code exchange code string
     * @return Exchange enum
     * @throws IllegalArgumentException if code is not recognized
     */
    public static Exchange fromCode(String code) {
        for (Exchange exchange : values()) {
            if (exchange.getExchangeCode().equalsIgnoreCase(code)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange code: " + code);
    }
}
