package ru.rutcampustrack.shared.testcontainers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-тест fixtures без Spring-контекста.
 *
 * <p>Требует запущенный Docker. Проверяет что контейнеры стартуют и основные
 * методы fixture доступны. Полноценный integration-тест со Spring-контекстом
 * делается в сервисах-потребителях (M01 Группа 8: notification-service).
 */
class FixtureSmokeTest {

    @Test
    @DisplayName("WireMockFixture стартует на динамическом порту")
    void wiremockStarts() {
        try (WireMockFixture wm = new WireMockFixture()) {
            wm.start();
            assertThat(wm.port()).isPositive();
            assertThat(wm.baseUrl()).startsWith("http://localhost:");
        }
    }

    @Test
    @DisplayName("GrpcInProcessFixture имеет уникальное имя канала")
    void grpcFixtureUniqueName() {
        GrpcInProcessFixture a = new GrpcInProcessFixture();
        GrpcInProcessFixture b = new GrpcInProcessFixture();
        assertThat(a.channelName()).isNotEqualTo(b.channelName());
    }

    @Test
    @DisplayName("MigrationTestUtils — entry point доступны (не требует подключения к БД)")
    void migrationUtilsApi() {
        // Проверяем сигнатуры — реальный прогон проверяется в тестах сервисов.
        assertThat(MigrationTestUtils.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("runMigrationsUpTo", "runAllMigrations", "clean");
    }

    /**
     * Стартует PostgreSQL из {@link ContainerTestBase} — убеждаемся что static
     * инициализация проходит и container.isRunning() = true.
     * Требует Docker. В CI без Docker — @Disabled чтобы не блокировать pipeline.
     */
    @Test
    @Disabled("требует Docker — проверяется integration-тестом notification-service (Группа 8)")
    void containersStart() {
        assertThat(ContainerTestBase.POSTGRES.isRunning()).isTrue();
        assertThat(ContainerTestBase.MONGO.isRunning()).isTrue();
        assertThat(ContainerTestBase.REDIS.isRunning()).isTrue();
        assertThat(ContainerTestBase.RABBITMQ.isRunning()).isTrue();
    }
}
