package com.saarthi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.model.AlertEvent;
import com.saarthi.repository.AlertRepository;
import com.saarthi.security.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emergency Outbound Alert Service: Dispatches critical climate and watchdog alerts to Webhooks
 * (Discord, Slack, Telegram, or SMS/IVR gateways) and maintains recent alert history for the UI.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Value("${saarthi.alert.webhook-url:}")
    private String webhookUrl;

    @Value("${saarthi.alert.min-interval-seconds:60}")
    private long minIntervalSeconds;

    @Value("${saarthi.alert.allow-private-webhooks:false}")
    private boolean allowPrivateWebhooks;

    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger audit;
    private final AlertRepository alertRepository;
    private final List<Map<String, Object>> alertHistory = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastAlertTimePerKey = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AlertService(ObjectMapper objectMapper, SecurityAuditLogger audit) {
        this(objectMapper, audit, null);
    }

    /**
     * @param alertRepository optional persistence; when null alerts remain in-memory only.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AlertService(ObjectMapper objectMapper, SecurityAuditLogger audit, AlertRepository alertRepository) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.audit = audit;
        this.alertRepository = alertRepository;
    }

    /**
     * Dispatch an alert with rate-limiting.
     */
    public boolean triggerAlert(String alertKey, String level, String title, String message, Map<String, Object> details) {
        long now = Instant.now().getEpochSecond();
        Long lastTime = lastAlertTimePerKey.get(alertKey);

        // Rate limit duplicate alerts
        if (lastTime != null && (now - lastTime) < minIntervalSeconds) {
            return false;
        }
        lastAlertTimePerKey.put(alertKey, now);

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", UUID.randomUUID().toString());
        alert.put("key", alertKey);
        alert.put("level", level); // "CRITICAL", "WARNING", "INFO"
        alert.put("title", title);
        alert.put("message", message);
        alert.put("timestamp", now);
        alert.put("details", details != null ? details : Map.of());

        // Keep last 50 alerts in memory (instant reads for the HUD)
        alertHistory.add(0, alert);
        if (alertHistory.size() > 50) {
            alertHistory.remove(alertHistory.size() - 1);
        }

        persistAlert(alert);

        log.info("ALERT DISPATCHED level={} title={} message={}", level, title, message);
        audit.alertDispatched(level, title, "system");

        // Send to external webhook asynchronously if configured
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            sendWebhookPayload(alert);
        }

        return true;
    }

    private boolean isSafePublicWebhookUrl(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) return false;
        if (allowPrivateWebhooks) return true;

        try {
            URI uri = URI.create(urlStr.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;

            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                log.warn("[SSRF BLOCKED] Disallowed private/loopback webhook target: {}", host);
                return false;
            }

            String ip = address.getHostAddress();
            if (isPrivateIp(ip)) {
                log.warn("[SSRF BLOCKED] Disallowed internal/metadata IP target: {}", ip);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("[SSRF BLOCKED] Webhook host resolution error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Rejects private, link-local, loopback, documentation, and legacy-local
     * IPv4 ranges and the IPv6 loopback/ULA prefixes. Covers RFC1918, 172.16/12,
     * 169.254/16, 127/8, 10/8, 192.168/16, 100.64/10, 198.18/15 and IPv6 ULA.
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null) return true;
        String cleanIp = ip;
        int slash = cleanIp.indexOf('%');
        if (slash >= 0) cleanIp = cleanIp.substring(0, slash);
        if (cleanIp.equals("::1") || cleanIp.startsWith("fc") || cleanIp.startsWith("fd")) {
            return true;
        }
        if (!cleanIp.contains(".")) return true;
        String[] parts = cleanIp.split("\\.");
        if (parts.length != 4) return true;
        int a = parseIntOctet(parts[0]);
        int b = parseIntOctet(parts[1]);
        int c = parseIntOctet(parts[2]);
        if (a < 0) return true;
        if (a == 10) return true;                       // 10.0.0.0/8
        if (a == 127) return true;                      // loopback
        if (a == 169 && b == 254) return true;          // link-local / metadata
        if (a == 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
        if (a == 192 && b == 168) return true;          // private
        if (a == 100 && b >= 64 && b <= 127) return true; // CGNAT 100.64/10
        if (a == 198 && (b == 18 || b == 19)) return true; // benchmarking 198.18/15
        if (a == 0) return true;                        // this-network
        return false;
    }

    private static int parseIntOctet(String octet) {
        try {
            return Integer.parseInt(octet);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void sendWebhookPayload(Map<String, Object> alert) {
        if (!isSafePublicWebhookUrl(webhookUrl)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("app", "Project SAARTHI");
                payload.put("level", alert.get("level"));
                payload.put("title", alert.get("title"));
                payload.put("message", alert.get("message"));
                payload.put("timestamp", alert.get("timestamp"));
                payload.put("details", alert.get("details"));

                String json = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl.trim()))
                        .timeout(Duration.ofSeconds(6))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> {
                            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                                log.info("Alert webhook delivered successfully.");
                            } else {
                                log.warn("Alert webhook responded with status: {}", res.statusCode());
                            }
                        })
                        .exceptionally(ex -> {
                            log.warn("Alert webhook dispatch error: {}", ex.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                log.warn("Failed to dispatch webhook alert: {}", e.getMessage());
            }
        });
    }

    /**
     * Best-effort persistence: a storage outage must never block or fail alert
     * dispatch, so failures are logged and swallowed.
     */
    private void persistAlert(Map<String, Object> alert) {
        if (alertRepository == null) return;
        try {
            String detailsJson = null;
            Object details = alert.get("details");
            if (details != null) {
                detailsJson = objectMapper.writeValueAsString(details);
                if (detailsJson.length() > 2000) {
                    detailsJson = detailsJson.substring(0, 2000);
                }
            }
            String message = String.valueOf(alert.getOrDefault("message", ""));
            if (message.length() > 2000) message = message.substring(0, 2000);

            alertRepository.save(new AlertEvent(
                    String.valueOf(alert.get("key")),
                    String.valueOf(alert.get("level")),
                    String.valueOf(alert.get("title")),
                    message,
                    detailsJson,
                    ((Number) alert.get("timestamp")).longValue()
            ));
        } catch (Exception e) {
            log.warn("Alert persistence skipped (storage unavailable): {}", e.getMessage());
        }
    }

    /**
     * Persistent alert history from the database (most recent first).
     */
    public List<AlertEvent> getAlertHistory(int limit) {
        if (alertRepository == null) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return alertRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, safeLimit));
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return Collections.unmodifiableList(alertHistory);
    }
}
