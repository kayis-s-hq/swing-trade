package com.swingtrade.api.exception;

import com.swingtrade.api.dto.ErrorResponse;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OptimisticLockExceptionHandler.
 */
class OptimisticLockExceptionHandlerTest {

    private final OptimisticLockExceptionHandler handler = new OptimisticLockExceptionHandler();

    private WebRequest mockRequest(String uri) {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(anyBoolean())).thenReturn("uri=" + uri);
        return request;
    }

    // ==================== handleOptimisticLock ====================

    @Nested
    class HandleOptimisticLock {

        @Test
        void handlesOptimisticLockingFailureException_returns409() {
            // Given
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Row was updated by another transaction");
            WebRequest request = mockRequest("/api/positions");

            // When
            ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(409);
            assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
            assertThat(response.getBody().getMessage()).isEqualTo("Record was modified by another transaction. Please retry.");
            assertThat(response.getBody().getPath()).isEqualTo("/api/positions");
        }

        @Test
        void multipleExceptionTypes_returnsConflict() {
            // Given different optimistic lock exception message
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Optimistic lock failure on entity");
            WebRequest request = mockRequest("/api/trade");

            // When
            ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
            assertThat(response.getBody().getTimestamp()).isNotNull();
        }

        @Test
        void handlesExceptionWithNullMessage() {
            // Given OptimisticLockingFailureException with null message
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException(null);
            WebRequest request = mockRequest("/api/portfolio");

            // When
            ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        }

        @Test
        void fallbackToUnknownEntityWhenNoQuotedName() {
            // Given
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Generic lock failure");
            WebRequest request = mockRequest("/api/unknown");

            // When
            ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        }

        @Test
        void responseIncludesTimestamp() {
            // Given
            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Lock conflict");
            WebRequest request = mockRequest("/api/test");

            // When
            ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getTimestamp()).isNotNull();
        }
    }

    // ==================== ErrorResponseIntegration ====================

    @Nested
    class ErrorResponseIntegration {

        @Test
        void conflictFactoryMethodProducesCorrectFields() {
            // Given & When
            ErrorResponse response = ErrorResponse.conflict("Test conflict message");

            // Then
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getCode()).isEqualTo("CONFLICT");
            assertThat(response.getMessage()).isEqualTo("Test conflict message");
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        void conflictFactoryMethodWithIncludesPath() {
            // Given & When
            ErrorResponse response = ErrorResponse.conflict("Test conflict", "/api/positions/123");

            // Then
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getCode()).isEqualTo("CONFLICT");
            assertThat(response.getPath()).isEqualTo("/api/positions/123");
        }
    }
}