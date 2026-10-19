-- V2: demo seed data (the doc's "Seed 数据" - there is no admin UI).
--
-- Demo accounts - all share the password "Password123!":
--   aoife.murphy@sitecheck.demo    INSPECTOR  (Dublin Docklands, Cherrywood, Cork)
--   liam.obrien@sitecheck.demo     INSPECTOR  (Cork, Galway)
--   siobhan.kelly@sitecheck.demo   MANAGER
--
-- The two inspectors share exactly one site (Cork) so both "can see own site"
-- and "403 on someone else's site" (acceptance scenario 5) are easy to demo.
-- Rows are linked by name/email instead of hard-coded ids because the id
-- columns are GENERATED ALWAYS.

-- ---------------------------------------------------------------------------
-- Users (password hash = BCrypt of "Password123!")
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, role) VALUES
    ('aoife.murphy@sitecheck.demo',  '$2a$10$0grXulp8aRdKQ3xV..8AyOBrvB31ctZxeNA0.ZcWi2ZStJEPKox4m', 'Aoife Murphy',  'INSPECTOR'),
    ('liam.obrien@sitecheck.demo',   '$2a$10$0grXulp8aRdKQ3xV..8AyOBrvB31ctZxeNA0.ZcWi2ZStJEPKox4m', 'Liam O''Brien', 'INSPECTOR'),
    ('siobhan.kelly@sitecheck.demo', '$2a$10$0grXulp8aRdKQ3xV..8AyOBrvB31ctZxeNA0.ZcWi2ZStJEPKox4m', 'Siobhan Kelly', 'MANAGER');

-- ---------------------------------------------------------------------------
-- Sites (housing / mixed-use builds around Ireland)
-- ---------------------------------------------------------------------------
INSERT INTO sites (name, address, latitude, longitude) VALUES
    ('Dublin Docklands Apartments', 'North Wall Quay, Dublin 1, D01 K8N7',        53.348800, -6.236900),
    ('Cherrywood Housing Phase 2',  'Cherrywood, Dublin 18, D18 X6Y2',            53.245400, -6.144900),
    ('Cork Docklands Office Block', 'Kennedy Quay, Cork, T12 RX76',               51.900200, -8.453700),
    ('Galway Student Residence',    'Headford Road, Galway, H91 T2CX',            53.283500, -9.048700);

-- ---------------------------------------------------------------------------
-- Inspection templates + checklist items
-- ---------------------------------------------------------------------------
INSERT INTO inspection_templates (name, description, frequency_days) VALUES
    ('Scaffold Inspection',       'Weekly scaffold check (GA3-style record).',                      7),
    ('Excavation Inspection',     'Check of excavations, shafts and earthworks (GA3-style record).', 7),
    ('General Site Safety Walk',  'Weekly walk-round of general site conditions.',                   7),
    ('Lifting Equipment Check',   'Monthly check of cranes, hoists and lifting accessories.',       30);

INSERT INTO template_items (template_id, position, title, guidance)
SELECT t.id, i.position, i.title, i.guidance
FROM inspection_templates t
JOIN (VALUES
    ('Scaffold Inspection', 1, 'Base plates and sole boards in place',        'Check ground is firm and level under every standard.'),
    ('Scaffold Inspection', 2, 'Guard rails and toe boards fitted',          'Main guard rail at least 950mm; no gap over 470mm.'),
    ('Scaffold Inspection', 3, 'Working platforms fully boarded',            'No trap ends, gaps or damaged boards.'),
    ('Scaffold Inspection', 4, 'Ties and bracing intact',                    NULL),
    ('Scaffold Inspection', 5, 'Access ladders secured',                     'Ladder extends 1m above landing and is tied.'),
    ('Scaffold Inspection', 6, 'Scaffold tag up to date',                    NULL),

    ('Excavation Inspection', 1, 'Sides supported or battered back',          NULL),
    ('Excavation Inspection', 2, 'Edge protection / barriers in place',       NULL),
    ('Excavation Inspection', 3, 'Spoil and materials kept back from edge',   NULL),
    ('Excavation Inspection', 4, 'Safe means of access and egress',           NULL),
    ('Excavation Inspection', 5, 'Underground services located and marked',   'Cable avoidance tool used; drawings checked.'),

    ('General Site Safety Walk', 1, 'Site hoarding and signage secure',        NULL),
    ('General Site Safety Walk', 2, 'Walkways clear of obstructions',          NULL),
    ('General Site Safety Walk', 3, 'PPE worn by all operatives',              'Hard hat, hi-vis, safety boots as a minimum.'),
    ('General Site Safety Walk', 4, 'Fire extinguishers present and in date',  NULL),
    ('General Site Safety Walk', 5, 'Welfare facilities clean and stocked',    NULL),

    ('Lifting Equipment Check', 1, 'Thorough examination certificate in date', NULL),
    ('Lifting Equipment Check', 2, 'Safe working load clearly marked',          NULL),
    ('Lifting Equipment Check', 3, 'Slings, chains and shackles undamaged',     NULL),
    ('Lifting Equipment Check', 4, 'Lift plan available for current lifts',     NULL)
) AS i (template_name, position, title, guidance) ON i.template_name = t.name;

-- ---------------------------------------------------------------------------
-- Inspector assignments
-- ---------------------------------------------------------------------------
INSERT INTO site_inspectors (site_id, user_id)
SELECT s.id, u.id
FROM (VALUES
    ('Dublin Docklands Apartments', 'aoife.murphy@sitecheck.demo'),
    ('Cherrywood Housing Phase 2',  'aoife.murphy@sitecheck.demo'),
    ('Cork Docklands Office Block', 'aoife.murphy@sitecheck.demo'),
    ('Cork Docklands Office Block', 'liam.obrien@sitecheck.demo'),
    ('Galway Student Residence',    'liam.obrien@sitecheck.demo')
) AS a (site_name, email)
JOIN sites s ON s.name = a.site_name
JOIN users u ON u.email = a.email;

-- ---------------------------------------------------------------------------
-- Which templates each site needs
-- ---------------------------------------------------------------------------
INSERT INTO site_templates (site_id, template_id)
SELECT s.id, t.id
FROM (VALUES
    ('Dublin Docklands Apartments', 'Scaffold Inspection'),
    ('Dublin Docklands Apartments', 'General Site Safety Walk'),
    ('Dublin Docklands Apartments', 'Lifting Equipment Check'),
    ('Cherrywood Housing Phase 2',  'Scaffold Inspection'),
    ('Cherrywood Housing Phase 2',  'Excavation Inspection'),
    ('Cherrywood Housing Phase 2',  'General Site Safety Walk'),
    ('Cork Docklands Office Block', 'Scaffold Inspection'),
    ('Cork Docklands Office Block', 'Lifting Equipment Check'),
    ('Galway Student Residence',    'Excavation Inspection'),
    ('Galway Student Residence',    'General Site Safety Walk')
) AS st (site_name, template_name)
JOIN sites s                ON s.name = st.site_name
JOIN inspection_templates t ON t.name = st.template_name;
