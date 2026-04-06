# Stack Research

**Domain:** University attendance system — 3 new frontend apps (Telegram Mini App, Angular Web Panel, Landing)
**Researched:** 2026-04-06
**Confidence:** MEDIUM-HIGH (most claims verified via official docs or multiple sources)

---

## Context: What Already Exists (Do NOT Re-add)

The PWA at `frontends/pwa/` already uses:
- React 19.1, Vite 7, TypeScript 5.8, Tailwind 4.1, TanStack Query 5.96, React Router 7
- Axios 1.14, STOMP/SockJS, Framer Motion (motion 12), Phosphor Icons, shadcn/ui
- Vitest 3.1, Testing Library 16, vite-plugin-pwa 1.2

The new frontends must integrate with the same API Gateway (port 8080, httpOnly JWT cookies, `/api/...` routes).

---

## 1. Telegram Mini App (React)

Student-facing app running inside Telegram WebView. Shares attendance/schedule features with the PWA but adapted for Telegram's native UI patterns.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| React | 19.1 (pin to PWA) | UI framework | Same version as PWA — share types/logic; React 19 concurrent features matter for smooth Telegram WebView |
| Vite | 7.x | Build tool | Matches PWA; Telegram templates officially use Vite; fast HMR critical for Mini App iteration |
| TypeScript | 5.8 | Type safety | Same TS config as PWA possible; Telegram SDK ships full types |
| @telegram-apps/sdk-react | ^3.3.9 | Telegram platform APIs | Official Telegram SDK; React bindings with hooks for platform, theme, back button, viewport, haptics |
| @telegram-apps/telegram-ui | ^2.x | Native Telegram UI components | 25+ components matching Telegram iOS/Android design; AppRoot handles light/dark theme automatically |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @tanstack/react-query | ^5.96 (pin to PWA) | Server state / API fetching | Reuse same query patterns as PWA; caching same schedule/attendance endpoints |
| axios | ^1.14 (pin to PWA) | HTTP client | Reuse same axios instance with `withCredentials: true` for httpOnly cookies |
| react-router | ^7.x (pin to PWA) | Client-side routing | Telegram Mini Apps need browser hash routing; `createHashRouter` required in WebView |
| @stomp/stompjs + sockjs-client | pin to PWA versions | Real-time STOMP events | Receive lesson.started / check-in confirmation events same as PWA |
| @telegram-apps/react-router-integration | latest | Back button to router sync | Wires Telegram hardware Back button to React Router navigation automatically |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| @vitejs/plugin-basic-ssl | Local HTTPS | Telegram WebView requires HTTPS; vite-plugin-mkcert is the alternative |
| mockTelegramEnv (from sdk) | Browser testing | Call in `src/mockEnv.ts`; wrap in try/catch — uses real params inside Telegram, mock params in browser |
| Eruda | Mobile debug console | Inject conditionally in dev mode only; shows console/network inside Telegram WebView |

### Installation

```bash
# Mini App frontend
npm create vite@latest frontends/mini-app -- --template react-ts

# Telegram SDK
npm install @telegram-apps/sdk-react @telegram-apps/telegram-ui

# Reuse from PWA (check exact versions match)
npm install @tanstack/react-query axios react-router @stomp/stompjs sockjs-client

# Telegram router integration
npm install @telegram-apps/react-router-integration

# Dev: HTTPS for local Telegram testing
npm install -D @vitejs/plugin-basic-ssl
```

### Critical Integration Note: Routing

Use `createHashRouter`, not `createBrowserRouter`. Telegram WebView does not support HTML5 History API navigation. The `@telegram-apps/react-router-integration` package binds the Telegram Back button to `router.navigate(-1)` — this is required or the hardware back button will close the Mini App instead of going back.

### Critical Integration Note: Auth

The Mini App user IS a Telegram user. Auth flow differs from PWA:
- The Telegram SDK provides `initData` / `initDataRaw` (signed launch params)
- Backend must verify `initDataRaw` HMAC against the bot token
- No traditional login form — Telegram identity is the session
- The existing Auth Service OTP flow (bot `/login` command) is the bridge: bot sends OTP, user enters it, receives JWT cookie

---

## 2. Angular Web Panel

Teacher/admin dashboard for managing users, groups, semesters, viewing attendance journal, and subject management. Read-heavy with complex data tables.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Angular | ^21.2.7 | Framework | Current stable (released Nov 2025, latest patch Apr 1 2026); zoneless change detection default; esbuild CLI |
| Angular CLI | ^21.x | Build/scaffold | esbuild default builder in v21 — significantly faster than Webpack; `ng add tailwindcss` for automated Tailwind setup |
| TypeScript | ~5.8 | Type safety | Angular 21 requires TS 5.7+; pin to same TS version as PWA |
| Tailwind CSS | ^4.1 | Utility-first styling | Angular CLI ng add support official as of v21; CSS-first config (no tailwind.config.js needed); consistent with PWA visual language |
| @tanstack/angular-query-experimental | ^5.96.2 | Server state management | Same TanStack Query patterns as PWA; version 5.96.2 latest (Apr 3 2026); works with Angular Signals; experimental but actively maintained |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @angular/cdk | ^21.x | Table, virtual scroll, overlay primitives | Use for the attendance journal grid (CdkTable + virtual scrolling for 500+ rows); ships with Angular Material package |
| ng2-charts | ^10.0 | Chart.js Angular wrapper | Attendance stats bar/line charts; v10 supports Angular 14+; Chart.js 4 peer dep; lightweight vs. Highcharts or ECharts |
| chart.js | ^4.x | Charting engine | Peer dep of ng2-charts; include explicitly |
| @phosphor-icons/web | ^2.x | Icon set | Same icon family as PWA; web/CSS version for Angular (no React dependency) |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Angular DevTools (Chrome extension) | Component inspection, signal debugging | Essential for zoneless apps with signals |
| Karma + Jasmine (Angular CLI default) | Unit tests | Keep Angular default test setup; no need to switch to Jest for this project |

### Installation

```bash
# Scaffold Angular project (standalone, no SSR, CSS styles)
npx @angular/cli@21 new frontends/web-panel --standalone --style=css --routing --ssr=false

# Tailwind CSS (official automated setup)
cd frontends/web-panel
ng add tailwindcss

# TanStack Query for Angular
npm install @tanstack/angular-query-experimental

# Charts
npm install ng2-charts chart.js

# Angular CDK (for table/virtual scroll)
npm install @angular/cdk

# Icons (web/CSS version of Phosphor)
npm install @phosphor-icons/web
```

### Auth Integration Pattern

```typescript
// app.config.ts — functional interceptor with withCredentials for httpOnly cookies
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withFetch(),
      withInterceptors([credentialsInterceptor])
    )
  ]
};

// credentials.interceptor.ts
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req.clone({ withCredentials: true }));
};
```

The backend already issues httpOnly cookie JWTs. Angular just needs `withCredentials: true` on all requests — no token storage in browser.

### Angular Architecture Pattern for This Project

Use standalone components exclusively (no NgModule). Angular 21 default. Use `inject()` function instead of constructor injection. Use Signals (`signal()`, `computed()`) for component-local state. Use TanStack Query for all server state — do NOT use BehaviorSubject/Subject for async data.

---

## 3. Landing Page

Marketing/info page for the RutCampusTrack project. No framework required.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| HTML5 | — | Structure | No framework overhead; fastest possible load |
| CSS3 | — | Styling | Custom properties for theming; no build step required |
| Vanilla JavaScript | ES2020+ | Minimal interactivity | Smooth scroll, hamburger menu, simple animations — no framework needed |

### Approach: No Build Tool, No Framework

A university project landing page needs zero runtime overhead. Vanilla HTML/CSS/JS loads instantly, has no dependency security risk, and can be served from nginx alongside the PWA with a single `COPY` line in Dockerfile.

Optional lightweight CSS additions (only if needed):

| Library | CDN | Purpose | Note |
|---------|-----|---------|------|
| Google Fonts / local @font-face | — | Geist or system font | Match PWA font for brand consistency |
| AOS (Animate on Scroll) | CDN 2.3.4 | Scroll-triggered fade-ins | 13KB; CDN only, no npm install |

### What NOT to Use for Landing

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| React/Next.js | 200KB+ runtime for a static page | Vanilla HTML |
| Bootstrap | 30KB+ CSS for a page with custom design | CSS custom properties + Flexbox/Grid |
| Any npm build pipeline | Adds maintenance overhead for static content | Direct HTML/CSS files served by nginx |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Mini App UI | @telegram-apps/telegram-ui | Custom Tailwind components | telegram-ui matches Telegram's exact design language; saves weeks of matching iOS/Android Telegram patterns |
| Mini App routing | createHashRouter | createBrowserRouter | Telegram WebView does not support HTML5 History API |
| Angular UI library | Angular CDK + Tailwind | Angular Material | Angular Material enforces Material Design visual language; Tailwind gives freedom to match PWA aesthetics |
| Angular UI library | Angular CDK + Tailwind | PrimeNG | PrimeNG is comprehensive but heavy; CDK is lightweight primitive layer that pairs well with Tailwind |
| Angular charts | ng2-charts (Chart.js) | ngx-echarts | Chart.js is simpler and sufficient for bar/line attendance stats; ECharts is overkill for this use case |
| Angular state | TanStack Query | NgRx | NgRx is complex boilerplate; TanStack Query covers server state (the dominant use case here) with signals integration |
| Mini App auth | initData HMAC + OTP | Traditional login form | Mini Apps run inside Telegram — user IS already authenticated via Telegram |

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| @tanstack/angular-query-experimental@5.96 | Angular 21, TypeScript 5.8 | Works with Angular Signals; still "experimental" — pin to patch version |
| @telegram-apps/sdk-react@3.3.9 | React 18 + 19 | Confirmed React bindings; React 19 likely works, pin and verify on first install |
| ng2-charts@10.x | Angular 14+ (confirmed for 20) | Latest published ~March 2026; Chart.js 4 peer dep |
| Tailwind 4.1 | Angular CLI 21 | `ng add tailwindcss` automated; CSS-first config, no tailwind.config.js needed |
| React 19 (Mini App) | @tanstack/react-query 5.96 | Same version as PWA — no conflict |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Zone.js in Angular 21 | Angular 21 defaults to zoneless; adding zone.js is a step backward | Signals + OnPush change detection |
| NgModule in Angular panel | Deprecated pattern; standalone is the Angular 21 default | Standalone components with inject() |
| @tma.js/sdk (old package name) | Legacy package, superseded by @telegram-apps/sdk | @telegram-apps/sdk-react 3.x |
| localStorage for JWT in Angular | XSS risk; backend uses httpOnly cookies | withCredentials: true on HttpClient |
| BehaviorSubject for API state in Angular | TanStack Query handles caching/loading/errors better | @tanstack/angular-query-experimental |
| React in Angular web panel | Mixing frameworks; no shared component boundary justified | Angular standalone components |

---

## Stack Patterns by Variant

**Mini App — Attendance/Schedule views (shared with PWA):**
- Reuse the same TanStack Query hooks shape and axios instance
- Do NOT copy-paste PWA components — they use Tailwind/shadcn which looks wrong in Telegram; use @telegram-apps/telegram-ui equivalents

**Angular Web Panel — Read-heavy journal grid:**
- Use CdkTable with virtual scrolling for attendance journal (500+ students x 30+ lessons)
- Compute derived stats client-side with Angular Signals + computed()

**Landing Page — Static hosting:**
- Single `index.html` + `style.css` + optional `main.js`
- Serve from the same nginx container as PWA (`location /landing { root /usr/share/nginx/html; }`)

---

## Sources

- [Angular end-of-life dates — endoflife.date](https://endoflife.date/angular) — Angular 21.2.7 confirmed as current stable (HIGH confidence)
- [Angular v21 release — angular.dev](https://angular.dev/events/v21) — zoneless default, esbuild CLI (HIGH confidence)
- [@tanstack/angular-query-experimental — npm](https://www.npmjs.com/package/@tanstack/angular-query-experimental) — version 5.96.2, Apr 3 2026 (HIGH confidence)
- [@telegram-apps/sdk-react — npm](https://www.npmjs.com/package/@telegram-apps/sdk-react) — version 3.3.9, last published Oct 2025 (MEDIUM confidence — npm returned 403 on direct fetch, confirmed via WebSearch)
- [Telegram Mini Apps reactjs-template — GitHub](https://github.com/Telegram-Mini-Apps/reactjs-template) — official React+Vite template reference (HIGH confidence)
- [Angular Tailwind integration — angular.dev/guide/tailwind](https://angular.dev/guide/tailwind) — ng add automated setup confirmed for v21 (HIGH confidence)
- [ng2-charts — npm](https://www.npmjs.com/package/ng2-charts) — version 10.0.0, supports Angular 14+ (MEDIUM confidence)
- [Angular CDK virtual scrolling — material.angular.dev](https://material.angular.dev/cdk/scrolling) — CdkTable for large grids (HIGH confidence)
- Vanilla HTML/CSS for landing — consensus from multiple 2026 sources (HIGH confidence — no disputed alternative for static marketing page)

---
*Stack research for: RutCampusTrack v7.0 — Mini App, Web Panel, Landing*
*Researched: 2026-04-06*
