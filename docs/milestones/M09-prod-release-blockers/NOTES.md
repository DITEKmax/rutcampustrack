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
  прода. Документируется в `docs/future-ideas.md`.
- **NEW-54 CSP-Report endpoint** — `report-uri` в CSP web-panel
  (owner явно указал «v0.1»).
- **Magic-link для первого входа** (01-Q1 accepted tradeoff).

## 2026-04-23 — Группа 2 WIP (5/7 закрыто, G2.6 debug в следующей сессии)

### Что сделано (коммиты)
- `3d6dfd1` feat(events): схема + OtpRequestedEvent класс
- `807b1f2` feat(auth): 204 No Content, OtpService void, OpenAPI + frontend types
- `b851221` feat(bot): otp_requested consumer + /login рефактор + tracker новые методы
- `70bd2db` test(auth): OtpRequestedContractTest (3 теста зелёные)
- `d4ca2ca` wip(auth): AuthOtpFlowIT (помечен @Disabled, причина ниже)

### G2.6 AuthOtpFlowIT — почему не работает

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
- `docs/architecture.md` раздел «OTP flow» (старая диаграмма HTTP body → новая event-driven). Есть ли там существующая OTP-диаграмма — проверить grep "OTP|otp".
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
