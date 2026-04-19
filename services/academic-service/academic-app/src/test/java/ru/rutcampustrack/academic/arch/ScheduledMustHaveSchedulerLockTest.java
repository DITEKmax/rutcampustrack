package ru.rutcampustrack.academic.arch;

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
 * M02 Группа 9 / NEW-28 — academic-service copy of NEW-28 guardrail.
 * См. {@code ru.rutcampustrack.schedule.arch.ScheduledMustHaveSchedulerLockTest}.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.academic",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ScheduledMustHaveSchedulerLockTest {

    @ArchTest
    static final ArchRule scheduledMethodsMustHaveLockOrExplicitWaiver =
            methods()
                    .that().areAnnotatedWith(Scheduled.class)
                    .should(haveSchedulerLockOrSingleInstanceWaiver())
                    .allowEmptyShould(true)
                    .because(
                            "NEW-28: @Scheduled методы должны координироваться через "
                                    + "@SchedulerLock при scale-out либо иметь явный "
                                    + "@SuppressWarnings(\"SingleInstance\") маркер.");

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
