# 🗺️ SAARTHI: Comprehensive Technical Implementation Plan
> **Technical Stack, System Architecture & Phased Engineering Roadmap**
> **Enterprise Architecture:** Java 21 LTS (Spring Boot 3.x) + Three.js 3D WebGL

---

## 1. System Architecture Diagram

```
┌──────────────────────────┐          MQTT (Paho) / WebSockets ┌──────────────────────────┐
│   ESP32 SENSOR NODE      │ ────────────────────────────────► │     SAARTHI CLOUD        │
│ • MQ-135 (CO2 Gas)       │                                   │ • Java 21 + Spring Boot 3│
│ • DHT22 (Temp/RH)        │ ◄──────────────────────────────── │ • Spring WebFlux / Netty │
│ • 1-Ch Relay (Fans)      │           Relay Trigger           │ • TimescaleDB (Logs)     │
└──────────────────────────┘                                   └────────────┬─────────────┘
                                                                            │ Spring WebSocket Push
                                                                            ▼
                                                               ┌──────────────────────────┐
                                                               │  DIGITAL TWIN HUD (WEB)  │
                                                               │ • Three.js 3D WebGL      │
                                                               │ • Web Speech Voice Core  │
                                                               │ • Real-Time Telemetry    │
                                                               └──────────────────────────┘
```

---

## 2. Technology Stack & Component Selection

| Layer | Technology | Rationale & Justification |
| :--- | :--- | :--- |
| **Frontend & 3D HUD** | **Three.js (WebGL) + HTML5 / CSS3** | Zero build-step overhead, 360° orbital chamber inspection, runs on any browser/phone. |
| **Voice Engine** | **Web Speech API (`SpeechSynthesis` & `Recognition`)** | Free, zero API latency, native browser audio, noise-filtered on client. |
| **Backend Core** | **Java 21 (LTS) + Spring Boot 3.x** | Enterprise-grade, Virtual Threads (Project Loom) handle millions of concurrent sensor pings. |
| **Real-Time IoT Broker** | **Spring Integration + Eclipse Paho MQTT** | Industrial-grade MQTT broker connectivity, handles spotty farm Wi-Fi with QoS 1. |
| **AI & Knowledge Engine** | **Spring AI + ONNX Runtime for Java** | Native Java RAG integration over PostgreSQL pgvector agronomy manuals. |
| **Database & Persistence** | **Spring Data JPA + TimescaleDB (PostgreSQL)** | Hypertable compression reduces 2-second raw sensor pings by 98% into 5-minute rolling logs. |
| **Security & Multi-Tenancy** | **Spring Security 6 + JWT** | Multi-tenant B2B farm access control (Farm Owner vs Room Worker roles). |
| **Microcontroller** | **ESP32-WROOM-32D (C++ / Arduino)** | Dual-core 240MHz, built-in Wi-Fi & BLE, streams JSON telemetry directly to Spring Boot. |
| **Alert Engine** | **Twilio / WhatsApp Business Cloud API** | Tiered alert escalation (WhatsApp interactive buttons -> automated voice calls). |

---

## 3. Engineering Work Breakdown Structure (WBS)

### Phase 1: Frontend 3D Digital Twin & Voice Engine
* [x] Build Dark Obsidian (`#080C10`) Glassmorphic Dashboard layout.
* [x] Integrate Three.js 3D Chamber OrbitControls and procedural crop rendering.
* [x] Implement live Web Speech API recognition and audio synthesis.
* [x] Build interactive telemetry slider rig and breath spike anomaly simulator.

### Phase 2: Java Spring Boot Backend & MQTT Broker
* [x] Initialize Spring Boot 3.x project with `spring-boot-starter-websocket` (REST ingestion; MQTT/Paho planned).
* [x] Implement `TelemetryWebSocketHandler` to broadcast real-time metrics to connected HUD clients (raw + `CHAMBER_UPDATE` + `FLEET_SNAPSHOT`).
* [x] Configure Spring Data JPA entity mapping for `ChamberTelemetry` (multi-chamber fleet) and `AlertEvent`.
* [x] Implement 60-second Dead-Man's Watchdog scheduler (`@Scheduled(fixedRate = 60000)`), per chamber.
* [x] Multi-chamber fleet backend: per-chamber state, recipes, actuation expiry, and fleet REST/WS endpoints.
* [x] Persistent alert history (Flyway V3 `alert_event`).
* [ ] Spring Integration + Eclipse Paho MQTT broker ingestion (spotty Wi-Fi QoS 1).

### Phase 3: Hardware Firmware & Telemetry Bridge
* [ ] Wire MQ-135 to GPIO 34 (ADC1) and DHT22 to GPIO 4.
* [ ] Flash ESP32 firmware with PubSubClient MQTT streaming to Spring Boot backend.
* [ ] Implement local safety threshold: If $CO_2 > 1,400$ ppm, trip GPIO 23 relay independently of cloud.
