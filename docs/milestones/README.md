# Milestones v0.0.0

Реализация плана из `docs/report-before-v0.0.0/99-executive-summary.md`
тематическими milestones. Lightweight workflow без GSD-orchestrator'а.

## Workflow

Каждый milestone — отдельный каталог `docs/milestones/M{N}-{slug}/` с
тремя файлами (шаблоны в `_TEMPLATE/`):

- **`PLAN.md`** (~100-250 строк) — Scope, модули, acceptance criteria,
  dependencies. Пишется один раз в начале milestone'а.
- **`CHECKLIST.md`** — атомарные tasks с галочками. Обновляется по
  ходу работы. Одна строка = 30 мин - 2 часа.
- **`NOTES.md`** — живой файл. Отклонения, измерения, surprises,
  вопросы к владельцу, TODO для других milestones.

**Никакого RESEARCH.md / VERIFICATION.md.** Research уже сделан аудитом
(`docs/report-before-v0.0.0/`). Verification — acceptance criteria в
PLAN.md + ручной UAT golden path + optional `bug-hunter`/`code-reviewer`
subagent на diff в конце milestone'а.

## Когда звать субагента

- **Explore** / **general-purpose** — «найди все места где X» (дешевле чем разбираться руками).
- **bug-hunter** — после большого PR, один вызов на весь diff milestone'а.
- **code-reviewer** — перед финальным merge milestone'а.
- **security-auditor** — для M1 (Secure Boundaries), там цена бага выше цены токенов.

Не звать: `gsd-*` (дороже без пропорциональной выгоды, research уже готов).

## Порядок milestones

Порядок определяется зависимостями (см. 99-executive-summary.md dependency graph).

| # | Milestone | Зависит от | Estimate | Статус |
|---|-----------|------------|----------|--------|
| M3 | [Shared Foundations](M3-shared-foundations/PLAN.md) | — | ~5-7д | ⏳ в работе |
| M2 | Reliable Eventing (ShedLock + outbox + contract-тесты) | M3 | ~8-10д | ⬜ |
| M1 | Secure Boundaries (Internal JWT + JWT cookie + logout) | M3, M2 | ~14-18д | ⬜ |
| M4 | Observability (Tracing, Alertmanager, JSON-логи) | M3 | ~5-7д | ⬜ |
| M5 | Performance (Indexes, Caffeine, EntityGraph, batch) | M3 | ~6-7д | ⬜ |
| M6 | Frontend Hardening (CSP, a11y, UX, openapi-typescript) | M1 | ~10-12д | ⬜ |
| M7 | Test Infrastructure (Playwright, golden, coverage-gate) | M3, M2, M1 | ~10-12д | ⬜ |
| M8 | Ops & Supply Chain (SHA tagging, Trivy, HEALTHCHECK) | — | ~3-4д | ⬜ |

**Parallel tracks:** M4 и M5 можно делать параллельно с M1 (не блокируют).
M8 полностью независим — можно делать когда угодно.

## Правила

1. **Один milestone = один logical release** (v0.0.0-alpha.N). После
   merge — всё работает, даже если следующий milestone ещё не начат.
2. **Коммиты атомарные** — один task из CHECKLIST = один коммит (или
   несколько, если большой).
3. **PLAN.md не переписывается** после старта milestone. Отклонения
   идут в NOTES.md. В конце — короткий `## Post-mortem` внизу PLAN.md.
4. **Acceptance criteria проверяются разово** в конце. Если не прошло —
   fix в том же milestone, не откладываем.

## После последнего milestone

- Финальный CHANGELOG.md entry `[v0.0.0]` (см. QD7 + NEW-107).
- `git tag v0.0.0 && git push --tags`.
- GitHub Release из CHANGELOG.
- Архив `docs/milestones/` остаётся как история принятых решений.
