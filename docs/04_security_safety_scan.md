# 🛡️ SAARTHI: Security, Safety & Reliability Assessment
> **Risk Analysis, Failsafe Architecture & Cleanroom Biosecurity**

---

## 1. Risk Matrix & Mitigation Protocols

| Risk ID | Failure Mode | Severity | Likelihood | Mitigation & Safeguard Protocol |
| :--- | :--- | :--- | :--- | :--- |
| **R-01** | **2 AM Power Blackout** (HVAC stops, Wi-Fi dies) | 🔴 CRITICAL | Moderate | **60-Second Dead-Man Watchdog:** If cloud misses 3 consecutive pings, it triggers automated IVR calls to primary and backup numbers within 3 minutes. |
| **R-02** | **pH / CO2 Sensor Drift** (False readings kill batch) | 🔴 CRITICAL | Moderate | **Algorithmic Variance Filter:** Flags unnatural flatlines or sudden impossible jumps; alerts for buffer recalibration. |
| **R-03** | **Relay Back-EMF Surge** (High voltage fries MCU) | 🟡 HIGH | Low | **Optocoupler Isolation:** Complete galvanic optical isolation between ESP32 3.3V logic and 230V AC fan motors. |
| **R-04** | **Biosecurity Contamination** (Device spreads mold) | 🟡 HIGH | Low | **IP66 Sealed Smooth Casing:** Safe to spray with 70% isopropyl alcohol or bleach washdowns between harvest cycles. |
| **R-05** | **Cloud Outage / Internet Drop** | 🟡 HIGH | Moderate | **Local Offline Rules Engine:** ESP32 autonomously trips fan relays if CO2 > 1,400 ppm even with zero internet connectivity. |

---

## 2. Cleanroom Biosecurity Standard Operating Procedure (SOP)
1. **Device Placement:** Mount sensor housing 1.2 meters above ground level, away from direct water spray jets.
2. **Sanitization Protocol:** During chamber clearing, wipe down outer enclosure with medical-grade isopropyl wipes. Never submerge sensor probes in liquid bleach.
3. **No Unsealed Motors:** Prohibit open spinning projector fans inside high-humidity chambers to prevent airborne green mold (Trichoderma) spore dissemination.
