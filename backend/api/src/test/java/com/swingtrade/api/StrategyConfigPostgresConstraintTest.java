package com.swingtrade.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the two Postgres-level invariants V46 relies on (plan §4.1 / task item 3), which the
 * default H2 unit-test profile never exercises (H2 with Flyway disabled + Hibernate
 * ddl-auto=create-drop skips the partial unique indexes entirely - see V46's migration
 * comment). Runs full Flyway migrations (classpath:db/migration) against a real, disposable
 * Postgres via Testcontainers, exactly like {@code DataPipelineE2ETest}.
 *
 * <p>Disabled by default (requires Docker) - run manually with Docker available, or in CI where
 * Testcontainers is provisioned.
 */
@SpringBootTest
@Testcontainers
@org.junit.jupiter.api.Disabled("Requires Docker - run manually to prove V46's Postgres-level constraints")
class StrategyConfigPostgresConstraintTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("swingtrade_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private javax.sql.DataSource dataSource;

    /**
     * ux_strategy_config_current: only one is_current=true row per variant_id. Inserting a
     * second current row for the seeded BREAKOUT_STRICT variant directly via SQL (bypassing the
     * service layer, which would itself refuse this) must violate the unique index.
     */
    @Test
    void onlyOneCurrentRowPerVariantAtDbLevel() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, "
                    + "params_hash, mode, is_current) VALUES ('BREAKOUT_STRICT', 2, 'BREAKOUT', '{}'::jsonb, "
                    + "'{}'::jsonb, repeat('a', 64), 'OFF', true)"));
        }
    }

    /**
     * ux_strategy_config_champion: at most one is_current=true row with mode='CHAMPION' across
     * all variants. BREAKOUT_STRICT is already the seeded current CHAMPION; inserting a second
     * current CHAMPION row for a different variant must violate the unique index.
     */
    @Test
    void onlyOneCurrentChampionAtDbLevel() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(
                "INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, "
                    + "params_hash, mode, is_current) VALUES ('OTHER_VARIANT', 1, 'BREAKOUT', '{}'::jsonb, "
                    + "'{}'::jsonb, repeat('b', 64), 'SHADOW', true)");
            assertThrows(SQLException.class, () -> st.execute(
                "UPDATE strategy_config SET mode = 'CHAMPION' WHERE variant_id = 'OTHER_VARIANT'"));
        }
    }
}
