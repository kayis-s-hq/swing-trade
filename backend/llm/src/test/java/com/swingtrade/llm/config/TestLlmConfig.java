package com.swingtrade.llm.config;

import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.service.AppSettingsService;
import com.swingtrade.llm.client.VLLMClient;
import com.swingtrade.llm.service.NewsFilterService;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.llm.service.SentimentCacheService;
import com.swingtrade.llm.service.SentimentAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Test configuration for LLM module E2E tests.
 * Provides real beans for integration testing with vLLM.
 * Uses real WebClient for actual HTTP calls to vLLM.
 * Configures H2 in-memory database for repository testing.
 */
@TestConfiguration
@EnableAutoConfiguration(
    exclude = {
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
    }
)
@ComponentScan(basePackages = {"com.swingtrade.llm", "com.swingtrade.data"})
@EnableJpaRepositories(basePackages = "com.swingtrade.data.repository")
public class TestLlmConfig {

    @MockBean
    private AppSettingsService appSettingsService;

    @Primary
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Primary
    @Bean
    public VLLMClient vllmClient(WebClient.Builder webClientBuilder, AppSettingsService appSettingsService) {
        return new VLLMClient(webClientBuilder, appSettingsService);
    }

    @Primary
    @Bean
    public SentimentAnalyzer sentimentAnalyzer() {
        return new SentimentAnalyzer(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Primary
    @Bean
    public NewsFilterService newsFilterService() {
        return new NewsFilterService();
    }

    @Primary
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Primary
    @Bean
    public NewsIngestionService newsIngestionService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            NewsFilterService newsFilterService) {
        return new NewsIngestionService(
                webClientBuilder,
                objectMapper,
                newsFilterService,
                null,
                10
        );
    }

    @Primary
    @Bean
    public SentimentCacheService sentimentCacheService() {
        // Use reasonable cache settings
        return new SentimentCacheService(100, 60);
    }

    // ===== H2 Database Configuration for Testing =====

    @Primary
    @Bean
    public DataSource dataSource() {
        org.springframework.boot.jdbc.DataSourceBuilder<?> dataSourceBuilder =
                org.springframework.boot.jdbc.DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.h2.Driver");
        dataSourceBuilder.url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSourceBuilder.username("sa");
        dataSourceBuilder.password("");
        return dataSourceBuilder.build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.swingtrade.data.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        em.setJpaProperties(properties);

        return em;
    }

    @Primary
    @Bean
    public SentimentService sentimentAnalysisService(
            VLLMClient vllmClient,
            SentimentAnalyzer sentimentAnalyzer,
            NewsIngestionService newsIngestionService,
            SentimentCacheService sentimentCacheService,
            SentimentResultRepository sentimentResultRepository,
            StockRepository stockRepository) {
        return new SentimentService(
                vllmClient,
                sentimentAnalyzer,
                newsIngestionService,
                sentimentCacheService,
                sentimentResultRepository,
                stockRepository,
                100,
                60,
                false,
                0.75
        );
    }
}
