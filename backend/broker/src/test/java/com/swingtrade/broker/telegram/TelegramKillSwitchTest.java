package com.swingtrade.broker.telegram;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.risk.RiskControlsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Telegram kill switch functionality.
 */
@ExtendWith(MockitoExtension.class)
class TelegramKillSwitchTest {

    @Mock
    private RiskControlsService riskControlsService;

    @Mock
    private TelegramMessageFormatter messageFormatter;

    @Mock
    private RestTemplate restTemplate;

    private TelegramNotificationService telegramService;

    @BeforeEach
    void setUp() {
        reset(riskControlsService, messageFormatter, restTemplate);
        telegramService = new TelegramNotificationService(messageFormatter, riskControlsService, new BrokerProperties());
        // Inject mock restTemplate using reflection
        try {
            java.lang.reflect.Field field = TelegramNotificationService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(telegramService, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock RestTemplate", e);
        }
    }

    @Test
    void testKillSwitchInitiallyInactive() {
        // Then
        assertThat(telegramService.killSwitchEnabled()).isFalse();
        assertThat(telegramService.getKillSwitchActivatedTime()).isNull();
        assertThat(telegramService.getKillSwitchReason()).isNull();
    }

    @Test
    void testHandleStopCommand() {
        // Given
        String chatId = "123456789";

        // When
        telegramService.processCommand("/stop", chatId, "");

        // Then
        assertThat(telegramService.killSwitchEnabled()).isTrue();
        assertThat(telegramService.getKillSwitchActivatedTime()).isNotNull();
        assertThat(telegramService.getKillSwitchReason()).isNotNull();

        // Verify risk controls were updated
        verify(riskControlsService).setKillSwitchActive(true);
    }

    @Test
    void testHandleResumeCommand() {
        // Given
        String chatId = "123456789";

        // First activate kill switch
        telegramService.processCommand("/stop", chatId, "");

        // When
        telegramService.processCommand("/resume", chatId, "");

        // Then
        assertThat(telegramService.killSwitchEnabled()).isFalse();
        assertThat(telegramService.getKillSwitchActivatedTime()).isNull();
        assertThat(telegramService.getKillSwitchReason()).isNull();

        // Verify risk controls were updated
        verify(riskControlsService).setKillSwitchActive(false);
    }

    @Test
    void testHandleStopCommand_AlreadyActive() {
        // Given
        String chatId = "123456789";

        // First activate kill switch
        telegramService.processCommand("/stop", chatId, "");

        // When - try to activate again
        telegramService.processCommand("/stop", chatId, "");

        // Then
        assertThat(telegramService.killSwitchEnabled()).isTrue();
        // Should not call setKillSwitchActive again
        verify(riskControlsService, times(1)).setKillSwitchActive(true);
    }

    @Test
    void testHandleResumeCommand_AlreadyInactive() {
        // Given
        String chatId = "123456789";

        // When - try to resume without activating
        telegramService.processCommand("/resume", chatId, "");

        // Then
        assertThat(telegramService.killSwitchEnabled()).isFalse();
        // Should not call setKillSwitchActive
        verify(riskControlsService, never()).setKillSwitchActive(anyBoolean());
    }

    @Test
    void testHandleStatusCommand() {
        // Given
        String chatId = "123456789";
        when(riskControlsService.getCurrentPositionCount()).thenReturn(3);
        when(riskControlsService.getRemainingPositionCapacity()).thenReturn(2);
        when(riskControlsService.getCurrentDailyPnL()).thenReturn(new java.math.BigDecimal("-5000"));
        when(riskControlsService.getDailyLossPercent()).thenReturn(new java.math.BigDecimal("-0.5"));

        // When
        telegramService.processCommand("/status", chatId, "");

        // Then - should not throw exception
        // The message is sent to Telegram (mocked)
    }

    @Test
    void testHandleHelpCommand() {
        // Given
        String chatId = "123456789";

        // When
        telegramService.processCommand("/help", chatId, "");

        // Then - should not throw exception
    }

    @Test
    void testProcessUnknownCommand() {
        // Given
        String chatId = "123456789";

        // When
        boolean handled = telegramService.processCommand("/unknown", chatId, "");

        // Then
        assertThat(handled).isFalse();
    }

    @Test
    void testProcessNullCommand() {
        // When
        boolean handled = telegramService.processCommand(null, "123", "");

        // Then
        assertThat(handled).isFalse();
    }

    @Test
    void testProcessEmptyCommand() {
        // When
        boolean handled = telegramService.processCommand("", "123", "");

        // Then
        assertThat(handled).isFalse();
    }
}
