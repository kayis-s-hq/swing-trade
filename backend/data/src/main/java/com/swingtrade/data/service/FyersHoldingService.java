package com.swingtrade.data.service;

import com.tts.in.model.HoldingModel;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fyers v3 holdings service.
 */
public class FyersHoldingService {

    private static final Logger logger = LoggerFactory.getLogger(FyersHoldingService.class);
    private final FyersAuthService authService;

    public FyersHoldingService(FyersAuthService authService) {
        this.authService = authService;
    }

    private FyersClass getSdk() {
        FyersClass sdk = FyersClass.getInstance();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) sdk.accessToken = token;
        return sdk;
    }

    public List<HoldingModel> getHoldings() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetHoldings();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseHoldingList(result.Item2());
        } catch (Exception e) {
            logger.error("Get holdings error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<HoldingModel> parseHoldingList(JSONObject data) {
        List<HoldingModel> holdings = new ArrayList<>();
        if (data == null) return holdings;
        JSONObject nodes = data.has("holdings") ? data.optJSONObject("holdings") : data;
        if (nodes == null) return holdings;
        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;
            HoldingModel model = new HoldingModel();
            model.Symbol = node.optString("symbol", null);
            model.FyToken = node.optString("fyToken", null);
            model.Qty = node.optInt("quantity", 0);
            model.CostPrice = node.optDouble("costPrice", 0);
            model.LTP = node.optDouble("ltp", 0);
            model.PL = node.optDouble("pl", 0);
            model.HoldingType = node.optString("holdingType", null);
            model.ISIN = node.optString("isin", null);
            holdings.add(model);
        }
        return holdings;
    }
}