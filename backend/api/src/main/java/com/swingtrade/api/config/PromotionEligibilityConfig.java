package com.swingtrade.api.config;

import com.swingtrade.strategy.PromotionEligibilityChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the (Spring-free) promotion eligibility checker as a bean so
 * {@code PromotionEligibilityService} can be constructed; without it the application fails to start.
 */
@Configuration
public class PromotionEligibilityConfig {

    @Bean
    public PromotionEligibilityChecker promotionEligibilityChecker() {
        return new PromotionEligibilityChecker();
    }
}
