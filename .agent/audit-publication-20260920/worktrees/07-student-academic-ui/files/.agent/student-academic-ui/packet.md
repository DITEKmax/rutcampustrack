# FE13 compact contract — Attendance8 + Statistics5

Date: 2026-09-08
Status: IMPLEMENTATION / REVIEW_READY_PENDING
Risk: S2 presentation; overall role S3
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Branch/worktree: `codex/student-academic-ui` / `.agent/worktrees/student-academic-ui`
Assigned writer: fresh FE13 implementation leaf (`gpt-5.6-luna`, `max`)

## 1. Goal

Реализовать controlled Vue-композиции Attendance8 и Statistics5 в mobile-core и
standalone real-component browser harness. Экран получает уже рассчитанные
server projections через typed props, отрисовывает состояния и выдаёт typed
events для host. Fetch, cache, transport, navigation и владельцы Requests/API
остаются снаружи.

## 2. Context / evidence

- Owner packet от root разрешает точные пути, baseline и порты harness 18210/18211.
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/attendance-source-note.md`
  подтверждает authoritative schedule/request boundary, day/week graph и
  отсутствие client-side eligibility inference.
- `.../statistics-root-decision.md` и `.../statistics-decision-result.md`
  подтверждают MetricSet4, H0=`null`, 0..100 percentages, server series,
  future neutral state, opaque subject/type grouping и no peer payload.
- `docs/design/COMPONENT_REGISTRY.md:1324-1357` фиксирует
  `student/MobileAttendance`, `attendance/MobileMetricLens` и inline-request как
  state экрана с единственным Requests form owner.
- `docs/design/figma-spec-mobile.md:356-389` фиксирует mobile shell boundary,
  Attendance states, 10px metadata icons, 44px targets, visible `+ / н / у`,
  full-width future history and no clipped overflow.
- Source frames are the 13 files listed in `source-map.md`; they are read-only
  originals under `.agent/student-role-02/design-context/`.
- The root checkout was dirty with unrelated BE and evidence paths; this
  worktree was created from immutable baseline and starts clean.

## 3. Relevant scope

Only these product paths may be written:

- `frontends/mobile-core/src/features/attendance/` — eight listed Attendance files.
- `frontends/mobile-core/src/features/statistics/` — eight listed Statistics files.
- `frontends/mobile-core/harness/attendance-statistics/` — six listed harness files.
- `.agent/student-academic-ui/**` — packet, source map, evidence, checks,
  review-ready summary and manifests.

No shared API/generated types, index exports, shell, lockfile, config, docs,
backend or PWA/TMA product files are in writer scope.

## 4. Required behavior

- Public screen props are typed and controlled. `ReadState` supports exactly
  `loading`, `ready(data)`, `empty`, `error(code,message,retryable)`,
  `forbidden(reason)` and `offline`; offline renders no previous data.
- Attendance renders server-provided dates, selected date, lessons with opaque
  stable IDs, subject/type/schedule/status, days/subjects/graph projections,
  day/week graph toggles, subject disclosure and Back/retry events.
- Request options are rendered only from the supplied authoritative lesson
  projection. Request-form is a named scoped slot carrying selected lesson,
  server option, `onClose`, and `onCompleted`; absent slot stays an explicit
  OPEN integration gate and does not implement reason/comment/files/uploads/submit.
- Statistics overview renders own rank/summary, server series and subjects.
  Subject detail preserves all type cards while filter events update the
  supplied controlled projection. Type order is `LECTURE`, `PRACTICE`, `LAB`.
- MetricSet4 preserves four counts/percentages plus `held`/`planned`; percentages
  are display values 0..100 or `null`. The client does not aggregate, rank or
  bucket. FUTURE/NO_DATA/H0 remain neutral/empty and never become 0%.
- Terminal/denied states disable actions; no eligibility is inferred from
  `ABSENT` or fixture metrics. Inline request has Back header, selected lesson,
  no dock; no slot is PASS claim.
- Theme is a root-local `data-theme="dark|light"` prop. Harness resolves the
  system media query and keeps composition identical between themes.

## 5. Constraints

- Russian UI, «ты» wording where a user-facing sentence is needed, Onest and
  tabular numerals; PCSS only, all author dimensions rem using 16px base.
- Semantic source values are resolved in local token files from
  `docs/design/tokens-v2.json`; components only consume `var(...)`.
- Geometry source values: content 358px/22.375rem, inset 16px/1rem, controls
  44px/2.75rem, metadata 10px/.625rem with 1px/.0625rem stroke and 4px/.25rem
  gap, disclosure 44px/22px, plot 170px/10.625rem, history 8px/.5rem. History
  fills its complete available width.
- Reuse existing semantic SVGs where available. No invented icons or assets.
- Respect focus-visible, keyboard order/return, reduced motion, narrow viewport
  and increased root font. No clipping. Do not create a second shell or form owner.

## 6. Existing patterns

- `TodayScreen.vue` is the nearest typed props/emits mobile feature pattern.
- `src/styles/tokens.pcss` establishes Onest/tabular/PCSS conventions, but new
  scoped token files resolve only the source-backed semantic aliases required by
  this task and do not alter shared tokens.
- Existing assets `room-*`, `lesson-type-*`, `attendance-tab`,
  `chevron-down`, `more-tab`, `profile-tab`, `schedule-tab` may be reused by
  the local harness chrome when needed.
- `vue-tsc` is required because baseline mobile-core `tsc` excludes `.vue`.

## 7. Acceptance criteria

All 13 source states are reachable in the harness and render through actual
components: `4593-142`, `4593-848365`, `4593-848496`, `4595-293`,
`4595-848430`, `4596-365`, `4710-232`, `4768-228`, `4603-142`,
`4603-848696`, `4798-142`, `4798-200`, `4798-285`.

Behavior checks cover selection/back, retained type cards, last-type filter,
H0 and null display, opaque IDs, denied actions, offline hiding stale data,
and controlled projection update. The harness uses no fake API call and claims
no backend/PWA/TMA PASS.

## 8. Verification

- Run scoped meaningful unit tests for both view-models and typed Vue component
  check including `.vue` files.
- Run exact harness Vite build and existing scoped ESLint.
- Start harness only on ports 18210 (Attendance) and 18211 (Statistics), use
  supported browser automation for 390x844 screenshots and keyboard/focus,
  dark/light/system/narrow/root-font/error states, then stop processes.
- Record revision, exact commands, exit codes, environment and evidence in
  `.agent/student-academic-ui/checks.json`; hash final evidence in manifest.
- Stop code edits before independent Sol review. Any review FAIL needs a new
  bounded defect packet and recheck before correction.

## 9. Do not

Do not touch shared shell/API/generated/lock/config paths, backend formulas,
StudentApi/navigation, Requests form implementation, fake fetch/cache, peer
payload, invented campus data, Figma or external state, secrets, deploy or
children. Missing product/contract/scope decisions become root deltas in
`.agent/student-academic-ui/`, not a redesign.
