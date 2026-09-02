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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring WebSocket Text Handler streaming real-time telemetry updates to Three.js HUD clients.
 * Authenticates incoming connections against configured device/operator tokens.
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityConfigValidator validator;

    @Value("${saarthi.security.enforce-token:true}")
    private boolean enforceToken;

    public TelemetryWebSocketHandler(SecurityConfigValidator validator) {
        this.validator = validator;
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
     */
    public void broadcastTelemetry(TelemetryRecord record) {
        if (sessions.isEmpty()) return;

        try {
            String jsonPayload = objectMapper.writeValueAsString(record);
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
        } catch (Exception e) {
            log.warn("Error serializing telemetry for WebSocket broadcast: {}", e.getMessage());
        }
    }
}
