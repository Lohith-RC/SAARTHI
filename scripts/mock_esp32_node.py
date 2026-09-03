#!/usr/bin/env python3
"""
=============================================================================
Project SAARTHI - Mock ESP32 Hardware Node Streaming Bridge
Simulates physical ESP32 microcontrollers with DHT22 and MQ-135 sensors,
streaming live telemetry packets into the Spring Boot backend.

Supports a whole FLEET: run `--nodes 3` to simulate 3 chambers at once
(e.g. 2 mushroom + 1 hydro) for the multi-chamber fleet dashboard.
=============================================================================
"""

import argparse
import math
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.request

# Windows consoles default to cp1252 and cannot encode emoji; force UTF-8 with
# replacement so the fleet simulator never crashes on a print statement.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
    sys.stderr.reconfigure(encoding="utf-8", errors="replace", line_buffering=True)
except Exception:
    pass

ENDPOINT = os.environ.get("SAARTHI_ENDPOINT", "http://localhost:8080/api/v1/telemetry/push")

# Device token MUST come from the environment; there is no committed default.
DEVICE_TOKEN = os.environ.get("SAARTHI_DEVICE_TOKEN", "").strip()
if not DEVICE_TOKEN:
    raise SystemExit(
        "SAARTHI_DEVICE_TOKEN is not set. Refusing to simulate a node without a "
        "valid device token. Export it before running, e.g.:\n"
        "  set SAARTHI_DEVICE_TOKEN=<your-device-token>\n"
        "  python scripts/mock_esp32_node.py --nodes 3"
    )

# Per-node baseline profiles (deviceId -> cropType, base co2, base rh, base temp)
NODE_PROFILES = [
    ("ESP32_NODE_01", "mushroom", 850.0, 91.5, 22.4),
    ("ESP32_NODE_02", "mushroom", 920.0, 90.0, 22.0),
    ("ESP32_NODE_03", "hydro",    1080.0, 68.0, 20.8),
    ("ESP32_NODE_04", "hydro",    1150.0, 66.0, 21.2),
    ("ESP32_NODE_05", "mushroom", 880.0, 92.0, 22.6),
    ("ESP32_NODE_06", "mushroom", 950.0, 89.0, 23.0),
    ("ESP32_NODE_07", "hydro",    1200.0, 64.0, 21.5),
    ("ESP32_NODE_08", "mushroom", 900.0, 91.0, 22.2),
]


def build_payload(device_id, crop_type, base_co2, base_rh, base_temp, t):
    """Simulate natural biological micro-climate breathing oscillations."""
    co2_fluctuation = math.sin(t * 0.1 + hash(device_id) % 7) * 35.0 + random.uniform(-10, 10)
    rh_fluctuation = math.cos(t * 0.08 + hash(device_id) % 5) * 1.5 + random.uniform(-0.5, 0.5)
    temp_fluctuation = math.sin(t * 0.05 + hash(device_id) % 3) * 0.4 + random.uniform(-0.1, 0.1)

    current_co2 = round(base_co2 + co2_fluctuation, 1)
    current_rh = round(base_rh + rh_fluctuation, 1)
    current_temp = round(base_temp + temp_fluctuation, 1)

    co2_warn_threshold = 1300.0 if crop_type == "mushroom" else 1600.0
    fan_rpm = 1420 if current_co2 < 1200 else 2400
    fan_duty = 45 if current_co2 < 1200 else 80

    return {
        "deviceId": device_id,
        "co2Ppm": current_co2,
        "humidityRh": current_rh,
        "tempC": current_temp,
        "fanRpm": fan_rpm,
        "fanDuty": fan_duty,
        "cropType": crop_type,
        "status": "OPTIMAL" if current_co2 < co2_warn_threshold else "WARNING",
    }


def run_node(index, nodes_total, stop_event):
    """Stream one simulated ESP32 node on its own thread."""
    device_id, crop_type, base_co2, base_rh, base_temp = NODE_PROFILES[index % len(NODE_PROFILES)]
    print(f"  ▶ Node {device_id} online (crop: {crop_type})")

    t = 0
    while not stop_event.is_set():
        try:
            payload = build_payload(device_id, crop_type, base_co2, base_rh, base_temp, t)
            json_data = json_dumps(payload)
            req = urllib.request.Request(
                ENDPOINT,
                data=json_data,
                headers={
                    "Content-Type": "application/json",
                    "X-Device-Token": DEVICE_TOKEN,
                    "User-Agent": "ESP32-HTTPClient/1.0",
                },
            )

            with urllib.request.urlopen(req, timeout=3) as response:
                res_body = response.read().decode("utf-8")
                status = payload["status"]
                print(
                    f"[{time.strftime('%H:%M:%S')}] 🟢 {device_id} {status} | "
                    f"CO2: {payload['co2Ppm']} ppm | RH: {payload['humidityRh']}% | "
                    f"Temp: {payload['tempC']}°C | {res_body.strip()}"
                )
        except urllib.error.URLError as e:
            print(f"[{time.strftime('%H:%M:%S')}] ⚠️ {device_id} backend connection failed: {e}")
        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] ❌ {device_id} error: {e}")

        t += 1
        time.sleep(2.0)


def json_dumps(payload):
    import json

    return json.dumps(payload).encode("utf-8")


def main():
    parser = argparse.ArgumentParser(
        description="Project SAARTHI - Mock ESP32 fleet simulator",
    )
    parser.add_argument(
        "--nodes",
        type=int,
        default=1,
        help="Number of simulated ESP32 nodes to stream (default: 1, max: 8)",
    )
    args = parser.parse_args()

    node_count = max(1, min(args.nodes, len(NODE_PROFILES)))
    print("=" * 70)
    print("  🌿 SAARTHI Hardware Bridge: Simulating ESP32 FLEET")
    print(f"  Target Cloud Endpoint: {ENDPOINT}")
    print(f"  Active Nodes: {node_count}")
    print("  Press Ctrl+C to stop.")
    print("=" * 70)

    stop_event = threading.Event()
    threads = [
        threading.Thread(target=run_node, args=(i, node_count, stop_event), daemon=True)
        for i in range(node_count)
    ]

    try:
        for t in threads:
            t.start()
        while any(t.is_alive() for t in threads):
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\n🛑 Stopping fleet simulation...")
        stop_event.set()
        for t in threads:
            t.join(timeout=1.0)
        sys.exit(0)


if __name__ == "__main__":
    main()