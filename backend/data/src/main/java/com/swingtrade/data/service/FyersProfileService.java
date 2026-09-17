package com.swingtrade.data.service;

import com.tts.in.model.ProfileModel;
import com.tts.in.model.FundModel;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fyers v3 profile and funds service.
 */
public class FyersProfileService {

    private static final Logger logger = LoggerFactory.getLogger(FyersProfileService.class);
    private final FyersAuthService authService;
    private final Supplier<FyersClass> sdkProvider;

    public FyersProfileService(FyersAuthService authService) {
        this(authService, FyersClass::getInstance);
    }

    FyersProfileService(FyersAuthService authService, Supplier<FyersClass> sdkProvider) {
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

    public ProfileModel getProfile() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetProfile();
            if (result == null) return null;
            if (!"success".equals(result.Item1().optString("s"))) return null;
            JSONObject body = result.Item2();
            ProfileModel model = new ProfileModel();
            model.Name = body.optString("name", null);
            model.DisplayName = body.optString("display_name", null);
            model.EmailId = body.optString("email_id", null);
            model.PAN = body.optString("PAN", null);
            model.FYId = body.optString("fy_id", null);
            model.MobileNumber = body.optString("mobile_number", null);
            return model;
        } catch (Exception e) {
            logger.error("Get profile error: {}", e.getMessage());
            return null;
        }
    }

    public List<FundModel> getFunds() {
        try {
            com.tts.in.utilities.Tuple<JSONObject, JSONObject> result = getSdk().GetFunds();
            if (result == null) return Collections.emptyList();
            if (!"success".equals(result.Item1().optString("s"))) return Collections.emptyList();
            return parseFundList(result.Item2());
        } catch (Exception e) {
            logger.error("Get funds error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FundModel> parseFundList(JSONObject data) {
        List<FundModel> funds = new ArrayList<>();
        if (data == null) return funds;
        JSONObject nodes = data.has("fund_limit") ? data.optJSONObject("fund_limit") : data;
        if (nodes == null) return funds;
        for (int i = 0; i < nodes.length(); i++) {
            String key = Integer.toString(i);
            if (!nodes.has(key)) break;
            JSONObject node = nodes.optJSONObject(key);
            if (node == null) continue;
            FundModel model = new FundModel();
            model.Title = node.optString("fund_type", null);
            model.EquityAmount = node.optDouble("equityAmount", 0);
            model.CommodityAmount = node.optDouble("commodityAmount", 0);
            funds.add(model);
        }
        return funds;
    }
}
