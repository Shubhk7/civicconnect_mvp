-- Sample wards (rough bounding-box polygons around real areas — good enough
-- for demo routing, not survey-accurate). Order: lng, lat for PostGIS.

INSERT INTO wards (name, city, boundary) VALUES
('Ward 134 - Dwarka Sector 12', 'Delhi',
 ST_GeomFromText('POLYGON((77.040 28.586, 77.052 28.586, 77.052 28.598, 77.040 28.598, 77.040 28.586))', 4326)),

('Ward 22 - IGI Airport Corridor', 'Delhi',
 ST_GeomFromText('POLYGON((77.094 28.550, 77.106 28.550, 77.106 28.562, 77.094 28.562, 77.094 28.550))', 4326)),

('Ward 6 - Sector 29', 'Gurugram',
 ST_GeomFromText('POLYGON((77.092 28.459, 77.104 28.459, 77.104 28.471, 77.092 28.471, 77.092 28.459))', 4326)),

('Ward 51 - Dwarka Expressway', 'Delhi',
 ST_GeomFromText('POLYGON((77.034 28.514, 77.046 28.514, 77.046 28.526, 77.034 28.526, 77.034 28.514))', 4326)),

('Ward 88 - Rohini Sector 7', 'Delhi',
 ST_GeomFromText('POLYGON((77.115 28.708, 77.127 28.708, 77.127 28.720, 77.115 28.720, 77.115 28.708))', 4326)),

('Ward 45 - Sector 14', 'Gurugram',
 ST_GeomFromText('POLYGON((77.028 28.468, 77.040 28.468, 77.040 28.480, 77.028 28.480, 77.028 28.468))', 4326));

-- Authority mapping: for each ward, which body owns which road type and
-- handles which issue type. This is the actual jurisdiction logic.

-- Ward 134 (Dwarka) - municipal roads under MCD
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(1, 'municipal', 'pothole',      'Roads',      'MCD', 72),
(1, 'municipal', 'garbage',      'Sanitation', 'MCD', 48),
(1, 'municipal', 'streetlight',  'Electrical', 'MCD', 96),
(1, 'municipal', 'water_leak',   'Water',      'MCD', 48),
(1, 'municipal', 'ewaste',       'Recycling',  'MCD', 120),
(1, 'municipal', 'ev_charger',   'Electrical', 'MCD', 48);

-- Ward 22 (IGI Airport corridor) - national highway under NHAI
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(2, 'national_highway', 'pothole',     'Roads',      'NHAI', 48),
(2, 'national_highway', 'streetlight', 'Electrical', 'NHAI', 72),
(2, 'municipal',        'garbage',     'Sanitation', 'MCD',  48),
(2, 'municipal',        'ev_charger',  'Electrical', 'MCD',  48);

-- Ward 6 (Sector 29, Gurugram) - municipal roads under MCG
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(3, 'municipal', 'pothole',      'Roads',      'MCG', 72),
(3, 'municipal', 'garbage',      'Sanitation', 'MCG', 48),
(3, 'municipal', 'streetlight',  'Electrical', 'MCG', 96),
(3, 'municipal', 'water_leak',   'Water',      'MCG', 48),
(3, 'municipal', 'ev_charger',   'Electrical', 'MCG', 48);

-- Ward 51 (Dwarka Expressway) - state PWD road
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(4, 'state_pwd', 'pothole',     'Roads',      'PWD', 96),
(4, 'state_pwd', 'streetlight', 'Electrical', 'PWD', 96),
(4, 'municipal', 'garbage',     'Sanitation', 'MCD', 48),
(4, 'municipal', 'ev_charger',  'Electrical', 'MCD', 48);

-- Ward 88 (Rohini) - municipal roads under MCD
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(5, 'municipal', 'pothole',     'Roads',      'MCD', 72),
(5, 'municipal', 'garbage',     'Sanitation', 'MCD', 48),
(5, 'municipal', 'water_leak',  'Water',      'MCD', 48),
(5, 'municipal', 'ev_charger',  'Electrical', 'MCD', 48);

-- Ward 45 (Sector 14, Gurugram) - municipal roads under MCG
INSERT INTO authority_mapping (ward_id, road_type, issue_type, department, authority_name, sla_hours) VALUES
(6, 'municipal', 'pothole',     'Roads',      'MCG', 72),
(6, 'municipal', 'garbage',     'Sanitation', 'MCG', 48),
(6, 'municipal', 'ev_charger',  'Electrical', 'MCG', 48);

-- A handful of realistic sample complaints so the dashboard/map isn't empty
-- when you demo it live.
INSERT INTO complaints (issue_type, description, photo_url, location, road_type, ward_id, department, authority_name, status, sla_deadline, created_at) VALUES
('pothole', 'Large pothole near main market entrance', NULL,
 ST_GeomFromText('POINT(77.045 28.591)', 4326), 'municipal', 1, 'Roads', 'MCD', 'ASSIGNED', now() + interval '48 hours', now() - interval '1 day'),

('garbage', 'Garbage not collected for 4 days', NULL,
 ST_GeomFromText('POINT(77.098 28.556)', 4326), 'municipal', 2, 'Sanitation', 'MCD', 'IN_PROGRESS', now() + interval '12 hours', now() - interval '2 days'),

('streetlight', 'Streetlight out on service road', NULL,
 ST_GeomFromText('POINT(77.100 28.556)', 4326), 'national_highway', 2, 'Electrical', 'NHAI', 'REPORTED', now() + interval '72 hours', now() - interval '3 hours'),

('pothole', 'Deep pothole causing traffic', NULL,
 ST_GeomFromText('POINT(77.096 28.462)', 4326), 'municipal', 3, 'Roads', 'MCG', 'RESOLVED', now() - interval '5 hours', now() - interval '4 days'),

('water_leak', 'Continuous water leakage from pipeline', NULL,
 ST_GeomFromText('POINT(77.040 28.520)', 4326), 'municipal', 4, 'Water', 'MCD', 'REPORTED', now() + interval '48 hours', now() - interval '6 hours');
