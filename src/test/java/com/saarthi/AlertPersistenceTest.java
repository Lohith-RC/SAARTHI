package com.saarthi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.model.AlertEvent;
import com.saarthi.repository.AlertRepository;
import com.saarthi.security.SecurityAuditLogger;
import com.saarthi.service.AlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Alert persistence tests: alerts are durably written to the database, and a
 * storage outage never blocks emergency dispatch.
 */
public class AlertPersistenceTest {

    @Test
    @DisplayName("Triggered alerts are persisted to the database")
    void alertsArePersistedToDatabase() {
        AlertRepository repo = mock(AlertRepository.class);
        AlertService service = new AlertService(new ObjectMapper(), mock(SecurityAuditLogger.class), repo);

        boolean sent = service.triggerAlert("TEST_KEY_1", "CRITICAL", "High CO2", "CO2 at 1500 ppm", Map.of("co2", 1500.0));

        assertTrue(sent);
        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(repo, times(1)).save(captor.capture());

        AlertEvent saved = captor.getValue();
        assertEquals("TEST_KEY_1", saved.getAlertKey());
        assertEquals("CRITICAL", saved.getLevel());
        assertEquals("High CO2", saved.getTitle());
        assertNotNull(saved.getTimestamp());
        assertTrue(saved.getDetailsJson().contains("1500"), "Details must be serialized to JSON");
    }

    @Test
    @DisplayName("Database outage does not block alert dispatch (best-effort persistence)")
    void storageFailureDoesNotBlockAlertDispatch() {
        AlertRepository repo = mock(AlertRepository.class);
        doThrow(new RuntimeException("Connection refused: 5432")).when(repo).save(any());

        AlertService service = new AlertService(new ObjectMapper(), mock(SecurityAuditLogger.class), repo);

        boolean sent = service.triggerAlert("TEST_KEY_2", "WARNING", "Low RH", "RH at 60%", Map.of("rh", 60.0));

        assertTrue(sent, "Alert must still dispatch even when persistence fails");
        assertFalse(service.getRecentAlerts().isEmpty(), "In-memory alert history must still be updated");
        assertEquals("WARNING", service.getRecentAlerts().get(0).get("level"));
    }

    @Test
    @DisplayName("AlertService without a repository stays in-memory only (legacy contract)")
    void legacyConstructorRemainsInMemoryOnly() {
        AlertService service = new AlertService(new ObjectMapper(), mock(SecurityAuditLogger.class));
        service.triggerAlert("TEST_KEY_3", "INFO", "Test", "msg", Map.of());
        assertEquals(1, service.getRecentAlerts().size());
        assertTrue(service.getAlertHistory(10).isEmpty(), "No repository means no persisted history");
    }
}