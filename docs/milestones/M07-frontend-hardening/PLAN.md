# M07 — Frontend Hardening

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 10-12 человеко-дней

---

## Scope

Frontend-фокус: CSP self-host, a11y baseline, openapi-typescript type-gen,
UX фиксы из P2-7A, и accumulated debt'ы M06 defer'ов, затрагивающие
frontend-stack.

Источники:
- `99-executive-summary.md` — Фаза 2 C0-6, Фаза 4 P1-C, Фаза 5 P2-7A/7B
- `OWNER-ANSWERS.md`:
  - QC1-7 (строки 1809-2053) — frontend reuse
  - QE3/4 (строки 2430-2510) — landing a11y + meta
  - P2-7A/1..8 (5215-5365) — UX fixes
  - P2-7B/1..4 (5366-5550) — a11y
  - 13 P0-4 / 12 P0-1 / C0-6 — CSP self-host landing
- `13-infra-docker-ci.md` P0-4 (CSP)
- `12-frontend-landing.md` (CSP, meta, a11y)
- `09-frontend-pwa.md`, `10-frontend-web-panel.md` (UX, a11y, routing)

**Включено:**

### CSP self-host (C0-6, 12 P0-1, 13 P0-4)
- Склонировать Fontshare/Google Fonts/unpkg GSAP/CDN ресурсы в
  `frontends/landing/dist/assets/vendor/`
- Переписать `<link>`/`<script>` на локальные пути
- CSP корневого nginx НЕ меняется (`'self'` остаётся)
- SRI integrity-хеши для оставшихся (если будут)
- Self-host fonts: woff2 файлы в `/assets/fonts/` + `@font-face` CSS

### Landing meta + a11y (QE3, QE4, P2-7B/3)
- `<head>`: `og:image`, `twitter:card`, `canonical`, `robots`, JSON-LD
- Brand og-image `1200×630` (NEW — дизайн-spec нужен)
- `prefers-reduced-motion` CSS media query + SMIL `pauseAnimations()`
- stylelint + a11y-rule check для media-query

### openapi-typescript type-gen (QC2)
- `openapi-typescript` + `openapi-fetch` в PWA/web-panel/mini-app
- Генерировать `.types.ts` из `/v3/api-docs` всех 5 backend-сервисов
- npm scripts `generate:types` в каждом frontend
- Update TanStack Query/Angular HttpClient использовать generated types
- Удалить ручные interface-копии
- CI drift-guard (NEW-84) — generated TS in-sync check

### Frontend error handling (QC3)
- RFC 7807 ProblemDetails interceptor — PWA (TanStack Query
  mutation/query error parser), web-panel (Angular HttpInterceptor)
- `traceId` в toast/log per error
- ErrorBoundary fallback для React PWA
- generated type `components['schemas']['ErrorResponse']` везде

### NotificationCenter unification (QC1)
- 3 STOMP-клиента в web-panel → один shared NotificationService
- WebSocket reconnect/backoff в одном месте
- Типизация через QC2 openapi-ts events schema

### UX (P2-7A/1..8)
- Pull-to-refresh в PWA (P2-7A/1)
- `useSwipeHandler` + threshold (P2-7A/2)
- `useDateNavigation` single-source (P2-7A/3)
- Schedule navigation bounds (P2-7A/4)
- Scroll position preservation (P2-7A/5)
- forkJoin waterfall fix (P2-7A/6)
- DrawerMenu единый (P2-7A/7)
- Geolocation high-accuracy + loading UX (P2-7A/8)

### ConfirmWithReasonDialog (QC4)
- Shared component PWA/web-panel для `window.prompt` replacement
- Валидация reason (non-empty, maxLength)

### Lazy-loading per-role (QC5)
- Angular web-panel: `/admin/*`, `/teacher/*`, `/student/*`, `/headman/*`
  feature modules lazy-loaded
- PWA React.lazy по ролям

### PWA aggregate + sparklines (QC6, QC7)
- StatsPage — `/attendance/stats/aggregate` batch endpoint вместо
  N×2 запросов
- Admin-dashboard real sparklines (replace псевдо-данных)

### PR-template + labels (QE1)
- `.github/pull_request_template.md` — `landing-review` / `docs-review`
  checkboxes
- `landing-review` label — авто-ссылка на `docs/contributing.md`

### M06 defer'ы (frontend-related)
- nginx `client_max_body_size` per-location (P2-9/3, 13 P2-3) — в M07
  scope т.к. затрагивает `infra/nginx/`
- nginx rate-limit (13 P1-3) — если в Spring Cloud Gateway оставили
  недостаточно (повторная оценка).

**Исключено (другие milestones):**
- openapi-typescript generation в CI как blocking gate — M08 (coverage-gate)
- Playwright e2e + axe-core automation — M08
- SBOM + cosign signing — M08
- isHeadman principal-based userId (gRPC proto redesign, breaking) — v0.1
- Redis cache hit/miss metrics через `RedisCacheMeterBinder` — v0.1

## Модули / изменения

### Frontend — PWA (`frontends/pwa/`)
- `src/api/generated/` — openapi-fetch generated client (NEW)
- `src/api/types/*.d.ts` — generated types
- `src/hooks/useSwipeHandler.ts` + `useDateNavigation.ts` (NEW)
- `src/components/PullToRefresh.tsx` (NEW)
- `src/components/ConfirmWithReasonDialog.tsx` (NEW)
- `src/components/ErrorBoundary.tsx` (NEW)
- `src/pages/StatsPage.tsx` — batch endpoint refactor
- `package.json` — `openapi-typescript`, `openapi-fetch` deps
- `vite.config.ts` — `generate:types` npm script
- a11y audit of all interactive components (semantic HTML,
  aria-labels, focus management)

### Frontend — web-panel (`frontends/web-panel/`)
- `src/app/core/services/notification.service.ts` (unified from 3
  STOMP clients)
- `src/app/core/services/error.interceptor.ts` (RFC 7807 parser)
- `src/app/features/{admin,teacher,student,headman}/...loadChildren`
  lazy setup
- `src/app/api/generated/` — openapi-fetch generated client
- `src/app/features/admin/dashboard/sparklines.component.ts` — real data
- `angular.json` — `generate:types` script

### Frontend — mini-app (`frontends/mini-app/`)
- `src/api/generated/` — shared generated types
- Мини-набор из QC6 aggregate (если stats-подобный flow есть)

### Frontend — landing (`frontends/landing/`)
- `dist/assets/vendor/` — self-hosted Fontshare/GSAP/CDN (NEW)
- `dist/assets/fonts/` — woff2 + @font-face (NEW)
- `dist/assets/og/ruttrack-og-1200x630.png` (NEW)
- `index.html` — meta-тегов (og/twitter/canonical/robots), JSON-LD
- `styles/hero.css` — `@media (prefers-reduced-motion)` override
- `scripts/hero.js` — SMIL `pauseAnimations()` + matchMedia listener

### Infra (`infra/nginx/`, `nginx/`)
- `nginx/nginx.conf` — `client_max_body_size 2m` (global), excuse
  location 25m, avatar 5m
- `docs/nginx-config.md` (NEW-152) — review checklist

### CI / docs
- `.github/pull_request_template.md` (NEW-74) — landing-review checkbox
- `docs/frontend-architecture.md` (NEW) — NotificationCenter, error
  handling, form validation policy
- `docs/a11y-checklist.md` (NEW) — WCAG 2.1 AA baseline
- `docs/contributing.md` (NEW-108) — когда обновлять лендинг/CLAUDE.md/
  docs
- `docs/nginx-config.md` (NEW-152)

## Acceptance criteria

- [ ] Landing визуально идентичен до/после self-host (шрифты, анимации
      работают offline от CDN)
- [ ] CSP корневого nginx не изменён — `'self'` purpose-only
- [ ] `landing/index.html` содержит og:image/twitter:card/canonical/
      robots/JSON-LD — проверить через Telegram Preview / Twitter Card
      Validator
- [ ] `openapi-typescript` генерит `.d.ts` для всех 5 сервисов в 3
      frontend'ах; CI-check on drift (fail если generated out-of-sync)
- [ ] PWA + web-panel используют generated types в API-клиентах; ручные
      interface-копии удалены
- [ ] RFC 7807 interceptor работает: backend returns ProblemDetails →
      toast показывает human-readable message + traceId для debug
- [ ] PWA `useSwipeHandler`, `useDateNavigation`, `PullToRefresh`
      функциональны; swipe threshold configurable
- [ ] StatsPage использует aggregate endpoint (1 запрос вместо N×2)
- [ ] web-panel lazy-loading по ролям работает (initial bundle < 500KB
      per role)
- [ ] a11y: `npx @axe-core/cli` на dev PWA/web-panel/landing — zero
      CRITICAL/SERIOUS
- [ ] `prefers-reduced-motion` уважается в landing + PWA transitions
- [ ] ConfirmWithReasonDialog заменил все `window.prompt` calls
- [ ] nginx `client_max_body_size` per-location; 26MB upload на
      `/api/excuse/` → 413 для не-excuse location
- [ ] `./gradlew build` зелёный (frontend changes не ломают backend
      tests)
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.8`

## Dependencies

- **Блокируется:** M03b (HttpOnly cookie + ws-ticket — frontend уже
  использует эти интерфейсы). ✅ готово.
- **Блокирует:** M08 (Playwright e2e тесты требуют axe-core + a11y
  baseline + generated types).
- **Parallel safe:** M08, M09. M09 может идти параллельно (разные
  scope файлов).

## Artifacts

- `docs/frontend-architecture.md` — NotificationCenter + error handling
- `docs/a11y-checklist.md` — WCAG 2.1 AA baseline
- `docs/contributing.md` (NEW-108) — ревизия лендинга/docs
- `docs/nginx-config.md` (NEW-152)
- `frontends/landing/dist/assets/vendor/` — self-hosted CDN
- `frontends/landing/dist/assets/og/ruttrack-og-1200x630.png`
- `frontends/{pwa,web-panel,mini-app}/src/api/generated/` — openapi-fetch
- `.github/pull_request_template.md` — landing-review checkbox

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (QC1-7 + QE3/4 + P2-7A/B). Здесь только WHAT и DONE-критерии._
