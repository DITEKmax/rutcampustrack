---
phase: 56
plan: "03"
subsystem: frontend-pwa
tags: [headman, react, tanstack-query, vitest, tdd, framer-motion, phosphor-icons]
dependency_graph:
  requires: [56-02]
  provides:
    - Overview page at /group/overview with stat cards + pending badges
    - StudentsList page at /group/students with assistant CRUD + AddAssistantModal
    - SubjectsList page at /group/subjects with subject CRUD + SubjectFormModal
  affects:
    - frontends/pwa/src/features/headman/overview/Overview.tsx
    - frontends/pwa/src/features/headman/students/StudentsList.tsx
    - frontends/pwa/src/features/headman/subjects/SubjectsList.tsx
tech_stack:
  added: []
  patterns:
    - TDD RED→GREEN with per-phase commits
    - Modal overlay pattern (fixed inset-0 + AnimatePresence fade-in) reusable shape
    - Floating Action Button pattern (fixed bottom-20 right-6 z-40, 56x56px)
    - Motion entrance animation (opacity 0→1, y 8→0, 150ms) on stat cards
    - Inline delete confirmation dialog (no separate route, inline state machine)
key_files:
  created:
    - frontends/pwa/src/features/headman/overview/Overview.test.tsx
    - frontends/pwa/src/features/headman/students/StudentsList.test.tsx
    - frontends/pwa/src/features/headman/students/AddAssistantModal.tsx
    - frontends/pwa/src/features/headman/subjects/SubjectsList.test.tsx
    - frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx
  modified:
    - frontends/pwa/src/features/headman/overview/Overview.tsx
    - frontends/pwa/src/features/headman/students/StudentsList.tsx
    - frontends/pwa/src/features/headman/subjects/SubjectsList.tsx
decisions:
  - "AddAssistantModal and SubjectFormModal use same overlay pattern (fixed inset-0 z-50 + AnimatePresence) — extractable to shared/ in future"
  - "AssistantModal uses native <select> for student picker per plan spec; searchable dropdown deferred"
  - "Delete confirmation uses inline state machine (no portal/separate component) to minimize complexity"
  - "useCreateAssistant called with (body, { onSuccess }) — test assertion uses expect.anything() for second arg"
metrics:
  duration_minutes: 11
  completed_date: "2026-04-13"
  tasks_completed: 3
  tasks_total: 3
  files_created: 5
  files_modified: 3
requirements:
  - PWA-HEAD-02
---

# Phase 56 Plan 03: Overview + StudentsList + SubjectsList Summary

**One-liner:** Three headman detail pages replace Wave 2 placeholders — Overview with live stat cards and pending-ticket badges, StudentsList with assistant CRUD and 4-permission AddAssistantModal, SubjectsList with full subject CRUD and teacher-picker SubjectFormModal; 15 new tests (3×5) cover all specified behaviors via TDD RED→GREEN.

## What Was Built

### Overview (`/group/overview`)

**File:** `frontends/pwa/src/features/headman/overview/Overview.tsx`
**Test:** `frontends/pwa/src/features/headman/overview/Overview.test.tsx` (5 tests)

Sections implemented per UI-SPEC §3:
- Back button `<Link to="/group">` with ArrowLeft icon
- **Члены группы** stat card — `useGroupMembers().data.length`, `text-lg font-semibold`, accent-primary color, Users 32px duotone icon
- **Текущая пара** stat card — `useTodayLesson(groupId)`, shows `subjectName` + time range in JetBrains Mono `tabular-nums`; null state: "На сегодня пар больше нет"
- **Пропусков ждут одобрения** — `<Link to="/group/excuses">`, red badge when count > 0, "Нет ожидающих запросов" when empty
- **Запросы опоздалой отметки** — `<Link to="/group/late-checkin">`, same badge pattern
- Motion entrance animation per card (opacity 0→1, y 8→0, 150ms, staggered delays)
- Edge case: groupId undefined → "Группа не назначена" empty state

**Graceful degradation points:**
- `useTodayLesson` returns `null` → "На сегодня пар больше нет" muted text
- `usePendingExcuses` / `usePendingLateCheckins` return `[]` or 404 → "Нет ожидающих запросов"
- `user.groupId` undefined → full page empty state

### StudentsList (`/group/students`)

**File:** `frontends/pwa/src/features/headman/students/StudentsList.tsx`
**Modal:** `frontends/pwa/src/features/headman/students/AddAssistantModal.tsx`
**Test:** `frontends/pwa/src/features/headman/students/StudentsList.test.tsx` (5 tests)

Features implemented per UI-SPEC §4:
- Full member list with avatar circle (first initial), fullName + login
- Role badges: "Староста" (accent-primary) for `isHeadman`, "Помощник" (accent-info) for `isAssistant`
- "Помощники старосты" sub-section listing only `isAssistant=true` members with trash icon
- Floating FAB (`fixed bottom-20 right-6 z-40`, 56×56px, accent-primary)
- Loading skeleton (4 rows × 56px) while `isLoading`

**AddAssistantModal shape** (reusable pattern for future):
```typescript
interface Props {
  open: boolean
  onClose: () => void
  candidates: GroupMember[]  // pre-filtered non-headman, non-assistant members
}
```
- Native `<select>` student picker
- 4 checkboxes: Управление студентами / Управление предметами / Одобрение пропусков / Редактирование статистики
- Calls `useCreateAssistant().mutate({ studentId, permissions }, { onSuccess })` 
- Inline error via `mapHeadmanApiError(status)`

**Delete confirmation:** inline state (`deleteTarget: number | null`), dialog copy "Убрать помощника?" + Удалить/Отмена

### SubjectsList (`/group/subjects`)

**File:** `frontends/pwa/src/features/headman/subjects/SubjectsList.tsx`
**Modal:** `frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx`
**Test:** `frontends/pwa/src/features/headman/subjects/SubjectsList.test.tsx` (5 tests)

Features implemented per UI-SPEC §5:
- Subject rows: name (font-semibold) + teacherName (text-muted below)
- Pencil icon (edit) + Trash icon (delete) per row
- Row body click OR pencil click opens EDIT modal with prefilled fields
- FAB opens CREATE modal with empty fields
- Empty state: "У группы пока нет предметов" centered muted text
- Loading skeleton (3 rows)

**SubjectFormModal shape** (reusable pattern for future):
```typescript
interface Props {
  open: boolean
  mode: 'create' | 'edit'
  subject?: Subject   // required when mode='edit'
  onClose: () => void
}
```
- Heading: "Новый предмет" (create) / "Редактировать предмет" (edit)
- Text input for name (minLength 2)
- `<select>` teacher picker populated from `useGroupTeachers(groupId)`
- Create: `useCreateSubject().mutate(body, { onSuccess })`
- Edit: `useUpdateSubject().mutate({ id, body }, { onSuccess })`
- Inline error via `mapHeadmanApiError(status)`

**Delete confirmation:** inline state (`deleteTarget: Subject | null`), dialog copy "Удалить этот предмет?" + Удалить/Отмена

## Test Counts

| Before Plan 56-03 | After Plan 56-03 |
|-------------------|------------------|
| 71 tests, 13 files | 86 tests, 16 files |

New test files: `Overview.test.tsx` (5), `StudentsList.test.tsx` (5), `SubjectsList.test.tsx` (5).
All 71 pre-existing tests continue to pass unchanged.

## Reusable Modal Pattern

Both `AddAssistantModal` and `SubjectFormModal` follow the same overlay pattern:

```tsx
<AnimatePresence>
  {open && (
    <motion.div className="fixed inset-0 z-50" style={{ background: 'rgba(0,0,0,0.4)' }}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div className="rounded-lg p-6 max-w-md mx-4 mt-24"
        style={{ background: 'var(--bg-secondary)' }}
        initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -16 }} transition={{ duration: 0.15 }}>
        {/* content */}
      </motion.div>
    </motion.div>
  )}
</AnimatePresence>
```

This pattern is a candidate for extraction to `shared/components/Modal.tsx` in a future plan if a 3rd modal is needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test assertions used getByText where multiple renders existed**
- **Found during:** Task 2 + Task 3 GREEN phase
- **Issue:** Members with `isAssistant=true` appear in both main list AND "Помощники старосты" sub-section, causing `getByText` to throw "Found multiple elements"
- **Fix:** Changed test assertions to `getAllByText().length >= 1`; this reflects correct component behavior (member appears in both sections intentionally per UI-SPEC §4)
- **Files modified:** `StudentsList.test.tsx`
- **Commit:** ad1ef43 (combined with StudentsList test)

**2. [Rule 1 - Bug] Test 4 assertion signature mismatch for mutate**
- **Found during:** Task 2 GREEN phase
- **Issue:** TanStack Query `useMutation.mutate` in `AddAssistantModal` is called as `mutate(body, { onSuccess })` — two arguments. Test asserted only one argument.
- **Fix:** Added `expect.anything()` as second argument to `toHaveBeenCalledWith`
- **Files modified:** `StudentsList.test.tsx`

None of the above are plan deviations — they are test fixture adjustments during the TDD GREEN phase.

## Threat Model Compliance

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-56-09: Client-side bypass of min-length | `minLength={2}` on name input (UX only); backend Bean Validation enforces | Implemented |
| T-56-10: XSS via rendered names | All text via `{subject.name}`, `{member.fullName}` — React auto-escapes | Verified: 0 dangerouslySetInnerHTML matches |
| T-56-11: Elevation of Privilege | mapHeadmanApiError returns "У вас нет прав" for 403; backend enforces via RoleCheckAspect | Implemented |

## Known Stubs

None — all three pages are fully implemented with real data sources wired. The Wave 2 placeholders ("Загрузка…") have been completely replaced.

## Threat Surface Scan

No new network endpoints or auth paths introduced. All mutations traverse existing API Gateway with Bearer token through `headmanApi.ts` hooks established in Plan 56-02. No new trust boundary crossings.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| frontends/pwa/src/features/headman/overview/Overview.tsx | FOUND |
| frontends/pwa/src/features/headman/overview/Overview.test.tsx | FOUND |
| frontends/pwa/src/features/headman/students/StudentsList.tsx | FOUND |
| frontends/pwa/src/features/headman/students/AddAssistantModal.tsx | FOUND |
| frontends/pwa/src/features/headman/students/StudentsList.test.tsx | FOUND |
| frontends/pwa/src/features/headman/subjects/SubjectsList.tsx | FOUND |
| frontends/pwa/src/features/headman/subjects/SubjectFormModal.tsx | FOUND |
| frontends/pwa/src/features/headman/subjects/SubjectsList.test.tsx | FOUND |
| Commit a0ee802 (Task 1 RED — Overview test) | VERIFIED |
| Commit e836539 (Task 1 GREEN — Overview impl) | VERIFIED |
| Commit 03f9be1 (Task 2 RED — StudentsList test) | VERIFIED |
| Commit 669e48c (Task 2 GREEN — StudentsList + AddAssistantModal) | VERIFIED |
| Commit 2ed2a1f (Task 3 RED — SubjectsList test) | VERIFIED |
| Commit ad1ef43 (Task 3 GREEN — SubjectsList + SubjectFormModal) | VERIFIED |
| npx vitest run: 86/86 pass | PASSED |
| npx tsc --noEmit: exits 0 | PASSED |
| dangerouslySetInnerHTML: 0 matches | PASSED |
