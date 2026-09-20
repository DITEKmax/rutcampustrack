# R4 Requests UI progress

Status: IMPLEMENTED_R4_COMPONENT_PROOF_READY_FOR_FULL_SOL_RECHECK

The milestone entries below preserve historical results from their recorded
source snapshots. The current-source result for the fresh LOW repair is the
final section; it supersedes earlier test counts and manifests for acceptance.

The isolated `codex/v2-requests-ui` worktree is based on clean E
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. Pre-fix source reproductions for
the shared operation epoch, silent attachment failures/popup blocking, and
MIME/extension OR acceptance are recorded in `evidence.md`. Browser/product
runtime was not executed in this leaf; the allowed N1 source checks and their
exit codes are recorded in `checks.json`.

Source is now frozen: the bounded diff adds independent request resource
generations, exact attachment validation, owner-generation fenced popup
actions, inline card states, and focused deferred/rendered/pure tests. A Sol
popup isolation correction now acquires the actual WindowProxy without
`noopener,noreferrer`, nulls `opener` synchronously, and fails closed if
isolation cannot be applied. Post-edit source hashes are recorded in the
handoff evidence. D1 installed the locked
dependencies in this worktree with `npm ci --offline --ignore-scripts
--no-audit --no-fund` (exit 0). The allowed N1 lane then passed the four-file
non-Vue suite (27 tests), mobile-core typecheck, and mobile-core lint (all exit
0), including the bundled F1/F2/F3 regressions: one guarded route/new-request
options load, stale 401/403 rejection fences, and backend filename sanitization
before extension matching. The rendered `RequestsScreen` suite passed through the existing
`pwa-vue/vite.config.ts` Vue plugin: 1 file / 7 tests, exit 0, one worker. The
initial direct/temporary-config sandbox probes and their exit 1 remain recorded
as historical harness evidence; no permanent config was added. The post-Sol
action regression is 1 file / 11 tests, and the post-correction typecheck and
lint both pass. Runtime and independent Sol recheck remain open.
The worktree diff is intentionally uncommitted: Git could not create the
worktree index lock (`.git/worktrees/v2-requests-ui/index.lock`, permission
denied), and no permission bypass was attempted.

Fresh completion recheck on 2026-09-15 made no product-source edits. It
reran the focused four-file suite (27 tests), mobile-core typecheck, and
mobile-core lint with exit code 0 in the isolated offline dependency cache.
The stale requests-controller.test.ts post-fix blob entry in evidence.md
was reconciled to the current
22ae429ded7b18f992f61e383f6dac6be8665eed. Runtime remains N/A; browser flow,
integration, and independent Sol recheck remain open for main.

Fresh F3 correction on 2026-09-15 changed only the two assigned
attachment-validation files. The validator now mirrors Java
Character.isWhitespace/String.strip semantics across the filename,
extension-normalization, and MIME/extension path. The four-file focused suite
now passes 35 tests, with typecheck and lint also exit 0. Evidence records the
NBSP-class reproduction, Java-whitespace positives, historical 13-file
manifest at that checkpoint, and the exact correction scope; the worktree was
then ready for a separately allocated full-Sol recheck.

Fresh F3 truncation correction on 2026-09-15 keeps the same two-file product
scope. Filename-derived extensions now use only the server-equivalent
substring(dot).toLowerCase result after UTF-16 truncation, while metadata
extension normalization remains separate. The full focused suite now passes
36 tests; typecheck and lint remain exit 0. The concrete 256-code-unit
counterexample and valid neighbor are recorded in evidence.md, and no other
product file changed.

Fresh R4 actual component regression proof on 2026-09-15 completed the
previously missing Owner mount. A bounded
`.agent/v2-requests-ui/vitest-client-environment.ts` selects Vite's client
environment while the existing `pwa-vue/vite.config.ts` supplies the Vue SFC
plugin; no project config or dependency changed. The custom host renderer now
mounts the real `StudentFeatureOwner` and drives the actual Today -> More ->
Requests -> `newRequest` event path with a deferred options Promise. The
one-line known mutation to direct `requests.loadOptions()` made the test fail
with 2 transports (expected failure, exit 1), proving the assertion catches
the regression. The original Owner source was restored and verified byte-for-
byte at SHA256
`655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F` (36033
bytes). Final client-config union verification passed 5 files / 44 tests,
including 7 RequestsScreen tests and 4 Owner tests; typecheck and lint both
pass (exit 0). Exact commands, transient harness corrections, mutation
hashes, and the controlled CRLF reconciliation are recorded in evidence.md
and checks.json. Browser/product runtime remains N/A; main owns integration,
independent full-Sol recheck, and runtime.

Fresh LOW MIME normalization repair on 2026-09-15 completed the two assigned
findings from the independent FULL R4 Sol review. `normalizeContentType` now
reuses the existing Java-equivalent `stripJavaWhitespace` helper before
lowercasing, matching `StudentRequestService.java:1645-1688`; the filename
pipeline and null/undefined behavior remain unchanged. The focused test adds
four `acceptsRequestFile` negatives for U+00A0/U+2007/U+202F/U+FEFF in the
declared MIME and four positives for tab/U+001E/U+1680/U+3000.

Current checks at HEAD `b8220ac92125a8afa37598b270aa4fab7aa1f470` passed through
the existing Vue config: 5 files / 52 tests, typecheck, lint, and
`git diff --check` all exit 0. The final product inventory is exactly 10
modified plus 4 added files, with the Owner.test and client environment fence
hashes recorded in `evidence.md` and `diff.md`. The first sandbox union attempt
was a historical harness access denial (exit 1 before collection); the exact
command passed under narrow scoped escalation. Runtime, integration, and fresh
independent Sol recheck remain open for main.
