package ru.rutcampustrack.auth.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ru.rutcampustrack.shared.testcontainers.IntegrationTestNamingRule;

/**
 * M08 Группа 1 (P2-8/1) — wrapper над shared
 * {@link IntegrationTestNamingRule#integrationTestsMustEndWithIT()}.
 *
 * <p>Scan только test-классов auth-service
 * ({@code OnlyIncludeTests.class}).
 */
@AnalyzeClasses(
        packages = "ru.rutcampustrack.auth",
        importOptions = ImportOption.OnlyIncludeTests.class)
class IntegrationTestNamingConventionIT {

    @ArchTest
    static final ArchRule integrationTestsMustEndWithIT =
            IntegrationTestNamingRule.integrationTestsMustEndWithIT();
}
