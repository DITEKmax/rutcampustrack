package ru.rutcampustrack.schedule.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M02 Группа 9 — negative-тест ArchUnit rule: сканируем test-fixture-пакет
 * с нарушающим классом, убеждаемся что rule падает с понятной ошибкой.
 * Если этот тест зелёный — {@link ScheduledMustHaveSchedulerLockTest} rule
 * реально работает (fix-не-забыли-проверить-негатив).
 */
class ScheduledLockRuleNegativeTest {

    /** Fixture: метод с {@link Scheduled} БЕЗ {@code @SchedulerLock} + без waiver'а. */
    @SuppressWarnings("unused")
    static class BadScheduledClass {
        @Scheduled(fixedRate = 60_000)
        public void unlockedJob() {
            // empty
        }
    }

    @Test
    void ruleFailsWhenScheduledMethodHasNoLockOrWaiver() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(BadScheduledClass.class);

        assertThatThrownBy(() ->
                ScheduledMustHaveSchedulerLockTest.scheduledMethodsMustHaveLockOrExplicitWaiver
                        .check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadScheduledClass.unlockedJob")
                .hasMessageContaining("@Scheduled без @SchedulerLock");
    }
}
