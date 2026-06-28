package com.swingtrade.data.service;

import com.swingtrade.data.entity.TradeEntity;
import com.swingtrade.data.entity.TradeLabel;
import com.swingtrade.data.repository.TradeLabelRepository;
import com.swingtrade.data.repository.TradeRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TradeLabelService {

    private static final Logger log = LoggerFactory.getLogger(TradeLabelService.class);

    private final TradeLabelRepository tradeLabelRepository;
    private final TradeRepository tradeRepository;

    public TradeLabelService(
            TradeLabelRepository tradeLabelRepository,
            TradeRepository tradeRepository) {
        this.tradeLabelRepository = tradeLabelRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional
    public TradeLabel labelTrade(
            Long tradeId,
            TradeLabel.ExitReason exitReason,
            String notes,
            BigDecimal exitConfidence,
            String labelledBy) {

        TradeEntity trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trade not found with id: " + tradeId));

        if (!"CLOSED".equals(trade.getTradeStatus())) {
            throw new IllegalStateException(
                    "Only closed trades can be labelled. Trade status: " + trade.getTradeStatus());
        }

        Optional<TradeLabel> existingLabel = tradeLabelRepository.findByTradeId(tradeId);

        if (existingLabel.isPresent()) {
            TradeLabel label = existingLabel.get();
            label.setExitReason(exitReason);
            label.setNotes(notes);
            label.setExitConfidence(exitConfidence);
            label.setLabelledBy(labelledBy);
            label.setLabelledAt(LocalDateTime.now());
            log.info("Updated existing label for trade {}: {}", tradeId, exitReason);
            return tradeLabelRepository.save(label);
        }

        TradeLabel label = new TradeLabel();
        label.setTradeId(tradeId);
        label.setExitReason(exitReason);
        label.setNotes(notes);
        label.setExitConfidence(exitConfidence != null ? exitConfidence : BigDecimal.valueOf(100.0));
        label.setLabelledBy(labelledBy);
        label.setLabelledAt(LocalDateTime.now());

        log.info("Created new label for trade {}: {}", tradeId, exitReason);
        return tradeLabelRepository.save(label);
    }

    public Optional<TradeLabel> getLabelByTradeId(Long tradeId) {
        return tradeLabelRepository.findByTradeId(tradeId);
    }

    public List<TradeLabel> getLabelsByTradeId(Long tradeId) {
        return tradeLabelRepository.findByTradeId(tradeId).stream().toList();
    }

    public List<TradeLabel> getLabelsByExitReason(TradeLabel.ExitReason reason) {
        return tradeLabelRepository.findByExitReason(reason);
    }

    public Map<TradeLabel.ExitReason, Long> getExitReasonDistribution() {
        Map<TradeLabel.ExitReason, Long> result = new HashMap<>();
        for (Object[] row : tradeLabelRepository.countByExitReason()) {
            TradeLabel.ExitReason reason = (TradeLabel.ExitReason) row[0];
            Long count = (Long) row[1];
            result.put(reason, count);
        }
        return result;
    }

    public List<TradeLabel> getAllLabels() {
        return tradeLabelRepository.findAll();
    }

    @Transactional
    public void deleteLabel(UUID labelId) {
        tradeLabelRepository.deleteById(labelId);
        log.info("Deleted trade label with id: {}", labelId);
    }

    @Transactional
    public void bulkLabelTrades(
            List<Long> tradeIds,
            TradeLabel.ExitReason exitReason,
            String notes,
            String labelledBy) {

        int count = 0;
        for (Long tradeId : tradeIds) {
            try {
                labelTrade(tradeId, exitReason, notes, BigDecimal.valueOf(100.0), labelledBy);
                count++;
            } catch (Exception e) {
                log.warn("Failed to label trade {}: {}", tradeId, e.getMessage());
            }
        }
        log.info("Bulk labelled {} trades with exit reason: {}", count, exitReason);
    }
}
