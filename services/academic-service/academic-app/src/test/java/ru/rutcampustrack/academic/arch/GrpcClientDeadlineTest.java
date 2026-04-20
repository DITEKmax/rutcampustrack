package ru.rutcampustrack.academic.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ru.rutcampustrack.shared.observability.GrpcDeadlineArchRules;

/**
 * M05 G9 DRY (NEW-149): правило вынесено в
 * {@link GrpcDeadlineArchRules}, здесь только per-service scoping.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.academic",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class GrpcClientDeadlineTest {

    @ArchTest
    static final ArchRule grpcClientsMustSetDeadline =
            GrpcDeadlineArchRules.grpcClientsMustSetDeadline();
}
