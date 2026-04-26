# M07 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу,
технические долги.

---

## Вопросы к owner'у до старта — ОТВЕЧЕНЫ (2026-04-21)

1. **Brand og-image** (G2, QE4): ответ **(b) SVG-first → PNG через
   sharp/resvg**. SVG коммитится в git, PNG генерируется из него
   npm-скриптом. Если brandbook не найду или не содержит RGB — вернуться
   к owner'у с уточняющим вопросом.
2. **mini-app scope** (G3): **(a) не трогаем mini-app в M07**. Owner
   planned path: после M12 закрытия + стабилизация PWA в проде →
   mini-app мигрируется **copy+adapt** из PWA. G3 scope = только
   PWA + web-panel (2 frontend'а). Запись про план — в
   `docs/archive/future-ideas.md` → "Mini-app unification: copy+adapt from PWA
   after M12".
3. **axe-core baseline** (G10): **(a) CRITICAL + SERIOUS = 0**.
   MODERATE/MINOR трекаем в `docs/product/a11y-checklist.md` как "a11y pass 2"
   (v0.1), не блокируем M07.
4. **Sparklines placeholder text** (G9): **(e) "Графики посещаемости
   появятся в следующем релизе"** + skeleton UI + info-badge.
5. **Schedule navigation bounds** (G6, P2-7A/4): **(d) bounds =
   активный семестр + info-screen** ("Семестр начнётся Y" /
   "Семестр закончился X, новый — Y"). Edge-case «каникулы без
   активного семестра» → отдельное D1-решение в DECISIONS.md.

## Ожидаемые surprises

- **CSP self-host может сломать GSAP intellisense** — bundled vs CDN
  версии могут отличаться по API. Проверить что hero-анимация работает
  после self-host.
- **openapi-ts может конфликтовать с Angular TypeScript strict mode** —
  generated types используют `readonly` / `unknown` агрессивно, может
  потребоваться `strict: false` override в generated module.
- **Lazy-loading в web-panel уже частично сделан в v9.0** — проверить
  что именно лениво, что нет (Phase 50+).
- **P2-7A/6 forkJoin fix для subject lookup** — может требовать backend
  `/subjects?ids=[...]` batch endpoint (QC6-related). Если endpoint'а
  нет — создать в backend первыми.

## Deferred до M07 (из M06 post-mortem)

- **nginx `client_max_body_size` per-location** — в Группу 11 M07.
- **13 P1-3 nginx rate-limit** — переоценить после M03a Redis
  rate-limiter. Если Spring Cloud Gateway достаточный — skip.
- **C0-6 CSP self-host landing** — в Группу 1 M07 (основной фокус).
- **nginx/postgres/mongo/redis/rabbitmq digest-pin** — defer в M08,
  это supply-chain, не frontend.
- **/actuator/** excluded from tracing** — v0.1 (требует custom OTel
  Sampler, integration tests).

---

## Metrics baseline (до M07)

Соберём после старта для сравнения в post-mortem:

- PWA bundle size: [TBD]
- web-panel initial chunk: [TBD]
- axe-core findings: [TBD CRITICAL, TBD SERIOUS]
- Landing Lighthouse Performance: [TBD]
- Landing Lighthouse A11y: [TBD]

---

## G9 discovery (2026-04-22)

### PWA StatsPage не получил batch-refactor — backend endpoint отсутствует

CHECKLIST говорил: «Проверить что `/attendance/stats/aggregate`
batch endpoint существует (из M05); если нет — создать (минимальный)».

Проверка показала:
- `/attendance/reports/journal` принимает **один** `subjectId` за вызов
  (generated types: `{groupId, subjectId, dateFrom, dateTo}`).
- `POST /attendance/marks/batch` (M05 G4) — bulk-mark, не stats-aggregate.
- Stats-aggregate endpoint'а в backend **нет**. M05 G5 сделал
  single-pass accumulators внутри `StudentStatsResponse` для одного
  студента, а не group-level aggregation.

**Решение:** создание нового backend endpoint'а = outside M07 scope
(M07 = frontend-only milestone). Текущая реализация
`SubjectStatsCollector` в `StatsPage` уже делает **N параллельных
queries** через TanStack — не sequential. Реального waterfall в
StatsPage **нет** (был только в schedule/LessonCard subject-lookup,
закрыт G6/6 через `useSubjectMap`).

**Отложено в `docs/archive/future-ideas.md` → NEW-94** (Real sparklines
backend) — этот же endpoint (`GET /api/admin/dashboard/metrics`
или `GET /api/stats/group-aggregate`) закроет оба кейса.

### Admin-dashboard sparklines были псевдо-данными

`admin-dashboard.component.ts::buildSpark()` делал **детерминированный
random** на основе target-числа (чтобы линия «не прыгала» между
рендерами). Визуально неотличимо от реальных данных — юзер принимал
их как факт. Удалено в G9:
- `BaseChartDirective`, `ChartConfiguration` импорты убраны
- `studentsSpark()`, `teachersSpark()`, `groupsSpark()`,
  `activeGroupsSpark()`, `chartData()`, `chartOptions` deleted
- `buildSpark()` helper deleted
- `[sparkData]` binding убран со всех `<app-stat-card>`
- Chart-секция заменена на skeleton-bars + info-сообщение

`StatCardComponent.sparkData` input оставлен как `input<number[] | null>(null)` — при `null` SVG не рендерится. Компонент готов вернуться
к реальным sparklines без повторного рефакторинга после NEW-94.

`chart.js` остаётся transitive-зависимостью (teacher-stats + student-stats
используют BaseChartDirective с реальными per-subject данными). Удаление
целиком отложено до миграции tree-shake (M08/v0.1).

---

## G3b discovery (2026-04-21)

### ExcuseType был runtime bug — lowercase vs UPPERCASE

**Pre-G3b состояние:** frontend (PWA + web-panel) держал ручной
`ExcuseType = 'illness' | 'summons' | ...` в lowercase. Backend
Java-enum сериализуется в JSON как UPPERCASE (`'ILLNESS' |
'SUMMONS' | ...`). Результат:
- `EXCUSE_TYPE_LABELS[ticket.excuseType]` возвращал `undefined` в
  UI — frontend показывал пустой label вместо «Болезнь».
- TypeScript не ловил ошибку, т.к. ручной тип совпадал сам с собой.
- 3 unit-тесты в web-panel фиксировали неверное lowercase поведение
  как «ожидаемое».

**После G3b:** `ExcuseType` re-export из `CreateExcuseRequest.excuseType`
(UPPERCASE union из generated), `EXCUSE_TYPE_LABELS` ключи UPPERCASE,
тесты обновлены на UPPERCASE. TypeScript гарантирует согласованность
с backend.

### WeekType migration NUMERATOR → ODD

**Pre-G3b:** student-schedule.types.ts держал `WeekType = 'NUMERATOR'
| 'DENOMINATOR' | 'BOTH'`. Backend давно перешёл на `'ALL' | 'ODD' |
'EVEN'` (CLAUDE.md memory). Миграция синхронизировала типы.

### HATEOAS optionality — Strict-wrapper

openapi-typescript помечает все response fields как `?:` (Spring
response DTO не имеют `@NotNull`). Для Angular strictTemplates это
фатально — `{{ user.login }}` compile-error. Решение — `Strict<T, K>`
wrapper в `api/schema.ts`, который делает listed keys required +
убирает `_links`. Централизовано: 1 файл для PWA, 1 для web-panel.

### Nullable поля — ручной override

Backend @Schema не отмечает `description`, `link`, `decisionBy`,
`decisionComment`, `decisionAt`, `comment`, `middleName`, `groupId`,
`employeeNumber`, `telegramId` как `nullable: true`. Generated types
помечают их `string | undefined`, runtime приходит `null`. Override'им
в schema.ts как `| null`. Устранение — M11 OpenAPI Polish.

### Что НЕ делали в G3b

- `openapi-fetch` client не подключён в runtime (D3 — pragmatic
  types-only). Dep остаётся установленной для будущих features.
- `fieldErrors → invalidParams` rename — отложен в G4 (D2).
- Ручные `*Props` interfaces в компонентах — не трогали, это UI-only.
- Frontend-aggregate types (GroupMember, JournalCell, ResolvedThreshold,
  NotificationItem, StompEnvelope, LateCheckinRequestedEvent) — оставлены
  ручными; они не являются прямыми backend DTO.

---
