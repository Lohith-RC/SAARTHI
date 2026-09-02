package com.saarthi.config;

import com.saarthi.websocket.TelemetryWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Spring WebSocket Configuration mapping /ws/telemetry to TelemetryWebSocketHandler.
 * Allowed origins share the same strict allow-list as REST CORS (never {@code *}).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetryWebSocketHandler telemetryWebSocketHandler;

    @Value("${saarthi.cors.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}")
    private String allowedOriginsConfig;

    public WebSocketConfig(TelemetryWebSocketHandler telemetryWebSocketHandler) {
        this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addHandler(telemetryWebSocketHandler, "/ws/telemetry")
                .setAllowedOrigins(origins);
    }
}
