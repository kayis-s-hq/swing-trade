package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Standardized error response DTO for API errors.
 * Used by GlobalExceptionHandler to return consistent error format.
 */
public class ErrorResponse {

    private int status;
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> fieldErrors;

    // Default constructor required for Jackson
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.fieldErrors = new ArrayList<>();
    }

    // Full constructor
    public ErrorResponse(int status, String code, String message, LocalDateTime timestamp) {
        this();
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    // Constructor with path
    public ErrorResponse(int status, String code, String message, LocalDateTime timestamp, String path) {
        this(status, code, message, timestamp);
        this.path = path;
    }

    // Getters and setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    /**
     * Add a field error to the response.
     */
    public void addFieldError(String field, String message) {
        this.fieldErrors.add(new FieldError(field, message));
    }

    // Static factory methods for common error types
    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(400, "BAD_REQUEST", message, LocalDateTime.now());
    }

    public static ErrorResponse badRequest(String message, String path) {
        return new ErrorResponse(400, "BAD_REQUEST", message, LocalDateTime.now(), path);
    }

    public static ErrorResponse notFound(String message) {
        return new ErrorResponse(404, "NOT_FOUND", message, LocalDateTime.now());
    }

    public static ErrorResponse notFound(String message, String path) {
        return new ErrorResponse(404, "NOT_FOUND", message, LocalDateTime.now(), path);
    }

    public static ErrorResponse conflict(String message) {
        return new ErrorResponse(409, "CONFLICT", message, LocalDateTime.now());
    }

    public static ErrorResponse conflict(String message, String path) {
        return new ErrorResponse(409, "CONFLICT", message, LocalDateTime.now(), path);
    }

    public static ErrorResponse internalError(String message) {
        return new ErrorResponse(500, "INTERNAL_ERROR", message, LocalDateTime.now());
    }

    public static ErrorResponse internalError(String message, String path) {
        return new ErrorResponse(500, "INTERNAL_ERROR", message, LocalDateTime.now(), path);
    }

    /**
     * Field error information.
     */
    public static class FieldError {
        private String field;
        private String message;

        public FieldError() {
        }

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        // Getters and Setters
        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
