# Requirements: RutCampusTrack v4.0

**Defined:** 2026-04-04
**Core Value:** Core attendance tracking — students check in via geo, headman marks manually, system auto-absents on lesson close, basic reports for journal and stats.

## v4.0 Requirements

Requirements for Attendance Service MVP. Each maps to roadmap phases.

### Infrastructure

- [x] **INFRA-01**: System initializes MongoDB indexes programmatically (unique on {lesson_id, user_id}, query indexes for reports)
- [x] **INFRA-02**: System serializes enums as lowercase strings in MongoDB via MongoCustomConversions
- [ ] **INFRA-03**: gRPC client connects to Schedule Service (GetActiveLesson, GetLessonById, GetLessonsByGroup)
- [ ] **INFRA-04**: gRPC client connects to Academic Service (GetGroupMembers, GetCampusGeofence, GetActiveSemester)
- [ ] **INFRA-05**: RabbitMQ consumer declares durable queue bound to rut-uit.events fanout exchange
- [ ] **INFRA-06**: System publishes attendance.marked event after successful checkin/manual mark

### Checkin

- [ ] **CHKN-01**: Student can geo-checkin by sending {lat, lng}, validated against campus geofence (Haversine)
- [ ] **CHKN-02**: Geo-checkin validates active lesson exists for student's group (gRPC to Schedule)
- [ ] **CHKN-03**: Geo-checkin enforces 5-min time window (lesson start - 5 min to lesson end + 5 min)
- [ ] **CHKN-04**: Geo-checkin respects is_geo_blocked flag from lesson
- [ ] **CHKN-05**: Geo-checkin is idempotent via MongoDB unique index (duplicate returns 409)
- [ ] **CHKN-06**: Redis dedup lock prevents double-submit (5-sec TTL per lesson+user)
- [ ] **CHKN-07**: Redis rate limiting prevents abuse (3 attempts/minute per user)

### Marking

- [ ] **MARK-01**: Headman can manually set attendance status for any student in their group
- [ ] **MARK-02**: Manual marking works per student (autosave per click, not batch)
- [ ] **MARK-03**: Auto-absent assigns status=absent to all unmarked students on lesson.closed event
- [ ] **MARK-04**: Auto-absent uses $setOnInsert to prevent overwriting existing checkins (race-safe)
- [ ] **MARK-05**: lesson.cancelled consumer updates existing attendance docs to status=cancelled

### Reports

- [ ] **RPRT-01**: Headman/teacher can view lesson attendance (all students + their status for a lesson)
- [ ] **RPRT-02**: Headman/teacher can view journal (students x lesson dates grid for group+subject)
- [ ] **RPRT-03**: Student can view own attendance stats (% per subject, excluding cancelled)
- [ ] **RPRT-04**: Student can view own attendance list (raw records, filterable by subject)
- [ ] **RPRT-05**: Report domain accesses checkin data only through AttendanceReadPort (domain isolation)

## Future Requirements

Deferred to v4.1+. Tracked but not in current roadmap.

### Excuse Tickets

- **EXCS-01**: Student can create excuse ticket (draft) with selected lessons
- **EXCS-02**: Student can submit ticket to headman for review
- **EXCS-03**: Headman can approve/reject excuse ticket
- **EXCS-04**: Approved ticket sets affected lessons to status=excused

### Late Checkin

- **LATE-01**: Student can request "forgot to mark" for a closed lesson
- **LATE-02**: Headman can confirm/reject late checkin request

### Advanced Reports

- **ADVR-01**: Top-skippers report per group
- **ADVR-02**: Attendance trend by week
- **ADVR-03**: Red zone alerts based on threshold configuration
- **ADVR-04**: PDF/Excel export of journal and stats

### File Attachments

- **FILE-01**: Student can attach files to excuse ticket
- **FILE-02**: Files forwarded to headman via Telegram (not stored in system)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Excuse tickets (create/submit/review) | Deferred to v4.1+ — MVP focuses on core checkin/marking/reports |
| File attachments + Telegram forwarding | Deferred to v4.1+ — depends on excuse tickets |
| Late checkin ("forgot to mark") flow | Deferred to v4.1+ — headman can manually mark for now |
| Advanced analytics (trends, top-skippers) | Deferred to v4.1+ — basic stats sufficient for MVP |
| PDF/Excel export | Deferred to v4.1+ — view-only sufficient for MVP |
| Teacher attendance correction | Read-only role by design; headman corrects via manual marking |
| Real-time WebSocket in Attendance Service | notification-web handles push; Attendance Service stays stateless |
| Batch manual marking | Autosave per-click is the stated UX; client-side loop if needed |
| Journal pagination | Bounded dataset (~600 docs max per query); revisit if scale grows |
| Pre-start 5-min checkin window | v4.0 requires ACTIVE lesson status; pre-start window deferred to v4.1 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 15 | Complete |
| INFRA-02 | Phase 15 | Complete |
| INFRA-03 | Phase 15 | Pending |
| INFRA-04 | Phase 15 | Pending |
| INFRA-05 | Phase 15 | Pending |
| INFRA-06 | Phase 17 | Pending |
| CHKN-01 | Phase 17 | Pending |
| CHKN-02 | Phase 17 | Pending |
| CHKN-03 | Phase 17 | Pending |
| CHKN-04 | Phase 17 | Pending |
| CHKN-05 | Phase 17 | Pending |
| CHKN-06 | Phase 17 | Pending |
| CHKN-07 | Phase 17 | Pending |
| MARK-01 | Phase 17 | Pending |
| MARK-02 | Phase 17 | Pending |
| MARK-03 | Phase 16 | Pending |
| MARK-04 | Phase 16 | Pending |
| MARK-05 | Phase 16 | Pending |
| RPRT-01 | Phase 18 | Pending |
| RPRT-02 | Phase 18 | Pending |
| RPRT-03 | Phase 18 | Pending |
| RPRT-04 | Phase 18 | Pending |
| RPRT-05 | Phase 18 | Pending |

**Coverage:**
- v4.0 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-04 after roadmap creation (v4.0 phases 15-18)*
