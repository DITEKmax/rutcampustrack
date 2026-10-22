# Q02 independent whole checking-package review

Verdict: **REQUEST_CHANGES_SOURCE — one blocking finding.** No client PASS or product defect is asserted. After the bounded correction below, recheck only the changed matcher, its source-line negative control and their effects; another whole-package review is unnecessary.

Requested/selected model: gpt-6.1-sol/high/forknone; actual model/effort UNKNOWN. Scope: fresh read-only whole Q02 review; Root is the shared workflow writer, existing author is the two-test-path writer. Baseline 3908fad9624f97aec8891fc79338ceb6b0616bf3; published producer 89746d1dc3d3ac7c23470c3998f9d85ed268859f; Root local ca29f5bc8e182e002203ca03c8b131275c90373f. Author worktree clean at 53f7e5ec3fcd6a5d6f19a61695fabc616321372e. No source or shared task-document edits, tests, native browser/server/Docker/network operations, artifact downloads or subagents. Only this report was created.

## Frozen identities

| Path | SHA256 |
| --- | --- |
| Root docs/validation/cloud-20261011/TASK-CONTRACT.md | 62afda54233d3f27e894d0108c6d12a99a217f11aa686a33550739a93d6c75ed |
| Root .github/workflows/ci.yml | 6113311149c86b5e262bf94da34de4f78b3e79ba9ba5ec947840ef66c83a6e61 |
| Author tests/e2e/specs/mobile-real-server.spec.ts | a8cbd2962d316745f304bf083957d0242f64c9a4371fcf7ca47f5280a97746ce |
| Author tests/e2e/artifact-controls/privacy.control.spec.ts | e2d964456f0316adf8782e3d6c8b3364e4859cb9eb9e3194c35b73a6d5699e71 |

Identities were checked at entry and after source review. Existing foreign/Root WIP was preserved.

## Blocking finding

1. **[P1] Match the bare current-homework URL before waiting for its failed request.** `/workspace/rct-worktrees/first3-spec-frame-20261013/tests/e2e/specs/mobile-real-server.spec.ts:269` keeps the glob `**/api/v1/student/homework?**`. That pattern requires a literal question mark. The current PWA feed sends the bare path `/api/v1/student/homework`: `StudentFeatureOwner.vue:326` supplies no `initialRange`; `use-homework.ts:259–276` starts with `normalizeHomeworkRange(undefined) === null` and calls `getHomework(undefined, undefined)`; `student-client.ts:119–124` omits the entire query suffix when neither date is supplied. The normal PWA version fetcher adds a header only, preserving the input URL (`frontends/pwa-vue/src/pwa-version.ts:27–35`). Pinned Playwright's `lib/utils/isomorphic/urlMatch.js:33–34,84` escapes `?` as a literal, so it cannot match the bare request. Consequently this callback does not induce the intended outage, and the newly added `Promise.all` at line 280 waits for a matching `requestfailed` that this route cannot cause; a healthy server produces a timeout rather than manual error/retry acceptance. This is a deterministic source mismatch, not a claim about the exact historical 897 runtime error.

   Minimal correction: use one stable URL predicate whose `pathname` equals `/api/v1/student/homework` for both `page.route` and the exact-callback `page.unroute`, retaining the GET-only guard, requestfailed witness, sustained outage through error UI, finally cleanup, manual click, real response 200 and marker assertion. This admits bare/current and dated feed reads without matching completion subresources. A bounded existing browser-free matcher check for bare, dated and completion URLs is sufficient for this correction; keep actual current normal-CI acceptance separate. Update the existing out-of-range source-line literal only if the final effective line count changes.

## Reviewed behavior and boundaries

- LIMITED selector correction matches the actual `input type="search" aria-label="Найти пользователя"` (`AdminUsersScreen.vue:1499–1503`). Existing create-button absence and real POST 403 remain; no new grant or product behavior is introduced. This finite case does not prove full registry data completeness or post-denial database absence, which remain separately qualified in the matrix.
- Except for the matcher finding, the retry design preserves the intended causal sequence: install before login/eager query; real GET failure; allow the owner's retry=1 to exhaust; observe manual retry UI while transport remains unavailable; remove only this callback in finally; register a response wait before clicking; assert 200 and actual fixture marker. No successful API response is fabricated, and no production retry policy is changed.
- Effective source line count is 449, making 450 the first invalid line under the unchanged sanitizer's split-and-pop rule. The only privacy-control change moves that negative bound. Canonical reporter, privacy guard, schema, error sanitizer, captures-off policy, source assertions and public case titles remain unchanged. Public declaration lines are 14/61/160/230/257/298/316/405; later evidence must join those new source locations to the same titles rather than attribute old 278/296/385 receipts to the new file.
- The actual production-worker case remains intact: real warm homework, ready/controller, offline reload, cached marker and disabled completion, online revoked-session login, foreign-user isolation, explicit logout and offline absence. The current 897 timeout does not prove a TLS failure or any of these later assertions. Schedule offline, absolute expiry, worker update/install/provider and unsupported-platform families remain separate.
- New Root workflow trust scope is GitHub-hosted CI only. It imports the exact generated/served bounded public `server.crt`, requests NSS SSL peer trust `P,,` rather than CA trust, exports it and compares DER SHA256 in RAM. No key is read; no HOME override, local NSS mutation, blanket certificate-ignore flag or production TLS change is added. Existing page `ignoreHTTPSErrors` remains unchanged; the separately reviewed Q15 API context remains strict. The current source-reference receipt documents M146+ default NSS path and legacy fallback; I inspected that receipt, not a fresh remote official-source fetch. Whether pinned Chromium147 consumes this trust and the worker activates is still for genuine CI to establish. `certutil -A -t P,,` requests the flags; the PEM export verifies identity, not an independent readback of trust settings.
- Trust is provisioned before browsers execute. Cleanup targets only the run/attempt nickname and chosen database. Hosted-runner lifetime bounds the change; there is no newly claimed VM-global/local trust permission. Existing CI trigger/job/gate steps and 30-minute E2E job limit remain. The preapproved Q15 body was checked only for interactions: it remains after remaining5 success, before trust removal/container cleanup, using the same server certificate and strict API verification. No Q15 re-review was opened.

## Optional, non-blocking observation

Root workflow lines 425–431 publish cleanup variables only after import/export/DER readback. A failure after successful import but before publication skips nickname cleanup. The runner is explicitly disposable and later tests skip on setup failure, so this does not establish a persistent host or product-security defect and is not an additional blocker. If touching this block, predeclare ownership/cleanup bookkeeping before the import (with an absent-nickname guard), or remove the just-imported nickname on verification failure; keep deletion exact. Independent trust-flag readback could also make the setup claim more explicit, but genuine worker/strict-TLS runtime proof remains necessary.

## Sources and verification limits

Read the full governing TASK-CONTRACT, CURRENT, decisions, questions, resources, root/tests/frontends AGENTS, RULES, workflow, relevant BOUNDARY-09/47/48/49 matrix entries, accepted D002/LIMITED decisions and current Vue API/query/UI sources. Read named Q02 localchecks, disposable-peer-trust, SW diagnosis, current-897-FIRST3/next5 receipts and the completed Q15 independent review/addressed recheck. Local reported five Node phases/42.59s, formatter idempotency/TSC/privacy controls and cleanup are accepted only for their recorded scope; I did not repeat them. Those checks do not exercise the URL interception mismatch or actual service worker.

Existing current-897 FIRST3 finite PASS and next5 two PASS/three failed outcomes retain their producer identity. No readiness promotion, no no-effect/complete-family claim, no B013/B014 equivalent, and no Angular/new web/Figma/main/Ready/deploy/private-secret access. Actual current normal CI is required after correction; offline cause stays UNKNOWN unless runtime evidence establishes it.

## Addressed recheck — 2026-10-14

Verdict: **APPROVE_SOURCE_LOCAL — the one P1 matcher finding is addressed; zero remaining blocking findings in the changed place and its effects.** This is the same reviewer's bounded recheck, preserving the original finding above. No new whole-package review or runtime acceptance was performed.

Author worktree is clean at `ade968280db2821ac5755dfd3a7333894df3e1bd`, directly atop reviewed `53f7e5ec3fcd6a5d6f19a61695fabc616321372e`. The exact correction diff changes only line 269 to `const homeworkRoute = (url: URL) => url.pathname === '/api/v1/student/homework';`. Bare and dated feed URLs have that exact pathname, while completion subresources have a longer pathname and do not match. Pinned Playwright `urlMatch.js:150–158` calls function matchers with a parsed URL, so the callback receives the declared URL shape. Its `urlMatchesEqual` compares functions by identity, and `page.js:435–444` additionally compares the route handler: lines 272 and 284 reuse the identical `homeworkRoute` and `abortHomework` objects, preserving exact-own-handler cleanup.

The GET-only abort guard, install-before-login ordering, requestfailed witness, sustained outage through manual error UI, finally unroute, wait-before-manual-click, response 200 and marker assertions are byte-unchanged. Effective line count remains 449, negative privacy bound 450 remains valid, privacy-control bytes are unchanged, and public cases remain 14/61/160/230/257/298/316/405. There is no additional route, product, reporter, schema or privacy behavior change.

Current frozen identities independently checked:

| Path | SHA256 |
| --- | --- |
| Author tests/e2e/specs/mobile-real-server.spec.ts | 1baa244f10df755235b288d2f570569231aff7a946f6dd55beae3f708e1d28e6 |
| Unchanged author tests/e2e/artifact-controls/privacy.control.spec.ts | e2d964456f0316adf8782e3d6c8b3364e4859cb9eb9e3194c35b73a6d5699e71 |
| Unchanged Root .github/workflows/ci.yml | 6113311149c86b5e262bf94da34de4f78b3e79ba9ba5ec947840ef66c83a6e61 |
| Unchanged Root TASK-CONTRACT.md | 62afda54233d3f27e894d0108c6d12a99a217f11aa686a33550739a93d6c75ed |

Reread current governing state/decisions/questions/resources and the parent receipt `docs/validation/cloud-20261011/receipts/Q02-search-retry-localchecks.safe.json`, specifically `addressed_review_finding`. Its four cached Node formatter/idempotency/check/TSC phases report PASS in 4.41 seconds, all four births reaped/absent and temporary link/files absent. These results are accepted only for their recorded source-check scope and were not repeated. Reusing the prior privacy PASS is coherent because the control, guard, reporter and effective source bounds are unchanged. Source status/diff/hash reads and diff whitespace validation found no foreign mutation. Only this report was appended.

The earlier optional hosted-runner cleanup observation remains non-blocking and unchanged. Root's workflow remains the previously whole-reviewed frozen version; the Q15 restore review was not reopened. Actual genuine after-correction client/SW/restore acceptance is **NOT_RUN**; historical 897 SW cause remains **UNKNOWN**. Proceed with the coherent designated-branch integration and existing normal CI; no broader review/test campaign is required by this correction. B013/B014 remain frozen and untouched.
