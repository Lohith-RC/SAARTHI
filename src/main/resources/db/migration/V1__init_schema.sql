-- =====================================================================
-- SAARTHI schema baseline v1
-- Mirrors com.saarthi.model.TelemetryRecord (camelCase -> snake_case via
-- Hibernate PhysicalNamingStrategy). Managed exclusively by Flyway; the
-- application uses ddl-auto=validate so mismatches fail fast.
-- =====================================================================
CREATE TABLE IF NOT EXISTS chamber_telemetry (
    id          BIGSERIAL PRIMARY KEY,
    device_id   VARCHAR(64)  NOT NULL,
    co2_ppm     DOUBLE PRECISION,
    humidity_rh DOUBLE PRECISION,
    temp_c      DOUBLE PRECISION,
    fan_rpm     INTEGER,
    fan_duty    INTEGER,
    crop_type   VARCHAR(32),
    status      VARCHAR(32),
    timestamp   BIGINT
);

CREATE INDEX IF NOT EXISTS idx_telemetry_timestamp
    ON chamber_telemetry (timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_timestamp
    ON chamber_telemetry (device_id, timestamp DESC);