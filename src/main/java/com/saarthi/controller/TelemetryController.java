package com.saarthi.controller;

import com.saarthi.model.TelemetryRecord;
import com.saarthi.service.TelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Spring REST Controller for Hardware & Client Telemetry Ingestion and History.
 * NOTE: Authentication for this endpoint is enforced by the Spring Security device
 * filter. The controller itself performs no token checks (defense used to live here).
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * Ingestion endpoint for ESP32 hardware and sensor nodes.
     * POST /api/v1/telemetry/push
     */
    @PostMapping("/push")
    public ResponseEntity<?> pushTelemetry(@RequestBody Map<String, Object> payload) {

        String deviceId = (String) payload.getOrDefault("deviceId", "SAARTHI_001");
        if (deviceId != null) {
            deviceId = deviceId.trim();
            if (deviceId.length() > 64 || !deviceId.matches("^[a-zA-Z0-9_\\-\\.]+$")) {
                throw new IllegalArgumentException("deviceId must be alphanumeric (dashes/dots/underscores allowed, max 64 characters)");
            }
        }

        Double co2 = toDouble(payload.get("co2Ppm"));
        Double rh = toDouble(payload.get("humidityRh"));
        Double temp = toDouble(payload.get("tempC"));
        Integer fanRpm = toInteger(payload.get("fanRpm"));
        String crop = (String) payload.get("cropType");

        // Physical range sanity checks (defense-in-depth; fails fast on bad data)
        if (co2 != null && (co2 < 0 || co2 > 10000)) {
            throw new IllegalArgumentException("co2Ppm out of range: " + co2);
        }
        if (rh != null && (rh < 0 || rh > 100)) {
            throw new IllegalArgumentException("humidityRh out of range: " + rh);
        }
        if (temp != null && (temp < -40 || temp > 80)) {
            throw new IllegalArgumentException("tempC out of range: " + temp);
        }
        if (fanRpm != null && (fanRpm < 0 || fanRpm > 3000)) {
            throw new IllegalArgumentException("fanRpm out of range: " + fanRpm);
        }

        TelemetryRecord record = telemetryService.ingestTelemetry(deviceId, co2, rh, temp, fanRpm, crop);

        return ResponseEntity.ok(Map.of(
                "status", "ACK",
                "deviceId", record.getDeviceId(),
                "chamberStatus", record.getStatus(),
                "timestamp", record.getTimestamp()
        ));
    }

    /**
     * Live snapshot endpoint.
     * GET /api/v1/telemetry/current
     */
    @GetMapping("/current")
    public ResponseEntity<TelemetryRecord> getCurrentTelemetry() {
        return ResponseEntity.ok(telemetryService.getCurrentStateRecord());
    }

    /**
     * Fleet snapshot: every chamber's live state.
     * GET /api/v1/telemetry/chambers
     */
    @GetMapping("/chambers")
    public ResponseEntity<List<TelemetryRecord>> getFleetSnapshot() {
        return ResponseEntity.ok(telemetryService.getFleetSnapshot());
    }

    /**
     * Single chamber live state.
     * GET /api/v1/telemetry/chambers/{deviceId}
     */
    @GetMapping("/chambers/{deviceId}")
    public ResponseEntity<TelemetryRecord> getChamberTelemetry(@PathVariable String deviceId) {
        TelemetryRecord record = telemetryService.getChamberStateRecord(deviceId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    /**
     * Historical telemetry logs for analytics.
     * GET /api/v1/telemetry/history?deviceId=...
     */
    @GetMapping("/history")
    public ResponseEntity<List<TelemetryRecord>> getTelemetryHistory(@RequestParam(required = false) String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return ResponseEntity.ok(telemetryService.getRecentHistory(deviceId));
        }
        return ResponseEntity.ok(telemetryService.getRecentHistory());
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value);
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value);
        }
    }
}
