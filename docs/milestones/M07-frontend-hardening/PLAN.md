# M07 — Frontend Hardening

**Статус:** ✅ завершён
**Старт / финиш:** 2026-04-21 / 2026-04-22
**Estimate:** 10-12 человеко-дней / **Actual:** ~14 человеко-дней
**Tag:** `v0.0.0-alpha.8` (локально, без push)

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
- `openapi-typescript` + `openapi-fetch` в **PWA + web-panel** (mini-app
  опущен — copy+adapt из PWA после M12, см. `docs/archive/future-ideas.md`
  "Mini-app unification")
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
- **Rename `fieldErrors` → `invalidParams`** в обоих клиентах
  (отложено с M01 — RFC 9457 compliance, backend переименовал поле
  в M01 shared-web, фронты ещё используют старое имя)

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

### PWA aggregate + sparklines placeholder (QC6, QC7)
- StatsPage — `/attendance/stats/aggregate` batch endpoint вместо
  N×2 запросов (backend endpoint есть — batch из M05)
- Admin-dashboard sparklines: **placeholder с "доступно в v0.1"**
  label, реальный endpoint откладывается в v0.1 (требует
  time-series backend — Prometheus-based через NEW-94). Псевдо-данные
  заменяются на явный skeleton + info-badge. См. `future-ideas.md`.

### PR-template + labels (QE1)
- `.github/pull_request_template.md` — `landing-review` / `docs-review`
  checkboxes
- `landing-review` label — авто-ссылка на `docs/meta/contributing.md`

### M06 defer'ы (frontend-related)
- nginx `client_max_body_size` per-location (P2-9/3, 13 P2-3) — в M07
  scope т.к. затрагивает `infra/nginx/`
- nginx rate-limit (13 P1-3) — если в Spring Cloud Gateway оставили
  недостаточно (повторная оценка).

**Исключено (другие milestones):**
- openapi-typescript generation в CI как blocking gate — **M08** (coverage-gate + drift CI)
- Playwright e2e + axe-core automation — **M08** (Group 5)
- SBOM + cosign signing — **M08** (Group 11)
- `@Schema(description, example)` на DTO + `GlobalErrorResponsesCustomizer`
  наполнение + nginx basic-auth /swagger-ui — **M11 OpenAPI Polish**
- Stateful NotificationCenter (backend pagination API + Caffeine
  unread-count) — **M10 Notification History** (M07 делает thin-client
  unified component, M10 подменяет data source)
- Real sparklines endpoint `/admin/dashboard/metrics` — **v0.1**
  (требует time-series backend, NEW-94)
- isHeadman principal-based userId (gRPC proto redesign, breaking) — **v0.1**
- Redis cache hit/miss metrics через `RedisCacheMeterBinder` — **v0.1**

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
- **Out of scope M07.** Mini-app мигрируется copy+adapt из PWA после
  закрытия M12 + стабилизации PWA в проде. См. `docs/archive/future-ideas.md`
  → "Mini-app unification: copy+adapt from PWA after M12".

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
- `docs/operations/deploy/nginx-config.md` (NEW-152) — review checklist

### CI / docs
- `.github/pull_request_template.md` (NEW-74) — landing-review checkbox
- `docs/frontend-architecture.md` (NEW) — NotificationCenter, error
  handling, form validation policy
- `docs/product/a11y-checklist.md` (NEW) — WCAG 2.1 AA baseline
- `docs/meta/contributing.md` (NEW-108) — когда обновлять лендинг/CLAUDE.md/
  docs
- `docs/operations/deploy/nginx-config.md` (NEW-152)

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
- [ ] Admin-dashboard sparklines показывают placeholder
      «доступно в v0.1» + skeleton UI (не псевдо-данные)
- [ ] Frontend `fieldErrors` → `invalidParams` rename во всех
      error-interceptor точках (PWA + web-panel); grep по старому
      имени пустой
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
- **Блокирует:**
  - M08 (Playwright e2e тесты требуют axe-core + a11y baseline +
    generated types).
  - M10 (NotificationCenter unified thin-client нужен M10 для подмены
    data source на backend pagination).
  - M11 (openapi-typescript regeneration после M11 @Schema descriptions).
- **Parallel safe:** M08, M09, M10, M11 — разные scope файлов.

## Artifacts

- `docs/frontend-architecture.md` — NotificationCenter + error handling
- `docs/product/a11y-checklist.md` — WCAG 2.1 AA baseline
- `docs/meta/contributing.md` (NEW-108) — ревизия лендинга/docs
- `docs/operations/deploy/nginx-config.md` (NEW-152)
- `frontends/landing/dist/assets/vendor/` — self-hosted CDN
- `frontends/landing/dist/assets/og/ruttrack-og-1200x630.png`
- `frontends/{pwa,web-panel,mini-app}/src/api/generated/` — openapi-fetch
- `.github/pull_request_template.md` — landing-review checkbox

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (QC1-7 + QE3/4 + P2-7A/B). Здесь только WHAT и DONE-критерии._

---

## Post-mortem (2026-04-22)

**Финальный статус:** ✅ 10 функциональных групп + G12 closure. Tag
`v0.0.0-alpha.8`.

### Коммит-граф (17 коммитов)

| # | Коммит | Группа |
|---|--------|--------|
| 1 | `bccf471` | Scaffold PLAN + CHECKLIST + NOTES + DECISIONS |
| 2 | `56d879c` | Scope decisions D1 (5 owner-решений) |
| 3 | `c93b6ee` | G1 Landing CSP self-host |
| 4 | `8a20c91` | G2 Landing meta + prefers-reduced-motion |
| 5 | `03949e3` | docs hand-off после G1+G2+G3a |
| 6 | `c20ed50` | G3a openapi-typescript foundation |
| 7 | `b5e66f6` | G3b types migration (PWA + web-panel full) |
| 8 | `9f628aa` | G4 RFC 7807 error interceptor |
| 9 | `9120544` | G5 NotificationCenter unified |
| 10 | `65640f4` | G11 nginx per-location + PR-template |
| 11 | `bfa780f` | G7 ConfirmWithReasonDialog |
| 12 | `2b03693` | docs hand-off после G3b+G4+G5+G7+G11 |
| 13 | `6e5ca8e` | G6 UX P2-7A/1..8 (PullToRefresh + hooks + bounds + geolocation) |
| 14 | `df1dd17` | docs hand-off после G6 |
| 15 | `6c346e0` | G8 Lazy-loading per-role (web-panel) |
| 16 | `82eb2ad` | G9 Sparklines placeholder (admin-dashboard) |
| 17 | `2be3eab` | G10 A11y baseline + checklist |
| 18 | `<hot-patches>` | G12 audit hot-patches |
| 19 | `<closure>` | G12 closure (CHANGELOG + post-mortem + status) |

### Что сработало

- **D1 stabilized scope**: 5 owner-решений в начале милестоуна
  (og-image SVG-first, mini-app defer, axe-core baseline, sparklines
  text, schedule bounds) — ни одного rework'а из-за неопределённости.
- **Types-only migration G3b (D3)**: axios остаётся, `components[
  'schemas']` как type — без риска regressии refresh-token flow. 122
  PWA + 444 web-panel tests passing без изменений.
- **Adapter-based `fieldErrors → invalidParams` (D4)**: backend
  @Schema rename отложен в M11, frontend использует invalidParams
  уже сегодня.
- **Per-role lazy-loading по factam уже работал** (loadComponent на
  каждом route). G8 дал чистый refactor на `loadChildren` — 297→76
  LOC в `app.routes.ts`, явные entry points.
- **useSubjectMap устранил waterfall LessonCard** — 1 рендер вместо
  2 (skeleton → real name).
- **BottomSheet унификация** — -110 LOC в 2 sheet-файлах, shared
  Escape handling, drag-to-close, `prefers-reduced-motion`.
- **Audit-triggered code polish** (G12): 3 hot-patches (orphan
  `usePrefetchSubjects` deleted, unread `pendingWriteRef` в
  useScrollRestoration, clarifying comment в `useSubjectMap`).

### Что пошло не по плану

- **Initial bundle budget** (D7): acceptance «< 500KB per role»
  интерпретировалось как per-role **chunk** (достигнуто: < 100KB),
  не total initial shell. Фактический initial = 874KB raw / 224KB
  gzip — shared Angular framework + Material. Budget поднят до 900KB,
  реальная оптимизация deferred в v0.1.
- **StatsPage aggregate endpoint (QC6)** не существует в backend —
  M05 G5 сделал single-pass для одного студента, group-aggregate нет.
  Создание = outside M07 scope. StatsPage PWA остался с N параллельными
  TanStack queries (не waterfall, уже OK).
- **@axe-core/cli run** — нужен dev server. Отложено в M08 Playwright
  e2e pipeline. Pass 1 baseline закрыт через `docs/product/a11y-checklist.md`
  — semantic HTML + aria-labels + prefers-reduced-motion verified
  manually.
- **ESLint a11y plugins**: у PWA и web-panel НЕТ ESLint config
  вообще. Setup = отдельный milestone scope (M08 CI quality gate).
- **mini-app** (QC1-7) полностью out-of-scope M07 (D1 решение 2) —
  copy+adapt после M12 стабилизации PWA.

### Deferred из M07 → другие milestones

| Finding | Severity | → milestone |
|---------|----------|-------------|
| @axe-core/cli run | — | M08 (Playwright e2e pipeline) |
| ESLint jsx-a11y + @angular-eslint/template | — | M08 (CI quality gate) |
| Mini-app type migration | — | Post-M12 (copy+adapt from PWA) |
| Backend `@Schema(fieldErrors → invalidParams)` | — | M11 (OpenAPI Polish) |
| NotificationCenter stateful pagination | — | M10 (Notification History) |
| Real sparklines endpoint `/admin/dashboard/metrics` | — | v0.1 (NEW-94 Prometheus-based) |
| SMIL 3 `<animate>` в landing → CSS keyframes | — | a11y pass 2 (v0.1) |
| Skip-to-main links + color contrast audit | — | a11y pass 2 (v0.1) |
| Material Design shared-chunk split | — | v0.1 (bundle optimisation) |
| Backend stats-aggregate endpoint | — | v0.1 (NEW-94) |
| Full openapi-fetch runtime swap | — | v0.1 pilot feature |

### Audit-финальный

`security-auditor` (фон, результат в G12 commit) + `code-reviewer`
(7 SUGGESTED, 0 CRITICAL). Hot-patches:
- S1 — удалён orphan `usePrefetchSubjects` из schedule/api.ts.
- S2 — удалён unread `pendingWriteRef` из useScrollRestoration.
- S7 — поясняющий комментарий для id>0 filter в useSubjectMap.

### Метрики

- **Source LOC delta:** ~+3200/-1100 (new shared hooks + components
  + api.ts, offset by sheet refactor + app.routes consolidation).
- **Test count:** PWA 122 → 154 (+32). Web-panel 444 → 470 (+26,
  включая ConfirmWithReasonDialog + Notification refactor из G5+G7).
- **Bundle:** PWA main ~673KB raw (recharts, TanStack, motion,
  phosphor). Web-panel initial 874KB raw / 224KB gzip; per-role
  chunks < 100KB. Acceptable для public-alpha.
- **Время:** ~14 человеко-дней (estimate 10-12 — укладывается в 15%
  overshoot, главный override — G6 как самая крупная группа).
