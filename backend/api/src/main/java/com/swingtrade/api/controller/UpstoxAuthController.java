package com.swingtrade.api.controller;

// Upstox integration is unconfigured — the whole implementation is commented out
// (kept in place, not deleted, so it can be restored when Upstox is configured again).
//
// import com.swingtrade.data.service.UpstoxAuthService;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;
//
// import java.util.Map;
//
// @RestController
// @RequestMapping("/api/auth/upstox")
// public class UpstoxAuthController {
//
//     private static final Logger logger = LoggerFactory.getLogger(UpstoxAuthController.class);
//     private final UpstoxAuthService upstoxAuthService;
//
//     public UpstoxAuthController(UpstoxAuthService upstoxAuthService) {
//         this.upstoxAuthService = upstoxAuthService;
//     }
//
//     /** Step 1 of OAuth2 flow: admin visits the returned loginUrl in a browser. */
//     @GetMapping("/login-url")
//     public ResponseEntity<Map<String, String>> getLoginUrl() {
//         return ResponseEntity.ok(Map.of("loginUrl", upstoxAuthService.generateLoginUrl()));
//     }
//
//     /** Step 2: Upstox redirects here after user login. Configure this as redirect_uri in dev portal. */
//     @GetMapping("/callback")
//     public ResponseEntity<Map<String, Object>> handleCallback(
//             @RequestParam String code,
//             @RequestParam(required = false) String state) {
//         try {
//             upstoxAuthService.exchangeCodeForToken(code);
//             return ResponseEntity.ok(Map.of(
//                 "status", "success",
//                 "message", "Access token stored. Upstox data feed is now active."
//             ));
//         } catch (Exception e) {
//             logger.error("OAuth2 callback failed: {}", e.getMessage());
//             return ResponseEntity.badRequest().body(Map.of(
//                 "status", "error",
//                 "message", e.getMessage()
//             ));
//         }
//     }
//
//     /** Validate the current access token against Upstox /v2/user/profile. */
//     @GetMapping("/validate")
//     public ResponseEntity<Map<String, Object>> validateToken() {
//         return ResponseEntity.ok(Map.of("tokenValid", upstoxAuthService.validateToken()));
//     }
//
//     /** Directly set an access token (e.g., Upstox extended 1-year tokens). */
//     @PostMapping("/token")
//     public ResponseEntity<Map<String, Object>> setToken(@RequestBody Map<String, String> body) {
//         String token = body.get("accessToken");
//         if (token == null || token.isEmpty()) {
//             return ResponseEntity.badRequest().body(Map.of("error", "accessToken is required"));
//         }
//         upstoxAuthService.setAccessToken(token);
//         return ResponseEntity.ok(Map.of("status", "success"));
//     }
// }
