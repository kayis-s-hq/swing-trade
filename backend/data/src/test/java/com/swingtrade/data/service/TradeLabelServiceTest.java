package com.swingtrade.data.service;

import com.swingtrade.data.entity.TradeEntity;
import com.swingtrade.data.entity.TradeLabel;
import com.swingtrade.data.repository.TradeLabelRepository;
import com.swingtrade.data.repository.TradeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeLabelServiceTest {
    @Mock private TradeLabelRepository labelRepository;
    @Mock private TradeRepository tradeRepository;
    private TradeLabelService service;

    @BeforeEach
    void setUp() {
        service = new TradeLabelService(labelRepository, tradeRepository);
    }

    @Test
    void createsAndUpdatesLabelsForClosedTrades() {
        TradeEntity trade = new TradeEntity();
        trade.setTradeStatus("CLOSED");
        when(tradeRepository.findById(7L)).thenReturn(Optional.of(trade));
        when(labelRepository.findByTradeId(7L)).thenReturn(Optional.empty());
        TradeLabel created = new TradeLabel();
        when(labelRepository.save(any(TradeLabel.class))).thenReturn(created);

        assertThat(service.labelTrade(7L, TradeLabel.ExitReason.TARGET_HIT, "good", null, "test"))
            .isSameAs(created);
        verify(labelRepository).save(any(TradeLabel.class));

        TradeLabel existing = new TradeLabel();
        when(labelRepository.findByTradeId(7L)).thenReturn(Optional.of(existing));
        assertThat(service.labelTrade(7L, TradeLabel.ExitReason.MANUAL, "updated",
            BigDecimal.valueOf(80), "reviewer")).isSameAs(created);
        assertThat(existing.getExitReason()).isEqualTo(TradeLabel.ExitReason.MANUAL);
        assertThat(existing.getNotes()).isEqualTo("updated");
    }

    @Test
    void rejectsMissingOrOpenTrades() {
        when(tradeRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.labelTrade(1L, TradeLabel.ExitReason.MANUAL, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        TradeEntity open = new TradeEntity();
        open.setTradeStatus("OPEN");
        when(tradeRepository.findById(2L)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.labelTrade(2L, TradeLabel.ExitReason.MANUAL, null, null, null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesQueriesDistributionAndBulkOperations() {
        TradeLabel label = new TradeLabel();
        when(labelRepository.findByTradeId(3L)).thenReturn(Optional.of(label));
        when(labelRepository.findByExitReason(TradeLabel.ExitReason.STOP_LOSS)).thenReturn(List.of(label));
        when(labelRepository.findAll()).thenReturn(List.of(label));
        when(labelRepository.countByExitReason()).thenReturn(List.<Object[]>of(
            new Object[]{TradeLabel.ExitReason.STOP_LOSS, 2L}));
        assertThat(service.getLabelByTradeId(3L)).contains(label);
        assertThat(service.getLabelsByTradeId(3L)).containsExactly(label);
        assertThat(service.getLabelsByExitReason(TradeLabel.ExitReason.STOP_LOSS)).containsExactly(label);
        assertThat(service.getAllLabels()).containsExactly(label);
        assertThat(service.getExitReasonDistribution()).containsEntry(TradeLabel.ExitReason.STOP_LOSS, 2L);
        UUID id = label.getId();
        service.deleteLabel(id);
        verify(labelRepository).deleteById(id);
    }
}
