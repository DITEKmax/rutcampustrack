-- The production-only Academic foundation deliberately has no test-data seed.
-- GetCampusGeofence reads id=1; configure the owner-approved campus independently
-- of whether that row existed before this forward migration.
LOCK TABLE campus_settings IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO campus_settings (id, name, lat, lng, radius_m)
VALUES (1, 'Главный кампус', 55.788328, 37.606637, 200)
ON CONFLICT (id) DO UPDATE
SET lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    radius_m = EXCLUDED.radius_m,
    updated_at = NOW()
WHERE (campus_settings.lat, campus_settings.lng, campus_settings.radius_m)
    IS DISTINCT FROM (EXCLUDED.lat, EXCLUDED.lng, EXCLUDED.radius_m);

-- Explicit id=1 must not collide with the next generated ID. Never rewind a
-- sequence that already advanced further than the existing rows.
SELECT setval(
    pg_get_serial_sequence('campus_settings', 'id'),
    GREATEST((SELECT last_value FROM campus_settings_id_seq),
             (SELECT MAX(id) FROM campus_settings)),
    TRUE
);
