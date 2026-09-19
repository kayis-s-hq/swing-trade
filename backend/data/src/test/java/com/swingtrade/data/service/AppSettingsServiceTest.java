package com.swingtrade.data.service;

import com.swingtrade.data.entity.AppSettingEntity;
import com.swingtrade.data.repository.AppSettingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppSettingsServiceTest {
    @Test
    void readsDbValuesCachesThemAndReturnsDefaultsWhenMissing() {
        AppSettingRepository repository = mock(AppSettingRepository.class);
        AppSettingEntity entity = new AppSettingEntity("trading.initial-capital", "500000");
        when(repository.findByKey("trading.initial-capital")).thenReturn(Optional.of(entity));
        when(repository.findByKey("missing")).thenReturn(Optional.empty());
        AppSettingsService service = new AppSettingsService(repository);

        assertThat(service.get("trading.initial-capital")).contains("500000");
        assertThat(service.get("trading.initial-capital")).contains("500000");
        assertThat(service.get("missing")).isEmpty();
        assertThat(service.get("missing", "fallback")).isEqualTo("fallback");
        verify(repository).findByKey("trading.initial-capital");
    }

    @Test
    void environmentPropertyOverridesDbAndMakesWritesReadOnly() {
        String key = "settings.test." + System.nanoTime();
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "from-property");
            AppSettingRepository repository = mock(AppSettingRepository.class);
            AppSettingsService service = new AppSettingsService(repository);

            assertThat(service.get(key, "fallback")).isEqualTo("from-property");
            service.set(key, "attempted-write");

            verifyNoInteractions(repository);
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    @Test
    void setUpsertsNewAndUpdatesExistingValuesInCache() {
        AppSettingRepository repository = mock(AppSettingRepository.class);
        AppSettingEntity existing = new AppSettingEntity("existing", "old");
        when(repository.findByKey("new")).thenReturn(Optional.empty());
        when(repository.findByKey("existing")).thenReturn(Optional.of(existing));
        AppSettingsService service = new AppSettingsService(repository);

        service.set("new", "one");
        service.set("existing", "two");

        verify(repository).save(any(AppSettingEntity.class));
        verify(repository).updateValue("existing", "two");
        assertThat(service.get("new")).contains("one");
        assertThat(service.get("existing")).contains("two");
    }

    @Test
    void getAllMapsNullValuesToEmptyStrings() {
        AppSettingRepository repository = mock(AppSettingRepository.class);
        AppSettingEntity populated = new AppSettingEntity("one", "value");
        AppSettingEntity empty = new AppSettingEntity();
        empty.setKey("two");
        empty.setValue(null);
        when(repository.findAll()).thenReturn(List.of(populated, empty));

        Map<String, String> settings = new AppSettingsService(repository).getAll();

        assertThat(settings).containsEntry("one", "value").containsEntry("two", "");
    }
}
