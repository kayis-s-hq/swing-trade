package com.swingtrade.data.repository;

import com.swingtrade.data.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {

    Optional<AppSettingEntity> findByKey(String key);

    @Modifying
    @Query("UPDATE AppSettingEntity a SET a.value = :value, a.updatedAt = CURRENT_TIMESTAMP WHERE a.key = :key")
    void updateValue(@Param("key") String key, @Param("value") String value);
}