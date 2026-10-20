"""Actual collector controls with temporary tracked Java/XML fixtures and no JVM or services."""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().parents[2] / "scripts/collect-ci-test-outcomes.py"
SPEC = importlib.util.spec_from_file_location("safe_outcomes", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Collector source unavailable")
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)
MARKER = "RCT_SYNTHETIC_PROOF_DO_NOT_EMIT_731904"


class SafeOutcomesControl(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(prefix="rct-ci-outcome-control-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "services/api-gateway/src/test/java/example/tests/SafeCases.java"
        self.source.parent.mkdir(parents=True)
        self.source.write_text(
            """package example.tests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
class SafeCases {
    @Test void pageUsesPublishedAssignments() {}
    @Test void failsSafely() {}
    @Test void errorsSafely() {}
    @Test void skippedSafely() {}
    @Test void classLiteralBeforeNestedBlock() { Object x = String.class; if (x != null) {} }
    @ParameterizedTest @ValueSource(strings = {"synthetic-secret"}) void parameterizedBoundary(String value) {}
    @Nested class Inner { @Test void nestedBoundary() {} }
    void helperMustNotBeMapped() {}
    String text = "@Test void counterfeitMethod() {}";
    // @Test void anotherCounterfeit() {}
}
""",
            encoding="utf8",
        )
        for arguments in (
            ["init", "-q"],
            ["add", "."],
            [
                "-c",
                "user.name=CI Control",
                "-c",
                "user.email=ci-control@example.invalid",
                "commit",
                "-qm",
                "source fixture",
            ],
        ):
            subprocess.run(["git", *arguments], cwd=self.root, capture_output=True, check=True)
        self.environment = {
            "RCT_CI_PR_HEAD_SHA": "b" * 40,
            "RCT_CI_EVENT_NAME": "pull_request",
            "RCT_CI_RUN_ID": "12345",
            "RCT_CI_RUN_ATTEMPT": "2",
            "RCT_CI_TEST_OUTCOME": "success",
        }
        self.results = self.root / "services/api-gateway/build/test-results/test"
        self.results.mkdir(parents=True)

    def xml(self, body: str, *, tests: int, failures: int = 0, errors: int = 0, skipped: int = 0) -> None:
        (self.results / "TEST-control.xml").write_text(
            f'<testsuite name="{MARKER}" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">'
            f'<properties><property name="proof" value="{MARKER}"/></properties>{body}'
            f"<system-out>{MARKER}</system-out><system-err>{MARKER}</system-err></testsuite>",
            encoding="utf8",
        )

    def collect(self) -> dict:
        report = COLLECTOR.collect(self.root, "api-gateway", "test", self.environment)
        self.assertTrue(MARKER not in json.dumps(report), "Privacy marker leaked into safe outcome artifact")
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        return report

    def test_success_static_identity_and_exact_checkout_metadata(self) -> None:
        self.xml('<testcase classname="example.tests.SafeCases" name="pageUsesPublishedAssignments()"/>', tests=1)
        report = self.collect()
        self.assertEqual(report["collection"], "RESULTS")
        self.assertEqual(report["cases"][0]["method"], "pageUsesPublishedAssignments")
        self.assertEqual(report["cases"][0]["classname"], "example.tests.SafeCases")
        self.assertEqual(report["cases"][0]["status"], "PASSED")
        self.assertEqual(report["metadata"]["checkoutSha"], COLLECTOR.git(self.root, "rev-parse", "HEAD"))
        self.assertEqual(report["metadata"]["prHeadSha"], "b" * 40)
        self.assertEqual(report["metadata"]["runId"], "12345")
        self.assertEqual(report["metadata"]["testStepOutcome"], "success")
        self.assertEqual(
            report["cases"][0]["sourceSha256"], COLLECTOR.hashlib.sha256(self.source.read_bytes()).hexdigest()
        )
        self.assertEqual(len(report["xml"][0]["sha256"]), 64)

    def test_failure_error_skip_kept_without_messages_properties_or_streams(self) -> None:
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        self.xml(
            f'<testcase classname="example.tests.SafeCases" name="failsSafely()"><failure message="{MARKER}">{MARKER}</failure></testcase>'
            f'<testcase classname="example.tests.SafeCases" name="errorsSafely()"><error message="{MARKER}"/></testcase>'
            f'<testcase classname="example.tests.SafeCases" name="skippedSafely()"><skipped message="{MARKER}"/></testcase>',
            tests=3,
            failures=1,
            errors=1,
            skipped=1,
        )
        report = self.collect()
        self.assertEqual([row["status"] for row in report["cases"]], ["FAILED", "ERROR", "SKIPPED"])
        self.assertEqual(report["metadata"]["testStepOutcome"], "failure")
        self.assertEqual(report["summary"]["PASSED"], 0)

    def test_parameter_values_and_unknown_valid_identifiers_never_become_identity(self) -> None:
        self.xml(
            f'<testcase classname="example.tests.SafeCases" name="[1] proof={MARKER}"/>'
            f'<testcase classname="example.tests.{MARKER}" name="{MARKER}"/>'
            f'<testcase classname="example.tests.SafeCases" name="parameterizedBoundary({MARKER})[1]"/>'
            '<testcase classname="example.tests.SafeCases" name="helperMustNotBeMapped()"/>',
            tests=4,
        )
        cases = self.collect()["cases"]
        self.assertEqual(cases[0]["identity"], "ORDINAL_ONLY")
        self.assertIsNone(cases[1]["classname"])
        self.assertEqual(cases[2]["method"], "parameterizedBoundary")
        self.assertIsNone(cases[3]["method"])
        self.assertEqual([row["ordinal"] for row in cases], [1, 2, 3, 4])

    def test_nested_class_maps_and_source_strings_comments_cannot_invent_methods(self) -> None:
        self.xml(
            '<testcase classname="example.tests.SafeCases$Inner" name="nestedBoundary()"/>'
            '<testcase classname="example.tests.SafeCases" name="counterfeitMethod()"/>'
            '<testcase classname="example.tests.SafeCases" name="anotherCounterfeit()"/>',
            tests=3,
        )
        cases = self.collect()["cases"]
        self.assertEqual(cases[0]["method"], "nestedBoundary")
        self.assertIsNone(cases[1]["method"])
        self.assertIsNone(cases[2]["method"])

    def test_missing_and_empty_data_are_no_results_even_if_step_succeeded(self) -> None:
        self.assertEqual(self.collect()["collection"], "NO_RESULTS")
        self.xml("", tests=0)
        report = self.collect()
        self.assertEqual(report["collection"], "NO_RESULTS")
        self.assertEqual(report["cases"], [])
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        self.assertEqual(self.collect()["metadata"]["testStepOutcome"], "failure")

    def test_declared_missing_and_explicit_notrun_cases_are_not_executed(self) -> None:
        self.xml('<testcase classname="example.tests.SafeCases" name="skippedSafely()" status="notrun"/>', tests=2)
        report = self.collect()
        self.assertEqual(report["collection"], "INCOMPLETE")
        self.assertEqual(report["summary"]["NOT_EXECUTED"], 2)
        self.assertEqual(report["summary"]["PASSED"], 0)

    def test_malformed_entity_and_oversize_xml_fail_closed_without_raw_error(self) -> None:
        path = self.results / "TEST-control.xml"
        for data, state in (
            (f"<testsuite>{MARKER}", "MALFORMED_XML"),
            (f'<!DOCTYPE test [<!ENTITY proof "{MARKER}">]><testsuite/>', "UNINSPECTABLE_XML"),
        ):
            path.write_text(data)
            report = self.collect()
            self.assertEqual(report["collection"], "UNINSPECTABLE_RESULTS")
            self.assertEqual(report["xml"][0]["state"], state)
        with patch.object(COLLECTOR, "MAX_XML_BYTES", 8):
            path.write_text("<testsuite/>")
            self.assertEqual(self.collect()["xml"][0]["state"], "UNINSPECTABLE_XML")

    def test_other_task_and_untracked_source_do_not_pollute_selected_scope(self) -> None:
        other = self.results.parent / "integrationTest"
        other.mkdir()
        (other / "TEST-other.xml").write_text(f'<testsuite name="{MARKER}" tests="1"/>')
        self.assertEqual(self.collect()["collection"], "NO_RESULTS")
        untracked = self.source.with_name("Untracked.java")
        untracked.write_text("package example.tests; class Untracked { @Test void fake() {} }")
        self.xml('<testcase classname="example.tests.Untracked" name="fake()"/>', tests=1)
        self.assertIsNone(self.collect()["cases"][0]["classname"])

    def test_wrong_job_and_invalid_metadata_are_rejected_without_echoing_values(self) -> None:
        self.environment["RCT_CI_JOB_ID"] = "java-integration-test"
        with self.assertRaisesRegex(ValueError, "Invalid job scope"):
            self.collect()
        self.environment["RCT_CI_JOB_ID"] = "java-unit-test"
        self.environment["RCT_CI_RUN_ID"] = MARKER
        with self.assertRaises(ValueError) as failure:
            self.collect()
        self.assertTrue(MARKER not in str(failure.exception), "Metadata error echoed a value")

    def test_symlinked_report_is_not_read_or_hashed(self) -> None:
        private = self.root / "private-control.txt"
        private.write_text(MARKER)
        (self.results / "TEST-symlink.xml").symlink_to(private)
        report = self.collect()
        self.assertEqual(report["collection"], "UNINSPECTABLE_RESULTS")
        self.assertIsNone(report["xml"][0]["sha256"])

    def test_linked_source_parent_never_reads_inside_or_outside_bytes(self) -> None:
        for outside in (False, True):
            with self.subTest(outside=outside):
                temporary = tempfile.TemporaryDirectory(prefix="rct-ci-source-target-")
                self.addCleanup(temporary.cleanup)
                target = Path(temporary.name) / "linked" if outside else self.root / "source-target"
                parent = self.source.parent
                parent.rename(target)
                parent.symlink_to(target, target_is_directory=True)
                try:
                    self.xml(
                        '<testcase classname="example.tests.SafeCases" name="pageUsesPublishedAssignments()"/>', tests=1
                    )
                    original = Path.read_bytes
                    reads = []

                    def track(path):
                        reads.append(path)
                        return original(path)

                    with patch.object(Path, "read_bytes", track):
                        report = self.collect()
                    self.assertIsNone(report["cases"][0]["classname"], "Linked source became canonical identity")
                    self.assertNotIn(self.source, reads, "Linked source bytes were read before rejection")
                finally:
                    parent.unlink()
                    target.rename(parent)

    def test_linked_result_task_directory_rejects_inside_outside_and_cross_task(self) -> None:
        for placement in ("inside", "outside", "cross-task"):
            with self.subTest(placement=placement):
                temporary = tempfile.TemporaryDirectory(prefix="rct-ci-report-target-")
                self.addCleanup(temporary.cleanup)
                target = (
                    Path(temporary.name) / "linked"
                    if placement == "outside"
                    else self.results.parent / "integrationTest"
                    if placement == "cross-task"
                    else self.root / "report-target"
                )
                self.results.rename(target)
                self.results.symlink_to(target, target_is_directory=True)
                try:
                    self.xml(
                        '<testcase classname="example.tests.SafeCases" name="pageUsesPublishedAssignments()"/>', tests=1
                    )
                    original = Path.read_bytes
                    reads = []

                    def track(path):
                        reads.append(path)
                        return original(path)

                    with patch.object(Path, "read_bytes", track):
                        report = self.collect()
                    self.assertEqual(
                        report["collection"], "UNINSPECTABLE_RESULTS", "Linked task directory was accepted"
                    )
                    self.assertEqual(report["cases"], [], "Linked task produced case outcomes")
                    self.assertEqual(report["xml"], [], "Linked task produced XML hashes")
                    self.assertNotIn(self.results / "TEST-control.xml", reads, "Linked task XML bytes were read")
                finally:
                    self.results.unlink()
                    target.rename(self.results)

    def test_linked_higher_report_ancestor_is_rejected_before_read(self) -> None:
        self.xml('<testcase classname="example.tests.SafeCases" name="pageUsesPublishedAssignments()"/>', tests=1)
        ancestor = self.results.parent.parent
        target = self.root / "linked-build"
        ancestor.rename(target)
        ancestor.symlink_to(target, target_is_directory=True)
        report = self.collect()
        self.assertEqual(report["collection"], "UNINSPECTABLE_RESULTS")
        self.assertEqual(report["cases"], [])
        self.assertEqual(report["xml"], [])

    def schedule_fixture(self) -> None:
        source = self.root / COLLECTOR.MODULES["schedule-service"] / "src/test/java/RecurringScheduleItemIT.java"
        source.parent.mkdir(parents=True)
        source.write_text(
            "package ru.rutcampustrack.schedule.integration; class RecurringScheduleItemIT {"
            "@Test void capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert() {}"
            "@Test void otherBoundary() {} }"
        )
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["schedule-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment["RCT_CI_JOB_ID"] = "java-coverage"
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"

    def collect_schedule(self) -> dict:
        report = COLLECTOR.collect(self.root, "schedule-service", "integrationTest", self.environment)
        self.assertNotIn(MARKER, json.dumps(report))
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        return report

    def test_coverage_retains_target_fail_and_only_closed_diagnostic(self) -> None:
        self.schedule_fixture()
        diagnostic = "CAP_FUTURE_DIAGNOSTIC_V1 class=CannotAcquireLockException sqlState=40P01 barrier=NOT_OBSERVED"
        self.xml(
            f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="{COLLECTOR.CAP_METHOD}()">'
            f'<failure message="org.opentest4j.AssertionFailedError: {diagnostic}">{MARKER}</failure></testcase>',
            tests=1,
            failures=1,
        )
        report = self.collect_schedule()
        self.assertEqual(report["summary"]["FAILED"], 1)
        self.assertEqual(report["metadata"]["job"], "java-coverage")
        self.assertEqual(report["metadata"]["testStepScope"], "unit+integration+report")
        self.assertEqual(
            report["cases"][0]["failureDiagnostic"],
            {
                "state": "CLOSED_MARKER",
                "capturedClass": "CannotAcquireLockException",
                "sqlState": "40P01",
                "barrier": "NOT_OBSERVED",
                "stage": "UNKNOWN",
            },
        )
        self.assertEqual(report["xml"][0]["suites"][0]["countsConsistent"], True)

    def test_cap_marker_unknown_values_and_conflicts_remain_unknown_without_leaks(self) -> None:
        self.schedule_fixture()
        good = "CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=TIMED_OUT"
        for message, text in (
            (good.replace("AssertionError", MARKER), ""),
            (good.replace("NONE", MARKER), ""),
            (good.replace("TIMED_OUT", MARKER), ""),
            (good.replace("AssertionError", "NONE"), ""),
            (good + MARKER, ""),
            (MARKER, MARKER),
            (good, good.replace("TIMED_OUT", "INTERRUPTED")),
        ):
            with self.subTest(message_category="synthetic invalid or conflicting"):
                self.xml(
                    f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="{COLLECTOR.CAP_METHOD}()">'
                    f'<failure message="{message}">{text}</failure></testcase>',
                    tests=1,
                    failures=1,
                )
                value = self.collect_schedule()["cases"][0]["failureDiagnostic"]
                self.assertEqual(value, {"state": "ABSENT_OR_UNRECOGNIZED", "stage": "UNKNOWN"})

    def test_barrier_marker_stack_header_fallback_and_other_case_isolation(self) -> None:
        self.schedule_fixture()
        good = "CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=INTERRUPTED"
        self.xml(
            f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="{COLLECTOR.CAP_METHOD}()">'
            f"<failure>java.lang.AssertionError:\n{good}\n{MARKER}</failure></testcase>"
            f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="otherBoundary()">'
            f'<failure message="{good}">{MARKER}</failure></testcase>',
            tests=2,
            failures=2,
        )
        report = self.collect_schedule()
        self.assertEqual(report["cases"][0]["failureDiagnostic"]["stage"], "AWAIT_BARRIER")
        self.assertNotIn("failureDiagnostic", report["cases"][1])

    def test_cap_diagnostic_requires_source_identity_failed_status_and_selected_task(self) -> None:
        self.schedule_fixture()
        good = "CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=TIMED_OUT"
        self.xml(
            f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="{COLLECTOR.CAP_METHOD}()"/>'
            f'<testcase classname="{COLLECTOR.CAP_CLASS}" name="unknownCase()">'
            f'<failure message="{good}"/></testcase>',
            tests=2,
            failures=1,
        )
        self.assertTrue(all("failureDiagnostic" not in row for row in self.collect_schedule()["cases"]))
        for task in ("test", "integrationTest"):
            report = COLLECTOR.collect(self.root, "schedule-service", task, self.environment)
            self.assertEqual(report["metadata"]["job"], "java-coverage")
        self.environment["RCT_CI_JOB_ID"] = MARKER
        with self.assertRaisesRegex(ValueError, "Invalid job scope"):
            self.collect_schedule()

    def test_bounded_xml_read_never_hashes_truncated_carrier(self) -> None:
        self.xml("<testcase/>", tests=1)
        with patch.object(COLLECTOR, "MAX_XML_BYTES", 8):
            report = self.collect()
        self.assertEqual(report["xml"][0]["state"], "UNINSPECTABLE_XML")
        self.assertIsNone(report["xml"][0]["sha256"])

    def test_cap_marker_cutoff_never_attests_a_truncated_original_line(self) -> None:
        good = "CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=TIMED_OUT"
        padded = " " * (2048 - len(good)) + good
        for text in (
            padded + MARKER,
            padded + " " * 50 + MARKER,
            good + " " * (2048 - len(good)) + MARKER,
            "header\n" + " " * (2048 - len(good) - len("header\n")) + good + MARKER,
            padded + MARKER + "\n",
        ):
            with self.subTest(category="truncated or malformed long original line"):
                case = COLLECTOR.ElementTree.Element("testcase")
                failure = COLLECTOR.ElementTree.SubElement(case, "failure")
                failure.text = text
                value = COLLECTOR.cap_failure_diagnostic(case)
                self.assertEqual(value, {"state": "ABSENT_OR_UNRECOGNIZED", "stage": "UNKNOWN"})
                self.assertNotIn(MARKER, json.dumps(value))

    def test_cap_marker_whole_bounded_lines_eof_newline_and_boundary_remain_valid(self) -> None:
        good = "CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=TIMED_OUT"
        padded = " " * (2048 - len(good)) + good
        for text in (
            good,
            good + "\n" + MARKER,
            "java.lang.AssertionError:\n" + good + "\n" + MARKER,
            padded,
            padded + "\n" + MARKER,
            good + "\r\n" + MARKER,
        ):
            with self.subTest(category="whole bounded original line"):
                case = COLLECTOR.ElementTree.Element("testcase")
                failure = COLLECTOR.ElementTree.SubElement(case, "failure")
                failure.text = text
                value = COLLECTOR.cap_failure_diagnostic(case)
                self.assertEqual(value["state"], "CLOSED_MARKER")
                self.assertEqual(value["stage"], "AWAIT_BARRIER")
                self.assertNotIn(MARKER, json.dumps(value))

    def headman_race_fixture(self) -> None:
        source = self.root / COLLECTOR.HEADMAN_RACE_SOURCE
        source.parent.mkdir(parents=True)
        source.write_text(
            "package ru.rutcampustrack.academic.group; class GroupHeadmanAssignmentIT {"
            "@Test void concurrentCandidatesHaveOneWinnerAndOneCasConflict() {} @Test void otherBoundary() {} }"
        )
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["academic-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment["RCT_CI_JOB_ID"] = "java-integration-test"
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"

    def test_headman_race_marker_projects_only_closed_http_category_with_owned_identity(self) -> None:
        self.headman_race_fixture()
        for category in COLLECTOR.HEADMAN_RACE_HTTP_CATEGORIES:
            with self.subTest(category=category):
                marker = "HEADMAN_RACE_HTTP_DIAGNOSTIC_V1 category=" + category
                self.xml(
                    f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="{COLLECTOR.HEADMAN_RACE_METHOD}()">'
                    f'<failure type="java.lang.AssertionError" message="java.lang.AssertionError: {marker}">'
                    f"java.lang.AssertionError: {marker}\n{MARKER}</failure></testcase>",
                    tests=1,
                    failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                case = report["cases"][0]
                self.assertEqual(case["identity"], "SOURCE_METHOD")
                self.assertEqual(case["source"], COLLECTOR.HEADMAN_RACE_SOURCE)
                self.assertEqual(
                    case["sourceSha256"],
                    COLLECTOR.hashlib.sha256((self.root / COLLECTOR.HEADMAN_RACE_SOURCE).read_bytes()).hexdigest(),
                )
                self.assertEqual(case["failureDiagnostic"], {"state": "CLOSED_MARKER", "category": category})
                self.assertEqual(report["summary"]["FAILED"], 1)
                self.assertEqual(report["acceptance"], "NOT_ASSESSED")
                self.assertNotIn(MARKER, json.dumps(report))

    def test_headman_race_marker_rejects_private_near_match_conflicting_and_foreign_carriers(self) -> None:
        self.headman_race_fixture()
        good = "HEADMAN_RACE_HTTP_DIAGNOSTIC_V1 category=HTTP503"
        for message, text, kind in (
            (good.replace("HTTP503", "HTTP500"), "", "java.lang.AssertionError"),
            (good.replace("HTTP503", MARKER), "", "java.lang.AssertionError"),
            (good + MARKER, "", "java.lang.AssertionError"),
            (MARKER + good, "", "java.lang.AssertionError"),
            (good + " " * 256 + MARKER, good + " " * 256 + MARKER, "java.lang.AssertionError"),
            (good, good.replace("HTTP503", "HTTP202"), "java.lang.AssertionError"),
            (good, "", "java.lang.IllegalStateException"),
        ):
            with self.subTest(carrier="malformed or conflicting synthetic marker"):
                self.xml(
                    f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="{COLLECTOR.HEADMAN_RACE_METHOD}()">'
                    f'<failure type="{kind}" message="{message}">{text}</failure></testcase>',
                    tests=1,
                    failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                self.assertEqual(report["cases"][0]["failureDiagnostic"], {"state": "ABSENT_OR_UNRECOGNIZED"})
                self.assertNotIn(MARKER, json.dumps(report))
        failure = f'<failure type="java.lang.AssertionError" message="{good}">{MARKER}</failure>'
        self.xml(
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="{COLLECTOR.HEADMAN_RACE_METHOD}()">{failure}{failure}</testcase>',
            tests=1,
            failures=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertEqual(report["cases"][0]["failureDiagnostic"], {"state": "ABSENT_OR_UNRECOGNIZED"})
        self.assertNotIn(MARKER, json.dumps(report))
        self.xml(
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="otherBoundary()">{failure}</testcase>'
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="unknownBoundary()">{failure}</testcase>'
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}NearMatch" name="{COLLECTOR.HEADMAN_RACE_METHOD}()">{failure}</testcase>'
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="{COLLECTOR.HEADMAN_RACE_METHOD}()"/>',
            tests=4,
            failures=3,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertTrue(all("failureDiagnostic" not in case for case in report["cases"]))
        self.assertNotIn(MARKER, json.dumps(report))
        self.xml(
            f'<testcase classname="{COLLECTOR.HEADMAN_RACE_CLASS}" name="{COLLECTOR.HEADMAN_RACE_METHOD}()">{failure}</testcase>',
            tests=1,
            failures=1,
        )
        unit = self.results.parent / "test"
        unit.mkdir()
        (unit / "TEST-control.xml").write_text((self.results / "TEST-control.xml").read_text())
        report = COLLECTOR.collect(self.root, "academic-service", "test", {**self.environment, "RCT_CI_JOB_ID": "java-unit-test"})
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        self.assertNotIn(MARKER, json.dumps(report))
        source = self.root / COLLECTOR.HEADMAN_RACE_SOURCE
        source.rename(source.with_name("OtherCases.java"))
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True, capture_output=True)
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertEqual(report["cases"][0]["identity"], "SOURCE_METHOD")
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        self.assertNotIn(MARKER, json.dumps(report))

    def notification_fixture(self) -> str:
        package = "ru.rutcampustrack.notification.diagnostic"
        source = self.root / COLLECTOR.MODULES["notification-service"] / "src/test/java/NotificationStartupIT.java"
        source.parent.mkdir(parents=True)
        source.write_text("package " + package + "; class NotificationStartupIT {@Test void contextStarts() {}}")
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["notification-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment["RCT_CI_JOB_ID"] = "java-coverage"
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        return package + ".NotificationStartupIT"

    def test_notification_failure_codes_preserve_named_and_ordinal_identity(self) -> None:
        classname = self.notification_fixture()
        self.xml(
            f'<testcase classname="{classname}" name="contextStarts()">'
            f'<failure type="java.lang.ExceptionInInitializerError" message="{MARKER}">'
            f"java.lang.ExceptionInInitializerError: {MARKER}\n"
            f"Caused by: org.springframework.beans.factory.BeanCreationException: {MARKER}\n"
            f"Caused by: com.mongodb.MongoTimeoutException: {MARKER}</failure></testcase>"
            f'<testcase classname="{classname}" name="{MARKER}">'
            f'<failure type="java.lang.NoClassDefFoundError">{MARKER}</failure></testcase>',
            tests=2,
            failures=2,
        )
        report = COLLECTOR.collect(self.root, "notification-service", "integrationTest", self.environment)
        named, ordinal = report["cases"]
        self.assertEqual(named["identity"], "SOURCE_METHOD")
        self.assertEqual(named["method"], "contextStarts")
        self.assertEqual(ordinal["identity"], "ORDINAL_ONLY")
        self.assertIsNone(ordinal["method"])
        self.assertEqual(named["sourceSha256"], ordinal["sourceSha256"])
        self.assertEqual(
            named["failureDiagnostic"],
            {"state": "CLOSED_CLASSES", "codes": ["INIT", "BEAN", "MONGO"], "overflow": False},
        )
        self.assertEqual(ordinal["failureDiagnostic"]["codes"], ["NO_CLASS"])
        self.assertEqual(report["summary"]["FAILED"], 2)
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        self.assertNotIn(MARKER, json.dumps(report))

    def test_notification_only_exact_anchored_classes_without_messages_frames_or_values(self) -> None:
        case = COLLECTOR.ElementTree.Element("testcase")
        failure = COLLECTOR.ElementTree.SubElement(case, "failure", type=MARKER, message=MARKER)
        failure.text = (
            "java.lang.OutOfMemoryError: " + MARKER + "\n"
            "prefix Caused by: java.lang.OutOfMemoryError: " + MARKER + "\n"
            "    at com.mongodb.MongoTimeoutException.fake(" + MARKER + ")\n"
            "Caused by: com.mongodb.MongoTimeoutExceptionCounterfeit: " + MARKER + "\n"
            "Caused by: com.mongodb.MongoSecurityException: " + MARKER + "\n"
            "\tCaused by: org.springframework.amqp.AmqpConnectException: " + MARKER + "\n"
        )
        value = COLLECTOR.notification_failure_diagnostic(case)
        self.assertEqual(value, {"state": "CLOSED_CLASSES", "codes": ["OTHER", "MONGO", "AMQP"], "overflow": False})
        self.assertNotIn(MARKER, json.dumps(value))

    def test_notification_all_categories_are_finite_and_capped_at_eight(self) -> None:
        case = COLLECTOR.ElementTree.Element("testcase")
        failure = COLLECTOR.ElementTree.SubElement(case, "failure", type="java.lang.AssertionError")
        failure.text = "\n".join(
            "Caused by: " + classes[0] + ": " + MARKER for classes in COLLECTOR.NOTIFICATION_FAILURE_CLASSES.values()
        )
        value = COLLECTOR.notification_failure_diagnostic(case)
        self.assertEqual(len(value["codes"]), 8)
        self.assertTrue(value["overflow"])
        self.assertEqual(value["codes"][0], "ASSERTION")
        self.assertTrue(set(value["codes"]) <= set(COLLECTOR.NOTIFICATION_FAILURE_CLASSES) | {"OTHER"})
        self.assertNotIn(MARKER, json.dumps(value))
        for code, classes in COLLECTOR.NOTIFICATION_FAILURE_CLASSES.items():
            for name in classes:
                with self.subTest(category=code):
                    failure.set("type", name)
                    failure.text = MARKER
                    self.assertEqual(COLLECTOR.notification_failure_diagnostic(case)["codes"], [code])

    def test_notification_diagnostic_scope_never_promotes_unknown_or_nonfailed_cases(self) -> None:
        classname = self.notification_fixture()
        body = (
            f'<testcase classname="{classname}" name="contextStarts()"/>'
            f'<testcase classname="{classname}" name="contextStarts()"><skipped/></testcase>'
            f'<testcase classname="{MARKER}" name="contextStarts()"><failure type="java.lang.OutOfMemoryError"/></testcase>'
        )
        self.xml(body, tests=3, failures=1, skipped=1)
        report = COLLECTOR.collect(self.root, "notification-service", "integrationTest", self.environment)
        self.assertTrue(all("failureDiagnostic" not in case for case in report["cases"]))
        self.assertEqual([case["status"] for case in report["cases"]], ["PASSED", "SKIPPED", "FAILED"])
        self.assertEqual(report["cases"][2]["identity"], "ORDINAL_ONLY")
        self.assertIsNone(report["cases"][2]["classname"])
        unit_dir = self.results.parent / "test"
        unit_dir.mkdir()
        (unit_dir / "TEST-control.xml").write_bytes((self.results / "TEST-control.xml").read_bytes())
        report = COLLECTOR.collect(self.root, "notification-service", "test", self.environment)
        self.assertTrue(all("failureDiagnostic" not in case for case in report["cases"]))
        self.assertNotIn(MARKER, json.dumps(report))

    def test_notification_cutoff_does_not_classify_partial_cause_and_multiple_failures_are_unknown(self) -> None:
        case = COLLECTOR.ElementTree.Element("testcase")
        failure = COLLECTOR.ElementTree.SubElement(case, "failure", type="java.lang.AssertionError")
        failure.text = "Caused by: java.lang.OutOfMemoryError" + " " * COLLECTOR.NOTIFICATION_DIAGNOSTIC_CHARS + MARKER
        value = COLLECTOR.notification_failure_diagnostic(case)
        self.assertEqual(value, {"state": "CLOSED_CLASSES", "codes": ["ASSERTION"], "overflow": True})
        COLLECTOR.ElementTree.SubElement(case, "failure", type="java.lang.OutOfMemoryError")
        self.assertEqual(
            COLLECTOR.notification_failure_diagnostic(case),
            {"state": "NO_SINGLE_FAILURE", "codes": [], "overflow": False},
        )

    def attendance_fixture(self) -> str:
        package = "ru.rutcampustrack.attendance.diagnostic"
        source = self.root / COLLECTOR.MODULES["attendance-service"] / "src/test/java/AttendanceStartupIT.java"
        source.parent.mkdir(parents=True)
        source.write_text("package " + package + "; class AttendanceStartupIT {@Test void contextStarts() {}}")
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["attendance-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment["RCT_CI_JOB_ID"] = "java-coverage"
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        return package + ".AttendanceStartupIT"

    def attendance_case(self, failure_type: str, text: str = MARKER, *, tag: str = "failure", message: str = MARKER):
        case = COLLECTOR.ElementTree.Element("testcase", classname=MARKER, name=MARKER)
        failure = COLLECTOR.ElementTree.SubElement(case, tag, type=failure_type, message=message)
        failure.text = text
        return case

    def test_attendance_unknown_failure_diagnostic_preserves_identity_status_and_acceptance(self) -> None:
        classname = self.attendance_fixture()
        self.xml(
            f'<testcase classname="{MARKER}" name="{MARKER}">'
            f'<failure type="org.gradle.api.internal.tasks.testing.TestSuiteExecutionException" message="{MARKER}">'
            f"Caused by: org.gradle.process.internal.worker.WorkerProcessException: {MARKER}\n"
            f"Caused by: org.gradle.process.internal.ExecException: Process '{MARKER}' finished with non-zero exit value 137"
            '</failure></testcase>'
            f'<testcase classname="{MARKER}" name="{MARKER}"><error type="java.lang.AssertionError"/></testcase>'
            f'<testcase classname="{classname}" name="contextStarts()"><failure type="java.lang.OutOfMemoryError"/></testcase>'
            f'<testcase classname="{classname}" name="{MARKER}"><failure type="java.lang.OutOfMemoryError"/></testcase>'
            f'<testcase classname="{MARKER}" name="{MARKER}"/>'
            f'<testcase classname="{MARKER}" name="{MARKER}"><skipped/></testcase>'
            f'<testcase classname="{MARKER}" name="{MARKER}" status="notrun"/>'
            f'<testcase classname="{MARKER}" name="{MARKER}"><failure/><error/></testcase>',
            tests=8,
            failures=3,
            errors=2,
            skipped=1,
        )
        report = COLLECTOR.collect(self.root, "attendance-service", "integrationTest", self.environment)
        cases = report["cases"]
        self.assertEqual([c["status"] for c in cases], ["FAILED", "ERROR", "FAILED", "FAILED", "PASSED", "SKIPPED", "NOT_EXECUTED", "ERROR"])
        self.assertEqual([c["identity"] for c in cases], ["ORDINAL_ONLY", "ORDINAL_ONLY", "SOURCE_METHOD"] + ["ORDINAL_ONLY"] * 5)
        self.assertEqual(cases[2]["classname"], classname)
        self.assertEqual(cases[2]["method"], "contextStarts")
        self.assertEqual(cases[3]["classname"], classname)
        self.assertIsNone(cases[3]["method"])
        for index in (0, 1, 4, 5, 6, 7):
            self.assertIsNone(cases[index]["classname"])
            self.assertIsNone(cases[index]["method"])
            self.assertNotIn("source", cases[index])
        self.assertEqual(cases[0]["failureDiagnostic"], {
            "kind": "ATTENDANCE_FAILURE_V1", "state": "CLOSED_CLASSES",
            "codes": ["GRADLE_SUITE", "GRADLE_WORKER", "PROCESS_EXEC"], "overflow": False,
            "processExit": {"state": "CLOSED_VALUE", "value": 137},
        })
        self.assertEqual(cases[1]["failureDiagnostic"]["codes"], ["ASSERTION"])
        self.assertEqual(cases[7]["failureDiagnostic"]["state"], "NO_SINGLE_FAILURE")
        self.assertTrue(all("failureDiagnostic" not in c for c in cases[2:7]))
        self.assertEqual(report["summary"], {"PASSED": 1, "FAILED": 3, "ERROR": 2, "SKIPPED": 1, "NOT_EXECUTED": 1})
        self.assertTrue(report["xml"][0]["suites"][0]["countsConsistent"])
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        self.assertNotIn(MARKER, json.dumps(report))

    def test_attendance_diagnostic_scope_excludes_other_jobs_tasks_and_services(self) -> None:
        self.attendance_fixture()
        self.xml(f'<testcase classname="{MARKER}" name="{MARKER}"><failure type="java.lang.OutOfMemoryError"/></testcase>', tests=1, failures=1)
        original = (self.results / "TEST-control.xml").read_bytes()
        for job in ("java-integration-test", None):
            environment = dict(self.environment)
            if job is None:
                environment.pop("RCT_CI_JOB_ID")
            else:
                environment["RCT_CI_JOB_ID"] = job
            report = COLLECTOR.collect(self.root, "attendance-service", "integrationTest", environment)
            self.assertNotIn("failureDiagnostic", report["cases"][0])
        for service, task in (("attendance-service", "test"), ("api-gateway", "integrationTest")):
            directory = self.root / COLLECTOR.MODULES[service] / "build/test-results" / task
            directory.mkdir(parents=True, exist_ok=True)
            (directory / "TEST-control.xml").write_bytes(original)
            report = COLLECTOR.collect(self.root, service, task, self.environment)
            self.assertNotIn("failureDiagnostic", report["cases"][0])
            self.assertEqual(report["cases"][0]["status"], "FAILED")
            self.assertEqual(report["acceptance"], "NOT_ASSESSED")

    def test_attendance_nested_closed_codes_do_not_make_wrapper_a_cause(self) -> None:
        suite = "org.gradle.api.internal.tasks.testing.TestSuiteExecutionException"
        for chain, expected in (
            (("java.lang.AssertionError",), ["GRADLE_SUITE", "ASSERTION"]),
            (("org.springframework.beans.factory.BeanCreationException", "org.springframework.beans.factory.UnsatisfiedDependencyException", "com.mongodb.MongoTimeoutException"), ["GRADLE_SUITE", "CONTEXT", "DEPENDENCY", "MONGO"]),
            (("java.lang.IllegalStateException",), ["GRADLE_SUITE", "ILLEGAL_STATE"]),
        ):
            with self.subTest(codes=expected):
                text = "\n".join("Caused by: " + name + ": " + MARKER for name in chain)
                value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(suite, text))
                self.assertEqual(value["codes"], expected)
                self.assertEqual(set(value), {"kind", "state", "codes", "overflow", "processExit"})
                self.assertEqual(value["processExit"], {"state": "ABSENT_OR_UNRECOGNIZED", "value": None})
                self.assertNotIn(MARKER, json.dumps(value))

    def test_attendance_all_codes_are_finite_exact_unique_and_private(self) -> None:
        text = "\n".join("Caused by: " + names[0] + ": " + MARKER for names in COLLECTOR.ATTENDANCE_FAILURE_CLASSES.values())
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, text + "\n" + text))
        self.assertEqual(value["codes"], ["OTHER"] + list(COLLECTOR.ATTENDANCE_FAILURE_CLASSES))
        self.assertEqual(len(value["codes"]), 15)
        for code, names in COLLECTOR.ATTENDANCE_FAILURE_CLASSES.items():
            for name in names:
                with self.subTest(code=code, name=name):
                    value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(name))
                    self.assertEqual(value["codes"], [code])
                    self.assertNotIn(MARKER, json.dumps(value))
        text = "\n".join([
            "java.lang.OutOfMemoryError: " + MARKER,
            "prefix Caused by: java.lang.OutOfMemoryError: " + MARKER,
            "    at com.mongodb.MongoTimeoutException.fake(" + MARKER + ")",
            "Caused by: com.mongodb.MongoTimeoutExceptionCounterfeit: " + MARKER,
            " " * 33 + "Caused by: java.lang.OutOfMemoryError: " + MARKER,
            "Caused by: " + " " * 8 + "java.lang.OutOfMemoryError: " + MARKER,
            "Caused by: com.mongodb.MongoSecurityException: " + MARKER,
        ])
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, text))
        self.assertEqual(value["codes"], ["OTHER", "MONGO"])
        self.assertNotIn(MARKER, json.dumps(value))

    def test_attendance_native_exit_is_bounded_private_and_never_infers_oom(self) -> None:
        native = "org.gradle.process.internal.ExecException"
        for exit_value in (1, 137, 143, 255):
            message = f"Process '{MARKER}' finished with non-zero exit value {exit_value}"
            for text, attribute, failure_type in (
                (MARKER, message, native),
                (native + ": " + message, MARKER, MARKER),
                ("\tCaused by: " + native + ": " + message + "\r\n", MARKER, MARKER),
            ):
                with self.subTest(exit=exit_value, nested=text != MARKER):
                    value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(failure_type, text, message=attribute))
                    self.assertEqual(value["processExit"], {"state": "CLOSED_VALUE", "value": exit_value})
                    self.assertNotIn("OOM", value["codes"])
                    self.assertNotIn(MARKER, json.dumps(value))
        first = f"Caused by: {native}: Process '{MARKER}' finished with non-zero exit value 137"
        second = first.replace("137", "143")
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, first + "\n" + second))
        self.assertEqual(value["processExit"], {"state": "AMBIGUOUS", "value": None})
        repeated = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, first + "\n" + first))
        self.assertEqual(repeated["processExit"], {"state": "CLOSED_VALUE", "value": 137})

    def test_attendance_exit_lookalikes_invalid_values_and_partial_lines_stay_unknown(self) -> None:
        native = "org.gradle.process.internal.ExecException"
        message = f"Process '{MARKER}' finished with non-zero exit value 137"
        invalid = [
            message.replace("137", value) for value in ("0", "256", "999", "1000", "-1", "0137", "+137")
        ] + ["prefix " + message, message + " extra", message.replace(MARKER, "x" * 513), message.replace(MARKER, "x'y"), message + "x" * 2049]
        for bad in invalid:
            with self.subTest(length=len(bad)):
                for text, attribute in ((MARKER, bad), ("Caused by: " + native + ": " + bad, MARKER)):
                    value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(native, text, message=attribute))
                    self.assertEqual(value["processExit"], {"state": "ABSENT_OR_UNRECOGNIZED", "value": None})
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(native, message=message + "\n"))
        self.assertEqual(value["processExit"], {"state": "ABSENT_OR_UNRECOGNIZED", "value": None})
        for text in ("prefix Caused by: " + native + ": " + message, "Caused by: " + native + "Counterfeit: " + message):
            value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, text, message=message))
            self.assertEqual(value["processExit"], {"state": "ABSENT_OR_UNRECOGNIZED", "value": None})
        prefix = "x\n" * ((COLLECTOR.ATTENDANCE_DIAGNOSTIC_CHARS - 10) // 2)
        text = prefix + "Caused by: " + native + ": " + message
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case(MARKER, text))
        self.assertTrue(value["overflow"])
        self.assertEqual(value["processExit"], {"state": "ABSENT_OR_UNRECOGNIZED", "value": None})

    def test_attendance_cutoff_and_failure_node_ambiguity_remain_closed(self) -> None:
        prefix = "x\n" * ((COLLECTOR.ATTENDANCE_DIAGNOSTIC_CHARS - 10) // 2)
        for text in (
            prefix + "Caused by: java.lang.OutOfMemoryError: " + MARKER,
            "x" * COLLECTOR.ATTENDANCE_DIAGNOSTIC_CHARS + "\nCaused by: java.lang.OutOfMemoryError: " + MARKER,
        ):
            value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case("java.lang.AssertionError", text))
            self.assertEqual(value["codes"], ["ASSERTION"])
            self.assertTrue(value["overflow"])
        text = "Caused by: java.lang.OutOfMemoryError: " + MARKER + "\n" + "x" * COLLECTOR.ATTENDANCE_DIAGNOSTIC_CHARS
        value = COLLECTOR.attendance_failure_diagnostic(self.attendance_case("java.lang.AssertionError", text))
        self.assertEqual(value["codes"], ["ASSERTION", "OOM"])
        self.assertTrue(value["overflow"])
        for tags in ((), ("failure", "failure"), ("error", "error"), ("failure", "error")):
            case = COLLECTOR.ElementTree.Element("testcase")
            for tag in tags:
                COLLECTOR.ElementTree.SubElement(case, tag, type="java.lang.OutOfMemoryError")
            self.assertEqual(COLLECTOR.attendance_failure_diagnostic(case), {
                "kind": "ATTENDANCE_FAILURE_V1", "state": "NO_SINGLE_FAILURE", "codes": [], "overflow": False,
                "processExit": {"state": "ABSENT_OR_UNRECOGNIZED", "value": None},
            })

    def academic_fixture(self, source_path: str | None = None) -> str:
        source = self.root / (source_path or COLLECTOR.ACADEMIC_SCHEMA_SOURCE)
        source.parent.mkdir(parents=True)
        source.write_text(
            "package ru.rutcampustrack.academic.homework; class HomeworkStudentCompletionConcurrencyIT {"
            "@Test void currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp() {} @Test void anotherCase() {}}"
        )
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["academic-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment["RCT_CI_JOB_ID"] = "java-integration-test"
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        return COLLECTOR.ACADEMIC_SCHEMA_CLASS

    def test_academic_missing_table_retains_exact_named_failed_outcome_and_red_summary(self) -> None:
        classname = self.academic_fixture()
        self.xml(
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()">'
            f'<failure type="java.lang.IllegalStateException" message="{MARKER}">'
            f"java.lang.IllegalStateException: {MARKER}\n"
            f"Caused by: {COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION}: Schema-validation: missing table [public.assignments]\n"
            f"    at synthetic.PrivateFrame({MARKER})\n{MARKER}</failure></testcase>",
            tests=1,
            failures=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        case = report["cases"][0]
        self.assertEqual(case["identity"], "SOURCE_METHOD")
        self.assertEqual(case["method"], "currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp")
        self.assertEqual(case["source"], COLLECTOR.ACADEMIC_SCHEMA_SOURCE)
        self.assertEqual(
            case["sourceSha256"],
            COLLECTOR.hashlib.sha256((self.root / COLLECTOR.ACADEMIC_SCHEMA_SOURCE).read_bytes()).hexdigest(),
        )
        self.assertEqual(case["status"], "FAILED")
        self.assertEqual(
            case["failureDiagnostic"],
            {"state": "NATIVE_MISSING_TABLE", "table": "assignments", "qualifier": "PUBLIC"},
        )
        self.assertEqual(report["summary"]["FAILED"], 1)
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        self.assertEqual(report["metadata"]["testStepOutcome"], "failure")
        self.assertNotIn(MARKER, json.dumps(report))

    def test_academic_missing_table_only_emits_closed_table_and_qualifier_values(self) -> None:
        self.assertEqual(len(COLLECTOR.ACADEMIC_SCHEMA_TABLES), 24)
        relations = [(table, table, "UNQUALIFIED") for table in COLLECTOR.ACADEMIC_SCHEMA_TABLES]
        relations += [
            ('public."assignments"', "assignments", "PUBLIC"),
            ('"pg_catalog".auth_identities', "auth_identities", "PG_CATALOG"),
            (MARKER + ".assignments", "assignments", "OTHER"),
            ("public." + MARKER, "OTHER", "PUBLIC"),
            (MARKER, "OTHER", "UNQUALIFIED"),
            ('"ASSIGNMENTS"', "OTHER", "UNQUALIFIED"),
        ]
        for relation, table, qualifier in relations:
            with self.subTest(table=table, qualifier=qualifier):
                case = COLLECTOR.ElementTree.Element("testcase")
                COLLECTOR.ElementTree.SubElement(
                    case,
                    "failure",
                    type=COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION,
                    message="Schema-validation: missing table [" + relation + "]",
                )
                value = COLLECTOR.academic_schema_failure_diagnostic(case)
                self.assertEqual(value, {"state": "NATIVE_MISSING_TABLE", "table": table, "qualifier": qualifier})
                self.assertNotIn(MARKER, json.dumps(value))

    def test_academic_missing_table_rejects_non_native_injection_trailing_and_partial_lines(self) -> None:
        native = COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION + ": Schema-validation: missing table [assignments]"
        texts = [
            native + " " + MARKER,
            native.replace("assignments", "assignments; DROP TABLE " + MARKER),
            native.replace("assignments", "assign\n" + MARKER),
            native.replace("assignments", "public.assignments.extra"),
            native.replace("missing table", "missing column"),
            native.replace("SchemaManagementException", "SchemaManagementExceptionCounterfeit"),
            "prefix " + native,
            "    at " + native,
            "Caused by: " + native + "; " + MARKER,
            "Caused by: " + native[:-1] + " " * COLLECTOR.ACADEMIC_SCHEMA_DIAGNOSTIC_CHARS + "]",
        ]
        for text in texts:
            with self.subTest(variant=texts.index(text)):
                case = COLLECTOR.ElementTree.Element("testcase")
                failure = COLLECTOR.ElementTree.SubElement(case, "failure", type=MARKER, message=MARKER)
                failure.text = text
                value = COLLECTOR.academic_schema_failure_diagnostic(case)
                self.assertEqual(value, {"state": "ABSENT_OR_UNRECOGNIZED", "table": "OTHER", "qualifier": "OTHER"})
                self.assertNotIn(MARKER, json.dumps(value))

    def test_academic_missing_table_conflicts_multiple_failures_and_wrong_type_are_unrecognized(self) -> None:
        case = COLLECTOR.ElementTree.Element("testcase")
        failure = COLLECTOR.ElementTree.SubElement(
            case, "failure", type=MARKER, message="Schema-validation: missing table [assignments]"
        )
        unknown = {"state": "ABSENT_OR_UNRECOGNIZED", "table": "OTHER", "qualifier": "OTHER"}
        self.assertEqual(COLLECTOR.academic_schema_failure_diagnostic(case), unknown)
        failure.set("type", COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION)
        failure.text = (
            "Caused by: " + COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION + ": Schema-validation: missing table [groups]"
        )
        self.assertEqual(COLLECTOR.academic_schema_failure_diagnostic(case), unknown)
        failure.text = ""
        COLLECTOR.ElementTree.SubElement(case, "failure", type=COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION)
        self.assertEqual(COLLECTOR.academic_schema_failure_diagnostic(case), unknown)

    def test_academic_missing_table_scope_excludes_other_methods_ordinal_nonfailed_and_unit_cases(self) -> None:
        classname = self.academic_fixture()
        failure = (
            f'<failure type="{COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION}" '
            'message="Schema-validation: missing table [assignments]"/>'
        )
        self.xml(
            f'<testcase classname="{classname}" name="anotherCase()">{failure}</testcase>'
            f'<testcase classname="{classname}" name="{MARKER}">{failure}</testcase>'
            f'<testcase classname="{MARKER}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()">{failure}</testcase>'
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()"/>'
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()"><skipped/></testcase>'
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()"><error/></testcase>',
            tests=6,
            failures=3,
            skipped=1,
            errors=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertTrue(all("failureDiagnostic" not in case for case in report["cases"]))
        self.assertEqual(
            [case["identity"] for case in report["cases"][:3]], ["SOURCE_METHOD", "ORDINAL_ONLY", "ORDINAL_ONLY"]
        )
        self.assertEqual(report["summary"]["FAILED"], 3)
        unit_dir = self.results.parent / "test"
        unit_dir.mkdir()
        self.results = unit_dir
        self.xml(
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()">{failure}</testcase>',
            tests=1,
            failures=1,
        )
        self.environment["RCT_CI_JOB_ID"] = "java-unit-test"
        report = COLLECTOR.collect(self.root, "academic-service", "test", self.environment)
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        self.assertNotIn(MARKER, json.dumps(report))

    def test_academic_missing_table_same_native_exception_at_other_source_has_no_diagnostic(self) -> None:
        source = COLLECTOR.MODULES["academic-service"] + "/src/test/java/OtherSource.java"
        classname = self.academic_fixture(source)
        self.xml(
            f'<testcase classname="{classname}" name="currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp()">'
            f'<failure type="{COLLECTOR.ACADEMIC_SCHEMA_EXCEPTION}" '
            'message="Schema-validation: missing table [assignments]"/></testcase>',
            tests=1,
            failures=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        case = report["cases"][0]
        self.assertEqual(case["identity"], "SOURCE_METHOD")
        self.assertEqual(case["source"], source)
        self.assertEqual(case["status"], "FAILED")
        self.assertNotIn("failureDiagnostic", case)

    def jdbc_fixture(self, classname: str | None = None, source_path: str | None = None) -> str:
        classname = classname or next(iter(COLLECTOR.ACADEMIC_JDBC_SOURCES))
        source = self.root / (source_path or COLLECTOR.ACADEMIC_JDBC_SOURCES[classname])
        source.parent.mkdir(parents=True, exist_ok=True)
        package, name = classname.rsplit(".", 1)
        source.write_text(
            f"package {package}; class {name} {{ @Test void firstFailure() {{}} @Test void laterFailure() {{}} }}"
        )
        subprocess.run(
            ["git", "add", str(source.relative_to(self.root))], cwd=self.root, check=True, capture_output=True
        )
        self.results = self.root / COLLECTOR.MODULES["academic-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True, exist_ok=True)
        self.environment["RCT_CI_TEST_OUTCOME"] = "failure"
        return classname

    def jdbc_case(self, text: str, native_type: bool = False, message: str = MARKER):
        case = COLLECTOR.ElementTree.Element("testcase")
        failure = COLLECTOR.ElementTree.SubElement(
            case, "failure", type=COLLECTOR.ACADEMIC_JDBC_EXCEPTION if native_type else MARKER, message=message
        )
        failure.text = text
        return case

    def test_academic_jdbc_only_first_source_failed_case_per_owned_class_gets_closed_hint(self) -> None:
        bodies = []
        for classname in COLLECTOR.ACADEMIC_JDBC_SOURCES:
            self.jdbc_fixture(classname)
            for method in ("firstFailure", "laterFailure"):
                bodies.append(
                    f'<testcase classname="{classname}" name="{method}()"><failure message="{MARKER}">Caused by: {COLLECTOR.ACADEMIC_JDBC_EXCEPTION}: FATAL: sorry, too many clients already\n{MARKER}</failure></testcase>'
                )
        self.xml("".join(bodies), tests=8, failures=8)
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertEqual(report["summary"]["FAILED"], 8)
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        for first, later in zip(report["cases"][::2], report["cases"][1::2], strict=True):
            self.assertEqual(first["identity"], "SOURCE_METHOD")
            self.assertEqual(
                first["failureDiagnostic"],
                {
                    "state": "CLOSED_NATIVE_CAUSE",
                    "category": "TOO_MANY_CONNECTIONS",
                    "sqlState": "NOT_OBSERVED",
                    "nativeCausePresent": True,
                    "overflow": False,
                },
            )
            self.assertNotIn("failureDiagnostic", later)
        self.assertNotIn(MARKER, json.dumps(report))

    def test_academic_jdbc_native_sql_states_emit_only_closed_codes_and_categories(self) -> None:
        for state, category in COLLECTOR.ACADEMIC_JDBC_SQL_STATES.items():
            with self.subTest(state=state):
                case = self.jdbc_case(
                    f"SQL State  : {state}\nCaused by: {COLLECTOR.ACADEMIC_JDBC_EXCEPTION}: {MARKER}\n{MARKER}"
                )
                value = COLLECTOR.academic_jdbc_failure_diagnostic(case)
                self.assertEqual(value["category"], category)
                self.assertEqual(value["sqlState"], state)
                self.assertTrue(value["nativeCausePresent"])
                self.assertNotIn(MARKER, json.dumps(value))

    def test_academic_jdbc_native_literal_slots_never_disclose_names_or_endpoints(self) -> None:
        messages = [
            ("AUTHENTICATION_FAILED", f'FATAL: password authentication failed for user "{MARKER}"'),
            ("MISSING_DATABASE", f'FATAL: database "{MARKER}" does not exist'),
            ("MISSING_RELATION", f'ERROR: relation "{MARKER}" does not exist'),
            ("MISSING_COLUMN", f'ERROR: column "{MARKER}" does not exist'),
            (
                "CONNECTION_REFUSED",
                f"Connection to {MARKER} refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.",
            ),
            ("SERVER_UNAVAILABLE", "FATAL: the database system is shutting down"),
        ]
        for category, message in messages:
            with self.subTest(category=category):
                case = self.jdbc_case("", native_type=True, message=message)
                value = COLLECTOR.academic_jdbc_failure_diagnostic(case)
                self.assertEqual(value["category"], category)
                self.assertEqual(value["sqlState"], "NOT_OBSERVED")
                self.assertNotIn(MARKER, json.dumps(value))

    def test_academic_jdbc_unknown_counterfeit_trailing_and_conflicting_causes_stay_unknown(self) -> None:
        native = f"Caused by: {COLLECTOR.ACADEMIC_JDBC_EXCEPTION}: "
        texts = [
            "SQL State  : 53300\n" + MARKER,
            native.replace("PSQLException", "PSQLExceptionCounterfeit") + "FATAL: sorry, too many clients already",
            "prefix " + native + "FATAL: sorry, too many clients already",
            native + "FATAL: sorry, too many clients already " + MARKER,
            native + f'ERROR: relation "{MARKER}" does not exist; DROP TABLE x',
            native + MARKER + "\nSQL State  : 99999",
            native + MARKER + "\nprefix SQL State  : 53300",
            native + MARKER + "\nSQL State  : 53300 " + MARKER,
            native + "FATAL: sorry, too many clients already\nSQL State  : 28P01",
            native + MARKER + "\nSQL State  : 53300\nSQL State  : 08006",
        ]
        for ordinal, text in enumerate(texts):
            with self.subTest(ordinal=ordinal):
                value = COLLECTOR.academic_jdbc_failure_diagnostic(self.jdbc_case(text))
                self.assertEqual(value["state"], "ABSENT_OR_UNRECOGNIZED")
                self.assertEqual(value["category"], "UNKNOWN")
                self.assertNotIn(MARKER, json.dumps(value))

    def test_academic_jdbc_caps_partial_lines_and_multiple_failures(self) -> None:
        limit = COLLECTOR.ACADEMIC_JDBC_DIAGNOSTIC_CHARS
        native = f"Caused by: {COLLECTOR.ACADEMIC_JDBC_EXCEPTION}: FATAL: sorry, too many clients already"
        for text in (MARKER * limit + "\n" + native, " " * (limit - 50) + native):
            value = COLLECTOR.academic_jdbc_failure_diagnostic(self.jdbc_case(text))
            self.assertEqual(value["category"], "UNKNOWN")
            self.assertTrue(value["overflow"])
        case = self.jdbc_case(native)
        COLLECTOR.ElementTree.SubElement(case, "failure", type=COLLECTOR.ACADEMIC_JDBC_EXCEPTION)
        self.assertEqual(COLLECTOR.academic_jdbc_failure_diagnostic(case)["category"], "UNKNOWN")

    def test_academic_jdbc_scope_preserves_ordinal_nonfailed_unit_and_wrong_source(self) -> None:
        classname = self.jdbc_fixture()
        failure = (
            f'<failure type="{COLLECTOR.ACADEMIC_JDBC_EXCEPTION}" message="FATAL: sorry, too many clients already"/>'
        )
        self.xml(
            f'<testcase classname="{classname}" name="{MARKER}">{failure}</testcase>'
            f'<testcase classname="{classname}" name="firstFailure()"/>'
            f'<testcase classname="{classname}" name="firstFailure()"><error/></testcase>',
            tests=3,
            failures=1,
            errors=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertTrue(all("failureDiagnostic" not in row for row in report["cases"]))
        self.assertEqual(report["cases"][0]["identity"], "ORDINAL_ONLY")
        self.results = self.results.parent / "test"
        self.results.mkdir()
        self.xml(f'<testcase classname="{classname}" name="firstFailure()">{failure}</testcase>', tests=1, failures=1)
        report = COLLECTOR.collect(self.root, "academic-service", "test", self.environment)
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        original = self.root / COLLECTOR.ACADEMIC_JDBC_SOURCES[classname]
        original.unlink()
        subprocess.run(
            ["git", "rm", "--cached", str(original.relative_to(self.root))],
            cwd=self.root,
            check=True,
            capture_output=True,
        )
        self.jdbc_fixture(classname, COLLECTOR.MODULES["academic-service"] + "/src/test/java/OtherSource.java")
        self.xml(f'<testcase classname="{classname}" name="firstFailure()">{failure}</testcase>', tests=1, failures=1)
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertEqual(report["cases"][0]["identity"], "SOURCE_METHOD")
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        self.assertNotIn(MARKER, json.dumps(report))

    def bootstrap_cli_failure_fixture(self) -> tuple[str, str]:
        source = self.root / COLLECTOR.BOOTSTRAP_CLI_SOURCE
        source.parent.mkdir(parents=True)
        method = COLLECTOR.BOOTSTRAP_CLI_METHOD
        source.write_text(
            "package ru.rutcampustrack.academic.bootstrap;\n"
            "class InitialAdministratorBootstrapIT {\n"
            "    @Test\n"
            f"    void {method}() {{\n"
            "        runOperatorCli();\n"
            "    }\n"
            "    private static ObjectNode runOperatorCli() {\n"
            "        throw new AssertionError();\n"
            "    }\n"
            "    void freshOwnDatabases() {\n"
            "        configure();\n"
            "    }\n"
            "}\n"
        )
        subprocess.run(["git", "add", COLLECTOR.BOOTSTRAP_CLI_SOURCE], cwd=self.root, check=True, capture_output=True)
        self.results = self.root / COLLECTOR.MODULES["academic-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment.update(
            RCT_CI_JOB_ID="e2e-auth", RCT_BOOTSTRAP_CLI_ACCEPTANCE="true", RCT_CI_TEST_OUTCOME="failure"
        )
        return COLLECTOR.BOOTSTRAP_CLI_CLASS, method

    def test_bootstrap_cli_failure_projects_only_closed_type_and_owned_source_site(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        for failure_type, helper, line, exception, site in (
            ("java.lang.AssertionError", "runOperatorCli", 8, "ASSERTION", "CLI_RESULT"),
            ("org.postgresql.util.PSQLException", "freshOwnDatabases", 11, "JDBC", "SETUP"),
            ("org.opentest4j.AssertionFailedError", method, 5, "ASSERTION", "TEST"),
        ):
            with self.subTest(site=site):
                self.xml(
                    f'<testcase classname="{classname}" name="{method}()"><failure type="{failure_type}" message="{MARKER}">'
                    f"{MARKER}\n\tat foreign.{MARKER}.method({MARKER}.java:73190)\n"
                    f"\tat app//{classname}.{helper}(InitialAdministratorBootstrapIT.java:{line})\n"
                    f"Caused by: private.{MARKER}: {MARKER}\n</failure></testcase>",
                    tests=1,
                    failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                self.assertEqual(
                    report["cases"][0]["failureDiagnostic"],
                    {
                        "state": "PROJECTED",
                        "exception": exception,
                        "site": site,
                        "sourceLine": line,
                    },
                )
                self.assertEqual(report["cases"][0]["status"], "FAILED")
                self.assertEqual(report["cases"][0]["identity"], "SOURCE_METHOD")
                self.assertEqual(report["summary"]["FAILED"], 1)
                self.assertEqual(report["acceptance"], "NOT_ASSESSED")
                self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_result_marker_projects_closed_invocation_guard_and_exit_only(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        for invocation, guard, expected, actual in (
            ("MISSING_CONFIG", "UNEXPECTED_EXIT", 2, 0),
            ("FRESH", "UNEXPECTED_EXIT", 0, 255),
            ("REPEAT", "UNEXPECTED_EXIT", 0, "OTHER"),
            ("FRESH", "OVERSIZED_STDOUT", 0, 0),
            ("REPEAT", "NONEMPTY_STDERR", 0, 0),
            ("MISSING_CONFIG", "SECRET_MARKER", 2, 2),
        ):
            with self.subTest(invocation=invocation, guard=guard):
                marker = (
                    f"BOOTSTRAP_CLI_RESULT_V1 invocation={invocation} guard={guard} "
                    f"expectedExit={expected} actualExit={actual}"
                )
                for message, first_line in (
                    (marker, ""),
                    ("", "java.lang.AssertionError: " + marker),
                    (marker, "java.lang.AssertionError: " + marker),
                ):
                    self.xml(
                        f'<testcase classname="{classname}" name="{method}()">'
                        f'<failure type="java.lang.AssertionError" message="{message}">{first_line}\n'
                        f"\tat {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:8)\n"
                        f"{MARKER}</failure></testcase>", tests=1, failures=1,
                    )
                    report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                    self.assertEqual(report["cases"][0]["failureDiagnostic"], {
                        "state": "CLOSED_MARKER", "exception": "ASSERTION", "site": "CLI_RESULT", "sourceLine": 8,
                        "invocation": invocation, "guard": guard, "expectedExit": expected, "actualExit": actual,
                    })
                    self.assertEqual(report["cases"][0]["status"], "FAILED")
                    self.assertEqual(report["acceptance"], "NOT_ASSESSED")
                    self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_result_marker_rejects_malformed_conflicting_and_unbound_claims(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        marker = "BOOTSTRAP_CLI_RESULT_V1 invocation=FRESH guard=UNEXPECTED_EXIT expectedExit=0 actualExit=2"
        frame = f"at {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:8)"
        for message, first_line, owned_frame, name, failure_type in (
            (marker, MARKER, frame, method, "java.lang.AssertionError"),
            (MARKER, "java.lang.AssertionError: " + marker, frame, method, "java.lang.AssertionError"),
            (marker + MARKER, marker + MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("FRESH", MARKER), MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("actualExit=2", "actualExit=256"), MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("actualExit=2", "actualExit=-1"), MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("actualExit=2", "actualExit=02"), MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("expectedExit=0", "expectedExit=2"), MARKER, frame, method, "java.lang.AssertionError"),
            (marker.replace("UNEXPECTED_EXIT", "SECRET_MARKER"), MARKER, frame, method, "java.lang.AssertionError"),
            (marker, marker.replace("FRESH", "REPEAT"), frame, method, "java.lang.AssertionError"),
            (marker, MARKER, frame.replace(":8)", ":5)"), method, "java.lang.AssertionError"),
            (marker, MARKER, frame.replace(classname, "foreign." + MARKER), method, "java.lang.AssertionError"),
            (marker, MARKER, frame, "runOperatorCli", "java.lang.AssertionError"),
            (marker, MARKER, frame, method, "java.io.IOException"),
        ):
            with self.subTest(message=message, name=name, failure_type=failure_type):
                self.xml(
                    f'<testcase classname="{classname}" name="{name}()">'
                    f'<failure type="{failure_type}" message="{message}">{first_line}\n'
                    f"{owned_frame}\n{MARKER}</failure></testcase>", tests=1, failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                diagnostic = report["cases"][0].get("failureDiagnostic", {})
                self.assertNotEqual(diagnostic.get("state"), "CLOSED_MARKER")
                self.assertNotIn("invocation", diagnostic)
                self.assertNotIn("actualExit", diagnostic)
                self.assertEqual(report["acceptance"], "NOT_ASSESSED")
                self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_failure_rejects_foreign_or_unbound_source_lines(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        for frame in (
            f"at foreign.{MARKER}.runOperatorCli(InitialAdministratorBootstrapIT.java:8)",
            f"at {classname}.runOperatorCli({MARKER}.java:8)",
            f"at {classname}.{MARKER}(InitialAdministratorBootstrapIT.java:8)",
            f"at {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:99999)",
            f"at {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:5)",
            f"at {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:8) {MARKER}",
            # First owned frame is unknown: do not skip it to manufacture a recognized site.
            (
                f"at {classname}.{MARKER}(InitialAdministratorBootstrapIT.java:8)\n"
                f"at {classname}.runOperatorCli(InitialAdministratorBootstrapIT.java:8)"
            ),
        ):
            with self.subTest(frame=frame):
                self.xml(
                    f'<testcase classname="{classname}" name="{method}()"><failure type="java.lang.AssertionError" message="{MARKER}">'
                    f"{frame}\n{MARKER}</failure></testcase>",
                    tests=1,
                    failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                self.assertEqual(
                    report["cases"][0]["failureDiagnostic"],
                    {
                        "state": "PROJECTED",
                        "exception": "ASSERTION",
                        "site": "UNKNOWN",
                        "sourceLine": None,
                    },
                )
                self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_failure_unknown_and_oversize_never_copy_native_text(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        for body, state in (
            (MARKER, "ABSENT_OR_UNRECOGNIZED"),
            (MARKER + "x" * COLLECTOR.BOOTSTRAP_CLI_DIAGNOSTIC_CHARS, "OVERFLOW"),
        ):
            with self.subTest(state=state):
                self.xml(
                    f'<testcase classname="{classname}" name="{method}()"><failure type="private.{MARKER}" message="{MARKER}">'
                    f"{body}</failure></testcase>",
                    tests=1,
                    failures=1,
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                self.assertEqual(
                    report["cases"][0]["failureDiagnostic"],
                    {
                        "state": state,
                        "exception": "UNKNOWN",
                        "site": "UNKNOWN",
                        "sourceLine": None,
                    },
                )
                self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_failure_diagnostic_does_not_expand_case_or_job_admission(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        failure = f'<failure type="java.lang.AssertionError">{MARKER}</failure>'
        for name, outcome, job in (
            (method + "()", "", "e2e-auth"),
            (method + "()", f'<skipped message="{MARKER}"/>', "e2e-auth"),
            ("runOperatorCli()", failure, "e2e-auth"),
            (MARKER, failure, "e2e-auth"),
            (method + "()", failure, "java-integration-test"),
        ):
            with self.subTest(name=name, job=job):
                self.environment["RCT_CI_JOB_ID"] = job
                self.xml(
                    f'<testcase classname="{classname}" name="{name}">{outcome}</testcase>',
                    tests=1,
                    failures=int("failure" in outcome),
                    skipped=int("skipped" in outcome),
                )
                report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
                self.assertNotIn("failureDiagnostic", report["cases"][0])
                self.assertEqual(report["acceptance"], "NOT_ASSESSED")
                self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_failure_wrong_source_path_has_no_diagnostic(self) -> None:
        classname, method = self.bootstrap_cli_failure_fixture()
        source = self.root / COLLECTOR.BOOTSTRAP_CLI_SOURCE
        renamed = source.with_name("OtherCases.java")
        source.rename(renamed)
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True, capture_output=True)
        self.xml(
            f'<testcase classname="{classname}" name="{method}()"><failure type="java.lang.AssertionError">{MARKER}</failure></testcase>',
            tests=1,
            failures=1,
        )
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
        self.assertEqual(report["cases"][0]["identity"], "SOURCE_METHOD")
        self.assertNotIn("failureDiagnostic", report["cases"][0])
        self.assertNotIn(MARKER, json.dumps(report))

    def test_bootstrap_cli_truthful_job_requires_explicit_exact_scope_flag(self) -> None:
        for service, task, flag in (
            ("academic-service", "integrationTest", None),
            ("academic-service", "integrationTest", "false"),
            ("academic-service", "integrationTest", "TRUE"),
            ("academic-service", "integrationTest", MARKER),
            ("academic-service", "test", "true"),
            ("auth-service", "integrationTest", "true"),
            ("schedule-service", "integrationTest", "true"),
        ):
            environment = {**self.environment, "RCT_CI_JOB_ID": "e2e-auth"}
            if flag is not None:
                environment["RCT_BOOTSTRAP_CLI_ACCEPTANCE"] = flag
            with (
                self.subTest(service=service, task=task, flag=flag),
                self.assertRaisesRegex(ValueError, "Invalid job scope"),
            ):
                COLLECTOR.metadata(self.root, service, task, environment)

    def test_bootstrap_cli_main_collects_real_source_method_without_auto_acceptance(self) -> None:
        source_path = "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/bootstrap/InitialAdministratorBootstrapIT.java"
        classname = "ru.rutcampustrack.academic.bootstrap.InitialAdministratorBootstrapIT"
        method = "operatorCliLoadsPrivateConfigAndCommitsFirstAdministrator"
        source = self.root / source_path
        source.parent.mkdir(parents=True)
        source.write_text(
            f"package ru.rutcampustrack.academic.bootstrap; class InitialAdministratorBootstrapIT {{ @Test void {method}() {{}} }}"
        )
        subprocess.run(["git", "add", source_path], cwd=self.root, check=True, capture_output=True)
        self.results = self.root / COLLECTOR.MODULES["academic-service"] / "build/test-results/integrationTest"
        self.results.mkdir(parents=True)
        self.environment.update(RCT_CI_JOB_ID="e2e-auth", RCT_BOOTSTRAP_CLI_ACCEPTANCE="true")
        self.xml(f'<testcase classname="{classname}" name="{method}()"/>', tests=1)
        script = self.root / "scripts/collect-ci-test-outcomes.py"
        script.parent.mkdir()
        script.write_bytes(SCRIPT.read_bytes())
        environment = {**self.environment, "PATH": os.environ.get("PATH", os.defpath), "RCT_PRIVATE_CONTROL": MARKER}
        result = subprocess.run(
            [sys.executable, str(script), "--service", "academic-service", "--task", "integrationTest"],
            env=environment,
            capture_output=True,
            text=True,
            check=True,
        )
        output = self.root / "build/ci-test-outcomes/academic-service/integrationTest/safe-outcomes.json"
        report = json.loads(output.read_text())
        self.assertEqual(json.loads(result.stdout), {"collection": "RESULTS", "acceptance": "NOT_ASSESSED"})
        self.assertEqual(report["metadata"]["job"], "e2e-auth")
        self.assertEqual(report["acceptance"], "NOT_ASSESSED")
        self.assertEqual(report["collection"], "RESULTS")
        self.assertEqual(report["summary"], {"PASSED": 1, "FAILED": 0, "ERROR": 0, "SKIPPED": 0, "NOT_EXECUTED": 0})
        self.assertEqual(len(report["cases"]), 1)
        case = report["cases"][0]
        self.assertEqual(
            (case["classname"], case["method"], case["source"], case["identity"]),
            (classname, method, source_path, "SOURCE_METHOD"),
        )
        self.assertEqual(case["sourceSha256"], COLLECTOR.hashlib.sha256(source.read_bytes()).hexdigest())
        self.assertNotIn(MARKER, json.dumps(report))
        self.assertNotIn("RCT_BOOTSTRAP_CLI_ACCEPTANCE", json.dumps(report))
        self.assertNotIn(MARKER, result.stdout + result.stderr)
        for service, task, flag in (
            ("academic-service", "integrationTest", None),
            ("academic-service", "integrationTest", "false"),
            ("academic-service", "test", "true"),
            ("auth-service", "integrationTest", "true"),
        ):
            with self.subTest(service=service, task=task, flag=flag):
                rejected_environment = {
                    key: value for key, value in environment.items() if key != "RCT_BOOTSTRAP_CLI_ACCEPTANCE"
                }
                if flag is not None:
                    rejected_environment["RCT_BOOTSTRAP_CLI_ACCEPTANCE"] = flag
                rejected = subprocess.run(
                    [sys.executable, str(script), "--service", service, "--task", task],
                    env=rejected_environment,
                    capture_output=True,
                    text=True,
                    check=True,
                )
                artifact = self.root / "build/ci-test-outcomes" / service / task / "safe-outcomes.json"
                expected = {"schemaVersion": 1, "collection": "COLLECTION_ERROR", "acceptance": "NOT_ASSESSED"}
                self.assertEqual(json.loads(artifact.read_text()), expected)
                self.assertEqual(
                    json.loads(rejected.stdout), {"collection": "COLLECTION_ERROR", "acceptance": "NOT_ASSESSED"}
                )
                self.assertNotIn(MARKER, rejected.stdout + rejected.stderr)


if __name__ == "__main__":
    unittest.main()
