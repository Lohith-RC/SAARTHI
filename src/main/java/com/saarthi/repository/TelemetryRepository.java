package com.saarthi.repository;

import com.saarthi.model.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Spring Data JPA Repository for Chamber Telemetry Time-Series.
 */
@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryRecord, Long> {
    
    List<TelemetryRecord> findTop50ByOrderByTimestampDesc();
    
    List<TelemetryRecord> findByDeviceIdOrderByTimestampDesc(String deviceId);
}
