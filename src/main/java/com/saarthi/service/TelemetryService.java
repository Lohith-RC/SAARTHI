package com.saarthi.service;

import com.saarthi.model.ActuationCommand;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Core Telemetry Service managing live in-memory state, threshold evaluation,
 * WebSocket broadcasting, JPA database logging, and the 60-second Dead-Man's Watchdog.
 */
@Service
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final TelemetryWebSocketHandler webSocketHandler;

    // Current In-Memory Chamber State
    private volatile String currentDeviceId = "SAARTHI_001";
    private volatile Double currentCo2 = 845.0;
    private volatile Double currentRh = 92.0;
    private volatile Double currentTemp = 22.4;
    private volatile Integer currentFanRpm = 1420;
    private volatile Integer currentFanDuty = 45;
    private volatile String currentCropType = "mushroom"; // 'mushroom' or 'hydro'
    private volatile String currentStatus = "OPTIMAL";
    private volatile long lastHeartbeat = Instant.now().getEpochSecond();

    public TelemetryService(TelemetryRepository telemetryRepository, TelemetryWebSocketHandler webSocketHandler) {
        this.telemetryRepository = telemetryRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Ingests telemetry packet from hardware or simulation.
     */
    public TelemetryRecord ingestTelemetry(String deviceId, Double co2, Double rh, Double temp, Integer fanRpm, String crop) {
        this.currentDeviceId = deviceId != null ? deviceId : this.currentDeviceId;
        if (co2 != null) this.currentCo2 = co2;
        if (rh != null) this.currentRh = rh;
        if (temp != null) this.currentTemp = temp;
        if (fanRpm != null) {
            this.currentFanRpm = fanRpm;
            this.currentFanDuty = (int) Math.min(100, Math.max(0, (fanRpm / 3000.0) * 100));
        }
        if (crop != null) this.currentCropType = crop;
        this.lastHeartbeat = Instant.now().getEpochSecond();

        // Anomaly & Threshold Evaluation
        evaluateThresholds();

        TelemetryRecord record = getCurrentStateRecord();
        
        // Broadcast to WebSocket clients
        webSocketHandler.broadcastTelemetry(record);

        // Persist snapshot to JPA H2 Database
        telemetryRepository.save(record);

        return record;
    }

    private void evaluateThresholds() {
        if (currentCo2 > 1300.0) {
            this.currentStatus = "WARNING";
            // Auto ramp exhaust blower if CO2 is critical
            if (this.currentFanRpm < 2000) {
                this.currentFanRpm = 2400;
                this.currentFanDuty = 80;
            }
        } else if (currentRh < 75.0 && "mushroom".equalsIgnoreCase(currentCropType)) {
            this.currentStatus = "WARNING";
        } else {
            this.currentStatus = "OPTIMAL";
        }
    }

    public TelemetryRecord getCurrentStateRecord() {
        return new TelemetryRecord(
                currentDeviceId,
                currentCo2,
                currentRh,
                currentTemp,
                currentFanRpm,
                currentFanDuty,
                currentCropType,
                currentStatus
        );
    }

    public List<TelemetryRecord> getRecentHistory() {
        return telemetryRepository.findTop50ByOrderByTimestampDesc();
    }

    /**
     * Executes manual or AI-driven actuation commands.
     */
    public TelemetryRecord executeActuation(ActuationCommand command) {
        if (command == null || command.getAction() == null) return getCurrentStateRecord();

        String action = command.getAction().toUpperCase();
        if ("RELAY_ON".equals(action) || "VENTILATE".equals(action) || "SET_RPM".equals(action)) {
            this.currentFanRpm = command.getRpm() != null ? command.getRpm() : 2400;
            this.currentFanDuty = 80;
        } else if ("RELAY_OFF".equals(action)) {
            this.currentFanRpm = 0;
            this.currentFanDuty = 0;
        } else if ("SWITCH_CROP".equals(action)) {
            this.currentCropType = "hydro".equalsIgnoreCase(command.getCrop()) ? "hydro" : "mushroom";
            if ("mushroom".equals(this.currentCropType)) {
                this.currentCo2 = 845.0;
                this.currentRh = 92.0;
                this.currentTemp = 22.4;
                this.currentFanRpm = 1420;
            } else {
                this.currentCo2 = 1100.0;
                this.currentRh = 68.0;
                this.currentTemp = 20.8;
                this.currentFanRpm = 1100;
            }
        } else if ("SPIKE".equals(action)) {
            this.currentCo2 = 1520.0;
            this.currentRh = 94.0;
            this.currentTemp = 23.2;
            this.currentFanRpm = 2800;
        } else if ("RESET".equals(action)) {
            this.currentCo2 = 845.0;
            this.currentRh = 92.0;
            this.currentTemp = 22.4;
            this.currentFanRpm = 1420;
        }

        evaluateThresholds();
        TelemetryRecord record = getCurrentStateRecord();
        webSocketHandler.broadcastTelemetry(record);
        telemetryRepository.save(record);
        return record;
    }

    /**
     * Dead-Man's Watchdog: Checks every 60 seconds if hardware pings are active.
     */
    @Scheduled(fixedRate = 60000)
    public void runDeadMansWatchdog() {
        long secondsSinceLastPing = Instant.now().getEpochSecond() - lastHeartbeat;
        if (secondsSinceLastPing > 180) {
            System.err.println("🚨 [WATCHDOG ALERT] No telemetry ping from " + currentDeviceId + " for " + secondsSinceLastPing + "s! Alert triggered.");
        } else {
            // Heartbeat persistence
            telemetryRepository.save(getCurrentStateRecord());
        }
    }
}
