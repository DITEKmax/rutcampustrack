# Feature Landscape: Academic Service

**Domain:** University management — academic structure CRUD for a microservice attendance tracking system
**Researched:** 2026-03-30
**Project:** RutCampusTrack v2.0

---

## Context: What Already Exists

These features are DONE and must not be re-implemented:

| Feature | Where | Notes |
|---------|-------|-------|
| Login (login + password) | Auth Service | BCrypt, JWT pair |
| JWT refresh / logout | Auth Service | Refresh token rotation in Redis |
| OTP flow | Auth Service | Telegram telegram_id, 6-digit, 120s TTL |
| Change password | Auth Service | Requires current password |
| JWT gateway filter | API Gateway | Header injection: X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman |
| Route groups | API Gateway | 5 route groups, public routes whitelisted |
| DB schema (12 tables) | Flyway V1 | academic_db fully migrated |
| Seed test data | Flyway V2 | admin/teacher/student with password "password" |
| Enum converters | academic-api-contract | LowercaseEnumConverter with autoApply |

---

## Table Stakes

Features the system cannot ship Phase 2 without. Missing = downstream services (Schedule, Attendance) cannot function.

| Feature | Why Required | Complexity | Dependencies |
|---------|--------------|------------|--------------|
| **Create user with auto-login generation** | Auth reads `users`; downstream uses X-User-Id | Medium | Flyway V1 users table, sequence logic |
| **Read/update/deactivate user (soft delete)** | Status management, header injection relies on active users | Low | users.status enum |
| **CRUD groups** | All other entities reference group_id; gRPC GetGroup must return real data | Low | groups table exists |
| **CRUD semesters** | teacher_subject_groups, homeworks, schedule_items are all semester-scoped | Low | semesters table with DB constraint |
| **Activate semester (single active enforcement)** | gRPC GetActiveSemester called by Schedule and Attendance on every checkin | Low | EXCLUDE constraint already in DB |
| **Delete semester with confirmation phrase** | Prevents accidental deletion of active semester with linked data | Low | Application-layer guard only |
| **CRUD subjects with type (lecture/practice/lab)** | Schedule Service cannot create lessons without subject_id; gRPC GetTeacherSubjects returns subject type | Low | subjects table, SubjectType enum |
| **Teacher-subject-group assignment** | gRPC GetTeacherSubjects is called by Schedule and Attendance to authorize teacher views | Medium | teacher_subject_groups, semester-scoped |
| **Assign/revoke headman** | is_headman flag drives X-Is-Headman header; headman features gated on this | Low | users.is_headman field |
| **gRPC server: GetGroup** | Schedule Service validates group_id on schedule_item creation | Low | Groups entity |
| **gRPC server: GetGroupMembers** | Attendance auto-absent loop iterates over group members | Medium | users + group_id filter |
| **gRPC server: GetTeacherSubjects** | Report module authorization check | Low | teacher_subject_groups join |
| **gRPC server: IsHeadman** | Attendance manual marking permission check | Low | users.is_headman |
| **gRPC server: GetActiveSemester** | Schedule Service lesson generation start/end dates | Low | semesters.is_active |
| **gRPC server: GetCampusGeofence** | Attendance checkin radius validation — called on every geo-checkin | Low | campus_settings (1 row) |
| **gRPC server: GetUserById** | Notification Bot needs display_name + telegram_id to send messages | Low | users entity |
| **Redis cache for gRPC read paths** | gRPC called on every lesson start and every geo-checkin; without cache = hot-spot | Medium | Spring Cache + Redis |

---

## Differentiators

Features that go beyond baseline university LMS behavior and make this system distinct.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Auto-login generation with sequential numbering** | `student00001` format is memorable, predictable, usable in Telegram-only environments where email is unavailable | Medium | Needs sequence: find MAX(login) where role='student', format with zero-padding to 5 digits; `student`, `teacher`, `admin` reserved |
| **initial_password stored until first change** | Enables Notification Bot to send credentials on /start without a separate delivery mechanism | Low | Set initial_password on create; nullify on change-password event |
| **Student group transfer with history** | Audit trail for accreditation; re-join statistics are correct because history tracks joined_at/left_at | Medium | student_group_history insert + users.group_id update atomically |
| **Headman assistant system with granular permissions** | Headman can delegate specific sub-tasks (e.g., mark attendance only) without giving full headman access | Medium | headman_assistants.permissions is VARCHAR(64)[] array; Gateway does not know about assistant permissions — enforcement is in Academic Service and downstream |
| **Auto-revoke all assistants on headman change** | Prevents orphaned permissions when a new headman is elected | Low | On revoke/assign headman: UPDATE headman_assistants SET is_active=false WHERE group_id=? |
| **Red zone thresholds at 3 levels (global/group/subject)** | Admin sets global floor; headman can tighten per-group or per-subject; most specific wins | Medium | attendance_thresholds table: NULL group_id + NULL subject_id = global; NULL subject_id + group_id = group-level; both set = subject-level |
| **Homework completion personal tracker** | Student marks their own homework done; does not affect attendance; lightweight personal accountability tool | Low | homework_completions UNIQUE(homework_id, student_id); simple POST/DELETE |
| **RabbitMQ events for group/semester/homework changes** | Downstream services (Notification Bot/Web) subscribe and push notifications without polling | Medium | group.updated, semester.archived, homework.published, homework.updated |
| **Admin dashboard summary** | Quick operational view: total students, groups, active teachers — replaces manual DB queries | Low | COUNT queries only; no caching needed initially |

---

## Anti-Features

Features to explicitly NOT build in Phase 2.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Attendance recording or reporting** | Belongs to Attendance Service (Phase 3); Academic Service owns structure, not events | Academic Service exposes gRPC so Attendance can read group members |
| **Schedule creation or lesson generation** | Belongs to Schedule Service (Phase 3) | Academic Service exposes gRPC GetActiveSemester for Schedule to use |
| **Email/SMS delivery of credentials** | Out of scope; Telegram is the delivery channel | Bot reads initial_password via /start and sends it via Telegram |
| **File upload or storage** | Excuse ticket files are forwarded via Telegram, not stored; no S3/MinIO in this phase | Files handled in Phase 3 Attendance Service |
| **Grade/mark recording** | Not in scope for this university system | System tracks attendance status only |
| **Timetable conflict detection** | Schedule Service handles slot uniqueness at the DB level; Academic does not need to know | UNIQUE constraint in schedule_items table (Phase 3) |
| **Password reset flow in Academic Service** | password_reset_tokens table exists but the OTP reset flow is Auth Service's responsibility | Auth Service already handles OTP |
| **Bulk import via CSV/Excel** | Complex parsing, error handling, rollback; not needed for MVP with a few hundred users | Admin creates users one at a time or small batch via REST API |
| **Soft delete on groups and semesters** | Groups and semesters have referential integrity; deactivation (is_active=false) is sufficient | Use is_active flag, never physical delete of referenced rows |

---

## Feature Dependencies

```
Semester (active) → teacher_subject_groups (semester-scoped)
Semester (active) → homeworks (semester-scoped)
Semester (active) → gRPC GetActiveSemester (Schedule Service depends on this)

Group → users.group_id
Group → headman_assistants.group_id
Group → teacher_subject_groups.group_id
Group → homeworks.group_id

User (role=teacher) → teacher_subject_groups.teacher_id
User (is_headman=true) → headman_assistants.assigned_by
User (role=student, group_id set) → headman_assistants.student_id

Subject → teacher_subject_groups.subject_id
Subject → homeworks.subject_id
Subject → attendance_thresholds.subject_id

Homework → homework_completions.homework_id

Headman assignment → assistant auto-revoke (business rule dependency)
```

**Cross-service dependencies (gRPC consumed by Phase 3+):**
```
Academic gRPC server → Schedule Service (Phase 3): GetGroup, GetActiveSemester, GetTeacherSubjects
Academic gRPC server → Attendance Service (Phase 3): GetGroupMembers, GetCampusGeofence, IsHeadman, GetUserById
Academic gRPC server → Notification Bot (Phase 4): GetGroupMembers, GetUserById
```

---

## MVP Recommendation

Phase 2 must deliver all Table Stakes features. The Academic Service is a prerequisite for every subsequent phase.

**Deliver in Phase 2:**

1. Full CRUD: users, groups, semesters, subjects, teacher_subject_groups — these are the structural backbone
2. gRPC server (all 7 RPCs) — Schedule and Attendance are blocked without it
3. Redis caching on all 5 cache-keys — gRPC is called on hot paths (every checkin, every lesson transition)
4. Headman assignment/revoke with assistant auto-revoke
5. Student group transfer with history
6. Headman assistant management with granular permissions
7. Red zone threshold configuration (all 3 levels)
8. Homework CRUD + homework_completions tracker
9. RabbitMQ event publishing (group.updated, semester.archived, homework.*)
10. Admin dashboard summary (counts only)

**Defer to later phases:**
- Campus geofence update API (1-row table, seed data already set; can be a simple admin endpoint added in Phase 3)
- Password reset token cleanup (tokens expire naturally; no batch job needed yet)
- Bulk user import (manual create sufficient for MVP user count)

---

## Endpoint Inventory by Role

### ADMIN endpoints

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| POST | /api/academic/users | Create user, auto-generate login/password | Medium |
| GET | /api/academic/users | List users with pagination + filters | Low |
| GET | /api/academic/users/{id} | Get user by ID | Low |
| PUT | /api/academic/users/{id} | Full update (display_name, email, etc.) | Low |
| PATCH | /api/academic/users/{id}/status | Change status (active/expelled/suspended/archived) | Low |
| POST | /api/academic/groups | Create group | Low |
| GET | /api/academic/groups | List groups | Low |
| GET | /api/academic/groups/{id} | Get group | Low |
| PUT | /api/academic/groups/{id} | Update group name/code | Low |
| PATCH | /api/academic/groups/{id}/deactivate | Deactivate group | Low |
| POST | /api/academic/groups/{id}/headman | Assign headman to group | Low |
| DELETE | /api/academic/groups/{id}/headman | Revoke headman | Low |
| POST | /api/academic/students/{id}/transfer | Transfer student to another group | Medium |
| POST | /api/academic/semesters | Create semester | Low |
| GET | /api/academic/semesters | List semesters | Low |
| GET | /api/academic/semesters/{id} | Get semester | Low |
| PATCH | /api/academic/semesters/{id}/activate | Activate semester (deactivates current active) | Low |
| DELETE | /api/academic/semesters/{id} | Delete with confirmation phrase | Low |
| GET | /api/academic/dashboard | Summary statistics | Low |

### HEADMAN endpoints

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| POST | /api/academic/subjects | Create subject | Low |
| GET | /api/academic/subjects | List subjects (group-scoped) | Low |
| PUT | /api/academic/subjects/{id} | Update subject | Low |
| DELETE | /api/academic/subjects/{id} | Delete subject | Low |
| POST | /api/academic/assignments | Assign teacher to subject+group+semester | Medium |
| DELETE | /api/academic/assignments/{id} | Remove teacher assignment | Low |
| GET | /api/academic/teachers/search | Search teacher by employee_number | Low |
| POST | /api/academic/assistants | Assign assistant with permissions | Medium |
| GET | /api/academic/assistants | List assistants for group | Low |
| PATCH | /api/academic/assistants/{id}/permissions | Update assistant permissions | Low |
| DELETE | /api/academic/assistants/{id} | Revoke assistant | Low |
| POST | /api/academic/homeworks | Publish homework | Low |
| GET | /api/academic/homeworks | List homeworks (group + subject filter) | Low |
| PUT | /api/academic/homeworks/{id} | Update homework (triggers homework.updated event) | Low |
| DELETE | /api/academic/homeworks/{id} | Delete homework | Low |
| POST | /api/academic/thresholds | Set threshold (group or subject level) | Low |
| GET | /api/academic/thresholds | Get thresholds for group | Low |
| PUT | /api/academic/thresholds/{id} | Update threshold | Low |

### STUDENT endpoints

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| GET | /api/academic/me | Own profile | Low |
| GET | /api/academic/my-group | Group composition | Low |
| GET | /api/academic/my-group/homeworks | Homeworks for own group | Low |
| POST | /api/academic/homeworks/{id}/complete | Mark homework done | Low |
| DELETE | /api/academic/homeworks/{id}/complete | Unmark homework done | Low |

### TEACHER endpoints

| Method | Path | Description | Complexity |
|--------|------|-------------|------------|
| GET | /api/academic/my-subjects | Own subject+group assignments | Low |

---

## Complexity Assessment

| Area | Complexity | Reason |
|------|------------|--------|
| Auto-login generation | Medium | Needs concurrent-safe sequence; MAX(login)+1 has race condition under parallel inserts; use DB sequence or table-level lock |
| Semester activation | Low-Medium | DB EXCLUDE constraint already enforces single active; app logic must deactivate previous active before activating new |
| Group transfer | Medium | Atomic: close history row (left_at=today), open new row, update users.group_id; invalidate group member cache keys for both groups |
| Threshold resolution | Medium | Must implement 3-level lookup: subject-specific > group-specific > global; UI shows effective threshold |
| gRPC server with cache invalidation | Medium | 5 cache keys, invalidation on each mutating operation, TTLs already defined in database-schema.md |
| Headman assistant permissions enforcement | Medium | Gateway injects X-Is-Headman but not assistant permissions; Academic Service and downstream must check headman_assistants table via service layer or a separate gRPC call |
| RabbitMQ event publishing | Low-Medium | fanout exchange `rut-uit.events` already in architecture; 3-4 event types; straightforward fire-and-forget |

---

## Sources

- `.planning/PROJECT.md` — milestone goals, active requirements list (HIGH confidence — primary source)
- `docs/phases-plan.md` — Phase 2 detailed specification (HIGH confidence — authoritative spec)
- `docs/job-stories.md` — business rules for all roles (HIGH confidence — product requirements)
- `docs/database-schema.md` — table structure, constraints, Redis keys (HIGH confidence — DB contract)
- `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — actual migrated schema (HIGH confidence — ground truth)
- `proto/academic.proto` — all 7 gRPC RPC signatures (HIGH confidence — contract file)
- `services/academic-service/academic-api-contract/src/main/java/.../enums/AssistantPermission.java` — 5 permission values (HIGH confidence — contract code)
