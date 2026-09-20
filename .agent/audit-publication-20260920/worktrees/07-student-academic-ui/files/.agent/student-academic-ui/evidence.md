# FE13 evidence

## Scope

The implementation is limited to the 23 assigned application files: eight Attendance feature files, eight Statistics feature files and seven standalone harness files. Agent evidence is under .agent/student-academic-ui. Shared API/generated types, shell, lockfiles, backend, PWA/TMA entrypoints, Figma and Requests form ownership are untouched.

## Criteria and evidence

| Criterion | Evidence | Result |
| --- | --- | --- |
| Controlled typed Attendance and Statistics compositions | Typed ReadState, props and emits in both view-models/screens; harness imports real components | PASS by typecheck/build |
| Six read states | Harness query supports loading, ready, empty, offline, forbidden and error; offline branches provide no ready data | PASS by source + build |
| Attendance days/subjects/graph/request gate | AttendanceScreen, LessonRow, SubjectList and Graph; request form is named slot with OPEN fallback | PASS by source + tests |
| Server projections and opaque IDs | findAttendanceLesson, graphForRange, seriesForRange, g:123 test id | PASS by tests |
| Statistics MetricSet4 and neutral states | Four metrics plus held/planned; null displays as em dash; DATA/NO_DATA/FUTURE remain distinct | PASS by source + tests |
| Type ordering/controlled selection | LECTURE/PRACTICE/LAB order; host updates selectedTypes without client aggregation | PASS by source + tests |
| Accessibility and responsive geometry | Full status/history labels, focus-visible mixins, focus transition watcher, rem tokens, long neutral badge sizing, separate rounded controls | PASS by source/typecheck; browser supplement required |
| Reachable source fixtures | 13 fixture URLs returned HTTP 200 on ports 18210/18211 | PASS HTTP smoke; browser paint supplement required |

## Runtime

Vite dev servers started only on loopback ports 18210 and 18211. The 13 fixture URLs plus main.ts returned HTTP 200 (probe exit 0), then task-owned PIDs were stopped. The production harness build emitted runtime/harness-dist with exit 0.

The leaf CUA surface exposed no browser provider. listBrowsers returned an empty list; iab, chrome and URL based browser selection all failed as unavailable. Therefore this lane makes no screenshot, DOM-paint or keyboard PASS claim. Root's independent CUA tab must attach the 390x844 screenshots and focus evidence to this packet before review.

## Diff and limitations

The final application diff is bounded to the 23 assigned paths. The request slot remains OPEN and no Requests form is implemented. No live backend, auth, PWA/TMA shell, network transport, cache or external state was exercised. One concurrent Vite optimize-cache EPERM was reproduced in the dev log; it was linked to shared local cache contention, and no unrelated code was changed.

