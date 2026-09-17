-- Marketplace hardening: geo on requests, SLA timestamps, reviews on jobs, quote caps.

ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS geo_area_id BIGINT REFERENCES geo_areas(id);

CREATE INDEX IF NOT EXISTS idx_service_requests_geo_area
    ON service_requests (geo_area_id) WHERE deleted_at IS NULL AND geo_area_id IS NOT NULL;

ALTER TABLE service_request_providers
    ADD COLUMN IF NOT EXISTS viewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS response_minutes INT;

UPDATE service_request_providers
SET sent_at = created_at
WHERE sent_at IS NULL;

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS job_id BIGINT REFERENCES jobs(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reviews_job_live
    ON reviews (job_id) WHERE job_id IS NOT NULL AND deleted_at IS NULL;

INSERT INTO plan_entitlements (plan_id, entitlement_code, value_type, value)
SELECT p.id, e.entitlement_code, e.value_type, e.value
FROM subscription_plans p
JOIN (VALUES
    ('STARTER',  'max_quotes_monthly', 'INT', '20'),
    ('STUDIO',   'max_quotes_monthly', 'INT', '80'),
    ('BUSINESS', 'max_quotes_monthly', 'INT', '300'),
    ('CHAIN',    'max_quotes_monthly', 'INT', '999')
) AS e(plan_code, entitlement_code, value_type, value) ON e.plan_code = p.code
ON CONFLICT (plan_id, entitlement_code) DO NOTHING;

UPDATE feature_flags
SET is_enabled_global = TRUE
WHERE code IN ('individual_providers', 'service_requests', 'jobs', 'community');
