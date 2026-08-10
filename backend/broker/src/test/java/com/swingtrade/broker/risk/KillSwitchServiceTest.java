package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.risk.KillSwitchService.KillSwitchState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for KillSwitchService covering enable/disable, state queries,
 * toggle, state persistence, config integration, and edge cases.
 */
class KillSwitchServiceTest {

    private BrokerProperties props;
    private KillSwitchService service;

    @BeforeEach
    void setUp() {
        props = new BrokerProperties();
        props.setKillSwitchEnabled(true);
        props.setKillSwitchActive(false);
        service = new KillSwitchService(props);
    }

    // ==================== Enable / Disable ====================

    @Nested
    class EnableDisable {

        @Test
        void enableWithReason() {
            // Given: Kill switch is initially disabled
            assertThat(service.isActive()).isFalse();

            // When
            service.enableKillSwitch("Market anomaly detected");

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isEqualTo("Market anomaly detected");
            assertThat(service.getEnabledAt()).isNotNull();
        }

        @Test
        void enableWithoutReason() {
            // Given: Kill switch is initially disabled
            assertThat(service.isActive()).isFalse();

            // When
            service.enableKillSwitch();

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isNull();
            assertThat(service.getEnabledAt()).isNotNull();
        }

        @Test
        void enableNullReason() {
            // Given: Kill switch is initially disabled
            assertThat(service.isActive()).isFalse();

            // When
            service.enableKillSwitch(null);

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isNull();
        }

        @Test
        void disable() {
            // Given: Kill switch is enabled
            service.enableKillSwitch("Test reason");
            assertThat(service.isActive()).isTrue();

            // When
            service.disableKillSwitch();

            // Then
            assertThat(service.isActive()).isFalse();
            assertThat(service.getReason()).isNull();
            assertThat(service.getEnabledAt()).isNull();
        }

        @Test
        void isActiveInitiallyFalse() {
            // When
            boolean active = service.isActive();

            // Then
            assertThat(active).isFalse();
        }

        @Test
        void isActiveAfterEnable() {
            // When
            service.enableKillSwitch("Test");

            // Then
            assertThat(service.isActive()).isTrue();
        }

        @Test
        void isActiveAfterDisable() {
            // When
            service.enableKillSwitch("Test");
            service.disableKillSwitch();

            // Then
            assertThat(service.isActive()).isFalse();
        }
    }

    // ==================== Get Kill Switch State ====================

    @Nested
    class GetKillSwitchState {

        @Test
        void activeStateWithTimestamps() {
            // Given: Kill switch enabled
            service.enableKillSwitch("System failure");

            // When
            KillSwitchState state = service.getKillSwitchState();

            // Then
            assertThat(state.isActive()).isTrue();
            assertThat(state.getEnabledAt()).isNotNull();
            assertThat(state.getReason()).isEqualTo("System failure");
        }

        @Test
        void activeStateWithoutReason() {
            // Given: Kill switch enabled without reason
            service.enableKillSwitch();

            // When
            KillSwitchState state = service.getKillSwitchState();

            // Then
            assertThat(state.isActive()).isTrue();
            assertThat(state.getEnabledAt()).isNotNull();
            assertThat(state.getReason()).isNull();
        }

        @Test
        void disabledState() {
            // When
            KillSwitchState state = service.getKillSwitchState();

            // Then
            assertThat(state.isActive()).isFalse();
            assertThat(state.getEnabledAt()).isNull();
            assertThat(state.getReason()).isNull();
        }

        @Test
        void stateAfterEnableAndDisable() {
            // Given: Enabled then disabled
            service.enableKillSwitch("Test");
            service.disableKillSwitch();

            // When
            KillSwitchState state = service.getKillSwitchState();

            // Then
            assertThat(state.isActive()).isFalse();
            assertThat(state.getEnabledAt()).isNull();
            assertThat(state.getReason()).isNull();
        }
    }

    // ==================== Toggle ====================

    @Nested
    class Toggle {

        @Test
        void toggleOn() {
            // Given: Kill switch is disabled
            assertThat(service.isActive()).isFalse();

            // When
            service.toggle(true);

            // Then
            assertThat(service.isActive()).isTrue();
        }

        @Test
        void toggleOff() {
            // Given: Kill switch is enabled
            service.enableKillSwitch("Test");
            assertThat(service.isActive()).isTrue();

            // When
            service.toggle(false);

            // Then
            assertThat(service.isActive()).isFalse();
        }

        @Test
        void toggleFromDisabled() {
            // When
            service.toggle(true);

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isNull();
        }

        @Test
        void toggleFromEnabled() {
            // Given: Kill switch enabled
            service.enableKillSwitch("Reason");

            // When
            service.toggle(false);

            // Then
            assertThat(service.isActive()).isFalse();
            assertThat(service.getReason()).isNull();
        }
    }

    // ==================== State Persistence ====================

    @Nested
    class StatePersistence {

        @Test
        void enabledAtSetOnEnable() {
            // Given: Kill switch is disabled
            assertThat(service.getEnabledAt()).isNull();

            // When
            service.enableKillSwitch("Test");

            // Then
            assertThat(service.getEnabledAt()).isNotNull();
            assertThat(service.getEnabledAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        void enabledAtClearedOnDisable() {
            // Given: Kill switch enabled
            service.enableKillSwitch("Test");
            LocalDateTime enabledAt = service.getEnabledAt();
            assertThat(enabledAt).isNotNull();

            // When
            service.disableKillSwitch();

            // Then
            assertThat(service.getEnabledAt()).isNull();
        }

        @Test
        void reasonSetOnEnable() {
            // Given: Kill switch is disabled
            assertThat(service.getReason()).isNull();

            // When
            service.enableKillSwitch("Critical error");

            // Then
            assertThat(service.getReason()).isEqualTo("Critical error");
        }

        @Test
        void reasonClearedOnDisable() {
            // Given: Kill switch enabled
            service.enableKillSwitch("Test");
            assertThat(service.getReason()).isEqualTo("Test");

            // When
            service.disableKillSwitch();

            // Then
            assertThat(service.getReason()).isNull();
        }

        @Test
        void getEnabledAtInitiallyNull() {
            // When
            LocalDateTime enabledAt = service.getEnabledAt();

            // Then
            assertThat(enabledAt).isNull();
        }

        @Test
        void getReasonInitiallyNull() {
            // When
            String reason = service.getReason();

            // Then
            assertThat(reason).isNull();
        }

        @Test
        void enabledAtIsRecent() {
            // When
            service.enableKillSwitch("Test");

            // Then
            LocalDateTime enabledAt = service.getEnabledAt();
            assertThat(enabledAt).isNotNull();
            assertThat(enabledAt).isAfterOrEqualTo(LocalDateTime.now().minusSeconds(5));
        }
    }

    // ==================== Config Integration ====================

    @Nested
    class ConfigIntegration {

        @Test
        void isKillSwitchEnabled_true() {
            // Given: Config has kill switch enabled
            props.setKillSwitchEnabled(true);
            service = new KillSwitchService(props);

            // When
            boolean enabled = service.isKillSwitchEnabled();

            // Then
            assertThat(enabled).isTrue();
        }

        @Test
        void isKillSwitchEnabled_false() {
            // Given: Config has kill switch disabled
            props.setKillSwitchEnabled(false);
            service = new KillSwitchService(props);

            // When
            boolean enabled = service.isKillSwitchEnabled();

            // Then
            assertThat(enabled).isFalse();
        }

        @Test
        void isKillSwitchEnabled_independentOfActive() {
            // Given: Config enabled but service not yet activated
            props.setKillSwitchEnabled(true);
            props.setKillSwitchActive(false);
            service = new KillSwitchService(props);

            // When / Then
            assertThat(service.isKillSwitchEnabled()).isTrue();
            assertThat(service.isActive()).isFalse();
        }

        @Test
        void getKillSwitchState_withConfig() {
            // Given: Config enabled, service disabled
            props.setKillSwitchEnabled(true);
            props.setKillSwitchActive(false);
            service = new KillSwitchService(props);

            // When
            KillSwitchState state = service.getKillSwitchState();

            // Then
            assertThat(state.isActive()).isFalse();
            assertThat(state.getReason()).isNull();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    class EdgeCases {

        @Test
        void nullReason() {
            // When
            service.enableKillSwitch(null);

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isNull();
        }

        @Test
        void emptyReason() {
            // When
            service.enableKillSwitch("");

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isEmpty();
        }

        @Test
        void rapidToggle() {
            // Given: Kill switch disabled
            assertThat(service.isActive()).isFalse();

            // When: Rapid toggling
            service.toggle(true);
            service.toggle(false);
            service.toggle(true);
            service.toggle(false);
            service.toggle(true);

            // Then
            assertThat(service.isActive()).isTrue();
        }

        @Test
        void statePersistenceMultipleEnables() {
            // Given: Kill switch enabled, disabled, re-enabled
            service.enableKillSwitch("First");
            service.disableKillSwitch();
            service.enableKillSwitch("Second");

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isEqualTo("Second");
            assertThat(service.getEnabledAt()).isAfter(
                    LocalDateTime.now().minusSeconds(10));
        }

        @Test
        void statePersistenceMultipleDisables() {
            // Given: Kill switch enabled
            service.enableKillSwitch("Test");

            // When: Multiple disables
            service.disableKillSwitch();
            service.disableKillSwitch();
            service.disableKillSwitch();

            // Then
            assertThat(service.isActive()).isFalse();
            assertThat(service.getReason()).isNull();
            assertThat(service.getEnabledAt()).isNull();
        }

        @Test
        void killSwitchStateImmutability() {
            // Given: Kill switch enabled
            service.enableKillSwitch("Test");

            // When
            KillSwitchState state1 = service.getKillSwitchState();

            // Disable
            service.disableKillSwitch();

            // When
            KillSwitchState state2 = service.getKillSwitchState();

            // Then: Each call returns current state
            assertThat(state1.isActive()).isTrue();
            assertThat(state2.isActive()).isFalse();
        }

        @Test
        void enableDisableCycle() {
            // When: Multiple enable/disable cycles
            for (int i = 0; i < 5; i++) {
                service.enableKillSwitch("Cycle " + i);
                assertThat(service.isActive()).isTrue();
                assertThat(service.getReason()).isEqualTo("Cycle " + i);

                service.disableKillSwitch();
                assertThat(service.isActive()).isFalse();
            }
        }

        @Test
        void longReason() {
            // Given: A very long reason
            String longReason = "A".repeat(1000);

            // When
            service.enableKillSwitch(longReason);

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).hasSize(1000);
        }

        @Test
        void specialCharactersInReason() {
            // Given: Reason with special characters
            String specialReason = "Error: ñüé ☃ 🔥 <>&";

            // When
            service.enableKillSwitch(specialReason);

            // Then
            assertThat(service.isActive()).isTrue();
            assertThat(service.getReason()).isEqualTo(specialReason);
        }
    }
}
