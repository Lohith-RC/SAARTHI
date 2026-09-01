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
* **🗄️ H2 Database Console:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

*Created by Founding Team: Product Engineering (Alex) • Strategy & Operations (Maya) • Lead Visionary*
