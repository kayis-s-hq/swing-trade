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
import com.swingtrade.broker.util.OptimisticLockRetryHelper;
import com.swingtrade.strategy.ExitReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges in-memory PaperTradingEngine with DB persistence.
 * Loads state from DB on startup; persists after every engine mutation.
 * Uses unified positions table with broker_type="PAPER".
 */
@Service
public class PaperTradingStateService {

    private static final Logger logger = LoggerFactory.getLogger(PaperTradingStateService.class);

    private static final String BROKER_TYPE_PAPER = "PAPER";

    private static final Pattern POSITION_ID_PATTERN = Pattern.compile("^POS_(\\d+)$");

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
            loadPendingOrders();
            logger.info("Paper trading state loaded from DB");
        } catch (Exception e) {
            logger.warn("Failed to load paper trading state from DB, starting fresh: {}", e.getMessage());
        } finally {
            // Always attempt to reseed the position ID counter, even if the
            // loads above partially failed — otherwise a fresh restart would
            // keep generating IDs from 0 and collide with (and corrupt) an
            // existing DB row via savePosition()'s upsert-by-positionId.
            seedPositionCounterFromDb();
        }
    }

    /**
     * Reseeds {@link PaperTradingEngine}'s in-memory position ID counter from
     * the historical maximum POS_ suffix found in the database (across ALL
     * statuses and broker types, not just currently-open positions — closed
     * positions' IDs are still occupied rows). Without this, the counter
     * always restarts at 0 after a JVM restart, so newly generated IDs would
     * collide with old (already CLOSED) position_id rows and silently
     * corrupt them via the upsert in {@link #savePosition(Position)}.
     */
    private void seedPositionCounterFromDb() {
        try {
            long maxSuffix = 0L;
            for (String positionId : unifiedPositionRepo.findAllPositionIds()) {
                if (positionId == null) {
                    continue;
                }
                Matcher matcher = POSITION_ID_PATTERN.matcher(positionId);
                if (!matcher.matches()) {
                    continue;
                }
                try {
                    long suffix = Long.parseLong(matcher.group(1));
                    if (suffix > maxSuffix) {
                        maxSuffix = suffix;
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Skipping malformed position ID during counter seed: {}", positionId);
                }
            }
            engine.seedPositionCounter(maxSuffix);
            logger.info("Seeded position ID counter to {} (max POS_ suffix found in DB)", maxSuffix);
        } catch (Exception e) {
            logger.warn("Failed to seed position ID counter from DB, starting from 0: {}", e.getMessage());
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

    private void loadPendingOrders() {
        orderRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(entity -> "PENDING".equals(entity.getStatus()) || "ACCEPTED".equals(entity.getStatus()))
            .forEach(entity -> {
                try {
                    Order order = new Order(entity.getOrderId(), entity.getSymbol(),
                        com.swingtrade.domain.OrderType.valueOf(entity.getType()),
                        com.swingtrade.domain.TradeDirection.valueOf(entity.getDirection()),
                        BigDecimal.valueOf(entity.getQuantity()), entity.getPrice(),
                        entity.getLimitPrice(), entity.getStopPrice());
                    order.setStatus(OrderStatus.valueOf(entity.getStatus()));
                    if (entity.getSignalId() != null && !entity.getSignalId().isBlank()) {
                        order.setAdditionalProperties(java.util.Map.of("signalId", entity.getSignalId()));
                    }
                    orderManager.getOrders().put(order.getOrderId(), order);
                    logger.info("Loaded pending order: {} for {}", order.getOrderId(), order.getSymbol());
                } catch (Exception ex) {
                    logger.warn("Failed to load pending order {}: {}", entity.getOrderId(), ex.getMessage());
                }
            });
    }

    // REQUIRES_NEW so this commits (and releases the row) immediately when the method
    // returns, instead of joining the caller's often much longer-lived transaction (e.g.
    // SignalPipeline.generatePrimarySignal's REQUIRES_NEW). Without this, PaperTradingEngine's
    // portfolioLock only serializes the in-JVM critical section - the version bump it wrote
    // is still uncommitted when the lock releases, so the next thread's read-modify-write
    // still races against it and loses at commit time.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePortfolio() {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                PaperTradingPortfolioEntity entity = portfolioRepo.findById(1L).orElse(new PaperTradingPortfolioEntity());
                entity.setId(1L);
                entity.setPortfolioId("default");
                entity.setCurrentCapital(engine.getPortfolio().getCurrentCapital());
                entity.setInitialCapital(engine.getPortfolio().getInitialCapital());
                entity.setTotalRealizedPnl(engine.getTotalRealizedPnL());
                entity.setTotalUnrealizedPnL(engine.getTotalUnrealizedPnL());
                entity.setOpenPositionCount(engine.getOpenPositionCount());
                portfolioRepo.save(entity);
            }, "PaperTradingPortfolioEntity");
        } catch (RuntimeException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw e;
            }
            throw new RuntimeException("Failed to save portfolio state", e);
        }
    }

    public void savePosition(Position position) {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                PositionEntity entity = unifiedPositionRepo.findByPositionId(position.positionId()).orElse(null);
                if (entity == null) {
                    entity = new PositionEntity(position);
                    entity.setBrokerType(BROKER_TYPE_PAPER);
                } else {
                    entity.setCurrentPrice(position.currentPrice());
                    entity.setUnrealizedPnL(position.unrealizedPnL());
                    entity.setRealizedPnL(position.realizedPnL());
                    entity.setStatus(position.status() != null ? position.status().name() : "OPEN");
                    if (position.status() != PositionStatus.OPEN && entity.getExitTime() == null) {
                        entity.setExitTime(LocalDateTime.now());
                        entity.setExitReason(position.exitReason() != null
                            ? position.exitReason() : ExitReason.MANUAL.name());
                    }
                }
                unifiedPositionRepo.save(entity);
            }, "PositionEntity");
        } catch (Exception e) {
            throw new RuntimeException("Failed to save position " + position.positionId(), e);
        }
    }

    // REQUIRES_NEW for the same reason as savePortfolio() - this must commit before
    // portfolioLock releases, not join the caller's longer-lived ambient transaction.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closePosition(String positionId, Position closedPos) {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                PositionEntity entity = unifiedPositionRepo.findByPositionId(positionId).orElse(null);
                if (entity != null) {
                    entity.setStatus("CLOSED");
                    entity.setCurrentPrice(closedPos.currentPrice());
                    entity.setUnrealizedPnL(closedPos.unrealizedPnL());
                    entity.setRealizedPnL(closedPos.realizedPnL());
                    entity.setExitTime(LocalDateTime.now());
                    entity.setExitReason(closedPos.exitReason() != null ? closedPos.exitReason() : ExitReason.MANUAL.name());
                    unifiedPositionRepo.save(entity);
                    logger.info("Closed position {} updated in unified table", positionId);
                }
            }, "PositionEntity");
        } catch (Exception e) {
            throw new RuntimeException("Failed to close position " + positionId + " in DB", e);
        }
    }

    public void saveOrder(Order order) {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                PaperTradingOrderEntity entity = orderRepo.findByOrderId(order.getOrderId()).orElse(null);
                if (entity == null) {
                    entity = new PaperTradingOrderEntity(order, "default");
                } else {
                    entity.setStatus(order.getStatus() != null ? order.getStatus().name() : entity.getStatus());
                    entity.setUpdatedAt(LocalDateTime.now());
                    if (order.getStatus() == OrderStatus.FILLED) {
                        entity.setExecutedAt(order.getExecutionTime());
                    }
                }
                orderRepo.save(entity);
            }, "PaperTradingOrderEntity");
        } catch (Exception e) {
            throw new RuntimeException("Failed to save order " + order.getOrderId(), e);
        }
    }

    public void saveSnapshot() {
        try {
            OptimisticLockRetryHelper.execute(() -> {
                PaperTradingSnapshotEntity entity = new PaperTradingSnapshotEntity();
                entity.setTotalValue(engine.getPortfolio().getTotalValue());
                entity.setCashBalance(engine.getCurrentCash());
                entity.setMarketValue(engine.getPortfolio().getTotalValue().subtract(engine.getCurrentCash()));
                entity.setTotalPnL(engine.getTotalPnL());
                entity.setReturnPct(engine.getReturnPercentage());
                entity.setOpenPositions(engine.getOpenPositionCount());
                entity.setPortfolioId("default");
                snapshotRepo.save(entity);
                logger.debug("Saved portfolio snapshot: total={}, cash={}, pnl={}",
                    entity.getTotalValue(), entity.getCashBalance(), entity.getTotalPnL());
            }, "PaperTradingSnapshotEntity");
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
