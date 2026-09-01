package com.saarthi.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA Entity & DTO for Chamber Telemetry Time-Series Records.
 */
@Entity
@Table(name = "chamber_telemetry")
public class TelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private Double co2Ppm;
    private Double humidityRh;
    private Double tempC;
    private Integer fanRpm;
    private Integer fanDuty;
    private String cropType; // 'mushroom' or 'hydro'
    private String status;   // 'OPTIMAL', 'WARNING', 'CRITICAL'
    private Long timestamp;

    public TelemetryRecord() {
        this.timestamp = Instant.now().getEpochSecond();
    }

    public TelemetryRecord(String deviceId, Double co2Ppm, Double humidityRh, Double tempC, 
                           Integer fanRpm, Integer fanDuty, String cropType, String status) {
        this.deviceId = deviceId;
        this.co2Ppm = co2Ppm;
        this.humidityRh = humidityRh;
        this.tempC = tempC;
        this.fanRpm = fanRpm;
        this.fanDuty = fanDuty;
        this.cropType = cropType;
        this.status = status;
        this.timestamp = Instant.now().getEpochSecond();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Double getCo2Ppm() { return co2Ppm; }
    public void setCo2Ppm(Double co2Ppm) { this.co2Ppm = co2Ppm; }

    public Double getHumidityRh() { return humidityRh; }
    public void setHumidityRh(Double humidityRh) { this.humidityRh = humidityRh; }

    public Double getTempC() { return tempC; }
    public void setTempC(Double tempC) { this.tempC = tempC; }

    public Integer getFanRpm() { return fanRpm; }
    public void setFanRpm(Integer fanRpm) { this.fanRpm = fanRpm; }

    public Integer getFanDuty() { return fanDuty; }
    public void setFanDuty(Integer fanDuty) { this.fanDuty = fanDuty; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
