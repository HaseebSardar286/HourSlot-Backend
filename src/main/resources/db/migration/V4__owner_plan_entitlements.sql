-- Align seeded entitlements with the business-only subscription plan
-- and put every existing organization on Starter until Stripe Billing ships.

INSERT INTO plan_entitlements (plan_id, entitlement_code, value_type, value)
SELECT p.id, e.entitlement_code, e.value_type, e.value
FROM subscription_plans p
JOIN (VALUES
    ('STARTER', 'last_minute_deals', 'BOOL', 'false'),
    ('STARTER', 'sms_monthly', 'INT', '0'),
    ('STARTER', 'yield_dashboard', 'BOOL', 'false'),
    ('STARTER', 'white_label', 'BOOL', 'false'),
    ('STARTER', 'owner_reply', 'BOOL', 'false'),
    ('STUDIO', 'last_minute_deals', 'BOOL', 'false'),
    ('STUDIO', 'sms_monthly', 'INT', '200'),
    ('STUDIO', 'yield_dashboard', 'BOOL', 'false'),
    ('STUDIO', 'white_label', 'BOOL', 'true'),
    ('STUDIO', 'owner_reply', 'BOOL', 'false'),
    ('BUSINESS', 'last_minute_deals', 'BOOL', 'true'),
    ('BUSINESS', 'sms_monthly', 'INT', '800'),
    ('BUSINESS', 'yield_dashboard', 'BOOL', 'true'),
    ('BUSINESS', 'white_label', 'BOOL', 'true'),
    ('BUSINESS', 'owner_reply', 'BOOL', 'true'),
    ('CHAIN', 'last_minute_deals', 'BOOL', 'true'),
    ('CHAIN', 'sms_monthly', 'INT', '3000'),
    ('CHAIN', 'yield_dashboard', 'BOOL', 'true'),
    ('CHAIN', 'white_label', 'BOOL', 'true'),
    ('CHAIN', 'owner_reply', 'BOOL', 'true')
) AS e(plan_code, entitlement_code, value_type, value) ON e.plan_code = p.code
ON CONFLICT (plan_id, entitlement_code) DO NOTHING;

UPDATE plan_entitlements pe
SET value = v.value
FROM subscription_plans p
JOIN (VALUES
    ('STARTER', 'max_branches', '1'),
    ('STARTER', 'max_staff', '2'),
    ('STARTER', 'max_businesses', '1'),
    ('STARTER', 'peak_pricing', 'false'),
    ('STARTER', 'packages', 'false'),
    ('STARTER', 'waitlist', 'false'),
    ('STUDIO', 'max_branches', '1'),
    ('STUDIO', 'max_staff', '8'),
    ('STUDIO', 'max_businesses', '1'),
    ('STUDIO', 'peak_pricing', 'true'),
    ('STUDIO', 'packages', 'true'),
    ('STUDIO', 'waitlist', 'true'),
    ('BUSINESS', 'max_branches', '3'),
    ('BUSINESS', 'max_staff', '25'),
    ('BUSINESS', 'max_businesses', '1'),
    ('BUSINESS', 'peak_pricing', 'true'),
    ('BUSINESS', 'packages', 'true'),
    ('BUSINESS', 'waitlist', 'true'),
    ('CHAIN', 'max_branches', '999'),
    ('CHAIN', 'max_staff', '999'),
    ('CHAIN', 'max_businesses', '999'),
    ('CHAIN', 'peak_pricing', 'true'),
    ('CHAIN', 'packages', 'true'),
    ('CHAIN', 'waitlist', 'true')
) AS v(plan_code, entitlement_code, value) ON v.plan_code = p.code
WHERE pe.plan_id = p.id
  AND pe.entitlement_code = v.entitlement_code;

INSERT INTO organization_subscriptions (
    organization_id, plan_id, status, cancel_at_period_end, version, created_at, updated_at
)
SELECT o.id, p.id, 'ACTIVE', FALSE, 1, NOW(), NOW()
FROM organizations o
JOIN subscription_plans p ON p.code = 'STARTER' AND p.is_active = TRUE
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM organization_subscriptions s
      WHERE s.organization_id = o.id
        AND s.status IN ('ACTIVE', 'TRIALING')
  );
