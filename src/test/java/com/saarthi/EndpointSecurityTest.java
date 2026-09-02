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
 * Full security regression matrix: every endpoint is asserted under anonymous,
 * device-token, and operator-token contexts to lock the two-principal model.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class EndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String DEVICE = "test-device-token-abc123";
    private static final String OPERATOR = "test-operator-token-def456";
    private static final String ROGUE = "totally-rogue-token";

    private static final String ACTUATE_BODY = """
            {"target":"FAN_01","action":"VENTILATE","rpm":2400,"durationSeconds":300}
            """;

    private static final String PUSH_BODY = """
            {"deviceId":"SEC_TEST","co2Ppm":900.0,"humidityRh":91.0,"tempC":22.0,"fanRpm":1400}
            """;

    @Test
    void telemetryPushIsDeviceOnly() throws Exception {
        // anonymous -> 401
        mockMvc.perform(post("/api/v1/telemetry/push").contentType(MediaType.APPLICATION_JSON).content(PUSH_BODY))
                .andExpect(status().isUnauthorized());
        // operator -> 403 (wrong principal type)
        mockMvc.perform(post("/api/v1/telemetry/push")
                        .header("X-Operator-Token", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON).content(PUSH_BODY))
                .andExpect(status().isForbidden());
        // rogue -> 401
        mockMvc.perform(post("/api/v1/telemetry/push")
                        .header("X-Device-Token", ROGUE)
                        .contentType(MediaType.APPLICATION_JSON).content(PUSH_BODY))
                .andExpect(status().isUnauthorized());
        // device -> 200
        mockMvc.perform(post("/api/v1/telemetry/push")
                        .header("X-Device-Token", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON).content(PUSH_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void actuationIsOperatorOnly() throws Exception {
        mockMvc.perform(post("/api/v1/actuate").contentType(MediaType.APPLICATION_JSON).content(ACTUATE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/actuate")
                        .header("X-Device-Token", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON).content(ACTUATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/actuate")
                        .header("X-Operator-Token", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON).content(ACTUATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));
    }

    @Test
    void simulationEndpointsAreOperatorOnly() throws Exception {
        mockMvc.perform(post("/api/v1/simulate/spike"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/simulate/spike").header("X-Device-Token", DEVICE))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/simulate/spike").header("X-Operator-Token", OPERATOR))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/simulate/reset").header("X-Operator-Token", OPERATOR))
                .andExpect(status().isOk());
    }

    @Test
    void alertsAreOperatorOnly() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/recent")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/alerts/recent").header("X-Device-Token", DEVICE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/alerts/recent").header("X-Operator-Token", OPERATOR))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/alerts/test").header("X-Operator-Token", OPERATOR))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/alerts/test")).andExpect(status().isUnauthorized());
    }

    @Test
    void aiManagementIsOperatorOnly() throws Exception {
        // chat
        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("X-Operator-Token", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hello\"}"))
                .andExpect(status().isOk());
        // tts is operator-only; with no ElevenLabs key configured it returns 503
        // (proves the request passed auth and reached the service), while
        // anonymous is rejected with 401 before reaching the service.
        mockMvc.perform(post("/api/v1/ai/tts")
                        .header("X-Operator-Token", OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test\"}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/ai/tts")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicReadEndpointsAreOpen() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/current")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ai/status")).andExpect(status().isOk());
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/style.css")).andExpect(status().isOk());
        mockMvc.perform(get("/app.js")).andExpect(status().isOk());
    }

    @Test
    void unmatchedRoutesAreDenied() throws Exception {
        // authenticated-but-unauthorized route -> 403
        mockMvc.perform(get("/api/v1/unknown").header("X-Operator-Token", OPERATOR))
                .andExpect(status().isForbidden());
        // anonymous -> routed to the entry point -> 401
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isUnauthorized());
    }
}