package com.saarthi.controller;

import com.saarthi.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Spring REST Controller for Emergency Outbound Alert Records & Test Dispatches.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * GET /api/v1/alerts/recent
     * Returns the latest emergency alerts dispatched by Watchdog or Threshold engines.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentAlerts() {
        return ResponseEntity.ok(alertService.getRecentAlerts());
    }

    /**
     * POST /api/v1/alerts/test
     * Manually trigger a test alert to verify webhook integration.
     */
    @PostMapping("/test")
    public ResponseEntity<?> triggerTestAlert(@RequestParam(defaultValue = "Manual Test Alert") String message) {
        boolean sent = alertService.triggerAlert(
                "MANUAL_TEST_" + System.currentTimeMillis(),
                "INFO",
                "Operator Test Alert",
                message,
                Map.of("source", "REST_API_TEST")
        );
        return ResponseEntity.ok(Map.of("success", sent, "message", "Test alert processed."));
    }
}
