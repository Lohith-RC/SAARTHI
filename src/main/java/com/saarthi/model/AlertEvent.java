package com.saarthi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent record of a dispatched emergency alert. Survives restarts so the
 * operator can audit every watchdog / threshold / manual alert.
 */
@Entity
@Table(name = "alert_event")
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_key", nullable = false, length = 96)
    private String alertKey;

    @Column(name = "level", nullable = false, length = 16)
    private String level;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "details_json", length = 2000)
    private String detailsJson;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    public AlertEvent() {
    }

    public AlertEvent(String alertKey, String level, String title, String message, String detailsJson, Long timestamp) {
        this.alertKey = alertKey;
        this.level = level;
        this.title = title;
        this.message = message;
        this.detailsJson = detailsJson;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAlertKey() { return alertKey; }
    public void setAlertKey(String alertKey) { this.alertKey = alertKey; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}