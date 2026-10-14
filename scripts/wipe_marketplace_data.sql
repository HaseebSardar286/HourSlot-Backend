-- Wipe marketplace/account data while keeping platform reference data
-- (roles, permissions, plans, entitlements, feature_flags, system_settings,
--  nav_menus, categories, geo_areas, flyway).
-- IMPORTANT: do not use TRUNCATE ... CASCADE — roles/settings have FKs to users/orgs/businesses.
-- businesses/organizations/users cannot be TRUNCATEd while roles/settings reference them; use DELETE.

BEGIN;

-- Protect reference tables that FK into wipe targets
UPDATE system_settings SET updated_by = NULL WHERE updated_by IS NOT NULL;
UPDATE roles SET organization_id = NULL, business_id = NULL
WHERE organization_id IS NOT NULL OR business_id IS NOT NULL;
UPDATE businesses SET primary_category_id = NULL WHERE primary_category_id IS NOT NULL;

TRUNCATE TABLE
    deal_redemptions,
    deals,
    payment_refunds,
    payment_allocations,
    payments,
    booking_items,
    booking_status_history,
    bookings,
    waitlist_entries,
    reviews,
    favorites,
    job_media,
    job_notes,
    job_status_history,
    jobs,
    quotes,
    service_request_media,
    service_request_providers,
    service_requests,
    disputes,
    community_reactions,
    community_reports,
    community_comments,
    community_posts,
    community_members,
    communities,
    customer_packages,
    package_services,
    service_packages,
    customer_business_notes,
    customer_memberships,
    membership_plans,
    customer_addresses,
    customer_profiles,
    staff_services,
    staff_time_off,
    staff_breaks,
    staff_working_intervals,
    staff_working_hours,
    staff_invites,
    staff,
    service_resources,
    resources,
    resource_unavailability,
    time_of_day_pricing,
    branch_breaks,
    branch_holidays,
    branch_working_intervals,
    branch_working_hours,
    branches,
    business_categories,
    business_media,
    business_service_areas,
    business_verification_documents,
    services,
    organization_usage_counters,
    usage_counters,
    organization_subscriptions,
    organization_members,
    member_roles,
    notification_preferences,
    notifications,
    email_verification_tokens,
    password_reset_tokens,
    auth_refresh_tokens,
    audit_events,
    media_assets
RESTART IDENTITY;

-- Referenced by roles / system_settings — must DELETE, not TRUNCATE
DELETE FROM businesses;
DELETE FROM organizations;
DELETE FROM users;

-- Reset identity sequences for delete targets
ALTER TABLE businesses ALTER COLUMN id RESTART WITH 1;
ALTER TABLE organizations ALTER COLUMN id RESTART WITH 1;
ALTER TABLE users ALTER COLUMN id RESTART WITH 1;

COMMIT;
