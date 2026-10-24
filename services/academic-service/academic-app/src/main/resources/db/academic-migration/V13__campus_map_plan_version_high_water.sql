-- Retain allocated version URLs after an explicitly confirmed whole-scheme purge.
-- This migration preserves all existing schemes and files.
ALTER TABLE campus_map_floor
    ADD COLUMN plan_version_high_water BIGINT NOT NULL DEFAULT 0
        CHECK (plan_version_high_water >= 0);

UPDATE campus_map_floor f
SET plan_version_high_water = COALESCE(
    (SELECT MAX(p.version) FROM campus_map_plan_version p WHERE p.floor_id = f.id), 0);
