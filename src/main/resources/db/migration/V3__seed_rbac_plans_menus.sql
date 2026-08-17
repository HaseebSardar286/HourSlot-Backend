-- Platform catalog, system roles, menus, subscription plans, and member_role backfill.

INSERT INTO permissions (code, module, description, is_active) VALUES
    ('booking.read', 'booking', 'View bookings', TRUE),
    ('booking.write', 'booking', 'Create bookings', TRUE),
    ('booking.manage', 'booking', 'Confirm, complete, cancel, reschedule', TRUE),
    ('service.read', 'catalog', 'View services', TRUE),
    ('service.write', 'catalog', 'Manage services', TRUE),
    ('staff.read', 'staff', 'View staff', TRUE),
    ('staff.manage', 'staff', 'Manage staff', TRUE),
    ('branch.read', 'ops', 'View branches', TRUE),
    ('branch.write', 'ops', 'Manage branches', TRUE),
    ('schedule.read', 'ops', 'View hours and holidays', TRUE),
    ('schedule.write', 'ops', 'Manage hours and holidays', TRUE),
    ('package.read', 'catalog', 'View packages', TRUE),
    ('package.write', 'catalog', 'Manage packages', TRUE),
    ('media.write', 'media', 'Upload gallery and logos', TRUE),
    ('business.read', 'business', 'View business profile', TRUE),
    ('business.write', 'business', 'Edit business profile', TRUE),
    ('billing.manage', 'billing', 'Manage org billing', TRUE),
    ('org.manage', 'org', 'Manage organization', TRUE),
    ('review.read', 'trust', 'Read reviews', TRUE),
    ('review.write', 'trust', 'Write reviews', TRUE),
    ('review.moderate', 'trust', 'Reply to reviews', TRUE),
    ('customer.profile', 'customer', 'Manage own customer profile', TRUE),
    ('admin.users', 'admin', 'Manage platform users', TRUE),
    ('admin.businesses', 'admin', 'Verify and moderate businesses', TRUE),
    ('admin.categories', 'admin', 'Manage taxonomy', TRUE),
    ('admin.settings', 'admin', 'Platform settings', TRUE),
    ('admin.audit', 'admin', 'View audit events', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (scope, code, name, is_system, created_at) VALUES
    ('PLATFORM', 'SUPER_ADMIN', 'Super Admin', TRUE, NOW()),
    ('PLATFORM', 'PLATFORM_ADMIN', 'Platform Admin', TRUE, NOW()),
    ('ORGANIZATION', 'ORG_OWNER', 'Organization Owner', TRUE, NOW()),
    ('ORGANIZATION', 'ORG_MANAGER', 'Organization Manager', TRUE, NOW()),
    ('BUSINESS', 'BUSINESS_MANAGER', 'Business Manager', TRUE, NOW()),
    ('BRANCH', 'BRANCH_MANAGER', 'Branch Manager', TRUE, NOW()),
    ('BRANCH', 'STAFF', 'Staff', TRUE, NOW()),
    ('PLATFORM', 'CUSTOMER', 'Customer', TRUE, NOW())
ON CONFLICT (code) WHERE is_system DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND r.is_system = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.module = 'admin'
WHERE r.code = 'PLATFORM_ADMIN' AND r.is_system = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ORG_OWNER' AND r.is_system = TRUE AND p.module NOT IN ('admin')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ORG_MANAGER', 'BUSINESS_MANAGER') AND r.is_system = TRUE
  AND p.module NOT IN ('admin', 'billing', 'org')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'booking.read', 'booking.manage', 'staff.read', 'schedule.read', 'schedule.write',
    'branch.read', 'service.read', 'package.read', 'business.read'
)
WHERE r.code = 'BRANCH_MANAGER' AND r.is_system = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'booking.read', 'booking.manage', 'schedule.read', 'staff.read', 'service.read'
)
WHERE r.code = 'STAFF' AND r.is_system = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'booking.read', 'booking.write', 'review.write', 'customer.profile', 'package.read'
)
WHERE r.code = 'CUSTOMER' AND r.is_system = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO nav_menus (code, label, path, icon, sort_order, audience, required_permission_code, is_active) VALUES
    ('customer.explore', 'Explore', '/profile/explore', 'fa-compass', 10, 'CUSTOMER', NULL, TRUE),
    ('customer.bookings', 'Bookings', '/profile/bookings', 'fa-calendar', 20, 'CUSTOMER', 'booking.read', TRUE),
    ('customer.favorites', 'Favorites', '/profile/favorites', 'fa-heart', 30, 'CUSTOMER', NULL, TRUE),
    ('customer.packages', 'Packages', '/profile/packages', 'fa-box', 40, 'CUSTOMER', 'package.read', TRUE),
    ('business.dashboard', 'Dashboard', '/business/dashboard', 'fa-chart-line', 10, 'BUSINESS', 'business.read', TRUE),
    ('business.bookings', 'Bookings', '/business/bookings', 'fa-calendar-check', 20, 'BUSINESS', 'booking.read', TRUE),
    ('business.branches', 'Branches', '/business/branches', 'fa-store', 30, 'BUSINESS', 'branch.read', TRUE),
    ('business.services', 'Services', '/business/services', 'fa-scissors', 40, 'BUSINESS', 'service.read', TRUE),
    ('business.staff', 'Staff', '/business/staff', 'fa-users', 50, 'BUSINESS', 'staff.read', TRUE),
    ('admin.dashboard', 'Dashboard', '/admin/dashboard', 'fa-gauge', 10, 'ADMIN', 'admin.users', TRUE),
    ('admin.users', 'Users', '/admin/users', 'fa-user-gear', 20, 'ADMIN', 'admin.users', TRUE),
    ('admin.businesses', 'Businesses', '/admin/businesses', 'fa-building', 30, 'ADMIN', 'admin.businesses', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO subscription_plans (code, name, billing_interval, price, currency, is_active, sort_order, features)
VALUES
    ('STARTER', 'Starter', 'MONTH', 0, 'USD', TRUE, 10, '{"copy":"Single location essentials"}'::jsonb),
    ('STUDIO', 'Studio', 'MONTH', 29, 'USD', TRUE, 20, '{"copy":"Peak pricing and packages"}'::jsonb),
    ('BUSINESS', 'Business', 'MONTH', 79, 'USD', TRUE, 30, '{"copy":"Waitlist, SMS, yield tools"}'::jsonb),
    ('CHAIN', 'Chain', 'MONTH', 199, 'USD', TRUE, 40, '{"copy":"Multi-business organization"}'::jsonb)
ON CONFLICT (code) DO NOTHING;

INSERT INTO plan_entitlements (plan_id, entitlement_code, value_type, value)
SELECT p.id, e.entitlement_code, e.value_type, e.value
FROM subscription_plans p
JOIN (VALUES
    ('STARTER', 'max_branches', 'INT', '1'),
    ('STARTER', 'max_staff', 'INT', '3'),
    ('STARTER', 'peak_pricing', 'BOOL', 'false'),
    ('STARTER', 'packages', 'BOOL', 'false'),
    ('STARTER', 'waitlist', 'BOOL', 'false'),
    ('STARTER', 'max_businesses', 'INT', '1'),
    ('STUDIO', 'max_branches', 'INT', '2'),
    ('STUDIO', 'max_staff', 'INT', '10'),
    ('STUDIO', 'peak_pricing', 'BOOL', 'true'),
    ('STUDIO', 'packages', 'BOOL', 'true'),
    ('STUDIO', 'waitlist', 'BOOL', 'false'),
    ('STUDIO', 'max_businesses', 'INT', '1'),
    ('BUSINESS', 'max_branches', 'INT', '5'),
    ('BUSINESS', 'max_staff', 'INT', '50'),
    ('BUSINESS', 'peak_pricing', 'BOOL', 'true'),
    ('BUSINESS', 'packages', 'BOOL', 'true'),
    ('BUSINESS', 'waitlist', 'BOOL', 'true'),
    ('BUSINESS', 'max_businesses', 'INT', '1'),
    ('CHAIN', 'max_branches', 'INT', '999'),
    ('CHAIN', 'max_staff', 'INT', '999'),
    ('CHAIN', 'peak_pricing', 'BOOL', 'true'),
    ('CHAIN', 'packages', 'BOOL', 'true'),
    ('CHAIN', 'waitlist', 'BOOL', 'true'),
    ('CHAIN', 'max_businesses', 'INT', '999')
) AS e(plan_code, entitlement_code, value_type, value) ON e.plan_code = p.code
ON CONFLICT (plan_id, entitlement_code) DO NOTHING;

-- Map legacy role codes stored during V2 to member_roles
INSERT INTO member_roles (user_id, role_id, organization_id, business_id, granted_at)
SELECT u.id, r.id,
       CASE WHEN r.code IN ('ORG_OWNER', 'ORG_MANAGER') THEN om.organization_id ELSE NULL END,
       CASE WHEN r.code IN ('ORG_OWNER', 'BUSINESS_MANAGER') THEN b.id ELSE NULL END,
       NOW()
FROM users u
JOIN roles r ON r.is_system = TRUE AND r.code = CASE u.legacy_role
    WHEN 'SUPER_ADMIN' THEN 'SUPER_ADMIN'
    WHEN 'ADMIN' THEN 'PLATFORM_ADMIN'
    WHEN 'BUSINESS_OWNER' THEN 'ORG_OWNER'
    WHEN 'BUSINESS_STAFF' THEN 'STAFF'
    ELSE 'CUSTOMER'
END
LEFT JOIN organization_members om ON om.user_id = u.id AND om.status = 'ACTIVE' AND om.deleted_at IS NULL
LEFT JOIN businesses b ON b.organization_id = om.organization_id AND b.deleted_at IS NULL
WHERE u.legacy_role IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM member_roles mr
      WHERE mr.user_id = u.id AND mr.role_id = r.id AND mr.deleted_at IS NULL
  );

-- Customers with no member_role still get CUSTOMER
INSERT INTO member_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, NOW()
FROM users u
JOIN roles r ON r.code = 'CUSTOMER' AND r.is_system = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM member_roles mr WHERE mr.user_id = u.id AND mr.deleted_at IS NULL
)
AND EXISTS (SELECT 1 FROM customer_profiles cp WHERE cp.user_id = u.id);

-- Staff users: attach staff_id when a staff row is linked
UPDATE member_roles mr
SET staff_id = s.id,
    branch_id = s.branch_id,
    business_id = br.business_id,
    organization_id = biz.organization_id
FROM staff s
JOIN branches br ON br.id = s.branch_id
JOIN businesses biz ON biz.id = br.business_id
JOIN roles r ON r.code = 'STAFF' AND r.is_system = TRUE
WHERE mr.user_id = s.user_id
  AND mr.role_id = r.id
  AND mr.deleted_at IS NULL;

ALTER TABLE users DROP COLUMN IF EXISTS legacy_role;
