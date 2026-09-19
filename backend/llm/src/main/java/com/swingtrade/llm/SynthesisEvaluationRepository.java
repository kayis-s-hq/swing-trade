package com.swingtrade.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SynthesisEvaluationRepository extends JpaRepository<SynthesisEvaluationEntity, Long> {
    Optional<SynthesisEvaluationEntity> findBySymbolAndAnalysisDate(String symbol, LocalDate analysisDate);

    List<SynthesisEvaluationEntity> findByOutcomeMeasuredAtIsNullAndAnalysisDateBeforeOrderByAnalysisDateAsc(
            LocalDate analysisDate);
}
