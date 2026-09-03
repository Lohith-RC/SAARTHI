package com.saarthi.service;

import com.saarthi.model.ActuationCommand;
import com.saarthi.model.CropRecipe;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Core Telemetry Service managing the multi-chamber fleet: live per-chamber
 * state, threshold evaluation, WebSocket broadcasting, high-performance batch
 * database persistence, timed actuation, and the per-chamber Dead-Man's Watchdog.
 *
 * <p>The default chamber {@value #DEFAULT_DEVICE_ID} is always seeded so legacy
 * single-chamber clients and endpoints keep their exact behavior.</p>
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    /** Legacy default chamber, always present so single-chamber behavior is preserved. */
    public static final String DEFAULT_DEVICE_ID = "SAARTHI_001";

    /** Hard fleet cap: guards against unbounded chamber creation (memory DoS). */
    private static final int MAX_CHAMBERS = 64;

    private final TelemetryRepository telemetryRepository;
    private final TelemetryWebSocketHandler webSocketHandler;
    private final AlertService alertService;
    private final long watchdogTimeoutSeconds;

    // High-Throughput In-Memory Write Buffer (eliminates single-row synchronous disk locks)
    private final ConcurrentLinkedQueue<TelemetryRecord> writeBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, CropRecipe> cropRecipeRegistry = new ConcurrentHashMap<>();

    // Multi-Chamber Fleet State (keyed by deviceId)
    private final Map<String, ChamberState> chambers = new ConcurrentHashMap<>();

    private static final int MAX_BUFFER_CAPACITY = 250;
    private volatile boolean storageDegraded = false;

    @Autowired
    public TelemetryService(TelemetryRepository telemetryRepository,
                            TelemetryWebSocketHandler webSocketHandler,
                            AlertService alertService,
                            @Value("${saarthi.watchdog.timeout-seconds:180}") long watchdogTimeoutSeconds) {
        this.telemetryRepository = telemetryRepository;
        this.webSocketHandler = webSocketHandler;
        this.alertService = alertService;
        this.watchdogTimeoutSeconds = watchdogTimeoutSeconds;

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

        // Seed the legacy default chamber so single-chamber callers keep working.
        chambers.put(DEFAULT_DEVICE_ID, new ChamberState(DEFAULT_DEVICE_ID, "mushroom"));
    }

    /**
     * Convenience constructor for unit tests (uses the default 180s watchdog timeout).
     */
    public TelemetryService(TelemetryRepository telemetryRepository,
                            TelemetryWebSocketHandler webSocketHandler,
                            AlertService alertService) {
        this(telemetryRepository, webSocketHandler, alertService, 180);
    }

    @PostConstruct
    void wireFleetSnapshotSupplier() {
        // No constructor this-escape: the handler only stores the reference and
        // invokes it after the context is up (on WebSocket connect).
        webSocketHandler.setFleetSnapshotSupplier(this::getFleetSnapshot);
    }

    // ------------------------------------------------------------------
    // Chamber resolution & record conversion
    // ------------------------------------------------------------------

    /**
     * Resolves (and lazily creates) the chamber for a deviceId. A null/blank id
     * targets the default chamber; unknown ids beyond the fleet cap route to the
     * default chamber instead of growing the map unboundedly.
     */
    private ChamberState resolveChamber(String deviceId) {
        String id = normalizeDeviceId(deviceId);
        ChamberState existing = chambers.get(id);
        if (existing != null) return existing;
        if (chambers.size() >= MAX_CHAMBERS) {
            log.warn("[FLEET CAP] Chamber fleet at capacity ({}); routing {} to default chamber.", MAX_CHAMBERS, id);
            return chambers.get(DEFAULT_DEVICE_ID);
        }
        ChamberState created = new ChamberState(id, "mushroom");
        ChamberState raced = chambers.putIfAbsent(id, created);
        return raced != null ? raced : created;
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return DEFAULT_DEVICE_ID;
        String trimmed = deviceId.trim();
        if (trimmed.length() > 64 || !trimmed.matches("^[a-zA-Z0-9_\\-\\.]+$")) {
            throw new IllegalArgumentException("deviceId must be alphanumeric (dashes/dots/underscores allowed, max 64 characters)");
        }
        return trimmed;
    }

    private CropRecipe recipeFor(String cropType) {
        return cropRecipeRegistry.getOrDefault(cropType == null ? "" : cropType.toLowerCase(), cropRecipeRegistry.get("mushroom"));
    }

    private static int dutyFor(int rpm) {
        return (int) Math.min(100, Math.max(0, (rpm / 3000.0) * 100));
    }

    private static TelemetryRecord toRecord(ChamberState chamber) {
        return new TelemetryRecord(
                chamber.deviceId,
                chamber.co2Ppm,
                chamber.humidityRh,
                chamber.tempC,
                chamber.fanRpm,
                chamber.fanDuty,
                chamber.cropType,
                chamber.status
        );
    }

    private void broadcastFor(ChamberState chamber, TelemetryRecord record) {
        // Backwards-compatible raw record for the default chamber (legacy HUDs),
        // plus the typed fleet message for every chamber.
        if (DEFAULT_DEVICE_ID.equals(chamber.deviceId)) {
            webSocketHandler.broadcastTelemetry(record);
        }
        webSocketHandler.broadcastChamberUpdate(chamber.deviceId, record);
    }

    // ------------------------------------------------------------------
    // Telemetry ingestion
    // ------------------------------------------------------------------

    /**
     * Ingests telemetry packet from hardware or simulation. Synchronized for
     * atomic compound updates across the fleet.
     */
    public synchronized TelemetryRecord ingestTelemetry(String deviceId, Double co2, Double rh, Double temp, Integer fanRpm, String crop) {
        ChamberState chamber = resolveChamber(deviceId);
        chamber.lastHeartbeat = Instant.now().getEpochSecond();
        if (co2 != null) chamber.co2Ppm = co2;
        if (rh != null) chamber.humidityRh = rh;
        if (temp != null) chamber.tempC = temp;
        if (fanRpm != null) {
            chamber.fanRpm = fanRpm;
            chamber.fanDuty = dutyFor(fanRpm);
        }
        if (crop != null && !crop.isBlank()) chamber.cropType = crop.toLowerCase();

        // Anomaly & Threshold Evaluation
        evaluateThresholds(chamber);
        chamber.dirty = true;

        TelemetryRecord record = toRecord(chamber);

        // Broadcast immediately to WebSocket clients (zero latency)
        broadcastFor(chamber, record);

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
     * Checks timed actuations across all chambers; auto-releases expired ones
     * back to their nominal baseline.
     */
    @Scheduled(fixedRate = 1000)
    public synchronized void checkActuationExpiry() {
        long now = Instant.now().getEpochSecond();
        for (ChamberState chamber : chambers.values()) {
            if (chamber.actuationExpiryEpoch != null && now >= chamber.actuationExpiryEpoch) {
                log.info("Actuation duration expired for {}. Auto-releasing fan to baseline nominal RPM.", chamber.deviceId);
                chamber.actuationExpiryEpoch = null;
                CropRecipe recipe = recipeFor(chamber.cropType);
                chamber.fanRpm = recipe.getBaselineFanRpm();
                chamber.fanDuty = dutyFor(recipe.getBaselineFanRpm());
                chamber.dirty = true;
                evaluateThresholds(chamber);
                broadcastFor(chamber, toRecord(chamber));
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        flushWriteBuffer();
    }

    /**
     * Evaluates metrics independently so concurrent anomalies (e.g. high CO2 AND low RH)
     * are each alerted rather than masked by mutual exclusion. Alert keys are
     * suffixed with the chamber deviceId so rate limiting never suppresses a
     * second chamber's alert within the same window.
     */
    private void evaluateThresholds(ChamberState chamber) {
        CropRecipe recipe = recipeFor(chamber.cropType);
        boolean warning = false;
        boolean critical = false;

        // 1. Independent CO2 Evaluation
        if (chamber.co2Ppm != null && chamber.co2Ppm > recipe.getMaxCo2()) {
            warning = true;
            if (chamber.fanRpm == null || chamber.fanRpm < recipe.getPurgeFanRpm()) {
                chamber.fanRpm = recipe.getPurgeFanRpm();
                chamber.fanDuty = dutyFor(chamber.fanRpm);
            }
            if (chamber.co2Ppm >= recipe.getSpikeCo2Threshold()) {
                critical = true;
                alertService.triggerAlert("CO2_SPIKE_" + recipe.getCropId().toUpperCase() + "_" + chamber.deviceId,
                        "CRITICAL", "High CO2 Concentration",
                        String.format("CO2 reached %.0f ppm in %s chamber (%s). FAE blower ramped to %d RPM.",
                                chamber.co2Ppm, recipe.getName(), chamber.deviceId, chamber.fanRpm),
                        Map.of("deviceId", chamber.deviceId, "co2", chamber.co2Ppm, "rpm", chamber.fanRpm, "crop", chamber.cropType));
            }
        }

        // 2. Independent Relative Humidity Evaluation
        if (chamber.humidityRh != null && chamber.humidityRh < recipe.getMinRh()) {
            warning = true;
            alertService.triggerAlert("HUMIDITY_LOW_" + recipe.getCropId().toUpperCase() + "_" + chamber.deviceId,
                    "WARNING", "Low Relative Humidity",
                    String.format("Humidity dropped to %.1f%% in %s chamber (%s) (below %.0f%%).",
                            chamber.humidityRh, recipe.getName(), chamber.deviceId, recipe.getMinRh()),
                    Map.of("deviceId", chamber.deviceId, "rh", chamber.humidityRh, "crop", chamber.cropType));
        } else if (chamber.humidityRh != null && chamber.humidityRh > recipe.getMaxRh()) {
            warning = true;
        }

        // 3. Independent Temperature Evaluation
        if (chamber.tempC != null && (chamber.tempC > recipe.getMaxTemp() || chamber.tempC < recipe.getMinTemp())) {
            warning = true;
        }

        if (critical) {
            chamber.status = "CRITICAL";
        } else if (warning) {
            chamber.status = "WARNING";
        } else {
            chamber.status = "OPTIMAL";
        }
    }

    // ------------------------------------------------------------------
    // Read endpoints
    // ------------------------------------------------------------------

    /**
     * Current state of the default chamber (legacy single-chamber contract).
     */
    public synchronized TelemetryRecord getCurrentStateRecord() {
        return toRecord(chambers.get(DEFAULT_DEVICE_ID));
    }

    /**
     * Current state of a specific chamber, or {@code null} when unknown.
     */
    public synchronized TelemetryRecord getChamberStateRecord(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return getCurrentStateRecord();
        ChamberState chamber = chambers.get(normalizeDeviceId(deviceId));
        return chamber != null ? toRecord(chamber) : null;
    }

    /**
     * Full fleet snapshot (all chambers, sorted by deviceId).
     */
    public synchronized List<TelemetryRecord> getFleetSnapshot() {
        List<TelemetryRecord> snapshot = new ArrayList<>(chambers.size());
        for (ChamberState chamber : chambers.values()) {
            snapshot.add(toRecord(chamber));
        }
        snapshot.sort(Comparator.comparing(TelemetryRecord::getDeviceId));
        return snapshot;
    }

    /**
     * Recent persisted history (legacy: across all devices).
     */
    public List<TelemetryRecord> getRecentHistory() {
        return telemetryRepository.findTop50ByOrderByTimestampDesc();
    }

    /**
     * Recent persisted history for a single chamber.
     */
    public List<TelemetryRecord> getRecentHistory(String deviceId) {
        return telemetryRepository.findTop50ByDeviceIdOrderByTimestampDesc(normalizeDeviceId(deviceId));
    }

    // ------------------------------------------------------------------
    // Actuation
    // ------------------------------------------------------------------

    /**
     * Executes manual or AI-driven actuation commands against a chamber
     * (defaults to {@value #DEFAULT_DEVICE_ID} for backwards compatibility).
     * Synchronized for atomic updates.
     */
    public synchronized TelemetryRecord executeActuation(ActuationCommand command) {
        if (command == null || command.getAction() == null) return getCurrentStateRecord();

        ChamberState chamber = resolveChamber(command.getDeviceId());
        String action = command.getAction().toUpperCase();
        if (command.getDurationSeconds() != null && command.getDurationSeconds() > 0) {
            chamber.actuationExpiryEpoch = Instant.now().getEpochSecond() + command.getDurationSeconds();
        } else if ("RELAY_OFF".equals(action)) {
            chamber.actuationExpiryEpoch = null;
        }

        if ("RELAY_ON".equals(action) || "VENTILATE".equals(action) || "SET_RPM".equals(action)) {
            chamber.fanRpm = command.getRpm() != null ? command.getRpm() : 2400;
            chamber.fanDuty = 80;
        } else if ("RELAY_OFF".equals(action)) {
            chamber.fanRpm = 0;
            chamber.fanDuty = 0;
        } else if ("SWITCH_CROP".equals(action)) {
            chamber.cropType = "hydro".equalsIgnoreCase(command.getCrop()) ? "hydro" : "mushroom";
            CropRecipe recipe = recipeFor(chamber.cropType);
            chamber.co2Ppm = (recipe.getMinCo2() + recipe.getMaxCo2()) / 2.0;
            chamber.humidityRh = (recipe.getMinRh() + recipe.getMaxRh()) / 2.0;
            chamber.tempC = (recipe.getMinTemp() + recipe.getMaxTemp()) / 2.0;
            chamber.fanRpm = recipe.getBaselineFanRpm();
            chamber.fanDuty = dutyFor(recipe.getBaselineFanRpm());
        } else if ("SPIKE".equals(action)) {
            CropRecipe recipe = recipeFor(chamber.cropType);
            chamber.co2Ppm = recipe.getSpikeCo2Threshold() + 120.0;
            chamber.humidityRh = Math.min(98.0, recipe.getMaxRh() + 2.0);
            chamber.tempC = 23.2;
            chamber.fanRpm = recipe.getPurgeFanRpm();
            chamber.fanDuty = dutyFor(chamber.fanRpm);
        } else if ("RESET".equals(action)) {
            CropRecipe recipe = recipeFor(chamber.cropType);
            chamber.co2Ppm = (recipe.getMinCo2() + recipe.getMaxCo2()) / 2.0;
            chamber.humidityRh = (recipe.getMinRh() + recipe.getMaxRh()) / 2.0;
            chamber.tempC = (recipe.getMinTemp() + recipe.getMaxTemp()) / 2.0;
            chamber.fanRpm = recipe.getBaselineFanRpm();
            chamber.fanDuty = dutyFor(recipe.getBaselineFanRpm());
        }

        chamber.dirty = true;
        evaluateThresholds(chamber);
        TelemetryRecord record = toRecord(chamber);
        broadcastFor(chamber, record);
        telemetryRepository.save(record);
        return record;
    }

    // ------------------------------------------------------------------
    // Dead-Man's Watchdog & retention
    // ------------------------------------------------------------------

    /**
     * Dead-Man's Watchdog: checks every 60 seconds if hardware pings are active,
     * independently per chamber. Heartbeat records are only persisted when a
     * chamber's state actually changed since the previous run (no write spam).
     */
    @Scheduled(fixedRate = 60000)
    public synchronized void runDeadMansWatchdog() {
        long now = Instant.now().getEpochSecond();
        for (ChamberState chamber : chambers.values()) {
            long secondsSinceLastPing = now - chamber.lastHeartbeat;
            if (secondsSinceLastPing >= watchdogTimeoutSeconds) {
                log.warn("[WATCHDOG ALERT] No telemetry ping from {} for {}s. Alert triggered.", chamber.deviceId, secondsSinceLastPing);
                alertService.triggerAlert("WATCHDOG_TIMEOUT_" + chamber.deviceId, "WARNING", "Hardware Node Offline",
                        String.format("No telemetry ping from node %s for %d seconds. Check Wi-Fi or power.", chamber.deviceId, secondsSinceLastPing),
                        Map.of("deviceId", chamber.deviceId, "elapsedSeconds", secondsSinceLastPing));
            } else if (chamber.dirty) {
                telemetryRepository.save(toRecord(chamber));
                chamber.dirty = false;
            }
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

    /**
     * Mutable per-chamber state holder. All field access happens under the
     * service lock, so plain volatile fields are sufficient.
     */
    static final class ChamberState {
        final String deviceId;
        volatile Double co2Ppm = 845.0;
        volatile Double humidityRh = 92.0;
        volatile Double tempC = 22.4;
        volatile Integer fanRpm = 1420;
        volatile Integer fanDuty = 45;
        volatile String cropType;
        volatile String status = "OPTIMAL";
        volatile long lastHeartbeat;
        volatile Long actuationExpiryEpoch = null;
        volatile boolean dirty = true;

        ChamberState(String deviceId, String cropType) {
            this.deviceId = deviceId;
            this.cropType = (cropType != null && !cropType.isBlank()) ? cropType.toLowerCase() : "mushroom";
            this.lastHeartbeat = Instant.now().getEpochSecond();
        }
    }
}