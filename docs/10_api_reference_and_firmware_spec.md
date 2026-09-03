# ⚙️ SAARTHI: API Reference & Firmware State Machine
> **Java Spring Boot REST / WebSocket Endpoints & ESP32 Microcontroller Lifecycle**

---

## 1. Java Spring Boot API Controller Endpoints

### A. Telemetry Ingestion Endpoint
* **POST** `/api/v1/telemetry/push`
* **Auth:** `X-Device-Token` (device principal only; operator and anonymous are rejected)
* **Payload:**
  ```json
  {
    "deviceId": "ESP32_NODE_01",
    "co2Ppm": 840.0,
    "humidityRh": 91.5,
    "tempC": 22.1,
    "fanRpm": 1420,
    "cropType": "mushroom"
  }
  ```
* **Validation:** `deviceId` (max 64 chars, `[a-zA-Z0-9_\-.]`), CO2 0–10,000, RH 0–100, temp −40–80 °C, fan 0–3000 RPM.
* **Response:** `200 OK` `{ "status": "ACK", "deviceId": "...", "chamberStatus": "OPTIMAL", "timestamp": 1772436000 }`

### B. Telemetry Read Endpoints (public, like `/current`)
* **GET** `/api/v1/telemetry/current` — default chamber (`SAARTHI_001`) live snapshot
* **GET** `/api/v1/telemetry/chambers` — full fleet snapshot (all chambers, sorted)
* **GET** `/api/v1/telemetry/chambers/{deviceId}` — single chamber snapshot (`404` when unknown)
* **GET** `/api/v1/telemetry/history?deviceId=...` — recent persisted records (optional per-chamber filter)

### C. Actuation Controller (operator-only)
* **POST** `/api/v1/actuate`
  ```json
  {
    "target": "FAN_01",
    "action": "VENTILATE",
    "durationSeconds": 600,
    "rpm": 2400,
    "deviceId": "ESP32_NODE_01"
  }
  ```
  `deviceId` is optional and defaults to `SAARTHI_001` (backwards compatible).
* **POST** `/api/v1/simulate/spike` — CO2 spike on the default chamber (operator-only)
* **POST** `/api/v1/simulate/reset` — reset the default chamber to baseline (operator-only)

### D. AI Copilot Endpoints (operator-only unless noted)
* **POST** `/api/v1/ai/chat` — multi-engine reasoning (Gemini 3.8 / Groq GPT-OSS / local OpenJarvis fallback)
* **GET** `/api/v1/ai/status` — public engine status
* **POST** `/api/v1/ai/tts` — ElevenLabs text-to-speech (server-side key only)
* **GET** `/api/v1/ai/memory/timeline` — persistent crop lifecycle ledger
* **POST** `/api/v1/ai/memory/log` — append milestone (validated: day 1–365, bounded strings)
* **POST** `/api/v1/ai/generate-crop-image` — Pollinations AI diagnostic image URL

### E. Alert Endpoints (operator-only)
* **GET** `/api/v1/alerts/recent` — in-memory recent alerts (instant)
* **GET** `/api/v1/alerts/history?limit=100` — persistent alert history from the database (max 500)
* **POST** `/api/v1/alerts/test?message=...` — manual test dispatch

### F. WebSocket
* **Endpoint:** `/ws/telemetry` (token via query `?token=` or `X-Operator-Token`/`X-Device-Token` handshake headers)
* **Legacy raw record** — default chamber broadcast (unchanged)
* **`{"type":"FLEET_SNAPSHOT","chambers":[...]}`** — pushed once on connect
* **`{"type":"CHAMBER_UPDATE","deviceId":...,"record":{...}}`** — per-chamber live update

### G. Actuator (operator-only)
* `/actuator/health`, `/actuator/info`, `/actuator/metrics`

---

## 2. ESP32 Firmware State Machine (C++ to Spring Boot)

Canonical firmware: **`firmware/saarthi_esp32_firmware.ino`** (FreeRTOS dual-core —
sensor/relay control on Core 1, Wi-Fi + HTTP telemetry on Core 0).

```
  [ 1. BOOT ] ──► [ 2. WI-FI HANDSHAKE ] ──► [ 3. SENSOR CALIBRATION ]
                                                       │
  ┌────────────────────────────────────────────────────┘
  ▼
  [ 4. ASYNC TELEMETRY LOOP ] ◄──────┐ (Every 2 seconds)
    ├── Read MQ-135 Analog Voltage    │
    ├── Read DHT22 Digital Stream     │
    ├── POST JSON to Spring Boot      │
    └── Stream to WebSocket Cloud ────┘
         │ (If Wi-Fi / Cloud drops)
         ▼
  [ 5. LOCAL FAILSAFE MODE ]
    └── If CO2 > 1400 ppm -> Force GPIO 23 Relay HIGH autonomously!
```

Build-time configuration (no committed defaults):
```
-DSAARTHI_DEVICE_TOKEN='"my-secret"'        # required; upload disabled without it
-DSAARTHI_DEVICE_ID='"ESP32_NODE_02"'       # optional; fleet identity (default ESP32_NODE_01)
```