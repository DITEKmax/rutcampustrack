# Feature Research

**Domain:** University attendance tracking — v7.0 frontends (Telegram Mini App, Angular Web Panel, Landing Page)
**Researched:** 2026-04-06
**Confidence:** HIGH (backend APIs fully defined, existing PWA provides clear patterns, job stories documented)

---

## Context: What Already Exists (Must Not Re-Implement)

The backend is complete and all APIs are operational. The React PWA «RutTrack» already covers:
- Student login (JWT/httpOnly cookies), schedule view, geo check-in, attendance stats, homework list, Web Push, A2HS, offline shell

v7.0 adds three new frontends that consume the same backend APIs. Features below are scoped exclusively to what is NEW in these three frontends.

---

## Application 1: Telegram Mini App (React, student-facing)

The Mini App runs inside Telegram, opened from bot buttons or the bot menu. Authentication is via Telegram's `initData` (HMAC-SHA256 signed by bot secret) — no separate login screen needed once the student links their account via `/start`.

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Backend Dependency |
|---------|--------------|------------|-------------------|
| Seamless auth via initData (no login form) | Mini Apps never show a login screen — Telegram identity IS the credential | MEDIUM | Auth Service: new endpoint to exchange initData for JWT; bot already links telegram_id (v5.0 done) |
| Today's schedule view | First thing a student checks before/during class | LOW | Schedule Service GET /api/schedule/lessons (date range) — already built |
| Geo check-in button | Core purpose of the Mini App per JS-STUDENT-01 | MEDIUM | Attendance Service POST /api/attendance/check-in — already built |
| Check-in success/failure feedback | Students need instant confirmation; silent failure is unacceptable | LOW | HTTP response + optimistic UI |
| Attendance stats per subject | Students want to know if they are in the red zone (JS-STUDENT-07) | LOW | Attendance Service GET /api/reports/student-stats — already built |
| Bottom navigation (Schedule / Check-in / Stats) | Standard Mini App navigation pattern; users expect tab-based navigation | LOW | No backend needed |
| Telegram native theme colors | Mini App must respect Telegram's color scheme (light/dark, OLED) | LOW | useThemeParams from tma.js SDK |
| MainButton integration | Telegram's native bottom action button for primary actions (check-in) | LOW | tma.js SDK useMainButton |
| Back button wiring | Telegram provides a native BackButton — must be wired to app navigation | LOW | tma.js SDK useBackButton |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Inline deep link from bot check-in button | One-tap from Telegram bot notification directly into Mini App at check-in screen | LOW | Bot already sends inline button (NOTIF-01 done v5.0); Mini App needs /checkin route |
| Homework list (read-only) | Students see assigned homework without leaving Telegram | LOW | Academic Service GET /api/academic/homeworks — built |
| Haptic feedback on check-in | Makes geo check-in feel native on iOS/Android | LOW | window.Telegram.WebApp.HapticFeedback — one-liner |
| Late check-in request | JS-STUDENT-06: deferred; no competing Mini App has it | HIGH | Blocked — event publisher not yet built (NOTIF-08 partial); defer to v7.x |
| Excuse ticket submission | JS-STUDENT-03..05: student picks reason, no file upload needed for MVP | HIGH | Blocked — same event publisher dependency; defer to v7.x |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Full login form with password | Fallback if initData auth fails | Defeats the purpose of the Mini App; creates two auth paths to maintain | Show error screen: "Link your account via /start in the bot" |
| Push notification management (subscribe/unsubscribe) | Students want control | Web Push does not work inside Telegram's WebView | Keep push settings in the PWA only |
| Schedule template creation / lesson management | Headman features feel natural to include | Mini App is student-facing; headman features belong in PWA or Web Panel | Headman uses PWA or Web Panel |
| PDF/Excel export | Seems useful | Useless on mobile inside Telegram; large payloads | Link to Web Panel for exports |
| Offline mode / Service Worker | Seems useful for reliability | Telegram WebView does not support Service Workers reliably | Show toast on network error; rely on TanStack Query cache |

---

## Application 2: Web Panel (Angular, teacher + admin facing)

Desktop-first. Two distinct role contexts: TEACHER (read-only journal/stats) and ADMIN (full CRUD management). Headman features exist but are lower priority since the PWA covers headman workflows on mobile.

### Table Stakes (Users Expect These)

#### Teacher View

| Feature | Why Expected | Complexity | Backend Dependency |
|---------|--------------|------------|-------------------|
| Login form (username + password) | Teachers have no Telegram identity in the system (JS-TEACHER-05) | LOW | Auth Service POST /api/auth/login — built |
| Subject + group filter dropdowns | JS-TEACHER-01: teacher sees only their own groups and subjects | LOW | Academic Service GET /api/academic/teacher-subjects — built |
| Attendance journal grid (students x lesson dates, status cells б/н/у/сп) | JS-TEACHER-03: core deliverable; read-only table | HIGH | Attendance Service GET /api/attendance/journal — built |
| Attendance stats per subject/group | JS-TEACHER-06: % attendance for comparison between groups | MEDIUM | Attendance Service GET /api/reports/student-stats — built |
| Token refresh / session persistence | Teacher should not be logged out mid-session | LOW | Auth Service POST /api/auth/refresh — built |
| Logout | Standard security expectation | LOW | Auth Service POST /api/auth/logout — built |

#### Admin View

| Feature | Why Expected | Complexity | Backend Dependency |
|---------|--------------|------------|-------------------|
| Admin dashboard with summary stats (users/groups/semesters) | JS-ADMIN-11: first screen after login | LOW | Academic Service GET /api/academic/dashboard — built |
| User list with search/filter (by role, group, status) | JS-ADMIN-01..04: user management is the core admin task | MEDIUM | Academic Service GET /api/academic/users — built |
| Create user (ADMIN, TEACHER, STUDENT) with auto-generated login | JS-ADMIN-01: system generates studentXXXXX / teacherXXXXX | MEDIUM | Academic Service POST /api/academic/users — built |
| Edit user (status change: active/expelled/suspended/archived, group assignment) | JS-ADMIN-02..03: expulsion, suspension, transfer | MEDIUM | Academic Service PATCH /api/academic/users/{id} — built |
| Soft delete user (archive) | JS-ADMIN-04 | LOW | Academic Service DELETE /api/academic/users/{id} — built |
| Group list and CRUD | JS-ADMIN-05: groups named e.g. ИВТ-21-1 | LOW | Academic Service /api/academic/groups — built |
| Assign / revoke headman | JS-ADMIN-06..07: toggle is_headman on a student | LOW | Academic Service PATCH /api/academic/groups/{id}/headman — built |
| Student transfer between groups | JS-ADMIN-03: with reason, history preserved | MEDIUM | Academic Service POST /api/academic/users/{id}/transfer — built |
| Semester list and CRUD | JS-ADMIN-08..09: create, activate (only one active at a time), deactivate | MEDIUM | Academic Service /api/academic/semesters — built |
| Semester delete with confirmation phrase | JS-ADMIN-10: type semester name to confirm deletion (GitHub-style) | LOW | Academic Service DELETE /api/academic/semesters/{id} — built |
| Role-based route guards | ADMIN routes blocked for TEACHER; TEACHER routes show read-only view | MEDIUM | JWT role claim from Auth Service |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Real-time STOMP WebSocket notifications in panel | JS-SYSTEM-09: lesson events, excuse alerts arrive live without page refresh | HIGH | notification-web STOMP built (v5.0); Angular needs SockJS + @stomp/stompjs client |
| Red zone student list per group | JS-ADMIN-12: students below threshold for decanat reporting | MEDIUM | Attendance + Academic APIs both built; cross-service join on frontend |
| Red zone threshold configuration UI | Global/group/subject override chain per JS-ADMIN-12 context | MEDIUM | Academic Service campus_settings + group_settings + subject_settings endpoints — built |
| Average attendance per group (admin dashboard) | JS-ADMIN-12: fast overview without export | MEDIUM | Attendance stats API built; aggregate on frontend |
| Headman panel within Web Panel | Headman can mark attendance and manage excuses from desktop | HIGH | Attendance Service manual marking built; headman auth via JWT role check |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Teacher can edit attendance | Teachers ask for override capability | Violates JS-TEACHER-04 business rule; creates audit trail problems | Headman is the only role that marks; teachers see read-only journal |
| Bulk CSV user import | Admins want to upload a spreadsheet | Out of scope per PROJECT.md (manual creation sufficient for now) | Use Create User form; admin can batch-create via API script |
| PDF/Excel export | JS-TEACHER-07 / JS-ADMIN-13 request it | No backend PDF/Excel generation exists | Defer to v7.x; mark as future enhancement |
| Real-time attendance marking by teacher during class | Teachers want to mark during class | Role violation; architectural complexity | Teachers observe only; headman marks via PWA |
| Angular Material full design system | Consistent UI | Overkill for solo-developer project; increases bundle and setup time | TailwindCSS + Angular CDK, or PrimeNG (smaller API surface) |

---

## Application 3: Landing Page (HTML + CSS)

Static marketing/info page. No login, no API calls. Target audience: students, teachers, and university administrators evaluating the system.

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Hero section (product name, tagline, CTA) | First thing a visitor sees; must communicate the product in 5 seconds | LOW | CTA links to PWA install or bot /start |
| Feature highlights (3-4 key features with icons) | Visitors need to understand what the system does before exploring | LOW | Geo check-in, Telegram integration, schedule, stats |
| Role overview (Student / Teacher / Admin) | Different visitors have different questions; address each role | LOW | Cards or tabs per role |
| "How it works" 3-step flow | Onboarding flow overview: install app, link Telegram, check in | LOW | Visual step diagram |
| Screenshots / mockup section | Visitors want to see the UI before committing | LOW | PWA screenshots + Mini App screenshots |
| Contact / footer with links | Basic credibility; navigation | LOW | GitHub link, developer info, university affiliation |
| Mobile-responsive layout | Students will visit on phone | MEDIUM | Flexbox/Grid; no framework needed |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Dark mode support | Students and developers prefer dark mode | LOW | CSS prefers-color-scheme media query |
| "Open in Telegram" deep link button | Direct CTA to launch the bot | LOW | https://t.me/{botname} link |
| "Install PWA" instructions | Students can install RutTrack from the landing | LOW | Links to PWA URL; iOS instructions modal |
| Live animated stats counter (students tracked, lessons recorded) | Social proof; makes the system feel active | LOW | CSS counter animation or small vanilla JS; purely cosmetic |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Login form on landing | Convenience — one less click | Mixes marketing and app concerns; CORS complexity for static page | Link to Angular web panel or PWA login |
| JavaScript framework (React/Vue) | Seems easier to develop | Overkill for a static page; adds build pipeline; violates "HTML+CSS" constraint | Vanilla HTML + CSS; small vanilla JS for animations only |
| CMS / dynamic content | Admin wants to update content without code | Out of scope for portfolio project | Static HTML; update via git |
| Video background / autoplay | Modern and eye-catching | Slow load; bandwidth penalty on mobile; accessibility problems | Static screenshot or SVG illustration |

---

## Feature Dependencies

```
[Mini App: geo check-in]
    └──requires──> [initData auth exchange to JWT]
                       └──requires──> [Bot /start account linking (DONE v5.0)]
                       └──requires──> [New Auth Service endpoint: POST /api/auth/telegram]

[Mini App: attendance stats]
    └──requires──> [initData auth]

[Mini App: late check-in request]
    └──blocked-by──> [excuse.requested event publisher (NOT YET BUILT)]
                         └──defer to v7.x

[Web Panel: STOMP notifications]
    └──requires──> [notification-web STOMP server (DONE v5.0)]
                       └──requires──> [Angular SockJS + @stomp/stompjs client setup]

[Web Panel: red zone display]
    └──requires──> [attendance stats API (DONE)] + [threshold API (DONE)]

[Web Panel: admin CRUD]
    └──requires──> [JWT with ADMIN role] ──requires──> [Login form]

[Web Panel: teacher journal grid]
    └──requires──> [JWT with TEACHER role] ──requires──> [Login form]

[Landing: "Open in Telegram" CTA]
    └──requires──> [Bot deployed and linked (DONE v5.0)]

[Landing: "Install PWA" CTA]
    └──requires──> [PWA URL deployed (DONE v6.0)]
```

### Dependency Notes

- **Mini App initData auth requires one new backend endpoint.** The bot's `/start` command (v5.0) already records `telegram_id → user_id`. The new piece is a single Auth Service endpoint that accepts raw `initData` (HMAC-SHA256 validated) and returns a JWT pair. This is the only new backend work needed for v7.0.
- **Mini App check-in requires no other new backend work.** Attendance Service geo-checkin endpoint exists; Mini App calls it with the JWT from initData auth.
- **Web Panel STOMP requires new frontend setup only.** The server side is done. Angular needs SockJS + @stomp/stompjs client, connect on login, subscribe to `/topic/group/{groupId}`.
- **PDF/Excel export is blocked.** No backend PDF/Excel generation exists. Correctly excluded from this milestone.
- **Excuse/late check-in in Mini App is blocked.** Event publishers for `excuse.requested` and `late_checkin.requested` are listed as deferred in PROJECT.md (NOTIF-08 partial). Do not include in v7.0 Mini App scope.

---

## MVP Definition

### Launch With (v7.0)

Minimum to have all three frontends functional and useful.

**Telegram Mini App:**
- [ ] initData auth exchange (one new Auth Service endpoint)
- [ ] Today's schedule view with lesson status badges
- [ ] Geo check-in with success/failure feedback
- [ ] Attendance stats per subject with red zone indicator
- [ ] Telegram native theme, MainButton, BackButton wiring
- [ ] Deep link routing: /checkin opened from bot inline button

**Web Panel (Angular):**
- [ ] Login form (teacher + admin, username + password)
- [ ] Teacher: subject/group filter dropdowns
- [ ] Teacher: attendance journal grid (students x lessons, read-only)
- [ ] Teacher: attendance stats per subject/group
- [ ] Admin: user CRUD (create, status change, soft delete)
- [ ] Admin: group CRUD + headman assign/revoke
- [ ] Admin: semester CRUD + activation + confirmation-phrase delete
- [ ] Admin: dashboard summary stats
- [ ] Role-based route guards (TEACHER / ADMIN)
- [ ] Token refresh + logout

**Landing Page:**
- [ ] Hero + tagline + CTA buttons (Telegram, PWA)
- [ ] 3-4 feature highlights with icons
- [ ] "How it works" 3-step flow
- [ ] Role overview (Student / Teacher / Admin)
- [ ] Screenshots/mockup section
- [ ] Mobile-responsive layout
- [ ] Footer with links (GitHub, Telegram bot, PWA)

### Add After Validation (v7.x)

- [ ] Mini App: late check-in request — unblocks when event publisher built
- [ ] Mini App: excuse ticket submission — same blocker
- [ ] Mini App: homework list with completion toggle
- [ ] Web Panel: STOMP real-time notifications (lesson/homework/excuse push to panel)
- [ ] Web Panel: red zone student list + threshold configuration UI
- [ ] Web Panel: average attendance stats per group (admin view)
- [ ] Web Panel: student transfer between groups
- [ ] Web Panel: headman panel (mark attendance, manage excuses from desktop)
- [ ] Landing: animated stats counter
- [ ] Landing: dark mode

### Future Consideration (v8+)

- [ ] PDF/Excel export — requires new backend generation service
- [ ] Admin analytics trends (top-skippers, red zone alerts with email)
- [ ] Bulk CSV user import
- [ ] Teacher attendance override with audit trail
- [ ] Multi-language support (RU/EN)

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Mini App: initData auth (new endpoint) | HIGH | MEDIUM | P1 |
| Mini App: geo check-in | HIGH | LOW (API exists) | P1 |
| Mini App: today's schedule | HIGH | LOW (API exists) | P1 |
| Mini App: attendance stats | HIGH | LOW (API exists) | P1 |
| Mini App: Telegram UX (MainButton, theme, haptics) | HIGH | LOW | P1 |
| Web Panel: login + role guards | HIGH | LOW | P1 |
| Web Panel: teacher journal grid | HIGH | HIGH (complex grid UI) | P1 |
| Web Panel: admin user CRUD | HIGH | MEDIUM | P1 |
| Web Panel: admin group + semester CRUD | HIGH | MEDIUM | P1 |
| Web Panel: admin dashboard stats | MEDIUM | LOW | P1 |
| Landing: hero + features + mobile responsive | MEDIUM | LOW | P1 |
| Web Panel: STOMP notifications | MEDIUM | MEDIUM | P2 |
| Web Panel: red zone + threshold config | MEDIUM | MEDIUM | P2 |
| Web Panel: student transfer | MEDIUM | LOW | P2 |
| Mini App: homework list | MEDIUM | LOW | P2 |
| Landing: dark mode + animated counters | LOW | LOW | P2 |
| Mini App: late check-in request | HIGH | HIGH (blocked) | P3 |
| Mini App: excuse ticket submission | HIGH | HIGH (blocked) | P3 |
| PDF/Excel export (any surface) | MEDIUM | HIGH (blocked on backend) | P3 |

**Priority key:**
- P1: Must have for v7.0 launch
- P2: Add after core is validated (v7.x)
- P3: Future milestone or blocked on backend work

---

## Sources

- `.planning/PROJECT.md` — v7.0 active requirements, target features, deferred items (HIGH confidence — authoritative source)
- `docs/job-stories.md` — JS-TEACHER-01..08, JS-ADMIN-01..13, JS-STUDENT-01..09, JS-HEADMAN-01..20 (HIGH confidence — authoritative source)
- Existing PWA feature patterns: `frontends/pwa/src/features/` (HIGH confidence — live code)
- Telegram Mini App SDK: [tma.js React template](https://github.com/Telegram-Mini-Apps/reactjs-template), [Authorizing User docs](https://docs.telegram-mini-apps.com/platform/authorizing-user), [Init Data docs](https://docs.telegram-mini-apps.com/platform/init-data)
- Telegram Mini App capabilities 2025-2026: [DEV.to Mini App handbook](https://dev.to/simplr_sh/telegram-mini-apps-creation-handbook-58em), [Merge.rocks guide](https://merge.rocks/blog/how-to-build-a-telegram-mini-app-your-telegram-mini-apps-guide)
- Angular education dashboard patterns: [CoreUI Angular](https://coreui.io/angular/), [Smart Angular template](https://einfosoft.com/templates/admin/smartangular/doc/intro.html)

---

*Feature research for: RutCampusTrack v7.0 — Telegram Mini App, Angular Web Panel, Landing Page*
*Researched: 2026-04-06*
