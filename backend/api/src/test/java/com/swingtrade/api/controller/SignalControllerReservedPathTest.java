package com.swingtrade.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the reserved-path-segment guard on
 * {@code DELETE /api/signals/{symbol}} — verifies literal routes like
 * {@code /latest} aren't silently treated as a stock symbol.
 *
 * <p>{@link SignalControllerTest} is currently {@code @Disabled} (needs
 * conversion to {@code @WebMvcTest}), so this guard is tested directly
 * against the controller instance instead — the guard runs before any
 * autowired dependency is touched, so no mocks are needed.
 */
class SignalControllerReservedPathTest {

    @ParameterizedTest
    @ValueSource(strings = {"latest", "LATEST", "date-range", "scan"})
    void clearSignalForSymbol_rejectsReservedPathSegments(String reservedSegment) {
        SignalController controller = new SignalController();

        ResponseEntity<Map<String, Object>> response = controller.clearSignalForSymbol(reservedSegment);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void clearSignalForSymbol_acceptsRealLookingSymbol() {
        SignalController controller = new SignalController();

        // A real symbol falls through past the guard and reaches signalStore.findBySymbol(),
        // which NPEs here since no store is wired — proving the guard did NOT reject it.
        assertThat(catchNpeFromUnwiredStore(controller)).isTrue();
    }

    private boolean catchNpeFromUnwiredStore(SignalController controller) {
        try {
            controller.clearSignalForSymbol("RELIANCE");
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }
}
