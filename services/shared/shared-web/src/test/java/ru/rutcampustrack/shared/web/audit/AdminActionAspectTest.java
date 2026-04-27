package ru.rutcampustrack.shared.web.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M16 G6 — расширенные тесты на {@link AdminActionAspect} после миграции
 * с заглушки на real handler.
 *
 * <p>Покрывает:
 * <ul>
 *   <li>aspect proxies метод и возвращает результат;</li>
 *   <li>aspect записывает entry в {@link AuditLogStorage};</li>
 *   <li>при exception entry пишется с {@code succeeded=false};</li>
 *   <li>methods без annotation — aspect no-op;</li>
 *   <li>storage exception не ломает caller (best-effort).</li>
 * </ul>
 */
@SpringJUnitConfig
@ContextConfiguration(classes = AdminActionAspectTest.TestConfig.class)
class AdminActionAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    @ComponentScan(basePackageClasses = AdminActionAspect.class)
    static class TestConfig {

        @Bean
        @Primary
        InMemoryAuditLogStorage auditLogStorage() {
            return new InMemoryAuditLogStorage();
        }

        @Bean
        @Primary
        AuditLogContextProvider auditLogContextProvider() {
            return new AuditLogContextProvider() {
                @Override public Long currentUserId() { return 42L; }
                @Override public String currentCorrelationId() { return "corr-test"; }
            };
        }
    }

    /** Test double — собирает entries для assertions. */
    static class InMemoryAuditLogStorage implements AuditLogStorage {
        final List<AuditLogEntry> entries = new ArrayList<>();
        boolean throwOnStore = false;

        @Override
        public void store(AuditLogEntry entry) {
            if (throwOnStore) throw new RuntimeException("storage down");
            entries.add(entry);
        }

        void reset() {
            entries.clear();
            throwOnStore = false;
        }
    }

    @Component
    static class Target {

        @AdminAction("test.action")
        public String execute(String payload) {
            return "result:" + payload;
        }

        @AdminAction("test.error")
        public void fail() {
            throw new IllegalStateException("boom");
        }

        public String noAnnotation(String x) {
            return "plain:" + x;
        }
    }

    @Autowired
    private Target target;

    @Autowired
    private InMemoryAuditLogStorage storage;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    @DisplayName("@AdminAction pointcut проксирует метод и возвращает результат proceed()")
    void aspectInterceptsAndReturnsResult() {
        String result = target.execute("x");
        assertThat(result).isEqualTo("result:x");
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(target)).isTrue();
    }

    @Test
    @DisplayName("M16 G6: aspect пишет audit entry в storage с context'ом")
    void aspectWritesAuditEntryOnSuccess() {
        target.execute("x");

        assertThat(storage.entries).hasSize(1);
        AuditLogEntry entry = storage.entries.get(0);
        assertThat(entry.action()).isEqualTo("test.action");
        assertThat(entry.userId()).isEqualTo(42L);
        assertThat(entry.correlationId()).isEqualTo("corr-test");
        assertThat(entry.succeeded()).isTrue();
        assertThat(entry.errorMessage()).isNull();
        assertThat(entry.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("M16 G6: при exception entry пишется с succeeded=false + error message")
    void aspectWritesAuditEntryOnException() {
        assertThatThrownBy(target::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(storage.entries).hasSize(1);
        AuditLogEntry entry = storage.entries.get(0);
        assertThat(entry.action()).isEqualTo("test.error");
        assertThat(entry.succeeded()).isFalse();
        assertThat(entry.errorMessage()).contains("boom");
    }

    @Test
    @DisplayName("M16 G6: storage exception не пробрасывается caller'у (best-effort)")
    void aspectSwallowsStorageException() {
        storage.throwOnStore = true;

        // Вызов должен пройти нормально, exception от storage swallow'ится.
        String result = target.execute("y");
        assertThat(result).isEqualTo("result:y");
    }

    @Test
    @DisplayName("@AdminAction pointcut пропускает исключения (не глотает)")
    void aspectPropagatesExceptions() {
        assertThatThrownBy(target::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("Методы без @AdminAction — возвращают результат (aspect no-op на них)")
    void unannotatedMethodIgnored() {
        assertThat(target.noAnnotation("y")).isEqualTo("plain:y");
        assertThat(storage.entries).isEmpty();
    }
}
