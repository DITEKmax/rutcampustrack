---
phase: 56
plan: "05"
subsystem: frontend-pwa
tags: [headman, stats, excuses, late-checkin, graceful-degradation, threshold, tdd, vitest]
dependency_graph:
  requires: [56-02]
  provides:
    - /group/excuses graceful-degradation empty state (D-10)
    - /group/late-checkin graceful-degradation empty state (D-10)
    - /group/stats full per-subject cards with red-zone sort + inline threshold editor
    - SubjectStatsCard reusable component (subject header + group metric + per-student rows + threshold editor)
  affects:
    - frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx
    - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx
    - frontends/pwa/src/features/headman/stats/StatsPage.tsx
tech_stack:
  added: []
  patterns:
    - Graceful-degradation shells (circle-icon + dev copy, no buttons) per UI-SPEC §7/§8
    - Per-subject hook iteration via SubjectStatsCollector (stable subjects array → stable hook order)
    - Client-side attendance % computation (present|excused|free_attendance counted; cancelled excluded; default 100 on empty denominator)
    - Inline threshold editor (number input 0..100 + Check save button) with fade-in success animation + mapped error text
    - Red-zone severity sort (red-zone first with lowest % leading, else alphabetical by Russian locale)
    - TDD RED→GREEN commits per task
key_files:
  created:
    - frontends/pwa/src/features/headman/excuses/ExcusesPage.test.tsx
    - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.test.tsx
    - frontends/pwa/src/features/headman/stats/SubjectStatsCard.tsx
    - frontends/pwa/src/features/headman/stats/StatsPage.test.tsx
  modified:
    - frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx
    - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx
    - frontends/pwa/src/features/headman/stats/StatsPage.tsx
decisions:
  - "Excuses and late-checkin pages do NOT call usePendingExcuses/usePendingLateCheckins from shared hooks — pure shells per D-10. Future re-enable phases should replace the empty-state block with a list."
  - "Hook iteration in SubjectStatsCollector calls useJournal + useResolveThreshold once per subject in the subjects-array order — relies on subjects being stable across renders (TanStack cache guarantees identity); disabled react-hooks/rules-of-hooks for that specific loop with justification"
  - "DEFAULT_THRESHOLD=75 constant used when ResolvedThreshold not yet loaded — avoids sorting flicker on first render"
  - "Test 5 asserts getAllByText('75%').length >= 1 because with a single student at 75%, both group-avg and student row render the same string — matches correct UI behavior"
  - "SubjectStatsCard uses useEffect to sync editValue with threshold prop after cache refetch — prevents stale user edit persisting when backend updates"
metrics:
  duration_minutes: 4
  completed_date: "2026-04-13"
  tasks_completed: 3
  tasks_total: 3
  files_created: 4
  files_modified: 3
requirements:
  - PWA-HEAD-02
---

# Phase 56 Plan 05: Excuses + LateCheckin + Stats Summary

**One-liner:** Two graceful-degradation shells (`/group/excuses`, `/group/late-checkin`) + fully-featured `/group/stats` with per-subject cards, client-side attendance %, red-zone sort, and inline threshold editor — replaces Wave 2 placeholders and closes Wave 3 of Phase 56.

## What Was Built

### 1. ExcusesPage (`/group/excuses`) — Graceful Degradation

**File:** `frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx`
**Test:** `frontends/pwa/src/features/headman/excuses/ExcusesPage.test.tsx` (5 tests)

- Back link `<Link to="/group">` with ArrowLeft
- Heading "Пропуски"
- 80×80 circle icon container (`w-20 h-20 rounded-full`, `--bg-secondary` bg, subtle border) with duotone `FileText` 36px in `--text-muted`
- "Функция в разработке" heading + body copy: *"Запросы студентов на одобрение пропусков появятся здесь. Сейчас эта функция находится в разработке."*
- No action buttons (D-10 shell)

### 2. LateCheckinPage (`/group/late-checkin`) — Graceful Degradation

**File:** `frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx`
**Test:** `frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.test.tsx` (5 tests)

Identical shell to ExcusesPage, but:
- Heading: "Запросы отметки"
- Icon: duotone `Clock` 36px
- Body: *"Запросы студентов на опоздалую отметку появятся здесь. Сейчас эта функция находится в разработке."*

### 3. SubjectStatsCard (reusable component)

**File:** `frontends/pwa/src/features/headman/stats/SubjectStatsCard.tsx`

```typescript
interface SubjectStatsCardProps {
  subjectId: number
  groupId: number
  subjectName: string
  groupAttendancePercent: number   // 0..100
  threshold: number                 // 0..100
  studentRows: Array<{ studentId, studentName, attendancePercent }>
}
```

- **Red-zone trigger:** `groupAttendancePercent < threshold` → 4px danger left-border + "Низкая посещаемость" badge + danger-colored group metric
- **Group metric:** `text-lg font-semibold`, `--accent-primary` normally / `--accent-danger` in red-zone
- **Per-student rows:** up to 10 rows; each row shows optional red-dot (if student % < threshold) + name + mono tabular-nums %; overflow "Ещё N студентов — смотрите в веб-панели"
- **Threshold editor:** `<input type="number" min="0" max="100">` + % suffix + 24×24 Check button
  - Local `editValue` state, synced with `threshold` prop via `useEffect` on prop change (prevents stale edits after cache refetch)
  - Input clamps to 0..100 on change (T-56-17 mitigation)
  - Save → `useSetSubjectThreshold.mutate({ subjectId, minPercentage })`
  - Success → 300ms fade-in+scale Check animation, auto-dismiss after 1s, then `reset()`
  - Error → `mapHeadmanApiError(status)` rendered as `role="alert"` below card

### 4. StatsPage (`/group/stats`)

**File:** `frontends/pwa/src/features/headman/stats/StatsPage.tsx`
**Test:** `frontends/pwa/src/features/headman/stats/StatsPage.test.tsx` (7 tests)

Orchestrates `useGroupSubjects()` + per-subject `useJournal()`/`useResolveThreshold()` via internal `SubjectStatsCollector` helper component, then renders sorted `SubjectStatsCard`s.

**Semester window:** `{year}-09-01` where year = current if month ≥ 9 else current-1 → today.

**Flow:**
1. `groupId` missing → "Группа не назначена"
2. subjects loading → 3 skeleton cards (`animate-pulse`, 200px high)
3. subjects empty → "Группе не назначены предметы"
4. subjects present → `SubjectStatsCollector` loops hooks per subject, computes stats, sorts by severity, renders cards

## Cell-to-Attendance-% Rule (documentation for Phase 57+)

```
For each subject → collect all journal cells in semester window
  For each student (grouped by studentId):
    presentLike = count of cells where status ∈ {present, excused, free_attendance}
    absent      = count of cells where status == absent
    cancelled   = EXCLUDED from denominator entirely
    studentPercent = (presentLike / (presentLike + absent)) × 100
    default 100% if denominator == 0
  groupPercent = mean of all student percentages
  isRedZone = groupPercent < threshold
```

Sort order: red-zone first (ascending by % — lowest first = most urgent), then non-red-zone alphabetically using Russian locale.

## Threshold Save Flow

```
User edits input (0..100, clamped on change)
  ↓ onClick Check button
mutate({ subjectId, minPercentage: clamped })
  ↓ success path
  isSuccess=true → showSuccess=true → 300ms fade-in+scale check icon
  → 1000ms setTimeout → showSuccess=false + reset()
  ↓ error path
  isError=true → inline <p role="alert"> with mapHeadmanApiError(status)
  input border switches to --accent-danger
```

## Test Counts

| Before Plan 56-05 | After Plan 56-05 |
|-------------------|------------------|
| 98 tests, 18 files (post 56-04) | 115 tests, 21 files |

Delta: **+17 tests** (5 ExcusesPage + 5 LateCheckinPage + 7 StatsPage) across **3 new test files**.

All 98 pre-existing tests continue to pass unchanged.

## Global XSS Gate

`grep -rn dangerouslySetInnerHTML frontends/pwa/src/features/headman/` → **0 matches**.

All new files (ExcusesPage, LateCheckinPage, SubjectStatsCard, StatsPage) and test files rely on React's automatic text-child escaping. No `innerHTML`, `bypassSecurityTrust*`, or `eval`.

## Threat Model Compliance

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-56-17 (Tampering — threshold out of 0..100) | HTML `type="number" min="0" max="100"` + explicit `Math.max(0, Math.min(100, ...))` clamp before mutate; backend @Min/@Max still enforces | Implemented |
| T-56-18 (EoP — forged is_headman) | Client surfaces 403 via `mapHeadmanApiError` inline alert; server-side authz unchanged (Phase 54 fix) | Implemented |
| T-56-19 (XSS — names in cards) | React auto-escape; zero `dangerouslySetInnerHTML` matches across headman/ | Verified |
| T-56-20 (Tampering — client-side % calc) | Accepted — computation is read-only for display; writes always through server mutation endpoints | Accepted |

## Frozen Directories Confirmation

No modifications to `features/{home,schedule,checkin,profile,push,auth}/`. Touched files live only under `features/headman/`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test 5 assertion collision on duplicate "75%" rendering**
- **Found during:** Task 3 GREEN verification
- **Issue:** Test 5 fixture has a single student at 75%, making both the group-average metric and the student-row render the literal "75%". `screen.getByText('75%')` throws "multiple elements found".
- **Fix:** Changed assertion to `screen.getAllByText('75%').length >= 1`. This matches correct UI behavior (both numbers should appear) and the test still verifies the computed % surfaces somewhere in the card.
- **Files modified:** `frontends/pwa/src/features/headman/stats/StatsPage.test.tsx`
- **Commit:** 2083cda (included in Task 3 GREEN commit)

None of the above are architectural deviations. All three tasks executed per plan.

## Phase 56 Wave 3 Aggregate (56-03 + 56-04 + 56-05)

| Wave 3 Plan | New Tests | Cumulative PWA Vitest |
|-------------|-----------|-----------------------|
| 56-03 (Overview + Students + Subjects) | +15 (5×3) | 86 |
| 56-04 (SegmentedControl + Journal) | +12 (5+7) | 98 |
| 56-05 (Excuses + LateCheckin + Stats) | +17 (5+5+7) | **115** |

**Wave 3 total new tests: 44.** All 115 tests currently pass; `npx tsc --noEmit` exits 0.

## Known Stubs

None. All three pages in this plan are fully wired:
- ExcusesPage / LateCheckinPage — intentional dev-state shells per D-10 (not stubs — the empty state IS the feature for Phase 56)
- StatsPage — fully wired to real `useGroupSubjects` + `useJournal` + `useResolveThreshold` + `useSetSubjectThreshold` hooks

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. All mutations continue to traverse the existing API Gateway via hooks established in Plan 56-02 (`useSetSubjectThreshold` → `PUT /academic/thresholds/subject?subjectId=`). No new trust boundary crossings.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx | FOUND |
| frontends/pwa/src/features/headman/excuses/ExcusesPage.test.tsx | FOUND |
| frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx | FOUND |
| frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.test.tsx | FOUND |
| frontends/pwa/src/features/headman/stats/SubjectStatsCard.tsx | FOUND |
| frontends/pwa/src/features/headman/stats/StatsPage.tsx | FOUND |
| frontends/pwa/src/features/headman/stats/StatsPage.test.tsx | FOUND |
| Commit b62575f (Task 1 RED — ExcusesPage + LateCheckinPage tests) | VERIFIED |
| Commit 9f662c0 (Task 1 GREEN — graceful-degradation shells) | VERIFIED |
| Commit c29b929 (Task 2 — SubjectStatsCard component) | VERIFIED |
| Commit 0729f8a (Task 3 RED — StatsPage tests) | VERIFIED |
| Commit 2083cda (Task 3 GREEN — StatsPage implementation) | VERIFIED |
| npx vitest run: 115/115 pass | PASSED |
| npx tsc --noEmit: exits 0 | PASSED |
| dangerouslySetInnerHTML across features/headman/: 0 matches | PASSED |
