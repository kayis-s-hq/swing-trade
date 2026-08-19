package com.swingtrade.data.service;

import com.swingtrade.data.entity.FyersSymbolEntity;
import com.swingtrade.data.repository.FyersSymbolRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Downloads and caches the Fyers NSE equity symbol master (unauthenticated CSV endpoint).
 * Backs symbol search and instrument-detail lookups for FyersServiceClient.
 * Not a Spring bean — Fyers SDK has broken internal dependencies. Instantiate manually.
 */
public class FyersSymbolMasterService {

    private static final Logger logger = LoggerFactory.getLogger(FyersSymbolMasterService.class);
    private static final String CSV_URL = "https://public.fyers.in/sym_details/NSE_CM.csv";
    private static final Pattern EQ_SYMBOL = Pattern.compile("^NSE:.+-EQ$");

    // Headerless CSV columns (verified against a live download 2026-07-04):
    // 0 fyToken | 1 name | 2 exch instrument type | 3 lot size | 4 tick size | 5 ISIN
    // 6 trading session | 7 last update | 8 expiry | 9 fyers symbol (NSE:X-EQ)
    // 10 exchange id | 11 segment id | 12 scrip code | 13 trading symbol (X)
    private static final int COL_FY_TOKEN = 0;
    private static final int COL_NAME = 1;
    private static final int COL_LOT_SIZE = 3;
    private static final int COL_TICK_SIZE = 4;
    private static final int COL_ISIN = 5;
    private static final int COL_FYERS_SYMBOL = 9;
    private static final int COL_TRADING_SYMBOL = 13;
    private static final int MIN_COLUMNS = 14;

    private final WebClient webClient;
    private final FyersSymbolRepository repository;

    public FyersSymbolMasterService(WebClient.Builder webClientBuilder, FyersSymbolRepository repository) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.webClient = webClientBuilder.exchangeStrategies(strategies).build();
        this.repository = repository;
    }

    @Transactional
    public int refresh() {
        String csv;
        try {
            csv = webClient.get().uri(CSV_URL).retrieve().bodyToMono(String.class).block();
        } catch (Exception e) {
            logger.error("Failed to download Fyers symbol master: {}", e.getMessage());
            return 0;
        }
        if (csv == null || csv.isBlank()) {
            logger.error("Fyers symbol master download returned empty response");
            return 0;
        }

        List<FyersSymbolEntity> rows = parse(csv);
        repository.deleteAllInBatch();
        repository.saveAll(rows);
        logger.info("Refreshed Fyers symbol master: {} symbols", rows.size());
        return rows.size();
    }

    // Package-private for unit testing
    List<FyersSymbolEntity> parse(String csv) {
        List<FyersSymbolEntity> rows = new ArrayList<>();
        int skipped = 0;

        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
            for (CSVRecord record : parser) {
                if (record.size() < MIN_COLUMNS) {
                    skipped++;
                    continue;
                }

                String fyersSymbol = record.get(COL_FYERS_SYMBOL).trim();
                if (!EQ_SYMBOL.matcher(fyersSymbol).matches()) {
                    continue; // not a plain equity row (derivatives, SME/BE series, etc.)
                }

                try {
                    FyersSymbolEntity entity = new FyersSymbolEntity();
                    entity.setFyToken(record.get(COL_FY_TOKEN).trim());
                    entity.setFyersSymbol(fyersSymbol);
                    entity.setTradingSymbol(record.get(COL_TRADING_SYMBOL).trim());
                    entity.setName(record.get(COL_NAME).trim());
                    entity.setExchange("NSE");
                    entity.setIsin(blankToNull(record.get(COL_ISIN).trim()));
                    entity.setLotSize(parseIntSafe(record.get(COL_LOT_SIZE)));
                    entity.setTickSize(parseDecimalSafe(record.get(COL_TICK_SIZE)));
                    entity.setUpdatedAt(LocalDateTime.now());
                    rows.add(entity);
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Fyers symbol master CSV: {}", e.getMessage());
        }

        if (skipped > 0) {
            logger.warn("Skipped {} malformed rows while parsing Fyers symbol master", skipped);
        }
        return rows;
    }

    public List<FyersSymbolEntity> search(String query, int limit) {
        return repository.search(query, Pageable.ofSize(limit));
    }

    public long count() {
        return repository.count();
    }

    public java.util.Optional<FyersSymbolEntity> findByTradingSymbol(String symbol) {
        return repository.findByTradingSymbolIgnoreCase(symbol);
    }

    public List<String> allTradingSymbols() {
        return repository.findAll().stream().map(FyersSymbolEntity::getTradingSymbol).toList();
    }

    @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            logger.error("Scheduled Fyers symbol master refresh failed: {}", e.getMessage(), e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshIfEmpty() {
        try {
            if (repository.count() == 0) {
                refresh();
            }
        } catch (Exception e) {
            logger.warn("Startup Fyers symbol master refresh failed: {}", e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Integer parseIntSafe(String s) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseDecimalSafe(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
