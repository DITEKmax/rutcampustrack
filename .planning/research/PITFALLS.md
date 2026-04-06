# Pitfalls Research

**Domain:** v7.0 Frontends — Telegram Mini App (React), Angular Web Panel, Landing page — adding three frontend clients to an existing microservice attendance system (RutCampusTrack).
**Researched:** 2026-04-06
**Confidence:** HIGH for Telegram Mini App auth (verified against official Telegram Mini Apps docs); HIGH for nginx multi-frontend routing (verified against official nginx docs and multiple production guides); MEDIUM for Angular-in-Java-Gradle-monorepo (no official support, verified against community patterns); HIGH for CORS expansion risks (directly observed in existing system v6.0 key decisions).

---

## Critical Pitfalls

### Pitfall 1: Using initDataUnsafe on the Backend — Any User Can Be Impersonated

**What goes wrong:**
The Telegram WebApp SDK provides `window.Telegram.WebApp.initDataUnsafe` as a parsed JavaScript object on the client side. If a developer reads `initDataUnsafe.user.id` on the frontend and sends it to the backend as a user identifier (e.g., `POST /api/mini-app/auth { telegramId: initDataUnsafe.user.id }`), any attacker can forge an arbitrary `telegramId` and receive a JWT for any student account. The attack requires only a fetch call with a crafted JSON body.

**Why it happens:**
`initDataUnsafe` is the easiest way to get user data during development. The name "Unsafe" does not strongly convey "cryptographically unverified" to developers who are moving fast. It works perfectly in testing because Telegram sets it correctly for real users — the forgery problem only exists for server-side trust.

**How to avoid:**
- The backend must validate the raw `initData` string (available as `window.Telegram.WebApp.initData`) — not the parsed `initDataUnsafe` object.
- Send the raw `initData` string from Mini App to backend: `POST /api/mini-app/auth { initData: window.Telegram.WebApp.initData }`.
- The backend validates the HMAC-SHA256 signature: compute `HMAC-SHA256(secret_key, data_check_string)` where `secret_key = HMAC-SHA256("WebAppData", BOT_TOKEN)` and `data_check_string` is all key=value pairs sorted alphabetically, excluding `hash`.
- Check `auth_date` field for expiry (reject if older than 5 minutes for auth, up to 1 hour for read-only operations).
- Use the `telegram-bot-api` Spring Boot library or a validated utility for HMAC verification — do not roll your own.
- After successful initData validation, issue a standard JWT (same format as existing PWA JWTs) so downstream services need zero changes.

**Warning signs:**
- Backend reads `telegramId` from a JSON body field without a corresponding signature check
- initData validation is skipped in tests ("we'll add it later")
- `window.Telegram.WebApp.initDataUnsafe.user` used as the source of truth for user identity anywhere in request construction

**Phase to address:**
Mini App authentication phase — implement backend initData validation before any authenticated endpoint is wired.

---

### Pitfall 2: Telegram Mini App Opens on Desktop Telegram — No initData Available

**What goes wrong:**
`window.Telegram.WebApp.initData` is empty string (`""`) when the Mini App is opened in Telegram Desktop (Windows/macOS/Linux) or Telegram Web, depending on version. `initDataUnsafe` may be partially populated. If the auth flow blindly sends empty `initData` to the backend, validation fails with an opaque error and the user sees a broken blank screen with no explanation.

Additionally, during development, opening the Mini App URL directly in a browser (not via Telegram) also yields empty initData — breaking the local dev workflow entirely unless handled.

**Why it happens:**
Developers test on mobile Telegram first where initData is always present. Desktop edge cases are discovered only when the student union tries it on a laptop.

**How to avoid:**
- On Mini App startup, check `window.Telegram.WebApp.initData !== ""` before attempting auth.
- If empty (desktop Telegram or browser): show a "Please open this app in Telegram on your mobile device" screen with a deep-link button (`tg://resolve?domain=RutTrackBot&appname=checkin`).
- For local development: use the `@telegram-apps/sdk` mock mode or Telegram's BotFather test environment. Never mock initData by hardcoding a fake string — this creates a security vulnerability that must be removed before deploy.
- Add an env flag `VITE_TMA_DEV_MODE=true` that bypasses initData check in development only, clearly gating it to non-production builds.

**Warning signs:**
- "Cannot read property of undefined" errors on `initDataUnsafe.user` on desktop
- Blank white screen when Mini App is opened via Telegram Web
- Local development requires commenting out initData validation code (this will be committed accidentally)

**Phase to address:**
Mini App authentication phase — build the empty-initData fallback before any real user testing.

---

### Pitfall 3: Telegram Mini App Viewport BottomSheet Breaks Fixed-Position Elements

**What goes wrong:**
On mobile Telegram (iOS and Android), the Mini App is displayed inside a native BottomSheet component. While the sheet is being dragged to expand or collapse, `window.innerHeight` and `document.documentElement.clientHeight` change rapidly. Any UI element using `position: fixed; bottom: 0` (e.g., a check-in button bar, a bottom navigation tab bar similar to the PWA) jumps or shifts during drag. The visual result is jarring and can cause accidental taps.

Additionally, on iOS the home indicator bar at the bottom overlaps `position: fixed; bottom: 0` content unless `env(safe-area-inset-bottom)` padding is applied. Telegram does NOT automatically apply this — it is the Mini App's responsibility.

**Why it happens:**
Developers test in Telegram Desktop or in a browser where there is no BottomSheet behavior, and the viewport size is stable. The issue only manifests on real mobile Telegram.

**How to avoid:**
- Use `window.Telegram.WebApp.expand()` on app init to immediately expand to full screen and lock the viewport height.
- Subscribe to `window.Telegram.WebApp.onEvent('viewportChanged', handler)` to detect height changes and temporarily suppress layout animations during transitions.
- For bottom-anchored elements, use CSS: `padding-bottom: env(safe-area-inset-bottom, 0px)` or rely on the Telegram CSS variable `var(--tg-viewport-stable-height)` for layout calculations instead of `100vh`.
- Use `window.Telegram.WebApp.viewportStableHeight` (not `viewportHeight`) for any layout that must not animate during scroll.
- Test on real mobile Telegram — simulators do not reproduce BottomSheet behavior.

**Warning signs:**
- Bottom navigation bar or CTA button jumps visually when Mini App first opens
- `100vh` height calculations show incorrect values on first render
- Content hidden behind iPhone home indicator on iOS

**Phase to address:**
Mini App scaffold/layout phase — establish the viewport strategy and safe area handling before building any screen with fixed-positioned elements.

---

### Pitfall 4: Angular Web Panel Added Without Updating Gateway CORS — Breaks Immediately

**What goes wrong:**
The existing API Gateway CORS configuration (in `application.yml`) allows only `http://localhost:5173` and `http://localhost:80` as origins. When the Angular web panel makes API calls from its own origin (e.g., `http://localhost:4200` in dev, `https://admin.rutcampustrack.ru` in prod), every preflight returns a CORS error. Since Angular's `HttpClient` with credentials uses strict CORS, the panel cannot call a single endpoint until allowed origins are updated.

This is not a new pitfall — it happened with the PWA too (v6.0 Key Decisions shows `OPTIONS bypass` and `DedupeResponseHeader` decisions) — but it is easy to forget to add the Angular origin alongside the React origins.

**Why it happens:**
CORS config lives in a YAML file that is not part of the Angular project. The Angular developer starts the dev server on port 4200 and assumes the backend "just works" because it worked for the PWA.

**How to avoid:**
- Before writing any Angular HTTP calls, add `http://localhost:4200` to `allowedOrigins` in Gateway `application.yml`.
- Add the production Angular panel URL (e.g., `https://admin.rutcampustrack.ru` or the nginx path-based URL) to `allowedOrigins` before deployment.
- Since the Angular panel uses `Authorization: Bearer <token>` in headers (not httpOnly cookies like the PWA), set `allowedHeaders: "*"` and keep `allowCredentials: false` for Bearer-based origins (or a separate route config) — mixing cookie-based and Bearer-based origins with `allowCredentials: true` requires exact origin matching, not wildcards.
- Document in Gateway config why each origin exists (comment inline).

**Warning signs:**
- Angular `HttpClient` calls return `net::ERR_FAILED` or CORS preflight 403 in DevTools
- "Access to XMLHttpRequest has been blocked by CORS policy" in Angular app console
- PWA still works but Angular panel does not — classic sign of missing origin

**Phase to address:**
Angular infrastructure/auth phase — update Gateway CORS as the first task before any API integration work.

---

### Pitfall 5: Angular Panel Auth Uses localStorage for JWT — Inconsistent With System Security Model

**What goes wrong:**
The existing PWA uses httpOnly cookies for the refresh token and in-memory storage for the access token (established in v6.0 for XSS protection). Angular tutorials and the `@auth0/angular-jwt` library default to `localStorage` for JWT storage. If the Angular panel stores the JWT in `localStorage`, it becomes an XSS target — more dangerous here than in the PWA because admin/teacher accounts have elevated privileges (headman marking, user management). A compromised admin token can bulk-create users or mark all students as absent.

**Why it happens:**
Angular JWT authentication tutorials overwhelmingly show `localStorage.setItem('token', jwt)`. Developers copy this pattern without realizing the PWA already established a different standard for this system.

**How to avoid:**
- The Angular panel should use the same auth pattern as the PWA: access token in memory (Angular service property, not localStorage), refresh token in httpOnly cookie via `POST /api/auth/refresh`.
- Since Angular panel serves teachers and admins (who don't have Telegram), the login flow is: login form → `POST /api/auth/login` with username/password → receive access JWT in response body + refresh token in Set-Cookie httpOnly → store access token in Angular `AuthService` as a private property.
- Use Angular `HttpInterceptorFn` (functional interceptor, Angular 15+) to attach `Authorization: Bearer <token>` to every outgoing request — reads from the in-memory service, not localStorage.
- On page refresh: the in-memory token is lost, but the httpOnly refresh cookie persists → trigger a silent refresh on app init.
- Never put the token in `sessionStorage` either — it is accessible to XSS just like `localStorage`.

**Warning signs:**
- `localStorage.setItem('accessToken', ...)` anywhere in the Angular codebase
- Angular `AuthGuard` reading token from `localStorage`
- Token visible in DevTools → Application → Local Storage

**Phase to address:**
Angular authentication layer phase — establish the in-memory + cookie pattern before any protected routes are built.

---

### Pitfall 6: Angular Frontend-Only RBAC Route Guards — Attackers Bypass by Editing JavaScript

**What goes wrong:**
Angular route guards (`canActivate`, role guards) and `*ngIf="user.role === 'ADMIN'"` directives are client-side checks only. They are good for UX (hiding irrelevant UI) but provide zero security. A determined student can open DevTools, modify the guard return value, or forge an API request directly with a captured teacher JWT to access admin-only endpoints. Since the backend uses `@RequireRole` AOP (not Spring Security at the service layer), the backend is the real security boundary — but only if it is correctly configured.

**Why it happens:**
When building the Angular panel, it is tempting to add RBAC only in the frontend to move fast. The `@RequireRole` on backend controllers exists in the Java services, but developers may accidentally leave some endpoints unguarded during the panel's development phase.

**How to avoid:**
- Angular route guards are for UX only — treat them as "redirect unauthorized users to login" not as security.
- For every API call the panel makes, the corresponding backend endpoint must have `@RequireRole({"ADMIN"})` or `@RequireRole({"TEACHER", "ADMIN"})` AOP annotation — this was established in v4.0 and must be verified for every new endpoint the panel calls.
- If a panel phase adds new backend endpoints (e.g., for admin user management), the AOP annotation must be part of the same plan, not deferred.
- Add an integration test for each new endpoint that verifies a STUDENT role JWT receives 403.

**Warning signs:**
- Angular route guard that returns `true` unconditionally while "we add real checks later"
- Backend endpoint added for the panel with no `@RequireRole` annotation
- "The panel shows the right UI, so the security is done" reasoning

**Phase to address:**
Angular auth + RBAC phase — verify every backend endpoint the panel calls has `@RequireRole` coverage before considering a phase "done."

---

### Pitfall 7: nginx Serving Multiple Frontends Without Proper SPA Fallback Per App — Angular Routing Breaks

**What goes wrong:**
The current nginx container (`pwa-nginx`) serves the PWA from `root /usr/share/nginx/html` with a single `try_files $uri $uri/ /index.html` rule. When the Angular web panel is added to nginx (e.g., at `/panel`), Angular's router uses HTML5 pushState URLs (`/panel/dashboard`, `/panel/users`). If a user bookmarks `/panel/users` and navigates directly, nginx looks for a physical file at `dist/panel/users` — finds nothing — and falls through to the PWA's `index.html`. The user sees the PWA instead of the Angular panel.

Similarly, the landing page at `/` conflicts with the PWA which currently owns `/`.

**Why it happens:**
The existing nginx config has exactly one `location /` block for the PWA. Adding a second frontend is done by adding a new `location /panel` block, but forgetting to add the SPA fallback `try_files` inside that block specifically.

**How to avoid:**
The correct nginx pattern for multi-SPA hosting:

```nginx
# PWA — served at /pwa or moved to a subpath
location /pwa/ {
    alias /usr/share/nginx/html/pwa/;
    try_files $uri $uri/ /pwa/index.html;
}

# Angular Web Panel — at /panel
location /panel/ {
    alias /usr/share/nginx/html/panel/;
    try_files $uri $uri/ /panel/index.html;
}

# Landing — at root (static, no SPA fallback needed)
location / {
    root /usr/share/nginx/html/landing/;
    index index.html;
}
```

Angular must also be built with `--base-href /panel/` so that all asset URLs and router links are relative to `/panel/` not `/`.

Alternatively: serve each frontend from a separate nginx container on a different port, with a single reverse-proxy nginx at the front routing by path or subdomain. This avoids the alias/try_files complexity entirely and keeps each frontend's nginx config simple and isolated.

**Warning signs:**
- Navigating to `/panel/users` directly returns the PWA `index.html` content
- Angular assets (main.js, styles.css) return 404 after deployment because they are referenced as `/main.js` not `/panel/main.js`
- Angular router `RouterModule.forRoot(routes, { useHash: false })` but nginx has no SPA fallback for that prefix

**Phase to address:**
Infrastructure / nginx routing phase — design the multi-frontend nginx layout before Angular or Landing builds are attempted.

---

### Pitfall 8: Angular Built Into Java Gradle Monorepo Without Isolation — Breaks Java Build

**What goes wrong:**
The project is a Gradle-based Java monorepo. Angular uses `npm`/Node.js. If Angular is added as a Gradle subproject using `com.github.node-gradle/gradle-node-plugin` or similar, the Java build now requires Node.js to be installed on every developer's machine and in CI. The `./gradlew build` command starts downloading Node.js, running `npm install`, and building Angular — adding 3-5 minutes to every Java service build. If the Angular build fails (e.g., a missing npm dependency on CI), the entire Gradle build fails, blocking Java service development.

**Why it happens:**
It feels "clean" to unify everything under Gradle. The `gradle-node-plugin` makes it technically possible.

**How to avoid:**
Keep Angular completely isolated from the Gradle build. Angular lives in `frontends/web-panel/` with its own `package.json`, `angular.json`, and `tsconfig.json`. It is built with `npm run build` directly — never via Gradle tasks.
- In `settings.gradle.kts`, do NOT include `frontends/web-panel` as a Gradle subproject.
- In `docker-compose.yml`, the web panel gets its own nginx container with a pre-built `dist/` directory — same pattern as the PWA.
- CI pipeline has separate jobs: `build-java` and `build-angular` with no dependency between them.
- Add `frontends/web-panel/node_modules/` to `.gitignore` and `frontends/web-panel/dist/` as well.

**Warning signs:**
- `frontends/web-panel` listed in `settings.gradle.kts` includes
- `./gradlew build` output contains "Downloading Node.js" or "npm install"
- Java service tests fail because `npm` is not found in CI environment

**Phase to address:**
Angular project scaffold phase — establish directory structure and isolation before any Angular code is written.

---

### Pitfall 9: Landing Page Served at Root `/` Displaces PWA — Breaks Install Flow

**What goes wrong:**
The PWA is currently served by `pwa-nginx` at port 80 as root (`/`). The Service Worker registers at `/sw.js` and the PWA manifest is at `/manifest.webmanifest`. If the landing page is also placed at `/` in the same nginx container, the PWA is overwritten. Students trying to install the PWA from the landing page's "Install the app" button will either get the landing page's `index.html` served as the PWA root or the Service Worker will not be found at `/sw.js`.

**Why it happens:**
It is natural to want the landing page at the root. The conflict with the existing PWA deployment is easy to overlook when thinking about them as separate things.

**How to avoid:**
Choose one layout strategy and commit to it before building the landing:

**Option A (Recommended for simplicity):** Landing at `/`, PWA moved to `/app/`. Update PWA nginx config to serve from `/app/`, update Vite `base: '/app/'` config, update Service Worker scope to `/app/`, update all deep links in push notifications and bot.

**Option B:** Separate nginx containers — landing on its own container, PWA on its own container, front-proxy nginx routes by path or subdomain.

**Option C:** PWA stays at `/`, landing at `/about` or on a separate subdomain (`rut.ru` vs `app.rut.ru`).

Whichever is chosen, document it and implement it in the infrastructure phase before any landing page content is written — changing the PWA base path after deployment invalidates all existing push subscription deep links.

**Warning signs:**
- Landing `index.html` and PWA `index.html` both configured to be served at `/`
- `sw.js` returning 404 after adding the landing page
- A2HS install prompt stops appearing after landing page deployment

**Phase to address:**
Infrastructure / nginx routing phase — resolve URL layout before any frontend is deployed.

---

### Pitfall 10: Telegram Mini App Does Not Support httpOnly Cookies — Authentication Must Use Authorization Header

**What goes wrong:**
The PWA uses httpOnly cookies for the refresh token (`Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict`). Telegram's WebView (the embedded browser that runs Mini Apps) has inconsistent and often disabled cookie support. On iOS, Telegram uses WKWebView which isolates cookies per-app and does not share them with Safari. Cookies set via `Set-Cookie` from the API Gateway may be silently dropped in the Telegram WebView, causing the refresh token to be lost immediately. The user appears logged in on first visit but is logged out on next open.

**Why it happens:**
Developers copy the PWA auth flow for the Mini App because it seems natural to reuse the existing auth pattern. The WebView cookie restriction is discovered only during device testing.

**How to avoid:**
For the Telegram Mini App specifically:
- Do NOT rely on httpOnly cookies for token persistence. The Mini App must use a different storage strategy.
- After validating initData and issuing JWT, return both the access token and refresh token in the **response body**.
- Store the access token in React state (in-memory) and the refresh token in `localStorage` within the Mini App (Telegram WebView's localStorage persists across opens on the same device).
- On Mini App open: check `localStorage` for a refresh token → call refresh endpoint with it in the request body (not cookie) → get a new access token in memory.
- This requires a new auth endpoint variant: `POST /api/auth/refresh-token-body` that accepts `{ refreshToken: "..." }` in the body and returns `{ accessToken: "...", refreshToken: "..." }` without relying on cookies.
- The security trade-off (localStorage vs cookie) is acceptable for the Mini App context: Telegram WebView is sandboxed per-app and not accessible from other apps.

**Warning signs:**
- Mini App user is logged in on first open, but "not authenticated" on the second open
- `document.cookie` shows empty string in Telegram WebView DevTools
- `Set-Cookie` headers visible in network tab but cookie does not appear in application storage

**Phase to address:**
Mini App authentication phase — design the token storage strategy for WebView before any authenticated API calls are made.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Using `initDataUnsafe` for backend user identification | Faster development | Any user can be impersonated — critical security breach | Never — validate raw `initData` HMAC every time |
| `localStorage` for JWT in Angular panel | Simple, tutorials show this | Admin/teacher tokens exposed to XSS | Never — use memory + httpOnly cookie |
| Combining all frontends in one nginx container immediately | Single service | SPA routing conflicts, nginx config becomes complex/fragile | Acceptable only if URL layout is pre-designed |
| Adding Angular to Gradle monorepo build | Unified `./gradlew build` | Node.js required everywhere, 3-5 min added to all Java builds, CI coupling | Never — keep isolated |
| Skipping Angular route guard role checks ("UI only is fine") | Less code | Backend endpoints may be unguarded; attackers bypass with direct API calls | Never for admin/teacher operations |
| Forwarding Telegram `user.id` as plain JSON field without HMAC | Simplest auth flow | Any user can impersonate any other user | Never |
| `Notification.requestPermission()` auto-called in Mini App | Simpler code | One-shot prompt wasted; Telegram WebView blocks re-prompting | Never |
| Empty-initData dev bypass committed to main branch | Easier local dev | Any browser can call Mini App endpoints without Telegram auth | Never commit — env-var gate only |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Mini App → API Gateway | Sending `initDataUnsafe.user.id` in request body as the user identifier | Send raw `initData` string; backend validates HMAC and extracts user |
| Mini App → API Gateway | Using `credentials: 'include'` (cookie mode) — Telegram WebView drops httpOnly cookies | Use `Authorization: Bearer <token>` header for all Mini App API calls |
| Angular → API Gateway | Not adding `http://localhost:4200` and production Angular origin to Gateway `allowedOrigins` | Add Angular origins to Gateway CORS config before any HTTP call |
| Angular JWT | Angular `HttpClient` not attaching Bearer header automatically | Register `HttpInterceptorFn` via `provideHttpClient(withInterceptors([authInterceptor]))` in `app.config.ts` |
| nginx → Angular SPA | Missing `try_files` inside Angular's `location /panel/` block | Each SPA location block needs its own `try_files $uri $uri/ /panel/index.html` |
| Angular build → nginx | Angular built without `--base-href /panel/` | All asset paths are relative to `/` instead of `/panel/`; assets 404 |
| Mini App Telegram theme | Hardcoded light-mode colors ignoring `tg.colorScheme` | Use `var(--tg-theme-bg-color)`, `var(--tg-theme-text-color)` CSS variables throughout |
| Landing → PWA deep link | Landing "Install App" button links to `/` | Link must point to the PWA's actual base path (e.g., `/app/`) after URL restructuring |
| CORS expansion | Adding Angular origin without checking `allowCredentials` implications | Bearer-token callers (`allowCredentials: false`) and cookie-token callers (`allowCredentials: true`) need separate gateway route configs or careful origin management |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Angular not using lazy-loaded routes | Initial bundle >2MB, slow first paint on teacher's laptop | Use `loadComponent` for each route — every route is lazy by default in modern Angular | Immediately; Angular panel has many pages |
| Mini App loading React without code splitting | 3+ second white screen inside Telegram | Use React.lazy + Suspense for each page component | First user opens app on slow connection |
| Angular panel fetching entire user list without pagination | 500-user table freeze UI, API times out | Use `PagedModel` (HATEOAS) from the existing backend, virtual scroll for long lists | >100 users in academic_db |
| Landing page with unoptimized images (raw PNGs > 500KB) | Slow load, Google PageSpeed red score | Compress images, use WebP, add explicit `width`/`height` attributes | Immediate on mobile networks |
| Mini App making sequential API calls for page data | Slow page loads inside Telegram | Use `Promise.all()` for parallel API calls; TanStack Query (already in PWA) can be reused | Every page open — Telegram WebView has cold start latency |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Trusting `initDataUnsafe` user data on the backend | Any student can impersonate any other user or teacher | Always validate raw `initData` HMAC-SHA256 on the backend before issuing JWT |
| Storing admin JWT in `localStorage` | Admin token stolen via XSS; attacker gets full user management access | Store access token in Angular service memory only; refresh token in httpOnly cookie |
| Angular frontend-only RBAC | Attacker bypasses route guard; calls unguarded admin endpoints directly | Every backend endpoint the panel calls must have `@RequireRole` AOP annotation |
| Not checking `auth_date` in initData validation | Replay attack: captured initData replayed hours later to get a new JWT | Reject initData older than 5 minutes (configurable via app settings) |
| Telegram bot token exposed in frontend bundle | Bot can be taken over; all users' Telegram accounts linked to the bot compromised | Bot token lives only in backend env vars; never included in any frontend build |
| nginx serving `dist/` directory listing if index.html missing | Attacker can enumerate all frontend assets and JS chunk contents | Always include `index off` or `autoindex off` in nginx; ensure `index.html` exists before deploy |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Mini App ignoring `tg.colorScheme` — hardcoded light colors | Dark mode Telegram users see blinding white app | Use CSS variables `var(--tg-theme-bg-color)` etc.; test in both Telegram dark and light themes |
| Mini App showing same bottom tab bar as PWA | Telegram already has its own back button and navigation — double navigation confuses users | In Mini App, use the Telegram BackButton API (`window.Telegram.WebApp.BackButton`) instead of in-app navigation arrows |
| Angular panel with no loading states on async table data | Teacher clicks "attendance journal" and sees blank screen for 2+ seconds | Add Angular `@defer` blocks or skeleton loading states for every async data fetch |
| Landing page with no mobile viewport meta tag | Landing looks broken on student's phone | Always include `<meta name="viewport" content="width=device-width, initial-scale=1">` |
| Mini App calling `window.Telegram.WebApp.close()` on logout | User is kicked out of Telegram app flow unexpectedly | On logout, navigate to Mini App's login screen, not close; only call `close()` on explicit "Done" user action |
| Angular panel showing STUDENT role data to ADMIN | Admin sees student view, misses admin-only features | Role detection must happen before routing; show role-appropriate nav from the start |

---

## "Looks Done But Isn't" Checklist

- [ ] **Mini App auth:** initData validates on frontend — verify backend validates HMAC-SHA256 signature, not just parses `initDataUnsafe`
- [ ] **Mini App token persistence:** Login works in one session — verify refresh token survives Mini App close and re-open (localStorage, not memory)
- [ ] **Angular CORS:** API calls work from Angular dev server — verify production Angular origin is also in Gateway `allowedOrigins`
- [ ] **Angular auth:** JWT appears to work — verify access token is NOT in `localStorage` (DevTools → Application → Local Storage must be empty)
- [ ] **Angular RBAC:** Route guards redirect unauthorized users — verify corresponding backend endpoints have `@RequireRole` and reject wrong-role JWTs with 403
- [ ] **Angular build:** `npm run build` produces files — verify `--base-href /panel/` is set and all assets load from the correct path in nginx
- [ ] **nginx multi-SPA:** Each app loads at its configured path — verify direct navigation to deep routes (e.g., `/panel/users/123`) serves the correct `index.html`, not the PWA's
- [ ] **Landing vs PWA conflict:** Landing deploys at `/` — verify PWA Service Worker at `/sw.js` is not broken (or PWA is moved to `/app/` with consistent redirects)
- [ ] **Mini App dark mode:** App looks correct — verify `var(--tg-theme-bg-color)` is used, test specifically in Telegram's dark theme
- [ ] **initData expiry:** Auth works — verify `auth_date` check rejects initData older than 5 minutes; test with a captured initData string

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| initDataUnsafe used in production — accounts impersonatable | HIGH | Invalidate all Mini App JWTs immediately; deploy backend HMAC validation; force all Mini App users to re-login |
| Angular JWT in localStorage discovered post-deploy | HIGH | Rotate JWT signing keys; invalidate all tokens; migrate to in-memory storage; redeploy; notify affected users |
| PWA broken by landing page conflict | MEDIUM | Roll back landing deploy; restructure nginx config with correct location blocks; update PWA base path; redeploy both |
| Angular assets 404 after deploy (wrong base-href) | LOW | Rebuild Angular with `--base-href /panel/`; redeploy nginx volume only (no backend changes needed) |
| Angular origin not in Gateway CORS | LOW | Add origin to Gateway `application.yml`; redeploy Gateway container only |
| Telegram WebView cookie auth broken (refresh token lost) | MEDIUM | Implement body-based refresh token endpoint; update Mini App to use localStorage refresh token pattern |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| initDataUnsafe backend trust | Mini App auth phase (Phase 1 of v7.0 Mini App) | Send forged `{"telegramId": 999}` directly to auth endpoint — must get 401, not JWT |
| Empty initData on desktop/browser | Mini App auth phase | Open Mini App URL in Chrome browser (not Telegram) — must show "Open in Telegram mobile" screen |
| Telegram BottomSheet viewport quirks | Mini App scaffold/layout phase | Test on real mobile Telegram; expand/collapse sheet while app is open — UI must not jump |
| Angular CORS blocked | Angular infra phase (first Angular phase) | `curl -I -X OPTIONS http://localhost:8080/api/academic -H "Origin: http://localhost:4200"` must return 200 with correct CORS headers |
| Angular JWT in localStorage | Angular auth phase | DevTools Application → Local Storage must contain no tokens after login |
| Angular frontend-only RBAC | Angular auth + RBAC phase | Direct `curl` to admin endpoint with student JWT must return 403 |
| nginx multi-SPA routing conflict | Infrastructure / nginx routing phase (before any frontend deploys) | Navigate directly to `/panel/users` in browser — must serve Angular `index.html`, not PWA |
| Angular added to Gradle monorepo | Angular scaffold phase | `./gradlew build` must complete without any Node.js download or npm invocation |
| Landing conflicts with PWA root | Infrastructure / nginx routing phase | After landing deploy, `/sw.js` must still return Service Worker file (not 404) |
| Mini App httpOnly cookie dropped by WebView | Mini App auth phase | Close and reopen Mini App — user must still be authenticated (refresh via localStorage token) |

---

## Sources

- [Init Data — Telegram Mini Apps official docs](https://docs.telegram-mini-apps.com/platform/init-data)
- [Authorizing User — Telegram Mini Apps official docs](https://docs.telegram-mini-apps.com/platform/authorizing-user)
- [Viewport — Telegram Mini Apps official docs](https://docs.telegram-mini-apps.com/platform/viewport)
- [Theming — Telegram Mini Apps official docs](https://docs.telegram-mini-apps.com/platform/theming)
- [Seamless Authentication in Telegram Mini Apps — Medium/Miralex](https://medium.com/@miralex13/seamless-authentication-in-telegram-mini-apps-building-a-secure-and-frictionless-user-experience-6249599e2693)
- [Telegram Mini App development and testing specifics — DEV Community](https://dev.to/dev_family/telegram-mini-app-development-and-testing-specifics-from-initialisation-to-launch-1ofh)
- [Angular HTTP Interceptors — angular.dev official docs](https://angular.dev/guide/http/interceptors)
- [Angular Route Guards — angular.dev official docs](https://angular.dev/guide/routing/route-guards)
- [Angular JWT Guard for RBAC based on JWT — Scrum and Coke/Medium](https://medium.com/scrum-and-coke/angular-guard-for-role-based-access-control-rbac-based-on-jwt-e79dfdb41f2a)
- [Hosting Angular and React on same domain using nginx — Medium/Devyash Sanghai](https://medium.com/@devyashsanghai/hosting-angular-and-react-app-on-a-same-domain-using-nginx-189f96493818)
- [How to use nginx to service multiple React apps — Medium/Tonny](https://tonny.medium.com/how-to-use-nginx-to-service-multiple-react-apps-641501e92581)
- [Mini App scrolls by iOS device's safe-area at bottom — Telegram-Mini-Apps GitHub Issues](https://github.com/Telegram-Mini-Apps/issues/issues/39)
- [Safe area inset env() support in Mini Apps — TelegramMessenger/Telegram-iOS GitHub](https://github.com/TelegramMessenger/Telegram-iOS/issues/1377)
- [CORS Configuration — Spring Cloud Gateway official docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html)
- Existing v6.0 Key Decisions in `.planning/PROJECT.md` (OPTIONS bypass, DedupeResponseHeader, httpOnly cookie pattern)

---
*Pitfalls research for: v7.0 Frontends — Telegram Mini App (React), Angular Web Panel, Landing page — added to existing RutCampusTrack microservice system*
*Researched: 2026-04-06*
