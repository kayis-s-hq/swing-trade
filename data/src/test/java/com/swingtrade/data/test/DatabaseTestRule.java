package com.swingtrade.data.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * JUnit 5 extension for database test setup and teardown.
 * Uses TestContainers for PostgreSQL with TimescaleDB.
 * Applies Flyway migrations before tests and cleans up after.
 */
public class DatabaseTestRule implements BeforeAllCallback, AfterAllCallback {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestRule.class);

    private final List<String> migrationLocations;

    public DatabaseTestRule() {
        this(List.of("db/migration"));
    }

    public DatabaseTestRule(List<String> migrationLocations) {
        this.migrationLocations = migrationLocations;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        logger.info("=== Database Test Rule: Starting setup ===");

        // Start TestContainer
        DatabaseTestContainer.start();

        // Configure and run Flyway migrations
        DataSource dataSource = createDataSource();
        runFlywayMigrations(dataSource);

        logger.info("=== Database Test Rule: Setup complete ===");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        logger.info("=== Database Test Rule: Starting teardown ===");

        // Stop TestContainer (closes all connections)
        DatabaseTestContainer.stop();

        logger.info("=== Database Test Rule: Teardown complete ===");
    }

    /**
     * Creates a DataSource configured with TestContainer credentials.
     *
     * @return configured DataSource
     */
    private DataSource createDataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();

        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(DatabaseTestContainer.getJdbcUrl());
        dataSource.setUsername(DatabaseTestContainer.getUsername());
        dataSource.setPassword(DatabaseTestContainer.getPassword());

        logger.info("DataSource configured for TestContainer");
        return dataSource;
    }

    /**
     * Runs Flyway migrations against the TestContainer database.
     *
     * @param dataSource the DataSource to use for migrations
     */
    private void runFlywayMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .load();

        logger.info("Running Flyway migrations from: {}", migrationLocations);

        flyway.migrate();
        logger.info("Migrations applied successfully");
    }

    /**
     * Creates a new DataSource for test usage.
     *
     * @return new DataSource instance
     */
    public DataSource createTestDataSource() {
        return createDataSource();
    }

    /**
     * Validates the database connection.
     *
     * @return true if connection is valid
     */
    public boolean validateConnection() {
        try (Connection conn = createDataSource().getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection validation failed", e);
            return false;
        }
    }
}
