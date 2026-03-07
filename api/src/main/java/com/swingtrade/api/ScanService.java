package com.swingtrade.api;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Service for triggering manual scans
 */
@Service
public class ScanService {
    
    /**
     * Trigger manual scan for trading opportunities
     * @return Scan result
     */
    public ScanResult triggerManualScan() {
        // In a real implementation, this would trigger a scan operation
        return new ScanResult(
            LocalDateTime.now(),
            12,
            Arrays.asList("AAPL", "TSLA", "MSFT", "GOOGL", "AMZN"),
            "SUCCESS"
        );
    }
}
