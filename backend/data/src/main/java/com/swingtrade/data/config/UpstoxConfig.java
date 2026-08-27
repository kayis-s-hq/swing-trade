package com.swingtrade.data.config;

// Upstox integration is unconfigured — the whole implementation is commented out
// (kept in place, not deleted, so it can be restored when Upstox is configured again).
//
// import org.springframework.boot.context.properties.ConfigurationProperties;
// import org.springframework.stereotype.Component;
//
// @Component
// @ConfigurationProperties(prefix = "upstox")
// public class UpstoxConfig {
//
//     private Api api = new Api();
//     private String clientId;
//     private String clientSecret;
//     private String redirectUri;
//     private String accessToken;
//     private String tokenStorePath = "data/upstox-tokens.json";
//     private int maxRetries = 3;
//     private long retryDelayMillis = 1000;
//
//     public Api getApi() { return api; }
//     public void setApi(Api api) { this.api = api; }
//
//     public String getClientId() { return clientId; }
//     public void setClientId(String clientId) { this.clientId = clientId; }
//
//     public String getClientSecret() { return clientSecret; }
//     public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
//
//     public String getRedirectUri() { return redirectUri; }
//     public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
//
//     public String getAccessToken() { return accessToken; }
//     public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
//
//     public String getTokenStorePath() { return tokenStorePath; }
//     public void setTokenStorePath(String tokenStorePath) { this.tokenStorePath = tokenStorePath; }
//
//     public int getMaxRetries() { return maxRetries; }
//     public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
//
//     public long getRetryDelayMillis() { return retryDelayMillis; }
//     public void setRetryDelayMillis(long retryDelayMillis) { this.retryDelayMillis = retryDelayMillis; }
//
//     public static class Api {
//         private String baseUrl = "https://api.upstox.com";
//
//         public String getBaseUrl() { return baseUrl; }
//         public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
//     }
// }
