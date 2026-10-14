-- Seed Super Admin (idempotent).
-- Password: @superadmin123123 (BCrypt)

INSERT INTO users (
    email, password_hash, first_name, last_name, status,
    email_verified_at, created_at, provider_intent
)
SELECT
    'haseebsardar123123@gmail.com',
    '$2b$10$MDrRd60/eZSw684GlxhHpOW.AYJ9UonWSaqh3CPk8vYc7RFDb7/O6',
    'Haseeb',
    'Sardar',
    'ACTIVE',
    NOW(),
    NOW(),
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE email = 'haseebsardar123123@gmail.com' AND deleted_at IS NULL
);

UPDATE users
SET password_hash = '$2b$10$MDrRd60/eZSw684GlxhHpOW.AYJ9UonWSaqh3CPk8vYc7RFDb7/O6',
    status = 'ACTIVE',
    email_verified_at = COALESCE(email_verified_at, NOW()),
    first_name = COALESCE(first_name, 'Haseeb'),
    last_name = COALESCE(last_name, 'Sardar'),
    updated_at = NOW(),
    deleted_at = NULL
WHERE email = 'haseebsardar123123@gmail.com';

INSERT INTO member_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, NOW()
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN' AND r.is_system = TRUE
WHERE u.email = 'haseebsardar123123@gmail.com'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM member_roles mr
      WHERE mr.user_id = u.id AND mr.role_id = r.id AND mr.deleted_at IS NULL
  );
