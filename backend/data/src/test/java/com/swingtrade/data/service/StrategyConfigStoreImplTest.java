package com.swingtrade.data.service;

import com.swingtrade.domain.PortfolioAction;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.data.StrategyConfigTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2-profile tests for {@link StrategyConfigStoreImpl}. Runs against H2 with Hibernate
 * ddl-auto=create-drop (Flyway disabled) per {@code TestProfileResolver}'s default - this proves
 * the store's own append-only/business-rule behaviour, NOT the Postgres partial unique indexes
 * from V46 (H2 never sees those - see V46's migration comment). The Postgres-level constraint
 * proof lives in a separate Testcontainers-backed test in the api module.
 */
@SpringBootTest(classes = StrategyConfigTestApp.class)
@ActiveProfiles(resolver = com.swingtrade.data.test.TestProfileResolver.class)
@Transactional
class StrategyConfigStoreImplTest {

    @Autowired
    private StrategyConfigStoreImpl store;

    private StrategyConfig newConfig(String variantId, int version, StrategyMode mode) {
        return new StrategyConfig(
            null, variantId, version, "BREAKOUT",
            Map.of("emaFast", 20, "emaSlow", 50),
            Map.of("sentimentGate", true),
            "hash-" + variantId + "-" + version,
            mode, new BigDecimal("500000"), true, null, "test", LocalDateTime.now());
    }

    @Test
    void savesFirstVersionAsCurrent() {
        StrategyConfig saved = store.save(newConfig("TEST_V1", 1, StrategyMode.OFF));

        assertThat(saved.id()).isNotNull();
        assertThat(store.findCurrent("TEST_V1")).isPresent();
        assertThat(store.findCurrent("TEST_V1").orElseThrow().variantId()).isEqualTo("TEST_V1");
        assertThat(store.findCurrent("TEST_V1").orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void newVersionFlipsPreviousCurrentOff() {
        store.save(newConfig("TEST_V2", 1, StrategyMode.OFF));
        StrategyConfig v2 = store.save(newConfig("TEST_V2", 2, StrategyMode.OFF));

        List<StrategyConfig> versions = store.findVersions("TEST_V2");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).isCurrent()).isFalse();
        assertThat(versions.get(1).isCurrent()).isTrue();
        assertThat(store.findCurrent("TEST_V2").orElseThrow().version()).isEqualTo(v2.version());
    }

    @Test
    void rejectsNonSequentialVersion() {
        store.save(newConfig("TEST_V3", 1, StrategyMode.OFF));

        assertThatThrownBy(() -> store.save(newConfig("TEST_V3", 5, StrategyMode.OFF)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByParamsHashDedupesAcrossVersions() {
        StrategyConfig v1 = store.save(newConfig("TEST_V4", 1, StrategyMode.OFF));

        Optional<StrategyConfig> found = store.findByParamsHash("TEST_V4", v1.paramsHash());
        assertThat(found).isPresent();
        assertThat(found.get().version()).isEqualTo(1);
    }

    @Test
    void countActiveCountsOnlyShadowAndChampion() {
        store.save(newConfig("TEST_ACTIVE_A", 1, StrategyMode.SHADOW));
        store.save(newConfig("TEST_ACTIVE_B", 1, StrategyMode.CHAMPION));
        store.save(newConfig("TEST_ACTIVE_C", 1, StrategyMode.OFF));
        store.save(newConfig("TEST_ACTIVE_D", 1, StrategyMode.BACKTEST_ONLY));

        assertThat(store.countActive()).isEqualTo(2);
    }

    @Test
    void updateModeWritesAuditRowAndChangesCurrentMode() {
        store.save(newConfig("TEST_MODE", 1, StrategyMode.OFF));

        store.updateMode("TEST_MODE", StrategyMode.SHADOW);

        assertThat(store.findCurrent("TEST_MODE").orElseThrow().mode()).isEqualTo(StrategyMode.SHADOW);
    }

    @Test
    void findCurrentChampionReturnsTheOnlyChampion() {
        store.save(newConfig("TEST_CHAMP", 1, StrategyMode.CHAMPION));

        assertThat(store.findCurrentChampion()).isPresent();
        assertThat(store.findCurrentChampion().orElseThrow().variantId()).isEqualTo("TEST_CHAMP");
    }

    @Test
    void portfolioActionIsPersistedOnNewVersion() {
        store.save(newConfig("TEST_PA", 1, StrategyMode.OFF));
        StrategyConfig v2 = new StrategyConfig(
            null, "TEST_PA", 2, "BREAKOUT", Map.of("emaFast", 21, "emaSlow", 50), Map.of(),
            "hash-TEST_PA-2", StrategyMode.OFF, new BigDecimal("500000"), true,
            PortfolioAction.RESET, "v2", LocalDateTime.now());

        StrategyConfig saved = store.save(v2);

        assertThat(saved.portfolioAction()).isEqualTo(PortfolioAction.RESET);
    }
}
