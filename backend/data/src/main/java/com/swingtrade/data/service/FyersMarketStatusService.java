package com.swingtrade.data.service;

import com.tts.in.model.MarketStatusModel;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fyers v3 market status service.
 */
public class FyersMarketStatusService {

    private static final Logger logger = LoggerFactory.getLogger(FyersMarketStatusService.class);
    private final FyersAuthService authService;
    private final Supplier<FyersClass> sdkProvider;

    public FyersMarketStatusService(FyersAuthService authService) {
        this(authService, FyersClass::getInstance);
    }

    FyersMarketStatusService(FyersAuthService authService, Supplier<FyersClass> sdkProvider) {
        this.authService = authService;
        this.sdkProvider = sdkProvider;
    }

    private FyersClass getSdk() {
        FyersClass sdk = sdkProvider.get();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) sdk.accessToken = token;
        return sdk;
    }

    public List<MarketStatusModel> getMarketStatus() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetMarketStatus();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseMarketStatusList(result.Item2());
        } catch (Exception e) {
            logger.error("Get market status error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MarketStatusModel> parseMarketStatusList(JSONObject data) {
        List<MarketStatusModel> statuses = new ArrayList<>();
        if (data == null) return statuses;
        JSONObject nodes = data.has("marketStatus") ? data.optJSONObject("marketStatus") : data;
        if (nodes == null) return statuses;
        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;
            MarketStatusModel model = new MarketStatusModel();
            model.Exchange = node.optInt("exchange", 0);
            model.MarketType = node.optString("market_type", null);
            model.Segment = node.optInt("segment", 0);
            model.Status = node.optString("status", null);
            statuses.add(model);
        }
        return statuses;
    }
}
