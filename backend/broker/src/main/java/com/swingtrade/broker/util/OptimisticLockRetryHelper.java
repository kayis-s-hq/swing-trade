package com.swingtrade.broker.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.function.Supplier;

/**
 * Utility for retrying operations that may fail due to JPA optimistic locking conflicts.
 * <p>
 * Used by broker service classes (e.g., {@code PaperTradingStateService},
 * {@code DailyLossCircuitBreaker}) when persisting state that could be
 * concurrently modified by other transactions or threads.
 * <p>
 * Retries up to 3 times with exponential backoff starting at 100ms.
 * Only retries on {@link OptimisticLockingFailureException} and
 * Hibernate's {@link org.hibernate.StaleObjectStateException}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * OptimisticLockRetryHelper.execute(
 *     () -> repository.save(entity),
 *     "PaperTradingPortfolioEntity"
 * );
 * }</pre>
 */
public final class OptimisticLockRetryHelper {

    private static final Logger logger = LoggerFactory.getLogger(OptimisticLockRetryHelper.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 100;

    private OptimisticLockRetryHelper() {
        // Utility class — no instantiation
    }

    /**
     * Execute a supplier with retry on optimistic lock exceptions.
     *
     * @param action the operation to retry
     * @param entityType the entity type name for logging
     * @param <T> the return type
     * @return the result of the supplier
     * @throws RuntimeException if all retries are exhausted (rethrows the original exception)
     */
    public static <T> T execute(Supplier<T> action, String entityType) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException | org.hibernate.StaleObjectStateException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1)); // 100, 200, 400
                    logger.warn("Optimistic lock conflict on [{}] (attempt {}/{}), retrying in {}ms: {}",
                            entityType, attempt, MAX_RETRIES, backoff, e.getMessage());
                    sleep(backoff);
                } else {
                    logger.warn("Optimistic lock conflict on [{}] after {} attempts, giving up: {}",
                            entityType, MAX_RETRIES, e.getMessage());
                }
            }
        }

        // Rethrow the original exception after retries are exhausted
        // to preserve backward compatibility with existing exception handling
        throw new RuntimeException(lastException.getMessage(), lastException);
    }

    /**
     * Execute a runnable with retry on optimistic lock exceptions.
     *
     * @param action the operation to retry
     * @param entityType the entity type name for logging
     */
    public static void execute(Runnable action, String entityType) {
        execute(() -> {
            action.run();
            return null;
        }, entityType);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}