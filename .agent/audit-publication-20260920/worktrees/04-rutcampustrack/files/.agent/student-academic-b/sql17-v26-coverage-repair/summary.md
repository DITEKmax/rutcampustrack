# Handoff summary

`SQL17_V26_EXACT4_ACCEPTED / RELEASED`

Изменены ровно два назначенных test source файла:

1. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java`
2. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`

V17 теперь доказывает valid recurring/one-off control и все четыре exact
cross-origin operations (INSERT+UPDATE) с `P0001`, точным message и
неизменными count/origin/status. V26 добавляет exact READY dimension,
published format INSERT, missing intent и mismatched-day dedupe paths с
неизменными format/asset/daily aggregate invariants.

Static checks прошли: revision, frozen SQL hash/bytes, marker/readback,
delimiter/import/trailing-whitespace и scoped `git diff --check` — exit `0`.
Post hashes: Schedule IT
`633285777706AF26FEF3B847B172BA1805C41E6607B5170025F4DE6A02AF1A68` / 28608;
Academic IT
`14C68464CFBD29EDF20C87021D9353863C81E5B0044C193D09930FBBCEE7FB8B` / 58295.
V17/V26 SQL frozen hashes unchanged.

Root выполнил exact runtime recheck: Academic `15/15` (session `75913`, exit
`0`, XML `20715B9C9782407A9842AC0324C2CF9EE577445AD2001DCF2FEA89FCDE4C4254` /
94655 bytes) и Schedule StudentOccurrence `7/7` (session `2390`, exit `0`, XML
`7E316529DAD60FDB6814B9FDE3014B30B2137F47ADB39BF92505AFE9F509367F` /
36150 bytes). Flyway не rerun; prior `3/3` XML evidence preserved. New exact
scope `22/22`, 0 failures/errors/skipped; processes/ports clear, Docker empty,
heavy lease released. Terra escalation не применялась: нет recorded
defect/complexity gate.

Fresh independent read-only reviewer `/root/sql17_26_coverage_recheck`
(`gpt-5.6-sol`, high, fork-none) дал `FINAL PASS —
SQL17_V26_COVERAGE_RECHECK_PASS`, findings none. Оба MEDIUM закрыты: V17
exact recurring/one-off INSERT+UPDATE rollback и V26 exact dimension,
published format INSERT, missing/mismatched intent с unchanged graph/dedupe/
aggregate. Flyway 3/3 принято из прежнего evidence; текущий XML отсутствует
после focused-task cleanup, что записано как limitation. Focused migration
scope принят; full-role acceptance остаётся открытой.
