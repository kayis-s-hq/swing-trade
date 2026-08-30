package com.swingtrade.data.repository;
import com.swingtrade.data.entity.LlmAnalysisResultEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDate; import java.util.Optional;
public interface LlmAnalysisResultRepository extends JpaRepository<LlmAnalysisResultEntity,Long>{Optional<LlmAnalysisResultEntity> findBySymbolAndAnalysisDate(String symbol,LocalDate date);}
