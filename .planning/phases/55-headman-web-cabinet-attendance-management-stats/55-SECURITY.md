---
phase: 55
slug: headman-web-cabinet-attendance-management-stats
status: verified
threats_open: 0
asvs_level: L1
created: 2026-04-10
---

# Phase 55 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| HTTP client → GET /api/attendance/reports/journal | Untrusted groupId/subjectId query params cross trust boundary | Schedule metadata (lessonId, status) |
| Angular route guard → component | headmanGuard must remain on all 4 new routes | Session/role data |
| Component cell click → markAttendance PUT | Status value must only be a valid AttendanceStatus string | Attendance status enum |
| API error → component | 404 must not propagate as uncaught exception | HTTP error codes |
| Threshold input → setSubjectThreshold PUT | User-controlled numeric value must be validated before API call | Integer 0-100 |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-55-01 | Information Disclosure | JournalCell.lessonId in response | accept | lessonId is non-secret schedule metadata; gated by authorizeHeadmanOrTeacher() at ReportService.java:115 | closed |
| T-55-02 | Elevation of Privilege | getJournal authorization | accept | requestContext.getGroupId().equals(groupId) enforced at ReportService.java:258 — group ownership binding | closed |
| T-55-03 | Elevation of Privilege | app.routes.ts headman routes | mitigate | All 4 new routes carry canActivate: [headmanGuard] — lines 202, 210, 218, 226 in app.routes.ts | closed |
| T-55-04 | Information Disclosure | markAttendance sends status to server | accept | Client cycles only through NEXT_STATUS closed record; backend MarkingService.ALLOWED_STATUSES provides server-side gate | closed |
| T-55-05 | Tampering | onCellClick status value sent to backend | mitigate | NEXT_STATUS: Record<AttendanceStatus, AttendanceStatus> at headman-journal-grid.component.ts:20-26; onCellClick guards cancelled/!lessonId before lookup | closed |
| T-55-06 | Information Disclosure | journalData @Input contains all student records | accept | Component reachable only via headmanGuard-gated /headman/journal at app.routes.ts:202-209 | closed |
| T-55-07 | Denial of Service | getPendingExcuses 404 error propagation | mitigate | catchError(() => of(null)) at headman-excuses.component.ts:40 and headman-late-checkin.component.ts:40 | closed |
| T-55-08 | Elevation of Privilege | /headman/excuses route | accept | Route carries canActivate: [headmanGuard] at app.routes.ts:211 | closed |
| T-55-09 | Tampering | Threshold input → setSubjectThreshold PUT | mitigate | onThresholdBlur at headman-stats.component.ts:176-184: parseInt + isNaN/range 0-100 guard reverts without API call | closed |
| T-55-10 | Information Disclosure | forkJoin journal data for all subjects | accept | Per-subject requests individually authorized by backend; scope bounded to headman's own group | closed |
| T-55-11 | Denial of Service | forkJoin of N journal requests | accept | Each request wrapped in catchError(() => of(null)) at headman-stats.component.ts:147-148 | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-55-01 | T-55-01 | lessonId is non-secret schedule metadata. Backend authorization gate (authorizeHeadmanOrTeacher) already limits who can receive the response to headman of the group or teaching teacher. No PII or sensitive data exposed. | gsd-security-auditor | 2026-04-10 |
| AR-55-02 | T-55-02 | lessonId field addition is purely additive to getJournal response. The authorization surface is unchanged — group ownership enforced at ReportService level. | gsd-security-auditor | 2026-04-10 |
| AR-55-04 | T-55-04 | Client status value is bounded by NEXT_STATUS closed record. Backend ALLOWED_STATUSES check provides defense-in-depth server-side validation. No new attack surface. | gsd-security-auditor | 2026-04-10 |
| AR-55-06 | T-55-06 | journalData arrives via headmanGuard-gated route. No additional input validation needed on @Input since data origin is the authorized API response. | gsd-security-auditor | 2026-04-10 |
| AR-55-08 | T-55-08 | /headman/excuses route protection is inherited from headmanGuard registered in Plan 02. Accept documented because guard coverage verified in app.routes.ts. | gsd-security-auditor | 2026-04-10 |
| AR-55-10 | T-55-10 | Journal data authorized server-side per subject. All subjects belong to headman's own group. No cross-group data leak possible. | gsd-security-auditor | 2026-04-10 |
| AR-55-11 | T-55-11 | N subjects typically < 20 per group. Individual catchError prevents cascade failures. No rate-limiting concern at this scale. | gsd-security-auditor | 2026-04-10 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-10 | 11 | 11 | 0 | gsd-security-auditor (sonnet) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter
