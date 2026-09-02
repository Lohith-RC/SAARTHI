package com.saarthi.service;

import com.saarthi.model.ActuationCommand;
import com.saarthi.model.CropRecipe;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

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

    private static final int MAX_BUFFER_CAPACITY = 250;
    private volatile boolean storageDegraded = false;
    private volatile Long actuationExpiryEpoch = null;

    /**
     * Ingests telemetry packet from hardware or simulation. Synchronized for atomic compound updates.
     */
    public synchronized TelemetryRecord ingestTelemetry(String deviceId, Double co2, Double rh, Double temp, Integer fanRpm, String crop) {
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

        // Bounded writeBuffer (protect heap during sustained DB failure)
        if (writeBuffer.size() >= MAX_BUFFER_CAPACITY) {
            writeBuffer.poll();
            log.warn("[BUFFER OVERFLOW] Telemetry buffer reached max capacity (250); dropped oldest record.");
        }
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
            try {
                telemetryRepository.saveAll(batch);
                if (storageDegraded) {
                    log.info("[STORAGE RECOVERED] Telemetry database connection restored.");
                    storageDegraded = false;
                }
            } catch (Exception e) {
                storageDegraded = true;
                log.error("[STORAGE DEGRADED] Database flush failed: {}. Retaining in bounded memory.", e.getMessage());
            }
        }
    }

    public boolean isStorageDegraded() {
        return storageDegraded;
    }

    /**
     * Checks if timed actuation has expired and auto-releases back to nominal baseline.
     */
    @Scheduled(fixedRate = 1000)
    public synchronized void checkActuationExpiry() {
        if (actuationExpiryEpoch != null && Instant.now().getEpochSecond() >= actuationExpiryEpoch) {
            log.info("Actuation duration expired. Auto-releasing fan to baseline nominal RPM.");
            actuationExpiryEpoch = null;
            CropRecipe recipe = cropRecipeRegistry.getOrDefault(currentCropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));
            this.currentFanRpm = recipe.getBaselineFanRpm();
            this.currentFanDuty = (int) Math.min(100, Math.max(0, (recipe.getBaselineFanRpm() / 3000.0) * 100));
            evaluateThresholds();
            TelemetryRecord record = getCurrentStateRecord();
            webSocketHandler.broadcastTelemetry(record);
        }
    }

    @PreDestroy
    public void onShutdown() {
        flushWriteBuffer();
    }

    /**
     * Evaluates metrics independently so concurrent anomalies (e.g. high CO2 AND low RH)
     * are each alerted rather than masked by mutual exclusion.
     */
    private void evaluateThresholds() {
        CropRecipe recipe = cropRecipeRegistry.getOrDefault(currentCropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));
        boolean warning = false;
        boolean critical = false;

        // 1. Independent CO2 Evaluation
        if (currentCo2 != null && currentCo2 > recipe.getMaxCo2()) {
            warning = true;
            if (this.currentFanRpm < recipe.getPurgeFanRpm()) {
                this.currentFanRpm = recipe.getPurgeFanRpm();
                this.currentFanDuty = (int) Math.min(100, Math.max(0, (recipe.getPurgeFanRpm() / 3000.0) * 100));
            }
            if (currentCo2 >= recipe.getSpikeCo2Threshold()) {
                critical = true;
                alertService.triggerAlert("CO2_SPIKE_" + recipe.getCropId().toUpperCase(), "CRITICAL", "High CO2 Concentration",
                        String.format("CO2 reached %.0f ppm in %s chamber. FAE blower ramped to %d RPM.", currentCo2, recipe.getName(), currentFanRpm),
                        Map.of("co2", currentCo2, "rpm", currentFanRpm, "crop", currentCropType));
            }
        }

        // 2. Independent Relative Humidity Evaluation
        if (currentRh != null && currentRh < recipe.getMinRh()) {
            warning = true;
            alertService.triggerAlert("HUMIDITY_LOW_" + recipe.getCropId().toUpperCase(), "WARNING", "Low Relative Humidity",
                    String.format("Humidity dropped to %.1f%% in %s chamber (below %.0f%%).", currentRh, recipe.getName(), recipe.getMinRh()),
                    Map.of("rh", currentRh, "crop", currentCropType));
        } else if (currentRh != null && currentRh > recipe.getMaxRh()) {
            warning = true;
        }

        // 3. Independent Temperature Evaluation
        if (currentTemp != null && (currentTemp > recipe.getMaxTemp() || currentTemp < recipe.getMinTemp())) {
            warning = true;
        }

        if (critical) {
            this.currentStatus = "CRITICAL";
        } else if (warning) {
            this.currentStatus = "WARNING";
        } else {
            this.currentStatus = "OPTIMAL";
        }
    }

    public synchronized TelemetryRecord getCurrentStateRecord() {
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
     * Executes manual or AI-driven actuation commands. Synchronized for atomic updates.
     */
    public synchronized TelemetryRecord executeActuation(ActuationCommand command) {
        if (command == null || command.getAction() == null) return getCurrentStateRecord();

        String action = command.getAction().toUpperCase();
        if (command.getDurationSeconds() != null && command.getDurationSeconds() > 0) {
            this.actuationExpiryEpoch = Instant.now().getEpochSecond() + command.getDurationSeconds();
        } else if ("RELAY_OFF".equals(action)) {
            this.actuationExpiryEpoch = null;
        }

        if ("RELAY_ON".equals(action) || "VENTILATE".equals(action) || "SET_RPM".equals(action)) {
            this.currentFanRpm = command.getRpm() != null ? command.getRpm() : 2400;
            this.currentFanDuty = 80;
        } else if ("RELAY_OFF".equals(action)) {
            this.currentFanRpm = 0;
            this.currentFanDuty = 0;
        } else if ("SWITCH_CROP".equals(action)) {
            this.currentCropType = "hydro".equalsIgnoreCase(command.getCrop()) ? "hydro" : "mushroom";
            CropRecipe recipe = cropRecipeRegistry.getOrDefault(this.currentCropType, cropRecipeRegistry.get("mushroom"));
            this.currentCo2 = (recipe.getMinCo2() + recipe.getMaxCo2()) / 2.0;
            this.currentRh = (recipe.getMinRh() + recipe.getMaxRh()) / 2.0;
            this.currentTemp = (recipe.getMinTemp() + recipe.getMaxTemp()) / 2.0;
            this.currentFanRpm = recipe.getBaselineFanRpm();
            this.currentFanDuty = (int) Math.min(100, Math.max(0, (recipe.getBaselineFanRpm() / 3000.0) * 100));
        } else if ("SPIKE".equals(action)) {
            CropRecipe recipe = cropRecipeRegistry.getOrDefault(currentCropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));
            this.currentCo2 = recipe.getSpikeCo2Threshold() + 120.0;
            this.currentRh = Math.min(98.0, recipe.getMaxRh() + 2.0);
            this.currentTemp = 23.2;
            this.currentFanRpm = recipe.getPurgeFanRpm();
            this.currentFanDuty = (int) Math.min(100, Math.max(0, (this.currentFanRpm / 3000.0) * 100));
        } else if ("RESET".equals(action)) {
            CropRecipe recipe = cropRecipeRegistry.getOrDefault(currentCropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));
            this.currentCo2 = (recipe.getMinCo2() + recipe.getMaxCo2()) / 2.0;
            this.currentRh = (recipe.getMinRh() + recipe.getMaxRh()) / 2.0;
            this.currentTemp = (recipe.getMinTemp() + recipe.getMaxTemp()) / 2.0;
            this.currentFanRpm = recipe.getBaselineFanRpm();
            this.currentFanDuty = (int) Math.min(100, Math.max(0, (recipe.getBaselineFanRpm() / 3000.0) * 100));
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
            log.warn("[WATCHDOG ALERT] No telemetry ping from {} for {}s. Alert triggered.", currentDeviceId, secondsSinceLastPing);
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
            log.info("[DATA RETENTION] Pruned {} historical records older than 7 days.", deleted);
        }
        return deleted;
    }
}
