package com.saarthi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the security-aware SAARTHI API.
 *
 * <p>Asserts the two-principal (device / operator) model enforced by Spring
 * Security: device-only tel/fan ingestion, operator-only actuation, alerts and
 * AI management, public-only HUD reads.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SaarthiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String DEVICE_TOKEN = "test-device-token-abc123";
    private static final String OPERATOR_TOKEN = "test-operator-token-def456";

    @Test
    void contextLoads() {
    }

    @Test
    void publicReadEndpointIsAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").exists())
                .andExpect(jsonPath("$.co2Ppm").exists())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void deviceTokenCanPushTelemetry() throws Exception {
        String payload = """
            {
                "deviceId": "TEST_NODE_99",
                "co2Ppm": 890.5,
                "humidityRh": 91.0,
                "tempC": 22.8,
                "fanRpm": 1500,
                "cropType": "mushroom"
            }
            """;

        mockMvc.perform(post("/api/v1/telemetry/push")
                .header("X-Device-Token", DEVICE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK"))
                .andExpect(jsonPath("$.deviceId").value("TEST_NODE_99"));
    }

    @Test
    void deviceTokenCannotPushWithOperatorToken() throws Exception {
        String payload = """
            {
                "deviceId": "TEST_NODE_99",
                "co2Ppm": 890.5
            }
            """;

        mockMvc.perform(post("/api/v1/telemetry/push")
                .header("X-Operator-Token", OPERATOR_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void telemetryPushRejectsInvalidToken() throws Exception {
        String payload = """
            {
                "deviceId": "ROGUE_NODE",
                "co2Ppm": 999.0
            }
            """;

        mockMvc.perform(post("/api/v1/telemetry/push")
                .header("X-Device-Token", "INVALID_TOKEN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void operatorTokenCanActuate() throws Exception {
        String payload = """
            {
                "target": "FAN_01",
                "action": "VENTILATE",
                "rpm": 2500,
                "durationSeconds": 300
            }
            """;

        mockMvc.perform(post("/api/v1/actuate")
                .header("X-Operator-Token", OPERATOR_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.action").value("VENTILATE"));
    }

    @Test
    void actuationRejectsDeviceToken() throws Exception {
        String payload = """
            {
                "target": "FAN_01",
                "action": "VENTILATE"
            }
            """;

        mockMvc.perform(post("/api/v1/actuate")
                .header("X-Device-Token", DEVICE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void alertsRequireOperatorToken() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/recent"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/alerts/recent")
                .header("X-Operator-Token", OPERATOR_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/alerts/recent")
                .header("X-Device-Token", DEVICE_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiChatRequiresOperatorToken() throws Exception {
        String payload = """
            {
                "query": "Why are green patches appearing on my mushroom bags?"
            }
            """;

        // Anonymous (public browser) chat must now be rejected
        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());

        // Device token is not an operator
        mockMvc.perform(post("/api/v1/ai/chat")
                .header("X-Device-Token", DEVICE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());

        // Operator token is allowed and returns the grounded local agronomy fallback
        mockMvc.perform(post("/api/v1/ai/chat")
                .header("X-Operator-Token", OPERATOR_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.modelUsed").value("saarthi-generative-core"));
    }
}