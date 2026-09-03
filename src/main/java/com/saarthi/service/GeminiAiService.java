package com.saarthi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.model.ActuationCommand;
import com.saarthi.model.AiChatModels.ChatRequest;
import com.saarthi.model.AiChatModels.ChatResponse;
import com.saarthi.model.LifecycleEvent;
import com.saarthi.model.TelemetryRecord;
import com.saarthi.repository.LifecycleEventRepository;
import com.saarthi.security.SecurityAuditLogger;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Multi-Turn Generative AI Reasoning & Agentic Tool Service.
 * Inspired by Stanford OpenJarvis (Local-First Edge Autonomy, Multi-Step Tool Registry & Crop Lifecycle Ledger).
 * Supports Google Gemini (gemini-3.6-flash), Groq DeepSeek-R1, Local OpenJarvis / Ollama, and Grounded Context.
 */
@Service
public class GeminiAiService {

    @Value("${saarthi.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${saarthi.ai.groq.default-model:openai/gpt-oss-120b}")
    private String groqDefaultModel;

    @Value("${saarthi.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${saarthi.ai.gemini.default-model:gemini-3.8-flash}")
    private String geminiDefaultModel;

    /** Comma-separated fallback chain tried when the requested/default Gemini model fails. */
    @Value("${saarthi.ai.gemini.candidate-models:gemini-3.8-flash,gemini-3.6-flash,gemini-2.5-flash}")
    private String geminiCandidateModels;

    /** Comma-separated fallback chain tried when the requested/default Groq model fails. */
    @Value("${saarthi.ai.groq.candidate-models:openai/gpt-oss-120b,openai/gpt-oss-20b,qwen/qwen3.8-27b}")
    private String groqCandidateModels;

    @Value("${saarthi.ai.customer-api-keys-enabled:false}")
    private boolean customerApiKeysEnabled;

    @Value("${saarthi.ai.openjarvis.endpoint:http://localhost:11434}")
    private String openJarvisEndpoint;

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);

    private final TelemetryService telemetryService;
    private final LifecycleEventRepository lifecycleEventRepository;
    private final SecurityAuditLogger audit;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Rolling conversation history (last 10 dialogue turns for multi-turn context)
    private final Deque<Map<String, String>> conversationHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_TURNS = 10;

    // OpenJarvis Persistent Crop Lifecycle Ledger (Day 1 - 60 Memory Trace)
    private final List<Map<String, Object>> cropLifecycleLedger = new CopyOnWriteArrayList<>();

    private static final Pattern ACTION_PATTERN = Pattern.compile("\\[ACTION:(.*?)\\]");
    private static final Pattern EMOTION_PATTERN = Pattern.compile("\\[EMOTION:(.*?)\\]");

    public GeminiAiService(TelemetryService telemetryService,
                           LifecycleEventRepository lifecycleEventRepository,
                           SecurityAuditLogger audit) {
        this.telemetryService = telemetryService;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.audit = audit;
    }

    @PostConstruct
    private void initDefaultCropLifecycleLedger() {
        try {
            if (lifecycleEventRepository.count() == 0) {
                addLifecycleEvent(1, "SUBSTRATE_INOCULATION", "Spawn bags inoculated with Pleurotus ostreatus. Darkness maintained, incubation at 24°C.", "OPTIMAL");
                addLifecycleEvent(7, "MYCELIUM_COLONIZATION", "Mycelium coverage at 65%. Zero contaminant patches identified.", "OPTIMAL");
                addLifecycleEvent(14, "PINHEAD_INDUCTION", "Chamber transferred to fruiting parameters. CO2 ramped below 900 ppm, RH >92%.", "OPTIMAL");
                addLifecycleEvent(18, "CANOPY_OPTIMIZATION", "Primordia clusters expanding evenly across Rack 1. Fresh air exchange duty set to 45%.", "OPTIMAL");
            } else {
                reloadLedgerFromDatabase();
            }
        } catch (Exception e) {
            log.warn("Could not initialize crop lifecycle ledger: {}", e.getMessage());
        }
    }

    private void reloadLedgerFromDatabase() {
        cropLifecycleLedger.clear();
        for (LifecycleEvent event : lifecycleEventRepository.findAllByOrderByCropDayAsc()) {
            cropLifecycleLedger.add(toLedgerMap(event));
        }
    }

    private static Map<String, Object> toLedgerMap(LifecycleEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId() == null ? UUID.randomUUID().toString() : event.getId().toString());
        map.put("day", event.getCropDay());
        map.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        map.put("type", event.getEventType());
        map.put("description", event.getDescription());
        map.put("healthStatus", event.getHealthStatus());
        return map;
    }

    public void addLifecycleEvent(int cropDay, String eventType, String description, String healthStatus) {
        LifecycleEvent persisted;
        try {
            persisted = lifecycleEventRepository.save(new LifecycleEvent(cropDay, eventType, description, healthStatus));
        } catch (Exception e) {
            log.warn("Could not persist lifecycle event: {}", e.getMessage());
            persisted = new LifecycleEvent(cropDay, eventType, description, healthStatus);
            persisted.setId(System.nanoTime());
        }
        cropLifecycleLedger.add(toLedgerMap(persisted));
    }

    public List<Map<String, Object>> getCropLifecycleLedger() {
        return Collections.unmodifiableList(cropLifecycleLedger);
    }

    public ChatResponse processAgroQuery(ChatRequest request) {
        return processAgroQuery(request, false);
    }

    /**
     * Process an agro query. {@code allowActuation} is a deliberate guardrail:
     * when {@code false}, any {at}ACTION directive parsed from model output is
     * ignored and NOT executed, preventing prompt-injection driven hardware
     * actuation even from authenticated operators.
     */
    public ChatResponse processAgroQuery(ChatRequest request, boolean allowActuation) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ChatResponse.error("Query cannot be empty.");
        }

        String userQuery = request.getQuery().trim();
        String requestedModel = request.getModel();

        TelemetryRecord current = telemetryService.getCurrentStateRecord();
        String systemPrompt = buildSystemGroundingPrompt(current);

        // TIER 1: Prioritize fastest available LPU inference (Groq ~280ms TTFT)
        // Unless caller explicitly requested Gemini or Local Edge
        boolean prefersGemini = requestedModel != null && requestedModel.toLowerCase().startsWith("gemini");
        boolean prefersLocal = requestedModel != null && (requestedModel.contains("openjarvis") || requestedModel.contains("ollama") || requestedModel.contains("local"));

        if (prefersLocal) {
            ChatResponse localJarvisResp = callOpenJarvisLocal(userQuery, systemPrompt, requestedModel, allowActuation);
            if (localJarvisResp != null && localJarvisResp.isSuccess()) {
                recordConversationTurn(userQuery, localJarvisResp.getReplyText());
                return localJarvisResp;
            }
        }

        // 1. FAST PATH: Groq LPU (Fastest generation: ~520 tokens/sec)
        if (!prefersGemini && groqApiKey != null && !groqApiKey.isBlank() && groqApiKey.length() > 10) {
            ChatResponse groqResponse = callGroqApi(userQuery, systemPrompt, groqApiKey, requestedModel, allowActuation);
            if (groqResponse != null && groqResponse.isSuccess() && groqResponse.getReplyText() != null && groqResponse.getReplyText().trim().length() > 5) {
                recordConversationTurn(userQuery, groqResponse.getReplyText());
                return groqResponse;
            }
            log.info("[FALLBACK TRIGGERED] Groq LPU unavailable or below quality threshold. Cascading to Tier 2 Gemini Flash.");
        }

        // 2. TIER 2: Google Gemini Flash (High-Reasoning & Multi-turn Fallback)
        if (geminiApiKey != null && !geminiApiKey.isBlank() && geminiApiKey.length() > 10) {
            ChatResponse geminiResponse = callGeminiApi(userQuery, systemPrompt, geminiApiKey, requestedModel, allowActuation);
            if (geminiResponse != null && geminiResponse.isSuccess() && geminiResponse.getReplyText() != null && geminiResponse.getReplyText().trim().length() > 5) {
                recordConversationTurn(userQuery, geminiResponse.getReplyText());
                return geminiResponse;
            }
            log.info("[FALLBACK TRIGGERED] Gemini Flash unavailable. Cascading to Tier 3 Edge Local Engine.");
        }

        // 3. TIER 3 (Edge Local Agent): Try Local OpenJarvis / Ollama
        ChatResponse localJarvisFallback = callOpenJarvisLocal(userQuery, systemPrompt, "llama3.2:3b", allowActuation);
        if (localJarvisFallback != null && localJarvisFallback.isSuccess()) {
            recordConversationTurn(userQuery, localJarvisFallback.getReplyText());
            return localJarvisFallback;
        }

        // 4. TIER 4 (Deterministic Zero-Latency Safety): In-Memory Agronomy Heuristics
        ChatResponse localResponse = processLocalAgronomyQuery(userQuery, allowActuation);
        recordConversationTurn(userQuery, localResponse.getReplyText());
        return localResponse;
    }

    private void recordConversationTurn(String userQuery, String assistantReply) {
        conversationHistory.addLast(Map.of("role", "user", "text", userQuery));
        conversationHistory.addLast(Map.of("role", "model", "text", assistantReply));
        while (conversationHistory.size() > MAX_HISTORY_TURNS * 2) {
            conversationHistory.pollFirst();
        }
    }

    /**
     * OpenJarvis / Local Ollama Edge Inference Engine (Offline-First Autonomy)
     */
    private ChatResponse callOpenJarvisLocal(String userQuery, String systemPrompt, String model, boolean allowActuation) {
        try {
            String activeModel = (model != null && !model.isBlank() && !model.contains("openjarvis")) ? model : "phi4-mini";
            String endpoint = openJarvisEndpoint.trim() + "/api/generate";

            Map<String, Object> payload = Map.of(
                    "model", activeModel,
                    "system", systemPrompt,
                    "prompt", userQuery,
                    "stream", false
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String rawReply = root.path("response").asText();

                if (!rawReply.isBlank()) {
                    String emotion = extractEmotion(rawReply, userQuery);
                    ActuationCommand executedAction = parseAndExecuteAction(rawReply, allowActuation);
                    String cleanReply = sanitizeReply(rawReply);

                    return new ChatResponse(cleanReply, executedAction, "openjarvis-" + activeModel, true, emotion);
                }
            }
        } catch (Exception e) {
            // Local edge agent offline, pass to next provider
        }
        return null;
    }

    private ChatResponse callGeminiApi(String userQuery, String systemPrompt, String apiKey, String model, boolean allowActuation) {
        String targetModel = (model != null && model.startsWith("gemini-")) ? model : (geminiDefaultModel != null && !geminiDefaultModel.isBlank() ? geminiDefaultModel : "gemini-3.8-flash");
        List<String> candidateModels = new ArrayList<>();
        candidateModels.add(targetModel);
        for (String candidate : geminiCandidateModels.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty() && !candidateModels.contains(trimmed)) {
                candidateModels.add(trimmed);
            }
        }

        for (String activeModel : candidateModels) {
            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey.trim();

                List<Map<String, Object>> contentsList = new ArrayList<>();

                // Insert previous conversation history for continuous context memory
                for (Map<String, String> turn : conversationHistory) {
                    contentsList.add(Map.of(
                            "role", turn.get("role"),
                            "parts", List.of(Map.of("text", turn.get("text")))
                    ));
                }

                // Add current turn with system grounding injected
                String currentTurnText = systemPrompt + "\n\nUser Question/Dialogue: \"" + userQuery + "\"";
                contentsList.add(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", currentTurnText))
                ));

                Map<String, Object> payload = new HashMap<>();
                payload.put("contents", contentsList);
                payload.put("generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 1000
                ));

                String requestBody = objectMapper.writeValueAsString(payload);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(6))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String rawReply = root.at("/candidates/0/content/parts/0/text").asText();

                    if (!rawReply.isBlank()) {
                        String emotion = extractEmotion(rawReply, userQuery);
                        ActuationCommand executedAction = parseAndExecuteAction(rawReply, allowActuation);
                        String cleanReply = sanitizeReply(rawReply);

                        return new ChatResponse(cleanReply, executedAction, activeModel, true, emotion);
                    }
                } else {
                    log.warn("Gemini API ({}) non-200 status: {}", activeModel, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Exception calling Gemini model {}: {}", activeModel, e.getMessage());
            }
        }
        return null;
    }

    private ChatResponse callGroqApi(String userQuery, String systemPrompt, String apiKey, String model, boolean allowActuation) {
        String activeModel = (model != null && !model.isBlank() && !model.startsWith("gemini-") && !model.contains("openjarvis"))
                ? model : (groqDefaultModel != null && !groqDefaultModel.isBlank() ? groqDefaultModel : "openai/gpt-oss-120b");

        List<String> candidateGroqModels = new ArrayList<>();
        candidateGroqModels.add(activeModel);
        for (String candidate : groqCandidateModels.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty() && !candidateGroqModels.contains(trimmed)) {
                candidateGroqModels.add(trimmed);
            }
        }
        String endpoint = "https://api.groq.com/openai/v1/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (Map<String, String> turn : conversationHistory) {
            messages.add(Map.of(
                    "role", "user".equals(turn.get("role")) ? "user" : "assistant",
                    "content", turn.get("text")
            ));
        }

        messages.add(Map.of("role", "user", "content", userQuery));

        for (String targetModel : candidateGroqModels) {
            try {
                Map<String, Object> payload = Map.of(
                        "model", targetModel,
                        "messages", messages,
                        "temperature", 0.7,
                        "max_tokens", 500
                );

                String requestBody = objectMapper.writeValueAsString(payload);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .timeout(Duration.ofSeconds(4))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String rawReply = root.at("/choices/0/message/content").asText();

                    String spokenReply = rawReply.replaceAll("(?s)<think>.*?</think>", "").trim();

                    String emotion = extractEmotion(spokenReply, userQuery);
                    ActuationCommand executedAction = parseAndExecuteAction(spokenReply, allowActuation);
                    String cleanReply = sanitizeReply(spokenReply);

                    return new ChatResponse(cleanReply, executedAction, "groq-" + targetModel, true, emotion);
                } else {
                    log.warn("Groq model ({}) non-200 status: {}", targetModel, response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Exception calling Groq model {}: {}", targetModel, e.getMessage());
            }
        }
        return null;
    }

    private String extractEmotion(String reply, String userQuery) {
        Matcher matcher = EMOTION_PATTERN.matcher(reply);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase().trim();
        }
        String combined = (reply + " " + userQuery).toLowerCase();
        if (combined.contains("mold") || combined.contains("spike") || combined.contains("warning") || combined.contains("danger") || combined.contains("burn") || combined.contains("rot")) {
            return "serious";
        } else if (combined.contains("harvest") || combined.contains("optimal") || combined.contains("great") || combined.contains("happy") || combined.contains("anniversary") || combined.contains("congrat")) {
            return "happy";
        } else if (combined.contains("calculate") || combined.contains("diagnose") || combined.contains("analyze") || combined.contains("status")) {
            return "thinking";
        }
        return "helpful";
    }

    private String sanitizeReply(String raw) {
        return raw.replaceAll("\\[ACTION:.*?\\]", "")
                  .replaceAll("\\[EMOTION:.*?\\]", "")
                  .replaceAll("(?s)<think>.*?</think>", "")
                  .trim();
    }

    private ActuationCommand parseAndExecuteAction(String rawReply, boolean allowActuation) {
        Matcher matcher = ACTION_PATTERN.matcher(rawReply);
        if (matcher.find()) {
            String actionJson = matcher.group(1);
            try {
                JsonNode actionNode = objectMapper.readTree(actionJson);
                String actionType = actionNode.path("type").asText();
                ActuationCommand cmd = null;

                if ("ventilate".equalsIgnoreCase(actionType)) {
                    cmd = new ActuationCommand("FAN_01", "VENTILATE", 600, actionNode.path("rpm").asInt(2400), "AI_VOICE_COMMAND");
                } else if ("switch_crop".equalsIgnoreCase(actionType)) {
                    cmd = new ActuationCommand("CROP_PROFILE", "SWITCH_CROP", null, null, "AI_VOICE_COMMAND");
                    cmd.setCrop(actionNode.path("crop").asText("mushroom"));
                } else if ("simulate_spike".equalsIgnoreCase(actionType)) {
                    cmd = new ActuationCommand("SIMULATION", "SPIKE", null, null, "AI_VOICE_COMMAND");
                } else if ("reset_optimal".equalsIgnoreCase(actionType)) {
                    cmd = new ActuationCommand("SIMULATION", "RESET", null, null, "AI_VOICE_COMMAND");
                }

                if (cmd != null && allowActuation) {
                    telemetryService.executeActuation(cmd);
                    return cmd;
                } else if (cmd != null) {
                    log.warn("[SECURITY] AI-triggered actuation suppressed: actuation is disabled for this context.");
                    audit.aiActuationSuppressed("system");
                    return null;
                }
            } catch (Exception e) {
                log.warn("Could not parse AI action JSON: {}", e.getMessage());
            }
        }
        return null;
    }

    public ChatResponse processLocalAgronomyQuery(String userQuery) {
        return processLocalAgronomyQuery(userQuery, false);
    }

    public ChatResponse processLocalAgronomyQuery(String userQuery, boolean allowActuation) {
        String queryLower = userQuery.toLowerCase();
        String replyText;
        String emotion = "helpful";
        ActuationCommand action = null;

        if (queryLower.contains("green") || queryLower.contains("mold") || queryLower.contains("trichoderma") || queryLower.contains("patch")) {
            replyText = "Green patches indicate Trichoderma mold. Isolate the affected substrate immediately, spot treat with 3% hydrogen peroxide, and ramp fresh air exhaust.";
            emotion = "serious";
        } else if (queryLower.contains("tip burn") || queryLower.contains("necrosis") || queryLower.contains("lettuce") || queryLower.contains("leaf")) {
            replyText = "Tip burn in greens is caused by calcium deficiency from low transpiration. Increase canopy air velocity and reduce nutrient EC by 0.2 mS/cm.";
            emotion = "serious";
        } else if (queryLower.contains("fan") || queryLower.contains("ventilate") || queryLower.contains("air") || queryLower.contains("exhaust") || queryLower.contains("fae")) {
            action = new ActuationCommand("FAN_01", "VENTILATE", 600, 2400, "RULE_ENGINE_COMMAND");
            if (allowActuation) {
                telemetryService.executeActuation(action);
            } else {
                log.warn("[SECURITY] AI-triggered fan actuation suppressed (actuation disabled).");
                audit.aiActuationSuppressed("RULE_ENGINE");
                action = null;
            }
            replyText = "Initiating 10-minute exhaust purge cycle at 2,400 RPM. Target CO2 reduction to 750 ppm.";
            emotion = "thinking";
        } else if (queryLower.contains("humidity") || queryLower.contains("rh") || queryLower.contains("mist") || queryLower.contains("moisture")) {
            TelemetryRecord rec = telemetryService.getCurrentStateRecord();
            replyText = String.format("Current humidity is %d%%. For %s fruiting, maintain between 85%% and 95%% relative humidity.",
                    Math.round(rec.getHumidityRh()), rec.getCropType());
            emotion = "helpful";
        } else if (queryLower.contains("temp") || queryLower.contains("heat") || queryLower.contains("cold") || queryLower.contains("degree")) {
            TelemetryRecord rec = telemetryService.getCurrentStateRecord();
            replyText = String.format("Current chamber temperature is %.1f°C. Optimal range for your %s profile is 18°C to 24°C.",
                    rec.getTempC(), rec.getCropType());
            emotion = "helpful";
        } else if (queryLower.contains("co2") || queryLower.contains("carbon") || queryLower.contains("ppm") || queryLower.contains("air quality")) {
            TelemetryRecord rec = telemetryService.getCurrentStateRecord();
            replyText = String.format("Current CO2 level is %d ppm. Target threshold is below 900 ppm during mushroom fruiting to prevent long stems.",
                    Math.round(rec.getCo2Ppm()));
            emotion = "helpful";
        } else if (queryLower.contains("harvest") || queryLower.contains("yield") || queryLower.contains("pinhead") || queryLower.contains("fruiting")) {
            replyText = "Pinheads mature into harvestable flushes in 4 to 6 days. Harvest before cap margins curl upwards to maintain maximum shelf life.";
            emotion = "happy";
        } else if (queryLower.contains("anniversary") || queryLower.contains("celebrat") || queryLower.contains("congrat") || queryLower.contains("awesome") || queryLower.contains("great job")) {
            replyText = "Thank you! Wishing you a wonderful celebration! Meanwhile, Chamber 1 environment is completely stable and automated.";
            emotion = "happy";
        } else if (queryLower.contains("who are you") || queryLower.contains("what can you do") || queryLower.contains("capabilities") || queryLower.contains("help")) {
            replyText = "I am SAARTHI, your autonomous indoor farming copilot. I monitor CO2, humidity, and temperature in real-time, diagnose crop pathologies, and automate exhaust blowers.";
            emotion = "helpful";
        } else if (queryLower.contains("hi") || queryLower.contains("hello") || queryLower.contains("hey") || queryLower.contains("good morning") || queryLower.contains("good evening")) {
            replyText = "Hello! I am Saarthi, your AI agronomist copilot. All chamber parameters are stable. How can I assist you today?";
            emotion = "helpful";
        } else {
            TelemetryRecord rec = telemetryService.getCurrentStateRecord();
            replyText = String.format("I'm monitoring Chamber 1 for you. Active Crop: %s, CO2: %d ppm, Humidity: %d%%, Temp: %.1f°C. What would you like me to analyze or optimize?",
                    rec.getCropType(), Math.round(rec.getCo2Ppm()), Math.round(rec.getHumidityRh()), rec.getTempC());
            emotion = "helpful";
        }

        return new ChatResponse(replyText, action, "saarthi-generative-core", true, emotion);
    }

    private String buildSystemGroundingPrompt(TelemetryRecord current) {
        StringBuilder memorySummary = new StringBuilder();
        memorySummary.append("LIFECYCLE LEDGER (Recent Milestones):\n");
        for (int i = Math.max(0, cropLifecycleLedger.size() - 3); i < cropLifecycleLedger.size(); i++) {
            Map<String, Object> event = cropLifecycleLedger.get(i);
            memorySummary.append(String.format("- Day %s: [%s] %s\n", event.get("day"), event.get("type"), event.get("description")));
        }

        return "You are SAARTHI, an intelligent, empathetic, and expert autonomous horticulture AI copilot.\n" +
                "You are holding a natural, interactive dialogue with the grower (Vikram).\n" +
                "You can discuss general topics, answer casual questions with warmth and personality, and provide expert agronomy explanations.\n\n" +
                "LIVE CHAMBER SENSORS:\n" +
                "- Active Crop: " + current.getCropType() + "\n" +
                "- CO2: " + Math.round(current.getCo2Ppm()) + " ppm\n" +
                "- Humidity: " + Math.round(current.getHumidityRh()) + "%\n" +
                "- Temp: " + current.getTempC() + " °C\n" +
                "- Exhaust Fan: " + current.getFanRpm() + " RPM (" + current.getFanDuty() + "%)\n\n" +
                memorySummary.toString() + "\n" +
                "EMOTION TAGS (Pick one at the start of your response):\n" +
                "- [EMOTION:HELPFUL] (helpful, friendly advice, answers)\n" +
                "- [EMOTION:HAPPY] (celebrations, greetings, harvests, milestones)\n" +
                "- [EMOTION:SERIOUS] (mold, alarms, CO2 spikes, diseases)\n" +
                "- [EMOTION:THINKING] (calculations, complex biology diagnostics)\n\n" +
                "COMMAND ACTIONS (append [ACTION:{...}] if user requests actuation):\n" +
                "- [ACTION:{\"type\":\"ventilate\",\"rpm\":2400}]\n" +
                "- [ACTION:{\"type\":\"switch_crop\",\"crop\":\"hydro\"}]\n\n" +
                "GUIDELINES:\n" +
                "1. Answer naturally, intelligently, and conversationally. Do NOT repeat robotic scripts like 'I analyzed...'.\n" +
                "2. Provide clear reasoning when asked questions.\n" +
                "3. Keep voice replies concise (1-2 sentences) so they sound great spoken out loud.";
    }
}
