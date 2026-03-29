package com.swingtrade.api.test.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TestContainer for PostgreSQL database integration testing.
 * Provides a real PostgreSQL database for API integration tests.
 *
 * Usage: Annotate test classes with @Testcontainers
 */
@Testcontainers
public class DatabaseTestContainer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestContainer.class);

    // PostgreSQL version matching production (15.x)
    private static final String POSTGRES_VERSION = "postgres:15.4";
    private static final String TEST_DATABASE = "swing_trade_test";
    private static final String TEST_USERNAME = "swing_trade_test";
    private static final String TEST_PASSWORD = "swing_trade_test";

    // Singleton container instance
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_VERSION)
            .withDatabaseName(TEST_DATABASE)
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD);

    /**
     * Register TestContainer properties with Spring Test.
     * This is called automatically by Spring Test context.
     */
    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        logger.info("Configuring test database from TestContainer");

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        // Disable schema validation (we use Flyway)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Flyway configuration
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        logger.info("Test database configured: {}", postgres.getJdbcUrl());
    }

    /**
     * Start the TestContainer before tests run.
     */
    public static void start() {
        if (!postgres.isRunning()) {
            logger.info("Starting PostgreSQL TestContainer...");
            postgres.start();
            logger.info("PostgreSQL TestContainer started at: {}", postgres.getJdbcUrl());
        }
    }

    /**
     * Stop the TestContainer after tests complete.
     */
    public static void stop() {
        if (postgres.isRunning()) {
            logger.info("Stopping PostgreSQL TestContainer...");
            postgres.stop();
            logger.info("PostgreSQL TestContainer stopped");
        }
    }

    /**
     * Get the JDBC URL for the TestContainer.
     */
    public static String getJdbcUrl() {
        return postgres.getJdbcUrl();
    }

    /**
     * Get the database name.
     */
    public static String getDatabaseName() {
        return TEST_DATABASE;
    }

    /**
     * Get the username.
     */
    public static String getUsername() {
        return TEST_USERNAME;
    }

    /**
     * Get the password.
     */
    public static String getPassword() {
        return TEST_PASSWORD;
    }

    /**
     * Check if container is running.
     */
    public static boolean isRunning() {
        return postgres.isRunning();
    }

    /**
     * Get container status.
     */
    public static String getStatus() {
        return postgres.isRunning() ? "running" : "stopped";
    }
}
