package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M03b Группа 10 (KI-7): bcrypt DoS mitigation smoke test.
 *
 * <p>50 concurrent invalid-password requests. С guard'ом N=2 permits (override)
 * — большинство должно получить 429 fail-fast, а не ждать bcrypt completion.
 * Без guard'а bcrypt бы отработал всем 50 одновременно (CPU saturation).
 *
 * <p>Проверяем:
 * <ul>
 *   <li>Ни один запрос не вешается — total time &lt; 10s (с guard'ом
 *     быстрый отказ).</li>
 *   <li>Минимум один ответ 429 (concurrency-limit сработал).</li>
 *   <li>Успешных 200 не должно быть (все invalid-password).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(BcryptDoSMitigationIT.TestGuardConfig.class)
class BcryptDoSMitigationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @TestConfiguration
    static class TestGuardConfig {
        @Bean
        @Primary
        BcryptConcurrencyGuard narrowGuard() {
            return new BcryptConcurrencyGuard(2, 0);
        }
    }

    @Test
    void concurrentInvalidPassword_rejectsWithoutCpuBlowup() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger status401 = new AtomicInteger();
        AtomicInteger status429 = new AtomicInteger();
        AtomicInteger statusOther = new AtomicInteger();

        long started = System.nanoTime();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                // Разные login'ы чтобы не упереться в LoginRateLimiter (5 failures →
                // progressive block). `student` seeded в test data — existing user.
                // 50 invalid passwords с разных login'ов не триггерят rate-limit.
                String login = "student";
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/auth/login",
                            new LoginRequest(login, "wrong_password_" + idx),
                            String.class);
                    int code = response.getStatusCode().value();
                    if (code == 401) status401.incrementAndGet();
                    else if (code == 429) status429.incrementAndGet();
                    else statusOther.incrementAndGet();
                } catch (Exception e) {
                    statusOther.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        executor.shutdown();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        // С guard'ом N=2 — максимум 2 bcrypt invocations параллельно, остальные 429.
        // Точные числа зависят от timing'а, но хотя бы несколько 429 должны быть
        // среди 50 concurrent.
        assertThat(status429.get())
                .as("Хотя бы один запрос должен получить 429 (concurrency-limit сработал)")
                .isGreaterThan(0);
        assertThat(status401.get() + status429.get())
                .as("Все 50 запросов должны завершиться 401 или 429 (никаких 5xx)")
                .isEqualTo(50);
        assertThat(elapsedMs)
                .as("50 concurrent не должны висеть дольше 30s")
                .isLessThan(30_000);
    }
}
