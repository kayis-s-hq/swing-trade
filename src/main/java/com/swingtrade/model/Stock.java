package com.swingtrade.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import jakarta.persistence.*;
import lombok.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal targetPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal stopLossPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal entryPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal riskRewardRatio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockStatus status;

    @Column(columnDefinition = "TEXT")
    private String analysisNotes;

    @Column(updatable = false)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updatedAt;

    public static class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public void serialize(LocalDateTime value, com.fasterxml.jackson.core.JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value != null) {
                gen.writeString(value.format(FORMATTER));
            } else {
                gen.writeNull();
            }
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StockStatus {
        WATCHLIST,
        ANALYZING,
        ACTIVE,
        CLOSED,
        STOPPED_OUT
    }
}
