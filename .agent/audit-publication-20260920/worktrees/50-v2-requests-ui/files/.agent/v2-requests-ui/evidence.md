# R4 historical pre-fix evidence

Recorded: 2026-09-13
Revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470`
Environment: Windows PowerShell; clean detached E source worktree for the
baseline; no secrets, dependency installation, tests, build, Docker, browser,
or product runtime used.

Entries above and in the earlier milestone sections below are historical
reproductions/checkpoints. Current-source acceptance for the fresh LOW repair
is recorded in the final section of this file and in `checks.json`.

## Scope and source hashes

The source checkout was clean before the isolated worktree was created. The
current root checkout was already dirty and was not used as a base or modified.

Git blob hashes at baseline:

| Path | Blob hash |
|---|---|
| `frontends/mobile-core/src/features/requests/requests-controller.ts` | `4a2375ccbe78ae2e1c6568fcdf3d9783bc83e94f` |
| `frontends/mobile-core/src/features/requests/requests-controller.test.ts` | `51345d7aad13774b548ee3a6b413d70134f73259` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `54b64803d8c63d5d30d2608252bcfe3f4c02eb8c` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `fd463373a42000656416996de1802b4db575d3f0` |
| `frontends/mobile-core/src/features/requests/RequestCard.vue` | `254b93442cea9f2a130ec155c336e087b7ce3ddb` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.vue` | `d4a824236b1d5937ac3140047dcb38e09cecae6d` |
| `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` | `05a83f6e6b84d83d5ce6bc5192dd6d566553e701` |
| `frontends/mobile-core/src/features/requests/requests.pcss` | `6b10ac78f0546d308accd8cece616f5ff70025fd` |
| `frontends/mobile-core/src/features/requests/types.ts` | `fbca1861f3a527c0feb3770e967d008cd65da72e` |

## Finding R4-2: shared operation epoch

Static reproduction from the baseline:

1. `loadBucket('open')` increments the single `operationEpoch` and stores that
   value in the OPEN request.
2. Before it settles, `loadBucket('archive')` or `loadOptions()` increments the
   same epoch.
3. The first response reaches `isCurrent(identity, epoch) === false`, so it
   returns without publishing and its `finally` also skips clearing the state.
4. `StudentFeatureOwner.ensureRequestsRoute` checks `state.loading` and
   `view.optionsLoading` before reloading. The stale flag therefore remains
   observable as a permanent busy state.

The same sequence works in either completion order; the defect is the shared
freshness bucket, not transport timing. Existing tests covered a late owner but
did not defer independent OPEN/ARCHIVE/options operations. New deferred tests
will make both orders observable.

## Finding R4-3: attachment failures and popup timing

Baseline `openRequestAttachment` starts `requests.downloadAttachment` and only
handles errors through `handleRequestsError`, which emits `ownerError` for
401/403. Nonterminal `StudentApiError` responses (including 410 and 5xx) are
therefore swallowed by the catch. After the blob resolves, the baseline calls
`window.open(objectUrl, ...)`; a null return immediately revokes the URL and
does not update the card. Since that call occurs after `await`, popup blockers
can reject a legitimate user click. `RequestCard` has only an ACTIVE button and
no pending/error props, so all these outcomes are silent.

Required reproduction matrix: successful open, HTTP 410, HTTP 5xx/network
failure, `window.open` returning null, owner/session change while download is
pending, and component teardown while a blob is pending. The correction will
open a blank window synchronously from the click path, fence async completion by
owner/action generation, and expose a retryable inline state.

## Finding R4-5: MIME/extension mismatch

The baseline `acceptsFile` in `requests-controller.ts` and `acceptsType` in
`RequestAttachmentField.vue` return true when either an allowed content type or
an allowed extension matches. The authoritative backend at
`services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:1645-1688`
requires allowed MIME and extension, detected signature equality, and the exact
pair mapping:

| MIME | Extensions |
|---|---|
| `image/jpeg` | `.jpg`, `.jpeg` |
| `image/png` | `.png` |
| `application/pdf` | `.pdf` |

Therefore a PDF payload named `document.jpg` with declared
`application/pdf` passes the baseline client OR check but is rejected by the
server. The client correction mirrors the pair mapping and reports the format
constraint before submit.

## WARN/ERROR handling

Git emitted the pre-existing global-ignore permission warning while reading the
dirty/root worktrees. It is unrelated to the three R4 findings and caused no
source change. No product WARN/ERROR was used as a code-change trigger.

## Post-fix source evidence

The source correction keeps the three findings bounded to the frontend Requests
surface:

- `requests-controller.ts` now carries one context generation plus independent
  OPEN/ARCHIVE bucket generations and an options generation. Hydration checks
  the bucket token before and after each detail response, so a late response
  cannot publish or leave the current resource busy. Context replacement also
  clears the options busy flag while the old request is fenced out. The owner
  uses `shouldLoadRequestOptions` to keep route entry and new-request navigation
  behind one `optionsLoading` guard. A stale options rejection is resolved
  after the generation check, so it cannot reach the owner auth-error path.
- `attachment-validation.ts` is shared by the controller and
  `RequestAttachmentField.vue`; it requires the backend's JPEG/PNG/PDF MIME
  and extension pairs when both option dimensions are present. Before extracting
  the extension it mirrors the backend filename sanitization, including slash,
  traversal, control-character, whitespace, empty-name, and 255-character
  handling.
- `request-attachment-action.ts` opens a blank popup synchronously, carries the
  authenticated blob into it only after the owner identity/generation fence,
  and handles blocked popups, download failures, stale owner, URL release, and
  teardown through explicit callbacks. `StudentFeatureOwner.vue` stores state
  by request and attachment id and `RequestCard.vue` renders pending/error/
  retry affordances.

Authz, read-only/offline guards, command idempotency, backend validation,
generated artifacts, and R5 paths remain unchanged in this worktree. Focused
tests are authored. In the granted N1 lane, the pure/non-Vue suites, rendered
Vue suite, typecheck, and lint passed. The rendered suite used the existing
`pwa-vue/vite.config.ts` under one scoped escalation after the sandbox-only
esbuild access restriction. See `checks.json` for exact commands and exit
codes.

## Sol correction: popup isolation semantics

Fresh full-Sol review found that the owner adapter passed
`noopener,noreferrer` as the `window.open` feature string. In browsers that
feature can return `null` even when the blank target is created, so the helper
treated a usable popup as blocked and skipped the download. The focused source
reproduction was the actual `window.open('', '_blank', 'noopener,noreferrer')`
call at `StudentFeatureOwner.vue:561`; the existing action mocks did not model
that adapter invocation.

The correction routes the real `window.open` object through a browser adapter
that calls the API with only `('', '_blank')` to retain the WindowProxy,
immediately assigns `popup.opener = null`, and verifies the setter result
before any promise or navigation. A setter throw or non-null result closes the
target and returns a visible isolation error; there is no unsafe fallback. The
owner still applies identity/generation/disposal fences, and existing
null-popup, 410/5xx/network, URL cleanup, stale-owner, and teardown paths remain
intact.

The action tests now assert the exact two-argument browser adapter call,
synchronous opener isolation, setter failure/close, and no
download/navigation after an isolation error. They do not claim a browser
runtime result.

## Full-Sol corrections: F1–F3

The bundled full-Sol recheck reproduced three independent defects in the
Requests surface. F1 observed route entry invoking `loadOptions` and then
`newRequest` invoking it again before the first request settled. The owner now
centralizes this path in `ensureRequestOptions`, whose shared guard includes the
controller's `optionsLoading` state. The deferred regression test proves one
transport call while the first result is pending and after it succeeds.

F2 observed an older 401/403 options rejection being thrown after a newer
options request had already succeeded. The controller now fences the catch
before setting `optionsError` or rethrowing; stale requests resolve quietly and
their finally block cannot alter current state. The test covers both a newer
success and an owner context change before a stale rejection.

F3 compared frontend raw extension extraction with the attendance service's
`safeFilename` sequence. The shared validator now applies the same trim,
separator replacement, non-overlapping `..` replacement, control filtering,
empty-name fallback, and 255-character truncation before taking the extension.
The baseline JPEG/PNG/PDF pairs remain accepted, while `proof..pdf` is rejected
because the server sanitizes it to `proof_pdf`.

Post-edit Git blob hashes at the frozen source milestone:

| Path | Blob hash |
|---|---|
| `frontends/mobile-core/src/features/requests/requests-controller.ts` | `01df8922d4ccb8ca58df1ea6067a74ff9d587ef1` |
| `frontends/mobile-core/src/features/requests/requests-controller.test.ts` | `22ae429ded7b18f992f61e383f6dac6be8665eed` |
| `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` | `de027277796478aecfbb0984da377b9ad90cae17` |
| `frontends/mobile-core/src/features/requests/RequestCard.vue` | `07691522d7f1c99574c8561cd6c683c7074fed82` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.vue` | `ac4be7422419f28cbefc340f992c51cdf76be4be` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts` | `506ef767ac2beef1c2980fc7b1ef5b36cadac33e` |
| `frontends/mobile-core/src/features/requests/requests.pcss` | `76b9ebd6cf7050ec7f139fd992eb99cc360cb488` |
| `frontends/mobile-core/src/features/requests/types.ts` | `56f600b1c6b87e4d48686712c125304caf6838af` |
| `frontends/mobile-core/src/features/requests/attachment-validation.ts` | `236861f908299444171fad219ade0d4606ab28f9` |
| `frontends/mobile-core/src/features/requests/attachment-validation.test.ts` | `05ff11f8c41d71519302531436bf8ff3a6aab2d8` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.ts` | `a57eb823083c3cfef2f7fe4eba886b6647ddcef0` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts` | `a1ba87f61cc1ac74294de30664c60e24d3cf923d` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `f1690c1fda0658ff68482d5dd6b56f0b4c1ce475` |

## Fresh completion recheck — 2026-09-15

This completion pass did not change product source. It reconciled the stale
`requests-controller.test.ts` post-fix blob entry above and reran the bounded
R4 checks from the isolated worktree at revision
`b8220ac92125a8afa37598b270aa4fab7aa1f470`.

Environment: Windows PowerShell, Node `v24.14.0`, npm `11.9.0`, own locked
`frontends/node_modules`, offline command execution.

| Criterion | Command | Exit | Evidence |
|---|---|---:|---|
| Focused R4 source tests | `npm exec --offline -- vitest run mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/attachment-validation.test.ts mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1` | 0 | 4 files / 27 tests passed in one worker |
| Mobile-core typecheck | `npm run typecheck --workspace @rct/mobile-core` | 0 | `tsc -p tsconfig.json --noEmit` |
| Mobile-core lint | `npm run lint --workspace @rct/mobile-core` | 0 | `eslint src scripts tests --max-warnings=0` |

Runtime remains N/A under the frozen contract; browser/product flow,
integration, and independent Sol recheck remain owned by main.

## Fresh F3 filename correction — 2026-09-15

Request/reference: fresh full-Sol finding F3-1 identified that the prior
client validator used ECMAScript trim while the authoritative
StudentRequestService safeFilename uses Java String.strip. The bounded owner
allocation covers only attachment-validation.ts and its focused test.

Recorded reproduction: acceptsRequestFile({ name: 'proof.pdf' + U+00A0,
type: 'application/pdf' }, limits) could remove the trailing NO-BREAK SPACE in
the client, derive .pdf, and return true; Java Character.isWhitespace does not
classify U+00A0, so the backend preserves it in the safe filename and rejects
the resulting .pdf plus U+00A0 extension. The same mismatch applies to U+2007,
U+202F, and U+FEFF.

New source evidence: StudentRequestService.java:1645-1688 strips the original
name with String.strip, replaces separators and non-overlapping .., removes
control characters, strips again, truncates with UTF-16 substring(0, 255), and
lowercases the extension substring without an extra Unicode trim.

Correction: attachment-validation.ts now applies the explicit
Character.isWhitespace-equivalent code-point set for both filename strips and
extension normalization. It preserves Java-excluded NBSP, FIGURE SPACE,
NARROW NO-BREAK SPACE, and ZERO WIDTH NO-BREAK SPACE; it retains the existing
control filtering, separator/traversal replacement, UTF-16 truncation, and
MIME/extension pair checks. attachment-validation.test.ts calls the real
acceptsRequestFile path for all four negative suffixes and for tab, record
separator, Ogham, and ideographic Java-whitespace positives.

Verification: the full authorized focused suite passed 4 files / 35 tests
(exit 0), mobile-core typecheck passed (exit 0), and mobile-core lint passed
(exit 0) in Windows PowerShell with Node v24.14.0, npm 11.9.0, and the own
offline dependency cache. No popup/owner/R5 source changed. Browser/runtime
remains N/A and independent full-Sol recheck remains open for main.

## Fresh F3 truncation correction — 2026-09-15

Request/reference: main's follow-up full-Sol finding showed that the
filename-derived extension was normalized after the server-equivalent
UTF-16 truncation. The same two assigned attachment-validation files remain
the sole product scope.

Recorded reproduction: a filename built as 250 repetitions of `a` followed by
`.pdf x` has length 256. Java safeFilename truncates it to 255 UTF-16
code units, leaving a raw suffix of `.pdf `; Java extension returns that suffix
lowercased and the backend rejects it. The prior client path passed that suffix
through normalizeAttachmentExtension, stripped the trailing ASCII space, and
could accept it as `.pdf`. A valid neighboring filename of 250 repetitions of
`a` plus `.pdf` remains accepted.

Correction: attachmentExtension now mirrors the backend extension step with
safeName.slice(dot).toLowerCase() only. mimeExtensionMatches compares that
already-derived suffix exactly and does not renormalize it. The metadata
normalizer remains separate for the server-provided extension list; no MIME,
popup, owner, backend, generated, or R5 code changed.

Verification: acceptsRequestFile tests cover the false truncation
counterexample and valid neighbor alongside the existing four preserved
NBSP-class negatives and four Java-whitespace positives. The full authorized
focused suite passed 4 files / 36 tests (exit 0); mobile-core typecheck and
lint each passed (exit 0) in Windows PowerShell with Node v24.14.0, npm
11.9.0, and the own offline dependency cache. Runtime remains N/A and the
separately allocated fresh full-Sol review remains open for main.

## Fresh R4 actual component mount and mutation proof — 2026-09-15

Request/reference: the compact R4 gate required a real mounted
`StudentFeatureOwner` to enter Requests and invoke the real `newRequest`
handler while the first options Promise remained pending. The prior controller
test only exercised a copied guard and did not prove the owner route flow.

Recorded harness reproduction: a default Vitest run transformed the SFC in
SSR mode and failed at generated `StudentFeatureOwner.vue:1054` while reading
`ssrContext.modules`. Providing a synthetic SSR context removed that exception
but left the component without a client `render` function. The bounded harness
therefore uses the existing `pwa-vue/vite.config.ts` (which already loads
`@vitejs/plugin-vue`) with `.agent/v2-requests-ui/vitest-client-environment.ts`
setting `viteEnvironment: 'client'`. It adds no package or product config and
retains the existing custom Vue host renderer pattern used by mobile-core
composable tests.

The test creates `ownerTestRenderer.createApp(StudentFeatureOwner, ...)`,
mounts the real SFC, and drives the mocked child screen event boundaries
`Today -> More -> Requests -> newRequest`. `getRequestOptions` returns a
deferred Promise; the test asserts `toHaveBeenCalledOnce()` both while pending
and after resolution. This is an actual Owner mount and real route/new-request
flow; only unrelated child screens and Today/Homework composables are mocked to
keep the test host dependency-free.

Mutation proof was run against the exact known line in `newRequest`:
`ensureRequestOptions()` was replaced with
`void requests.loadOptions().catch(handleRequestsError)`. The original working
file was SHA256
`655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F`
(36033 bytes); the mutant was
`EA06D40AB7890BEEA10E17E46ACB8A8B5F973489C8224A641886F6EBDD60AA45`.
The exact client-config component command exited 1 with 1 failing test / 3
passing tests: `getRequestOptions` was expected once but observed twice. The
original line was restored immediately. The first textual reverse left one
controlled lone-LF difference (SHA
`53EC94DFD7081CF57B13A895622B9B357F0A0756E1C6124F753053DBA0839057`), so the
recorded byte-level reconciliation inserted one CR at offset 20912 only; the
final Owner file is again SHA256
`655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F` and 36033
bytes.

Final verification after harness typing/lint cleanup:

| Criterion | Command | Exit | Evidence |
|---|---|---:|---|
| Actual Owner component mount | `npm exec --offline -- vitest run --config pwa-vue/vite.config.ts --environment ../.agent/v2-requests-ui/vitest-client-environment.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1` | 0 | 1 file / 4 tests passed; deferred options transport count stayed one |
| R4 focused rendered union | `npm exec --offline -- vitest run --config pwa-vue/vite.config.ts --environment ../.agent/v2-requests-ui/vitest-client-environment.ts mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/attachment-validation.test.ts mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/RequestsScreen.test.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1` | 0 | 5 files / 44 tests passed, including 7 RequestsScreen tests |
| Mobile-core typecheck after harness fix | `npm run typecheck --workspace @rct/mobile-core` | 0 | `tsc -p tsconfig.json --noEmit` |
| Mobile-core lint after harness fix | `npm run lint --workspace @rct/mobile-core` | 0 | `eslint src scripts tests --max-warnings=0` |
| Owner restoration hash | `Get-FileHash -LiteralPath frontends\\mobile-core\\src\\shared\\components\\StudentFeatureOwner.vue -Algorithm SHA256` | 0 | Required original SHA and 36033 bytes |

No browser/product runtime was run; runtime remains N/A under the frozen
contract. No R5/generated/backend path, dependency, lockfile, or product
source was changed in this completion pass. The independent full-Sol review,
integration, and final runtime remain owned by main.

## Fresh LOW MIME normalization repair — 2026-09-15 20:49 MSK

Request/reference: the independent FULL R4 Sol recheck left two LOW findings
in the assigned compact repair: `normalizeContentType` used ECMAScript
`trim`, while the attendance backend at
`C:/Users/maksd/.codex/worktrees/6a61/rutcampustrack/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:1645-1688`
uses `String.strip().toLowerCase(Locale.ROOT)`. This is a contract mismatch,
not a product redesign request.

Recorded reproduction before the repair: a valid `proof.pdf` with declared
MIME `application/pdf` plus U+00A0/U+2007/U+202F/U+FEFF was trimmed by the
client and accepted through `acceptsRequestFile`, while Java `strip` preserves
each code point and the backend rejects the request. Java whitespace around a
valid MIME (tab/U+001E/U+1680/U+3000) must continue to be accepted. The
filename path already used `stripJavaWhitespace` and was left unchanged.

Correction: `normalizeContentType` now reuses `stripJavaWhitespace` before
lowercasing. `null`/`undefined` still normalize to the empty string; lowercase
matching, exact JPEG/PNG/PDF MIME-extension concordance, and all filename
sanitization/extension behavior remain unchanged. The test adds eight real
`acceptsRequestFile` MIME-path fixtures: four server-preserved negatives and
four Java-whitespace positives.

Current source verification was run from the assigned worktree at HEAD
`b8220ac92125a8afa37598b270aa4fab7aa1f470` with Windows PowerShell, Node
`v24.14.0`, npm `11.9.0`, and the worktree's own offline dependencies. The
first in-sandbox invocation of the exact Vue-configured union exited 1 before
collection because esbuild could not read the existing config directory
(`Access is denied`); this was a harness permission result with no product
failure or source change. The exact same command under the permitted narrow
scoped harness escalation passed:

| Criterion | Command | Exit | Current evidence |
|---|---|---:|---|
| Five-file rendered Vitest union | `npm exec --offline -- vitest run --config pwa-vue/vite.config.ts --environment ../.agent/v2-requests-ui/vitest-client-environment.ts mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/attachment-validation.test.ts mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/RequestsScreen.test.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1` | 0 | 5 files / 52 tests passed in one worker, including all eight new MIME fixtures |
| Mobile-core typecheck | `npm run typecheck --workspace @rct/mobile-core` | 0 | `tsc -p tsconfig.json --noEmit` |
| Mobile-core lint | `npm run lint --workspace @rct/mobile-core` | 0 | `eslint src scripts tests --max-warnings=0` |
| Scoped diff whitespace | `git diff --check` | 0 | No whitespace errors; pre-existing line-ending warnings only |

The current product delta is exactly 10 modified files plus 4 added files:

| Product path | Git blob hash |
|---|---|
| `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` | `de027277796478aecfbb0984da377b9ad90cae17` |
| `frontends/mobile-core/src/features/requests/RequestCard.vue` | `07691522d7f1c99574c8561cd6c683c7074fed82` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts` | `506ef767ac2beef1c2980fc7b1ef5b36cadac33e` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.vue` | `ac4be7422419f28cbefc340f992c51cdf76be4be` |
| `frontends/mobile-core/src/features/requests/requests-controller.test.ts` | `22ae429ded7b18f992f61e383f6dac6be8665eed` |
| `frontends/mobile-core/src/features/requests/requests-controller.ts` | `01df8922d4ccb8ca58df1ea6067a74ff9d587ef1` |
| `frontends/mobile-core/src/features/requests/requests.pcss` | `76b9ebd6cf7050ec7f139fd992eb99cc360cb488` |
| `frontends/mobile-core/src/features/requests/types.ts` | `56f600b1c6b87e4d48686712c125304caf6838af` |
| `frontends/mobile-core/src/features/requests/attachment-validation.ts` | `5def017488bf1ef660a011882c19f547be373e64` |
| `frontends/mobile-core/src/features/requests/attachment-validation.test.ts` | `8afa541b6110b38636386a55847a4f21054cc708` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.ts` | `a57eb823083c3cfef2f7fe4eba886b6647ddcef0` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts` | `a1ba87f61cc1ac74294de30664c60e24d3cf923d` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `f1690c1fda0658ff68482d5dd6b56f0b4c1ce475` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `387035a7075c501bc22a2bb3ac7771ac945dd64b` |

Owner and harness fence hashes were rechecked in the same worktree:

| Path | SHA256 | Bytes |
|---|---|---:|
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F` | 36033 |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `D90E583F3D5C90E01E8351C211B1F89052A67A5297D841BA4CDC764010681348` | 11148 |
| `.agent/v2-requests-ui/vitest-client-environment.ts` | `CF3212D7D65917AB03161C487471EFD6A088B7463A0EF76C542D93CA65782205` | 221 |

Runtime evidence remains explicitly `N/A` in `runtime-evidence.json`: this
leaf contract forbids browser/product runtime, and main owns integration,
independent full-Sol recheck, and any runtime lease. No Terra escalation gate
was opened; no recorded defect or complexity boundary required one.
