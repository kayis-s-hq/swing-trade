package com.swingtrade.api.config;

import com.swingtrade.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for all API controllers.
 * Uses @RestControllerAdvice to catch exceptions from any @RestController.
 * Provides consistent error response format across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors from @Valid request bodies.
     * Returns 400 BAD_REQUEST with list of field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        logger.warn("Validation error: {}", ex.getMessage());

        // Extract field errors
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + (error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining(", "));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Validation failed: " + errors,
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle resource not found exceptions.
     * Returns 404 NOT_FOUND.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex, WebRequest request) {

        logger.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "The requested resource was not found",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle general runtime exceptions.
     * Returns 500 INTERNAL_SERVER_ERROR.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        logger.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.internalServerError().body(response);
    }
}
