package com.swingtrade.data.service;

import com.tts.in.model.FundModel;
import com.tts.in.model.FyersClass;
import com.tts.in.model.HoldingModel;
import com.tts.in.model.ProfileModel;
import com.tts.in.utilities.Tuple;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FyersProfileHoldingServiceTest {
    @Test
    void parsesProfileAndFundsWithOptionalContainers() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(auth.getClientId()).thenReturn("client");
        when(auth.getAccessToken()).thenReturn("token");
        JSONObject profile = new JSONObject().put("name", "Trader").put("display_name", "Swing Trader")
                .put("email_id", "trader@example.test").put("PAN", "ABCDE1234F")
                .put("fy_id", "FY123").put("mobile_number", "9999999999");
        when(sdk.GetProfile()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"), profile));
        JSONObject fund = new JSONObject().put("fund_type", "Equity").put("equityAmount", 500000)
                .put("commodityAmount", 10000);
        when(sdk.GetFunds()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("fund_limit", new JSONObject().put("0", fund))));

        FyersProfileService service = new FyersProfileService(auth, () -> sdk);
        ProfileModel profileResult = service.getProfile();
        List<FundModel> funds = service.getFunds();

        assertThat(profileResult.Name).isEqualTo("Trader");
        assertThat(profileResult.DisplayName).isEqualTo("Swing Trader");
        assertThat(profileResult.PAN).isEqualTo("ABCDE1234F");
        assertThat(funds).hasSize(1);
        assertThat(funds.getFirst().Title).isEqualTo("Equity");
        assertThat(funds.getFirst().EquityAmount).isEqualTo(500000.0);
        assertThat(funds.getFirst().CommodityAmount).isEqualTo(10000.0);
    }

    @Test
    void parsesHoldingsAndReturnsSafeDefaults() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        JSONObject holding = new JSONObject().put("symbol", "NSE:TCS-EQ").put("fyToken", "FY-1")
                .put("quantity", 10).put("costPrice", 100).put("ltp", 105).put("pl", 50)
                .put("holdingType", " demat ").put("isin", "INE467B01029");
        when(sdk.GetHoldings()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"),
                new JSONObject().put("holdings", new JSONObject().put("0", holding))));

        List<HoldingModel> result = new FyersHoldingService(auth, () -> sdk).getHoldings();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().Symbol).isEqualTo("NSE:TCS-EQ");
        assertThat(result.getFirst().Qty).isEqualTo(10);
        assertThat(result.getFirst().LTP).isEqualTo(105.0);
        assertThat(result.getFirst().ISIN).isEqualTo("INE467B01029");

        when(sdk.GetHoldings()).thenReturn(null);
        assertThat(new FyersHoldingService(auth, () -> sdk).getHoldings()).isEmpty();
        when(sdk.GetHoldings()).thenThrow(new IllegalStateException("offline"));
        assertThat(new FyersHoldingService(auth, () -> sdk).getHoldings()).isEmpty();
    }

    @Test
    void profileAndFundFailuresAreFailClosed() {
        FyersAuthService auth = mock(FyersAuthService.class);
        FyersClass sdk = mock(FyersClass.class);
        when(sdk.GetProfile()).thenReturn(null);
        when(sdk.GetFunds()).thenReturn(new Tuple<>(new JSONObject().put("s", "error"), new JSONObject()));
        FyersProfileService service = new FyersProfileService(auth, () -> sdk);
        assertThat(service.getProfile()).isNull();
        assertThat(service.getFunds()).isEmpty();
        when(sdk.GetFunds()).thenReturn(new Tuple<>(new JSONObject().put("s", "success"), new JSONObject()));
        assertThat(service.getFunds()).isEmpty();
    }
}
