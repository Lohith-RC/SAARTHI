package com.saarthi.service;

import com.saarthi.model.ActuationCommand;
import com.saarthi.model.CropRecipe;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Core Telemetry Service managing live in-memory state, threshold evaluation,
 * WebSocket broadcasting, high-performance batch database persistence, and the 60-second Dead-Man's Watchdog.
 */
@Service
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final TelemetryWebSocketHandler webSocketHandler;
    private final AlertService alertService;

    // High-Throughput In-Memory Write Buffer (eliminates single-row synchronous disk locks)
    private final ConcurrentLinkedQueue<TelemetryRecord> writeBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, CropRecipe> cropRecipeRegistry = new ConcurrentHashMap<>();

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

    public TelemetryService(TelemetryRepository telemetryRepository, TelemetryWebSocketHandler webSocketHandler, AlertService alertService) {
        this.telemetryRepository = telemetryRepository;
        this.webSocketHandler = webSocketHandler;
        this.alertService = alertService;

        // Initialize Centralized Declarative Crop Recipes
        cropRecipeRegistry.put("mushroom", new CropRecipe(
                "mushroom", "Oyster / Button Mushroom Fruiting",
                600.0, 1300.0, 1400.0,
                75.0, 95.0, 15.0, 28.0,
                1420, 2400
        ));
        cropRecipeRegistry.put("hydro", new CropRecipe(
                "hydro", "Hydroponic Leafy Greens & Basil",
                500.0, 1600.0, 1700.0,
                45.0, 85.0, 16.0, 28.0,
                1100, 2000
        ));
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
        
        // Broadcast immediately to WebSocket clients (zero latency)
        webSocketHandler.broadcastTelemetry(record);

        // Buffer record for batched disk persistence
        writeBuffer.offer(record);
        if (writeBuffer.size() >= 50) {
            flushWriteBuffer();
        }

        return record;
    }

    /**
     * High-Throughput Batch Flush: Persists buffered telemetry records in atomic batches every 3 seconds.
     */
    @Scheduled(fixedRate = 3000)
    public synchronized void flushWriteBuffer() {
        if (writeBuffer.isEmpty()) return;

        List<TelemetryRecord> batch = new ArrayList<>();
        TelemetryRecord rec;
        while ((rec = writeBuffer.poll()) != null && batch.size() < 100) {
            batch.add(rec);
        }

        if (!batch.isEmpty()) {
            telemetryRepository.saveAll(batch);
        }
    }

    @PreDestroy
    public void onShutdown() {
        flushWriteBuffer();
    }

    private void evaluateThresholds() {
        CropRecipe recipe = cropRecipeRegistry.getOrDefault(currentCropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));

        if (currentCo2 > recipe.getMaxCo2()) {
            this.currentStatus = "WARNING";
            if (this.currentFanRpm < recipe.getPurgeFanRpm()) {
                this.currentFanRpm = recipe.getPurgeFanRpm();
                this.currentFanDuty = (int) Math.min(100, Math.max(0, (recipe.getPurgeFanRpm() / 3000.0) * 100));
            }
            if (currentCo2 >= recipe.getSpikeCo2Threshold()) {
                alertService.triggerAlert("CO2_SPIKE_" + recipe.getCropId().toUpperCase(), "CRITICAL", "High CO2 Concentration",
                        String.format("CO2 reached %.0f ppm in %s chamber. FAE blower ramped to %d RPM.", currentCo2, recipe.getName(), currentFanRpm),
                        Map.of("co2", currentCo2, "rpm", currentFanRpm, "crop", currentCropType));
            }
        } else if (currentRh < recipe.getMinRh()) {
            this.currentStatus = "WARNING";
            alertService.triggerAlert("HUMIDITY_LOW_" + recipe.getCropId().toUpperCase(), "WARNING", "Low Relative Humidity",
                    String.format("Humidity dropped to %.1f%% in %s chamber (below %.0f%%).", currentRh, recipe.getName(), recipe.getMinRh()),
                    Map.of("rh", currentRh, "crop", currentCropType));
        } else if (currentTemp > recipe.getMaxTemp() || currentTemp < recipe.getMinTemp()) {
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
            alertService.triggerAlert("WATCHDOG_TIMEOUT", "WARNING", "Hardware Node Offline",
                    String.format("No telemetry ping from node %s for %d seconds. Check Wi-Fi or power.", currentDeviceId, secondsSinceLastPing),
                    Map.of("deviceId", currentDeviceId, "elapsedSeconds", secondsSinceLastPing));
        } else {
            // Heartbeat persistence
            telemetryRepository.save(getCurrentStateRecord());
        }
    }

    /**
     * Automated Data Retention Policy: Prunes raw telemetry records older than 7 days daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public int runDataRetentionPruning() {
        long cutoffEpoch = Instant.now().getEpochSecond() - (7 * 86400L); // 7 days
        int deleted = telemetryRepository.pruneRecordsOlderThan(cutoffEpoch);
        if (deleted > 0) {
            System.out.printf("🧹 [DATA RETENTION] Pruned %d historical records older than 7 days.%n", deleted);
        }
        return deleted;
    }
}
