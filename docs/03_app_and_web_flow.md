# 🔄 SAARTHI: Application, Web & User Experience Flows
> **State Machines, Sequence Diagrams & Data Payloads**
> **Backend Engine:** Spring Boot 3.x / Java 21 Reactive Architecture

---

## 1. End-to-End System Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Grower as Grower (User)
    participant Sensor as ESP32 Sensor Node
    participant JavaServer as Spring Boot 3 Backend (Java 21)
    participant HUD as Three.js Digital Twin HUD
    participant Voice as Saarthi Voice Core

    Sensor->>JavaServer: MQTT Publish topic: /chambers/01/telemetry (CO2: 1280 ppm, RH: 93%)
    JavaServer->>JavaServer: AnomalyEvaluatorService: Evaluates Warning Threshold
    JavaServer->>HUD: Spring WebSocket Broadcast { status: "WARNING", co2: 1280 }
    HUD->>HUD: 3D Scene glows Amber Gold (#FFB800)

    alt Grower Enters Room (Ambient Trigger)
        Grower->>HUD: Proximity / App Active
        Voice->>Grower: "Warning: CO2 spike in Chamber 1 at 1,280 ppm. Ventilation recommended."
    else Grower Responds with Hands-Free Voice
        Grower->>Voice: "Hey Saarthi, ventilate for 10 minutes"
        Voice->>JavaServer: REST POST /api/v1/actuate (Target: FAN_01, Duration: 600s)
        JavaServer->>Sensor: MQTT Command topic: /chambers/01/control { relay_on: 23, duration: 600 }
        Sensor-->>JavaServer: ACK (Relay Active)
        Voice->>Grower: "Exhaust fans running. CO2 dropping to optimal range."
    end
```

---

## 2. Real-Time Telemetry JSON Schema (Spring Boot DTO)

```json
{
  "deviceId": "SAARTHI_NODE_001",
  "facilityId": "URBAN_MUSHROOM_HQ",
  "chamberId": "ROOM_01_OYSTER",
  "timestamp": "2026-09-01T16:05:00.000Z",
  "metrics": {
    "co2Ppm": 845,
    "humidityRh": 91.2,
    "temperatureC": 22.4,
    "fanStatus": "OFF",
    "relayState": 0
  },
  "diagnostics": {
    "wifiRssi": -58,
    "sensorHealth": "OPTIMAL",
    "uptimeSeconds": 86420,
    "calibrationDriftIndex": 0.02
  }
}
```

---

## 3. UI State Transitions & Visual Logic

| State | Trigger Criteria | Primary HUD Color | 3D Lighting Effect | Audio Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **🟢 OPTIMAL** | $CO_2 < 900$ ppm, RH 85–92%, Temp 20–24°C | Neon Emerald (`#00F5A0`) | Soft Green Ambient Glow | Silent ambient mode |
| **🟡 WARNING** | $CO_2$ 900–1,300 ppm OR RH $<80\%$ | Amber Gold (`#FFB800`) | Amber PointLight Pulse | Speaks warning upon entry |
| **🔴 CRITICAL** | $CO_2 > 1,400$ ppm OR Power Outage | Pulsing Crimson (`#FF3366`) | Rapid Red Emergency Flash | Triggers WhatsApp + IVR Phone Call |
