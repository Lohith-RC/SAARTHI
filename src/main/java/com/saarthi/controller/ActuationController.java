package com.saarthi.controller;

import com.saarthi.model.ActuationCommand;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Spring REST Controller for Hardware Relays, Exhaust Fan Control & Climate Simulations.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ActuationController {

    private final TelemetryService telemetryService;

    public ActuationController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * POST /api/v1/actuate
     */
    @PostMapping("/actuate")
    public ResponseEntity<?> actuateDevice(@RequestBody ActuationCommand command) {
        TelemetryRecord updatedState = telemetryService.executeActuation(command);
        return ResponseEntity.ok(Map.of(
                "status", "EXECUTED",
                "action", command.getAction(),
                "chamberState", updatedState
        ));
    }

    /**
     * POST /api/v1/simulate/spike
     */
    @PostMapping("/simulate/spike")
    public ResponseEntity<?> simulateSpike() {
        ActuationCommand cmd = new ActuationCommand("SIMULATION", "SPIKE", null, null, "USER_MANUAL_SIMULATION");
        TelemetryRecord record = telemetryService.executeActuation(cmd);
        return ResponseEntity.ok(record);
    }

    /**
     * POST /api/v1/simulate/reset
     */
    @PostMapping("/simulate/reset")
    public ResponseEntity<?> resetSimulation() {
        ActuationCommand cmd = new ActuationCommand("SIMULATION", "RESET", null, null, "USER_MANUAL_RESET");
        TelemetryRecord record = telemetryService.executeActuation(cmd);
        return ResponseEntity.ok(record);
    }
}
