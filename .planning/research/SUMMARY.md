# Project Research Summary

**Project:** RutCampusTrack v7.0 — Telegram Mini App, Angular Web Panel, Landing Page
**Domain:** University attendance system — three new frontend clients for existing Java microservice backend
**Researched:** 2026-04-06
**Confidence:** HIGH

## Executive Summary

v7.0 adds three distinct frontend applications to a fully operational Java/Spring Boot microservice backend. The backend APIs are complete and proven — every feature in scope already has a working endpoint. The only new backend work is a single Auth Service endpoint (`POST /api/auth/tma`) to exchange Telegram `initData` for a JWT, plus a body-based refresh endpoint for Mini App token persistence, plus CORS configuration updates to the API Gateway.

The three frontends target radically different contexts: the Telegram Mini App runs inside a sandboxed Telegram WebView on mobile with platform-specific auth, routing, and viewport constraints; the Angular Web Panel is a desktop-first admin/teacher dashboard consuming existing CRUD and reporting APIs; the Landing Page is purely static HTML/CSS with zero backend dependency.

Two non-negotiable security decisions: (1) raw `initData` HMAC-SHA256 validation on the backend (never `initDataUnsafe`); (2) JWT access tokens in memory/signal storage only (never localStorage for access tokens in any frontend).

---

## Key Findings

### Recommended Stack

**Mini App:** React 19.1 + Vite 7 + TypeScript 5.8 + @telegram-apps/sdk-react ^3.3.9 + @telegram-apps/telegram-ui ^2.x + TanStack Query 5.96 + createHashRouter (React Router 7)

**Web Panel:** Angular 21 standalone (zoneless, no Zone.js) + Tailwind 4.1 + @tanstack/angular-query-experimental 5.96.2 + Angular CDK (CdkTable + virtual scroll) + ng2-charts 10.x + Chart.js 4 + @phosphor-icons/web ^2.x

**Landing:** Vanilla HTML5 + CSS3 + minimal ES2020 JS. No framework, no build tool.

**Avoid:** Zone.js, NgModules, @tma.js/sdk (legacy), localStorage for JWTs, shared npm package between PWA and Mini App, adding Angular to Gradle build.

### Expected Features

**Mini App P1:** initData auth exchange (one new backend endpoint), today's schedule view, geo check-in with MainButton + haptic feedback, attendance stats with red zone indicator, Telegram native UX (BackButton, theme colors, bottom tab navigation)

**Web Panel P1:** Login form (teacher + admin), role-based route guards, attendance journal grid (CdkTable virtual scroll — students x lessons matrix), admin CRUD (users/groups/semesters), dashboard summary stats, token refresh + logout

**Landing P1:** Hero + CTAs, feature highlights, "how it works" flow, role overview, screenshots, mobile-responsive layout

**Blocked/Deferred:** Excuse tickets + late check-in (event publishers not built), PDF/Excel export (backend generation service needed)

### Architecture

- Three separate nginx containers (one per frontend), consistent with existing `pwa-nginx` pattern
- Auth Service: new `POST /api/auth/tma` with HMAC-SHA256 initData validation using bot token secret, looks up user by `telegram_id` (stored from v5.0 bot linking); new `POST /api/auth/refresh-body` for Mini App (WebView drops httpOnly cookies)
- Gateway: CORS origins expansion (Mini App + Web Panel dev/prod origins) + `/api/auth/tma` + `/api/auth/refresh-body` added to PUBLIC_PATHS
- Mini App: access token in React state, refresh token in localStorage (WebView cookie limitation), createHashRouter (no HTML5 History API in WebView)
- Web Panel: access token in Angular signal (memory), httpOnly cookie for refresh token, lazy-loaded routes
- URL layout decision required before any deployment — separate nginx containers per frontend recommended

### Critical Pitfalls

1. **initDataUnsafe for backend auth** → user impersonation vulnerability. Must validate raw `initData` HMAC-SHA256 server-side.
2. **Telegram WebView drops httpOnly cookies** → Mini App needs localStorage + body-based refresh; cannot reuse PWA cookie pattern.
3. **Landing at `/` conflicts with PWA** → breaks existing push notification deep links. Decide URL layout before any deployment.
4. **Angular CORS blocked** → add dev/prod origins to Gateway before first API call.
5. **nginx multi-SPA routing** → each app needs own `try_files` block + correct `--base-href`.
6. **Angular JWT in localStorage** → admin/teacher tokens exposed to XSS. Use Angular signal (memory) + httpOnly cookie.
7. **Telegram BottomSheet viewport** → breaks `position: fixed` and `100vh`. Use `viewportStableHeight` + safe-area CSS variables.

---

## Implications for Roadmap

### Suggested Phase Structure (8 phases, 33-40)

| # | Phase | Goal | Dependencies |
|---|-------|------|-------------|
| 33 | Infrastructure | URL layout decision, Gateway CORS + public paths, nginx configs, docker-compose | None |
| 34 | Auth Service TMA | `POST /auth/tma` + `POST /auth/refresh-body` endpoints, bot secret injection | None |
| 35 | Landing Page | Static HTML/CSS marketing page, nginx container | Phase 33 |
| 36 | Mini App Scaffold + Auth | Vite scaffold, viewport setup, initData auth flow, localStorage refresh pattern, dev mock env | Phase 33, 34 |
| 37 | Mini App Features | Schedule, geo check-in, attendance stats, Telegram UX (MainButton, haptic, theme) | Phase 36 |
| 38 | Web Panel Scaffold + Auth | Angular 21 scaffold, Tailwind, interceptors, role guards, login, logout | Phase 33 |
| 39 | Web Panel Teacher | Attendance journal grid (CdkTable virtual scroll), stats charts (ng2-charts) | Phase 38 |
| 40 | Web Panel Admin | User/group/semester CRUD, headman assign/revoke, dashboard summary stats | Phase 38 |

### Build Order Rationale

- Infrastructure first (Phase 33): nginx URL layout is a one-way door; Gateway CORS must exist before any frontend makes API calls
- Auth backend (Phase 34) before Mini App (Phases 36-37): initData validation must be hardened before Mini App calls it
- Landing (Phase 35) parallel-safe: zero backend dependencies, short phase
- Angular scaffold (Phase 38) before features (Phases 39-40): security model must be established before HTTP calls
- Teacher journal (Phase 39) before admin CRUD (Phase 40): virtual scroll grid is riskiest UI component

### Research Flags

- **Phase 36:** Telegram WebView viewport edge cases + initData flow need deeper research during planning
- **Phase 39:** CdkTable virtual scroll with 500+ rows — research optimal pattern
- Phases 33, 34, 35, 40: standard patterns, skip research

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Angular 21.2.7 verified via endoflife.date; TanStack Query 5.96.2 via npm; @telegram-apps/sdk-react 3.3.9 via WebSearch |
| Features | HIGH | Backend APIs fully operational; job stories authoritative; PWA provides live code reference |
| Architecture | HIGH | Gateway and auth-service source inspected; Telegram official docs cover initData flow; existing pwa-nginx pattern proven |
| Pitfalls | HIGH | Telegram Mini App pitfalls verified against official docs; nginx multi-SPA verified; CORS pitfall observed from v6.0 |

**Overall confidence: HIGH**

### Gaps to Address

- Body-based refresh endpoint needs explicit design in Phase 34
- Production Mini App CORS origin must be confirmed before Phase 33 Gateway config
- @tanstack/angular-query-experimental — pin to 5.96.2, do not auto-update during v7.0
- Mini App localStorage persistence across Telegram app updates — validate on real devices during Phase 36

---

## Sources

### Primary
- `.planning/PROJECT.md` — v7.0 active requirements and deferred items
- `docs/job-stories.md` — JS-TEACHER-01..08, JS-ADMIN-01..13, JS-STUDENT-01..09
- `frontends/pwa/src/` — live PWA code; source of truth for API patterns and auth model
- `services/api-gateway/src/main/resources/application.yml` — CORS config
- `services/api-gateway/src/main/java/.../JwtAuthenticationFilter.java` — public paths
- [Telegram Mini Apps — Init Data validation](https://docs.telegram-mini-apps.com/platform/init-data)
- [Telegram Mini Apps — Authorizing User](https://docs.telegram-mini-apps.com/platform/authorizing-user)
- [Telegram Mini Apps — Viewport](https://docs.telegram-mini-apps.com/platform/viewport)
- [Angular v21 — angular.dev](https://angular.dev)
- [Angular CDK virtual scrolling — material.angular.dev](https://material.angular.dev/cdk/scrolling)

### Secondary
- [@telegram-apps/sdk-react — npm](https://www.npmjs.com/package/@telegram-apps/sdk-react)
- [ng2-charts 10.0.0 — npm](https://www.npmjs.com/package/ng2-charts)
- [Telegram Mini Apps reactjs-template — GitHub](https://github.com/Telegram-Mini-Apps/reactjs-template)

---
*Research completed: 2026-04-06*
*Ready for requirements: yes*
