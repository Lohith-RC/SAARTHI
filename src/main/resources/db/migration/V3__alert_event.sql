-- =====================================================================
-- SAARTHI schema v3: persistent alert history
-- Mirrors com.saarthi.model.AlertEvent. Alerts survive restarts so the
-- operator can audit every dispatched emergency alert.
-- =====================================================================
CREATE TABLE IF NOT EXISTS alert_event (
    id           BIGSERIAL PRIMARY KEY,
    alert_key    VARCHAR(96)  NOT NULL,
    level        VARCHAR(16)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    message      VARCHAR(2000),
    details_json VARCHAR(2000),
    timestamp    BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alert_event_timestamp
    ON alert_event (timestamp DESC);