package ru.rutcampustrack.schedule.config;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G24-fix-5 — structure regression guard для invariant'а:
 * {@link SchedulingConfig} в prod профиле обязан выставлять
 * {@link LockProvider} bean. {@code OutboxConfig.Publisher} +
 * {@code IdempotencyCleanupJob} зависят от него для @SchedulerLock.
 *
 * <p>Если кто-то удалит SchedulingConfig либо переименует bean без
 * правки OutboxConfig — prod scheduler упадёт runtime «no LockProvider
 * bean defined». Этот тест catch'ит регрессию compile-time.
 *
 * <p>Не используем Spring context (был бы overkill) — reflection
 * проверяет наличие @Bean метода с возвращаемым типом LockProvider
 * + class-level @Profile("!test") (чтобы не активировался в test).
 */
class SchedulingConfigStructureTest {

    @Test
    @DisplayName("SchedulingConfig имеет @Bean LockProvider")
    void schedulingConfig_declaresLockProviderBean() {
        Method[] methods = SchedulingConfig.class.getDeclaredMethods();
        boolean hasBean = Arrays.stream(methods)
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .anyMatch(m -> LockProvider.class.isAssignableFrom(m.getReturnType()));

        assertThat(hasBean)
                .as("SchedulingConfig должен иметь @Bean метод возвращающий LockProvider — "
                        + "OutboxConfig.Publisher (idempotencyCleanupJob, outboxPublisherJob, "
                        + "outboxCleanupJob) зависят от него для @SchedulerLock в prod")
                .isTrue();
    }

    @Test
    @DisplayName("SchedulingConfig имеет @Profile(\"!test\") (не активен в IT)")
    void schedulingConfig_isProfileGuarded() {
        Profile profile = SchedulingConfig.class.getAnnotation(Profile.class);
        assertThat(profile)
                .as("SchedulingConfig должен иметь @Profile(\"!test\") чтобы не "
                        + "тикать @Scheduled jobs в IT")
                .isNotNull();
        assertThat(profile.value())
                .as("@Profile значение должно быть [\"!test\"] чтобы исключать только test profile")
                .containsExactly("!test");
    }
}
