# Remaining STUDENT source review — 324916123

Verdict: **two confirmed P2 findings**. No confirmed P1 found in the bounded remaining STUDENT packet.

Reviewed frozen `324916123afca07d4ac6a16d970b89029e83643d`, including the `78f15e44d → 479382b05 → 1a4679384 → d12be8138 → a4414ce6c → 324916123` source chain and all six student routes. Scope: Today/schedule/check-in, attendance/mark detail, statistics/subject/ranking, request submission/list/detail/cancel/attachments, and PWA banner. The two already accepted homework actions and unrelated owners' source are excluded. Existing backend/domain contracts were read only where necessary for their new consumers, not reopened as full backend reviews.

## Findings

### P2 — Ordinary pointer or keyboard reading blocks STUDENT live updates indefinitely

Location: `frontends/web-vue/src/features/student/student-state.ts:34–37,59–60`, wired by `StudentRegion.vue:10–17`.

The common scope blocks live refresh whenever the pointer or focus is anywhere inside the entire page. This includes passive reading and focusing a read-only mark/detail/control; it is independent of an unsaved draft or active command. The shared live bus keeps one pending invalidation and cannot flush it until this condition becomes false.

Concrete reproduction: leave the mouse over the Today/request/attendance content, or keep keyboard focus on one of its controls, then receive an external attendance/request decision or schedule invalidation. The updated server state remains queued while the user continues reading in that page. Request decisions and check-in/lesson eligibility can remain stale until the user moves the pointer and focus outside the region. The clock only updates selected local display/cooldown values; it does not replace the authoritative projection refresh.

A dependency-light probe of the actual frozen StudentScope captured `ordinaryPointer=true, dirty=false, liveBlocked=true`. The page-wide event wiring and existing bus's blocked/pending behavior establish the causal path. This is the new STUDENT scope; the separately accepted homework ordinary-hover fix is not reopened.

Limit suppression to actual drafts/commands or other concrete unsafe replacement windows. Preserve keyboard focus and stable read-only content without treating ordinary pointer/focus presence as a reason to defer every authoritative update.

### P2 — Attendance refresh erases the selected subject lesson types

Location: `frontends/web-vue/src/features/student/StudentAttendancePage.vue:56,102–108`; relevant resource behavior: `student-state.ts` clears data at the start of a non-retaining read.

The selected-type watcher intersects `types` with `availableTypes` even while the attendance resource is temporarily empty for a refresh. Once the initial selection has been restored, `restoredTypes` remains true. Clearing attendance therefore filters the selected types to an empty array; the successful response cannot restore them, because the watcher now only filters the empty selection. `loadSubject()` then clears the subject metrics and the subject attendance table becomes empty despite a successful read and unchanged selection in the URL.

Concrete reproduction: open subject4 with LECTURE selected, then receive a live/focus refresh or explicitly retry attendance. A successful identical response leaves subject4 selected but no lesson types selected, hiding its rows and metrics.

An independent in-memory probe used the actual frozen page script and resource implementation, with an identical successful attendance response on both reads:

```text
afterInitial={"subject":"4","types":["LECTURE"]}
afterRefresh={"subject":"4","types":[]}
```

Keep the user's type selection through transient resource clearing; reconcile it only against an accepted authoritative response. Preserve the distinction between a deliberately empty selection and a temporary lack of available data, including explicit retry after an error.

## Other inspected boundaries

- Role routes require STUDENT. Each mounted scope captures transport identity; resource revisions/current-lifetime checks fence asynchronous reads, and new decode/attachment continuations assert current authority. No confirmed current-consumer account/role crossing was found.
- Check-in uses the existing recovery owner/key and command payload, checks server capability/read-only/eligibility, and distinguishes uncertain outcomes from definite rejection. The positive current-window action is not claimed from source inspection.
- Requests reuse the domain controller's current context, server options, validation, frozen command fingerprint and original idempotency key. An ambiguous form remains locked for explicit same-command reconciliation. Cancellation performs authoritative readback rather than inventing an ACK. Bucket generations protect list/hydration work, and pagination is server-owned.
- Attachment reads use authenticated own-request endpoints. Preview ownership covers request/attachment/authority/expiry; bytes are restricted to supported PNG/JPEG/PDF signatures and MIME before creating preview URLs. Server content is rendered as images or PDF canvas, not as arbitrary HTML. Object URLs are released on disposal/expiry. Foreign attachment/request denial does not publish a decoded foreign body through these wrappers.
- Attendance labels reflect actual status values without inferring unavailable auto/manual provenance. Metrics/ranking are drawn from server aggregates; no fabricated counts were introduced. URL selection handling and period initialization corrections were inspected. PWA dismissal is a bounded preference, not a credential or authority source.

## Evidence and limits

Read the three requested nonprivate summaries: `/tmp/student-web-first-flow/summary.json`, `/tmp/student-web-request-flow/summary.json`, `/tmp/student-web-read-flows/summary.json`. They preserve positive own user15/group4/lesson64 reads, actual TOO_EARLY denial, the native PDF upload/download hash, lost request ACK replay with the same key, lost cancellation ACK followed by GET, foreign403, keyboard/read/filter/error-retry behavior and empty runtime error arrays. These positive records do not cover the two refresh findings above.

Current-window positive check-in/late actions were not run by this reviewer and remain pending the canonical window described by root. The ongoing 10→11 pagination fixture gate is not claimed as complete. No screenshot/Figma acceptance, browser suite, build, Gradle, Docker, install, CI, server mutation, product source write, commit or child agent was used. `git diff --check 479382b05^..324916123 -- frontends/web-vue/src/features/student` passed. Lightweight causal probes used frozen modules/scripts and dummy data only.

An isolated ranking-refresh observation was not retained as a finding: the actual parent clears its statistics resource and unmounts that child during refresh, so the isolated probe did not establish a current-consumer defect.

Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
