package com.saarthi.repository;

import com.saarthi.model.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Spring Data JPA Repository for Chamber Telemetry Time-Series.
 */
@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryRecord, Long> {
    
    List<TelemetryRecord> findTop50ByOrderByTimestampDesc();

    List<TelemetryRecord> findTop50ByDeviceIdOrderByTimestampDesc(String deviceId);
    
    List<TelemetryRecord> findByDeviceIdOrderByTimestampDesc(String deviceId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TelemetryRecord t WHERE t.timestamp < :cutoffEpoch")
    int pruneRecordsOlderThan(@Param("cutoffEpoch") long cutoffEpoch);
}
