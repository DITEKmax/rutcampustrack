# STUDENT narrow recheck — 757b35bdd

Verdict: **PASS**. Both P2 findings in `REVIEW-324916123.md` are closed. No new confirmed P1/P2 found in the nine-file correction delta, including its changed resource/parent lifecycle and Ranking behavior.

Reviewed frozen `757b35bddf54c0e0361393ccd81d5fc0d495cc8f` only. Existing homework and the full STUDENT/backend baseline were not reopened.

## Causal closure

- **Ordinary reading/live updates:** StudentRegion no longer turns page-wide pointer/focus presence into a live blocker. StudentScope blocks only actual dirty/command state. Same-context Today, attendance, schedule and statistics reads retain their current projection while refreshing; existing keyed cells remain mounted. The attendance detail selection rebinds by stable lesson id rather than closing on every accepted list replacement.
- **Attendance type selection:** the available-type watcher skips transient null data and intersects only when an accepted attendance projection exists. Retaining the same-context read avoids the original temporary empty projection. Existing selected types survive refresh/error-retry; an intentional empty selection remains empty.
- **Context boundary for retained data:** each resource synchronously clears and advances its read revision when the captured user/group/semester/session/version/read-only context changes. The old retained projection cannot cross that boundary or be republished by a late response. Authority failures still clear retained data where applicable.

## Ranking and affected parent lifecycle

The previous withdrawal relied on the statistics parent clearing its resource and unmounting Ranking. Same-context statistics now deliberately retains that parent content, so this premise was rechecked rather than assumed.

Ranking now permits replacement reads during a pending read and increments its query revision, so a refresh is not silently dropped. A synchronous context-key watcher clears the page and fences old results; the resource owner also clears the parent projection on an actual context/semester transition. An append/prepend must match the requested page, existing total and non-overlapping ids; replacement results supersede outstanding append work. Earlier-page and own-position controls use the existing server-selected own page without inventing positions.

Independent in-memory probes ran frozen actual modules/scripts with existing TypeScript/Vue and dummy fetch/adapter responses. Successful checks:

- Live blocker is false for ordinary reading and true for an actual dirty state.
- Lecture selection survives identical successful attendance refresh; intentionally empty selection remains empty.
- Same-context data remains available during a held refresh; changing context immediately clears it and a late old read cannot restore it.
- A held ranking read for semester2 followed by semester3 refresh accepts only the semester3 result, even if the old result completes later.
- A replacement refresh while next-page append is held accepts the replacement and ignores the stale append.

These are bounded source/causal checks, not another browser suite.

## Evidence and limits

Read `/tmp/student-web-refresh-actual/summary.json`, whose revision matches the frozen candidate. It records eleven targeted checks: actual REST under ordinary hover/focus, selected Lecture and empty-filter preservation, stable connected focused mark/detail and Escape focus restoration, dirty request draft deferral/text/focus preservation, pending read after closing the draft, zero student mutations and zero runtime errors. The listed actual reads are HTTP200; `posts` is zero.

Inspected the four new SFC regressions for meaningful visible state and interaction assertions, including failure/retry and earlier/later ranking pages. Author reports those tests plus build/lint/types passing; they were not rerun here. `git show --check 757b35bdd` passed.

No browser, build, Gradle, Docker, install, CI, server mutation, product source write or child agent was used. Earlier first/request/read evidence and pagination fixtures were not repeated. Positive current-window check-in/late acceptance and unrelated outstanding gates are not promoted to PASS by this narrow report. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
