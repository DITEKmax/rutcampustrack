# Phase 12: Lesson Auto-Generation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 12-lesson-auto-generation
**Areas discussed:** Trigger timing, Week parity anchor, Re-generation on update

---

## Trigger Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Synchronous | Generate in same transaction as template creation. Simpler, no race conditions. ON CONFLICT DO NOTHING handles retries. | ✓ |
| Async after commit | Use @TransactionalEventListener(AFTER_COMMIT). Faster HTTP response but partial state briefly visible. | |
| You decide | Claude picks best approach. | |

**User's choice:** Synchronous (Recommended)
**Notes:** None

---

## Week Parity Anchor

| Option | Description | Selected |
|--------|-------------|----------|
| Semester start = week 1 (odd) | First week of semester is week 1 (odd). Simple, deterministic. | |
| ISO week number | Use Java ISO week-of-year. Calendar-aligned but potentially confusing. | |
| You decide | Claude picks based on conventions. | |

**User's choice:** (Rejected initial options) — User clarified that semesters can start from either odd or even week. Need a `first_week_type` field on the semester itself.

**Follow-up:**

| Option | Description | Selected |
|--------|-------------|----------|
| Поле в семестре | Add first_week_type to semesters table + gRPC SemesterResponse. Requires V6 migration in academic_db. | ✓ |
| Параметр при создании шаблона | Headman specifies anchor when creating template. Duplicates logic across templates. | |

**User's choice:** Поле в семестре (Recommended)
**Notes:** Semesters can start on a weekend (e.g., Sept 1 = Sunday). First week is still that week, but first classes are Monday. Generation iterates from date_from matching day_of_week, so weekends are naturally excluded.

---

## Re-generation on Update

| Option | Description | Selected |
|--------|-------------|----------|
| Не перегенерировать | Existing lessons stay as-is. Template changes affect display only. | |
| Перегенерировать planned | Delete planned lessons with date >= today, re-generate. Keep active/closed/cancelled. | ✓ (modified) |
| Запретить изменение day/weekType | Force delete+recreate template for schedule changes. | |

**User's choice:** Modified version of "Перегенерировать planned" — key point is that attendance is tied to lesson.id, so non-planned lessons must never be deleted. Schedule-affecting changes (day, time, weekType) trigger re-generation of future planned lessons. Non-schedule changes (teacher, subject, room) do not.
**Notes:** User emphasized that attendance must be preserved under the old calendar. Full lesson deletion management deferred to post-MVP.

---

## Claude's Discretion

- Service class organization for generation logic
- Batch insert strategy
- Test approach

## Deferred Ideas

- Individual lesson deletion by headman — post-MVP
- Complex schedule management (swap dates, etc.) — post-MVP
