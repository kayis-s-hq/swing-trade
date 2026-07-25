package com.swingtrade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

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
    String industry,
    Long marketCap,
    BigDecimal peRatio,
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
        OTHERS;

        private static final java.util.Map<String, Sector> DB_NAME_MAP;
        static {
            DB_NAME_MAP = new java.util.HashMap<>();
            DB_NAME_MAP.put("Oil & Gas", OIL_GAS);
            DB_NAME_MAP.put("Oil and Gas", OIL_GAS);
            DB_NAME_MAP.put("Banking", BANK);
            DB_NAME_MAP.put("Banks", BANK);
            DB_NAME_MAP.put("Pharmaceuticals", PHARMA);
            DB_NAME_MAP.put("Pharma", PHARMA);
            DB_NAME_MAP.put("Healthcare", HEALTHCARE);
            DB_NAME_MAP.put("Information Technology", IT);
            DB_NAME_MAP.put("Software", IT);
            DB_NAME_MAP.put("Metals", METALS);
            DB_NAME_MAP.put("Cement", CONSUMER_GOODS);
            DB_NAME_MAP.put("Fast Moving Consumer Goods", FMCG);
            DB_NAME_MAP.put("Power", ENERGY);
            DB_NAME_MAP.put("Electricity", ENERGY);
            DB_NAME_MAP.put("Power & Energy", ENERGY);
            DB_NAME_MAP.put("Telecommunications", TELECOM);
            DB_NAME_MAP.put("Real Estate", REAL_ESTATE);
            DB_NAME_MAP.put("Textiles", TEXTILES);
            DB_NAME_MAP.put("Chemicals", CHEMICAL);
            DB_NAME_MAP.put("Consumer Goods", CONSUMER_GOODS);
            DB_NAME_MAP.put("Financial Services", FINANCIAL_SERVICES);
        }

        public static Sector fromDbName(String name) {
            if (name == null) return OTHERS;
            try {
                return Sector.valueOf(name.toUpperCase(Locale.ROOT).replace(" ", "_").replace("&", ""));
            } catch (IllegalArgumentException e) {
                Sector mapped = DB_NAME_MAP.get(name);
                if (mapped != null) return mapped;
                return OTHERS;
            }
        }
    }
}
