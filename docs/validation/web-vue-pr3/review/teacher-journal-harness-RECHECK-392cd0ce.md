# Teacher journal — narrow F1/F2 source recheck

Verdict: **PASS — F1 and F2 closed in source; no blocking findings in the correction or its influence.** This is not runtime/visual acceptance or browser execution authorization. Root's separate GO and the Profile-held/TMA coordination gates remain in force.

Reviewed exact runner SHA256: `392cd0ce381ea659026c8e69cf21a5f374eac3259686577e1d8f468943c37755` (`/workspace/rct-pr3-runtime/visual-readiness-20261024/teacher-journal-a851.cjs`). Correction patch SHA256: `dffb4d2f1a365484245d8a4defff1f1d48c1d032a13bf714b4cda12b66c6f0c3` (`teacher-journal-d458-to-bounded.patch`). Preserved predecessor independently hashes to the previously reviewed `d4589d5f746823cb4fca034a78a7197e0ad27aad66a0e613a20b37e3a0c1c20f`. Public source diff and current source were read; the diff corresponds to the supplied correction patch. PREP metadata names the corrected SHA and retains pending Root GO/personal PNG inspection.

## F1 — closed

`captureOnly()` at runner lines54–65 races each capture operation against the remaining absolute65s deadline and a registered STOP rejection. SIGINT/SIGTERM notify those capture waiters at lines66–68. The helper removes its timer/listener in `finally` and throws the failure into `shot()`'s failed-attempt path and the existing outer native-cleanup `finally`.

All awaited operations inside `shot()` at lines201–211 now use that helper, including font readiness, animation frame, metadata evaluation, viewport/media, focus and screenshot. Screenshot also receives the remaining Playwright timeout. Thus unresolved fonts/rAF no longer prevent the awaiting runner from reaching cleanup after budget/STOP. A still-pending underlying read/evaluation is not treated as completed capture; the helper adds no Auth/API continuation, fallback or replay. Cleanup remains outside the capture deadline and outside `captureOnly()`, as required for known or unknown own-session resolution.

This closes the specific unbounded font/rAF capture await finding. No claim of observed timing, injected stall or executed signal handling is made.

## F2 — closed

The held request now has an absolute8s deadline and a retained watchdog-expiry flag at lines135–140. MB-TJ-02 uses the earlier of that deadline and the capture deadline for its waits. `requireHeldLoading()` at line69 requires the one intercepted request to remain HELD and unexpired.

The loading capture checks that condition on entry, immediately before the screenshot and after it. Visible loading is checked before and after the PNG at lines209–211, with an additional held check after the asynchronous visibility read. The raw screenshot can only enter `samples` after those checks and the final checkpoint. Failed attempts are retained as `FAILED_RAW_NOT_QUALIFIED`, with safe failure metadata; a possibly existing raw file is not promoted to a qualified sample.

The explicit release after MB-TJ-02 again requires a still-held request. Watchdog expiry blocks the controlled503 success path, `captureComplete` and `completed` at lines230,237,275. Exactly one synthetic503 and the unchanged exact-URL native actual200 retry are retained. No extra screenshots, success mocks or retry operations were added.

## Influence and reused review

The source admission, private handle/scope format, Root terminal-proof control, native logout route guard and cookie-only cleanup bodies are unchanged in the source diff. The prior full review's accepted ownership/privacy/Auth boundaries are reused. New receipt fields contain capture attempt metadata and controlled hold timestamps/booleans, with no bearer, cookie, credential or session ID exposure. The correction does not add mutation permissions or change unknown cleanup into automatic closure.

Only public source, patch, PREP and hashes were read. No runner/module/syntax/test execution, browser, API/Auth, private-file read, product fix or existing Profile/TMA runtime interaction occurred.

Created: `/workspace/rct-pr3-review/teacher-journal-visual/RECHECK-392cd0ce.md`. Modified/deleted: none. Next decision belongs to Root after its explicit runtime coordination gates; actual six PNG still require personal inspection.
