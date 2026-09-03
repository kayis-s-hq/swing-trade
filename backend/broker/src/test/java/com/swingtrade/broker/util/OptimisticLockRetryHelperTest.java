package com.swingtrade.broker.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OptimisticLockRetryHelper.
 */
class OptimisticLockRetryHelperTest {

    // ==================== execute(Supplier) ====================

    @Nested
    class ExecuteSupplier {

        @Test
        void succeedsOnFirstAttempt_returnsResult() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get()).thenReturn("success");

            // When
            String result = (String) OptimisticLockRetryHelper.execute(supplier, "TestEntity");

            // Then
            assertThat(result).isEqualTo("success");
            verify(supplier, times(1)).get();
        }

        @Test
        void succeedsOnSecondAttempt_returnsResult() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            // First call throws optimistic lock, second succeeds
            when(supplier.get())
                    .thenThrow(new OptimisticLockingFailureException("Row updated"))
                    .thenReturn("success");

            // When
            String result = (String) OptimisticLockRetryHelper.execute(supplier, "TestEntity");

            // Then
            assertThat(result).isEqualTo("success");
            verify(supplier, times(2)).get();
        }

        @Test
        void exhaustsRetries_throwsRuntimeException() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get())
                    .thenThrow(new OptimisticLockingFailureException("Row updated"))
                    .thenThrow(new OptimisticLockingFailureException("Row updated"))
                    .thenThrow(new OptimisticLockingFailureException("Row updated"));

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(supplier, "TestEntity"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Row updated");
        }

        @Test
        void retriesOnlyOnOptimisticLockExceptions() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get())
                    .thenThrow(new RuntimeException("Other error"));

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(supplier, "TestEntity"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Other error");

            // Only one attempt — not retried for non-optimistic-lock exceptions
            verify(supplier, times(1)).get();
        }

        @Test
        void returnsNonNullResult() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get()).thenReturn(42);

            // When
            Integer result = (Integer) OptimisticLockRetryHelper.execute(supplier, "TestEntity");

            // Then
            assertThat(result).isEqualTo(42);
        }
    }

    // ==================== execute(Runnable) ====================

    @Nested
    class ExecuteRunnable {

        @Test
        void succeedsOnFirstAttempt() {
            // Given
            var runnable = mock(Runnable.class);

            // When
            OptimisticLockRetryHelper.execute(runnable, "TestEntity");

            // Then
            verify(runnable, times(1)).run();
        }

        @Test
        void succeedsOnRetry() {
            // Given
            var runnable = mock(Runnable.class);
            doThrow(new OptimisticLockingFailureException("Row updated"))
                    .doNothing()
                    .when(runnable).run();

            // When
            OptimisticLockRetryHelper.execute(runnable, "TestEntity");

            // Then
            verify(runnable, times(2)).run();
        }

        @Test
        void exhaustsRetries_throwsRuntimeException() {
            // Given
            var runnable = mock(Runnable.class);
            doThrow(new OptimisticLockingFailureException("Row updated"))
                    .doThrow(new OptimisticLockingFailureException("Row updated"))
                    .doThrow(new OptimisticLockingFailureException("Row updated"))
                    .when(runnable).run();

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(runnable, "TestEntity"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Row updated");
        }
    }

    // ==================== HibernateStaleObjectStateException ====================

    @Nested
    class HibernateStaleObjectStateException {

        @Test
        void retriesOnStaleObjectStateException() throws Exception {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get())
                    .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Stale object"))
                    .thenReturn("success");

            // When
            String result = (String) OptimisticLockRetryHelper.execute(supplier, "TestEntity");

            // Then
            assertThat(result).isEqualTo("success");
            verify(supplier, times(2)).get();
        }

        @Test
        void exhaustsRetriesOnStaleObjectStateException() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get())
                    .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Stale object"))
                    .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Stale object"))
                    .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Stale object"));

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(supplier, "TestEntity"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Stale object");
        }
    }

    // ==================== NonOptimisticLockExceptions ====================

    @Nested
    class NonOptimisticLockExceptions {

        @Test
        void doesNotRetryOnRuntimeException() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get()).thenThrow(new RuntimeException("DB connection lost"));

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(supplier, "TestEntity"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB connection lost");

            verify(supplier, times(1)).get();
        }

        @Test
        void doesNotRetryOnIllegalArgumentException() {
            // Given
            var supplier = mock(java.util.function.Supplier.class);
            when(supplier.get()).thenThrow(new IllegalArgumentException("Invalid argument"));

            // When / Then
            assertThatThrownBy(() -> OptimisticLockRetryHelper.execute(supplier, "TestEntity"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid argument");

            verify(supplier, times(1)).get();
        }
    }
}