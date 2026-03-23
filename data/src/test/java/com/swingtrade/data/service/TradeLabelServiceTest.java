package com.swingtrade.data.service;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.entity.TradeLabel;
import com.swingtrade.data.repository.TradeLabelRepository;
import com.swingtrade.data.repository.TradeRepository;
import com.swingtrade.domain.TradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeLabelServiceTest {

    @Mock
    private TradeLabelRepository tradeLabelRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeLabelService tradeLabelService;

    private UUID positionId;
    private UUID labelId;

    @BeforeEach
    void setUp() {
        positionId = UUID.randomUUID();
        labelId = UUID.randomUUID();
    }

    @Test
    void testLabelTrade_Success() {
        // Arrange
        PositionEntity position = createMockPosition(positionId, TradeStatus.CLOSED, "TARGET_HIT");
        when(tradeRepository.findById(positionId)).thenReturn(Optional.of(position));
        when(tradeLabelRepository.findByTradeId(positionId)).thenReturn(Optional.empty());
        when(tradeLabelRepository.save(any(TradeLabel.class))).thenAnswer(i -> {
            TradeLabel label = i.getArguments()[0];
            label.setId(labelId);
            label.setLabelledAt(LocalDateTime.now());
            return label;
        });

        // Act
        TradeLabel label = tradeLabelService.labelTrade(
                positionId,
                TradeLabel.ExitReason.TARGET_HIT,
                "Test notes",
                100.0,
                "admin");

        // Assert
        assertNotNull(label);
        assertEquals(positionId, label.getPosition().getId());
        assertEquals(TradeLabel.ExitReason.TARGET_HIT, label.getExitReason());
        assertEquals("Test notes", label.getNotes());
        assertEquals(new BigDecimal("100.00"), label.getExitConfidence());
        assertEquals("admin", label.getLabelledBy());
        assertNotNull(label.getLabelledAt());
    }

    @Test
    void testLabelTrade_UpdateExisting() {
        // Arrange
        PositionEntity position = createMockPosition(positionId, TradeStatus.CLOSED, "STOP_LOSS");
        TradeLabel existingLabel = new TradeLabel();
        existingLabel.setId(labelId);
        existingLabel.setPosition(position);
        existingLabel.setExitReason(TradeLabel.ExitReason.STOP_LOSS);
        existingLabel.setLabelledAt(LocalDateTime.now());

        when(tradeRepository.findById(positionId)).thenReturn(Optional.of(position));
        when(tradeLabelRepository.findByTradeId(positionId)).thenReturn(Optional.of(existingLabel));
        when(tradeLabelRepository.save(any(TradeLabel.class))).thenReturn(existingLabel);

        // Act
        TradeLabel updatedLabel = tradeLabelService.labelTrade(
                positionId,
                TradeLabel.ExitReason.TARGET_HIT,
                "Updated notes",
                95.0,
                "user1");

        // Assert
        assertEquals(TradeLabel.ExitReason.TARGET_HIT, updatedLabel.getExitReason());
        assertEquals("Updated notes", updatedLabel.getNotes());
        assertEquals(new BigDecimal("95.00"), updatedLabel.getExitConfidence());
        assertEquals("user1", updatedLabel.getLabelledBy());
    }

    @Test
    void testLabelTrade_TradeNotFound() {
        // Arrange
        when(tradeRepository.findById(positionId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tradeLabelService.labelTrade(positionId, TradeLabel.ExitReason.TARGET_HIT, "notes", 100.0, "admin")
        );
        assertTrue(exception.getMessage().contains("Trade not found"));
    }

    @Test
    void testLabelTrade_OpenTrade() {
        // Arrange
        PositionEntity position = createMockPosition(positionId, TradeStatus.OPEN, "TARGET_HIT");
        when(tradeRepository.findById(positionId)).thenReturn(Optional.of(position));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> tradeLabelService.labelTrade(positionId, TradeLabel.ExitReason.TARGET_HIT, "notes", 100.0, "admin")
        );
        assertTrue(exception.getMessage().contains("Only closed trades can be labelled"));
    }

    @Test
    void testGetLabelByTradeId_Found() {
        // Arrange
        TradeLabel label = new TradeLabel();
        label.setId(labelId);
        when(tradeLabelRepository.findByTradeId(positionId)).thenReturn(Optional.of(label));

        // Act
        Optional<TradeLabel> result = tradeLabelService.getLabelByTradeId(positionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(labelId, result.get().getId());
    }

    @Test
    void testGetLabelByTradeId_NotFound() {
        // Arrange
        when(tradeLabelRepository.findByTradeId(positionId)).thenReturn(Optional.empty());

        // Act
        Optional<TradeLabel> result = tradeLabelService.getLabelByTradeId(positionId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testGetExitReasonDistribution() {
        // Arrange
        when(tradeLabelRepository.countByExitReason()).thenReturn(Map.of(
                TradeLabel.ExitReason.TARGET_HIT, 5L,
                TradeLabel.ExitReason.STOP_LOSS, 3L,
                TradeLabel.ExitReason.TIME_STOP, 2L
        ));

        // Act
        var result = tradeLabelService.getExitReasonDistribution();

        // Assert
        assertEquals(3, result.size());
        assertEquals(5L, result.get(TradeLabel.ExitReason.TARGET_HIT));
        assertEquals(3L, result.get(TradeLabel.ExitReason.STOP_LOSS));
        assertEquals(2L, result.get(TradeLabel.ExitReason.TIME_STOP));
    }

    @Test
    void testBulkLabelTrades_Success() {
        // Arrange
        List<UUID> positionIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        PositionEntity position1 = createMockPosition(positionIds.get(0), TradeStatus.CLOSED, "TARGET_HIT");
        PositionEntity position2 = createMockPosition(positionIds.get(1), TradeStatus.CLOSED, "STOP_LOSS");
        when(tradeRepository.findById(positionIds.get(0))).thenReturn(Optional.of(position1));
        when(tradeRepository.findById(positionIds.get(1))).thenReturn(Optional.of(position2));
        when(tradeLabelRepository.findByTradeId(any())).thenReturn(Optional.empty());
        when(tradeLabelRepository.save(any(TradeLabel.class))).thenAnswer(i -> {
            TradeLabel label = i.getArguments()[0];
            label.setId(UUID.randomUUID());
            label.setLabelledAt(LocalDateTime.now());
            return label;
        });

        // Act
        tradeLabelService.bulkLabelTrades(positionIds, TradeLabel.ExitReason.TARGET_HIT, "bulk label", "admin");

        // Assert
        verify(tradeLabelRepository, times(2)).save(any(TradeLabel.class));
    }

    @Test
    void testDeleteLabel() {
        // Arrange
        UUID labelDeleteId = UUID.randomUUID();

        // Act
        tradeLabelService.deleteLabel(labelDeleteId);

        // Assert
        verify(tradeLabelRepository).deleteById(labelDeleteId);
    }

    private PositionEntity createMockPosition(UUID id, TradeStatus status, String exitReason) {
        PositionEntity mock = mock(PositionEntity.class);
        when(mock.getId()).thenReturn(id);
        when(mock.getStatus()).thenReturn(status.name());
        when(mock.getPnl()).thenReturn(BigDecimal.valueOf(100.0));
        return mock;
    }
}
