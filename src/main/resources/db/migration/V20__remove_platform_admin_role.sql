-- Collapse Platform Admin into Super Admin (full access only).

-- Reassign any PLATFORM_ADMIN memberships to SUPER_ADMIN
UPDATE member_roles mr
SET role_id = sa.id
FROM roles pa
JOIN roles sa ON sa.code = 'SUPER_ADMIN' AND sa.is_system = TRUE
WHERE pa.code = 'PLATFORM_ADMIN' AND pa.is_system = TRUE
  AND mr.role_id = pa.id
  AND NOT EXISTS (
      SELECT 1 FROM member_roles existing
      WHERE existing.user_id = mr.user_id
        AND existing.role_id = sa.id
        AND existing.deleted_at IS NULL
  );

-- Remove leftover PLATFORM_ADMIN assignments already covered by SUPER_ADMIN
DELETE FROM member_roles
WHERE role_id IN (SELECT id FROM roles WHERE code = 'PLATFORM_ADMIN' AND is_system = TRUE);

DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE code = 'PLATFORM_ADMIN' AND is_system = TRUE);

DELETE FROM role_menu_items
WHERE role_id IN (SELECT id FROM roles WHERE code = 'PLATFORM_ADMIN' AND is_system = TRUE);

DELETE FROM roles
WHERE code = 'PLATFORM_ADMIN' AND is_system = TRUE;
