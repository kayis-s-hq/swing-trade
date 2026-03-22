package com.swingtrade.data.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * TestContainer for PostgreSQL with TimescaleDB extension.
 * Provides a clean database instance for integration tests.
 * Uses static singleton pattern with @Container annotation style.
 */
public class DatabaseTestContainer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestContainer.class);

    private static final String TIMESCALEDB_IMAGE = "timescale/timescaledb:latest-pg15";
    private static final String DATABASE_NAME = "swingtrade_test";
    private static final String DATABASE_USER = "test";
    private static final String DATABASE_PASSWORD = "test";

    private static PostgreSQLContainer<?> postgreSQLContainer;

    static {
        // Register shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseTestContainer::stop, "db-test-container-shutdown-hook"));
    }

    /**
     * Start the TestContainer with TimescaleDB extension setup.
     * Initializes PostgreSQL with the required database and schema.
     */
    public static void start() {
        if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
            logger.info("PostgreSQL TestContainer already running");
            return;
        }

        logger.info("Starting PostgreSQL TestContainer with TimescaleDB...");

        postgreSQLContainer = new PostgreSQLContainer<>(
                DockerImageName.parse(TIMESCALEDB_IMAGE)
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(DATABASE_USER)
                .withPassword(DATABASE_PASSWORD)
                .withInitScript("db/migration/V2__create_hypertables.sql");

        postgreSQLContainer.start();

        // Setup TimescaleDB extension
        setupTimescaleDBExtension();

        logger.info("PostgreSQL TestContainer started successfully");
        logger.info("JDBC URL: {}", postgreSQLContainer.getJdbcUrl());
    }

    /**
     * Setup TimescaleDB extension in the database.
     */
    private static void setupTimescaleDBExtension() {
        try (Connection conn = postgreSQLContainer.createConnection("");
             Statement stmt = conn.createStatement()) {
            // Enable TimescaleDB extension
            stmt.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
            logger.info("TimescaleDB extension enabled");
        } catch (SQLException e) {
            logger.warn("Failed to enable TimescaleDB extension (may already be enabled): {}", e.getMessage());
        }
    }

    /**
     * Stop and clean up the TestContainer.
     */
    public static void stop() {
        if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
            logger.info("Stopping PostgreSQL TestContainer...");
            postgreSQLContainer.stop();
            postgreSQLContainer = null;
            logger.info("PostgreSQL TestContainer stopped");
        }
    }

    /**
     * Get the JDBC connection URL for the TestContainer.
     *
     * @return JDBC URL for connecting to the TestContainer
     */
    public static String getJdbcUrl() {
        if (postgreSQLContainer == null || !postgreSQLContainer.isRunning()) {
            throw new IllegalStateException("TestContainer not started");
        }
        return postgreSQLContainer.getJdbcUrl();
    }

    /**
     * Get the database name.
     *
     * @return database name
     */
    public static String getDatabaseName() {
        return DATABASE_NAME;
    }

    /**
     * Get the database username.
     *
     * @return database username
     */
    public static String getUsername() {
        return DATABASE_USER;
    }

    /**
     * Get the database password.
     *
     * @return database password
     */
    public static String getPassword() {
        return DATABASE_PASSWORD;
    }

    /**
     * Get the complete JDBC URL with credentials.
     *
     * @return formatted JDBC connection string
     */
    public static String getConnectionString() {
        return String.format("jdbc:postgresql://%s:%s/%s",
                postgreSQLContainer.getHost(),
                postgreSQLContainer.getMappedPort(5432),
                DATABASE_NAME);
    }

    /**
     * Check if the container is currently running.
     *
     * @return true if running, false otherwise
     */
    public static boolean isRunning() {
        return postgreSQLContainer != null && postgreSQLContainer.isRunning();
    }
}
