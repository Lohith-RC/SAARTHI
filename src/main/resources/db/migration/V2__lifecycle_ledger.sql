-- =====================================================================
-- SAARTHI schema v2: persistent crop lifecycle ledger
-- Mirrors com.saarthi.model.LifecycleEvent.
-- =====================================================================
CREATE TABLE IF NOT EXISTS lifecycle_event (
    id            BIGSERIAL PRIMARY KEY,
    crop_day      INTEGER      NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    description   VARCHAR(512) NOT NULL,
    health_status VARCHAR(32)  NOT NULL,
    recorded_at   BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_event_day
    ON lifecycle_event (crop_day ASC);