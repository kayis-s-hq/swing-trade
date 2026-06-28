package com.swingtrade.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingtrade.data.config.UpstoxConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

@Service
public class UpstoxAuthService {

    private static final Logger logger = LoggerFactory.getLogger(UpstoxAuthService.class);

    private static final String AUTHORIZATION_ENDPOINT = "/v2/login/authorization/dialog";
    private static final String TOKEN_ENDPOINT = "/v2/login/authorization/token";
    private static final String PROFILE_ENDPOINT = "/v2/user/profile";

    private final UpstoxConfig upstoxConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;

    public UpstoxAuthService(UpstoxConfig upstoxConfig) {
        this.upstoxConfig = upstoxConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        // Priority 1: env var / config property UPSTOX_ACCESS_TOKEN
        String envToken = upstoxConfig.getAccessToken();
        if (envToken != null && !envToken.isEmpty()) {
            this.cachedAccessToken = envToken;
            logger.info("Loaded Upstox access token from environment/config");
            return;
        }
        // Priority 2: persisted token file
        loadTokenFromFile();
    }

    public String generateLoginUrl() {
        URI uri = UriComponentsBuilder
            .fromUriString(upstoxConfig.getApi().getBaseUrl())
            .path(AUTHORIZATION_ENDPOINT)
            .queryParam("response_type", "code")
            .queryParam("client_id", upstoxConfig.getClientId())
            .queryParam("redirect_uri", upstoxConfig.getRedirectUri())
            .queryParam("state", Long.toHexString(System.currentTimeMillis()))
            .build()
            .toUri();
        logger.info("Generated OAuth2 login URL");
        return uri.toString();
    }

    public String exchangeCodeForToken(String authorizationCode) {
        logger.info("Exchanging authorization code for access token");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", authorizationCode);
        formData.add("client_id", upstoxConfig.getClientId());
        formData.add("client_secret", upstoxConfig.getClientSecret());
        formData.add("redirect_uri", upstoxConfig.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        try {
            URI tokenUri = new URI(upstoxConfig.getApi().getBaseUrl() + TOKEN_ENDPOINT);
            RequestEntity<MultiValueMap<String, String>> request = RequestEntity
                .post(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = Optional.ofNullable(response.getBody().get("access_token"))
                    .map(Object::toString)
                    .orElseThrow(() -> new RuntimeException("access_token not in response"));
                setAccessToken(token);
                logger.info("Successfully exchanged authorization code for access token");
                return token;
            }
            throw new RuntimeException("Token exchange failed: " + response.getStatusCode());
        } catch (URISyntaxException | RestClientException e) {
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    public String getAccessToken() {
        return cachedAccessToken;
    }

    public void setAccessToken(String token) {
        this.cachedAccessToken = token;
        persistToken(token);
        logger.info("Access token updated");
    }

    public boolean validateToken() {
        String token = getAccessToken();
        if (token == null) return false;
        try {
            URI profileUri = new URI(upstoxConfig.getApi().getBaseUrl() + PROFILE_ENDPOINT);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            RequestEntity<Void> request = RequestEntity.get(profileUri).headers(headers).build();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public void clearTokens() {
        this.cachedAccessToken = null;
        logger.info("Cleared cached tokens");
    }

    private void persistToken(String token) {
        String path = upstoxConfig.getTokenStorePath();
        if (path == null || path.isEmpty()) return;
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            objectMapper.writeValue(file, Map.of(
                "access_token", token,
                "stored_at", java.time.Instant.now().toString()
            ));
            logger.debug("Persisted access token to {}", path);
        } catch (IOException e) {
            logger.warn("Failed to persist token to {}: {}", path, e.getMessage());
        }
    }

    private void loadTokenFromFile() {
        String path = upstoxConfig.getTokenStorePath();
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) return;
        try {
            Map<String, Object> data = objectMapper.readValue(file,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String token = (String) data.get("access_token");
            if (token != null && !token.isEmpty()) {
                this.cachedAccessToken = token;
                logger.info("Loaded access token from file {}", path);
            }
        } catch (IOException e) {
            logger.warn("Failed to load token from {}: {}", path, e.getMessage());
        }
    }
}
