package com.swingtrade.data.entity;

import com.swingtrade.domain.UniverseSnapshot;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "universe_snapshots", uniqueConstraints = @UniqueConstraint(
    name = "uq_universe_snapshot_symbol_date", columnNames = {"symbol", "snapshot_date"}))
public class UniverseSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20) private String symbol;
    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;
    @Column(length = 12) private String exchange;
    @Column(length = 12) private String isin;
    @Column(nullable = false) private boolean included;
    @Column(nullable = false, length = 80) private String source;
    @Column(name = "captured_at", nullable = false) private Instant capturedAt;

    public UniverseSnapshotEntity() {}

    public static UniverseSnapshotEntity fromDomain(UniverseSnapshot value) {
        UniverseSnapshotEntity entity = new UniverseSnapshotEntity();
        entity.symbol = value.symbol(); entity.snapshotDate = value.snapshotDate();
        entity.exchange = value.exchange(); entity.isin = value.isin();
        entity.included = value.included(); entity.source = value.source(); entity.capturedAt = value.capturedAt();
        return entity;
    }

    public UniverseSnapshot toDomain() {
        return new UniverseSnapshot(symbol, snapshotDate, exchange, isin, included, source, capturedAt);
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public String getExchange() { return exchange; }
    public String getIsin() { return isin; }
    public boolean isIncluded() { return included; }
    public String getSource() { return source; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setSymbol(String value) { symbol = value; }
    public void setSnapshotDate(LocalDate value) { snapshotDate = value; }
    public void setExchange(String value) { exchange = value; }
    public void setIsin(String value) { isin = value; }
    public void setIncluded(boolean value) { included = value; }
    public void setSource(String value) { source = value; }
    public void setCapturedAt(Instant value) { capturedAt = value; }
}
