# Промпт для следующей сессии — M13 re-open: G25 (CI hot-fixes + e2e infrastructure)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M13 был закрыт 2026-04-25 (tag `v0.0.0-alpha.14` локально, push'нут на
origin). После push в `origin/dev` (commit `3db123b`, +99 commits ahead)
CI обнаружил 3 проблемы. M13 re-open в виде Группы 25 чтобы закрыть
ВСЁ перед VPS dry-run.**

## Текущая позиция (на конец прошлой сессии 2026-04-25)

**Push сделан, CI красный:**

1. ✅ **Шаг 1 (push)** — `dev` на origin = `3db123b`. 13 alpha tags
   на origin (alpha.2-14). Tag `v0.0.0-alpha.14` → `c9861f7`.
2. ⚠️ **Шаг 2 (CI)** — выполнен, но **3 фейла на commit `3db123b`**:
   - **Python lint & test:** `ruff format --check` падает на 9 файлах
     (5 prod + 4 test). Локально fix применён (`ruff format .`),
     но **не закоммичен**.
   - **Python coverage:** `test_consumer_watchdog::test_watchdog_restarts_on_consumer_failure`
     FAIL — детерминированный регресс M13 G8 (mock не принимает новый
     параметр `idempotency_guard`). Локально fix применён к 4 mock
     signature в `tests/test_consumer_watchdog.py`, **не закоммичен**.
   - **Playwright E2E auth flow:** Docker build fail на multi-stage
     COPY paths. Корневая проблема — dev `docker-compose.yml` не
     содержит backend Java сервисов, а `Dockerfile` notification-web
     ожидает build context = root репо. **Не fix'нуто, требует
     инфраструктурную работу.**

## План на новую сессию — M13 Группа 25

Подробности в `docs/milestones/M13-pre-deploy-hardening/CHECKLIST.md`
секция **«Группа 25 — CI hot-fixes + e2e-auth job infrastructure»**
и `NOTES.md` секция **«Группа 25 — CI hot-fixes + e2e-auth infrastructure»**.

### G25.1 — закоммитить ruff fix _(15 мин)_

```bash
# Локально уже применено: 9 файлов отформатированы
git status                          # покажет 9 изменённых .py файлов
cd services/notification-bot && py -m ruff format --check . && py -m ruff check .
# оба должны пройти
git add services/notification-bot/bot services/notification-bot/tests
git commit -m "style(notification-bot): ruff format compliance (M13 G25.1)"
```

### G25.2 — закоммитить watchdog mock fix _(15 мин)_

```bash
# Локально уже применено: 4 mock signature в test_consumer_watchdog.py
cd services/notification-bot && py -m pytest tests/test_consumer_watchdog.py -v
# должно быть 6 PASSED
git add services/notification-bot/tests/test_consumer_watchdog.py
git commit -m "test(notification-bot): mock_start_consumer signature update for M13 G8 (M13 G25.2)"
```

### G25.3 — docker-compose.e2e.yml + CI integration _(2-4 часа)_

**Это основная работа G25.** План:

1. **Создать `docker-compose.e2e.yml`** — minimal prod-like stack:
   - Все 5 backend Java сервисов (auth, academic, schedule,
     attendance, gateway) с `context: .` + правильный path к
     Dockerfile
   - 6 инфра контейнеров (postgres × 2, mongo, redis, rabbitmq;
     БЕЗ tempo/alertmanager — observability не нужен в e2e)
   - 4 nginx (pwa, mini-app, web-panel, landing)
   - 2 notification (web + bot)
   - **Всего ~17 контейнеров**, ~3-4 мин boot на CI runner

2. **Тестовые JWT keys.** Два варианта:
   - **A. Generate в job step** (чище): `openssl genrsa -out test.key 2048 && openssl rsa -in test.key -pubout -out test.pub`. Сложнее: пробросить в правильный path внутри auth-service container.
   - **B. Commit fixture** (быстрее): `tests/e2e/keys/{private,public}.key` помеченные `# CI-ONLY — DO NOT USE IN PROD`. Gitignored ANY `*.pem`/`*.key` сейчас не пускает `tests/e2e/keys/*.key` — добавить exception в `.gitignore`.
   - **Рекомендуемый: B** (упрощает debugging, single source of truth).

3. **Тестовые секреты** в `tests/e2e/.env.ci` — committed:
   ```
   POSTGRES_ACADEMIC_PASSWORD=ci-test-only
   POSTGRES_SCHEDULE_PASSWORD=ci-test-only
   MONGO_ROOT_PASS=ci-test-only
   REDIS_PASSWORD=ci-test-only
   RABBITMQ_USER=test
   RABBITMQ_PASSWORD=ci-test-only
   GRPC_SECRET=ci-test-grpc-secret
   JWT_PUBLIC_KEY_PATH=/keys/public.key
   JWT_PRIVATE_KEY_PATH=/keys/private.key
   ...
   ```

4. **Bitnami MongoDB replica set init** — критичная неизвестная.
   Bitnami's mongo standalone work, replica set требует `rs.initiate()`.
   В M13 G7 (production) это делается через `docker exec` после
   container up. В CI:
   - Вариант 1: `MONGODB_REPLICA_SET_MODE=primary` + `MONGODB_REPLICA_SET_NAME=rs0` env vars (Bitnami auto-init)
   - Вариант 2: post-up step `docker exec rct-mongo-attendance mongosh --eval 'rs.initiate()'`
   - Проверить **локально** перед push.

5. **Обновить `.github/workflows/ci.yml` e2e-auth job:**
   ```yaml
   - name: Boot full stack (e2e compose)
     run: |
       cp tests/e2e/.env.ci .env
       docker compose -f docker-compose.e2e.yml up -d --build
       # increase poll timeout to 6 min for full prod-like stack
   ```

6. **Локальная проверка** перед push:
   ```bash
   cp tests/e2e/.env.ci .env
   docker compose -f docker-compose.e2e.yml up -d --build
   # ждать ~3-5 мин для healthy
   docker compose -f docker-compose.e2e.yml ps
   cd tests/e2e && npx playwright test --grep @smoke --project=chromium
   docker compose -f docker-compose.e2e.yml down -v
   ```

7. **Коммит:**
   ```
   test(e2e): docker-compose.e2e.yml + test JWT keys + CI integration (M13 G25.3)
   ```

### G25.4 — финализация _(15 мин)_

```bash
# CHANGELOG.md — секция G25 в [Unreleased]
# docs/e2e-testing.md — раздел "CI compose: docker-compose.e2e.yml"
git add CHANGELOG.md docs/e2e-testing.md
git commit -m "docs(m13): finalize G25 — CHANGELOG + e2e-testing.md (M13 G25.4)"
git tag -a v0.0.0-alpha.15 -m "M13 G25 ✅ — CI green: ruff + watchdog + e2e compose"
git push origin dev
git push origin v0.0.0-alpha.15
```

## После G25 ✅: возвращаемся к ОРИГИНАЛЬНОМУ плану

3. **Шаг 3 — Live VPS dry-run** по `docs/prod-deploy-checklist.md`
   (owner-driven). Зафиксировать findings в M13 NOTES.md G23 секции.
4. **Шаг 4 — Tag `v0.0.0` GA** после успешного dry-run + любые
   follow-up patches. Bump version в root `build.gradle.kts` +
   `frontends/*/package.json` на `0.0.0`. Push tag.

## Старт новой сессии — дословно

> Читаю в порядке:
>
> 1. `docs/milestones/NEXT-SESSION.md` — этот файл
> 2. `docs/milestones/M13-pre-deploy-hardening/CHECKLIST.md` —
>    Группа 25 секция
> 3. `docs/milestones/M13-pre-deploy-hardening/NOTES.md` —
>    Группа 25 секция (3 surprises + 4 unknowns)
> 4. `docker-compose.yml` (dev — 12 containers) +
>    `docker-compose.prod.yml` (prod-full — все services + observability)
> 5. `services/notification-service/notification-app/Dockerfile` —
>    понять multi-stage COPY contract
> 6. `.github/workflows/ci.yml` lines 232-317 — current e2e-auth job
> 7. `tests/e2e/auth-token-lifecycle.spec.ts` — что собственно
>    тестируется
>
> Спрашиваю владельца: **starting G25.1?** (быстрый ruff коммит).
> Потом G25.2 (mock fix коммит). Потом G25.3 (основная работа).

## Pending decisions (для new conversation)

1. **JWT key strategy** — A (generate in CI) или B (commit fixture).
   Рекомендация: B.
2. **Bitnami Mongo init** — env-based (вариант 1) или post-up `docker exec`
   (вариант 2). Локально протестировать оба перед commit.
3. **e2e compose: override prod либо самостоятельный?** — рекомендация:
   самостоятельный `docker-compose.e2e.yml` без observability stack
   (proще debug, быстрее boot, меньше container'ов).

## Local state на момент hand-off

Локально применены **uncommitted** изменения:

```
services/notification-bot/bot/__main__.py                          (ruff format)
services/notification-bot/bot/config.py                            (ruff format)
services/notification-bot/bot/notifications/alert_fired.py         (ruff format)
services/notification-bot/bot/notifications/otp_requested.py       (ruff format)
services/notification-bot/bot/services/idempotency_guard.py        (ruff format)
services/notification-bot/tests/test_alert_fired.py                (ruff format)
services/notification-bot/tests/test_callback_excuse.py            (ruff format)
services/notification-bot/tests/test_callback_late_checkin.py      (ruff format)
services/notification-bot/tests/test_callback_prefs.py             (ruff format)
services/notification-bot/tests/test_consumer_watchdog.py          (4 mock signatures)
```

Проверить через `git diff --stat` перед коммитом G25.1.

---

## История milestone'ов (архив)

M01-M08 ✅ (см. git tags `v0.0.0-alpha.1..alpha.9`).
M09 Prod Release Blockers ✅ 2026-04-24 (`v0.0.0-alpha.10`).
M10 Notification History ✅ 2026-04-24 (`v0.0.0-alpha.11`).
M11 OpenAPI Polish ✅ 2026-04-24 (`v0.0.0-alpha.12`).
M12 Auth Contract-first Refactor ✅ 2026-04-24 (`v0.0.0-alpha.13`).
M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.14`)
**→ re-open Группа 25** (CI hot-fixes + e2e infra) → `v0.0.0-alpha.15`.

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
