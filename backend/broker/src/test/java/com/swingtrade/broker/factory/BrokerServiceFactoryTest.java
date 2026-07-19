package com.swingtrade.broker.factory;

import com.swingtrade.broker.config.BrokerMode;
import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.kite.BrokerClient;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.risk.KillSwitchService;
import com.swingtrade.broker.risk.RiskControls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BrokerServiceFactory.
 */
@ExtendWith(MockitoExtension.class)
class BrokerServiceFactoryTest {

    @Mock
    private BrokerClient mockClient;

    @Mock
    private ObjectProvider<BrokerClient> mockClientProvider;

    @Mock
    private RiskControls mockRisk;

    @Mock
    private PaperTradingEngine mockPaperEngine;

    @Mock
    private OrderManager mockOrderManager;

    @Mock
    private KillSwitchService mockKillSwitch;

    private BrokerServiceFactory createFactory(String mode) {
        BrokerProperties props = new BrokerProperties();
        props.setMode(mode);
        BrokerServiceFactory factory = new BrokerServiceFactory(mockPaperEngine, mockOrderManager, mockClientProvider, mockRisk, mockKillSwitch, props);
        factory.initialize();
        return factory;
    }

    @Test
    void testBrokerMode_Paper() {
        // When
        BrokerServiceFactory factory = createFactory("paper");

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.PAPER);
        assertThat(factory.getService()).isNotNull();
    }

    @Test
    void testBrokerMode_DryRun() {
        // When
        BrokerServiceFactory factory = createFactory("dry_run");

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.DRY_RUN);
        assertThat(factory.getService()).isNotNull();
    }

    @Test
    void testSwitchToLive_WithoutConfig_throws() {
        // Given
        when(mockClient.isConfigured()).thenReturn(false);
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerServiceFactory factory = createFactory("paper");

        // When/Then — switching to live without configured broker throws
        assertThatThrownBy(() -> factory.switchMode(BrokerMode.LIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Broker API key not configured");
    }

    @Test
    void testSwitchMode() {
        // Given
        when(mockClient.isConfigured()).thenReturn(true);
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerServiceFactory factory = createFactory("paper");

        // When
        factory.switchMode(BrokerMode.LIVE);

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.LIVE);
    }

    @Test
    void testSwitchMode_String() {
        // Given
        when(mockClient.isConfigured()).thenReturn(true);
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerServiceFactory factory = createFactory("paper");

        // When
        factory.switchMode("live");

        // Then
        assertThat(factory.getMode()).isEqualTo(BrokerMode.LIVE);
    }

    @Test
    void testAllowsExecution_Paper() {
        // When
        BrokerServiceFactory factory = createFactory("paper");

        // When
        boolean allows = factory.allowsExecution();

        // Then
        assertThat(allows).isFalse();
    }

    @Test
    void testAllowsExecution_Live() {
        // Given
        when(mockClient.isConfigured()).thenReturn(true);
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerServiceFactory factory = createFactory("paper");
        factory.switchMode(BrokerMode.LIVE);

        // When
        boolean allows = factory.allowsExecution();

        // Then
        assertThat(allows).isTrue();
    }

    @Test
    void testIsSafeMode_Paper() {
        // When
        BrokerServiceFactory factory = createFactory("paper");

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isTrue();
    }

    @Test
    void testIsSafeMode_DryRun() {
        // When
        BrokerServiceFactory factory = createFactory("dry_run");

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isTrue();
    }

    @Test
    void testIsSafeMode_Live() {
        // Given
        when(mockClient.isConfigured()).thenReturn(true);
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerServiceFactory factory = createFactory("paper");
        factory.switchMode(BrokerMode.LIVE);

        // When
        boolean safe = factory.isSafeMode();

        // Then
        assertThat(safe).isFalse();
    }

    @Test
    void testGetModeString() {
        // Given
        when(mockClientProvider.getIfAvailable()).thenReturn(mockClient);
        BrokerProperties props = new BrokerProperties(); // default "paper" to avoid live service in constructor
        BrokerServiceFactory factory = new BrokerServiceFactory(mockPaperEngine, mockOrderManager, mockClientProvider, mockRisk, mockKillSwitch, props);
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
