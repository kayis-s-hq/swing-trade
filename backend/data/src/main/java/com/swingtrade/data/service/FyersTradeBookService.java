package com.swingtrade.data.service;

import com.tts.in.model.TradeBookModel;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fyers v3 trade book service.
 */
public class FyersTradeBookService {

    private static final Logger logger = LoggerFactory.getLogger(FyersTradeBookService.class);
    private final FyersAuthService authService;
    private final Supplier<FyersClass> sdkProvider;

    public FyersTradeBookService(FyersAuthService authService) {
        this(authService, FyersClass::getInstance);
    }

    FyersTradeBookService(FyersAuthService authService, Supplier<FyersClass> sdkProvider) {
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

    public List<TradeBookModel> getTradeBook() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetTradeBook();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseTradeBook(result.Item2());
        } catch (Exception e) {
            logger.error("Get trade book error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TradeBookModel> parseTradeBook(JSONObject data) {
        List<TradeBookModel> trades = new ArrayList<>();
        if (data == null) return trades;
        JSONObject nodes = data.has("tradeBook") ? data.optJSONObject("tradeBook") : data;
        if (nodes == null) return trades;
        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;
            TradeBookModel model = new TradeBookModel();
            model.Symbol = node.optString("symbol", null);
            model.Side = node.optInt("side", 0);
            model.TradedQty = node.optInt("tradedQty", 0);
            model.TradePrice = node.optDouble("tradePrice", 0);
            model.TradeValue = node.optDouble("tradeValue", 0);
            model.OrderNumber = node.optString("orderNumber", null);
            model.FYToken = node.optString("fyToken", null);
            model.ProductType = node.optString("productType", null);
            model.OrderTag = node.optString("orderTag", null);
            trades.add(model);
        }
        return trades;
    }
}
