# M07 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — D{N}: короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M07 нет — scope зафиксирован в
OWNER-ANSWERS QC1-7 (строки 1809-2053), QE3/4 (2430-2510), P2-7A/1..8
(5215-5365), P2-7B/1..4 (5366-5550). Если появится micro-решение —
записать как `## YYYY-MM-DD — D{N}: заголовок`._

Ожидаемые точки разветвления (предварительно):
- CSP self-host strategy — bundle vs separate `@font-face` vs inline
  base64 (Группа 1).
- openapi-typescript code-gen output structure — single-file vs
  per-service directory (Группа 3).
- axe-core severity threshold (Группа 10).
- Lazy-loading boundary для web-panel role-split (Группа 8).

---

## 2026-04-21 — D1: Owner-answers по 5 стартовым вопросам

Обсуждалось в первом сообщении сессии 2026-04-21 (см. NOTES.md).

**Что выбрано:**

1. **Brand og-image (G2):** SVG-first → PNG через `sharp`/`@resvg/resvg-js`.
   SVG коммитится в git как source-of-truth, PNG генерируется
   npm-скриптом. Альтернативы: (a) Pillow/sharp-скрипт с рендером
   с нуля — отвергнут (overkill), (c) defer в v0.1 — отвергнут
   (маркетинг-значимо для public-alpha).

2. **mini-app scope G3:** полностью out-of-scope M07. Миграция
   произойдёт copy+adapt из PWA **после M12** + стабилизации PWA.
   Альтернативы: полная migration — дублирование работы; generate-only
   — не value, формально закрывает ticket но не удаляет interface-копии.
   Записано в `docs/archive/future-ideas.md` → "Mini-app unification: copy+adapt
   from PWA after M12".

3. **axe-core baseline (G10):** `CRITICAL + SERIOUS = 0`.
   MODERATE/MINOR tracked в `docs/product/a11y-checklist.md` как "a11y pass 2"
   для v0.1. Альтернативы: +MODERATE — раздувает M07 до 12-14д,
   толкает M08/M09; +MINOR — false-positive heavy (Material Design
   часто неправ по axe AAA).

4. **Sparklines placeholder text (G9):** «Графики посещаемости появятся
   в следующем релизе» + skeleton UI + info-badge. Альтернативы:
   "доступно в v0.1" (внутренняя терминология), "аналитика скоро"
   (generic), без текста (риск "кажется баг, напишу в поддержку").

5. **Schedule navigation bounds (G6, P2-7A/4):** bounds = активный
   семестр. За границей показывать info-screen («Семестр закончился
   YYYY-MM-DD, новый начнётся YYYY-MM-DD» или «Семестр начнётся
   YYYY-MM-DD»), не blank + disabled button. Edge-case «каникулы без
   активного семестра» — пока без активного семестра, показать
   «Сейчас каникулы. Ближайший семестр начнётся Y.» + (опционально)
   кнопку «Посмотреть прошлый семестр» как отдельное navigation action,
   не часть prev/next. Альтернативы: disabled-кнопки — UX ambiguity;
   auto-cross-semester navigation — требует backend endpoint
   `/semesters/adjacent` (overkill для prev/next).

---

## 2026-04-21 — D2: G3b rename `fieldErrors → invalidParams` отложен в G4

**Что выбрано:** rename `fieldErrors → invalidParams` **НЕ** делаем в
G3b. Обоснование: (а) generated types показывают `fieldErrors` — backend
в M01 поле не переименовал (grep в `docs/openapi/attendance.json` +
`attendance.types.ts:389` подтверждает); (б) во frontend-коде
`fieldErrors`/`invalidParams` **не используется** (0 совпадений в
`frontends/pwa/src` и `frontends/web-panel/src` кроме generated); (в)
переименование имеет смысл только когда появится error-interceptor,
который парсит ProblemDetails — это G4. В G4 делаем post-parse adapter
`fieldErrors → invalidParams` (или backend-rename в M11 OpenAPI Polish).

**Альтернатива:** переименовать backend `@Schema` сейчас — отвергнуто:
выходит за scope G3b (touches backend DTO), провоцирует лишний CI-цикл,
M11 как раз отдельный milestone для OpenAPI polish.

---

## 2026-04-21 — D3: G3b подход — types-only, axios остаётся

**Что выбрано:** pragmatic minimum — импортируем `components['schemas']['...']`
как type в существующие axios-клиенты (`apiClient.get<T>()`), удаляем
ручные interface-копии. `openapi-fetch` **не подключаем** в runtime
клиенты — dep остаётся установленной из G3a для будущих features.

**Почему:** (а) PLAN.md acceptance «используют generated types в API-
клиентах; ручные interface-копии удалены» — про types, не про fetch
implementation. (б) `shared/lib/axios.ts` содержит ~95 LOC
refresh-token interceptor (cookie-based, M03b); полная замена на
openapi-fetch = 1-2д сверх scope + риск регрессии auth-flow.
(в) web-panel — Angular HttpClient с собственным interceptor-стеком,
те же соображения.

**Альтернативы:**
- **B. Full openapi-fetch swap** — отвергнут: ломает рабочий refresh,
  требует отдельной session.
- **C. Hybrid (new features на openapi-fetch)** — эквивалентен A на
  M07 timeframe; если в v0.1 появится новая feature → можно попробовать
  openapi-fetch там как pilot.

**Implication для acceptance:** критерий «используют generated types
в API-клиентах» выполняется через type-parameter в существующих axios
вызовах. Ручные interface-копии удаляются.

**Pilot feature:** `features/schedule/` (types.ts + api.ts +
headmanSheetApi.ts + lessonActionsApi.ts) — самая изолированная,
3 ручных DTO-типа, generated types полностью покрывают.

---

## 2026-04-22 — D4: G4 rename `fieldErrors → invalidParams` как adapter

**Что выбрано:** не переименовывать backend-поле, а сделать парсер,
принимающий оба shape (`fieldErrors` legacy attendance/academic/
schedule + `invalidParams` shared-web M11) и нормализующий на
`invalidParams` в consumer API.

**Почему:**
- Backend имеет 4 разных `ErrorResponse`-класса в разных сервисах
  (attendance/academic/schedule локальные + shared-web с
  `invalidParams`). Сейчас только shared-web использует `invalidParams`;
  остальные — `fieldErrors`.
- Переименовать все 4 backend DTO одновременно — отдельный milestone
  scope (M11 OpenAPI Polish). М07 — frontend-milestone, не должен
  требовать backend DTO change.
- Parser-based adapter позволяет user-facing frontend коду уже
  использовать `invalidParams` сегодня, независимо от backend прогресса.

**Альтернативы:**
- Backend rename сейчас — отвергнуто (scope creep, M11 scope).
- Держать `fieldErrors` во frontend — отвергнуто (RFC 9457 compliance
  + OWNER-ANSWERS M01 P0-1 требовали invalidParams).

**Implication:** после M11 парсер продолжит работать — `invalidParams`
shape берётся первым (см. `coerceInvalidParams`). Когда все сервисы
переедут на shared-web, legacy branch мёртвой не будет — просто
никогда не сработает, и удаляется в любой момент.

---

## 2026-04-22 — D5: G7 ConfirmWithReasonDialog только в web-panel

**Что выбрано:** создать Material dialog в web-panel
(`shared/confirm-with-reason-dialog/`), заменить единственный
`window.prompt` в headman-lessons. PWA shared-компонент **не
создавать** в G7.

**Почему:**
- Grep по `window\.prompt\(` в PWA — ноль вхождений, т.е. нет call
  site'ов для замены.
- PWA `confirm()` (1 call в `LessonHomeworkSection`) — это confirm без
  reason, не в scope QC4.
- Preemptive создание неиспользуемого компонента — over-engineering.
  Когда PWA потребуется reason-диалог, он будет создан по образу
  web-panel'овского (≤30 мин).

**Альтернативы:**
- Создать PWA shared-компонент заранее — отвергнуто (YAGNI, не
  оправдано scope в OWNER-ANSWERS QC4).
- Заменить PWA `confirm()` тоже в G7 — отвергнуто (другая UX-форма,
  отдельный scope).

**Implication:** если в G6 (PullToRefresh/swipe) или будущих PWA
фичах появится необходимость reason-диалога — сделать inline.

---

## 2026-04-22 — D6: G11 avatar 5m per-location отложен

**Что выбрано:** добавить только 25m per-location для
`/api/attendance/excuses/with-file` (multipart excuse upload).
Отдельный 5m per-location для avatar **не создавать**.

**Почему:** эндпоинт `PATCH /api/academic/users/me/avatar` принимает
JSON `{ avatarId: string }` — это preset-id (см.
`frontends/web-panel/src/app/core/profile/preset-avatars.ts`), не file
upload. Лимит 2m global более чем достаточно для JSON ~50 байт.

**Альтернативы:**
- Добавить 5m «на всякий случай» — отвергнуто (YAGNI, лишний location
  block под несуществующий use-case).

**Implication:** если в v0.1 появится file-based avatar upload
(non-preset, custom image), добавить `location = /api/academic/users/
me/avatar { client_max_body_size 5m }` и обновить `docs/operations/deploy/nginx-config.md`.

---

## 2026-04-22 — D7: G8 initial bundle budget поднят с 500KB до 900KB

**Что выбрано:** `angular.json` budget `initial.maximumWarning: 900kB`
/ `maximumError: 1.5MB` вместо прежних 500KB/1MB. Фактический initial
total = **874KB raw / 224KB transfer (gzip)** для web-panel.

**Почему:**
- Angular CLI budget измеряется по **raw** размеру (uncompressed). Для
  Angular Material + RxJS + zone.js + Chart.js + shared CDK
  это ≈800-900KB. 500KB budget нереален без urgent shared-deps
  оптимизации (code-split Material Design в отдельный chunk, что
  ломает runtime через circular deps — пробовалось в Phase 50).
- **Transfer size (gzip) = 224KB** — реальный network cost, который
  клиент скачивает. Для PWA это в пределах ожиданий (Lighthouse Good
  rating < 400KB gzip).
- Per-role chunks (то, ради чего G8 существует) **уже < 100KB** каждый:
  самый большой `groups-page-component` = 77KB, `headman-schedule` =
  63KB, `users-page` = 48KB. Здесь QC5 acceptance criteria выполнен.

**Альтернативы:**
- **Снизить initial через code-split Material** — отвергнуто (ломает
  runtime, требует shared chunk-manifest, M08-level refactor).
- **Убрать Chart.js из shell** — `shell.component` не импортирует
  Chart.js; он живёт в teacher/stats, который lazy. Initial 874KB
  — это сам Angular framework + Material + theme.
- **Оставить 500KB и игнорировать warning** — отвергнуто: warning
  без action создаёт шум, который снижает сигнальность (команда
  начнёт игнорировать все warnings).

**Implication:** G8 реально достигает **per-role lazy loading** (что
подтверждается размерами per-component chunks < 100KB). Initial bundle
= shared Angular + Material + polyfills (унификация которых
выходит за scope M07). Возврат к 500KB budget — candidate v0.1
(tree-shake Material, switch to Angular CDK only, или SSR где initial
shell grows naturally).

---

## 2026-04-22 — D8: G8 headman double-guards удалены

**Что выбрано:** в старом `app.routes.ts` headman секция имела
`canActivate: [headmanGuard]` и на parent (`/headman`), и на **каждом**
child (dashboard/group/subjects/…). После G8 split в
`headman.routes.ts` убраны children-level guards — оставлен только
parent.

**Почему:** parent canActivate блокирует вход во всю ветку **до
loadChildren fetch'a**. Childern-guard'ы срабатывали уже после того,
как parent прошёл — т.е. вторая проверка всегда возвращала `true`
(иначе parent бы нас сюда не пустил). Это чистый overhead без
security-benefit.

**Альтернативы:** оставить children-guards как defence-in-depth —
отвергнуто: headman status в JWT не меняется между parent и child
routing событиями (одна и та же synchronous iteration matcher'а),
поэтому смены состояния, которое defense-in-depth ловил бы, нет.

**Implication:** если в v0.1 потребуется route-scoped permission
(например, headman-stats требует отдельный grant), тогда child-level
`canActivate` вернётся — но уже с _другим_ guard'ом, не
`headmanGuard`.
