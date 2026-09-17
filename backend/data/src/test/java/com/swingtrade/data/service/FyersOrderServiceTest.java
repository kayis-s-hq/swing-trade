package com.swingtrade.data.service;

import com.tts.in.model.FyersClass;
import com.tts.in.model.OrderModel;
import com.tts.in.utilities.Tuple;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FyersOrderServiceTest {
    @Test
    void placesOrderWithOptionalRiskFieldsAndReturnsProviderId() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getAccessToken()).thenReturn("token");
        when(auth.getClientId()).thenReturn("client");
        when(sdk.PlaceOrder(any())).thenAnswer(invocation -> {
            var model = invocation.getArgument(0, com.tts.in.model.PlaceOrderModel.class);
            assertThat(model.Symbol).isEqualTo("NSE:TCS-EQ");
            assertThat(model.Qty).isEqualTo(10);
            assertThat(model.StopLoss).isEqualTo(95.0);
            assertThat(model.TakeProfit).isEqualTo(110.0);
            assertThat(model.OrderTag).isEqualTo("swing");
            return tuple("success", new JSONObject().put("id", "OID-1"));
        });

        String id = service(auth, sdk).placeOrder("NSE:TCS-EQ", 10, 1, 2, 100.0,
                "INTRADAY", 95.0, 110.0, "swing");

        assertThat(id).isEqualTo("OID-1");
        assertThat(sdk.clientId).isEqualTo("client");
        assertThat(sdk.accessToken).isEqualTo("token");
    }

    @Test
    void rejectsMissingTokenAndHandlesOrderFailures() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getAccessToken()).thenReturn(null);
        FyersOrderService service = service(auth, sdk);
        assertThat(service.placeOrder("TCS", 1, 1, 2, 0, "CNC", null, null, null)).isNull();
        verify(sdk, org.mockito.Mockito.never()).PlaceOrder(any());

        when(auth.getAccessToken()).thenReturn("token");
        when(sdk.PlaceOrder(any())).thenReturn(null);
        assertThat(service.placeOrder("TCS", 1, 1, 2, 0, "CNC", null, null, null)).isNull();
        when(sdk.PlaceOrder(any())).thenReturn(tuple("error", new JSONObject()));
        assertThat(service.placeOrder("TCS", 1, 1, 2, 0, "CNC", null, null, null)).isNull();
    }

    @Test
    void cancelsOrdersAndReturnsFalseForProviderErrors() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getAccessToken()).thenReturn("token");
        when(auth.getClientId()).thenReturn("client");
        when(sdk.CancelOrder("OID-1")).thenReturn(tuple("success", new JSONObject()));
        FyersOrderService service = service(auth, sdk);
        assertThat(service.cancelOrder("OID-1")).isTrue();
        when(sdk.CancelOrder("OID-2")).thenReturn(tuple("error", new JSONObject()));
        assertThat(service.cancelOrder("OID-2")).isFalse();
        when(sdk.CancelOrder("OID-3")).thenReturn(null);
        assertThat(service.cancelOrder("OID-3")).isFalse();
    }

    @Test
    void parsesAllOrdersAndHistoryAndSkipsMalformedNodes() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getAccessToken()).thenReturn("token");
        when(auth.getClientId()).thenReturn("client");
        JSONObject orders = new JSONObject()
                .put("orderBook", new JSONObject()
                        .put("0", new JSONObject().put("id", "OID-1").put("symbol", "TCS")
                                .put("qty", 10).put("filledQty", 10).put("status", 2))
                        .put("1", JSONObject.NULL));
        when(sdk.GetAllOrders()).thenReturn(tuple("success", orders));
        when(sdk.GetOrderHistory("TCS")).thenReturn(tuple("success", orders));

        FyersOrderService service = service(auth, sdk);
        List<OrderModel> all = service.getAllOrders();
        List<OrderModel> history = service.getOrderHistory("TCS");
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().OrderId).isEqualTo("OID-1");
        assertThat(all.getFirst().Qty).isEqualTo(10);
        assertThat(history).hasSize(1);

        when(sdk.GetAllOrders()).thenReturn(tuple("error", new JSONObject()));
        when(sdk.GetOrderHistory("TCS")).thenReturn(null);
        assertThat(service.getAllOrders()).isEmpty();
        assertThat(service.getOrderHistory("TCS")).isEmpty();
    }

    private static FyersOrderService service(FyersAuthService auth, FyersClass sdk) {
        return new FyersOrderService(auth, () -> sdk);
    }

    private static Tuple<JSONObject, JSONObject> tuple(String status, JSONObject body) {
        return new Tuple<>(new JSONObject().put("s", status), body);
    }
}
