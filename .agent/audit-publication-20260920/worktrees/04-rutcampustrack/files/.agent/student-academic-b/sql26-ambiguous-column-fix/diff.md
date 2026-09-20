# Diff

One source hunk changed in `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` (function `validate_campus_map_ready_asset()`, lines 243–247):

```diff
-        SELECT * INTO asset_record
-        FROM campus_map_asset
-        WHERE id = NEW.asset_id
-          AND plan_version_id = NEW.plan_version_id
-          AND format = NEW.format;
+        SELECT asset.* INTO asset_record
+        FROM campus_map_asset AS asset
+        WHERE asset.id = NEW.asset_id
+          AND asset.plan_version_id = NEW.plan_version_id
+          AND asset.format = NEW.format;
```

Semantics: `asset.*` still assigns the same `campus_map_asset` row to the existing `asset_record`; only table-column qualification changed. The lookup now cannot resolve `plan_version_id` to the PL/pgSQL local variable by accident.
