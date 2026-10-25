# Teacher journal visual harness — independent readonly review

Verdict: **REQUEST_CHANGES before Root browser GO**. Two bounded-harness findings below. Product implementation, visual appearance and actual runtime behavior are not accepted or rejected by this source review.

Reviewed harness: `/workspace/rct-pr3-runtime/visual-readiness-20261024/teacher-journal-a851.cjs`, SHA256 `d4589d5f746823cb4fca034a78a7197e0ad27aad66a0e613a20b37e3a0c1c20f`. Also read `teacher-journal-a851-prepared.json`, `NEXT-readonly-state-packet-a851.md`, referenced public root-proof template and relevant public source. Root HEAD observed by Git read: `cab3eed183d9453a6bfcc17ca12149d1894f39ba`; product basis declared `a851d5cf780c965513e79c2334a924eaaac72cdc`.

## Findings

1. **P2 — Font readiness can bypass the declared capture budget and delay own-session cleanup.** Location: `teacher-journal-a851.cjs:179`, with budget/checkpoint at `:35,50,178`, signal handlers at `:51–52` and cleanup admission in `:244–250`.

   `shot()` awaits `page.evaluate(() => document.fonts.ready)` without a timeout or stop-aware race. `page.setDefaultTimeout(7000)` does not bound `page.evaluate`; the installed Playwright client's `Page.evaluate` / `Frame.evaluate` source forwards evaluation without that timeout. A still-loading font can therefore keep the runner inside this await beyond the declared 65-second budget. The deadline is checked only before entering `shot()`. SIGINT/SIGTERM set a flag and release the targeted journal GET, but do not settle this font await. Consequently the runner does not reach `finally`, its native logout, or the sealed UNKNOWN_HOLD protocol while this await remains pending. No outer timeout is allowed by the packet.

   Required correction: bound capture-only asynchronous waits by the remaining capture budget and stop request, so expiration throws a public safe failure into the existing cleanup path. Preserve the browser for unknown cleanup; do not replace the missing bound with an outer process kill, fallback Auth or a repeated business operation. Apply the same principle to the subsequent animation-frame promise if it can remain suspended.

   This is a source-derived failure path; no hanging resource, browser or API was exercised during review.

2. **P2 — The loading PNG is not causally guaranteed to be captured while its GET is held.** Location: `teacher-journal-a851.cjs:120` and `:177–187,201–203`.

   The intercepted journal GET automatically proceeds to synthetic503 after eight seconds. The runner first observes loading, then calls `shot()`, which changes viewport/media, waits for theme/fonts/frame and captures a PNG. There is no check that `faultMode` is still `HELD` at capture completion. If these operations take more than the remainder of the hold interval, MB-TJ-02 may contain the error state while being labeled `controlled-held-get-loading`; MB-TJ-03 can then capture the same error and the run can still set `captureComplete` / `completed`. The later mandatory personal inspection may reject that image, but the harness's loading-state oracle itself does not establish the packet's required held-GET cause.

   Required correction: make watchdog expiry fail the loading capture, and/or explicitly verify the same request remains held through the screenshot boundary. A failed loading capture must remain a qualified failure, not a loading receipt. Keep exactly one synthetic503 and the native unchanged-query actual200 retry; no extra frames or success mocks are needed.

## Reviewed boundaries without blocking findings

- One-shot reservation precedes browser/Auth. Exact invocation HEAD/self-SHA plus relevant tracked-tree checks gate execution and captures. No second login or post-login refresh is admitted by the route guard.
- Dedicated empty browser context, actor2/SID observations, private0600 handles and login-attempt window support exact ownership and unresolved-login qualification. Public receipts store sanitized codes/statuses and hashed unknown paths; credentials, bearers and cookie values remain in RAM. No private artifact was opened during review.
- The normal Stats-to-journal entry and initial empty type selection are supported by the current `TeacherStatsScreen`, `TeacherFeatureOwner` and `TeacherJournalScreen` source. The chosen controls change read context; domain/preference/export-generation mutations are blocked. No API/Auth success response is mocked. Retry matches the exact held URL and requires actual200 plus settled UI.
- Native PWA logout in `frontends/pwa-vue/src/auth.ts:708` is cookie-only. The runner correctly does not require an Authorization header on that POST. It qualifies the captured bearer through canonical session200, correlates cookie subject/SID in RAM, compares the native request cookie, admits at most one logout and requires204 followed by401 for that same captured bearer. No fallback or Auth replay is present.
- Unknown cleanup seals browser API continuation and retains browser/RAM. The only stdin control validates a fresh current-run private0600 Root terminal proof bound to run/revision/harness/current scope; it closes the already resolved browser and retains the original failure result. The referenced template is false/template-only.
- Exactly six planned frames remain bounded in count; `personallyInspected:false` honestly defers visual/focus/overflow acceptance to actual PNG inspection. No CSS/wording/structural test requests are part of this review.

## Execution and inventory

Only public-file/Git reads and creation of this review report were performed. No runner/module import, syntax/test execution, browser, runtime/tool API, Auth, private-file read, rights/business write or product fix. Existing Profile held case92079 was untouched.

Created: `/workspace/rct-pr3-review/teacher-journal-visual/REVIEW-d4589d5f.md`. Modified/deleted: none.

Next: author corrects the two harness findings, freezes a new SHA and updates its PREP metadata; independent recheck of the changed wait/hold paths precedes Root GO. This report provides no execution authorization.
