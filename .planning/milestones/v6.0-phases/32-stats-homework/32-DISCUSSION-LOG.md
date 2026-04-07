# Phase 32: Stats + Homework - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 32-stats-homework
**Areas discussed:** Homework completion storage, BottomNav restructure, Red zone threshold source, Offline strategy for stats

---

## Homework Completion Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side (Recommended) | Use POST/DELETE /{id}/complete. Data persists across devices, no localStorage sync issues. Optimistic UI toggle with server confirmation. Backend already built. | ✓ |
| localStorage only | Per UI-SPEC. Key: hw:{userId}:{homeworkId}. Faster, no network needed, but data lost on cache clear and not synced across devices. | |
| You decide | Claude picks the best approach during planning | |

**User's choice:** Server-side (Recommended)
**Notes:** UI-SPEC assumed no backend endpoint existed, but HomeworkApi already has markComplete/unmarkComplete endpoints with per-student HomeworkCompletion tracking.

---

## BottomNav Restructure

| Option | Description | Selected |
|--------|-------------|----------|
| Replace Главная with Статистика, add Задания (5 tabs) | Главная is currently a placeholder with no real content. Replace it with Статистика. Add Задания as 5th tab. Total: Статистика, Расписание, Отметка, Задания, Профиль. | ✓ |
| Keep all 6 tabs | Add both Статистика and Задания alongside existing 4. Labels truncate on small screens but all accessible from one tap. | |
| Nest Stats+HW under Профиль | Profile page gets sections/links to Stats and Homework. BottomNav stays 4 tabs. Requires extra navigation. | |

**User's choice:** Replace Главная with Статистика, add Задания (5 tabs)
**Notes:** None

---

## Red Zone Threshold Source

| Option | Description | Selected |
|--------|-------------|----------|
| Single resolve call per group (Recommended) | Call GET /thresholds/resolve?groupId=X once to get the group-level default threshold. Compare each subject's percentage against it. Covers 90% of cases with 1 API call. Per-subject overrides are rare. | ✓ |
| Per-subject resolve calls | Call GET /thresholds/resolve?groupId=X&subjectId=Y for each subject. Accurate for per-subject thresholds but N+1 API calls. Only needed if headmen frequently set subject-specific thresholds. | |
| You decide | Claude picks the best approach during planning | |

**User's choice:** Single resolve call per group (Recommended)
**Notes:** None

---

## Offline Strategy for Stats

| Option | Description | Selected |
|--------|-------------|----------|
| Same as schedule: 1hr stale (Recommended) | Consistent pattern with Phase 30. staleTime 1hr, refetchOnReconnect, stale banner when offline. Simple and predictable for students. | ✓ |
| Longer stale: 4hr for stats, 1hr for homework | Stats change only after lessons end (~once a day). Homework list changes when headman publishes. Different staleness reflects data volatility. | |
| You decide | Claude picks the best approach during planning | |

**User's choice:** Same as schedule: 1hr stale (Recommended)
**Notes:** None

---

## Claude's Discretion

- TanStack Query hook implementation details (queryKey structure, error handling)
- Loading skeleton vs spinner choice per screen
- How to obtain student's groupId and semesterId
- AttendanceRecordsPage grouping strategy
- Homework sort order refinement
- Stagger animation timing

## Deferred Ideas

None — discussion stayed within phase scope
