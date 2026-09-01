# ⚙️ SAARTHI: API Reference & Firmware State Machine
> **Java Spring Boot REST / WebSocket Endpoints & ESP32 Microcontroller Lifecycle**

---

## 1. Java Spring Boot API Controller Endpoints

### A. Telemetry Ingestion Endpoint
* **POST** `/api/v1/telemetry/push`
* **Spring Controller:** `@PostMapping(value = "/push", consumes = "application/json")`
* **Headers:** `X-Device-Token: <UUID4_DEVICE_SECRET>`
* **Java DTO Payload:**
  ```json
  {
    "deviceId": "SAARTHI_001",
    "co2Ppm": 840.0,
    "humidityRh": 91.5,
    "tempC": 22.1,
    "relayState": 0,
    "timestamp": 1772436000
  }
  ```
* **Response:** `200 OK` `{ "status": "ACK", "actuateOverride": null }`

### B. Real-Time Actuation Controller
* **POST** `/api/v1/devices/{deviceId}/actuate`
* **Spring Controller:** `@PostMapping("/{deviceId}/actuate")`
* **Payload:**
  ```json
  {
    "targetPin": 23,
    "action": "RELAY_ON",
    "durationSeconds": 600,
    "reason": "VOICE_COMMAND_USER"
  }
  ```

### C. Spring WebSocket STOMP Endpoint
* **Endpoint:** `/ws-saarthi`
* **Topic Subscription:** `/topic/chambers/{chamberId}/telemetry`
* **Message Broker:** Spring In-Memory Broker / RabbitMQ

---

## 2. ESP32 Firmware State Machine (C++ to Spring Boot)

```
  [ 1. BOOT ] ──► [ 2. WI-FI HANDSHAKE ] ──► [ 3. PROBE CALIBRATION ]
                                                       │
  ┌────────────────────────────────────────────────────┘
  ▼
  [ 4. ASYNC TELEMETRY LOOP ] ◄──────┐ (Every 2 seconds)
    ├── Read MQ-135 Analog Voltage    │
    ├── Read DHT22 Digital Stream     │
    ├── Publish MQTT to Spring Boot   │
    └── Stream to WebSocket Cloud ────┘
         │ (If Wi-Fi / Cloud drops)
         ▼
  [ 5. LOCAL FAILSAFE MODE ]
    └── If CO2 > 1400 ppm -> Force GPIO 23 Relay HIGH autonomously!
```
