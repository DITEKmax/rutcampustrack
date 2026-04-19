# M01 Notes

## 2026-04-19

- Старт milestone'а. PLAN + CHECKLIST написаны. Коммит initial.
- Группа 1 (Gradle scaffolding) завершена:
  - Создан `gradle/libs.versions.toml` (catalog с 7 версиями) — см. DECISIONS.md.
  - 4 build.gradle.kts под решения NEW-34 (compileOnly для spring/jackson/slf4j).
  - `shared-test-containers` — плагин `java-test-fixtures` (см. DECISIONS.md).
  - CHECKLIST п.1 «services/shared/.gitkeep» — **skipped N/A** (директория
    наполнилась сразу 4 модулями с файлами, .gitkeep не нужен).
  - Билд `./gradlew build` отложен до наполнения модулей кодом (Группа 2+),
    чтобы не прогонять Gradle на пустых compileJava задачах.
- Группы 2-4 (shared-web): 40 тестов (10 handlers + 3 validator'а + 3
  config-bean'а + 9 config-тестов + factories). AOP-тест aspect'а не может
  проверять счётчик на автоваренном бине (CGLIB proxy не делит state с
  target) — использую `AopUtils.isAopProxy` вместо side-effect counter.
- Группа 5 (shared-events): 13 тестов. Отступ от PLAN.md — в `build.gradle.kts`
  вынужден был добавить `testImplementation("jackson-databind")` и `jsr310`
  потому что `spring-boot-starter-test` не подтаскивает `JavaTimeModule`
  транзитивно без spring-data-* starter'а.
- Группа 6 (shared-logback): 18 тестов. Отступ от плана — вместо
  `MessageJsonProvider extends CompositeJsonProvider` использую
  `MessageJsonProvider extends MessageJsonProvider` (конкретный класс,
  не abstract parent) — overrides `writeTo` с regex-replace.
  Тестовый XML (`src/test/resources/logback-test.xml`) удалён,
  integration-тест строит pipeline программно. Добавил тест без
  вызова `start()` на providers — их стартует сам encoder (иначе
  `JsonFactory has not been set`).
- Группа 7 (shared-test-containers): 4 теста (3 passed + 1 @Disabled
  требующий Docker). PLAN.md упоминал `java-library (testFixtures scope)` —
  я применил плагин `java-test-fixtures`. Добавил `testImplementation`
  assertj/junit для smoke-тестов модуля (testFixtures-зависимости
  недоступны в `src/test/java` по умолчанию).
- Группа 8 (notification-service acceptance):
  - Пункты CHECKLIST «удалить локальный error-handling» и «мигрировать
    NotificationIntegrationIT с @MockitoBean» — **N/A**: ни одного из
    этих артефактов в notification-service до M01 не было. Зафиксировано
    в CHECKLIST с пометкой `[~]`.
  - `NotificationErrorHandlingIT extends ContainerTestBase` — реальный
    Mongo+Rabbit+Redis+Postgres через Testcontainers. 4 теста зелёные.
  - Пришлось добавить `spring-security-core` в notification-service deps:
    shared-web компилируется с `AccessDeniedException` (Spring Security)
    в compileOnly, но без runtime-класса Spring Boot на старте падает
    на рефлексии. Это единственный сервис который теперь имеет
    spring-security-core — остальные получат при миграции в M03.
  - Пришлось замокать `nl.martijndwars.webpush.PushService` в тесте:
    `WebPushConfig.@Bean` парсит VAPID ключи через BouncyCastle на
    старте, тестовые значения `test-priv` не являются валидной EC-парой.
  - `JwtHandshakeInterceptor.init()` читает JWT public key с диска —
    сгенерировал настоящий RSA PEM в `src/test/resources/test-public.pem`
    через `openssl`, путь пробрасывается через `@DynamicPropertySource`.
  - `logback-test.xml` в notification-service test/resources — без него
    `logback-spring.xml` (Spring Boot extension) не загружается в
    non-Spring-контексте (`NotificationLoggingIT`).
  - Notification-service после M01: 59 тестов (53 существующих + 6 новых
    integration), 0 failures.
