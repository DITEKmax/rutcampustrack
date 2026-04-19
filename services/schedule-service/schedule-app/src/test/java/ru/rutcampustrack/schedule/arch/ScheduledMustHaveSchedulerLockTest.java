package ru.rutcampustrack.schedule.arch;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * M02 Группа 9 / NEW-28 — любой метод с {@link Scheduled} должен иметь либо
 * {@link SchedulerLock} (cluster-coordinated), либо
 * {@code @SuppressWarnings("SingleInstance")} (осознанный single-instance
 * by design).
 *
 * <p>Без этой гарантии при scale-out нескольких инстансов сервиса
 * {@code @Scheduled} методы выполнялись бы параллельно на каждом, что для
 * outbox publisher'а означает duplicate messages, а для cron'ов вроде
 * status-transitions — race-conditions.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.schedule",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ScheduledMustHaveSchedulerLockTest {

    @ArchTest
    static final ArchRule scheduledMethodsMustHaveLockOrExplicitWaiver =
            methods()
                    .that().areAnnotatedWith(Scheduled.class)
                    .should(haveSchedulerLockOrSingleInstanceWaiver())
                    .because(
                            "NEW-28: @Scheduled методы должны координироваться через "
                                    + "@SchedulerLock при scale-out. Single-instance by design — "
                                    + "явный @SuppressWarnings(\"SingleInstance\") + комментарий.");

    static ArchCondition<JavaMethod> haveSchedulerLockOrSingleInstanceWaiver() {
        return new ArchCondition<>("have @SchedulerLock or @SuppressWarnings(\"SingleInstance\")") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(SchedulerLock.class)) return;
                if (hasSingleInstanceSuppression(method)) return;
                events.add(SimpleConditionEvent.violated(
                        method,
                        "Метод " + method.getFullName()
                                + " помечен @Scheduled без @SchedulerLock и без "
                                + "@SuppressWarnings(\"SingleInstance\")"));
            }
        };
    }

    static boolean hasSingleInstanceSuppression(JavaMethod method) {
        return method.tryGetAnnotationOfType(SuppressWarnings.class.getName())
                .map(a -> {
                    Object value = a.get("value").orElse(null);
                    if (value instanceof String[] arr) {
                        for (String v : arr) {
                            if ("SingleInstance".equals(v)) return true;
                        }
                    }
                    return value instanceof String s && "SingleInstance".equals(s);
                })
                .orElse(false);
    }
}
