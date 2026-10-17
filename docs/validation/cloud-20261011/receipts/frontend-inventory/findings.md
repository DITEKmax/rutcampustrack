# Frontend and formatting inventory — bounded receipt

Inspected revision: 3908fad9624f97aec8891fc79338ceb6b0616bf3. Repository read-only. Only this scratch directory written. No children, Gradle/Docker/browser service processes, tests or product changes started. Actual model metadata UNKNOWN by packet exception. Runtime statuses are UNKNOWN/explicit EXCLUDED, never inferred PASS.

## Deliverables

- scenarios.csv / scenarios.json: 41 finite client scenario families with current source/story refs, actor/actual grants, client, prestate/action/result/forbidden case, implementation/test references, status/blocker. Groupings are reconciliation candidates for root, not 41 distinct release features or complete 147-story denominator.
- finite-runtime-packet.md: 11 bounded real-server acceptance flows, existing exact commands/resources, required deterministic synthetic data, UI selector paths and writer dependencies.
- tracked-language-inventory.json: 6297 tracked files, raw extension and format-candidate triage including per-file reasons. Counts preserve exclusions for review rather than deciding product completeness.
- readability-hotspots.json: exact >300-character-line source candidates for formatter dry-run.
- next-checks.md: minimal language-aware formatter plan, ownership and risk-based checks.

## Confirmed client path and harness facts

1. PWA Vue /app/ and TMA Vue /mini-app/ share mobile-core domain/features/styles with separate auth/bootstrap/host adapters. Real transport is default. `VITE_MOBILE_FIXTURE_MODE==='true'` swaps both clients to fixture transport and TMA host; Dockerfiles default false. Review fixture/dev entry pages cannot prove real backend readiness.
2. Current real browser harness is tests/e2e, ci.yml e2e-auth, docker-compose.e2e.yml. Only auth-token-lifecycle.spec.ts + mini-app.spec.ts selected by current CI. Existing smoke tests perform real gateway/auth/DB HTTP and role session/bootstrap, with synthetic signed Telegram teacher WebApp host. No API stubs in current PWA auth fixture. External Telegram launch/download/provider delivery not proven by that smoke.
3. Older auth.spec.ts, role-*.spec.ts, headman-mark.spec.ts, student-excuse.spec.ts, admin-create-user.spec.ts target deferred Angular paths and are explicitly excluded from current Vue CI smoke (README). They cannot be reused as passed Vue role acceptance. TEST_USERS.expectedLandingPath/isHeadman is historical Angular metadata; actual grants come from server CurrentSession.
4. Playwright globally serviceWorkers='block'. PWA offline/auth test only disconnects/reconnects loaded page and waits real session; despite its name it does not reload while offline. Worker migration/push tests execute SW JavaScript in node:vm with mock caches/clients. Current mobile CI worker checks do not prove actual browser offline shell + IndexedDB hydration. Dedicated production/SW-allow browser flow required.
5. V2 synthetic seed has admin/teacher/student, headman student converted via V24, one Spring2026 Feb-Jun semester/group, geofence; V51 seeds FULL admin. Teacher Telegram binding SQL exists. Domain flow data (current Moscow lessons/assignments/homeworks/foreign group/plain student/assistant/LIMITED admin) must be created in isolated dataset; baseline auth seeds do not contain these states.
6. TLS edge ports default8088/8443. Internal DB endpoints only exposed privately; project/network/IPAM/jwt-keys volume need explicit reservation. Tests support E2E_BASE_URL pointing to root shared task stand. No stack started. Existing Windows local acceptance script uses forbidden private18514/LOCALAPPDATA and is not a Cloud harness.
7. Node24.19.0/npm11.9.0/Python3.12.14 observed; frontends/node_modules exists, tests/e2e/node_modules/root browser cache not found by light metadata checks. This does not establish browser install/runtime status. Managed environment current unrestricted enforced network, no outbound identity/secret/runtime variable capabilities observed. Real provider acceptance may be preserved from historical receipts rather than rerun; root should link those exact receipts.

## Accepted offline/session/admission rules

Owner answered early Q001=A; D002 and docs/product/decisions/2026-10-11-pwa-offline-read-only-session.md now authoritative. PWA may display last confirmed STUDENT schedule/homework read-only while server session may have expired. Online reconnect requires fresh authority before operations; no offline writes/new attachments/TMA offline authority. Explicit logout clearing/user isolation remain security checks. scenarios rows updated to UNKNOWN runtime; policy blocker removed.

Current implementation App.vue:603-680 structurally validates persisted user/role/group/semester scope then hydrates readOnly with null session/version, matching D002 policy direction. SemesterSnapshotStore serializes durable writes/cleanup and blocks unresolved cleanup; stable offline partition deliberately excludes session/reset generation. Auth owner maintains in-memory bearer, BroadcastChannel invalidation and persistent explicit-logout latch; generation-bound clients reject late callbacks/retries when owner changes. These are source mechanisms, not runtime verdicts.

TMA bootstrap signs into real /api/auth/tma and retains token/initData only memory; on unauthorized it reauthenticates using host proof. Current smoke covers valid/tampered teacher proof. Signed malformed auth_date/future/duplicate/replay/live-binding cases require targeted auth/security verification. TmaService source hypothesis sent root/security; no product bug claimed from static inspection. Owner Q002=A / D003 now confirms reusable signed initData within86400s with fresh authority, no same-session promise. D004 separately excludes only map icon legend; other map criteria remain required.

QR backend accepted by 2026-10-02 decision; scan/confirmation mobile UI expressly separate client stage (decision line7). Only that UI scenario marked EXCLUDED; QR backend stays required in root server matrix. Teacher active-group read rule from 2026-09-23 supersedes historical only-own-lessons tests. Limited-admin 2026-10-08 rule supersedes scalar ADMIN-only assumptions; actual 12-code catalog found in admin-access-client.ts, separate USER_CREDENTIALS_MANAGE and ADMIN_ACCESS_MANAGE. Assistant 5-code grant set found in headman-group-client.ts; assistant is capability subset of STUDENT scope, not invented role.

## Formatting facts and exclusions

Raw tracked source counts: Java1519, TS518, Vue126, PCSS94, Python123, SQL95, JS122, HTML101, CSS39, Kotlin build24; inventory also includes config/proto/shell/PowerShell. Conservative candidates after triage: Java1490, TS480, Vue126, PCSS94, Python105, SQL92, JS25, HTML98, CSS36. Counts may decrease/increase after exact fixture/historical/generated classification; no formatter run made.

Mobile workspace ESLint10.10.0 + typescript-eslint8.69.0 + Vue recommended, TS5.9.3/vue-tsc3.3.11, Vite7.3.6, Vitest4.1.4 and PostCSS8.5.26/postcss-mixins12.1.2. No mobile Prettier/format:check/config found; root .editorconfig absent, web-panel .editorconfig exists. Mobile CI runs contracts/typechecks/worker/build, Coverage runs Vitest; mobile lint script exists but CI mobile job omits lint. Angular has type/build/Vitest without lint script. Landing explicitly canonical dist author sources, no build/lint tool.

Python bot already Ruff py312/line120, E/F/W/I lint and generated grpc exclusions, CI lint+format gate. No Java formatter/Spotless/Checkstyle/SQL formatter found in tracked root build/scripts/CI. ShellCheck covers scripts/*.sh warning+error, dashboard JSON validation exists. Need sole shared formatter/config/lockfile writer.

Important formatting boundaries: blanket dist exclusion is wrong for frontends/landing/dist canonical author files. 87 tracked SQL files are db/migration historical Flyway sources; whitespace alters checksums. No repair/clean to hide drift. Treat versioned byte-exact migrations separately with compatible disposition before any SQL format. Do not format generated API/proto stubs, build/vendor, intentional fixtures/snapshots or machine evidence. PCSS parser must preserve @mixin/rem/tokens, Vue template whitespace and Angular separate semicolon conventions.

## Design prerequisite

rutcampustrack-design current skill not found in executor skill catalog, /workspace/.agents or repository search. Existing design references and archived SKILL.md.inactive are not declared current skill. No UI fixes attempted; source/harness/format work independent of this prerequisite. Root notified early.

## Verification/evidence

Light checks only: git HEAD/status, tracked path/extension inventory, source/config/CI reads, installed version and directory metadata. Baseline remains frozen; root task docs and D002 were concurrently written by root and preserved. Receipt JSON/CSV parse/count validated below in final summary. No product runtime PASS, no suite duplication, no processes/resources to release. Next: root assigns one finite runtime harness writer + data/stand owner; formatter shared config owner separately.
