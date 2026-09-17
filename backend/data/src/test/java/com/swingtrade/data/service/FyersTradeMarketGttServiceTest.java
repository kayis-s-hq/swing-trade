package com.swingtrade.data.service;

import com.tts.in.model.FyersClass;
import com.tts.in.model.MarketStatusModel;
import com.tts.in.model.TradeBookModel;
import com.tts.in.utilities.Tuple;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FyersTradeMarketGttServiceTest {
    @Test
    void parsesTradeBookAndMarketStatus() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        JSONObject trade = new JSONObject().put("symbol", "NSE:TCS-EQ").put("side", 1)
                .put("tradedQty", 10).put("tradePrice", 100).put("tradeValue", 1000)
                .put("orderNumber", "OID").put("fyToken", "FY").put("productType", "CNC")
                .put("orderTag", "swing");
        when(sdk.GetTradeBook()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("tradeBook", new JSONObject().put("0", trade))));
        JSONObject status = new JSONObject().put("exchange", 10).put("market_type", " equity")
                .put("segment", 10).put("status", "OPEN");
        when(sdk.GetMarketStatus()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("marketStatus", new JSONObject().put("0", status))));

        List<TradeBookModel> trades = new FyersTradeBookService(auth, () -> sdk).getTradeBook();
        List<MarketStatusModel> statuses = new FyersMarketStatusService(auth, () -> sdk).getMarketStatus();
        assertThat(trades).hasSize(1);
        assertThat(trades.getFirst().Symbol).isEqualTo("NSE:TCS-EQ");
        assertThat(trades.getFirst().TradedQty).isEqualTo(10);
        assertThat(trades.getFirst().TradePrice).isEqualTo(100.0);
        assertThat(statuses).hasSize(1);
        assertThat(statuses.getFirst().Exchange).isEqualTo(10);
        assertThat(statuses.getFirst().Status).isEqualTo("OPEN");
    }

    @Test
    void placesGttWithTriggerLegAndReturnsId() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(sdk.PlaceGTTOrder(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<com.tts.in.model.GTTModel> models = invocation.getArgument(0, List.class);
            assertThat(models).hasSize(1);
            assertThat(models.getFirst().Symbol).isEqualTo("NSE:TCS-EQ");
            assertThat(models.getFirst().OrderInfo.get("leg1").TriggerPrice).isEqualTo(95);
            assertThat(models.getFirst().OrderInfo.get("leg1").Price).isEqualTo(100);
            return new Tuple<>(new JSONObject().put("s", "success"), new JSONObject().put("id", "GTT-1"));
        });

        assertThat(new FyersGTTService(auth, () -> sdk)
                .placeGTTOrder("NSE:TCS-EQ", 1, "CNC", 95.9, 100.9, 10)).isEqualTo("GTT-1");
    }

    @Test
    void tradeMarketAndGttFailuresReturnSafeDefaults() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(sdk.GetTradeBook()).thenReturn(null);
        when(sdk.GetMarketStatus()).thenThrow(new IllegalStateException("offline"));
        when(sdk.PlaceGTTOrder(any())).thenReturn(new Tuple<>(new JSONObject().put("s", "error"), new JSONObject()));
        assertThat(new FyersTradeBookService(auth, () -> sdk).getTradeBook()).isEmpty();
        assertThat(new FyersMarketStatusService(auth, () -> sdk).getMarketStatus()).isEmpty();
        assertThat(new FyersGTTService(auth, () -> sdk)
                .placeGTTOrder("TCS", 1, "CNC", 95, 100, 1)).isNull();
    }
}
