package com.saarthi.controller;

import com.saarthi.model.AiChatModels.ChatRequest;
import com.saarthi.model.AiChatModels.ChatResponse;
import com.saarthi.service.ElevenLabsVoiceService;
import com.saarthi.service.GeminiAiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Spring REST Controller for Voice & Text AI Agronomy Copilot, ElevenLabs TTS & OpenJarvis Memory Traces.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiVoiceController {

    private final GeminiAiService aiService;
    private final ElevenLabsVoiceService elevenLabsService;

    public AiVoiceController(GeminiAiService aiService, ElevenLabsVoiceService elevenLabsService) {
        this.aiService = aiService;
        this.elevenLabsService = elevenLabsService;
    }

    /**
     * POST /api/v1/ai/chat
     * Executed on lightweight Virtual Threads (spring.threads.virtual.enabled=true).
     * Requires operator authentication. AI-triggered hardware actuation is only
     * honored when the caller is an authenticated operator and the guarded
     * {@link com.saarthi.security.SaarthiPrincipal} is present — otherwise it is
     * suppressed to prevent prompt-injection driven physical actions.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> handleChatQuery(
            @RequestBody ChatRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.saarthi.security.SaarthiPrincipal principal) {

        boolean allowActuation = principal != null && principal.isOperator();
        ChatResponse response = aiService.processAgroQuery(request, allowActuation);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/ai/memory/timeline
     * Returns the persistent crop lifecycle memory ledger (inspired by OpenJarvis local traces).
     */
    @GetMapping("/memory/timeline")
    public ResponseEntity<List<Map<String, Object>>> getCropLifecycleTimeline() {
        return ResponseEntity.ok(aiService.getCropLifecycleLedger());
    }

    /**
     * POST /api/v1/ai/memory/log
     * Appends a new milestone to the persistent crop lifecycle ledger.
     * Field values are validated and bounded before persistence.
     */
    @PostMapping("/memory/log")
    public ResponseEntity<?> logCropLifecycleEvent(@RequestBody Map<String, Object> eventData) {
        int day = parseDay(eventData.get("day"));
        String type = boundedString(eventData.get("type"), "MANUAL_LOG", 64, "type");
        String desc = boundedString(eventData.get("description"), "Operator logged note", 500, "description");
        String status = boundedString(eventData.get("healthStatus"), "OPTIMAL", 32, "healthStatus");

        aiService.addLifecycleEvent(day, type, desc, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "Milestone recorded in OpenJarvis ledger"));
    }

    private static int parseDay(Object value) {
        if (value == null) return 1;
        try {
            int day = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
            if (day < 1 || day > 365) {
                throw new IllegalArgumentException("day must be between 1 and 365");
            }
            return day;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("day must be a valid integer");
        }
    }

    private static String boundedString(Object value, String fallback, int maxLen, String field) {
        if (value == null) return fallback;
        String s = value.toString().trim();
        if (s.isEmpty()) return fallback;
        if (s.length() > maxLen) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLen + " characters");
        }
        return s;
    }

    /**
     * POST /api/v1/ai/tts
     * Synthesizes realistic human speech using ElevenLabs API.
     * The API key is always the server-configured one; caller-supplied keys are
     * rejected to prevent key exfiltration via this endpoint.
     */
    @PostMapping("/tts")
    public ResponseEntity<?> synthesizeVoice(@RequestBody Map<String, String> request) {
        String text = request.getOrDefault("text", "Telemetry online.");
        String voiceId = request.get("voiceId");

        byte[] audioBytes = elevenLabsService.synthesizeVoice(text, null, voiceId);

        if (audioBytes != null && audioBytes.length > 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentLength(audioBytes.length);
            headers.set("Cache-Control", "no-cache");
            return ResponseEntity.ok().headers(headers).body(audioBytes);
        } else {
            return ResponseEntity.status(503).body(Map.of("error", "ElevenLabs TTS unavailable or quota exceeded. Use browser TTS."));
        }
    }

    /**
     * POST /api/v1/ai/generate-crop-image
     * Generates free AI diagnostic images for crop anomalies and growth simulation.
     */
    @PostMapping("/generate-crop-image")
    public ResponseEntity<?> generateCropImage(@RequestBody Map<String, String> request) {
        String prompt = request.getOrDefault("prompt", "oyster mushroom growing on darkroom grow rack, cinematic neon sci-fi lighting");
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=512&height=512&nologo=true";

        return ResponseEntity.ok(Map.of(
                "success", true,
                "prompt", prompt,
                "imageUrl", imageUrl
        ));
    }

    /**
     * GET /api/v1/ai/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getAiStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "ONLINE",
                "engines", Map.of(
                        "primaryReasoning", "Google Gemini 3.8 Flash",
                        "deepReasoning", "Groq GPT-OSS 120B",
                        "localEdgeReasoning", "OpenJarvis / Local Ollama (Phi-4-Mini / Llama 3.2)",
                        "voiceSynthesis", "ElevenLabs AI Audio + Browser Web Speech",
                        "visionGeneration", "Pollinations AI / Flux"
                ),
                "grounding", "OpenJarvis Persistent Crop Ledger + Live IoT Telemetry",
                "supportedModels", new String[]{"gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-flash", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openjarvis-phi4-mini"}
        ));
    }
}
