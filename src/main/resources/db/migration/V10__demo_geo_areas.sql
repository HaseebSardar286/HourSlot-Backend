-- Demo-named Lahore areas (product demo labels, not official boundaries).
-- Safe to run whether or not V9 already included these rows.
INSERT INTO geo_areas (country_code, region, city, name, slug, latitude, longitude, status, created_at)
SELECT v.country_code, v.region, v.city, v.name, v.slug, v.latitude, v.longitude, 'ACTIVE', NOW()
FROM (VALUES
    ('PK', 'Punjab', 'Lahore', 'Demo DHA Phase 5', 'demo-dha-phase-5', 31.4697, 74.4105),
    ('PK', 'Punjab', 'Lahore', 'Demo Gulberg', 'demo-gulberg', 31.5102, 74.3441),
    ('PK', 'Punjab', 'Lahore', 'Demo Model Town', 'demo-model-town', 31.4824, 74.3250),
    ('PK', 'Punjab', 'Lahore', 'Demo Johar Town', 'demo-johar-town', 31.4697, 74.2728)
) AS v(country_code, region, city, name, slug, latitude, longitude)
WHERE NOT EXISTS (
    SELECT 1 FROM geo_areas g
    WHERE g.country_code = v.country_code AND g.city = v.city AND g.slug = v.slug AND g.deleted_at IS NULL
);
