-- Default cover images for root service categories (landing cards).
-- Admins can replace these via Admin → Categories upload.

UPDATE categories SET image_url = v.url
FROM (VALUES
    ('beauty-salons',    'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=900&q=80'),
    ('health-medical',   'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=900&q=80'),
    ('fitness-sports',   'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=900&q=80'),
    ('home-services',    'https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=900&q=80'),
    ('pets-animals',     'https://images.unsplash.com/photo-1450778869180-41d0601e046e?auto=format&fit=crop&w=900&q=80'),
    ('education',        'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?auto=format&fit=crop&w=900&q=80'),
    ('automotive',       'https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=900&q=80'),
    ('events-lifestyle', 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=900&q=80')
) AS v(slug, url)
WHERE categories.slug = v.slug
  AND categories.parent_id IS NULL
  AND (categories.image_url IS NULL OR categories.image_url = '');
