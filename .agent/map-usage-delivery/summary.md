# Map usage delivery

## Scope

S2 package in detached worktree `map-usage-delivery-20260922` at frozen
baseline `368e99de779a72c33c831243dcf310fffb4422d1`. The package records a
successful user-facing floor-plan opening once per logical UUID and exposes the
persisted `all_time` count per floor in the existing ADMIN map inventory. The
future ADMIN total is a query over the same daily aggregate.

Owner decisions recorded in `contract.md`: use a dedicated configured
HMAC-SHA256 key, fail closed for event writes when it is absent, and count
every distinct opening UUID while treating the same UUID as a retry. V30 drops
only the old owner/floor/day suppression; it retains owner/intent idempotency
and existing aggregate history.

## Criteria and evidence

- Event boundary is the successful `load` event of the current SVG viewer;
  manifest, plan reads, PNG-only/independent transport, pre-viewer SVG load
  and ADMIN upload preview do not call the event endpoint. `MapScreen.vue`
  keeps one UUID for a logical floor-plan key, reuses it on transport/auth
  retry, aborts stale requests on context/unmount, and resets it on a floor,
  plan or authenticated-generation change.
- Telemetry failure is rendered as an in-card status and does not replace the
  loaded map. The map client and screen bind pending event/asset work to the
  existing PWA/TMA session generation; the only App changes are the two
  approved `CampusMapClient` constructor callbacks.
- Academic authorization is reused through `CampusMapReadService`; the event
  requires an existing active building/floor pair and a published plan.
- `CampusMapUsageRepository` inserts the immutable open intent and dedupe row;
  the existing database trigger remains the only increment authority.
- `CampusMapUsageCleanupJob` runs under the existing scheduled/ShedLock
  infrastructure, deletes expired dedupe children before 48-hour intents at
  the database's UTC deadlines, and preserves the daily aggregate.
- V30 migration removes only `owner_floor_day_uq`, allowing distinct UUIDs on
  the same floor/day. `StudentFoundationMigrationIT` exercises two UUIDs,
  retry duplicate, per-floor aggregate and total sum.
- Dedicated owner HMAC configuration is `CAMPUS_MAP_USAGE_HMAC_KEY`; it is
  never derived from JWT or gRPC credentials and is never printed.
- Canonical `/api/v1/map/.../opens` and existing `/api/v1/student/map/.../opens`
  adapters forward the UUID through BFF and Academic gRPC and return 204.
- ADMIN map DTO/client/screen render the server `openCount` with its
  `openCountPeriod` label; client-side accumulation is absent.

## Checks

- `npm ci` from the isolated `frontends` directory — exit 0, using the existing lockfile; no package manifest or lockfile change was made.
- `npm run typecheck --workspace @rct/mobile-core` from the isolated `frontends` directory — exit 0 (`tsc -p tsconfig.json --noEmit`).
- `npm run typecheck --workspace @rct/pwa-vue` and
  `npm run typecheck --workspace @rct/tma-vue` — exit 0 after the two
  generation-callback hunks.
- `npx vitest run mobile-core/src/api/map-client.test.ts` from `frontends` —
  exit 0, one stale-generation transport test passed; the client rejects a
  response arriving after the authenticated owner generation changes.
- The bounded native Gradle invocation from this worktree (session `99314`) — exit 0, `BUILD SUCCESSFUL`, 43 actionable tasks (8 executed, 35 up-to-date). It ran `:services:academic-service:academic-app:test` with `CampusMapUsageServiceTest` and `CampusMapOwnerHmacProviderTest`, a separate `:services:academic-service:academic-app:integrationTest` selector for `StudentFoundationMigrationIT.v30CountsDistinctLogicalOpeningsAndKeepsSameIntentIdempotent`, and `:services:mobile-bff:mobile-bff-app:compileJava`, with `--no-daemon --no-parallel --max-workers=1 --no-problems-report`. The initial session `21055` stopped on Mockito `UnnecessaryStubbingException` in the new test fixture; moving the two published-floor stubs into the two applicable cases fixed that fixture, and the complete bounded rerun passed. No further heavy checks are planned.
- `git diff --check` — exit 0 (Git emitted only LF-to-CRLF normalization
  warnings).
- The native retention gate (session `40797`) — exit 0, `BUILD SUCCESSFUL`,
  37 actionable tasks (3 executed, 34 up-to-date). It ran only
  `:services:academic-service:academic-app:integrationTest --tests
  'ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.intentAndDailyRetentionUseIndependentDeadlines'`
  with `--no-daemon --no-parallel --max-workers=1 --no-problems-report`.
  XML `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.migration.StudentFoundationMigrationIT.xml`
  reports `tests=1`, `failures=0`, `errors=0`, `skipped=0`; the cleanup job
  deleted the expired dedupe child before its intent and preserved the daily
  aggregate assertion.

## Runtime evidence and limits

No live PWA/TMA → Academic → BFF → ADMIN refetch evidence exists in this
worktree. Runtime acceptance still needs the configured dedicated key and the
root-owned harness. No deploy, push, production data, global session/auth
redesign, V29 migration, generated dependencies or foreign worktrees were
changed.

## Inventory

Added:

- `.agent/map-usage-delivery/contract.md`
- `.agent/map-usage-delivery/summary.md`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageConfig.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageProperties.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapOwnerHmacProvider.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageCleanupJob.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V30__campus_map_distinct_openings.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapOwnerHmacProviderTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapUsageServiceTest.java`

Modified:

- `frontends/mobile-core/src/api/map-client.ts`
- `frontends/mobile-core/src/api/map-client.test.ts`
- `frontends/mobile-core/src/api/types.ts`
- `frontends/mobile-core/src/features/map/MapScreen.vue`
- `frontends/mobile-core/src/features/admin-map/AdminMapScreen.vue`
- `frontends/mobile-core/src/features/admin-map/admin-map-screen.pcss`
- `frontends/pwa-vue/src/App.vue` (one approved map-client generation callback)
- `frontends/tma-vue/src/App.vue` (one approved map-client generation callback)
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/map/CampusMapAdminModels.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/OutboxConfig.java`
- `services/academic-service/academic-app/src/main/resources/application.yml`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/MapApi.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentMapApi.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MapAcademicClient.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapQueryFacade.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/StudentMapCompatibilityController.java`

Deleted: none.
