package com.saarthi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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

    public static void main(String[] args) {
        loadDotEnvIfExists();
        SpringApplication.run(SaarthiApplication.class, args);
        System.out.println("\n=======================================================");
        System.out.println(" 🌿 SAARTHI Java 17/21 + Spring Boot 3.x System ONLINE");
        System.out.println(" 🌐 3D Digital Twin HUD: http://localhost:8080");
        System.out.println(" 📊 REST API Docs:       http://localhost:8080/api/v1/telemetry/current");
        System.out.println(" 🔌 WebSocket Endpoint:  ws://localhost:8080/ws/telemetry");
        System.out.println(" 🗄️ H2 Database Console: http://localhost:8080/h2-console");
        System.out.println("=======================================================\n");
    }

    /**
     * Reads .env file from project root and sets system properties securely if not already set.
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
                System.out.println("🔒 [SECURITY] Loaded private environment configuration from .env");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not load .env file: " + e.getMessage());
        }
    }
}
