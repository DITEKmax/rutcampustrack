# M01 Checklist

Порядок важен — каждая группа строится на предыдущей.

## Группа 1 — Gradle scaffolding

- [~] Создать директорию `services/shared/` с пустым `.gitkeep` — **N/A**, директория наполнена 4 модулями сразу (см. NOTES.md)
- [x] Создать `services/shared/shared-web/build.gradle.kts` (compileOnly для spring/jackson/slf4j — NEW-34; hibernate-validator + aspectjweaver implementation)
- [x] Создать `services/shared/shared-events/build.gradle.kts` (compileOnly для jackson/slf4j)
- [x] Создать `services/shared/shared-logback/build.gradle.kts` (compileOnly logback/slf4j, implementation logstash-encoder через catalog)
- [x] Создать `services/shared/shared-test-containers/build.gradle.kts` (`java-test-fixtures` plugin, testFixturesApi; testcontainers-bom + grpc + wiremock через catalog)
- [x] Создать `gradle/libs.versions.toml` (version catalog — см. DECISIONS.md)
- [x] Добавить 4 модуля в `settings.gradle.kts`
- [x] `./gradlew :services:shared:shared-web:test` зелёный (12 тестов, 34s первый прогон)

## Группа 2 — shared-web (core)

- [x] `ErrorResponse` record (status, type, title, detail, instance, timestamp, traceId, invalidParams, field, extras) — 9 полей, см. DECISIONS.md (Вариант B)
- [x] `InvalidParam` record (name, reason, rejectedValue) — 3 поля, см. DECISIONS.md
- [x] `ErrorResponse.badRequest(...)`, `.notFound(...)`, `.internal(...)` factory methods
- [x] `GlobalExceptionHandler` — 9 handler'ов: MethodArgumentNotValidException, ConstraintViolationException, HttpMessageNotReadableException, HttpMediaTypeNotSupportedException, MissingServletRequestParameterException, MethodArgumentTypeMismatchException, HttpRequestMethodNotSupportedException, NoHandlerFoundException, AccessDeniedException
- [x] `GlobalExceptionHandler.handleGeneral(Exception)` — catch-all с `correlation=<traceId>` (MDC) + log.error, не утечка message в response
- [x] Unit-тесты handler'ов (12 кейсов, 0 failures)

## Группа 3 — shared-web (validation)

- [x] `@StartBeforeEnd(start, end)` + `StartBeforeEndValidator` (BeanWrapper, LocalDate/LocalDateTime/Instant/OffsetDateTime/ZonedDateTime)
- [x] `@DateRangeValid(from, to)` + `DateRangeValidator` (inclusive: `from == to` валидно)
- [x] `@ValidFile(maxSizeBytes=10MiB default, allowedMediaTypes)` + `ValidFileValidator` (MultipartFile, custom message per reason)
- [x] Параметризованные unit-тесты (19 кейсов по 3 валидаторам, 0 failures)

## Группа 4 — shared-web (config beans)

- [x] `JacksonConfig` — `Jackson2ObjectMapperBuilderCustomizer` (READ_UNKNOWN_ENUM_VALUES_AS_NULL, FAIL_ON_UNKNOWN_PROPERTIES=false, WRITE_DATES_AS_TIMESTAMPS=false), 5 тестов
- [x] `SharedOpenApiCustomizer` bean-заглушка с `@ConditionalOnClass(OpenApiCustomizer.class)` (реальная логика в M06), 1 тест
- [x] `@AdminAction` marker annotation + `AdminActionAspect` заглушка (proxy verified через AopUtils, реальный handler в M04), 3 теста
- [x] Итого shared-web: 40 tests, 0 failures

## Группа 5 — shared-events

- [x] `DomainEvent` abstract class (event_version, trace_id, occurred_at, source) с `@JsonProperty` snake_case
- [x] `@EventVersion` annotation (value default 1) — read via reflection включая inherited
- [x] `AbstractEventPublisher.fillDefaults()` — auto-fill MDC/reflection/clock, не перезаписывает explicit
- [x] `AbstractEventConsumer.withTraceContext()` — MDC put/restore с previous value, cleanup при исключении
- [x] Unit-тесты (13 тестов: JSON round-trip + publisher + consumer + MDC lifecycle, 0 failures)

## Группа 6 — shared-logback

- [x] `shared/logback-base.xml` (ConsoleAppender + LoggingEventCompositeJsonEncoder, JSON format, поля ts/level/logger/thread/msg/service/MDC[traceId,userId,eventType]/stack)
- [x] `MaskingJsonProvider` extends `MessageJsonProvider` — regex masking поля msg
- [x] Regex patterns: `BEARER_TOKEN` (JWT после `Bearer`), `TELEGRAM_ID` (json field telegram_id/telegramId numeric or quoted), `FCM_ENDPOINT` (googleapis URL)
- [x] Unit-тесты: 14 параметризованных + 4 integration через programmatic Logback pipeline (0 failures)
- [x] `README.md` модуля — как подключить через `<include resource="shared/logback-base.xml"/>`

## Группа 7 — shared-test-containers

- [x] `ContainerTestBase` abstract class с `@Testcontainers` + 4 containers (Postgres 16 / Mongo 7 / Redis 7 / RabbitMQ 3.13, `reuse=true`)
- [x] `@DynamicPropertySource` — `spring.datasource.*` + `spring.data.mongodb.uri` + `spring.data.redis.*` + `spring.rabbitmq.*`
- [x] `GrpcInProcessFixture` — in-process gRPC round-trip с уникальным channel name
- [x] `WireMockFixture` — динамический порт, AutoCloseable
- [x] `MigrationTestUtils` — `runMigrationsUpTo(version)`, `runAllMigrations`, `clean`
- [x] `~/.testcontainers.properties` инструкция в `README.md` + подключение через `testImplementation(testFixtures(project(...)))`
- [x] Smoke-тесты (3 passing + 1 disabled-Docker, 0 failures); реальный integration в Группе 8

## Группа 8 — notification-service миграция (acceptance)

- [x] `notification-service/build.gradle.kts` → shared-web + shared-events + shared-logback (implementation) + testImplementation(testFixtures(shared-test-containers)) + spring-security-core (для AccessDeniedException в classpath)
- [~] Удалить локальный error-handling в notification-service — **N/A**: в notification-service `@RestControllerAdvice` не было, только `AccessDeniedException` доменный класс (сохранён, локальный `NotificationExceptionHandler` маппит его в 403 problem+json)
- [x] Компонент-скан: `@SpringBootApplication(scanBasePackages={"ru.rutcampustrack.notification", "ru.rutcampustrack.shared.web"})` — GlobalExceptionHandler + JacksonConfig + AdminActionAspect + SharedOpenApiCustomizer подхватываются
- [x] `notification-app/src/main/resources/logback-spring.xml` → `<include resource="shared/logback-base.xml"/>` + `SERVICE_NAME=notification-web`
- [x] `NotificationErrorHandlingIT` (extends ContainerTestBase, @SpringBootTest+MockMvc): 4 кейса (validation → invalidParams, malformed JSON, method-not-allowed, AccessDenied → 403) все зелёные
- [x] `NotificationLoggingIT` (programmatic Logback capture): JSON структура + masking Bearer в реальном pipeline, 2 теста зелёных
- [~] `NotificationIntegrationIT` миграция с @MockitoBean — **N/A**: такого теста до M01 не существовало. Вместо миграции — NotificationErrorHandlingIT уже extends ContainerTestBase (real Mongo+Rabbit)
- [~] Smoke-тест `docker compose up notification-web` + curl — **отложено**: NotificationErrorHandlingIT (real containers + HTTP через MockMvc) даёт ту же уверенность

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
