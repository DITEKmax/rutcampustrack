# Public mass-cancel removal — implementation evidence

## Scope and contract

- Risk: S2.
- Worktree: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-mass-cancel-removal`.
- Branch: `codex/mass-cancel-removal-20260920`.
- Frozen base and current revision before edits: `ed9b63c9449b23fa3f5dbf2feb52cd8f9e9cfe83`.
- Rules source: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\RULES.md`, SHA-256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Source map: `.agent/lessons-mass-cancel-removal/RESULT.md`.

The owner decision removes the public `POST /schedule/lessons/mass-cancel` operation while retaining individual cancellation and restoration, their audit/events/history, and the repository query used by gRPC. The bounded implementation removes the route, controller delegation, batch service method, dedicated DTOs, checked-in OpenAPI route/schemas, generated Schedule client exposure, and web-panel aliases. The existing `LessonApiIT` now covers an authenticated missing-route request with lesson and outbox no-effect assertions and checks runtime API docs for route/schema absence.

The eleven assigned product/test/generated paths are:

1. `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java`
2. `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java`
3. `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java`
4. `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelRequest.java`
5. `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelResponse.java`
6. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java`
7. `docs/openapi/schedule.json`
8. `frontends/web-panel/src/app/api/generated/schedule.types.ts`
9. `frontends/pwa/src/api/generated/schedule.types.ts`
10. `frontends/mini-app/src/api/generated/schedule.types.ts`
11. `frontends/web-panel/src/app/api/schema.ts`

No migrations, event/history implementation, shared repository query, lockfile, dependency, unrelated generated contract, frontend UI, Figma file, accepted union, or original dirty checkout was changed.

## Criteria and evidence

- Public Java mapping, delegation, batch method, and DTOs are absent: `java-contract-absence=PASS`.
- Checked-in OpenAPI route and both schemas are absent and JSON parses: `openapi-schedule-absence=PASS`.
- Three Schedule generated outputs were regenerated from the updated document and contain no mass-cancel exposure; web-panel aliases are absent: `generated-contract-absence=PASS`.
- Generation used the read-only trusted package `openapi-typescript@7.13.0`, `immutable:false`, `astToString`, and the existing generated-file header. Package SHA-256: `70BB8B789FADEDB1C1D0923907A5B9B9FDF64E727E8182D9FA9128BC5723769B`.
- Shared query `findByScheduleItemIdInAndDateBetweenAndStatusIn` remains in the repository and `ScheduleGrpcServiceImpl`: `shared-query-preserved=PASS`.
- The V17-safe `LessonApiIT` fixture uses fresh group IDs and direct occurrence/physical lesson rows, checks authenticated 404 plus unchanged lesson/outbox state, and checks `/api-docs` omits the route and schemas: `v17-fixture-static=PASS`, `lesson-api-regressions-static=PASS`.
- The diff contains exactly the eleven assigned paths: `eleven-path-diff=PASS`.

## Checks

All lightweight checks below were run in the implementation worktree on Windows PowerShell and returned exit code 0:

| Check | Command/evidence | Exit |
|---|---|---:|
| Whitespace | `git diff --check` | 0 |
| OpenAPI validity/absence | Node JSON parse plus absence assertions for `/schedule/lessons/mass-cancel`, `MassCancelRequest`, and `MassCancelResponse` | 0 |
| Java contract absence | `rg` over the three Java product paths for `MassCancelRequest`, `MassCancelResponse`, `massCancelLessons`, and the removed mapping | 0 |
| Generated/web contract absence | `rg` over the three generated Schedule files and `schema.ts` | 0 |
| Shared query preservation | `rg` for `findByScheduleItemIdInAndDateBetweenAndStatusIn` in repository and gRPC caller | 0 |
| Task-local generator contract | Dynamic import of trusted `openapi-typescript` 7.13.0 with `immutable:false` and `astToString`; generated outputs scanned for forbidden exposure | 0 |
| V17 fixture static guard | Required `lesson_occurrences`, `current_lesson_id`, `assignment_id`, `occurrence_id`; no repository `deleteAll()` fixture cleanup | 0 |
| Lesson API regression static guard | Authenticated removed-route request, precise `.isNotFound()`, `/api-docs` route/schema absence assertions | 0 |
| Scope count | `git diff --name-only` compared with the eleven-path contract | 0 |

The generator and absence checks were lightweight and schedule-only. Git emitted normal LF-to-CRLF working-copy warnings and the environment emitted a non-interactive PSReadLine warning; neither changed a file or failed a check.

## Runtime status and intended command

The PostgreSQL/Spring `LessonApiIT` runtime was not run in this leaf because root’s H85 heavy lease is reserved for the isolated runtime queue. It therefore has no runtime PASS claim and no exit code. Root should run the focused command after reserving the isolated database resource:

```text
.\gradlew.bat :services:schedule-service:schedule-app:integrationTest --tests "ru.rutcampustrack.schedule.integration.LessonApiIT"
```

The implementation is frozen pending that root-owned runtime check and the required independent review. This report does not claim whole-product readiness.

## Diff and hashes

The final bounded diff before evidence freeze is 11 paths, `76 insertions(+), 787 deletions(-)`. Deleted DTOs are represented by their deletion in the raw diff; all remaining working files have these SHA-256 values:

```text
docs/openapi/schedule.json|86F121AEE4041292EF772BFCB587B101CC3C777A92D7AE00F43BF827DE3B74B9
frontends/mini-app/src/api/generated/schedule.types.ts|3BCEF0EAD0D2AB4F1B9FEBC8EC4E11640876D5E2155EC484B50A9FA5356609B0
frontends/pwa/src/api/generated/schedule.types.ts|3BCEF0EAD0D2AB4F1B9FEBC8EC4E11640876D5E2155EC484B50A9FA5356609B0
frontends/web-panel/src/app/api/generated/schedule.types.ts|3BCEF0EAD0D2AB4F1B9FEBC8EC4E11640876D5E2155EC484B50A9FA5356609B0
frontends/web-panel/src/app/api/schema.ts|4D5DCD7C073730AFD2BBC3CFA1E6BEFD9009263409D73C98631179C532329C42
services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java|BF787905F4CD63EBADC307A0B4FD4BAED03ABC07F4BE99CDF923026D72AB99ED
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java|8050F5020B825F4D85FAEFA318F2B7134481DF08DA372FE3A123B0E6147C6073
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java|4397805FE84D2B2E8663A0ABA66D5998AF0F25687BDB711E3CB3FC4A1D932A56
services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java|94E295C02C07A4F6E4B662284A545B9398A8218BFC9A80187026CD568500C7E7
```

Baseline blob IDs from `git diff --raw` are `ff9feb0e` (OpenAPI), `0e81e2bc` (each generated Schedule file), `972edea3` (web aliases), `62fb37d4` (API), `94b7974e`/`45a1eeb6` (deleted DTOs), `7018b95f` (controller), `116c54da` (service), and `5963b8c2` (integration test).

## Limitations

No Gradle compile or integration runtime was run by this leaf. Historical Javadocs outside the exact eleven-path contract may still contain the words `massCancel`; no live mapping or service method remains in the assigned product scope. Any additional caller or scope issue found by root’s runtime/review must be reported as a new evidence-backed amendment before editing.
