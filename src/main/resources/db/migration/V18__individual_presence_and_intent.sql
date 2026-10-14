-- Individual provider intent, presence, and disable-ready fields for org-join.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider_intent VARCHAR(32);

UPDATE users SET provider_intent = NULL WHERE provider_intent IS NOT NULL AND provider_intent = '';

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS last_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS presence_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS individual_disabled_reason VARCHAR(64);

UPDATE businesses
SET is_online = COALESCE(is_online, FALSE);

CREATE INDEX IF NOT EXISTS idx_businesses_presence_online
    ON businesses (is_online, last_seen_at)
    WHERE deleted_at IS NULL AND listing_mode = 'INDIVIDUAL';
