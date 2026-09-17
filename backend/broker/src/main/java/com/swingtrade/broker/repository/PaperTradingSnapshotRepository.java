package com.swingtrade.broker.repository;

import com.swingtrade.broker.entity.PaperTradingSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaperTradingSnapshotRepository extends JpaRepository<PaperTradingSnapshotEntity, Long> {
    List<PaperTradingSnapshotEntity> findAllByOrderBySnapshotTimeDesc();

    List<PaperTradingSnapshotEntity> findByPortfolioIdOrderBySnapshotTimeDesc(String portfolioId);

    /**
     * The most recent snapshot for {@code portfolioId} strictly before {@code before} (i.e. the
     * prior trading day's close), used as today's mark-to-market baseline by
     * {@code PaperPortfolioServiceImpl.isDailyLossBreached()}.
     */
    Optional<PaperTradingSnapshotEntity> findFirstByPortfolioIdAndSnapshotTimeBeforeOrderBySnapshotTimeDesc(
        String portfolioId, LocalDateTime before);
}