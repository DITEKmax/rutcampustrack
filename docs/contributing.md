# Contributing Guide (NEW-108)

Гайд для commit-policies, PR-labels и review-routing. Короткий, без
общих слов. Для архитектуры и стека — `CLAUDE.md` + `architecture.md`.

## Branches

- `main` — prod. Merge из `dev` только когда milestone закрыт + tag.
- `dev` — active development. Все PR merge'ятся сюда.
- Feature-branches именовать как `feat/<short-topic>`, `fix/<short-topic>`.
- **Не пушить** в `dev` / `main` напрямую без PR (кроме `docs-only`
  коммитов от maintainer'а, согласовано в M07 workflow).

## Commits

- Conventional Commits: `<type>(<scope>): <subject>`.
- Типы: `feat`, `fix`, `refactor`, `perf`, `docs`, `chore`, `test`,
  `ci`, `build`, `revert`.
- Scope чаще всего: `backend`, `frontend`, `pwa`, `web-panel`,
  `landing`, `infra`, `ops`, `m07`, `api`.
- Subject — повелительное наклонение, без точки, <70 символов.
- Body — «что» и «почему», не «как» (код уже показывает как).
- Tailing: `Co-Authored-By: ...` если работал агент (CLAUDE.md
  предписание).

Пример:
```
feat(frontend): RFC 7807 error interceptor (M07 Группа 4, QC3)

Unified ProblemDetails parser для PWA + web-panel…
```

## PR labels (manual в UI)

| Label | Когда ставить | Trigger review |
|-------|---------------|----------------|
| `landing-review` | PR меняет `frontends/landing/` | Дизайнер / product owner |
| `docs-review` | PR меняет `docs/milestones/**`, `CLAUDE.md`, архитектурные `.md` | Docs maintainer |
| `security` | PR меняет auth, crypto, rate-limit, CSP, secrets | `security-auditor` агент обязателен |
| `breaking` | PR меняет public API (OpenAPI, events), wire-format | Отдельное ревью + runbook миграции |
| `migration` | PR добавляет Flyway V{N} | DB-maintainer; cross-check checksum drift |
| `dependency` | Renovate/Dependabot auto-PR | Auto-merge allowed для patch/pin/digest |

## Когда ревизовать landing?

- **Визуально:** любые изменения в `frontends/landing/dist/index.html`,
  `dist/styles/*`, `dist/scripts/hero.js` — требуют screenshot
  before/after в PR.
- **CSP:** self-host инвариант (M07 G1) — ни один external URL не
  должен появляться. Если нужен — pre-approve в отдельном issue.
- **Meta/OG:** `og-image`, `canonical`, JSON-LD — проверить через
  Telegram Preview + Twitter Card Validator на staging.
- **A11y:** `prefers-reduced-motion` override должен работать.
  axe-core run не обязателен, но recommend.

## Когда ревизовать docs?

- **`CLAUDE.md`:** раздел «Статус» при закрытии milestone. Version bump
  (v0.0.0-alpha.X) — по checklist'у milestone'а.
- **`docs/milestones/*/PLAN.md`:** не редактировать после утверждения
  scope. Исключение — Post-mortem секция при закрытии.
- **`docs/milestones/*/CHECKLIST.md`:** отметка `[x]` + commit hash
  при закрытии задачи.
- **`docs/milestones/*/DECISIONS.md`:** append-only. Новые D{N}
  дописываются снизу, старые не правятся.
- **`architecture.md`, `database-schema.md`:** при breaking changes.
  Fine-grained правки в соответствующие доменные docs.

## Flyway migrations

- **Никогда** не редактировать применённый `V{N}__*.sql`. Boot fail по
  checksum mismatch.
- Патчи: `V{N+1}__fix_<описание>.sql`.
- Repeatable migrations (`R__*.sql`) — только для view/function
  redefinitions, не для DML.
- Seed / fixture — в `docs/milestones/M*/seed-*.sql`, **не** в
  Flyway (мы не катаем на prod).

## openapi-typescript drift

CI `.github/workflows/openapi-drift.yml` fail'ит, если committed
`docs/openapi/*.json` расходятся с generated `frontends/*/src/api/generated/`.

Фикс:
```bash
cd frontends/pwa && npm run generate:types:offline
cd ../web-panel && npm run generate:types:offline
git add frontends/*/src/api/generated/  # или docs/openapi/*.json если backend изменился
```

При реальном изменении backend DTO сначала обнови
`docs/openapi/*.json` (пойми `npm run generate:types` — не offline —
с запущенным backend), затем regenerate types.

## Что НЕ коммитить

- `frontends/*/dist/**` — build-артефакты (кроме landing'а, где dist
  committed для deploy).
- `.env*`, `.htpasswd`, secrets — всегда через `docker-compose` secrets
  или vps `/opt/rutcampustrack/.env.prod`.
- Личные IDE-файлы (`.idea/`, `.vscode/` — в global gitignore).

## GSD planning loop (short)

1. **/gsd-new-milestone** → scope в `docs/milestones/M{N}/PLAN.md`.
2. **/gsd-discuss-phase** — owner-answers если scope unclear.
3. **/gsd-plan-phase** — per-phase `PLAN.md` + `CHECKLIST.md`.
4. **/gsd-execute-phase** — с atomic commits + checkpoint protocol.
5. **/gsd-verify-phase** → `VERIFICATION.md`.
6. **/gsd-complete-milestone** → tag + archive.

Подробнее — в `docs/info-for-gsd.md` и `.claude/agents/gsd-*.md`.

---

_Обновлено M07 G11 (NEW-108)._
