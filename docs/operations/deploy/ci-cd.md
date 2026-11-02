# CI/CD

Флоу сборки, тестирования, деплоя и обновления зависимостей. Введён в
M06 Группа 5 (NEW-105).

## Общая картина

```
┌──────────────────────────────────────────────────────────────────┐
│  GitHub Actions (.github/workflows/)                              │
│                                                                   │
│  ┌──────────┐    manual dispatch ┌──────────┐                    │
│  │  ci.yml  │ ── quality gate ──> │deploy.yml│                    │
│  │(M06 G7)  │                     │          │                    │
│  └──────────┘                     └──────────┘                    │
│       │                                 │                         │
│       │ on: push/PR '**'                │ → GHCR build+push       │
│       │ paths-ignore: docs,planning,md  │ → SSH VPS deploy        │
│       │                                 │ → smoke /login          │
│       │                                 ↓                         │
│       │                            rutcampustrack VPS             │
│       │                                                           │
│       └──> security.yml (trivy + gitleaks, weekly cron)          │
│                                                                   │
│  ┌──────────┐                                                     │
│  │ Renovate │ ← renovate.json (nightly, после 22:00)              │
│  └──────────┘                                                     │
│       │                                                           │
│       │ auto-merge: patch, pin, digest                            │
│       │ manual: minor, major                                      │
│       ↓                                                           │
│  creates PRs                                                      │
│                                                                   │
│  ┌──────────────┐                                                 │
│  │  Dependabot  │ ← .github/dependabot.yml (security-only)        │
│  └──────────────┘                                                 │
│       │                                                           │
│       │ CVE fixes, daily                                          │
│       ↓                                                           │
│  creates PRs (label: security)                                    │
└──────────────────────────────────────────────────────────────────┘
```

## Workflows

### ci.yml — тесты

- Trigger: `push: branches: '**'` + `pull_request: branches: '**'`.
- Paths-ignore: `docs/**`, `.planning/**`, `*.md` (M06 G7, 13 P1-11).
- Jobs:
  - **Java matrix** — 6 сервисов × `./gradlew check` (unit + IT + ArchUnit).
  - **Python** — `ruff check` + `pytest` (notification-bot).
  - **Frontend matrix** — 3 × `npm ci && npm test && npm run build` (pwa,
    web-panel, mini-app). Landing — static HTML, без build-step.
- Required status checks — включаются через GitHub branch protection UI.

### deploy.yml — продакшен-деплой

- Trigger: только `workflow_dispatch` на main. Решение владельца 2026-11-02
  отменяет автоматический Deploy после CI; merge/push и успешный CI его не запускают.
- Укажи полный `commit_sha` и причину. Все четыре quality workflows
  (CI, Coverage, Security, OpenAPI) должны успешно пройти на этом SHA;
  перед apply повторно проверяются current main и тот же SHA.
- `prepare_only=true` по умолчанию: build, blocking scan и подписи всех 13
  image digests без production inputs или SSH.
- Apply требует `prepare_only=false` и repository variable
  `PRODUCTION_DEPLOY_ENABLED` ровно `true`. Отсутствующее/другое значение запрещает apply.
- Создание первого FULL администратора — отдельная repository variable
  `PRODUCTION_BOOTSTRAP_INITIAL_ADMIN` ровно `true` при ручном non-prepare apply.
  Checkbox отсутствует. При отсутствующей/выключенной переменной запрос
  `INITIAL_ADMIN_REQUEST` не передаётся, admin DB-логика не запускается.
  При включённой используется существующий приватный идемпотентный протокол
  после readiness Compose и проверки served web. См.
  [initial-administrator-deploy.md](../../runbooks/initial-administrator-deploy.md).
- На 2026-11-02 обе repository variables выключены. Документация и commit
  сами по себе их не включают и не разрешают работу на VPS.
- Release доставляется по проверенным digest/signatures и точной revision.
  Ошибка readiness/web/bootstrap не записывает `.deployed-sha`.
- PWA build получает `VITE_APP_VERSION=1-${DEPLOY_SHA}` и генерирует
  forced `version.json`; см. [pwa-forced-updates.md](pwa-forced-updates.md).

### security.yml — supply-chain (M06 G6, QD5)

- Trigger: `push: main`, `pull_request`, `schedule: cron '0 3 * * 1'`.
- Jobs:
  - **Trivy** — repo scan (fs) + image scan для всех 11 GHCR-образов.
    Fail на HIGH/CRITICAL.
  - **Gitleaks** — secret-detection в commits + PRs.
- `SECURITY.md` — disclosure policy (NEW-103).

## Renovate (renovate.json)

- GitHub App «Renovate».
- Schedule: ежедневно после 22:00 Europe/Moscow + weekends.
- Auto-merge: `patch`, `pin`, `digest` updates после зелёного CI.
- Manual review: `minor`, `major`.
- Groupings:
  - Spring Boot (все `org.springframework.boot:*`).
  - Angular (`@angular/*`).
  - React (`react`, `react-dom`, `@types/react*`).
  - TanStack (`@tanstack/*`).
- Special rules:
  - `grafana/loki` major → manual (schema migration, см.
    `docs/operations/runbooks/loki-major-upgrade.md`).
  - `cadvisor` + `promtail` digest → auto-merge (M06 D2).
- Dashboard issue — всегда up-to-date лист pending updates.

Validate: `npx --package renovate -- renovate-config-validator`.

## Dependabot (.github/dependabot.yml)

- Security-only updates. Parallel с Renovate (freshness), не дублирует.
- Ecosystems: gradle, npm × 3, pip (bot), docker, github-actions.
- Daily schedule (GitHub-actions — weekly).
- Labels: `security` + `dependencies`.

## Branch protection (manual setup)

В GitHub UI (`Settings → Branches → Add rule` для `main`):

- Require status checks to pass: `ci.yml` (все матриксы).
- Require linear history: **off** (merge commits ОК).
- Required reviews: **не требуется** (single developer project).
- Allow force pushes: **off**.
- Allow deletions: **off**.

Ручной Deploy не обходит same-SHA quality gate: failing, missing или pending
результат любого из четырёх обязательных workflows блокирует выпуск.

## Release process (QD7, manual)

1. Feature merge в main → quality workflows. Deploy запускается владельцем
   отдельно через workflow_dispatch после их успешного завершения на точном SHA.
2. Для semver-тэга `vX.Y.Z`:
   - `CHANGELOG.md` → перенести `[Unreleased]` → `[vX.Y.Z] - YYYY-MM-DD`.
   - Commit: `chore(release): vX.Y.Z`.
   - `git tag vX.Y.Z && git push --tags`.
   - GitHub → Releases → «Create release from tag» → скопировать
     CHANGELOG-раздел.
3. Ручной Deploy выбирает точный commit_sha; semver tag и GitHub Release
   не запускают его автоматически.

## Откат (rollback)

**Deployment rollback:**
```bash
# На VPS:
cat /opt/rutcampustrack/.deployed-sha
# .deployed-sha=<current_sha>
IMAGE_TAG=<previous_sha> docker compose \
  --env-file /opt/rutcampustrack/.env.prod \
  -f docker-compose.prod.yml pull
IMAGE_TAG=<previous_sha> docker compose \
  --env-file /opt/rutcampustrack/.env.prod \
  -f docker-compose.prod.yml up -d --wait
```

**Git rollback (если нужно откатить + redeploy):**
```bash
git revert <bad-commit-sha>
git push origin main
# После quality checks запусти Deploy вручную с точным SHA revert commit.
```

## История изменений

| Дата | Что | Коммит |
|------|-----|--------|
| 2026-04-21 | M06 initial — CI/CD runbook + Renovate + Dependabot | TBD |
