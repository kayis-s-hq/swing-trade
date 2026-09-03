package com.swingtrade.data.config;

// Upstox integration is unconfigured — the whole implementation is commented out
// (kept in place, not deleted, so it can be restored when Upstox is configured again).
//
// import org.junit.jupiter.api.Test;
// import static org.assertj.core.api.Assertions.assertThat;
//
// class UpstoxConfigTest {
//
//     @Test
//     void nestedApiClassBindsBaseUrl() {
//         UpstoxConfig config = new UpstoxConfig();
//         UpstoxConfig.Api api = new UpstoxConfig.Api();
//         api.setBaseUrl("https://api.upstox.com");
//         config.setApi(api);
//         assertThat(config.getApi().getBaseUrl()).isEqualTo("https://api.upstox.com");
//     }
//
//     @Test
//     void defaultBaseUrlIsUpstox() {
//         UpstoxConfig config = new UpstoxConfig();
//         assertThat(config.getApi().getBaseUrl()).isEqualTo("https://api.upstox.com");
//     }
//
//     @Test
//     void tokenStorePathDefaultsToFile() {
//         UpstoxConfig config = new UpstoxConfig();
//         assertThat(config.getTokenStorePath()).isEqualTo("data/upstox-tokens.json");
//     }
// }
