-- Owner decision 2026-11-10: canonical campus radius is 400m, inclusive.
-- Forward-only: do not change checksums of the original campus configuration.
LOCK TABLE campus_settings IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO campus_settings (id, name, lat, lng, radius_m)
VALUES (1, 'Главный кампус', 55.788328, 37.606637, 400)
ON CONFLICT (id) DO UPDATE
SET lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    radius_m = EXCLUDED.radius_m,
    updated_at = NOW()
WHERE (campus_settings.lat, campus_settings.lng, campus_settings.radius_m)
    IS DISTINCT FROM (EXCLUDED.lat, EXCLUDED.lng, EXCLUDED.radius_m);

-- Preserve an advanced sequence and all unrelated campus/domain rows.
SELECT setval(
    pg_get_serial_sequence('campus_settings', 'id'),
    GREATEST((SELECT last_value FROM campus_settings_id_seq),
             (SELECT MAX(id) FROM campus_settings)),
    TRUE
);
