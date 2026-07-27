package com.swingtrade.data.service;

import com.tts.in.model.GTTModel;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fyers v3 GTT (Good Till Triggered) order service.
 */
public class FyersGTTService {

    private static final Logger logger = LoggerFactory.getLogger(FyersGTTService.class);
    private final FyersAuthService authService;

    public FyersGTTService(FyersAuthService authService) {
        this.authService = authService;
    }

    private FyersClass getSdk() {
        FyersClass sdk = FyersClass.getInstance();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) sdk.accessToken = token;
        return sdk;
    }

    public String placeGTTOrder(String symbol, int side, String productType,
                                 double triggerPrice, double limitPrice, int qty) {
        GTTModel model = new GTTModel();
        model.Side = side;
        model.Symbol = symbol;
        model.productType = productType;
        GTTLeg leg = new GTTLeg((int) limitPrice, (int) triggerPrice, qty);
        model.addGTTLeg("leg1", leg);
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().PlaceGTTOrder(List.of(model));
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;
            JSONObject body = result.Item2();
            return body.has("id") ? body.getString("id") : null;
        } catch (Exception e) {
            logger.error("GTT order error: {}", e.getMessage());
            return null;
        }
    }
}