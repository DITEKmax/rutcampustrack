# M3 Checklist

Порядок важен — каждая группа строится на предыдущей.

## Группа 1 — Gradle scaffolding

- [ ] Создать директорию `services/shared/` с пустым `.gitkeep`
- [ ] Создать `services/shared/shared-web/build.gradle.kts` (java-library, deps: spring-web, spring-context, jakarta.validation-api, jackson-databind, hibernate-validator)
- [ ] Создать `services/shared/shared-events/build.gradle.kts` (java-library, deps: jackson-databind, slf4j-api)
- [ ] Создать `services/shared/shared-logback/build.gradle.kts` (java-library, deps: logback-classic, logstash-logback-encoder)
- [ ] Создать `services/shared/shared-test-containers/build.gradle.kts` (java-library, deps: testcontainers BOM + postgres/mongodb/redis/rabbitmq/junit-jupiter)
- [ ] Добавить 4 модуля в `settings.gradle.kts`
- [ ] `./gradlew :services:shared:shared-web:build` зелёный (пустой модуль)

## Группа 2 — shared-web (core)

- [ ] `ErrorResponse` record (type, title, status, detail, instance, timestamp, traceId, invalidParams nullable)
- [ ] `InvalidParam` record (name, reason)
- [ ] `ErrorResponse.badRequest(...)`, `.notFound(...)`, `.internal(...)` factory methods
- [ ] `GlobalExceptionHandler` — 9 handler'ов: MethodArgumentNotValidException, ConstraintViolationException, HttpMessageNotReadableException, HttpMediaTypeNotSupportedException, MissingServletRequestParameterException, MethodArgumentTypeMismatchException, HttpRequestMethodNotSupportedException, NoHandlerFoundException, AccessDeniedException
- [ ] `GlobalExceptionHandler.handleGeneral(Exception)` — catch-all с `correlation=<traceId>` (MDC) + log.error
- [ ] Unit-тесты handler'ов (mock HttpServletRequest, ServletWebRequest → expected ErrorResponse)

## Группа 3 — shared-web (validation)

- [ ] `@StartBeforeEnd(start, end)` + `StartBeforeEndValidator` (BeanWrapper)
- [ ] `@DateRangeValid(from, to)` + `DateRangeValidator`
- [ ] `@ValidFile(maxSizeBytes, allowedMediaTypes)` + `ValidFileValidator` (MultipartFile)
- [ ] Параметризованные unit-тесты (valid/invalid cases)

## Группа 4 — shared-web (config beans)

- [ ] `JacksonConfig` — `Jackson2ObjectMapperBuilderCustomizer` с `READ_UNKNOWN_ENUM_VALUES_AS_NULL`, `FAIL_ON_UNKNOWN_PROPERTIES=false`, `WRITE_DATES_AS_TIMESTAMPS=false`
- [ ] `OpenApiCustomizer` bean-заглушка (реальная логика в M6)
- [ ] `@AdminAction` marker annotation + aspect-заглушка (реальный handler в M4)

## Группа 5 — shared-events

- [ ] Abstract record / class `DomainEvent` (event_version int, trace_id String, occurred_at Instant, source String)
- [ ] `EventVersion` annotation + default `=1`
- [ ] `AbstractEventPublisher` — auto-fill MDC → фабрика для Producer'ов (без привязки к AMQP, чистое API)
- [ ] `AbstractEventConsumer` — extract trace_id из payload → MDC.put перед handler
- [ ] Unit-тесты: publisher заполняет trace_id, consumer ставит в MDC

## Группа 6 — shared-logback

- [ ] `logback-base.xml` (ConsoleAppender + LogstashEncoder, JSON format)
- [ ] `MaskingProvider.java` extends CompositeJsonProvider — regex masking
- [ ] Regex patterns (class constant): BEARER_TOKEN, TELEGRAM_ID, FCM_ENDPOINT
- [ ] Unit-тесты: `log.info("token: {}", "eyJabc...")` → в capture'нутом output `token: ***`
- [ ] `README.md` модуля — как подключить (одна `<include>` строчка в сервисном `logback-spring.xml`)

## Группа 7 — shared-test-containers

- [ ] `ContainerTestBase` abstract class с `@Testcontainers` + 4 containers (reuse=true)
- [ ] `@DynamicPropertySource` provider
- [ ] `GrpcInProcessFixture` helper
- [ ] `WireMockFixture` helper
- [ ] `MigrationTestUtils.runMigrationsUpTo(version)` static helper
- [ ] `~/.testcontainers.properties` инструкция в `README.md` модуля (reuse.enable=true для dev)

## Группа 8 — notification-service миграция (acceptance)

- [ ] `notification-service/build.gradle.kts` → добавить зависимости shared-web + shared-logback + shared-events (implementation)
- [ ] Удалить не-existent (или существующий minimal) локальный error-handling в notification-service
- [ ] Компонент-скан: убедиться что `@RestControllerAdvice` из shared-web подхватывается (scan `ru.rutcampustrack.shared.web`)
- [ ] `notification-service/src/main/resources/logback-spring.xml` → `<include resource="shared/logback-base.xml"/>`
- [ ] Integration-тест `NotificationErrorHandlingIT`: POST с невалидным body → 400 `application/problem+json` + `invalid-params[]`
- [ ] Integration-тест `NotificationLoggingIT`: логирование JSON + masking Bearer token
- [ ] `NotificationIntegrationIT` extends `ContainerTestBase` (миграция с @MockitoBean на real Testcontainers Mongo + Rabbit)
- [ ] Smoke-тест: `docker compose up notification-web` локально, `curl -i` с невалидным body → проверить headers/body вручную

## Группа 9 — документация

- [ ] `docs/architecture.md` → раздел «Shared modules» (4 модуля × 3-5 строк)
- [ ] `docs/shared-modules-usage.md` — 1-страничный quick-start для разработчика
- [ ] `CHANGELOG.md` → `## [Unreleased]` → `### Added` запись
- [ ] `CLAUDE.md` → упомянуть shared-модули в разделе «Структура репозитория»

## Группа 10 — финал

- [ ] Прогнать все acceptance criteria из PLAN.md
- [ ] `./gradlew build` зелёный полностью (все 5 сервисов + 4 shared)
- [ ] Optional: `bug-hunter` subagent на diff milestone'а (один вызов)
- [ ] Финальный коммит `feat(shared): shared-web + shared-events + shared-logback + shared-test-containers (M3)`
- [ ] Post-mortem в PLAN.md (измерения, surprises, TODO для других milestones)
- [ ] Отметить M3 как ✅ готов в `docs/milestones/README.md`

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
