package com.swingtrade.data.service;

import com.tts.in.model.MarketDepthModel;
import com.tts.in.model.AskBid;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fyers v3 market depth service.
 */
public class FyersMarketDepthService {

    private static final Logger logger = LoggerFactory.getLogger(FyersMarketDepthService.class);
    private final FyersAuthService authService;

    public FyersMarketDepthService(FyersAuthService authService) {
        this.authService = authService;
    }

    private FyersClass getSdk() {
        FyersClass sdk = FyersClass.getInstance();
        sdk.clientId = authService.getClientId();
        String token = authService.getAccessToken();
        if (token != null) sdk.accessToken = token;
        return sdk;
    }

    public MarketDepthModel getMarketDepth(String symbol) {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetMarketDepth(symbol, 1);
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;
            return parseMarketDepth(result.Item2());
        } catch (Exception e) {
            logger.error("Get market depth error: {}", e.getMessage());
            return null;
        }
    }

    private MarketDepthModel parseMarketDepth(JSONObject data) {
        MarketDepthModel model = new MarketDepthModel();
        if (data == null) return model;
        JSONObject first = (data.length() > 0) ? data.optJSONObject(data.keySet().iterator().next()) : null;
        if (first == null) return model;
        model.Symbol = first.optString("symbol", null);
        model.Open = toDouble(first, "o");
        model.High = toDouble(first, "h");
        model.Low = toDouble(first, "l");
        model.Close = toDouble(first, "c");
        model.Change = toDouble(first, "ch");
        model.ChangePercent = toDouble(first, "chp");
        model.LTP = toDouble(first, "ltp");
        model.LTQ = first.optInt("ltq", 0);
        model.LTT = toDouble(first, "ltt");
        model.V = toDouble(first, "v");
        model.ATP = toDouble(first, "atp");
        model.TickSize = toDouble(first, "tick_Size");
        model.LowerCircuit = toDouble(first, "lower_ckt");
        model.UpperCircuit = toDouble(first, "upper_ckt");
        model.TotalBuyQty = first.optInt("totalbuyqty", 0);
        model.TotalSellQty = first.optInt("totalsellqty", 0);
        model.OI = toDouble(first, "oi");
        model.OIFlag = first.optBoolean("oiflag", false);
        parseSides(first, model);
        return model;
    }

    private void parseSides(JSONObject first, MarketDepthModel model) {
        JSONObject bidsObj = first.optJSONObject("bids");
        if (bidsObj != null) {
            for (int i = 0; i < bidsObj.length(); i++) {
                String key = Integer.toString(i);
                if (!bidsObj.has(key)) break;
                AskBid askBid = new AskBid();
                JSONObject bid = bidsObj.optJSONObject(key);
                if (bid != null) {
                    askBid.Price = bid.optDouble("price", 0);
                    askBid.Volume = bid.optDouble("volume", 0);
                    askBid.Ord = bid.optDouble("ord", 0);
                    model.Bids.add(askBid);
                }
            }
        }
        JSONObject asksObj = first.optJSONObject("ask");
        if (asksObj != null) {
            for (int i = 0; i < asksObj.length(); i++) {
                String key = Integer.toString(i);
                if (!asksObj.has(key)) break;
                AskBid askBid = new AskBid();
                JSONObject ask = asksObj.optJSONObject(key);
                if (ask != null) {
                    askBid.Price = ask.optDouble("price", 0);
                    askBid.Volume = ask.optDouble("volume", 0);
                    askBid.Ord = ask.optDouble("ord", 0);
                    model.Asks.add(askBid);
                }
            }
        }
    }

    private Double toDouble(JSONObject obj, String key) {
        if (!obj.has(key)) return null;
        try { return obj.getDouble(key); }
        catch (Exception e) { return null; }
    }
}