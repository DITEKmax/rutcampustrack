# M09 Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «OTP flow p95: HTTP body 45ms → RabbitMQ event 180ms»
- **Surprises:** «обнаружил что cleanupOrphans ещё вызывается из IndexInitializer»
- **Вопросы к владельцу:** «нужна ли retry policy на OTP consumer при bot down?»
- **Технические долги:** «оставил TODO в X — закрою в M10 / v0.1»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md Q-P0-4/5, Q-P0-1/2 в 14, Q-P0-2 в 12).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## Открытые вопросы (решить до старта Группы 2)

1. **OTP consumer failure handling** — если `notification-bot` down
   дольше TTL (5 мин) когда приходит `otp.requested`, что делать?
   - Вариант A: DLQ + alert в Alertmanager → админ видит что student
     не получил код, может руками переотправить.
   - Вариант B: bot на `ApplicationReadyEvent` читает backlog, но коды
     уже просрочены → игнорирует.
   - Вариант C: auth-service при retry от клиента генерит новый код
     (старый в Redis перезаписывается через `SET EX=300`). Самый
     простой, self-healing, но каждый retry = новое событие в Rabbit.
   - **Рекомендую C** (consistency с текущей логикой `POST
     /auth/otp/request` — идемпотентность по `telegram_id`).

2. **`.env.prod` TELEGRAM_BOT_USERNAME** — использовать существующий
   `BOT_TOKEN` для авто-discovery через `getMe()` API, или явная
   переменная? Явная переменная проще и независима от Telegram API.

3. **Coverage-gate для latecheckin / handlers** — 70% как в
   OWNER-ANSWERS, или можно 80% для нового кода (pilot для M08
   `diff-coverage ≥ 80%`)? Если 80% — estimate растёт ~0.5д.

## Отложено в v0.1 (не делаем в M09)

- **01 P0-1 `auth-api-contract`** — структурный refactor (вынести
  `AuthController` + все DTO в отдельный Gradle-модуль). Не блокер
  прода. Документируется в `docs/archive/future-ideas.md`.
- **NEW-54 CSP-Report endpoint** — `report-uri` в CSP web-panel
  (owner явно указал «v0.1»).
- **Magic-link для первого входа** (01-Q1 accepted tradeoff).

## 2026-04-23 — Группа 6 ЗАКРЫТА (role check + NEW-121 audit)

### Что сделано (коммит — следующий)

- **06 P1-1**: `bot/handlers/excuse.py` + `late_checkin.py` — перед publish
  проверяют `is_headman` через `academic_client.get_user_by_telegram_id`.
  Helper `_verify_headman` вынесен в `excuse.py`, `late_checkin.py`
  импортирует его (избегаем дублирования 20 строк). Ошибка gRPC →
  fail-closed: «Не удалось проверить права» без publish.
- **23 unit-тестов** (11 excuse + 12 late_checkin), handlers coverage
  92.83% (>70% gate), 198 passed суммарно.
- **DECISIONS D6**: не создаём `excuse.approved/rejected.json` — flow
  уже single `excuse.decided` со status-полем, симметрично
  `late_checkin.decided` (избегаем асимметрии + dup handler-кода).

### NEW-121 — audit asymmetric flows (bot → backend)

**Inventory всех синхронных REST-вызовов из notification-bot:**

| From | To | Method | Purpose | Verdict |
|------|-----|--------|---------|---------|
| `/login` handler | auth-service `/auth/otp/request` | POST | инициировать OTP | OK — command (не decision), нет смысла через Rabbit command queue для одного endpoint'а |
| `/status` handler | schedule gRPC `get_active_lesson` | gRPC | get active lesson | OK — read-only query, Rabbit не подходит (synchronous reply required) |
| `/status` handler | attendance REST `GET /api/attendance/reports/student/records` | GET | read attendance history | OK — read-only query |
| excuse/late_checkin callback | academic gRPC `get_user_by_telegram_id` | gRPC | role check (G6) | OK — read-only query, JUST added in G6 |
| various consumer notif-handlers | academic gRPC `get_group_members`, `get_subjects_by_ids` | gRPC | resolve names | OK — read-only query |

**Нет** оставшихся asymmetric «bot publishes decision через REST». Все
decision events идут через Rabbit (`excuse.decision`, `late_checkin.decision`).
Все REST/gRPC-вызовы — либо read-only query (natural fit), либо single
command-trigger (OTP).

**Вывод:** audit NEW-121 закрыт без action items. Закрепляем pattern:
«bot публикует decision events через Rabbit, читает лукапы через gRPC,
POST-команды только для simple command-triggers без решений». Новые
handler'ы должны следовать этому.

## 2026-04-23 — Группа 2 ЗАКРЫТА (G2.6 + G2.7 в финальной сессии)

### Что сделано (коммиты)
- `3d6dfd1` feat(events): схема + OtpRequestedEvent класс
- `807b1f2` feat(auth): 204 No Content, OtpService void, OpenAPI + frontend types
- `b851221` feat(bot): otp_requested consumer + /login рефактор + tracker новые методы
- `70bd2db` test(auth): OtpRequestedContractTest (3 теста зелёные)
- `d4ca2ca` wip(auth): AuthOtpFlowIT (был @Disabled — ниже разбор)
- **(следующий)** feat(auth): AuthOtpFlowIT + architecture.md (G2.6+G2.7)

### G2.6 root cause (разобрано 2026-04-23)

**Истинная причина была не в гипотезе #1/#3/#4 (каждая по отдельности),
а в сочетании двух факторов в `RabbitConfig.java`:**

1. `@Configuration` + `@ConditionalOnBean(ConnectionFactory.class)` —
   user-`@Configuration` обрабатывается Spring'ом **до** autoconfig'а, а
   `ConnectionFactory` создаёт именно `RabbitAutoConfiguration`.
   Condition оценивался false → наш `@Bean rabbitTemplate` + converter
   **не создавались никогда**, даже когда Rabbit полностью доступен.
2. `DomainEventListener` (`@Component @ConditionalOnBean(RabbitTemplate.class)`)
   всё равно находил autoconfig-default `RabbitTemplate` (он создаётся
   Spring Boot'ом autoconfig'ом) и инжектил его. У default-шаблона —
   `SimpleMessageConverter`, который сериализует объекты как
   `application/x-java-serialized-object` байты. Тест ожидает UTF-8 JSON
   с полем `event_type` → `path("event_type").asText()` видит пустую
   строку, assertion падает. Но на уровне логов это выглядело как
   «message вообще не пришёл» — т.к. MessageConverter consumer'а
   (Jackson по умолчанию в `rabbitTemplate.receive`) тоже ломался и
   логи не показывали body.

**Fix (минимальный набор изменений):**
- `RabbitConfig.java` — убран `@ConditionalOnBean(ConnectionFactory.class)`.
  Теперь конфигурация активна всегда, Rabbit/Connection `@Bean`'ы
  создаются с нашим Jackson2JsonMessageConverter.
- `DomainEventListener` — убран `@Component` + `@ConditionalOnBean`,
  регистрируется как `@Bean` в `RabbitConfig` (гарантия: получает тот
  же `RabbitTemplate`, что и `@Bean rabbitTemplate`, а не autoconfig'овый).
- `catch(AmqpException)` → `catch(Exception)` — MessageConversionException
  / IllegalStateException из Jackson пробрасывались и ломали
  `/auth/otp/request`. Fire-and-forget требует ловить всё.
- `application-test.yml` — убран `spring.autoconfigure.exclude:
  RabbitAutoConfiguration`. Теперь все IT поднимают Rabbit autoconfig;
  те, что реально не используют Rabbit, просто не делают `convertAndSend`
  и `CachingConnectionFactory` остаётся idle. `management.health.rabbit
  .enabled=false` оставлен, чтобы `/actuator/health` не ждал живое соединение.
- `application-test.yml` — добавлен дефолтный `spring.rabbitmq.*` (localhost:5672/guest/guest),
  чтобы autoconfig не падал на старте без env-переменных.

**Проверка:** `./gradlew :services:auth-service:test
:services:auth-service:integrationTest` — BUILD SUCCESSFUL, 84/84
зелёные (unit + IT включая AuthOtpFlowIT зелёным за 9.5s).

### G2.6 (старый текст — исторический debug) — почему не работало

**Файл:** `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java`

**Симптомы:**
1. `response.getStatusCode() == NO_CONTENT` → ✅ проходит
2. `redisTemplate.get("otp:123456789")` → 6-digit code, ✅ проходит
3. `rabbitTemplate.receive(TEST_QUEUE, 500)` по 16 итерациям (8s total) → null
4. В логах нет «Published event» от `DomainEventListener` — похоже listener не триггерится

**Окружение теста:**
- `@TestPropertySource(properties = { "spring.autoconfigure.exclude=" })` — override'им exclude RabbitAutoConfiguration из application-test.yml (там он отключён).
- PostgreSQL + Redis + RabbitMQ testcontainers локально.
- Test queue уникальное имя `auth-otp-flow-it.<nanoTime>`, durable=false, exclusive=false, **autoDelete=false** (после first try с autoDelete=true был 404).

**Гипотезы (проверить в новой сессии по приоритету):**

1. **`@ConditionalOnBean(RabbitTemplate.class)` порядок** — `DomainEventListener` помечен `@ConditionalOnBean(RabbitTemplate.class)`. RabbitTemplate создаётся как `@Bean` в `RabbitConfig` (тоже `@ConditionalOnBean(ConnectionFactory.class)`). Spring может оценить условие `DomainEventListener` **до** того как RabbitTemplate зарегистрирован, и listener пропустит. **Проверка:** временно заменить `@ConditionalOnBean(RabbitTemplate.class)` → `@ConditionalOnProperty(prefix="spring.rabbitmq", name="host")`. Или дебаг `AuditBeanRegistry` через `ConditionEvaluationReport`.

2. **Fanout exchange не declared до publish.** Auth `RabbitConfig.@Bean authEventsExchange()` → `FanoutExchange("rut-uit.events", durable=true, autoDelete=false)`. Amqp-admin auto-declare при startup. Но если auto-declare отложен и публикация letsит раньше — msg теряется. **Проверка:** в @BeforeEach перед `amqpAdmin.declareExchange` сделать `amqpAdmin.getQueueProperties("rut-uit.events")` — посмотреть что он видит, или явно `rabbitTemplate.setExchange(...)` перед convertAndSend.

3. **Jackson сериализация падает молча в `DomainEventListener.onDomainEvent`.** Catch(AmqpException) логирует `.warn`, но не все исключения AmqpException — NPE/JsonMappingException вылетят как unchecked. **Проверка:** ловить `Exception` в тесте запускаем через manual logging (set log level `org.springframework.amqp` = DEBUG).

4. **`@EventListener` type mismatch.** `DomainEventListener.onDomainEvent(DomainEvent event)` — `import ru.rutcampustrack.auth.event.DomainEvent` (локальный alias). `OtpRequestedEvent extends auth.event.DomainEvent`. Должен match'иться. Но Spring использует `ResolvableType` — если generic wildcard`ы вмешались — пропустит. **Проверка:** в `DomainEventListener` поменять параметр на `shared.events.DomainEvent` — `auth.event.DomainEvent extends shared.events.DomainEvent`.

5. **RabbitTemplate routing key.** Мы шлём через `convertAndSend(EXCHANGE, "", event)` — пустой routing key. Для fanout routing key игнорируется. Но если в test где-то выставляется alternative exchange... (маловероятно, но проверить.

**Альтернатива:** вместо receive-polling использовать `SimpleMessageListenerContainer` с `@RabbitListener` или `MessageListener` внутри теста — это настоящий consumer, а не short-lived basic.get polling. Возможно помогает.

### G2.7 осталось
- `docs/architecture/architecture.md` раздел «OTP flow» (старая диаграмма HTTP body → новая event-driven). Есть ли там существующая OTP-диаграмма — проверить grep "OTP|otp".
- Финальный коммит на группу: `feat(auth): OTP через RabbitMQ event (01 P0-4, 08 P0-2)` — можно сделать отдельным docs(m09) закрывающим.

### Открытые вопросы (решены в Группе 2)
- ✅ **Q1 OTP failure handling** — выбран вариант C (retry перезаписывает Redis-код + публикует новое событие). См. docstring `OtpRequestedEvent`.
- ✅ **Q2 `.env.prod` TELEGRAM_BOT_USERNAME** — не требуется в G2 (bot получает telegram_id из event.payload, chat_id == telegram_id). Переменная нужна только для landing (уже hardcoded в G1).

## 2026-04-23 — Группа 1 закрыта

- **G1.1 (01 P0-5):** `OtpService.verifyOtp` переведён на
  `MessageDigest.isEqual` + unit `OtpServiceTest` (4 теста:
  correct/wrong/null code + structural guard против String.equals регрессии).
- **G1.2 (04 P0-6):** `cleanupOrphans` + gRPC-вызов `getLessonsByIds`
  удалены из `AttendanceIndexInitializer`. Bean больше не зависит от
  `ScheduleGrpcClient` (сам клиент остаётся — используется 17 другими
  файлами). IT `StartupOrphanCleanupRemovedIT` — regression guard:
  (a) `verifyNoInteractions(scheduleGrpcClient)` при старте,
  (b) re-run runner'а с orphan-doc не удаляет его.
- **G1.3 (12 P0-2):** 4 CTA на landing с `/login` → deep-link
  `https://t.me/ruttrack_bot/ruttrack` (см. DECISIONS D3 —
  hardcode вместо build-pipeline для статического HTML).
- **G1 CHECKLIST пункт `.env.prod.example TELEGRAM_BOT_USERNAME`**
  перенесён в Группу 7 (prod-deploy-checklist + env-шаблоны) —
  в M09 G1 переменная не нужна, deep-link hardcoded.
- **Smoke-check лендинга:** dev-окружение landing сейчас не
  поднимается локально; visual smoke (клик → Telegram) — при
  deploy на staging в Группе 7.
