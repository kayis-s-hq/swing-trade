package com.swingtrade.data.repository;

import com.swingtrade.data.entity.JobRunStageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRunStageRepository extends JpaRepository<JobRunStageEntity, Long> {
    List<JobRunStageEntity> findByRunIdOrderBySymbolAscStageNameAsc(UUID runId);
    List<JobRunStageEntity> findByRunIdAndSymbol(UUID runId, String symbol);
    List<JobRunStageEntity> findByRunIdAndStageName(UUID runId, String stageName);

    @Query("SELECT j FROM JobRunStageEntity j WHERE j.runId = :runId AND j.symbol = :symbol AND j.stageName = :stageName")
    List<JobRunStageEntity> findByRunIdAndSymbolAndStageName(
        @Param("runId") UUID runId,
        @Param("symbol") String symbol,
        @Param("stageName") String stageName
    );
}