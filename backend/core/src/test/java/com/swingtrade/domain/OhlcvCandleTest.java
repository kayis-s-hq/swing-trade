package com.swingtrade.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the OhlcvCandle domain model.
 */
class OhlcvCandleTest {

    // Test data generators
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);
    private static final BigDecimal OPEN = BigDecimal.valueOf(2400.50);
    private static final BigDecimal HIGH = BigDecimal.valueOf(2450.75);
    private static final BigDecimal LOW = BigDecimal.valueOf(2380.25);
    private static final BigDecimal CLOSE = BigDecimal.valueOf(2440.00);
    private static final Long VOLUME = 1500000L;
    private static final BigDecimal ADJ_CLOSE = BigDecimal.valueOf(2440.00);

    private OhlcvCandle createValidCandle() {
        return new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);
    }

    private OhlcvCandle createValidCandleWithoutAdjClose() {
        return OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);
    }

    /**
     * Tests for valid OhlcvCandle creation with all fields populated.
     */
    @Nested
    class CandleCreation {

        @Test
        void shouldCreateCandleWithAllFields() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.symbol()).isEqualTo(SYMBOL);
            assertThat(candle.date()).isEqualTo(DATE);
            assertThat(candle.open()).isEqualTo(OPEN);
            assertThat(candle.high()).isEqualTo(HIGH);
            assertThat(candle.low()).isEqualTo(LOW);
            assertThat(candle.close()).isEqualTo(CLOSE);
            assertThat(candle.volume()).isEqualTo(VOLUME);
            assertThat(candle.adjClose()).isEqualTo(ADJ_CLOSE);
        }

        @Test
        void shouldCreateCandleWithFactoryMethod() {
            OhlcvCandle candle = createValidCandleWithoutAdjClose();

            assertThat(candle.symbol()).isEqualTo(SYMBOL);
            assertThat(candle.date()).isEqualTo(DATE);
            assertThat(candle.open()).isEqualTo(OPEN);
            assertThat(candle.high()).isEqualTo(HIGH);
            assertThat(candle.low()).isEqualTo(LOW);
            assertThat(candle.close()).isEqualTo(CLOSE);
            assertThat(candle.volume()).isEqualTo(VOLUME);
            assertThat(candle.adjClose()).isEqualTo(CLOSE);
        }

        @Test
        void shouldCreateCandleWithDifferentValues() {
            OhlcvCandle candle = OhlcvCandle.of(
                "TCS",
                LocalDate.of(2024, 1, 16),
                BigDecimal.valueOf(3800.00),
                BigDecimal.valueOf(3850.50),
                BigDecimal.valueOf(3780.25),
                BigDecimal.valueOf(3840.75),
                2000000L
            );

            assertThat(candle.symbol()).isEqualTo("TCS");
            assertThat(candle.date()).isEqualTo(LocalDate.of(2024, 1, 16));
            assertThat(candle.open()).isEqualTo(BigDecimal.valueOf(3800.00));
            assertThat(candle.high()).isEqualTo(BigDecimal.valueOf(3850.50));
            assertThat(candle.low()).isEqualTo(BigDecimal.valueOf(3780.25));
            assertThat(candle.close()).isEqualTo(BigDecimal.valueOf(3840.75));
            assertThat(candle.volume()).isEqualTo(2000000L);
            assertThat(candle.adjClose()).isEqualTo(BigDecimal.valueOf(3840.75));
        }

        @Test
        void shouldCreateCandleWithZeroVolume() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, 0L);

            assertThat(candle.volume()).isEqualTo(0L);
        }

        @Test
        void adjustedForAnalysisScalesRawOhlcToAdjustedClose() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE,
                new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("40"),
                new BigDecimal("50"), VOLUME, new BigDecimal("25"));

            OhlcvCandle adjusted = candle.adjustedForAnalysis();

            assertThat(adjusted.open()).isEqualByComparingTo("25");
            assertThat(adjusted.high()).isEqualByComparingTo("30");
            assertThat(adjusted.low()).isEqualByComparingTo("20");
            assertThat(adjusted.close()).isEqualByComparingTo("25");
            assertThat(adjusted.adjClose()).isEqualByComparingTo("25");
        }

        @Test
        void adjustedForAnalysisFallsBackWhenAdjustmentIsInvalid() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME, null);

            assertThat(candle.adjustedForAnalysis()).isSameAs(candle);
        }

        @Test
        void shouldCreateCandleWithVeryLargeValues() {
            OhlcvCandle candle = OhlcvCandle.of(
                "HIGHVALUE",
                DATE,
                new BigDecimal("10000.50"),
                new BigDecimal("10500.75"),
                new BigDecimal("9900.25"),
                new BigDecimal("10450.00"),
                50000000L
            );

            assertThat(candle.open()).isEqualTo(new BigDecimal("10000.50"));
            assertThat(candle.volume()).isEqualTo(50000000L);
        }
    }

    /**
     * Tests for date validation.
     */
    @Nested
    class DateValidation {

        @Test
        void shouldAllowCandleWithValidDate() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle.date()).isEqualTo(DATE);
        }

        @Test
        void shouldAllowCandleWithPastDate() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, LocalDate.of(2020, 1, 1), OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle.date()).isEqualTo(LocalDate.of(2020, 1, 1));
        }

        @Test
        void shouldAllowCandleWithFutureDate() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, LocalDate.of(2030, 12, 31), OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle.date()).isEqualTo(LocalDate.of(2030, 12, 31));
        }

        @Test
        void shouldAllowCandleWithTodayDate() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, LocalDate.now(), OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle.date()).isEqualTo(LocalDate.now());
        }
    }

    /**
     * Tests for OHLC values validation.
     */
    @Nested
    class OHLCValidation {

        @Test
        void shouldCreateCandleWithPositiveOHLCValues() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.open().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
            assertThat(candle.high().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
            assertThat(candle.low().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
            assertThat(candle.close().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }

        @Test
        void shouldCreateCandleWithZeroValues() {
            BigDecimal zero = BigDecimal.ZERO;
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, zero, zero, zero, zero, VOLUME, zero);

            assertThat(candle.open()).isEqualByComparingTo(zero);
            assertThat(candle.high()).isEqualByComparingTo(zero);
            assertThat(candle.low()).isEqualByComparingTo(zero);
            assertThat(candle.close()).isEqualByComparingTo(zero);
        }

        @Test
        void shouldCreateCandleWithSmallDecimalValues() {
            BigDecimal tiny = new BigDecimal("0.0001");
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, tiny, tiny, tiny, tiny, VOLUME, tiny);

            assertThat(candle.open()).isEqualTo(tiny);
        }

        @Test
        void shouldMaintainHighAsMaximumOfOpenAndClose() {
            OhlcvCandle candle = createValidCandle();

            BigDecimal maxOfOpenClose = OPEN.max(CLOSE);
            assertThat(candle.high()).isGreaterThanOrEqualTo(maxOfOpenClose);
        }

        @Test
        void shouldMaintainHighGreaterThanOrEqualOpen() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.high()).isGreaterThanOrEqualTo(OPEN);
        }

        @Test
        void shouldMaintainHighGreaterThanOrEqualClose() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.high()).isGreaterThanOrEqualTo(CLOSE);
        }

        @Test
        void shouldMaintainLowAsMinimumOfOpenAndClose() {
            OhlcvCandle candle = createValidCandle();

            BigDecimal minOfOpenClose = OPEN.min(CLOSE);
            assertThat(candle.low()).isLessThanOrEqualTo(minOfOpenClose);
        }

        @Test
        void shouldMaintainLowLessThanOrEqualOpen() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.low()).isLessThanOrEqualTo(OPEN);
        }

        @Test
        void shouldMaintainLowLessThanOrEqualClose() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.low()).isLessThanOrEqualTo(CLOSE);
        }

        @Test
        void shouldHandleBearishCandleWithHighAndLow() {
            BigDecimal bearishOpen = BigDecimal.valueOf(100.00);
            BigDecimal bearishClose = BigDecimal.valueOf(95.00);
            BigDecimal bearishHigh = BigDecimal.valueOf(101.50);
            BigDecimal bearishLow = BigDecimal.valueOf(94.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, bearishOpen, bearishHigh, bearishLow, bearishClose, VOLUME, bearishClose);

            assertThat(candle.high()).isEqualTo(bearishHigh);
            assertThat(candle.low()).isEqualTo(bearishLow);
            assertThat(candle.high()).isGreaterThanOrEqualTo(bearishOpen);
            assertThat(candle.high()).isGreaterThanOrEqualTo(bearishClose);
            assertThat(candle.low()).isLessThanOrEqualTo(bearishOpen);
            assertThat(candle.low()).isLessThanOrEqualTo(bearishClose);
        }
    }

    /**
     * Tests for volume validation.
     */
    @Nested
    class VolumeValidation {

        @Test
        void shouldCreateCandleWithPositiveVolume() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.volume()).isGreaterThan(0L);
        }

        @Test
        void shouldCreateCandleWithZeroVolume() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, 0L, ADJ_CLOSE);

            assertThat(candle.volume()).isEqualTo(0L);
        }

        @Test
        void shouldCreateCandleWithVeryLargeVolume() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, 1000000000L, ADJ_CLOSE);

            assertThat(candle.volume()).isEqualTo(1000000000L);
        }

        @Test
        void shouldAllowNullVolume() {
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, null, ADJ_CLOSE);

            assertThat(candle.volume()).isNull();
        }
    }

    /**
     * Tests for Factory method OhlcvCandle.of().
     */
    @Nested
    class FactoryMethodTests {

        @Test
        void shouldCreateCandleWithAdjCloseEqualToClose() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.adjClose()).isEqualTo(CLOSE);
        }

        @Test
        void shouldCreateCandleWithCorrectSymbol() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.symbol()).isEqualTo(SYMBOL);
        }

        @Test
        void shouldCreateCandleWithCorrectDate() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.date()).isEqualTo(DATE);
        }

        @Test
        void shouldCreateCandleWithCorrectOpen() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.open()).isEqualTo(OPEN);
        }

        @Test
        void shouldCreateCandleWithCorrectHigh() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.high()).isEqualTo(HIGH);
        }

        @Test
        void shouldCreateCandleWithCorrectLow() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.low()).isEqualTo(LOW);
        }

        @Test
        void shouldCreateCandleWithCorrectClose() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.close()).isEqualTo(CLOSE);
        }

        @Test
        void shouldCreateCandleWithCorrectVolume() {
            OhlcvCandle candle = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            assertThat(candle.volume()).isEqualTo(VOLUME);
        }

        @Test
        void shouldCreateDifferentCandleInstances() {
            OhlcvCandle candle1 = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);
            OhlcvCandle candle2 = OhlcvCandle.of(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME);

            // Same values, but different instances
            assertThat(candle1).isEqualTo(candle2);
            assertThat(candle1).isNotSameAs(candle2);
        }
    }

    /**
     * Tests for getRange() calculation.
     */
    @Nested
    class GetRangeTests {

        @Test
        void shouldCalculateRangeAsHighMinusLow() {
            OhlcvCandle candle = createValidCandle();

            BigDecimal expectedRange = HIGH.subtract(LOW);
            BigDecimal actualRange = candle.getRange();

            assertThat(actualRange).isEqualTo(expectedRange);
        }

        @Test
        void shouldCalculateRangeCorrectly() {
            OhlcvCandle candle = createValidCandle();

            // HIGH = 2450.75, LOW = 2380.25
            // Range = 2450.75 - 2380.25 = 70.50
            BigDecimal expectedRange = new BigDecimal("70.50");
            assertThat(candle.getRange()).isEqualByComparingTo(expectedRange);
        }

        @Test
        void shouldCalculateRangeForZeroValues() {
            BigDecimal zero = BigDecimal.ZERO;
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, zero, zero, zero, zero, VOLUME, zero);

            assertThat(candle.getRange()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldCalculateRangeForBearishCandle() {
            BigDecimal open = BigDecimal.valueOf(100.00);
            BigDecimal high = BigDecimal.valueOf(102.50);
            BigDecimal low = BigDecimal.valueOf(98.00);
            BigDecimal close = BigDecimal.valueOf(99.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            // Range = 102.50 - 98.00 = 4.50
            BigDecimal expectedRange = BigDecimal.valueOf(4.50);
            assertThat(candle.getRange()).isEqualTo(expectedRange);
        }

        @Test
        void shouldReturnNonNegativeRange() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.getRange()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    /**
     * Tests for getChangePercent() calculation.
     */
    @Nested
    class GetChangePercentTests {

        @Test
        void shouldCalculateChangePercentForBullishCandle() {
            // OPEN = 2400.50, CLOSE = 2440.00
            // Change% = (2440.00 - 2400.50) / 2400.50 * 100 = 1.6455%
            OhlcvCandle candle = createValidCandle();

            BigDecimal expectedChange = new BigDecimal("1.6455");
            assertThat(candle.getChangePercent()).isEqualByComparingTo(expectedChange);
        }

        @Test
        void shouldCalculateChangePercentForBearishCandle() {
            BigDecimal open = BigDecimal.valueOf(100.00);
            BigDecimal close = BigDecimal.valueOf(95.00);
            BigDecimal high = BigDecimal.valueOf(101.00);
            BigDecimal low = BigDecimal.valueOf(94.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            // Change% = (95.00 - 100.00) / 100.00 * 100 = -5.00%
            BigDecimal expectedChange = new BigDecimal("-5.00");
            assertThat(candle.getChangePercent()).isEqualByComparingTo(expectedChange);
        }

        @Test
        void shouldReturnZeroWhenOpenIsZero() {
            BigDecimal zero = BigDecimal.ZERO;
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, zero, BigDecimal.valueOf(100), zero, BigDecimal.valueOf(100), VOLUME, BigDecimal.valueOf(100));

            assertThat(candle.getChangePercent()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldReturnZeroWhenOpenEqualsClose() {
            BigDecimal sameValue = BigDecimal.valueOf(100.00);
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, sameValue, BigDecimal.valueOf(105), BigDecimal.valueOf(95), sameValue, VOLUME, sameValue);

            assertThat(candle.getChangePercent()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldRoundToFourDecimalPlaces() {
            BigDecimal open = new BigDecimal("100.0001");
            BigDecimal close = new BigDecimal("100.0002");
            BigDecimal high = new BigDecimal("100.0003");
            BigDecimal low = new BigDecimal("99.9999");

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            BigDecimal actualPercent = candle.getChangePercent();
            // Verify it has at most 4 decimal places
            assertThat(actualPercent.scale()).isLessThanOrEqualTo(4);
        }

        @Test
        void shouldCalculateSmallChangePercent() {
            BigDecimal open = BigDecimal.valueOf(1000.00);
            BigDecimal close = BigDecimal.valueOf(1000.50);
            BigDecimal high = BigDecimal.valueOf(1001.00);
            BigDecimal low = BigDecimal.valueOf(999.50);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            // Change% = (1000.50 - 1000.00) / 1000.00 * 100 = 0.05%
            BigDecimal expectedChange = new BigDecimal("0.0500");
            assertThat(candle.getChangePercent()).isEqualTo(expectedChange);
        }
    }

    /**
     * Tests for isBullish() and isBearish() methods.
     */
    @Nested
    class BullishBearishTests {

        @Test
        void shouldReturnTrueForBullishCandle() {
            // CLOSE (2440.00) > OPEN (2400.50)
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.isBullish()).isTrue();
        }

        @Test
        void shouldReturnFalseForBearishCandle() {
            BigDecimal open = BigDecimal.valueOf(100.00);
            BigDecimal close = BigDecimal.valueOf(95.00);
            BigDecimal high = BigDecimal.valueOf(101.00);
            BigDecimal low = BigDecimal.valueOf(94.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            assertThat(candle.isBullish()).isFalse();
        }

        @Test
        void shouldReturnTrueForBearishCandle() {
            BigDecimal open = BigDecimal.valueOf(100.00);
            BigDecimal close = BigDecimal.valueOf(95.00);
            BigDecimal high = BigDecimal.valueOf(101.00);
            BigDecimal low = BigDecimal.valueOf(94.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            assertThat(candle.isBearish()).isTrue();
        }

        @Test
        void shouldReturnFalseForBullishCandleWhenBearish() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle.isBearish()).isFalse();
        }

        @Test
        void shouldReturnFalseWhenOpenEqualsClose() {
            BigDecimal sameValue = BigDecimal.valueOf(100.00);
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, sameValue, BigDecimal.valueOf(105), BigDecimal.valueOf(95), sameValue, VOLUME, sameValue);

            assertThat(candle.isBullish()).isFalse();
            assertThat(candle.isBearish()).isFalse();
        }

        @Test
        void shouldHandleCandleWithNegativeChange() {
            BigDecimal open = BigDecimal.valueOf(2000.00);
            BigDecimal close = BigDecimal.valueOf(1950.00);
            BigDecimal high = BigDecimal.valueOf(2010.00);
            BigDecimal low = BigDecimal.valueOf(1940.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            assertThat(candle.isBearish()).isTrue();
            assertThat(candle.isBullish()).isFalse();
        }

        @Test
        void shouldHandleCandleWithPositiveChange() {
            BigDecimal open = BigDecimal.valueOf(2000.00);
            BigDecimal close = BigDecimal.valueOf(2050.00);
            BigDecimal high = BigDecimal.valueOf(2060.00);
            BigDecimal low = BigDecimal.valueOf(1990.00);

            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, open, high, low, close, VOLUME, close);

            assertThat(candle.isBullish()).isTrue();
            assertThat(candle.isBearish()).isFalse();
        }

        @Test
        void shouldHandleOpenEqualsClose() {
            BigDecimal same = BigDecimal.valueOf(100.00);
            OhlcvCandle candle = new OhlcvCandle(SYMBOL, DATE, same, BigDecimal.valueOf(102), BigDecimal.valueOf(98), same, VOLUME, same);

            assertThat(candle.isBullish()).isFalse();
            assertThat(candle.isBearish()).isFalse();
        }
    }

    /**
     * Tests for equality and hash code.
     */
    @Nested
    class EqualityAndHashTests {

        @Test
        void shouldEqualWhenAllFieldsAreSame() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = createValidCandle();

            assertThat(candle1).isEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenSymbolsDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle("TCS", DATE, OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenDatesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, LocalDate.of(2024, 1, 16), OPEN, HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenOpenValuesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, BigDecimal.valueOf(2410.00), HIGH, LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenHighValuesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, OPEN, BigDecimal.valueOf(2460.00), LOW, CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenLowValuesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, BigDecimal.valueOf(2390.00), CLOSE, VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenCloseValuesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, BigDecimal.valueOf(2450.00), VOLUME, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenVolumesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, 2000000L, ADJ_CLOSE);

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldNotEqualWhenAdjCloseValuesDiffer() {
            OhlcvCandle candle1 = createValidCandle();
            OhlcvCandle candle2 = new OhlcvCandle(SYMBOL, DATE, OPEN, HIGH, LOW, CLOSE, VOLUME, BigDecimal.valueOf(2450.00));

            assertThat(candle1).isNotEqualTo(candle2);
        }

        @Test
        void shouldBeSameWhenReferencingSameObject() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle).isSameAs(candle);
        }

        @Test
        void shouldNotEqualWhenComparingWithNull() {
            OhlcvCandle candle = createValidCandle();

            assertThat(candle).isNotEqualTo(null);
        }

        @Test
        void shouldGenerateConsistentHashCode() {
            OhlcvCandle candle = createValidCandle();
            int hashCode1 = candle.hashCode();
            int hashCode2 = candle.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }
    }

    /**
     * Tests for toString() method.
     */
    @Nested
    class ToStringTests {

        @Test
        void shouldReturnCorrectStringFormat() {
            OhlcvCandle candle = createValidCandle();
            String toString = candle.toString();

            assertThat(toString).contains("OhlcvCandle");
            assertThat(toString).contains("symbol=" + SYMBOL);
            assertThat(toString).contains("date=" + DATE);
            assertThat(toString).contains("open=" + OPEN);
            assertThat(toString).contains("high=" + HIGH);
            assertThat(toString).contains("low=" + LOW);
            assertThat(toString).contains("close=" + CLOSE);
            assertThat(toString).contains("volume=" + VOLUME);
            assertThat(toString).contains("adjClose=" + ADJ_CLOSE);
        }
    }
}
