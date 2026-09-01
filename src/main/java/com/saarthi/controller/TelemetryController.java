package com.saarthi.controller;

import com.saarthi.model.TelemetryRecord;
import com.saarthi.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Spring REST Controller for Hardware & Client Telemetry Ingestion and History.
 */
@RestController
@RequestMapping("/api/v1/telemetry")
@CrossOrigin(origins = "*")
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
    public ResponseEntity<?> pushTelemetry(@RequestBody Map<String, Object> payload,
                                           @RequestHeader(value = "X-Device-Token", required = false) String token) {
        String deviceId = (String) payload.getOrDefault("deviceId", "SAARTHI_001");
        Double co2 = payload.containsKey("co2Ppm") ? Double.valueOf(payload.get("co2Ppm").toString()) : null;
        Double rh = payload.containsKey("humidityRh") ? Double.valueOf(payload.get("humidityRh").toString()) : null;
        Double temp = payload.containsKey("tempC") ? Double.valueOf(payload.get("tempC").toString()) : null;
        Integer fanRpm = payload.containsKey("fanRpm") ? Integer.valueOf(payload.get("fanRpm").toString()) : null;
        String crop = (String) payload.get("cropType");

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
     * Historical telemetry logs for analytics.
     * GET /api/v1/telemetry/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<TelemetryRecord>> getTelemetryHistory() {
        return ResponseEntity.ok(telemetryService.getRecentHistory());
    }
}
