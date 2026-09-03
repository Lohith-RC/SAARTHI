package com.saarthi.repository;

import com.saarthi.model.AlertEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for the persistent alert history.
 */
@Repository
public interface AlertRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findAllByOrderByTimestampDesc(Pageable pageable);
}