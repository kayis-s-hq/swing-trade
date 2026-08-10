package com.swingtrade.broker.service;

import com.swingtrade.broker.engine.PaperTradingEngine;
import com.swingtrade.broker.manager.OrderManager;
import com.swingtrade.broker.manager.PositionManager;
import com.swingtrade.domain.Order;
import com.swingtrade.domain.OrderStatus;
import com.swingtrade.broker.entity.PaperTradingOrderEntity;
import com.swingtrade.broker.entity.PaperTradingPortfolioEntity;
import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import com.swingtrade.broker.repository.PaperTradingOrderRepository;
import com.swingtrade.broker.repository.PaperTradingPortfolioRepository;
import com.swingtrade.broker.repository.PaperTradingSnapshotRepository;
import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bridges in-memory PaperTradingEngine with DB persistence.
 * Loads state from DB on startup; persists after every engine mutation.
 * Uses unified positions table with broker_type="PAPER".
 */
@Service
public class PaperTradingStateService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingStateService.class);

    private static final String BROKER_TYPE_PAPER = "PAPER";

    private final PaperTradingEngine engine;
    private final PositionManager positionManager;
    private final OrderManager orderManager;
    private final PaperTradingPortfolioRepository portfolioRepo;
    private final PositionRepository unifiedPositionRepo;
    private final PaperTradingOrderRepository orderRepo;
    private final PaperTradingSnapshotRepository snapshotRepo;

    public PaperTradingStateService(@Lazy PaperTradingEngine engine,
                                    PositionManager positionManager,
                                    OrderManager orderManager,
                                    PaperTradingPortfolioRepository portfolioRepo,
                                    PositionRepository unifiedPositionRepo,
                                    PaperTradingOrderRepository orderRepo,
                                    PaperTradingSnapshotRepository snapshotRepo) {
        this.engine = engine;
        this.positionManager = positionManager;
        this.orderManager = orderManager;
        this.portfolioRepo = portfolioRepo;
        this.unifiedPositionRepo = unifiedPositionRepo;
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
        List<PositionEntity> openEntities = unifiedPositionRepo.findAllOpenPositions().stream()
            .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType()))
            .toList();
        for (PositionEntity e : openEntities) {
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
        List<PositionEntity> closedEntities = new ArrayList<>();
        try {
            closedEntities.addAll(unifiedPositionRepo.findByStatus("CLOSED").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType()))
                .toList());
            closedEntities.addAll(unifiedPositionRepo.findByStatus("STOPPED").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType()))
                .toList());
            closedEntities.addAll(unifiedPositionRepo.findByStatus("TARGET_HIT").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType()))
                .toList());
        } catch (Exception e) {
            logger.error("Failed to load closed positions", e);
            throw new IllegalStateException("Failed to load closed positions from database", e);
        }
        BigDecimal totalRealized = BigDecimal.ZERO;
        for (PositionEntity e : closedEntities) {
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
            PositionEntity entity = unifiedPositionRepo.findByPositionId(position.positionId()).orElse(null);
            if (entity == null) {
                entity = new PositionEntity(position);
                entity.setBrokerType(BROKER_TYPE_PAPER);
            } else {
                entity.setCurrentPrice(position.currentPrice());
                entity.setUnrealizedPnL(position.unrealizedPnL());
                entity.setRealizedPnL(position.realizedPnL());
                entity.setStatus(position.status() != null ? position.status().name() : "OPEN");
                if (position.status() != PositionStatus.OPEN) {
                    entity.setExitTime(LocalDateTime.now());
                    entity.setExitReason("auto");
                }
            }
            unifiedPositionRepo.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save position " + position.positionId(), e);
        }
    }

    @Transactional
    public void closePosition(String positionId, Position closedPos) {
        try {
            PositionEntity entity = unifiedPositionRepo.findByPositionId(positionId).orElse(null);
            if (entity != null) {
                entity.setStatus("CLOSED");
                entity.setCurrentPrice(closedPos.currentPrice());
                entity.setUnrealizedPnL(closedPos.unrealizedPnL());
                entity.setRealizedPnL(closedPos.realizedPnL());
                entity.setExitTime(LocalDateTime.now());
                entity.setExitReason("manual");
                unifiedPositionRepo.save(entity);
                logger.info("Closed position {} updated in unified table", positionId);
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

    public List<PositionEntity> getOpenPositions() {
        return unifiedPositionRepo.findAllOpenPositions().stream()
            .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType()))
            .toList();
    }

    public List<PositionEntity> getClosedPositions() {
        try {
            var all = new java.util.ArrayList<PositionEntity>();
            all.addAll(unifiedPositionRepo.findByStatus("CLOSED").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
            all.addAll(unifiedPositionRepo.findByStatus("STOPPED").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
            all.addAll(unifiedPositionRepo.findByStatus("TARGET_HIT").stream()
                .filter(p -> BROKER_TYPE_PAPER.equals(p.getBrokerType())).toList());
            return all;
        } catch (Exception e) {
            return List.of();
        }
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

    /**
     * Looks up a persisted position entity by its database ID.
     * Used by PaperTradingEngine.closePosition(Long) to resolve the
     * positionId string when the in-memory counter may have reset.
     *
     * @param id the database position ID
     * @return the position entity, or null if not found
     */
    public PositionEntity getPositionById(Long id) {
        return unifiedPositionRepo.findById(id).orElse(null);
    }
}