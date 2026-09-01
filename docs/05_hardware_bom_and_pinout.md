# 🔌 SAARTHI: Hardware Bill of Materials (BOM) & Pinout Schematic
> **Component Specifications, Vendor Sources & Wiring Diagram**

---

## 1. Prototype Bench Bill of Materials (BOM)

| Item # | Component Name | Exact Part Model | Purpose / Specification | Vendor Link / Source | Unit Price |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **1** | **Microcontroller** | ESP32-WROOM-32D | Dual Core 240MHz, 4MB Flash, Wi-Fi/BT | [Robu.in](https://robu.in) | ₹400 |
| **2** | **Air Quality / Gas** | MQ-135 Sensor Module | CO2, Ammonia, Smoke sensitivity | [Robu.in](https://robu.in) | ₹140 |
| **3** | **Temp & Humidity** | DHT22 (AM2302) | ±0.5°C, 0-100% RH precision | [Robu.in](https://robu.in) | ₹160 |
| **4** | **Relay Switch** | 1-Channel 5V Relay | Opto-isolated, 10A 250V AC capacity | [Robu.in](https://robu.in) | ₹90 |
| **5** | **Prototyping Base** | Mini 400-Point Breadboard | Solderless interconnect | [Robu.in](https://robu.in) | ₹80 |
| **6** | **Jumper Wires** | 20cm Dupont (M-M / M-F) | 10x Male-to-Female, 10x Male-to-Male | [Robu.in](https://robu.in) | ₹60 |
| **7** | **Display Enclosure** | Transparent Acrylic Box | 100x80x40 mm with vent cutouts | Local / Amazon | ₹180 |
| **TOTAL**| **COMPLETE BENCH RIG** | | **Everything needed for live demo** | | **₹1,110 ($14)** |

---

## 2. Pin-to-Pin Wiring Table

```
       ESP32 BOARD                 PERIPHERAL COMPONENT
  ┌───────────────────┐           ┌──────────────────────┐
  │  5V (VIN)         │ ────────► │  MQ-135 VCC / Relay  │
  │  3V3              │ ────────► │  DHT22 VCC           │
  │  GND              │ ────────► │  Common Ground Rail  │
  │  GPIO 34 (ADC1_6) │ ◄──────── │  MQ-135 AOUT (Gas)   │
  │  GPIO 4  (D4)     │ ◄──────── │  DHT22 DATA (Temp/RH)│
  │  GPIO 23 (D23)    │ ────────► │  Relay IN (Fan Ctrl) │
  └───────────────────┘           └──────────────────────┘
```
