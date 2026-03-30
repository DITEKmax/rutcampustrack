# Requirements: RutCampusTrack

**Defined:** 2026-03-30
**Core Value:** Working authentication and authorization perimeter — all downstream services receive validated user context through the Gateway.

## v2.0 Requirements

Requirements for Academic Service milestone. Each maps to roadmap phases.

### User Management

- [ ] **USER-01**: Admin can create user with auto-generated login (student00001/teacher00001) and initial password
- [ ] **USER-02**: Admin can view, update, and soft-delete (archive) users
- [ ] **USER-03**: Admin can assign headman flag to a student in a group
- [ ] **USER-04**: Admin can revoke headman (auto-deactivates all assistants)
- [ ] **USER-05**: Admin can transfer student between groups with reason (history tracked)
- [ ] **USER-06**: Student can view own profile
- [ ] **USER-07**: Student can view group composition (members list)
- [ ] **USER-08**: Teacher can view own assigned subjects and groups

### Groups & Semesters

- [ ] **GSEM-01**: Admin can CRUD groups (name, code, active flag)
- [ ] **GSEM-02**: Admin can CRUD semesters (name, date range)
- [ ] **GSEM-03**: Admin can activate semester (only one active at a time, DB-enforced)
- [ ] **GSEM-04**: Admin can delete semester with confirmation phrase guard

### Subjects & Assignments

- [ ] **SUBJ-01**: Headman can CRUD subjects with type (lecture/practice/lab)
- [ ] **SUBJ-02**: Headman can assign teacher to subject+group (search by employee number)
- [ ] **SUBJ-03**: Headman can remove teacher-subject-group assignment

### Headman Assistants

- [ ] **ASST-01**: Headman can assign assistant with granular permissions
- [ ] **ASST-02**: Headman can revoke assistant
- [ ] **ASST-03**: Headman can update assistant permissions

### Homeworks

- [ ] **HW-01**: Headman can CRUD homeworks (title, description, optional link)
- [ ] **HW-02**: Student can view group homeworks
- [ ] **HW-03**: Student can mark/unmark homework as completed (personal tracker)

### Red Zone Thresholds

- [ ] **THRSH-01**: Admin can set global attendance threshold
- [ ] **THRSH-02**: Headman can set group-level threshold
- [ ] **THRSH-03**: Headman can set subject-level threshold
- [ ] **THRSH-04**: System resolves threshold with most-specific-wins logic

### Admin Dashboard

- [ ] **DASH-01**: Admin can view summary statistics (students, groups, teachers count)

### gRPC Server

- [ ] **GRPC-01**: GetGroup returns group info by ID
- [ ] **GRPC-02**: GetGroupMembers returns active students in a group
- [ ] **GRPC-03**: GetTeacherSubjects returns teacher's subjects with groups
- [ ] **GRPC-04**: IsHeadman checks if user is headman of a group
- [ ] **GRPC-05**: GetActiveSemester returns current active semester
- [ ] **GRPC-06**: GetCampusGeofence returns campus coordinates and radius
- [ ] **GRPC-07**: GetUserById returns user info (display_name, telegram_id)

### Redis Caching

- [ ] **CACHE-01**: Read-heavy gRPC paths cached with configurable TTL
- [ ] **CACHE-02**: Cache invalidated on data mutations (with cascading eviction)

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
| USER-01 | TBD | Pending |
| USER-02 | TBD | Pending |
| USER-03 | TBD | Pending |
| USER-04 | TBD | Pending |
| USER-05 | TBD | Pending |
| USER-06 | TBD | Pending |
| USER-07 | TBD | Pending |
| USER-08 | TBD | Pending |
| GSEM-01 | TBD | Pending |
| GSEM-02 | TBD | Pending |
| GSEM-03 | TBD | Pending |
| GSEM-04 | TBD | Pending |
| SUBJ-01 | TBD | Pending |
| SUBJ-02 | TBD | Pending |
| SUBJ-03 | TBD | Pending |
| ASST-01 | TBD | Pending |
| ASST-02 | TBD | Pending |
| ASST-03 | TBD | Pending |
| HW-01 | TBD | Pending |
| HW-02 | TBD | Pending |
| HW-03 | TBD | Pending |
| THRSH-01 | TBD | Pending |
| THRSH-02 | TBD | Pending |
| THRSH-03 | TBD | Pending |
| THRSH-04 | TBD | Pending |
| DASH-01 | TBD | Pending |
| GRPC-01 | TBD | Pending |
| GRPC-02 | TBD | Pending |
| GRPC-03 | TBD | Pending |
| GRPC-04 | TBD | Pending |
| GRPC-05 | TBD | Pending |
| GRPC-06 | TBD | Pending |
| GRPC-07 | TBD | Pending |
| CACHE-01 | TBD | Pending |
| CACHE-02 | TBD | Pending |
| EVENT-01 | TBD | Pending |
| EVENT-02 | TBD | Pending |
| EVENT-03 | TBD | Pending |

**Coverage:**
- v2.0 requirements: 37 total
- Mapped to phases: 0
- Unmapped: 37 (pending roadmap creation)

---
*Requirements defined: 2026-03-30*
*Last updated: 2026-03-30 after initial definition*
