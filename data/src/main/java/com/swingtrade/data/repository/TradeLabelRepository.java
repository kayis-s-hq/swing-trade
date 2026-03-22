package com.swingtrade.data.repository;

import com.swingtrade.data.entity.TradeLabel;
import com.swingtrade.data.entity.TradeLabel.ExitReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TradeLabelRepository extends JpaRepository<TradeLabel, UUID> {

    List<TradeLabel> findByExitReason(ExitReason reason);

    @Query("SELECT new map(er as exitReason, COUNT(tl) as count) " +
           "FROM TradeLabel tl GROUP BY tl.exitReason")
    Map<ExitReason, Long> countByExitReason();
}
