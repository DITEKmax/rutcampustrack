# Requirements: RutCampusTrack v9.0 — Frontend Unification

**Defined:** 2026-04-08
**Core Value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Milestone Goal:** Fix critical post-v8.0 production frontend bugs and deliver full web cabinets for STUDENT and HEADMAN roles, unified under a single `/login` entry point.
**Brief:** See `.planning/milestones/v9.0-BRIEF.md` for full context, problem analysis, architecture decisions, and constraints.

---

## v9.0 Requirements

Requirements for v9.0 Frontend Unification milestone. Each maps to roadmap phases 49+ (continues from v8.0).

### Infrastructure (nginx routing + deployment)

- [ ] **INFRA-v9-01**: Root URL `https://ruttrack.site/` no longer serves PWA — instead redirects to `/login` (unified entry point for all roles)
- [ ] **INFRA-v9-02**: `https://ruttrack.site/presentation/` serves the landing page (moved from `/landing/`, accessible only by explicit link, NOT shown on first visit)
- [ ] **INFRA-v9-03**: `https://ruttrack.site/app/` serves the React PWA (formerly on `/`)
- [ ] **INFRA-v9-04**: Angular web-panel serves `/login`, `/admin/*`, `/teacher/*`, `/student/*`, `/headman/*` as a single SPA with `baseHref: /`
- [ ] **INFRA-v9-05**: All internal landing links no longer contain `https://t.me/` — replaced with `/login` or removed (currently broken in `frontends/landing/dist/index.html:1029,1107,1306`)
- [ ] **INFRA-v9-06**: v8.0 CI/CD pipeline (GitHub Actions build + deploy) continues passing with no modifications to workflow files
- [ ] **INFRA-v9-07**: All 4 existing frontend Docker images (`rct-pwa-nginx`, `rct-web-panel-nginx`, `rct-mini-app-nginx`, `rct-landing-nginx`) rebuild successfully with new path configurations

### Authentication (unified login)

- [ ] **AUTH-v9-01**: User can log in at `/login` with username + password via existing `POST /api/auth/login` endpoint; access token stored in Angular signal (memory-only per D-06)
- [ ] **AUTH-v9-02**: After successful login, user is routed to their role-specific dashboard: ADMIN → `/admin/dashboard`, TEACHER → `/teacher/dashboard`, STUDENT (not headman) → `/student/dashboard`, STUDENT with is_headman=true → `/headman/dashboard`
- [ ] **AUTH-v9-03**: `AuthService.currentUser` signal reads `role`, `is_headman`, `group_id` claims from JWT access token (JwtService.java:94-96 already includes these)
- [ ] **AUTH-v9-04**: `headmanGuard` allows access only to users with `role=STUDENT && is_headman=true` and additionally grants them access to all `/student/*` routes
- [ ] **AUTH-v9-05**: `studentGuard` allows access to any user with `role=STUDENT` (plain students and headmen alike)
- [ ] **AUTH-v9-06**: Logout button on every dashboard clears tokens, invalidates refresh token on server, and redirects to `/login`
- [ ] **AUTH-v9-07**: Existing 129 web-panel vitest tests continue to pass after `baseHref` migration from `/admin/` to `/`

### Student Web Cabinet

- [ ] **STU-WEB-01**: `/student/dashboard` — overview page: today's schedule, next active lesson, red-zone subject warnings, recent notifications
- [ ] **STU-WEB-02**: `/student/schedule` — week/day navigation, lesson details (subject, time, room, status), visual parity with PWA schedule view
- [ ] **STU-WEB-03**: `/student/checkin` — active lesson card with geo check-in button, GPS capture, STOMP WebSocket real-time status updates (mirrors PWA CHKIN-01..03)
- [ ] **STU-WEB-04**: `/student/homework` — list of homework assignments for student's group with optimistic completion toggle (mirrors PWA HW-01..02)
- [ ] **STU-WEB-05**: `/student/stats` — attendance statistics per subject with red-zone indicators and ng2-charts visualization (mirrors PWA ATT-01..03)
- [ ] **STU-WEB-06**: `/student/notifications` — scrollable log of past notifications (lesson.started, lesson.cancelled, homework.published/updated, attendance.marked confirmations)
- [ ] **STU-WEB-07**: `/student/excuses` — list student's own excuse tickets and "Submit new ticket" form with file upload (files forwarded via Telegram bot pipeline, not stored)
- [ ] **STU-WEB-08**: `/student/late-checkin` — request "I was present, forgot to mark" flow for past `absent` lessons; sends `late_checkin.requested` event to headman
- [ ] **STU-WEB-09**: `/student/profile` — current password change (requires old password, mirrors JS-SYSTEM-04)
- [ ] **STU-WEB-10**: After first successful STUDENT login, web-panel shows non-intrusive banner "Установите RutTrack на главный экран" with a button linking to `/app/` — banner is dismissible, NO forced redirect to PWA

### Headman Web Cabinet

- [ ] **HEAD-WEB-01**: Headman (`role=STUDENT && is_headman=true`) has full access to all `/student/*` routes in addition to `/headman/*`
- [ ] **HEAD-WEB-02**: `/headman/dashboard` — group overview: member count, today's active lesson, pending excuse tickets count, pending late check-in requests count
- [ ] **HEAD-WEB-03**: `/headman/group` — list of students in own group, assistant management (create/remove headman assistants with granular permissions, closes WPAN-13)
- [ ] **HEAD-WEB-04**: `/headman/subjects` — CRUD for subjects taught to own group, assign/reassign teachers to each subject
- [ ] **HEAD-WEB-05**: `/headman/journal` — subject filter → students×lessons matrix grid, mass-mark attendance statuses (present/absent/excused/free_attendance/cancelled)
- [ ] **HEAD-WEB-06**: `/headman/excuses` — approve or reject student excuse tickets (list view with actions)
- [ ] **HEAD-WEB-07**: `/headman/late-checkin` — approve or reject student late check-in requests (list view with actions)
- [ ] **HEAD-WEB-08**: `/headman/stats` — group attendance statistics with configurable red-zone threshold per subject

### PWA HEADMAN Mode

- [ ] **PWA-HEAD-01**: React PWA detects `is_headman=true` claim in JWT and renders an additional "Группа" tab in BottomNav after the existing 4 student tabs
- [x] **PWA-HEAD-02**: All headman web cabinet features (HEAD-WEB-02..HEAD-WEB-08) are available in PWA with mobile-first UI (React + Framer Motion, matches existing PWA patterns)
- [ ] **PWA-HEAD-03**: Existing 63 PWA vitest tests continue to pass after HEADMAN feature addition
- [x] **PWA-HEAD-04**: PWA Service Worker cache strategy extended to cover headman endpoints (read-through cache for group members, teachers, subjects)

### Landing (Presentation Mode)

- [ ] **LAND-v9-01**: Landing page accessible at `/presentation/` only, NOT at site root (current location `/landing/` is deprecated/removed)
- [ ] **LAND-v9-02**: Landing contains GSAP scroll-driven animation section "Как работает система" depicting the full flow (git push → CI/CD → backend microservices → RabbitMQ → WebSocket/Push → user device)
- [ ] **LAND-v9-03**: All "Войти" / "Login" buttons on landing link to `/login` (never to `https://t.me/`)
- [ ] **LAND-v9-04**: Landing remains responsive across 360-1440px viewport widths and supports dark mode via `prefers-color-scheme`
- [ ] **LAND-v9-05**: Landing describes all 4 roles (ADMIN, TEACHER, STUDENT, HEADMAN) with their capabilities — closes documentation gap for HEADMAN role discovery

### Documentation

- [ ] **DOCS-v9-01**: `CLAUDE.md` updated — project status reflects v9.0 in progress, phase table current, URL layout section added
- [ ] **DOCS-v9-02**: `docs/url-layout.md` updated — new path routing table (/, /login, /admin, /teacher, /student, /headman, /app, /presentation)
- [ ] **DOCS-v9-03**: `docs/job-stories.md` extended with JS-STUDENT-WEB-01..10 and JS-HEADMAN-WEB-01..08 stories for web cabinets (currently job stories describe only PWA/Mini App/Telegram flows for students)
- [ ] **DOCS-v9-04**: `.planning/PROJECT.md` updated — v9.0 moves to Shipped Milestones section after completion

---

## Future Requirements (v10.0+)

Deferred to future releases. Tracked but not in v9.0 roadmap.

### OTP Login UI

- **AUTH-OTP-01**: User can log in via OTP code instead of password. Backend already supports: `POST /api/auth/otp/request` (rate-limited, generates code) and `POST /api/auth/otp/verify` (returns `TokenResponse` JWT pair). Only UI missing.

### PWA for TEACHER and ADMIN

- **PWA-TCH-01..NN**: Teacher mobile client (journal read-only, stats on the go) — `docs/design-decisions.md §3` fixes this as target architecture but defers to future
- **PWA-ADM-01..NN**: Admin mobile client (user/group/semester management on mobile)

### Other deferred

- **Excuse file storage backend**: Currently files are forwarded via Telegram bot pipeline without being stored. Storing attachments server-side would require dedicated file service. Out of scope.
- **PDF/Excel export of journals and statistics**: Would require dedicated backend report service (beyond current REST endpoints).
- **Live broker-level verification of STOMP group isolation (WS-07)**: Deferred from v5.0.

---

## Out of Scope

Explicitly excluded from v9.0 to prevent scope creep.

| Feature | Reason |
|---------|--------|
| OTP login UI | Backend ready (`/auth/otp/verify` returns JWT pair), but v9.0 focuses on routing + roles. Deferred to v10.0 as AUTH-OTP-01 |
| Telegram Mini App changes | Mini App stable and shipped in v7.0; user explicitly requested "don't touch" |
| Telegram Bot changes | Not in v9.0 scope |
| Native iOS/Android apps | Web-first strategy; no appetite for native builds |
| PWA for TEACHER and ADMIN | Per `design-decisions.md §3` "all roles get PWA eventually"; v9.0 focuses on STUDENT + HEADMAN only. Deferred to v10.0+ |
| Excuse attachment server-side storage | Files forwarded via Telegram bot (CLAUDE.md business rule); no storage service needed |
| PDF/Excel export | Requires dedicated report generation backend service |
| Backend `UserRole` enum extension (HEADMAN as distinct role) | Too disruptive — would cascade through 5 services. HEADMAN stays as `is_headman` boolean; JWT already carries this claim |
| Search/filter enhancements in admin user list | Already shipped in v7.0 (WPAN-09), no changes needed |
| Dark mode toggle in web cabinets | Angular Material M3 already supports system dark mode; explicit toggle deferred |
| Offline-first for web cabinets | PWA handles offline use case; Angular web cabinet stays online-first |

---

## Traceability

Which phases cover which requirements.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-v9-01 | Phase 49 | Pending |
| INFRA-v9-02 | Phase 49 | Pending |
| INFRA-v9-03 | Phase 49 | Pending |
| INFRA-v9-04 | Phase 50 | Pending |
| INFRA-v9-05 | Phase 49 | Pending |
| INFRA-v9-06 | Phase 49 | Pending |
| INFRA-v9-07 | Phase 49 | Pending |
| AUTH-v9-01 | Phase 50 | Pending |
| AUTH-v9-02 | Phase 50 | Pending |
| AUTH-v9-03 | Phase 50 | Pending |
| AUTH-v9-04 | Phase 50 | Pending |
| AUTH-v9-05 | Phase 50 | Pending |
| AUTH-v9-06 | Phase 50 | Pending |
| AUTH-v9-07 | Phase 50 | Pending |
| STU-WEB-01 | Phase 51 | Pending |
| STU-WEB-02 | Phase 51 | Pending |
| STU-WEB-03 | Phase 51 | Pending |
| STU-WEB-04 | Phase 52 | Pending |
| STU-WEB-05 | Phase 52 | Pending |
| STU-WEB-06 | Phase 52 | Pending |
| STU-WEB-07 | Phase 53 | Pending |
| STU-WEB-08 | Phase 53 | Pending |
| STU-WEB-09 | Phase 52 | Pending |
| STU-WEB-10 | Phase 53 | Pending |
| HEAD-WEB-01 | Phase 54 | Pending |
| HEAD-WEB-02 | Phase 54 | Pending |
| HEAD-WEB-03 | Phase 54 | Pending |
| HEAD-WEB-04 | Phase 54 | Pending |
| HEAD-WEB-05 | Phase 55 | Pending |
| HEAD-WEB-06 | Phase 55 | Pending |
| HEAD-WEB-07 | Phase 55 | Pending |
| HEAD-WEB-08 | Phase 55 | Pending |
| PWA-HEAD-01 | Phase 56 | Pending |
| PWA-HEAD-02 | Phase 56 | Complete |
| PWA-HEAD-03 | Phase 56 | Pending |
| PWA-HEAD-04 | Phase 56 | Complete |
| LAND-v9-01 | Phase 49 | Pending |
| LAND-v9-02 | Phase 57 | Pending |
| LAND-v9-03 | Phase 49 | Pending |
| LAND-v9-04 | Phase 57 | Pending |
| LAND-v9-05 | Phase 57 | Pending |
| DOCS-v9-01 | Phase 57 | Pending |
| DOCS-v9-02 | Phase 57 | Pending |
| DOCS-v9-03 | Phase 57 | Pending |
| DOCS-v9-04 | Phase 57 | Pending |

**Coverage:**
- v9.0 requirements: 45 total (7 INFRA + 7 AUTH + 10 STU-WEB + 8 HEAD-WEB + 4 PWA-HEAD + 5 LAND + 4 DOCS)
- Mapped to phases: 45/45
- Unmapped: 0

---

*Requirements defined: 2026-04-08*
*Last updated: 2026-04-08 — traceability populated after roadmap creation*
