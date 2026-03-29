package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard API response envelope.
 * Used for all API responses to provide consistent structure.
 *
 * @param success whether the request was successful
 * @param data the response data (null on error)
 * @param error the error message (null on success)
 * @param timestamp the request timestamp
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        LocalDateTime timestamp
) {
    /**
     * Create a successful response.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    /**
     * Create an error response.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, LocalDateTime.now());
    }

    /**
     * Create an error response with additional data.
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, data, message, LocalDateTime.now());
    }

    /**
     * Create a response with additional metadata.
     */
    public static <T> ApiResponse<T> ok(T data, Map<String, Object> metadata) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }
}
