package com.swingtrade.api.scheduler;

import com.swingtrade.api.app.SwingTradeApiApplication;
import com.swingtrade.api.fixtures.DataPipelineFixtures;
import com.swingtrade.api.service.JobOrchestratorService;
import com.swingtrade.data.entity.JobRunEntity;
import com.swingtrade.data.entity.JobRunStageEntity;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.JobRunRepository;
import com.swingtrade.data.repository.JobRunStageRepository;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.repository.WatchlistRepository;
import com.swingtrade.domain.JobRun;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Signal;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import com.swingtrade.strategy.BacktestEngine;
import com.swingtrade.data.service.DataIngestionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Integration tests for daily scheduler components.
 * Uses @SpringBootTest with H2 in-memory database (no TestContainers).
 * TestBeans defines DataSource, EntityManagerFactory, and JdbcTemplate explicitly
 * so Spring Boot auto-configuration is not needed.
 *
 * NOTE: Disabled — not a Spring Boot 4.1.1 regression. JobOrchestratorService manages its own
 * concurrency via Executors.newCachedThreadPool()/CompletableFuture (application code, unchanged
 * by the upgrade); Spring's spring.task.execution.* pool and TaskExecutor bean this test
 * previously overrode play no part in it. With those overrides removed, JobOrchestratorService
 * still leaves the JobRun in RUNNING past the 30s poll — a pre-existing async/transactional
 * issue in the service (likely persistence-context visibility across the CompletableFuture
 * thread vs. the polling thread) that was never actually exercised, since this test was already
 * @Disabled at the base commit for an unrelated Testcontainers/Postgres reason. Needs dedicated
 * debugging of JobOrchestratorService's async completion path, not an upgrade-scoped fix.
 */
@Disabled("Pre-existing async/transactional issue in JobOrchestratorService — see class Javadoc")
@SpringBootTest(classes = {SwingTradeApiApplication.class, DailySchedulerIntegrationTest.TestBeans.class})
@ActiveProfiles("test")
@Import(DailySchedulerIntegrationTest.TestBeans.class)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.ai.openai.api-key=dummy"})
@DisplayName("Daily scheduler integration tests")
class DailySchedulerIntegrationTest {

    /**
     * Provides DataSource, EntityManagerFactory, JdbcTemplate, and TransactionManager
     * for the full application context to work with H2 in-memory database.
     */
    @Configuration
    static class TestBeans {

        @Bean
        DataSource dataSource() {
            org.springframework.jdbc.datasource.DriverManagerDataSource ds =
                    new org.springframework.jdbc.datasource.DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan(
                    "com.swingtrade.data.entity",
                    "com.swingtrade.broker.entity",
                    "com.swingtrade.domain"
            );
            em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            Properties jpaProps = new Properties();
            jpaProps.put("hibernate.hbm2ddl.auto", "update");
            jpaProps.put("hibernate.show_sql", "false");
            jpaProps.put("hibernate.format_sql", "false");
            em.setJpaProperties(jpaProps);

            return em;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory.getObject());
        }
    }

    @MockitoBean
    private NewsIngestionService newsIngestionService;

    @MockitoBean
    private SentimentService sentimentService;

    @MockitoBean
    private BacktestEngine backtestEngine;

    @MockitoBean
    private DataIngestionService dataIngestionService;

    // ==================== JobOrchestratorService ====================

    @Autowired
    private JobOrchestratorService jobOrchestratorService;

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobRunStageRepository jobRunStageRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private OhlcvCandleRepository ohlcvCandleRepository;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        // Configure mocks to return safe defaults
        when(newsIngestionService.fetchStockNews(anyString())).thenReturn(List.of());

        when(sentimentService.analyzeStockSentiment(anyString(), any(LocalDate.class)))
                .thenReturn(SentimentResult.create(
                        "TEST", LocalDate.now(),
                        SentimentResult.SentimentScore.POSITIVE,
                        "Positive sentiment", "raw content", 0.8));

        when(backtestEngine.runBacktest(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Backtest skipped in test"));

        doNothing().when(dataIngestionService).processSingleStock(anyString(), any(LocalDate.class));

        // Clean slate
        jobRunRepository.deleteAll();
        ohlcvCandleRepository.deleteAll();
        stockRepository.deleteAll();
        watchlistRepository.deleteAll();
    }

    @Test
    @DisplayName("Persists JobRun with COMPLETED status and stage rows")
    void testJobOrchestratorService_StartRun_PersistsJobRunAndStages() {
        // Arrange: insert stock + candles
        insertStockAndCandles("RELIANCE");

        // Add symbol to watchlist
        WatchlistEntity we = new WatchlistEntity();
        we.setSymbol("RELIANCE");
        we.setName("RELIANCE Ltd");
        we.setExchange("NSE");
        we.setIsActive(true);
        watchlistRepository.save(we);

        // Act: start run
        JobRun run = jobOrchestratorService.startRun(JobRun.TriggerType.MANUAL);

        // Wait for async completion
        waitForJobCompletion(run.runId());

        // Assert: JobRun persisted with COMPLETED status
        Optional<JobRunEntity> runOpt = jobRunRepository.findByRunId(run.runId());
        assertThat(runOpt).isPresent();
        assertThat(runOpt.get().getStatus()).isEqualTo("COMPLETED");

        // Assert: JobRunStage rows persisted -- at minimum DATA_FETCH stage executed
        List<JobRunStageEntity> stages = jobRunStageRepository
                .findByRunIdOrderBySymbolAscStageNameAsc(run.runId());
        assertThat(stages).isNotEmpty();

        // Verify stages have actual execution results (not just PENDING)
        long completedOrErrorStages = stages.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()) || "ERROR".equals(s.getStatus()))
                .count();
        assertThat(completedOrErrorStages).isGreaterThan(0);
    }

    /**
     * Polls the database until the job run transitions from RUNNING to a terminal state.
     */
    private void waitForJobCompletion(java.util.UUID runId) {
        long deadline = System.currentTimeMillis() + 30_000L; // 30s timeout
        while (System.currentTimeMillis() < deadline) {
            Optional<JobRunEntity> opt = jobRunRepository.findByRunId(runId);
            if (opt.isPresent()) {
                String status = opt.get().getStatus();
                if (!"RUNNING".equals(status)) {
                    return; // COMPLETED, FAILED, or CANCELLED
                }
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Inserts a stock entity and ~100 OHLCV candles for it.
     */
    private void insertStockAndCandles(String symbol) {
        StockEntity stock = new StockEntity();
        stock.setSymbol(symbol);
        stock.setName(symbol + " Ltd");
        stock.setExchange("NSE");
        stock.setAddedOn(LocalDate.now());
        stockRepository.save(stock);

        LocalDate[] tradingDays = generateTradingDays(100);
        for (int i = 0; i < 100; i++) {
            OhlcvCandleEntity candle = DataPipelineFixtures.createValidCandle(symbol, tradingDays[i]);
            candle.setOpenPrice(new BigDecimal("1000.00").add(BigDecimal.valueOf(i)));
            candle.setHighPrice(new BigDecimal("1020.00").add(BigDecimal.valueOf(i)));
            candle.setLowPrice(new BigDecimal("990.00").add(BigDecimal.valueOf(i)));
            candle.setClosePrice(new BigDecimal("1015.00").add(BigDecimal.valueOf(i)));
            candle.setAdjClosePrice(new BigDecimal("1015.00").add(BigDecimal.valueOf(i)));
            ohlcvCandleRepository.save(candle);
        }
    }

    private LocalDate[] generateTradingDays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate current = LocalDate.now().minusYears(1);
        while (days.size() < count && !current.isAfter(LocalDate.now())) {
            int dow = current.getDayOfWeek().getValue();
            if (dow <= 5) {
                days.add(current);
            }
            current = current.plusDays(1);
        }
        return days.toArray(new LocalDate[0]);
    }
}