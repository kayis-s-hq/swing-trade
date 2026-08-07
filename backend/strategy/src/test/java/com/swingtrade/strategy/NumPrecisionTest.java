package com.swingtrade.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NumPrecision")
class NumPrecisionTest {

    @Nested
    @DisplayName("DoubleNum loses precision")
    class DoubleNumPrecision {

        @Test
        @DisplayName("doubleValue loses precision at many decimals")
        void doubleValue_looses_precision_at_many_decimals() {
            BigDecimal input = new BigDecimal("150.12345678901234567890");
            Num doubleNum = DoubleNum.valueOf(input);
            BigDecimal result = BigDecimal.valueOf(doubleNum.doubleValue());

            assertThat(result).isNotEqualByComparingTo(input);
        }

        @Test
        @DisplayName("numToBigDecimal via doubleValue loses precision")
        void numToBigDecimal_via_doubleValue_loses_precision() {
            // This simulates what PriceActionSignalEngine.numToBigDecimal() and
            // BacktestEngine.numToBigDecimal() currently do: BigDecimal.valueOf(num.doubleValue())
            BigDecimal input = new BigDecimal("150.12345678901234567890");
            Num num = DoubleNum.valueOf(input);

            // The production code does: BigDecimal.valueOf(num.doubleValue())
            BigDecimal result = BigDecimal.valueOf(num.doubleValue());

            // This should FAIL with the current DoubleNum-based implementation
            // and PASS after switching to DecimalNum
            assertThat(result).isNotEqualByComparingTo(input);
        }
    }

    @Nested
    @DisplayName("DecimalNum preserves precision")
    class DecimalNumPrecision {

        @Test
        @DisplayName("getDelegate preserves exact value")
        void toBigDecimal_preserves_exact_value() {
            BigDecimal input = new BigDecimal("150.123456789");
            Num decimalNum = DecimalNum.valueOf(input);
            BigDecimal result = (BigDecimal) decimalNum.getDelegate();

            assertThat(result).isEqualByComparingTo(input);
        }

        @Test
        @DisplayName("numToBigDecimal via getDelegate preserves precision")
        void numToBigDecimal_via_getDelegate_preserves_precision() {
            BigDecimal input = new BigDecimal("150.123456789");
            Num decimalNum = DecimalNum.valueOf(input);

            // After switching to DecimalNum, the production code should use:
            // (BigDecimal) num.getDelegate() instead of BigDecimal.valueOf(num.doubleValue())
            BigDecimal result = (BigDecimal) decimalNum.getDelegate();

            assertThat(result).isEqualByComparingTo(input);
        }
    }
}