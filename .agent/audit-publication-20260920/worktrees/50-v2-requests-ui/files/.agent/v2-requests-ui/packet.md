# Requests UI R4 compact contract

Recorded: 2026-09-13
Role: fresh bounded implementation leaf / sole writer for R4
Model/effort: gpt-5.6-luna / max (assigned by root)
Risk: S3 task gate; R4 product slice is two MEDIUM UI lifecycle defects and one LOW validation defect
Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md` (SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`)
Current: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md`
Baseline: `b8220ac92125a8afa37598b270aa4fab7aa1f470` (`E`), clean isolated source
Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-ui`
Branch: `codex/v2-requests-ui`

## 1. Goal

Repair the R1 Requests findings assigned to R4 while preserving the verified E
Requests import and the existing mobile Requests interaction model. Deliver a
stable frontend source diff, focused deferred tests, and evidence for the main
integration/review lane.

## 2. Context/evidence

Fresh Sol R1 review `ffde92fdd1a7791ec3fb375102dc878d2fb2adc9..b8220ac92125a8afa37598b270aa4fab7aa1f470` reviewed 116 files and reported:

- MEDIUM: `requests-controller.ts` uses one `operationEpoch` for OPEN, ARCHIVE,
  and options. A second request invalidates the first response and prevents its
  `finally` from clearing `loading`/`optionsLoading`; the owner then refuses to
  reload a permanently busy resource.
- MEDIUM: `StudentFeatureOwner.vue` handles attachment download failures only
  through terminal owner auth handling. HTTP 410/5xx and a `window.open` null
  result are silent; opening a popup after an async fetch also loses the user
  gesture. The card has no per-attachment pending/error state.
- LOW: `requests-controller.ts` and `RequestAttachmentField.vue` accept a file
  when MIME **or** extension matches. The authoritative attendance backend
  requires allowed MIME, extension, and detected signature to agree; its current
  pairs are JPEG + `.jpg`/`.jpeg`, PNG + `.png`, and PDF + `.pdf`.

Pre-fix source evidence is recorded in `evidence.md`. Baseline source hashes are
recorded there and were read from the clean E worktree; no tests/build/runtime
were run in this leaf before the main lane grants checks.

## 3. Relevant scope

Owned product files:

- `frontends/mobile-core/src/features/requests/requests-controller.ts`
- `frontends/mobile-core/src/features/requests/requests-controller.test.ts`
- `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue`
- `frontends/mobile-core/src/features/requests/RequestCard.vue`
- `frontends/mobile-core/src/features/requests/RequestsScreen.vue`
- `frontends/mobile-core/src/features/requests/requests.pcss` (only styles needed for the existing inline attachment states)
- `frontends/mobile-core/src/features/requests/types.ts` (only feature-local view state if needed)
- `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue`
- `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` (only focused owner lifecycle coverage if needed)

Owned evidence and pointer are under `.agent/v2-requests-ui/`. R5 owns env,
OpenAPI, generated types, and contract tests; those paths are excluded here.

## 4. Required behavior

1. Use a context/owner generation independent from per-bucket and options
   freshness tokens. OPEN and ARCHIVE loads may settle in either order, and an
   options response may settle concurrently; each current resource settles its
   own loading flag and publishes only its own result. Owner/session changes
   invalidate every late response and reset the feature state.
2. Keep the existing page/detail hydration limits and command semantics. Do not
   weaken auth, stale-owner, offline, read-only, or idempotency guards.
3. Track attachment action state by `(requestId, attachmentId)`: pending is
   visible and disables the action; HTTP 410/5xx/network failures and blocked
   popups are visible inline with a retry action. Start the blank popup in the
   synchronous user-click path, navigate it only after the authenticated blob
   arrives, and revoke ObjectURLs on expiry, stale owner, popup failure, and
   teardown. Do not publish an old owner result.
4. Make attachment selection reject the same MIME/extension combinations the
   current backend accepts. Keep size/count/total checks and provide clear
   Russian format feedback; do not broaden backend acceptance.
5. Add meaningful deferred controller tests for both completion orders and
   attachment validation tests for matching and mismatching pairs. Add focused
   rendered/component tests only where the new observable attachment states
   require them.

## 5. Constraints

- Fresh worktree and branch above are the only writer; preserve all unrelated
  source and user changes.
- No generated/schema/OpenAPI edits, no R5 paths, no new dependencies/assets,
  no router/cache/auth owner, no Figma writes, and no redesign beyond the
  compact behavior above.
- No Gradle, Docker, build, dependency installation, browser/runtime, or test
  execution in this leaf. Author tests and propose exact commands for main.
- Use existing Vue/PCSS patterns, semantic tokens, rem lengths, keyboardable
  controls, and Russian text. Do not turn WARN/ERROR into a code change without
  the finding reproduction already recorded above.
- No children, Terra, push, deploy, main merge, secrets, or production actions.

## 6. Existing patterns

`RequestsController` owns in-memory request state and stale owner checks;
`useRequests` exposes a reactive view. `StudentFeatureOwner.vue` is the single
mobile owner/navigation boundary and already tears down ObjectURLs. `RequestCard`
is the existing attachment read surface; `RequestsScreen` forwards owner
actions. `RequestAttachmentField` owns keyboard file selection and server driven
limits. The backend `StudentRequestService` is the authority for exact file
signature/MIME/extension concordance. Existing request styles use semantic
variables and focus mixins.

## 7. Acceptance criteria

- OPEN, ARCHIVE, and options complete independently in both deferred orders;
  each current resource clears its own busy state, while a prior owner cannot
  publish late data.
- Attachment pending/error states are visible per card and recoverable; 410,
  5xx/network, blocked popup, stale owner, and teardown paths do not leave silent
  UI or leaked ObjectURLs.
- Matching JPEG/PDF/PNG combinations pass client validation; mismatched MIME/
  extension pairs fail before submission with clear feedback, including both
  the controller and file field paths.
- Existing Requests routing, offline/read-only/auth/idempotency behavior and
  unrelated files remain unchanged.
- Focused tests are authored, and the main lane can run typecheck/lint and the
  affected Vitest suites after integration. No required generated or R5 check
  is claimed by this leaf.

## 8. Verification

Main lane should run from `frontends` after review/lease:

```text
npm exec --offline -- vitest run mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/attachment-validation.test.ts mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/RequestsScreen.test.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1
npm run typecheck --workspace @rct/mobile-core
npm run lint --workspace @rct/mobile-core
```

The leaf records baseline and granted D1/N1 commands with exit codes in
`checks.json`; runtime is explicitly N/A here and recorded in
`runtime-evidence.json`. Main owns browser flow, screenshot, integration, and
final independent Sol recheck.

## 9. Do not

Do not alter R5 env/OpenAPI/generated files or backend validation; do not hand
edit generated artifacts; do not widen MIME acceptance; do not suppress or
reinterpret terminal auth errors; do not persist drafts/files/tokens; do not
leave popup/ObjectURL work outside owner fences; do not claim tests/runtime PASS
before the main lane executes them.

## Fresh bounded LOW repair addendum — 2026-09-15

This addendum supersedes only the MIME-normalization portion of the earlier
R4 handoff; the accepted Owner regression and all other product fixes remain
frozen. It is recorded against HEAD `b8220ac92125a8afa37598b270aa4fab7aa1f470`
with the same sole-writer worktree and owner rules above.

### 1. Goal

Repair the two LOW findings from the independent FULL R4 Sol review with the
smallest frontend validation change and complete, current handoff evidence.

### 2. Context/evidence

`normalizeContentType` used ECMAScript `trim`, while the authoritative
`StudentRequestService.java:1645-1688` uses Java `String.strip().toLowerCase`.
The backend preserves U+00A0, U+2007, U+202F, and U+FEFF around a declared MIME;
the client removed them and could accept a request the backend rejects.
The existing Java-equivalent `stripJavaWhitespace` helper already governs the
filename path.

### 3. Relevant scope

Only `frontends/mobile-core/src/features/requests/attachment-validation.ts`,
`attachment-validation.test.ts`, and the scoped `.agent/v2-requests-ui`
packet/evidence/checks/summary/diff records may change.

### 4. Required behavior

Route MIME normalization through `stripJavaWhitespace`, then lowercase. Add
real `acceptsRequestFile` MIME-path fixtures for four server-preserved negative
suffixes (U+00A0/U+2007/U+202F/U+FEFF) and four Java-whitespace positive pairs
(tab/U+001E/U+1680/U+3000).

### 5. Constraints

Preserve null/undefined handling, lowercase matching, filename sanitization and
extension behavior, exact MIME/extension concordance, the accepted Owner.vue
and Owner.test bytes, and the existing client environment. No R5/backend,
generated/config/dependency, or unrelated product changes.

### 6. Existing patterns

Reuse the existing `stripJavaWhitespace` helper and `acceptsRequestFile` test
path. Keep the Java source as the MIME normalization authority and do not add
a second whitespace implementation.

### 7. Acceptance criteria

All eight new MIME fixtures exercise `acceptsRequestFile`; matching existing
JPEG/PNG/PDF pairs and filename behavior remain green; the final inventory is
10 modified plus 4 added product files; owner fence hashes remain exact; no
foreign worktree changes appear.

### 8. Verification

Run the existing one-worker five-file Vue-configured Vitest union, mobile-core
typecheck, mobile-core lint, and `git diff --check`. Record each command and
exit code, current source hashes, runtime N/A, and limitations. A sandbox
access denial may receive only the exact scoped harness escalation already
recorded; it is not a product defect.

### 9. Do not

Do not redesign the validator, re-run mutation proof, touch Owner files, widen
backend acceptance, change R5 paths, install dependencies, run browser/build/
Gradle/Docker/runtime, create children, use Terra, or claim integration/review
completion.
