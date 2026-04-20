package ru.rutcampustrack.attendance.arch;

import com.tngtech.archunit.core.domain.JavaClass;
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
 * M05 Группа 8 — NEW-149: preventive gRPC deadline guard.
 *
 * <p>Каждый публичный метод класса с суффиксом {@code *GrpcClient} должен
 * вызывать {@code withDeadlineAfter(...)} или {@code withDeadline(...)} на
 * gRPC stub'е в том же методе. Это гарантирует, что новый callsite без
 * deadline не пройдёт CI — deadline-аудит M05 G8 закреплён как invariant.
 *
 * <p>Детали реализации: ArchUnit не умеет fluent-chain анализ, но умеет
 * method-invocation scanning по байт-коду. Проверяем что среди всех
 * `methodCallsFromSelf` метода есть хотя бы один invocation method-name'ом
 * {@code withDeadline*}.
 *
 * <p>Пропускаем: private helpers, static utility-методы, методы с именем
 * {@code toString/equals/hashCode} — они не делают gRPC call'ов.
 *
 * <p>Complement: runtime-guard {@code GrpcDeadlineEnforcingInterceptor}
 * (attendance/grpc/) вернёт {@code DEADLINE_EXCEEDED} если вызов прошёл
 * через статический анализ но deadline всё же null (например, если код
 * динамически создаёт stub через рефлексию).
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.attendance",
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
                    .because("NEW-149: каждый gRPC-call должен иметь "
                            + ".withDeadlineAfter(...) / .withDeadline(...) — "
                            + "иначе cascade timeouts при деградации downstream.");

    static ArchCondition<JavaMethod> invokeWithDeadline() {
        return new ArchCondition<>("invoke withDeadlineAfter/withDeadline on gRPC stub") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (EXCLUDED_METHOD_NAMES.contains(method.getName())) return;
                // Skip accessor methods, toString etc. — они не взаимодействуют с stub.
                if (isAccessor(method)) return;
                // Skip bridge / synthetic — Lombok generated.
                if (method.getModifiers().stream()
                        .anyMatch(m -> m.name().equalsIgnoreCase("SYNTHETIC")
                                || m.name().equalsIgnoreCase("BRIDGE"))) {
                    return;
                }
                // Skip методы-помощники которые не касаются gRPC stub (есть public
                // fallback / error-map утилиты в некоторых GrpcClient). Эвристика:
                // метод должен содержать хотя бы один вызов *BlockingStub/Stub
                // чтобы считаться gRPC-вызывающим.
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
                                    + " вызывает gRPC stub без .withDeadlineAfter(...) / "
                                    + ".withDeadline(...). Добавьте deadline (3s по умолчанию) "
                                    + "чтобы избежать cascade timeouts. См. M05 G8 / NEW-149."));
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
