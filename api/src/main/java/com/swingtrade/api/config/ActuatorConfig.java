package com.swingtrade.api.config;

import io.micrometer.core.audit.AuditLogger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerPort;
import org.springframework.boot.actuate.endpoint.web.ExposedServletEndpoints;
import org.springframework.boot.actuate.endpoint.web.ServletEndpointManagementContext;
import org.springframework.boot.actuate.endpoint.web.ServletWebEndpointRegistrar;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.config.DefaultWebEndpointResolver;
import org.springframework.boot.actuate.endpoint.web.config.WebEndpointFilter;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class ActuatorConfig {

    @Bean
    @ConditionalOnProperty(name = "management.endpoints.enabled-by-default", havingValue = "true", matchIfMissing = true)
    public WebMvcEndpointHandlerMapping endpointHandlerMapping(
            WebEndpointsSupplier webEndpointsSupplier,
            ControllerEndpointsSupplier controllerEndpointsSupplier,
            ServletEndpointsSupplier servletEndpointsSupplier,
            WebEndpointFilter webEndpointFilter,
            ManagementServerPort managementServerPort,
            WebEndpointProperties webEndpointProperties,
            MeterRegistry meterRegistry) {

        List<WebEndpointFilter> filters = new ArrayList<>();
        filters.add(webEndpointFilter);

        return new WebMvcEndpointHandlerMapping(
                new DefaultWebEndpointResolver(
                        webEndpointsSupplier,
                        controllerEndpointsSupplier,
                        servletEndpointsSupplier,
                        filters,
                        meterRegistry
                ),
                webEndpointProperties.getBasePath()
        );
    }

    @Bean
    @Primary
    public AuditLogger auditLogger(MeterRegistry meterRegistry) {
        return new AuditLogger(meterRegistry);
    }
}
