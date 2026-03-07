package com.swingtrade.api;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing paper trading positions
 */
@Service
public class PositionService {
    
    /**
     * Get open paper trading positions
     * @return List of open paper positions
     */
    public List<Position> getOpenPositions() {
        // In a real implementation, this would fetch positions from a database or service
        List<Position> positions = new ArrayList<>();
        
        // Sample data for demonstration
        positions.add(new Position("AAPL", "LONG", 145.0, 10.0, LocalDateTime.now(), 1500.0, 50.0));
        positions.add(new Position("GOOGL", "LONG", 2800.0, 2.0, LocalDateTime.now(), 5600.0, 100.0));
        
        return positions;
    }
}
