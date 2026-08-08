package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PositionSizeValidator} covering position size validation,
 * percentage calculations, max size queries, recommended sizing, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionSizeValidatorTest {

    @Mock
    private BrokerProperties props;

    private PositionSizeValidator validator;

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000000");
    private static final BigDecimal MAX_CAPITAL_PER_TRADE = new BigDecimal("50000");
    private static final BigDecimal MAX_POSITION_SIZE_PCT = new BigDecimal("10");
    private static final BigDecimal MIN_POSITION_SIZE_PCT = new BigDecimal("1");

    @Nested
    class ValidatePositionSize {

        @Test
        void validSize_passes() {
            // Given: Set up mock properties
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("25000"));

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getCheckType()).isEqualTo("POSITION_SIZE_VALIDATION");
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("validated"));
        }

        @Test
        void exceedsMaxPerTrade_fails() {
            // Given
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("75000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("exceeds maximum per trade"));
        }

        @Test
        void exceedsMaxPercentage_fails() {
            // Given: 10% of 1,000,000 = 100,000 max allowed; raise per-trade limit so percentage check fires first
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("200000"));
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("150000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("exceeds maximum position size"));
        }

        @Test
        void belowMinimumWarning() {
            // Given: 1% of 1,000,000 = 10,000 minimum recommended
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("5000"));

            // Then
            assertThat(result.isPassed()).isFalse(); // addWarning sets passed=false
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("below minimum recommended"));
        }

        @Test
        void nullTradeValue_fails() {
            // Given
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(null);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("Invalid trade value"));
        }

        @Test
        void zeroTradeValue_fails() {
            // Given
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(BigDecimal.ZERO);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        void negativeTradeValue_fails() {
            // Given
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("-1000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        void exactMaxPerTrade_boundary_passes() {
            // Given: trade value exactly at max per trade
            when(props.getMaxCapitalPerTrade()).thenReturn(MAX_CAPITAL_PER_TRADE);
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(MAX_CAPITAL_PER_TRADE);

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void exactMaxPercentage_boundary_passes() {
            // Given: trade value exactly at max percentage (10% of 1,000,000 = 100,000)
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("200000"));
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("100000"));

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void bothMaxPerTradeAndMaxPercentage_checked() {
            // Given: value exceeds max per trade AND is within percentage
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("30000"));
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("40000"));

            // Then
            assertThat(result.isPassed()).isFalse();
            // Max per trade is checked first
            assertThat(result.getMessages()).anySatisfy(msg -> assertThat(msg).contains("exceeds maximum per trade"));
        }
    }

    @Nested
    class CalculatePositionPercent {

        @Test
        void basic() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("25000"));

            // Then
            assertThat(pct).isEqualByComparingTo(new BigDecimal("2.5000"));
        }

        @Test
        void zeroCapital_returnsZero() {
            // Given
            when(props.getInitialCapital()).thenReturn(BigDecimal.ZERO);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("5000"));

            // Then
            assertThat(pct).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void largePosition() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("500000"));

            // Then
            assertThat(pct).isEqualByComparingTo(new BigDecimal("50.0000"));
        }

        @Test
        void exactlyOneHundredPercent() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("1000000"));

            // Then
            assertThat(pct).isEqualByComparingTo(new BigDecimal("100.0000"));
        }

        @Test
        void smallFraction_rounded() {
            // Given
            when(props.getInitialCapital()).thenReturn(new BigDecimal("300000"));
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("1000"));

            // Then: 1000/300000 * 100 = 0.3333 (rounded to 4 decimal places)
            assertThat(pct).isEqualByComparingTo(new BigDecimal("0.3333"));
        }
    }

    @Nested
    class GetMaxPositionSize {

        @Test
        void fromProperties() {
            // Given: 10% of 1,000,000 = 100,000
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal max = validator.getMaxPositionSize();

            // Then
            assertThat(max).isEqualByComparingTo(new BigDecimal("100000"));
        }

        @Test
        void withDifferentCapital() {
            // Given
            when(props.getInitialCapital()).thenReturn(new BigDecimal("500000"));
            when(props.getMaxPositionSizePercentage()).thenReturn(new BigDecimal("5"));
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal max = validator.getMaxPositionSize();

            // Then
            assertThat(max).isEqualByComparingTo(new BigDecimal("25000"));
        }

        @Test
        void withSmallPercentage() {
            // Given
            when(props.getInitialCapital()).thenReturn(new BigDecimal("1000000"));
            when(props.getMaxPositionSizePercentage()).thenReturn(new BigDecimal("2"));
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal max = validator.getMaxPositionSize();

            // Then
            assertThat(max).isEqualByComparingTo(new BigDecimal("20000"));
        }
    }

    @Nested
    class CalculateRecommendedPositionSize {

        @Test
        void withinLimits() {
            // Given: riskPerTrade = 5 means 5% of capital
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal recommended = validator.calculateRecommendedPositionSize(new BigDecimal("5"));

            // Then
            assertThat(recommended).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        void atBoundary() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal recommended = validator.calculateRecommendedPositionSize(new BigDecimal("10"));

            // Then
            assertThat(recommended).isEqualByComparingTo(new BigDecimal("100000"));
        }

        @Test
        void zeroCapital() {
            // Given
            when(props.getInitialCapital()).thenReturn(BigDecimal.ZERO);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal recommended = validator.calculateRecommendedPositionSize(new BigDecimal("5"));

            // Then
            assertThat(recommended).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void smallRiskPercentage() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal recommended = validator.calculateRecommendedPositionSize(new BigDecimal("1"));

            // Then
            assertThat(recommended).isEqualByComparingTo(new BigDecimal("10000"));
        }

        @Test
        void largeRiskPercentage() {
            // Given
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal recommended = validator.calculateRecommendedPositionSize(new BigDecimal("25"));

            // Then
            assertThat(recommended).isEqualByComparingTo(new BigDecimal("250000"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void veryLargeValues() {
            // Given
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("10000000000"));
            when(props.getInitialCapital()).thenReturn(new BigDecimal("10000000000"));
            when(props.getMaxPositionSizePercentage()).thenReturn(new BigDecimal("50"));
            when(props.getMinPositionSizePercentage()).thenReturn(new BigDecimal("0.1"));
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("5000000000"));

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void decimalPrecision() {
            // Given
            when(props.getInitialCapital()).thenReturn(new BigDecimal("750000"));
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("100000"));
            when(props.getMaxPositionSizePercentage()).thenReturn(new BigDecimal("15"));
            when(props.getMinPositionSizePercentage()).thenReturn(new BigDecimal("2"));
            validator = new PositionSizeValidator(props);

            // When
            BigDecimal pct = validator.calculatePositionPercent(new BigDecimal("12345"));

            // Then: 12345/750000 * 100 = 1.646 (rounded to 4 decimal places)
            assertThat(pct).isEqualByComparingTo(new BigDecimal("1.6460"));
        }

        @Test
        void boundaryCondition_atMinRecommended() {
            // Given: 1% of 1,000,000 = 10,000 minimum recommended
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("200000"));
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("10000"));

            // Then: exactly at minimum, no warning
            assertThat(result.isPassed()).isTrue();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        void boundaryCondition_justBelowMinRecommended() {
            // Given: 0.99% of 1,000,000 = 9,900 (just below 10,000 minimum)
            when(props.getMaxCapitalPerTrade()).thenReturn(new BigDecimal("200000"));
            when(props.getInitialCapital()).thenReturn(INITIAL_CAPITAL);
            when(props.getMaxPositionSizePercentage()).thenReturn(MAX_POSITION_SIZE_PCT);
            when(props.getMinPositionSizePercentage()).thenReturn(MIN_POSITION_SIZE_PCT);
            validator = new PositionSizeValidator(props);

            // When
            RiskCheckResult result = validator.validatePositionSize(new BigDecimal("9900"));

            // Then: below minimum, warning issued (passed=false because addWarning sets it)
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
        }
    }
}
