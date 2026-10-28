-- Restore platform service taxonomy (not mock businesses / reviews).
-- Idempotent: skips when slug already exists.

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

INSERT INTO categories (parent_id, name, slug, icon, search_tags, is_active, sort_order, created_at)
SELECT p.id, v.name, v.slug, v.icon, v.search_tags, TRUE, v.sort_order, NOW()
FROM (VALUES
    ('beauty-salons', 'Hair Salon',       'hair-salon',       'fa-scissors',   'haircut,color,styling,salon', 11),
    ('beauty-salons', 'Barber',           'barber',           'fa-scissors',   'barber,fade,beard,mens hair', 12),
    ('beauty-salons', 'Nail Salon',       'nail-salon',       'fa-hand',       'nails,manicure,pedicure', 13),
    ('beauty-salons', 'Spa & Wellness',   'spa-wellness',     'fa-spa',        'spa,massage,wellness,relax', 14),
    ('beauty-salons', 'Makeup Artist',    'makeup-artist',    'fa-palette',    'makeup,bridal,glam', 15),
    ('beauty-salons', 'Skincare',         'skincare',         'fa-leaf',       'facial,skincare,dermatology beauty', 16),

    ('health-medical', 'Dentist',          'dentist',          'fa-tooth',         'dental,teeth,orthodontics', 21),
    ('health-medical', 'General Clinic',   'general-clinic',   'fa-house-medical', 'gp,clinic,doctor,checkup', 22),
    ('health-medical', 'Physiotherapy',    'physiotherapy',    'fa-person-walking','physio,rehab,injury', 23),
    ('health-medical', 'Mental Health',    'mental-health',    'fa-brain',         'therapy,counseling,psychology', 24),
    ('health-medical', 'Dermatology',      'dermatology',      'fa-hand-dots',     'skin,dermatologist', 25),
    ('health-medical', 'Optometry',        'optometry',        'fa-eye',           'eye,vision,glasses', 26),

    ('fitness-sports', 'Gym',               'gym',               'fa-dumbbell', 'gym,weights,fitness center', 31),
    ('fitness-sports', 'Yoga',              'yoga',              'fa-spa',      'yoga,stretch,mindfulness', 32),
    ('fitness-sports', 'Personal Trainer',  'personal-trainer',  'fa-person',   'pt,trainer,coaching', 33),
    ('fitness-sports', 'Pilates',           'pilates',           'fa-child',    'pilates,core,reformer', 34),
    ('fitness-sports', 'Martial Arts',      'martial-arts',      'fa-hand-fist','karate,mma,boxing', 35),

    ('home-services', 'Electrician',           'electrician',           'fa-bolt',        'wiring,electrical,outlet', 41),
    ('home-services', 'Plumbing',              'plumbing',              'fa-faucet',      'plumber,pipe,leak', 42),
    ('home-services', 'Cleaning',              'cleaning',              'fa-broom',       'clean,maid,housekeeping', 43),
    ('home-services', 'AC & Appliance Repair', 'ac-appliance-repair',   'fa-snowflake',   'ac,fridge,appliance,repair', 44),
    ('home-services', 'Painting',              'painting',              'fa-paint-roller','paint,walls,interior', 45),
    ('home-services', 'Carpentry',             'carpentry',             'fa-hammer',      'wood,furniture,carpenter', 46),

    ('pets-animals', 'Veterinary',   'veterinary',   'fa-briefcase-medical', 'vet,clinic,animal doctor', 51),
    ('pets-animals', 'Pet Grooming', 'pet-grooming', 'fa-scissors',          'groom,bath,pet salon', 52),
    ('pets-animals', 'Pet Training', 'pet-training', 'fa-dog',               'training,obedience', 53),

    ('education', 'Academic Tutoring', 'academic-tutoring', 'fa-book',            'tutor,math,science,school', 61),
    ('education', 'Language Classes',  'language-classes',  'fa-language',        'english,language,spoken', 62),
    ('education', 'Music Lessons',     'music-lessons',     'fa-music',           'guitar,piano,singing', 63),
    ('education', 'Driving Lessons',   'driving-lessons',   'fa-car-side',        'driving,license,instructor', 64),

    ('automotive', 'Car Wash',    'car-wash',    'fa-shower',     'wash,clean,car', 71),
    ('automotive', 'Auto Repair', 'auto-repair', 'fa-wrench',     'mechanic,service,fix', 72),
    ('automotive', 'Detailing',   'detailing',   'fa-spray-can',  'detail,polish,interior', 73),

    ('events-lifestyle', 'Photography',    'photography',    'fa-camera',      'photo,portrait,wedding', 81),
    ('events-lifestyle', 'Event Planning', 'event-planning', 'fa-calendar-check','events,wedding planner', 82),
    ('events-lifestyle', 'Catering',       'catering',       'fa-utensils',    'food,catering,events', 83)
) AS v(parent_slug, name, slug, icon, search_tags, sort_order)
JOIN categories p ON p.slug = v.parent_slug AND p.parent_id IS NULL
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = v.slug);
