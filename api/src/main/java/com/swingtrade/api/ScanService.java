package com.swingtrade.api;

import com.swingtrade.data.repository.SignalRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.strategy.SignalEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * Trigger manual scan for trading opportunities
     * @return Scan result
     */
    public ScanResult triggerManualScan() {
        // Trigger the signal engine to generate signals for all stocks
        List<String> opportunities = new ArrayList<>();

        // Get all stock symbols
        List<String> symbols = new ArrayList<>();
        stockRepository.findAll().forEach(stock -> symbols.add(stock.getSymbol()));

        // Generate signals for each stock
        for (String symbol : symbols) {
            var signal = signalEngine.generateSignal(symbol);
            if (signal != null && signal.getType() == SignalEngine.SignalType.BUY) {
                opportunities.add(symbol);
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
     * Get scan history
     * @return List of previous scan results
     */
    public List<ScanResult> getScanHistory() {
        // Query recent signals as scan history
        // In production, would have a dedicated scan history table
        return new ArrayList<>();
    }

    /**
     * Get scan results for a specific date
     * @param date the scan date
     * @return Scan results for the date
     */
    public ScanResult getScanResultsByDate(LocalDate date) {
        // Query signals for the specified date
        List<String> opportunities = new ArrayList<>();

        var signals = signalRepository.findBySymbolAndDate("RELIANCE", date);
        for (var signal : signals) {
            if ("BUY".equals(signal.getSignalType())) {
                opportunities.add(signal.getSymbol());
            }
        }

        return new ScanResult(
            LocalDateTime.now(),
            opportunities.size(),
            opportunities,
            "SUCCESS"
        );
    }

    // DTO class for API response

    public static class ScanResult {
        private final LocalDateTime scanTime;
        private final int opportunityCount;
        private final List<String> opportunities;
        private final String status;

        public ScanResult(LocalDateTime scanTime, int opportunityCount,
                          List<String> opportunities, String status) {
            this.scanTime = scanTime;
            this.opportunityCount = opportunityCount;
            this.opportunities = opportunities;
            this.status = status;
        }

        public LocalDateTime getScanTime() { return scanTime; }
        public int getOpportunityCount() { return opportunityCount; }
        public List<String> getOpportunities() { return opportunities; }
        public String getStatus() { return status; }
    }
}
