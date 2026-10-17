"""Actual collector controls with temporary tracked Java/XML fixtures and no JVM or services."""

import importlib.util
import json
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


if __name__ == "__main__":
    unittest.main()
