package com.swingtrade.controller;

import com.swingtrade.model.TradeSignal;
import com.swingtrade.model.TradeSignalRequest;
import com.swingtrade.service.TradeSignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
@Slf4j
public class TradeSignalController {

    private final TradeSignalService tradeSignalService;

    @GetMapping
    public ResponseEntity<List<TradeSignal>> getAllSignals() {
        return ResponseEntity.ok(tradeSignalService.getAllSignals());
    }

    @GetMapping("/stock/{stockId}")
    public ResponseEntity<List<TradeSignal>> getSignalsByStock(@PathVariable Long stockId) {
        return ResponseEntity.ok(tradeSignalService.getSignalsByStockId(stockId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<TradeSignal>> getSignalsByStatus(@PathVariable TradeSignal.SignalStatus status) {
        return ResponseEntity.ok(tradeSignalService.getSignalsByStatus(status));
    }

    @PostMapping
    public ResponseEntity<TradeSignal> createSignal(@Valid @RequestBody TradeSignalRequest request) {
        try {
            TradeSignal signal = TradeSignal.builder()
                    .stock(new com.swingtrade.model.Stock())
                    .type(TradeSignal.SignalType.valueOf(request.getType().toUpperCase()))
                    .entryPrice(request.getEntryPrice())
                    .targetPrice(request.getTargetPrice())
                    .stopLossPrice(request.getStopLossPrice())
                    .confidence(request.getConfidence())
                    .reasoning(request.getReasoning())
                    .status(TradeSignal.SignalStatus.valueOf(request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING"))
                    .build();
            
            // Set stock reference by ID
            signal.getStock().setId(request.getStockId());
            
            TradeSignal created = tradeSignalService.createSignal(signal);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid signal type or status: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Error creating signal: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TradeSignal> updateSignalStatus(
            @PathVariable Long id,
            @RequestBody SignalStatusUpdateRequest request) {
        try {
            TradeSignal updated = tradeSignalService.updateSignalStatus(id, request.getStatus());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSignal(@PathVariable Long id) {
        tradeSignalService.deleteSignal(id);
        return ResponseEntity.noContent().build();
    }

    public static class SignalStatusUpdateRequest {
        private TradeSignal.SignalStatus status;

        public TradeSignal.SignalStatus getStatus() {
            return status;
        }

        public void setStatus(TradeSignal.SignalStatus status) {
            this.status = status;
        }
    }
}
