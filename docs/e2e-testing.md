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

Default `baseURL = http://localhost`. Для VPS:
```bash
E2E_BASE_URL=https://ruttrack.site npm test
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

## CI job (отложен до M09)

**В CI job `e2e-tests` не включён в v0.0.0.** Причина: требуется
`docker-compose up` + ожидание healthy + запуск Playwright + teardown
— ~15 минут CI time + комплексная оркестрация. VPS/staging стабильного
окружения нет до M09 (prod-deploy-checklist).

**Plan для M09**:
1. `.github/workflows/e2e.yml` — workflow_dispatch + nightly schedule.
2. GH runner: `docker compose -f docker-compose.ci.yml up -d
   --wait` (healthchecks + `depends_on: condition: service_healthy`).
3. Seed test users через Flyway (academic V2 seed или отдельная
   V-скрипта в test profile).
4. `cd tests/e2e && npm install && npm test`.
5. On failure — upload traces/screenshots/videos как artifacts (14d
   retention).

Эти шаги — scope Группы 12 M08 (финализация) или M09.

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

- **Без CI job** — ручной прогон локально. См. M09 plan выше.
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

## CI integration (M13 G22)

GitHub Actions job `e2e-auth` (`.github/workflows/ci.yml`):

1. Java 21 + Node 22 + Gradle setup.
2. `./gradlew assemble` — собрать все backend service jar'ы.
3. `npm ci && npm run build` для PWA + mini-app + web-panel frontends.
4. `docker compose up -d --build` — поднять полный dev stack (5 backend +
   gateway + nginx + Postgres×2 + Mongo + Redis + Rabbit + 4 frontend-nginx).
5. Wait для healthchecks (max 4 мин, polls каждые 5 сек).
6. `cd tests/e2e && npm install && npx playwright install --with-deps chromium`.
7. Запуск **только `@smoke`-grouped specs** (`--grep @smoke`):
   - `auth.spec.ts` × 3 теста
   - `auth-token-lifecycle.spec.ts` × 5 тестов (M13 G22)
   Полный suite остаётся локальной командой `npm test`.
8. `--project=chromium` only (WebKit пропущен в CI чтобы держать runtime
   < 5 мин; локально `npm test` гонит оба).
9. На failure — upload `playwright-report/` + `docker logs` для всех
   контейнеров (artefacts retention 7 дней).

**Cost:** ~5-7 мин на ubuntu-latest runner. Trigger — push на любую ветку
(paths-ignore не относится к `tests/`).

## Источники

- M08 PLAN.md Группа 5 — `docs/milestones/M08-test-infrastructure/PLAN.md`
- M08 DECISIONS D1 (mini-app skip) — `docs/milestones/M08-test-infrastructure/DECISIONS.md`
- M07 G10 a11y baseline — `docs/a11y-checklist.md`
- M13 G18 STOMP heartbeat — `docs/websocket-flow.md`
- Playwright docs — https://playwright.dev/
