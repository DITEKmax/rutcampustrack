package ru.rutcampustrack.shared.observability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

/**
 * M05 G9 DRY (NEW-149): shared ArchUnit rule, проверяющая что каждый публичный метод класса с
 * суффиксом {@code *GrpcClient}, вызывающий gRPC stub, содержит {@code withDeadlineAfter(...)} /
 * {@code withDeadline(...)} в том же методе.
 *
 * <p>Per-service использование:
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "ru.rutcampustrack.attendance",
 *         importOptions = ImportOption.DoNotIncludeTests.class)
 * class GrpcClientDeadlineTest {
 *     @ArchTest
 *     static final ArchRule rule = GrpcDeadlineArchRules.grpcClientsMustSetDeadline();
 * }
 * }</pre>
 *
 * <p>Раньше правило дублировалось в attendance/schedule/academic arch тестах (3 × ~75 LOC).
 * Вынесено в shared-observability testFixtures для устранения copy-paste при обновлениях условия
 * (например, при добавлении нового суффикса stub'а в gRPC библиотеке).
 */
public final class GrpcDeadlineArchRules {

    private static final Set<String> EXCLUDED_METHOD_NAMES =
            Set.of("<init>", "toString", "equals", "hashCode", "getClass");

    private GrpcDeadlineArchRules() {}

    /**
     * Structural guard (runtime CallOptions observation is required for deadline proof). ArchRule:
     * public methods в *GrpcClient, вызывающие *Stub, обязаны содержать {@code withDeadlineAfter} /
     * {@code withDeadline} в теле.
     */
    public static ArchRule grpcClientsMustSetDeadline() {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("GrpcClient")
                .and()
                .arePublic()
                .should(invokeWithDeadline())
                .because(
                        "NEW-149: каждый gRPC-call должен иметь "
                                + ".withDeadlineAfter(...) / .withDeadline(...) — "
                                + "иначе cascade timeouts при деградации downstream.");
    }

    static ArchCondition<JavaMethod> invokeWithDeadline() {
        return new ArchCondition<>("invoke withDeadlineAfter/withDeadline on gRPC stub") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (EXCLUDED_METHOD_NAMES.contains(method.getName())) return;
                // Skip bridge / synthetic — Lombok/compiler generated.
                if (method.getModifiers().stream()
                        .anyMatch(
                                m ->
                                        m.name().equalsIgnoreCase("SYNTHETIC")
                                                || m.name().equalsIgnoreCase("BRIDGE"))) {
                    return;
                }
                java.util.Set<JavaMethod> reachable = new java.util.HashSet<>();
                collectDelegates(method, reachable);
                boolean callsStub =
                        reachable.stream()
                                .flatMap(candidate -> candidate.getMethodCallsFromSelf().stream())
                                .map(call -> call.getTargetOwner().getName())
                                .anyMatch(
                                        owner ->
                                                owner.endsWith("BlockingStub")
                                                        || owner.endsWith("FutureStub")
                                                        || owner.endsWith("Stub"));
                if (!callsStub) return;
                boolean hasDeadline =
                        reachable.stream()
                                .flatMap(candidate -> candidate.getMethodCallsFromSelf().stream())
                                .map(JavaMethodCall::getTarget)
                                .anyMatch(
                                        target ->
                                                target.getName().equals("withDeadlineAfter")
                                                        || target.getName().equals("withDeadline"));
                if (!hasDeadline) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    method,
                                    "Метод "
                                            + method.getFullName()
                                            + " вызывает gRPC stub без .withDeadlineAfter(...) /"
                                            + " .withDeadline(...). Добавьте deadline (3s по"
                                            + " умолчанию) чтобы избежать cascade timeouts. См. M05"
                                            + " G8 / NEW-149."));
                }
            }
        };
    }

    private static void collectDelegates(JavaMethod method, java.util.Set<JavaMethod> reachable) {
        if (!reachable.add(method)) return;
        method.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().equals(method.getOwner()))
                .map(call -> call.getTarget().resolveMember())
                .flatMap(java.util.Optional::stream)
                .forEach(delegate -> collectDelegates(delegate, reachable));
    }

    /**
     * Runtime oracle for a consumer's observing ClientInterceptor. Pass the actual CallOptions
     * supplied to Channel.newCall/interceptCall, and separately assert that the RPC was observed.
     * Reflection keeps this ArchUnit fixture usable without adding gRPC runtime dependencies. The
     * static rule above is a structural guard; it cannot prove immutable stub return use.
     */
    public static void assertCallHasDeadline(Object actualCallOptions) {
        try {
            Object deadline =
                    actualCallOptions.getClass().getMethod("getDeadline").invoke(actualCallOptions);
            if (deadline == null) throw new AssertionError("Actual gRPC call has no deadline");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Expected actual io.grpc.CallOptions", error);
        }
    }
}
