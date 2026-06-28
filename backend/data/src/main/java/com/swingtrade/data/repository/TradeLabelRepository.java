package com.swingtrade.data.repository;

import com.swingtrade.data.entity.TradeLabel;
import com.swingtrade.data.entity.TradeLabel.ExitReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeLabelRepository extends JpaRepository<TradeLabel, UUID> {

    List<TradeLabel> findByExitReason(ExitReason reason);

    @Query("SELECT tl.exitReason, COUNT(tl) FROM TradeLabel tl GROUP BY tl.exitReason")
    List<Object[]> countByExitReason();

    Optional<TradeLabel> findByTradeId(Long tradeId);
}
