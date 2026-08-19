package com.swingtrade.llm.config;

import com.swingtrade.domain.store.StockStore;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.llm.client.LlamaCppClient;
import com.swingtrade.llm.service.LlmBackendSelector;
import com.swingtrade.llm.service.LlamaCppServerManager;
import com.swingtrade.llm.service.NewsFilterService;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.llm.config.SentimentPromptLoader;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import com.swingtrade.llm.config.SynthesisPromptLoader;
import com.swingtrade.llm.service.SentimentAnalyzer;
import com.swingtrade.llm.service.LlmClientProvider;
import com.swingtrade.llm.service.LlmServerManagerProvider;
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
import java.util.Optional;
import java.util.Properties;

/**
 * Test configuration for LLM module E2E tests.
 * Provides real beans for integration testing with local llama.cpp.
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
    private AppSettingsStore appSettingsStore;

    @MockBean
    private SentimentStore sentimentStore;

    @MockBean
    private LlamaCppServerManager serverManager;

    @Primary
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Primary
    @Bean
    public LlamaCppClient llamaCppClient(WebClient.Builder webClientBuilder,
                                         AppSettingsStore appSettingsStore,
                                         LlmBackendSelector selector) {
        return new LlamaCppClient(webClientBuilder, appSettingsStore, selector, "localhost", 8090);
    }

    @Primary
    @Bean
    public SentimentPromptLoader sentimentPromptLoader() {
        return new SentimentPromptLoader(
                new org.springframework.core.io.ClassPathResource("prompts/sentiment-system.md"),
                new org.springframework.core.io.ClassPathResource("prompts/sentiment-user.md"));
    }

    @Primary
    @Bean
    public SynthesisPromptLoader synthesisPromptLoader() {
        return new SynthesisPromptLoader(
                new org.springframework.core.io.ClassPathResource("prompts/synthesis-system.md"),
                new org.springframework.core.io.ClassPathResource("prompts/synthesis-user.md"));
    }

    @Primary
    @Bean
    public PdfExtractionPromptLoader pdfExtractionPromptLoader() {
        return new PdfExtractionPromptLoader(
                new org.springframework.core.io.ClassPathResource("prompts/pdf-extraction.md"));
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
            LlmClientProvider clientProvider,
            LlmServerManagerProvider serverManagerProvider,
            SentimentPromptLoader promptLoader,
            SentimentAnalyzer sentimentAnalyzer,
            NewsIngestionService newsIngestionService,
            SentimentStore sentimentStore,
            StockStore stockStore,
            AppSettingsStore appSettingsStore) {
        return new SentimentService(
                clientProvider,
                serverManagerProvider,
                promptLoader,
                sentimentAnalyzer,
                newsIngestionService,
                sentimentStore,
                stockStore,
                appSettingsStore,
                0.75
        );
    }
}