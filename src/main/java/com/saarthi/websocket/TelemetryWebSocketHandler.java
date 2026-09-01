package com.saarthi.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.model.TelemetryRecord;
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

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("🌐 [WebSocket] Client Connected: " + session.getId() + " (Total: " + sessions.size() + ")");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("🔌 [WebSocket] Client Disconnected: " + session.getId() + " (Remaining: " + sessions.size() + ")");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
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
                        System.err.println("Failed to send WebSocket message to session " + session.getId() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error serializing telemetry for WebSocket broadcast: " + e.getMessage());
        }
    }
}
