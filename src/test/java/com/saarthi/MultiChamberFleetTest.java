package com.saarthi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.config.SecurityConfigValidator;
import com.saarthi.model.ActuationCommand;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.LifecycleEventRepository;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.security.SecurityAuditLogger;
import com.saarthi.service.AlertService;
import com.saarthi.service.TelemetryService;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Multi-chamber fleet regression tests: per-chamber isolation, per-chamber
 * recipes, per-chamber actuation expiry, per-chamber watchdog, fleet snapshots,
 * and the WebSocket fleet message contract.
 */
public class MultiChamberFleetTest {

    private TelemetryRepository telemetryRepository;
    private TelemetryWebSocketHandler webSocketHandler;
    private AlertService alertService;
    private SecurityAuditLogger auditLogger;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryRepository = mock(TelemetryRepository.class);
        auditLogger = mock(SecurityAuditLogger.class);
        LifecycleEventRepository lifecycleRepository = mock(LifecycleEventRepository.class);

        SecurityConfigValidator validator = new SecurityConfigValidator("test-device", "test-operator");
        webSocketHandler = new TelemetryWebSocketHandler(validator);
        alertService = new AlertService(new ObjectMapper(), auditLogger);
        telemetryService = new TelemetryService(telemetryRepository, webSocketHandler, alertService);
    }

    @Test
    @DisplayName("Chambers are fully isolated per deviceId")
    void chambersAreIsolatedPerDevice() {
        telemetryService.ingestTelemetry("CHAMBER_A", 750.0, 90.0, 22.0, 1420, "mushroom");
        telemetryService.ingestTelemetry("CHAMBER_B", 1550.0, 60.0, 25.0, 1000, "mushroom");

        TelemetryRecord a = telemetryService.getChamberStateRecord("CHAMBER_A");
        TelemetryRecord b = telemetryService.getChamberStateRecord("CHAMBER_B");

        assertEquals("OPTIMAL", a.getStatus());
        assertEquals("CRITICAL", b.getStatus());
        assertEquals(750.0, a.getCo2Ppm());
        assertEquals(1550.0, b.getCo2Ppm());
        assertEquals("CHAMBER_A", a.getDeviceId());
        assertEquals("CHAMBER_B", b.getDeviceId());
    }

    @Test
    @DisplayName("Per-chamber recipes drive spike thresholds without affecting the default chamber")
    void perChamberRecipesDriveSpikeAndReset() {
        telemetryService.ingestTelemetry("HYDRO_01", 900.0, 60.0, 22.0, 1100, "hydro");

        ActuationCommand spike = new ActuationCommand("SIMULATION", "SPIKE", null, null, "TEST");
        spike.setDeviceId("HYDRO_01");
        telemetryService.executeActuation(spike);

        // Hydro spike threshold is 1700 ppm
        TelemetryRecord spiked = telemetryService.getChamberStateRecord("HYDRO_01");
        assertTrue(spiked.getCo2Ppm() >= 1700.0, "Hydro chamber spike must use hydro recipe thresholds");

        // Default chamber must be completely unaffected
        TelemetryRecord def = telemetryService.getCurrentStateRecord();
        assertEquals("SAARTHI_001", def.getDeviceId());
        assertEquals(845.0, def.getCo2Ppm());
        assertEquals(1420, def.getFanRpm());
    }

    @Test
    @DisplayName("Actuation expiry auto-releases only the target chamber")
    void actuationExpiryReleasesOnlyTargetChamber() throws Exception {
        ActuationCommand cmd = new ActuationCommand("FAN_01", "VENTILATE", 1, 2800, "TEST");
        cmd.setDeviceId("CHAMBER_X");
        telemetryService.executeActuation(cmd);
        assertEquals(2800, telemetryService.getChamberStateRecord("CHAMBER_X").getFanRpm());

        // Not yet expired
        telemetryService.checkActuationExpiry();
        assertEquals(2800, telemetryService.getChamberStateRecord("CHAMBER_X").getFanRpm());

        Thread.sleep(1100);
        telemetryService.checkActuationExpiry();

        // CHAMBER_X released back to mushroom baseline (1420 RPM)
        assertEquals(1420, telemetryService.getChamberStateRecord("CHAMBER_X").getFanRpm());

        // Default chamber was never actuated and stays at its own baseline
        assertEquals(1420, telemetryService.getCurrentStateRecord().getFanRpm());
        assertEquals("OPTIMAL", telemetryService.getCurrentStateRecord().getStatus());
    }

    @Test
    @DisplayName("Dead-Man's Watchdog flags each silent chamber independently")
    void watchdogAlertsPerDevice() throws Exception {
        TelemetryService fastWatchdog = new TelemetryService(telemetryRepository, webSocketHandler, alertService, 1);
        fastWatchdog.ingestTelemetry("NODE_SLEEPY", 800.0, 90.0, 22.0, 1420, "mushroom");

        Thread.sleep(1100);
        fastWatchdog.runDeadMansWatchdog();

        boolean hasAlert = alertService.getRecentAlerts().stream()
                .anyMatch(a -> "WATCHDOG_TIMEOUT_NODE_SLEEPY".equals(a.get("key")));
        assertTrue(hasAlert, "Watchdog must raise a per-device alert for the silent chamber");
    }

    @Test
    @DisplayName("Fleet snapshot lists every chamber sorted by deviceId")
    void fleetSnapshotContainsAllChambersSorted() {
        telemetryService.ingestTelemetry("ZETA_01", 800.0, 90.0, 22.0, 1420, "mushroom");
        telemetryService.ingestTelemetry("ALPHA_01", 900.0, 70.0, 23.0, 1100, "hydro");

        List<TelemetryRecord> snapshot = telemetryService.getFleetSnapshot();
        List<String> ids = snapshot.stream().map(TelemetryRecord::getDeviceId).toList();

        assertEquals(List.of("ALPHA_01", "SAARTHI_001", "ZETA_01"), ids);
        assertEquals(3, snapshot.size());
    }

    @Test
    @DisplayName("Unknown chamber lookup returns null (404 contract)")
    void unknownChamberReturnsNull() {
        assertNull(telemetryService.getChamberStateRecord("DOES_NOT_EXIST"));
        // Default chamber always exists
        assertNotNull(telemetryService.getCurrentStateRecord());
    }

    @Test
    @DisplayName("WebSocket: FLEET_SNAPSHOT is pushed once on connect")
    void fleetSnapshotSentOnWebSocketConnect() throws Exception {
        SecurityConfigValidator validator = new SecurityConfigValidator("test-device", "test-operator");
        TelemetryWebSocketHandler handler = new TelemetryWebSocketHandler(validator);
        handler.setFleetSnapshotSupplier(() -> List.of(
                new TelemetryRecord("SAARTHI_001", 845.0, 92.0, 22.4, 1420, 45, "mushroom", "OPTIMAL"),
                new TelemetryRecord("ESP32_NODE_01", 880.0, 90.0, 22.6, 1420, 45, "mushroom", "OPTIMAL")
        ));

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-test-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        verify(session, times(1)).sendMessage(argThat(msg -> {
            TextMessage text = (TextMessage) msg;
            return text.getPayload().contains("\"type\":\"FLEET_SNAPSHOT\"")
                    && text.getPayload().contains("ESP32_NODE_01");
        }));
        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("WebSocket: CHAMBER_UPDATE message is well-formed for non-default chambers")
    void chamberUpdateMessageIsTyped() throws Exception {
        SecurityConfigValidator validator = new SecurityConfigValidator("test-device", "test-operator");
        TelemetryWebSocketHandler handler = new TelemetryWebSocketHandler(validator);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-test-2");
        when(session.isOpen()).thenReturn(true);

        // enforceToken is false in a plain unit test, so the session connects.
        handler.afterConnectionEstablished(session);

        handler.broadcastChamberUpdate("ESP32_NODE_01",
                new TelemetryRecord("ESP32_NODE_01", 880.0, 90.0, 22.6, 1420, 45, "mushroom", "OPTIMAL"));

        verify(session, atLeastOnce()).sendMessage(argThat(msg -> {
            TextMessage text = (TextMessage) msg;
            return text.getPayload().contains("\"type\":\"CHAMBER_UPDATE\"")
                    && text.getPayload().contains("\"deviceId\":\"ESP32_NODE_01\"")
                    && text.getPayload().contains("\"co2Ppm\":880.0");
        }));
    }
}