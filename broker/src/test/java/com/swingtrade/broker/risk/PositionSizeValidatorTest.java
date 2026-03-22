package com.swingtrade.broker.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PositionSizeValidator.
 */
class PositionSizeValidatorTest {

    private PositionSizeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PositionSizeValidator();
    }

    @Test
    void testValidatePositionSize_ValidSize() {
        // When
        RiskCheckResult result = validator.validatePositionSize(new BigDecimal("50000"));

        // Then
        assertThat(result.isPassed()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testValidatePositionSize_ExceedsMaxCapital() {
        // Given - set max to a lower value for testing
        validator = new PositionSizeValidator() {
            @Override
            public BigDecimal getMaxCapitalPerTrade() {
                return new BigDecimal("25000"); // Lower threshold
            }
        };

        // When
        RiskCheckResult result = validator.validatePositionSize(new BigDecimal("50000"));

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testValidatePositionSize_NullValue() {
        // When
        RiskCheckResult result = validator.validatePositionSize(null);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testValidatePositionSize_ZeroValue() {
        // When
        RiskCheckResult result = validator.validatePositionSize(BigDecimal.ZERO);

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testValidatePositionSize_NegativeValue() {
        // When
        RiskCheckResult result = validator.validatePositionSize(new BigDecimal("-1000"));

        // Then
        assertThat(result.isPassed()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testCalculatePositionPercent() {
        // Given
        BigDecimal tradeValue = new BigDecimal("100000");

        // When
        BigDecimal percent = validator.calculatePositionPercent(tradeValue);

        // Then
        assertThat(percent).isNotNull();
        assertThat(percent.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void testGetMaxPositionSize() {
        // When
        BigDecimal maxSize = validator.getMaxPositionSize();

        // Then
        assertThat(maxSize).isNotNull();
        assertThat(maxSize.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void testCalculateRecommendedPositionSize() {
        // Given
        BigDecimal riskPercent = new BigDecimal("2.0");

        // When
        BigDecimal recommended = validator.calculateRecommendedPositionSize(riskPercent);

        // Then
        assertThat(recommended).isNotNull();
        assertThat(recommended.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void testValidatePositionSize_MinWarning() {
        // Given - create validator with higher minimum
        validator = new PositionSizeValidator() {
            @Override
            public BigDecimal getMinPositionSizePercentage() {
                return new BigDecimal("5"); // 5% minimum
            }
        };

        // When
        RiskCheckResult result = validator.validatePositionSize(new BigDecimal("10000"));

        // Then
        assertThat(result.hasWarnings()).isTrue();
    }
}
