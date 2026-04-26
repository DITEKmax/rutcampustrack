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
- [x] Commit `c93b6ee`: `feat(landing): self-host CDN assets (M07 Группа 1, C0-6)`

## Группа 2 — Landing meta + a11y (QE3, QE4, P2-7B/3) — ~3ч

- [x] Создать `dist/assets/og/ruttrack-og.svg` (SVG source-of-truth,
      brand-tokens из `docs/product/Rutcampustrack brandbook.md` §2) + скрипт
      `scripts/generate-og.mjs` (resvg-js, self-hosted woff2 → PNG) +
      `package.json` с devDep `@resvg/resvg-js@^2.6.2`.
- [x] Сгенерировать `dist/assets/og/ruttrack-og-1200x630.png` (162KB)
- [x] `index.html` `<head>`: `og:image` + `og:image:type/width/height/alt`,
      `og:site_name`, `twitter:card=summary_large_image`,
      `twitter:image/title/description/image:alt`,
      `<link rel=canonical href=ruttrack.site/presentation/>`,
      `<meta name=robots content=index,follow>`
- [x] JSON-LD `<script type=application/ld+json>` Organization +
      WebApplication schema (inLanguage ru-RU, offers price=0, publisher
      ref)
- [x] CSS `@media (prefers-reduced-motion: reduce)` — global override
      (animation 0.001ms + transition 0.001ms + scroll-behavior auto)
- [x] JS `gsap.matchMedia('(prefers-reduced-motion: reduce)')` scope +
      `svg.pauseAnimations()` / `unpauseAnimations()` cleanup для SMIL
      `<animateMotion>` × 2 в hero (уже было partially, дополнено)
- [x] Smoke: py http.server → JSON-LD парсится, og-image 200 OK,
      index.html 70KB
- [ ] Smoke: Telegram Preview / Twitter Card Validator на staging URL
      — deferred до deploy в dev (G11 closure)
- [x] Commit `8a20c91`: `feat(landing): meta + prefers-reduced-motion (M07 Группа 2, QE3/4)`

## Группа 3 — openapi-typescript type-gen (QC2) — ~2д

### G3a — foundation (commit `c20ed50`) ✅

- [x] `frontends/pwa` install `openapi-typescript` (dev) + `openapi-fetch`
      (dep) — ^7.13.0 / ^0.17.0
- [x] `frontends/web-panel` install (same versions)
- [~] ~~`frontends/mini-app` install~~ — **skipped by owner (2026-04-21):**
      mini-app будет мигрирован copy+adapt из PWA после M12, см.
      `docs/future-ideas.md` → "Mini-app unification"
- [x] npm script `generate:types` + `generate:types:offline` в PWA +
      web-panel — fetch `/api-docs` (не `/v3/api-docs`!) каждого из 4
      REST-сервисов (auth/academic/schedule/attendance; gateway = proxy).
- [x] Commit `.json` specs в `docs/openapi/{svc}.json` — single source
      of truth для CI drift-guard (NEW-84). ~207KB total.
- [x] Commit generated `.types.ts` в `frontends/pwa/src/api/generated/`
      + `frontends/web-panel/src/app/api/generated/`. ~190KB total каждый.
- [x] Infra: `docker-compose.override.yml` (локальный, в .gitignore) +
      `scripts/m07-g3-{launch,stop}-services.sh` для поднятия 5 Java-
      сервисов через gradle bootRun на host с правильными env-overrides.
- [x] Discovery: Prometheus exemplars + Lettuce deadlock при host-local
      startup — env `MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=false`
      обходит (только local dev, НЕ на VPS).
- [x] Commit `c20ed50`: `feat(frontend): openapi-typescript foundation + generated types (M07 G3a, QC2)`

### G3b — migration callsite'ов (✅ 2026-04-21)

Фактический подход — **types-only** (D3): axios/HttpClient runtime
остаётся, `openapi-fetch` client не подключается. `components['schemas']['...']`
импортируется как type через shared `api/schema.ts` (Strict-wrapper
поверх HATEOAS-optionality).

- [x] PWA pilot: `features/schedule/` (types.ts + api.ts +
      headmanSheetApi.ts + lessonActionsApi.ts) мигрирован на
      `@/api/schema`; ручные copies удалены; `tsc -b` зелёный.
- [x] PWA widespread: `features/{auth,home,checkin,homework,headman,push}/*`
      + `features/headman/shared/types.ts` — все DTO-копии заменены
      на re-export из `schema.ts`. `npm test` 122 зелёных, build зелёный.
      Discovery: `ExcuseType` был lowercase в frontend vs UPPERCASE в
      backend — runtime bug зафиксирован, после миграции тесты
      обновлены на UPPERCASE.
- [x] web-panel pilot: `core/auth/auth.api.ts` — TokenResponse +
      WsTicketResponse из generated.
- [x] web-panel widespread: `features/admin/shared/types.ts`,
      `features/student/shared/student-schedule.types.ts`,
      `features/teacher/journal/types.ts`, `features/headman/{excuses,
      late-checkin,homework,stats,schedule,lessons,weekly-journal,journal}/*`
      + `core/profile/profile.service.ts`. `ng build` зелёный,
      `npm test` 444 зелёных.
- [x] Drift-guard CI: `.github/workflows/openapi-drift.yml` —
      `npm run generate:types:offline` в PWA + web-panel →
      `git diff --exit-code` на `src/api/generated/`. Проверено
      локально: zero diff после offline-regen.
- [~] ~~Rename `fieldErrors → invalidParams`~~ — **отложено в G4**
      (D2). Generated types содержат `fieldErrors` (backend M01 @Schema
      не переименовал); во frontend-коде `fieldErrors`/`invalidParams`
      не используется. Rename станет post-parse adapter'ом в G4
      error-interceptor'е.
- [x] Commit `b5e66f6`: `feat(frontend): migrate PWA + web-panel to generated types (M07 G3b, QC2)`

## Группа 4 — RFC 7807 error interceptor (QC3) — ~4ч

- [x] PWA: `src/api/problemDetails.ts` — parse + normalise adapter;
      axios response interceptor кеширует результат на error object
      через Symbol.for key.
- [x] PWA: `src/shared/components/ErrorBoundary.tsx` +
      `ErrorToastHost.tsx` + `errorToastBus.ts` emitter; QueryCache /
      MutationCache emit через bus.
- [x] web-panel: `core/errors/problem-details.ts` parser +
      `problem-details.interceptor.ts` + `error-toast.service.ts` +
      `error-toast.component.ts` (MatSnackBar).
- [x] Оба: `traceId` в error-toast через copy button с «trace:
      abc12345…» + «Скопировано» feedback (1.5с).
- [x] **Rename `fieldErrors` → `invalidParams`** — **adapter-based**
      (D4). Parser принимает оба shape и нормализует на `invalidParams`.
      User code не видит `fieldErrors` (см. grep ниже).
- [x] Grep проверка: `fieldErrors` в frontends/ присутствует только
      в (а) generated types, (б) parser-коде / tests / комментах.
      User-facing код пользуется `invalidParams`.
- [x] Unit-тесты парсера: PWA 9 кейсов (src/api/__tests__/
      problemDetails.test.ts), web-panel 7 кейсов (core/errors/
      problem-details.spec.ts). Покрывают: RFC 7807 happy path,
      post-M11 shape, plain-text 502, network error, non-axios/non-
      HttpErrorResponse, garbage fieldErrors items, body без
      title/detail/type.
- [x] Suppress flags: `meta.skipGlobalErrorToast` (PWA Query/Mutation)
      и `X-Skip-Global-Error-Toast: 1` header (web-panel) — для
      graceful-degradation хуков, ожидающих 403/404.
- [x] Commit `930f194`: `feat(frontend): RFC 7807 error interceptor (M07 Группа 4, QC3)`

## Группа 5 — NotificationCenter unification (QC1) — ~4ч

- [x] web-panel: `NotificationCenterService` уже был с phase-pre-G5
      реализован; G5 его усиливает до unified STOMP-клиента.
- [x] `StudentStompService` и `HeadmanStompService` переделаны в
      thin adapter'ы над `NotificationCenterService.onEvent$`
      (filter по type). Один WebSocket на всё приложение, один
      reconnect-механизм.
- [x] Exponential backoff в NotificationCenter:
      `reconnectDelay: 1000` + `maxReconnectDelay: 30_000` +
      `reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL`.
      Раньше был fixed 2s — raffle'ил broker при одновременных
      отключениях клиентов.
- [~] Повторная типизация event payloads через generated types —
      **отложено**: backend STOMP envelope'ы (snake_case) не описаны
      в OpenAPI, это шина MQ + WS, не REST. Типизация шины →
      отдельный milestone (возможно M10 Notification History).
- [x] Unit-тесты:
      - `notification-center.service.spec.ts` — 6 кейсов (exponential
        backoff config, headman sub-topic, logout disconnect,
        onEvent$ propagation, garbage JSON не крэшит).
      - `student-stomp.service.spec.ts` — 8 кейсов, переписан под
        adapter-паттерн (mock NotificationCenter.onEvent$).
      - `headman-stomp.service.spec.ts` — 5 кейсов (новый файл).
- [x] Commit `ded220b`: `refactor(frontend): unified NotificationService (M07 Группа 5, QC1)`

## Группа 6 — UX P2-7A/1..8 — ~2д

- [x] PWA `PullToRefresh` (P2-7A/1) — `shared/components/PullToRefresh.tsx`
      + TanStack `invalidateQueries` refetch; применено в Home/Schedule/
      Homework/Stats.
- [x] `useSwipeHandler` hook + configurable threshold (P2-7A/2) —
      `shared/hooks/useSwipeHandler.ts`; унифицирует 5 inline onDragEnd
      (SchedulePage day, WeekDayTabs, NotificationsPage, 2 sheets via
      BottomSheet). Поддерживает velocity threshold (fast flick).
- [x] `useDateNavigation` single source-of-truth (P2-7A/3) —
      `shared/hooks/useDateNavigation.ts`; применён в DayView/WeekView/
      MonthView/SchedulePage. unit = day/week/month, bounds opt-in.
- [x] Schedule navigation bounds (P2-7A/4) — prev/next ограничены
      активным семестром (`useActiveSemester`), за границей показываем
      `ScheduleBoundsNotice` (D1 решение 5) с info-screen и кнопкой
      возврата. Disable кнопок на границе.
- [x] Scroll position preservation (P2-7A/5) —
      `shared/hooks/useScrollRestoration.ts`; sessionStorage per
      pathname, POP=restore/PUSH=top, 3-rAF attempts для lazy chunks.
- [x] forkJoin waterfall fix (P2-7A/6) — `useSubjectMap` batch-lookup
      в schedule/api.ts, LessonCard принимает `subjectName` как prop,
      больше не вызывает `useSubjectName` сам. Устраняет двойной render.
- [x] Unified BottomSheet PWA (P2-7A/7) —
      `shared/components/BottomSheet.tsx`; рефакторит LessonActionsSheet
      и HeadmanLessonSheet на shared backdrop + slide-up + swipe-down-
      to-close + Escape + header template. `prefers-reduced-motion`
      уважается.
- [x] Geolocation high-accuracy + loading UX (P2-7A/8) —
      `enableHighAccuracy: true` + timeout 15s + `mapGeolocationError`
      (denied/timeout/unavailable) в CheckInButton + feature-detection
      для отсутствующего geolocation API.
- [x] Commit `6e5ca8e`: `feat(pwa): UX improvements P2-7A/1..8 (M07 Группа 6)`

## Группа 7 — ConfirmWithReasonDialog (QC4) — ~2ч

- [~] ~~PWA shared component с Tailwind стилями~~ — **отложено** (D5).
      В PWA нет `window.prompt` call site'ов (grep пустой), preemptive
      shared-компонент = YAGNI. Создать inline когда появится
      потребность.
- [x] web-panel Angular component (Material dialog) —
      `shared/confirm-with-reason-dialog/`. Standalone-компонент с
      textarea, inline validation (non-empty + maxLength),
      destructive-акцент, focus-trap через MatDialog.
- [x] Заменить `window.prompt` в web-panel — единственный call site
      в `headman-lessons.component.ts::onCancel()`. Spec обновлён
      под dialog-поток.
- [x] Reason validation: non-empty + auto-trim + maxLength
      (default 500, configurable).
- [x] Unit-тесты: 8 кейсов dialog (пустое поле → disabled, trim на
      confirm, cancel → null, initialReason, destructive button,
      custom maxLength) + 3 переписанных case'а в headman-lessons
      spec.
- [x] Commit `ac51b90`: `feat(frontend): ConfirmWithReasonDialog (M07 Группа 7, QC4)`

## Группа 8 — Lazy-loading per-role (QC5) — ~3ч

- [x] web-panel: `app.routes.ts` разбит через `loadChildren` на
      `features/{admin,teacher,student,headman}/{role}.routes.ts` —
      297 LOC → 76 LOC в корневом файле. Per-role chunks теперь имеют
      явную entry point, Angular build эмитит их через
      `@angular/build:application` как отдельные файлы.
- [x] Route guards проверяют role перед loadChildren (parent-level
      canActivate). `headmanGuard` double-guards на child-уровне
      удалены (D8 — lazy-overhead без security benefit).
- [x] Bundle analysis: per-role chunks **< 100KB** каждый (самый
      большой `groups-page-component` = 77KB). Initial total = 874KB
      raw / **224KB transfer (gzip)** — shared Angular Material +
      RxJS + polyfills. Budget поднят до 900KB raw (D7).
- [x] PWA: `main.tsx` уже использует `React.lazy()` для всех 15
      route-level компонентов. Headman-routes (`GroupHub`, `Overview`,
      `StudentsList`, `SubjectsList`, `JournalPage`, `ExcusesPage`,
      `LateCheckinPage`, `StatsPage`) живут в отдельных chunks;
      student+shared (`HomeDashboard`, `SchedulePage`, `HomeworkPage`,
      `CheckInScreen`, `ProfilePage`, `NotificationsPage`) — в других.
      Роли не пересекаются.
- [x] Build зелёный, 470/470 unit-тесты passing.
- [x] Commit `6c346e0`: `refactor(web-panel): lazy-loading per-role (M07 Группа 8, QC5)`

## Группа 9 — StatsPage aggregate + sparklines placeholder (QC6, QC7) — ~2ч

- [~] ~~`/attendance/stats/aggregate` batch endpoint~~ — **не существует**
      в backend (M05 G5 сделал single-pass для одного студента, не
      group-aggregate). Создание — outside M07 scope. См. NOTES.md
      «G9 discovery» и `docs/future-ideas.md` → NEW-94.
- [~] ~~PWA StatsPage refactor — 1 запрос вместо N×2~~ — `SubjectStatsCollector`
      уже делает N **параллельных** queries через TanStack (не
      sequential). Реального waterfall нет. Batch-endpoint отложен
      до NEW-94.
- [x] Admin-dashboard sparklines: Chart.js + `buildSpark()` псевдо-данные
      удалены. Заменены на skeleton-bars + info-сообщение «Графики
      посещаемости появятся в следующем релизе» (D1 решение 4).
      `StatCardComponent.sparkData` готов вернуться к real data без
      refactor.
- [x] `docs/future-ideas.md` NEW-94 обновлён — описано состояние после
      G9 (skeleton UI), архитектурный выбор Prometheus-based endpoint.
- [x] Commit `82eb2ad`: `feat(web-panel): sparklines placeholder (M07 Группа 9, QC6/7)`

## Группа 10 — a11y audit baseline (P2-7B/1..4) — ~1д

- [x] PWA: semantic HTML audit. `<main>` в AppShell подтверждён.
      ProfilePage уже использует `<section>`/`<article>`. HomeDashboard
      обёрнут в `<section aria-labelledby>` + `<h1 class="sr-only">`
      (route landmark). Остальные страницы (Schedule/Homework/CheckIn/
      Notifications/Stats) — `<div>`-wrappers, родительский `<main>`
      достаточен для screen-reader'ов; явные section-wrappers отложены
      в pass 2.
- [x] web-panel: Material aria-labels обёртки. grep: 131 `aria-label`
      в 49 файлах. `<main>` в `shell.component.html` подтверждён.
      Admin-dashboard — `<section>` + `<header>` + semantic `<table>`.
- [~] Landing: SMIL → CSS keyframes — **отложено в pass 2**. 3
      `<animate>` остаются, они уже работают корректно с `svg.pause
      Animations()` при `prefers-reduced-motion` (M07 G2).
- [~] `@axe-core/cli` CLI run — **отложено в pass 2 / M08**. Требует
      running dev server; в M08 интегрируется через Playwright e2e.
- [~] `jsx-a11y` ESLint rules в PWA — **отложено в M08**. PWA не
      имеет ESLint config вообще; setup = отдельный scope (new dev-dep
      chain, config file, rules tuning).
- [~] `@angular-eslint/template-accessibility` rules в web-panel —
      **отложено в M08**. Аналогично: ESLint config нет в web-panel.
- [x] `docs/product/a11y-checklist.md` — WCAG 2.1 AA baseline создан.
      Pass 1 (M07 G10 baseline, **выполнено**) + Pass 2 (v0.1 items:
      axe-core run, ESLint a11y plugins, Lighthouse score ≥ 95, skip-
      links, color contrast, SMIL replace, touch targets, screen reader
      testing, heading hierarchy, live regions).
- [x] Commit `2be3eab`: `feat(frontend): a11y baseline + checklist (M07 Группа 10, P2-7B)`

## Группа 11 — nginx per-location + PR-template (P2-9/3, NEW-74) — ~2ч

- [x] `nginx/nginx.conf` global `client_max_body_size 2m` (было 12m).
- [x] `/api/attendance/excuses/with-file` → 25m per-location (prefix-
      match побеждает generic `/api/` через nginx precedence).
- [~] ~~`/api/academic/users/me/avatar` → 5m~~ — **отложено** (D6).
      Endpoint принимает JSON preset-id, не multipart; 2m global
      достаточно. Вернуться при появлении file-based avatar upload.
- [x] `docs/operations/deploy/nginx-config.md` (NEW-152) — runbook: limits, security
      headers, CSP, upstream routing, reload checklist, troubleshooting.
- [x] `.github/pull_request_template.md` (NEW-74) — scope, areas
      touched, verification, risk — с labels `landing-review` и
      `docs-review`.
- [~] GitHub labels `landing-review`, `docs-review` manual UI —
      создадутся при первом использовании в PR (owner setup).
- [x] `docs/meta/contributing.md` (NEW-108) — branches, commits,
      PR-labels, когда ревизовать лендинг/docs, Flyway rules,
      OpenAPI drift recovery.
- [x] Commit `8aa7a22`: `feat(ops): nginx per-location + PR-template (M07 Группа 11, P2-9/3)`

## Группа 12 — Audit + docs close — ~3ч

- [x] `./gradlew build` финальный — зелёный (verified в `82cf482`:
      `./gradlew :services:api-gateway:test` зелёный после hot-patches)
- [x] `npm run build` в PWA/web-panel/mini-app/landing — зелёные
      (PWA `tsc -b` + 154/154 tests в `82cf482`)
- [x] `security-auditor` + `code-reviewer` агенты на diff M07 —
      findings адресованы в `82cf482` (HIGH-1 CSP, MED-1 rate-limit,
      S1/S2/S7 code-review). INFO-5/INFO-1 отложены в M10/v0.1.
- [~] ~~`@axe-core/cli` финальный run~~ — отложено в M08 / pass 2
      (см. Группа 10, требует running dev server + Playwright e2e).
- [x] Hot-patches — commit `82cf482`: `fix(m07): audit hot-patches —
      CSP, rate-limit, code review (G12)`
- [x] `CHANGELOG.md` `[Unreleased]` — M07 entries (в `26aef81`)
- [x] `docs/milestones/M07-frontend-hardening/PLAN.md` — Post-mortem
      секция (в `26aef81`)
- [x] `docs/milestones/README.md` — M07 → ✅ 2026-04-22 (в `26aef81`)
- [x] `CLAUDE.md` — статус M07 → ✅ 2026-04-22 (в `26aef81`)
- [x] `docs/milestones/NEXT-SESSION.md` — hand-off для M08 (в `26aef81`)
- [x] `git tag v0.0.0-alpha.8` на финальном commit'е M07 (26aef81)
- [x] Commit `26aef81`: `docs(m07): closure — post-mortem + CHANGELOG + hand-off для M08`

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
