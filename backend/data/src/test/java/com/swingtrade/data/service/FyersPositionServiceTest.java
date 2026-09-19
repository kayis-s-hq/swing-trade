package com.swingtrade.data.service;

import com.tts.in.model.FyersClass;
import com.tts.in.model.PositionModel;
import com.tts.in.utilities.Tuple;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FyersPositionServiceTest {
    @Test
    void parsesNetPositions() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getClientId()).thenReturn("client");
        when(auth.getAccessToken()).thenReturn("token");
        JSONObject node = new JSONObject().put("symbol", "NSE:TCS-EQ").put("netQty", 10)
                .put("side", 1).put("avgPrice", 100).put("netAvg", 101)
                .put("realized_profit", 12.5).put("unrealized_profit", 20.5).put("ltp", 103)
                .put("productType", "INTRADAY").put("fyToken", "FY-1");
        when(sdk.GetPositions()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("netPositions", new JSONObject().put("0", node))));

        List<PositionModel> result = new FyersPositionService(auth, () -> sdk).getPositions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().Symbol).isEqualTo("NSE:TCS-EQ");
        assertThat(result.getFirst().NetQty).isEqualTo(10);
        assertThat(result.getFirst().AvgPrice).isEqualTo(100.0);
        assertThat(result.getFirst().UnRealizedProfit).isEqualTo(20.5);
        assertThat(result.getFirst().FyToken).isEqualTo("FY-1");
    }

    @Test
    void exitsAllOrOnePositionAndConvertsProduct() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getAccessToken()).thenReturn("token");
        when(auth.getClientId()).thenReturn("client");
        when(sdk.ExitPositions(true)).thenReturn(success());
        when(sdk.ExitPositions(List.of("OID-1"))).thenReturn(success());
        when(sdk.PositionConversion(any())).thenReturn(success());
        FyersPositionService service = new FyersPositionService(auth, () -> sdk);

        assertThat(service.exitAllPositions()).isTrue();
        assertThat(service.exitPosition("OID-1")).isTrue();
        assertThat(service.convertPosition("TCS", 1, 10, "INTRADAY", "CNC")).isTrue();
    }

    @Test
    void returnsSafeDefaultsForProviderFailuresAndMalformedPayloads() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(sdk.GetPositions()).thenReturn(null);
        when(sdk.ExitPositions(true)).thenThrow(new IllegalStateException("offline"));
        when(sdk.ExitPositions(List.of("OID"))).thenReturn(new Tuple<>(new JSONObject().put("s", "error"), new JSONObject()));
        when(sdk.PositionConversion(any())).thenReturn(null);
        FyersPositionService service = new FyersPositionService(auth, () -> sdk);
        assertThat(service.getPositions()).isEmpty();
        assertThat(service.exitAllPositions()).isFalse();
        assertThat(service.exitPosition("OID")).isFalse();
        assertThat(service.convertPosition("TCS", 1, 1, "CNC", "INTRADAY")).isFalse();

        when(sdk.GetPositions()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("netPositions", JSONObject.NULL)));
        assertThat(service.getPositions()).isEmpty();
    }

    private static Tuple<JSONObject, JSONObject> success() {
        return new Tuple<>(new JSONObject().put("s", "success"), new JSONObject());
    }
}
