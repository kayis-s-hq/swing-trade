package com.swingtrade.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the Stock domain model.
 */
class StockTest {

    // Test data constants
    private static final String SYMBOL = "RELIANCE";
    private static final String NAME = "Reliance Industries Limited";
    private static final String ISIN = "INE002A01018";
    private static final Integer LOT_SIZE = 1;
    private static final LocalDate ADDED_ON = LocalDate.of(2024, 1, 15);

    // Exchange enum test cases
    private static final List<Stock.Exchange> EXCHANGES = Arrays.asList(
        Stock.Exchange.NSE,
        Stock.Exchange.BSE
    );

    // Sector enum test cases
    private static final List<Stock.Sector> SECTORS = Arrays.asList(
        Stock.Sector.AUTO,
        Stock.Sector.BANK,
        Stock.Sector.CHEMICAL,
        Stock.Sector.CONSUMER_GOODS,
        Stock.Sector.ENERGY,
        Stock.Sector.FINANCIAL_SERVICES,
        Stock.Sector.FMCG,
        Stock.Sector.HEALTHCARE,
        Stock.Sector.IT,
        Stock.Sector.METALS,
        Stock.Sector.OIL_GAS,
        Stock.Sector.PHARMA,
        Stock.Sector.REAL_ESTATE,
        Stock.Sector.TELECOM,
        Stock.Sector.TEXTILES,
        Stock.Sector.UTILITIES,
        Stock.Sector.OTHERS
    );

    /**
     * Helper method to create a valid Stock instance.
     */
    private Stock createValidStock() {
        return new Stock(
            SYMBOL,
            Stock.Exchange.NSE,
            NAME,
            Stock.Sector.OIL_GAS,
            ISIN,
            LOT_SIZE,
            ADDED_ON
        );
    }

    /**
     * Helper method to create a valid Stock instance with custom parameters.
     */
    private Stock createStock(String symbol, Stock.Exchange exchange, String name,
                              Stock.Sector sector, String isin, Integer lotSize, LocalDate addedOn) {
        return new Stock(symbol, exchange, name, sector, isin, lotSize, addedOn);
    }

    @Nested
    class ValidStockCreation {

        @Test
        void shouldCreateStockWithAllFields() {
            Stock stock = createValidStock();

            assertThat(stock.symbol()).isEqualTo(SYMBOL);
            assertThat(stock.exchange()).isEqualTo(Stock.Exchange.NSE);
            assertThat(stock.name()).isEqualTo(NAME);
            assertThat(stock.sector()).isEqualTo(Stock.Sector.OIL_GAS);
            assertThat(stock.isin()).isEqualTo(ISIN);
            assertThat(stock.lotSize()).isEqualTo(LOT_SIZE);
            assertThat(stock.addedOn()).isEqualTo(ADDED_ON);
        }

        @Test
        void shouldCreateStockWithNullFields() {
            Stock stock = new Stock(
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThat(stock.symbol()).isNull();
            assertThat(stock.exchange()).isNull();
            assertThat(stock.name()).isNull();
            assertThat(stock.sector()).isNull();
            assertThat(stock.isin()).isNull();
            assertThat(stock.lotSize()).isNull();
            assertThat(stock.addedOn()).isNull();
        }

        @Test
        void shouldCreateStockWithDifferentExchange() {
            Stock stock = createStock(
                "TCS",
                Stock.Exchange.BSE,
                "Tata Consultancy Services",
                Stock.Sector.IT,
                "INE467B01029",
                1,
                LocalDate.of(2024, 2, 1)
            );

            assertThat(stock.symbol()).isEqualTo("TCS");
            assertThat(stock.exchange()).isEqualTo(Stock.Exchange.BSE);
            assertThat(stock.sector()).isEqualTo(Stock.Sector.IT);
        }
    }

    @Nested
    class ExchangeEnum {

        @Test
        void shouldHaveAllExchangeValues() {
            assertThat(Stock.Exchange.values()).hasSize(2);
            assertThat(Stock.Exchange.values()).containsExactlyInAnyOrder(
                Stock.Exchange.NSE,
                Stock.Exchange.BSE
            );
        }

        @Test
        void shouldReturnFullNameForNSE() {
            Stock.Exchange nse = Stock.Exchange.NSE;
            assertThat(nse.getFullName()).isEqualTo("National Stock Exchange of India");
        }

        @Test
        void shouldReturnFullNameForBSE() {
            Stock.Exchange bse = Stock.Exchange.BSE;
            assertThat(bse.getFullName()).isEqualTo("Bombay Stock Exchange");
        }

        @Test
        void shouldCompareExchangeValuesCorrectly() {
            Stock.Exchange nse = Stock.Exchange.NSE;
            Stock.Exchange nseCopy = Stock.Exchange.NSE;

            assertThat(nse).isEqualTo(nseCopy);
            assertThat(nse).isNotEqualTo(Stock.Exchange.BSE);
        }

        @Test
        void shouldReturnHashCodeForExchangeValues() {
            assertThat(Stock.Exchange.NSE.hashCode()).isPositive();
            assertThat(Stock.Exchange.BSE.hashCode()).isPositive();
        }
    }

    @Nested
    class SectorEnum {

        @Test
        void shouldHaveAllSectorValues() {
            Stock.Sector[] sectorValues = Stock.Sector.values();
            assertThat(sectorValues).hasSize(17);

            // Verify all expected sectors are present
            List<Stock.Sector> allSectors = Arrays.asList(sectorValues);
            for (Stock.Sector expectedSector : SECTORS) {
                assertThat(allSectors).contains(expectedSector);
            }
        }

        @Test
        void shouldVerifyAllSectorValues() {
            assertThat(Stock.Sector.values()).contains(
                Stock.Sector.AUTO,
                Stock.Sector.BANK,
                Stock.Sector.CHEMICAL,
                Stock.Sector.CONSUMER_GOODS,
                Stock.Sector.ENERGY,
                Stock.Sector.FINANCIAL_SERVICES,
                Stock.Sector.FMCG,
                Stock.Sector.HEALTHCARE,
                Stock.Sector.IT,
                Stock.Sector.METALS,
                Stock.Sector.OIL_GAS,
                Stock.Sector.PHARMA,
                Stock.Sector.REAL_ESTATE,
                Stock.Sector.TELECOM,
                Stock.Sector.TEXTILES,
                Stock.Sector.UTILITIES,
                Stock.Sector.OTHERS
            );
        }

        @Test
        void shouldCompareSectorValuesCorrectly() {
            Stock.Sector it = Stock.Sector.IT;
            Stock.Sector itCopy = Stock.Sector.IT;
            Stock.Sector bank = Stock.Sector.BANK;

            assertThat(it).isEqualTo(itCopy);
            assertThat(it).isNotEqualTo(bank);
            assertThat(it).isNotEqualTo("IT");
        }

        @Test
        void shouldReturnHashCodeForSectorValues() {
            assertThat(Stock.Sector.IT.hashCode()).isPositive();
            assertThat(Stock.Sector.BANK.hashCode()).isPositive();
        }

        @Test
        void shouldHandleSectorSwitching() {
            Stock.Sector currentSector = Stock.Sector.IT;
            Stock.Sector nextSector = Stock.Sector.BANK;

            assertThat(currentSector).isNotEqualTo(nextSector);
            assertThat(currentSector.ordinal()).isNotEqualTo(nextSector.ordinal());
        }
    }

    @Nested
    class EqualsAndHashCode {

        private Stock stock1;
        private Stock stock2;
        private Stock stock3;

        @Test
        void shouldReturnTrueWhenComparingSameObject() {
            stock1 = createValidStock();
            assertThat(stock1).isEqualTo(stock1);
            assertThat(stock1.hashCode()).isEqualTo(stock1.hashCode());
        }

        @Test
        void shouldReturnTrueWhenComparingEqualStocks() {
            stock1 = createValidStock();
            stock2 = createValidStock();

            assertThat(stock1).isEqualTo(stock2);
            assertThat(stock1.hashCode()).isEqualTo(stock2.hashCode());
        }

        @Test
        void shouldReturnFalseWhenSymbolsDiffer() {
            stock1 = createValidStock();
            stock2 = createStock("TCS", Stock.Exchange.NSE, "TCS", Stock.Sector.IT, "INE467B01029", 1, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
            assertThat(stock1).isNotEqualTo("RELIANCE");
        }

        @Test
        void shouldReturnFalseWhenExchangesDiffer() {
            stock1 = createStock("RELIANCE", Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);
            stock2 = createStock("RELIANCE", Stock.Exchange.BSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnFalseWhenNamesDiffer() {
            stock1 = createValidStock();
            stock2 = createStock(SYMBOL, Stock.Exchange.NSE, "Different Name", Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnFalseWhenSectorsDiffer() {
            stock1 = createStock("RELIANCE", Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);
            stock2 = createStock("RELIANCE", Stock.Exchange.NSE, NAME, Stock.Sector.ENERGY, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnFalseWhenISINDiffer() {
            stock1 = createValidStock();
            stock2 = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, "INE002A01099", LOT_SIZE, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnFalseWhenLotSizesDiffer() {
            stock1 = createValidStock();
            stock2 = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, 10, ADDED_ON);

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnFalseWhenAddedOnDatesDiffer() {
            stock1 = createValidStock();
            stock2 = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, LocalDate.of(2024, 3, 1));

            assertThat(stock1).isNotEqualTo(stock2);
        }

        @Test
        void shouldReturnTrueWhenAllFieldsAreNull() {
            stock1 = new Stock(null, null, null, null, null, null, null);
            stock2 = new Stock(null, null, null, null, null, null, null);

            assertThat(stock1).isEqualTo(stock2);
            assertThat(stock1.hashCode()).isEqualTo(stock2.hashCode());
        }

        @Test
        void shouldReturnFalseWhenComparingToNonStockObject() {
            stock1 = createValidStock();

            assertThat(stock1).isNotEqualTo("RELIANCE");
            assertThat(stock1).isNotEqualTo(123);
            assertThat(stock1).isNotEqualTo(null);
        }
    }

    @Nested
    class ToString {

        @Test
        void shouldIncludeAllFieldsInToString() {
            Stock stock = createValidStock();
            String stockString = stock.toString();

            assertThat(stockString).contains("RELIANCE");
            assertThat(stockString).contains("NSE");
            assertThat(stockString).contains("Reliance Industries Limited");
            assertThat(stockString).contains("OIL_GAS");
            assertThat(stockString).contains("INE002A01018");
            assertThat(stockString).contains("1");
            assertThat(stockString).contains("2024-01-15");
        }

        @Test
        void shouldHandleNullFieldsInToString() {
            Stock stock = new Stock(null, null, null, null, null, null, null);
            String stockString = stock.toString();

            assertThat(stockString).startsWith("Stock[");
            assertThat(stockString).contains("null");
            assertThat(stockString).endsWith("]");
        }

        @Test
        void shouldFormatToStringProperly() {
            Stock stock = createStock("TCS", Stock.Exchange.BSE, "Tata Consultancy Services",
                Stock.Sector.IT, "INE467B01029", 1, LocalDate.of(2024, 2, 1));

            String stockString = stock.toString();

            assertThat(stockString).startsWith("Stock[");
            assertThat(stockString).endsWith("]");
            assertThat(stockString).contains("symbol=");
            assertThat(stockString).contains("exchange=");
            assertThat(stockString).contains("name=");
            assertThat(stockString).contains("sector=");
            assertThat(stockString).contains("isin=");
            assertThat(stockString).contains("lotSize=");
            assertThat(stockString).contains("addedOn=");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldHandleEmptyStringSymbol() {
            Stock stock = new Stock("", Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(stock.symbol()).isEmpty();
        }

        @Test
        void shouldHandleVeryLongName() {
            String veryLongName = "This is a very long company name that exceeds normal length limits for testing purposes";
            Stock stock = new Stock(SYMBOL, Stock.Exchange.NSE, veryLongName, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(stock.name()).isEqualTo(veryLongName);
        }

        @Test
        void shouldHandleZeroLotSize() {
            Stock stock = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, 0, ADDED_ON);

            assertThat(stock.lotSize()).isEqualTo(0);
        }

        @Test
        void shouldHandleNegativeLotSize() {
            Stock stock = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, -1, ADDED_ON);

            assertThat(stock.lotSize()).isEqualTo(-1);
        }

        @Test
        void shouldHandleFutureAddedOnDate() {
            LocalDate futureDate = LocalDate.of(2030, 12, 31);
            Stock stock = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, futureDate);

            assertThat(stock.addedOn()).isEqualTo(futureDate);
        }

        @Test
        void shouldHandlePastAddedOnDate() {
            LocalDate pastDate = LocalDate.of(1990, 1, 1);
            Stock stock = createStock(SYMBOL, Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, pastDate);

            assertThat(stock.addedOn()).isEqualTo(pastDate);
        }
    }

    @Nested
    class DifferentSectorCases {

        @Test
        void shouldCreateStockWithAUTO() {
            Stock stock = createStock("MARUTI", Stock.Exchange.NSE, "Maruti Suzuki India Limited",
                Stock.Sector.AUTO, "INE585B01010", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.AUTO);
        }

        @Test
        void shouldCreateStockWithBANK() {
            Stock stock = createStock("HDFCBANK", Stock.Exchange.NSE, "HDFC Bank Limited",
                Stock.Sector.BANK, "INE040A01034", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.BANK);
        }

        @Test
        void shouldCreateStockWithCHEMICAL() {
            Stock stock = createStock("URJA", Stock.Exchange.NSE, "Ujala Energy Ventures Limited",
                Stock.Sector.CHEMICAL, "INE876B01010", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.CHEMICAL);
        }

        @Test
        void shouldCreateStockWithFMCG() {
            Stock stock = createStock("HINDUNILVR", Stock.Exchange.NSE, "Hindustan Unilever Limited",
                Stock.Sector.FMCG, "INE030A01027", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.FMCG);
        }

        @Test
        void shouldCreateStockWithIT() {
            Stock stock = createStock("INFY", Stock.Exchange.NSE, "Infosys Limited",
                Stock.Sector.IT, "INE018A01030", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.IT);
        }

        @Test
        void shouldCreateStockWithPHARMA() {
            Stock stock = createStock("DRREDDY", Stock.Exchange.NSE, "Dr. Reddy's Laboratories Limited",
                Stock.Sector.PHARMA, "INE081A01020", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.PHARMA);
        }

        @Test
        void shouldCreateStockWithOTHERS() {
            Stock stock = createStock("VARI", Stock.Exchange.NSE, "Various Company",
                Stock.Sector.OTHERS, "INE123B01010", 1, ADDED_ON);

            assertThat(stock.sector()).isEqualTo(Stock.Sector.OTHERS);
        }
    }

    @Nested
    class DifferentExchangeCases {

        @Test
        void shouldCreateStockWithNSE() {
            Stock stock = createStock("TCS", Stock.Exchange.NSE, "Tata Consultancy Services",
                Stock.Sector.IT, "INE467B01029", 1, ADDED_ON);

            assertThat(stock.exchange()).isEqualTo(Stock.Exchange.NSE);
            assertThat(stock.exchange().getFullName()).isEqualTo("National Stock Exchange of India");
        }

        @Test
        void shouldCreateStockWithBSE() {
            Stock stock = createStock("RELIANCE", Stock.Exchange.BSE, "Reliance Industries Limited",
                Stock.Sector.OIL_GAS, "INE002A01018", 1, ADDED_ON);

            assertThat(stock.exchange()).isEqualTo(Stock.Exchange.BSE);
            assertThat(stock.exchange().getFullName()).isEqualTo("Bombay Stock Exchange");
        }

        @Test
        void shouldCompareStocksWithDifferentExchanges() {
            Stock nseStock = createStock("RELIANCE", Stock.Exchange.NSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);
            Stock bseStock = createStock("RELIANCE", Stock.Exchange.BSE, NAME, Stock.Sector.OIL_GAS, ISIN, LOT_SIZE, ADDED_ON);

            assertThat(nseStock).isNotEqualTo(bseStock);
            assertThat(nseStock.exchange()).isNotEqualTo(bseStock.exchange());
        }
    }
}
