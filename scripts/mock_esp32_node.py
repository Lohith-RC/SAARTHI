#!/usr/bin/env python3
"""
=============================================================================
Project SAARTHI - Mock ESP32 Hardware Node Streaming Bridge
Simulates a physical ESP32 microcontroller with DHT22 and MQ-135 sensors,
streaming live telemetry packets into the Spring Boot backend.
=============================================================================
"""

import time
import math
import random
import json
import urllib.request
import urllib.error

ENDPOINT = "http://localhost:8080/api/v1/telemetry/push"
DEVICE_ID = "ESP32_NODE_01"
DEVICE_TOKEN = "SAARTHI_UUID4_MASTER_SECRET"

def run_mock_node():
    print("=" * 65)
    print("  🌿 SAARTHI Hardware Bridge: Simulating ESP32 Node [ESP32_NODE_01]")
    print(f"  Target Cloud Endpoint: {ENDPOINT}")
    print("  Press Ctrl+C to stop.")
    print("=" * 65)

    base_co2 = 850.0
    base_rh = 91.5
    base_temp = 22.4
    t = 0

    while True:
        try:
            # Simulate natural biological micro-climate breathing oscillations
            co2_fluctuation = math.sin(t * 0.1) * 35.0 + random.uniform(-10, 10)
            rh_fluctuation = math.cos(t * 0.08) * 1.5 + random.uniform(-0.5, 0.5)
            temp_fluctuation = math.sin(t * 0.05) * 0.4 + random.uniform(-0.1, 0.1)

            current_co2 = round(base_co2 + co2_fluctuation, 1)
            current_rh = round(base_rh + rh_fluctuation, 1)
            current_temp = round(base_temp + temp_fluctuation, 1)
            fan_rpm = 1420 if current_co2 < 1200 else 2400
            fan_duty = 45 if current_co2 < 1200 else 80

            payload = {
                "deviceId": DEVICE_ID,
                "co2Ppm": current_co2,
                "humidityRh": current_rh,
                "tempC": current_temp,
                "fanRpm": fan_rpm,
                "fanDuty": fan_duty,
                "cropType": "mushroom",
                "status": "OPTIMAL" if current_co2 < 1300 else "WARNING"
            }

            json_data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                ENDPOINT,
                data=json_data,
                headers={
                    "Content-Type": "application/json",
                    "X-Device-Token": DEVICE_TOKEN,
                    "User-Agent": "ESP32-HTTPClient/1.0"
                }
            )

            with urllib.request.urlopen(req, timeout=3) as response:
                res_body = response.read().decode("utf-8")
                print(f"[{time.strftime('%H:%M:%S')}] 🟢 Telemetry Ack: {res_body.strip()} | CO2: {current_co2} ppm | RH: {current_rh}% | Temp: {current_temp}°C")

        except urllib.error.URLError as e:
            print(f"[{time.strftime('%H:%M:%S')}] ⚠️ Backend connection failed: {e}")
        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] ❌ Error: {e}")

        t += 1
        time.sleep(2.0)

if __name__ == "__main__":
    run_mock_node()
