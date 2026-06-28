package com.swingtrade.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignalRequest {

    @NotNull(message = "Stock ID is required")
    private Long stockId;

    @NotBlank(message = "Signal type is required")
    private String type;

    private BigDecimal entryPrice;

    private BigDecimal targetPrice;

    private BigDecimal stopLossPrice;

    private BigDecimal confidence;

    private String reasoning;

    private String status;
}
