package com.swingtrade.data;

import com.swingtrade.data.repository.StrategyConfigAuditRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.data.service.StrategyConfigStoreImpl;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot test app used only by {@code StrategyConfigStoreImplTest}. Declared in
 * package {@code com.swingtrade.data} (rather than nested inside the test class) so Spring
 * Boot's default entity-scan (no explicit {@code @EntityScan}) picks up
 * {@code com.swingtrade.data.entity} as a sub-package, without component-scanning the rest of
 * {@code com.swingtrade.data} (which pulls in unrelated beans, e.g. Fyers/resilience4j config,
 * that aren't wired for a narrow unit-style test).
 */
@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.swingtrade.data.repository")
public class StrategyConfigTestApp {

    @Bean
    StrategyConfigStoreImpl strategyConfigStoreImpl(
        StrategyConfigRepository repository, StrategyConfigAuditRepository auditRepository
    ) {
        return new StrategyConfigStoreImpl(repository, auditRepository);
    }
}
