package com.swingtrade.api;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing trading signals
 */
@Service
public class SignalService {
    
    /**
     * Get the latest trading signals for today
     * @return List of latest trading signals
     */
    public List<Signal> getLatestSignals() {
        // In a real implementation, this would fetch signals from a database or service
        List<Signal> signals = new ArrayList<>();
        
        // Sample data for demonstration
        signals.add(new Signal("AAPL", "BUY", 150.0, LocalDateTime.now(), "Breakout above resistance"));
        signals.add(new Signal("TSLA", "SELL", 250.0, LocalDateTime.now(), "Support level broken"));
        
        return signals;
    }
}
