package com.swingtrade.broker.factory;

import com.swingtrade.broker.config.BrokerMode;
import com.swingtrade.broker.kite.KiteConnectClient;
import com.swingtrade.broker.risk.RiskControlsService;
import com.swingtrade.broker.service.PaperTradingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BrokerServiceFactory.
 */
@ExtendWith(MockitoExtension.class)
class BrokerServiceFactoryTest {

    @Mock
    private PaperTradingServiceImpl paperTradingService;

    @Mock
    private KiteConnectClient kiteConnectClient;

    @Mock
    private RiskControlsService riskControlsService;

    @InjectMocks
    private BrokerServiceFactory factory;

    @BeforeEach
    void setUp() {
        reset(paperTradingService, kiteConnectClient, riskControlsService);
    }

    @Test
    void testInitialize_PaperMode() {
        // Given
        factory.setModeString("paper");

        // When
        factory.initialize();

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.PAPER);
        assertThat(factory.getService()).isNotNull();
    }

    @Test
    void testInitialize_DryRunMode() {
        // Given
        factory.setModeString("dry_run");

        // When
        factory.initialize();

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.DRY_RUN);
        assertThat(factory.getService()).isNotNull();
    }

    @Test
    void testInitialize_LiveMode_WithoutConfig() {
        // Given
        factory.setModeString("live");
        when(kiteConnectClient.isConfigured()).thenReturn(false);

        // When/Then
        assertThatThrownBy(factory::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kite Connect API key not configured");
    }

    @Test
    void testSwitchMode() {
        // Given
        factory.setModeString("paper");
        factory.initialize();

        // When
        factory.switchMode(BrokerMode.LIVE);

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.LIVE);
    }

    @Test
    void testSwitchMode_String() {
        // Given
        factory.setModeString("paper");
        factory.initialize();

        // When
        factory.switchMode("live");

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.LIVE);
    }

    @Test
    void testAllowsExecution_Paper() {
        // Given
        factory.setModeString("paper");
        factory.initialize();

        // When
        boolean allows = factory.allowsExecution();

        // Then
        assertThat(allows).isFalse();
    }

    @Test
    void testAllowsExecution_Live() {
        // Given
        factory.setModeString("live");
        factory.initialize();

        // When
        boolean allows = factory.allowsExecution();

        // Then
        assertThat(allows).isTrue();
    }

    @Test
    void testIsSafeMode_Paper() {
        // Given
        factory.setModeString("paper");
        factory.initialize();

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isTrue();
    }

    @Test
    void testIsSafeMode_DryRun() {
        // Given
        factory.setModeString("dry_run");
        factory.initialize();

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isTrue();
    }

    @Test
    void testIsSafeMode_Live() {
        // Given
        factory.setModeString("live");
        factory.initialize();

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isFalse();
    }

    @Test
    void testGetModeString() {
        // Given
        factory.setModeString("live");

        // When
        String mode = factory.getModeString();

        // Then
        assertThat(mode).isEqualTo("live");
    }

    @Test
    void testFromString_Paper() {
        // When
        BrokerMode mode = BrokerMode.fromString("paper");

        // Then
        assertThat(mode).isEqualTo(BrokerMode.PAPER);
    }

    @Test
    void testFromString_Live() {
        // When
        BrokerMode mode = BrokerMode.fromString("live");

        // Then
        assertThat(mode).isEqualTo(BrokerMode.LIVE);
    }

    @Test
    void testFromString_DryRun() {
        // When
        BrokerMode mode = BrokerMode.fromString("dry_run");

        // Then
        assertThat(mode).isEqualTo(BrokerMode.DRY_RUN);
    }

    @Test
    void testFromString_Null() {
        // When
        BrokerMode mode = BrokerMode.fromString(null);

        // Then
        assertThat(mode).isEqualTo(BrokerMode.PAPER);
    }

    @Test
    void testFromString_Invalid() {
        // When/Then
        assertThatThrownBy(() -> BrokerMode.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown broker mode");
    }

    @Test
    void testGetDescription() {
        // Then
        assertThat(BrokerMode.PAPER.getDescription()).contains("Paper");
        assertThat(BrokerMode.LIVE.getDescription()).contains("Live");
        assertThat(BrokerMode.DRY_RUN.getDescription()).contains("Dry-Run");
    }
}
