package com.swingtrade.api.exception;

import com.swingtrade.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for JPA optimistic locking conflicts.
 * Catches Hibernate's {@link StaleObjectStateException} (extends {@link org.hibernate.orm.hibernate.core.OptimisticLockException})
 * and Spring's {@link OptimisticLockingFailureException}, returning HTTP 409 Conflict.
 * <p>
 * This applies to all REST endpoints managed by {@code @RestControllerAdvice}.
 * For non-REST contexts (internal service calls), use {@link com.swingtrade.broker.util.OptimisticLockRetryHelper}.
 */
@RestControllerAdvice
public class OptimisticLockExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(OptimisticLockExceptionHandler.class);

    /**
     * Handle JPA optimistic locking conflicts.
     * Returns 409 CONFLICT with a message indicating the record was modified by another transaction.
     * <p>
     * Common causes:
     * <ul>
     *   <li>Two concurrent transactions updating the same entity</li>
     *   <li>Stale entity state due to missing @Version annotation or concurrent writes</li>
     * </ul>
     *
     * @param ex the optimistic locking exception
     * @param request the current web request
     * @return HTTP 409 Conflict with structured error response
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex, WebRequest request) {

        String entityType = extractEntityType(ex);

        logger.warn("Optimistic lock conflict on entity [{}]: {}",
                entityType, ex.getMessage());

        ErrorResponse response = ErrorResponse.conflict(
                "Record was modified by another transaction. Please retry.",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Extract entity type from the exception message when available.
     * Falls back to "unknown" if the entity type cannot be determined.
     */
    private String extractEntityType(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "unknown";
        }
        // Hibernate StaleObjectStateException includes entity name in message:
        // "org.hibernate.StaleObjectStateException: Row was updated or deleted by another transaction"
        // or "Stale object: 'com.swingtrade.data.entity.PositionEntity'"
        int entityStart = message.indexOf("'");
        if (entityStart != -1) {
            int entityEnd = message.indexOf("'", entityStart + 1);
            if (entityEnd != -1) {
                String entityName = message.substring(entityStart + 1, entityEnd);
                // Return simple name (last segment after dot)
                int lastDot = entityName.lastIndexOf('.');
                return lastDot != -1 ? entityName.substring(lastDot + 1) : entityName;
            }
        }
        return "unknown";
    }
}