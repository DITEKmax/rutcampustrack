# Finite risk-ranked execution groups

Input product baseline `3908fad9624f97aec8891fc79338ceb6b0616bf3`. This reviewer ran no runtime checks. All 52 proposed rows remain UNKNOWN. Existing test paths below were found in the frozen repository; names are candidates, not claims that their internals cover every oracle. New focused names in JSON are proposals unless a stable author packet supplies them.

Root assigns writers and the sole heavy lease. Reuse sufficient current evidence and execute only uncovered/changed dependencies or meaningful distinct invariants. Each check records tested revision, command/exit, nonempty discovery/count/skips, synthetic IDs, live actor/grants, fixed Europe/Moscow clock where needed, and observable API/DB/history/outbox/files. Browser traces require redaction. Mocks remain scoped evidence. New critical tests need meaningful red-before or equivalent controlled mutation, then green at the reviewed revision; infrastructure failure does not prove sensitivity.

| Order | Group / rows | Risk | Sufficient actual evidence |
|---|---|---|---|
| 1 | G1 / 01–08 | Admission, roles/grants, OTP/QR/TMA, secrets, protected RPC | Focused real Auth/session/OTP/QR/TMA checks; current Vue multi-role/revoke; actual sensitive-error and protected RPC boundary. Preserve D003 and restricted initial-password exception. |
| 2 | G2 / 09–15 | FULL/LIMITED, last-FULL, archive/restore, membership | Real PostgreSQL admin/grant/headman/archive/cycle checks; one protected action per distinct capability; LIMITED Vue; durable history/grants/session no-resurrection. |
| 3 | G3 / 16–19 | Archive, stale semester authority, irreversible cascades | Semester/homework archive checks; genuine warm cache/missed event with healthy authority; distinct writer/RPC/background guards; interrupted durable synthetic cascade per coordinator. |
| 4 | G4 / 20–25 | Generation/replacement/transfer history and partial failures | Recurring/one-off/replacement/transfer DB checks; actual interrupted cross-service retry and conditional BFF ETag; builder/cancel/transfer client state readback. |
| 5 | G5 / 26–32 | Moscow window, quotas, irreversible ticket races | StudentRequestDomainIT/Marking/EventConsumer real Mongo; clocks, quota/decision races; student→headman flow; teacher historical read; current FREE_ATTENDANCE denial. |
| 6 | G8 / 40–43 | Async loss, recipient leakage, preference reset, open WS | Broker fault+durable effect; dated/current audience retry; durable preferences/binding outage/race; actual open-socket revoke event. |
| 7 | G10 / 47–49 | Real Vue, offline reload and user isolation | Finite eleven-flow packet with current seed/profiles; production SW/IndexedDB; offline reload/reconnect/logout/A→B callbacks; actual TMA adapter. |
| 8 | G9 / 44–46 | File ownership/expiry, SVG, replacement, export bytes | Actual allowed/forbidden bytes and year cutoff; browser SVG marker/storage retry; distinct format implementations and changed template/model golden content; browser download. |
| 9 | G7 / 36–39 | Helper RPC, completion/history/cutoffs, external links | MANAGE_HOMEWORK-only helper publish/edit/next-lesson RPC; completion race/exact DATE cutoff; Vue rollback/focus and unsafe-link rendering. |
| 10 | G6 / 33–35 | Statistics oracle and stable filtered pagination | Golden denominators/transfer/future/zero dataset; actual projections/changed models; tied rows across pages and Vue URL/back/export. |
| 11 | G11 / 50–52 | Stable mappings/history, recovery, author/remote gates | Enum sensitivity/Flyway clean+historical upgrade; actual disposable DB/files restore and admitted replay; formatter semantic review; exact-SHA CI/Coverage/Security/readback. |

This order ranks risk, not a strict dependency chain. Independent ready work proceeds under its writer/lease. G10 harness and G11 formatter inventory/config preparation start early; real clients use integrated stable behavior, and mass formatting/remote gates follow semantic integration. No exhaustive permutations or benchmarks are required.

## Existing focused entry points

Run only selected relevant candidates from the assigned worktree after lease. These examples do not request every command or duplicate broad suites. Respect repository compilation ordering, `test` versus `integrationTest`, namespaces and the max-workers cap. Selected methods must actually run with meaningful assertions.

```bash
# G1
./gradlew :services:auth-service:auth-app:integrationTest --tests '*JdbcSessionAuthorityIT' --tests '*TmaIT' --no-daemon --max-workers=2
# G2
./gradlew :services:academic-service:academic-app:integrationTest --tests '*AdminAccessAuthorityIT' --tests '*AdminPermissionRpcIT' --tests '*UserArchiveLifecycleIT' --tests '*GroupHeadmanAssignmentIT' --tests '*GroupPromotionCycleIT' --no-daemon --max-workers=2
# G3
./gradlew :services:academic-service:academic-app:integrationTest --tests '*SemesterArchiveStateIT' --tests '*HomeworkBindingArchiveIT' --no-daemon --max-workers=2
# G4 schedule
./gradlew :services:schedule-service:schedule-app:integrationTest --tests '*RecurringScheduleItemIT' --tests '*OneOffLessonControllerIT' --tests '*AssignmentReplacementServiceIT' --tests '*LessonTransferWriterIT' --no-daemon --max-workers=2
# G4 homework participant
./gradlew :services:academic-service:academic-app:integrationTest --tests '*HomeworkBindingTransferIT' --no-daemon --max-workers=2
# G5
./gradlew :services:attendance-service:attendance-app:integrationTest --tests '*StudentRequestDomainIT' --tests '*MarkingIT' --tests '*EventConsumerIT' --no-daemon --max-workers=2
# G8 local atomicity/effect candidates; broker interruption still separate
./gradlew :services:attendance-service:attendance-app:integrationTest --tests '*OutboxAtomicityIT' --tests '*EventConsumerIT' --no-daemon --max-workers=2
# G6 registry
./gradlew :services:academic-service:academic-app:integrationTest --tests '*GroupRegistryIT' --no-daemon --max-workers=2
```

No existing baseline `SemesterCacheConvergenceIT` or `OutboxBrokerConfirmIT` file was found in file-name inspection. These JSON names denote proposed focused checks or equivalent actual stand evidence. Empty/wrong wildcard selection cannot pass those rows. New regressions require a stable writer packet before quality review.

The available current Vue specs are `tests/e2e/specs/auth-token-lifecycle.spec.ts` and `mini-app.spec.ts`. From `tests/e2e`, after task-owned HTTPS stack, browser availability and current seed preflight:

```bash
E2E_BASE_URL=https://localhost:8443 npx playwright test specs/auth-token-lifecycle.spec.ts specs/mini-app.spec.ts --project=chromium --grep @smoke --workers=1
```

Actual execution proves only selected auth/TMA paths. `docs/validation/cloud-20261011/receipts/frontend-inventory/finite-runtime-packet.md` supplies eleven finite flows, seed and proposed `mobile-role-flows.spec.ts`/`mobile-offline.spec.ts`; those filenames did not exist at baseline. Their command becomes valid after harness writing and verified discovery. Offline requires production build, `serviceWorkers: 'allow'`, activation and reload; auth specs block workers. Keep `VITE_MOBILE_FIXTURE_MODE=false`; Spring2026 seed alone supports auth/empty states, not current lesson-domain operations. Use live capability catalog and profiles.

## Closing rule

Sufficient actual evidence closes a row at its tested revision. Add variants only for distinct accepted rules, guard/query/format implementations, new failures or changed dependencies. Evidence covering several rows is reused with scope. Family PASS reconciles all applicable boundaries; this focused 52-row proposal does not excuse a separately accepted significant original clause.

L05/L06 measurements can remain diagnostic/optional with justified disposition; no arbitrary count/latency or migration blocks DONE. L08 requires honest ownership/atomicity evidence, not DB split. Genuinely unavailable provider/platform/operations checks remain scoped UNKNOWN/BLOCKED with concrete dependencies. Final formatting, SHA equality and current gates remain owner requirements. Dry-run, old Actions results, skips/empty runs or green backend-only build cannot substitute.
