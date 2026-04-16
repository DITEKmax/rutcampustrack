-- V14__update_campus_coordinates.sql
-- Update campus coordinates to the actual RUT MIIT main campus (ул. Образцова 9, Москва).
-- The V2 seed shipped approximate coordinates (55.7699, 37.7039) that were off by ~8 km,
-- causing legitimate check-ins to be rejected as "out of zone".
-- Production DBs have V2 already applied, so edits to V2 do not reach them — this migration
-- patches existing rows idempotently.
UPDATE campus_settings
SET lat = 55.788204,
    lng = 37.606762
WHERE name = 'Main Campus'
  AND lat = 55.7699
  AND lng = 37.7039;
