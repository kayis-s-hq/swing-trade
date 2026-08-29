package com.swingtrade.api.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.Locale;


/**
 * Main application class for Swing Trade API module
 * Enables Spring Boot auto-configuration and component scanning
 * Enables scheduled tasks for weekly sector digest and other periodic jobs
 * <p>
 * Boot's default JPA repository/entity auto-configuration only scans the package of this
 * class (com.swingtrade.api.app) and below, not the extra @ComponentScan base packages -
 * repositories/entities under com.swingtrade.data were never being registered as beans.
 */
@ComponentScan(
    basePackages = {"com.swingtrade.api", "com.swingtrade.broker", "com.swingtrade.data", "com.swingtrade.strategy", "com.swingtrade.llm", "com.swingtrade.core"}
)
@EnableJpaRepositories(basePackages = {
        "com.swingtrade.data.repository",
        "com.swingtrade.broker.repository"
    })
@EntityScan(basePackages = {
        "com.swingtrade.data.entity",
        "com.swingtrade.broker.entity"
    })
@EnableScheduling
@SpringBootApplication
public class SwingTradeApiApplication {

    static {
        Locale.setDefault(Locale.ROOT);
    }

    private SwingTradeApiApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(SwingTradeApiApplication.class, args);
    }
}
