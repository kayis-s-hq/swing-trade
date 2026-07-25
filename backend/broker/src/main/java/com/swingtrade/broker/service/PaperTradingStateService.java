package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.broker.entity.*;
import com.swingtrade.broker.repository.*;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Bridges in-memory PaperTradingEngine with DB persistence.
 * Loads state from DB on startup; persists after every engine mutation.
 */
@Service
public class PaperTradingStateService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingStateService.class);

    private final PaperTradingEngine engine;
    private final PositionManager positionManager;
    private final OrderManager orderManager;
    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PaperTradingPositionRepository positionRepo;
    private final PaperTradingClosedPositionRepository closedPositionRepo;
    private final PaperTradingOrderRepository orderRepo;
    private final PaperTradingSnapshotRepository snapshotRepo;

    public PaperTradingStateService(@Lazy PaperTradingEngine engine,
                                    PositionManager positionManager,
                                    OrderManager orderManager,
                                    PaperTradingPortfolioRepository portfolioRepo,
                                    PaperTradingPositionRepository positionRepo,
                                    PaperTradingClosedPositionRepository closedPositionRepo,
                                    PaperTradingOrderRepository orderRepo,
                                    PaperTradingSnapshotRepository snapshotRepo) {
        this.engine = engine;
        this.positionManager = positionManager;
        this.orderManager = orderManager;
        this.portfolioRepo = portfolioRepo;
        this.positionRepo = positionRepo;
        this.closedPositionRepo = closedPositionRepo;
        this.orderRepo = orderRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public void loadState() {
        try {
            loadPortfolio();
            loadOpenPositions();
            loadClosedPositions();
            logger.info("Paper trading state loaded from DB");
        } catch (Exception e) {
            logger.warn("Failed to load paper trading state from DB, starting fresh: {}", e.getMessage());
        }
    }

    private void loadPortfolio() {
        PaperTradingPortfolioEntity saved = portfolioRepo.findById(1L).orElse(null);
        if (saved != null) {
            engine.getPortfolio().setCurrentCapital(saved.getCurrentCapital());
            engine.getPortfolio().setInitialCapital(saved.getInitialCapital());
            logger.info("Loaded portfolio: capital={}, initial={}",
                saved.getCurrentCapital(), saved.getInitialCapital());
        } else {
            logger.info("No portfolio state found, using defaults");
        }
    }

    private void loadOpenPositions() {
        List<PaperTradingPositionEntity> openEntities = positionRepo.findAllByStatusOpen();
        for (PaperTradingPositionEntity e : openEntities) {
            try {
                Position pos = e.toDomain();
                engine.getPortfolio().addPosition(pos);
                positionManager.getPositions().put(pos.positionId(), pos);
                logger.info("Loaded open position: {} for {}", pos.positionId(), pos.symbol());
            } catch (Exception ex) {
                logger.warn("Failed to load position {}: {}", e.getPositionId(), ex.getMessage());
            }
        }
    }

    private void loadClosedPositions() {
        List<PaperTradingClosedPositionEntity> closedEntities = closedPositionRepo.findAllByOrderByExitTimeDesc();
        BigDecimal totalRealized = BigDecimal.ZERO;
        for (PaperTradingClosedPositionEntity e : closedEntities) {
            totalRealized = totalRealized.add(e.getRealizedPnL() != null ? e.getRealizedPnL() : BigDecimal.ZERO);
        }
        // currentCapital from DB already includes realized P&L — do NOT add again
        logger.info("Loaded {} closed positions, total realized P&L: {}", closedEntities.size(), totalRealized);
    }

    public void savePortfolio() {
        try {
            PaperTradingPortfolioEntity entity = portfolioRepo.findById(1L).orElse(new PaperTradingPortfolioEntity());
            entity.setId(1L);
            entity.setPortfolioId("default");
            entity.setCurrentCapital(engine.getPortfolio().getCurrentCapital());
            entity.setInitialCapital(engine.getPortfolio().getInitialCapital());
            entity.setTotalRealizedPnl(engine.getTotalRealizedPnL());
            entity.setTotalUnrealizedPnL(engine.getTotalUnrealizedPnL());
            entity.setOpenPositionCount(engine.getOpenPositionCount());
            portfolioRepo.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save portfolio state", e);
        }
    }

    public void savePosition(Position position) {
        try {
            PaperTradingPositionEntity entity = positionRepo.findByPositionId(position.positionId()).orElse(null);
            if (entity == null) {
                entity = new PaperTradingPositionEntity(position);
            } else {
                entity.setCurrentPrice(position.currentPrice());
                entity.setPnl(position.unrealizedPnL());
                entity.setUnrealizedPnL(position.unrealizedPnL());
                entity.setRealizedPnL(position.realizedPnL());
                entity.setDirection(position.direction() != null ? position.direction().name() : "LONG");
                entity.setStatus(position.status() != null ? position.status().name() : "OPEN");
                if (position.status() != PositionStatus.OPEN) {
                    entity.setExitTime(LocalDateTime.now());
                    entity.setExitReason("auto");
                }
            }
            positionRepo.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save position " + position.positionId(), e);
        }
    }

    public void closePosition(String positionId, Position closedPos) {
        try {
            PaperTradingPositionEntity openEntity = positionRepo.findByPositionId(positionId).orElse(null);
            if (openEntity != null) {
                PaperTradingClosedPositionEntity closed = new PaperTradingClosedPositionEntity(openEntity);
                closed.setExitPrice(closedPos.currentPrice());
                closed.setPnl(closedPos.unrealizedPnL());
                closed.setRealizedPnL(closedPos.unrealizedPnL());
                closed.setExitReason(closedPos.exitTime() != null ? "auto" : "manual");
                closedPositionRepo.save(closed);
                positionRepo.deleteByPositionId(positionId);
                logger.info("Closed position {} moved to history", positionId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to close position " + positionId + " in DB", e);
        }
    }

    public void saveOrder(Order order) {
        try {
            PaperTradingOrderEntity entity = orderRepo.findByOrderId(order.getOrderId()).orElse(null);
            if (entity == null) {
                entity = new PaperTradingOrderEntity(order);
            } else {
                entity.setStatus(order.getStatus() != null ? order.getStatus().name() : entity.getStatus());
                entity.setUpdatedAt(LocalDateTime.now());
                if (order.getStatus() == OrderStatus.FILLED) {
                    entity.setExecutedAt(order.getExecutionTime());
                }
            }
            orderRepo.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save order " + order.getOrderId(), e);
        }
    }

    public void saveSnapshot() {
        try {
            PaperTradingSnapshotEntity entity = new PaperTradingSnapshotEntity();
            entity.setTotalValue(engine.getPortfolio().getTotalValue());
            entity.setCashBalance(engine.getCurrentCash());
            entity.setMarketValue(engine.getPortfolio().getTotalValue().subtract(engine.getCurrentCash()));
            entity.setTotalPnL(engine.getTotalPnL());
            entity.setReturnPct(engine.getReturnPercentage());
            entity.setOpenPositions(engine.getOpenPositionCount());
            snapshotRepo.save(entity);
            logger.debug("Saved portfolio snapshot: total={}, cash={}, pnl={}",
                entity.getTotalValue(), entity.getCashBalance(), entity.getTotalPnL());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save portfolio snapshot", e);
        }
    }

    public List<PaperTradingPositionEntity> getOpenPositions() {
        return positionRepo.findAllByStatusOpen();
    }

    public List<PaperTradingClosedPositionEntity> getClosedPositions() {
        return closedPositionRepo.findAllByOrderByExitTimeDesc();
    }

    public List<PaperTradingOrderEntity> getOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<PaperTradingSnapshotEntity> getSnapshots() {
        return snapshotRepo.findAllByOrderBySnapshotTimeDesc();
    }

    public PaperTradingPortfolioEntity getPortfolio() {
        return portfolioRepo.findById(1L).orElse(null);
    }
}