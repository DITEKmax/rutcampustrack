package ru.rutcampustrack.attendance.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules;

/**
 * M05 G8 (NEW-149) + G9 DRY: preventive gRPC deadline guard для
 * attendance-service. Правило полностью вынесено в
 * {@link GrpcDeadlineArchRules}; здесь только per-service scoping через
 * {@link AnalyzeClasses}.
 *
 * <p>Complement (runtime-guard): при желании поверх ArchUnit можно
 * добавить {@code GrpcDeadlineEnforcingInterceptor} — см. DECISIONS
 * D11 для контекста отказа от него в M05.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.attendance",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class GrpcClientDeadlineTest {

    @ArchTest
    static final ArchRule grpcClientsMustSetDeadline =
            GrpcDeadlineArchRules.grpcClientsMustSetDeadline();
}
