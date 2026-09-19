package com.swingtrade.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata and focused endpoint groups for the dashboard/API consumers. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Swing Trade API",
                version = "1.0",
                description = "Market data, analysis, paper trading, and risk-control APIs."
        )
)
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "Required for administrative routes when API-key enforcement is enabled."
)
public class OpenApiConfig {

    @Bean
    GroupedOpenApi tradingApi() {
        return group("Trading", "/api/trades/**", "/api/orders/**", "/api/backtest/**");
    }

    @Bean
    GroupedOpenApi signalsApi() {
        return group("Signals", "/api/signals/**", "/api/analysis/**");
    }

    @Bean
    GroupedOpenApi positionsApi() {
        return group("Positions", "/api/positions/**", "/api/portfolio/**");
    }

    @Bean
    GroupedOpenApi portfolioApi() {
        return group("Portfolio", "/api/portfolio/**", "/api/performance/**", "/api/metrics/**");
    }

    @Bean
    GroupedOpenApi adminApi() {
        return group("Admin", "/api/admin/**", "/api/ingestion/**", "/api/settings/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
