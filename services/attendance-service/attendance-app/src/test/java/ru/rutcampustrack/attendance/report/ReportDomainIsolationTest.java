package ru.rutcampustrack.attendance.report;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit test enforcing domain isolation: report/ must NOT import from checkin/.
 * Proves RPRT-05 requirement (D-17 decision).
 *
 * Access path between domains goes through shared/port/AttendanceReadPort only.
 */
@AnalyzeClasses(packages = "ru.rutcampustrack.attendance")
class ReportDomainIsolationTest {

    @ArchTest
    static final ArchRule reportDoesNotImportCheckin =
            noClasses()
                    .that().resideInAPackage("ru.rutcampustrack.attendance.report..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("ru.rutcampustrack.attendance.checkin..");
}
