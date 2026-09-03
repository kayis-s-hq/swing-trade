package com.swingtrade.data.repository;
import com.swingtrade.data.entity.BacktestResultEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDate; import java.util.Optional;
public interface BacktestResultRepository extends JpaRepository<BacktestResultEntity,Long>{ Optional<BacktestResultEntity> findBySymbolAndRunDate(String symbol, LocalDate date); }
