# M01 Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями (пример: «Jackson 2.x или JsonB?»).
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода («почему shared-web
  не через Spring Boot starter»).

---

## 2026-04-19 — Initial scaffold

_(Будет заполняться по ходу milestone'а.)_

## 2026-04-19 — Gradle Version Catalog вместо inline-версий

**Выбрано:** `gradle/libs.versions.toml` с минимальным набором ключей (springBoot, logstash, testcontainers-bom, grpc, wiremock, springdoc).
**Отвергнуто:** (a) inline-версии в каждом build.gradle.kts, (b) `extra` properties в корневом build.
**Причина:** M06 включает Renovate — catalog даёт единое место правки; в существующих сервисах уже drift по 8+ версиям, catalog остановит рост drift'а без насильной миграции (сервисы мигрируют постепенно вне scope M01).
**Последствия:** новые модули/правки версий идут через catalog; существующие сервисы не трогаем в M01.

## 2026-04-19 — shared-test-containers через java-test-fixtures

**Выбрано:** плагин `java-test-fixtures`, потребители подключают через `testImplementation(testFixtures(project(...)))`.
**Отвергнуто:** (b) обычный `java-library` + `testImplementation(project(...))` — проще синтаксис, но модуль-fixture может случайно попасть в main classpath сервиса.
**Причина:** семантическая корректность + защита от ошибки подключения. PLAN.md изначально имел это в виду («testFixtures scope»).
**Последствия:** сервисные тесты используют двойной wrapper `testFixtures(project(...))`; вариант-b случая «притащил Testcontainers в прод-jar» исключён.

## 2026-04-19 — shared-web: compileOnly для spring/jackson/slf4j

**Выбрано:** spring-web, spring-webmvc, spring-context, jakarta.validation-api, jackson-databind, jsr310, jackson-annotations, spring-security-core, jakarta.servlet-api, slf4j-api — все `compileOnly`. Сервис провайдит через свой Spring Boot starter.
**Отвергнуто:** (a) `api` — удобно, но дублирует spring-webmvc между модулем и starter сервиса, риск конфликта версий; (b) `implementation` — тот же минус без удобства.
**Причина:** буквальная трактовка NEW-34 («никакой магии, подключение как обычная библиотека»). Все 5 сервисов-потребителей уже имеют spring-boot-starter-web или transitively-webmvc.
**Последствия:** сервис БЕЗ spring-boot-starter-web не сможет использовать shared-web (ожидаемо — он там просто не нужен).

## 2026-04-19 — ErrorResponse: 9 полей, канонически от academic + traceId

**Выбрано:** shared-web `ErrorResponse` = record с 9 полями: `status, type, title, detail, instance, timestamp, traceId, invalidParams[], field, extras`. Поле validation-списка переименовано `fieldErrors` → `invalidParams[]` (RFC 9457). Добавлен top-level `traceId` (P2-3/1).
**Отвергнуто:** (A) буквально PLAN.md (без field/extras) — потеряли бы BUG-006-2 field-тэгирование и каскадный extras; (C) оставить `fieldErrors` — non-standard имя; (D) драфтовать постепенно — долго живущий drift между API.
**Причина:** максимальная RFC 9457 совместимость + сохранение всех существующих фич academic. Миграция фронтов = один rename `fieldErrors`→`invalidParams` (web-panel + pwa).
**Последствия:** при миграции сервисов (M04+) фронты получат breaking rename. В M01 — only notification-service использует shared ErrorResponse сразу.

## 2026-04-19 — InvalidParam: 3 поля вместо 2

**Выбрано:** `InvalidParam(name, reason, rejectedValue)` — 3 поля. `rejectedValue` опционально (`@JsonInclude(NON_NULL)`).
**Отвергнуто:** PLAN.md буквально — `(name, reason)` только 2 поля.
**Причина:** zero-cost расширение, сохраняет текущий FieldError.rejectedValue — полезно для debug-логов и подсветки значения на фронте.
**Последствия:** PLAN.md отклонён на 1 поле — зафиксировано в NOTES.md.

## 2026-04-19 — GlobalExceptionHandler: только 9 стандартных + catch-all

**Выбрано:** shared-web `GlobalExceptionHandler` содержит ТОЛЬКО 9 стандартных Spring MVC handlers (MethodArgumentNotValidException, ConstraintViolationException, HttpMessageNotReadableException, HttpMediaTypeNotSupportedException, MissingServletRequestParameterException, MethodArgumentTypeMismatchException, HttpRequestMethodNotSupportedException, NoHandlerFoundException, AccessDeniedException) + `handleGeneral(Exception)`.
**Отвергнуто:** включать доменные handler'ы (ResourceNotFoundException, ConflictException, DataIntegrityViolationException, …) — они специфичны для academic.
**Причина:** NEW-34 «shared-web — без сервис-специфики». Доменные handler'ы остаются в `@RestControllerAdvice` каждого сервиса и просто переиспользуют shared `ErrorResponse` record.
**Последствия:** сервис, подключающий shared-web, получает 10 handlers «бесплатно». Свои доменные — дополняет локально. Academic продолжает иметь свой `@RestControllerAdvice(order=0)` выше по приоритету.

## 2026-04-19 — Config beans через @Component + component scan (не autoconfig)

**Выбрано:** `JacksonConfig`, `OpenApiCustomizer`, `AdminActionAspect` — обычные `@Component` / `@Configuration` классы в пакете `ru.rutcampustrack.shared.web.*`. Сервис подключает через component scan (`@ComponentScan` на `ru.rutcampustrack.shared.web` или `scanBasePackages` в `@SpringBootApplication`).
**Отвергнуто:** (a) Spring Boot AutoConfiguration через `META-INF/spring.factories` / `AutoConfiguration.imports` — запрещено NEW-34. (b) `static` factory methods — требует явного вызова в каждом сервисе (больше трения).
**Причина:** NEW-34 «без autoconfiguration» означает отсутствие `spring.factories` / auto-imports, а НЕ отсутствие `@Component`. Component scan — стандартный Spring-паттерн для библиотек, `GlobalExceptionHandler` (Группа 2) уже работает по тому же паттерну.
**Последствия:** сервис-потребитель должен явно указать shared-web пакет в component scan. Проверка в acceptance-тесте (Группа 8).

---

_Формат записи:_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
