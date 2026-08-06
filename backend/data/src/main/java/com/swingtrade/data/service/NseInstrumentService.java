package com.swingtrade.data.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Service
@Profile("upstox")
public class NseInstrumentService {

    private static final Logger logger = LoggerFactory.getLogger(NseInstrumentService.class);
    private static final String INSTRUMENTS_URL =
        "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, String> symbolToInstrumentKey = new ConcurrentHashMap<>();

    public NseInstrumentService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostConstruct
    public void loadInstruments() {
        try {
            logger.info("Downloading NSE instrument master from {}", INSTRUMENTS_URL);
            byte[] gzipBytes = webClient.get()
                .uri(INSTRUMENTS_URL)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

            if (gzipBytes == null || gzipBytes.length == 0) {
                logger.error("NSE instruments download returned empty response");
                return;
            }

            String json;
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
                json = new String(gzis.readAllBytes());
            }

            loadFromJson(json);
            logger.info("Loaded {} NSE equity instruments", symbolToInstrumentKey.size());
        } catch (IOException e) {
            logger.error("Failed to load NSE instruments: {}", e.getMessage(), e);
        }
    }

    // Package-private for unit testing
    void loadFromJson(String json) {
        try {
            List<NseInstrumentRecord> records = objectMapper.readValue(
                json, new TypeReference<List<NseInstrumentRecord>>() {}
            );
            Map<String, String> newMap = new HashMap<>();
            for (NseInstrumentRecord r : records) {
                if ("NSE_EQ".equals(r.segment) && "EQ".equals(r.instrumentType)
                        && r.tradingSymbol != null && r.instrumentKey != null) {
                    newMap.put(r.tradingSymbol.toUpperCase(), r.instrumentKey);
                }
            }
            this.symbolToInstrumentKey = newMap;
        } catch (IOException e) {
            logger.error("Failed to parse instruments JSON: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns the URL-encoded instrument key for a given NSE trading symbol.
     * Example: "RELIANCE" → Optional.of("NSE_EQ%7CINE002A01018")
     */
    public Optional<String> getEncodedInstrumentKey(String tradingSymbol) {
        String raw = symbolToInstrumentKey.get(tradingSymbol.toUpperCase());
        if (raw == null) return Optional.empty();
        return Optional.of(raw.replace("|", "%7C"));
    }

    public Set<String> getAllSymbols() {
        return symbolToInstrumentKey.keySet();
    }

    public void refreshInstruments() {
        loadInstruments();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NseInstrumentRecord {
        @JsonProperty("segment") private String segment;
        @JsonProperty("instrument_type") private String instrumentType;
        @JsonProperty("trading_symbol") private String tradingSymbol;
        @JsonProperty("instrument_key") private String instrumentKey;
        @JsonProperty("name") private String name;
        @JsonProperty("isin") private String isin;
    }
}
