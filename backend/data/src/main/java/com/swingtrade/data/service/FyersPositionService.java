package com.swingtrade.data.service;

import com.tts.in.model.PositionModel;
import com.tts.in.model.PositionConversionModel;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fyers v3 position management service.
 */
public class FyersPositionService {

    private static final Logger logger = LoggerFactory.getLogger(FyersPositionService.class);
    private final FyersAuthService authService;

    public FyersPositionService(FyersAuthService authService) {
        this.authService = authService;
    }

    private FyersClass getSdk() {
        FyersClass sdk = FyersClass.getInstance();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) sdk.accessToken = token;
        return sdk;
    }

    public List<PositionModel> getPositions() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetPositions();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parsePositionList(result.Item2());
        } catch (Exception e) {
            logger.error("Get positions error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean exitAllPositions() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().ExitPositions(true);
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Exit positions error: {}", e.getMessage());
            return false;
        }
    }

    public boolean exitPosition(String orderId) {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().ExitPositions(List.of(orderId));
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Exit position error: {}", e.getMessage());
            return false;
        }
    }

    public boolean convertPosition(String symbol, int side, int convertQty,
                                    String fromProduct, String toProduct) {
        PositionConversionModel model = new PositionConversionModel();
        model.Symbol = symbol;
        model.Side = side;
        model.ConvertQty = convertQty;
        model.ConvertFrom = fromProduct;
        model.ConvertTo = toProduct;
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().PositionConversion(model);
            if (result == null) return false;
            return "success".equals(result.Item1().optString("s"));
        } catch (Exception e) {
            logger.error("Position conversion error: {}", e.getMessage());
            return false;
        }
    }

    private List<PositionModel> parsePositionList(JSONObject data) {
        List<PositionModel> positions = new ArrayList<>();
        if (data == null) return positions;
        JSONObject nodes = data.has("netPositions") ? data.optJSONObject("netPositions") : data;
        if (nodes == null) return positions;
        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;
            PositionModel model = new PositionModel();
            model.Symbol = node.optString("symbol", null);
            model.NetQty = node.optInt("netQty", 0);
            model.Side = node.optInt("side", 0);
            model.AvgPrice = node.optDouble("avgPrice", 0);
            model.NetAvg = node.optDouble("netAvg", 0);
            model.RealizedProfit = node.optDouble("realized_profit", 0);
            model.UnRealizedProfit = node.optDouble("unrealized_profit", 0);
            model.LTP = node.optDouble("ltp", 0);
            model.ProductType = node.optString("productType", null);
            model.FyToken = node.optString("fyToken", null);
            positions.add(model);
        }
        return positions;
    }
}