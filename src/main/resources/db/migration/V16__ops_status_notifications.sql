-- Provider ops status, notification event refs, trust-friendly indexes.

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS ops_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE';

UPDATE businesses SET ops_status = 'AVAILABLE' WHERE ops_status IS NULL OR ops_status = '';

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reference_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_notifications_event_ref
    ON notifications (user_id, event_type, reference_id);

CREATE INDEX IF NOT EXISTS idx_srp_response_minutes
    ON service_request_providers (provider_id, response_minutes)
    WHERE response_minutes IS NOT NULL;
