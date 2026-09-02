package com.saarthi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent record of a crop lifecycle milestone in the agronomy ledger.
 * Replaces the previous purely in-memory {@literal OpenJarvis} memory trace so
 * milestones survive restarts.
 */
@Entity
@Table(name = "lifecycle_event")
public class LifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crop_day")
    private Integer cropDay;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "description")
    private String description;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "recorded_at")
    private Long recordedAt;

    public LifecycleEvent() {
        this.recordedAt = Instant.now().getEpochSecond();
    }

    public LifecycleEvent(Integer cropDay, String eventType, String description, String healthStatus) {
        this.cropDay = cropDay;
        this.eventType = eventType;
        this.description = description;
        this.healthStatus = healthStatus;
        this.recordedAt = Instant.now().getEpochSecond();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getCropDay() { return cropDay; }
    public void setCropDay(Integer cropDay) { this.cropDay = cropDay; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public Long getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Long recordedAt) { this.recordedAt = recordedAt; }
}