/* ==========================================================================
   PROJECT SAARTHI: ESP32 HARDWARE SENSOR NODE FIRMWARE (C++ / ARDUINO)
   Sensors: MQ-135 Gas (GPIO 34 ADC1), DHT22 Temp/RH (GPIO 4), Relay (GPIO 23)
   Backend: Java 21 Spring Boot 3.x REST Ingestion + Local 1400ppm Failsafe
   ========================================================================== */

#include <WiFi.h>
#include <HTTPClient.h>
#include <DHT.h>

// Wi-Fi Credentials
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// Spring Boot Cloud / LAN Ingestion URL
const char* serverUrl = "http://192.168.1.100:8080/api/v1/telemetry/push";
const char* deviceToken = "SAARTHI_UUID4_MASTER_SECRET";

// Pinout Configuration
#define MQ135_PIN 34       // ADC1_CH6
#define DHT_PIN 4          // Digital I/O
#define DHT_TYPE DHT22
#define RELAY_PIN 23       // Active LOW Relay (Optocoupled)
#define LED_BLUE 2         // Status LED

DHT dht(DHT_PIN, DHT_TYPE);

// Calibration Constants for MQ-135
const float RZERO = 76.63; 
const float CO2_CRITICAL_THRESHOLD = 1400.0; // Failsafe limit

void setup() {
  Serial.begin(115200);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH); // Active LOW relay default OFF
  pinMode(LED_BLUE, OUTPUT);

  dht.begin();
  Serial.println("\n[SAARTHI] Booting ESP32 Microcontroller Node...");

  // Connect to Wi-Fi
  WiFi.begin(ssid, password);
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    digitalWrite(LED_BLUE, !digitalRead(LED_BLUE));
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[SAARTHI] Wi-Fi Connected! IP: " + WiFi.localIP().toString());
    digitalWrite(LED_BLUE, HIGH);
  } else {
    Serial.println("\n[SAARTHI] Wi-Fi Connection Failed. Running in Autonomous Local Failsafe Mode.");
  }
}

void loop() {
  // Read Sensors
  float humidity = dht.readHumidity();
  float temperature = dht.readTemperature();

  // Read MQ-135 Analog Voltage and calculate estimated CO2 PPM
  int rawADC = analogRead(MQ135_PIN);
  float voltage = rawADC * (3.3 / 4095.0);
  float co2Ppm = 400.0 + (rawADC * 0.42); // Linearized baseline proxy

  if (isnan(humidity) || isnan(temperature)) {
    humidity = 90.0;
    temperature = 22.0;
  }

  Serial.printf("[SENSOR] CO2: %.1f ppm | RH: %.1f%% | Temp: %.1f C\n", co2Ppm, humidity, temperature);

  // LOCAL FAILSAFE CHECK (Trigger even if Wi-Fi / Cloud is disconnected)
  if (co2Ppm > CO2_CRITICAL_THRESHOLD) {
    Serial.println("🚨 [LOCAL FAILSAFE] CO2 EXCEEDED 1400 PPM! TRIPPING RELAY GPIO 23 ON!");
    digitalWrite(RELAY_PIN, LOW); // Turn Relay ON (Active LOW)
  } else {
    digitalWrite(RELAY_PIN, HIGH); // Relay OFF
  }

  // Stream Telemetry to Java Spring Boot Backend
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(serverUrl);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Token", deviceToken);

    String jsonPayload = "{\"deviceId\":\"SAARTHI_001\",\"co2Ppm\":" + String(co2Ppm, 1) +
                         ",\"humidityRh\":" + String(humidity, 1) +
                         ",\"tempC\":" + String(temperature, 1) +
                         ",\"fanRpm\":" + String(co2Ppm > 1300 ? 2400 : 1420) +
                         ",\"cropType\":\"mushroom\"}";

    int httpResponseCode = http.POST(jsonPayload);
    if (httpResponseCode > 0) {
      Serial.printf("[HTTP] Spring Ingestion Response: %d\n", httpResponseCode);
    } else {
      Serial.printf("[HTTP] Error sending POST: %s\n", http.errorToString(httpResponseCode).c_str());
    }
    http.end();
  }

  delay(2000); // 2-second telemetry loop
}
