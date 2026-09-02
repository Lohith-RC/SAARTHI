package com.saarthi;

import com.saarthi.config.SecurityConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Project SAARTHI: Smart Autonomous Assistant for Resilient Tech-Driven Horticulture & Indoor Farming
 * Master Spring Boot 3.x Entry Point with Scheduled Watchdogs & WebSocket Telemetry.
 */
@SpringBootApplication
@EnableScheduling
public class SaarthiApplication {

    private static final Logger log = LoggerFactory.getLogger(SaarthiApplication.class);

    private final SecurityConfigValidator securityConfigValidator;

    public SaarthiApplication(SecurityConfigValidator securityConfigValidator) {
        this.securityConfigValidator = securityConfigValidator;
    }

    public static void main(String[] args) {
        loadDotEnvIfExists();
        SpringApplication.run(SaarthiApplication.class, args);
        log.info("SAARTHI Java 21 + Spring Boot 3.x system ONLINE. HUD: http://localhost:8080, REST: http://localhost:8080/api/v1/telemetry/current, WS: ws://localhost:8080/ws/telemetry");
    }

    /**
     * Fail-fast at startup: when the application is fully ready, invoke the
     * security configuration validator which refuses to boot on missing or
     * default device/operator tokens.
     */
    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        securityConfigValidator.validate();
    }

    /**
     * Reads .env file from project root and sets system properties securely if not already set.
     * .env is dev-only: real deployments must inject secrets via environment/secret managers.
     */
    private static void loadDotEnvIfExists() {
        try {
            File envFile = new File(".env");
            if (envFile.exists()) {
                List<String> lines = Files.readAllLines(Path.of(".env"));
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int eqIdx = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eqIdx).trim();
                    String val = trimmed.substring(eqIdx + 1).trim();

                    if (!key.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, val);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load .env file: {}", e.getMessage());
        }
    }
}
