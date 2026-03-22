package com.swingtrade.api.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Properties;

/**
 * TestContainer for Redis.
 * Provides a standalone Redis instance for integration tests.
 */
public class RedisTestContainer {

    private static final Logger logger = LoggerFactory.getLogger(RedisTestContainer.class);

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> redisContainer;

    /**
     * Start the Redis TestContainer.
     */
    public static void start() {
        if (redisContainer != null && redisContainer.isRunning()) {
            logger.info("Redis TestContainer already running");
            return;
        }

        logger.info("Starting Redis TestContainer...");

        redisContainer = new GenericContainer<>(
                DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT);

        redisContainer.start();

        // Wait for container to be ready
        redisContainer.waitUntil(container -> {
            try {
                return redisContainer.isRunning() &&
                        redisContainer.getHealthStatus() != null;
            } catch (Exception e) {
                return true; // Accept if we can't check health
            }
        });

        logger.info("Redis TestContainer started successfully");
        logger.info("Host: {}", redisContainer.getHost());
        logger.info("Mapped Port: {}", redisContainer.getMappedPort(REDIS_PORT));
    }

    /**
     * Stop and clean up the TestContainer.
     */
    public static void stop() {
        if (redisContainer != null && redisContainer.isRunning()) {
            logger.info("Stopping Redis TestContainer...");
            redisContainer.stop();
            redisContainer = null;
            logger.info("Redis TestContainer stopped");
        }
    }

    /**
     * Get the Redis host.
     *
     * @return host address
     */
    public static String getHost() {
        if (redisContainer == null || !redisContainer.isRunning()) {
            throw new IllegalStateException("TestContainer not started");
        }
        return redisContainer.getHost();
    }

    /**
     * Get the mapped Redis port.
     *
     * @return mapped port number
     */
    public static int getPort() {
        if (redisContainer == null || !redisContainer.isRunning()) {
            throw new IllegalStateException("TestContainer not started");
        }
        return redisContainer.getMappedPort(REDIS_PORT);
    }

    /**
     * Get the Redis connection URL.
     *
     * @return connection URL in redis://host:port format
     */
    public static String getUrl() {
        return String.format("redis://%s:%s", getHost(), getPort());
    }

    /**
     * Create a JedisConnectionFactory for Spring Data Redis.
     *
     * @return configured JedisConnectionFactory
     */
    public static JedisConnectionFactory createConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(getHost());
        config.setPort(getPort());

        JedisConnectionFactory factory = new JedisConnectionFactory(config);
        factory.afterPropertiesSet();

        logger.info("JedisConnectionFactory created for TestContainer");
        return factory;
    }

    /**
     * Create a JedisConnectionFactory without calling afterPropertiesSet.
     * Useful for manual configuration.
     *
     * @return configured JedisConnectionFactory
     */
    public static JedisConnectionFactory createRawConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(getHost());
        config.setPort(getPort());

        return new JedisConnectionFactory(config);
    }

    /**
     * Check if the container is currently running.
     *
     * @return true if running, false otherwise
     */
    public static boolean isRunning() {
        return redisContainer != null && redisContainer.isRunning();
    }

    /**
     * Get container logs.
     *
     * @return container logs as string
     */
    public static String getLogs() {
        if (redisContainer == null) {
            return "Container not started";
        }
        return redisContainer.getLogs();
    }
}
