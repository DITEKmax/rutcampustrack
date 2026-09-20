# Checks

| Check | Command | Exit |
| --- | --- | ---: |
| Base revision | `git rev-parse HEAD` | 0 |
| V17 origin/test selectors | `rg -n "NEW.schedule_item_id IS DISTINCT FROM occurrence_record.schedule_item_id|NEW.one_off_lesson_id IS DISTINCT FROM occurrence_record.one_off_lesson_id|crossTemplateRecurringOriginIsRejectedEvenWhenSnapshotsMatch|crossOneOffOriginIsRejectedEvenWhenSnapshotsMatch" <V17/test paths>` | 0 |
| V26 correction markers | `rg -n "protect_campus_map_plan_version|published campus map plan version is immutable|published campus map plan format is immutable|FOR UPDATE|campus_map_floor_demand_dedupe_count_trg|cannot be removed before expiry|cannot be removed before UTC retention" <V26>` | 0 |
| Intent FK guard | PowerShell `Select-String` for `campus_map_floor_demand_dedupe_intent_fk|REFERENCES campus_map_open_intent` | 0 (`no persistent intent FK`) |
| Academic behavior selectors | `rg -n "<eight new StudentFoundationMigrationIT methods>|assertSqlFailure|currentUtcDayMinusTwo|lock_timeout" <Academic IT>` | 0 |
| Changed-method readback | PowerShell `Get-Content` readback of V17 trigger, V26 guards/functions and added Java methods/helpers | 0 |
| Java bracket balance | PowerShell character count on both IT sources | 0 |
| Trailing whitespace | PowerShell scan on four products, packet and evidence | 0 |
| Scoped whitespace diff | `git diff --check -- <four product paths> .agent/student-academic-b/sql17-v26-content-repair` | 0 |

No Gradle, Docker, Testcontainers, PostgreSQL, compilation, staging, commit,
reset, clean, migration or deploy command was run.
