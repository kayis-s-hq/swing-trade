package com.swingtrade.data.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nse_holidays", indexes = {
    @Index(name = "idx_nse_holidays_date", columnList = "holiday_date")
})
public class NseHolidayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "occasion", nullable = false, length = 128)
    private String occasion;

    @Column(name = "holiday_type", nullable = false, length = 32)
    private String holidayType = "FULL";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public NseHolidayEntity() {}

    public NseHolidayEntity(LocalDate holidayDate, String occasion, String holidayType) {
        this.holidayDate = holidayDate;
        this.occasion = occasion;
        this.holidayType = holidayType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }

    public String getOccasion() { return occasion; }
    public void setOccasion(String occasion) { this.occasion = occasion; }

    public String getHolidayType() { return holidayType; }
    public void setHolidayType(String holidayType) { this.holidayType = holidayType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}