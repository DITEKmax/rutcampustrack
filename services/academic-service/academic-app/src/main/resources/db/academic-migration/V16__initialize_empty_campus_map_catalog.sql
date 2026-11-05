-- An unpublished, genuinely empty inventory has a valid empty manifest. The
-- student UI already explains that campus plans have not been published yet.
-- This initializes metadata only, using the same versions as admin publication;
-- it never invents a building/floor/asset or repairs a partial existing graph.
-- Admin publication takes this advisory lock before writing inventory and then
-- catalog rows. Acquire it first as well, avoiding an inverted table-lock order.
SELECT pg_advisory_xact_lock(1381253965, 1);

LOCK TABLE campus_map_catalog_revision, campus_map_building, campus_map_floor,
    campus_map_plan_version, campus_map_plan_format, campus_map_asset
    IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO campus_map_catalog_revision
    (revision, schema_version, validation_policy_version, is_current)
SELECT 1, 1, 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM campus_map_catalog_revision)
  AND NOT EXISTS (SELECT 1 FROM campus_map_building)
  AND NOT EXISTS (SELECT 1 FROM campus_map_floor)
  AND NOT EXISTS (SELECT 1 FROM campus_map_plan_version)
  AND NOT EXISTS (SELECT 1 FROM campus_map_plan_format)
  AND NOT EXISTS (SELECT 1 FROM campus_map_asset);
