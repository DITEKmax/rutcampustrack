# Stable review packet — JS-STUDENT-01-r1

Date: 2026-09-06. Risk: S3. Review state: pending fresh independent Sol high
review because the agent thread limit prevented scheduling a reviewer.

## Revisions

- Original repository baseline: `87784165874e2da6fc261abc1c01584e24624289`.
- Frozen contract/foundation baseline: `3df5fbe6736cb2add826392ee30a4e786e96fbe1`.
- Frontend lane: `81c96755` (Terra high).
- Backend/contract lane: `99f8848e` (Sol high requested; runtime model metadata unavailable).
- Stable integration merge: `f04542e10feb4ebe01e6acc465feea83b4783dc8`.
- Required full review range, including the previously unreviewed contract foundation:
  `87784165874e2da6fc261abc1c01584e24624289..f04542e10feb4ebe01e6acc465feea83b4783dc8`.
- Primary FE/BE implementation range:
  `3df5fbe6736cb2add826392ee30a4e786e96fbe1..f04542e10feb4ebe01e6acc465feea83b4783dc8`.

## Original goal and contract

The frozen goal, source evidence, ownership, behavior, constraints, acceptance
criteria and verification contract are in `.agent/vertical-js-student-01/contract-r1.md`.
Owner deltas are dated in `.agent/vertical-js-student-01/decisions.md`. The relevant
original sources are:

- `docs/research/reference-rutcampustrack-design/knowledge/job-stories.md:702-708`;
- `docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:530-558`;
- `docs/design/packets/JS-STUDENT-01/` and the VISUAL-r3 correction recorded in
  `.agent/vertical-js-student-01/today-visual-source-correction.md`.

## Review scope

Review the complete range from the original repository baseline, including the
contract/foundation commit, for functional regressions, authentication and
scope boundaries, public/generated contract drift, Mongo transaction/race behavior,
outbox/event loss or duplication, PWA cached-read isolation, offline mutation
prevention, and consumer handling of `CANCELLED/GEO_CONFIRMED`.

The backend owns eligibility, time windows, cooldown, geofence outcome, idempotency,
pair coordination and mutations. The Mobile BFF composes only authenticated read and
command projections. Gateway/client `X-User-*` headers are never identity. PWA cache
is user-partitioned, read-only offline, cleared on logout/account switch, and never
caches tokens or queues mutations. TMA is online-only.

The public Mobile BFF source is Java → OpenAPI → generated TypeScript/fixtures. The
late-check-in legacy contract is Java → attendance OpenAPI → three generated clients;
local aliases derive the lowercase four-state response enum. Look for any handwritten
contract divergence.

## Evidence

- Backend commands, exit codes, diagnostics and runtime: `be-checks.json`.
- Full runtime assertion result: `runtime-evidence/20260906-184531/runtime-result.json`.
- Frontend checks, browser geometry, interaction, cached recovery and service-worker
  runtime: `fe-evidence.json`.
- Offline shell reproduction/acceptance details: `runtime-test-packet.md`.
- Backend implementation summary: `be-summary.md`.

## Known limits

- A real Telegram injected host was not available. The mock host passed, while real
  TMA host execution remains a separate runtime gate.
- CUA offline reload diverged with an empty app despite controller/cache 7/7. The
  independent standard Chrome checker passed the same one-online-load then offline
  scenario; both observations are retained in FE evidence.
- Raw runtime service logs and intermediate failed-run artifacts are excluded from
  the commit. The final structured result contains the asserted state/trace chain and
  no secrets.

Report each finding with severity, file:line, evidence, impact and reproduction.
Distinguish defects from questions. The required completion condition is no unresolved
blocking finding from a fresh independent Sol high reviewer.
