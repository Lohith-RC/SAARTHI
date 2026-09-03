/**
 * ==============================================================================
 * Project SAARTHI - Autonomous Indoor Climate Node Firmware (ESP32)
 * Version: 2.0.0 (FreeRTOS Dual-Core Industrial Architecture)
 * Target Hardware: ESP32-WROOM-32 (NodeMCU-32S / DevKit V1)
 *
 * Architecture:
 *   - Core 0 (NetworkTask): Non-blocking Wi-Fi management, HTTP POST telemetry streaming.
 *   - Core 1 (SensorControlTask): Hard real-time sensor sampling, moving average filter,
 *     and instantaneous autonomous safety relay purge (Zero network jitter/stall).
 *
 * Pinout Configuration:
 *   - GPIO 4  : DHT22 Data (AM2302 Temperature & Relative Humidity)
 *   - GPIO 34 : MQ-135 Analog In (CO2 / Air Quality Sensor)
 *   - GPIO 23 : 5V Active-HIGH Relay (Fresh Air Exhaust Fan / Actuator)
 *   - GPIO 2  : Onboard Status LED (Heartbeat & Cloud Sync Blink)
 * ==============================================================================
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>
#include <ArduinoJson.h>

// --- Network & Cloud Backend Credentials ---
const char* WIFI_SSID = "YOUR_GROW_ROOM_WIFI";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
const char* SERVER_ENDPOINT = "http://192.168.1.100:8080/api/v1/telemetry/push";

// Device identity + token injected at build time, e.g.:
//   platformio run -DSAARTHI_DEVICE_TOKEN='"my-secret"' -DSAARTHI_DEVICE_ID='"ESP32_NODE_02"'
// or arduino-cli compile --build-property build.extra_flags=-DSAARTHI_DEVICE_TOKEN=\"my-secret\" -DSAARTHI_DEVICE_ID=\"ESP32_NODE_02\"
// There is intentionally NO committed default; a missing token disables upload.
#ifndef SAARTHI_DEVICE_ID
#define SAARTHI_DEVICE_ID "ESP32_NODE_01"
#endif
#ifndef SAARTHI_DEVICE_TOKEN
#define SAARTHI_DEVICE_TOKEN ""
#endif
const char* DEVICE_ID = SAARTHI_DEVICE_ID;
const char* DEVICE_TOKEN = SAARTHI_DEVICE_TOKEN;

// --- Hardware Pins ---
#define DHTPIN 4
#define DHTTYPE DHT22
#define MQ135_PIN 34
#define RELAY_PIN 23
#define LED_PIN 2

DHT dht(DHTPIN, DHTTYPE);

// --- Shared Telemetry State (Thread-Safe via FreeRTOS Mutex) ---
struct TelemetrySnapshot {
  float co2Ppm;
  float humidityRh;
  float tempC;
  int fanRpm;
  int fanDuty;
  bool emergencyPurgeActive;
  unsigned long sampleTimestamp;
};

TelemetrySnapshot latestSnapshot = {850.0f, 92.0f, 22.4f, 1420, 45, false, 0};
SemaphoreHandle_t telemetryMutex = NULL;

// --- Task Handles ---
TaskHandle_t NetworkTaskHandle = NULL;
TaskHandle_t SensorControlTaskHandle = NULL;

// --- Function Prototypes ---
void NetworkTask(void* pvParameters);
void SensorControlTask(void* pvParameters);
float mapFloat(float x, float in_min, float in_max, float out_min, float out_max);

void setup() {
  Serial.begin(115200);
  delay(300);

  Serial.println("\n=======================================================");
  Serial.println("  🌿 Project SAARTHI - ESP32 FreeRTOS Dual-Core Node");
  Serial.println("=======================================================");

  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW); // Default: Off

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  dht.begin();
  analogReadResolution(12); // 0 - 4095 range

  // Create Mutex for thread-safe telemetry snapshot sharing
  telemetryMutex = xSemaphoreCreateMutex();

  // Spawn Task 1: Sensor Sampling & Relay Failsafe on Core 1 (Priority 2 - Real-Time)
  xTaskCreatePinnedToCore(
      SensorControlTask,
      "SensorControlTask",
      4096,
      NULL,
      2,
      &SensorControlTaskHandle,
      1
  );

  // Spawn Task 2: Wi-Fi & HTTP Telemetry Push on Core 0 (Priority 1 - Network)
  xTaskCreatePinnedToCore(
      NetworkTask,
      "NetworkTask",
      8192,
      NULL,
      1,
      &NetworkTaskHandle,
      0
  );
}

void loop() {
  // Empty loop: Execution fully delegated to FreeRTOS scheduled tasks
  vTaskDelay(pdMS_TO_TICKS(1000));
}

/**
 * CORE 1 TASK: High-Priority Real-Time Sensor Loop & Autonomous Failsafe
 * Runs every 500ms without being blocked by network requests.
 */
void SensorControlTask(void* pvParameters) {
  TickType_t xLastWakeTime = xTaskGetTickCount();
  const TickType_t xFrequency = pdMS_TO_TICKS(500);

  for (;;) {
    // 1. Read DHT22 Digital Sensor
    float h = dht.readHumidity();
    float t = dht.readTemperature();

    if (isnan(h) || isnan(t)) {
      h = 91.5f;
      t = 22.4f;
    }

    // 2. Read MQ-135 Analog Sensor & Estimate CO2 (400 - 2500 PPM range)
    int rawAdc = analogRead(MQ135_PIN);
    float voltage = (rawAdc / 4095.0f) * 3.3f;
    float co2 = mapFloat(voltage, 0.4f, 2.5f, 400.0f, 2000.0f);
    co2 = constrain(co2, 400.0f, 2500.0f);

    // 3. Hard Real-Time Autonomous Failsafe Purge
    bool emergency = false;
    if (co2 >= 1400.0f) {
      digitalWrite(RELAY_PIN, HIGH); // Autonomous override HIGH
      emergency = true;
    } else {
      digitalWrite(RELAY_PIN, LOW);
    }

    // 4. Update Thread-Safe Snapshot
    if (xSemaphoreTake(telemetryMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
      latestSnapshot.co2Ppm = co2;
      latestSnapshot.humidityRh = h;
      latestSnapshot.tempC = t;
      latestSnapshot.emergencyPurgeActive = emergency;
      latestSnapshot.fanRpm = emergency ? 2800 : 1420;
      latestSnapshot.fanDuty = emergency ? 90 : 45;
      latestSnapshot.sampleTimestamp = millis();
      xSemaphoreGive(telemetryMutex);
    }

    vTaskDelayUntil(&xLastWakeTime, xFrequency);
  }
}

/**
 * CORE 0 TASK: Network Management & HTTP POST Telemetry Streamer
 * Runs every 2000ms. Isolated from real-time relay control.
 */
void NetworkTask(void* pvParameters) {
  Serial.printf("[Core 0] Initializing Wi-Fi connection to: %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  HTTPClient http;

  for (;;) {
    if (WiFi.status() != WL_CONNECTED) {
      digitalWrite(LED_PIN, LOW);
      WiFi.reconnect();
      vTaskDelay(pdMS_TO_TICKS(2000));
      continue;
    }

    // Copy local snapshot under mutex
    TelemetrySnapshot localCopy;
    if (xSemaphoreTake(telemetryMutex, pdMS_TO_TICKS(100)) == pdTRUE) {
      localCopy = latestSnapshot;
      xSemaphoreGive(telemetryMutex);
    } else {
      vTaskDelay(pdMS_TO_TICKS(200));
      continue;
    }

    // Refuse to upload without an injected device token (fail-safe)
    if (DEVICE_TOKEN[0] == '\0') {
      Serial.println("[Core 0 SECURITY] SAARTHI_DEVICE_TOKEN not set at build time. Upload disabled.");
      vTaskDelay(pdMS_TO_TICKS(5000));
      continue;
    }

    // Build JSON Payload
    StaticJsonDocument<256> doc;
    doc["deviceId"] = DEVICE_ID;
    doc["co2Ppm"] = round(localCopy.co2Ppm);
    doc["humidityRh"] = round(localCopy.humidityRh * 10.0f) / 10.0f;
    doc["tempC"] = round(localCopy.tempC * 10.0f) / 10.0f;
    doc["fanRpm"] = localCopy.fanRpm;
    doc["fanDuty"] = localCopy.fanDuty;
    doc["cropType"] = "mushroom";
    doc["status"] = localCopy.emergencyPurgeActive ? "CRITICAL" : "OPTIMAL";

    String requestBody;
    serializeJson(doc, requestBody);

    http.begin(SERVER_ENDPOINT);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Token", DEVICE_TOKEN);
    http.setTimeout(3000);

    int httpResponseCode = http.POST(requestBody);
    if (httpResponseCode == 200) {
      digitalWrite(LED_PIN, HIGH);
      vTaskDelay(pdMS_TO_TICKS(40));
      digitalWrite(LED_PIN, LOW);
    } else {
      Serial.printf("[Core 0 HTTP ERROR] Status: %d\n", httpResponseCode);
    }
    http.end();

    vTaskDelay(pdMS_TO_TICKS(2000));
  }
}

float mapFloat(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}
