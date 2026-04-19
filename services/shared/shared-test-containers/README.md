# shared-test-containers

Testcontainers-фикстуры для integration-тестов всех сервисов (NEW-158, P2-8/2, P2-8/3).

## Что внутри

- **`ContainerTestBase`** — `@Testcontainers` base class с 4 контейнерами (Postgres/Mongo/Redis/RabbitMQ) и `@DynamicPropertySource`, пробрасывающим их в `spring.datasource.*` / `spring.data.mongodb.*` / `spring.data.redis.*` / `spring.rabbitmq.*`.
- **`GrpcInProcessFixture`** — реальный gRPC round-trip в одном JVM через `InProcessChannelBuilder`, без сетевого stack'а.
- **`WireMockFixture`** — HTTP-моки внешних сервисов (Telegram, FCM) на динамическом порту.
- **`MigrationTestUtils`** — поэтапный прогон Flyway-миграций (data-preservation тесты).

## Подключение в сервисе

`build.gradle.kts`:
```kotlin
dependencies {
    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
}
```

Тест:
```java
class MyServiceIT extends ContainerTestBase {
    @Autowired TestRestTemplate rest;

    @Test
    void smoke() {
        // Postgres/Rabbit/... уже подняты, spring auto-wired.
    }
}
```

## Включить reuse контейнеров (dev-машина)

Файл `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

После этого повторный прогон интеграционных тестов в сессии не перезапускает контейнеры (экономия 5-10 секунд на старт).

**В CI** переменная `TESTCONTAINERS_REUSE_ENABLE` намеренно **не** выставляется — каждый прогон начинает с чистого state.

## Зачем all-in-one `ContainerTestBase`

PLAN M01 явно требует один base class со всеми 4 контейнерами. Сервисам, которым не нужны все, это пока не мешает (reuse нивелирует cold-start). Если появится необходимость в модульном доступе — рефакторим в M08.
