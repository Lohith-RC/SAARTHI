# 🌿 Project SAARTHI

**Smart Autonomous Assistant for Resilient Tech-Driven Horticulture & Indoor Farming**

> *"You grow the harvest. Saarthi steers the climate."*

[![Render Deployment](https://img.shields.io/badge/Render-Live%20Service-46E3B7?logo=render&logoColor=white)](https://saarthi-backend-bvdl.onrender.com)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Vercel](https://img.shields.io/badge/Vercel-Compatible-000000?logo=vercel&logoColor=white)](https://vercel.com)

---

## 🌐 Live Cloud Deployments

| Platform | Role | Live URL | Status |
| :--- | :--- | :--- | :--- |
| **Vercel (Edge UI)** | Global Edge CDN + Reverse Proxy UI | **[https://project-saarthi-theta.vercel.app](https://project-saarthi-theta.vercel.app)** | 🟢 Live / Active |
| **Render (Backend Engine)** | Full-Stack Engine (Spring Boot + WebSockets + SQLite/H2) | **[https://saarthi-backend-bvdl.onrender.com](https://saarthi-backend-bvdl.onrender.com)** | 🟢 Live / Active |

### 🔗 Public Endpoints
* **🖥️ WebGL 3D Spatial HUD (Vercel):** [https://project-saarthi-theta.vercel.app](https://project-saarthi-theta.vercel.app)
* **📱 Low-Bandwidth Lite Mode (Vercel):** [https://project-saarthi-theta.vercel.app/lite.html](https://project-saarthi-theta.vercel.app/lite.html)
* **🖥️ Direct Backend HUD (Render):** [https://saarthi-backend-bvdl.onrender.com](https://saarthi-backend-bvdl.onrender.com)
* **📊 Multi-Chamber Fleet Snapshot API:** [https://saarthi-backend-bvdl.onrender.com/api/v1/telemetry/chambers](https://saarthi-backend-bvdl.onrender.com/api/v1/telemetry/chambers)
* **⚡ Live Sensor Telemetry API:** [https://saarthi-backend-bvdl.onrender.com/api/v1/telemetry/current](https://saarthi-backend-bvdl.onrender.com/api/v1/telemetry/current)
* **🔌 WebSocket Telemetry Stream:** `wss://saarthi-backend-bvdl.onrender.com/ws/telemetry`

---

## 🚀 Deployment Guide

### 1. Render Deployment (Automated via Docker)
The repository contains an optimized multi-stage [Dockerfile](./Dockerfile) targeting Eclipse Temurin Java 21 JRE.
1. Connect your GitHub repository (`Lohith-RC/SAARTHI`) in [Render Dashboard](https://dashboard.render.com).
2. Choose **Web Service** with **Docker** runtime.
3. Configure the following environment variables:
   ```env
   SERVER_PORT=8080
   SERVER_ADDRESS=0.0.0.0
   SAARTHI_CORS_ORIGINS=https://saarthi-backend-bvdl.onrender.com,http://localhost:8080
   GEMINI_API_KEY=your_gemini_api_key
   GROQ_API_KEY=your_groq_api_key
   ELEVENLABS_API_KEY=your_elevenlabs_api_key
   SPRING_DATASOURCE_URL=jdbc:postgresql://<supabase-host>:5432/<db>  # (Optional: falls back to embedded H2)
   ```

### 2. Vercel Deployment (Static Frontend + API Proxy)
The repository includes a custom [`vercel.json`](./vercel.json) that serves the WebGL static UI directly from Vercel's Global Edge Network and routes `/api/*` and `/ws/*` calls to the live Render backend:
```bash
# Deploy with Vercel CLI
npx vercel
```

---

## 📁 Master Documentation Suite (`/docs`)

All project specifications, engineering blueprints, business models, and legal documents are organized inside the **[`docs/`](./docs)** folder:

| # | Document File | Focus Area |
| :-: | :--- | :--- |
| **01** | 🎯 **[01_goals_and_milestones.md](./docs/01_goals_and_milestones.md)** | Master Goals, KPIs, Phase 1/2/3 Roadmaps & Sprint Milestones |
| **02** | 🗺️ **[02_implementation_plan.md](./docs/02_implementation_plan.md)** | Technical Architecture, Stack Selection & Engineering WBS |
| **03** | 🔄 **[03_app_and_web_flow.md](./docs/03_app_and_web_flow.md)** | Sequence Diagrams, WebSocket JSON Schemas & HUD States |
| **04** | 🛡️ **[04_security_safety_scan.md](./docs/04_security_safety_scan.md)** | Risk Matrix, Dead-Man Watchdog, Relay Safety & Biosecurity |
| **05** | 🔌 **[05_hardware_bom_and_pinout.md](./docs/05_hardware_bom_and_pinout.md)** | ₹1,110 ($14) Component BOM, Vendor Links & Pinout Tables |
| **06** | 🎙️ **[06_voice_speech_script.md](./docs/06_voice_speech_script.md)** | "Hey Saarthi" Wake-Word Specs, Audio Filters & Dialogue Trees |
| **07** | 💼 **[07_business_model_and_pitch.md](./docs/07_business_model_and_pitch.md)** | B2B SaaS Unit Economics, Margins & 3-Min Pitch Script |
| **08** | 🍄 **[08_agronomy_knowledge_and_crop_recipes.md](./docs/08_agronomy_knowledge_and_crop_recipes.md)** | Crop Climate Parameters, Diseases & Organic Remediation |
| **09** | 📋 **[09_pilot_grower_sla_and_terms.md](./docs/09_pilot_grower_sla_and_terms.md)** | Pilot NDA, Advisory Liability Shield & Data Ownership |
| **10** | ⚙️ **[10_api_reference_and_firmware_spec.md](./docs/10_api_reference_and_firmware_spec.md)** | REST / WebSocket Ingestion API & ESP32 State Machine |
| **11** | ⚔️ **[11_competitor_landscape_and_moat.md](./docs/11_competitor_landscape_and_moat.md)** | Competitor Matrix (Priva vs Saarthi) & 4-Layer Moat |
| **12** | 🏆 **[12_grant_and_hackathon_cheat_sheet.md](./docs/12_grant_and_hackathon_cheat_sheet.md)** | Tough Investor Q&A Bible & Startup India Grant Blurbs |

---

## 🔧 Firmware

Canonical ESP32 firmware: **`firmware/saarthi_esp32_firmware.ino`** (FreeRTOS dual-core: real-time sensor/relay failsafe on Core 1, Wi-Fi + HTTP telemetry on Core 0). Device identity and token are injected at build time:

```bash
platformio run -DSAARTHI_DEVICE_TOKEN='"my-secret"' -DSAARTHI_DEVICE_ID='"ESP32_NODE_02"'
```

---

## 🔒 Security & Environment Setup

Copy `.env.example` to `.env` and insert your private API keys:
```bash
cp .env.example .env
```

Your `.env` file is excluded in `.gitignore` and loaded securely by Spring Boot without exposing credentials to frontend clients.

```env
GROQ_API_KEY=your_groq_api_key
GEMINI_API_KEY=your_gemini_api_key
ELEVENLABS_API_KEY=your_elevenlabs_api_key
SAARTHI_DEVICE_TOKEN=your_device_token
SAARTHI_OPERATOR_TOKEN=your_operator_token
```

---

## 🏃 Running Locally

```bash
# 1-Click Launch (Windows)
run_server.bat

# Or via Maven
mvn spring-boot:run
```
* **🌐 WebGL 3D HUD:** [http://localhost:8080](http://localhost:8080)
* **📊 Live Telemetry API:** [http://localhost:8080/api/v1/telemetry/current](http://localhost:8080/api/v1/telemetry/current)
* **🌐 Fleet Snapshot API:** [http://localhost:8080/api/v1/telemetry/chambers](http://localhost:8080/api/v1/telemetry/chambers)

---

## 🚀 Multi-Chamber Fleet Simulation (2-minute setup)

SAARTHI runs a full **multi-chamber fleet**: every ESP32 node is its own chamber with independent crop recipes, watchdog, and alerting. The HUD has a fleet chamber selector in the top header.

```bash
# 1. Start the backend
mvn spring-boot:run

# 2. Simulate a 3-chamber fleet (mushroom + hydro mix)
set SAARTHI_DEVICE_TOKEN=<your-device-token>
python scripts/mock_esp32_node.py --nodes 3

# 3. Open the HUD, enter the operator token in ⚡ AI Studio, and watch the
#    chamber selector populate with live nodes.
```

**Key fleet endpoints:**
| Endpoint | Description | Auth |
| :--- | :--- | :--- |
| `GET /api/v1/telemetry/chambers` | Full fleet snapshot | Public |
| `GET /api/v1/telemetry/chambers/{deviceId}` | Single chamber | Public |
| `GET /api/v1/telemetry/history?deviceId=...` | Per-chamber history | Public |
| `POST /api/v1/actuate` | Actuate a specific chamber (`deviceId` optional) | Operator |
| `GET /api/v1/alerts/history` | Persistent alert history | Operator |
| `WS /ws/telemetry` | `FLEET_SNAPSHOT` + `CHAMBER_UPDATE` messages | Token |

---

*Created by Founding Team: Product Engineering (Alex) • Strategy & Operations (Maya) • Lead Visionary*
