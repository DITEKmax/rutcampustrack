# Phase 55: Headman Web Cabinet — Attendance Management + Stats - Discussion Log (Assumptions Mode)

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in CONTEXT.md — this log preserves the analysis.

**Date:** 2026-04-09
**Phase:** 55-headman-web-cabinet-attendance-management-stats
**Mode:** assumptions
**Areas analyzed:** Journal Grid, Excuse & Late Check-in Degradation, Stats + Threshold, HeadmanApiService Extension, Sidebar + Routes

## Assumptions Presented

### Journal Grid — Write Capability
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Headman journal needs a new `HeadmanJournalGridComponent` (not reuse teacher's read-only grid) | Confident | `journal-cell.component.ts` is read-only; teacher grid has no click outputs |
| `JournalCell` backend DTO must be extended with `lessonId` field | Confident | `dto/report/JournalCell.java` has no `lessonId`; marking API requires it; `AttendanceRecord` port has it |
| Optimistic UI on cell click — no "Save" button | Likely | ROADMAP success criterion: "clicking a cell cycles through attendance statuses" implies immediate action |

### Excuse & Late Check-in — Graceful Degradation
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Backend endpoints not implemented — graceful degradation required | Confident | `HeadmanApiService` docs say "deferred from v5.0"; no controller found in academic-service for excuses/late-checkins |
| `catchError(() => of(null))` pattern + `.page-empty` state | Confident | Already implemented in `HeadmanDashboardComponent` for same endpoints |

### Stats + Red-Zone Threshold
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Stats derived from journal data (no dedicated stats endpoint) | Confident | No headman stats endpoint found; teacher stats derive from `JournalApiService.getJournal()` calls per subject |
| Threshold via `GET /academic/thresholds/resolve` + `PUT /academic/thresholds/subject` | Confident | `ThresholdApi.java` lines 50-70; `@RequireRole(STUDENT)` — headman passes after Phase 54 fix |
| Inline editing (not dialog) | Confident | ROADMAP success criterion 4: "edit the threshold per subject inline and save" |

### HeadmanApiService Extension
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| Add to existing `HeadmanApiService` (not a new service) | Likely | Phase 54 pattern: single service for all headman HTTP calls |
| Methods needed: `getJournal`, `markAttendance`, `resolveThreshold`, `setSubjectThreshold` | Confident | Derived from backend endpoint inventory and phase requirements |

### Sidebar + Route Registration
| Assumption | Confidence | Evidence |
|------------|-----------|----------|
| 4 new `allNavItems` entries with `{ isHeadman: true }` | Confident | `sidebar.component.ts` lines 169-191: existing 3 headman items use this exact pattern |
| 4 new routes in headman children block before `redirectTo` | Confident | `app.routes.ts` headman block structure (lines 168-203) |

## Corrections Made

No corrections — all assumptions auto-confirmed (assumptions mode, no interactive clarification needed).

## Key Research Finding

**lessonId gap confirmed:** `JournalCell` backend DTO (`dto/report/JournalCell.java`) does not
expose `lessonId`. `AttendanceRecord` port (the source data) DOES carry `lessonId`. Fix is
additive: extend `JournalCell` constructor + update `ReportService.getJournal()` to pass
`r.lessonId()`. Angular `types.ts` gains `lessonId?: number`. Teacher grid is unaffected.
