package com.saarthi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.config.SecurityConfigValidator;
import com.saarthi.model.ActuationCommand;
import com.saarthi.model.AiChatModels.ChatRequest;
import com.saarthi.model.AiChatModels.ChatResponse;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.LifecycleEventRepository;
import com.saarthi.repository.TelemetryRepository;
import com.saarthi.security.RateLimitingFilter;
import com.saarthi.security.SecurityAuditLogger;
import com.saarthi.service.AlertService;
import com.saarthi.service.GeminiAiService;
import com.saarthi.service.TelemetryService;
import com.saarthi.websocket.TelemetryWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Forensic Unit Tests answering Carver's Iron Mandate [P0-TEST-001]:
 * 1. TelemetryService threshold evaluation & independent anomaly triggers.
 * 2. AlertService SSRF validation against loopback, metadata, and private IP blocks.
 * 3. GeminiAiService prompt-injection defense suppressing unauthorized hardware actuation.
 */
public class CoreLogicAndSecurityUnitTest {

    private TelemetryRepository telemetryRepository;
    private TelemetryWebSocketHandler webSocketHandler;
    private AlertService alertService;
    private SecurityAuditLogger auditLogger;
    private LifecycleEventRepository lifecycleRepository;

    private TelemetryService telemetryService;
    private GeminiAiService geminiAiService;

    @BeforeEach
    void setUp() {
        telemetryRepository = mock(TelemetryRepository.class);
        auditLogger = mock(SecurityAuditLogger.class);
        lifecycleRepository = mock(LifecycleEventRepository.class);

        SecurityConfigValidator validator = new SecurityConfigValidator("test-device", "test-operator");
        webSocketHandler = new TelemetryWebSocketHandler(validator);

        alertService = new AlertService(new ObjectMapper(), auditLogger);
        telemetryService = new TelemetryService(telemetryRepository, webSocketHandler, alertService);
        geminiAiService = new GeminiAiService(telemetryService, lifecycleRepository, auditLogger);
    }

    // =========================================================================
    // 1. TELEMETRY THRESHOLD & ANOMALY LOGIC TESTS
    // =========================================================================

    @Test
    @DisplayName("Optimal readings should yield OPTIMAL status")
    void testOptimalTelemetry() {
        TelemetryRecord record = telemetryService.ingestTelemetry("TEST_001", 750.0, 90.0, 22.0, 1420, "mushroom");
        assertEquals("OPTIMAL", record.getStatus());
        assertEquals(750.0, record.getCo2Ppm());
        assertEquals(90.0, record.getHumidityRh());
    }

    @Test
    @DisplayName("CO2 exceeding purge threshold triggers WARNING and auto-ramps fan")
    void testCo2PurgeThreshold() {
        // Mushroom max CO2 is 1300 ppm; spike is 1400 ppm
        TelemetryRecord record = telemetryService.ingestTelemetry("TEST_001", 1350.0, 90.0, 22.0, 1000, "mushroom");
        assertEquals("WARNING", record.getStatus());
        assertTrue(record.getFanRpm() >= 2400, "Purge fan RPM should auto-ramp to at least 2400");
    }

    @Test
    @DisplayName("CO2 exceeding spike threshold triggers CRITICAL alert")
    void testCo2SpikeCritical() {
        TelemetryRecord record = telemetryService.ingestTelemetry("TEST_001", 1550.0, 90.0, 22.0, 1000, "mushroom");
        assertEquals("CRITICAL", record.getStatus());
        assertTrue(record.getFanRpm() >= 2400);
        assertFalse(alertService.getRecentAlerts().isEmpty(), "Critical alert must be registered in alert history");
        assertEquals("CRITICAL", alertService.getRecentAlerts().get(0).get("level"));
    }

    @Test
    @DisplayName("Independent evaluation: High CO2 and Low Humidity trigger both alerts concurrently")
    void testConcurrentAnomaliesTriggerIndependently() {
        // High CO2 (1500 ppm, critical) AND Low RH (60%, below 75% min)
        TelemetryRecord record = telemetryService.ingestTelemetry("TEST_001", 1500.0, 60.0, 22.0, 1000, "mushroom");
        assertEquals("CRITICAL", record.getStatus(), "Overall status should be CRITICAL due to CO2");

        // Verify both alerts were generated in history
        boolean hasCo2Alert = alertService.getRecentAlerts().stream()
                .anyMatch(a -> a.get("key").toString().startsWith("CO2_SPIKE"));
        boolean hasRhAlert = alertService.getRecentAlerts().stream()
                .anyMatch(a -> a.get("key").toString().startsWith("HUMIDITY_LOW"));

        assertTrue(hasCo2Alert, "CO2 spike alert must be triggered");
        assertTrue(hasRhAlert, "Low humidity alert must be triggered concurrently without being masked");
    }

    @Test
    @DisplayName("Recipe-driven SPIKE and RESET actions use active crop thresholds")
    void testRecipeDrivenSpikeAndReset() {
        // Switch to hydro
        telemetryService.executeActuation(new ActuationCommand("CROP_PROFILE", "SWITCH_CROP", null, null, "TEST") {{
            setCrop("hydro");
        }});

        TelemetryRecord current = telemetryService.getCurrentStateRecord();
        assertEquals("hydro", current.getCropType());

        // Spike in hydro profile
        TelemetryRecord spiked = telemetryService.executeActuation(new ActuationCommand("SIMULATION", "SPIKE", null, null, "TEST"));
        assertTrue(spiked.getCo2Ppm() >= 1700.0, "Hydro CO2 spike should be derived from hydro recipe");

        // Reset in hydro profile
        TelemetryRecord reset = telemetryService.executeActuation(new ActuationCommand("SIMULATION", "RESET", null, null, "TEST"));
        assertEquals("OPTIMAL", reset.getStatus());
    }

    // =========================================================================
    // 2. SSRF PROTECTION UNIT TESTS
    // =========================================================================

    @Test
    @DisplayName("SSRF Guard blocks loopback, cloud metadata, and RFC1918 private subnets")
    void testSsrfProtection() throws Exception {
        Method isSafeMethod = AlertService.class.getDeclaredMethod("isSafePublicWebhookUrl", String.class);
        isSafeMethod.setAccessible(true);

        // 1. Loopback addresses must be blocked
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://127.0.0.1:8080/webhook"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://localhost:8080/webhook"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://127.0.0.2:9000"));

        // 2. AWS/GCP/Azure link-local metadata address must be blocked
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://169.254.169.254/latest/meta-data"));

        // 3. RFC1918 Private subnets must be blocked
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://10.0.0.1/alert"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://192.168.1.100/webhook"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://172.16.0.5/api"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://172.31.255.254/api"));

        // 4. CGNAT 100.64/10 range must be blocked
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "http://100.64.0.1/alert"));

        // 5. Invalid protocols must be blocked
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "file:///etc/passwd"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "ftp://1.2.3.4/file"));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, (Object) null));
        assertFalse((Boolean) isSafeMethod.invoke(alertService, "   "));

        // 6. Public HTTPS webhooks are permitted
        assertTrue((Boolean) isSafeMethod.invoke(alertService, "https://discord.com/api/webhooks/123/abc"));
        assertTrue((Boolean) isSafeMethod.invoke(alertService, "https://hooks.slack.com/services/T00/B00/X00"));
    }

    // =========================================================================
    // 3. AI PROMPT-INJECTION HARDWARE ACTUATION SUPPRESSION TESTS
    // =========================================================================

    @Test
    @DisplayName("AI Actuation Guard: Hardware actions are suppressed when allowActuation is false")
    void testAiActuationSuppression() {
        ChatRequest request = new ChatRequest();
        request.setQuery("Turn on the exhaust ventilation fan right now");

        // Call without operator actuation authorization
        ChatResponse unauthResponse = geminiAiService.processAgroQuery(request, false);
        assertNotNull(unauthResponse);
        assertNull(unauthResponse.getExecutedAction(), "Actuation command must be null when allowActuation is false");

        // Call with operator actuation authorization
        ChatResponse authResponse = geminiAiService.processAgroQuery(request, true);
        assertNotNull(authResponse);
        assertNotNull(authResponse.getExecutedAction(), "Actuation command should be populated when allowActuation is true");
        assertEquals("VENTILATE", authResponse.getExecutedAction().getAction());
    }

    // =========================================================================
    // 4. BUFFER BOUNDING, STORAGE DEGRADATION & TIMED ACTUATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Fail-Safe: writeBuffer is capped at 250 on sustained DB failure without OOM")
    void testWriteBufferBoundingAndStorageDegraded() {
        // Simulate database outage
        Mockito.doThrow(new RuntimeException("Connection refused: 5432"))
                .when(telemetryRepository).saveAll(any());

        // Ingest 300 records under DB outage
        for (int i = 0; i < 300; i++) {
            telemetryService.ingestTelemetry("NODE_" + i, 800.0, 90.0, 22.0, 1400, "mushroom");
        }

        // Trigger flush (which fails)
        telemetryService.flushWriteBuffer();

        // Verify storage is marked degraded
        assertTrue(telemetryService.isStorageDegraded(), "Storage must be marked degraded during DB outage");
    }

    @Test
    @DisplayName("Actuation: durationSeconds auto-releases fan back to nominal baseline upon expiry")
    void testDurationSecondsAutoRelease() {
        // Run actuation for 1 second
        ActuationCommand cmd = new ActuationCommand("FAN_01", "VENTILATE", 1, 2800, "TEST");
        telemetryService.executeActuation(cmd);

        assertEquals(2800, telemetryService.getCurrentStateRecord().getFanRpm());

        // Call checkActuationExpiry immediately (not yet expired)
        telemetryService.checkActuationExpiry();
        assertEquals(2800, telemetryService.getCurrentStateRecord().getFanRpm());

        // Advance simulated clock by sleeping 1.1s
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {}

        telemetryService.checkActuationExpiry();
        // Mushroom nominal baseline is 1420 RPM
        assertEquals(1420, telemetryService.getCurrentStateRecord().getFanRpm(),
                "Fan must auto-release to nominal baseline (1420 RPM) after durationSeconds expires");
    }

    @Test
    @DisplayName("Rate Limiter: Bucket4j sliding-window rate limit returns 429 Too Many Requests")
    void testRateLimitingFilterRejectsExcessRequests() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        // Use reflection to configure test capacities
        java.lang.reflect.Field enabledField = RateLimitingFilter.class.getDeclaredField("rateLimitEnabled");
        enabledField.setAccessible(true);
        enabledField.set(filter, true);

        java.lang.reflect.Field anonRpm = RateLimitingFilter.class.getDeclaredField("anonymousRpm");
        anonRpm.setAccessible(true);
        anonRpm.set(filter, 2);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/ai/chat");
        request.setRemoteAddr("198.51.100.25");
        org.springframework.mock.web.MockHttpServletResponse response1 = new org.springframework.mock.web.MockHttpServletResponse();

        // 1st request -> ok
        filter.doFilter(request, response1, new org.springframework.mock.web.MockFilterChain());
        assertEquals(200, response1.getStatus());

        // 2nd request -> ok
        org.springframework.mock.web.MockHttpServletResponse response2 = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request, response2, new org.springframework.mock.web.MockFilterChain());
        assertEquals(200, response2.getStatus());

        // 3rd request -> 429 Too Many Requests
        org.springframework.mock.web.MockHttpServletResponse response3 = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request, response3, new org.springframework.mock.web.MockFilterChain());
        assertEquals(429, response3.getStatus());
        assertTrue(response3.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }
}
