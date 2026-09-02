package com.saarthi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emergency Outbound Alert Service: Dispatches critical climate and watchdog alerts to Webhooks
 * (Discord, Slack, Telegram, or SMS/IVR gateways) and maintains recent alert history for the UI.
 */
@Service
public class AlertService {

    @Value("${saarthi.alert.webhook-url:}")
    private String webhookUrl;

    @Value("${saarthi.alert.min-interval-seconds:60}")
    private long minIntervalSeconds;

    @Value("${saarthi.alert.allow-private-webhooks:false}")
    private boolean allowPrivateWebhooks;

    private final ObjectMapper objectMapper;
    private final List<Map<String, Object>> alertHistory = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastAlertTimePerKey = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AlertService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
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

        // Keep last 50 alerts in memory
        alertHistory.add(0, alert);
        if (alertHistory.size() > 50) {
            alertHistory.remove(alertHistory.size() - 1);
        }

        System.out.printf("📢 [ALERT DISPATCHED] [%s] %s: %s%n", level, title, message);

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
                System.err.println("🛡️ [SSRF BLOCKED] Disallowed private/loopback webhook target: " + host);
                return false;
            }

            String ip = address.getHostAddress();
            if (ip.startsWith("169.254.") || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.16.")) {
                System.err.println("🛡️ [SSRF BLOCKED] Disallowed internal/metadata IP target: " + ip);
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("🛡️ [SSRF BLOCKED] Webhook host resolution error: " + e.getMessage());
            return false;
        }
    }

    private void sendWebhookPayload(Map<String, Object> alert) {
        if (!isSafePublicWebhookUrl(webhookUrl)) {
            return;
        }

        new Thread(() -> {
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
                                System.out.println("✅ Alert webhook delivered successfully.");
                            } else {
                                System.err.println("⚠️ Alert webhook responded with status: " + res.statusCode());
                            }
                        })
                        .exceptionally(ex -> {
                            System.err.println("⚠️ Alert webhook dispatch error: " + ex.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("⚠️ Failed to dispatch webhook alert: " + e.getMessage());
            }
        }).start();
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return Collections.unmodifiableList(alertHistory);
    }
}
