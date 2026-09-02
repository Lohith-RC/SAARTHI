package com.saarthi.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.model.TelemetryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring WebSocket Text Handler streaming real-time telemetry updates to Three.js HUD clients.
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket client connected: {} (total: {})", session.getId(), sessions.size());
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
