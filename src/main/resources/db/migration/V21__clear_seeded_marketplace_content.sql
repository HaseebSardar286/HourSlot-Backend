-- Remove platform-seeded dummy content (categories, demo geos, demo community).
-- RBAC, plans, entitlements, feature flags, and system_settings stay intact.
-- Admins add real categories / geo areas through the admin UI.

UPDATE businesses SET primary_category_id = NULL WHERE primary_category_id IS NOT NULL;

DELETE FROM business_categories;
DELETE FROM categories;

-- Demo-labeled areas from V9 / V10
DELETE FROM geo_areas
WHERE slug LIKE 'demo-%'
   OR name ILIKE 'Demo %';

-- Default demo community from V13
DELETE FROM community_reactions;
DELETE FROM community_reports;
DELETE FROM community_comments;
DELETE FROM community_posts;
DELETE FROM community_members;
DELETE FROM communities
WHERE slug = 'karachi-home-services';

-- Any leftover review rows from mock seeders
DELETE FROM reviews;
