# M07 Checklist

Атомарные задачи в порядке выполнения. Одна строка = одна единица работы
(~30 мин - 2 часа). Отмечаются `[x]` после коммита.

Группы в порядке dependency — G1 (CSP self-host) → G2 (meta + a11y
landing) → G3 (openapi-ts generation) → остальные могут параллельно.

## Группа 1 — Landing CSP self-host (C0-6, 12 P0-1, 13 P0-4) — ~1д

- [x] Аудит всех CDN-запросов из `frontends/landing/index.html` — 9
      внешних ресурсов: 3 preconnect + 4 stylesheet (Fontshare CSS,
      Google Fonts CSS, unpkg Phosphor duotone + regular) + 2 script
      (jsdelivr GSAP + ScrollTrigger).
- [x] Скачать woff2 шрифты + CSS Fontshare → `dist/assets/fonts/fontshare/`
      (4 файла: ClashDisplay 600/700 + GeneralSans 500/600) +
      `dist/assets/css/fontshare.css` (local paths)
- [x] Скачать woff2 + CSS Google Fonts → `dist/assets/fonts/google/`
      (8 уникальных файлов: DM Sans latin + latin-ext variable,
      JetBrains Mono × 6 subsets cyrillic/cyr-ext/greek/vietnamese/
      latin/latin-ext) + `dist/assets/css/google-fonts.css`
- [x] Скачать Phosphor Icons woff2 + CSS → `dist/assets/vendor/phosphor/`
      (2 файла: Phosphor.woff2 + Phosphor-Duotone.woff2, CSS stripped
      до woff2-only)
- [x] Скачать GSAP + ScrollTrigger min.js → `dist/assets/vendor/gsap/`
- [x] Переписать `<link>` и `<script>` в index.html на локальные пути;
      удалить 3 preconnect'а
- [x] Smoke local: `py -m http.server` в dist/ — все 11 ресурсов
      отдают HTTP 200 с корректными размерами; CSS-relative `url(...)`
      пути резолвятся
- [ ] Deploy smoke (dev): открыть `/presentation/` и убедиться что
      CSP не блокирует (DevTools → Console) — пост-G11 после reload
      nginx, сейчас dev-инфра не трогается
- [x] CDN-URL'ы удалены из index.html (grep `cdn\.|unpkg|fontshare\.com|
      googleapis|gstatic|jsdelivr` → пусто). Контекст сохранён в
      commit message + DECISIONS.
- [ ] Commit: `feat(landing): self-host CDN assets (M07 Группа 1, C0-6)`

## Группа 2 — Landing meta + a11y (QE3, QE4, P2-7B/3) — ~3ч

- [ ] Создать `dist/assets/og/ruttrack-og-1200x630.png` (дизайн-spec
      нужен — спросить owner'а про brand-гайд)
- [ ] `index.html` `<head>`: `og:image`, `og:title`, `og:description`,
      `og:url`, `twitter:card`, `twitter:image`, `<link rel=canonical>`,
      `<meta name=robots content=index,follow>`
- [ ] JSON-LD `<script type=application/ld+json>` Organization + WebApp
      schema
- [ ] CSS `@media (prefers-reduced-motion: reduce)` — override анимаций
      на `0.001ms`
- [ ] JS `matchMedia('(prefers-reduced-motion: reduce)').addEventListener`
      + `svg.pauseAnimations?.()` для SMIL
- [ ] Smoke: Chrome DevTools emulate `prefers-reduced-motion: reduce` →
      анимации замирают
- [ ] Smoke: Telegram Preview / Twitter Card Validator на staging URL
- [ ] Commit: `feat(landing): meta + prefers-reduced-motion (M07 Группа 2, QE3/4)`

## Группа 3 — openapi-typescript type-gen (QC2) — ~2д

- [ ] `frontends/pwa` install `openapi-typescript` + `openapi-fetch` deps
- [ ] `frontends/web-panel` install
- [~] ~~`frontends/mini-app` install~~ — **skipped by owner (2026-04-21):**
      mini-app будет мигрирован copy+adapt из PWA после M12, см.
      `docs/future-ideas.md` → "Mini-app unification"
- [ ] npm script `generate:types` в PWA + web-panel — fetch `/v3/api-docs`
      каждого из 5 сервисов, типизировать в `src/api/generated/{svc}.types.ts`
- [ ] Интегрировать generated types в 1 frontend (PWA) как pilot
- [ ] Миграция web-panel на generated types (удалить ручные interface
      копии)
- [~] ~~mini-app миграция~~ — **deferred to post-M12 copy+adapt**
- [ ] Drift-guard CI check: diff generated vs committed → fail если
      changed без update
- [ ] Commit: `feat(frontend): openapi-typescript type-gen (M07 Группа 3, QC2)`

## Группа 4 — RFC 7807 error interceptor (QC3) — ~4ч

- [ ] PWA: `src/api/interceptors/problemDetails.ts` — parse + throw
      typed error
- [ ] PWA: `src/components/ErrorBoundary.tsx` + toast integration
- [ ] web-panel: `CoreModule` HttpInterceptor — `ProblemDetails` parser
      + material snackbar
- [ ] Оба: показывать `traceId` в error-toast (hidden detail + copy button)
- [ ] **Rename `fieldErrors` → `invalidParams`** в PWA error parser
      (RFC 9457 compliance, отложено с M01)
- [ ] **Rename `fieldErrors` → `invalidParams`** в web-panel error
      interceptor
- [ ] Grep проверка: `fieldErrors` отсутствует в frontends/
- [ ] Integration test: backend returns 400 + `ErrorResponse` → toast
      отображает title + detail
- [ ] Commit: `feat(frontend): RFC 7807 error interceptor (M07 Группа 4, QC3)`

## Группа 5 — NotificationCenter unification (QC1) — ~4ч

- [ ] web-panel: создать `NotificationService` (unified STOMP client)
- [ ] Заменить 3 существующих STOMP-клиента вызовами NotificationService
- [ ] WebSocket reconnect + exponential backoff в одном месте
- [ ] Повторно типизировать event payloads через generated types (QC2)
- [ ] Unit-тесты reconnect logic
- [ ] Commit: `refactor(frontend): unified NotificationService (M07 Группа 5, QC1)`

## Группа 6 — UX P2-7A/1..8 — ~2д

- [ ] PWA `PullToRefresh` (P2-7A/1) — TanStack Query refetch
- [ ] `useSwipeHandler` hook + configurable threshold (P2-7A/2)
- [ ] `useDateNavigation` single source-of-truth (P2-7A/3)
- [ ] Schedule navigation bounds (P2-7A/4) — prev/next ограничены
      семестром
- [ ] Scroll position preservation (P2-7A/5) — sessionStorage per route
- [ ] forkJoin waterfall fix для subject lookup (P2-7A/6)
- [ ] Unified DrawerMenu PWA (P2-7A/7)
- [ ] Geolocation high-accuracy + loading UX (P2-7A/8) — spinner + timeout
- [ ] Commit: `feat(pwa): UX improvements P2-7A/1..8 (M07 Группа 6)`

## Группа 7 — ConfirmWithReasonDialog (QC4) — ~2ч

- [ ] PWA shared component с Tailwind стилями
- [ ] web-panel Angular component (Material dialog)
- [ ] Заменить все `window.prompt` calls на этот component
- [ ] Reason validation: non-empty, maxLength 500
- [ ] Commit: `feat(frontend): ConfirmWithReasonDialog (M07 Группа 7, QC4)`

## Группа 8 — Lazy-loading per-role (QC5) — ~3ч

- [ ] web-panel: refactor `/admin/*`, `/teacher/*`, `/student/*`,
      `/headman/*` как lazy-loaded feature modules
- [ ] Route guards проверяют role перед загрузкой
- [ ] Bundle analyzer: initial chunk < 500KB per role
- [ ] PWA: `React.lazy(() => import(...))` для role-specific pages
- [ ] Commit: `refactor(web-panel): lazy-loading per-role (M07 Группа 8, QC5)`

## Группа 9 — StatsPage aggregate + sparklines placeholder (QC6, QC7) — ~2ч

- [ ] Проверить что `/attendance/stats/aggregate` batch endpoint
      существует (из M05); если нет — создать (минимальный)
- [ ] PWA StatsPage refactor — 1 запрос вместо N×2
- [ ] Admin-dashboard sparklines: заменить псевдо-данные на skeleton
      UI + info-badge «Графики доступны в v0.1»
- [ ] Добавить запись в `docs/future-ideas.md` о real sparklines
      endpoint `/admin/dashboard/metrics` (Prometheus-based, NEW-94)
- [ ] Commit: `feat(frontend): StatsPage aggregate + sparklines placeholder (M07 Группа 9, QC6/7)`

## Группа 10 — a11y audit baseline (P2-7B/1..4) — ~1д

- [ ] PWA: semantic HTML audit (`<main>`, `<nav>`, `<article>`, `<section>`)
- [ ] web-panel: Material aria-labels обёртки
- [ ] Landing: SMIL → CSS keyframes где возможно (P2-7B/3)
- [ ] `@axe-core/cli` CLI run → zero CRITICAL/SERIOUS findings
- [ ] `jsx-a11y` ESLint rules в PWA
- [ ] `@angular-eslint/template-accessibility` rules в web-panel
- [ ] `docs/a11y-checklist.md` (WCAG 2.1 AA baseline)
- [ ] Commit: `feat(frontend): a11y baseline + axe-core audit (M07 Группа 10, P2-7B)`

## Группа 11 — nginx per-location + PR-template (P2-9/3, NEW-74) — ~2ч

- [ ] `nginx/nginx.conf` global `client_max_body_size 2m`
- [ ] `/api/attendance/excuse/` → 25m per-location
- [ ] `/api/academic/users/` → 5m (avatar)
- [ ] `docs/nginx-config.md` (NEW-152)
- [ ] `.github/pull_request_template.md` (NEW-74) — `landing-review` +
      `docs-review` checkboxes
- [ ] GitHub labels `landing-review`, `docs-review` (manual UI)
- [ ] `docs/contributing.md` (NEW-108) — когда ревизовать лендинг/docs
- [ ] Commit: `feat(ops): nginx per-location + PR-template (M07 Группа 11, P2-9/3)`

## Группа 12 — Audit + docs close — ~3ч

- [ ] `./gradlew build` финальный — зелёный
- [ ] `npm run build` в PWA/web-panel/mini-app/landing — зелёные
- [ ] `security-auditor` + `code-reviewer` агенты на diff M07
- [ ] `@axe-core/cli` финальный run
- [ ] Hot-patches если найдутся — отдельным commit
- [ ] `CHANGELOG.md` `[Unreleased]` — M07 entries
- [ ] `docs/milestones/M07-frontend-hardening/PLAN.md` — Post-mortem секция
- [ ] `docs/milestones/README.md` — M07 → ✅ + дата
- [ ] `CLAUDE.md` — статус M07 → ✅ + дата
- [ ] `docs/milestones/NEXT-SESSION.md` — hand-off для M08
- [ ] `git tag v0.0.0-alpha.8` на финальном commit'е M07
- [ ] Commit: `docs(m07): закрытие milestone — post-mortem + CHANGELOG + hand-off`

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
