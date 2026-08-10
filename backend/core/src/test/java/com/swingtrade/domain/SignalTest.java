package com.swingtrade.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the Signal domain model.
 */
class SignalTest {

    // Test data generators
    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);
    private static final BigDecimal CONFIDENCE = BigDecimal.valueOf(0.85);
    private static final String REASONING = "Bullish momentum with RSI breakout";
    private static final BigDecimal ENTRY_PRICE = BigDecimal.valueOf(2440.00);
    private static final BigDecimal STOP_LOSS = BigDecimal.valueOf(2400.00);
    private static final BigDecimal TARGET = BigDecimal.valueOf(2500.00);
    private static final BigDecimal RISK_REWARD = new BigDecimal("1.5");
    private static final String INDICATORS = "RSI,MACD,VWAP";

    private Signal createValidBuySignal() {
        return new Signal(
            1L,
            SYMBOL,
            DATE,
            Signal.SignalType.BUY,
            CONFIDENCE,
            REASONING,
            ENTRY_PRICE,
            STOP_LOSS,
            TARGET,
            RISK_REWARD,
            INDICATORS,
            LocalDate.now(),
            null,
            null
        );
    }

    private Signal createValidSellSignal() {
        return new Signal(
            2L,
            SYMBOL,
            DATE,
            Signal.SignalType.SELL,
            CONFIDENCE,
            REASONING,
            ENTRY_PRICE,
            STOP_LOSS,
            TARGET,
            RISK_REWARD,
            INDICATORS,
            LocalDate.now(),
            null,
            null
        );
    }

    private Signal createValidHoldSignal() {
        return new Signal(
            3L,
            SYMBOL,
            DATE,
            Signal.SignalType.HOLD,
            CONFIDENCE,
            REASONING,
            ENTRY_PRICE,
            STOP_LOSS,
            TARGET,
            RISK_REWARD,
            INDICATORS,
            LocalDate.now(),
            null,
            null
        );
    }

    /**
     * Tests for valid Signal creation with all fields populated.
     */
    @Nested
    class SignalCreation {

        @Test
        void shouldCreateBuySignalWithAllFields() {
            Signal signal = createValidBuySignal();

            assertThat(signal.id()).isEqualTo(1L);
            assertThat(signal.symbol()).isEqualTo(SYMBOL);
            assertThat(signal.date()).isEqualTo(DATE);
            assertThat(signal.type()).isEqualTo(Signal.SignalType.BUY);
            assertThat(signal.confidence()).isEqualTo(CONFIDENCE);
            assertThat(signal.reasoning()).isEqualTo(REASONING);
            assertThat(signal.entryPrice()).isEqualTo(ENTRY_PRICE);
            assertThat(signal.stopLoss()).isEqualTo(STOP_LOSS);
            assertThat(signal.target()).isEqualTo(TARGET);
            assertThat(signal.riskReward()).isEqualTo(RISK_REWARD);
            assertThat(signal.indicators()).isEqualTo(INDICATORS);
        }

        @Test
        void shouldCreateSellSignalWithAllFields() {
            Signal signal = createValidSellSignal();

            assertThat(signal.type()).isEqualTo(Signal.SignalType.SELL);
        }

        @Test
        void shouldCreateHoldSignalWithAllFields() {
            Signal signal = createValidHoldSignal();

            assertThat(signal.type()).isEqualTo(Signal.SignalType.HOLD);
        }

        @Test
        void shouldCreateSignalWithNullValues() {
            Signal signal = new Signal(
                null,
                "TCS",
                null,
                Signal.SignalType.HOLD,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThat(signal.symbol()).isEqualTo("TCS");
            assertThat(signal.type()).isEqualTo(Signal.SignalType.HOLD);
            assertThat(signal.id()).isNull();
            assertThat(signal.date()).isNull();
            assertThat(signal.confidence()).isNull();
            assertThat(signal.reasoning()).isNull();
        }

        @Test
        void shouldCreateSignalWithDifferentSymbol() {
            Signal signal = Signal.create(
                "INFY",
                LocalDate.of(2024, 1, 16),
                Signal.SignalType.BUY,
                BigDecimal.valueOf(0.75),
                "Breakout above resistance"
            );

            assertThat(signal.symbol()).isEqualTo("INFY");
            assertThat(signal.type()).isEqualTo(Signal.SignalType.BUY);
            assertThat(signal.confidence()).isEqualTo(BigDecimal.valueOf(0.75));
        }

        @Test
        void shouldCreateSignalWithDifferentDate() {
            Signal signal = Signal.create(
                SYMBOL,
                LocalDate.of(2025, 12, 31),
                Signal.SignalType.SELL,
                BigDecimal.valueOf(0.90),
                "Bearish divergence"
            );

            assertThat(signal.date()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(signal.type()).isEqualTo(Signal.SignalType.SELL);
        }
    }

    /**
     * Tests for Symbol validation.
     */
    @Nested
    class SymbolValidation {

        @Test
        void shouldCreateSignalWithValidSymbol() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.symbol()).isEqualTo(SYMBOL);
        }

        @Test
        void shouldCreateSignalWithShortSymbol() {
            Signal signal = Signal.create("AAPL", DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.symbol()).isEqualTo("AAPL");
        }

        @Test
        void shouldCreateSignalWithLongSymbol() {
            Signal signal = Signal.create("INFRATECH", DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.symbol()).isEqualTo("INFRATECH");
        }

        @Test
        void shouldCreateSignalWithNullSymbol() {
            Signal signal = new Signal(
                1L,
                null,
                DATE,
                Signal.SignalType.HOLD,
                CONFIDENCE,
                REASONING,
                null,
                null,
                null,
                null,
                null,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.symbol()).isNull();
        }
    }

    /**
     * Tests for SignalType enum values (BUY, SELL, HOLD).
     */
    @Nested
    class SignalTypeEnumTests {

        @Test
        void shouldCreateSignalWithBUYType() {
            Signal signal = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal.type()).isEqualTo(Signal.SignalType.BUY);
        }

        @Test
        void shouldCreateSignalWithSELLType() {
            Signal signal = new Signal(1L, SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal.type()).isEqualTo(Signal.SignalType.SELL);
        }

        @Test
        void shouldCreateSignalWithHOLDType() {
            Signal signal = new Signal(1L, SYMBOL, DATE, Signal.SignalType.HOLD, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal.type()).isEqualTo(Signal.SignalType.HOLD);
        }

        @Test
        void shouldReturnCorrectDescriptionForBUY() {
            assertThat(Signal.SignalType.BUY.getDescription()).isEqualTo("Buy Signal");
        }

        @Test
        void shouldReturnCorrectDescriptionForSELL() {
            assertThat(Signal.SignalType.SELL.getDescription()).isEqualTo("Sell Signal");
        }

        @Test
        void shouldReturnCorrectDescriptionForHOLD() {
            assertThat(Signal.SignalType.HOLD.getDescription()).isEqualTo("Hold");
        }

        @Test
        void shouldCompareSignalTypeValuesCorrectly() {
            assertThat(Signal.SignalType.BUY).isEqualTo(Signal.SignalType.BUY);
            assertThat(Signal.SignalType.SELL).isEqualTo(Signal.SignalType.SELL);
            assertThat(Signal.SignalType.HOLD).isEqualTo(Signal.SignalType.HOLD);

            assertThat(Signal.SignalType.BUY).isNotEqualTo(Signal.SignalType.SELL);
            assertThat(Signal.SignalType.SELL).isNotEqualTo(Signal.SignalType.HOLD);
            assertThat(Signal.SignalType.BUY).isNotEqualTo(Signal.SignalType.HOLD);
        }
    }

    /**
     * Tests for confidence normalization (clamping to 0.0-1.0).
     */
    @Nested
    class ConfidenceValidation {

        @Test
        void shouldCreateSignalWithConfidenceInRange() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(0.5), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.valueOf(0.5));
        }

        @Test
        void shouldCreateSignalWithConfidenceExactlyZero() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.ZERO, REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldCreateSignalWithConfidenceExactlyOne() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.ONE, REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void shouldClampNegativeConfidenceToZero() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(-0.5), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldClampVeryNegativeConfidenceToZero() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(-10.0), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldClampConfidenceGreaterThanOneToOne() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(1.5), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void shouldClampVeryLargeConfidenceToOne() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(100.0), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void shouldPreserveExactConfidenceValue() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, new BigDecimal("0.847532"), REASONING);

            assertThat(signal.confidence()).isEqualTo(new BigDecimal("0.847532"));
        }

        @Test
        void shouldClampConfidenceWhenCreatingWithFactoryMethod() {
            Signal signal1 = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(-1.0), REASONING);
            Signal signal2 = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(2.0), REASONING);

            assertThat(signal1.confidence()).isEqualTo(BigDecimal.ZERO);
            assertThat(signal2.confidence()).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void shouldClampConfidenceAtBoundaryValues() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, new BigDecimal("0.999999"), REASONING);

            assertThat(signal.confidence()).isEqualTo(new BigDecimal("0.999999"));
        }
    }

    /**
     * Tests for Price validation (entryPrice, stopLoss, target must be positive when set).
     */
    @Nested
    class PriceValidation {

        @Test
        void shouldCreateSignalWithPositiveEntryPrice() {
            Signal signal = new Signal(
                1L,
                SYMBOL,
                DATE,
                Signal.SignalType.BUY,
                CONFIDENCE,
                REASONING,
                BigDecimal.valueOf(2440.00),
                STOP_LOSS,
                TARGET,
                RISK_REWARD,
                INDICATORS,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.entryPrice()).isEqualTo(BigDecimal.valueOf(2440.00));
        }

        @Test
        void shouldCreateSignalWithPositiveStopLoss() {
            Signal signal = new Signal(
                1L,
                SYMBOL,
                DATE,
                Signal.SignalType.BUY,
                CONFIDENCE,
                REASONING,
                ENTRY_PRICE,
                BigDecimal.valueOf(2400.00),
                TARGET,
                RISK_REWARD,
                INDICATORS,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.stopLoss()).isEqualTo(BigDecimal.valueOf(2400.00));
        }

        @Test
        void shouldCreateSignalWithPositiveTarget() {
            Signal signal = new Signal(
                1L,
                SYMBOL,
                DATE,
                Signal.SignalType.BUY,
                CONFIDENCE,
                REASONING,
                ENTRY_PRICE,
                STOP_LOSS,
                BigDecimal.valueOf(2500.00),
                RISK_REWARD,
                INDICATORS,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.target()).isEqualTo(BigDecimal.valueOf(2500.00));
        }

        @Test
        void shouldAllowNullPrices() {
            Signal signal = new Signal(
                1L,
                SYMBOL,
                DATE,
                Signal.SignalType.BUY,
                CONFIDENCE,
                REASONING,
                null,
                null,
                null,
                RISK_REWARD,
                INDICATORS,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.entryPrice()).isNull();
            assertThat(signal.stopLoss()).isNull();
            assertThat(signal.target()).isNull();
        }

        @Test
        void shouldAllowZeroPrices() {
            Signal signal = new Signal(
                1L,
                SYMBOL,
                DATE,
                Signal.SignalType.BUY,
                CONFIDENCE,
                REASONING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                RISK_REWARD,
                INDICATORS,
                LocalDate.now(),
            null,
            null
            );

            assertThat(signal.entryPrice()).isEqualTo(BigDecimal.ZERO);
            assertThat(signal.stopLoss()).isEqualTo(BigDecimal.ZERO);
            assertThat(signal.target()).isEqualTo(BigDecimal.ZERO);
        }
    }

    /**
     * Tests for Factory method Signal.create().
     */
    @Nested
    class FactoryMethodTests {

        @Test
        void shouldCreateSignalWithNullId() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.id()).isNull();
        }

        @Test
        void shouldCreateSignalWithCorrectSymbol() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.symbol()).isEqualTo(SYMBOL);
        }

        @Test
        void shouldCreateSignalWithCorrectDate() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.date()).isEqualTo(DATE);
        }

        @Test
        void shouldCreateSignalWithCorrectType() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.type()).isEqualTo(Signal.SignalType.BUY);
        }

        @Test
        void shouldCreateSignalWithClampedConfidence() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(1.5), REASONING);

            assertThat(signal.confidence()).isEqualTo(BigDecimal.ONE);
        }

        @Test
        void shouldCreateSignalWithCorrectReasoning() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.reasoning()).isEqualTo(REASONING);
        }

        @Test
        void shouldCreateSignalWithNullEntryPrice() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.entryPrice()).isNull();
        }

        @Test
        void shouldCreateSignalWithNullStopLoss() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.stopLoss()).isNull();
        }

        @Test
        void shouldCreateSignalWithNullTarget() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.target()).isNull();
        }

        @Test
        void shouldCreateSignalWithNullRiskReward() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.riskReward()).isNull();
        }

        @Test
        void shouldCreateSignalWithNullIndicators() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.indicators()).isNull();
        }

        @Test
        void shouldSetGeneratedAtToCurrentDate() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.generatedAt()).isEqualTo(LocalDate.now());
        }

        @Test
        void shouldCreateDifferentSignalInstances() {
            Signal signal1 = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);
            Signal signal2 = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal1).isEqualTo(signal2);
            assertThat(signal1).isNotSameAs(signal2);
        }
    }

    /**
     * Tests for isBuySignal(), isSellSignal(), isHoldSignal() methods.
     */
    @Nested
    class SignalTypeCheckTests {

        @Test
        void shouldReturnTrueForBuySignal() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.isBuySignal()).isTrue();
        }

        @Test
        void shouldReturnFalseForSellSignalAsBuy() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING);

            assertThat(signal.isBuySignal()).isFalse();
        }

        @Test
        void shouldReturnFalseForHoldSignalAsBuy() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.HOLD, CONFIDENCE, REASONING);

            assertThat(signal.isBuySignal()).isFalse();
        }

        @Test
        void shouldReturnTrueForSellSignal() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING);

            assertThat(signal.isSellSignal()).isTrue();
        }

        @Test
        void shouldReturnFalseForBuySignalAsSell() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.isSellSignal()).isFalse();
        }

        @Test
        void shouldReturnFalseForHoldSignalAsSell() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.HOLD, CONFIDENCE, REASONING);

            assertThat(signal.isSellSignal()).isFalse();
        }

        @Test
        void shouldReturnTrueForHoldSignal() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.HOLD, CONFIDENCE, REASONING);

            assertThat(signal.isHoldSignal()).isTrue();
        }

        @Test
        void shouldReturnFalseForBuySignalAsHold() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);

            assertThat(signal.isHoldSignal()).isFalse();
        }

        @Test
        void shouldReturnFalseForSellSignalAsHold() {
            Signal signal = Signal.create(SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING);

            assertThat(signal.isHoldSignal()).isFalse();
        }

        @Test
        void shouldOnlyReturnTrueForOneType() {
            Signal buySignal = Signal.create(SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING);
            Signal sellSignal = Signal.create(SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING);
            Signal holdSignal = Signal.create(SYMBOL, DATE, Signal.SignalType.HOLD, CONFIDENCE, REASONING);

            assertThat(buySignal.isBuySignal()).isTrue();
            assertThat(buySignal.isSellSignal()).isFalse();
            assertThat(buySignal.isHoldSignal()).isFalse();

            assertThat(sellSignal.isBuySignal()).isFalse();
            assertThat(sellSignal.isSellSignal()).isTrue();
            assertThat(sellSignal.isHoldSignal()).isFalse();

            assertThat(holdSignal.isBuySignal()).isFalse();
            assertThat(holdSignal.isSellSignal()).isFalse();
            assertThat(holdSignal.isHoldSignal()).isTrue();
        }
    }

    /**
     * Tests for equals(), hashCode(), toString() methods.
     */
    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldEqualWhenAllFieldsAreSame() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = createValidBuySignal();

            assertThat(signal1).isEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenIdsDiffer() {
            Signal signal1 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);
            Signal signal2 = new Signal(2L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenSymbolsDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, "TCS", DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenDatesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, LocalDate.of(2024, 1, 16), Signal.SignalType.BUY, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenTypesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.SELL, CONFIDENCE, REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenConfidencesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, BigDecimal.valueOf(0.5), REASONING, null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenReasoningsDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, "Different reasoning", null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenEntryPricesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, BigDecimal.valueOf(2500.00), STOP_LOSS, TARGET, RISK_REWARD, INDICATORS, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenStopLossesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, ENTRY_PRICE, BigDecimal.valueOf(2450.00), TARGET, RISK_REWARD, INDICATORS, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenTargetsDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, ENTRY_PRICE, STOP_LOSS, BigDecimal.valueOf(2550.00), RISK_REWARD, INDICATORS, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenRiskRewardsDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, ENTRY_PRICE, STOP_LOSS, TARGET, new BigDecimal("2.0"), INDICATORS, LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenIndicatorsDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, ENTRY_PRICE, STOP_LOSS, TARGET, RISK_REWARD, "Different indicators", LocalDate.now(), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldNotEqualWhenGeneratedAtDatesDiffer() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = new Signal(1L, SYMBOL, DATE, Signal.SignalType.BUY, CONFIDENCE, REASONING, ENTRY_PRICE, STOP_LOSS, TARGET, RISK_REWARD, INDICATORS, LocalDate.of(2025, 1, 1), null, null);

            assertThat(signal1).isNotEqualTo(signal2);
        }

        @Test
        void shouldBeSameWhenReferencingSameObject() {
            Signal signal = createValidBuySignal();

            assertThat(signal).isSameAs(signal);
        }

        @Test
        void shouldNotEqualWhenComparingWithNull() {
            Signal signal = createValidBuySignal();

            assertThat(signal).isNotEqualTo(null);
        }

        @Test
        void shouldNotEqualWhenComparingWithDifferentType() {
            Signal signal = createValidBuySignal();

            assertThat(signal).isNotEqualTo("String");
        }

        @Test
        void shouldGenerateConsistentHashCode() {
            Signal signal = createValidBuySignal();
            int hashCode1 = signal.hashCode();
            int hashCode2 = signal.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }

        @Test
        void shouldGenerateDifferentHashCodesForDifferentSignals() {
            Signal signal1 = createValidBuySignal();
            Signal signal2 = createValidSellSignal();

            // Both should have valid hash codes
            int hash1 = signal1.hashCode();
            int hash2 = signal2.hashCode();

            assertThat(hash1).isNotEqualTo(0);
            assertThat(hash2).isNotEqualTo(0);
        }
    }

    /**
     * Tests for toString() method output.
     */
    @Nested
    class ToStringTests {

        @Test
        void shouldReturnCorrectStringFormat() {
            Signal signal = createValidBuySignal();
            String toString = signal.toString();

            assertThat(toString).contains("Signal");
            assertThat(toString).contains("id=1");
            assertThat(toString).contains("symbol=RELIANCE");
            assertThat(toString).contains("date=" + DATE);
            assertThat(toString).contains("type=BUY");
            assertThat(toString).contains("confidence=0.85");
            assertThat(toString).contains("reasoning=" + REASONING);
            assertThat(toString).contains("entryPrice=2440.0");
            assertThat(toString).contains("stopLoss=2400.0");
            assertThat(toString).contains("target=2500.0");
            assertThat(toString).contains("riskReward=1.5");
            assertThat(toString).contains("indicators=RSI,MACD,VWAP");
        }

        @Test
        void shouldReturnCorrectStringFormatWithNullValues() {
            Signal signal = new Signal(
                null,
                "TCS",
                null,
                Signal.SignalType.HOLD,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            String toString = signal.toString();

            assertThat(toString).contains("symbol=TCS");
            assertThat(toString).contains("type=HOLD");
            assertThat(toString).contains("confidence=null");
            assertThat(toString).contains("generatedAt=");
        }
    }
}
