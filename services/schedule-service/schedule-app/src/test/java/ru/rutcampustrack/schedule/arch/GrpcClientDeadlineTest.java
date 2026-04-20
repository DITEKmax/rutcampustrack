package ru.rutcampustrack.schedule.arch;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * M05 Группа 8 — NEW-149 (schedule-app). См. attendance-app duplicate
 * {@code GrpcClientDeadlineTest} для полного описания. Проверяет
 * {@code AcademicGrpcClient} в schedule-service.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.schedule",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class GrpcClientDeadlineTest {

    private static final Set<String> EXCLUDED_METHOD_NAMES = Set.of(
            "<init>", "toString", "equals", "hashCode", "getClass"
    );

    @ArchTest
    static final ArchRule grpcClientsMustSetDeadline =
            methods()
                    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("GrpcClient")
                    .and().arePublic()
                    .should(invokeWithDeadline())
                    .because("NEW-149: каждый gRPC-call должен иметь deadline.");

    static ArchCondition<JavaMethod> invokeWithDeadline() {
        return new ArchCondition<>("invoke withDeadlineAfter/withDeadline on gRPC stub") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (EXCLUDED_METHOD_NAMES.contains(method.getName())) return;
                if (isAccessor(method)) return;
                if (method.getModifiers().stream()
                        .anyMatch(m -> m.name().equalsIgnoreCase("SYNTHETIC")
                                || m.name().equalsIgnoreCase("BRIDGE"))) return;

                boolean callsStub = method.getMethodCallsFromSelf().stream()
                        .map(call -> call.getTargetOwner().getName())
                        .anyMatch(owner -> owner.endsWith("BlockingStub")
                                || owner.endsWith("FutureStub")
                                || owner.endsWith("Stub"));
                if (!callsStub) return;

                boolean hasDeadline = method.getMethodCallsFromSelf().stream()
                        .map(JavaMethodCall::getTarget)
                        .anyMatch(target -> target.getName().equals("withDeadlineAfter")
                                || target.getName().equals("withDeadline"));
                if (!hasDeadline) {
                    events.add(SimpleConditionEvent.violated(method,
                            "Метод " + method.getFullName()
                                    + " вызывает gRPC stub без deadline. Добавьте "
                                    + ".withDeadlineAfter(3, TimeUnit.SECONDS). "
                                    + "См. M05 G8 / NEW-149."));
                }
            }
        };
    }

    private static boolean isAccessor(JavaMethod method) {
        String name = method.getName();
        return (name.startsWith("get") || name.startsWith("is"))
                && method.getRawParameterTypes().isEmpty();
    }
}
