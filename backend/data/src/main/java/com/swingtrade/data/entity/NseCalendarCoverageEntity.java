package com.swingtrade.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "nse_calendar_coverage")
public class NseCalendarCoverageEntity {
    @Id
    @Column(name = "calendar_year")
    private Integer calendarYear;
    @Column(nullable = false, length = 16)
    private String status;
    private String source;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    protected NseCalendarCoverageEntity() {}
    public Integer getCalendarYear() { return calendarYear; }
    public String getStatus() { return status; }
}
