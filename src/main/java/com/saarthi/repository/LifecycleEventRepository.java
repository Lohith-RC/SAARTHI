package com.saarthi.repository;

import com.saarthi.model.LifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistent persistence for the crop lifecycle ledger.
 */
@Repository
public interface LifecycleEventRepository extends JpaRepository<LifecycleEvent, Long> {

    List<LifecycleEvent> findAllByOrderByCropDayAsc();
}