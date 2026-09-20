package com.swingtrade.data.repository;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.store.PositionStoreImpl;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.PositionSummary;
import com.swingtrade.domain.TradeDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a real Hibernate EntityManagerFactory on H2 and creates the repository through Spring
 * Data's {@link JpaRepositoryFactory}, which validates every {@code @Query} on creation. This
 * catches JPQL mistakes (for example a constructor expression whose parameter types do not
 * match the entity column types) that mock-based tests cannot see and that would otherwise
 * only surface as an application startup failure.
 */
class PositionRepositoryTest {

    private static LocalContainerEntityManagerFactoryBean factoryBean;
    private static PositionRepository repository;
    private static TransactionTemplate tx;

    @BeforeAll
    static void bootPersistence() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:position_repo_test;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");

        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setManagedTypes(PersistenceManagedTypes.of(PositionEntity.class.getName()));
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        factoryBean.setJpaProperties(props);
        factoryBean.afterPropertiesSet();

        EntityManagerFactory emf = factoryBean.getObject();
        EntityManager sharedEm = SharedEntityManagerCreator.createSharedEntityManager(emf);
        repository = new JpaRepositoryFactory(sharedEm).getRepository(PositionRepository.class);
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
    }

    @AfterAll
    static void shutdown() {
        factoryBean.destroy();
    }

    @BeforeEach
    void clean() {
        tx.executeWithoutResult(status -> repository.deleteAll());
    }

    @Test
    void findOpenSummariesReturnsOnlyOpenRowsNewestFirstWithMappedColumns() {
        tx.executeWithoutResult(status -> {
            repository.save(position("OLD", "OPEN", "SHORT", LocalDate.of(2026, 1, 10), "50.0000"));
            repository.save(position("NEW", "OPEN", "LONG", LocalDate.of(2026, 2, 10), "100.0000"));
            repository.save(position("GONE", "CLOSED", "LONG", LocalDate.of(2026, 3, 10), "70.0000"));
        });

        List<PositionSummaryProjection> rows = repository.findOpenSummaries();

        assertThat(rows).extracting(PositionSummaryProjection::getSymbol).containsExactly("NEW", "OLD");
        PositionSummaryProjection newest = rows.get(0);
        assertThat(newest.getId()).isNotNull();
        assertThat(newest.getStatus()).isEqualTo("OPEN");
        assertThat(newest.getDirection()).isEqualTo("LONG");
        assertThat(newest.getEntryPrice()).isEqualByComparingTo("100.0000");
        assertThat(newest.getQuantity()).isEqualTo(10);
        assertThat(newest.getCurrentPrice()).isEqualByComparingTo("104.00");
        assertThat(newest.getUnrealizedPnL()).isEqualByComparingTo("40.00");
        assertThat(newest.getStopLoss()).isEqualByComparingTo("95.00");
        assertThat(newest.getTarget()).isEqualByComparingTo("110.00");
        assertThat(newest.getBrokerType()).isEqualTo("PAPER");
        assertThat(newest.getEntryDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(newest.getEntryReason()).isEqualTo("Reason NEW");
    }

    @Test
    void storeConvertsProjectionRowsToDomainSummaries() {
        tx.executeWithoutResult(status ->
            repository.save(position("TCS", "OPEN", "SHORT", LocalDate.of(2026, 1, 10), "100.0000")));

        List<PositionSummary> summaries = new PositionStoreImpl(repository).findOpenSummaries();

        assertThat(summaries).hasSize(1);
        PositionSummary summary = summaries.get(0);
        assertThat(summary.symbol()).isEqualTo("TCS");
        assertThat(summary.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(summary.direction()).isEqualTo(TradeDirection.SHORT);
        assertThat(summary.entryPrice()).isEqualByComparingTo("100");
        assertThat(summary.quantity()).isEqualTo(10);
        assertThat(summary.brokerType()).isEqualTo("PAPER");
        assertThat(summary.entryDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(summary.entryReason()).isEqualTo("Reason TCS");
    }

    @Test
    void summaryQueryIsAScalarProjectionThatNeverLoadsEntitiesOrOrders() {
        // Evidence for AD-H6: the list query selects columns only. Rows are projection proxies,
        // not managed PositionEntity instances, and the projection exposes no order association.
        tx.executeWithoutResult(status ->
            repository.save(position("TCS", "OPEN", "LONG", LocalDate.of(2026, 1, 10), "100.0000")));

        tx.executeWithoutResult(status -> {
            List<PositionSummaryProjection> rows = repository.findOpenSummaries();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).isNotInstanceOf(PositionEntity.class);
            assertThat(java.util.Arrays.stream(PositionSummaryProjection.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("order"));
        });
    }

    @Test
    void findOpenSummariesIsEmptyWhenNothingIsOpen() {
        assertThat(repository.findOpenSummaries()).isEmpty();
    }

    private static PositionEntity position(String symbol, String status, String direction,
                                           LocalDate entryDate, String entryPrice) {
        PositionEntity entity = new PositionEntity();
        entity.setSymbol(symbol);
        entity.setBrokerType("PAPER");
        entity.setEntryPrice(new BigDecimal(entryPrice));
        entity.setEntryDate(entryDate);
        entity.setQuantity(10);
        entity.setStopLoss(new BigDecimal("95.00"));
        entity.setTarget(new BigDecimal("110.00"));
        entity.setStatus(status);
        entity.setDirection(direction);
        entity.setCurrentPrice(new BigDecimal("104.00"));
        entity.setUnrealizedPnL(new BigDecimal("40.00"));
        entity.setEntryReason("Reason " + symbol);
        return entity;
    }
}
