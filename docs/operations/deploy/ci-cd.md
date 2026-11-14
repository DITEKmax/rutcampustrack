# CI/CD

Флоу сборки, тестирования, деплоя и обновления зависимостей. Введён в
M06 Группа 5 (NEW-105).

## Общая картина

```
┌──────────────────────────────────────────────────────────────────┐
│  GitHub Actions (.github/workflows/)                              │
│                                                                   │
│  ┌──────────┐    main CI / вручную ┌──────────┐                  │
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

- Автоматический Deploy использует SHA успешного CI, запущенного push в
  `main` этого репозитория. Для него нужны обе repository variables со
  значением `true`: `PRODUCTION_AUTO_DEPLOY_ENABLED` и `PRODUCTION_DEPLOY_ENABLED`.
  Чтобы остановить новые автоматические запуски, выключи
  `PRODUCTION_AUTO_DEPLOY_ENABLED`; ручной запуск сохранится. Уже запущенные
  или ожидающие Deploy проверь отдельно в Actions.
- Для ручного `workflow_dispatch` выбери `main`, укажи полный `commit_sha` и
  причину. `prepare_only=true` по умолчанию: сборка и публикация 13 образов
  с фиксацией digest без production inputs или SSH. Ручной apply требует
  `prepare_only=false` и `PRODUCTION_DEPLOY_ENABLED=true`.
- Перед сборкой все четыре quality workflows (CI, Coverage, Security, OpenAPI)
  должны успешно пройти на том же SHA. Для apply этот SHA должен совпадать с
  текущим `main`; SHA и все четыре проверки проверяются повторно перед apply.
  Готовые к проверкам ручные и автоматические запуски ждут в общей очереди.
  Если CI более старого коммита завершится позже, он не вытеснит ожидающий
  запуск. Проверка текущего `main` отклонит устаревший SHA.
- Создание первого FULL администратора — отдельная repository variable
  `PRODUCTION_BOOTSTRAP_INITIAL_ADMIN` ровно `true` при ручном non-prepare apply.
  Checkbox отсутствует. При отсутствующей/выключенной переменной запрос
  `INITIAL_ADMIN_REQUEST` не передаётся, admin DB-логика не запускается.
  При включённой используется существующий приватный идемпотентный протокол
  после readiness Compose и проверки served web. См.
  [initial-administrator-deploy.md](../../runbooks/initial-administrator-deploy.md).
- Автоматический Deploy не запускает bootstrap и не получает `INITIAL_ADMIN_REQUEST`.
- Release доставляется по проверенным digest и точной revision.
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

Deploy в любом режиме требует успешного результата всех четырёх обязательных
workflows на том же SHA. При ошибке, отсутствии или ожидании результата
выпуск не проходит.

## Release process (QD7)

1. Merge в `main` запускает quality workflows. Если обе переменные включены,
   успешный CI запускает Deploy, который проверяет остальные результаты на
   том же SHA. Если автоматический запуск выключен, запусти Deploy вручную
   через `workflow_dispatch` на точном SHA текущего `main` после проверок.
2. Для semver-тэга `vX.Y.Z`:
   - `CHANGELOG.md` → перенести `[Unreleased]` → `[vX.Y.Z] - YYYY-MM-DD`.
   - Commit: `chore(release): vX.Y.Z`.
   - `git tag vX.Y.Z && git push --tags`.
   - GitHub → Releases → «Create release from tag» → скопировать
     CHANGELOG-раздел.
3. Semver tag и публикация GitHub Release не запускают Deploy.

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
# При включённом auto Deploy дождись запуска после успешного CI на revert SHA.
# Иначе после quality checks запусти Deploy вручную с точным SHA revert commit.
```

## История изменений

| Дата | Что | Коммит |
|------|-----|--------|
| 2026-04-21 | M06 initial — CI/CD runbook + Renovate + Dependabot | TBD |
