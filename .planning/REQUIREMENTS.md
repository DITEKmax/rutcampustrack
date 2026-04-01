# Requirements: RutCampusTrack v3.0

**Defined:** 2026-03-31
**Core Value:** Schedule Service with full lesson lifecycle — template CRUD, auto-generation, status transitions, RabbitMQ events, gRPC server.

## v3.0 Requirements

Requirements for Schedule Service milestone. Each maps to roadmap phases.

### Schedule Templates

- [x] **TMPL-01**: Headman can create a schedule template (subject, teacher, room, day, time, week parity)
- [x] **TMPL-02**: Headman can update an existing schedule template
- [x] **TMPL-03**: Headman can delete (deactivate) a schedule template
- [x] **TMPL-04**: Headman can view all schedule templates for their group
- [x] **TMPL-05**: System validates subject/teacher via gRPC to Academic Service before creating template

### Lesson Management

- [ ] **LSSN-01**: System auto-generates lessons for all semester dates when template is created
- [ ] **LSSN-02**: Lesson generation respects week parity (odd/even/all) anchored to semester start
- [x] **LSSN-03**: Lesson generation is idempotent (retry-safe via UNIQUE constraint)
- [x] **LSSN-04**: Headman can cancel a specific lesson with reason
- [x] **LSSN-05**: Headman can restore a cancelled lesson
- [x] **LSSN-06**: Headman can mass-cancel lessons for a date range
- [x] **LSSN-07**: Headman can toggle geo-checkin blocking on a specific lesson

### Schedule Viewing

- [x] **VIEW-01**: Any authenticated user can view group schedule for a date range
- [x] **VIEW-02**: Schedule response includes lesson status, room, teacher, subject info

### Status Automation

- [ ] **CRON-01**: Cron transitions planned->active when current time >= lesson start_time (Moscow TZ)
- [ ] **CRON-02**: Cron transitions active->closed when current time >= lesson end_time + 5 min (Moscow TZ)
- [ ] **CRON-03**: Cron catches up missed transitions on service restart
- [x] **CRON-04**: Cron runs every minute with proper timezone handling

### Events

- [ ] **EVNT-01**: System publishes lesson.started event when lesson becomes active
- [ ] **EVNT-02**: System publishes lesson.closed event when lesson becomes closed
- [ ] **EVNT-03**: System publishes lesson.cancelled event when lesson is cancelled
- [ ] **EVNT-04**: Events use @TransactionalEventListener(AFTER_COMMIT) pattern

### gRPC Server

- [ ] **GRPC-01**: GetActiveLesson returns the currently active lesson for a group
- [ ] **GRPC-02**: GetLessonById returns lesson details by ID
- [ ] **GRPC-03**: GetLessonsByGroup returns all lessons for a group in a date range

## Future Requirements

Deferred to later milestones. Tracked but not in current roadmap.

### Attendance Integration (v4.0)

- **ATT-01**: Attendance Service consumes lesson.started/closed/cancelled events
- **ATT-02**: Attendance Service calls GetActiveLesson gRPC for geo-checkin validation

### Caching (v3.x if needed)

- **CACHE-01**: Redis caching for GetActiveLesson gRPC responses
- **CACHE-02**: Redis caching for group schedule queries

## Out of Scope

| Feature | Reason |
|---------|--------|
| Holiday calendar | Requires new table + external data source; headman can mass-cancel instead |
| Substitute teacher per lesson | Requires lesson-level teacher override column; defer to v3.x |
| Room change per lesson | Requires lesson-level room override column; defer to v3.x |
| Exam period scheduling | Different lifecycle from regular lessons; defer to v3.x |
| Teacher conflict detection | Requires cross-group schedule queries; low priority for single-campus |
| Redis caching | Not needed at MVP load (500-5000 students); add if performance degrades |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| LSSN-03 | Phase 10 | Complete |
| CRON-04 | Phase 10 | Complete |
| TMPL-01 | Phase 11 | Complete |
| TMPL-02 | Phase 11 | Complete |
| TMPL-03 | Phase 11 | Complete |
| TMPL-04 | Phase 11 | Complete |
| TMPL-05 | Phase 11 | Complete |
| LSSN-04 | Phase 11 | Complete |
| LSSN-05 | Phase 11 | Complete |
| LSSN-06 | Phase 11 | Complete |
| LSSN-07 | Phase 11 | Complete |
| VIEW-01 | Phase 11 | Complete |
| VIEW-02 | Phase 11 | Complete |
| LSSN-01 | Phase 12 | Pending |
| LSSN-02 | Phase 12 | Pending |
| CRON-01 | Phase 13 | Pending |
| CRON-02 | Phase 13 | Pending |
| CRON-03 | Phase 13 | Pending |
| EVNT-01 | Phase 13 | Pending |
| EVNT-02 | Phase 13 | Pending |
| EVNT-03 | Phase 13 | Pending |
| EVNT-04 | Phase 13 | Pending |
| GRPC-01 | Phase 14 | Pending |
| GRPC-02 | Phase 14 | Pending |
| GRPC-03 | Phase 14 | Pending |

**Coverage:**
- v3.0 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-03-31 — traceability filled by roadmapper*
