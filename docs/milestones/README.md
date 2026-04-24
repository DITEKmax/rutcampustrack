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
- **security-auditor** — для M03 (Secure Boundaries), там цена бага выше цены токенов.

Не звать: `gsd-*` (дороже без пропорциональной выгоды, research уже готов).

## Порядок milestones

Нумерация = **порядок выполнения** (1 → 8), определённый dependency graph
из 99-executive-summary.md. Название отражает содержание, не приоритет.

| # | Milestone | Зависит от | Estimate | Статус |
|---|-----------|------------|----------|--------|
| M01 | [Shared Foundations](M01-shared-foundations/PLAN.md) | — | ~5-7д | ✅ 2026-04-19 |
| M02 | [Reliable Eventing (ShedLock + outbox + contract-тесты)](M02-reliable-eventing/PLAN.md) | M01 | ~8-10д | ✅ 2026-04-19 |
| M03a | [Internal JWT + Rate-limit](M03a-internal-jwt-ratelimit/PLAN.md) | M01, M02 | ~5-8д | ✅ 2026-04-20 |
| M03b | [Secure Boundaries Part B (JWT cookie + ws-ticket + logout)](M03b-jwt-cookie-ws-ticket/PLAN.md) | M03a | ~8-12д | ✅ 2026-04-20 |
| M04 | [Observability (Tracing, Alertmanager, JSON-логи)](M04-observability/PLAN.md) | M01 | ~5-7д | ✅ 2026-04-20 |
| M05 | [Performance (Indexes, Redis cache, HikariCP, batch, gRPC, push retention)](M05-performance/PLAN.md) | M01 | ~6-7д | ✅ 2026-04-21 |
| M06 | [Ops & Supply Chain (SHA tagging, Trivy, HEALTHCHECK)](M06-ops-supply-chain/PLAN.md) | — | ~3-4д | ✅ 2026-04-21 |
| M07 | [Frontend Hardening (CSP, a11y, UX, openapi-typescript)](M07-frontend-hardening/PLAN.md) | M03b | ~10-12д | ✅ 2026-04-22 |
| M08 | [Test Infrastructure (Playwright, golden, coverage-gate, SBOM)](M08-test-infrastructure/PLAN.md) | M01, M02, M03b, M07 | ~10-12д | ✅ 2026-04-23 |
| M09 | [Prod Release Blockers (P0 + event unification + prod-deploy-checklist)](M09-prod-release-blockers/PLAN.md) | M02, M03a | ~7-8д | ✅ 2026-04-24 |
| M10 | [Notification History (stateful notification-web + MongoDB)](M10-notification-history/PLAN.md) | M01, M02, M05 | ~4-5д | ✅ 2026-04-24 |
| M11 | [OpenAPI Polish (@Schema, customizer, swagger basic-auth)](M11-openapi-polish/PLAN.md) | M01, M10 | ~3д | ✅ 2026-04-24 |
| M12 | [Auth Contract-first Refactor (auth-api-contract + AuthApi interface)](M12-auth-contract/PLAN.md) | M07, M11 | ~2.5д | ✅ 2026-04-24 |
| M13 | [Pre-Deploy Hardening (VPS GA blockers)](M13-pre-deploy-hardening/PLAN.md) | M01-M12 | ~5-7д | 🟡 2026-04-25 (7/24 групп: G1-G7 ✅) |

**Parallel tracks:** M04 и M05 можно делать одновременно с M03a/M03b
(независимы по коду). M06 полностью независим — можно делать когда угодно,
даже параллельно M01. M03 разделён на M03a (Internal JWT + rate-limit) и
M03b (JWT cookie + ws-ticket + logout) для промежуточного тега
`v0.0.0-alpha.3` и снижения риска breaking change.

**Финальные milestones (M07-M12)** parallel safe между собой при
соблюдении dependency graph:
- **M07 → M08** (M08 Playwright требует M07 axe-core baseline + openapi-ts)
- **M07 → M10** (M10 подменяет data source в thin-client NotificationCenter
  из M07)
- **M07 → M11** (M11 наполняет @Schema, regenerate openapi-ts из M07)
- **M10 → M11** (M11 финализирует @Schema в notification-api-contract)
- **M11 → M12** (M12 применяет M11 @Schema policy к auth-api-contract
  сразу при создании)
- **M07 → M12** (M12 regenerate openapi-ts требует M07 generator setup)
- **M09** независим — параллельно с M07-M12.

Релизный тег `v0.0.0` ставится только после **всех** M07-M12.
M12 — последний структурный долг (Contract-first compliance); после
M12 единственное исключение в правилах — `api-gateway` (прокси).

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
