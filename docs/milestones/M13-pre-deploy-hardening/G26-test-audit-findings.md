# G26 — Test Audit Findings

Дата: 2026-04-26  
Аудит проведён по итогам G25 hot-fix marathon. Охват: JUnit IT, Playwright e2e, pytest notification-bot, Vitest web-panel/pwa.

---

## Категория A — E2E (Playwright): несуществующие `data-testid` локаторы (P1)

**Суть:** Несколько спецификаций обращаются к `data-testid`-атрибутам которые отсутствуют в актуальных Angular-шаблонах. Тесты проходят (или падают) не по логике приложения, а по отсутствию элемента, что делает их `flaky` или вечно-зелёными.

| Файл | Локатор | Статус в шаблоне |
|------|---------|-----------------|
| `tests/e2e/specs/role-teacher.spec.ts:34` | `[data-testid="red-zone-badge"]` | Отсутствует во всех `.html` web-panel |
| `tests/e2e/specs/headman-mark.spec.ts:29` | `[data-testid="lesson-card"]` | Отсутствует (класс `.today-lesson-card`, не `data-testid`) |
| `tests/e2e/specs/headman-mark.spec.ts:52` | `[data-testid="group-attendance-count"]` | Отсутствует |
| `tests/e2e/specs/student-excuse.spec.ts:34` | `[data-testid="lesson-picker-item"]` | Отсутствует |
| `tests/e2e/specs/admin-create-user.spec.ts:49` | `[data-testid="initial-password-display"]` | Отсутствует |
| `tests/e2e/specs/student-excuse.spec.ts:56` | `[data-testid="excuse-card"]` | Отсутствует (класс `.excuse-card`, не testid) |

**Что сломано:** Тест `role-teacher.spec.ts` "journal shows red-zone indicator" использует `expect(page.locator('body')).toBeVisible()` после промаха по `[data-testid="red-zone-badge"]` — тест всегда зелёный независимо от наличия badge. Остальные тесты (`headman-mark`, `student-excuse`, `admin-create-user`) упадут при запуске с `timeout` на ожидании несуществующего элемента.

**Fix:** Добавить `data-testid` атрибуты в соответствующие шаблоны, либо заменить локаторы на семантические (ARIA role + текст). Второй вариант предпочтителен (не требует изменения HTML).

---

## Категория B — E2E (Playwright): неверный маршрут `/teacher/schedule` (P1)

**Файл:** `tests/e2e/specs/role-teacher.spec.ts:18`

```ts
const paths = ['/teacher/schedule', '/teacher/stats'];
```

**Проблема:** Маршрут `/teacher/schedule` не определён в `TEACHER_ROUTES` (`teacher.routes.ts`). Существуют: `dashboard`, `journal`, `stats`. Роут `/teacher/schedule` вернёт 404-like "Страница не найдена" (wildcard `**` → NotFoundComponent) вместо ожидаемой страницы расписания.

**Тест упадёт** так как `expect(page).toHaveURL(new RegExp('/teacher/schedule'))` не совпадёт — Angular role-guard или wildcard redirect выдаст другой URL.

**Fix:** Убрать `/teacher/schedule` из списка (или добавить маршрут если feature запланирована).

---

## Категория C — E2E (Playwright): `waitForTimeout` без условия — flaky (P2)

**Файл:** `tests/e2e/specs/auth-token-lifecycle.spec.ts:122`

```ts
await context.setOffline(true);
await page.waitForTimeout(5_000);   // безусловный sleep 5 сек
await context.setOffline(false);
```

**Проблема:** `waitForTimeout` — жёсткий sleep. В медленной среде STOMP-reconnect может не успеть за 5 сек; на быстрой машине — избыточное замедление CI. После `setOffline(false)` тест делает `page.reload()` что само по себе является лучшим сигналом готовности, чем таймаут.

**Fix:** Заменить `waitForTimeout` + `reload` на `context.setOffline(false)` → `await page.waitForLoadState('networkidle')` (после reload).

---

## Категория D — E2E (Playwright): `test.skip` с TODO без трекинга (P2)

**Файл:** `tests/e2e/specs/auth.spec.ts:101-106`

```ts
test.skip(true, 'TODO M13 G25.24: headman dashboard color-contrast violation ...')
```

**Проблема:** Тест пропускается безусловно (`skip(true, ...)`). Нет механизма который напомнил бы о нём. Комментарий ссылается на "следующий запуск --grep @a11y" который никогда не упустит тест (`skip` переопределяет `@a11y`).

**Fix:** Либо убрать `test.skip` (оставив `@a11y` тег как gate), либо завести GitHub issue и поставить условный skip через переменную окружения (`process.env.SKIP_A11Y`).

---

## Категория E — E2E (Playwright): `role-student.spec.ts` — seed user является headman (P1)

**Файл:** `tests/e2e/specs/role-student.spec.ts:14-39`

```ts
test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.student); // student имеет is_headman=true
});

test('cannot access /headman/* without is_headman=true', ...
    await expect(page).toHaveURL(/\/student\//, ...);   // НИКОГДА не выполнится
```

**Проблема:** `TEST_USERS.student` в `fixtures/users.ts` — это seed user с `is_headman=true`. Тест "cannot access /headman/* without is_headman=true" при логине этого пользователя получит доступ к `/headman/dashboard` (role-guard пропустит). Ожидание `URL /\/student\//` не выполнится — тест упадёт или даст false positive в зависимости от порядка redirect.

Аналогично: `test('cannot access /admin/* or /teacher/*')` прогоняется от headman-student, и redirect уйдёт на `/headman/`, а не `/student/` — assertion `toHaveURL(/\/student\//)` провалится.

**Fix:** Либо создать в seed отдельного `student_plain` (is_headman=false), либо убрать эти тесты из `role-student.spec.ts` (они проверяют функционал который недостижим с текущим seed пользователем).

---

## Категория F — Vitest (web-panel): `login.component.spec.ts` — описание теста не соответствует поведению (P2)

**Файл:** `frontends/web-panel/src/app/features/login/login.component.spec.ts:121`

```ts
it('on successful login, calls AuthService.setTokens and ...
    expect(mockAuthService.setAccessToken).toHaveBeenCalledWith(TEACHER_TOKEN);
```

**Проблема:** Название теста говорит "calls AuthService.setTokens", но `login.component.ts:53` вызывает `setAccessToken()`, а не `setTokens()`. В mock объекте присутствуют оба (`setTokens: vi.fn()` и `setAccessToken: vi.fn()`). Assertion корректна (`setAccessToken`), но название вводит в заблуждение и может маскировать будущую регрессию если кто-то обновит component обратно на `setTokens`.

**Fix:** Переименовать test description: `'on successful login, calls AuthService.setAccessToken ...'`

---

## Категория G — Java IT: `NotificationHistoryConsumerIT` — безусловный `Thread.sleep(1500)` (P2)

**Файл:** `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java:116-120`

```java
try {
    Thread.sleep(1500);
} catch (InterruptedException e) { ... }
assertThat(repository.findAll()).isEmpty();
```

**Проблема:** Negative assertion ("пустой репозиторий после N мс") — классический flaky паттерн. 1500 мс достаточно для медленного CI, но не гарантировано. Если consumer обрабатывает событие медленнее чем ожидается — тест даст false positive.

**Fix:** Использовать Awaitility с `await().during(1, SECONDS).atMost(3, SECONDS).until(() -> repository.count() == 0)` или `await().pollDelay(1, SECONDS)...`. Это проверяет что в течение окна ничего не появилось, а не фиксирует произвольный sleep.

---

## Категория H — Java IT: `StompIntegrationIT` — `Thread.sleep(300)` перед subscribe (P2)

**Файл:** `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java:145`

```java
TimeUnit.MILLISECONDS.sleep(300);
// затем convertAndSend
```

**Проблема:** Гонка — 300 мс ожидание subscription ack. На нагруженном CI может быть недостаточно.

**Fix:** STOMP `StompSession.subscribe()` возвращает `Subscription`. Можно использовать `CountDownLatch` или `BlockingQueue.poll(timeout)` перед первым отправленным сообщением вместо фиксированного sleep. Альтернативно — Awaitility.

---

## Категория I — Python (notification-bot): `guard` fixture — `@pytest.fixture` вместо `@pytest_asyncio.fixture` (P1)

**Файл:** `services/notification-bot/tests/test_idempotency_guard.py:8-10`

```python
@pytest.fixture           # ← НЕ @pytest_asyncio.fixture
async def guard(fake_redis):
    g = BotIdempotencyGuard(redis_client=fake_redis)
    yield g
```

**Проблема:** При `asyncio_mode = auto` (pytest.ini) `pytest-asyncio` автоматически оборачивает `async def test_*` функции. Однако `async def` fixture с декоратором `@pytest.fixture` (не `@pytest_asyncio.fixture`) — поведение зависит от версии pytest-asyncio. В строгом режиме (asyncio_mode=strict, которого здесь нет) это вызвало бы ошибку. В `auto` режиме работает в текущей конфигурации, но `conftest.py:8` использует правильный `@pytest_asyncio.fixture` для `fake_redis`, а `guard` — нет. Несогласованность: если версия pytest-asyncio изменится или проект перейдёт на strict mode, `guard` fixture сломается.

**Fix:** Заменить `@pytest.fixture` на `@pytest_asyncio.fixture` для `async def guard`.

---

## Категория J — Java IT: `AuthOtpFlowIT` — собственный Testcontainers setup дублирует `AbstractIntegrationTest` (P2)

**Файл:** `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java:152-187`

**Проблема:** `AuthOtpFlowIT` объявляет собственные static `POSTGRES`, `REDIS`, `RABBITMQ` поля с `@DynamicPropertySource` вместо наследования от `AbstractIntegrationTest`. При этом она не является его наследником (`class AuthOtpFlowIT` без extends). Это означает два набора контейнеров запускаются параллельно в одном JVM — `AbstractIntegrationTest.POSTGRES` и `AuthOtpFlowIT.POSTGRES` — хотя оба `withReuse(true)`. При reuse=false (CI) это удваивает время старта.

Кроме этого: в `AuthOtpFlowIT` поля `POSTGRES`/`REDIS`/`RABBITMQ` объявлены как `static final` без аннотации `@Container` — Testcontainers lifecycle не управляется автоматически; правильно работает только благодаря явному `start()` в static initializer.

**Fix (medium priority):** Вынести Rabbit-aware базовый класс `AbstractAuthEventIntegrationTest extends AbstractIntegrationTest` + добавить Rabbit контейнер. Устранит дублирование static init.

---

## Категория K — Java IT: `RestApiIT` — `@TestMethodOrder` + `@Transactional` = зависимые тесты (P2)

**Файл:** `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java:32`

**Проблема:** Класс использует `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` с `@Order(N)` для нумерованных тестов. Часть тестов помечена `@Transactional` (авторолбек), часть нет. Тесты с `@Order(4)` и `@Order(5)` содержат `jdbcTemplate.update("UPDATE semesters SET is_active = false")` — сайд-эффект который не откатывается (нет `@Transactional`). Это нарушает изоляцию тестов: порядок выполнения влияет на результат.

**Fix:** Либо пометить все тесты `@Transactional`, либо перенести seed/teardown в `@BeforeEach`/`@AfterEach`. Убрать `@TestMethodOrder`.

---

## Краткий итог по severity

| Severity | Кол-во | Категории |
|----------|--------|-----------|
| P1 — ложный pass / упадёт в prod | 4 | A (testid), B (teacher/schedule), E (student/headman seed), I (async fixture) |
| P2 — cleanup / ненадёжность | 6 | C, D, F, G, H, J, K |

**Самые критичные для немедленного исправления:**
1. **[E]** `role-student.spec.ts` — тест "cannot access /headman/*" использует headman user → assertion гарантированно неверна
2. **[B]** `role-teacher.spec.ts` — `/teacher/schedule` маршрут не существует
3. **[A]** Все `data-testid` локаторы в `headman-mark`, `student-excuse`, `admin-create-user` спеках — отсутствуют в шаблонах
4. **[I]** `guard` fixture — `@pytest.fixture` для async coroutine вместо `@pytest_asyncio.fixture`
