package com.swingtrade.data.entity;

import jakarta.persistence.*;

/**
 * JPA entity for the app_settings key/value table.
 */
@Entity
@Table(name = "app_settings", indexes = {
    @Index(name = "idx_app_settings_key", columnList = "key", unique = true)
})
public class AppSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    public AppSettingEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Checks if a value is overridden by env var or system property.
     */
    public static String fromEnv(String key, String defaultValue) {
        String envValue = System.getenv(key.replace('.', '_').toUpperCase());
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String sysValue = System.getProperty(key);
        if (sysValue != null && !sysValue.isBlank()) {
            return sysValue;
        }
        return defaultValue;
    }
}