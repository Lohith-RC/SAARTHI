# 🎙️ SAARTHI: Voice Interaction Script & Intent Tree
> **Wake-Word Acoustic Models, Dialogue Branches & Natural Language Intents**

---

## 1. Wake Word & Acoustic Specs
* **Wake Word:** *"Hey Saarthi"* (Phonetic: `heɪ sɑːr.θi`)
* **Acoustic Filter:** 300Hz–3400Hz bandpass filter applied on browser audio stream to eliminate industrial blower fan hums.

---

## 2. Core Dialogue Branches

### Branch A: Ambient Room Entry Briefing (Proactive)
* **Trigger:** Grower enters room or opens app.
* **Saarthi Speaks:**
  > *"Good afternoon, Vikram. Chamber 1 is running optimal. CO2 is steady at 825 ppm, humidity at 92%. Batch #4 Oyster pins are forming 12 hours ahead of schedule."*

### Branch B: Anomaly & Drift Alert (Warning State)
* **Trigger:** CO2 rises above 1,100 ppm during fruiting stage.
* **Saarthi Speaks:**
  > *"Attention: CO2 levels have risen to 1,280 ppm in Chamber 1. Would you like me to initiate a 10-minute ventilation cycle?"*
* **User Response:** *"Yes, ventilate."*
* **Saarthi Action:** Trips GPIO 23 relay -> *"Ventilation active. Auto-stopping at 850 ppm."*

### Branch C: Hands-Free Harvest Logging
* **User Query:** *"Hey Saarthi, log 18 kilograms from Rack 3."*
* **Saarthi Responds:**
  > *"Logged 18.0 kg Oyster Mushroom from Rack 3. Batch #4 total is now 52.4 kg. Yield efficiency is 18% above target."*

### Branch D: Consumables Auto-Reorder Prompt
* **Trigger:** Batch is 5 days from final clearing.
* **Saarthi Speaks:**
  > *"Rack 3 clears this Friday. Would you like to reorder 50kg fresh Blue Oyster spawn from GreenBio at your 10% partner discount?"*
