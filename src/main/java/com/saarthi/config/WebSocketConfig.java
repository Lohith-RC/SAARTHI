package com.saarthi.config;

import com.saarthi.websocket.TelemetryWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSocket Configuration mapping /ws/telemetry to TelemetryWebSocketHandler.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetryWebSocketHandler telemetryWebSocketHandler;

    public WebSocketConfig(TelemetryWebSocketHandler telemetryWebSocketHandler) {
        this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(telemetryWebSocketHandler, "/ws/telemetry")
                .setAllowedOrigins("*");
    }
}
