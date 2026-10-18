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

    def test_bootstrap_cli_collects_real_source_method_without_auto_acceptance(self) -> None:
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
        report = COLLECTOR.collect(self.root, "academic-service", "integrationTest", self.environment)
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


if __name__ == "__main__":
    unittest.main()
