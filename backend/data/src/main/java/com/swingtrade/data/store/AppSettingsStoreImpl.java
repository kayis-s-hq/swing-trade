package com.swingtrade.data.store;

import com.swingtrade.data.repository.AppSettingRepository;
import com.swingtrade.domain.store.AppSettingsStore;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AppSettingsStoreImpl implements AppSettingsStore {

    private final AppSettingRepository repository;

    public AppSettingsStoreImpl(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> get(String key) {
        return repository.findByKey(key).map(com.swingtrade.data.entity.AppSettingEntity::getValue);
    }

    @Override
    public void set(String key, String value) {
        repository.updateValue(key, value);
    }
}