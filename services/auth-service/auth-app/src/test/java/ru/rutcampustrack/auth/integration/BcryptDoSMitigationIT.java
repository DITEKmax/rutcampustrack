package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;

import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual HTTP requests hold real BCrypt work at the guard boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(BcryptDoSMitigationIT.TestGuardConfig.class)
class BcryptDoSMitigationIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private HoldingEncoder encoder;

    @TestConfiguration
    static class TestGuardConfig {
        @Bean
        @Primary
        HoldingEncoder holdingEncoder() {
            return new HoldingEncoder();
        }

        @Bean
        @Primary
        BcryptConcurrencyGuard narrowGuard() {
            return new BcryptConcurrencyGuard(2, 0);
        }
    }

    @Test
    void concurrentInvalidPassword_rejectsWithoutCpuBlowup() throws Exception {
        encoder.reset();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> first = executor.submit(() -> wrongPassword("student"));
            Future<ResponseEntity<String>> second = executor.submit(() -> wrongPassword("teacher"));
            assertThat(encoder.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(wrongPassword("admin").getStatusCode().value()).isEqualTo(429);
            assertThat(first.isDone()).isFalse();
            assertThat(second.isDone()).isFalse();
            encoder.release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(401);
            assertThat(second.get(10, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(401);
            assertThat(encoder.maximum.get()).isEqualTo(2);
            assertThat(wrongPassword("admin").getStatusCode().value()).isEqualTo(401);
            assertThat(encoder.active.get()).isZero();
            assertThat(encoder.calls.get()).isEqualTo(3);
        } finally {
            encoder.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResponseEntity<String> wrongPassword(String login) {
        return restTemplate.postForEntity(
                "/auth/login", new LoginRequest(login, "synthetic-wrong-password"), String.class);
    }

    static final class HoldingEncoder
            implements org.springframework.security.crypto.password.PasswordEncoder {
        private final org.springframework.security.crypto.password.PasswordEncoder delegate =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maximum = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        volatile java.util.concurrent.CountDownLatch entered;
        volatile java.util.concurrent.CountDownLatch release;

        void reset() {
            active.set(0);
            maximum.set(0);
            calls.set(0);
            entered = new java.util.concurrent.CountDownLatch(2);
            release = new java.util.concurrent.CountDownLatch(1);
        }

        @Override
        public String encode(CharSequence raw) {
            return delegate.encode(raw);
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            if (entered == null) return delegate.matches(raw, encoded);
            calls.incrementAndGet();
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS))
                    throw new AssertionError("BCrypt work was not released");
                return delegate.matches(raw, encoded);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
