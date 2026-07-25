package com.swingtrade.api.service;

import com.swingtrade.api.dto.ScanResponse;
import com.swingtrade.domain.Signal;
import com.swingtrade.domain.Stock;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.api.service.SignalEngine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Service for triggering manual scans
 */
@Service
public class ScanService {

    private final SignalEngine signalEngine;
    private final StockStore stockStore;
    private final SignalStore signalStore;
    private final com.swingtrade.domain.store.CandleStore candleStore;

    public ScanService(SignalEngine signalEngine, StockStore stockStore,
                       SignalStore signalRepository, com.swingtrade.domain.store.CandleStore candleStore) {
        this.signalEngine = signalEngine;
        this.stockStore = stockStore;
        this.signalStore = signalRepository;
        this.candleStore = candleStore;
    }

    /**
     * Trigger manual scan for trading opportunities.
     * Generates signals for all stocks via both swing and price-action engines,
     * then queries the latest candle date's BUY signals from the repository.
     *
     * @return Scan result
     */
    public ScanResult triggerManualScan() {
        List<String> symbols = stockStore.findAllActive().stream()
                .map(Stock::symbol)
                .toList();

        for (String symbol : symbols) {
            signalEngine.generateSignalForSymbolNow(symbol);
            signalEngine.generatePriceActionSignalForSymbolNow(symbol);
        }

        // Use the latest candle date across all symbols (signals are generated for that date)
        LocalDate latestDate = symbols.stream()
                .map(candleStore::findLatestBySymbol)
                .filter(java.util.Optional::isPresent)
                .map(opt -> opt.get().date())
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        List<String> opportunities = new ArrayList<>();
        for (String symbol : symbols) {
            List<Signal> signals = signalStore.findBySymbolAndDate(symbol, latestDate);
            for (Signal signal : signals) {
                if (Signal.SignalType.BUY == signal.type()) {
                    opportunities.add(symbol);
                    break;
                }
            }
        }

        return new ScanResult(
            LocalDateTime.now(),
            symbols.size(),
            opportunities.size(),
            opportunities,
            "SUCCESS"
        );
    }

    /**
     * Trigger scan and return ScanResponse DTO
     * @return ScanResponse with scan results
     */
    public ScanResponse triggerScan() {
        ScanResult result = triggerManualScan();
        return convertScanResultToResponse(result);
    }

    /**
     * Get scan history — returns today's BUY signal summary.
     * @return List of scan response summaries
     */
    public List<ScanResponse> getScanHistory() {
        LocalDate today = LocalDate.now();
        List<Signal> todaySignals = signalStore.findAll().stream()
                .filter(s -> s.date() != null && s.date().equals(today))
                .filter(s -> s.type() == Signal.SignalType.BUY)
                .toList();
        if (todaySignals.isEmpty()) {
            return Collections.emptyList();
        }
        ScanResponse response = new ScanResponse();
        response.setScanTime(LocalDateTime.now());
        response.setStatus(ScanResponse.ScanStatus.COMPLETED);
        response.setSymbolsScanned(stockStore.findAllActive().size());
        response.setSignalsFound(todaySignals.size());
        response.setBuySignals(todaySignals.size());
        response.setSellSignals(0);
        response.setHoldSignals(0);
        response.setScannedSymbols(new ArrayList<>());
        response.setMessage("SUCCESS");
        return List.of(response);
    }

    /**
     * Get scan results for a specific date.
     * Queries all stock symbols from the repository (not hardcoded).
     * @param date the scan date
     * @return Scan results for the date
     */
    public ScanResult getScanResultsByDate(LocalDate date) {
        List<String> symbols = stockStore.findAllActive().stream()
                .map(Stock::symbol)
                .toList();

        List<String> opportunities = new ArrayList<>();
        for (String symbol : symbols) {
            List<Signal> signals = signalStore.findBySymbolAndDate(symbol, date);
            for (Signal signal : signals) {
                if (Signal.SignalType.BUY == signal.type()) {
                    opportunities.add(signal.symbol());
                }
            }
        }

        return new ScanResult(
            LocalDateTime.now(),
            symbols.size(),
            opportunities.size(),
            opportunities,
            "SUCCESS"
        );
    }

    /**
     * Convert ScanResult to ScanResponse DTO
     */
    private ScanResponse convertScanResultToResponse(ScanResult result) {
        ScanResponse response = new ScanResponse();
        response.setScanTime(result.scanTime());
        response.setStatus(ScanResponse.ScanStatus.COMPLETED);
        response.setSymbolsScanned(result.symbolsScanned());
        response.setSignalsFound(result.opportunityCount());
        response.setBuySignals(result.opportunityCount());
        response.setSellSignals(0);
        response.setHoldSignals(0);
        response.setScannedSymbols(result.opportunities());
        response.setMessage(result.status());
        return response;
    }

    public record ScanResult(LocalDateTime scanTime, int symbolsScanned, int opportunityCount, List<String> opportunities, String status) {
    }
}
