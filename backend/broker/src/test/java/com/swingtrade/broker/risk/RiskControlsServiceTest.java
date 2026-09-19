package com.swingtrade.broker.risk;

import com.swingtrade.broker.config.BrokerProperties;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.broker.model.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskControlsServiceTest {
    @Mock private PositionLimitChecker positionLimitChecker;
    @Mock private PositionSizeValidator positionSizeValidator;
    @Mock private DailyLossCircuitBreaker dailyLossCircuitBreaker;
    @Mock private CapitalTracker capitalTracker;
    @Mock private KillSwitchService killSwitchService;
    @Mock private PositionManager positionManager;
    @Mock private BrokerProperties props;

    private RiskControlsService service;

    @BeforeEach
    void setUp() {
        service = new RiskControlsService(positionLimitChecker, positionSizeValidator,
            dailyLossCircuitBreaker, capitalTracker, killSwitchService, positionManager, props);
        when(props.getMaxPositionSizePercentage()).thenReturn(BigDecimal.TEN);
    }

    @Test
    void rejectsNullAndInvalidOrders() {
        assertThat(service.preTradeCheck(null, BigDecimal.TEN).isPassed()).isFalse();
        assertThat(service.quickPreTradeCheck(null).getMessages()).containsExactly("Order response must not be null");

        when(dailyLossCircuitBreaker.isTradingAllowed()).thenReturn(true);
        OrderResponse invalid = order(null, BigDecimal.TEN);
        assertThat(service.preTradeCheck(invalid, BigDecimal.TEN).getMessages().get(0))
            .contains("Invalid order value");
        assertThat(service.quickPreTradeCheck(invalid).isPassed()).isFalse();
    }

    @Test
    void blocksWhenKillSwitchOrCircuitIsActive() {
        OrderResponse order = order(BigDecimal.TWO, BigDecimal.TEN);
        when(killSwitchService.isActive()).thenReturn(true);
        assertThat(service.preTradeCheck(order, null).getMessages().get(0)).contains("kill switch");
        assertThat(service.quickPreTradeCheck(order).getMessages().get(0)).contains("kill switch");

        when(killSwitchService.isActive()).thenReturn(false);
        when(dailyLossCircuitBreaker.isTradingAllowed()).thenReturn(false);
        assertThat(service.preTradeCheck(order, null).getMessages().get(0)).contains("Daily loss");
        assertThat(service.quickPreTradeCheck(order).getMessages().get(0)).contains("Daily loss");
    }

    @Test
    void runsAllChecksAndUsesMarketPriceWhenOrderPriceMissing() {
        OrderResponse order = order(BigDecimal.TWO, null);
        when(dailyLossCircuitBreaker.isTradingAllowed()).thenReturn(true);
        when(positionManager.getOpenPositions()).thenReturn(List.of());
        when(positionLimitChecker.canAddPosition(new BigDecimal("20"))).thenReturn(new RiskCheckResult());
        when(positionSizeValidator.validatePositionSize(new BigDecimal("20"))).thenReturn(new RiskCheckResult());
        when(capitalTracker.enforceLimits(List.of(), new BigDecimal("20"))).thenReturn(new RiskCheckResult());
        when(dailyLossCircuitBreaker.canPlaceTrade(new BigDecimal("20"), new BigDecimal("2.0")))
            .thenReturn(new RiskCheckResult());

        RiskCheckResult result = service.preTradeCheck(order, BigDecimal.TEN);
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getCheckType()).isEqualTo("PRE_TRADE");
        assertThat(result.getMessages()).anyMatch(message -> message.contains("All pre-trade checks passed"));
    }

    @Test
    void stopsAtEachFailedRiskCheck() {
        OrderResponse order = order(BigDecimal.TWO, BigDecimal.TEN);
        when(dailyLossCircuitBreaker.isTradingAllowed()).thenReturn(true);
        when(positionManager.getOpenPositions()).thenReturn(List.of());
        RiskCheckResult failed = new RiskCheckResult(false, "ERROR: blocked");

        when(positionLimitChecker.canAddPosition(new BigDecimal("20"))).thenReturn(failed);
        assertThat(service.preTradeCheck(order, null).isPassed()).isFalse();
        when(positionLimitChecker.canAddPosition(new BigDecimal("20"))).thenReturn(new RiskCheckResult());
        when(positionSizeValidator.validatePositionSize(new BigDecimal("20"))).thenReturn(failed);
        assertThat(service.preTradeCheck(order, null).isPassed()).isFalse();
        when(positionSizeValidator.validatePositionSize(new BigDecimal("20"))).thenReturn(new RiskCheckResult());
        when(capitalTracker.enforceLimits(List.of(), new BigDecimal("20"))).thenReturn(failed);
        assertThat(service.preTradeCheck(order, null).isPassed()).isFalse();
    }

    @Test
    void quickCheckRequiresCapacityAndDelegatesStateOperations() {
        OrderResponse order = order(BigDecimal.TWO, BigDecimal.TEN);
        when(dailyLossCircuitBreaker.isTradingAllowed()).thenReturn(true);
        when(positionLimitChecker.getRemainingPositionCapacity()).thenReturn(0);
        assertThat(service.quickPreTradeCheck(order).getMessages().get(0)).contains("Maximum concurrent");

        service.updateRiskState();
        service.setKillSwitchActive(true);
        service.setKillSwitchActive(false);
        verify(dailyLossCircuitBreaker).updateWithCurrentPositions();
        verify(killSwitchService).enableKillSwitch("Set via RiskControls");
        verify(killSwitchService).disableKillSwitch();
    }

    @Test
    void exposesRiskState() {
        when(killSwitchService.isActive()).thenReturn(true);
        when(positionLimitChecker.getCurrentPositionCount()).thenReturn(2);
        when(positionLimitChecker.getRemainingPositionCapacity()).thenReturn(3);
        when(dailyLossCircuitBreaker.getCurrentDailyPnL()).thenReturn(new BigDecimal("-12"));
        when(dailyLossCircuitBreaker.getLossPercent()).thenReturn(new BigDecimal("1.2"));

        assertThat(service.isKillSwitchActive()).isTrue();
        assertThat(service.getCurrentPositionCount()).isEqualTo(2);
        assertThat(service.getRemainingPositionCapacity()).isEqualTo(3);
        assertThat(service.getCurrentDailyPnL()).isEqualByComparingTo("-12");
        assertThat(service.getDailyLossPercent()).isEqualByComparingTo("1.2");
    }

    private static OrderResponse order(BigDecimal quantity, BigDecimal price) {
        OrderResponse order = new OrderResponse();
        order.setSymbol("NSE:TEST");
        order.setQuantity(quantity);
        order.setPrice(price);
        return order;
    }
}
