package ru.rutcampustrack.academic.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;

/**
 * M02 Группа 9 / NEW-28 — academic-service copy of NEW-28 guardrail. См. {@code
 * ru.rutcampustrack.schedule.arch.ScheduledMustHaveSchedulerLockTest}.
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.academic",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ScheduledMustHaveSchedulerLockTest {

    @ArchTest
    static final ArchRule scheduledMethodsMustHaveLockOrExplicitWaiver =
            methods()
                    .that()
                    .areAnnotatedWith(Scheduled.class)
                    .should(haveSchedulerLockOrSingleInstanceWaiver())
                    .allowEmptyShould(true)
                    .because(
                            "NEW-28: @Scheduled методы должны координироваться через "
                                    + "@SchedulerLock при scale-out либо иметь явный "
                                    + "@SingleInstanceScheduled с непустым reason.");

    static ArchCondition<JavaMethod> haveSchedulerLockOrSingleInstanceWaiver() {
        return new ArchCondition<>("have @SchedulerLock or documented @SingleInstanceScheduled") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(SchedulerLock.class)) return;
                if (hasSingleInstanceWaiver(method)) return;
                events.add(
                        SimpleConditionEvent.violated(
                                method,
                                "Метод "
                                        + method.getFullName()
                                        + " помечен @Scheduled без @SchedulerLock и без "
                                        + "@SingleInstanceScheduled с непустым reason"));
            }
        };
    }

    @Test
    void schedulerConditionRequiresLockOrRetainedNonblankReason() {
        var examples =
                new ClassFileImporter()
                        .importClasses(SchedulerExamples.class)
                        .get(SchedulerExamples.class);
        var condition = haveSchedulerLockOrSingleInstanceWaiver();
        for (String method : new String[] {"unmarked", "emptyReason", "blankReason"}) {
            var events = ConditionEvents.Factory.create();
            condition.check(examples.getMethod(method), events);
            assertThat(events.containViolation()).as(method).isTrue();
        }
        for (String method : new String[] {"documentedLocal", "coordinated"}) {
            var events = ConditionEvents.Factory.create();
            condition.check(examples.getMethod(method), events);
            assertThat(events.containViolation()).as(method).isFalse();
        }
    }

    private static final class SchedulerExamples {
        @Scheduled(fixedDelay = 1000)
        void unmarked() {}

        @Scheduled(fixedDelay = 1000)
        @SingleInstanceScheduled(reason = "")
        void emptyReason() {}

        @Scheduled(fixedDelay = 1000)
        @SingleInstanceScheduled(reason = "  ")
        void blankReason() {}

        @Scheduled(fixedDelay = 1000)
        @SingleInstanceScheduled(reason = "Each process refreshes its own local snapshot")
        void documentedLocal() {}

        @Scheduled(fixedDelay = 1000)
        @SchedulerLock(name = "schedulerRuleExample")
        void coordinated() {}
    }

    static boolean hasSingleInstanceWaiver(JavaMethod method) {
        return method.tryGetAnnotationOfType(SingleInstanceScheduled.class.getName())
                .map(annotation -> annotation.get("reason").orElse(null))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(reason -> !reason.isBlank())
                .isPresent();
    }
}
