package ru.rutcampustrack.shared.outbox;

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
 * M02 Группа 11 (bug-hunter fix) / NEW-28 — закрывает gap в покрытии:
 * сервис-специфичные {@code ScheduledMustHaveSchedulerLockTest} сканируют
 * только {@code ru.rutcampustrack.{service}}, не shared-outbox. Если кто-то
 * добавит {@code @Scheduled} метод в {@code OutboxPublisherJob} /
 * {@code OutboxCleanupJob} без {@code @SchedulerLock}, сервисные правила
 * это не поймают. Этот тест — rule для самого shared-outbox модуля.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.shared.outbox",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class SharedOutboxSchedulerLockTest {

    @ArchTest
    static final ArchRule scheduledMethodsMustHaveLockOrExplicitWaiver =
            methods()
                    .that().areAnnotatedWith(Scheduled.class)
                    .should(haveSchedulerLockOrSingleInstanceWaiver())
                    .allowEmptyShould(true)
                    .because(
                            "NEW-28: @Scheduled методы в shared-outbox должны "
                                    + "координироваться через @SchedulerLock.");

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
