package com.swingtrade.api;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.api.dto.ClosePositionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing paper trading positions
 */
@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final PaperTradingEngine paperTradingEngine;

    @Autowired
    public PositionService(PositionRepository positionRepository, PaperTradingEngine paperTradingEngine) {
        this.positionRepository = positionRepository;
        this.paperTradingEngine = paperTradingEngine;
    }

    /**
     * Get open paper trading positions
     * @return List of open paper positions
     */
    public List<Position> getOpenPositions() {
        List<PositionEntity> entities = positionRepository.findAllOpenPositions();
        return convertToDomain(entities);
    }

    /**
     * Get position by ID
     * @param id the position ID
     * @return Position details
     */
    public Position getPositionById(Long id) {
        Optional<PositionEntity> entity = positionRepository.findById(id);
        return entity.map(this::convertToDomain).orElse(null);
    }

    /**
     * Get position by symbol
     * @param symbol the stock symbol
     * @return Position details for the symbol
     */
    public Position getPositionBySymbol(String symbol) {
        List<PositionEntity> entities = positionRepository.findOpenBySymbol(symbol);
        if (!entities.isEmpty()) {
            return convertToDomain(entities.get(0));
        }
        return null;
    }

    /**
     * Close a position
     * @param id the position ID
     * @param request close position request
     * @return Closed position details
     */
    public Position closePosition(Long id, ClosePositionRequest request) {
        // Delegate to PaperTradingEngine for position closure
        // This executes the actual trade and updates the position status
        PositionEntity closedEntity = paperTradingEngine.closePosition(id);
        return convertToDomain(closedEntity);
    }

    /**
     * Get position P&L
     * @param id the position ID
     * @return Position P&L details
     */
    public PositionPnL getPositionPnL(Long id) {
        Optional<PositionEntity> entity = positionRepository.findById(id);
        if (entity.isPresent()) {
            PositionPosition position = convertToDomain(entity.get());
            double currentPrice = getCurrentPrice(position.getSymbol());
            double entryPrice = position.getEntryPrice();
            double quantity = position.getQuantity();
            double pnl = (currentPrice - entryPrice) * quantity;
            double pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100;

            return new PositionPnL(
                id,
                position.getSymbol(),
                entryPrice,
                currentPrice,
                quantity,
                pnl,
                pnlPercent
            );
        }
        return null;
    }

    /**
     * Get current price for a symbol (from latest candle data)
     */
    private double getCurrentPrice(String symbol) {
        // This would fetch the latest price from the data layer
        // For now, return a placeholder
        return 100.0;
    }

    /**
     * Convert PositionEntity to Position domain object
     */
    private Position convertToDomain(PositionEntity entity) {
        return new Position(
            entity.getSymbol(),
            entity.getDirection(),
            entity.getEntryPrice(),
            entity.getStopLoss(),
            entity.getEntryDate(),
            entity.getTarget(),
            entity.getQuantity(),
            entity.getStatus()
        );
    }

    // DTO classes for API responses

    public static class Position {
        private final String symbol;
        private final String direction;
        private final Double entryPrice;
        private final Double stopLoss;
        private final LocalDateTime entryDate;
        private final Double target;
        private final Double quantity;
        private final String status;

        public Position(String symbol, String direction, Double entryPrice, Double stopLoss,
                        LocalDateTime entryDate, Double target, Double quantity, String status) {
            this.symbol = symbol;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.stopLoss = stopLoss;
            this.entryDate = entryDate;
            this.target = target;
            this.quantity = quantity;
            this.status = status;
        }

        public String getSymbol() { return symbol; }
        public String getDirection() { return direction; }
        public Double getEntryPrice() { return entryPrice; }
        public Double getStopLoss() { return stopLoss; }
        public LocalDateTime getEntryDate() { return entryDate; }
        public Double getTarget() { return target; }
        public Double getQuantity() { return quantity; }
        public String getStatus() { return status; }
    }

    public static class PositionPnL {
        private final Long id;
        private final String symbol;
        private final Double entryPrice;
        private final Double currentPrice;
        private final Double quantity;
        private final Double pnl;
        private final Double pnlPercent;

        public PositionPnL(Long id, String symbol, Double entryPrice, Double currentPrice,
                           Double quantity, Double pnl, Double pnlPercent) {
            this.id = id;
            this.symbol = symbol;
            this.entryPrice = entryPrice;
            this.currentPrice = currentPrice;
            this.quantity = quantity;
            this.pnl = pnl;
            this.pnlPercent = pnlPercent;
        }

        public Long getId() { return id; }
        public String getSymbol() { return symbol; }
        public Double getEntryPrice() { return entryPrice; }
        public Double getCurrentPrice() { return currentPrice; }
        public Double getQuantity() { return quantity; }
        public Double getPnl() { return pnl; }
        public Double getPnlPercent() { return pnlPercent; }
    }
}
