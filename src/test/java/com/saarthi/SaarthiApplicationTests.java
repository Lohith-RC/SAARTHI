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

@SpringBootTest
@AutoConfigureMockMvc
public class SaarthiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void testCurrentTelemetryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").exists())
                .andExpect(jsonPath("$.co2Ppm").exists())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testTelemetryPushEndpoint() throws Exception {
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK"))
                .andExpect(jsonPath("$.deviceId").value("TEST_NODE_99"));
    }

    @Test
    void testActuationEndpoint() throws Exception {
        String payload = """
            {
                "target": "FAN_01",
                "action": "VENTILATE",
                "rpm": 2500,
                "durationSeconds": 300
            }
            """;

        mockMvc.perform(post("/api/v1/actuate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.action").value("VENTILATE"));
    }

    @Test
    void testAiChatAgronomyRules() throws Exception {
        String payload = """
            {
                "query": "Why are green patches appearing on my mushroom bags?"
            }
            """;

        mockMvc.perform(post("/api/v1/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.modelUsed").value("saarthi-local-rules"));
    }
}
