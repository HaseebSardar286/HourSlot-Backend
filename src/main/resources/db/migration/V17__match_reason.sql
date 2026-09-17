ALTER TABLE service_request_providers
    ADD COLUMN IF NOT EXISTS match_reason VARCHAR(64);
