# 🌿 Project SAARTHI

**Smart Autonomous Assistant for Resilient Tech-Driven Horticulture & Indoor Farming**

> *"You grow the harvest. Saarthi steers the climate."*

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

Canonical ESP32 firmware: **`firmware/saarthi_esp32_firmware.ino`** (FreeRTOS
dual-core: real-time sensor/relay failsafe on Core 1, Wi-Fi + HTTP telemetry on
Core 0). Device identity and token are injected at build time:

```
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
```

---

## 🏃 Running the Full-Stack Server

```bash
# 1-Click Launch (Windows)
run_server.bat

# Or via Maven
mvn spring-boot:run
```
* **🌐 WebGL 3D HUD:** [http://localhost:8080](http://localhost:8080)
* **📊 Live Telemetry API:** [http://localhost:8080/api/v1/telemetry/current](http://localhost:8080/api/v1/telemetry/current)
* **🌐 Fleet Snapshot API:** [http://localhost:8080/api/v1/telemetry/chambers](http://localhost:8080/api/v1/telemetry/chambers)
* **🗄️ H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

## 🚀 Multi-Chamber Fleet Demo (2-minute setup)

SAARTHI runs a full **multi-chamber fleet**: every ESP32 node is its own
chamber with independent crop recipes, watchdog, and alerting. The HUD has a
fleet chamber selector in the top header.

```bash
# 1. Start the backend (with your tokens set)
mvn spring-boot:run

# 2. Simulate a 3-chamber fleet (mushroom + hydro mix)
set SAARTHI_DEVICE_TOKEN=<your-device-token>
python scripts/mock_esp32_node.py --nodes 3

# 3. Open the HUD, enter the operator token in ⚡ AI Studio, and watch the
#    🌐 chamber selector populate with live nodes. Switch chambers, trigger
#    spikes, and check alerts.
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
