package com.swingtrade.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the SentimentResult domain model.
 */
class SentimentResultTest {

    private static final String SYMBOL = "RELIANCE";
    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);
    private static final String SUMMARY = "Positive earnings report and strong guidance";
    private static final String RAW_CONTENT = "News article content about company earnings";
    private static final Double CONFIDENCE = 0.85;

    @Nested
    class SentimentResultCreation {

        @Test
        void shouldCreateSentimentResultWithPositiveScore() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.symbol()).isEqualTo(SYMBOL);
            assertThat(result.date()).isEqualTo(DATE);
            assertThat(result.score()).isEqualTo(SentimentResult.SentimentScore.POSITIVE);
            assertThat(result.summary()).isEqualTo(SUMMARY);
            assertThat(result.rawContent()).isEqualTo(RAW_CONTENT);
            assertThat(result.confidence()).isEqualTo(0.85);
            assertThat(result.analyzedAt()).isEqualTo(LocalDate.now());
            assertThat(result.id()).isNull();
        }

        @Test
        void shouldCreateSentimentResultWithNeutralScore() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.score()).isEqualTo(SentimentResult.SentimentScore.NEUTRAL);
        }

        @Test
        void shouldCreateSentimentResultWithNegativeScore() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.score()).isEqualTo(SentimentResult.SentimentScore.NEGATIVE);
        }

        @Test
        void shouldCreateSentimentResultWithNullId() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.id()).isNull();
        }

        @Test
        void shouldSetAnalyzedAtToCurrentDate() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.analyzedAt()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    class ConfidenceNormalization {

        @Test
        void shouldNormalizeConfidenceInRange() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 0.75
            );

            assertThat(result.confidence()).isEqualTo(0.75);
        }

        @Test
        void shouldNormalizeConfidenceToZeroWhenNegative() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, -0.5
            );

            assertThat(result.confidence()).isEqualTo(0.0);
        }

        @Test
        void shouldNormalizeConfidenceToOneWhenGreaterThanOne() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 1.5
            );

            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        void shouldNormalizeConfidenceAtZeroBoundary() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 0.0
            );

            assertThat(result.confidence()).isEqualTo(0.0);
        }

        @Test
        void shouldNormalizeConfidenceAtOneBoundary() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 1.0
            );

            assertThat(result.confidence()).isEqualTo(1.0);
        }

        @Test
        void shouldHandleNullConfidence() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, null
            );

            assertThat(result.confidence()).isNull();
        }

        @Test
        void shouldNormalizeConfidenceAtHighPrecision() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 0.9999
            );

            assertThat(result.confidence()).isEqualTo(0.9999);
        }
    }

    @Nested
    class SentimentScoreEnumTests {

        @Test
        void shouldHaveAllEnumValues() {
            assertThat(SentimentResult.SentimentScore.values()).hasSize(3);
            assertThat(SentimentResult.SentimentScore.values())
                .containsExactlyInAnyOrder(
                    SentimentResult.SentimentScore.POSITIVE,
                    SentimentResult.SentimentScore.NEUTRAL,
                    SentimentResult.SentimentScore.NEGATIVE
                );
        }

        @Test
        void shouldReturnCorrectDisplayNameForPositive() {
            assertThat(SentimentResult.SentimentScore.POSITIVE.getDisplayName()).isEqualTo("Positive");
        }

        @Test
        void shouldReturnCorrectDisplayNameForNeutral() {
            assertThat(SentimentResult.SentimentScore.NEUTRAL.getDisplayName()).isEqualTo("Neutral");
        }

        @Test
        void shouldReturnCorrectDisplayNameForNegative() {
            assertThat(SentimentResult.SentimentScore.NEGATIVE.getDisplayName()).isEqualTo("Negative");
        }

        @Test
        void shouldCompareSentimentScoreValuesCorrectly() {
            assertThat(SentimentResult.SentimentScore.POSITIVE).isEqualTo(SentimentResult.SentimentScore.POSITIVE);
            assertThat(SentimentResult.SentimentScore.POSITIVE).isNotEqualTo(SentimentResult.SentimentScore.NEUTRAL);
            assertThat(SentimentResult.SentimentScore.POSITIVE).isNotEqualTo(SentimentResult.SentimentScore.NEGATIVE);
        }

        @Test
        void shouldReturnHashCodeForSentimentScoreValues() {
            assertThat(SentimentResult.SentimentScore.POSITIVE.hashCode()).isPositive();
            assertThat(SentimentResult.SentimentScore.NEUTRAL.hashCode()).isPositive();
            assertThat(SentimentResult.SentimentScore.NEGATIVE.hashCode()).isPositive();
        }
    }

    @Nested
    class SentimentCheckMethods {

        @Test
        void shouldReturnTrueForPositiveSentimentIsPositive() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isPositive()).isTrue();
        }

        @Test
        void shouldReturnFalseForPositiveSentimentIsNeutral() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNeutral()).isFalse();
        }

        @Test
        void shouldReturnFalseForPositiveSentimentIsNegative() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNegative()).isFalse();
        }

        @Test
        void shouldReturnTrueForNeutralSentimentIsNeutral() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNeutral()).isTrue();
        }

        @Test
        void shouldReturnFalseForNeutralSentimentIsPositive() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isPositive()).isFalse();
        }

        @Test
        void shouldReturnFalseForNeutralSentimentIsNegative() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNegative()).isFalse();
        }

        @Test
        void shouldReturnTrueForNegativeSentimentIsNegative() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNegative()).isTrue();
        }

        @Test
        void shouldReturnFalseForNegativeSentimentIsPositive() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isPositive()).isFalse();
        }

        @Test
        void shouldReturnFalseForNegativeSentimentIsNeutral() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.isNeutral()).isFalse();
        }
    }

    @Nested
    class SupportsEntryTests {

        @Test
        void shouldReturnTrueForPositiveSentimentSupportsEntry() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.supportsEntry()).isTrue();
        }

        @Test
        void shouldReturnTrueForNeutralSentimentSupportsEntry() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.supportsEntry()).isTrue();
        }

        @Test
        void shouldReturnFalseForNegativeSentimentSupportsEntry() {
            SentimentResult result = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(result.supportsEntry()).isFalse();
        }

        @Test
        void shouldCombinePositiveAndNeutralAsSupportingEntry() {
            SentimentResult positiveResult = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );
            SentimentResult neutralResult = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE
            );
            SentimentResult negativeResult = SentimentResult.create(
                SYMBOL, DATE, SentimentResult.SentimentScore.NEGATIVE, SUMMARY, RAW_CONTENT, CONFIDENCE
            );

            assertThat(positiveResult.supportsEntry()).isTrue();
            assertThat(neutralResult.supportsEntry()).isTrue();
            assertThat(negativeResult.supportsEntry()).isFalse();
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldEqualWhenAllFieldsAreSame() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            SentimentResult result2 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result1).isEqualTo(result2);
        }

        @Test
        void shouldNotEqualWhenSymbolsDiffer() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            SentimentResult result2 = SentimentResult.create("TCS", DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        void shouldNotEqualWhenDatesDiffer() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            SentimentResult result2 = SentimentResult.create(SYMBOL, LocalDate.of(2024, 1, 16), SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        void shouldNotEqualWhenScoresDiffer() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            SentimentResult result2 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.NEUTRAL, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        void shouldNotEqualWhenSummariesDiffer() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            SentimentResult result2 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, "Different summary", RAW_CONTENT, CONFIDENCE);

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        void shouldNotEqualWhenConfidencesDiffer() {
            SentimentResult result1 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 0.85);
            SentimentResult result2 = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, 0.75);

            assertThat(result1).isNotEqualTo(result2);
        }

        @Test
        void shouldBeSameWhenReferencingSameObject() {
            SentimentResult result = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result).isSameAs(result);
        }

        @Test
        void shouldNotEqualWhenComparingWithNull() {
            SentimentResult result = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);

            assertThat(result).isNotEqualTo(null);
        }

        @Test
        void shouldGenerateConsistentHashCode() {
            SentimentResult result = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            int hashCode1 = result.hashCode();
            int hashCode2 = result.hashCode();

            assertThat(hashCode1).isEqualTo(hashCode2);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldReturnCorrectStringFormat() {
            SentimentResult result = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, CONFIDENCE);
            String toString = result.toString();

            assertThat(toString).contains("SentimentResult");
            assertThat(toString).contains("symbol=" + SYMBOL);
            assertThat(toString).contains("date=" + DATE);
            assertThat(toString).contains("score=POSITIVE");
            assertThat(toString).contains("summary=" + SUMMARY);
            assertThat(toString).contains("confidence=0.85");
        }

        @Test
        void shouldHandleNullConfidenceInToString() {
            SentimentResult result = SentimentResult.create(SYMBOL, DATE, SentimentResult.SentimentScore.POSITIVE, SUMMARY, RAW_CONTENT, null);
            String toString = result.toString();

            assertThat(toString).contains("confidence=null");
        }
    }
}
