package com.swingtrade.domain;

import java.time.LocalDate;

/**
 * Represents a stock entity with essential identifying and descriptive information.
 * This record captures fundamental stock attributes used for tracking and analysis
 * in the swing trading system.
 *
 * @param symbol    the unique identifier for the stock (e.g., "RELIANCE", "TCS")
 * @param exchange  the stock exchange where the stock is traded (NSE or BSE)
 * @param name      the full company name (e.g., "Reliance Industries Limited")
 * @param sector    the industry sector the company belongs to (e.g., "Oil & Gas")
 * @param isin      the International Securities Identification Number
 * @param lotSize   the trading lot size for the stock
 * @param addedOn   the date when the stock was added to the exchange universe
 */
public record Stock(
    String symbol,
    Exchange exchange,
    String name,
    Sector sector,
    String isin,
    Integer lotSize,
    LocalDate addedOn
) {

    /**
     * Enum representing the stock exchanges supported by the system.
     */
    public enum Exchange {
        NSE("National Stock Exchange of India"),
        BSE("Bombay Stock Exchange");

        private final String fullName;

        Exchange(String fullName) {
            this.fullName = fullName;
        }

        public String getFullName() {
            return fullName;
        }
    }

    /**
     * Enum representing the industry sectors for stock classification.
     */
    public enum Sector {
        AUTO,
        BANK,
        CHEMICAL,
        CONSUMER_GOODS,
        ENERGY,
        FINANCIAL_SERVICES,
        FMCG,
        HEALTHCARE,
        IT,
        METALS,
        OIL_GAS,
        PHARMA,
        REAL_ESTATE,
        TELECOM,
        TEXTILES,
        UTILITIES,
        OTHERS
    }
}
