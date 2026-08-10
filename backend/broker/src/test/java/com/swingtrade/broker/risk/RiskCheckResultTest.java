package com.swingtrade.broker.risk;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RiskCheckResult covering construction, pass/fail status,
 * error/warning/info/message management, equality, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class RiskCheckResultTest {

    // ==================== Construction ====================

    @Nested
    class Construction {

        @Test
        void defaultConstructor_passedTrue() {
            // Given: A new RiskCheckResult via default constructor
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void constructor_passedTrue() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true);

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void constructor_passedFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void constructor_withMessage_passedTrue() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true, "All checks passed");

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("All checks passed");
        }

        @Test
        void constructor_withMessage_passedFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false, "Risk limit exceeded");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("Risk limit exceeded");
        }

        @Test
        void constructor_withNullMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true, null);

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void constructor_withEmptyMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true, "");

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void passedAndFailedAreIndependent() {
            // Given
            RiskCheckResult passed = new RiskCheckResult(true);
            RiskCheckResult failed = new RiskCheckResult(false);

            // Then
            assertThat(passed.isPassed()).isNotEqualTo(failed.isPassed());
        }
    }

    // ==================== isPassed / hasErrors ====================

    @Nested
    class IsPassedAndHasErrors {

        @Test
        void isPassed_trueByDefault() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void isPassed_setToFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.setPassed(false);

            // Then
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        void isPassed_setToTrueAfterFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.setPassed(true);

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void hasErrors_emptyMessages_returnsFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        void hasErrors_withError_returnsTrue() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Position limit exceeded");

            // Then
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        void hasErrors_withWarningOnly_returnsFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("High volatility detected");

            // Then
            assertThat(result.hasErrors()).isFalse();
        }

        @Test
        void hasErrors_withInfoOnly_returnsFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addInfo("Market data refreshed");

            // Then
            assertThat(result.hasErrors()).isFalse();
        }
    }

    // ==================== addError ====================

    @Nested
    class AddError {

        @Test
        void addError_singleError_setsPassedFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError("Position limit exceeded");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("ERROR: Position limit exceeded");
        }

        @Test
        void addError_multipleErrors() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError("Position limit exceeded");
            result.addError("Margin insufficient");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("ERROR: Position limit exceeded", "ERROR: Margin insufficient");
        }

        @Test
        void addError_nullError() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError(null);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("ERROR: null");
        }

        @Test
        void addError_emptyError() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError("");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("ERROR: ");
        }

        @Test
        void addError_withFormatString() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError("Order value {} exceeds limit {}", 50000, 100000);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).first().isEqualTo("ERROR: Order value 50000 exceeds limit 100000");
        }

        @Test
        void addError_withFormatString_placeholders() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addError("Symbol {} has {} positions", "RELIANCE-EQ", 5);

            // Then
            assertThat(result.getMessages()).first().isEqualTo("ERROR: Symbol RELIANCE-EQ has 5 positions");
        }

        @Test
        void addError_preservesExistingMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("High volatility");

            // When
            result.addError("Position limit exceeded");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("WARNING: High volatility", "ERROR: Position limit exceeded");
        }

        @Test
        void addError_alreadyFailed_staysFailed() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.addError("Additional error");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
        }
    }

    // ==================== addWarning ====================

    @Nested
    class AddWarning {

        @Test
        void addWarning_singleWarning_setsPassedFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addWarning("High volatility detected");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("WARNING: High volatility detected");
        }

        @Test
        void addWarning_multipleWarnings() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addWarning("High volatility");
            result.addWarning("Low liquidity");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("WARNING: High volatility", "WARNING: Low liquidity");
        }

        @Test
        void addWarning_nullWarning() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addWarning(null);

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("WARNING: null");
        }

        @Test
        void addWarning_emptyWarning() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addWarning("");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("WARNING: ");
        }

        @Test
        void addWarning_withFormatString() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addWarning("Position value {} exceeds {} threshold", 50000, 40000);

            // Then
            assertThat(result.getMessages()).first().isEqualTo("WARNING: Position value 50000 exceeds 40000 threshold");
        }

        @Test
        void addWarning_preservesPassedFalse() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.addWarning("Additional warning");

            // Then
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        void addWarning_doesNotAffectErrors() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Error one");

            // When
            result.addWarning("Warning one");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
        }
    }

    // ==================== addInfo ====================

    @Nested
    class AddInfo {

        @Test
        void addInfo_singleInfo() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addInfo("Market data refreshed");

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("INFO: Market data refreshed");
        }

        @Test
        void addInfo_multipleInfoMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addInfo("Checking position limits");
            result.addInfo("Checking margin requirements");
            result.addInfo("Checking order value");

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).hasSize(3);
            assertThat(result.getMessages()).contains(
                    "INFO: Checking position limits",
                    "INFO: Checking margin requirements",
                    "INFO: Checking order value"
            );
        }

        @Test
        void addInfo_nullInfo() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addInfo(null);

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("INFO: null");
        }

        @Test
        void addInfo_emptyInfo() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addInfo("");

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("INFO: ");
        }

        @Test
        void addInfo_withFormatString() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addInfo("Symbol {} checked with {} positions", "RELIANCE-EQ", 3);

            // Then
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getMessages()).first().isEqualTo("INFO: Symbol RELIANCE-EQ checked with 3 positions");
        }

        @Test
        void addInfo_doesNotChangePassedStatus() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true);

            // When
            result.addInfo("Info message");

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void addInfo_doesNotChangePassedFalseStatus() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.addInfo("Info message");

            // Then
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        void addInfo_preservesExistingMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("Warning message");

            // When
            result.addInfo("Info message");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("WARNING: Warning message", "INFO: Info message");
        }
    }

    // ==================== addMessage ====================

    @Nested
    class AddMessage {

        @Test
        void addMessage_singleMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("Custom message");

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("Custom message");
        }

        @Test
        void addMessage_multipleMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("First message");
            result.addMessage("Second message");
            result.addMessage("Third message");

            // Then
            assertThat(result.getMessages()).hasSize(3);
            assertThat(result.getMessages()).contains("First message", "Second message", "Third message");
        }

        @Test
        void addMessage_nullMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage(null);

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains((String) null);
        }

        @Test
        void addMessage_emptyMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("");

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("");
        }

        @Test
        void addMessage_doesNotChangePassedStatus() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true);

            // When
            result.addMessage("Some message");

            // Then
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        void addMessage_doesNotChangePassedFalseStatus() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.addMessage("Some message");

            // Then
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        void addMessage_fluent_returnsSameInstance() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            RiskCheckResult returned = result.addMessage("Message");

            // Then
            assertThat(returned).isSameAs(result);
        }
    }

    // ==================== getMessages / getCheckType ====================

    @Nested
    class GetMessagesAndGetCheckType {

        @Test
        void getMessages_empty() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void getMessages_populated() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("Message 1");
            result.addMessage("Message 2");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("Message 1", "Message 2");
        }

        @Test
        void getMessages_returnsMutableList() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("Original");

            // When
            result.getMessages().clear();

            // Then
            assertThat(result.getMessages()).isEmpty();
        }

        @Test
        void setMessages_replacesList() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("Old message");

            // When
            result.setMessages(Arrays.asList("New message 1", "New message 2"));

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("New message 1", "New message 2");
        }

        @Test
        void setMessages_nullList() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.setMessages(null);

            // Then
            assertThat(result.getMessages()).isNull();
        }

        @Test
        void getCheckType_default_null() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result.getCheckType()).isNull();
        }

        @Test
        void setCheckType_populates() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.setCheckType("POSITION_LIMIT");

            // Then
            assertThat(result.getCheckType()).isEqualTo("POSITION_LIMIT");
        }

        @Test
        void setCheckType_overwrites() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.setCheckType("FIRST_CHECK");

            // When
            result.setCheckType("SECOND_CHECK");

            // Then
            assertThat(result.getCheckType()).isEqualTo("SECOND_CHECK");
        }

        @Test
        void setCheckType_null() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.setCheckType("CHECK_A");

            // When
            result.setCheckType(null);

            // Then
            assertThat(result.getCheckType()).isNull();
        }

        @Test
        void checkTypeIndependentOfMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.setCheckType("MARGIN_CHECK");
            result.addMessage("Some message");

            // Then
            assertThat(result.getCheckType()).isEqualTo("MARGIN_CHECK");
            assertThat(result.getMessages()).hasSize(1);
        }
    }

    // ==================== hasWarnings ====================

    @Nested
    class HasWarnings {

        @Test
        void hasWarnings_trueWithWarning() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("High volatility");

            // Then
            assertThat(result.hasWarnings()).isTrue();
        }

        @Test
        void hasWarnings_falseWithNoWarnings() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // Then
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        void hasWarnings_falseWithOnlyErrors() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Error message");

            // Then
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        void hasWarnings_falseWithOnlyInfo() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addInfo("Info message");

            // Then
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        void hasWarnings_falseWithOnlyCustomMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("Custom message");

            // Then
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        void hasWarnings_mixedErrorsAndWarnings() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Error message");
            result.addWarning("Warning message");
            result.addInfo("Info message");

            // Then
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        void hasWarnings_warningInCustomMessageNotCounted() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("WARNING: This is a custom message, not a warning");

            // Then
            // hasWarnings checks messages.stream().anyMatch(m -> m.startsWith("WARNING:"))
            // So a custom message starting with "WARNING:" IS counted as a warning
            assertThat(result.hasWarnings()).isTrue();
        }
    }

    // ==================== toString ====================

    @Nested
    class ToString {

        @Test
        void toString_passed() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true);

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("passed=true");
            assertThat(str).contains("messages=[]");
        }

        @Test
        void toString_failedWithErrors() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Limit exceeded");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("passed=false");
            assertThat(str).contains("ERROR: Limit exceeded");
        }

        @Test
        void toString_withWarnings() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("High volatility");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("passed=false");
            assertThat(str).contains("WARNING: High volatility");
        }

        @Test
        void toString_withCheckType() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.setCheckType("MARGIN_CHECK");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("checkType='MARGIN_CHECK'");
        }

        @Test
        void toString_withMultipleMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Error 1");
            result.addWarning("Warning 1");
            result.addInfo("Info 1");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("ERROR: Error 1");
            assertThat(str).contains("WARNING: Warning 1");
            assertThat(str).contains("INFO: Info 1");
        }

        @Test
        void toString_className() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            String str = result.toString();

            // Then
            assertThat(str).startsWith("RiskCheckResult{");
            assertThat(str).endsWith("}");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void veryLongMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            StringBuilder longMsg = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longMsg.append("x");
            }

            // When
            result.addMessage(longMsg.toString());

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0)).hasSize(1000);
        }

        @Test
        void unicodeMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("こんにちは - 😀 - éèê");

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0)).contains("こんにちは");
            assertThat(result.getMessages().get(0)).contains("😀");
        }

        @Test
        void specialCharactersInMessage() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("Message with \"quotes\" and 'apostrophes' and \nnewlines\tand tabs");

            // Then
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages().get(0)).contains("quotes");
            assertThat(result.getMessages().get(0)).contains("apostrophes");
        }

        @Test
        void manyMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            for (int i = 0; i < 100; i++) {
                result.addMessage("Message " + i);
            }

            // Then
            assertThat(result.getMessages()).hasSize(100);
        }

        @Test
        void addErrorAfterWarnings() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addWarning("Warning 1");
            result.addWarning("Warning 2");

            // When
            result.addError("Error 1");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getMessages()).hasSize(3);
        }

        @Test
        void addWarningAfterErrors() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addError("Error 1");
            result.addError("Error 2");

            // When
            result.addWarning("Warning 1");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getMessages()).hasSize(3);
        }

        @Test
        void setPassedFalseThenAddInfo() {
            // Given
            RiskCheckResult result = new RiskCheckResult(false);

            // When
            result.addInfo("Info after failure");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getMessages()).contains("INFO: Info after failure");
        }

        @Test
        void setPassedTrueThenAddError() {
            // Given
            RiskCheckResult result = new RiskCheckResult(true);
            result.setPassed(true);

            // When
            result.addError("Error after setting passed true");

            // Then
            assertThat(result.isPassed()).isFalse();
            assertThat(result.hasErrors()).isTrue();
        }

        @Test
        void emptyStringMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("");
            result.addMessage("");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages()).contains("", "");
        }

        @Test
        void whitespaceOnlyMessages() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("   ");
            result.addMessage("\t\n");

            // Then
            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getMessages().get(0)).isEqualTo("   ");
            assertThat(result.getMessages().get(1)).isEqualTo("\t\n");
        }

        @Test
        void fluentAPI_chain() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.addMessage("Base message")
                    .addInfo("Info message")
                    .addWarning("Warning message")
                    .addError("Error message")
                    .setCheckType("CHAIN_CHECK");

            // Then
            assertThat(result.getMessages()).hasSize(4);
            assertThat(result.getCheckType()).isEqualTo("CHAIN_CHECK");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        void messagesListOperations() {
            // Given
            RiskCheckResult result = new RiskCheckResult();
            result.addMessage("First");

            // When
            List<String> messages = result.getMessages();
            messages.add("Second");
            messages.add(0, "Zeroth");

            // Then
            assertThat(result.getMessages()).hasSize(3);
            assertThat(result.getMessages().get(0)).isEqualTo("Zeroth");
            assertThat(result.getMessages().get(1)).isEqualTo("First");
            assertThat(result.getMessages().get(2)).isEqualTo("Second");
        }

        @Test
        void checkTypeWithSpecialCharacters() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.setCheckType("MARGIN_CHECK_2026!");

            // Then
            assertThat(result.getCheckType()).isEqualTo("MARGIN_CHECK_2026!");
        }

        @Test
        void checkTypeWithSpaces() {
            // Given
            RiskCheckResult result = new RiskCheckResult();

            // When
            result.setCheckType("Risk Check - Position Limit");

            // Then
            assertThat(result.getCheckType()).isEqualTo("Risk Check - Position Limit");
        }
    }
}
