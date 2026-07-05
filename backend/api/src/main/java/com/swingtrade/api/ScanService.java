package com.swingtrade.api;

import com.swingtrade.api.dto.ScanResponse;
import com.swingtrade.data.entity.SignalEntity;
import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.strategy.SignalEngine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Service for triggering manual scans
 */
@Service
public class ScanService {

    private final SignalEngine signalEngine;
    private final StockRepository stockRepository;
    private final SignalRepository signalRepository;

    @Autowired
    public ScanService(SignalEngine signalEngine, StockRepository stockRepository, SignalRepository signalRepository) {
        this.signalEngine = signalEngine;
        this.stockRepository = stockRepository;
        this.signalRepository = signalRepository;
    }

    /**
     * Trigger manual scan for trading opportunities.
     * Generates signals for all stocks via the signal engine, then queries
     * today's BUY signals from the repository.
     *
     * @return Scan result
     */
    public ScanResult triggerManualScan() {
        List<String> symbols = new ArrayList<>();
        stockRepository.findAll().forEach(stock -> symbols.add(stock.getSymbol()));

        for (String symbol : symbols) {
            signalEngine.generateSignalForSymbolNow(symbol);
        }

        LocalDate today = LocalDate.now();
        List<String> opportunities = new ArrayList<>();
        for (String symbol : symbols) {
            List<SignalEntity> signals = signalRepository.findBySymbolAndDate(symbol, today);
            for (SignalEntity signal : signals) {
                if ("BUY".equals(signal.getSignalType())) {
                    opportunities.add(symbol);
                    break;
                }
            }
        }

        return new ScanResult(
            LocalDateTime.now(),
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
        List<SignalEntity> todaySignals = signalRepository.findByDateRangeAndSignalType(
            today, today, "BUY", PageRequest.of(0, 50));
        if (todaySignals.isEmpty()) {
            return Collections.emptyList();
        }
        ScanResponse response = new ScanResponse();
        response.setScanTime(LocalDateTime.now());
        response.setStatus(ScanResponse.ScanStatus.COMPLETED);
        response.setSymbolsScanned((int) stockRepository.count());
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
        List<String> symbols = new ArrayList<>();
        stockRepository.findAll().forEach(stock -> symbols.add(stock.getSymbol()));

        List<String> opportunities = new ArrayList<>();
        for (String symbol : symbols) {
            List<SignalEntity> signals = signalRepository.findBySymbolAndDate(symbol, date);
            for (SignalEntity signal : signals) {
                if ("BUY".equals(signal.getSignalType())) {
                    opportunities.add(signal.getSymbol());
                }
            }
        }

        return new ScanResult(
            LocalDateTime.now(),
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
        response.setSymbolsScanned(result.opportunityCount());
        response.setSignalsFound(result.opportunityCount());
        response.setBuySignals(result.opportunityCount());
        response.setSellSignals(0);
        response.setHoldSignals(0);
        response.setScannedSymbols(result.opportunities());
        response.setMessage(result.status());
        return response;
    }

    public record ScanResult(LocalDateTime scanTime, int opportunityCount, List<String> opportunities, String status) {
    }
}
