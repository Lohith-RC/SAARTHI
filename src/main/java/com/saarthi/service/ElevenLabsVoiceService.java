package com.saarthi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Service for hyper-realistic AI voice synthesis using ElevenLabs API (eleven_flash_v2_5).
 */
@Service
public class ElevenLabsVoiceService {

    @Value("${saarthi.ai.elevenlabs.api-key:}")
    private String defaultApiKey;

    @Value("${saarthi.ai.elevenlabs.default-voice-id:JBFqnCBsd6RMkjVDRZzb}")
    private String defaultVoiceId;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] synthesizeVoice(String text, String customApiKey, String customVoiceId) {
        String apiKey = (customApiKey != null && !customApiKey.isBlank()) ? customApiKey : defaultApiKey;
        String voiceId = (customVoiceId != null && !customVoiceId.isBlank()) ? customVoiceId : defaultVoiceId;

        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            String endpoint = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId;

            Map<String, Object> body = Map.of(
                    "text", text,
                    "model_id", "eleven_flash_v2_5",
                    "voice_settings", Map.of(
                            "stability", 0.5,
                            "similarity_boost", 0.75
                    )
            );

            String jsonPayload = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("xi-api-key", apiKey.trim())
                    .header("Accept", "audio/mpeg")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                System.err.println("ElevenLabs API Response Code: " + response.statusCode() + " | Body: " + new String(response.body()));
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error calling ElevenLabs TTS: " + e.getMessage());
            return null;
        }
    }
}
