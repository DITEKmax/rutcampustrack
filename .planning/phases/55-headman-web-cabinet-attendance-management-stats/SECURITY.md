# SECURITY.md — Phase 55: Headman Web Cabinet — Attendance Management & Stats

**Audited:** 2026-04-10
**ASVS Level:** L1
**Auditor:** gsd-security-auditor (claude-sonnet-4-6)

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-55-01 | Information Disclosure | accept | CLOSED | `authorizeHeadmanOrTeacher()` confirmed at ReportService.java:115 — gates every call to getJournal; lessonId is non-secret schedule metadata |
| T-55-02 | Elevation of Privilege | accept | CLOSED | `authorizeHeadmanOrTeacher()` enforces role check (headman) + `requestContext.getGroupId().equals(groupId)` at ReportService.java:258 — group ownership verified before journal data is returned |
| T-55-03 | Elevation of Privilege | mitigate | CLOSED | All 4 new headman child routes include `canActivate: [headmanGuard]`: journal (app.routes.ts:202), excuses (app.routes.ts:210), late-checkin (app.routes.ts:218), stats (app.routes.ts:226) |
| T-55-04 | Information Disclosure | accept | CLOSED | Status value cycled only through NEXT_STATUS closed record on client; backend MarkingService.ALLOWED_STATUSES provides server-side enforcement (pre-existing, outside Phase 55 scope) |
| T-55-05 | Tampering | mitigate | CLOSED | `NEXT_STATUS: Record<AttendanceStatus, AttendanceStatus>` defined at headman-journal-grid.component.ts:20-26 with exactly 5 keys; `onCellClick` guards `if (cell.status === 'cancelled' \|\| !cell.lessonId) return` at line 225 before NEXT_STATUS lookup — no arbitrary string can reach markAttendance |
| T-55-06 | Information Disclosure | accept | CLOSED | journalData @Input only reachable through headmanGuard-protected /headman/journal route confirmed in app.routes.ts:202-209 |
| T-55-07 | Denial of Service | mitigate | CLOSED | `getPendingExcuses().pipe(catchError(() => of(null)))` at headman-excuses.component.ts:39-41; `getPendingLateCheckins().pipe(catchError(() => of(null)))` at headman-late-checkin.component.ts:39-41 — neither component throws unhandled rejection |
| T-55-08 | Elevation of Privilege | accept | CLOSED | /headman/excuses route guarded by `canActivate: [headmanGuard]` at app.routes.ts:211 — confirmed present |
| T-55-09 | Tampering | mitigate | CLOSED | `onThresholdBlur` at headman-stats.component.ts:176-184 runs `parseInt(input.value, 10)`, rejects `isNaN(value) \|\| value < 0 \|\| value > 100` and reverts input without API call; only valid 0-100 integers reach `saveThreshold` → `setSubjectThreshold` |
| T-55-10 | Information Disclosure | accept | CLOSED | Each per-subject journal request is authorized by backend `authorizeHeadmanOrTeacher()` — data scope is bounded to headman's own group |
| T-55-11 | Denial of Service | accept | CLOSED | Each subject forkJoin request wrapped in `catchError(() => of(null))` at headman-stats.component.ts:147-148 — one failing request does not cancel others |

---

## Accepted Risks Log

| Threat ID | Category | Rationale | Owner |
|-----------|----------|-----------|-------|
| T-55-01 | Information Disclosure | lessonId is non-secret schedule metadata (schedule data is already visible to headman and teacher); access already gated by authorizeHeadmanOrTeacher() | Backend |
| T-55-02 | Elevation of Privilege | Adding lessonId to JournalCell does not widen the authorization surface; authorizeHeadmanOrTeacher() enforces group ownership on every journal call | Backend |
| T-55-04 | Information Disclosure | Client-side NEXT_STATUS cycling uses only valid AttendanceStatus values; MarkingService.ALLOWED_STATUSES enforces valid values server-side as defense-in-depth | Backend |
| T-55-06 | Information Disclosure | Component is only reachable via headmanGuard-protected route; backend already authorized the data before it reaches the Angular @Input | Frontend |
| T-55-08 | Elevation of Privilege | /headman/excuses route carries canActivate: [headmanGuard]; plain STUDENT role is blocked at the route level before the component is instantiated | Frontend |
| T-55-10 | Information Disclosure | All per-subject journal requests are individually authorized by backend authorizeHeadmanOrTeacher(); headman can only query their own group | Backend |
| T-55-11 | Denial of Service | Subject count per group is typically < 20; each request is lightweight; individual catchError prevents cascade failure | Frontend |

---

## Unregistered Threat Flags

None — all SUMMARY.md files for Plans 01-05 report no new threat flags.

---

## Summary

**Threats Closed:** 11/11
**Threats Open:** 0/11
**Mitigations Verified:** 4/4 (T-55-03, T-55-05, T-55-07, T-55-09)
**Accepted Risks Logged:** 7/7
