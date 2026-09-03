package com.saarthi.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.config.SecurityConfigValidator;
import com.saarthi.model.TelemetryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Spring WebSocket Text Handler streaming real-time telemetry updates to Three.js HUD clients.
 * Authenticates incoming connections against configured device/operator tokens.
 *
 * <p>Wire format (additive, fully backwards compatible):</p>
 * <ul>
 *   <li>Raw {@code TelemetryRecord} JSON (legacy, default chamber only)</li>
 *   <li>{@code {"type":"CHAMBER_UPDATE","deviceId":...,"record":{...}}} for any chamber</li>
 *   <li>{@code {"type":"FLEET_SNAPSHOT","chambers":[...]}} sent once on connect</li>
 * </ul>
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityConfigValidator validator;

    /** Registered by TelemetryService after startup; serves the on-connect fleet snapshot. */
    private volatile Supplier<List<TelemetryRecord>> fleetSnapshotSupplier;

    @Value("${saarthi.security.enforce-token:true}")
    private boolean enforceToken;

    public TelemetryWebSocketHandler(SecurityConfigValidator validator) {
        this.validator = validator;
    }

    public void setFleetSnapshotSupplier(Supplier<List<TelemetryRecord>> supplier) {
        this.fleetSnapshotSupplier = supplier;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (enforceToken) {
            String token = extractToken(session);
            boolean valid = token != null && (validator.matchesDeviceToken(token) || validator.matchesOperatorToken(token));
            if (!valid) {
                log.warn("[WS AUTH DENIED] WebSocket client {} rejected: missing or invalid authentication token.", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        }
        sessions.add(session);
        log.info("WebSocket client connected and authenticated: {} (total: {})", session.getId(), sessions.size());

        // Push the full fleet snapshot to the freshly connected client.
        Supplier<List<TelemetryRecord>> supplier = fleetSnapshotSupplier;
        if (supplier != null) {
            try {
                List<TelemetryRecord> snapshot = supplier.get();
                if (snapshot != null && !snapshot.isEmpty()) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("type", "FLEET_SNAPSHOT");
                    message.put("chambers", snapshot);
                    sendToSession(session, objectMapper.writeValueAsString(message));
                }
            } catch (Exception e) {
                log.warn("Failed to send fleet snapshot to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private String extractToken(WebSocketSession session) {
        // 1. Check URI query parameters (e.g., /ws/telemetry?token=...)
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    String key = pair[0].trim();
                    if ("token".equalsIgnoreCase(key) || "operatorToken".equalsIgnoreCase(key) || "deviceToken".equalsIgnoreCase(key)) {
                        return pair[1].trim();
                    }
                }
            }
        }

        // 2. Check handshake headers
        List<String> opTokens = session.getHandshakeHeaders().get("X-Operator-Token");
        if (opTokens != null && !opTokens.isEmpty()) return opTokens.get(0).trim();

        List<String> devTokens = session.getHandshakeHeaders().get("X-Device-Token");
        if (devTokens != null && !devTokens.isEmpty()) return devTokens.get(0).trim();

        return null;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {} (remaining: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        log.warn("WebSocket transport error from {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Broadcast live telemetry record to all connected WebGL HUD clients.
     * Legacy raw-format channel (kept for the default chamber).
     */
    public void broadcastTelemetry(TelemetryRecord record) {
        if (sessions.isEmpty()) return;
        try {
            broadcastJson(objectMapper.writeValueAsString(record));
        } catch (Exception e) {
            log.warn("Error serializing telemetry for WebSocket broadcast: {}", e.getMessage());
        }
    }

    /**
     * Typed fleet message for a single chamber update: {@code {"type":"CHAMBER_UPDATE",...}}.
     */
    public void broadcastChamberUpdate(String deviceId, TelemetryRecord record) {
        if (sessions.isEmpty()) return;
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "CHAMBER_UPDATE");
            message.put("deviceId", deviceId);
            message.put("record", record);
            broadcastJson(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Error serializing chamber update for WebSocket broadcast: {}", e.getMessage());
        }
    }

    private void broadcastJson(String jsonPayload) {
        TextMessage message = new TextMessage(jsonPayload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    private void sendToSession(WebSocketSession session, String jsonPayload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(jsonPayload));
        }
    }
}