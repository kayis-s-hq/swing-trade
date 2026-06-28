package com.swingtrade.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorResponse DTO.
 */
class ErrorResponseTest {

    private ObjectMapper objectMapper;
    private ErrorResponse errorResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        errorResponse = new ErrorResponse();
    }

    @Test
    void testDefaultConstructorSetsTimestampAndFieldErrors() {
        assertNotNull(errorResponse.getTimestamp());
        assertNotNull(errorResponse.getFieldErrors());
        assertTrue(errorResponse.getFieldErrors().isEmpty());
    }

    @Test
    void testFullConstructor() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 29, 10, 0, 0);
        ErrorResponse response = new ErrorResponse(400, "BAD_REQUEST", "Test error", timestamp);

        assertEquals(400, response.getStatus());
        assertEquals("BAD_REQUEST", response.getCode());
        assertEquals("Test error", response.getMessage());
        // Timestamp is set in the constructor, verify it matches
        assertEquals(timestamp.getYear(), response.getTimestamp().getYear());
        assertEquals(timestamp.getMonthValue(), response.getTimestamp().getMonthValue());
        assertEquals(timestamp.getDayOfMonth(), response.getTimestamp().getDayOfMonth());
        assertEquals(timestamp.getHour(), response.getTimestamp().getHour());
        assertEquals(timestamp.getMinute(), response.getTimestamp().getMinute());
    }

    @Test
    void testFullConstructorWithPath() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 29, 12, 0, 0);
        ErrorResponse response = new ErrorResponse(404, "NOT_FOUND", "Resource not found", timestamp, "/api/stocks/123");

        assertEquals(404, response.getStatus());
        assertEquals("NOT_FOUND", response.getCode());
        assertEquals("Resource not found", response.getMessage());
        // Timestamp is set in constructor
        assertEquals(timestamp.getYear(), response.getTimestamp().getYear());
        assertEquals(timestamp.getMonthValue(), response.getTimestamp().getMonthValue());
        assertEquals(timestamp.getDayOfMonth(), response.getTimestamp().getDayOfMonth());
        assertEquals("/api/stocks/123", response.getPath());
    }

    @Test
    void testSettersAndGetters() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 3, 29, 12, 0, 0);
        errorResponse.setTimestamp(timestamp);
        errorResponse.setStatus(500);
        errorResponse.setCode("INTERNAL_ERROR");
        errorResponse.setMessage("Internal server error");
        errorResponse.setPath("/api/internal");

        assertEquals(timestamp, errorResponse.getTimestamp());
        assertEquals(500, errorResponse.getStatus());
        assertEquals("INTERNAL_ERROR", errorResponse.getCode());
        assertEquals("Internal server error", errorResponse.getMessage());
        assertEquals("/api/internal", errorResponse.getPath());
    }

    @Test
    void testAddFieldError() {
        errorResponse.addFieldError("username", "Username is required");
        errorResponse.addFieldError("email", "Invalid email format");

        assertEquals(2, errorResponse.getFieldErrors().size());

        ErrorResponse.FieldError firstError = errorResponse.getFieldErrors().get(0);
        assertEquals("username", firstError.getField());
        assertEquals("Username is required", firstError.getMessage());
    }

    @Test
    void testJacksonSerialization() throws Exception {
        errorResponse.setStatus(404);
        errorResponse.setCode("NOT_FOUND");
        errorResponse.setMessage("Resource not found");
        errorResponse.setPath("/api/stocks/123");

        String json = objectMapper.writeValueAsString(errorResponse);

        assertTrue(json.contains("\"status\":404"));
        assertTrue(json.contains("\"code\":\"NOT_FOUND\""));
        assertTrue(json.contains("\"message\":\"Resource not found\""));
        assertTrue(json.contains("\"path\""));
    }

    @Test
    void testJacksonDeserialization() throws Exception {
        String json = "{\"status\":400,\"code\":\"BAD_REQUEST\",\"message\":\"Invalid input\",\"path\":\"/api/stocks\"}";

        ErrorResponse deserialized = objectMapper.readValue(json, ErrorResponse.class);

        assertEquals(400, deserialized.getStatus());
        assertEquals("BAD_REQUEST", deserialized.getCode());
        assertEquals("Invalid input", deserialized.getMessage());
        assertEquals("/api/stocks", deserialized.getPath());
    }

    @Test
    void testEmptyConstructorInitializesFieldErrorsList() {
        ErrorResponse response = new ErrorResponse();
        response.addFieldError("test", "Test error");
        assertEquals(1, response.getFieldErrors().size());
    }

    @Test
    void testStaticFactoryMethodBadRequest() {
        ErrorResponse response = ErrorResponse.badRequest("Validation failed");
        assertEquals(400, response.getStatus());
        assertEquals("BAD_REQUEST", response.getCode());
        assertEquals("Validation failed", response.getMessage());
    }

    @Test
    void testStaticFactoryMethodNotFound() {
        ErrorResponse response = ErrorResponse.notFound("Stock not found");
        assertEquals(404, response.getStatus());
        assertEquals("NOT_FOUND", response.getCode());
        assertEquals("Stock not found", response.getMessage());
    }

    @Test
    void testStaticFactoryMethodConflict() {
        ErrorResponse response = ErrorResponse.conflict("Position already exists");
        assertEquals(409, response.getStatus());
        assertEquals("CONFLICT", response.getCode());
        assertEquals("Position already exists", response.getMessage());
    }

    @Test
    void testStaticFactoryMethodInternalError() {
        ErrorResponse response = ErrorResponse.internalError("Database connection failed");
        assertEquals(500, response.getStatus());
        assertEquals("INTERNAL_ERROR", response.getCode());
        assertEquals("Database connection failed", response.getMessage());
    }
}
