package com.swingtrade.data.service;

import com.tts.in.model.FyersClass;
import com.tts.in.model.MarketDepthModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FyersMarketDepthServiceTest {
    @Test
    void parsesQuoteFieldsPriceBandsAndBidAskSides() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getClientId()).thenReturn("client");
        when(auth.getAccessToken()).thenReturn("token");
        JSONObject row = new JSONObject()
                .put("symbol", "NSE:TCS-EQ").put("o", 100).put("h", 110).put("l", 95).put("c", 105)
                .put("ch", 5).put("chp", 5).put("ltp", 105).put("ltq", 20).put("ltt", 1234)
                .put("v", 10000).put("atp", 103).put("tick_Size", .05).put("lower_ckt", 90)
                .put("upper_ckt", 120).put("totalbuyqty", 500).put("totalsellqty", 400)
                .put("oi", 25).put("oiflag", true)
                .put("bids", new JSONObject().put("0", new JSONObject().put("price", 104).put("volume", 10).put("ord", 2)))
                .put("ask", new JSONObject().put("0", new JSONObject().put("price", 106).put("volume", 12).put("ord", 3)));
        when(sdk.GetMarketDepth("NSE:TCS-EQ", 1)).thenReturn(
                new Tuple<>(new JSONObject().put("s", "success"), new JSONObject().put("0", row)));

        MarketDepthModel result = new FyersMarketDepthService(auth, () -> sdk).getMarketDepth("NSE:TCS-EQ");

        assertThat(result.Symbol).isEqualTo("NSE:TCS-EQ");
        assertThat(result.LTP).isEqualTo(105.0);
        assertThat(result.LowerCircuit).isEqualTo(90.0);
        assertThat(result.UpperCircuit).isEqualTo(120.0);
        assertThat(result.TotalBuyQty).isEqualTo(500);
        assertThat(result.OIFlag).isTrue();
        assertThat(result.Bids).hasSize(1);
        assertThat(result.Bids.getFirst().Price).isEqualTo(104.0);
        assertThat(result.Asks).hasSize(1);
        assertThat(result.Asks.getFirst().Ord).isEqualTo(3.0);
    }

    @Test
    void returnsNullForProviderFailureOrNullResponse() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(sdk.GetMarketDepth("TCS", 1)).thenReturn(null);
        FyersMarketDepthService service = new FyersMarketDepthService(auth, () -> sdk);
        assertThat(service.getMarketDepth("TCS")).isNull();

        when(sdk.GetMarketDepth("TCS", 1)).thenReturn(
                new Tuple<>(new JSONObject().put("s", "error"), new JSONObject()));
        assertThat(service.getMarketDepth("TCS")).isNull();
        when(sdk.GetMarketDepth("TCS", 1)).thenThrow(new IllegalStateException("offline"));
        assertThat(service.getMarketDepth("TCS")).isNull();
    }

    @Test
    void emptyDataReturnsAnEmptyDepthModelAndInvalidNumbersBecomeNull() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        JSONObject row = new JSONObject().put("symbol", "TCS").put("ltp", "not-a-number");
        when(sdk.GetMarketDepth("TCS", 1)).thenReturn(
                new Tuple<>(new JSONObject().put("s", "success"), new JSONObject().put("0", row)));
        MarketDepthModel result = new FyersMarketDepthService(auth, () -> sdk).getMarketDepth("TCS");
        assertThat(result.Symbol).isEqualTo("TCS");
        assertThat(result.LTP).isNull();
        assertThat(result.Bids).isEmpty();
        assertThat(result.Asks).isEmpty();
    }
}
