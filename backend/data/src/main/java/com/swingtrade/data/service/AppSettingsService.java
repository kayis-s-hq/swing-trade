package com.swingtrade.data.service;

import com.swingtrade.data.entity.AppSettingEntity;
import com.swingtrade.data.repository.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.swingtrade.domain.store.AppSettingsStore;

@Service
public class AppSettingsService implements AppSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);

    private final AppSettingRepository repo;

    public AppSettingsService(AppSettingRepository repo) {
        this.repo = repo;
    }

    /**
     * Read a setting: env var override → DB → default.
     */
    @Override
    public Optional<String> get(String key) {
        String envValue = AppSettingEntity.fromEnv(key, null);
        if (envValue != null) {
            return Optional.of(envValue);
        }
        Optional<AppSettingEntity> entity = repo.findByKey(key);
        if (entity.isPresent() && entity.get().getValue() != null) {
            return Optional.of(entity.get().getValue());
        }
        return Optional.empty();
    }

    /**
     * Read a setting: env var override → DB → default.
     */
    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Write a setting (upsert). Skips write if env var is set (read-only in that case).
     */
    @Transactional
    public void set(String key, String value) {
        String envValue = AppSettingEntity.fromEnv(key, null);
        if (envValue != null) {
            log.warn("Cannot set {}: overridden by environment variable", key);
            return;
        }
        Optional<AppSettingEntity> existing = repo.findByKey(key);
        if (existing.isPresent()) {
            repo.updateValue(key, value);
        } else {
            repo.save(new AppSettingEntity(key, value));
        }
        log.debug("Updated setting {}: {}", key, value);
    }

    /**
     * Get all settings as a map.
     */
    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        for (AppSettingEntity entity : repo.findAll()) {
            result.put(entity.getKey(), entity.getValue() != null ? entity.getValue() : "");
        }
        return result;
    }
}