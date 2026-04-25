# E2E testing — runbook (NEW-161)

Playwright end-to-end тесты для RutCampusTrack.

## Что покрывается

| Spec | Сценарий | Roles |
|------|----------|-------|
| `auth.spec.ts` | login → schedule visible → logout clears state | student, admin |
| `auth-token-lifecycle.spec.ts` (M13 G22) | HttpOnly cookie + role-guard + refresh + logout cookie clear + offline/online STOMP reconnect | student, admin |
| `headman-mark.spec.ts` | headman bulk-mark → WebSocket live-update | headman |
| `student-excuse.spec.ts` | excuse + 10MB PDF upload → headman approves | student, headman |
| `admin-create-user.spec.ts` | admin creates user → initial_password visible | admin |
| `role-admin.spec.ts` | admin-only paths + cross-role guard | admin |
| `role-teacher.spec.ts` | teacher read-only + cross-role guard | teacher |
| `role-student.spec.ts` | student paths + headman blocked | student |
| `role-headman.spec.ts` | headman paths + student fallback | headman |

axe-core integration: **каждый spec** запускает
`assertNoA11yCriticalOrSerious(page)` → **zero CRITICAL+SERIOUS**
violations (WCAG 2.1 AA). MODERATE/MINOR → `docs/a11y-checklist.md`
следующий pass.

## Быстрый старт

```bash
cd tests/e2e
npm install
npm run install:browsers

# Запуск (все specs, Chromium + WebKit + mobile-chrome)
npm test

# Только Chromium
npm run test:chromium

# UI mode для отладки
npm run test:ui

# Отчёт
npm run report
```

Default `baseURL = https://localhost` (M13 G25.3 — e2e compose
терминирует HTTPS через self-signed cert, нужно потому что
`AuthCookies` выставляет `Secure` flag на refresh cookie).
`ignoreHTTPSErrors: true` в `playwright.config.ts` принимает self-signed.

Для VPS:
```bash
E2E_BASE_URL=https://ruttrack.site npm test
```

Для **локального dev** stack'а (без HTTPS, через `gradle bootRun` +
`npm run dev`):
```bash
E2E_BASE_URL=http://localhost npm test
# T1/T3/T4 specs из auth-token-lifecycle упадут — refresh cookie
# `Secure` flag без HTTPS browser'ом отбрасывается. Используй CI
# compose ниже для полного покрытия.
```

## Инфраструктура

Playwright тестам нужно **живое окружение**:
- `docker compose up -d` — Postgres × 2, Mongo, Redis, RabbitMQ
- 5 Java-сервисов (api-gateway:8080, auth:9090, academic:9091,
  schedule:9092, attendance:9093)
- web-panel (Angular) на `/`
- PWA (React+Vite) на `/app/`
- nginx reverse-proxy (опц. для ruttrack.site)

Для локального dev-прогона: `scripts/m07-g3-launch-services.sh`
(из M07) запускает 5 сервисов, `cd frontends/web-panel && npm run
start` / `cd frontends/pwa && npm run dev` — фронты.

## CI job (M13 G22 + G25.3)

**`e2e-auth` job** в `.github/workflows/ci.yml` бежит на каждый push.
Setup: docker-compose.e2e.yml stack + Playwright @smoke specs (8 тестов
в 2 specs). Детали — секции «CI integration» и «CI compose» ниже.

Полный suite (включая `headman-mark`, `student-excuse`,
`role-*` × 4 specs) остаётся локальной командой `npm test`. Перенос
полного suite в CI — scope v0.1 (требует scaling beyond `@smoke`).

## Post-deploy smoke (scripts/smoke-prod.sh)

Lightweight curl-based smoke без Playwright для release-process:

```bash
scripts/smoke-prod.sh https://ruttrack.site student student_test_pass
```

Exit codes:
- `0` — всё ok (health + login + schedule + logout прошли)
- `1` — health-check fail
- `2` — login fail
- `3` — schedule fail

Запускается как последний шаг deploy.yml перед merge to main (будет
настроено в M09).

## Паттерны написания spec'а

### 1. Login через shared helper

```typescript
import { loginAs, logout } from '../fixtures/auth';
import { TEST_USERS } from '../fixtures/users';

test('scenario', async ({ page }) => {
  await loginAs(page, TEST_USERS.student);
  // ... test body
  await logout(page);
});
```

### 2. Multi-user сценарий (student + headman одновременно)

```typescript
test('cross-user', async ({ page, browser }) => {
  await loginAs(page, TEST_USERS.student);
  // ... student действует

  const headmanContext = await browser.newContext();
  const headmanPage = await headmanContext.newPage();
  try {
    await loginAs(headmanPage, TEST_USERS.headman);
    // ... headman действует
  } finally {
    await headmanContext.close();
  }
});
```

### 3. axe-core integration

Каждый spec вызывает `assertNoA11yCriticalOrSerious(page)` после
navigation / action, когда DOM стабилен. Scope — `main` (избегаем
false positives на Angular router-outlet'ах).

### 4. Data-testid locators

Предпочитать `data-testid="lesson-card"` over CSS-селекторы —
устойчивее к CSS refactor'ам.

Добавить в компонент:
```html
<mat-card [attr.data-testid]="'lesson-card'">...</mat-card>
```

Использовать в spec:
```typescript
const lessonCard = page.locator('[data-testid="lesson-card"]').first();
```

### 5. @smoke tag

Тесты с `@smoke` в title/describe попадают в post-deploy smoke
(grep pattern `--grep @smoke`). Минимум — 1-2 flow'а (auth + schedule).

## Известные ограничения v0.0.0

- **CI job: только `@smoke`** — 8 тестов в 2 specs (`auth.spec.ts`,
  `auth-token-lifecycle.spec.ts`). Полный suite (8 specs, ~30+ тестов)
  — локальный `npm test`. Расширение CI на full suite — scope v0.1.
- **Seed test users** — `student`/`teacher`/`admin`/`headman` захардкожены
  в `fixtures/users.ts`. Синхронизация с `V2__seed_test_data.sql`
  требуется вручную (ArchUnit правило — идея для v0.1).
- **test-excuse.pdf** — 10MB PDF для excuse upload ожидается в
  `tests/e2e/fixtures/test-excuse.pdf`. Файл НЕ в git (генерируется
  один раз локально: `dd if=/dev/urandom of=test-excuse.pdf bs=1M
  count=10` или любой валидный PDF 10MB+).
- **Mini-app skip** — per DECISIONS D1 (M08). Telegram Mini App не
  покрывается E2E до v0.1 (mini-app not ready).
- **Firefox skip** — instability в CI downloads. PWA не officially
  Firefox-target.

## FAQ

**Q: Как запустить один spec?**
A: `npx playwright test specs/auth.spec.ts --project=chromium`

**Q: Как ускорить локальный прогон?**
A: `npm run test:chromium` — без WebKit (webkit ~2× slow на Linux).

**Q: Тест падает на WebSocket — что делать?**
A: Увеличить `expect({ timeout: 10_000 })` локально для WebSocket
   waits. STOMP reconnect M07 G5 имеет exponential backoff, первый
   connect может занимать до 3s на slow runners.

**Q: Как подключить VPN/proxy?**
A: `playwright.config.ts` — добавить `use.proxy.server = 'socks5://...'`.

## CI integration (M13 G22 + G25.3)

GitHub Actions job `e2e-auth` (`.github/workflows/ci.yml`):

1. **`actions/checkout@v4` + `actions/setup-node@v4`** (Node 22).
2. **`docker/setup-buildx-action@v3`** — buildx нужен для BuildKit
   cache mount (`--mount=type=cache,target=/root/.gradle` в backend
   Dockerfile'ах). Без buildx default driver игнорирует cache mounts.
3. **Generate self-signed TLS cert** — `bash tests/e2e/infra/scripts/generate-test-certs.sh`.
   `AuthCookies` выставляет `Secure` flag → HTTPS обязателен.
4. **`docker compose -f docker-compose.e2e.yml up -d --build`** — поднять
   standalone e2e stack (см. ниже «CI compose: docker-compose.e2e.yml»).
5. Wait для healthchecks (max 8 мин, polls каждые 5 сек × 96).
   Cold CI runner: pulls + builds ~3-5 мин, Mongo RS init + 5 backend
   service start ~2-3 мин.
6. `cd tests/e2e && npm install && npx playwright install --with-deps chromium`.
7. Запуск **только `@smoke`-grouped specs** (`--grep @smoke`):
   - `auth.spec.ts` × 3 теста
   - `auth-token-lifecycle.spec.ts` × 5 тестов (M13 G22)
   `E2E_BASE_URL=https://localhost` через self-signed TLS.
   Полный suite остаётся локальной командой `npm test`.
8. `--project=chromium` only (WebKit пропущен в CI чтобы держать runtime
   < 30 мин; локально `npm test` гонит оба).
9. На failure — upload `playwright-report/` + `docker logs` для всех
   контейнеров (artefacts retention 7 дней).

**Cost:** ~10-15 мин на ubuntu-latest runner (cold) / ~6-8 мин (warm cache).
`timeout-minutes: 30`. Trigger — push на любую ветку (paths-ignore не
относится к `tests/`).

## CI compose: docker-compose.e2e.yml (M13 G25.3)

Standalone (НЕ override prod) compose — minimal prod-like stack для
эфемерного CI runner'а. **17 контейнеров**:

| Группа | Контейнеры |
|--------|------------|
| Infra (5) | postgres-academic, postgres-schedule, mongo-attendance (Bitnami RS rs0), redis, rabbitmq |
| Backend (5) | auth-service, academic-service, schedule-service, attendance-service, api-gateway |
| Notification (2) | notification-web, notification-bot |
| Frontend (4) | pwa-nginx, mini-app-nginx, web-panel-nginx, landing-nginx |
| Reverse-proxy (1) | nginx (HTTP→HTTPS + self-signed TLS termination) |

**Что НЕ включено vs prod:**
- Observability (tempo, alertmanager, prometheus, grafana, loki,
  promtail, blackbox-exporter, cadvisor, node-exporter)
- Certbot (SSL termination через self-signed cert вместо Let's Encrypt)
- `mem_limit` / `mem_reservation` (CI runner ~7GB, лишние ограничения =
  OOMKill flap)
- Digest-pin'ы образов (Renovate ротейтит — лишний noise в e2e)

**Тестовые секреты** — `tests/e2e/.env.ci` (committed, помечены
`# CI-ONLY — НЕ ИСПОЛЬЗОВАТЬ В PROD`). Все значения статичные плейсхолдеры
для эфемерного stack'а.

**JWT keys** — auth-service сам генерирует RSA-3072 key pair на старте
в `JwtService.init()`, пишет в named volume `jwt-keys`. Notification-web
читает через тот же volume `:ro`. Никаких commit'нутых fixture'ов.

**Bitnami Mongo replica set** — env-based init
(`MONGODB_REPLICA_SET_MODE=primary`, `MONGODB_REPLICA_SET_NAME=rs0`),
как в dev/prod compose. Single-node primary без secondaries.

**Self-signed TLS** — `tests/e2e/infra/certs/{server.crt,server.key}`,
генерируется `tests/e2e/infra/scripts/generate-test-certs.sh` (openssl
RSA-2048, CN=localhost, 365d). Certs gitignored, генерируются перед
каждым `docker compose up`.

**Локальный smoke-прогон:**
```bash
# 1. Test secrets
cp tests/e2e/.env.ci .env

# 2. TLS cert (idempotent — skip если уже есть)
bash tests/e2e/infra/scripts/generate-test-certs.sh

# 3. Boot stack (~10-15 мин cold, ~3-5 мин warm)
docker compose -f docker-compose.e2e.yml up -d --build

# 4. Wait healthy
docker compose -f docker-compose.e2e.yml ps

# 5. Run specs
cd tests/e2e
npm install
npx playwright install --with-deps chromium
npx playwright test --project=chromium --grep @smoke

# 6. Cleanup
cd ../..
docker compose -f docker-compose.e2e.yml down -v
rm .env
```

**Trade-off vs dev compose** (`docker-compose.yml`): dev compose не
содержит backend Java сервисов (запускаются через `gradle bootRun`
локально). e2e compose self-contained и build всё from source — это
prod-like, но cold build тратит ~10 мин.

## Источники

- M08 PLAN.md Группа 5 — `docs/milestones/M08-test-infrastructure/PLAN.md`
- M08 DECISIONS D1 (mini-app skip) — `docs/milestones/M08-test-infrastructure/DECISIONS.md`
- M07 G10 a11y baseline — `docs/a11y-checklist.md`
- M13 G18 STOMP heartbeat — `docs/websocket-flow.md`
- Playwright docs — https://playwright.dev/
