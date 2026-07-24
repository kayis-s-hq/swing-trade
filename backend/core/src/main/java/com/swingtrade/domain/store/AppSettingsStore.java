package com.swingtrade.domain.store;

import java.util.Optional;

public interface AppSettingsStore {

    Optional<String> get(String key);

    void set(String key, String value);
}