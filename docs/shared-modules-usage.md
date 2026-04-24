# Shared modules — quick start для разработчика

1-страничный гайд: как подключить shared-модули M01 к новому или существующему Java-сервису.

## TL;DR

```kotlin
// build.gradle.kts сервиса
dependencies {
    // M11 G0: shared-web — Spring Boot starter с @AutoConfiguration
    // (catch-all Spring MVC handler, JacksonConfig, SharedOpenApiCustomizer,
    // AdminActionAspect). Транзитивно приносит shared-web-api (ErrorResponse,
    // FieldError, InvalidParam) + spring-security-core (для AccessDenied handler).
    implementation(project(":services:shared:shared-web"))
    implementation(project(":services:shared:shared-events"))
    implementation(project(":services:shared:shared-logback"))
    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
}
```

```java
// Application.java — M11 G0.8: scanBasePackages НЕ нужен (раньше был
// hack для подхвата shared-web beans). Теперь через
// META-INF/spring/AutoConfiguration.imports (Spring Boot 3 idiom).
@SpringBootApplication
public class MyServiceApplication { ... }
```

### API-contract модули

Если у сервиса есть `*-api-contract` модуль (academic/schedule/
attendance/notification), добавьте зависимость на shared-web-api —
тогда controller-интерфейсы получат `ErrorResponse` для
`@ApiResponse(... schema = @Schema(implementation = ErrorResponse.class))`:

```kotlin
// *-api-contract/build.gradle.kts
dependencies {
    api(project(":services:shared:shared-web-api"))
    // ... jakarta.validation-api, spring-web, swagger-annotations, ...
}
```

### Domain exceptions поверх shared handler

Свой `@RestControllerAdvice` с `@Order(Ordered.HIGHEST_PRECEDENCE)`
обрабатывает domain-specific exceptions. Shared handler с
`@Order(Ordered.LOWEST_PRECEDENCE)` остаётся catch-all (Spring MVC
validation / noHandler / accessDenied / generic):

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    @ExceptionHandler(MyDomainException.class)
    public ResponseEntity<ErrorResponse> handleMyDomain(...) { ... }
}
```

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <property name="SERVICE_NAME" value="my-service"/>
    <include resource="shared/logback-base.xml"/>
</configuration>
```

## Что получаете

### 1. RFC 9457 error handling (shared-web)

Любое стандартное Spring MVC исключение — `MethodArgumentNotValidException`,
`HttpMessageNotReadableException`, `AccessDeniedException` и др. — автоматически
превращается в `application/problem+json`:

```json
{
  "status": 400,
  "type": "https://api.rutcampustrack.ru/problems/validation-failed",
  "title": "Ошибка валидации",
  "detail": "Одно или несколько полей не прошли проверку",
  "instance": "/api/users",
  "timestamp": "2026-04-19T10:00:00Z",
  "traceId": "abc-123",
  "invalidParams": [
    {"name": "email", "reason": "must be a valid email", "rejectedValue": "not-an-email"}
  ]
}
```

Доменные исключения (`ResourceNotFoundException`, `ConflictException` и т.п.)
маппятся собственным `@RestControllerAdvice(order=HIGHEST_PRECEDENCE)` сервиса —
используют тот же `ru.rutcampustrack.shared.web.exception.ErrorResponse`.

### 2. Cross-field validation (shared-web)

```java
@StartBeforeEnd(start = "startsAt", end = "endsAt")
public record SemesterDto(LocalDate startsAt, LocalDate endsAt) { }

@DateRangeValid(from = "from", to = "to")  // inclusive: from == to валидно
public record ReportFilter(LocalDate from, LocalDate to) { }

public record UploadRequest(
    @ValidFile(maxSizeBytes = 5 * 1024 * 1024, allowedMediaTypes = {"image/png","image/jpeg"})
    MultipartFile file
) { }
```

### 3. Унифицированный Jackson (shared-web)

`JacksonConfig` автоматически применяется через component scan:
`READ_UNKNOWN_ENUM_VALUES_AS_NULL` + `FAIL_ON_UNKNOWN_PROPERTIES=false` +
`WRITE_DATES_AS_TIMESTAMPS=false`.

### 4. Doman events (shared-events)

```java
@EventVersion(2)
public class LessonStartedEvent extends DomainEvent {
    private Long lessonId;
    // ... getters/setters
}

// В publisher-адаптере сервиса (подключение к AMQP будет в M02):
public class LessonPublisher extends AbstractEventPublisher {
    public LessonPublisher() { super("schedule-service"); }

    public void publish(LessonStartedEvent event) {
        fillDefaults(event);  // event_version=2, trace_id из MDC, occurred_at, source
        // отправить в Rabbit...
    }
}

// В consumer-адаптере:
public class LessonConsumer extends AbstractEventConsumer {
    public void onMessage(LessonStartedEvent event) {
        withTraceContext(event, e -> {
            // handler выполняется с traceId события в MDC
        });
    }
}
```

### 5. JSON-логирование с маскированием (shared-logback)

После `<include resource="shared/logback-base.xml"/>`:

```java
log.info("Authorization: Bearer eyJ...");           // → "msg":"Authorization: Bearer ***"
log.info("request {\"telegram_id\":12345}");         // → "msg":"request {\"telegram_id\":\"***\"}"
log.info("push to https://fcm.googleapis.com/...");  // → "msg":"push to https://fcm.googleapis.com/***"
```

Поля JSON: `ts`, `level`, `logger`, `thread`, `msg`, `service`, MDC `traceId`/`userId`/`eventType`, `stack`.

### 6. Testcontainers fixtures (shared-test-containers)

```java
@SpringBootTest
class MyServiceIT extends ContainerTestBase {
    // Postgres + Mongo + Redis + RabbitMQ уже подняты с reuse=true.
    // spring.datasource.* / spring.data.mongodb.uri / spring.data.redis.* /
    // spring.rabbitmq.* проброшены в контекст.

    @Test void smoke() { /* ... */ }
}

// gRPC in-process:
try (GrpcInProcessFixture fx = new GrpcInProcessFixture()) {
    fx.startServer(new MyServiceImpl());
    var stub = MyServiceGrpc.newBlockingStub(fx.channel());
}

// WireMock:
try (WireMockFixture wm = new WireMockFixture()) {
    wm.start();
    wm.server().stubFor(get(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));
    var externalApiUrl = wm.baseUrl();
}
```

Для `reuse=true` на dev-машине нужен `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

## Частые ошибки

1. **`ClassNotFoundException: AccessDeniedException`** — забыли
   `implementation("org.springframework.security:spring-security-core")`.
2. **`@ExceptionHandler` доменный не срабатывает** — shared handler имеет
   `@Order(LOWEST_PRECEDENCE)`, доменный должен быть `HIGHEST_PRECEDENCE`
   или default.
3. **`GlobalExceptionHandler` не подхватывается** — не расширили
   `scanBasePackages` до `ru.rutcampustrack.shared.web`.
4. **Поля в JSON-логах идут camelCase вместо snake_case** — это событие,
   а не лог. В JSON-логах поля камелкейс (ts, level, logger, service — короткие).
   В JSON-событиях `DomainEvent` — snake_case через `@JsonProperty`.

## Что в М01 *не* вошло

- Spring Boot AutoConfiguration — осознанно нет (NEW-34). Подключать руками.
- Интеграция с AMQP — `AbstractEventPublisher/Consumer` не знают про Rabbit.
  Адаптеры в сервисах + outbox в M02.
- Реальная audit-запись `@AdminAction` — aspect пишет только DEBUG.
  Handler в M04.
- Обогащение OpenAPI-спеки — `SharedOpenApiCustomizer` no-op заглушка. M06.

## Первая миграция (M01 acceptance)

`notification-web` — см. `services/notification-service/notification-app/`
(scanBasePackages, logback-spring.xml с include, `NotificationErrorHandlingIT`
extends `ContainerTestBase`).
