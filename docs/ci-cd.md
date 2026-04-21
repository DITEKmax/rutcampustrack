# CI/CD

Флоу сборки, тестирования, деплоя и обновления зависимостей. Введён в
M06 Группа 5 (NEW-105).

## Общая картина

```
┌──────────────────────────────────────────────────────────────────┐
│  GitHub Actions (.github/workflows/)                              │
│                                                                   │
│  ┌──────────┐    workflow_run    ┌──────────┐                    │
│  │  ci.yml  │ ──────(success)───> │deploy.yml│                    │
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

- Trigger: `on: workflow_run: workflows: [CI]: types: [completed]:
  branches: [main]` + `if: github.event.workflow_run.conclusion ==
  'success'` (M06 G7, C0-8).
- Gated — deploy.yml стартует только после зелёного `ci.yml`.
- Steps:
  1. `docker/login-action@v3` → GHCR с `GHCR_TOKEN`.
  2. 11 × `docker/build-push-action@v7` — build+push с tags
     `:${{ github.sha }}` + `:latest` (M06 G2, QD1).
  3. `appleboy/ssh-action@v1` → VPS:
     - `git pull --ff-only`.
     - JWT-keys volume init (openssl genrsa 3072 если отсутствуют).
     - `export IMAGE_TAG=${{ github.sha }}`.
     - `docker compose pull` + `up -d --wait --wait-timeout 120`.
     - Observability config reload (если `infra/*` изменился).
     - `nginx -s reload` + smoke `GET /login`.
- Rollback: `IMAGE_TAG=<old_sha> docker compose up -d`
  (см. `/opt/rutcampustrack/.deployed-sha`).

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
    `docs/runbooks/loki-major-upgrade.md`).
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

После включения `deploy.yml` физически не может запуститься на failing
CI — `workflow_run` проверка.

## Release process (QD7, manual)

1. Feature merge в main → auto-deploy как dev-release.
2. Для semver-тэга `vX.Y.Z`:
   - `CHANGELOG.md` → перенести `[Unreleased]` → `[vX.Y.Z] - YYYY-MM-DD`.
   - Commit: `chore(release): vX.Y.Z`.
   - `git tag vX.Y.Z && git push --tags`.
   - GitHub → Releases → «Create release from tag» → скопировать
     CHANGELOG-раздел.
3. `deploy.yml` триггерится по `push: tags v*` (опционально — добавляется
   в M06/M09) — GHCR image с тегом `vX.Y.Z` как фиксированная точка
   rollback.

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
# deploy.yml триггерится через workflow_run → CI → deploy
```

## История изменений

| Дата | Что | Коммит |
|------|-----|--------|
| 2026-04-21 | M06 initial — CI/CD runbook + Renovate + Dependabot | TBD |
