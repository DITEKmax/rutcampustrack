# Student target correction — 2026-09-27

Scope: notification request cancellation and exact attendance-row focus only; worktree `codex/notification-student-routes-20260927`, base `2b2aa97f721a50ae4124b278897f99573a9923fa`.
Criteria: EXCUSE/LATE_CHECKIN target cancel re-reads exact request, checks kind and current `canCancel`, calls cancel API, and returns mapped server result without bucket traversal.
Criteria: cancellation success/error is published only for the same active intent; 403/404 remain unavailable; later context responses are stale; initial exact lesson target gets focus.
Evidence: controller tests cover both request kinds with empty buckets, stale student context, and cancellation 403; final run passed 16 tests.
Diff: `requests-controller.ts`, `use-requests.ts`, `requests-controller.test.ts`, `StudentFeatureOwner.vue`, `RequestsScreen.vue`, and `AttendanceScreen.vue`.
Initial check batch found the same `exactOptionalPropertyTypes` error in PWA/TMA for optional error title; omitted `undefined`, then reran the full scoped batch.
Checks ran from `frontends/` on Windows PowerShell; exact commands and exit codes are in `.agent/checks.json`.
Runtime evidence: no authenticated live PWA/TMA session or browser flow was run; controller tests are Node-level evidence only.
Limitations: no live UI acceptance; `BRAND_DIRECTION.md`, `A11Y_REQUIREMENTS.md`, and `docs/wireframes/` are absent in this worktree; the focus fix changes no styling.
Correction 2 (base `700a6aed900affd362c4358e679958c867864a51`): target cancel refreshes only loaded/in-flight buckets at page 0 through `loadBucket`; list refresh failures stay in the bucket error state and do not reject cancellation.
Follow-up checks: mobile-core typecheck exit 0; focused controller Vitest exit 0, 18 tests, including open→archive refresh and best-effort refresh failure. No live runtime was run.
