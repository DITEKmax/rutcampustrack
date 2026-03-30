# Requirements: RutCampusTrack

**Defined:** 2026-03-30
**Core Value:** Working authentication and authorization perimeter — all downstream services receive validated user context through the Gateway.

## v2.0 Requirements

Requirements for Academic Service milestone. Each maps to roadmap phases.

### User Management

- [x] **USER-01**: Admin can create user with auto-generated login (student00001/teacher00001) and initial password
- [x] **USER-02**: Admin can view, update, and soft-delete (archive) users
- [x] **USER-03**: Admin can assign headman flag to a student in a group
- [x] **USER-04**: Admin can revoke headman (auto-deactivates all assistants)
- [x] **USER-05**: Admin can transfer student between groups with reason (history tracked)
- [x] **USER-06**: Student can view own profile
- [x] **USER-07**: Student can view group composition (members list)
- [x] **USER-08**: Teacher can view own assigned subjects and groups

### Groups & Semesters

- [x] **GSEM-01**: Admin can CRUD groups (name, code, active flag)
- [x] **GSEM-02**: Admin can CRUD semesters (name, date range)
- [x] **GSEM-03**: Admin can activate semester (only one active at a time, DB-enforced)
- [x] **GSEM-04**: Admin can delete semester with confirmation phrase guard

### Subjects & Assignments

- [x] **SUBJ-01**: Headman can CRUD subjects with type (lecture/practice/lab)
- [x] **SUBJ-02**: Headman can assign teacher to subject+group (search by employee number)
- [x] **SUBJ-03**: Headman can remove teacher-subject-group assignment

### Headman Assistants

- [x] **ASST-01**: Headman can assign assistant with granular permissions
- [x] **ASST-02**: Headman can revoke assistant
- [x] **ASST-03**: Headman can update assistant permissions

### Homeworks

- [x] **HW-01**: Headman can CRUD homeworks (title, description, optional link)
- [x] **HW-02**: Student can view group homeworks
- [x] **HW-03**: Student can mark/unmark homework as completed (personal tracker)

### Red Zone Thresholds

- [x] **THRSH-01**: Admin can set global attendance threshold
- [x] **THRSH-02**: Headman can set group-level threshold
- [x] **THRSH-03**: Headman can set subject-level threshold
- [x] **THRSH-04**: System resolves threshold with most-specific-wins logic

### Admin Dashboard

- [x] **DASH-01**: Admin can view summary statistics (students, groups, teachers count)

### gRPC Server

- [x] **GRPC-01**: GetGroup returns group info by ID
- [x] **GRPC-02**: GetGroupMembers returns active students in a group
- [x] **GRPC-03**: GetTeacherSubjects returns teacher's subjects with groups
- [x] **GRPC-04**: IsHeadman checks if user is headman of a group
- [x] **GRPC-05**: GetActiveSemester returns current active semester
- [x] **GRPC-06**: GetCampusGeofence returns campus coordinates and radius
- [x] **GRPC-07**: GetUserById returns user info (display_name, telegram_id)

### Redis Caching

- [x] **CACHE-01**: Read-heavy gRPC paths cached with configurable TTL
- [x] **CACHE-02**: Cache invalidated on data mutations (with cascading eviction)

### RabbitMQ Events

- [ ] **EVENT-01**: group.updated published on group composition changes
- [ ] **EVENT-02**: semester.archived published on semester deactivation
- [ ] **EVENT-03**: homework.published / homework.updated published on homework changes

## Future Requirements

### Schedule & Attendance (v3.0)

- **SCHED-01**: Headman can CRUD schedule items with auto lesson generation
- **SCHED-02**: System transitions lesson status (planned → active → closed)
- **ATT-01**: Student can geo-checkin to active lesson
- **ATT-02**: Headman can mark attendance manually

### Notifications (v4.0)

- **NOTIF-01**: WebSocket push notifications for attendance events
- **NOTIF-02**: Telegram bot for attendance reminders and excuse file forwarding

## Out of Scope

| Feature | Reason |
|---------|--------|
| Attendance recording/reporting | Belongs to Attendance Service (Phase 3) |
| Schedule creation/lesson generation | Belongs to Schedule Service (Phase 3) |
| Email/SMS credential delivery | Telegram is the delivery channel (Notification Bot) |
| File upload/storage | Excuse files forwarded via Telegram, not stored |
| Grade tracking | Not part of attendance system |
| Bulk CSV user import | Manual creation sufficient for v2.0 |
| Campus settings admin endpoint | Single row seeded via Flyway; defer to later if needed |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| USER-01 | Phase 5 + Phase 6 | Complete |
| USER-02 | Phase 5 + Phase 6 | Complete |
| USER-03 | Phase 5 + Phase 6 | Complete |
| USER-04 | Phase 5 + Phase 6 | Complete |
| USER-05 | Phase 5 + Phase 6 | Complete |
| USER-06 | Phase 6 | Complete |
| USER-07 | Phase 6 | Complete |
| USER-08 | Phase 6 | Complete |
| GSEM-01 | Phase 5 + Phase 6 | Complete |
| GSEM-02 | Phase 5 + Phase 6 | Complete |
| GSEM-03 | Phase 5 + Phase 6 | Complete |
| GSEM-04 | Phase 5 + Phase 6 | Complete |
| SUBJ-01 | Phase 5 + Phase 6 | Complete |
| SUBJ-02 | Phase 5 + Phase 6 | Complete |
| SUBJ-03 | Phase 5 + Phase 6 | Complete |
| ASST-01 | Phase 5 + Phase 6 | Complete |
| ASST-02 | Phase 5 + Phase 6 | Complete |
| ASST-03 | Phase 5 + Phase 6 | Complete |
| HW-01 | Phase 5 + Phase 6 | Complete |
| HW-02 | Phase 6 | Complete |
| HW-03 | Phase 6 | Complete |
| THRSH-01 | Phase 5 + Phase 6 | Complete |
| THRSH-02 | Phase 5 + Phase 6 | Complete |
| THRSH-03 | Phase 5 + Phase 6 | Complete |
| THRSH-04 | Phase 6 | Complete |
| DASH-01 | Phase 6 | Complete |
| GRPC-01 | Phase 7 | Complete |
| GRPC-02 | Phase 7 | Complete |
| GRPC-03 | Phase 7 | Complete |
| GRPC-04 | Phase 7 | Complete |
| GRPC-05 | Phase 7 | Complete |
| GRPC-06 | Phase 7 | Complete |
| GRPC-07 | Phase 7 | Complete |
| CACHE-01 | Phase 8 | Complete |
| CACHE-02 | Phase 8 | Complete |
| EVENT-01 | Phase 9 | Pending |
| EVENT-02 | Phase 9 | Pending |
| EVENT-03 | Phase 9 | Pending |

**Coverage:**
- v2.0 requirements: 37 total
- Mapped to phases: 37
- Unmapped: 0

---
*Requirements defined: 2026-03-30*
*Last updated: 2026-03-30 after roadmap creation*
