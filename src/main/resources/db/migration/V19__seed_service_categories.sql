-- Platform taxonomy for customer / individual / business onboarding.
-- Idempotent: skips when slug already exists.

-- ---------------------------------------------------------------------------
-- Root categories
-- ---------------------------------------------------------------------------
INSERT INTO categories (parent_id, name, slug, icon, search_tags, is_active, sort_order, created_at)
SELECT NULL, v.name, v.slug, v.icon, v.search_tags, TRUE, v.sort_order, NOW()
FROM (VALUES
    ('Beauty & Salons',  'beauty-salons',    'fa-scissors',      'salon,hair,barber,nails,spa,beauty,makeup', 10),
    ('Health & Medical', 'health-medical',   'fa-heart-pulse',   'clinic,dentist,doctor,physio,therapy,health', 20),
    ('Fitness & Sports', 'fitness-sports',   'fa-dumbbell',      'gym,yoga,trainer,fitness,sports,pilates', 30),
    ('Home Services',    'home-services',    'fa-house',         'electrician,plumber,cleaning,repair,ac,home', 40),
    ('Pets & Animals',   'pets-animals',     'fa-paw',           'vet,pet,grooming,dog,cat,animals', 50),
    ('Education',        'education',        'fa-graduation-cap','tutor,lessons,music,driving,classes,learning', 60),
    ('Automotive',       'automotive',       'fa-car',           'car,wash,repair,detailing,auto,mechanic', 70),
    ('Events & Lifestyle','events-lifestyle','fa-camera',        'photo,event,catering,lifestyle,wedding', 80)
) AS v(name, slug, icon, search_tags, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = v.slug);

-- ---------------------------------------------------------------------------
-- Subcategories (parent matched by slug)
-- ---------------------------------------------------------------------------
INSERT INTO categories (parent_id, name, slug, icon, search_tags, is_active, sort_order, created_at)
SELECT p.id, v.name, v.slug, v.icon, v.search_tags, TRUE, v.sort_order, NOW()
FROM (VALUES
    -- Beauty & Salons
    ('beauty-salons', 'Hair Salon',       'hair-salon',       'fa-scissors',   'haircut,color,styling,salon', 11),
    ('beauty-salons', 'Barber',           'barber',           'fa-scissors',   'barber,fade,beard,mens hair', 12),
    ('beauty-salons', 'Nail Salon',       'nail-salon',       'fa-hand',       'nails,manicure,pedicure', 13),
    ('beauty-salons', 'Spa & Wellness',   'spa-wellness',     'fa-spa',        'spa,massage,wellness,relax', 14),
    ('beauty-salons', 'Makeup Artist',    'makeup-artist',    'fa-palette',    'makeup,bridal,glam', 15),
    ('beauty-salons', 'Skincare',         'skincare',         'fa-leaf',       'facial,skincare,dermatology beauty', 16),

    -- Health & Medical
    ('health-medical', 'Dentist',          'dentist',          'fa-tooth',         'dental,teeth,orthodontics', 21),
    ('health-medical', 'General Clinic',   'general-clinic',   'fa-house-medical', 'gp,clinic,doctor,checkup', 22),
    ('health-medical', 'Physiotherapy',    'physiotherapy',    'fa-person-walking','physio,rehab,injury', 23),
    ('health-medical', 'Mental Health',    'mental-health',    'fa-brain',         'therapy,counseling,psychology', 24),
    ('health-medical', 'Dermatology',      'dermatology',      'fa-hand-dots',     'skin,dermatologist', 25),
    ('health-medical', 'Optometry',        'optometry',        'fa-eye',           'eye,vision,glasses', 26),

    -- Fitness & Sports
    ('fitness-sports', 'Gym',               'gym',               'fa-dumbbell', 'gym,weights,fitness center', 31),
    ('fitness-sports', 'Yoga',              'yoga',              'fa-spa',      'yoga,stretch,mindfulness', 32),
    ('fitness-sports', 'Personal Trainer',  'personal-trainer',  'fa-person',   'pt,trainer,coaching', 33),
    ('fitness-sports', 'Pilates',           'pilates',           'fa-child',    'pilates,core,reformer', 34),
    ('fitness-sports', 'Martial Arts',      'martial-arts',      'fa-hand-fist','karate,mma,boxing', 35),

    -- Home Services
    ('home-services', 'Electrician',           'electrician',           'fa-bolt',        'wiring,electrical,outlet', 41),
    ('home-services', 'Plumbing',              'plumbing',              'fa-faucet',      'plumber,pipe,leak', 42),
    ('home-services', 'Cleaning',              'cleaning',              'fa-broom',       'clean,maid,housekeeping', 43),
    ('home-services', 'AC & Appliance Repair', 'ac-appliance-repair',   'fa-snowflake',   'ac,fridge,appliance,repair', 44),
    ('home-services', 'Painting',              'painting',              'fa-paint-roller','paint,walls,interior', 45),
    ('home-services', 'Carpentry',             'carpentry',             'fa-hammer',      'wood,furniture,carpenter', 46),

    -- Pets & Animals
    ('pets-animals', 'Veterinary',   'veterinary',   'fa-briefcase-medical', 'vet,clinic,animal doctor', 51),
    ('pets-animals', 'Pet Grooming', 'pet-grooming', 'fa-scissors',          'groom,bath,pet salon', 52),
    ('pets-animals', 'Pet Training', 'pet-training', 'fa-dog',               'training,obedience', 53),

    -- Education
    ('education', 'Academic Tutoring', 'academic-tutoring', 'fa-book',            'tutor,math,science,school', 61),
    ('education', 'Language Classes',  'language-classes',  'fa-language',        'english,language,spoken', 62),
    ('education', 'Music Lessons',     'music-lessons',     'fa-music',           'guitar,piano,singing', 63),
    ('education', 'Driving Lessons',   'driving-lessons',   'fa-car-side',        'driving,license,instructor', 64),

    -- Automotive
    ('automotive', 'Car Wash',    'car-wash',    'fa-shower',     'wash,clean,car', 71),
    ('automotive', 'Auto Repair', 'auto-repair', 'fa-wrench',     'mechanic,service,fix', 72),
    ('automotive', 'Detailing',   'detailing',   'fa-spray-can',  'detail,polish,interior', 73),

    -- Events & Lifestyle
    ('events-lifestyle', 'Photography',    'photography',    'fa-camera',      'photo,portrait,wedding', 81),
    ('events-lifestyle', 'Event Planning', 'event-planning', 'fa-calendar-check','events,wedding planner', 82),
    ('events-lifestyle', 'Catering',       'catering',       'fa-utensils',    'food,catering,events', 83)
) AS v(parent_slug, name, slug, icon, search_tags, sort_order)
JOIN categories p ON p.slug = v.parent_slug AND p.parent_id IS NULL
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = v.slug);

-- ---------------------------------------------------------------------------
-- Broader geo areas for individual / customer address setup
-- ---------------------------------------------------------------------------
INSERT INTO geo_areas (country_code, region, city, name, slug, latitude, longitude, status, created_at)
SELECT v.country_code, v.region, v.city, v.name, v.slug, v.latitude, v.longitude, 'ACTIVE', NOW()
FROM (VALUES
    -- Lahore (keep demos from V9/V10; add practical areas)
    ('PK', 'Punjab', 'Lahore', 'DHA', 'lahore-dha', 31.4697, 74.4105),
    ('PK', 'Punjab', 'Lahore', 'Gulberg', 'lahore-gulberg', 31.5102, 74.3441),
    ('PK', 'Punjab', 'Lahore', 'Model Town', 'lahore-model-town', 31.4824, 74.3250),
    ('PK', 'Punjab', 'Lahore', 'Johar Town', 'lahore-johar-town', 31.4697, 74.2728),
    ('PK', 'Punjab', 'Lahore', 'Bahria Town', 'lahore-bahria-town', 31.3675, 74.1780),
    ('PK', 'Punjab', 'Lahore', 'Cantt', 'lahore-cantt', 31.5150, 74.3850),

    -- Karachi
    ('PK', 'Sindh', 'Karachi', 'Clifton', 'karachi-clifton', 24.8138, 67.0222),
    ('PK', 'Sindh', 'Karachi', 'DHA Phase 6', 'karachi-dha-phase-6', 24.8030, 67.0650),
    ('PK', 'Sindh', 'Karachi', 'Gulshan-e-Iqbal', 'karachi-gulshan-e-iqbal', 24.9180, 67.0971),
    ('PK', 'Sindh', 'Karachi', 'PECHS', 'karachi-pechs', 24.8730, 67.0540),
    ('PK', 'Sindh', 'Karachi', 'North Nazimabad', 'karachi-north-nazimabad', 24.9420, 67.0460),

    -- Islamabad / Rawalpindi
    ('PK', 'Islamabad Capital Territory', 'Islamabad', 'F-6', 'islamabad-f6', 33.7295, 73.0760),
    ('PK', 'Islamabad Capital Territory', 'Islamabad', 'F-7', 'islamabad-f7', 33.7210, 73.0570),
    ('PK', 'Islamabad Capital Territory', 'Islamabad', 'F-8', 'islamabad-f8', 33.7110, 73.0420),
    ('PK', 'Islamabad Capital Territory', 'Islamabad', 'G-9', 'islamabad-g9', 33.6890, 73.0330),
    ('PK', 'Punjab', 'Rawalpindi', 'Bahria Town', 'rawalpindi-bahria-town', 33.5480, 73.1230),
    ('PK', 'Punjab', 'Rawalpindi', 'Saddar', 'rawalpindi-saddar', 33.5970, 73.0470)
) AS v(country_code, region, city, name, slug, latitude, longitude)
WHERE NOT EXISTS (
    SELECT 1 FROM geo_areas g
    WHERE g.country_code = v.country_code AND g.city = v.city AND g.slug = v.slug AND g.deleted_at IS NULL
);

-- Ensure marketplace feature flags stay on for account flows
UPDATE feature_flags
SET is_enabled_global = TRUE
WHERE code IN ('individual_providers', 'service_requests', 'jobs', 'community');

