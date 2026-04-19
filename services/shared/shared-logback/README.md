# shared-logback

Общий Logback-конфиг + маскирование секретов в логах (NEW-68, QA7).

## Что даёт

- JSON-вывод в `stdout` через `LoggingEventCompositeJsonEncoder`.
- Унифицированные поля: `ts`, `level`, `logger`, `thread`, `msg`, `service`, `traceId`, `userId`, `eventType`, `stack`.
- Regex-маскирование **в поле `msg`** (не в stack trace!):
  - `Bearer <jwt>` → `Bearer ***` (только JWT, ловит `Bearer ey...`)
  - `"telegram_id": 123` / `"telegramId": 123` → `"telegram_id": "***"` (имя нормализуется в `telegram_id`)
  - `https://fcm.googleapis.com/...` → `https://fcm.googleapis.com/***`

## Known limitations (M01)

1. **Stack traces НЕ маскируются.** Маскирование работает только на поле
   `msg` (форматированный message). Если вы делаете
   `log.error("...", exception)`, то `exception.getMessage()` и вся цепочка
   `cause` попадают в лог через `StackTraceJsonProvider` **без маскирования**.
   **Никогда не кладите секреты в exception messages** (SQL-ошибки,
   HttpClient echo). Расширение masking на stack traces — backlog M04.
2. **Bearer regex ловит только JWT** (`Bearer ey...`). Opaque OAuth2 токены
   и dev-токены типа `Bearer test-token` не маскируются. Если сервис
   использует не-JWT bearer — расширить regex локально.
3. **telegram_id regex переписывает `telegramId` → `telegram_id`** — имя
   поля нормализуется. Backlog M04: использовать capture group для сохранения
   оригинального имени.

## Как подключить в сервисе

1. Добавить зависимость в `build.gradle.kts` сервиса:
   ```kotlin
   implementation(project(":services:shared:shared-logback"))
   ```
2. В `src/main/resources/logback-spring.xml` сервиса:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <configuration>
       <property name="SERVICE_NAME" value="academic-service"/>
       <include resource="shared/logback-base.xml"/>
   </configuration>
   ```
3. Поднять `SERVICE_NAME` до уровня property (или задать через env `-Dservice.name=...`) — попадёт в поле `service` каждого JSON-лога.

## Почему не RxJava/Micrometer tracing здесь

`traceId` кладётся в MDC отдельным слоем (в M02 — micrometer observation propagation). Шаблон просто читает MDC-ключ `traceId`.

## Тесты

- `MaskingJsonProviderTest` — unit-тесты regex'ов (параметризованные).
- `LogbackBaseIntegrationTest` — проверка что JSON-вывод правильной формы и что маскирование работает в реальном Logback pipeline (через `test/resources/logback-test.xml`).
