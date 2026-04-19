# shared-logback

Общий Logback-конфиг + маскирование секретов в логах (NEW-68, QA7).

## Что даёт

- JSON-вывод в `stdout` через `LoggingEventCompositeJsonEncoder`.
- Унифицированные поля: `ts`, `level`, `logger`, `thread`, `msg`, `service`, `traceId`, `userId`, `eventType`, `stack`.
- Regex-маскирование в поле `msg`:
  - `Bearer <jwt>` → `Bearer ***`
  - `"telegram_id": 123` / `"telegramId": 123` → `"telegram_id": "***"`
  - `https://fcm.googleapis.com/...` → `https://fcm.googleapis.com/***`

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
