# Ответы владельца на вопросы аудита

Сессия аудита: 2026-04-18 (продолжение). Файл накапливается по ходу разговора.
Итог фазы 99 будет опираться на этот файл.

Источник вопросов: секции «Вопросы к владельцу проекта» в отчётах 01–10, 12–15
(итого 14 файлов, 141 вопрос; 16 без вопросов; 11 пропущен).

---

## Meta-решения (глобальные)

Сюда попадают ответы, которые автоматически закрывают/переклассифицируют
проблемы в нескольких отчётах одновременно.

### M1 — Юрисдикция: проект НЕ в РФ
- **Решение:** Сервис физически развёрнут вне юрисдикции РФ. Закон 152-ФЗ
  (О персональных данных) НЕ применяется. Отсутствие политики
  конфиденциальности и пользовательского соглашения — НЕ юридический риск.
- **Источник:** прямое указание владельца перед стартом фазы.
- **Технические следствия (НЕ автоматические — требуют отдельного решения
  владельца):**
  - Хранение `initial_password` в plaintext (БД, REST, gRPC, Telegram) —
    больше не «нарушение 152-ФЗ», но остаётся технический риск:
    - XSS / supply-chain → пароль читается из localStorage/Telegram-чата
    - Telegram-оператор видит plaintext-пароль в трафике бота
    - Если пользователь использует тот же пароль где-то ещё — прямая
      компрометация
  - Хранение GPS-координат геоотметки (или их отсутствие) — больше не
    обязательная privacy-policy, но остаётся бизнес-вопрос «нужен ли
    след для anti-spoof».
  - Имена студентов в push-уведомлениях — больше не ПДн в юридическом
    смысле, но остаётся UX-вопрос «комфортно ли видеть имена на lock-screen».
- **Связанные вопросы (требуют явного ответа владельца):**
  - 01-Q1 (initial_password)
  - 02-Q1 (initial_password в REST/gRPC)
  - 04-Q2 (GPS-координаты)
  - 06-Q2 (история показа initial_password)
  - 08-Q1 (initial_password в proto)
  - 09-Q6 (имена студентов в push)
  - 10-Q7 (initialPassword в admin-таблице)
  - 12-Q5 (страница приватности на лендинге)
  - 15-Q2 (выбор magic-link vs разовый пароль)

---

## По отчётам

### 01-auth-service
_(отвечено: 6 / 10)_

- **Q-P0-1.** Создать `auth-api-contract` модуль?
  **Ответ (2026-04-18):** **(a)** ДА. Создаётся `auth-api-contract`
  (java-library без Spring Boot) с интерфейсом `AuthApi`, DTO (records
  для request, classes для response), аннотациями `@Operation`/`@ApiResponse`.
  `AuthController implements AuthApi`. Estimate ~1 день.
  Closes 01 P0-1.
- **Q-P0-3.** Выделить `auth_db` от `academic_db`?
  **Ответ (2026-04-18):** **(c) ACCEPTED** — оставляем shared-DB как есть.
  Auth подключён к `academic_db`, academic владеет миграциями `users`.
  Решение принято осознанно при проектировании: один разработчик, тесная
  связь auth+users, performance важнее изоляции, JOIN'ы возможны.
  Документировать как «осознанный shared-DB tradeoff» в `docs/architecture/architecture.md`
  (NEW-1 уже это покрывает).
  **Вариант (b) — auth-owned schema** перенесён в `docs/future-ideas.md`
  (раздел «Архитектура») для v0.1+ когда понадобится auth-only функционал
  (MFA, login-аналитика, lockout policies).
  Closes 01 P0-3.
- **Q-P0-4.** OTP-код в HTTP body?
  **Ответ (2026-04-18):** **(a)** Убрать `code` из response. Auth
  публикует `otp.requested {telegram_id, code, ttl_seconds}` в RabbitMQ.
  Бот читает событие и шлёт код в Telegram. Тело HTTP-ответа — `204 No
  Content` или `{"delivery": "telegram"}`. Estimate ~1-2 дня.
  Closes 01 P0-4 + связано с 08 P0-2 (нужна схема `otp.requested`).
  **Каскад:** 08 P0-2 (нет схемы `otp.requested`) теперь становится
  ОБЯЗАТЕЛЬНОЙ зависимостью этого фикса — нельзя выкатить (a) без
  отдельной схемы события.
- **Q-P0-5.** Timing-атака на OTP?
  **Ответ (2026-04-18):** **(a)** Заменить `String.equals` на
  `MessageDigest.isEqual(storedCode.getBytes(StandardCharsets.UTF_8),
  request.code().getBytes(StandardCharsets.UTF_8))`. 1 строка, ~5 минут.
  Closes 01 P0-5.

- **Q1.** initial_password — это временная мера или фича?
  **Ответ (2026-04-18):** **(a) ACCEPTED BY OWNER** — by design, оставляем
  plaintext в БД и в Telegram. Юридического риска нет (M1). Технические
  риски (XSS / supply-chain / Telegram-оператор / повторное использование
  пароля) явно приняты.
  Идея magic-link перенесена в `docs/future-ideas.md` (раздел «Безопасность
  → Magic-link для первого входа») для возможной реализации в v0.1+.

- **Q1.** initial_password — это временная мера или фича?
  **Ответ (2026-04-18):** **(a) ACCEPTED BY OWNER** — by design, оставляем
  plaintext в БД и в Telegram. Юридического риска нет (M1). Технические
  риски (XSS / supply-chain / Telegram-оператор / повторное использование
  пароля) явно приняты.
  Идея magic-link перенесена в `docs/future-ideas.md` (раздел «Безопасность
  → Magic-link для первого входа») для возможной реализации в v0.1+.
  **Последствия (каскад):**
  - Кластер C0-2 в 15-cross-cutting → **РАСПУСКАЕТСЯ**, перемещается в
    «Принято как есть».
  - 01 P0-2 (БД хранит plaintext) → Accepted tradeoff.
  - 02 P0-1 (REST + gRPC отдают plaintext) → Accepted tradeoff.
  - 06 P0-3 (plaintext в Telegram без self-destruct) → Accepted tradeoff
    (но под-вопрос «история показа» 06-Q2 ещё надо обсудить).
  - 08 P0-1 (initial_password в proto-контракте) → Accepted tradeoff.
  - 10 P2-13 (initialPassword в admin-таблице) → Accepted (даже усиливается
    как заявленная фича).
  - 02 P2-1 (DEBUG-логи в default — пишут initialPassword) → СОХРАНЯЕТСЯ:
    проблема не в самом пароле, а в его попадании в файлы логов; это
    отдельный вектор утечки помимо штатных каналов. Помечу как «связанная,
    но не закрытая».
  - 13 P0-3 (`.env.prod` с секретами в репо) → СВЯЗЬ ослабевает (там
    упомянута зависимость от 08 P0-1), но ротация BOT_TOKEN/GHCR/VAPID
    остаётся обязательной по своим причинам.
  - 12-Q5 (privacy-страница на лендинге) → **AUTO-RESOLVED** через M1:
    юр. требования нет, владелец позже сам решит, нужна ли страница.
  - 15-Q2 (выбор magic-link vs разовый пароль) → **AUTO-RESOLVED**:
    выбран вариант (a) — не делаем ничего, accept tradeoff.

  **NEW (от этого решения):** Появляется обязанность задокументировать
  «accepted tradeoff» в `docs/architecture/architecture.md` или `docs/security-model.md`
  как явное архитектурное решение (чтобы будущие разработчики/аудиторы
  не «пере-открывали» эту дискуссию). Добавлю в TODO для 99.

### 02-academic-service
_(отвечено: 6 / 12)_

- **Q-P0-3.** `NumberFormatException` при кривом `X-User-Id` → 500?
  **Ответ (2026-04-18):** **(a) AUTO-RESOLVED через C0-1.** После перехода
  на `Authorization: Internal <jwt>` (02-Q2) `UserContextFilter` будет
  парсить JWT, а не заголовок. `JwtParser.parse` кидает правильные
  исключения (`MalformedJwtException`, `ExpiredJwtException`) → 401, не 500.
  Специальный фикс не нужен, страховочный try-catch избыточен.
  **Закрывает:** 02 P0-3, 03 P0-3, 04 P0-3 (одинаковая проблема в трёх
  сервисах).
  **Каскад:**
  - 02 P0-3, 03 P0-3, 04 P0-3 → помечаются «AUTO-RESOLVED через C0-1»
    в своих отчётах.
  - В 15-cross-cutting-issues.md — добавить в описание C0-1 что он
    заодно закрывает 3 специфичных P0 (было «5 P0» → станет «5 P0 +
    3 AUTO через unification»).

- **Q-P0-4.** Race в `SemesterService.activateSemester`?
  **Ответ (2026-04-18):** **(c) ACCEPTED BY OWNER** — полагаемся на
  single-admin invariant (один админ, два одновременных клика не делает).
  Для pet-проекта с одним админом реальный риск нулевой.
  **Действия:**
  - Добавить javadoc в `SemesterService.activateSemester(...)`: «Method
    is NOT thread-safe. Relies on single-admin invariant. If multi-admin
    becomes a requirement, add SELECT FOR UPDATE or SERIALIZABLE isolation.»
  - Документировать принятый tradeoff в `docs/architecture/architecture.md` (раздел
    «Принятые архитектурные tradeoffs», создаётся через NEW-1).
  **Последствия (каскад):**
  - 02 P0-4 → «✅ ACCEPTED — single-admin invariant».
  - Если в будущем появится вторая роль admin (co-admin, помощник) —
    эта проблема снова становится P0. Зафиксировать в `docs/future-ideas.md`
    как «триггер пересмотра».

- **Q-P0-5.** Пустой `${GRPC_SECRET:}` default?
  **Ответ (2026-04-18):** **(a)** Убрать default → `${GRPC_SECRET}`.
  Если переменной нет — Spring fail-fast на старте, контейнер не
  поднимается. Громкая ошибка лучше тихой дыры.
  **Мотивация (зафиксирована после обсуждения с владельцем):**
  - В **dev-окружении** (`docker-compose.yml` без `.env.prod`) переменной
    нет → Spring читает `""` → gRPC-интерцептор сравнивает `"" == ""`,
    любой клиент проходит. Разработчик не видит регрессию.
  - В **CI** переменная не выставлена → тесты gRPC проходят с пустым
    секретом → тесты не покрывают реальную prod-логику.
  - В **проде** опечатка в `.env.prod` (удалённая строка) → сервис
    поднимается с пустым секретом, health-check зелёный, никакого
    сигнала о проблеме. Если порты 9091-9094 случайно открылись или
    прокинут SSH-туннель — gRPC без аутентификации.
  **Закрывает:** 02 P0-5, 06 P0-2 (бот — тот же паттерн
  `${GRPC_SECRET:}` пустой default).
  **Каскад:**
  - 02 P0-5 → 🔧 TO-FIX (fail-fast).
  - 06 P0-2 → 🔧 TO-FIX (тот же фикс в `notification-bot/config.py`).
  - `.env.prod.example` (NEW-20/21 от C0-9) должен включать
    `GRPC_SECRET=` с комментарием, чтобы при копировании файла ничего
    не забыли.
  - В CI workflow (`.github/workflows/ci.yml`) добавить `env:
    GRPC_SECRET: test-secret` — без этого тесты упадут после фикса.
  **Estimate:** ~1 час (убрать default × 2 места + CI-env + smoke-тест
  что пустая переменная теперь валит старт).
  **NEW (порождённые задачи):**
  - **NEW-24:** audit всех `${VAR:}` паттернов в application.yml
    всех сервисов — любой default=«» это потенциально такая же тихая
    дыра. Искать regex `\$\{[A-Z_]+:\}` в `services/*/src/main/resources`.
  - **NEW-25:** в CI добавить environment-variable smoke-тест: спеллинг
    переменной изменился / secret удалён → контейнер не стартует.
    Простая проверка — запуск `docker compose -f docker-compose.prod.yml
    config` без `.env.prod` должен падать с понятной ошибкой.

- **Q-P0-7.** Загрузка всех ДЗ в память + N+1?
  **Ответ (2026-04-18):** **(a)** Pageable + `@EntityGraph` / `JOIN FETCH`
  в одном запросе. Стандартный Spring-рецепт, ~30 минут работы,
  закрывает OOM-риск и N+1 одновременно.
  **Что делается:**
  - `HomeworkRepository.findAll()` → `Page<Homework> findAll(Pageable
    pageable)` с `@EntityGraph(attributePaths = {"author", "subject",
    "group"})`.
  - Контроллер принимает `Pageable` (Spring Data автоматически).
  - Response → `PagedModel<EntityModel<HomeworkResponse>>` (HATEOAS).
  - Фронт (web-panel, PWA) — добавить pagination UI.
  **Последствия (каскад):**
  - 02 P0-7 → 🔧 TO-FIX.
  - 09 / 10 — фронты сейчас ожидают массив `Homework[]`, после фикса
    структура ответа меняется на `PagedModel`. **Breaking change** для
    клиентов → связать с C1-2 (OpenAPI→TS type-gen) чтобы поймать type
    drift автоматически.
  - Аналогичный аудит нужен для других `findAll()` без Pageable —
    похожая проблема может быть в `GroupService`, `SubjectService`,
    `UserService` и т.д. (см. NEW-26).
  **Estimate:** ~0.5 дня (backend) + ~1 день (фронт migration × 2 клиента
  + тесты pagination).
  **NEW:**
  - **NEW-26:** audit всех `findAll()` без Pageable во всех сервисах.
    `Grep -rn "findAll()" services/*/src/main/java`. Большинство админ-
    ендпоинтов (users, groups, subjects, semesters) имеют ту же
    потенциальную проблему. Классифицировать: «сейчас мало данных,
    accept» vs «растёт, нужна пагинация».
  - **NEW-27:** связка с C1-2 (OpenAPI→TS type-gen) — после breaking
    change HomeworkController нужен автогенератор TS-типов, чтобы фронт
    не падал на `.map` по `PagedModel`.

- **Q1.** `initial_password` в `UserResponse` и gRPC — compliance-gap или
  намеренно?
  **Ответ (auto-resolved через 01-Q1, подтверждено явно 2026-04-18):**
  **(a) ACCEPTED BY OWNER** — оставляем оба канала (REST и gRPC) как есть,
  они нужны боту (`GetUserByTelegramId`) и web-panel admin (создание
  пользователя). 02 P0-1 → «Принято как есть».

- **Q2.** `X-User-Id` trust model — когда mTLS / signed headers?
  **Ответ (2026-04-18):** **(d) Internal JWT (Уровень 2 Zero Trust)** —
  Gateway после валидации внешнего JWT генерирует короткоживущий внутренний
  JWT (TTL ~5 мин, scope `internal`, claims `userId/role/groupId`),
  подписанный той же RSA-парой что и основной JWT. Передаётся как
  `Authorization: Internal <jwt>` (или `X-Internal-Token`). Сервисы
  валидируют публичным RSA-ключом (он у них уже есть для основных JWT).
  Старые `X-User-*` заголовки удаляются.
  **Последствия (каскад):**
  - Кластер C0-1 в 15-cross-cutting → **закрывается целиком фиксом**.
    5 P0 (02 P0-2, 03 P0-1, 04 P0-1, 05 P0-1, 07 P0-2 strip) переходят
    в «решено фиксом», НЕ в «accepted».
  - 14 P1-1 (нет contract-теста Gateway↔downstream) → СВЯЗАНО, требует
    нового теста на валидацию Internal JWT.
  - 03-Q1, 04-Q1, 07-Q9 → **AUTO-RESOLVED** через 02-Q2, тот же фикс.
  - 02 P0-3 (`NumberFormatException` при невалидном X-User-Id) →
    устаревает: вместо парсинга заголовка теперь будет `JwtParser.parse`
    с готовой обработкой ошибок → 401, не 500.
  **Estimate:** ~3 человеко-дня (Gateway issuer + общая jwt-validation
  библиотека для downstream + миграция UserContextFilter + e2e-тест
  bypass-сценария).
  **NEW (порождённые задачи):**
  - **NEW-3:** spec файл `docs/api/internal-jwt-spec.md` — формат токена,
    claims, TTL, ключи. Чтобы будущий разработчик не пере-открывал
    дискуссию.
  - **NEW-4:** при выкатывании фикса нужен «двойной режим» на короткий
    период — Gateway шлёт И старые `X-User-*`, И новый `Authorization:
    Internal`, сервисы принимают оба. После раскатки во всех 4 — переключить
    на «только Internal». Это защищает от downtime при поэтапном деплое.
  - **NEW-5:** smoke-тест в CI/deploy.yml — после деплоя проверить, что
    порты 9091-9094 НЕ доступны снаружи VPS (попытка curl с публичного IP
    должна давать timeout/refused, не 200/4xx). Это defense-in-depth
    дополнительно к Internal JWT.

- **Q3.** Outbox events — готовы принять допущение message loss или внедряем?
  **Ответ (2026-04-18):** **(b) In-app outbox table** — добавить таблицу
  `{service}_outbox` в каждом из 3 backend-сервисов (academic, schedule,
  attendance). Listener вместо прямой публикации в RabbitMQ пишет событие
  в outbox-таблицу в той же транзакции. Отдельный `@Scheduled`
  publisher-job каждые ~5 секунд читает unsent rows, шлёт в RabbitMQ,
  помечает как sent (или удаляет успешные). Failed-rows ретраятся.
  **Последствия (каскад):**
  - Кластер C0-3 в 15 → закрывается фиксом. 3 P0 (02 P0-6, 03 P0-2,
    04 P0-5 DLQ-потеря) → 🔧 TO-FIX.
  - 06 P1-7 (dispatcher глушит ошибки) → СВЯЗАНО, остаётся отдельной
    проблемой бота.
  - 03-Q2, 04-Q6, 15-Q3 → **AUTO-RESOLVED** через 02-Q3.
  - C1-7 (ShedLock) → УСИЛИВАЕТСЯ: publisher-job обязательно нуждается
    в ShedLock, иначе при scaling будет double-publish.
  - C1-5 (contract-тесты событий) → теперь проще писать (event сначала
    попадает в outbox-таблицу, можно тестировать read-from-outbox).
  **Estimate:** ~3-4 человеко-дня (Flyway-миграция × 3, OutboxEntity
  × 3, общая `OutboxPublisherJob` библиотека или копия × 3, тесты
  «kill RabbitMQ → события не теряются»).
  **NEW:**
  - **NEW-6:** общий модуль `shared-outbox` (или копи-паст по 3 сервисам)
    — решить при планировании. Общий модуль снижает дублирование, но
    добавляет связность; копи-паст — наоборот.
  - **NEW-7:** retention-policy для outbox-таблицы (удалять sent после
    7 дней, например) + индекс по `(sent_at, status)` для производительности
    publisher-job.
  - **NEW-8:** ShedLock в C1-7 повышается из P1 до P0 для publisher-job
    (без него double-publish при scaling). Хотя у тебя сейчас single
    instance — это ОК пока, но смок-тест нужен.

- **Q-rate-limit (sub-Q к 02-Q? но логически здесь, в OWNER-ANSWERS).**
  Где делать rate-limiting?
  **Ответ (2026-04-18):** **(c) Spring Cloud Gateway + Redis** — установить
  `spring-cloud-starter-gateway` redis-rate-limiter в API Gateway. Лимиты
  настраиваются per-route, per-user (с ключом из Internal JWT после фикса
  C0-1) или per-IP. Redis уже есть в инфре (используется auth-service для
  OTP), Gateway получает к нему доступ.
  **Закрывает вопросы:** 01-Q6, 07-Q2, 13-Q5, 15-Q4.
  **Последствия (каскад):**
  - Кластер C0-4 в 15 → 🔧 TO-FIX через Gateway+Redis.
  - 01 P0-6 (DoS через login rate-limiter) → 🔧 TO-FIX (per-IP лимит на
    `/auth/login` 5 req/min).
  - 07 P1 (rate-limiting в Gateway) → 🔧 TO-FIX.
  - 13 P1-3 (rate-limiting в nginx) → ❌ ОТКЛОНЁН (выбран Gateway, не nginx).
  - 14 P1-2 (нет тестов rate-limit) → СВЯЗАНО, тесты через Testcontainers
    Redis + WebTestClient.
  - C0-1 (Internal JWT) — становится **soft-prerequisite**: per-user
    лимиты опираются на claim `userId` из Internal JWT. Можно сделать
    параллельно, но если C0-1 ещё не готов — стартуем с per-IP лимитов
    и переключаем на per-user после.
  **Estimate:** ~2-3 человеко-дня (зависимости + RouteLocator config + key
  resolver + Redis-аутопровизионинг + тесты + конфиги лимитов на каждый
  чувствительный endpoint).
  **Ключевые лимиты для v0.0.0:**
  - `/auth/otp/request` — 1 req/min per IP (один OTP-код в минуту)
  - `/auth/otp/verify-by-code` — 5 req/min per IP (anti-brute-force)
  - `/auth/login` — 5 req/min per IP, 10 req/min per login (anti-brute-force)
  - `/auth/refresh` — 30 req/min per user (нормальная работа клиентов)
  - `/api/attendance/check-in` — 10 req/min per user (защита от спама/спуфинга)
  - Глобально на любой `/api/**` — 600 req/min per IP (DDoS guard).
  **NEW (порождённые задачи):**
  - **NEW-9:** Redis в Gateway требует health-check. Если Redis недоступен
    — нужна fallback-стратегия: либо «fail-open» (пропускать без лимитов,
    но логировать WARN), либо «fail-closed» (отклонять запросы 503).
    Рекомендую fail-open для v0.0.0 — иначе Redis-glitch уронит весь сайт.
  - **NEW-10:** мониторинг лимитов в Grafana — счётчик «отказы из-за
    rate-limit» по route'ам. Помогает увидеть legitimate traffic spikes
    и tune лимиты постфактум.
  - **NEW-11:** документировать лимиты в `docs/api/api-rate-limits.md` — чтобы
    клиентский код знал про 429 ответы и backoff'ил.

- **Q-frontend-security (sub-Q к фронт-кластеру).** JWT cookie + WS-ticket +
  logout cleanup — делаем в v0.0.0?
  **Ответ (2026-04-18):** **(a) + breaking change без двойных endpoint'ов**.
  Реализуем оба фикса (Часть А — JWT HttpOnly cookie + ws-ticket; Часть Б —
  logout lifecycle cleanup) в одном релизе для v0.0.0. `/auth/refresh`
  меняет поведение (тело → cookie), `/auth/refresh-cookie` НЕ создаём.
  **Закрывает вопросы:** 09-Q1, 09-Q3, 10-Q1, 10-Q2, 15-Q6.
  **Часть А — JWT cookie + ws-ticket:**
  - Refresh-токен → HttpOnly Secure SameSite=Strict cookie, Path=/api/auth.
  - Access-токен → in-memory (React state / Angular service), теряется
    при reload, рефрешится через `/auth/refresh` (cookie-based).
  - Новый endpoint `POST /auth/ws-ticket` → возвращает 60-секундный
    opaque ticket (UUID, хранится в Redis с TTL 60). WebSocket подключается
    `wss://ruttrack.site/api/ws?ticket=<uuid>`. Notification-service
    валидирует ticket в Redis, удаляет (single-use), возвращает identity.
  - Gateway CORS: `credentials: true`, `origin: https://ruttrack.site`
    (НЕ wildcard).
  - Удаляются: `localStorage.setItem('rct.auth.v1', ...)` в PWA и web-panel.
  **Часть Б — logout lifecycle:**
  - Общий клиентский `clearAllClientState()`:
    - `caches.keys().then(k => k.forEach(caches.delete))` — все SW caches
    - `pushSubscription.unsubscribe()` + `DELETE /api/notifications/push/subscriptions/me`
    - `sessionStorage.clear()`, `localStorage.clear()` (на всякий случай)
    - закрытие STOMP-соединений
  - Backend: новый endpoint `DELETE /api/notifications/push/subscriptions/me`
    в notification-service.
  **Последствия (каскад):**
  - Кластеры C0-5 (logout) и C0-7 (JWT cookie) в 15 → 🔧 TO-FIX.
  - 7 P0 (09 P0-1, P0-2, P0-4, P0-5; 10 P0-1, P0-2, P0-4) → 🔧 TO-FIX.
  - 14 P1-4 (нет тестов logout-сценариев) → СВЯЗАНО, требует e2e-теста
    «logout → проверить что cache очищен, push отписан, ws закрыт».
  - 13 P0-3 (`.env.prod` ротация) — VAPID-ключи всё равно нужно ротировать.
  - C0-1 (Internal JWT) — НЕ зависит от этого, можно делать параллельно.
  **Estimate:** **8-12 человеко-дней** (auth-service ~2д, gateway CORS ~0.5д,
  PWA migration ~3д, web-panel migration ~3д, notification ws-ticket+logout
  ~2д, e2e-тесты ~1.5д). Самый дорогой фикс в проекте.
  **NEW (порождённые задачи):**
  - **NEW-12:** **strict pre-deploy QA**. Поскольку breaking change без
    двойных endpoint'ов — нужно вручную пройти golden path во всех
    клиентах перед merge: PWA (login/refresh/logout/ws-reconnect),
    web-panel (то же × 4 роли admin/teacher/student/headman), TG mini-app
    если он работает в v0.0.0. Если хоть один сломается — релиз блокируется.
  - **NEW-13:** **migration runbook** в `docs/release-v0.0.0-runbook.md`.
    Поскольку поле `localStorage['rct.auth.v1']` уйдёт — все
    залогиненные пользователи получат 401 при первом refresh после
    deploy и должны re-login. Это нормально, но нужно предупредить
    через banner за день до раскатки или сразу после.
  - **NEW-14:** новая Flyway-миграция в auth-service для CSRF-token
    хранения (если SameSite=Strict не хватит — некоторые WebView не
    шлют SameSite=Strict cookies для cross-origin requests). Хотя при
    same-origin (наш случай: `ruttrack.site` обслуживает и фронт, и
    `/api`) это не нужно. Решить при имплементации.
  - **NEW-15:** обновить тесты `auth-service` — `RestApiIT` сейчас
    проверяет `refreshToken` в JSON body. После фикса — Set-Cookie header.
  - **NEW-16:** удалить endpoint `/auth/refresh-body` (01-Q3 — это legacy
    endpoint, который сейчас параллельно с `/auth/refresh`). После
    cookie-фикса оба сольются в один. **NB:** не помечено как auto-resolved
    в самом отчёте 01 — спросить владельца отдельно при обходе 01-Q3.

- **Q-csp-landing (sub-Q к C0-6).** Self-host CDN или whitelist?
  **Ответ (2026-04-18):** **(a) Self-host** — скачать все CDN-ресурсы
  лендинга (Fontshare/Google Fonts шрифты, unpkg иконки, jsdelivr GSAP)
  в `frontends/landing/dist/assets/`, отдавать с того же домена.
  Bundle +~350 KB. CSP корневого nginx остаётся строгой, но не блокирует
  собственные ресурсы. Независимость от CDN uptime и supply-chain.
  **Закрывает вопросы:** 12-Q1, 13-Q2, 15-Q5.
  **Последствия (каскад):**
  - Кластер C0-6 в 15 → 🔧 TO-FIX через self-host.
  - 12 P0-1 (CSP блокирует CDN) → 🔧 TO-FIX.
  - 13 P0-4 (CSP корневого nginx блокирует лендинг) → 🔧 TO-FIX (тот же
    фикс — self-host решает обе стороны: CSP не меняется, лендинг не
    нуждается во внешних CDN).
  - 12 P1-4 (нет SRI на CDN) → ❌ ОТКЛОНЁН (SRI не нужен — нет CDN).
  - C1-8 (процесс ревизии лендинга при изменении бизнес-логики) — НЕ
    меняется, отдельная задача.
  **Estimate:** ~1-2 человеко-дня (download → переписать пути в HTML
  → проверить визуально что ничего не сломалось → сборка → проверить
  CSP в DevTools).
  **NEW (порождённые задачи):**
  - **NEW-17:** добавить шрифты в `licenses.txt` лендинга — Fontshare и
    Google Fonts имеют SIL Open Font License, требуется атрибуция.
  - **NEW-18:** для GSAP — проверить лицензию (MIT для core, но плагины
    могут требовать GreenSock plus). Если используется только core — OK.
  - **NEW-19:** добавить в `gsd-verifier` или CI проверку «нет ссылок
    на CDN в `frontends/landing/index.html`» (regex `https?://(?!ruttrack\.site)`)
    — чтобы случайная PR-ка не вернула CDN-ссылки.

- **Q-ci-deploy-gate (sub-Q к C0-8).** Branch protection?
  **Ответ (2026-04-18):** **(a) Branch protection + required status checks**
  (без required reviews — единственный разработчик). + `workflow_run`
  trigger в `deploy.yml` чтобы deploy запускался только после успешного CI.
  **Закрывает:** C0-8 (CI↔deploy decoupling), 13 P0-2.
  **Estimate:** ~5 минут настройка GitHub + 1-2 часа на правку workflow.

- **Q-secrets-rotation (sub-Q к C0-9).** Ротация секретов?
  **Ответ (2026-04-18):** **Гибрид**:
  - Ротации НЕТ. Владелец подтвердил: `.env.prod` никогда не был в git
    (проверено), загружается напрямую на VPS, утечки не оценены.
  - `.env.prod.example` создаётся в репо для целей читаемости/документации
    (видно какие переменные используются системой). Файл без значений,
    с короткими комментариями — для чего каждая переменная.
  - Реальный `.env.prod` остаётся только на VPS.
  **Закрывает:** C0-9 (частично — оригинальная задача предполагала
  ротацию, но владелец её отклонил).
  **Последствия (каскад):**
  - 13 P0-3 (`.env.prod` в рабочей копии) → переклассифицируется как
    «accepted, документирован шаблон». Risk-сценарии (скриншот/бекап/
    compromised dev machine) принимаются.
  - 13-Q3 (`.env.prod.example` нужен?) → ✅ ДА, создаётся.
  **Estimate:** ~30 минут (создать `.env.prod.example`, обновить gitignore
  если нужно, проверить что `.env.prod` остаётся ignored).
  **NEW:**
  - **NEW-20:** `.env.prod.example` должен включать ВСЕ переменные,
    реально используемые в `docker-compose.prod.yml`. Сделать diff
    между ним и реальным `.env.prod` чтобы ничего не забыть.
    Можно автоматизировать в CI: `diff <(grep -oE '\$\{[A-Z_]+' docker-compose.prod.yml) <(cat .env.prod.example)`.
  - **NEW-21:** добавить inline-комментарии в `.env.prod.example` для
    каждой переменной — назначение, где её получить (пример:
    `# BOT_TOKEN — Telegram bot token из @BotFather`).
  - **NEW-22 (followup):** для будущей версии — рассмотреть переход на
    secrets management (HashiCorp Vault, GitHub Actions secrets, Docker
    secrets). Это снимает класс проблем «секрет на диске разработчика».
    Не для v0.0.0.

- **Q-le-cert (sub-Q к C0-10).** LE cert-name fix?
  **Ответ (2026-04-18):** **(a)** Переименовать cert-name на `ruttrack.site`
  + `--force-renewal` + обновить `init-letsencrypt.sh`. Минимум 30 мин
  downtime для https.
  **Закрывает:** C0-10, 13 P0-1.
  **Estimate:** ~1-2 часа (правка скрипта + плановое окно обслуживания
  на VPS + переиздать cert + рестарт nginx + smoke-тест).
  **NEW:**
  - **NEW-23:** запланировать окно обслуживания (например, 03:00 МСК
    в выходной), оповестить пользователей за день через banner на лендинге
    «плановые работы 03:00–03:30, сервис может быть недоступен 5-10 минут».

### 03-schedule-service
_(отвечено: 3 / 11)_

- **Q-P0-3.** `NumberFormatException` при кривом `X-User-Id` → 500?
  **Ответ (2026-04-18):** **AUTO-RESOLVED через C0-1** (Internal JWT,
  02-Q2). Тот же паттерн что 02 P0-3 и 04 P0-3 — после замены парсинга
  заголовка на `JwtParser.parse` проблема исчезает.
  Подтверждено владельцем явно 2026-04-18.

- **Q-P0-4.** `@Scheduled` без ShedLock — double-publish при scaling?
  **Ответ (2026-04-18):** **(a)** Добавить ShedLock сейчас (PostgreSQL
  LockProvider, таблица `shedlock`). Страховка от будущего multi-instance
  deploy и обязательная зависимость для outbox publisher-job (C0-3).
  **Мотивация:**
  - Сегодня один инстанс schedule-service → проблема не проявляется.
  - Через год при blue-green / rolling deploy / HA — две копии
    одновременно выполнят `@Scheduled` → двойная публикация
    `lesson.started/closed` → дубликаты в attendance.
  - Когда C0-3 outbox активен — `OutboxPublisherJob` обязательно нужен
    ShedLock, иначе race между двумя publisher'ами при `UPDATE ... SET
    sent=true`.
  - Стоимость: одна зависимость + `@EnableSchedulerLock` +
    `@SchedulerLock` на методах. ~1-2 часа работы.
  **Что делается:**
  - Добавить в `schedule-app/build.gradle.kts`:
    `net.javacrumbs.shedlock:shedlock-spring`,
    `shedlock-provider-jdbc-template`.
  - Flyway миграция `V{N}__shedlock_table.sql` с таблицей `shedlock
    (name PRIMARY KEY, lock_until, locked_at, locked_by)`.
  - `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")` в main class.
  - `@SchedulerLock(name = "regenerateUpcoming", lockAtMostFor = "PT10M")`
    на `LessonGenerationService.regenerateUpcoming()`.
  - Аналогично для `OneOffLessonReconciler.reconcile()`.
  - Smoke-тест: поднять два инстанса → убедиться что публикуется одна
    копия события.
  **Последствия (каскад):**
  - 03 P0-4 → 🔧 TO-FIX через ShedLock.
  - C1-7 (ShedLock) в 15 — **повышается до P0-уровня** для publisher-job
    (02-Q3 уже это отметил как NEW-8). Теперь C1-7 обязательный
    prerequisite для C0-3 outbox.
  - Порядок исполнения: C1-7 ShedLock → C0-3 outbox → C0-1 Internal JWT.
  - 13 P0-? (если есть упоминания single-instance assumption в
    docker-compose.prod.yml) — можно ослабить, теперь scaling безопасен.
  **Estimate:** ~1-2 часа.
  **NEW:**
  - **NEW-28:** аналогичный аудит `@Scheduled` в academic-service и
    attendance-service (cleanup jobs, cache invalidation). Каждая
    schedule-задача должна иметь `@SchedulerLock` или явный комментарий
    «single-instance only, not scaled».

- **Q-P0-5.** Дрейф week-parity между `LessonGenerationService` и
  `OneOffLessonService`?
  **Ответ (2026-04-18):** **(a)** Вынести единый helper
  `WeekParityResolver.resolve(LocalDate date, Semester semester):
  WeekType`. Обе службы используют его. Unit-тесты на граничные даты.
  **Мотивация:**
  - Дублирование логики расчёта дат — классический источник «один раз
    в семестр всё расходится». Симптом: студент видит пару в расписании,
    но `lesson.started` для неё не публикуется (или наоборот).
  - Это баг корректности, не оптимизация.
  - Memory-заметка `project_week_parity_convention.md` фиксирует
    конвенцию (ISO-чёт→1-я→ODD; ISO-нечёт→2-я→EVEN). Helper становится
    единственным носителем этой конвенции.
  **Что делается:**
  - Создать `schedule-app/.../util/WeekParityResolver.java` с статическим
    методом `resolve(LocalDate date, Semester semester): WeekType`.
  - Перенести логику из `LessonGenerationService` и `OneOffLessonService`.
  - Добавить unit-тесты на граничные даты:
    - Первая неделя семестра (start date = понедельник vs воскресенье).
    - Переход года (31 декабря / 1 января).
    - 53-я ISO-неделя (бывает раз в несколько лет).
    - `start_date` семестра попадает на середину недели.
  - Документировать конвенцию в javadoc resolver'а с ссылкой на
    memory-заметку.
  **Последствия (каскад):**
  - 03 P0-5 → 🔧 TO-FIX через helper.
  - 14 P1-? (тесты week-parity) — покрывается unit-тестами helper'а.
  - В будущем если появится «неделя 3» (триместровая система) — меняем
    только helper, без поиска по коду.
  **Estimate:** ~2-3 часа (helper + тесты + миграция двух use-sites).
  **NEW:**
  - **NEW-29:** golden-test «неделя A: ISO=X → WeekType=Y» в виде
    параметризованного `@ParameterizedTest` с табличкой 20-30 дат,
    покрывающих все edge cases. Если когда-нибудь helper сломают —
    тест упадёт.
  - **NEW-30:** после фикса — разовый smoke-прогон на реальных семестрах
    (весна 2026 / осень 2025) с diff'ом «до фикса / после фикса»: какие
    пары изменили weekType. Если расхождений нет — молча замена. Если
    есть — нужен ручной review (возможно, один из двух сервисов жил
    с багом и исправление может сдвинуть расписание пользователей).

### 04-attendance-service
_(отвечено: 4 / 10)_

- **Q-P0-3.** `NumberFormatException` при кривом `X-User-Id` → 500?
  **Ответ (2026-04-18):** **AUTO-RESOLVED через C0-1** (Internal JWT,
  02-Q2). Тот же паттерн что 02 P0-3 и 03 P0-3 — закрыто групповым
  ответом 13a.

- **Q-P0-4.** `UserContextFilter` не проверяет `X-Group-Id` — IDOR?
  **Ответ (2026-04-18):** **(b) AUTO через C0-1 + contract-тест на IDOR.**
  После C0-1 `groupId` становится claim'ом подписанного Internal JWT,
  подделать невозможно. Дополнительно — обязательный contract-тест:
  «запрос с Internal JWT, где `groupId` != `checkin.group_id`, возвращает
  403 (или 404, не раскрывая существование записи)».
  **Мотивация:** авторизация через JWT-claim достаточна, но один коммит
  может случайно отключить проверку в фильтре/сервисе. Contract-тест
  ловит регрессию.
  **Закрывает:** 04 P0-4.
  **Каскад:**
  - 04 P0-4 → 🔧 TO-FIX через C0-1 + test.
  - 14 P1-1 (contract-тесты Gateway↔downstream) — явно связано, тест
    на IDOR пишется в том же модуле.
  - NEW (связанный с C0-1): аналогичные IDOR-тесты для academic (чужой
    user_id в /users/{id}), schedule (чужой group_id в /lessons),
    notification (чужой subscription через /push/me). Это один пакет
    e2e-тестов безопасности.
  **Estimate:** ~1 час на тест (после готовности C0-1).
  **NEW:**
  - **NEW-31:** test-suite `SecurityIdorIT` в каждом backend-сервисе:
    проверяет что JWT-claim `groupId` / `userId` не позволяет доступ
    к чужим ресурсам. Шаблон теста общий, варьируется endpoint-таблицей.
  - **NEW-32:** при 403 vs 404 — выбрать единую политику. Рекомендую
    404 (не раскрывать существование записи), задокументировать в
    `docs/api/api-error-conventions.md`.

- **Q-P0-6.** `cleanupOrphans` mass-delete на старте?
  **Ответ (2026-04-18):** **(a)** Убрать `cleanupOrphans` совсем.
  Orphaned-отметки — пренебрежимый объём, накапливаются медленно. При
  необходимости — разовый админ-скрипт.
  **Мотивация:**
  - Race при старте (attendance раньше schedule → schedule возвращает
    пусто → удаляем всё) — реальный риск.
  - Mass-delete без лимита/бекапа — необратимая потеря истории.
  - Выполняется при каждом container restart → constant risk surface.
  - Для pet-проекта с медленным накоплением «мусора» проще удалить
    код, чем защищать его guard'ами, schedule и ShedLock.
  **Что делается:**
  - Удалить `@PostConstruct cleanupOrphans()` метод из `AttendanceService`
    (или где он лежит).
  - Удалить соответствующий gRPC-вызов `ListLessons` если он больше
    нигде не нужен.
  - Удалить тесты cleanup-логики (если есть).
  - В `docs/operations/runbooks/` или новый `docs/operations/deploy/admin-scripts.md` — шаблон
    разового скрипта на `mongosh`/Spring CLI для ручной чистки, когда
    реально потребуется. В проде этот скрипт запускать вручную после
    бекапа.
  **Последствия (каскад):**
  - 04 P0-6 → 🔧 TO-FIX (удаление кода).
  - 04-Q5 (нужен ли cleanup вообще) → **AUTO-RESOLVED** — не нужен.
  - Gateway/schedule rate-limit для `ListLessons` (если был рассчитан
    под cleanup) — можно снизить. Минорно.
  - Отчёт 04 P0-6: раздел «Как чинить → вариант (b) schedule + guard»
    → помечается «отклонён владельцем», убирается.
  **Estimate:** ~1 час (удаление + тест «старт без schedule работает»
  + пометка в docs/operations/deploy/admin-scripts.md).
  **NEW:**
  - **NEW-33:** документ `docs/operations/deploy/admin-scripts.md` — шаблоны разовых
    админ-задач (cleanup orphans, backfill, recovery). Цель: когда
    реально понадобится — не изобретать заново.

- **Q2.** Локация студента: возвращаем сохранение координат в
  `AttendanceDocument.checkin_location` или нет?
  **Ответ (2026-04-18):** **(a)** — координаты НЕ сохраняем, только
  проверка «в радиусе кампуса → да/нет». Anti-spoof расследование через
  лог координат не предусмотрено. По M1 юридических требований нет.
  **Действия:**
  - Обновить `docs/architecture/database-schema.md` — убрать поле `checkin_location`
    из описания `AttendanceDocument` (или явно пометить «не используется,
    зарезервировано для будущего»).
  - 04 P1 (или соответствующий пункт о расхождении кода и доки) —
    переклассифицировать как «doc-fix», не security.
  **Последствия (каскад):**
  - В отчёте 04 проверить упоминания `checkin_location` — пометить
    «accepted by owner: координаты не нужны».
  - В 09-frontend-pwa и 10-frontend-web-panel — клиент не должен слать
    координаты на бэкенд (только пользоваться geolocation API локально
    для расчёта дистанции). Если сейчас шлёт — оставляем (бэкенд их
    игнорирует), но при следующей итерации уборки можно убрать из payload.
  **NEW:** Для `database-schema.md` правка нужна сейчас (один раз),
  иначе отчёты будут расходиться с правдой и при следующем аудите снова
  поднимется.

### 05-notification-service
_(отвечено: 4 / 12)_

- **Q-P0-2.** Нет `GlobalExceptionHandler` — 500 вместо RFC 7807?
  **Ответ (2026-04-18):** **(b)** Вынести общий `GlobalExceptionHandler`
  в новый Gradle-модуль `shared-web` (чистый `java-library` без Spring
  Boot, с Spring Web dependencies). Подключить к 4 backend-сервисам
  (academic, schedule, attendance, notification).
  **Мотивация:**
  - Сейчас 3 сервиса (academic/schedule/attendance) имеют свои копии
    `GlobalExceptionHandler` с drift'ом между ними. Notification-service
    без handler'а вообще.
  - Shared-модуль убирает drift, даёт единый RFC 7807 `ErrorResponse`
    формат, общие маппинги (`MethodArgumentNotValidException` → 400,
    `EntityNotFoundException` → 404, `AccessDeniedException` → 403).
  - 4 сервиса × рефакторинг ~0.5 дня каждый = ~2 дня суммарно.
  **Закрывает:** 05 P0-2, C1-11 (был отложен на v0.1, теперь включаем
  в v0.0.0).
  **Что делается:**
  - Новый модуль `shared/shared-web/` → `java-library` с зависимостями
    `spring-web`, `spring-context`, Jackson.
  - `GlobalExceptionHandler` class + `ErrorResponse` record (уже есть
    в academic — копируем оттуда как canonical).
  - Сервис-специфичные исключения: каждый сервис может добавить свой
    `@RestControllerAdvice(order=lower)` для локальных случаев.
  - Миграция: academic/schedule/attendance убирают свои копии, подключают
    shared-web. Notification подключает с нуля.
  - Contract-тест: `POST /api/{svc}/users` с невалидным body → проверка
    что формат ответа RFC 7807 (одинаков для всех 4 сервисов).
  **Последствия (каскад):**
  - 05 P0-2 → 🔧 TO-FIX через shared-web.
  - C1-11 (GlobalExceptionHandler notification) в 15 → 🔧 TO-FIX
    через тот же модуль. Повышается из P1 в P0-scope (т.к. объединяется
    с 05 P0-2).
  - 02/03/04 drift между копиями handler'ов → устраняется.
  - 14 P2-? (contract-тесты error responses) — может использовать shared
    fixtures из shared-web.
  **Estimate:** ~2 человеко-дня (новый модуль 0.5д + миграция × 4
  сервиса по 0.25-0.5д + contract-тесты 0.5д).
  **NEW:**
  - **NEW-34:** правила `shared-web` — никакой Spring Boot autoconfiguration,
    никаких bean'ов кроме `@ControllerAdvice`. Модуль должен быть
    подключаемым «как обычная библиотека», без магии. Это упростит
    будущий auth-service (который тоже нужно подключить к shared-web).
  - **NEW-35:** при подключении к auth-service (когда он получит
    `auth-api-contract` от 01 P0-1) — тоже использовать shared-web.
    Итого 5 сервисов под одним handler'ом.

- **Q-P0-3.** `push_subscriptions` в чужой БД (`attendance_db`)?
  **Ответ (2026-04-18):** **(b)** Оставить в `attendance_db`, перенести
  миграции и владение в notification-service. Accepted shared-DB
  паттерн (аналогично 01 P0-3 auth↔academic).
  **Мотивация:**
  - Вариант (a) = новый PostgreSQL-контейнер, новый бекап, новый
    volume, data migration → слишком много инфра-изменений для pet-
    проекта.
  - (c) оставляет ownership неясным — через год при аудите снова
    поднимется.
  - (b) консистентно с принятым shared-DB tradeoff.
  **Закрывает:** 05 P0-3.
  **Что делается:**
  - Перенести Flyway-миграцию `V{N}__push_subscriptions.sql` из
    `attendance-app/src/main/resources/db/migration/` в
    `notification-web-app/src/main/resources/db/migration/V1__push_subscriptions.sql`.
  - `notification-web` подключается к `attendance_db` (через `spring.datasource.url`).
  - В attendance-service: `flyway.schemas: public` (или через
    `flyway.table: attendance_flyway_schema_history`), notification
    использует отдельную таблицу истории (`notification_flyway_schema_history`).
  - Attendance перестаёт «знать» про таблицу (удалить JPA-entity если
    есть, удалить миграцию после переноса).
  - Baseline для notification-service: `flyway.baseline-on-migrate: true`
    + `flyway.baseline-version: 0` + существующая таблица уже в БД.
  **Последствия (каскад):**
  - 05 P0-3 → 🔧 TO-FIX через перенос миграций.
  - Документировать в `docs/architecture/architecture.md` → раздел «Принятые
    shared-DB паттерны»: auth↔academic_db, notification↔attendance_db.
  - `feedback_flyway_no_edit.md` (memory) — применимо: миграция уже
    применена в проде, нельзя просто переместить файл. Нужен runbook:
    (1) создать в notification новую V1 с тем же `CREATE TABLE IF NOT
    EXISTS` (идемпотентно), (2) `flyway repair` на attendance чтобы
    исключить старую миграцию из history.
  - 13 P0-? (если упоминается push_subscriptions в docker-compose) —
    проверить переменные окружения notification: нужен `SPRING_DATASOURCE_URL`
    → `attendance_db`.
  **Estimate:** ~1 человеко-день (перенос миграции + baseline + runbook
  + smoke-тест в dev).
  **NEW:**
  - **NEW-36:** добавить в `docs/architecture/architecture.md` раздел «Shared-DB
    паттерны» со списком: auth→academic_db (users), notification→attendance_db
    (push_subscriptions). Чтобы новый разработчик понимал ownership.
  - **NEW-37:** runbook переноса миграции в `docs/operations/runbooks/flyway-migration-move.md`
    — пошаговый чеклист с `flyway repair` и проверками.

- **Q-P0-4.** `SubscriptionAuthInterceptor` IDOR — удаление чужой
  subscription?
  **Ответ (2026-04-18):** **(b)** Заменить endpoint на
  `DELETE /api/notifications/push/subscriptions/me` — убрать параметр
  `{id}` из URL. Всегда удаляется subscription текущего пользователя.
  Если пользователь имеет несколько устройств — передаётся `endpoint`
  URL в request body для выбора конкретной подписки (уникален per-device).
  **Мотивация:**
  - Resource `me` не требует проверки `id` — архитектурно чище.
  - Endpoint `/me` уже запланирован в 02-Q-frontend-security (Часть Б
    logout lifecycle). Просто делаем его ЕДИНСТВЕННЫМ (вместо
    добавочного к `/{id}`).
  - (a) оставляет уязвимую поверхность, (c) over-engineering.
  **Закрывает:** 05 P0-4.
  **Что делается:**
  - Удалить endpoint `DELETE /push/subscriptions/{id}` из контракта
    и контроллера.
  - Оставить только `DELETE /push/subscriptions/me` (с опциональным
    query param `?endpoint=<url>` или body `{endpoint}` для multi-device).
  - Без query/body — удаляются ВСЕ subscriptions текущего пользователя
    (sign-out-all-devices).
  - С query/body — удаляется только subscription с матчем `endpoint`.
  - `SubscriptionAuthInterceptor` упрощается: проверяет только
    аутентификацию (JWT валиден), не авторизацию (userId берётся
    из Internal JWT после C0-1, не из URL).
  **Последствия (каскад):**
  - 05 P0-4 → 🔧 TO-FIX через смену endpoint.
  - 02-Q-frontend-security (Часть Б) — уточняется: endpoint `/me`,
    не `/{id}`. Клиенты PWA/web-panel вызывают `DELETE /me` при logout.
  - 09/10 — переписать клиентский `unsubscribe()` код (был
    `DELETE /{id}` → `DELETE /me`).
  - Breaking change для существующих клиентов — при релизе v0.0.0
    старые клиенты упадут на 404 при logout. Это acceptable т.к. после
    релиза все форсированно перелогинятся (см. NEW-13).
  **Estimate:** ~3 часа (backend контракт+контроллер+тест + 2 клиента).
  **NEW:**
  - **NEW-38:** contract-тест `SecurityIdorIT` для push: «JWT A удаляет
    push-subscription B через неверный endpoint → 404 или 204 (отсутствие
    match'а), НЕ 200 с удалением». Часть NEW-31 из 15a.

- **Q-P0-5.** STOMP handshake без audit trail?
  **Ответ (2026-04-18):** **(a)** Добавить `HandshakeInterceptor.afterHandshake()`
  + лог INFO `{timestamp, userId, ip, userAgent, ticketId, sessionId}`.
  Логи пишутся в stdout → собираются Loki/Grafana (уже есть в инфре).
  **Мотивация:**
  - Grafana+Loki уже есть — queryable логи там.
  - Отдельная таблица `ws_sessions` — over-engineering без ROI для
    pet-проекта.
  - Nginx access.log не знает `userId` — критично сопоставление IP↔userId.
  **Закрывает:** 05 P0-5.
  **Что делается:**
  - В `WebSocketConfig` зарегистрировать `HandshakeInterceptor`.
  - `beforeHandshake()` — извлечь `userId` из ws-ticket (из Redis),
    положить в attributes.
  - `afterHandshake(..., Exception)` — если Exception==null, лог INFO
    `ws.connected {userId, ip, ua, ticketId, sessionId}`.
  - При disconnect (через `SessionDisconnectEvent` listener) — лог INFO
    `ws.disconnected {userId, sessionId, duration_ms}`.
  - В Loki dashboard Grafana добавить panel «WebSocket connections»
    с фильтром по userId.
  **Последствия (каскад):**
  - 05 P0-5 → 🔧 TO-FIX через interceptor + log.
  - C0-7 (JWT cookie + ws-ticket) — логирование `ticketId` полезно для
    correlate с Redis-audit «сколько тикетов выдано, сколько
    использовано».
  - Privacy — `ip` и `ua` сохраняются в логах. По M1 (не в РФ) нет
    юр.требований. Loki retention 30-45д (уже зафиксирован в 15).
    Задокументировать в `docs/security-model.md` как accepted.
  **Estimate:** ~3-4 часа (interceptor + disconnect listener + grafana
  panel + unit-тест `MockHttpServletRequest` проход interceptor'а).
  **NEW:**
  - **NEW-39:** аналогичный handshake log для `/api/ws` в Gateway (уже
    должен существовать через nginx access.log, но без userId). Если
    Gateway Spring Cloud — добавить `GlobalFilter` с логированием. Если
    nginx — акцепт, userId только на notification-service.
  - **NEW-40:** grafana-алерт «подозрительная активность ws» —
    например, >50 подключений за минуту от одного userId (боты/шпаргалка)
    или >10 разных IP от одного userId за час (угон JWT). Нашёл на
    v0.1 backlog, не в v0.0.0 scope.

### 06-notification-bot
_(отвечено: 3 / 8)_

- **Q-P0-1.** gRPC к academic/schedule через `insecure_channel` без TLS?
  **Ответ (2026-04-18):** **(c) ACCEPTED BY OWNER** — docker-сеть
  `rutcampustrack_private_net` считается границей доверия. Весь gRPC
  (academic/schedule ↔ notification-bot ↔ notification-web) не выходит
  за пределы одного VPS.
  **Мотивация:**
  - mTLS (a) = big effort (CA management, cert rotation, init-скрипты ×
    N сервисов), не оправдан для pet-проекта с одним VPS.
  - Server-TLS (b) = половинчатая защита, дополнительный maintenance.
  - `GRPC_SECRET` (fail-fast после 13c) остаётся единственным механизмом
    аутентификации между сервисами — этого достаточно при принятой
    trust-модели.
  - Если когда-нибудь бот поедет на отдельный VPS или Kubernetes-кластер
    с несколькими нодами — эта проблема становится P0. Фиксируется как
    «триггер пересмотра» в future-ideas.
  **Закрывает:** 06 P0-1.
  **Действия:**
  - Задокументировать trust-модель в `docs/security-model.md`:
    «Внутренний gRPC-трафик между контейнерами не шифруется. Границы
    доверия — пределы docker-сети `rutcampustrack_private_net` на одном
    VPS. Аутентификация сервисов через `GRPC_SECRET` (обязательный, без
    default, fail-fast). Компрометация любого контейнера внутри сети
    даёт доступ ко всему внутреннему трафику — accepted tradeoff.»
  - Ports 9091-9094 (gRPC) НЕ должны быть exposed наружу VPS —
    проверить в `docker-compose.prod.yml`. Если `ports:` секция
    содержит публичные маппинги — удалить, оставить только `expose:`
    для internal-only.
  - Добавить в `future-ideas.md` раздел «Безопасность → mTLS для
    внутреннего gRPC», с триггером «когда разносим сервисы на разные
    VPS / k8s cluster».
  **Последствия (каскад):**
  - 06 P0-1 → ✅ ACCEPTED.
  - NEW-5 (smoke-тест «порты 9091-9094 не доступны снаружи VPS» из
    02-Q2) — становится обязательным, т.к. это единственная защита.
  - 13 P1-? (supply-chain scan для bot-образа) — связано: если
    bot-контейнер скомпрометирован, он видит gRPC-трафик в открытую.
    Trivy/gitleaks на bot-образ — соответствующая компенсация.
  **Estimate:** ~30 минут (проверить docker-compose + документация).
  **NEW:**
  - **NEW-41:** в `future-ideas.md` добавить «mTLS для внутреннего
    gRPC» с триггером «разнесение контейнеров на разные хосты».
  - **NEW-42:** добавить CI-check или runbook: «docker-compose.prod.yml
    НЕ должен иметь `ports:` для сервисов academic/schedule/attendance/
    notification — только `expose:`». Grep-regex по файлу.

- **Q-P0-2.** Placeholder `BOT_TOKEN` / пустой `GRPC_SECRET`?
  **Ответ (auto-resolved через 13c, подтверждено 2026-04-18):**
  **(a) TO-FIX** — убрать default `GRPC_SECRET` в бот-конфиге, fail-fast.
  `BOT_TOKEN` аналогично: без default, fail-fast.
  **Закрывает:** 06 P0-2.
  **Что делается:**
  - В `notification-bot/config.py` (или где читаются env): `os.environ['GRPC_SECRET']`
    и `os.environ['BOT_TOKEN']` — без `.get(..., '')` и без default.
    KeyError на старте → контейнер падает, supervisor рестартует, логи
    громко сообщают об отсутствующей переменной.
  - В `.env.prod.example` (из C0-9 NEW-20/21) включить обе переменные
    с комментариями.

- **Q2.** Где хранится `initial_password` после первой раздачи? Нужна ли
  история показа (чтобы `/start` не показывал пароль повторно)?
  **Ответ (2026-04-18):** **(a) ACCEPTED BY OWNER** — оставляем как
  сейчас: бот показывает `initial_password` при каждом `/start`, пока
  пароль не сменён пользователем. Trust-модель: «если ты в моём TG-аккаунте,
  ты и есть владелец». История показа НЕ ведётся.
  **Последствия (каскад):**
  - Под-вопрос из 01-Q1 (нужно ли show-once для P0-3) → закрыт.
  - 06 P0-3 окончательно переходит в Accepted без residual подзадач.
  - В 06 P0-3 раздел «Как чинить → пункт 3 (initial_password_shown_at)»
    помечается «отклонено владельцем», убирается из списка кандидатов
    на фикс. Пункты 1-2 (magic-link и spoiler+delete) тоже отклонены
    через 01-Q1.
  - В отчёте 06 в зависимостях P0-3 → academic не нужно добавлять
    `initial_password_shown_at`. Этот пункт удаляется.
  **NEW (риски, остающиеся в открытую — должны быть в 99 как «Accepted
  but documented»):**
  - Студент со старой TG-историей может найти пароль через scroll.
  - При продаже/передаче TG-аккаунта новый владелец увидит пароль через
    `/start` (если жертва не сменила пароль на сайте).
  - Staff помогает студенту с настройкой бота, видит пароль на экране.
  Все три риска осознанно приняты владельцем.

### 07-api-gateway
_(отвечено: 2 / 10)_

- **Q-P0-1.** CORS-домен `rutcampustrack.ru` vs реальный `ruttrack.site`?
  **Ответ (2026-04-18):** **(b)** Исправить домен на `ruttrack.site` +
  вынести в env-переменную `CORS_ALLOWED_ORIGINS`. Hardcoded list в
  `application-prod.yml` заменяется на `${CORS_ALLOWED_ORIGINS}`.
  **Мотивация:**
  - Текущая опечатка `rutcampustrack.ru` не проявляется только потому
    что фронт и api на одном origin (`ruttrack.site`). Любой cross-origin
    сценарий (WebView, внешний клиент, staging с другим доменом)
    сломан.
  - Env-переменная позволяет добавить preview-окружения
    (`ruttrack-staging.site`, `dev.ruttrack.site`) без пересборки
    Gateway.
  - Минимум кода: 1 строка в yml + строка в `.env.prod` / `.env.prod.example`.
  **Закрывает:** 07 P0-1.
  **Что делается:**
  - В `api-gateway/application-prod.yml`:
    `spring.cloud.gateway.globalcors.cors-configurations.[/**].allowedOrigins: ${CORS_ALLOWED_ORIGINS:https://ruttrack.site}`.
    Default остаётся prod-домен на случай если env не проставлен.
  - В `.env.prod.example` добавить `CORS_ALLOWED_ORIGINS=https://ruttrack.site`.
  - В `.env.prod` (на VPS) явно прописать переменную.
  - Для dev (`application.yml` без profile) — `http://localhost:5173,http://localhost:4200`
    напрямую в yml (не через env), т.к. dev не читает `.env.prod`.
  - `allowCredentials: true` (требуется для C0-7 cookie-based auth).
  - `allowedMethods: GET,POST,PUT,DELETE,PATCH,OPTIONS`.
  **Последствия (каскад):**
  - 07 P0-1 → 🔧 TO-FIX.
  - Связь с 02-Q-frontend-security: cookie-based auth требует
    `credentials: true` + non-wildcard origin — обе требования
    удовлетворены.
  - NEW-20 (`.env.prod.example`) — дополняется переменной.
  - NEW-25 (CI smoke «compose без .env.prod падает») — если
    `CORS_ALLOWED_ORIGINS` пустой, Gateway поднимется с default
    `https://ruttrack.site` (fallback). Это ОК, но чеклист релиза должен
    явно проверять актуальность домена.
  **Estimate:** ~30 минут (правка yml + env files + smoke-тест в dev
  + проверка preflight-запросов из браузера).
  **NEW:**
  - **NEW-43:** добавить в `docs/release-v0.0.0-runbook.md` (NEW-13)
    пункт «проверить `CORS_ALLOWED_ORIGINS` актуален для текущего домена».
  - **NEW-44:** CI-lint: grep по `application-prod.yml` и `docker-compose.prod.yml`
    на hardcoded `rutcampustrack.ru` (старое имя домена) → если
    находит, CI падает. Защищает от будущих «забыли обновить».

- **Q-P0-2.** `/api/ws/**` и `/api/auth/otp/**` публичны без JWT?
  **Ответ (2026-04-18):** **(a) частично AUTO + (c) доп.защита по
  telegram_id.**
  **Частично AUTO-RESOLVED:**
  - `/api/ws/**` — закрыто через C0-7 ws-ticket (02-Q-frontend-security).
    Gateway по-прежнему пропускает endpoint как public, но notification-web
    требует одноразовый ticket в query. Без ticket → `403 Forbidden`
    на WS handshake.
  - `/api/auth/otp/**` — по природе публичные (вызываются ДО логина).
    C0-4 rate-limit (`/otp/request` 1 req/min per IP, `/otp/verify-by-code`
    5 req/min per IP) + `MessageDigest.isEqual` (01 P0-5) уже закрывают
    брутфорс и timing-атаки.
  **Доп.защита (c):** добавить per-telegram_id лимит «один активный
  OTP на telegram_id в 5 минут» в Redis. Не даёт
  `telegram_id`-enumeration (попытку узнать, кто зарегистрирован,
  через отправку OTP на диапазон telegram_id'шников) и не даёт спамить
  OTP-сообщения в чат жертвы (bot-flood атака).
  **Что делается:**
  - В `auth-service`, handler `/auth/otp/request`:
    ```java
    String key = "otp:active:" + telegramId;
    Boolean set = redis.setIfAbsent(key, "1", Duration.ofMinutes(5));
    if (Boolean.FALSE.equals(set)) {
        throw new TooManyRequestsException("OTP already active for this user");
    }
    // отправка OTP через RabbitMQ event (01-Q-P0-4)
    ```
  - При успешной верификации OTP или истечении TTL ключ удаляется
    автоматически. Если пользователь успешно верифицировался раньше —
    явный `redis.delete(key)` чтобы не ждать 5 минут до следующего
    входа.
  - Ответ 429 для повторной попытки: `{error: "otp_cooldown", retry_after_seconds: N}`.
    Клиент PWA/web-panel показывает timer «следующий OTP через N сек».
  **Закрывает:** 07 P0-2.
  **Последствия (каскад):**
  - 07 P0-2 → ✅ ACCEPTED (WS) + 🔧 TO-FIX (OTP telegram_id guard).
  - C0-4 (rate-limit) — `/otp/request` теперь имеет двойную защиту:
    per-IP + per-telegram_id. Это не дублирование, покрывают разные
    векторы (один атакующий vs multiple IPs против одного user).
  - 01 P0-6 (DoS через login rate-limiter) — связано, тот же Redis
    используется. Проверить что TTL-ключи разных лимитов не конфликтуют
    (префиксы `otp:active:*`, `otp:rl:ip:*`, `login:rl:ip:*`).
  - NEW (порождённая): документировать Redis key-space в
    `docs/redis-keyspace.md` — префиксы, TTL, назначение. Без этого
    через год никто не вспомнит, что чего.
  **Estimate:** ~2-3 часа (Redis setIfAbsent + unit-тест + frontend
  UX для 429 с timer-ом).
  **NEW:**
  - **NEW-45:** `docs/redis-keyspace.md` — реестр всех Redis-ключей
    с префиксами, TTL, назначением. Сервисы: auth (OTP, ws-ticket,
    rate-limits), gateway (rate-limit counters).
  - **NEW-46:** UX в PWA/web-panel: при 429 на `/otp/request` показать
    countdown-timer «повторить через 4:32». Без этого пользователь
    не понимает, сколько ждать, и будет долбить кнопку.

### 08-shared-proto-events
_(отвечено: 2 / 10)_

- **Q-P0-2.** Отсутствует схема события `otp.requested`?
  **Ответ (2026-04-18):** **(b)** Создать JSON Schema
  `event-schemas/otp-requested.json` с обязательными полями + `event_version: 1`
  для будущей эволюции.
  **Мотивация:**
  - Event `otp.requested` стал обязательным после 01 P0-4 (OTP через
    RabbitMQ вместо HTTP body).
  - `event_version` заложен сразу — смена формата кода (4 vs 6 цифр,
    изменение TTL политики, добавление `locale` для i18n) не потребует
    breaking-change событий.
  - Любой новый event-type в v0.0.0+ должен иметь `event_version`.
    Ретроспективно добавлять версионирование к 14+ существующим событиям
    — отдельная задача на v0.1 (NEW).
  **Что делается:**
  - `event-schemas/otp-requested.json`:
    ```json
    {
      "$schema": "http://json-schema.org/draft-07/schema#",
      "title": "otp.requested",
      "type": "object",
      "additionalProperties": false,
      "required": ["event_version", "telegram_id", "code", "ttl_seconds", "trace_id", "occurred_at"],
      "properties": {
        "event_version": {"type": "integer", "const": 1},
        "telegram_id": {"type": "integer"},
        "code": {"type": "string", "pattern": "^[0-9]{6}$"},
        "ttl_seconds": {"type": "integer", "minimum": 60, "maximum": 600},
        "trace_id": {"type": "string"},
        "occurred_at": {"type": "string", "format": "date-time"}
      }
    }
    ```
  - Contract-тест publisher (auth-service) ↔ consumer (notification-bot):
    проверка что сериализованное событие соответствует схеме.
  - В `docs/architecture/event-schemas.md` — раздел «Versioning policy»: правила
    эволюции (backward-compat → минорная версия, breaking → мажорная
    + параллельное потребление обеих версий на время миграции).
  **Закрывает:** 08 P0-2.
  **Последствия (каскад):**
  - 01 P0-4 (OTP через RabbitMQ) — зависимость выполнена, можно
    имплементировать.
  - C1-5 (contract-тесты RabbitMQ events) — `otp.requested` добавляется
    в coverage как первое событие с version-1.
  - 14 P1-5 (contract-тесты схем) — связано, `otp-requested.json`
    один из первых тестируемых.
  - NEW: existing 14+ events без `event_version` — отдельная задача
    добавления версионирования в v0.1. Не блокирует v0.0.0.
  **Estimate:** ~2-3 часа (schema + contract-test publisher-side +
  consumer-side schema-validation).
  **NEW:**
  - **NEW-47:** задача на v0.1 «retrofit `event_version` во все
    существующие 14+ events». В `docs/future-ideas.md` → раздел «События».
  - **NEW-48:** `docs/architecture/event-schemas.md` с versioning policy. Если такого
    файла нет — создать. Если есть — добавить раздел.

- **Q1.** `initial_password` в gRPC-контракте — когда удалим? Нужен ли
  альтернативный канал (`setup_token`)?
  **Ответ (auto-resolved через 01-Q1, подтверждено явно 2026-04-18):**
  **(a) ACCEPTED BY OWNER** — поле `string initial_password = 10;`
  в `proto/academic.proto:155` остаётся. Альтернативный канал не нужен.
  08 P0-1 → «Принято как есть». Зависимости от него (01 P0-2, 02 P0-1,
  06 P0-3) тоже все приняты.

### 09-frontend-pwa
_(отвечено: 2 / 10)_

- **Q-P0-3.** Нет ролевых guards в роутере PWA?
  **Ответ (2026-04-18):** **(b)** Централизованный `useAuth()` hook
  с реактивным `role`, `<RoleGuard>` HOC использует его. Guards ре-рендерятся
  при смене роли.
  **Scope PWA:** PWA = STUDENT + STUDENT+headman only. Admin и teacher
  работают в web-panel (Angular). `RoleGuard` пропускает только STUDENT.
  Если роль другая — `<Navigate to="/forbidden" />` или редирект на
  `https://ruttrack.site/login` (web-panel).
  **Admin/teacher в PWA** — перенесено в `docs/future-ideas.md` (раздел
  «Frontend → PWA для admin/teacher ролей») как идея v0.1+.
  **Мотивация:**
  - После C0-7 (HttpOnly cookie) подделка JWT невозможна, но клиентские
    guards нужны для UX: PWA не должна пытаться рендерить чужие роуты
    (requests в backend → 403 → broken page).
  - `useAuth()` hook — стандартный React-паттерн, 1-2 часа работы.
  - Централизация избавляет от drift'а: одно место, где роль проверяется.
  **Что делается:**
  - `src/hooks/useAuth.ts`: возвращает `{user, role, isHeadman, isLoading}`.
    Внутри — React context из `AuthProvider` (подписан на auth-state).
  - `src/components/RoleGuard.tsx`: принимает `allow: Role[]`, рендерит
    `children` если `role` в списке, иначе `<Navigate>`.
  - Роутер: `<Route path="/headman/*" element={<RoleGuard allow={['HEADMAN']}><HeadmanLayout/></RoleGuard>}/>`.
  - Корневой guard: любой не-STUDENT редирект на web-panel `/login`.
  - Unit-тесты: «role=TEACHER → redirect», «role=STUDENT headman=false
    → `/headman/*` → forbidden», «role=STUDENT headman=true → ok».
  **Последствия (каскад):**
  - 09 P0-3 → 🔧 TO-FIX через `useAuth` + `RoleGuard`.
  - 10 P0-? (web-panel — если там тоже нет ролевых guards) — аналогичный
    подход, но через Angular `CanActivate` guard. Отдельный фикс.
  - Связь с C0-1 (Internal JWT) — `role` должна приходить из токена,
    не из локального state (который можно подделать). После C0-7 JWT
    в HttpOnly cookie, после C0-1 backend проверяет claim. Front-guard
    — только UX-слой.
  - NEW-31 (`SecurityIdorIT`) — backend должен проверять роль независимо
    от фронта. Guards не замена, а дополнение.
  **Estimate:** ~0.5 дня (hook + guard + тесты + миграция роутера).
  **NEW:**
  - **NEW-49:** в `docs/future-ideas.md` добавлен раздел «PWA для
    admin/teacher ролей». Триггер пересмотра — запрос от реальных
    пользователей.
  - **NEW-50:** аналогичный audit для web-panel (Angular) — проверить,
    есть ли `CanActivate` guards на `/admin/*`, `/teacher/*`, `/headman/*`.
    Если нет — отдельный P0 в отчёте 10. Задача для следующего прохода
    аудита.

- **Q6.** Push-уведомления с именами студентов — деперсонализировать?
  **Ответ (2026-04-18):** **(a)** — оставляем «Иван Иванов подал пропуск»
  как есть. По M1 это не ПДн юридически, UX-риск утечки на lock-screen
  принят. `NotificationCenter.tsx:212-228` (`buildBody`) не меняется.

### 10-frontend-web-panel
_(отвечено: 2 / 12)_

- **Q-P0-3.** Нет CSP / security headers в nginx web-panel?
  **Ответ (2026-04-18):** **(a)** Добавить в nginx config блок security
  headers: строгая CSP + HSTS + X-Content-Type-Options +
  Referrer-Policy + X-Frame-Options.
  **Мотивация:**
  - XSS-поверхность большая (пользовательский контент: имена, группы,
    описания ДЗ, причины отмены). Без CSP один непроэкранированный
    `<script>` компрометирует сессию.
  - HSTS защищает от SSL stripping (отдельная атака, независимая от XSS).
  - ~40 минут работы (10 мин правка + 30 мин проверка Angular'а
    на `'unsafe-inline'` style).
  **Закрывает:** 10 P0-3.
  **Что делается:**
  - В nginx config для web-panel (vhost `/admin/*`, `/teacher/*`,
    `/student/*`, `/headman/*`, `/login`):
    ```nginx
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self' wss://ruttrack.site; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header X-Frame-Options "DENY" always;
    ```
  - Проверка в DevTools → Console: нет CSP-violations после reload всех
    страниц (login, admin dashboard, teacher journal, student stats,
    headman approve).
  - Если Angular генерирует inline-scripts где-то (avoid!) — нужны
    nonce'ы (перенести в (b) на v0.1).
  - `'unsafe-inline'` для `style-src` оставляется, т.к. Angular Material
    и ng-style этого требуют. Это acceptable tradeoff (styles безопаснее
    scripts).
  **Последствия (каскад):**
  - 10 P0-3 → 🔧 TO-FIX.
  - 12 P0-1 / 13 P0-4 (CSP лендинга) — отдельный фикс через self-host
    (C0-6, уже закрыт). CSP web-panel и landing — разные vhost'ы,
    разные конфиги.
  - 09 P1-? (CSP для PWA) — аналогичный фикс для `/app/*` vhost'а.
    Подтвердить в отчёте 09 — отдельный P0/P1 или уже покрыто.
  - Связь с C0-7 (cookie HttpOnly): CSP + HttpOnly cookie — два слоя
    защиты от XSS-сессионного-хищения. Ни один по отдельности недостаточен.
  **Estimate:** ~40 минут (nginx + smoke в браузере + проверить что
  WebSocket `wss://ruttrack.site` не блокируется `connect-src`).
  **NEW:**
  - **NEW-54:** CSP-Report endpoint (v0.1) — добавить
    `report-uri /csp-violations` чтобы собирать violation-отчёты. Не
    в v0.0.0, но полезно знать, не блокирует ли CSP что-то в production.
  - **NEW-55:** аналогичный CSP для PWA vhost (`/app/*`). Сделать в
    одном PR с web-panel, т.к. паттерн одинаковый. Проверить в отчёте
    09 — если P0 уже есть, этот ответ его тоже закрывает.
  - **NEW-56:** добавить CSP check в CI — `curl -I https://ruttrack.site/login`
    → проверка наличия header'ов. Защита от случайного удаления.

- **Q7.** `/admin/users` показывает `initialPassword` в таблице — убирать
  после фикса 02 P0-1, или есть текущая необходимость показывать?
  **Ответ (auto-resolved через 01-Q1, подтверждено явно 2026-04-18):**
  **(a) ACCEPTED BY OWNER** — колонка `initialPassword` остаётся видимой
  в админ-таблице как заявленная фича (полезно админу повторно сообщить
  пароль студенту, потерявшему Telegram-чат). Защита shoulder-surfing
  (скрытая колонка с кнопкой «Показать») НЕ внедряется. 10 P2-13 →
  «Принято как есть».

### 12-frontend-landing
_(отвечено: 2 / 8)_

- **Q-P0-2.** «Открыть в Telegram» ведёт на `/login` web-panel, а не
  в бота?
  **Ответ (2026-04-18):** **(a)** Заменить `href` на deep-link
  `https://t.me/<bot_username>`. Username бота выносится в конфиг
  лендинга (переменная `VITE_TELEGRAM_BOT_USERNAME` или hardcoded
  constant в одном месте, в зависимости от build-системы лендинга).
  **Мотивация:**
  - Буквально правка `href` в HTML, 5 минут работы.
  - UX-expectation: кнопка «Открыть в Telegram» → открывает Telegram.
    Текущий редирект на `/login` — баг.
  **Закрывает:** 12 P0-2.
  **Что делается:**
  - Узнать username бота в проде (проверить `.env.prod` или у @BotFather).
  - В `frontends/landing/index.html` (или соответствующем шаблоне)
    заменить `<a href="/login">` → `<a href="https://t.me/<bot_username>">`.
  - Если есть build-процесс — вынести в env-переменную при сборке.
  - Smoke-тест: клик по кнопке открывает `tg://` или web-версию
    Telegram (в зависимости от устройства и настроек браузера).
  **Последствия (каскад):**
  - 12 P0-2 → 🔧 TO-FIX.
  - NEW-42 / NEW-43 (release runbook) — добавить чеклист «username
    бота корректен на лендинге».
  **Estimate:** ~30 минут (узнать username + правка + smoke).
  **NEW:**
  - **NEW-51:** `<bot_username>` — добавить в `docs/architecture/architecture.md`
    (или в `.env.prod.example`) как документированный секрет/config.

- **Q5.** Добавить страницу публичной политики конфиденциальности /
  пользовательского соглашения?
  **Ответ (auto-resolved через M1 + 01-Q1):** юридического требования по
  152-ФЗ нет (M1). Решение остаётся за владельцем как UX/маркетинговый
  выбор, не как блокер релиза. По умолчанию — НЕ добавляем для v0.0.0.

### 13-infra-docker-ci
_(ожидает ответов: 12 вопросов)_

### 14-tests-audit
_(отвечено: 2 / 10)_

- **Q-P0-1.** `attendance-service/latecheckin/` — полный домен без тестов?
  **Ответ (2026-04-18):** **(b)** Unit + integration + contract-тест
  RabbitMQ event `late_checkin.approved`.
  **Scope:**
  - **Unit-тесты (service layer):** все методы `LateCheckinService` —
    create, approve (с проверкой роли старосты), reject, list by group,
    list by user. Edge cases: student != owner, already approved,
    lesson closed, user not headman.
  - **Integration-тест (controller → MongoDB):** один happy-path для
    каждого endpoint'а через `MockMvc` + embedded MongoDB (или
    Testcontainers).
  - **Contract-тест RabbitMQ:** publisher `LateCheckinService.approve()`
    публикует `late_checkin.approved` event → валидация против
    `event-schemas/late-checkin-approved.json` (создать, если нет) →
    consumer-side schema validation.
  **Мотивация:**
  - Новый домен (Phase 60), бизнес-логика «кто имеет право одобрить» —
    классический источник багов при регрессии.
  - Contract-тест гарантирует что publisher и consumer не дрейфуют по
    формату события.
  - Прямая связь с C1-5 (contract-тесты RabbitMQ events) — этот фикс
    становится первым примером покрытия по схеме.
  **Закрывает:** 14 P0-1.
  **Что делается:**
  - `LateCheckinServiceTest.java` — unit-тесты (Mockito).
  - `LateCheckinControllerIT.java` — integration test.
  - `event-schemas/late-checkin-approved.json` — JSON Schema с
    `event_version: 1`.
  - `LateCheckinEventContractTest.java` — валидация payload против схемы.
  - Добавить в coverage-gate (C1-4) минимум 70% line coverage для
    `latecheckin/` package.
  **Последствия (каскад):**
  - 14 P0-1 → 🔧 TO-FIX.
  - C1-5 (contract-тесты) — первый покрытый event.
  - C1-4 (coverage-gate) — `latecheckin/` будет первым пакетом, куда
    gate применяется строго.
  - 08 P0-2 (схема `otp.requested`) — аналогичный паттерн, тот же
    contract-test framework.
  **Estimate:** ~1.5 человеко-дня (unit ~0.5д + IT ~0.5д + contract ~0.5д).
  **NEW:**
  - **NEW-52:** `event-schemas/late-checkin-approved.json` + аналогично
    для других late-checkin events (`late_checkin.requested`,
    `late_checkin.rejected`). Добавить все в один прогон.

- **Q-P0-2.** Telegram-бот callback_query без тестов (excuse, late_checkin,
  prefs)?
  **Ответ (2026-04-18):** **(b)** pytest + Aiogram fake-updates harness
  (integration) + mock-based unit-тесты.
  **Scope:**
  - **Unit-тесты (mock-based):** для каждого callback-handler'а:
    `excuse_approve`, `excuse_reject`, `late_checkin_approve`,
    `late_checkin_reject`, `prefs_*`. Mock `CallbackQuery`,
    `aiogram.Dispatcher`, gRPC-клиенты. Проверка:
    - handler вызывает правильный gRPC-метод с правильными параметрами;
    - handler проверяет роль старосты (где применимо);
    - handler публикует правильное RabbitMQ-событие;
    - handler отвечает правильным message.
  - **Integration-тесты (Aiogram fake-updates):** полный flow через
    dispatcher — `bot.handle_update({...})`. Aiogram 3 имеет встроенный
    test harness.
  - **Edge-cases:** отсутствующий пользователь, неверный callback_data
    формат, истёкший TTL кнопки, параллельные approve от двух старост.
  **Мотивация:**
  - Бот в проде без тестов — один забытый `await` и весь flow молча
    рвётся.
  - Aiogram fake-updates harness — стандартный способ, не требует
    реального Telegram-сервера.
  - Связь с 06 P0-5 (callback'и не проверяют роль) — тесты гарантируют
    regression prevention после фикса 06 P0-5.
  **Закрывает:** 14 P0-2.
  **Что делается:**
  - `notification-bot/tests/test_callback_excuse.py` — unit-тесты.
  - `notification-bot/tests/test_callback_late_checkin.py` — unit-тесты.
  - `notification-bot/tests/test_callback_prefs.py` — unit-тесты.
  - `notification-bot/tests/integration/test_full_flow.py` — Aiogram
    fake-updates для 2-3 ключевых сценариев (excuse approve happy path,
    late_checkin reject, prefs toggle).
  - `pytest-cov` в CI с gate минимум 70% для `handlers/` пакета.
  **Последствия (каскад):**
  - 14 P0-2 → 🔧 TO-FIX.
  - 06 P0-5 (callback'и не проверяют роль) — тесты прямо проверяют
    «не-староста не может approve». Фикс безопасности + regression
    test в одном пакете.
  - C1-4 (coverage-gate) — включает `pytest-cov` для бота.
  - 14 P1-7 (нет pytest-cov в CI) — связано, закрывается этим же
    PR'ом.
  **Estimate:** ~1.5 человеко-дня (unit ~1д + integration ~0.5д).
  **NEW:**
  - **NEW-53:** шаблон `tests/integration/conftest.py` для Aiogram
    fake-updates — фикстуры `dispatcher`, `bot`, `callback_query_factory`.
    Переиспользуется во всех integration-тестах.

### 15-cross-cutting-issues
_(отвечено: 1 / 13 — авто)_

- **Q2.** C0-2 (initial_password) — magic-link или разовый пароль?
  **Ответ (auto-resolved через 01-Q1):** ни то, ни другое. Выбран вариант
  **(a) accept tradeoff** — оставляем plaintext-цепочку как есть. Кластер
  C0-2 распускается, его 4-5 P0/P2 переходят в «Принято как есть».
  Magic-link сохранён в `docs/future-ideas.md` для v0.1+.

---

## P1 — Пачка A (Observability)

Ответы на 7 кросс-сервисных вопросов по логированию, метрикам, трейсингу,
health-checks. Ответы зафиксированы 2026-04-18.

- **QA1 — DEBUG в default application.yml всех Spring-сервисов?**
  **Ответ:** **(a)** Поменять default на `INFO`. Dev-окружение явно
  включает DEBUG через `application-dev.yml` или env-переменную.
  **Мотивация:** secure-by-default. DEBUG в проде = один пропущенный
  `SPRING_PROFILES_ACTIVE=prod`. JWT в query / SQL / payloads больше
  не логируются по ошибке.
  **Закрывает:** C1-10, 02 P2-1 (дополнительная страховка для
  accepted initial_password → не утекает в логи).
  **Что делается:**
  - Во всех `application.yml` (5 сервисов): `logging.level.ru.rutcampustrack: INFO`.
  - Создать `application-dev.yml` (или дополнить существующий) с
    `logging.level.ru.rutcampustrack: DEBUG`.
  - Удалить `application-prod.yml` переопределение (теперь default
    уже INFO, prod-overload не нужен для этого).
  - IDE run configuration выставляет `SPRING_PROFILES_ACTIVE=dev`.
  - В `docker-compose.yml` (dev) оставить env без prod-профиля, но
    явно `SPRING_PROFILES_ACTIVE=dev` если нужны DEBUG-логи для
    локальной отладки.
  **Estimate:** ~1 час (5 yml-правок + проверка IDE-конфигов).
  **NEW:**
  - **NEW-57:** CI-check «в `application.yml` нет `DEBUG`-уровней для
    пакетов `ru.rutcampustrack.*`». Grep-regex защищает от регрессии.

- **QA2 — Distributed tracing между сервисами?**
  **Ответ:** **(b)** Spring Cloud Sleuth → Micrometer Tracing
  (OpenTelemetry) + Grafana Tempo для хранения трейсов. Полноценный APM.
  **Мотивация:** запрос проходит Gateway → 2-3 сервиса → RabbitMQ →
  бот. При баге невозможно отследить цепочку без trace_id.
  Tempo даёт визуализацию span-дерева в Grafana — критично для
  дебага производительности и межсервисных ошибок.
  **Закрывает:** NEW-наблюдаемости от P1-A.
  **Что делается:**
  - Добавить `io.micrometer:micrometer-tracing-bridge-otel` +
    `io.opentelemetry:opentelemetry-exporter-otlp` во все 5 Spring-сервисов.
  - Конфиг: `management.tracing.sampling.probability: 1.0` для v0.0.0
    (малый traffic, ловим всё; снизить до 0.1 когда вырастет).
  - В `docker-compose.prod.yml` + `docker-compose.yml` добавить
    контейнер `grafana/tempo:2.x` с digest-пин'ом (NEW-16).
  - Tempo persistent volume + retention `14d` (согласовано с QA5).
  - Grafana datasource Tempo, пример запросов в README.
  - Python-бот: `opentelemetry-instrumentation-aiogram` +
    `opentelemetry-exporter-otlp` → trace_id в том же формате.
  **Последствия (каскад):**
  - QA3 (trace_id в RabbitMQ events) — Sleuth автоматически вставит
    trace_id в Spring AMQP messages через RabbitTemplate + встроенные
    interceptor'ы. Ручное копирование в body остаётся нужным (QA3=a)
    для явного поля в схеме и для Python-стороны.
  - QA4 (метрики) — Micrometer уже будет для tracing, метрики бесплатно.
  - 13 P1 (base images digest) — Tempo тоже нужен digest-пин.
  **Estimate:** ~3-4 человеко-дня (конфиг 5 сервисов + Tempo setup +
  Python instrumentation + тестирование end-to-end trace видимости).
  **NEW:**
  - **NEW-58:** `docs/operations/monitoring/observability.md` — раздел «Distributed tracing»,
    формат trace_id, как читать span-tree в Grafana, типичные dashboards.
  - **NEW-59:** OTLP exporter требует порт 4317 (gRPC) — добавить в
    docker-compose internal network.

- **QA3 — Корреляция логов и RabbitMQ-событий?**
  **Ответ:** **(a)** Добавить `trace_id` и `occurred_at` в body
  каждого RabbitMQ-события. Publisher читает из MDC (заполняется
  Sleuth из QA2), consumer вытаскивает и ставит в свой MDC.
  **Мотивация:**
  - Явное поле в схеме = event self-describing (consumer без Sleuth
    тоже видит trace).
  - Python-бот (consumer) получает trace_id понятным способом, не
    полагаясь на AMQP-headers.
  - Связка с 19a (схема otp.requested) — унифицируем все схемы: каждое
    событие обязано иметь `event_version`, `trace_id`, `occurred_at`.
  **Закрывает:** C1-5 (contract-тесты событий) дополняется по trace,
  наблюдаемость.
  **Что делается:**
  - Обновить JSON-schema для всех 14+ events: добавить обязательные
    поля `trace_id` (string), `occurred_at` (date-time), `event_version`
    (integer). Связано с NEW-47 (retrofit версионирования).
  - Общая библиотека `shared-events` (или расширить `shared-web`):
    `AbstractEventPublisher` автоматом заполняет поля из MDC.
  - Consumer: `AbstractEventConsumer` вытаскивает trace_id и кладёт
    в MDC перед handler'ом.
  - Python-бот: дублирующая логика через aio-pika + logging extra.
  **Estimate:** ~2 человеко-дня (библиотека + миграция всех схем +
  publisher/consumer рефакторинг).
  **NEW:**
  - **NEW-60:** shared-events модуль (либо внутри shared-web) —
    `AbstractEventEnvelope` record с общими полями.
  - **NEW-61:** версионирование схем (NEW-47 ранее) объединяется с
    этой задачей — один PR обновляет все схемы сразу.

- **QA4 — Нет бизнес-метрик (KPI)?**
  **Ответ:** **(b)** `@Counted`/`@Timed` на ключевые методы +
  Grafana-dashboard + **Telegram-алерты** админу при аномалиях
  (check-ins < 5% от baseline).
  **Мотивация:** при инциденте «никто не check-in'ится» быстрый ответ
  «бэкенд сломан vs никто не пришёл». Алерты в Telegram используют уже
  существующий bot-канал, новой инфры не нужно.
  **Закрывает:** наблюдаемость для инцидент-отклика.
  **Что делается:**
  - Методы для @Counted: `AttendanceService.checkIn`,
    `ExcuseService.create`, `LateCheckinService.create`,
    `OtpService.request`, `OtpService.verify`, `AuthService.login`.
  - Методы для @Timed: gRPC-калёллы между сервисами, `RabbitTemplate.send`.
  - Custom metrics: `students_in_red_zone` (gauge, обновляется
    каждые 5 минут scheduled-job'ом), `active_ws_sessions` (из
    handshake из 16d).
  - Grafana dashboard «Business KPIs» — 6-8 панелей.
  - **Alert-rule в Prometheus:**
    `rate(attendance_checkin_total[1h]) < 0.05 * avg_over_time(rate(attendance_checkin_total[1h])[7d:1h])`
    → Alertmanager → webhook → notification-bot → Telegram админу.
  - **Baseline collection:** первые 2 недели после релиза алерты
    выключены (собираем данные), потом включаем с порогами.
  **Последствия (каскад):**
  - QA2 Tempo + QA4 Grafana — один stack, дешевый.
  - 02-Q3 outbox — добавить метрики «outbox lag» (сколько unsent
    events старше 30с).
  - NEW (связанный): admin получит Telegram-алерт, значит нужен
    механизм «тихий час» (22:00-08:00) чтобы не будить ночью по
    некритичным алертам. Часть (b).
  **Estimate:** ~3 человеко-дня (метрики × 6-8 методов + custom
  gauges + dashboard JSON + alert-rule + Telegram-webhook bot-side).
  **NEW:**
  - **NEW-62:** `notification-bot` получает новый endpoint
    `POST /internal/alert` (Python FastAPI или добавить в Aiogram webapp)
    для приёма Alertmanager webhook'ов → рассылка Telegram админу.
  - **NEW-63:** `docs/operations/monitoring/alerts.md` — список алертов, порогов, runbook
    «что делать при срабатывании».
  - **NEW-64:** «тихий час» для не-critical алертов — параметр
    `inhibit_rules` в Alertmanager или логика в notification-bot.
  - **NEW-65:** 2 недели baseline перед включением алертов — зафиксировать
    в release runbook (NEW-13).

- **QA5 — Retention Loki/Prometheus?**
  **Ответ:** **(c) 14 дней.** Экономим VPS диск.
  **Мотивация:** pet-проект, один VPS. 14 дней достаточно для
  инцидент-отклика (типичное окно обнаружения ≤ недели).
  **Закрывает:** observability retention policy.
  **Что делается:**
  - `loki.yaml`: `limits_config.retention_period: 336h` (14d).
  - `prometheus.yml`: `--storage.tsdb.retention.time=14d` в command.
  - `grafana/tempo`: retention 14d (из QA2).
  - Задокументировать в `docs/operations/monitoring/observability.md` → раздел «Retention».
  - Включить мониторинг disk usage через `cadvisor` (уже есть в инфре),
    алерт «disk > 80%» (добавить в QA4 alert-набор).
  **Последствия (каскад):**
  - Если инцидент обнаружен >14д после факта — forensics невозможен.
    Accept tradeoff.
  - NEW-66 (ниже): если проект вырастет — пересмотреть.
  **Estimate:** ~1 час (config + smoke-тест retention срабатывает).
  **NEW:**
  - **NEW-66:** триггер пересмотра retention — когда VPS расширится
    или появится compliance-требование. Записать в `docs/future-ideas.md`.

- **QA6 — Health-check endpoint'ы без meaningful статуса?**
  **Ответ:** **(a)** `management.endpoint.health.show-details: always`
  + custom `HealthIndicator` для gRPC-клиентов + RabbitMQ + БД.
  Docker health-check → `/actuator/health` (не по порту).
  **Мотивация:** partial degradation (БД работает, RabbitMQ упал) —
  сервис должен сигнализировать DEGRADED/DOWN, docker будет рестартить.
  **Закрывает:** наблюдаемость partial-failure сценариев.
  **Что делается:**
  - `management.endpoint.health.show-details: always` во всех 5
    Spring-сервисах.
  - `management.endpoint.health.probes.enabled: true` для будущего
    k8s-ready (хотя мы на compose).
  - Custom `GrpcClientHealthIndicator` — ping downstream через
    reflection-service или легкий gRPC health-protocol.
  - Spring Boot уже даёт автоматические indicators: `db`, `rabbit`,
    `redis`, `mongo`, `diskSpace`. Включить их явно.
  - В `docker-compose.prod.yml`: `healthcheck: test: curl
    http://localhost:9091/actuator/health | grep -q UP`.
  - Если health DOWN 3 раза подряд — docker compose restarts
    container (настроить `restart: unless-stopped` + healthcheck).
  **Последствия (каскад):**
  - QA4 — алерт «сервис DOWN» поверх health.
  - QA2 trace — health-check запросы исключить из sampling (спам).
  - 07 P0-? (если есть health-check в Gateway) — тот же паттерн.
  **Estimate:** ~1.5 дня (5 сервисов × конфиг + 2-3 custom indicators
  + docker healthcheck обновления + тесты).
  **NEW:**
  - **NEW-67:** включить Spring Actuator endpoint `/actuator/info` с
    build-version, git-sha — для диагностики «какая версия в проде».
    Интегрируется с NEW-16 (IMAGE_TAG=${sha}).

- **QA7 — Логи бота и унификация формата?**
  **Ответ:** **(b)** Структурированный JSON-логи везде: Python
  `structlog`, Java `logstash-logback-encoder`. Единый формат полей:
  `{ts, level, msg, service, trace_id, user_id, ...}`.
  **Мотивация:** queryable логи в Grafana/Loki — `{service="attendance"}
  | json | user_id="123"` работает одинаково для всех сервисов.
  Plain-text grep сжигает queryability.
  **Закрывает:** C1-? (если есть в списке), базовая observability.
  **Что делается:**
  - Java: в каждый сервис добавить зависимость
    `net.logstash.logback:logstash-logback-encoder`, `logback-spring.xml`
    с JSON encoder + MDC fields.
  - Общий `logback-spring.xml` в `shared-web` модуле (из 16a) —
    включается через `<include resource="shared/logback-base.xml"/>`.
  - Python-бот: `structlog` с JSON-processor + stdlib-logging
    bridge. Aiogram middleware вставляет `user_id`, `callback_type`,
    `trace_id` в context.
  - В Loki/Grafana: стандартные dashboards по `service`/`user_id`/
    `trace_id`.
  - Все `log.debug("user {}"...)` → `log.debug("user", user_id=123)` —
    structured.
  **Последствия (каскад):**
  - QA1 (INFO-дефолт) — применяется к JSON-логам одинаково.
  - QA3 (trace_id в events) — MDC автоматически пропагирует в JSON.
  - Размер логов растёт на ~30% (JSON overhead) — учесть в QA5 retention.
  **Estimate:** ~2 человеко-дня (5 Spring-сервисов конфиг + Python
  structlog wiring + bot middleware + обновление Grafana dashboards).
  **NEW:**
  - **NEW-68:** `shared/logback-base.xml` в shared-web модуле.
    Защищает от drift'а между сервисами.
  - **NEW-69:** рефакторинг всех существующих `log.X(..., param)`
    вызовов на structured-style. Автоматизация через IntelliJ structural
    search. Scope ~2-3 часа.
  - **NEW-70:** Python-бот добавляется также в promtail scrape config
    (если ещё нет), labels `service=notification-bot`.

---

## P1 — Пачка B (Data integrity)

Ответы на 7 вопросов по soft-delete, audit, миграциям, целостности.
Ответы зафиксированы 2026-04-18.

- **QB1 — Hard-delete групп?**
  **Ответ:** **(c)** Soft-delete через `groups.status = 'archived'`
  + audit-log «кто когда заархивировал».
  **Мотивация:** consistent с users (CLAUDE.md soft-delete через status).
  FK остаются валидными. Audit-запись даёт trace «кто инициатор»
  (полезно если админ случайно нажал не на ту группу).
  **Закрывает:** 02 P1-? (hard-delete групп).
  **Что делается:**
  - Миграция `V{N}__groups_archived_status.sql`: добавить `status
    VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN
    ('active','archived'))`, `archived_at TIMESTAMPTZ`, `archived_by BIGINT`.
  - `GroupService.delete()` → `archive()`: `UPDATE groups SET status='archived',
    archived_at=NOW(), archived_by=:actorId WHERE id=:id`.
  - Все queries автоматически фильтруют `WHERE status='active'` через
    Hibernate `@Where(clause = "status = 'active'")` или custom repo.
  - Admin UI: кнопка «архивировать» вместо «удалить», отдельная страница
    «архив» для восстановления.
  - Audit-поля (`archived_by`) пишутся из Internal JWT userId (после C0-1).
  **Последствия (каскад):**
  - QB2 (общий audit-log) отклонён — поля `archived_by`/`archived_at`
    в самой таблице достаточны + Loki JSON-логи дают остальное.
  - NEW (связанный): аналогичный паттерн для subjects, semesters,
    lesson_templates — где hard-delete. Audit минимально: `archived_by`,
    `archived_at` поля.
  **Estimate:** ~4 часа (миграция + service + UI кнопка + тесты).
  **NEW:**
  - **NEW-71:** аудит всех hard-delete операций (grep `DELETE FROM`
    по коду) → решить для каждой сущности: soft/hard. Subjects,
    semesters, lesson_templates — скорее всего soft. Excuse-tickets —
    hard после решения (уже не нужны).

- **QB2 — Audit-log административных действий?**
  **Ответ:** **(c) ACCEPTED** — полагаемся на JSON-логи Loki (QA7) +
  локальные `archived_by`/`changed_by` поля в таблицах где нужно.
  **Мотивация:**
  - Отдельная audit-таблица = дополнительная поддержка (миграции,
    retention, queries) без пропорциональной выгоды для pet-проекта.
  - QA7 (structured JSON) уже даёт: service, user_id, action,
    timestamp, trace_id, entity_id в логах.
  - Ограничение: Loki retention 14д (QA5). Для долгосрочного audit
    (> 14д) — недостаточно, но это accepted tradeoff.
  **Закрывает:** cross-cutting audit-log вопрос.
  **Что делается:**
  - Документировать в `docs/operations/monitoring/observability.md` → раздел «Audit через
    логи»: как найти «кто создал user X», «кто удалил lesson Y»
    через LogQL-queries.
  - Примеры queries: `{service="academic"} |= "action=archive"
    | json | entity_type="group" | entity_id=42`.
  - Обеспечить что все admin-операции логируются структурированно
    (в QA7 middleware / aspect — `@AdminAction` аннотация).
  **Последствия (каскад):**
  - QB1 audit-поля в таблице groups — не пересекаются с Loki, дополняют.
  - NEW-66 (retention триггер пересмотра) — если появится
    compliance/forensics-требование, первой добавляется отдельная
    audit-таблица с retention 1-5 лет.
  **Estimate:** ~1 час (доки + проверка что QA7 покрывает admin-actions).
  **NEW:**
  - **NEW-72:** `@AdminAction(entity="group", action="archive")`
    аннотация + AOP aspect — гарантирует единый формат в логах. Часть
    shared-web модуля (из 16a). Упрощает LogQL-queries.
  - **NEW-73:** в `docs/operations/monitoring/observability.md` примеры LogQL-запросов для
    типичных audit-вопросов («кто удалил group X», «все действия
    admin user 42 за сутки»).

- **QB3 — Flyway миграции без rollback-стратегии?**
  **Ответ:** **(b)** Expand/contract паттерн документируется как
  правило для breaking-миграций.
  **Мотивация:**
  - Совместимо с memory-заметкой `feedback_flyway_no_edit.md` (не
    править применённые миграции).
  - Позволяет откатить код без БД-отката: старая версия ещё работает
    со старой схемой, новая — с новой.
  - Дисциплина применяется только к breaking-миграциям (drop column,
    rename, change type). Add-column идёт напрямую.
  **Закрывает:** 13 P1-? (rollback-стратегия).
  **Что делается:**
  - `docs/operations/runbooks/flyway-expand-contract.md` — пошаговый чеклист:
    1. Expand: добавить новое поле/таблицу рядом со старым (V_N).
    2. Deploy code: читает из обоих, пишет в оба.
    3. Backfill: миграция копирует старые данные в новые (V_N+1).
    4. Deploy code: читает из нового, пишет в новое.
    5. Contract: через 1-2 релиза удалить старое (V_N+2).
  - В PR-template (на github) добавить чеклист «breaking migration?
    → expand/contract runbook».
  - Примеры: переименование `users.email` → `users.contact_email`
    через 3 релиза.
  **Последствия (каскад):**
  - C0-7 (JWT cookie) — breaking change, но не для БД (только API).
    Не требует expand/contract.
  - NEW-37 (Flyway migration move runbook из 16b) — связан, один
    раздел в `docs/operations/runbooks/`.
  **Estimate:** ~2 часа (runbook + PR-template + один пример).
  **NEW:**
  - **NEW-74:** PR-template `.github/pull_request_template.md` с
    чеклистом «breaking-migration → expand/contract».
  - **NEW-75:** шаблон expand/contract в `docs/operations/runbooks/` —
    копипастабельный для новых случаев.

- **QB4 — `homework_submissions` без soft-delete?**
  **Ответ:** **ПЕРЕКЛАССИФИЦИРОВАНО — НЕ ПРОБЛЕМА.** Владелец уточнил:
  `homework_submissions` это **UI-трекер студента для самоконтроля**,
  не submissions в академическом смысле. Студент ставит/снимает галочку
  «сделал/не сделал» сам. Отчётность не нужна, soft-delete не нужен.
  **Действия:**
  - В отчёте 02 пометка P1 «homework_submissions без soft-delete» →
    переклассифицировать как «UI-state, accept hard-delete».
  - В `docs/architecture/database-schema.md` — явное пояснение к таблице
    `homework_submissions`: «личный трекер студента, hard-delete
    allowed, не используется для академической отчётности».
  - В API: endpoint `DELETE /homework/submissions/{id}` → убедиться
    что проверяет `owner_id == current_user.id` (IDOR-защита как
    NEW-31).

- **QB5 — Изменение `lesson` без истории?**
  **Ответ:** **(d)** Запретить изменение lesson после старта пары.
  Только отмена (cancellation). Простой guard.
  **Мотивация:**
  - Event-sourcing/temporal-table — переписать половину сервиса,
    overkill.
  - Accept даёт broken UX (check-in «за час до пары»).
  - (d) — cheapest защита: один `if (lesson.startTime.isBefore(now()))
    throw`. Закрывает 90% проблемы.
  - Temporal history для lesson — в `future-ideas.md` как v0.1+ фича
    («поддержка переноса пары после старта»).
  **Закрывает:** 03 P1-? (lesson update без истории).
  **Что делается:**
  - В `LessonService.update()`: валидация `lesson.startTime >= now()`
    перед применением изменений. Если нет — `LessonAlreadyStartedException`
    → 409 Conflict с понятным message.
  - Для admin-override (если такой нужен — преподаватель опоздал,
    хочет сдвинуть время уже начавшейся пары): отдельный endpoint
    `POST /lessons/{id}/override` с role=ADMIN и audit-логом.
  - В frontend (web-panel teacher/admin): disable edit-кнопки если
    пара уже началась, показать «Пара началась, только отмена».
  - `LessonService.cancel()` работает всегда (включая in-progress):
    меняет статус на `cancelled`, логирует причину.
  **Последствия (каскад):**
  - 03 P1 → 🔧 TO-FIX через guard.
  - NEW (связан): UX в web-panel — disabled кнопки с tooltip
    «Пара уже началась».
  **Estimate:** ~3 часа (guard + exception + frontend UX + тесты).
  **NEW:**
  - **NEW-76:** в `docs/future-ideas.md` добавить раздел «Lesson
    temporal history» — когда понадобится поддержка переноса in-progress
    пары, внедрять temporal table.
  - **NEW-77:** admin-override endpoint как отдельная задача, если
    реально нужен. Пока accept — не нужен.

- **QB6 — Change `telegram_id` без verification?**
  **Ответ:** **(a)** Двухшаговая верификация: новый telegram_id
  подтверждает через `/start` с токеном из admin-панели.
  **Мотивация:**
  - Классический привилегированный-эскалации вектор: admin может
    переписать telegram_id жертвы на свой → получать OTP/push.
  - Двухшаговая верификация закрывает все сценарии админ-атаки
    и user-ошибки (опечатка в новом telegram_id).
  - ~1 день работы.
  **Закрывает:** 01 P1-?, 02 P1-? (telegram_id change без verification).
  **Что делается:**
  - Новая таблица `telegram_id_changes`: `{id, user_id, old_telegram_id,
    new_telegram_id, token, token_expires_at, status, requested_by,
    confirmed_at}`.
  - Admin/self-service endpoint `POST /users/{id}/telegram-id`:
    генерирует token (random 32-byte), сохраняет в pending-статусе
    с TTL 24 часа.
  - Admin/пользователь получает instruction: «открыть @bot → /start
    <token>».
  - Бот handler `/start <token>`: валидирует token, telegram_id запроса
    == new_telegram_id из записи, активирует: `UPDATE users SET
    telegram_id = new_telegram_id`, `UPDATE telegram_id_changes SET
    status='confirmed'`.
  - До подтверждения: старый telegram_id остаётся активным,
    OTP/push идут туда.
  - Expired/cancelled pending — token invalidated.
  **Последствия (каскад):**
  - C0-1 (Internal JWT) — проверка `actor_role = ADMIN` для
    admin-initiated changes; для self-service actor == user.
  - QB7 (uniqueness telegram_id) — при проверке pending-записей
    тоже блокировать повторное использование.
  - NEW-45 (`docs/redis-keyspace.md`) — tokens можно в Redis
    вместо таблицы (TTL нативно). Решить при имплементации.
  - Связка с 01-Q-P0-4 (OTP через RabbitMQ) — аналогичный паттерн
    «bot как second channel verification».
  **Estimate:** ~1 человеко-день (миграция + 2 endpoint'а + bot-handler
  + UI + тесты).
  **NEW:**
  - **NEW-78:** решение Redis vs таблица для tokens — при имплементации.
    Redis проще, таблица даёт audit.
  - **NEW-79:** self-service flow в PWA/web-panel: «хочу сменить
    Telegram» → показать token → рендер QR-кода с deep-link
    `https://t.me/<bot>?start=<token>`. UX-task.

- **QB7 — Uniqueness login/telegram_id для archived users?**
  **Ответ:** **(a)** Archived пользователи сохраняют уникальность
  login/telegram_id. Нельзя создать нового с тем же login, даже если
  старый архивирован. При «утерял Telegram» — админ перепривязывает
  через QB6-flow.
  **Мотивация:**
  - История сохраняется: один login = один человек за все времена.
  - Восстановить старого пользователя (unarchive) проще чем разбираться
    с конфликтами.
  - Unique-индекс остаётся глобальным, partial unique не нужен.
  **Закрывает:** 01 P1-? (re-use login после archive).
  **Что делается:**
  - Проверить текущий unique-индекс на `users.login` и `users.telegram_id`
    — если там где-то есть `WHERE status != 'archived'` — убрать.
    Unique остаётся безусловным.
  - Admin UI при создании user'а: если login коллизит с archived —
    показать «логин занят, такой пользователь был архивирован
    [дата]. Хотите восстановить?». Операция `unarchive` (status →
    active).
  - Документировать в `docs/architecture/architecture.md`: «login / telegram_id
    immutable identity, unique across all statuses, unarchive вместо
    создания нового».
  **Последствия (каскад):**
  - QB1 (soft-delete групп) — не пересекается, uniqueness в группах
    не применима (имена групп могут повторяться между годами).
  - QB6 (telegram_id change) — верификация проверяет и archived
    users: если новый telegram_id уже где-то (включая archived) — reject.
  **Estimate:** ~2 часа (проверка индексов + Admin UI «unarchive»
  кнопка + документация).
  **NEW:**
  - **NEW-80:** `GET /admin/users/archived` endpoint + UI-раздел
    «Архив пользователей». Без этого админ не знает, что там есть.
  - **NEW-81:** при unarchive учесть что `telegram_id` может уже
    принадлежать другому активному пользователю (если QB7 нарушался
    в прошлом). Проверить перед включением ограничения, возможно
    нужна data-cleanup миграция.

---

## P1 — Пачка C (Frontend reuse)

Ответы на 7 вопросов по фронтенд-дубликатам, type-drift, unified UX.
Ответы зафиксированы 2026-04-18.

- **QC1 — Три STOMP-клиента в web-panel?**
  **Ответ:** **(a)** Единый `NotificationCenter` сервис в Angular —
  один STOMP-клиент, fan-out через RxJS Subject'ы. Три консьюмера
  (admin-dashboard, teacher-journal, notification-bell) подписываются
  на relevant streams.
  **Мотивация:**
  - 3 connection'а → 3x нагрузка на notification-web handshake.
  - Разный код reconnect/disconnect — разное поведение при сетевых
    проблемах.
  - Единая точка — единый audit-log (QA7 JSON), единый trace (QA2).
  **Закрывает:** C1-1, 10 P1-5.
  **Что делается:**
  - Создать `web-panel/src/app/core/notification-center/notification-center.service.ts`:
    - `@Injectable({providedIn: 'root'})` — singleton.
    - Один `StompClient` подключается при логине через ws-ticket (C0-7).
    - Топики: `messages$: Subject<NotificationEvent>` (per-user queue),
      `dashboard$: Subject<DashboardEvent>` (broadcast), и т.д.
    - `reconnectWithBackoff()` — экспоненциальный бэкофф, общий для всех.
    - `disconnect()` вызывается из `clearAllClientState()` (02-Q-frontend-security Часть Б).
  - Три консьюмера переписать на `notificationCenter.messages$.pipe(filter(...))`.
  - Удалить три отдельных StompClient инициализации.
  - Unit-тест: один реконнект для всех подписчиков, сообщение доставляется
    всем заинтересованным consumers.
  **Последствия (каскад):**
  - C0-7 ws-ticket — один `/auth/ws-ticket` вызов на страницу, не три.
  - 09 P1-? (PWA три STOMP в разных местах) — аналогичный NotificationCenter
    для React, но в отдельном пакете (не shared через npm — сложно).
  - QC2 (openapi-ts) — события типизируются из backend OpenAPI.
  **Estimate:** ~3 человеко-дня (Angular service + миграция 3 консьюмеров
  + PWA React-версия + unit-тесты).
  **NEW:**
  - **NEW-82:** PWA NotificationCenter (React+zustand/context) —
    отдельная реализация, единый паттерн. Позже при monorepo-миграции
    (v0.1+) можно выделить в общий пакет.
  - **NEW-83:** docs/frontend-architecture.md — раздел про
    NotificationCenter, его API, как добавить новый consumer.

- **QC2 — OpenAPI → TypeScript type-gen?**
  **Ответ:** **(b)** `openapi-typescript` + `openapi-fetch` → generated
  `src/api/generated/` + typed axios/fetch client.
  **Мотивация:**
  - Backend swagger уже есть (OpenAPI 3.x через springdoc).
  - Generated client = один источник правды, drift невозможен на
    compile-time.
  - `openapi-fetch` лучше чем axios для типизации (discriminated unions
    по path+method).
  - Закрывает breaking-change из 02 P0-7 (`Homework[]` → `PagedModel`) —
    TS compiler отловит несоответствие до runtime.
  **Закрывает:** C1-2, NEW-27 (type-gen связка с C1-2), 09 P2-5, 10 P2-8.
  **Что делается:**
  - В build-pipeline PWA и web-panel: pre-build шаг `openapi-typescript
    https://ruttrack.site/api/v3/api-docs > src/api/generated/schema.ts`.
  - `src/api/client.ts` — обёртка `createClient<paths>({baseUrl: '/api'})`.
  - Все fetch-вызовы переписать на `client.GET('/api/homework', {params: {...}})`.
  - OpenAPI YAML коммитится в репо (snapshot) — CI проверяет что
    generated code in-sync с commit'ом. Если бэкенд поменял DTO без
    regeneration — CI падает.
  - Поддержать как backend local (для dev) так и production URL.
  **Последствия (каскад):**
  - 02 P0-7 (Pageable HomeworkController breaking change) — после
    type-gen работы не боится breaking changes: CI сразу показывает
    какие фронт-вызовы сломаны.
  - QC3 (RFC 7807 parser) — тип `ErrorResponse` тоже generated.
  - 08 (OpenAPI/proto events) — proto всё равно отдельно (gRPC), но
    REST унифицируется.
  **Estimate:** ~2 дня (setup + миграция всех endpoint-вызовов в PWA
  + web-panel + CI-check).
  **NEW:**
  - **NEW-84:** CI-job «openapi-schema-check» — regenerates client,
    `git diff --exit-code`, падает если генерированный код не in-sync.
  - **NEW-85:** решить snapshot OpenAPI YAML в репо или нет. Рекомендую
    да (`docs/openapi-snapshot.yaml`) — позволяет фронт-разработчику
    работать без запущенного backend.

- **QC3 — Обработка ошибок в PWA/web-panel?**
  **Ответ:** **(b)** Axios/fetch interceptor + RFC 7807 parser →
  централизованная обработка: 401→redirect, 403→toast, 429→cooldown
  с retry_after, 4xx/5xx→показать `detail` из RFC 7807.
  **Мотивация:**
  - 16a (shared-web) гарантирует единый RFC 7807 формат ошибок с
    backend'а — фронт должен уметь parse'ить.
  - Централизация избавляет от drift'а: один код обработки ошибок.
  - Связка с C0-4 rate-limit: 429 с `retry_after_seconds` показывает
    countdown (NEW-46).
  **Закрывает:** dedup обработки ошибок, связан с C1-11.
  **Что делается:**
  - `src/api/error-interceptor.ts` в PWA и web-panel:
    - 401 → `authService.logout()` + `router.navigate('/login')`.
    - 403 → `toast.error(problem.detail || 'Нет доступа')`.
    - 429 → `toast.error('Слишком много запросов')` + countdown в
      specific UI.
    - 5xx → `toast.error('Сервис временно недоступен')` + Sentry (v0.1).
  - `ProblemDetails` type generated через QC2 openapi-ts.
  - Убрать индивидуальный try/catch из компонентов (там где он только
    для toast) — централизованный interceptor покрывает.
  - Для специфичных случаев (409 Conflict при lesson update из QB5) —
    компонент обрабатывает локально, interceptor пропускает через
    `errorType === 'conflict'`.
  **Последствия (каскад):**
  - NEW-46 (countdown на 429) — реализуется в interceptor.
  - QC1 NotificationCenter — ws disconnect обрабатывается отдельно,
    не через HTTP-interceptor.
  **Estimate:** ~1.5 дня (interceptor × 2 приложения + миграция
  компонентов + unit-тесты).
  **NEW:**
  - **NEW-86:** `docs/frontend-architecture.md` раздел «Error handling» —
    матрица «HTTP status × action».
  - **NEW-87:** общий `toast-service` (Angular Material Snackbar,
    PWA react-hot-toast) — унифицированный API `toast.error/success/info`.

- **QC4 — `window.prompt` для cancel-reason?**
  **Ответ:** **(b)** Angular Material `MatDialog` +
  переиспользуемый `ConfirmWithReasonDialog` компонент.
  **Мотивация:**
  - Паттерн повторяется: cancel lesson, reject excuse, reject
    late_checkin, отмена homework (если понадобится).
  - Один компонент = consistent UX, min character validation,
    accessible focus management.
  **Закрывает:** 10 P1-? (prompt), косвенно excuse/late_checkin UX.
  **Что делается:**
  - `web-panel/src/app/shared/confirm-with-reason/confirm-with-reason.component.ts`:
    - Inputs: `title`, `message`, `reasonMinLength` (default 10),
      `reasonRequired` (default true), `confirmLabel`, `dangerAction`
      (стиль красной кнопки).
    - Выход: `{confirmed: boolean, reason: string | null}` через
      dialogRef.close().
  - Все `window.prompt` в web-panel заменить на
    `dialog.open(ConfirmWithReasonDialog, {data: {...}})`.
  - Для PWA (React) — аналогичный `ConfirmWithReasonModal` с той же
    API. React-портировать паттерн.
  - Audit всех confirmation-UX в приложении: confirm delete, confirm
    archive (QB1), confirm cancel — все через этот компонент либо
    простой ConfirmDialog (без reason).
  **Последствия (каскад):**
  - QB1 archive group — использует ConfirmDialog (без reason) или
    ConfirmWithReasonDialog если надо объяснить.
  - QB5 lesson cancellation — использует с reason.
  **Estimate:** ~2 человеко-дня (Angular компонент + React-аналог +
  миграция всех call-sites).
  **NEW:**
  - **NEW-88:** также `ConfirmDialog` (без reason) — для простых
    «Вы уверены?». Пара компонентов закрывает весь confirmation UX.
  - **NEW-89:** Storybook-like showcase для shared components — если
    команда вырастет, нужна документация. Не в v0.0.0.

- **QC5 — Lazy-loading по ролям в web-panel?**
  **Ответ:** **(a)** Per-role modules: admin/teacher/student/headman
  каждая через `loadChildren: () => import(...)`. Main bundle ~800KB,
  per-role ~500-800KB.
  **Мотивация:**
  - Админ не грузит teacher/student/headman код — быстрее first-load,
    меньше surface для XSS через dead code.
  - Natural code boundary совпадает с ролевыми guards (09 P0-3 /
    web-panel аналог).
  - Route-level split (b) — over-engineering, каждая дополнительная
    точка split = риск lazy-load багов.
  **Закрывает:** 10 P2-? (bundle size).
  **Что делается:**
  - В `web-panel/src/app/app-routing.module.ts`:
    ```ts
    {path: 'admin', loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule), canActivate: [RoleGuard], data: {allow: ['ADMIN']}},
    {path: 'teacher', loadChildren: () => import('./teacher/teacher.module')...},
    {path: 'student', loadChildren: () => import('./student/student.module')...},
    {path: 'headman', loadChildren: () => import('./headman/headman.module')...},
    ```
  - Каждый модуль (`AdminModule`, etc) — `NgModule` со своими routes,
    components, services.
  - `CoreModule` / `SharedModule` — общие сервисы (NotificationCenter,
    toast, dialogs из QC4).
  - Bundle-analyzer проверка после миграции: main должен быть
    <800KB, лишнее смотреть.
  **Последствия (каскад):**
  - C0-7 (JWT cookie) — ws-ticket вызывается после логина один раз,
    module-load не влияет.
  - QC3 error-interceptor — регистрируется в CoreModule,
    lazy-loaded модули его видят автоматом.
  **Estimate:** ~2-3 человеко-дня (структурная перекладка + тесты
  всех роутов + bundle-analyzer check + performance smoke).
  **NEW:**
  - **NEW-90:** CI-check бандл-размера — `bundle-size-action` или
    самописный шаг: main bundle > 1MB → фейл. Защита от regression.
  - **NEW-91:** preload-стратегия: `PreloadAllModules` after-app-load
    чтобы при ленивой навигации не было задержки. Или `QuicklinkStrategy`
    для умной подгрузки.

- **QC6 — PWA StatsPage N×2 запросов?**
  **Ответ:** **(b)** Backend-aggregate endpoint
  `GET /attendance/stats/my?group_id=&semester_id=` + TanStack Query
  cache `staleTime: 60_000`.
  **Мотивация:**
  - 10 предметов = 20 запросов в PWA. Сетевой latency на мобильном
    критичен.
  - Backend-агрегат = 1 запрос, 1 SQL с GROUP BY, <100ms.
  - Cache 60 сек: студент листает туда-обратно — повторных запросов нет.
  **Закрывает:** 09 P0-5 (повышено до P1 после рекласса), 09 P1-?
  (N+1 запросов).
  **Что делается:**
  - Backend attendance-service: `GET /api/attendance/stats/my` →
    `{subjects: [{subjectId, subjectName, total, present, absent,
    excused, free_attendance, cancelled}]}`. Один SQL с `COUNT(*)
    FILTER (WHERE status=...)`.
  - HATEOAS: обёртка в `EntityModel` с `_links`.
  - `Pageable` НЕ нужен — у студента фиксированное число предметов.
  - PWA StatsPage переписать на single `useQuery(['stats/my'])` с
    `staleTime: 60_000`.
  - Invalidate cache на событие `attendance.marked` через
    NotificationCenter (QC1) — когда новая отметка, react-query
    invalidates stats.
  **Последствия (каскад):**
  - QC2 openapi-ts — новый endpoint появится в schema, PWA типизируется
    автоматически.
  - Analogous для admin/teacher/headman страниц статистики — тот же
    паттерн aggregate.
  **Estimate:** ~1.5 дня (backend endpoint + SQL + тесты + PWA migrate
  + react-query wiring).
  **NEW:**
  - **NEW-92:** аналогичный audit всех list+detail страниц: если
    делают N+1 запрос — добавить backend aggregate. Scope: teacher
    journal page, admin dashboard.
  - **NEW-93:** react-query invalidation через NotificationCenter
    pattern — документировать в `docs/frontend-architecture.md`.

- **QC7 — Admin-dashboard sparklines = псевдо-данные?**
  **Ответ:** **(a)** Новый endpoint `GET /admin/dashboard/metrics` с
  реальными данными. Sparklines рендерят реальные values.
  **Мотивация:**
  - QA4 уже вводит бизнес-метрики в Prometheus — backend endpoint
    может либо query Prometheus API, либо считать сам.
  - Подмена моком — обман пользователя. Show real data или удалить.
  **Закрывает:** 10 P1-? (sparklines моки).
  **Что делается:**
  - Backend `academic-service` (или новый `analytics-service` в v0.1):
    `GET /api/admin/dashboard/metrics` → `{active_users_7d,
    new_check_ins_24h, red_zone_count, sparklines: {check_ins_by_day:
    [{day, count}, ...], active_users_by_day: [...]}}`.
  - Реализация: либо прямые SQL к attendance_db/academic_db (dashboard
    доступен только admin), либо proxy-читать Prometheus через
    http client (cleaner, но требует наличия QA4 метрик).
  - Frontend web-panel admin-dashboard: sparklines потребляют
    `sparklines.check_ins_by_day`.
  - Refresh interval 30 сек (NotificationCenter может push'ить
    updates при attendance.marked).
  **Последствия (каскад):**
  - QA4 (метрики) — dashboard читает из тех же собираемых данных.
  - QC2 openapi-ts — dashboard DTO типизируется.
  - QC6 (aggregate) — аналогичный паттерн.
  **Estimate:** ~1.5 дня (backend endpoint + SQL/Prometheus query +
  frontend wiring + тесты).
  **NEW:**
  - **NEW-94:** решить при имплементации: SQL-queries против
    attendance_db/academic_db напрямую или через Prometheus. SQL
    проще (нет зависимости от стека QA4), Prometheus — единая
    истина данных. Рекомендую SQL для v0.0.0, миграция на Prometheus
    в v0.1.
  - **NEW-95:** кэш `@Cacheable` на backend-стороне endpoint'а (60
    сек в Redis) — защита от admin'а, который каждую секунду
    открывает dashboard.

---

## P1 — Пачка D (CI/CD) — частично

Ответы на 6 из 7 вопросов (QD7 на уточнении у владельца).
Ответы зафиксированы 2026-04-19.

- **QD1 — `:latest` теги в prod-compose?**
  **Ответ:** **(b)** `IMAGE_TAG=${GITHUB_SHA}` для каждого деплоя +
  semver-теги (v0.0.1, v0.0.2) вручную для релизных точек.
  **Мотивация:**
  - SHA-теги = детерминированный откат на любой commit, бесплатно
    из CI.
  - Semver-теги = человечески-читаемые релизные точки: «откати на
    v0.0.1» звучит лучше чем «откати на 4f3a8b9».
  - Связка с QD7 release-process: semver-теги появляются в формальном
    release-flow, не автоматом.
  **Закрывает:** C1-3, 13 P1-1.
  **Что делается:**
  - В `deploy.yml` build step: `docker build -t ghcr.io/.../auth-service:${{ github.sha }}`.
  - В `docker-compose.prod.yml`: `image: ghcr.io/.../auth-service:${IMAGE_TAG}`.
  - На VPS в `.env.prod`: `IMAGE_TAG=${github_sha}` (пишется CI через
    ssh/scp).
  - Для release: `git tag v0.0.0 && git push --tags` → `deploy.yml`
    также срабатывает на tag push → build'ит и пушит тег в GHCR.
  - Runbook отката (NEW-13): `ssh vps; IMAGE_TAG=<sha> docker compose up -d`.
  **Последствия (каскад):**
  - QD7 (release-process) — semver-теги создаются там.
  - NEW-13 (migration runbook) — дополняется «как откатить» секцией.
  - 13 P0-? (deploy gate) — уже закрыто через C0-8 branch protection.
  **Estimate:** ~3 часа (deploy.yml + compose + .env.prod wiring + smoke).
  **NEW:**
  - **NEW-96:** `docs/operations/runbooks/rollback.md` — пошаговая инструкция
    отката, с примерами команд, проверками health после отката.
  - **NEW-97:** GHCR retention policy — хранить последние 50 SHA-тегов
    + все semver-теги. Остальные удалять через scheduled workflow.
    Иначе registry раздуется до терабайт.

- **QD2 — Coverage-gate в CI?**
  **Ответ:** **(b)** JaCoCo + Vitest + pytest-cov с gate'ами (Java
  60%, TS 50%, Python 50%) + **diff-coverage ≥ 80%** для changed lines.
  **Мотивация:**
  - Total coverage растёт медленно, diff-coverage гарантирует что
    новый код покрывается.
  - Baseline 60/50/50 — нормальный старт, повышать со временем.
  - Диф-коверадж стимулирует TDD/test-first для нового кода без
    обязательства покрывать legacy.
  **Закрывает:** C1-4, 14 P1-3, 14 P1-7 (pytest-cov).
  **Что делается:**
  - Java: gradle `jacocoTestReport` + `jacocoTestCoverageVerification`
    с minimum 60% per-module.
  - GitHub Action `madrapps/jacoco-report` → PR-comment с coverage-diff.
  - TS (PWA + web-panel): `vitest --coverage` +
    `davelosert/vitest-coverage-report-action`.
  - Python: `pytest --cov` + `pytest-coverage-comment` action.
  - Diff-coverage: `diff-cover` (Python tool) поверх coverage-отчётов
    → threshold 80% для changed lines → CI fail'ится если ниже.
  - Baseline set в первом PR после внедрения — текущая coverage
    становится «не хуже».
  **Последствия (каскад):**
  - QC2 (openapi-ts) — generated code excluded из coverage (через
    `--coverage.exclude`).
  - 19e (latecheckin тесты) — первый пакет где gate применяется
    строго (70%).
  - 19f (callback_query тесты) — аналогично для Python.
  **Estimate:** ~2 дня (настройка × 3 языка + PR-template integration
  + baseline в первом PR).
  **NEW:**
  - **NEW-98:** dashboard «coverage trend» в Grafana (если метрики
    coverage экспортировать в Prometheus). Не в v0.0.0, но полезно
    для долгосрочного tracking'а.
  - **NEW-99:** исключить из coverage: generated code, DTO/record'ы
    (getter'ы), main classes. Списки в `.gitignore`-style формате.

- **QD3 — Contract-тесты для всех 14+ RabbitMQ events?**
  **Ответ:** **(a)** Для каждого event-type: unit-тест
  publisher-side (payload соответствует JSON Schema) + consumer-тест
  (event из схемы корректно handle'ится).
  **Мотивация:**
  - Pact-брокер overkill для 2 producer'ов и 2-3 consumer'ов.
  - Schema-validation тест — стандартный pattern, ~10 строк кода
    на event.
  - Связка с QA3 (trace_id в events) + 19a (event_version) —
    все новые events с обязательными полями, тесты гарантируют
    compliance.
  **Закрывает:** C1-5, 14 P1-5, NEW-52 (late-checkin схемы).
  **Что делается:**
  - Список events из `event-schemas/`: `lesson.started`,
    `lesson.closed`, `attendance.marked`, `homework.created`,
    `homework.updated`, `homework.deleted`, `excuse.created`,
    `excuse.approved`, `excuse.rejected`, `late_checkin.requested`,
    `late_checkin.approved`, `late_checkin.rejected`,
    `otp.requested`, `notification.sent`, и др. ~14+.
  - Убедиться что каждый имеет JSON Schema с `event_version`,
    `trace_id`, `occurred_at`, `additionalProperties: false`.
  - Publisher-тест: вызвать service-метод → перехватить payload
    через Mockito spy на RabbitTemplate → validate против schema
    (через `everit-json-schema` или `networknt/json-schema-validator`).
  - Consumer-тест: parse schema-example → call `@RabbitListener`
    handler через test-harness → expect no exception.
  - Centralize в `shared-events` тест-модуль (или shared-web test fixtures):
    `EventContractTestBase<EventT>` с abstract schema path.
  **Последствия (каскад):**
  - QD2 (coverage-gate) — contract-тесты в общем coverage,
    поднимают gate лучше.
  - 08 P1-? (нет additionalProperties: false в схемах) — закрывается
    попутно: тесты упадут если схема без строгости.
  - 08 P1-? (нет versioning) — форсирует добавление `event_version:
    const 1` везде.
  **Estimate:** ~1-1.5 дня (shared-base + 14 тестов по шаблону).
  **NEW:**
  - **NEW-100:** retrofit `event_version`, `trace_id`, `occurred_at`
    во все существующие схемы (связано с NEW-47 и NEW-61) — делать
    одним PR перед QD3-тестами.
  - **NEW-101:** автоматическая проверка в CI «все events в коде
    имеют соответствующий файл в `event-schemas/`» — grep по
    `rabbitTemplate.convertAndSend(..., "lesson.started"` vs файл
    `lesson-started.json`. Drift-guard.

- **QD4 — Base images без digest-пина?**
  **Ответ:** **Гибрид — (a) digest для privileged/socket-containers,
  (b) tag + Renovate для обычных.**
  **Мотивация:**
  - `cadvisor` (privileged: true) + `promtail` (docker.sock mount) —
    supply-chain compromise даёт full host access. Digest обязателен.
  - Обычные сервисы (loki, prometheus, grafana, tempo, rabbitmq) —
    tag + Renovate проще в поддержке, достаточный level trust'а.
  - Renovate auto-PR'ит обновления digest'ов — ручная работа минимальна.
  **Закрывает:** C1-6, 13 P1-10.
  **Что делается:**
  - **Digest-пин для:**
    - `gcr.io/cadvisor/cadvisor@sha256:...`
    - `grafana/promtail@sha256:...`
  - **Tag + Renovate для:**
    - `grafana/loki:2.9.5`
    - `grafana/tempo:2.3` (из QA2)
    - `grafana/grafana:10.4`
    - `prom/prometheus:v2.50`
    - `prom/alertmanager:v0.27` (из QA4)
    - `rabbitmq:3.13-management`
    - `postgres:16.4`
    - `redis:7.4-alpine`
    - `mongo:7.0`
  - `renovate.json` в корне репо — расписание (ежедневные PR для
    patch, еженедельные для minor), auto-merge patch после CI green.
  - Docker base images для собственных сервисов (eclipse-temurin, python) —
    также tag-pin с Renovate.
  **Последствия (каскад):**
  - QD6 (Renovate) — часть этого же конфига.
  - QD5 (trivy) — scan базовых образов каждый build → CVE в любой
    версии детектируется.
  - Tempo из QA2 — новый container, применяется тот же принцип.
  **Estimate:** ~3 часа (digest-пиннинг × 2 + Renovate config + docs).
  **NEW:**
  - **NEW-102:** `docs/operations/deploy/container-trust.md` — политика «что
    требует digest, что tag», обоснование. Защита от будущих
    «добавил новый privileged container без digest».

- **QD5 — Supply-chain scan?**
  **Ответ:** **(a)** Trivy (CVE + container scan) + Gitleaks
  (secrets, pre-commit + CI) + Dependabot (auto-PR для security updates).
  **Мотивация:**
  - Trivy open-source, отлично интегрируется с GitHub Actions.
  - Gitleaks как pre-commit + CI = двойной слой защиты от случайной
    утечки `.env`.
  - Dependabot для security-only updates (Renovate для обычных —
    QD6) — разделение concerns.
  **Закрывает:** supply-chain risk, частично 13 P0-3 (`.env.prod`
  leak guard).
  **Что делается:**
  - `.github/workflows/security.yml`:
    - `aquasecurity/trivy-action` — scan репо + Docker images,
      fail on HIGH/CRITICAL CVE.
    - `gitleaks/gitleaks-action` — scan commits + open PRs.
    - Weekly-cron trivy scan образов в registry (свежие CVE могут
      появиться после build'а).
  - `.pre-commit-config.yaml` с `gitleaks` hook — ловит секреты до
    push'а. Установить git pre-commit framework разово.
  - Dependabot через `.github/dependabot.yml`: gradle, npm (× 3
    фронта), pip (бот), docker, github-actions.
  - SECURITY.md — куда сообщать об уязвимостях (email / Telegram admin).
  **Последствия (каскад):**
  - QD4 (digest pin) — trivy подскажет когда надо обновить digest.
  - QD6 (Renovate) — Dependabot для security, Renovate для
    обычных — оба активны, дополняют.
  - NEW-9 (rate-limit fallback) — никакого прямого влияния.
  **Estimate:** ~1 день (workflows setup + pre-commit + dependabot
  config + тестирование что alerts реально приходят).
  **NEW:**
  - **NEW-103:** `SECURITY.md` с ответственным раскрытием (email
    или Telegram contact). Обязательно для публичных репо.
  - **NEW-104:** ответственное раскрытие после v0.0.0 — подумать,
    хочешь ли ты принимать bug-reports от внешних. Если да — нужна
    disclosure policy.

- **QD6 — Renovate для автоматического bump?**
  **Ответ:** **(a)** Renovate с `automerge: true` для patch-версий,
  manual review для minor/major. Бесплатный Renovate GitHub app.
  **Мотивация:**
  - Renovate гибче чем Dependabot: groupings (все Spring Boot одним
    PR), schedules (ночью), auto-merge rules.
  - Auto-merge patch снижает overhead: 90% bump'ов — безопасные
    patches, их не читать.
  - Minor/major — осознанная проверка, не теряется дисциплина.
  **Закрывает:** C1-? (общая freshness зависимостей).
  **Что делается:**
  - Установить GitHub App «Renovate» → выдать доступ к репо.
  - `renovate.json`:
    ```json
    {
      "extends": ["config:recommended"],
      "packageRules": [
        {"matchUpdateTypes": ["patch"], "automerge": true, "automergeType": "pr"},
        {"matchUpdateTypes": ["minor","major"], "automerge": false},
        {"matchPackagePatterns": ["spring-boot"], "groupName": "Spring Boot"},
        {"matchPackagePatterns": ["angular"], "groupName": "Angular"}
      ],
      "schedule": ["after 22:00 every weekday", "every weekend"],
      "timezone": "Europe/Moscow"
    }
    ```
  - Интеграция с CI: auto-merge только после зелёного CI (QD2
    coverage gate, QD5 trivy scan).
  - Dashboard issue (Renovate создаёт) — видно все pending updates.
  **Последствия (каскад):**
  - QD4 digest-пиннинг — Renovate автоматически bump'ает digest'ы.
  - QD5 Dependabot — работает параллельно для security-updates,
    Renovate игнорирует то что Dependabot уже обработал (через
    PR-presence check).
  - NEW-28 (ShedLock audit) — Renovate bump версии ShedLock сам.
  **Estimate:** ~3 часа (GitHub App install + renovate.json + первый
  PR-test + документация in `docs/operations/deploy/ci-cd.md`).
  **NEW:**
  - **NEW-105:** `docs/operations/deploy/ci-cd.md` с описанием всей цепочки: GitHub
    Actions, Renovate, Dependabot, Trivy, deploy flow. Для будущих
    разработчиков.

- **QD7 — Release process без формализации?**
  **Ответ (2026-04-19):** **(b)** Manual: `CHANGELOG.md` + git-теги
  вручную перед merge релизного PR'а. GitHub Release через UI.
  **Мотивация:**
  - Один разработчик → Conventional Commits / semantic-release —
    overhead без compensating-value.
  - 10 минут раз в 1-2 месяца = честная цена за явную release-дисциплину.
  - Ручной проход по commits перед релизом = sanity-check «не забыл
    ли обновить CLAUDE.md / closed TODO / docs».
  - Переход на автоматизацию (a) Semantic-release — в v0.1+ если
    накопится усталость от ручного заполнения или появится 2+ разработчик.
  **Закрывает:** отсутствие формализованного релиз-процесса.
  **Что делается:**
  - Создать `CHANGELOG.md` в корне репо (формат Keep a Changelog):
    ```md
    # Changelog
    ## [Unreleased]
    ### Added
    ### Changed
    ### Fixed
    ### Breaking
    ## [v0.0.0] - 2026-04-XX
    (ретроспективно заполнить из текущей работы)
    ```
  - В `docs/release-v0.0.0-runbook.md` (NEW-13) добавить чеклист:
    1. Все фиксы v0.0.0 помёржены.
    2. `CHANGELOG.md` → перенести `[Unreleased]` в `[vX.Y.Z]` с датой.
    3. Коммит `chore(release): vX.Y.Z`.
    4. `git tag vX.Y.Z && git push --tags`.
    5. GitHub → Releases → «Create release from tag» → скопировать
       CHANGELOG-раздел.
    6. Опционально: bot-алерт «вышла vX.Y.Z» в Telegram.
  - `deploy.yml` триггерится по `push: main` (auto-deploy каждого merge)
    **+ по `push: tags v*`** (semver-тег создаёт GHCR-образ с тем же
    тегом — фиксированная точка для отката).
  - В PR-template (NEW-74) добавить строчку «☐ Обновил CHANGELOG.md
    → [Unreleased]».
  **Последствия (каскад):**
  - QD1 (semver-теги) — создаются именно в этом процессе.
  - QD2/QD5 CI gates должны быть green перед релизом.
  - NEW-13 (migration runbook) — часть release checklist'а.
  **Estimate:** ~2 часа (CHANGELOG baseline + runbook + PR-template
  update + retroactive [v0.0.0] секция).
  **NEW:**
  - **NEW-106:** в будущем (v0.1+) оценить миграцию на semantic-release
    — если commits начнут следовать conventional-format естественно
    (иногда при autocomplete'е AI-ассистенты такой формат предлагают).
    Триггер в `docs/future-ideas.md` под «DevEx».
  - **NEW-107:** retroactive `[v0.0.0]` entries — подготовить при
    выкатке релиза, используя `git log main` + материал этого
    аудита (все TO-FIX кластеры/P0 из OWNER-ANSWERS.md, которые
    будут реализованы в v0.0.0).

---

## P1 — Пачка E (Remaining infra)

Ответы на 5 оставшихся кросс-сервисных P1. Ответы зафиксированы
2026-04-19.

- **QE1 — Процесс ревизии лендинга при изменении бизнес-логики?**
  **Ответ:** **(a)** Чеклист в PR-template (NEW-74): «если PR меняет
  пользовательское поведение → поставить label `landing-review` →
  после merge создать follow-up issue для правки лендинга».
  **Мотивация:**
  - PR-template уже создаётся (NEW-74), добавить одну строку бесплатно.
  - CODEOWNERS overkill для одного разработчика.
  - Ревизия раз в месяц вручную забывается.
  **Закрывает:** C1-8, 12 P1-6 (excuse на лендинге по старому flow).
  **Что делается:**
  - В `.github/pull_request_template.md` (NEW-74) пункт:
    `☐ Этот PR меняет пользовательское поведение? Если да — поставить
    label \`landing-review\` и создать follow-up issue.`
  - Labels `landing-review` и `docs-review` в репо.
  - В `docs/contributing.md` (если нет — создать) — раздел «Когда
    обновлять лендинг»: smoke-критерии («видит ли студент новое
    поведение? новая кнопка? изменился flow?»).
  - Разовая ретрофит-правка лендинга: excuse-тикеты (старый flow) →
    новый backend-flow.
  **Последствия (каскад):**
  - NEW-74 PR-template расширяется.
  - 12 P1-6 → 🔧 TO-FIX (разовая правка + процесс на будущее).
  **Estimate:** ~4 часа (PR-template + labels + docs + разовая
  переработка excuse-секции лендинга).
  **NEW:**
  - **NEW-108:** `docs/contributing.md` с разделом «Когда обновлять
    лендинг / CLAUDE.md / docs».

- **QE2 — ShedLock в academic/attendance?**
  **Ответ:** **(b)** ArchUnit custom-правило: любой `@Scheduled`
  метод должен иметь `@SchedulerLock` либо явно помечен
  `@SingleInstanceOnly` (с javadoc-объяснением). CI-fail при
  нарушении.
  **Мотивация:**
  - (a) одноразовый аудит — regression через полгода.
  - (c) tech-debt накапливается.
  - ArchUnit — 10-15 строк кода, защищает вечно. Уже применяется
    в Spring-сообществе.
  **Закрывает:** NEW-28, C1-7 расширение на academic/attendance.
  **Что делается:**
  - `@SingleInstanceOnly` — новая маркер-аннотация в shared-web
    (из 16a).
  - `ArchitectureTest.java` в каждом сервис-модуле:
    ```java
    @Test
    void scheduledMustHaveLock() {
      methods().that().areAnnotatedWith(Scheduled.class)
        .should().beAnnotatedWith(SchedulerLock.class)
        .orShould().beAnnotatedWith(SingleInstanceOnly.class)
        .check(CLASSES);
    }
    ```
  - Аудит существующих `@Scheduled` — каждый получает одну из
    двух аннотаций.
  - Outbox publisher-job из C0-3 — обязательно `@SchedulerLock`.
  **Последствия (каскад):**
  - C1-7 (ShedLock) расширяется на все сервисы.
  - C0-3 (outbox) — prerequisite закрыт.
  - NEW-28 audit из 14b — покрывается автоматом через ArchUnit.
  **Estimate:** ~4 часа (аннотация + ArchUnit-тест × 3 сервиса +
  разовый аудит).
  **NEW:**
  - **NEW-109:** ArchUnit framework для других правил (no-cyclic-deps,
    controllers-implement-contract, records-not-classes-for-request-DTO).
    Добавлять по мере нужды, не в v0.0.0.

- **QE3 — Лендинг: SMIL-анимация не уважает `prefers-reduced-motion`?**
  **Ответ:** **(a)** CSS media query `@media (prefers-reduced-motion:
  reduce)` + JS `element.pauseAnimations()` для SMIL.
  **Мотивация:**
  - A11y-improvement почти бесплатный.
  - ~10 строк CSS + 5 строк JS.
  - Соответствует WCAG 2.3.3 (Animation from Interactions).
  **Закрывает:** 12 P1-? (SMIL + reduced-motion).
  **Что делается:**
  - CSS в `frontends/landing/styles/hero.css`:
    ```css
    @media (prefers-reduced-motion: reduce) {
      .hero-animation, .hero-animation * {
        animation-duration: 0.001ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.001ms !important;
      }
    }
    ```
  - JS для SMIL-тегов:
    ```js
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      document.querySelectorAll('svg').forEach(svg => svg.pauseAnimations?.());
    }
    ```
  - Ещё лучше: DOM `matchMedia().addEventListener('change', ...)` —
    реагировать на смену preference без reload.
  - Smoke-тест в DevTools: Emulate CSS `prefers-reduced-motion` →
    анимации замирают.
  **Estimate:** ~1-1.5 часа (CSS + JS + smoke-тест + в 3 разных
  браузерах).
  **NEW:**
  - **NEW-110:** lint-check CSS lint'ером (stylelint + plugin
    a11y-rules) на обязательность reduced-motion media query в
    файлах с `animation:`. Не блокирующее, в v0.1.

- **QE4 — Лендинг: нет `og:image`, `twitter:card`, `canonical`, `robots`?**
  **Ответ:** **(a)** Полный набор meta-тегов в `<head>` лендинга.
  **Мотивация:**
  - При шаринге в Telegram/Twitter/Discord без og — серая карточка,
    видно «ruttrack.site», не видно «что это».
  - `canonical` предотвращает дубликаты (если появится mirror/staging).
  - `robots=index,follow` даёт зелёный свет поисковикам.
  - JSON-LD (b) — v0.1 SEO-bonus, не блокер.
  **Закрывает:** 12 P1-? (отсутствие meta-тегов).
  **Что делается:**
  - Создать `frontends/landing/assets/og/ruttrack-og-1200x630.png` —
    брендированное изображение (скриншот + логотип + слоган).
  - В `<head>` лендинга:
    ```html
    <title>RutTrack — посещаемость РУТ МИИТ</title>
    <meta name="description" content="Геоотметка, расписание,
      excuse-тикеты. Прозрачная посещаемость для студентов, преподавателей,
      старост.">
    <link rel="canonical" href="https://ruttrack.site/presentation/">
    <meta name="robots" content="index,follow">
    <meta property="og:type" content="website">
    <meta property="og:url" content="https://ruttrack.site/presentation/">
    <meta property="og:title" content="RutTrack — посещаемость РУТ МИИТ">
    <meta property="og:description" content="...">
    <meta property="og:image" content="https://ruttrack.site/presentation/assets/og/ruttrack-og-1200x630.png">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="RutTrack">
    <meta name="twitter:image" content="...">
    ```
  - Smoke-тест через:
    - Telegram: отправить ссылку себе → увидеть preview-карточку.
    - Twitter Card Validator: https://cards-dev.twitter.com/validator.
    - Facebook Debugger: https://developers.facebook.com/tools/debug/.
  **Estimate:** ~3 часа (дизайн og-картинки + мета-теги + валидация
  в 3 проверялках).
  **NEW:**
  - **NEW-111:** JSON-LD structured data (Organization, WebSite) —
    v0.1+ SEO-bonus. В `docs/future-ideas.md`.
  - **NEW-112:** при смене дизайна лендинга — обновить og-картинку.
    Включить в NEW-108 контрибьютинг-гайд.

- **QE5 — JWT и другие секреты в логах?**
  **Ответ:** **(a) + (b) частично** — в Gateway исключить `Authorization`
  header и `token`/`ticket`/`code` query params из access-log
  фильтром + разовый audit всех существующих `log.X(...)` вызовов
  в проекте на предмет утечки чувствительных данных.
  **Мотивация:**
  - После C0-7 (cookie-based auth) JWT будет в cookie, не в query,
    но старые клиенты до миграции будут слать `?token=`.
  - Sanitization-wrapper (обёртка вокруг `log.info`) — over-engineering,
    легко забыть использовать, frustrates dev-flow.
  - Audit существующих вызовов — разовая работа, закрывает текущие
    дыры без инфраструктурных изменений.
  **Закрывает:** QA1 дополнение, C1-10 (DEBUG-логи с JWT).
  **Что делается:**
  - **Gateway (в `application.yml` Spring Cloud Gateway):**
    - Access-log формат: `%r` содержит URI с query. Заменить на
      sanitized версию через `AccessLogAdapter` (filter) или
      изменить log-pattern чтобы не писать query string.
    - Альтернатива: Logback `PatternLayout` с conversion rule,
      маскирующим `token=*`, `ticket=*`, `code=*` → `token=***`.
  - **Audit существующих `log.X(...)`:**
    - `Grep -rn "log.\(debug\|info\|warn\|error\)" services/*/src/main/java`
    - Для каждого найденного: проверить что в аргументах нет
      пароля, JWT, OTP-кода, phone, telegram_id без маскировки.
    - Чувствительные — заменить на маскированные версии
      (`user.phone.substring(0,3) + "***"`).
  - В `docs/security-model.md` — раздел «Logging hygiene»: список
    запрещённых к логированию полей (password, JWT, OTP code,
    initial_password, VAPID private key).
  **Последствия (каскад):**
  - QA1 (INFO-default) закрывает 80%, этот фикс — оставшиеся 20%.
  - QA7 (structured JSON) упрощает grep по secret-patterns в Loki:
    alerta «пароль в логах» становится возможна.
  - NEW (связан): alert-rule в Loki/Grafana «если в логах появилась
    строка match'ящая pattern password/token/code=<value>» →
    немедленный алерт админу. Не в v0.0.0, v0.1.
  **Estimate:** ~3-4 часа (Gateway filter + audit + docs).
  **NEW:**
  - **NEW-113:** Loki-alert на утечку секретов в логах (v0.1).
  - **NEW-114:** в `docs/security-model.md` раздел «Logging hygiene».

---

## P2 — Группа 11 (Event schemas & proto)

Ответы на 8 вопросов из отчёта 08. Зафиксированы 2026-04-19.

- **P2-11/1 — proto3 optional для `group_id`:** **(a)** `optional int64
  group_id = 6;` в `academic.proto:121,151`. В Java-коде замена
  `getGroupId() != 0` → `hasGroupId()` / `getGroupId()`. Teacher/admin
  получают «не установлено», не «0».
  - **Что делается:** правка `.proto` × 2 места, регенерация Java-stub'ов,
    поиск всех `.getGroupId()` в коде → заменить на `.hasGroupId()` guard.
  - **Estimate:** ~2 часа.
  - **NEW-115:** аналогичный audit для других primitive-полей где 0
    может быть валидным значением (`user_id`, `lesson_id`, `subject_id`) —
    но там 0 реально означает «нет», так что optional не нужен.

- **P2-11/2 — `GroupResponse` без semester_id:** **(c)** добавить
  `optional int64 current_semester_id = X;` в `GroupResponse`.
  Backward-compat, consumer знает текущий активный семестр группы.
  - **Мотивация:** groups долгоживущие (через семестры), но consumer
    почти всегда хочет контекст «в каком семестре она сейчас». Optional
    field не ломает существующих consumers.
  - **Estimate:** ~1 час (proto + backend logic + tests).
  - **NEW-116:** backend logic расчёта `current_semester_id` — из
    таблицы `semesters` WHERE status='active' AND group_id=? (если
    много-семестровая связь) или просто active semester (если одна
    активная). Уточнить при имплементации.

- **P2-11/3 — `StudentInfo.display_name` = полное ФИО:** **(a)** разбить
  на `last_name`, `first_name`, `middle_name_opt` + вычисляемое
  `display_name_short = "Иванов И.И."`. Consumer сам решает что
  показывать.
  - **Каскад:** косвенно улучшает 09-Q6 (push-имена) — push может
    использовать short-form при желании.
  - **Breaking change:** consumers, использующие `display_name`
    напрямую, должны переключиться. QC2 (openapi-ts) поймает.
  - **Estimate:** ~3 часа (proto + backend генератор + миграция
    consumers + тесты).
  - **NEW-117:** формат `display_name_short` — «Иванов И.И.» или
    «И. Иванов»? Зафиксировать в `docs/design-decisions.md` как
    конвенцию.

- **P2-11/4 — `HeadmanCheckRequest(user_id, group_id)`:** **(a)**
  добавить комментарий в `.proto` объясняющий cross-group защиту.
  Оставить API как есть.
  - **Что делается:** одна строка в `academic.proto:88-91`:
    `// group_id ensures caller verifies user is headman of THIS group
    (cross-group check); do NOT drop for API cleanup without reading
    docs/security-model.md`.
  - **Estimate:** ~10 минут.

- **P2-11/5 — `lesson.deleted` без snapshot:** **(a) + (b)** — перейти
  на soft-delete lesson (status='cancelled'), переименовать событие
  в `lesson.cancelled` с полным snapshot'ом. `lesson.deleted` event
  удаляется как несуществующий concept.
  - **Каскад:**
    - QB5 (запрет change lesson после старта) — уже предполагает
      cancel вместо delete. Эта P2 закрывает related schema issue.
    - NEW-77 (admin-override для жёсткого delete) — остаётся как
      edge-case для v0.1. В v0.0.0 hard-delete lesson отсутствует.
    - NEW-80/81 (unarchive users) — аналогичный soft-delete паттерн.
  - **Что делается:**
    - Миграция `V{N}__lesson_status_cancelled.sql`: `ALTER TABLE
      lessons ADD CONSTRAINT ... CHECK (status IN ('planned','in_progress',
      'closed','cancelled'))`. Column уже есть из v4.0.
    - Service: `LessonService.delete(id)` → deprecate, внутри вызывает
      `cancel(id, reason)`.
    - Event: `lesson.cancelled.json` схема с полным snapshot
      (lesson_id, group_id, subject_id, date, start_time, end_time,
      lesson_number, reason, cancelled_at, cancelled_by).
    - Удалить `event-schemas/lesson.deleted.json` + publisher/consumer
      кода.
    - Consumers (attendance, notification): переключаются на
      `lesson.cancelled` handler.
  - **Estimate:** ~1 день (migration + service + event + consumers + tests).
  - **NEW-118:** `docs/architecture/architecture.md` — раздел «Lesson lifecycle»
    с диаграммой status-transitions (planned → in_progress → closed,
    planned/in_progress → cancelled).
  - **NEW-119:** UI в admin/teacher (web-panel) — замена кнопки
    «удалить» на «отменить с причиной» (связь с QC4 ConfirmWithReasonDialog).

- **P2-11/6 — `lesson.closed` без snapshot group_members:** **(b)
  Accept** — gRPC roundtrip дешёвый при текущей нагрузке. Overhead
  события при группе 30+ студентов начинает быть заметным, экономия
  микросекунд не оправдана. Если нагрузка вырастет — вариант (c)
  кэш group_members с TTL — простой миграционный путь.
  - **Действия:** документировать выбор в `docs/architecture/architecture.md`
    (раздел «Event payload philosophy»): «в событиях несём минимум,
    дополнительные детали consumer запрашивает через gRPC; cache
    добавляем когда измерим hotspot».

- **P2-11/7 — `lesson_number maximum:8` в 6 схемах:** **(b)** Вынести
  в общий JSON Schema `$defs` в master-схеме. Все 6 lesson-схем
  reference'ят через `$ref`.
  - **Каскад:** заодно вынести общие поля (`event_version`, `trace_id`,
    `occurred_at`) в те же `$defs` — связка с QA3, NEW-100, NEW-61.
  - **Что делается:**
    - Создать `event-schemas/_common.json` с `$defs`:
      `lessonNumber` (integer min:1, без max), `traceId` (string),
      `eventVersion` (integer const:1), `occurredAt` (date-time).
    - Все schema-файлы reference'ят: `{"$ref": "_common.json#/$defs/lessonNumber"}`.
    - Json Schema validator (Everit / networknt) поддерживает $ref
      через HTTP или file-loader.
  - **Estimate:** ~1 день (common schema + миграция всех 14+ schemas +
    test что validator-loader работает).
  - **NEW-120:** `event-schemas/_common.json` + раздел в
    `docs/architecture/event-schemas.md` (NEW-48) объясняющий shared definitions.

- **P2-11/8 — Отсутствие `excuse.decision` event:** **(a)+(c)** —
  добавить `excuse.decision.json` + мигрировать excuse flow на
  event-based (симметрично `late_checkin.approved/rejected`).
  - **Мотивация:** симметрия flow упрощает обучение/дебаг. При фиксе
    06 P0-5 (callback'и без проверки роли) и 19f (bot тесты) —
    унифицированный шаблон защиты и тестирования.
  - **Что делается:**
    - Схемы `excuse.approved.json`, `excuse.rejected.json` с полным
      snapshot (excuse_id, student_id, group_id, lesson_ids,
      decided_by, reason, decided_at).
    - Bot handler (вместо REST call): публикует event через
      aio-pika → academic consumer обновляет БД.
    - Если currently есть REST excuse-decision endpoint — deprecate
      и удалить после миграции.
    - Contract-тесты (QD3 pattern).
  - **Каскад:**
    - 06 P0-5 (callback без role check) — фикс = одна проверка
      перед публикацией event.
    - 19f (bot pytest) — тестирует правильную публикацию event.
    - 5 P0-? (если notification-service отдельно обрабатывает
      decision) — тоже переключается на event.
  - **Estimate:** ~1 день (2 схемы + bot migration + consumer +
    contract-tests + remove REST if exists).
  - **NEW-121:** audit всех других asymmetric flow — bot публикует
    через REST или через event? Унифицировать. Вероятно `prefs.*`
    тоже. Разовый аудит.

---

## P2 — Группа 2 (OpenAPI аннотации)

Ответы на 8 вопросов из отчётов 01/02/03/04/05/07/13. Prerequisite для
QC2 type-gen (`openapi-typescript` + `openapi-fetch`). Зафиксированы
2026-04-19.

- **P2-2/1 — RFC 7807 `ErrorResponse` в OpenAPI всех сервисов:** **(a)**
  Глобальный `OpenApiCustomizer` в `shared-web` (из 16a) автоматом
  добавляет `@ApiResponse` 400/401/403/404/409/429/500 с
  `content=@Content(mediaType="application/problem+json",
  schema=@Schema(implementation=ErrorResponse.class))` ко всем operations.
  - **Мотивация:**
    - Контракты остаются чистыми: `@ApiResponse(responseCode="200",
      description="...")` + `summary` без boilerplate.
    - Нет drift'а между сервисами — единый formatter.
    - После QC2 type-gen (openapi-typescript) фронт получает типизированный
      `ErrorResponse` для всех 4xx/5xx автоматически, не руками описывать
      × 80 endpoints.
  - **Что делается:**
    - В `shared/shared-web/` (из Q16a) — новый класс
      `GlobalErrorResponsesCustomizer implements OpenApiCustomizer`.
    - Для каждой Operation без явного 4xx/5xx — добавить стандартные
      ответы с ссылкой на `ErrorResponse` schema (тоже из shared-web).
    - Если контракт уже явно декларирует (например, `@ApiResponse(400,
      description="invalid otp code")`) — Customizer не перезаписывает,
      только дополняет.
    - `ErrorResponse` schema регистрируется один раз в `components.schemas`.
    - Integration-тест: запрос к несуществующему endpoint → OpenAPI-spec
      содержит 404 с RFC 7807 schema в applicable operations.
  - **Каскад:**
    - Q16a (shared-web) расширяется: не только handler, но и OpenAPI
      customizer.
    - QC2 (openapi-typescript) — генерирует полностью типизированный
      `ProblemDetails` type. QC3 error-interceptor парсит через этот type.
    - P2-2/5 (ErrorResponse example rassync) — решается внутри этой же
      задачи.
  - **Estimate:** ~3 часа (customizer + integration-тест + smoke в Swagger-UI).
  - **NEW-122:** документировать `OpenApiCustomizer` pattern в
    `docs/frontend-architecture.md` (NEW-86) — как фронт работает с
    generated error types.

- **P2-2/2 — Auth-service OpenAPI в `AuthApi` interface:** **(a)** Все
  `@Operation`/`@ApiResponse`/`@Schema` переезжают в `AuthApi` interface
  из `auth-api-contract` (создаётся в 01 P0-1). `AuthController` только
  `implements AuthApi`, без аннотаций.
  - **Мотивация:**
    - Консистентно с academic/schedule/attendance (CLAUDE.md D-11/D-12).
    - Фронт после QC2 использует один generated-client для всех 5 сервисов,
      auth — не исключение.
  - **Что делается:**
    - В 01 P0-1 (создание `auth-api-contract`) — интерфейс `AuthApi` с
      аннотациями перенесёнными из `AuthController.java:39-124`.
    - `AuthController implements AuthApi`, в контроллере только бизнес-вызовы.
    - P2-2/1 customizer автоматом добавит RFC 7807 ответы — не дублировать
      `@ApiResponse(401,...)` в контракте (кроме случаев где 401 имеет
      специфический description: «Invalid credentials» vs «Invalid OTP»).
  - **Каскад:**
    - 01 P0-1 → этот пункт его дополняет (но не изменяет объём, аннотации
      всё равно копируются).
  - **Estimate:** ~1 час (входит в 01 P0-1 ~1 день).

- **P2-2/3 — Рассинхрон `@ApiResponse` с runtime:** **(a) + (c)** —
  правим контроллеры под контракт (204 для безответных мутаций, REST
  convention) + CI-тест «OpenAPI spec соответствует runtime».
  - **Мотивация:**
    - 204 — стандарт REST для empty-body success. `changePassword` (01 P2-11)
      и `markComplete` (02 P1-5) должны быть 204.
    - Без CI-теста drift вернётся через полгода (автор поправил контроллер,
      забыл контракт).
    - QC2 (openapi-fetch) типизирует response по декларации — runtime drift
      = рантайм-бага на фронте.
  - **Что делается:**
    - **(a)** Runtime:
      - `AuthController.changePassword` → `.noContent().build()` вместо
        `.ok().build()`.
      - `HomeworkController.markComplete` → `.noContent().build()`.
      - Audit всех `ResponseEntity.ok().build()` с пустым body → если
        контракт декларирует 204, править на `.noContent()`. Если
        наоборот — править контракт (редко).
    - **(c)** CI-check:
      - Использовать [`swagger-request-validator`](https://bitbucket.org/atlassian/swagger-request-validator/)
        + RestAssured/MockMvc в integration-тесте: каждый endpoint
        проверяется против generated OpenAPI-spec. Mismatch → fail.
      - Либо упрощение — integration-test поднимает приложение, дёргает
        все endpoints, сравнивает actual status code с `@ApiResponse`
        в контракте через reflection.
      - Первый вариант надёжнее, второй быстрее имплементить.
  - **Каскад:**
    - QD2 (coverage gate) — этот CI-тест идёт в ту же coverage-сборку.
    - QC2 (openapi-ts) — генерированные типы для клиентов корректны.
    - 01 P2-11, 02 P1-5 закрываются этим же PR'ом.
  - **Estimate:** ~1-1.5 дня (runtime-правки + CI-test harness + audit
    всех endpoints).
  - **NEW-123:** CI-job «openapi-runtime-conformance» — использует
    swagger-request-validator. Добавить в `.github/workflows/ci.yml`
    после setup'а QC2 type-gen (порядок: 01 P0-1 auth-contract → 16a
    shared-web → P2-2/1 customizer → P2-2/3 conformance-test).

- **P2-2/4 — `@Schema(description, example)` на DTO полях:** **(a)**
  Полный проход по всем контрактным DTO — добавить `@Schema(description,
  example)` ко всем полям.
  - **Мотивация:**
    - После QC2 type-gen Swagger-UI остаётся основным каналом документации
      API для будущих разработчиков.
    - Описания (description) транслируются в JSDoc в generated TS-типах —
      hover в IDE показывает смысл поля.
    - Примеры (example) попадают в try-it-out Swagger-UI — разработчик
      тестирует API без guess'инга валидных значений.
    - Единицы измерения (01 P2-10: `expiresIn` — секунды), форматы дат,
      enum-values — критичны для корректной интеграции.
  - **Scope:**
    - Все DTO в `*-api-contract` модулях: request-records + response-classes.
    - Поля: id, имена, даты, enum, числовые с единицами, opaque tokens.
    - Пример: `@Schema(description="Время жизни access-токена в секундах",
      example="900") long expiresIn;`.
    - Для enum-полей: `@Schema(allowableValues={...})` или опираться на
      Jackson serialization (springdoc подхватывает).
  - **Что делается:**
    - Audit всех `*-api-contract/src/main/java/**/dto/**` — создать
      чеклист DTO (~60-80 штук).
    - Проходить по одному, при PR — поля без `@Schema` CI-предупреждение
      (не fail).
    - Отдельный PR или серия небольших — не блокирует другие фиксы.
  - **Каскад:**
    - QC2 openapi-ts — richer generated types с JSDoc.
    - P2-2/1 customizer не трогает DTO schemas, они отдельно.
  - **Estimate:** ~1 человеко-день (~60-80 DTO × ~1 минута на поле).
  - **NEW-124:** CI-lint «все public поля в DTO контрактных модулях
    должны иметь `@Schema(description)`» — soft warning, не блокер.
    ArchUnit-правило или Checkstyle. Защищает от будущих DTO без schema.

- **P2-2/5 — `ErrorResponse example` рассинхрон (03 P2-12):** **(a)**
  Убрать конкретные `@Schema(example=...)` из `ErrorResponse` в shared-web.
  Generic placeholder: `"example": "see errors array"`. Конкретные
  сообщения приходят из `MethodArgumentNotValidException` в runtime.
  - **Мотивация:**
    - Примеры в annotations неизбежно drift'ят — валидации меняются,
      примеры забываются.
    - Real validation messages генерируются из `@Min/@Max/@NotNull/...`
      Jakarta Validation, они точны на runtime.
    - Generic placeholder честнее: «смотри runtime-ответ, там реальные
      сообщения».
  - **Что делается:**
    - В shared-web `ErrorResponse` record — `@Schema(example="Validation
      failed: see errors[] for details")` на `message`.
    - `errors[]` — `@Schema(example=[{\"field\":\"...\",
      \"message\":\"...\"}])` generic.
    - Старые конкретные примеры в `schedule-api-contract/exception/ErrorResponse.java:44-49`
      — удаляются (ErrorResponse переезжает в shared-web, старый класс
      deprecate → remove).
  - **Каскад:**
    - Q16a (shared-web) выполняет эту задачу автоматом (ErrorResponse
      туда переезжает). Фактически P2-2/5 — следствие, отдельного
      estimate нет.
    - 03 P2-12 → ✅ AUTO-RESOLVED через Q16a + P2-2/5.
  - **Estimate:** ~0 (входит в Q16a).

- **P2-2/6 — Springdoc в проде: /swagger-ui/** и /v3/api-docs:** **(b)**
  Basic-auth через nginx с `SWAGGER_PASSWORD` из `.env.prod`. Аналогично
  Grafana — владелец смотрит документацию с телефона онлайн, важно иметь
  защищённый доступ.
  - **Мотивация:**
    - Владелец явно использует Swagger online с мобильного для дебага/ревью.
    - Grafana уже работает по той же схеме (basic-auth nginx) — консистентно.
    - (a) отключение в проде — ломает use-case владельца.
    - (c) Spring Security — дублирует nginx, усложняет gateway.
  - **Что делается:**
    - В `nginx/conf.d/default.conf:91` уже есть `auth_basic "..."; auth_basic_user_file
      /etc/nginx/.htpasswd;` для `/swagger-ui/` и `/v3/api-docs` locations.
      Проверить что location блоки покрывают оба URL + gateway aggregator.
    - Создать `.htpasswd` при деплое:
      - В `deploy.yml` новый шаг: `htpasswd -bc /opt/rutcampustrack/nginx/.htpasswd
        admin "$SWAGGER_PASSWORD"` перед `docker compose up`.
      - `SWAGGER_PASSWORD` уже в `.env.prod` (13-Q9 подтверждает).
      - Монтировать `.htpasswd` в nginx-контейнер volume mount'ом
        (проверить docker-compose.prod.yml).
    - В `.env.prod.example` (NEW-20) явно: `SWAGGER_PASSWORD=` с
      комментарием «basic-auth для /swagger-ui и /v3/api-docs, logins
      admin:<пароль>».
  - **Каскад:**
    - 07 P2-4 → 🔧 TO-FIX через nginx basic-auth (не через springdoc disable).
    - 13-Q9 (`.htpasswd` отсутствует) → 🔧 TO-FIX: deploy-скрипт генерирует
      из `SWAGGER_PASSWORD` при старте.
    - NEW-20 (`.env.prod.example`) → дополняется `SWAGGER_PASSWORD`.
    - NEW-96 (rollback.md) → добавить пункт «`.htpasswd` регенерируется
      при деплое — rollback'ом не перетирается».
    - QD5 gitleaks — `.htpasswd` НЕ коммитится (генерируется на VPS),
      safe.
    - Supply-chain: basic-auth защищает ровно от drive-by scraping, не
      от целенаправленной атаки. Для pet-проекта достаточно.
  - **Estimate:** ~2 часа (deploy-скрипт + nginx volume + .env.prod.example
    + smoke из браузера).
  - **NEW-125:** задокументировать в `docs/architecture/architecture.md` или новом
    `docs/admin-access.md` — что защищено basic-auth (swagger, grafana,
    prometheus?), какие credentials, где меняются. Чтобы при смене пароля
    один источник правды.
  - **NEW-126:** CI-check или runbook-step: `.htpasswd` регенерируется
    при каждом деплое (не stale). Если `SWAGGER_PASSWORD` в `.env.prod`
    изменился — `.htpasswd` обновляется автоматом.

- **P2-2/7 — `@Tag` на всех контрактах:** **(a)** Явные `@Tag(name=,
  description=)` на всех interface-ах в `*-api-contract`, описания
  на русском (консистентно с языком аудита).
  - **Мотивация:**
    - Swagger-UI группирует endpoints по тегам — без них `<untagged>`
      выглядит плохо.
    - Описания тегов попадают в Swagger-UI header — документация для
      будущих разработчиков.
    - Русский язык — зафиксировано `feedback_language_russian.md` для
      аудитов; для OpenAPI — внутренняя документация проекта, язык
      консистентен.
  - **Что делается:**
    - Audit всех contract-interfaces, добавить отсутствующие `@Tag`.
    - `PushApi` имеет `@Tag(name="Push", description="Web Push subscription
      management")` — переписать на русский: `@Tag(name="Push",
      description="Управление подписками Web Push")`.
    - Примеры: `@Tag(name="Authentication", description="Эндпоинты
      JWT-аутентификации и OTP")`, `@Tag(name="Users", description="CRUD
      пользователей (admin)")`.
    - Если сервис крупный (academic) — несколько тегов по доменам:
      `Users`, `Groups`, `Subjects`, `Semesters`, `Homework`, `Thresholds`.
  - **Каскад:**
    - QC2 — generated TS-типы не зависят от тегов, но Swagger-UI станет
      читаемым.
    - NEW-105 (`docs/operations/deploy/ci-cd.md`) — ссылается на Swagger-UI как основной
      API-reference.
  - **Estimate:** ~30-45 минут (~20 interfaces × несколько минут).

- **P2-2/8 — AsyncAPI для WebSocket/STOMP и RabbitMQ:** **(c)** AsyncAPI
  spec, автогенерация из `event-schemas/`, интеграция в Swagger-UI через
  отдельный tab.
  - **Мотивация:**
    - Event-driven часть системы (RabbitMQ + STOMP) — равноценна по
      важности REST, без документации клиенты пишут по коду.
    - AsyncAPI — industry-standard для event-spec'ов, аналог OpenAPI.
    - Генерация из существующих `event-schemas/*.json` + `_common.json`
      (NEW-120) — minimal incremental effort.
    - Интеграция в swagger-ui через `asyncapi-react` component или
      отдельный static HTML за тем же basic-auth (P2-2/6).
    - Единая документация: REST + STOMP + RabbitMQ — всё за одним
      URL с паролем владельца.
  - **Scope для v0.0.0:**
    - AsyncAPI 3.0 spec файл `docs/asyncapi-snapshot.yaml` (аналог
      `docs/openapi-snapshot.yaml` из NEW-85).
    - Описаны:
      - **Channels RabbitMQ:** все 14+ events из `event-schemas/`
        (publisher: какой сервис, consumer: кто читает).
      - **Channels STOMP:** `/topic/user/{userId}/messages`,
        `/topic/group/{groupId}/dashboard`, и т.д. Payload schemas.
      - **Messages:** reference на JSON Schema в `event-schemas/`
        через `$ref`.
    - Генератор: либо вручную вести (один файл, не так много events),
      либо `asyncapi-generator` из JSON Schemas. Начнём с manual, если
      накопится drift — автоматизировать.
    - Hosting: `https://ruttrack.site/asyncapi/` за nginx basic-auth
      (та же что swagger) — static HTML из `@asyncapi/react-component`.
  - **Что делается:**
    - Создать `docs/asyncapi-snapshot.yaml` с начальным набором channels
      (все events + STOMP топики).
    - `frontends/asyncapi/` — static directory со сборкой AsyncAPI-UI
      (либо `@asyncapi/html-template`).
    - nginx config: `location /asyncapi/` → static + basic-auth.
    - CI-check: drift-guard между реальными events (код) и AsyncAPI
      spec (NEW-101 расширяется — проверяет и OpenAPI-schemas, и
      AsyncAPI-channels).
  - **Каскад:**
    - QA3 (trace_id в events) → AsyncAPI документирует поле в message
      payload.
    - NEW-47/48/60/61/100 (event_version retrofit, shared-events) →
      AsyncAPI описывает versioning policy в spec.
    - NEW-101 (drift-guard) → расширяется: events в коде ↔ AsyncAPI
      channels + event-schemas JSON.
    - NEW-120 (`_common.json` $defs) → AsyncAPI reference'ит через `$ref`.
    - NEW-121 (audit asymmetric flows) → дополняется: «publish через
      event → документируется в AsyncAPI; публикация через REST →
      документируется в OpenAPI». Разделение чёткое.
    - P2-2/6 nginx basic-auth → защищает и AsyncAPI как Swagger.
  - **Estimate:** ~2-3 человеко-дня (initial spec + static UI + nginx
    + CI drift-guard + docs).
  - **NEW-127:** AsyncAPI generator — решение manual vs автоматическая
    генерация из `event-schemas/`. Manual для v0.0.0 (14 events — не
    так много). Триггер пересмотра: >30 events или частый drift.
  - **NEW-128:** `docs/architecture/architecture.md` раздел «Event documentation» —
    где документируются события (AsyncAPI), версионирование, добавление
    новых channels.
  - **NEW-129:** AsyncAPI spec для STOMP — нужно описать payload не
    только RabbitMQ-событий, но и web push notifications (типы
    `attendance.marked`, `excuse.decided`, и т.д., которые фронт
    получает по WebSocket). Фактически это overlap с RabbitMQ events —
    часть STOMP-событий это просто forward из Rabbit. Документировать
    как таковые.

---

## P2 — Группа 3 (Error handling edge-cases)

Ответы на 8 вопросов из отчётов 02/03/04/05/06/07. Расширение shared-web
(Q16a) детализацией поведения. Зафиксированы 2026-04-19.

- **P2-3/1 — catch-all `Exception.class` → 500 detail:** **(b)**
  `detail = "Обратитесь в поддержку, correlation=<trace_id>"` + `trace_id`
  как отдельное поле в ErrorResponse. Stacktrace только в логах.
  - **Мотивация:**
    - 04 P2-5, 02 P1-14: сейчас `ex.getMessage()` утекает SQL/Mongo-детали
      (`null value in column "employee_number"`) — info disclosure.
    - Статичный «обратитесь в поддержку» без ID — админ не найдёт инцидент
      в Loki/Grafana. Использование `trace_id` из QA2 даёт единый ID через
      всю цепочку: gateway → service → RabbitMQ → bot, queryable в Tempo
      span-tree.
  - **Что делается:**
    - В `shared-web` ErrorResponse record: добавить поле `String traceId`
      (обязательное, заполняется из MDC `Sleuth`/Micrometer Tracing).
    - `GlobalExceptionHandler.handleGeneral(Exception ex)`:
      ```java
      String traceId = MDC.get("traceId");  // или tracer.currentSpan().context().traceId()
      log.error("Unexpected error (traceId={})", traceId, ex);
      return new ErrorResponse(
          "about:blank", "Internal Server Error", 500,
          "Обратитесь в поддержку, correlation=" + traceId,
          request.getRequestURI(), Instant.now(), traceId, null
      );
      ```
    - Если `traceId` null (tracing не включён или fallback) — генерируется
      random UUID.
  - **Каскад:**
    - 04 P2-5, 02 P1-14 → 🔧 TO-FIX через Q16a+P2-3/1.
    - QA2 (tracing) — prerequisite (MDC.traceId должен быть заполнен).
    - QA7 (JSON-logs) — `traceId` автоматически в logs через MDC.
    - QC3 (frontend interceptor) — parse `traceId` из ProblemDetails,
      отображает пользователю «Ошибка, trace: abc123».
    - P2-2/1 (OpenApiCustomizer) — ErrorResponse schema в OpenAPI spec
      содержит `traceId` поле.
    - NEW-123 (CI openapi-runtime-conformance) — проверяет что 500-ответ
      содержит traceId.
  - **Estimate:** ~2 часа (ErrorResponse +1 поле, handler логика, smoke-тест).
  - **NEW-130:** Grafana dashboard «Incident lookup» — panel «Найти по
    trace_id» (LogQL + Tempo trace view). Документировать в
    `docs/operations/monitoring/observability.md` (NEW-58) как «если клиент сообщает trace=abc123,
    см. dashboard X».

- **P2-3/2 — `MethodArgumentNotValidException` формат:** **(b)** RFC 7807
  extension `invalid-params[]` согласно приложению B.2.
  - **Мотивация:**
    - Формально по RFC 7807 B.2 — стандартная структура: `{"name": "...",
      "reason": "..."}` per field.
    - QC2 openapi-typescript генерирует typed `ProblemDetails` с
      `invalid-params`, openapi-fetch даёт typed response.
    - Reuse в async-api (NEW-129) и gateway (P2-3/6) — единый формат.
    - `errors[]` был Spring-specific, `invalid-params[]` — industry standard.
  - **Что делается:**
    - В `shared-web` `ErrorResponse` record:
      ```java
      public record ErrorResponse(
          String type, String title, int status, String detail,
          String instance, Instant timestamp, String traceId,
          List<InvalidParam> invalidParams  // nullable, только для 400 Validation
      ) {}
      public record InvalidParam(String name, String reason) {}
      ```
    - `@ExceptionHandler(MethodArgumentNotValidException.class)`:
      ```java
      List<InvalidParam> params = ex.getBindingResult().getFieldErrors().stream()
          .map(fe -> new InvalidParam(fe.getField(), fe.getDefaultMessage()))
          .toList();
      return ErrorResponse.badRequest("Validation Failed",
          "Validation failed for " + params.size() + " fields",
          request, params);
      ```
    - Аналогично для `ConstraintViolationException` (path-params validation):
      `name = violation.getPropertyPath().toString()`.
  - **Каскад:**
    - **Breaking change** для существующих фронт-клиентов, использующих
      `errors[]` в form-validation. Но QC2 type-gen первой волной
      перегенерит API-клиент во всех фронтах — TS compiler поймает
      несоответствие в компайл-тайм.
    - QC3 error-interceptor — отдельная логика для 400 с `invalid-params`:
      возвращает структуру formGroup.setErrors (Angular) / react-hook-form
      setError (PWA).
    - P2-2/1 (OpenApiCustomizer) — `invalid-params` в schema для 400-ответа.
    - P2-2/5 (generic example) — остаётся generic, конкретные invalid-params
      приходят в runtime.
  - **Estimate:** ~3 часа (ErrorResponse extension + 2 handler'а + тесты).
  - **NEW-131:** в `docs/frontend-architecture.md` (NEW-86) раздел
    «Form validation error handling» — как маппить `invalid-params` на
    form-controls в Angular (`formGroup.get(name).setErrors({server:
    reason})`) и PWA (react-hook-form).

- **P2-3/3 — Полный набор `@ExceptionHandler` в shared-web:** **(a)**
  Стандартные Spring-исключения покрываются один раз в `shared-web`.
  - **Мотивация:**
    - Без этого Spring default: 500 "Internal Server Error" без RFC 7807
      body. Фронт теряет status code semantic.
    - В shared-web — одно место, все 4 сервиса получают одинаковое
      поведение.
    - CI-тест (NEW-123 swagger-request-validator) требует точных status
      codes — без этих handler'ов тесты упадут.
  - **Scope (минимум для v0.0.0):**
    - `HttpMessageNotReadableException` → 400 "Malformed request body".
      Для `JsonParseException` parent (malformed JSON) и
      `JsonMappingException` (не матчит schema).
    - `HttpMediaTypeNotSupportedException` → 415 "Unsupported media type".
      `detail`: список поддерживаемых (`Supported: application/json`).
    - `HttpMediaTypeNotAcceptableException` → 406 "Not Acceptable".
    - `MissingServletRequestParameterException` → 400 "Missing parameter: {name}".
    - `MethodArgumentTypeMismatchException` → 400 "Invalid type for parameter {name}".
    - `HttpRequestMethodNotSupportedException` → 405 "Method not allowed".
      Добавить header `Allow: GET, POST, ...`.
    - `NoHandlerFoundException` → 404 "Endpoint not found". Требует
      `spring.mvc.throw-exception-if-no-handler-found: true` +
      `spring.web.resources.add-mappings: false` в каждом сервисе.
    - `AsyncRequestTimeoutException` → 503 "Request timeout".
    - `MaxUploadSizeExceededException` → 413 "Payload too large" (для
      excuse-file upload в attendance).
  - **Что делается:**
    - Все handler'ы в `shared-web/GlobalExceptionHandler` с `@Order(1)`.
    - Каждый сервис может добавить `@RestControllerAdvice(order=0)` для
      домен-специфичных `@ExceptionHandler` (например, academic →
      `DuplicateLoginException → 409`).
    - Spring config в каждом `application.yml`: `spring.mvc.throw-exception-if-no-handler-found: true`.
    - Integration-тест в shared-web (через shared-web-testing или inline
      в каждом сервисе): malformed JSON → 400 RFC 7807.
  - **Каскад:**
    - Q16a (shared-web) расширяется на весь edge-case set.
    - 05 P0-2 (нет GlobalExceptionHandler) — покрывается полностью.
    - NEW-123 (openapi-runtime-conformance) — тесты проходят.
    - P2-2/1 (OpenApiCustomizer) — все эти коды в OpenAPI spec.
  - **Estimate:** ~4 часа (9 handler'ов + тесты + config tweaks × 4 сервиса).
  - **NEW-132:** `docs/api/api-error-conventions.md` — таблица «HTTP status ×
    exception × title × когда возникает». Отсылка в OpenAPI (NEW-85
    snapshot) и frontend-architecture (NEW-86). Связано с NEW-32
    (404 vs 403 policy).

- **P2-3/4 — `DataIntegrityViolationException` parsing (03 P3-9):** **(c)**
  Service-слой catch'ит DIVE рядом с операцией, throw'ит
  `DuplicateXxxException`; handler маппит custom → 409.
  - **Мотивация:**
    - Substring-parsing (`"uq_one_off_slot"` в message) хрупкое — Postgres
      JDBC driver может менять формат; Mongo даёт другой формат; тесты
      проходят, прод падает.
    - Service знает контекст — «я сейчас сохраняю OneOffLesson, unique
      violation = duplicate slot». Exception с domain-meaning.
    - Handler маппит: `DuplicateOneOffLessonException → 409 "Pair already
      exists on this date"`. Понятное сообщение для клиента.
    - Testable — unit-тест service'а мокает repo и проверяет правильное
      исключение.
  - **Что делается:**
    - Новые domain-exceptions в каждом сервисе:
      - `schedule`: `DuplicateOneOffLessonException`.
      - `academic`: `DuplicateLoginException`, `DuplicateTelegramIdException`,
        `DuplicateGroupNameException`, `DuplicateSubjectNameException`.
      - `attendance`: `DuplicateCheckinException` (если unique по
        `lesson_id+user_id` в Mongo).
    - Service-слой:
      ```java
      try {
          return repository.save(entity);
      } catch (DataIntegrityViolationException ex) {
          throw new DuplicateOneOffLessonException(groupId, date, lessonNumber, ex);
      }
      ```
    - Shared-web handler для generic `DataIntegrityViolationException`
      (fallback): 409 "Конфликт данных". Не должен срабатывать если
      service-слой правильно catch'ит, но страховка.
    - Audit: grep `DataIntegrityViolationException` по 4 сервисам, каждое
      место обёрнуть в domain-exception.
  - **Каскад:**
    - 03 P3-9 → 🔧 TO-FIX (substring parsing удаляется, остаётся generic fallback).
    - QB7 (uniqueness login/telegram_id) — требует `DuplicateLoginException`.
    - QB6 (telegram_id change) — аналогично.
    - 04 P0-6 (Mongo unique) — аналогичный паттерн для Mongo
      `DuplicateKeyException`.
    - NEW-31 (SecurityIdorIT) — контракт-тесты на 409 conflict.
  - **Estimate:** ~0.5 дня (~10 мест × 10 минут + 5 domain exceptions + тесты).
  - **NEW-133:** audit всех `catch (DataIntegrityViolationException)`
    и `catch (DuplicateKeyException)` (Mongo). Для каждого — обернуть
    в domain-exception. Добавить в NEW-132 таблицу.

- **P2-3/5 — STOMP error-handling (05 P1-5):** **(c) hybrid** —
  unauthorized → тихий `null` (drop SUBSCRIBE), malformed/other →
  STOMP ERROR frame с RFC 7807 telemetry.
  - **Мотивация:**
    - Unauthorized → тихо: (1) IDOR-защита — злоумышленник не получает
      подтверждения существования topic'а; (2) валидные подписки того же
      клиента не рвутся.
    - Malformed/server-error → ERROR frame с traceId: клиент может
      залогировать, показать toast. STOMP spec допускает ERROR frame
      без разрыва соединения.
    - Не идеально (ERROR frame в STOMP 1.2 → connection closed в
      некоторых клиентах), но Spring обрабатывает через
      `ChannelInterceptor.preSend` return null vs throw.
  - **Что делается:**
    - `SubscriptionAuthInterceptor.preSend`:
      ```java
      if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
          if (!isAuthorized(userId, destination)) {
              log.info("Denied subscription: user={} topic={}", userId, destination);
              return null;  // silent drop
          }
      }
      return message;
      ```
    - Для malformed frame / auth-missing → throw `MessageHandlingException`,
      `@MessageExceptionHandler` в new `StompExceptionHandler` формирует
      ERROR frame с payload в RFC 7807 format.
    - Shared-web формирует ERROR frame через common utility
      `StompErrorFrames.error(status, title, detail, traceId)`.
  - **Каскад:**
    - 05 P0-2 (GlobalExceptionHandler) — STOMP ERROR frame использует
      тот же ErrorResponse schema.
    - C0-7 ws-ticket — если ticket невалидный, connection rejected на
      handshake, до STOMP даже не доходит.
    - Фронт (QC1 NotificationCenter) — handle ERROR frame: log + toast +
      reconnect with ws-ticket.
    - NEW-39 (ws handshake audit log) — unauthorized drops логируются.
  - **Estimate:** ~3 часа (interceptor + MessageExceptionHandler + ERROR
    frame utility + тесты).
  - **NEW-134:** задокументировать STOMP error-contract в
    `docs/websocket-protocol.md` или AsyncAPI (NEW-128): типы ошибок,
    что делает клиент при ERROR frame, какие silent drops ожидаемы.

- **P2-3/6 — Gateway RFC 7807 формат (07 P1-8):** **(a)** Ручная копия
  формата в `JwtAuthenticationFilter.unauthorized()` — без подключения
  gateway к shared-web.
  - **Мотивация:**
    - shared-web — Spring MVC (blocking). Gateway — WebFlux (reactive).
      Разные runtime-контексты, разные Bean-конфигурации.
    - Создавать WebFlux-вариант shared-web = дублировать код, создавать
      поддержку × 2.
    - Gateway RFC 7807 — всего ~5 мест (401 unauthorized, 403 forbidden,
      503 downstream unavailable, 429 rate-limit, 404 no route). Ручная
      копия + utility-метод = ~30 строк.
    - Клиенту формат визуально идентичен downstream — не знает разницы.
  - **Что делается:**
    - `gateway/util/ErrorResponses.java`:
      ```java
      public static Mono<Void> write(ServerHttpResponse response, HttpStatus status,
                                     String title, String detail, String path, String traceId) {
          response.setStatusCode(status);
          response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
          var body = Map.of(
              "type", "about:blank",
              "title", title,
              "status", status.value(),
              "detail", detail,
              "instance", path,
              "timestamp", Instant.now().toString(),
              "traceId", traceId != null ? traceId : "unknown"
          );
          return response.writeWith(Mono.just(response.bufferFactory().wrap(JSON.toBytes(body))));
      }
      ```
    - Использование в `JwtAuthenticationFilter.unauthorized()` и других
      filter-местах.
    - Gateway получает `traceId` из request headers (Sleuth propagates
      автоматом).
  - **Каскад:**
    - 07 P1-8 → 🔧 TO-FIX.
    - QA2 (tracing) — `traceId` propagates.
    - QC3 (frontend) — парсит одинаково из gateway и downstream.
    - P2-3/1 (correlation) — gateway также использует traceId.
  - **Estimate:** ~1.5 часа (utility + 5 use-sites + тесты).

- **P2-3/7 — Python-бот transient vs permanent:** **(b)** requeue для
  transient (gRPC UNAVAILABLE), DLQ для permanent (malformed/schema).
  + prometheus counter.
  - **Мотивация:**
    - Transient (academic gRPC down 30с) — после рестарта обработка
      продолжается. requeue даёт RabbitMQ задержать delivery.
    - Permanent (malformed event, несоответствие schema) — reject → DLQ,
      manual inspect.
    - Без метрики не видно что DLQ растёт — QA4 counter `notification_bot_dispatch_failed_total{event_type, kind}`
      где `kind ∈ {transient, permanent}`.
    - Связка с C0-3 outbox: Java гарантирует delivery в Rabbit, Python
      гарантирует обработку (с retry и DLQ).
  - **Что делается:**
    - Классификация exceptions в `bot/consumers/event_dispatcher.py`:
      ```python
      TRANSIENT_EXCEPTIONS = (grpc.RpcError, ConnectionError, TimeoutError, asyncio.TimeoutError)
      PERMANENT_EXCEPTIONS = (ValidationError, json.JSONDecodeError, KeyError, TypeError)

      try:
          await handler(body)
          await message.ack()
      except TRANSIENT_EXCEPTIONS as e:
          logger.warning("Transient error, requeuing (traceId=%s)", trace_id, exc_info=e)
          transient_counter.labels(event_type=event_type).inc()
          await message.reject(requeue=True)
      except PERMANENT_EXCEPTIONS as e:
          logger.error("Permanent error, DLQ (traceId=%s)", trace_id, exc_info=e)
          permanent_counter.labels(event_type=event_type).inc()
          await message.reject(requeue=False)  # goes to DLQ
      except Exception as e:  # catch-all — treat as permanent (safer)
          logger.exception("Unknown error, DLQ (traceId=%s)", trace_id)
          permanent_counter.labels(event_type=event_type, reason="unknown").inc()
          await message.reject(requeue=False)
      ```
    - Метрика через `prometheus_client` (уже в боте? проверить) или
      добавить dependency.
    - Back-off для transient (избежать tight-loop): Rabbit TTL queue для
      delay через x-message-ttl, либо `await asyncio.sleep(backoff)` перед
      reject.
    - DLQ alerts (QA4): «DLQ size > 10 events» → Telegram админу через
      NEW-62 bot webhook.
  - **Каскад:**
    - 06 P1-6 → 🔧 TO-FIX.
    - QA4 (метрики) — dispatch_failed counters в dashboard.
    - C0-3 (outbox Java) — Python-сторона теперь reliable consumer.
    - NEW-65 (baseline 2 недели) — baseline для DLQ размера перед
      алертом.
    - NEW-63 (alerts runbook) — «что делать при DLQ alert».
  - **Estimate:** ~0.5 дня (dispatcher + metrics + DLQ alerting + tests).
  - **NEW-135:** back-off strategy для transient в Python-боте —
    exponential backoff через RabbitMQ TTL queue vs in-process sleep.
    Решить при имплементации. Рекомендация — RabbitMQ TTL queue (не
    блокирует consumer).
  - **NEW-136:** runbook `docs/operations/runbooks/rabbit-dlq-recovery.md` — как
    читать DLQ, решать проблему, replay events. Nested с NEW-133 (migration
    runbook).

- **P2-3/8 — Swallowed exceptions audit + ArchUnit:** **(a) + (b)**
  Разовый audit сейчас + ArchUnit-правило «пустой catch block = fail».
  - **Мотивация:**
    - Swallowed exceptions — главный источник silent bugs. «Почему
      пусто/null?» — catch проглотил.
    - Разовый audit закрывает текущее состояние.
    - ArchUnit (NEW-109) — защищает на будущее. 2-е правило в том же
      модуле, дёшево.
  - **Что делается:**
    - Audit Java-сервисов:
      - `grep -rn "catch.*(.*) {" services/*/src/main/java | grep -v "log\."`
      - Для каждого empty/log-less catch — добавить `log.debug("...", e)`
        (если намерено swallow'им) или исправить handling.
      - Известные места: 04 P2-3 (ExcuseService.resolveLessonDetails),
        02 (?), 03 (?) — полный audit найдёт все.
    - Audit Python-бота:
      - `grep -rn "except.*:" services/notification-bot/bot/ -A1 | grep -B1 "pass"` — поиск `except: pass`.
      - Аналогично без log.
    - ArchUnit-правило:
      ```java
      @ArchTest
      static final ArchRule catch_blocks_must_log =
          noMethods().that().haveRawParameterTypes(Throwable.class)  // примерно
              .should(/* empty catch = fail */);
      ```
      Реально ArchUnit не умеет анализировать bytecode на уровне catch-body
      без дополнительных библиотек. Альтернатива — Checkstyle
      `EmptyCatchBlock` rule (встроенный).
    - Для Python — `flake8-bugbear` правило `B902` или custom
      `ruff` правило.
  - **Каскад:**
    - 04 P2-3, 06 P1-3 → 🔧 TO-FIX.
    - NEW-109 (ArchUnit framework) → +1 правило.
    - NEW-119/110 (stylelint-like для Python) → +1 правило.
  - **Estimate:** ~3 часа (audit + фиксы + Checkstyle config + Python lint).
  - **NEW-137:** CI-job «lint-empty-catch» для Java (Checkstyle) и
    Python (ruff/flake8-bugbear). Fail PR если добавлен пустой catch.
    Связано с NEW-109 (ArchUnit) и NEW-110 (stylelint) — все три в
    одной CI-сборке QD2 coverage-gate.

---

## P2 — Группа 4 (Validation constraints)

Ответы на 8 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: P2-3/2 (RFC 7807 `invalid-params[]` handler в shared-web),
P2-2/1 (OpenApiCustomizer документирует 400), QD2 (coverage-gate),
QC2 (openapi-typescript генерирует TS-типы с constraint'ами).

Audit существующих валидаций показал: базовые `@NotNull/@NotBlank/@Size`
везде есть (academic — идеально, attendance-checkin — `@DecimalMin/Max`
на координатах ✅). Пробелы: cross-field validation, format-patterns,
pagination guards, path-variable IDs, element constraints на списках,
file-upload MIME, date-boundaries, enum-deserialization strategy.

- **P2-4/1 — Cross-field validation:** **(b)** custom class-level
  аннотации + `ConstraintValidator` в новом пакете
  `shared-web/validation/`.
  - **Мотивация:**
    - Cross-field нарушения (startTime < endTime, dateFrom ≤ dateTo,
      STUDENT+groupId required) — явный user-error, должны ловиться
      одинаково на уровне DTO, а не в service-слое.
    - Custom annotations переиспользуемы (`@StartBeforeEnd`,
      `@DateRangeValid`, `@RoleFieldsComplete`) — одно место правды, одна
      библиотека на проект.
    - Работает вместе с handler'ом из P2-3/2: ConstraintValidator
      кладёт error в `BindingResult` → `MethodArgumentNotValidException`
      → `invalid-params[]` с field-level info. QC2 генерирует TS-типы
      с этими constraint'ами → фронт валидирует до submit.
  - **Scope для v0.0.0:**
    - `@StartBeforeEnd(start, end)` — `CreateScheduleItemRequest` (03 P1-10).
    - `@DateRangeValid(from, to)` — `MassCancelRequest` (03 P1-2),
      `CreateSemesterRequest` (если бизнес-правило from ≤ to).
    - Для role-specific требований (`CreateUserRequest`: STUDENT без
      groupId, TEACHER без employeeNumber) — оставить в service-слое
      (вариант c), т.к. логика сложнее чем «либо-либо» (future MFA,
      ADMIN имеет другой набор полей). Service бросает
      `BadRequestException` → handler маппит → 400 RFC 7807.
  - **Что делается:**
    - Новый пакет `shared-web/src/main/java/.../validation/`:
      - `@StartBeforeEnd` + `StartBeforeEndValidator implements
        ConstraintValidator<StartBeforeEnd, Object>` (BeanWrapper
        доступ к start/end полям).
      - `@DateRangeValid` аналогично.
    - Пример применения:
      ```java
      @StartBeforeEnd(start = "startTime", end = "endTime")
      public record CreateScheduleItemRequest(..., LocalTime startTime,
                                              LocalTime endTime, ...) {}
      ```
    - Unit-тесты ConstraintValidator'ов — параметризованные (valid/invalid
      cases).
  - **Каскад:**
    - 03 P1-10 (CreateScheduleItemRequest), 03 P1-2 (MassCancelRequest)
      → 🔧 TO-FIX через custom annotations.
    - P2-3/2 (handler invalid-params[]) — получает field-level errors
      автоматом.
    - QC2 (openapi-typescript) — `x-constraints` в OpenAPI schema.
      Frontend валидирует pre-submit.
    - NEW-138 (ниже) — пакет `shared-web/validation/`.
  - **Estimate:** ~4 часа (3 annotations + validators + тесты +
    application в 2-3 DTO).
  - **NEW-138:** новый пакет `shared-web/validation/` с common
    cross-field annotations. Документировать в `docs/api/api-error-conventions.md`
    (NEW-132) — когда использовать какую аннотацию.

- **P2-4/2 — Format-patterns (login, OTP-code, telegram_id):** **(a)**
  Полный audit string/number полей с семантическим форматом + `@Pattern`
  / `@Positive` / `@Min` / `@Max` везде где известна семантика.
  - **Мотивация:**
    - Защита в depth: даже если frontend отправит мусор (сломанный
      клиент, bypass TS-типов через curl), backend отклонит с 400 до
      Redis/БД-операций.
    - Format-mismatch раньше ловится = проще диагностика (400 с полем
      «code must match `^\d{6}$`» vs 401 после неуспешного lookup).
  - **Scope для v0.0.0:**
    - `OtpVerifyByCodeRequest.code` → `@Pattern("^\\d{6}$")` (01 P2-3).
    - `LoginRequest.login` → `@Size(max=32)` + `@Pattern` для формата
      `student00001`/`teacher00001`/тестовых `student`/`teacher`/`admin`.
      Pattern: `^(student|teacher|admin|student\d{5}|teacher\d{5})$`.
    - `OtpRequest.telegramId` → `@Positive` + `@Max(9_999_999_999_999L)`
      (Telegram ID fits in int64, но реально < 10^13).
    - `CreateUserRequest.employeeNumber` — проверить формат (если есть
      корпоративный стандарт — pattern; иначе только `@Size`).
    - Все telegram_id поля в contract-DTO — унифицировать `@Positive`.
    - Group names, subject names, ФИО — `@Size(max=255)` + optional
      `@Pattern` (без управляющих символов).
  - **Что делается:**
    - Audit всех request-DTO в 5 contract-модулях (auth/academic/schedule/
      attendance/notification).
    - Для каждого string-поля: либо уже `@Size` + `@Pattern` → ok, либо
      добавить.
    - Для numeric: `@Positive`, `@Min(0)`, `@Max(...)` по семантике.
    - Messages на русском (консистентно с academic).
  - **Каскад:**
    - 01 P2-3 → 🔧 TO-FIX (OTP pattern).
    - P2-2/4 (`@Schema(description, example)`) — усиливается, pattern
      отражается в OpenAPI spec автоматически.
    - NEW-132 (api-error-conventions.md) — таблица «поле → формат».
    - QC2 — TS-типы с minLength/maxLength/pattern.
  - **Estimate:** ~2 часа (audit × 5 модулей + правки).
  - **NEW-139:** audit checklist в `docs/api/api-error-conventions.md`:
    таблица «DTO-поле → тип → constraint → rationale». Обновляется при
    добавлении новых полей (как часть NEW-108 contributing.md).

- **P2-4/3 — Pagination guards:** **(a)** глобальная property
  `spring.data.web.pageable.max-page-size: 100` во всех 5 сервисах.
  - **Мотивация:**
    - Spring Data автоматом конвертирует `?page=X&size=Y` в `Pageable`,
      но НЕ валидирует `size` сверху. Админ/staff может запросить
      `?size=1000000` → backend грузит 1M записей → OOM.
    - После фикса 02 P0-7 (HomeworkController на Pageable) и NEW-26
      (audit findAll без Pageable) все list-endpoints принимают
      Pageable — нужен глобальный guard.
    - Одна property в yml, Spring auto-applies, клиент получает 400 при
      size > 100. Документировать в NEW-132.
  - **Что делается:**
    - Property добавляется в base `application.yml` каждого сервиса
      (через shared-web-config yml import или per-service):
      ```yaml
      spring:
        data:
          web:
            pageable:
              max-page-size: 100
              default-page-size: 20
              one-indexed-parameters: false
      ```
    - Audit всех controller-методов на Pageable — убедиться что нигде
      нет custom `@RequestParam int size` без `@Max`.
    - Integration-тест в каждом сервисе: `GET /api/x?size=1000` → 400.
  - **Каскад:**
    - 02 P0-7 → подтверждено (Pageable + max-size).
    - NEW-26 (audit findAll) → после фикса все попадут под этот guard.
    - QC6 (stats aggregate) — не нужен лимит на aggregate, но list-
      endpoints под лимитом.
  - **Estimate:** ~1.5 часа (5 yml-правок + audit + тесты).

- **P2-4/4 — Валидация `@PathVariable` IDs:** **(a)** `@Validated` на
  controller + `@Positive @PathVariable Long id` (или `@Min(1)`).
  - **Мотивация:**
    - Сейчас `/api/academic/users/0`, `.../users/-5`,
      `.../users/99999999999999999` проходят в сервис. Service обычно
      `findById(id)` → 404, но 0 и negative — не валидные PK (BIGSERIAL
      стартует с 1). Overflow может дать `NumberFormatException` → 500.
    - Явная семантика: fail-fast с RFC 7807 400 + invalid-params.
    - Handler для `ConstraintViolationException` уже покрыт P2-3/3.
  - **Что делается:**
    - `@Validated` на class-level каждого controller'а.
    - Audit всех `@PathVariable Long id` / `Long userId` / `Long groupId`
      — добавить `@Positive`.
    - QC2 openapi-typescript — constraint в spec.
    - Integration-тест: `GET /api/academic/users/0` → 400 RFC 7807.
  - **Каскад:**
    - P2-3/2 (`invalid-params[]` для 400) — ConstraintViolationException
      → тот же handler.
    - P2-3/3 (handler в shared-web) — уже покрывает
      `ConstraintViolationException`.
    - NEW-123 (openapi-runtime-conformance) — контракт-тест 400.
  - **Estimate:** ~1 час (audit × 4 сервиса + правки).

- **P2-4/5 — Element constraints на списках:** **(a)** `@NotEmpty
  List<@Positive @NotNull Long> lessonIds` + `@Size(max=100)` (DoS-защита).
  - **Мотивация:**
    - `CreateExcuseRequest.lessonIds` — `@NotEmpty`, но элементы не
      валидируются (null или 0 в списке проходят). Аналогично
      `CreateSubjectRequest.teacherIds`.
    - Jakarta Validation поддерживает element constraints на generic
      type — стандартный механизм.
    - `@Size(max=100)` — защита от DoS (фронт прислал список из 10000
      IDs, backend делает N+1 lookups до выяснения что часть не
      существует).
  - **Scope:**
    - `CreateExcuseRequest.lessonIds`: `@NotEmpty @Size(max=100)
      List<@NotNull @Positive Long>`.
    - `CreateSubjectRequest.teacherIds`: аналогично + `@NotNull`
      (если список обязателен).
    - Audit всех `List<Long>` / `List<String>` в contract-DTO.
  - **Каскад:**
    - P2-4/4 — аналогичный паттерн positive IDs, только внутри списка.
    - 04 (CreateExcuseRequest), 02 (CreateSubjectRequest).
    - P2-3/2 — `invalid-params[]` с `name = "lessonIds[3]"` для
      element-level errors.
  - **Estimate:** ~1 час (audit + правки).

- **P2-4/6 — File upload валидация:** **(b) + (a) unified** — custom
  `@ValidFile` annotation в shared-web + unified config property
  `ATTENDANCE_EXCUSE_MAX_FILE_SIZE` в application.yml.
  - **Мотивация:**
    - 04 P2-7: magic number 10MB дублируется в `application.yml` и
      `ExcuseService.java` (drift риск).
    - Нет MIME-валидации — студент может отправить `.exe` под видом
      документа. Бот forward'ит старосте через Telegram → Telegram
      отфильтрует exe, но в системе может быть сохранено/логировано.
    - Декларативная аннотация: `@ValidFile(maxSizeBytes=10_485_760,
      allowedMediaTypes={"image/jpeg","image/png","application/pdf"})
      MultipartFile file` — читабельно, переиспользуемо.
  - **Что делается:**
    - `shared-web/validation/@ValidFile` + `ValidFileValidator implements
      ConstraintValidator<ValidFile, MultipartFile>`:
      - Проверяет `file.getContentType() in allowedMediaTypes`.
      - Проверяет `file.getSize() <= maxSizeBytes`.
      - Null-check (если `required=true`).
    - `@ConfigurationProperties("attendance.excuse")` с полями
      `max-file-size`, `allowed-media-types`. Используется и в
      controller-аннотации (через SpEL `#{@excuseProps.maxFileSize}`),
      и в `spring.servlet.multipart.max-file-size`.
    - Handler для `MaxUploadSizeExceededException` уже в P2-3/3 → 413.
    - Spring `MultipartResolver` лимит — первый заслон, `@ValidFile` —
      второй (MIME + semantic).
  - **MIME spoofing note:** Content-Type header от клиента — можно
    подделать. Magic-byte проверка (apache-tika) — более надёжно, но
    overkill для v0.0.0. Отложено в future-ideas.md.
  - **Каскад:**
    - 04 P2-7 → 🔧 TO-FIX (unified config + @ValidFile).
    - P2-3/3 (handler `MaxUploadSizeExceededException` → 413) —
      уже в scope.
    - NEW-140 (ниже) — future-idea: magic-byte MIME check.
  - **Estimate:** ~3 часа (@ValidFile + validator + ConfigurationProperties
    + refactor ExcuseService + тесты).
  - **NEW-140:** future-ideas.md — magic-byte MIME validation через
    apache-tika (защита от Content-Type spoofing). Приоритет — низкий,
    Telegram сам фильтрует основные executable-форматы на своей стороне.

- **P2-4/7 — Date-boundary constraints:** **(a) + (b)** явные
  `@FutureOrPresent` где явная семантика, accept retrofit там где
  бизнес допускает прошлое.
  - **Мотивация:**
    - `CreateHomeworkRequest.lessonDate` уже `@FutureOrPresent` ✅.
    - `MassCancelRequest.dateFrom/dateTo` — нельзя отменять прошлые
      пары (staff отменяет future-only) → `@FutureOrPresent` на `dateFrom`.
    - `CreateOneOffLessonRequest.date` — доп-пара создаётся на будущее
      → `@FutureOrPresent`. Admin-override через отдельный endpoint/
      параметр (NEW-77).
    - `CreateSemesterRequest.dateFrom/dateTo` — retrofit допустим (админ
      может создать семестр за прошлый год) → accept, без аннотации,
      с комментарием в @Schema.
  - **Что делается:**
    - Audit всех LocalDate / Instant / OffsetDateTime полей в request-DTO.
    - Где бизнес требует future-only → `@FutureOrPresent`.
    - Где retrofit допустим → комментарий в `@Schema(description = "...
      (retrofit допустим)")`.
  - **Каскад:**
    - NEW-132 (api-error-conventions.md) — раздел «Date-boundary
      semantics».
    - QB5 (запрет lesson change после старта) — аналогичная семантика.
  - **Estimate:** ~1 час (audit + правки).

- **P2-4/8 — @Valid audit + enum-deserialization strategy:** **(c)**
  оба пункта: audit `@Valid` на nested DTO + `READ_UNKNOWN_ENUM_VALUES_AS_NULL`
  в shared-web ObjectMapper config.
  - **Мотивация:**
    - `SubscribeRequest.keys` имеет `@Valid` ✅. Audit остальных nested
      DTO — если где-то забыт, constraints внутри не работают.
    - Enum-поля (UserRole, AttendanceStatus, ExcuseType, WeekType,
      GroupStatus): без strategy Jackson кидает
      `HttpMessageNotReadableException` → 400 "Malformed request body"
      (generic, без field-level info).
    - С `READ_UNKNOWN_ENUM_VALUES_AS_NULL` + `@NotNull` на поле:
      неизвестное значение → null → constraint ловит → RFC 7807
      `invalid-params[]` с именем поля и ожидаемыми значениями в
      message. Понятнее для фронта.
  - **КРИТИЧЕСКИЙ нюанс:** `READ_UNKNOWN_ENUM_VALUES_AS_NULL` требует
    что все enum-поля в DTO имеют `@NotNull` (иначе null пройдёт
    silently → NPE в service). **Audit всех enum-полей обязателен**
    перед включением feature. Без этого audit'а — НЕ включать.
  - **Что делается:**
    - Audit всех nested DTO на `@Valid`:
      - grep по `*-api-contract` на поля типа `record` / не-primitive
        DTO без `@Valid`.
      - Примеры: `SubscribeRequest.keys` (уже @Valid), проверить
        `CreateScheduleItemRequest.recurrenceRules`, `CreateExcuseRequest`
        если есть nested.
    - Audit всех enum-полей:
      - grep по enum-типам (UserRole, AttendanceStatus, ...) в request-DTO.
      - Каждое → `@NotNull`. Если optional → оставить Jackson-default
        behavior (без READ_UNKNOWN_ENUM_VALUES_AS_NULL strategy per-field,
        либо пометить `@JsonDeserialize` custom).
    - Shared-web `JacksonConfig`:
      ```java
      @Bean
      public Jackson2ObjectMapperBuilderCustomizer customizer() {
          return b -> b
              .featuresToEnable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
              .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      }
      ```
    - Integration-test: POST с enum-значением `"INVALID_ROLE"` → 400
      RFC 7807 + `invalid-params[{name:"role", reason:"must not be null"}]`.
  - **Каскад:**
    - P2-3/2 (invalid-params[]) — enum-ошибки теперь попадают сюда.
    - QC2 (TS-типы enum'ов) — фронт валидирует union types до отправки.
    - P2-3/3 (handler) — `HttpMessageNotReadableException` остаётся для
      действительно malformed JSON.
  - **Estimate:** ~4 часа (audit enum-полей × 5 contracts + @NotNull +
    Jackson config в shared-web + integration-тесты).
  - **NEW-141:** audit checklist «enum-поля в contract-DTO → все
    `@NotNull`» в `docs/api/api-error-conventions.md` (NEW-132). Часть
    NEW-108 contributing.md как PR-check.

**Итого P2-4 (8 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-4:** ~2.5 человеко-дня (4 + 2 + 1.5 + 1 + 1 +
  3 + 1 + 4 часа = ~17.5 часов).
- **Новый пакет:** `shared-web/validation/` с custom annotations
  (NEW-138, P2-4/1 + P2-4/6).
- **Unified config:** `spring.data.web.pageable.max-page-size: 100` во
  всех yml (P2-4/3).
- **Jackson config:** `READ_UNKNOWN_ENUM_VALUES_AS_NULL` в shared-web
  (P2-4/8) — после обязательного audit'а enum-полей.
- **Новых NEW-задач:** 4 (NEW-138..141).
- **Auto-resolves:** 01 P2-3, 03 P1-2, 03 P1-10, 04 P2-7.

---

## P2 — Группа 10 (Performance hotspots)

Ответы на 8 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: QC6 (stats aggregate + 60s cache), 02 P0-7 (Pageable +
@EntityGraph), NEW-26 (audit findAll), P2-4/3 (max-page-size: 100),
QA2 (tracing + Micrometer), NEW-28 (ShedLock audit).

Audit показал: базовые индексы на FK есть, пробелы — composite indexes
на hot queries, кэш справочников (semester, subject, RBAC), N+1 при
загрузке lesson-details, N×await в frontend-циклах (bulk-mark),
connection pool defaults (HikariCP=10), cleanup push-подписок,
параллелизация hot-path gRPC (checkin).

- **P2-10/1 — Composite indexes на hot queries:** **(a)** Добавить
  composite indexes миграциями + `docs/performance-indexes.md` с
  EXPLAIN ANALYZE before/after.
  - **Мотивация:**
    - 03 P2-2: schedule без composite index на
      `(group_id, date_from, date_to)` → seq-scan при getWeekLessons
      для групп с 10k+ lesson-записей за год.
    - 04 P2-9: attendance без `(group_id, status, created_at)` на
      LateCheckinRequest → slow scan в headman-dashboard.
    - 02 P2-4: academic без `(group_id, semester_id)` в Users.
    - Индекс добавляет минимальный write-overhead, критично ускоряет
      read. EXPLAIN-artifact — документ для v0.1 capacity-planning.
  - **Scope (v0.0.0):**
    - `schedule_db`: `CREATE INDEX idx_lessons_group_dates ON
      lessons (group_id, date) WHERE status != 'cancelled';`
      (partial для filter cancelled).
    - `schedule_db`: `CREATE UNIQUE INDEX idx_oneoff_dedup ON
      one_off_lessons (group_id, lesson_number, date);` (03 P2-8).
    - `attendance_db` (PG? или всё Mongo — проверить): index
      `(group_id, status, created_at DESC)` на LateCheckinRequest.
    - Mongo attendance collection: `db.attendances.createIndex({
      group_id: 1, lesson_id: 1 })` если ещё нет.
    - `academic_db`: `(group_id, semester_id)` на user_groups таблице.
  - **Что делается:**
    - Flyway V{N}__add_performance_indexes.sql в каждом сервисе.
    - EXPLAIN ANALYZE до и после (на test-datasets с 10k+ rows).
    - Integration-тест: query time assertion `< 50ms` (regression
      guard).
    - `docs/performance-indexes.md` — таблица «запрос → indexes → p50
      до → p50 после». Обновляется при добавлении новых индексов.
  - **Каскад:**
    - 02 P2-4, 03 P2-2, 03 P2-8, 04 P2-9 → 🔧 TO-FIX.
    - NEW-142 (performance-indexes.md).
    - P2-10/2 — composite indexes работают вместе с @EntityGraph.
    - NEW-74 (PR-template expand/contract) — индексы добавляются как
      expand-step, не требуют rollback-скрипта.
  - **Estimate:** ~3 часа (5-6 миграций + EXPLAIN + integration-тесты).
  - **NEW-142:** `docs/performance-indexes.md` с before/after EXPLAIN.
    Артефакт для v0.1 capacity-planning. Runbook «как добавить новый
    индекс» с шаблоном EXPLAIN + Flyway migration + regression test.

- **P2-10/2 — N+1 SELECT lesson-details/group-members:** **(a+c)
  mixed** — `@EntityGraph` для list-endpoints, DTO projection для
  single-detail где нужны считанные поля.
  - **Мотивация:**
    - 03 P2-4: `LessonService.getLesson` → отдельные SELECT subject/
      room/group. 50 lessons × 3 queries = 150 SELECTs.
    - 02 P2-3: N+1 при загрузке group-members через subject.
    - `@EntityGraph(attributePaths)` для list — один JOIN для всей
      страницы. DTO projection для mobile detail screen — только
      нужные поля, минимум данных по сети.
  - **Scope:**
    - List-endpoints (schedule getWeek, attendance getWeekReport,
      academic getGroupMembers):
      ```java
      @EntityGraph(attributePaths = {"subject","group","room"})
      List<Lesson> findAllByDateBetween(Pageable p, LocalDate from, LocalDate to);
      ```
    - Single-detail (mobile lesson screen):
      ```java
      interface LessonDetailsProjection {
          Long getId();
          String getSubjectName();
          String getRoomLabel();
          LocalTime getStartTime();
          LocalTime getEndTime();
      }
      @Query("SELECT l.id AS id, l.subject.name AS subjectName, ... FROM Lesson l WHERE l.id = :id")
      LessonDetailsProjection findLessonDetails(@Param("id") Long id);
      ```
    - Audit всех repository-методов: grep `findAll`, `findBy*` →
      проверить что либо Pageable+EntityGraph, либо projection.
  - **Каскад:**
    - 02 P2-3, 03 P2-4 → 🔧 TO-FIX.
    - 02 P0-7 (homework Pageable + @EntityGraph) — тот же паттерн.
    - QD2 coverage-gate — новые repository-методы unit-тестируются.
    - NEW-143 ArchUnit-правило.
  - **Estimate:** ~1 день (audit × 3 сервиса + 5-10 методов + тесты).
  - **NEW-143:** ArchUnit-правило «repository-метод возвращает
    коллекцию entity → либо Pageable, либо @EntityGraph, либо
    projection». Часть NEW-109 (ArchUnit framework). Автоматически
    ловит N+1 в PR.

- **P2-10/3 — Caffeine cache для справочников и RBAC:** **(a)**
  Spring `@Cacheable` + Caffeine (in-memory, TTL+size). Single-instance
  ok для v0.0.0.
  - **Мотивация:**
    - 03 P2-7: `getActiveSemester()` ~10+ req/day на то же значение.
    - 03 P2-6, 04 P2-2: synchro gRPC `isHeadmanFor` / `authorizeHeadmanOrTeacher`
      за каждый запрос — ~hundred per minute в peak.
    - 02 P2-5: Subject/Group кэш в памяти без TTL — memory leak при
      долгом uptime.
    - 02 P2-7: RBAC без кэша.
    - Caffeine — zero dependencies (в Spring Boot starter), TTL+size
      нативно.
  - **Scope (v0.0.0):**
    - Caffeine config в shared-web (или per-service):
      ```java
      @EnableCaching
      @Configuration
      public class CacheConfig {
          @Bean CacheManager cacheManager() {
              var cm = new CaffeineCacheManager("semester", "subject", "group", "rbac");
              cm.setCaffeine(Caffeine.newBuilder()
                  .maximumSize(1000)
                  .expireAfterWrite(Duration.ofMinutes(5)));
              return cm;
          }
      }
      ```
    - Cache-specific TTLs:
      - `semester` (current): 5 мин. @CacheEvict на
        activateSemester (Q13b race mitigation — TTL короче админ-
        action-latency).
      - `subject`, `group` (metadata): 10 мин. @CacheEvict на
        update/delete.
      - `rbac` (isHeadman, groupMembership): 1 мин. Короче т.к.
        админ может изменить is_headman в любой момент.
    - `@Cacheable("semester") Semester getActiveSemester()`.
    - `@Cacheable(value="rbac", key="#userId + ':' + #groupId")
      boolean isHeadmanFor(Long userId, Long groupId)`.
  - **Watch-out:**
    - Q13b race activateSemester — TTL 5 мин = окно рассинхронизации.
      Если admin активирует новый семестр, 5 минут часть сервисов
      видит старый. Accept, т.к. single-admin invariant + активация
      редкая (раз в семестр).
    - Multi-instance scale-out (v0.1) сломает консистентность.
      Миграция на Redis (тип c) при scale-out. Документируется.
  - **Каскад:**
    - 02 P2-5/P2-7, 03 P2-6/P2-7, 04 P2-2 → 🔧 TO-FIX.
    - Q13b → documented race window.
    - QC6 (stats aggregate 60s cache) — тот же Caffeine pattern.
    - P2-10/8 (hot-path gRPC) — существенно закрывается этим кэшем.
    - NEW-144 (caching-strategy.md).
  - **Estimate:** ~1 день (audit + cache-annotations + invalidation
    + integration-тесты на cache hit/miss).
  - **NEW-144:** `docs/caching-strategy.md` — какие cache'и, TTL,
    invalidation triggers, trade-offs консистентности. Раздел
    «Miграция на Redis при multi-instance» (ссылка на v0.1).

- **P2-10/4 — Batch-операции (backend + frontend):** **(c) Оба** —
  backend `POST /batch` endpoints + frontend один вызов вместо loop.
  - **Мотивация:**
    - 09 P2-11: `HeadmanLessonSheet.handleBulkMark` — 30 await'ов
      последовательно = 6 сек блокировки UI.
    - 10 P2-14: `HeadmanWeeklyJournal.loadWeek` — N параллельных
      `getLessonAttendance`.
    - 02 P2-6: нет batch create homework (admin импорт делает loop).
    - Batch endpoint = одна транзакция = атомарность (либо все
      отметки headman'а сохранены, либо никакие).
    - Partial-success response 202 + detailed results — клиент видит
      «29/30 ok, lesson#17 conflict».
  - **Scope (v0.0.0):**
    - `POST /api/attendance/marks/batch` — body `List<MarkRequest>`,
      response `207 Multi-Status` или `202 Accepted` с
      `List<MarkResult { lessonId, status, error? }>`.
    - `POST /api/academic/homeworks/batch` — аналогично.
    - Frontend: один HTTP-запрос вместо 30. Loading state — один
      progress indicator.
    - Validation: per-element через `@Valid @Size(max=100)
      List<@Valid MarkRequest>` (P2-4/5). P2-3/2 `invalid-params[]`
      с индексом `name = "marks[3].lessonId"`.
  - **Трансakционная семантика:**
    - Attendance bulk-mark: **атомарный** (все или никто) —
      headman-action.
    - Homework batch: **partial-success** (import файла, некоторые
      rows могут быть дубликатами) — caller сам разбирает результаты.
    - Определяется per-endpoint, документируется в OpenAPI.
  - **Каскад:**
    - 09 P2-11, 10 P2-14, 02 P2-6 → 🔧 TO-FIX через (c).
    - P2-3/2 (invalid-params[]) для batch-validation.
    - P2-4/5 (element constraints на списках).
    - P2-10/6 (connection pool) — batch снижает давление на pool.
    - QC1 (unified NotificationCenter) — STOMP event после batch
      notification.
  - **Estimate:** ~1 день (2 endpoint'а + partial-success schema +
    frontend refactor × 2 клиента).
  - **NEW-145:** раздел «Batch endpoint conventions» в
    `docs/api/api-error-conventions.md` (NEW-132): HTTP status для
    атомарных vs partial-success, schema `MarkResult`, как клиент
    обрабатывает 207 Multi-Status.

- **P2-10/5 — SQL-aggregate вместо in-memory stream:** **(a)**
  Переписать отчёты на aggregate-запросы.
  - **Мотивация:**
    - 03 P2-5: `LessonService` stream over all lessons для поиска
      one-off → GC pressure при 10k+ lesson записях.
    - attendance `ReportService`: `.collect(toList()) + stream.filter`
      вместо `SELECT COUNT(*) ... GROUP BY`.
    - 10 P2-4: admin-dashboard sparklines фейковые — должны быть
      реальные aggregate-queries.
    - Memory-footprint: 1 DTO-строка (result) vs 10k-100k entities.
    - SQL-aggregate использует indexes (P2-10/1) эффективно.
  - **Scope:**
    - Attendance stats: `SELECT status, COUNT(*) FROM attendance
      WHERE group_id=? AND date BETWEEN ? AND ? GROUP BY status`.
    - Excuse analytics: aggregate по ExcuseStatus.
    - Admin-dashboard sparklines (10 P2-4, уже в scope NEW-94):
      реальные metrics через SQL или Prometheus — решено в QC7.
    - Schedule one-off search: SQL WHERE вместо stream.filter.
    - Audit всех `.collect(toList())` в service-слое где >50 строк
      результата.
  - **Что делается:**
    - Repository-методы с JPQL `@Query` returning DTO projection.
    - `@Query("SELECT new AttendanceStatsDto(a.status, COUNT(a))
      FROM Attendance a WHERE ... GROUP BY a.status")`.
    - Integration-тесты: проверяют корректность агрегации + query
      time < 100ms.
  - **Каскад:**
    - 03 P2-5 → 🔧 TO-FIX.
    - QC7 (sparklines real metrics) → уже закрывался через NEW-94,
      этот ответ подтверждает подход.
    - QC6 (stats aggregate endpoint + cache) — тот же паттерн.
    - P2-10/1 (indexes) — aggregate queries получают выгоду от
      composite indexes.
  - **Estimate:** ~1 день (audit + 4-5 repository aggregate-методов
    + integration-тесты).
  - **NEW-146:** audit checklist «service-метод делает
    `.collect(toList())` для агрегации → переписать на SQL-aggregate».
    Часть NEW-108 contributing.md. Manual audit, не ArchUnit (сложно
    формализовать).

- **P2-10/6 — Connection pool tuning:** **(c)** Явно выставить
  HikariCP для academic/schedule/attendance, остальное accept defaults.
  - **Мотивация:**
    - 03 P2-9: HikariCP default `maximum-pool-size=10` — при
      bulk-mark 30 параллельных queries часть stall'ится.
    - P2-10/4 batch → больше concurrent queries → нужен больший pool.
    - Mongo/Redis defaults (100 per host, shared event-loop) ok для
      ожидаемой нагрузки.
    - Явный конфиг + комментарий — документация для будущих
      разработчиков.
  - **Scope:**
    - academic-service, schedule-service, attendance-service yml:
      ```yaml
      spring:
        datasource:
          hikari:
            maximum-pool-size: 20
            minimum-idle: 5
            connection-timeout: 5000   # 5s
            idle-timeout: 600000        # 10 min
            max-lifetime: 1800000       # 30 min
            leak-detection-threshold: 60000  # 1 min (warning)
      ```
    - Обоснование формулой `cpu_cores × 2 + effective_disk_spindles`
      — для VPS 4 cores SSD = ~10, но с async tasks + scheduled +
      batch endpoints — запас до 20.
    - auth-service, notification-service — Redis/memory, без JDBC-
      pool.
  - **Что делается:**
    - yml-правки × 3 сервиса.
    - Prometheus-метрики `hikaricp_connections_active` /
      `hikaricp_connections_pending` уже экспортируются автоматом.
    - Grafana alert: «pool_usage > 80% for 5m» → Telegram админу
      (через bot webhook, NEW-62).
  - **Каскад:**
    - 03 P2-9 → 🔧 TO-FIX.
    - P2-10/4 (batch) — работает с pool=20.
    - QA4 (бизнес-метрики) + NEW-62 (bot webhook) — alert routes.
    - NEW-147 (connection-pool-tuning.md).
  - **Estimate:** ~2 часа (3 yml + alert + smoke-тест).
  - **NEW-147:** `docs/connection-pool-tuning.md` — формулы, текущие
    значения, Grafana alert rules. Раздел «Когда пересматривать»
    (при изменении CPU cores, scale-out, введении read-replicas).

- **P2-10/7 — Cleanup старых push-подписок (+audit refresh-token
  TTL):** **(a)** `@Scheduled` weekly cleanup + `last_seen` tracking.
  - **Мотивация:**
    - 05 P2-7: push_subscriptions растут бесконечно для разлогиненных
      устройств. Web Push retry-queue засоряется failed endpoint'ами.
    - Auth refresh-tokens — Redis EX, audit подтвердить TTL=7d.
    - late-checkin requests — бизнес-history, accept retention.
    - Scheduled + ShedLock (NEW-28) — not double-run при multi-instance.
  - **Scope:**
    - Schema change: добавить `push_subscriptions.last_seen
      TIMESTAMPTZ NOT NULL DEFAULT NOW()`. Flyway V{N+1}.
    - Update `last_seen = NOW()` в `WebPushDeliveryService` при
      successful send (не на 410 Gone — сразу delete).
    - `@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock(name="cleanupStalePushSubs")
      void cleanupStalePush()`:
      ```sql
      DELETE FROM push_subscriptions
       WHERE last_seen < NOW() - INTERVAL '90 days';
      ```
    - Audit auth-service: подтвердить `redis.set("refresh:<hash>",
      uid, EX=604800)` (7d). Если нет EX — фикс.
    - `docs/data-retention-policy.md` — таблица «что хранится →
      retention → cleanup mechanism».
  - **Каскад:**
    - 05 P2-7 → 🔧 TO-FIX.
    - NEW-28 (ShedLock audit) — новый @Scheduled покрыт.
    - QA5 (retention 14d для логов) — общий подход к retention.
    - Redis keyspace (NEW-45) — refresh-token TTL документируется.
  - **Estimate:** ~3 часа (Flyway migration + service update +
    @Scheduled + тесты).
  - **NEW-148:** `docs/data-retention-policy.md` — объединяет
    retention push_subs (90d), refresh-tokens (7d), attendance-marks
    (accept - history), excuse-tickets (accept - history), OTP
    (5 min TTL). Триггер пересмотра (связано с NEW-66).

- **P2-10/8 — Hot-path gRPC: cache + параллелизация + метрики:**
  **(c)** Кэш (P2-10/3) + `CompletableFuture` для checkin + grpc-
  micrometer.
  - **Мотивация:**
    - 04 P2-1: `CheckinService.checkin` synchro schedule + geofence =
      latency a+b. Параллелизация → max(a, b).
    - 04 P2-2, 03 P2-6: synchro `authorizeHeadmanOrTeacher` /
      `isHeadmanFor` — решается P2-10/3 cache.
    - Без метрик gRPC не видно p50/p95/p99, где узкое место.
    - Micrometer `grpc-micrometer` — zero code, `counter` + `timer`
      для каждого RPC.
  - **Scope:**
    - Параллелизация в `CheckinService`:
      ```java
      var scheduleTask = CompletableFuture.supplyAsync(
          () -> scheduleClient.getLesson(lessonId), taskExecutor);
      var userTask = CompletableFuture.supplyAsync(
          () -> academicClient.getUser(userId), taskExecutor);
      CompletableFuture.allOf(scheduleTask, userTask).join();
      var lesson = scheduleTask.get();
      var user = userTask.get();
      ```
    - `grpc-micrometer` dependency + auto-configure via
      `@GrpcClientInterceptor`.
    - Grafana dashboard «gRPC latency by method» (Histogram) +
      error-rate panel.
    - Timeout: явный `deadline` на каждом gRPC-call (3s default)
      вместо default (no deadline).
  - **Каскад:**
    - 04 P2-1/P2-2, 03 P2-6 → 🔧 TO-FIX частично через P2-10/3
      (cache), часть через параллелизацию.
    - QA2 (tracing Sleuth) — span-level latency уже видна,
      Micrometer даёт aggregate-percentiles.
    - NEW-58 (observability.md) — раздел gRPC dashboard.
    - NEW-149 (gRPC deadlines).
  - **Estimate:** ~1 день (parallelism refactor + Micrometer +
    Grafana dashboard + deadline audit).
  - **NEW-149:** audit всех gRPC-вызовов → явный `.withDeadline(
    Deadline.after(3, SECONDS))`. Без deadline зависший сервис
    блокирует caller бесконечно. CI-lint «gRPC-call без deadline = fail»
    (Checkstyle или ArchUnit custom rule).

**Итого P2-10 (8 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-10:** ~5-6 человеко-дней (3ч + 1д + 1д +
  1д + 1д + 2ч + 3ч + 1д = ~40-48 часов).
- **Новые доки:** `performance-indexes.md` (NEW-142), `caching-strategy.md`
  (NEW-144), `connection-pool-tuning.md` (NEW-147),
  `data-retention-policy.md` (NEW-148).
- **ArchUnit правила +1:** NEW-143 (repo collection → Pageable/
  @EntityGraph/projection).
- **CI-lint +1:** NEW-149 (gRPC deadline required).
- **Новых NEW-задач:** 8 (NEW-142..149).
- **Auto-resolves:** 02 P2-3/P2-4/P2-5/P2-6/P2-7, 03 P2-2/P2-4/P2-5/
  P2-6/P2-7/P2-8/P2-9, 04 P2-1/P2-2/P2-9, 05 P2-7, 09 P2-11, 10 P2-14.

---

## P2 — Группа 9 (Docker/compose nits)

Ответы на 9 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: QD1 (`:latest` → SHA+semver), QD4 (digest для cadvisor/
promtail), QA4+NEW-62 (bot webhook alerting), QA5 (retention 14д),
Q16b (shared-DB accepted), C0-9 (ротация секретов).

Audit покрыл: healthcheck'и, pin observability-образов, nginx-лимиты,
Loki retention, dangling alertmanager config, mongo unused user,
postgres shared password, dev/prod passwords, JVM resource limits.

- **P2-9/1 — Healthcheck + curl в alpine-образах:** **(a)**
  `HEALTHCHECK` в Dockerfile + `curl` в alpine для всех 5 backend-
  сервисов, единообразно.
  - **Мотивация:**
    - 05 P2-1: notification Dockerfile на alpine-jre без wget/curl,
      healthcheck false-positive.
    - 07 P2-6: gateway без `HEALTHCHECK` вообще.
    - Метаданные образа живут вместе с образом (docker run без compose
      тоже работает).
    - curl полезен для debug exec'ов.
  - **Что делается:**
    - Каждый `Dockerfile` backend-сервиса:
      ```dockerfile
      RUN apk add --no-cache curl
      HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
          CMD curl -fsS http://localhost:${PORT}/actuator/health || exit 1
      ```
    - `docker-compose.prod.yml`: `depends_on: { academic-service: {
      condition: service_healthy } }` для gateway (ждёт когда backend
      healthy перед стартом).
    - Python-бот: отдельный шаблон (pip install), healthcheck через
      `curl http://localhost:9097/health` (если есть метрика-endpoint
      Prometheus) или `pgrep -f "aiogram" || exit 1`.
  - **Каскад:**
    - 05 P2-1, 07 P2-6 → 🔧 TO-FIX.
    - QD1 (SHA tagging) — консистентный Dockerfile шаблон облегчает
      audit.
    - NEW-150 (dockerfile-conventions.md).
  - **Estimate:** ~2 часа (5 Dockerfile + compose depends_on +
    smoke-тест `docker-compose up --wait`).
  - **NEW-150:** `docs/operations/deploy/dockerfile-conventions.md` — общий шаблон FROM,
    USER (non-root), WORKDIR, HEALTHCHECK, labels, security-best-practices
    (apk install без `update`, multi-stage builds). Часть NEW-108
    contributing.md.

- **P2-9/2 — Pin observability-образов:** **(a+b) mixed** — semver+
  Renovate для Grafana/Prometheus/Loki, digest для cadvisor/promtail
  (QD4).
  - **Мотивация:**
    - 13 P2-2: все observability-образы на `:latest`.
    - Loki major-upgrade ломает schema → data-loss или боль migration.
    - Renovate auto-merge patch для security-patches Grafana/Prometheus.
    - cadvisor privileged + promtail docker.sock — supply-chain
      concern, digest обязательно.
  - **Что делается:**
    - `docker-compose.prod.yml`:
      ```yaml
      prometheus:
        image: prom/prometheus:v2.55.0  # Renovate-managed
      grafana:
        image: grafana/grafana:11.3.0
      loki:
        image: grafana/loki:3.2.0
      cadvisor:
        image: gcr.io/cadvisor/cadvisor@sha256:<digest>  # digest pin (QD4)
      promtail:
        image: grafana/promtail@sha256:<digest>
      ```
    - `renovate.json`: policy `auto-merge: patch` для app-images и
      Grafana/Prometheus/Loki. `manual` для Loki major (schema).
    - `semverCoerce` для Loki: minor-upgrade = manual (data-dir compat
      ревью).
  - **Каскад:**
    - 13 P2-2 → 🔧 TO-FIX.
    - QD1 (app SHA+semver) — аналогичный подход.
    - QD4 (cadvisor/promtail digest) — подтверждается.
    - NEW-102 (container-trust.md) — обновить таблицу pinning strategy.
    - NEW-151 (runbook Loki major upgrade).
  - **Estimate:** ~1 час (compose правка + Renovate config).
  - **NEW-151:** `docs/operations/runbooks/loki-major-upgrade.md` — процедура
    для major-версии: backup data, schema check, canary, rollback.
    Применяется при Renovate PR для Loki major.

- **P2-9/3 — Nginx client_max_body_size per-location:** **(a)**
  Per-location лимиты, глобально 2m.
  - **Мотивация:**
    - 13 P2-3: глобально 12m — DoS-вектор через flood больших POST
      на `/api/auth/login` (<1KB достаточно).
    - Excuse-upload нуждается в 25m (10MB файл + multipart overhead).
    - P2-4/6 @ValidFile — второй заслон на backend.
  - **Что делается:**
    - `infra/nginx/nginx.conf`:
      ```nginx
      http {
          client_max_body_size 2m;  # default, strict
          client_body_buffer_size 128k;
          # ... other global settings
      }
      server {
          location /api/attendance/excuse/ {
              client_max_body_size 25m;  # file upload
              proxy_pass http://gateway;
          }
          location /api/academic/users/ {
              # avatar upload на /users/{id}/avatar
              client_max_body_size 5m;
              proxy_pass http://gateway;
          }
          # rest — default 2m
      }
      ```
    - Комментарий в conf: «Больше — через @ValidFile P2-4/6 backend
      check + ATTENDANCE_EXCUSE_MAX_FILE_SIZE ConfigurationProperties».
  - **Каскад:**
    - 13 P2-3 → 🔧 TO-FIX.
    - P2-4/6 (@ValidFile MIME) — backend уровень.
    - NEW-140 (magic-byte MIME check, future-ideas).
    - NEW-152 (nginx-conf review checklist).
  - **Estimate:** ~30 мин (nginx.conf правка + comment + integration-
    тест на `curl -F` 26MB файлом → 413).
  - **NEW-152:** `docs/operations/deploy/nginx-config.md` — review checklist для nginx:
    per-location limits, rate-limit zones, timeouts, security headers
    (C0-6 CSP), gzip, cache-control. Ссылки в NEW-55 (PWA CSP) и
    NEW-56 (CI-check headers).

- **P2-9/4 — Loki retention:** **(c) ACCEPT 14д** (консистентно с QA5).
  - **Мотивация:**
    - QA5 уже принял 14д для app-метрик и логов. Не переоткрываем.
    - Инцидент через 2+ недели — accept trade-off. Бизнес-критичные
      события (excuse-approved, lesson-closed) хранятся в БД как
      историческая запись, не в логах.
    - Loki storage = 14д × ~2GB/день ≈ 28GB. Умещается на VPS.
  - **Что делается:**
    - `infra/loki/loki.yml`: `retention_period: 336h` (14д).
    - `compactor.retention_enabled: true` + `retention_delete_delay: 2h`.
    - Grafana alert «loki_ingester_chunks_stored_total rate» для
      предупреждения аномальных скачков объёма (подозрительно много
      логов = либо DEBUG включили в prod, либо атака flood).
  - **Каскад:**
    - 13 P2-9 → ✅ ACCEPTED (14д, не 30д).
    - QA5 — применяется к Loki тоже.
    - NEW-58 (observability.md) — раздел retention + storage estimate.
  - **Estimate:** ~15 мин (loki.yml + comment).
  - **Никаких новых NEW** — QA5 покрывает.

- **P2-9/5 — Alertmanager unified router:** **(c)** Запустить
  Alertmanager как общий router для Prometheus и Loki alerts, bot
  webhook как receiver.
  - **Мотивация:**
    - Dangling config (13 P2-10) `ruler.alertmanager_url` — Loki
      log-based alerts не доставляются.
    - Bot webhook (QA4+NEW-62) сейчас — endpoint для Prometheus raw
      alerts. Нет grouping/silencing — один инцидент = 100 сообщений
      в ЛС админу.
    - Alertmanager = маленький контейнер (~30MB), unified routing с
      grouping/silencing/inhibition → один alert-группа = одно
      сообщение.
    - Log-based алерты (NEW-113 JWT-в-логах, OOME pattern, malformed
      event rate) — Loki-ruler → Alertmanager → bot webhook.
    - Unified pipeline: Prometheus → Alertmanager, Loki-ruler →
      Alertmanager → bot webhook receiver.
  - **Что делается:**
    - Новый контейнер `alertmanager: prom/alertmanager:v0.27.0` в
      `docker-compose.prod.yml`. Network `private_net`. Not exposed
      наружу.
    - `infra/alertmanager/alertmanager.yml`:
      ```yaml
      route:
        receiver: 'bot-webhook'
        group_by: ['alertname', 'service', 'severity']
        group_wait: 30s          # ждать 30с после первого алерта
        group_interval: 5m       # интервал группы
        repeat_interval: 4h      # не спамить повторно чаще 4ч
        routes:
          - match: { severity: critical }
            receiver: 'bot-webhook'
            repeat_interval: 1h  # критичные — чаще
      receivers:
        - name: 'bot-webhook'
          webhook_configs:
            - url: 'http://notification-bot:9097/internal/alert'
              send_resolved: true
      inhibit_rules:
        - source_match: { severity: critical }
          target_match: { severity: warning }
          equal: ['alertname', 'service']
      ```
    - Prometheus config (NEW-62 bot-webhook endpoint меняется на
      Alertmanager):
      ```yaml
      alerting:
        alertmanagers:
          - static_configs:
              - targets: ['alertmanager:9093']
      ```
    - Loki `ruler`:
      ```yaml
      ruler:
        alertmanager_url: http://alertmanager:9093
        enable_alertmanager_v2: true
        storage:
          type: local
          local:
            directory: /loki/rules
      ```
    - Бот `/internal/alert`: парсит Alertmanager webhook format
      (массив alerts в payload, grouping labels), форматирует в
      человеко-читаемое сообщение для Telegram. Schema:
      ```json
      {
        "version": "4",
        "groupKey": "...",
        "status": "firing|resolved",
        "receiver": "bot-webhook",
        "alerts": [
          {"status": "firing", "labels": {...}, "annotations": {...}, "startsAt": "..."}
        ]
      }
      ```
    - Example log-based alert для NEW-113 (JWT в логах):
      ```yaml
      # infra/loki/rules/security.yml
      groups:
        - name: security
          rules:
            - alert: JwtTokenLeakedInLogs
              expr: |
                count_over_time({container=~"rct-.*"}
                  |~ `Bearer eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.` [5m]) > 0
              for: 1m
              labels: { severity: critical }
              annotations:
                summary: "JWT token found in container logs"
      ```
  - **Каскад:**
    - 13 P2-10 → 🔧 TO-FIX.
    - QA4 + NEW-62 — endpoint `/internal/alert` остаётся, но payload-
      schema меняется на Alertmanager webhook. Бот-хендлер
      переписывается на Alertmanager format.
    - NEW-113 (JWT-в-логах alert) — становится реальным Loki-rule.
    - NEW-63 (alerts.md runbook) — расширяется секцией Alertmanager
      rules + grouping + silencing.
    - NEW-64 (тихий час) — реализуется через Alertmanager `time_intervals`
      + `mute_time_intervals` (native feature, не custom в боте).
    - NEW-65 (baseline 2 недели) — Alertmanager silencing на первые
      2 недели, потом включаем rules постепенно.
    - NEW-153 (alertmanager config docs).
    - NEW-154 (bot webhook schema migration).
  - **Estimate:** ~1 день (Alertmanager контейнер + config + Prometheus
    rerouting + Loki ruler setup + bot webhook schema update + 2-3
    начальных rule'а + smoke-тест).
  - **NEW-153:** `infra/alertmanager/alertmanager.yml` + `docs/operations/monitoring/alerts.md`
    (NEW-63 расширение) — routing tree, grouping labels, inhibition
    rules, silencing procedures. Runbook «как добавить новый alert».
  - **NEW-154:** bot `/internal/alert` endpoint — переход на
    Alertmanager webhook schema. Документировать в `websocket-protocol.md`
    нет — это HTTP webhook, отдельный доки-пункт. Либо дополнение
    в NEW-62 spec.

- **P2-9/6 — Unused mongo user:** **(b) KEEP** notification_db init
  оставить на будущее (когда notification-service получит свою БД).
  - **Мотивация:**
    - Q16b (shared-DB accepted) — push_subscriptions мигрируют в
      attendance_db.
    - Но архитектурно notification — отдельный bounded-context, v0.1+
      может получить own DB (для web-push-history, in-app-notifications
      persistence).
    - Удаление + повторное добавление = лишний churn.
    - dangling user роль в Mongo не даёт security-риска (readWrite
      только на notification_db, которая пока пуста).
  - **Что делается:**
    - Оставить `init-mongo.js` как есть.
    - Добавить комментарий: `// notification_db reserved for future
      notification-service own persistence (v0.1+). Currently
      push_subscriptions in attendance_db (Q16b shared-DB pattern).`
  - **Каскад:**
    - 13 P2-11 → ✅ ACCEPTED (KEEP как reserved).
    - Q16b (shared-DB accepted) — не противоречит.
    - NEW-36 (shared-DB patterns в architecture.md) — раздел «reserved
      capacity для future services».
  - **Estimate:** ~5 мин (комментарий).

- **P2-9/7 — POSTGRES_ACADEMIC_PASSWORD shared:** **(b) ACCEPT** —
  один admin-пароль на auth/academic/schedule, ротация = одновременный
  deploy (acceptable).
  - **Мотивация:**
    - Single-admin operator model (Q13b activateSemester) — ротация
      выполняется один раз, координированно.
    - Ротация раз в квартал (C0-9) — acceptable одновременный рестарт.
    - Per-user + per-db GRANTs (вариант a) — +4 часа работы, сложнее
      рецепт бекапа/восстановления (GRANT-ы отдельно).
    - Blast-radius: утечка secrets из одного контейнера = доступ к
      трём БД, но все три контейнера в одной docker-сети; атакующий с
      доступом к контейнеру academic уже имеет локальный доступ к
      auth/schedule по сети → per-user GRANT не защищает (он просто
      логинится как auth_user).
    - Реальная защита blast-radius = docker network isolation, а не
      per-user password.
  - **Что делается:**
    - Документировать в `docs/security-model.md` (NEW-114):
      «single POSTGRES_ACADEMIC_PASSWORD accepted as trade-off.
      Mitigation: docker network isolation + secret rotation quarterly.»
    - Ротация раз в квартал → NEW-23 (maintenance window runbook)
      + NEW-97 (retention GHCR).
    - Audit `.env.prod` после ротации: все три сервиса получают
      обновлённое значение одновременно (deploy.yml restart all).
  - **Каскад:**
    - 13 P2-12 → ✅ ACCEPTED.
    - C0-9 (ротация секретов) — quarterly runbook.
    - NEW-114 (security-model.md logging hygiene) — раздел «shared
      credentials trade-offs».
    - NEW-155 (quarterly rotation runbook).
  - **Estimate:** ~30 мин (docs + runbook).
  - **NEW-155:** `docs/operations/runbooks/secret-rotation.md` — quarterly
    procedure: rotate `POSTGRES_ACADEMIC_PASSWORD`, `BOT_TOKEN`,
    `GHCR_TOKEN`, `VAPID_PRIVATE_KEY`, `JWT_SECRET`. Downtime estimate
    = ~2 мин per rolling restart. Checklist.

- **P2-9/8 — Dev .env одинаковые пароли:** **(b) ACCEPT** — dev
  insecure by design, integration-тесты через Testcontainers.
  - **Мотивация:**
    - Dev-среда ≠ RBAC testing env. RBAC проверяется в интеграционных
      тестах, не через docker-compose.
    - Testcontainers генерирует уникальные creds per test-run →
      realistic security-testing.
    - `.env` (dev) — developer convenience, одинаковый `rct_dev_pass`
      снижает trial-error для newcomers.
    - Документировать в README чтобы не путать с prod-настройкой.
  - **Что делается:**
    - `.env` (dev) — оставить как есть, `rct_dev_pass` для всех.
    - README: «**ВНИМАНИЕ:** `.env` содержит dev-пароли (одинаковые).
      Для RBAC-тестирования используется Testcontainers (см.
      `/docs/testing.md`). `.env.prod` — отдельный файл с
      индивидуальными паролями, не commit'ится.»
    - NEW-22 (inline-comments в .env.prod.example) — расширить
      комментарий про dev vs prod структуру.
  - **Каскад:**
    - 13 P2-6 → ✅ ACCEPTED.
    - QD2 (coverage-gate + Testcontainers) — real security testing.
    - NEW-22 (env inline-comments).
    - NEW-156 (testing.md).
  - **Estimate:** ~10 мин (README update).
  - **NEW-156:** `docs/testing.md` — стратегия: unit (mockito) /
    integration (Testcontainers) / e2e (через docker-compose). Раздел
    «RBAC integration tests» — paradigm: Testcontainers с уникальными
    creds per test run, НЕ через `.env`.

- **P2-9/9 — JVM resource limits + restart policies:** **(c)** Явные
  limits для JVM backend, БД accept (self-tune), `unless-stopped`
  везде.
  - **Мотивация:**
    - Без `-Xmx` / MaxRAMPercentage JVM ест ~25% RAM host'а по default
      = ~1GB каждый backend × 5 = 5GB на VPS 4GB → OOM.
    - docker-compose без `deploy.resources.limits` = нет lower-bound
      на sibling services (один багги сервис забивает RAM, все
      рушатся).
    - Postgres/Mongo/Redis — self-tune (shared_buffers, maxmemory), не
      требуют external limits. Accept.
    - `unless-stopped` — Docker best practice: при admin-shutdown не
      рестартятся, при краше — да.
  - **Что делается:**
    - `docker-compose.prod.yml` для backend-сервисов:
      ```yaml
      academic-service:
        image: ...
        environment:
          JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
        deploy:
          resources:
            limits:
              memory: 512M
              cpus: "1.0"
            reservations:
              memory: 256M
        restart: unless-stopped
      ```
    - `notification-web`, `api-gateway`, `notification-bot`: 256M (без
      heavy JPA entity-кэша).
    - `academic-service`, `schedule-service`, `attendance-service`: 512M.
    - `auth-service`: 256M (Redis-only, Spring context минимальный).
    - БД accept (но restart: unless-stopped):
      ```yaml
      postgres-academic:
        restart: unless-stopped
        # нет deploy.resources.limits — Postgres сам управляет через
        # shared_buffers (см. postgresql.conf)
      ```
    - Prometheus alert: `container_memory_usage_bytes /
      container_spec_memory_limit_bytes > 0.9 for 5m` → warning
      через Alertmanager → bot.
  - **VPS resource budget (4GB RAM):**
    - 5 backend × ~400M (avg) = 2GB
    - Postgres ×2 × 256M = 512M
    - Mongo 256M
    - Redis 64M
    - RabbitMQ 256M
    - Prometheus/Grafana/Loki/Alertmanager × ~100M = 400M
    - OS + overhead = 500M
    - **Total: ~4GB** — умещается, но tight. При peak load need to
      scale up RAM.
  - **Каскад:**
    - 03 P2-9 (connection pool) → зависит от RAM budget (P2-10/6
      Hikari 20 connections ok для 512M JVM heap).
    - P2-9/5 (Alertmanager) → memory usage alerts через Alertmanager.
    - NEW-147 (connection-pool-tuning.md) → добавить раздел «RAM
      budget per service».
    - NEW-157 (resource-limits.md).
  - **Estimate:** ~2 часа (compose правки × 5 сервисов + smoke-тест
    под нагрузкой + Prometheus alert rule).
  - **NEW-157:** `docs/operations/deploy/resource-limits.md` — VPS budget, per-service
    memory allocation, JVM flags, alerts. Раздел «Когда scale-up»
    (triggers: consistent 80%+ memory, p99 latency degradation).

**Итого P2-9 (9 вопросов):** 5 совпали с рекомендациями ((a) для /1,
/2, /3; (b)/(c) для /4 (accept); (c) для /9), владелец уточнил
P2-9/5 → (c) Alertmanager, P2-9/6/7/8 → (b) accept.

- **Суммарный estimate P2-9:** ~4-5 человеко-дней (~32-40 часов).
  Большая часть — Alertmanager (P2-9/5, 1д) + resource limits audit
  (P2-9/9, 2ч) + Dockerfile conventions (P2-9/1, 2ч).
- **Новый контейнер:** `alertmanager` (P2-9/5) — первый новый infra-
  контейнер с P0 работы.
- **Новые доки:** `dockerfile-conventions.md` (NEW-150),
  `runbooks/loki-major-upgrade.md` (NEW-151), `nginx-config.md`
  (NEW-152), `alerts.md` расширение (NEW-153), bot webhook schema
  migration (NEW-154), `runbooks/secret-rotation.md` (NEW-155),
  `testing.md` (NEW-156), `resource-limits.md` (NEW-157).
- **Изменения в уже принятых:** QA4+NEW-62 (bot webhook format =
  Alertmanager webhook schema), NEW-63 (alerts.md), NEW-64 (тихий
  час = Alertmanager `mute_time_intervals`), NEW-65 (baseline через
  silencing).
- **Новых NEW-задач:** 8 (NEW-150..157).
- **Auto-resolves:** 05 P2-1, 07 P2-6, 13 P2-2/P2-3/P2-9/P2-10/P2-11/
  P2-12/P2-6.
- **ACCEPT:** 13 P2-9 (14д retention), 13 P2-11 (reserved mongo-user),
  13 P2-12 (shared postgres password), 13 P2-6 (dev одинаковые).

---

## P2 — Группа 8 (Test gaps)

Ответы на 8 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: QD2 (coverage-gate 60/50/50 + diff 80%), QD3 (contract events),
NEW-31 (SecurityIdorIT), NEW-52/53 (event-schemas late-checkin +
shared conftest.py), NEW-109 (ArchUnit framework), NEW-143 (N+1 rule),
NEW-156 (testing.md strategy).

Audit 14-tests-audit.md показал 15 P2: mixed именование *Test vs *IT,
мок-heavy integration-тесты (36 @MockitoBean в IT), отсутствие
reminders/week-parity/e2e/load тестов, Flyway MigrationIT только в
academic, TMA HMAC не тестируется, CSRF/CSP тесты отсутствуют.

- **P2-8/1 — Naming + Gradle task-split:** **(b)** переименование без
  нового source-set + Gradle `filter { includeTestsMatching "*IT" }`
  + ArchUnit rule.
  - **Мотивация:**
    - 14 P2-1: 17 `*IT`, 48 `*Test` где многие — real IT.
    - (a) новый source-set = большой refactor (перенос файлов × 5).
    - (b) minimum effort: правильное именование + 2 Gradle task'а
      (`test` = unit, `integrationTest` = IT). Dev-цикл быстрый
      (`./gradlew test` — только unit, ~30 сек).
    - ArchUnit (NEW-109 +1 rule) — gate на будущее.
  - **Что делается:**
    - Audit `*Test` → если `@SpringBootTest` / `@DataJpaTest` /
      `@WebMvcTest` / Testcontainers / `@AutoConfigureMockMvc` →
      переименовать в `*IT`.
    - `build.gradle.kts` (shared-conventions или per-service):
      ```kotlin
      tasks.register<Test>("integrationTest") {
          description = "Runs integration tests (*IT.java)"
          group = "verification"
          useJUnitPlatform()
          filter { includeTestPatterns("*IT") }
          shouldRunAfter("test")
      }
      tasks.test {
          filter { excludeTestPatterns("*IT") }
      }
      tasks.check { dependsOn("integrationTest") }
      ```
    - CI workflow `ci.yml` — параллельные шаги `unit-test` +
      `integration-test` (быстрее feedback).
    - ArchUnit rule:
      ```java
      @ArchTest
      static final ArchRule integration_tests_named_IT =
          classes().that().areAnnotatedWith(SpringBootTest.class)
              .or().areAnnotatedWith(DataJpaTest.class)
              .or().areAnnotatedWith(WebMvcTest.class)
              .or().areAssignableTo(Testcontainers.class)
              .should().haveSimpleNameEndingWith("IT");
      ```
  - **Каскад:**
    - 14 P2-1 → 🔧 TO-FIX.
    - NEW-109 (ArchUnit framework) +1 rule.
    - NEW-156 (testing.md) — раздел «naming convention + Gradle tasks».
    - QD2 (coverage-gate) — отдельные jacoco reports для unit vs IT
      (merged в финал).
  - **Estimate:** ~1 день (audit + rename + Gradle + ArchUnit rule +
    CI split).

- **P2-8/2 — Testcontainers + gRPC in-process + WireMock:** **(b)
  hybrid** — real БД/Rabbit, real gRPC через in-process, WireMock для
  HTTP.
  - **Мотивация:**
    - 14 P2-2: 36 @MockitoBean в IT — фейк-успех, прод ловит schema
      mismatch.
    - Testcontainers для БД/Rabbit = real behavior (migrations,
      message serialization, indexes).
    - gRPC in-process (`InProcessChannelBuilder` + real stub) = real
      proto-contract, быстрее network (тот же процесс).
    - WireMock для WebClient = real HTTP flow, record/replay.
    - QD3 contract-тесты (schema validation) — дополнительный слой,
      не замена.
  - **Что делается:**
    - Новый модуль `shared-test-containers` (Gradle `java-library`)
      с fixtures:
      ```java
      public abstract class ContainerTestBase {
          @Container
          static final RabbitMQContainer RABBIT = new RabbitMQContainer(
              "rabbitmq:3.13-management-alpine").withReuse(true);

          @Container
          static final PostgreSQLContainer<?> POSTGRES =
              new PostgreSQLContainer<>("postgres:17-alpine").withReuse(true);

          @DynamicPropertySource
          static void props(DynamicPropertyRegistry r) {
              r.add("spring.rabbitmq.host", RABBIT::getHost);
              r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
              r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
              // ...
          }
      }
      ```
    - Reuse containers между тестами через `.withReuse(true)` +
      `testcontainers.reuse.enable=true` в `~/.testcontainers.properties`.
    - gRPC fixtures: `@Bean @Primary InProcessChannel channel` +
      `GrpcServerBuilder.forName("test")` c real service-impl.
    - WireMock для external HTTP (если будет).
    - Audit 36 @MockitoBean мест → переключение на real Testcontainers.
    - CI time budget: ~5-10 минут дополнительно (приемлемо с reuse).
  - **Каскад:**
    - 14 P2-2 → 🔧 TO-FIX.
    - NEW-158 (shared-test-containers модуль).
    - NEW-156 (testing.md) — «когда моки, когда Testcontainers».
    - NEW-53 (shared conftest.py для Python) — аналогичный подход.
    - QD3 (contract events) — валидация schema НАД integration-тестом.
  - **Estimate:** ~2-3 дня (модуль + refactor 36 мест + CI-time
    оптимизация + документация).
  - **NEW-158:** модуль `shared-test-containers` с reusable fixtures:
    Postgres, Mongo, Redis, RabbitMQ containers (with reuse). Helper-
    методы для gRPC in-process и WireMock. Документация в
    `docs/testing.md` (NEW-156) раздел «Testcontainers conventions».

- **P2-8/3 — Flyway MigrationIT + data-preservation test:** **(a) +
  (b) частично** — MigrationIT в schedule обязательно, data-preservation
  для P1-миграций (QB1 soft-delete).
  - **Мотивация:**
    - 14 P2-9: только academic имеет MigrationIT, schedule/attendance/
      auth — нет.
    - Memory `feedback_flyway_no_edit` — регрессия checksum mismatch
      ловится MigrationIT.
    - Fresh install на прод → риск V42 зависит от V41 но не протестировано.
    - Data-preservation тест для QB1 (soft-delete groups): migrate →
      insert group → apply soft-delete migration → assert group не
      удалена, только status=archived.
  - **Что делается:**
    - `schedule-service/src/integrationTest/java/.../FlywayMigrationIT.java`:
      ```java
      @SpringBootTest
      @ActiveProfiles("integration")
      class FlywayMigrationIT extends ContainerTestBase {
          @Autowired Flyway flyway;
          @Test void freshInstallAppliesAllMigrations() {
              flyway.clean(); flyway.migrate();
              var info = flyway.info();
              assertThat(info.applied()).hasSizeGreaterThan(0);
              assertThat(info.pending()).isEmpty();
          }
          @Test void checksumConsistency() {
              // повторный migrate не даёт изменений
              flyway.migrate(); flyway.validate();
          }
      }
      ```
    - Data-preservation для P1-миграций (QB1):
      ```java
      @Test void softDeleteMigrationPreservesData() {
          // migrate V42-1 (до soft-delete)
          // INSERT into groups: (id=1, name='test')
          // migrate V42 (add status column)
          // assert: SELECT * FROM groups WHERE id=1 → row exists, status='active'
      }
      ```
    - Helper в `shared-test-containers`: `MigrationTestUtils.runMigrationsUpTo(version)`.
  - **Каскад:**
    - 14 P2-9 → 🔧 TO-FIX.
    - NEW-158 (shared-test-containers) +migration helper.
    - Memory `feedback_flyway_no_edit` — regression guard.
    - QB1 (soft-delete groups) — data-preservation тест обязателен.
    - NEW-74 (PR-template expand/contract) — checklist «MigrationIT
      обновлён».
  - **Estimate:** ~1 день (3 сервиса + helper + P1-миграции тесты).
  - **NEW-159:** runbook `docs/operations/runbooks/migration-testing.md` —
    когда data-preservation тест обязателен (P1 data-critical), когда
    fresh-install достаточно. Шаблон теста.

- **P2-8/4 — Golden tests + property-based + Clock-injection:** **(a)
  + (b)** — golden JSON-fixtures для week-parity/ФИО, jqwik для
  corner-cases, Clock-injection для date-arithmetic.
  - **Мотивация:**
    - 14 P2-4: week-parity drift не тестируется.
    - NEW-29 (golden-test table дат) — формализация через JSON.
    - DST transitions, year-transition — жёсткие corner-cases для
      property-based.
    - 04 P2-4: CheckinService hardcoded `Europe/Moscow` — не тестируется
      через Clock.
    - NEW-117 (display_name_short формат) — golden для ФИО-разбиения.
  - **Что делается:**
    - `src/test/resources/golden/week-parity.json`:
      ```json
      [
        {"date":"2025-09-01","semesterStart":"2025-09-01","expected":"ODD","week":1},
        {"date":"2025-09-08","semesterStart":"2025-09-01","expected":"EVEN","week":2},
        {"date":"2025-12-29","semesterStart":"2025-09-01","expected":"ODD","week":17}
      ]
      ```
    - Parametrized test:
      ```java
      @ParameterizedTest
      @MethodSource("weekParityGoldenCases")
      void weekParity_goldenTable(GoldenCase c) {
          assertThat(resolver.resolve(c.date(), c.semesterStart())).isEqualTo(c.expected());
      }
      ```
    - Property-based (jqwik):
      ```java
      @Property
      void parityFlipsEveryWeek(@ForAll("validDates") LocalDate d) {
          var p1 = resolver.resolve(d, SEMESTER_START);
          var p2 = resolver.resolve(d.plusWeeks(1), SEMESTER_START);
          assertThat(p1).isNotEqualTo(p2);
      }
      ```
    - Clock-injection: рефактор `CheckinService` на `Clock clock`
      constructor-injection. Test уже использует `Clock.fixed(...)`.
    - Golden для ФИО (NEW-117):
      ```json
      [
        {"full":"Иванов Иван Иванович","short":"Иванов И.И."},
        {"full":"Petrov-Sidorov Pyotr Pyotrovich","short":"Petrov-Sidorov P.P."},
        {"full":"Кузнецов Вадим","short":"Кузнецов В."}
      ]
      ```
  - **Каскад:**
    - 14 P2-4, 04 P2-4 → 🔧 TO-FIX.
    - NEW-29 (golden-test) — формализация.
    - NEW-30 (diff real semesters) — подтверждается.
    - NEW-117 (display_name_short формат) — golden-fixture.
    - 03 P0-5 (drift week-parity AUTO-RESOLVED) — golden-test как
      regression guard.
    - NEW-160 (golden-tests.md docs).
  - **Estimate:** ~1.5 дня (golden files + parametrized tests +
    jqwik dependency + Clock-injection refactor).
  - **NEW-160:** `docs/golden-tests.md` — когда golden vs
    property-based, fixture update policy, JSON structure convention.
    Подсказка для ревью: diff в golden JSON виден как список
    «ожидание изменилось».

- **P2-8/5 — E2E: Playwright + post-deploy smoke:** **(a) + (c)** —
  Playwright × 4-5 critical flows в CI, shell smoke-scripts post-deploy.
  - **Мотивация:**
    - 14 P2-11: web-panel без e2e.
    - Full-stack bugs (URL rewriting, CORS, JWT flow, WebSocket
      reconnect) ловятся только e2e.
    - Post-deploy smoke = minute-level regression detection на prod.
  - **Scope (v0.0.0):**
    - 4 Playwright flows:
      1. `auth.spec.ts`: login + /schedule visible + logout clears
         localStorage.
      2. `headman-mark.spec.ts`: headman открывает lesson, bulk-mark
         30 студентов, WebSocket event доставлен другому клиенту.
      3. `student-excuse.spec.ts`: создание excuse + file upload (10MB
         PDF) + headman approves.
      4. `admin-create-user.spec.ts`: admin создаёт student, видит
         initial_password (01-Q1 accepted).
    - CI: отдельный job `e2e-tests` после integration-tests. Docker-
      compose up full stack → Playwright → teardown.
    - Screenshots + traces on failure → artefacts.
    - Post-deploy smoke `scripts/smoke-prod.sh`:
      ```bash
      curl -fsS https://ruttrack.site/actuator/health | jq .status
      curl -fsS -X POST https://ruttrack.site/api/auth/login ...
      curl -fsS https://ruttrack.site/api/schedule/today ...
      ```
    - 14 P2-15 (landing zero tests) → ACCEPT, визуальное ревью через PR.
  - **Каскад:**
    - 14 P2-11 → 🔧 TO-FIX.
    - 14 P2-15 → ✅ ACCEPTED.
    - NEW-31 (SecurityIdorIT) — частично перекрывается на backend-
      level, e2e на full-stack.
    - NEW-96 (rollback.md) — smoke-script triggers rollback.
    - QD2 (coverage-gate) — e2e НЕ в coverage, отдельный CI job.
    - NEW-161 (e2e-testing.md).
  - **Estimate:** ~3-5 дней (Playwright setup + CI integration + 4
    tests + flaky-mitigation + smoke-scripts).
  - **NEW-161:** `docs/e2e-testing.md` + `tests/e2e/` директория с
    Playwright config (multi-browser Chromium+WebKit), fixtures,
    fixture-data. Раздел «как писать новый e2e тест» в NEW-108
    contributing.md.

- **P2-8/6 — Frontend unit-тесты критичных hooks/components:** **(a)
  частично** — hooks обязательно, components selective, SW logout
  purge, Mini-app accept.
  - **Мотивация:**
    - 09 P2-8: отсутствуют тесты headman-экранов, SW, axios refresh.
    - 14 P2-5: multi-STOMP logout не покрыт.
    - 14 P2-6: CSP-compat — через e2e (P2-8/5 trustedTypes check).
    - 14 P2-12: mini-app не мокает Telegram — ACCEPT пока не доделан.
    - Hooks = business logic, быстрые тесты, ловят regression.
    - Components (HeadmanLessonSheet) — критичны для bulk-operations
      UX.
  - **Scope (v0.0.0):**
    - Hooks (Vitest):
      - `useAuth.test.ts`: login flow, token refresh, logout.
      - `useErrorInterceptor.test.ts` (QC3): RFC 7807 parsing,
        toast-dispatch.
      - `useNotificationCenter.test.ts` (QC1): STOMP subscribe,
        reconnect, logout disconnect.
      - `useConfirmWithReason.test.ts` (QC4): dialog flow.
      - `useGroupMembers.test.ts`: pagination (09 P2-6).
    - Components:
      - `HeadmanLessonSheet.test.tsx`: bulk-mark (09 P2-11), optimistic
        update, error-recovery.
      - `CheckInButton.test.tsx`: geolocation permission, window
        validation, API call, success/error states.
    - SW:
      - `sw-cache.test.ts`: logout invalidates `headman-api-cache-v1`
        (09 P0-4 regression guard).
    - Axios refresh: MSW (mock service worker) для интерцептора 401
      → refresh flow.
    - Mini-app (14 P2-12) → ACCEPT.
  - **Каскад:**
    - 09 P2-8 → 🔧 TO-FIX частично.
    - 14 P2-5, 14 P2-6 → 🔧 TO-FIX (P2-8/5 e2e + P2-8/6 hooks).
    - 14 P2-12 → ✅ ACCEPTED (mini-app not ready).
    - QC1/QC3/QC4 (reuse frontend) — unit-тесты покрывают новые
      hooks/components.
    - QD2 (vitest --coverage) — 50% diff-coverage already decided.
    - 09 P0-4 (logout SW purge) — regression guard.
    - NEW-162 (критичные frontend units list).
  - **Estimate:** ~2-3 дня (5 hooks + 2 components + SW + MSW + CI
    integration).
  - **NEW-162:** список «критичных frontend юнитов» в
    `docs/testing.md` (NEW-156) — обновляется при добавлении новых
    critical flows. PR-template checklist «новый hook → unit-тест
    обязателен».

- **P2-8/7 — Нагрузочные тесты: minimal k6 + manual pre-release:**
  **(c) + minimal (a)** — 2 k6 scenarios (bulk-mark, geolocation)
  manual перед релизом, полный load-suite → v0.1.
  - **Мотивация:**
    - 14 P2-8: нет load-тестов.
    - P2-10/6 (Hikari=20) базируется на предположении — нужен
      baseline.
    - Prod-нагрузка пока не известна (<5000 users, 1 университет) —
      полный load-suite overkill.
    - Manual pre-release validation = достаточно для v0.0.0.
  - **Что делается:**
    - `tests/load/bulk-mark.js` (k6):
      ```js
      import { check, sleep } from 'k6';
      import http from 'k6/http';
      export const options = {
          vus: 10,  // 10 headmen параллельно
          duration: '2m',
          thresholds: {
              http_req_duration: ['p(95)<500'],
              http_req_failed: ['rate<0.01'],
          },
      };
      export default function () {
          // логин headman → POST /api/attendance/marks/batch [30 marks]
      }
      ```
    - `tests/load/geolocation-flood.js`: 50 VU одновременно
      checkin'аются в 10:30 (пик пары).
    - Runbook `docs/load-testing.md`: «перед релизом запусти оба
      scenarios, зафиксируй p95/p99 в baseline.md».
    - Baseline file `docs/performance-baseline.md` — результаты
      каждого релиза, тренд.
    - Полный load-suite → v0.1 (future-ideas.md).
  - **Каскад:**
    - 14 P2-8 → 🔧 TO-FIX частично (minimal).
    - P2-10/6 (Hikari tuning) — валидируется реальным load.
    - NEW-65 (baseline 2 недели) — аналогичный подход для alerts.
    - NEW-163 (load-testing.md + performance-baseline.md).
  - **Estimate:** ~1 день (k6 install + 2 scripts + runbook +
    baseline file).
  - **NEW-163:** `docs/load-testing.md` + `tests/load/` + `docs/performance-baseline.md`.
    Раздел «когда запускать» (pre-release + при major-архитектурных
    изменениях). Future-ideas.md: full load-suite (JMeter/Gatling)
    → v0.1.

- **P2-8/8 — Security contract-тесты:** **(a)** 3 теста: GRPC_SECRET
  fail-fast, TMA HMAC invalid, CSRF double-submit.
  - **Мотивация:**
    - 14 P2-7: GRPC_SECRET пустой не тестируется (Q13c fail-fast).
    - 14 P2-14: TMA HMAC invalid не тестируется (06 P0-5 related).
    - 05 P2-4: нет CSRF-защиты на REST (C0-7 JWT-cookie flow
      требует CSRF).
    - Security-contract = самые ценные тесты (ловят regression в
      auth flow = critical vulnerabilities).
  - **Scope:**
    - `GrpcSecretFailFastIT` (notification-bot):
      ```python
      def test_empty_grpc_secret_fails_startup(monkeypatch):
          monkeypatch.setenv("GRPC_SECRET", "")
          with pytest.raises(StartupError):
              load_app_config()
      ```
    - `TmaHmacValidationIT` (auth-service): signed payload → 200,
      mutated signature → 401, replay-attack (same timestamp) → 401.
    - `CsrfDoubleSubmitIT` (shared-web): POST без `X-CSRF-TOKEN` header
      → 403; POST с mismatched cookie+header → 403; POST с matched
      → 200.
    - Добавляются в `SecurityIdorIT` test-suite как `SecurityContractsIT`
      (аналогичный паттерн, single-spot for audit).
  - **Каскад:**
    - 14 P2-7, 14 P2-14, 05 P2-4 → 🔧 TO-FIX.
    - NEW-31 (SecurityIdorIT) — расширяется до `SecurityContractsIT`.
    - Q13c (GRPC_SECRET fail-fast) — regression guard.
    - C0-7 (JWT HttpOnly cookie + CSRF) — contract-test как acceptance
      criteria.
    - 06 P0-5 (TMA handshake) — HMAC-тест.
    - NEW-164 (SecurityContractsIT документация).
  - **Estimate:** ~1 день (3 теста × ~2 часа + документация).
  - **NEW-164:** `SecurityContractsIT` test-suite (параллельно с
    `SecurityIdorIT` NEW-31) — fail-fast startup checks, HMAC/signed-
    payload validation, CSRF contracts. Описан в `docs/testing.md`
    раздел «Security contract tests».

**Итого P2-8 (8 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-8:** ~10-12 человеко-дней (~80-100 часов).
  Самые крупные: Playwright e2e (3-5д), Testcontainers refactor
  (2-3д), frontend unit (2-3д), golden tests (1.5д).
- **Новый модуль:** `shared-test-containers` (NEW-158) — fixture
  library для Java IT-тестов.
- **Новые test-suites:** Playwright e2e (`tests/e2e/`), k6 load
  (`tests/load/`), `SecurityContractsIT`.
- **Новые доки:** `golden-tests.md` (NEW-160), `e2e-testing.md`
  (NEW-161), `load-testing.md` + `performance-baseline.md` (NEW-163),
  `SecurityContractsIT` docs (NEW-164), migration-testing.md (NEW-159).
- **Новых NEW-задач:** 7 (NEW-158..164).
- **Auto-resolves:** 04 P2-4 (Clock-injection), 09 P2-6 (pagination
  тест), 09 P2-11 (bulk-mark тест).
- **ACCEPT:** 14 P2-12 (mini-app not ready), 14 P2-15 (landing zero
  tests — визуальное ревью).

---

## P2 — Группа 6 (Логи-нюансы)

Ответы на 6 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: QA7 (unified JSON structured logs + shared logback, NEW-68/69),
QA1 (INFO default + NEW-57 CI-check), QA3 (trace_id в events),
NEW-113 (Loki alert JWT в логах), NEW-114 (security-model.md logging
hygiene), NEW-70 (bot promtail), P2-9/5 (Alertmanager).

Audit: 10 пунктов, консолидировано в 6 вопросов (2 auto-resolve).
Темы — sensitive data masking, diagnostic log levels для auth-failures,
notification history persistence (архитектурное изменение), nginx
JSON log-format.

- **P2-6/1 — Sensitive data masking в логах:** **(c) hybrid** —
  ручной audit + Logback MaskingConverter как safety-net.
  - **Мотивация:**
    - 01 P2-2: `event.toString()` в WARN раскрывает telegramId.
    - 05 P2-1: push `endpoint` (FCM secret token) в DEBUG/INFO/WARN.
    - 07 P1-4: Gateway DEBUG логирует URL с `?token=JWT`.
    - (b) ручной audit — правильно семантически, но один разработчик
      забудет → регрессия.
    - (a) masking — safety-net: даже при регрессии PII скрыт.
    - NEW-113 (Loki alert JWT pattern) — detection, не prevent.
  - **Scope:**
    - Ручной audit и рефактор:
      - 01 `DomainEventListener`: `log.warn("AMQP error", kv("event_type",
        event.type()), kv("trace_id", traceId))` — whitelisted fields
        только.
      - 05 `WebPushDeliveryService`: `log.info("push sent",
        kv("endpoint_hash", sha256(endpoint).substring(0,8)),
        kv("user_id", userId))` — hash endpoint, не plain.
      - 07 Gateway: отключить Spring Cloud Gateway `logback.level
        .org.springframework.cloud.gateway=INFO` (не DEBUG). Custom
        filter логирует без query-string.
    - Logback masking в shared-logback (NEW-68):
      ```xml
      <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
          <encoder class="net.logstash.logback.encoder.LogstashEncoder">
              <customFields>{"app":"${APP_NAME}"}</customFields>
              <provider class="...MaskingProvider">
                  <!-- patterns masked before write -->
                  <pattern>Bearer\s+[A-Za-z0-9-_.]+\.[A-Za-z0-9-_.]+\.[A-Za-z0-9-_.]+</pattern>
                  <replacement>Bearer ***</replacement>
              </provider>
              <provider class="...MaskingProvider">
                  <pattern>"telegram_?id"\s*:\s*\d+</pattern>
                  <replacement>"telegram_id":***</replacement>
              </provider>
              <provider class="...MaskingProvider">
                  <pattern>https://fcm\.googleapis\.com/[^\s"]+</pattern>
                  <replacement>https://fcm.googleapis.com/***</replacement>
              </provider>
          </encoder>
      </appender>
      ```
    - Unit-тесты masking: `log.info("telegram_id: {}", 12345)` → в
      output `"telegram_id":***`.
  - **Каскад:**
    - 01 P2-2, 05 P2-1, 07 P1-4 → 🔧 TO-FIX через (c).
    - NEW-68 (shared logback) → расширяется MaskingProvider.
    - NEW-113 (Loki alert JWT) — третий эшелон, не замена.
    - NEW-114 (security-model.md logging hygiene) → раздел masking.
    - NEW-165 (logging-conventions.md).
  - **Estimate:** ~1 день (3 места audit + MaskingProvider в
    shared-logback + masking-tests).
  - **NEW-165:** `docs/operations/monitoring/logging-conventions.md` — whitelist allowed
    fields (`event_type`, `trace_id`, `user_id` для admin, `ip`,
    `path`, `status`, `reason_code`). Banned: `event.toString()`,
    `request.getQueryString()`, `subscription.getEndpoint()`,
    `token`, любое полное DTO. Masking patterns list.

- **P2-6/2 — Auth-failure diagnostic logs:** **(a)** WARN + reason
  enum + ip + path, без содержимого токена.
  - **Мотивация:**
    - 07 P3-1: `log.debug` при 401 — в prod (DEBUG=false QA1) нет
      инфы вообще.
    - WARN = семантически правильно для failure events.
    - Без токена = безопасно (P2-6/1 + NEW-113).
    - Rate WARN по одному ip = brute-force signal для Alertmanager
      (P2-9/5).
  - **Что делается:**
    - Enum:
      ```java
      public enum AuthFailureReason {
          EXPIRED_TOKEN, INVALID_SIGNATURE, MALFORMED_TOKEN,
          MISSING_TOKEN, REVOKED_TOKEN, UNSUPPORTED_ALGORITHM;
      }
      ```
    - Log в `JwtAuthenticationFilter`:
      ```java
      log.warn("Auth failed: reason={}, ip={}, path={}, user_agent={}",
          reason, request.getRemoteAddr(),
          request.getPath().value(),
          request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
      ```
    - Loki rule (через ruler → Alertmanager P2-9/5):
      ```yaml
      - alert: AuthBruteForce
        expr: |
          sum by (ip) (count_over_time({app="api-gateway"}
            |~ `Auth failed` [5m])) > 20
        for: 2m
        labels: { severity: warning }
        annotations:
          summary: "Possible brute-force from IP {{ $labels.ip }}"
      ```
  - **Каскад:**
    - 07 P3-1 → 🔧 TO-FIX.
    - P2-9/5 (Alertmanager) — WARN rate → brute-force alert.
    - C0-4 (rate-limiting) — complement detection + prevention.
    - NEW-153 (alerts.md) +rule.
    - NEW-165 (logging-conventions.md) — enum reason-codes list.
  - **Estimate:** ~30 мин (enum + log call + Loki rule).

- **P2-6/3 — Attendance orphan-cleanup дублирование:** ✅
  **AUTO-RESOLVED через Q15b.**
  - **Мотивация:** Q15b уже решил удалить `@PostConstruct
    cleanupOrphans` в `AttendanceIndexInitializer`. После фикса
    остаётся только `ReportService.filterExistingLessons` —
    дублирования нет.
  - **Действий нет:** подтверждение в отчёте 04 P2-6 → пометка
    `✅ AUTO-RESOLVED через Q15b` при разметке.

- **P2-6/4 — NotificationCenter: backend persistence + pagination:**
  **(b) FULL** — активация notification-web как stateful сервиса
  с собственной MongoDB.
  - **АРХИТЕКТУРНОЕ ИЗМЕНЕНИЕ:** notification-web перестаёт быть
    «stateless event forwarder» (CLAUDE.md change). Получает own БД
    для notification history.
  - **Мотивация:**
    - 09 P2-13, 10 P2-13: sessionStorage лимит 200 без UI-индикации
      → silent truncation.
    - Backend persistence = full history для пользователя (возвращение
      из отпуска, multi-device sync).
    - Read/unread tracking = unread badge в UI корректный.
    - Separate consumer → persistence не блокирует real-time delivery.
  - **Решения под-вопросов:**
    - **P2-6/4a — БД: Mongo `notification_db`** (ACTIVATED, отменяет
      P2-9/6 reserved). Коллекция `notification_history` с индексом
      `(user_id, sent_at DESC)`.
    - **P2-6/4b — Retention: 30 дней** через Mongo TTL index
      (`expireAfterSeconds: 2592000`).
    - **P2-6/4c — Read tracking: оба endpoint'а** — single PATCH
      `/api/notifications/{id}/read` + bulk `POST /api/notifications/mark-all-read`.
    - **P2-6/4d — Consumer: separate** `NotificationHistoryConsumer`
      параллельно с существующим STOMP forward'ером. Decoupling.
  - **Что делается:**
    - Schema `notification_history`:
      ```
      {
        _id: ObjectId,
        user_id: Long (indexed),
        type: String (enum EXCUSE_APPROVED/LESSON_REMINDER/...),
        payload: { /* denormalized from event */ },
        sent_at: ISODate (TTL-indexed, 30d),
        read_at: ISODate | null,
        trace_id: String
      }
      Indexes:
        { user_id: 1, sent_at: -1 } — list for user (DESC)
        { user_id: 1, read_at: 1 } — unread badge count
        { sent_at: 1 } — TTL (expireAfterSeconds: 2592000)
      ```
    - `NotificationHistoryConsumer`:
      ```java
      @RabbitListener(queues = "notification.history")
      public void persist(NotificationEvent event) {
          try {
              historyRepository.save(event.toHistory());
          } catch (Exception ex) {
              log.warn("Failed to persist notification history",
                  kv("trace_id", event.traceId()));
              // don't rethrow — delivery уже пошёл через другой consumer
          }
      }
      ```
    - REST endpoints в `notification-api-contract`:
      ```
      GET /api/notifications?page=N&size=20&unreadOnly=false
        → PagedModel<NotificationHistoryDto>
      GET /api/notifications/unread-count → { count: int }
      PATCH /api/notifications/{id}/read → 204
      POST /api/notifications/mark-all-read → 204
      ```
    - Consumer получает events через Rabbit fanout (тот же exchange,
      отдельная queue с биндингом на все events). Decoupling: если
      history-queue тормозит, delivery-queue не страдает.
    - Frontend (PWA + web-panel):
      - `NotificationCenter` теперь загружает из backend, не из
        sessionStorage. sessionStorage → read/unread optimistic update.
      - Infinite scroll / «Показать ещё».
      - Unread badge — `GET /unread-count` кэшируется Caffeine
        (P2-10/3, 30s TTL, invalidate on STOMP new-event).
    - RabbitMQ binding + TTL на message для fallback (если DB upsert
      падает несколько раз подряд, message идёт в DLQ через P2-3/7).
  - **Каскад (BIG — архитектурное изменение):**
    - 09 P2-13, 10 P2-13 → 🔧 TO-FIX через (b).
    - **P2-9/6 → ПЕРЕОПРЕДЕЛЕНО:** notification_db ACTIVATED, не
      reserved. init-mongo.js остаётся, notification-web получает
      creds и подключается.
    - **CLAUDE.md change:** notification-web описание обновляется с
      «stateless event forwarder» → «event forwarder + notification
      history persistence».
    - **NEW-36 (shared-DB patterns)** — расширяется: «notification-web
      ВЛАДЕЕТ notification_db, не shared».
    - **Q16b (push_subscriptions в attendance_db)** — остаётся в
      силе (это уже принято), но notification_history → в свою БД.
      Два разных persistence concerns в notification-domain.
    - **NEW-148 (data-retention-policy.md)** — раздел
      notification_history: 30d через TTL.
    - **QC1 (unified NotificationCenter)** — backend теперь
      authoritative, frontend — thin client.
    - **P2-10/3 (Caffeine cache)** — unread-count endpoint кэшируется.
    - **P2-10/4 (batch endpoints)** — `mark-all-read` — batch.
    - **P2-3/2 (invalid-params[])** — PATCH validation.
    - **QD3 (contract events)** — NotificationHistoryConsumer
      подписывается на все events → schema validation обязательна.
    - **P2-8/2 (Testcontainers)** — IT для NotificationHistoryConsumer
      через Testcontainers Mongo + Rabbit.
    - **NEW-166 (notification-history схема + миграция frontend).**
    - **NEW-167 (pagination + unread-count endpoints spec в
      OpenAPI / NEW-85 snapshot).**
    - **NEW-168 (CLAUDE.md update: notification-web status).**
  - **Estimate:** ~4-5 дней (schema + consumer + REST endpoints +
    Caffeine unread-count + frontend migration × 2 клиента + tests +
    docs). Это самая крупная работа P2-6.
  - **NEW-166:** `notification_history` Mongo schema + миграция
    V1__init.js с индексами. Документация в `docs/architecture/database-schema.md`
    (новый раздел для Mongo notification_db).
  - **NEW-167:** OpenAPI spec для notification REST endpoints +
    unread-count contract. Связка с QC2 (openapi-typescript) →
    frontend auto-generated types.
  - **NEW-168:** обновить CLAUDE.md: `Notification Web | 9094 |
    Spring Boot WebSocket + MongoDB notification_db` (вместо
    «— (stateless event forwarder)»).

- **P2-6/5 — Nginx JSON log_format:** **(b)** JSON access-log +
  Promtail pipeline parsing.
  - **Мотивация:**
    - 13 P3-6: нет `$request_time`/`$upstream_response_time` →
      невозможно building «slow endpoints» в Loki.
    - JSON → Promtail парсит без regex → Loki labels trivial.
    - Consistency с QA7 JSON-logs backend — unified approach.
  - **Что делается:**
    - `infra/nginx/nginx.conf`:
      ```nginx
      log_format json_combined escape=json
        '{"time":"$time_iso8601","method":"$request_method","path":"$request_uri",'
        '"status":$status,"bytes":$body_bytes_sent,'
        '"rt":$request_time,"urt":$upstream_response_time,'
        '"ua":"$http_user_agent","ip":"$remote_addr",'
        '"upstream":"$upstream_addr","host":"$host"}';
      access_log /var/log/nginx/access.log json_combined;
      ```
    - Promtail pipeline `infra/promtail/config.yml`:
      ```yaml
      - job_name: nginx
        static_configs:
          - targets: [localhost]
            labels:
              job: nginx
              __path__: /var/log/nginx/*.log
        pipeline_stages:
          - json:
              expressions:
                method: method
                path: path
                status: status
                rt: rt
                urt: urt
          - labels:
              method:
              status:
      ```
    - Grafana dashboard «Nginx latency & errors»:
      - p50/p95/p99 per path (histogram_quantile)
      - error-rate (status 5xx/4xx)
      - slow endpoints table (top 10 by p95 rt)
  - **Каскад:**
    - 13 P3-6 → 🔧 TO-FIX через (b).
    - QA7 (JSON structured backend) — consistency.
    - NEW-70 (bot promtail) — nginx аналогично, unified labels.
    - P2-9/5 (Alertmanager) — Loki rule на slow endpoints.
    - NEW-169 (Promtail pipeline docs).
  - **Estimate:** ~30 мин (nginx.conf + promtail pipeline + Grafana
    dashboard).
  - **NEW-169:** `infra/promtail/` директория с pipeline configs для
    nginx + java-backend + python-bot. Унифицированные labels (`app`,
    `service`, `job`). Grafana dashboard JSON export в `infra/grafana/dashboards/`.

- **P2-6/6 — Loki retention 7д:** ✅ **AUTO-RESOLVED через P2-9/4.**
  - Уже принято P2-9/4 как ACCEPT 14д (консистентно с QA5).
  - **Действий нет:** подтверждение в отчёте 13 P2-9 → пометка
    `✅ ACCEPTED через P2-9/4 (14д retention)`.

**Итого P2-6 (6 вопросов):** 3 полных ответа + 2 auto-resolve + 1
архитектурное изменение (P2-6/4).

- **Суммарный estimate P2-6:** ~5-6 человеко-дней. Основная часть —
  P2-6/4 notification persistence (4-5д). Остальное мелкое (~1-2д).
- **Архитектурные изменения:**
  - notification-web становится stateful (own MongoDB)
  - P2-9/6 (reserved mongo user) → **ACTIVATED**
  - CLAUDE.md update (NEW-168)
  - NEW-36 (shared-DB patterns) расширяется
- **Новые REST endpoints:** 4 (GET list / unread-count / PATCH read /
  POST mark-all-read).
- **Новый consumer:** `NotificationHistoryConsumer` (separate queue,
  decoupled from delivery).
- **Новые доки:** `logging-conventions.md` (NEW-165),
  notification_history schema (NEW-166), OpenAPI spec (NEW-167),
  Promtail pipeline (NEW-169).
- **Новых NEW-задач:** 5 (NEW-165..169).
- **Auto-resolves:** 01 P2-2, 05 P2-1, 07 P1-4, 07 P3-1, 13 P3-6,
  13 P2-9 (через P2-9/4), 04 P2-6 (через Q15b), 09 P2-13, 10 P2-13.

---

## P2 — Группа 7A (Frontend UX / flow)

Ответы на 8 вопросов (14 audit-пунктов консолидированы).
Зафиксированы 2026-04-19 (восьмая сессия). Связка: QC1 (unified
NotificationCenter), QC2 (openapi-typescript), QC3 (error-interceptor),
QC4 (ConfirmWithReasonDialog), QC6 (stats aggregate), P2-6/4
(notification persistence), P2-10/4 (batch endpoints).

- **P2-7A/1 — Query freshness + pull-to-refresh:** **(b)** 09 P2-1,
  09 P2-2 → 🔧 TO-FIX.
  - **Что делается:**
    - `queryClient.ts`: `refetchOnWindowFocus: true` + `staleTime`
      per-query-key:
      ```ts
      const defaults = {
          queries: {
              refetchOnWindowFocus: true,
              staleTime: 30_000,  // 30s default
          }
      };
      // per-query override:
      useQuery(['schedule', week], ..., { staleTime: 30_000 });
      useQuery(['notifications/unread-count'], ..., { staleTime: 15_000 });
      useQuery(['subjects'], ..., { staleTime: 5 * 60_000 });  // static data
      ```
    - Pull-to-refresh hook `usePullToRefresh(onRefresh)`:
      - Touch-start при `window.scrollY === 0`
      - Drag down >80px → trigger `queryClient.invalidateQueries`
      - Visual indicator (spinner в top-bar)
    - Integration на 5 страницах: Home, Schedule, Homework, Notifications,
      Group.
  - **Каскад:** QC1 (STOMP) — notifications уже real-time, pull-to-
    refresh = fallback. P2-6/4 (unread-count) — staleTime 15s.
  - **Estimate:** ~1 день.

- **P2-7A/2 — Auto-scroll + swipe thresholds:** **(a)** 09 P2-7,
  09 P2-9 → 🔧 TO-FIX.
  - **Что делается:**
    - SchedulePage: `useEffect(() => { scrollToCurrentLesson(); },
      [selectedWeek, currentLessonIndex])`.
    - `useSwipeHandler(onLeft, onRight, { threshold: 100, velocity: 0.3 })`
      custom hook в `shared/hooks/`. Переиспользование в
      HomeworkPage WeekView.
    - Velocity = distance / timeDiff; если velocity > 0.3 — trigger
      даже при threshold < 100 (fast flick).
  - **NEW-170:** `useSwipeHandler` hook в shared/hooks/ PWA.

- **P2-7A/3 — DatePicker single source-of-truth + configurable
  windowDays:** **(a)** 09 P2-13, 10 P2-15 → 🔧 TO-FIX.
  - **Что делается:**
    - PWA (Zustand): `useDateStore({ selectedDate, setSelectedDate,
      windowDays })`. WeekView/MonthView derive current week/month
      from selectedDate (pure derivation, не own state).
    - Web-panel (Angular signal): `dateState = signal({ date, windowDays: 14 })`.
      HeadmanLessonsComponent принимает `windowDays` input, default 14.
      Navigation prev/next сдвигает window (not fixed).
    - Empty state при windowDays large + нет lessons → «Нет пар в
      этом периоде».
  - **NEW-171:** `useDateNavigation` hook PWA + `DateNavigationService`
    Angular. Unified UX, но разные implementations.

- **P2-7A/4 — Schedule navigation bounds:** **(a)+(b)** 10 P2-5 → 🔧
  TO-FIX.
  - **Что делается:**
    - `semester.dateFrom`/`semester.dateTo` limits для prev/next
      buttons.
    - Disable state + tooltip «Конец семестра» / «Начало семестра».
    - Empty state «Пары не найдены в этом периоде. Это может быть
      каникулы или пары ещё не добавлены».
    - NEW-116 (current_semester_id optional) — используется для
      границ.
  - **Каскад:** NEW-116 → frontend получает активный семестр с
    бакендовой метой.

- **P2-7A/5 — Scroll position preservation:** **(a)** 10 P2-16 → 🔧
  TO-FIX.
  - **Что делается:**
    - HeadmanWeeklyJournalComponent: `@ViewChild` на scroll-container.
    - Angular signal `scrollPosition = signal({ top: 0, left: 0 })`.
    - `(scroll)` event update signal (throttle 100мс).
    - `afterViewChecked` / `effect` restore scrollTop/scrollLeft после
      week-change.
    - Per-week key: `scrollPositions = Map<weekKey, {top, left}>`,
      чтобы возврат на ту же неделю вернул ту же position.
  - **Estimate:** ~3 часа.

- **P2-7A/6 — Waterfall → forkJoin + Map for subject lookup:**
  **(a)** 10 P2-6, 10 P2-14 → 🔧 TO-FIX.
  - **Что делается:**
    - `forkJoin([semesters$, schedule$, subjects$])` в
      `loadActiveSemesterAndSchedule`.
    - `subjectMap: Map<Long, Subject>` построен один раз из
      `subjects$` результата. Template использует `subjectMap.get(id)?.name`.
    - + OnPush change detection (если ещё нет).
  - **Каскад:** QC6 (aggregate endpoint) — снижает число HTTP-запросов.
    P2-10/3 (Caffeine cache) — backend latency ↓.

- **P2-7A/7 — Sidebar duplication + DrawerMenu:** **(a)** 09 P2-16,
  10 P2-1 → 🔧 TO-FIX.
  - **Что делается:**
    - Web-panel `sidebar.component.ts`: для роли headman показывать
      только «Кабинет старосты» (не «Главная»). Entry-point един.
    - Внутри кабинета — вкладки (общее, расписание, журнал,
      студенты).
    - PWA: убрать DrawerMenu компонент. «ДЗ» → BottomNav (5 вкладок:
      Home, Schedule, Homework, Notifications, Profile). «Профиль» →
      avatar-click в header (secondary entry).
    - BottomNav cap = 5 вкладок.
  - **NEW-172:** `docs/frontend-navigation.md` — правила entries
    (single per role, role-based hiding, BottomNav ≤5, drawer от 6+
    rarely-used items).

- **P2-7A/8 — Geolocation high-accuracy + loading UX:** **(a)+(c)**
  09 P2-17 → 🔧 TO-FIX.
  - **Что делается:**
    - CheckInButton:
      ```ts
      navigator.geolocation.getCurrentPosition(success, error, {
          enableHighAccuracy: true,
          timeout: 15_000,
          maximumAge: 0  // всегда свежая точка, не cached
      });
      ```
    - Loading indicator «Уточняем геолокацию…» с pulse animation
      (5-10s ожидание).
    - Error handling:
      - `PERMISSION_DENIED` → «Разрешите доступ к геолокации в
        настройках браузера».
      - `POSITION_UNAVAILABLE` → «Не удалось определить местоположение.
        Попробуйте выйти ближе к окну или в коридор».
      - `TIMEOUT` → retry button + fallback «Отправить без геоданных
        на подтверждение старосте».
    - accuracy-threshold logging: если `position.coords.accuracy > 50`
      — показать warning «Точность определения низкая (±N м)».
  - **NEW-173:** `docs/geofencing.md` — accuracy trade-offs, fallback
    flow (indoor без GPS), accuracy logging.

**Итого P2-7A (8 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-7A:** ~2.5-3 человеко-дня (~20 часов).
- **Новые hooks/services:** `usePullToRefresh`, `useSwipeHandler`,
  `useDateNavigation` / `DateNavigationService`, scroll-preservation
  pattern.
- **Новые доки:** `frontend-navigation.md` (NEW-172),
  `geofencing.md` (NEW-173).
- **Новых NEW-задач:** 4 (NEW-170..173).
- **Auto-resolves:** 09 P2-1, P2-2, P2-7, P2-9, P2-13, P2-16, P2-17;
  10 P2-1, P2-5, P2-6, P2-14, P2-15, P2-16.

---

## P2 — Группа 7B (Frontend a11y)

Ответы на 4 вопроса (8 audit-пунктов консолидированы).
Зафиксированы 2026-04-19 (восьмая сессия). Связка: QE3 (reduced-motion
landing, уже закрыт), NEW-110 (stylelint a11y v0.1), P2-8/5 (Playwright
e2e), QC3 (toast aria-live), QC4 (dialog focus-trap), QC2 (типизация
auto устраняет any[]).

- **P2-7B/1 — Semantic HTML + типизация:** **(a)** 09 P2-14, 10 P2-7
  → 🔧 TO-FIX.
  - **Мотивация:**
    - `<div role="button">` не handle Enter/Space автоматически.
    - Screen reader'ы: студенты/teacher с disabilities. WCAG 2.1 AA —
      минимум для EDU-систем.
    - `<button>` + display:flex = zero-cost a11y. Focus-ring нативный.
    - Angular `any[]` → через QC2 openapi-typescript auto-type'ы.
  - **Что делается:**
    - PWA audit (~10 мест): SubjectsList, CheckInButton wrapper,
      HeadmanLessonSheet actions, etc. → `<div role="button">` →
      `<button type="button" className="flex ...">`.
    - Web-panel Angular (~15 мест): аналогично `<div (click)>` →
      `<button>`.
    - `HeadmanGroupComponent.students()` → typed `Student[]` из
      OpenAPI generated types (NEW-27 / QC2).
    - ESLint config PWA:
      ```json
      {
        "extends": ["plugin:jsx-a11y/recommended"],
        "rules": {
          "jsx-a11y/no-noninteractive-element-to-interactive-role": "error",
          "jsx-a11y/click-events-have-key-events": "error",
          "jsx-a11y/no-static-element-interactions": "error"
        }
      }
      ```
    - Angular: `@angular-eslint/template-no-any` +
      `@angular-eslint/template-accessibility-*` rules enabled.
  - **Каскад:**
    - 09 P2-14, 10 P2-7 → 🔧 TO-FIX.
    - QC2 (openapi-typescript) — типизация auto.
    - NEW-110 (stylelint a11y) — повышается до v0.0.0 scope
      (ESLint + Angular template lint).
    - NEW-174 (a11y-checklist.md).
  - **Estimate:** ~1 день (audit × 2 frontends + ESLint/Angular lint
    + fixes).
  - **NEW-174:** `docs/a11y-checklist.md` — whitelist elements,
    banned patterns (`div` with `onClick`, `role="button"` without
    `tabindex` + keyboard-handlers), ESLint rules references.

- **P2-7B/2 — ARIA labels + axe-core automation:** **(a)+(c)** 12
  P2-7 → 🔧 TO-FIX.
  - **Мотивация:**
    - Theme-toggle, icon-only buttons, live-regions, dialogs — все
      требуют ARIA atts.
    - Manual audit закрывает текущее состояние, axe-core в тестах —
      regression guard.
    - P2-8/5 Playwright уже в scope — +axe plugin бесплатно.
  - **Что делается:**
    - Audit interactive-elements в 3 frontends:
      - **Landing theme-toggle**: `aria-pressed="true/false"` +
        `aria-label="Переключить тему"`.
      - **Icon-only buttons** (checkin, bell, logout, close-dialog):
        `aria-label`.
      - **Toast container** (QC3): `aria-live="polite"` (non-critical)
        / `aria-live="assertive"` (errors).
      - **Unread count badge**: `aria-label="Непрочитанных
        уведомлений: {count}"`.
      - **Dialogs** (QC4 ConfirmWithReason): `role="dialog"` +
        `aria-labelledby` + `aria-describedby` + focus-trap + return
        focus on close.
      - **Form inputs**: `<label for>` или `aria-labelledby`. Angular
        Material auto-handles, vanilla нужно явно.
      - **Error announcements**: RFC 7807 errors из P2-3/2 →
        `aria-live="assertive"` region на form-submit.
    - axe-core в Playwright (P2-8/5):
      ```ts
      import AxeBuilder from '@axe-core/playwright';
      test('login page a11y', async ({ page }) => {
          await page.goto('/login');
          const results = await new AxeBuilder({ page })
              .withTags(['wcag2a', 'wcag2aa'])
              .analyze();
          expect(results.violations).toEqual([]);
      });
      ```
    - vitest-axe в unit-тестах critical components:
      `expect(await axe(container)).toHaveNoViolations()`.
  - **Каскад:**
    - 12 P2-7 → 🔧 TO-FIX.
    - P2-8/5 (Playwright) — axe plugin.
    - P2-8/6 (vitest hooks/components) — vitest-axe layer.
    - QC3 (toast) — aria-live в toast-container.
    - QC4 (ConfirmDialog) — focus-trap + aria-describedby.
    - P2-3/2 (invalid-params[]) — error-announcement region.
    - NEW-174 (a11y-checklist.md) — расширяется ARIA patterns.
    - NEW-175 (axe-core test setup docs).
  - **Estimate:** ~2 дня (audit × 3 frontends + axe Playwright setup
    + vitest-axe + component fixes).
  - **NEW-175:** axe-core integration в `tests/e2e/` Playwright config
    + vitest-axe в unit-тестах. Baseline violations file (чтобы
    existing violations не ломали CI, но новые ловились). Документация
    в NEW-161 (e2e-testing.md).

- **P2-7B/3 — SMIL → CSS keyframes (reduced-motion):** **(b)** 12
  P2-5 → 🔧 TO-FIX.
  - **Мотивация:**
    - SMIL `<animateMotion>` не подчиняется CSS media-query автоматом.
    - SMIL deprecated (Chrome отключал и откатывал).
    - CSS keyframes + media-query — native, maintainable.
    - Landing — статика, одна правка навсегда.
    - Замыкает QE3 полностью (был частичный fix, теперь complete).
  - **Что делается:**
    - `frontends/landing/dist/index.html` hero SVG:
      - Удалить все `<animateMotion>` / `<animate>` SMIL tags.
      - Заменить на CSS:
        ```css
        @keyframes hero-float {
            from { transform: translate(0, 0); }
            to   { transform: translate(10px, -5px); }
        }
        .hero__dot {
            animation: hero-float 4s ease-in-out infinite alternate;
        }
        @media (prefers-reduced-motion: reduce) {
            .hero__dot {
                animation: none;
            }
        }
        ```
      - Всё остальное: паттерн `animation: none` в `@media
        (prefers-reduced-motion)`.
    - NEW-110 (stylelint) — повышается правило
      `stylelint-a11y/no-display-none` / custom rule
      «`animation` без `@media (prefers-reduced-motion)` override».
  - **Каскад:**
    - 12 P2-5 → 🔧 TO-FIX через (b).
    - QE3 (landing reduced-motion) — полностью закрыт.
    - NEW-110 (stylelint a11y) — v0.0.0 scope, добавить правило.
  - **Estimate:** ~2 часа.

- **P2-7B/4 — Content freshness + responsive overflow:** **(a)+(c)**
  12 P2-3, 12 P2-8 → 🔧 TO-FIX.
  - **Мотивация:**
    - Hardcoded «ЧТ · 8 АПР», «94%» — landing выглядит dead/
      neglected.
    - Tablet 1024-1280px overflow — UX поломка на целевом устройстве
      (staff-ноутбук).
  - **Что делается:**
    - **Content (a):** заменить на neutral-placeholder:
      - «ЧТ · 8 АПР» → «Сегодня» или «Расписание на день».
      - «94%» → «Посещаемость группы» (без числа).
      - Любые конкретные даты/числа в hero/демо-карточках → убрать.
    - **Responsive (c):**
      ```css
      .hero__inner {
          display: grid;
          grid-template-columns: 1.15fr 0.85fr;
      }
      .hero__inner > * {
          min-width: 0;  /* prevent grid blowout */
          overflow-wrap: break-word;
      }
      @media (max-width: 1280px) {
          .hero__inner {
              grid-template-columns: 1fr;  /* single column on tablet */
          }
      }
      ```
  - **Каскад:**
    - 12 P2-3, 12 P2-8 → 🔧 TO-FIX через (a)+(c).
    - NEW-111 (JSON-LD v0.1) — landing structured data.
    - NEW-112 (og update при редизайне) — подтверждается.
  - **Estimate:** ~1 час.

**Итого P2-7B (4 вопроса):** все совпали с рекомендациями.

- **Суммарный estimate P2-7B:** ~3-4 человеко-дня (~25-30 часов).
  Основная часть — ARIA audit + axe-core automation (2д), semantic
  HTML audit (1д).
- **Новые правила линта:** eslint-plugin-jsx-a11y, angular-eslint
  template-accessibility, stylelint prefers-reduced-motion rule.
- **Новые test-интеграции:** axe-core в Playwright, vitest-axe в unit.
- **Новые доки:** `a11y-checklist.md` (NEW-174), axe-core setup
  (NEW-175).
- **Новых NEW-задач:** 2 (NEW-174..175).
- **NEW-110 повышен** из v0.1 в v0.0.0 scope (a11y stylelint +
  ESLint rules).
- **Auto-resolves:** 09 P2-14, 10 P2-7, 12 P2-3, 12 P2-5, 12 P2-7,
  12 P2-8.

---

## P2 — Группа 1 (Contract quality / Type-safety / Serialization)

Ответы на 8 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: QC2 (openapi-typescript), P2-4 (validation), P2-2 (OpenAPI
schema), P2-4/8 (READ_UNKNOWN_ENUM_VALUES_AS_NULL), NEW-100/101
(retrofit event schemas), NEW-60 (shared-events module), CLAUDE.md
contract-first principles.

Примечание: исходная тема P2-1 «HATEOAS» оказалась почти пустой в P2
(все HATEOAS-недочёты ушли в P3 тема F в 16-nit-backlog: 03 P3-2
HealthCheck без HATEOAS — удаляется; 04 P3-5 ExcuseAssembler
PagedModel без nav-links; 05 P3-5 VapidPublicKey без self-link).
Эти P3 закрываются пачкой P3 (раздел 16). Поэтому группа P2-1 собрала
другую тематику — contract quality / type-safety.

- **P2-1/1 — Boolean getter JSON serialization:** **(a)** `@JsonProperty("isHeadman")`
  на getter, audit всех `boolean is*` в response DTO.
  - **Мотивация:**
    - 02 P2-2: Jackson strip `is` prefix → JSON field `headman`,
      frontend ожидает `isHeadman`.
    - QC2 openapi-typescript обнажит mismatch (TS тип следует spec'у).
    - `@JsonProperty` — минимальный impact, явный signal.
  - **Что делается:**
    - Audit `boolean is*` в response DTO × 5 сервисов.
    - Каждое поле: `@JsonProperty("isHeadman") public boolean isHeadman()`.
    - Checkstyle/ArchUnit rule (NEW-109): «boolean getter `isX()` в
      *Response DTO → требуется @JsonProperty("isX")».
  - **Каскад:**
    - 02 P2-2 → 🔧 TO-FIX.
    - QC2 → поле `isHeadman` в TS.
    - NEW-109 (ArchUnit) +1 правило.
  - **Estimate:** ~30 мин.

- **P2-1/2 — Redis cache typed serialization:** **(c)** explicit
  `Jackson2JsonRedisSerializer<T>` per-cache, без `activateDefaultTyping`.
  - **Мотивация:**
    - 02 P2-3: `NON_FINAL` default typing — риск десериализации
      произвольных классов из Redis.
    - Deprecated since Jackson 2.10 (RCE gadgets).
    - Explicit types = максимальная безопасность, zero-ambiguity.
  - **Что делается:**
    - `RedisConfig` refactor:
      ```java
      @Bean
      public RedisCacheManager cacheManager(RedisConnectionFactory cf,
                                            ObjectMapper mapper) {
          var userCache = RedisCacheConfiguration.defaultCacheConfig()
              .serializeValuesWith(fromPair(new StringRedisSerializer(),
                  new Jackson2JsonRedisSerializer<>(User.class)))
              .entryTtl(Duration.ofMinutes(10));
          // ... per-cache config
          return RedisCacheManager.builder(cf)
              .withCacheConfiguration("users", userCache)
              .withCacheConfiguration("subjects", subjectCache)
              .build();
      }
      ```
    - Удалить `activateDefaultTyping` везде.
    - Smoke-test: старые cached keys с type-hint → cleanup (flushdb
      перед deploy).
  - **Каскад:**
    - 02 P2-3 → 🔧 TO-FIX.
    - P2-10/3 (Caffeine) — in-process, нет этой проблемы.
    - Migration: multi-instance → explicit serializers обязательны.
    - NEW-45 (redis-keyspace.md) — раздел serialization policy.
  - **Estimate:** ~3 часа (5-10 serializer beans + cleanup script +
    smoke-test).

- **P2-1/3 — Enum persistence: String → typed enum:** **(a)** рефактор
  `Semester.firstWeekType`, `HeadmanAssistant.permissions` + активация
  конвертера.
  - **Мотивация:**
    - 02 P2-5/6/13: нарушает CLAUDE.md (Java UPPER_CASE enum, PG
      lowercase string, LowercaseEnumConverter autoApply).
    - Type safety: compiler ловит regression.
    - Converter объявлен, но не используется (02 P2-13) — dead code
      до рефакта.
  - **Что делается:**
    - `Semester.firstWeekType`: `String` → `WeekType`.
    - `HeadmanAssistant.permissions`: `String[]` → `List<AssistantPermission>`
      с `@ElementCollection` + `@Enumerated(EnumType.STRING)` or
      custom converter.
    - Миграция НЕ нужна (string column сохраняет совместимость).
    - Unit-тесты: all enum values roundtrip через persistence.
    - ArchUnit rule (NEW-109): «field `String` name matching `*Type`/
      `*Status`/`*Role` в entity → должен быть enum».
  - **Каскад:**
    - 02 P2-5/6/13 → 🔧 TO-FIX.
    - CLAUDE.md enum conventions — regression guard.
    - NEW-109 (ArchUnit) +1 правило.
  - **Estimate:** ~4 часа.

- **P2-1/4 — `@JsonInclude(NON_NULL)` на JWT claims:** **(a)**
  удалить `null` fields из JWT payload.
  - **Мотивация:**
    - 01 P2-5: `group_id: null` для admin/teacher → TS ambiguity.
    - Smaller payload → faster network.
    - `null` vs `undefined` в TS — разная семантика.
  - **Что делается:**
    - `@JsonInclude(JsonInclude.Include.NON_NULL)` на JWT claims DTO.
    - QC2 openapi-typescript → `group_id?: number` (optional).
    - Unit-test: claims для admin → без `group_id` field вовсе.
  - **Каскад:**
    - 01 P2-5 → 🔧 TO-FIX.
    - QC2 (TS types) — optional fields.
  - **Estimate:** ~15 мин.

- **P2-1/5 — Event base fields + ignoreUnknown:** **(a)+(b)** удалить
  `@JsonIgnoreProperties({"source","timestamp"})`, добавить common
  base fields, `ignoreUnknown = true` globally.
  - **Мотивация:**
    - 01 P2-6: `@JsonIgnoreProperties({"source","timestamp"})` —
      ассиметричная сериализация, поля теряются при forward.
    - NEW-100/101 (retrofit event_version, trace_id, occurred_at) —
      эти поля часть contract.
    - `ignoreUnknown = true` — forward-compat при schema evolution.
  - **Что делается:**
    - `shared-events` base class (NEW-60):
      ```java
      public abstract class DomainEvent {
          @JsonProperty("event_version") int eventVersion;
          @JsonProperty("trace_id") String traceId;
          @JsonProperty("occurred_at") Instant occurredAt;
          @JsonProperty("source") String source;
      }
      ```
    - Все event-DTO extend `DomainEvent`, поля auto-included в JSON.
    - Jackson config globally:
      ```java
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      ```
    - Удалить `@JsonIgnoreProperties` annotations где явно.
  - **Каскад:**
    - 01 P2-6 → 🔧 TO-FIX.
    - NEW-100/101 (event schema retrofit) — consistent base fields.
    - NEW-60 (shared-events module) — home для base class.
    - NEW-61 (consolidate schemas).
  - **Estimate:** ~1 час.

- **P2-1/6 — Query string uniqueness check:** **(a)** reject duplicate
  keys в TMA `parseQueryString`.
  - **Мотивация:**
    - 01 P2-7: `hash=X&hash=Y` → attacker shadowing.
    - HMAC validation покрывает, но defense-in-depth.
    - 5 строк кода, major security win для TMA flow.
  - **Что делается:**
    - `parseQueryString`:
      ```java
      var pairs = query.split("&");
      var result = new LinkedHashMap<String, String>();
      for (var pair : pairs) {
          var parts = pair.split("=", 2);
          var key = URLDecoder.decode(parts[0], UTF_8);
          if (result.containsKey(key)) {
              throw new IllegalArgumentException(
                  "Duplicate query param: " + key);
          }
          result.put(key, URLDecoder.decode(parts[1], UTF_8));
      }
      return result;
      ```
    - Security contract-test (P2-8/8): malicious init-data с
      duplicate hash → 401.
  - **Каскад:**
    - 01 P2-7 → 🔧 TO-FIX.
    - P2-8/8 (security contract-tests) — TMA HMAC tests расширены.
    - NEW-164 (SecurityContractsIT).
  - **Estimate:** ~20 мин.

- **P2-1/7 — Enum converter graceful unknown-value:** **(a)** log.warn
  + Prometheus counter + return null (Hibernate side consistent with
  Jackson P2-4/8).
  - **Мотивация:**
    - 01 P2-8: `LowercaseEnumConverter.convertToEntityAttribute`
      throws на unknown value → startup crash при rollback/forward.
    - P2-4/8 уже решил это на уровне Jackson (
      `READ_UNKNOWN_ENUM_VALUES_AS_NULL`). Hibernate — аналогично.
    - Resilience: forward-compat при schema evolution.
  - **Что делается:**
    - `LowercaseEnumConverter.convertToEntityAttribute`:
      ```java
      @Override
      public E convertToEntityAttribute(String dbValue) {
          if (dbValue == null) return null;
          try {
              return Enum.valueOf(enumClass, dbValue.toUpperCase());
          } catch (IllegalArgumentException ex) {
              log.warn("Unknown enum value: class={}, value={}",
                  enumClass.getSimpleName(), dbValue);
              unknownEnumCounter
                  .labels(enumClass.getSimpleName(), dbValue)
                  .inc();
              return null;  // caller handles via @NotNull / default
          }
      }
      ```
    - Prometheus metric `unknown_enum_total{enum_class, value}`.
    - Grafana alert (через Alertmanager P2-9/5): «counter > 0 per
      deploy» → bot webhook.
  - **Каскад:**
    - 01 P2-8 → 🔧 TO-FIX.
    - P2-4/8 (Jackson strategy) — consistent.
    - P2-3/3 (exception handler) — null-safety downstream.
    - P2-9/5 (Alertmanager) — alert on unknown values.
    - NEW-62 (bot webhook) — receiver.
  - **Estimate:** ~30 мин.

- **P2-1/8 — Lombok conventions для Entity/Document:** **(a)**
  `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` + `@Include`
  на `_id`/`@Id`.
  - **Мотивация:**
    - 05 P2-9: `@Data + @Builder + @AllArgsConstructor + @NoArgsConstructor`
      на PushSubscriptionDocument — избыточно. `@Data` генерирует
      `equals/hashCode` на всех полях — опасно для JPA/Mongo (transient
      fields, lazy-loaded collections).
    - Best-practice: `equals/hashCode` только на immutable `_id`.
    - Cognitive cost (developer видит `@Data` и ожидает value-object
      semantics, а это managed entity).
  - **Что делается:**
    - Pattern:
      ```java
      @Getter
      @Setter
      @NoArgsConstructor(access = AccessLevel.PROTECTED)
      @AllArgsConstructor
      @Builder
      @EqualsAndHashCode(onlyExplicitlyIncluded = true)
      @Document(collection = "push_subscriptions")
      public class PushSubscriptionDocument {
          @Id
          @EqualsAndHashCode.Include
          private String id;
          // other fields без @Include
      }
      ```
    - Audit всех entity/document × 5 сервисов.
    - Удалить `@Data` (заменить на `@Getter @Setter` + explicit
      `@EqualsAndHashCode`).
    - `toString` (если нужен) — explicit `@ToString(exclude = {...})`
      без lazy-loaded fields.
  - **Каскад:**
    - 05 P2-9 → 🔧 TO-FIX.
    - NEW-108 (contributing.md) — Lombok conventions section.
    - NEW-109 (ArchUnit) +1 правило «@Data banned on @Entity /
      @Document».
    - NEW-176 (java-conventions.md).
  - **Estimate:** ~1 час (audit + refactor).
  - **NEW-176:** `docs/java-conventions.md` — Lombok usage policy,
    entity vs value-object semantics, banned annotations на entity.

**Итого P2-1 (8 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-1:** ~10-12 часов (1.5 человеко-дня).
- **Новые ArchUnit rules (+3):** boolean `isX()` requires @JsonProperty,
  enum-like String field → enum, `@Data` banned on entity.
- **Новые доки:** `java-conventions.md` (NEW-176).
- **Новых NEW-задач:** 1 (NEW-176).
- **Auto-resolves:** 01 P2-5, P2-6, P2-7, P2-8; 02 P2-2, P2-3, P2-5,
  P2-6, P2-13; 05 P2-9.

---

## P2 — Группа 5 (Hardcoded constants / Dead code / Cleanup)

Ответы на 10 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Связка: NEW-109 (ArchUnit), NEW-123 (openapi-runtime-conformance),
QC2 (openapi-typescript), Q13c (fail-fast), C0-3 (outbox), C0-7 (JWT
cookie), P2-2/6 (nginx basic-auth swagger), P2-3/8 (empty catch
audit), P2-9/5 (Alertmanager), NEW-28 (ShedLock), NEW-118 (lesson
lifecycle).

Содержит разношёрстные P2-пункты: HTTP status correctness, unit suffix
docs, fail-fast env, dead code cleanup, missing admin endpoints,
eventing bugs, DLQ operations hygiene, gateway/notification/schedule
operational nits.

- **P2-5/1 — HTTP 204 for void endpoints:** **(a)** audit × 5 сервисов.
  - **Мотивация:** 01 P2-11 `changePassword` → 200 вместо 204.
    NEW-123 (swagger-request-validator) требует spec↔runtime
    соответствия.
  - **Что делается:** audit всех `@PostMapping`/`@PutMapping`/
    `@PatchMapping`/`@DeleteMapping` с пустым body → `ResponseEntity.noContent().build()`
    или `@ResponseStatus(HttpStatus.NO_CONTENT)`. OpenAPI spec
    обновлён.
  - **Каскад:** 01 P2-11 → 🔧 TO-FIX. NEW-123 (CI conformance),
    P2-2/3 (runtime под контракт).
  - **Estimate:** ~1 час.

- **P2-5/2 — Unit suffix в time fields:** **(a)** rename
  `expiresIn` → `expiresInSeconds` + @Schema units audit.
  - **Мотивация:** 01 P2-10. Имя-как-документация устраняет
    ambiguity. Breaking change для frontend, но QC2 openapi-typescript
    ловит в compile-time.
  - **Что делается:** rename field в TokenResponse. Audit всех
    time-fields в DTO × 5 сервисов (`interval`, `timeout`, `duration`,
    `delay`, `ttl`) — суффикс явный (`Seconds`/`Millis`/`Minutes`)
    или ISO-8601 Duration.
  - **Каскад:** 01 P2-10 → 🔧 TO-FIX. QC2 → поле переименовывается
    в TS. P2-2/4 (@Schema description/example) — units в description.
  - **Estimate:** ~30 мин + 1 час audit.

- **P2-5/3 — Fail-fast для TMA_BOT_TOKEN:** **(a)**
  `@ConfigurationProperties` + `@NotBlank` validation.
  - **Мотивация:** 01 P2-12. Consistency с Q13c (GRPC_SECRET
    fail-fast). NEW-24 (audit всех `${VAR:}`).
  - **Что делается:**
    ```java
    @ConfigurationProperties(prefix = "tma")
    @Validated
    public record TmaProperties(@NotBlank String botToken) {}
    ```
    Startup падает при отсутствии env.
  - **Каскад:** 01 P2-12 → 🔧 TO-FIX. Q13c pattern. NEW-24 покрывает.
    P2-8/8 (security contract-test) — empty TMA_BOT_TOKEN → fail-fast.
  - **Estimate:** ~20 мин.

- **P2-5/4 — Dead code cleanup:** **(a)+(b)** удалить сейчас +
  ArchUnit/IntelliJ inspection.
  - **Мотивация:** 02 P2-9/10, 03 P2-4/5. Dead code — cognitive cost,
    confusion для future developers.
  - **Что делается:**
    - Audit × 5 сервисов: `grep "findBy.*Containing\|findAll.*get(0)\|existsByX"`
      + IntelliJ «Safe Delete» проверка unused methods.
    - Удалить: `SubjectRepository.findByNameContainingIgnoreCase`,
      `existsByGroupIdAndDateAndLessonNumber`,
      `LessonRepository.findByScheduleItemIdAndDateBetween`, etc.
    - NEW-109 (ArchUnit) +1 rule: «repository-метод с 0 usage в
      production code → fail». Требует анализа call-graph —
      ArchUnit ограничен, альтернатива IntelliJ `qodana`
      (command-line inspections) в CI.
  - **Каскад:** 02 P2-9/10, 03 P2-4/5 → 🔧 TO-FIX. NEW-109 +1 rule
    (или qodana integration).
  - **Estimate:** ~1 час.

- **P2-5/5 — Missing admin endpoint + validation:** **(a)** campus-
  settings endpoint, GroupNameParser tighten, firstWeekType cross-
  field.
  - **Что делается:**
    - 02 P2-11: `PUT /api/academic/admin/campus-settings` (admin-only):
      body `UpdateCampusSettingsRequest { latitude, longitude, radius }`.
      @RequireRole(ADMIN). HATEOAS с self-link.
    - 02 P2-12: GroupNameParser regex ужесточить (`^[а-яА-Я]{2,6}-\d{1,3}$`
      или similar — точный формат подтвердить с бизнесом). Audit
      existing names, миграция edge-cases вручную (admin-scripts.md
      NEW-33).
    - 03 P2-11: `ScheduleItemService.updateScheduleItem` validate
      `firstWeekType == activeSemester.firstWeekType`. Иначе
      `BadRequestException("Неверный firstWeekType для активного
      семестра")`. P2-4/1 custom cross-field validator (новая аннотация
      `@ConsistentWithSemester`).
  - **Каскад:** 02 P2-11/12, 03 P2-11 → 🔧 TO-FIX. P2-4/1 cross-field
    validation — расширяется. NEW-33 (admin-scripts.md) — migration
    guide для edge-cases names.
  - **Estimate:** ~4 часа.

- **P2-5/6 — Eventing bugs:** 02 P2-14 fix + 02 P2-15 AUTO + 03 P2-10
  schema extend.
  - **Что делается:**
    - 02 P2-14: `UserService.patchUser` — evict `groups` cache при
      любом `isHeadman`/`groupId` change (not только set):
      ```java
      @Caching(evict = {
          @CacheEvict(value = "groups", key = "#request.groupId",
                      condition = "#oldUser.groupId != #request.groupId"),
          @CacheEvict(value = "groups", key = "#oldUser.groupId",
                      condition = "#oldUser.groupId != #request.groupId"),
          @CacheEvict(value = "users", key = "#userId")
      })
      ```
    - 02 P2-15: **AUTO-RESOLVED через C0-3 outbox** — события
      публикуются только после commit, rollback не оставляет
      orphan events.
    - 03 P2-10: event schema `lesson.blocked` расширить
      `blocked_by_user_id: int64`. Update NEW-118 (lesson lifecycle)
      — blocked как часть lifecycle.
  - **Каскад:** 02 P2-14 → 🔧 TO-FIX. 02 P2-15 → ✅ AUTO-RESOLVED
    via C0-3. 03 P2-10 → 🔧 TO-FIX (schema extend). NEW-118
    (lifecycle docs) обновляется.
  - **Estimate:** ~2 часа.

- **P2-5/7 — DLQ TTL + reset-marker history:** **(a)** DLQ ops
  hygiene + historical note.
  - **Что делается:**
    - 03 P2-7: `scheduleDlqQueue`:
      ```java
      Map.of(
          "x-message-ttl", 604_800_000L,  // 7 days
          "x-max-length", 10_000,
          "x-overflow", "reject-publish-dlx"  // overflow → parking lot
      )
      ```
      Альтернатива — secondary DLQ (parking lot queue) для messages
      старше 7д. Alertmanager alert (P2-9/5) на `queue_messages
      > 1000`.
    - 03 P2-14: в `docs/architecture/database-schema.md` раздел «Migration
      history» — historical note про V8/V9 reset-marker iterations.
      Один абзац — «история незакрытой формулы parity, решена в
      03 P0-5 через WeekParityResolver».
  - **Каскад:** 03 P2-7 → 🔧 TO-FIX. 03 P2-14 → docs note. NEW-136
    (DLQ recovery runbook) +TTL config. P2-9/5 (Alertmanager) alert.
  - **Estimate:** ~2 часа.

- **P2-5/8 — Gateway config cleanup:** **(a)** одной пачкой.
  - **Мотивация:** 9 мелких правок в одном application.yml gateway.
    Ревизия одним commit'ом.
  - **Что делается:**
    - 07 P2-1: `management.endpoint.health.show-details: when_authorized`
      (prod); dev остаётся `always`.
    - 07 P2-2: `/actuator/prometheus` защитить через nginx basic-auth
      (consistent с P2-2/6 swagger — тот же htpasswd, NEW-125).
    - 07 P2-3: `DedupeResponseHeader` сохранить (важен при CORS
      merge через несколько filters).
    - 07 P2-5: **AUTO-RESOLVED через C0-7** (JWT HttpOnly cookie —
      Set-Cookie станет обязательным, gateway config обновится).
    - 07 P2-7: `max-in-memory-size: 12MB` — оставить, comment:
      `# для excuse file upload (10MB + multipart overhead)`.
      Связка с P2-9/3 (nginx 25m для excuse endpoint).
    - 07 P2-8: вынести `auth-service-url` в один
      `GatewayRoutingProperties` + `@ConfigurationProperties`.
    - 07 P2-9: comment `@EnableScheduling` — «для PublicKeyConfig
      scheduled key-refresh (каждые 5 мин)».
    - 07 P2-10: dev CORS `allowed-headers: "*"` — accept. Prod через
      env `CORS_ALLOWED_HEADERS` (уже настроено через NEW-43).
    - 07 P2-11: remove `springdoc.enable-native-support: true`
      (legacy).
    - NEW-177: `docs/gateway-config.md` — ревизия всех настроек,
      rationale.
  - **Каскад:** 07 P2-1/2/3/5/7/8/9/10/11 → 🔧 TO-FIX / AUTO /
    ACCEPT mixed. P2-2/6 (swagger basic-auth) — prometheus тоже.
    C0-7 (JWT cookie) — Set-Cookie. P2-9/3 (nginx limits) —
    consistency. NEW-125 (admin-access.md) — htpasswd extends.
  - **Estimate:** ~2 часа.
  - **NEW-177:** `docs/gateway-config.md` — ревизия каждой настройки
    gateway yml с rationale и trade-offs. Co-location с nginx-config.md
    (NEW-152).

- **P2-5/9 — Notification-service ops cleanup:** **(a)** VAPID
  public, dedup @Valid, strategy-pattern templates, metrics.
  - **Что делается:**
    - 05 P2-2: `PushController.getVapidPublicKey` remove `@RequireRole`
      — public by design (название же public key).
    - 05 P2-3: remove duplicate `@Valid @RequestBody` в controller
      (оставить только в interface, CLAUDE.md contract-first).
    - 05 P2-5: refactor `WebPushDeliveryService.buildTitle/Body`
      в Strategy-pattern:
      ```java
      interface NotificationTemplate {
          EventType supportedEvent();
          String buildTitle(DomainEvent event);
          String buildBody(DomainEvent event);
      }
      @Component class LessonStartedTemplate implements NotificationTemplate { ... }
      @Component class ExcuseApprovedTemplate implements NotificationTemplate { ... }
      // Map<EventType, Template> — auto-discovered через Spring
      ```
    - 05 P2-6: `application.yml` — `attendance_db` явно в dev
      (hardcoded), prod через env. Add comment: `# Q16b shared-DB
      pattern: push_subscriptions в attendance_db`.
    - 05 P2-8: Micrometer counters:
      - `notification_delivered_total{channel, event_type, status}`
      - `notification_delivery_duration_seconds{channel}`
      - STOMP: `stomp_subscribe_total{topic_pattern}`,
        `stomp_active_sessions`.
      - Alertmanager rule: `rate(notification_delivered_total{status="failure"}[5m]) > 0.1` → alert.
    - 05 P2-10: `RequestContext.setRole` graceful:
      ```java
      try { role = UserRole.valueOf(header.toUpperCase()); }
      catch (IllegalArgumentException ex) {
          log.warn("Unknown role header: {}", header);
          unknownRoleCounter.labels(header).inc();
          throw new UnauthorizedException();  // 401, не 500
      }
      ```
  - **Каскад:** 05 P2-2/3/5/6/8/10 → 🔧 TO-FIX. QA4 (метрики) +
    notification metrics. P2-9/5 (Alertmanager) failure alerts.
    P2-1/7 (enum converter graceful) consistency. NEW-178.
  - **Estimate:** ~1 день (strategy-pattern + metrics + refactors).
  - **NEW-178:** `docs/notification-template-catalog.md` — per-event
    templates list + variables schema (what each event offers для
    title/body interpolation). Co-location с `websocket-protocol.md`
    (NEW-134).

- **P2-5/10 — Schedule-service nits + 04 AUTO-RESOLVED:** **(a)**
  для 03, подтвердить auto-resolve для 04.
  - **Что делается:**
    - 03 P2-3: `IsoParityReconciler` INSERT с RETURNING:
      ```sql
      INSERT INTO reconciler_markers (...) VALUES (...)
      ON CONFLICT (semester_id) DO NOTHING
      RETURNING id;
      ```
      `Optional<Long>` → если empty → другой instance уже записал
      (ShedLock NEW-28 гарантирует один writer, но на всякий случай).
      log.debug при empty.
    - 03 P2-16: `@Profile("!test")` → `@Profile("prod | dev")` или
      `@ConditionalOnProperty(name = "scheduling.enabled",
      havingValue = "true", matchIfMissing = true)`. Test profiles
      без cron по default.
    - 04 P2-3 → ✅ AUTO-RESOLVED через P2-3/8 (audit empty catch +
      Checkstyle EmptyCatchBlock rule).
    - 04 P2-5 → ✅ AUTO-RESOLVED через P2-3/1 (catch-all Exception →
      detail="Обратитесь в поддержку, correlation=<trace_id>").
    - 04 P2-8 → ✅ AUTO-RESOLVED через C0-1 (UserContextFilter
      strengthens + SecurityIdorIT NEW-31 тест).
  - **Каскад:** 03 P2-3, 03 P2-16 → 🔧 TO-FIX. 04 P2-3/5/8 → ✅
    AUTO-RESOLVED.
  - **Estimate:** ~1 час.

**Итого P2-5 (10 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-5:** ~2.5 человеко-дня (~20 часов).
  Основная работа — P2-5/5 (admin endpoint + validation, 4ч), P2-5/9
  (notification ops, 1д), P2-5/8 (gateway cleanup, 2ч).
- **Новые доки:** `gateway-config.md` (NEW-177),
  `notification-template-catalog.md` (NEW-178).
- **Новых NEW-задач:** 2 (NEW-177, NEW-178).
- **ArchUnit rules +1 (или qodana inspection):** dead-code detection
  для repository methods.
- **Auto-resolves:** 01 P2-10, P2-11, P2-12; 02 P2-9, P2-10, P2-11,
  P2-12, P2-14, P2-15; 03 P2-3, P2-4, P2-5, P2-7, P2-10, P2-11,
  P2-14, P2-16; 04 P2-3, P2-5, P2-8; 05 P2-2, P2-3, P2-5, P2-6,
  P2-8, P2-10; 07 P2-1, P2-2, P2-3, P2-5, P2-7, P2-8, P2-9, P2-10,
  P2-11.

---

## P2 — Группа 12 (Misc / финальная метла)

Ответы на 7 вопросов. Зафиксированы 2026-04-19 (восьмая сессия).
Финальная группа P2 — собрала оставшиеся пункты, не попавшие в
предыдущие 11 групп.

- **P2-12/1 — Missing env в .env.prod.example:** **(a)** добавить
  DOMAIN, CERTBOT_EMAIL + явная проверка в init-letsencrypt.sh.
  - **Что делается:**
    - `.env.prod.example` (NEW-20 восстанавливает): добавить
      `DOMAIN=ruttrack.site`, `CERTBOT_EMAIL=admin@ruttrack.site`.
    - `init-letsencrypt.sh`:
      ```bash
      : "${DOMAIN:?ERROR: DOMAIN not set — check .env.prod}"
      : "${CERTBOT_EMAIL:?ERROR: CERTBOT_EMAIL not set — check .env.prod}"
      ```
    - NEW-23 (maintenance window LE cert runbook) — checklist items.
  - **Каскад:** 13 P2-5 → 🔧 TO-FIX. NEW-20 + NEW-23.
  - **Estimate:** ~15 мин.

- **P2-12/2 — Service-layer @RequireRole для admin-mutating:** **(a)**
  defense-in-depth + ArchUnit rule.
  - **Мотивация:** 02 P2-7: `ThresholdService.setGlobalThreshold`
    без service-level security. Controller-only защита ломается если
    кто-то добавит event-listener/scheduled job вызывающий тот же
    service.
  - **Что делается:**
    - Audit admin-mutating методы в academic/schedule/attendance
      services. Добавить `@RequireRole(ADMIN)` на service-метод
      (не только контроллер).
    - `@RequireRole` aspect проверяет `RequestContext` → throws
      `AuthorizationException`.
    - ArchUnit rule (NEW-109) +1: «methods starting with `setGlobal*`,
      `deleteAll*`, `overrideAll*` в service-layer → требуется
      `@RequireRole(ADMIN)`».
    - NEW-31 (SecurityIdorIT) test: event-listener вызывает
      ThresholdService.setGlobalThreshold с non-admin context →
      AuthorizationException.
  - **Каскад:** 02 P2-7 → 🔧 TO-FIX. NEW-109 +1 rule. NEW-31
    расширяется.
  - **Estimate:** ~1 час.

- **P2-12/3 — @CreationTimestamp / @UpdateTimestamp audit:** **(a)**
  Hibernate-native аннотации на все timestamp поля.
  - **Мотивация:** 02 P2-8 `Semester.createdAt` без @PrePersist —
    может быть null при save.
  - **Что делается:**
    - Audit entity × 5 сервисов:
      ```java
      @CreationTimestamp
      @Column(name = "created_at", updatable = false, nullable = false)
      private Instant createdAt;

      @UpdateTimestamp
      @Column(name = "updated_at")
      private Instant updatedAt;
      ```
    - Hibernate auto-sets на insert/update.
    - ArchUnit rule (NEW-109) +1: «field `createdAt` / `updatedAt` в
      @Entity → требуется @CreationTimestamp / @UpdateTimestamp».
    - Memory `feedback_flyway_no_edit` — не требует миграции (schema
      уже TIMESTAMPTZ NOT NULL DEFAULT NOW()).
  - **Каскад:** 02 P2-8 → 🔧 TO-FIX. NEW-109 +1 rule. CLAUDE.md
    TIMESTAMPTZ правила.
  - **Estimate:** ~1 час.

- **P2-12/4 — LessonService WeekType no-match: document + test:**
  **(a)** документирование + golden-test regression guard.
  - **Что делается:**
    - `LessonService.java` javadoc: «Schedule items с `weekType !=
      activeParity` исключаются. Корректное поведение: пара на
      нечётной неделе не показывается на чётной».
    - Golden-table (P2-8/4) `week-parity.json` +cases на
      ODD-only/EVEN-only визуализацию.
    - `docs/architecture/architecture.md` — раздел «Schedule week-parity semantics».
  - **Каскад:** 03 P2-2 → 🔧 TO-FIX. P2-8/4 (golden tests) —
    regression guard. 03 P0-5 (WeekParityResolver) — AUTO чтение.
  - **Estimate:** ~30 мин.

- **P2-12/5 — JWT public key periodic publish:** **(a)** @Scheduled
  каждые 5 мин + ShedLock.
  - **Мотивация:** 01 P2-9: `JwtService.init` publish один раз.
    Ротация keypair → старый key в Redis → downstream services не
    валидируют new tokens до TTL expiration.
  - **Что делается:**
    - auth-service:
      ```java
      @Scheduled(cron = "0 */5 * * * *")
      @SchedulerLock(name = "jwtKeyPublish", lockAtMostFor = "4m")
      public void publishCurrentPublicKey() {
          var pubKey = jwtKeyPair.getPublic();
          var encoded = Base64.getEncoder().encodeToString(pubKey.getEncoded());
          redisTemplate.opsForValue().set(
              "jwt:public-key:current",
              encoded,
              Duration.ofMinutes(10)
          );
      }
      ```
    - Downstream services: local 1-мин TTL cache → propagation ≤ 6 мин.
    - NEW-155 (secret-rotation runbook) — раздел JWT rotation procedure.
  - **Каскад:** 01 P2-9 → 🔧 TO-FIX. NEW-28 (ShedLock) — @Scheduled
    защищён. NEW-155 +section. NEW-67 (actuator/info) — тот же pattern.
  - **Estimate:** ~2 часа.

- **P2-12/6 — Testcontainers Python (replace fakeredis):** **(a)**
  testcontainers-python Redis.
  - **Мотивация:** 14 P2-10: fakeredis не поддерживает Lua/pub-sub.
  - **Что делается:**
    - `tests/conftest.py` (NEW-53):
      ```python
      import pytest
      from testcontainers.redis import RedisContainer

      @pytest.fixture(scope="session")
      def redis_container():
          with RedisContainer("redis:7-alpine") as redis:
              yield redis
      ```
    - Migrate fakeredis tests → real Redis.
    - CI time impact: +5s startup (reused в сессии).
  - **Каскад:** 14 P2-10 → 🔧 TO-FIX. NEW-53 расширяется. NEW-158
    — Python parallel.
  - **Estimate:** ~3 часа.

- **P2-12/7 — Reminders остаются в Python + pytest+freezegun:**
  **(a)** подтверждение scope v0.0.0.
  - **Решение:**
    - Reminders остаются в Python bot (v0.0.0). APScheduler для
      scheduling. In-memory state с rehydration на startup.
    - Migration в Java notification-service → **v0.1** (future-ideas.md).
    - Python tests: pytest + freezegun для time-manipulation:
      ```python
      from freezegun import freeze_time

      @freeze_time("2026-04-19 10:25:00", tz_offset=3)
      async def test_reminder_sent_at_lesson_start(bot, lesson):
          scheduler = ReminderScheduler(bot)
          await scheduler.tick()
          assert bot.sent_messages == [("start", lesson.id)]
      ```
    - 3 scenarios × 5 test cases = 15 tests minimum.
  - **Каскад:** 14 P2-3 → 🔧 TO-FIX через (a). NEW-53 +freezegun
    fixture. NEW-118 (lesson lifecycle) — future migration v0.1.
    06 P0-5 (handshake audit) — related coverage.
  - **Estimate:** ~2 часа.

**Итого P2-12 (7 вопросов):** все совпали с рекомендациями.

- **Суммарный estimate P2-12:** ~10 часов (~1.5 человеко-дня).
- **ArchUnit rules +2:** admin-mutating @RequireRole, timestamp
  аннотации на entity.
- **Новых NEW-задач:** 0 (все расширяют existing).
- **Auto-resolves:** 01 P2-9; 02 P2-7, P2-8; 03 P2-2; 13 P2-5; 14
  P2-3, P2-10.

---

## 🎉 ВЕСЬ P2 ЗАКРЫТ (2026-04-19, восьмая сессия)

**Статистика P2:**
- **12 групп** обсуждены: P2-11 (Event schemas), P2-2 (OpenAPI),
  P2-3 (Error handling), P2-4 (Validation), P2-10 (Performance),
  P2-9 (Docker/compose), P2-8 (Test gaps), P2-6 (Логи-нюансы),
  P2-7A+7B (Frontend UX/a11y), P2-1 (Contract quality), P2-5
  (Cleanup), P2-12 (Misc).
- **Всего consolidated вопросов P2:** 79 (покрыли ~165 audit-пунктов).
- **Новых NEW-задач в P2:** 57 (121 → 178).

**Архитектурные изменения P2 (крупные):**
- **P2-9/5:** новый контейнер `alertmanager` (unified router для
  Prometheus + Loki alerts). Меняет QA4+NEW-62 (bot webhook payload =
  Alertmanager format).
- **P2-6/4:** notification-web становится stateful — own MongoDB
  `notification_db` (P2-9/6 reserved → **ACTIVATED**). CLAUDE.md
  update (NEW-168).
- **NEW-110** (stylelint a11y) повышен из v0.1 в v0.0.0 scope.

**Новые модули/компоненты:**
- `shared-web` (Q16a) — GlobalExceptionHandler + validation +
  masking + OpenApi customizer
- `shared-events` (NEW-60) — общий DomainEvent base с version/trace_id
- `shared-test-containers` (NEW-158) — Testcontainers fixtures
- `shared-logback` (NEW-68) — JSON + MaskingConverter + unified labels

**Новые документы (суммарно за P2):** ~25 новых docs.

---

**ИТОГО ВЕСЬ ОПРОС ВЛАДЕЛЬЦА:**
- **P0** (53 исходных): все закрыты (10 P0-кластеров + 6 точечных
  групп)
- **P1** (136 исходных): все закрыты (пачки A-E + 8 extra P0-
  остаточных)
- **P2** (165 исходных): все закрыты (12 групп, 79 вопросов)
- **P3** (110 nits): разбираются одной пачкой через 16-nit-backlog
  (отдельный workflow, не в этом опросе)

**Следующий шаг:** разметка отчётов 01-16 (вариант A — один проход)
→ 99-executive-summary.md → финальный коммит.

---

## Meta-решение M2 — весь P2 в v0.0.0

**Решение (2026-04-19):** Владелец выбрал детально обсудить **все 165
P2-вопросов**, включить их в scope v0.0.0. Причина — важна чистота
кода и корректность с первого релиза, а не после. Стратегия из
изначального плана («P2 → v0.1 backlog») отменена.

**Следствия:**
- Объём работы для v0.0.0 резко возрастает — с ~30 P0 + ~33 P1 + cluster-фиксов
  до ~33 P1 + 165 P2. Срок v0.0.0 сдвигается.
- Каждая P2-группа обсуждается детально (как P0/P1), с каскадами и NEW-задачами.
- 12 тематических групп (P2-1..12) обсуждаются по одной или парами.

**Порядок обсуждения:** от самых тесно связанных с уже принятыми
решениями (P2-3 error handling → shared-web 16a; P2-11 event schemas
→ 19a+QD3+QA3; P2-4 validation → QD2 gate; P2-2 OpenAPI → QC2 type-gen)
к более независимым (P2-1 HATEOAS, P2-5 constants, P2-12 misc).

## Рекомендованный порядок групп

1. **P2-11 Event schemas** — прямо связано с 19a+QA3+QD3+NEW-100.
2. **P2-2 OpenAPI аннотации** — prerequisite для QC2 type-gen.
3. **P2-3 Error handling edge-cases** — расширяет shared-web из 16a.
4. **P2-4 Validation constraints** — дешёвое покрытие, ловит данные на входе.
5. **P2-10 Performance hotspots** — срочная часть (индексы) для стабильного прода.
6. **P2-9 Docker/compose nits** — надёжность прода.
7. **P2-8 Test gaps** — связка с QD2 coverage-gate.
8. **P2-6 Логи-нюансы** — связка с QA7 structured logs.
9. **P2-7 Frontend UX / a11y** — большая группа (~30 пунктов).
10. **P2-1 HATEOAS** — большая, но относительно изолированная.
11. **P2-5 Hardcoded constants** — рефакторинг.
12. **P2-12 Misc** — заключительная метла.

---

## Изменения в отчётах (audit trail)

Сюда после каждого ответа пишется: «убрал/изменил/добавил X в отчёте Y,
причина — ответ на вопрос Z или meta-решение M».

### Запланированные правки от 01-Q1 (ACCEPTED initial_password by design)

После завершения опроса по всем 14 отчётам делаются эти изменения. Сейчас
не применяются — ждём, чтобы возможные следующие ответы не противоречили
(например, если 06-Q2 окажется «надо show-once», часть пометок изменится).

- 01-auth-service.md:
  - P0-2 → переместить в раздел «## Принято как есть» с пометкой
    «✅ ACCEPTED BY OWNER (OWNER-ANSWERS.md 01-Q1, M1)».
  - Связь P0-2 ↔ P1-8 (password recovery) — пометить «accepted, recovery
    остаётся как отдельная фича».
- 02-academic-service.md:
  - P0-1 → «## Принято как есть» с той же пометкой.
  - P0-1 в разделе «Зависимости» → удалить упоминание блокирования.
  - P2-1 (DEBUG-логи) → СОХРАНИТЬ, добавить пояснение «отдельный вектор
    утечки initialPassword через лог-файлы остаётся даже при accept-tradeoff».
- 06-notification-bot.md:
  - P0-3 → «## Принято как есть» (но обсудить отдельно 06-Q2 про
    history of show-once — может породить под-правку).
- 08-shared-proto-events.md:
  - P0-1 → «## Принято как есть».
  - Зависимости в разделе блокирования других P0 → удалить.
- 10-frontend-web-panel.md:
  - P2-13 → «## Принято как есть» с пометкой «фича по решению владельца».
- 13-infra-docker-ci.md:
  - P0-3 (`.env.prod`) → СОХРАНИТЬ, удалить только упоминание зависимости
    от 08 P0-1 (зависимости больше нет).
- 15-cross-cutting-issues.md:
  - Кластер **C0-2 целиком** → переместить в новый раздел «## Распущенные
    кластеры (accepted by owner)» с описанием почему.
  - Dependency graph → убрать узел C0-2.
  - Порядок исполнения P0-кластеров → C0-2 удалить, перенумеровать или
    оставить пропуск (договоримся).
  - Метрики «~30 P0 закрывается кластерами из 53» → пересчитать
    (минус 4 P0 + минус 1 P2 от C0-2).
  - Q2 в разделе «Вопросы к владельцу» → пометить «✅ AUTO-RESOLVED».
- 12-frontend-landing.md:
  - Q5 → пометить «✅ AUTO-RESOLVED через M1». Ничего из текста проблем
    не меняется (там вопрос про privacy-страницу не привязан к P0/P1).
- PROGRESS.md:
  - Строка 09 (auth) и 02 — переписать счётчики P0 (минус 1 каждому).
  - Строка 06 — минус 1 P0 (без обсуждения 06-Q2 пока что).
  - Строка 08 — минус 1 P0.
  - Строка 15 — пересчитать «10 P0-кластеров» → «9», обновить общий итог.

### Новые задачи, порождённые ответом

- **NEW-1:** Добавить раздел «Принятые архитектурные tradeoffs» в
  `docs/architecture/architecture.md` (или новый `docs/security-model.md`). Список:
  plaintext-`initial_password` цепочка. Цель — чтобы будущие аудиты
  не пере-открывали дискуссию.
- **NEW-2:** Файл `docs/future-ideas.md` создан. Зафиксировать в
  `CLAUDE.md` существование этого файла.

---

## Open questions (skipped, для v0.1)

_(пусто — будет наполняться, если владелец ответит «скип» или «не знаю»)_
