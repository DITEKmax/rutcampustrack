"""Offline controls for the existing inline HTTP500 carrier; no CI/runtime main executes."""

import argparse
import ast
import copy
import hashlib
import json
import os
import re
import stat
import tempfile
import textwrap
import time
import unittest
from pathlib import Path
from unittest.mock import patch

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--workflow", type=Path)
parser.add_argument("--source-root", type=Path)
parser.add_argument("--fixture-root", type=Path, help="Read-only sparse-worktree fixture overlay")
parser.add_argument("--hosted-spec", type=Path, help="Read-only frozen hosted source before integration")
parser.add_argument("--baseline-red", action="store_true")
parser.add_argument("--homework-compat-red", action="store_true")
args = parser.parse_args()
source_root = (args.source_root or Path(__file__).resolve().parents[3]).resolve()
fixture_root = (args.fixture_root or source_root / "tests/e2e/fixtures").resolve()
hosted_spec = args.hosted_spec or source_root / "tests/e2e/specs/mobile-real-server.spec.ts"
workflow = args.workflow or source_root / ".github/workflows/ci.yml"
carrier = workflow.read_text()
start = carrier.index("          import hashlib, json, os, re, selectors, stat, subprocess, time")
end = carrier.index("          PY_HTTP500_DIAGNOSTIC", start)
tree = ast.parse(textwrap.dedent(carrier[start:end]))
definitions = []
for node in tree.body:
    if isinstance(node, ast.Assign) and any(
        isinstance(target, ast.Name) and target.id == "result" for target in node.targets
    ):
        break
    if isinstance(node, (ast.Assign, ast.FunctionDef)):
        definitions.append(node)


def source_path(value):
    path = Path(value)
    if path.as_posix() in {"tests/e2e/fixtures/mobile-dataset.ts", "tests/e2e/fixtures/semester-lifecycle.spec.ts"}:
        return fixture_root / path.name
    return path


source_path.cwd = Path.cwd
namespace = {"Path": source_path, "hashlib": hashlib, "json": json, "os": os, "re": re, "stat": stat, "time": time}
# The trusted repository AST contains definitions only; the runtime block is excluded above.
exec(compile(ast.Module(body=definitions, type_ignores=[]), "<HTTP500 definitions only>", "exec"), namespace)  # noqa: S102
assert "result" not in namespace and "collect" in namespace
os.chdir(source_root)
config = namespace["STAGES"]["MARK500"]
hosted_config = namespace["STAGES"]["HOSTED_BOT_MARK500"]
request_id = "00000000-0000-4000-8000-000000000001"
other_id = "00000000-0000-4000-8000-000000000002"
private = "SYNTHETIC_PRIVATE_MARKER_NOT_FOR_OUTPUT"


def current_receipt():
    # Same closed signature as artifact12233356110; no request identity or response data.
    return {
        "status": "failed",
        "runnerErrors": 0,
        "unsafeArtifact": False,
        "suppressedStdoutBytes": 0,
        "suppressedStderrBytes": 0,
        "results": [
            {
                "file": "semester-lifecycle.spec.ts",
                "line": 1657,
                "project": "chromium",
                "status": "failed",
                "expectedStatus": "passed",
                "durationMs": 57714,
                "errorCount": 1,
                "diagnostics": [
                    {"stage": "DATASET_HTTP", "cause": "DATASET_ATTENDANCE_MARK_HTTP", "httpStatus": 500},
                    {"stage": "UNKNOWN", "cause": "REPORTED_CLASS_ERROR", "httpStatus": 0},
                    {"stage": "UNKNOWN", "cause": "REPORTED_FRAME_DATASET_CALL", "httpStatus": 0},
                ],
                "errorLocations": [{"file": "semester-lifecycle.spec.ts", "line": 1883}],
            }
        ],
    }


if args.baseline_red:
    signature = namespace["is_target_failure"](current_receipt(), config)
    try:
        namespace["verify_http_sources"](config)
        sources = True
    except ValueError:
        sources = False
    print(
        json.dumps(
            {
                "status": "GREEN" if sources and signature else "RED",
                "currentSourceBindingsAccepted": sources,
                "currentSignatureAccepted": signature,
            }
        )
    )
    raise SystemExit(0 if sources and signature else 1)


def reconnect_observation():
    return {
        "offlineSeen": True,
        "onlineSeen": True,
        "online": True,
        "observationUnknown": False,
        "native": {
            epoch: {endpoint: {"phase": "PENDING", "multiple": False} for endpoint in ("refresh", "current")}
            for epoch in ("preOnline", "postOnline")
        },
        "pageHttp": {
            endpoint: {"requested": True, "responded": False, "failed": False} for endpoint in ("refresh", "current")
        },
        "priorPending": {"refresh": True, "current": False},
        "refreshHttpStatus": "UNOBSERVED",
    }


def homework_receipt(extension=False):
    value = current_receipt()
    homework = value["results"][0]
    homework.update(
        file="mobile-real-server.spec.ts",
        line=123,
        diagnostics=[{"stage": "DATASET_HTTP", "cause": "DATASET_HOMEWORK_CREATE_HTTP", "httpStatus": 503}],
        errorLocations=[{"file": "mobile-real-server.spec.ts", "line": 123}],
    )
    reconnect = copy.deepcopy(homework)
    reconnect.update(
        line=3959,
        diagnostics=[{"stage": "UNKNOWN", "cause": "REPORTED_CLASS_ERROR", "httpStatus": 0}],
        errorLocations=[{"file": "mobile-real-server.spec.ts", "line": 3959}],
    )
    if extension:
        reconnect["d002ReconnectObservation"] = reconnect_observation()
    value["results"].append(reconnect)
    return value


if args.homework_compat_red:
    homework_config = namespace["STAGES"]["HOMEWORK_CREATE503"]
    base = namespace["is_target_failure"](homework_receipt(), homework_config)
    extension = namespace["is_target_failure"](homework_receipt(extension=True), homework_config)
    print(
        json.dumps(
            {
                "status": "GREEN" if base and extension else "RED",
                "legacyTwoFailedRowsAccepted": base,
                "closedD002ExtensionAccepted": extension,
            }
        )
    )
    raise SystemExit(0 if base and extension else 1)


def error_record(stack, *, identity=request_id, gateway=False):
    return json.dumps(
        {
            "level": "ERROR",
            "logger": namespace["GATEWAY_LOGGER"]
            if gateway
            else "ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler",
            "service": "api-gateway" if gateway else "attendance-service",
            "requestId": identity,
            "msg": private,
            "stack": stack,
        }
    ).encode()


controller_frame = "\tat ru.rutcampustrack.attendance.marking.MarkingController.mark(MarkingController.java:44)\n"
body_frame = (
    "\tat ru.rutcampustrack.attendance.event.SemesterArchiveFence.lock"
    "(SemesterArchiveFence.java:54)\n"
    "\tat ru.rutcampustrack.attendance.student.PairWriteCoordinator.lockSemesterForWrite"
    "(PairWriteCoordinator.java:51)\n"
)


def mongo_stack(code=112, name="WriteConflict", labels=None, site="BODY"):
    response = {
        "code": code,
        "codeName": name,
        "errmsg": private,
        "errorLabels": ["TransientTransactionError"] if labels is None else labels,
    }
    frame = (
        body_frame
        if site == "BODY"
        else (
            "\tat org.springframework.data.mongodb.MongoTransactionManager.do"
            + site.title()
            + "(MongoTransactionManager.java:300)\n"
        )
    )
    return (
        "org.springframework.data.mongodb.UncategorizedMongoDbException: "
        + private
        + "\n"
        + frame
        + controller_frame
        + "Caused by: com.mongodb.MongoCommandException: Command failed with error "
        + str(code)
        + " ("
        + name
        + "): '"
        + private
        + "' on server synthetic:27017. The full response is "
        + json.dumps(response)
        + "\n\tat com.mongodb.internal.Synthetic.call(Synthetic.java:1)\n"
    )


def classify(payload, captured=None, identity=request_id):
    return namespace["classify"](payload, config, captured, identity)


def complete(payload):
    domain, gateway = classify(payload), classify(b"", "api-gateway")
    value = namespace["combine"](domain, gateway, config)
    payloads = {"attendance-service": payload, "api-gateway": b""}
    value["reportedAmbiguityEvidence"] = namespace["ambiguity_evidence"](payloads, config, domain, gateway, request_id)
    value["reportedMongo"] = namespace["hosted_mongo"](payloads, config, value, request_id)
    return namespace["finalize_ordinary_mark_result"](value)


class MarkDiagnosticContract(unittest.TestCase):
    def test_homework_legacy_and_exact_d002_extension_admission(self):
        homework_config = namespace["STAGES"]["HOMEWORK_CREATE503"]
        self.assertTrue(namespace["is_target_failure"](homework_receipt(), homework_config))
        self.assertTrue(namespace["is_target_failure"](homework_receipt(extension=True), homework_config))
        for field, wrong in (
            ("line", 3958),
            ("file", "other.spec.ts"),
            ("project", "firefox"),
            ("status", "passed"),
            ("expectedStatus", "failed"),
            ("errorCount", 0),
        ):
            with self.subTest(field=field):
                value = homework_receipt(extension=True)
                value["results"][1][field] = wrong
                self.assertFalse(namespace["is_target_failure"](value, homework_config))
        for extra in ("unknownField", "nativePrimaryFailures"):
            for extension in (False, True):
                value = homework_receipt(extension=extension)
                value["results"][1][extra] = []
                self.assertFalse(namespace["is_target_failure"](value, homework_config))

    def test_homework_previous_base_and_target_guards_remain(self):
        homework_config = namespace["STAGES"]["HOMEWORK_CREATE503"]
        for field, wrong in (
            ("durationMs", -1),
            ("durationMs", 1800001),
            ("errorCount", 9),
            ("line", True),
            ("errorLocations", [{"file": "other.ts", "line": 1}]),
            ("diagnostics", [{"stage": "private text", "cause": "X", "httpStatus": 0}]),
        ):
            for index in (0, 1):
                with self.subTest(field=field, index=index):
                    value = homework_receipt(extension=True)
                    value["results"][index][field] = wrong
                    self.assertFalse(namespace["is_target_failure"](value, homework_config))
        for wrong in (500, 200):
            value = homework_receipt(extension=True)
            value["results"][0]["diagnostics"][0]["httpStatus"] = wrong
            self.assertFalse(namespace["is_target_failure"](value, homework_config))
        value = homework_receipt(extension=True)
        value["results"][0]["status"] = "passed"
        self.assertFalse(namespace["is_target_failure"](value, homework_config))
        for field, wrong in (("status", "passed"), ("runnerErrors", 1), ("unsafeArtifact", True)):
            value = homework_receipt(extension=True)
            value[field] = wrong
            self.assertFalse(namespace["is_target_failure"](value, homework_config))

    def test_d002_observation_closed_flags_phases_status_and_size(self):
        closed = namespace["closed_d002_reconnect_observation"]
        for phase in ("NOT_CALLED", "PENDING", "FULFILLED", "REJECTED"):
            for status in ("UNOBSERVED", "OTHER", 200, 401, 403, 429, 503):
                value = reconnect_observation()
                value["native"]["postOnline"]["current"]["phase"] = phase
                value["refreshHttpStatus"] = status
                self.assertTrue(closed(value))
        changes = [
            (("native", "preOnline", "refresh", "phase"), private),
            (("native", "postOnline", "current", "phase"), "x" * 2049),
            (("native", "postOnline", "refresh", "multiple"), 1),
            (("pageHttp", "current", "responded"), 0),
            (("priorPending", "refresh"), "true"),
            (("offlineSeen",), True.__int__()),
            (("refreshHttpStatus",), 200.0),
            (("refreshHttpStatus",), True),
            (("native", "preOnline"), []),
            (("pageHttp",), None),
        ]
        for path, wrong in changes:
            with self.subTest(path=path):
                value = reconnect_observation()
                target = value
                for key in path[:-1]:
                    target = target[key]
                target[path[-1]] = wrong
                self.assertFalse(closed(value))
                receipt = homework_receipt(extension=True)
                receipt["results"][1]["d002ReconnectObservation"] = value
                self.assertFalse(namespace["is_homework_failure"](receipt))
        for path in (
            (),
            ("native",),
            ("native", "preOnline"),
            ("native", "postOnline", "refresh"),
            ("pageHttp", "current"),
            ("priorPending",),
        ):
            value = reconnect_observation()
            target = value
            for key in path:
                target = target[key]
            target["unknownField"] = private
            self.assertFalse(closed(value))
        self.assertFalse(closed({}))
        self.assertFalse(closed([]))
        self.assertFalse(closed(None))

    def test_d002_schema_source_bindings_fail_before_runtime(self):
        namespace["verify_d002_reconnect_sources"]()
        sources = namespace["D002_RECONNECT_SOURCES"]
        for source in sources:
            with self.subTest(source=source), patch.dict(sources, {source: "0" * 64}), self.assertRaises(ValueError):
                namespace["verify_d002_reconnect_sources"]()
        lines = hosted_spec.read_text().splitlines()
        self.assertIn(
            "D002 reconnect rechecks changed grants and a late old feed cannot replace the new account", lines[3958]
        )

    def test_existing_privacy_suite_is_selected_only_after_client_install(self):
        install = carrier.index("      - name: Install Playwright + browsers")
        check = carrier.index("      - name: Validate the existing safe artifact privacy contract for client scenarios")
        trust = carrier.index("      - name: Trust the disposable localhost certificate for Chromium workers")
        self.assertLess(install, check)
        self.assertLess(check, trust)
        block = carrier[check:trust]
        self.assertIn("if: ${{ matrix.group == 'client' }}", block)
        self.assertIn("working-directory: tests/e2e", block)
        self.assertEqual(carrier.count("npm run test:artifact-privacy"), 1)
        self.assertIn("if npm run test:artifact-privacy; then privacy_status=0; else privacy_status=$?; fi", block)
        self.assertIn("raise SystemExit(int(sys.argv[1]))", block)
        self.assertIn("umask 077", block)
        self.assertIn("0 < info.st_size <= 4096", block)
        self.assertIn("RCT_E2E_CONTROL_RECEIPT_PATH:", block)
        self.assertIn("steps.artifact-privacy-controls.outputs.safe_receipt == 'true'", block)
        upload = block.split("      - name: Upload only the closed existing privacy control receipt", 1)[1]
        self.assertIn(
            "path: ${{ runner.temp }}/rct-artifact-privacy-client-${{ github.run_id }}-${{ github.run_attempt }}.safe.json",
            upload,
        )
        self.assertNotIn("*", upload)
        self.assertIn(
            "npm ci\n          node check-admission-retry.mjs\n          npx playwright install --with-deps chromium webkit",
            carrier[install:check],
        )

    def test_existing_privacy_receipt_is_closed_without_running_its_suite(self):
        check = carrier.index("      - name: Validate the existing safe artifact privacy contract for client scenarios")
        start = carrier.index("          import json, os, re, stat, sys", check)
        end = carrier.index("          PY_PRIVACY_RECEIPT", start)
        tree = ast.parse(textwrap.dedent(carrier[start:end]))
        functions = [node for node in tree.body if isinstance(node, ast.FunctionDef)]
        self.assertEqual([node.name for node in functions], ["receipt_pairs", "closed_privacy_receipt"])
        scope = {}
        exec(compile(ast.Module(body=functions, type_ignores=[]), "<privacy receipt validator only>", "exec"), scope)  # noqa: S102
        closed = scope["closed_privacy_receipt"]
        boundary = {
            "stage": "AUTOMATIC_CONTEXT_BEFORE_ATTACHMENT",
            "markerAbsent": True,
            "failureRetained": True,
            "attachmentNotYetCreated": True,
            "interrupted": False,
        }
        value = {
            "state": "PRIVACY_BOUNDARIES_PASSED",
            "normalChildStatus": 0,
            "markerAbsent": True,
            "producerBoundary": boundary,
            "originalMetadataLeakReproduced": True,
            "expectedFailureRetained": True,
            "successRetained": True,
            "interruptedChildStatus": 1,
            "interruptedChildSignal": None,
            "interruptionBoundary": dict(boundary, interrupted=True),
            "interruptionPassed": True,
            "missingGuardRejectedBeforeOperation": True,
            "browser": "NOT_STARTED",
            "guardControl": {
                key: True
                for key in (
                    "canonicalReporterAccepted",
                    "sameBasenameAlternateRejected",
                    "extraReporterRejected",
                    "traceRejected",
                    "videoRejected",
                    "screenshotRejected",
                    "missingSnapshotGuardRejected",
                    "wrongSnapshotGuardRejected",
                )
            },
        }
        self.assertTrue(closed(value))
        failed = dict(value, state="PRIVACY_CONTROL_FAILED", producerBoundary=None, interruptionBoundary=None)
        del failed["guardControl"]
        self.assertTrue(closed(failed))
        self.assertTrue(
            closed({"state": "CONTROL_EXECUTION_ERROR", "rawErrorSuppressed": True, "browser": "NOT_STARTED"})
        )
        for field, wrong in (
            ("state", private),
            ("browser", private),
            ("normalChildStatus", 256),
            ("interruptedChildStatus", True),
            ("markerAbsent", 1),
            ("interruptedChildSignal", private),
            ("producerBoundary", dict(boundary, raw=private)),
        ):
            self.assertFalse(closed(dict(value, **{field: wrong})))
        self.assertFalse(closed(dict(value, rawOutput=private)))
        with self.assertRaises(ValueError):
            json.loads(
                '{"state":"' + private + '","state":"CONTROL_EXECUTION_ERROR"}',
                object_pairs_hook=scope["receipt_pairs"],
            )
        self.assertLessEqual(len(json.dumps(value).encode()), 4096)

    def test_current_signature_and_bound_sources(self):
        self.assertTrue(namespace["is_target_failure"](current_receipt(), config))
        namespace["verify_http_sources"](config)
        self.assertEqual(namespace["frame_sources"](hosted_config), namespace["HOSTED_FRAME_SOURCES"])
        self.assertNotIn("ATTENDANCE_REPORT_SERVICE", namespace["frame_sources"](config))

    def test_signature_negative_controls(self):
        for field, wrong in (
            ("line", 1592),
            ("file", "mobile-real-server.spec.ts"),
            ("project", "firefox"),
            ("status", "passed"),
        ):
            with self.subTest(field=field):
                value = current_receipt()
                value["results"][0][field] = wrong
                self.assertFalse(namespace["is_target_failure"](value, config))
        for line in (1818, 1884):
            value = current_receipt()
            value["results"][0]["errorLocations"][0]["line"] = line
            self.assertFalse(namespace["is_target_failure"](value, config))
        value = current_receipt()
        value["results"][0]["diagnostics"][0]["httpStatus"] = 409
        self.assertFalse(namespace["is_target_failure"](value, config))
        self.assertFalse(namespace["is_target_failure"](current_receipt(), hosted_config))

    def test_hosted_admission_remains_disjoint(self):
        value = current_receipt()
        row = value["results"][0]
        row.update(
            file="mobile-real-server.spec.ts",
            line=5948,
            errorLocations=[{"file": "mobile-real-server.spec.ts", "line": 6069}],
            diagnostics=[{"stage": "DATASET_HTTP", "cause": "DATASET_ATTENDANCE_MARK_HTTP", "httpStatus": 500}],
        )
        self.assertTrue(namespace["is_target_failure"](value, hosted_config))
        self.assertFalse(namespace["is_target_failure"](value, config))
        self.assertEqual(namespace["exception_classes"](hosted_config), namespace["HOSTED_CLASSES"])
        for line in (1883, 5915, 6065, 6147):
            row["errorLocations"][0]["line"] = line
            self.assertFalse(namespace["is_target_failure"](value, hosted_config))
        self.assertEqual(
            hashlib.sha256(hosted_spec.read_bytes()).hexdigest(),
            namespace["HOSTED_MARK_SOURCES"]["tests/e2e/specs/mobile-real-server.spec.ts"],
        )
        hosted_lines = hosted_spec.read_text().splitlines()
        self.assertIn(
            "test('actual private bot menu roster and teacher Today use hosted current authority'", hosted_lines[5947]
        )
        self.assertEqual(hosted_lines[6068].strip(), "await dataset.headmanActor.call(")
        witness = (fixture_root / "mobile-dataset.ts").read_text()
        self.assertIn("info.line !== 5948 ||", witness)
        self.assertNotIn("info.line !== 5794 ||", witness)
        self.assertEqual(
            hashlib.sha256(witness.encode()).hexdigest(),
            namespace["HOSTED_MARK_SOURCES"]["tests/e2e/fixtures/mobile-dataset.ts"],
        )

    def test_changed_source_fails_closed(self):
        sources = namespace["ORDINARY_MARK_FRAME_SOURCES"]
        for symbol in sources:
            with self.subTest(symbol=symbol), patch.dict(sources):
                entry = sources[symbol]
                sources[symbol] = (*entry[:3], "0" * 64, entry[4])
                with self.assertRaises(ValueError):
                    namespace["verify_http_sources"](config)
        fixture_sources = namespace["ORDINARY_MARK_SOURCES"]
        for source in fixture_sources:
            with (
                self.subTest(source=source),
                patch.dict(fixture_sources, {source: "0" * 64}),
                self.assertRaises(ValueError),
            ):
                namespace["verify_http_sources"](config)

    def test_mongo_chain_frame_code_labels_and_sites(self):
        for site in ("BODY", "BEGIN", "COMMIT", "ROLLBACK"):
            with self.subTest(site=site):
                result = complete(error_record(mongo_stack(site=site)))
                self.assertEqual(result["state"], "UNIQUE")
                self.assertEqual(result["reportedOrigin"], "ATTENDANCE_MVC")
                self.assertEqual(result["reportedExceptionChain"], ["MONGO_TRANSACTION", "MONGO"])
                self.assertEqual(
                    result["reportedOwnFrame"],
                    {
                        "symbol": "ATTENDANCE_SEMESTER_ARCHIVE_FENCE",
                        "line": 54,
                    }
                    if site == "BODY"
                    else {"symbol": "ATTENDANCE_MARKING_CONTROLLER", "line": 44},
                )
                self.assertEqual(
                    result["reportedMongo"],
                    {
                        "state": "REPORTED",
                        "code": "WRITE_CONFLICT",
                        "site": site,
                        "serverLabels": ["TRANSIENT_TRANSACTION_ERROR"],
                    },
                )
                self.assertTrue(namespace["closed_ordinary_mark_result"](result))
                serialized = json.dumps(result)
                for forbidden in (private, request_id, "synthetic:27017", "errmsg", "stack", "requestId"):
                    self.assertNotIn(forbidden, serialized)

    def test_first_request_correlation(self):
        payload = error_record(mongo_stack(), identity=other_id)
        self.assertEqual(classify(payload)["state"], "UNAVAILABLE_NO_ERROR_RECORD")
        self.assertEqual(classify(payload, identity=None)["state"], "UNAVAILABLE_PRIVATE_INPUT")
        mixed = payload + b"\n" + error_record(mongo_stack())
        self.assertEqual(complete(mixed)["reportedMongo"]["code"], "WRITE_CONFLICT")

    def test_ambiguity_and_multiple_origins(self):
        payload = error_record(mongo_stack())
        double = payload + b"\n" + payload
        self.assertEqual(complete(double)["state"], "AMBIGUOUS")
        self.assertEqual(complete(double)["reportedMongo"]["state"], "AMBIGUOUS")
        gateway = error_record(
            "java.lang.IllegalStateException: " + private + "\n"
            "\tat ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter.filter"
            "(InternalJwtIssuerFilter.java:200)\n",
            gateway=True,
        )
        self.assertEqual(
            namespace["combine"](classify(payload), classify(gateway, "api-gateway"), config)["state"], "AMBIGUOUS"
        )

    def test_unknown_classes_and_driver_fields(self):
        value = complete(error_record("example.UnknownException: " + private + "\n" + controller_frame))
        self.assertEqual(value["exceptionCategory"], "UNKNOWN")
        self.assertEqual(value["reportedExceptionClassChain"], ["UNKNOWN"])
        self.assertEqual(value["reportedMongo"]["state"], "UNKNOWN")
        for code, name, labels, expected in (
            (999, "OtherCode", [], "OTHER"),
            (112, "WrongName", [], "UNKNOWN"),
            (112, "WriteConflict", [private], "UNKNOWN"),
        ):
            value = complete(error_record(mongo_stack(code, name, labels)))
            self.assertEqual(value["reportedMongo"]["code"], expected)
            self.assertNotIn(private, json.dumps(value))

    def test_malformed_and_capture_bounds(self):
        for payload, state in (
            (b"{", "UNAVAILABLE_NO_ERROR_RECORD"),
            (b"[]", "UNAVAILABLE_NO_ERROR_RECORD"),
            (b"x" * 131073, "UNAVAILABLE_LOG_LIMIT"),
            (b"\n".join([b"{}"] * 201), "UNAVAILABLE_LOG_LIMIT"),
            (b"x" * (namespace["LIMIT"] + 1), "UNAVAILABLE_LOG_LIMIT"),
            (error_record(""), "UNAVAILABLE_EXCEPTION_STACK"),
            (error_record("x" * 131073), "UNAVAILABLE_LOG_LIMIT"),
        ):
            with self.subTest(size=len(payload), expected=state):
                self.assertEqual(classify(payload)["state"], state)
        malformed = complete(b"{\n" + error_record(mongo_stack()))
        self.assertEqual(malformed["state"], "REPORTED_PARTIAL_LOG_FORMAT")
        self.assertEqual(malformed["reportedMongo"]["state"], "UNKNOWN")

    def test_duplicate_json_and_suppressed_stacks(self):
        record = error_record(mongo_stack())
        duplicated = record.replace(b'"level": "ERROR"', b'"level":"ERROR","level":"ERROR"')
        self.assertEqual(classify(duplicated)["state"], "UNAVAILABLE_NO_ERROR_RECORD")
        suppressed = error_record(mongo_stack() + "\tSuppressed: java.lang.IllegalStateException: hidden\n")
        self.assertEqual(classify(suppressed)["state"], "AMBIGUOUS")

    def test_artifact_second_allowlist(self):
        value = complete(error_record(mongo_stack()))
        self.assertTrue(namespace["closed_ordinary_mark_result"](value))
        for key, wrong in (
            ("state", private),
            ("sourceStage", private),
            ("exceptionCategory", private),
            ("reportedOrigin", private),
            ("reportedExceptionChain", [private]),
            ("reportedExceptionClassChain", [private]),
            ("reportedOwnFrame", {"symbol": private, "line": 1}),
            ("reportedMongo", {"state": "REPORTED", "code": private, "site": "BODY", "serverLabels": []}),
        ):
            with self.subTest(key=key):
                changed = dict(value, **{key: wrong})
                self.assertFalse(namespace["closed_ordinary_mark_result"](changed))
        self.assertFalse(namespace["closed_ordinary_mark_result"](dict(value, requestId=request_id)))
        self.assertFalse(namespace["closed_ordinary_mark_result"](dict(value, reportedExceptionChain=["MONGO"] * 17)))
        self.assertFalse(
            namespace["closed_ordinary_mark_result"](
                dict(
                    value,
                    reportedAmbiguityEvidence=[
                        {
                            "capturedOrigin": "ATTENDANCE_MVC",
                            "reasons": [private],
                            "exceptionClasses": [],
                            "ownFrames": [],
                        }
                    ],
                )
            )
        )

    def test_whole_output_schema_and_4096_byte_cap(self):
        value = complete(error_record(mongo_stack()))
        self.assertEqual(value["schema"], "attendance-mark-http500.v1")
        classes = namespace["HOSTED_CLASSES"]
        longest = max(classes, key=len)
        value["reportedAmbiguityEvidence"] = [
            {
                "capturedOrigin": origin,
                "reasons": sorted(namespace["AMBIGUITY_REASONS"]),
                "exceptionClasses": [longest] * len(classes),
                "ownFrames": [],
            }
            for origin in ("ATTENDANCE_MVC", "GATEWAY_WEBFLUX")
        ]
        self.assertTrue(namespace["closed_ordinary_mark_result"](value))
        self.assertGreater(len(json.dumps(value).encode()), 4096)
        result = namespace["finalize_ordinary_mark_result"](value)
        self.assertEqual(result["state"], "UNAVAILABLE_LOG_LIMIT")
        self.assertLessEqual(len((json.dumps(result, sort_keys=True) + "\n").encode()), 4096)
        result = namespace["finalize_ordinary_mark_result"](dict(value, requestId=request_id))
        self.assertEqual(result["state"], "UNAVAILABLE_LOG_FORMAT")
        self.assertNotIn(request_id, json.dumps(result))

    def test_private_persistence_and_witness_refusals(self):
        with tempfile.TemporaryDirectory(prefix="rct-mark-contract-") as directory:
            temporary = Path(directory)
            work = temporary / "rct-semester-lifecycle-1-1"
            work.mkdir(mode=0o700)
            (work / "artifacts").mkdir(mode=0o700)
            marker = work / "disposable-target"
            marker.write_bytes(b"RCT_SEMESTER_DISPOSABLE\n")
            marker.chmod(0o600)
            witness = work / "mark-http500.request-id"
            witness.write_text(request_id + "\n")
            witness.chmod(0o600)
            environment = {
                "GITHUB_ACTIONS": "true",
                "RUNNER_ENVIRONMENT": "github-hosted",
                "RCT_E2E_SYNTHETIC_SEED": "true",
                "GITHUB_RUN_ID": "1",
                "GITHUB_RUN_ATTEMPT": "1",
                "RUNNER_TEMP": directory,
                "RCT_SEMESTER_LIFECYCLE_RUN": str(work),
            }
            with patch.dict(os.environ, environment):
                value = complete(error_record(mongo_stack()))
                self.assertEqual(namespace["load_mark_request_id"](config), request_id)
                for invalid in ("", private, "AAAAAAAA-0000-4000-8000-000000000002"):
                    witness.write_text(invalid + "\n")
                    with self.assertRaises(ValueError):
                        namespace["load_mark_request_id"](config)
                witness.write_text(request_id + "\n")
                witness.chmod(0o644)
                with self.assertRaises(ValueError):
                    namespace["load_mark_request_id"](config)
                witness.chmod(0o600)
                with patch("os.getuid", return_value=os.getuid() + 1):
                    with self.assertRaises(ValueError):
                        namespace["load_mark_request_id"](config)
                    with self.assertRaises(ValueError):
                        namespace["persist_ordinary_mark_result"](config, value)
                for variable, wrong in (
                    ("GITHUB_RUN_ID", "2"),
                    ("GITHUB_RUN_ATTEMPT", "2"),
                    ("RCT_E2E_SYNTHETIC_SEED", "false"),
                    ("RUNNER_ENVIRONMENT", "self-hosted"),
                ):
                    with patch.dict(os.environ, {variable: wrong}), self.assertRaises(ValueError):
                        namespace["persist_ordinary_mark_result"](config, value)
                work.chmod(0o755)
                with self.assertRaises(ValueError):
                    namespace["persist_ordinary_mark_result"](config, value)
                work.chmod(0o700)
                artifact_directory = work / "artifacts"
                artifact_directory.rmdir()
                artifact_directory.symlink_to(temporary, target_is_directory=True)
                with self.assertRaises(ValueError):
                    namespace["persist_ordinary_mark_result"](config, value)
                artifact_directory.unlink()
                artifact_directory.mkdir(mode=0o700)
                marker.write_text(private)
                with self.assertRaises(ValueError):
                    namespace["persist_ordinary_mark_result"](config, value)
                marker.write_bytes(b"RCT_SEMESTER_DISPOSABLE\n")
                with self.assertRaises(ValueError):
                    namespace["persist_ordinary_mark_result"](hosted_config, value)
                namespace["persist_ordinary_mark_result"](config, value)
                output = work / "mark-http500.safe.json"
                self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
                self.assertEqual(json.loads(output.read_text()), value)
                self.assertLessEqual(output.stat().st_size, 4096)
                self.assertNotIn(request_id, output.read_text())
                with self.assertRaises(FileExistsError):
                    namespace["persist_ordinary_mark_result"](config, value)
                output.unlink()
                output.symlink_to(witness)
                with self.assertRaises(FileExistsError):
                    namespace["persist_ordinary_mark_result"](config, value)
                self.assertEqual(witness.read_text(), request_id + "\n")
                witness.unlink()
                witness.symlink_to(marker)
                with self.assertRaises(OSError):
                    namespace["load_mark_request_id"](config)

    def test_exact_artifact_upload_and_unchanged_capture_caps(self):
        upload = carrier.split("      - name: Upload closed semester lifecycle outcome\n", 1)[1].split(
            "      - name:", 1
        )[0]
        self.assertIn("${{ env.RCT_SEMESTER_LIFECYCLE_RUN }}/mark-http500.safe.json", upload)
        self.assertNotIn("request-id", upload)
        self.assertNotIn("*", upload)
        self.assertIn("time.monotonic() + 15, LIMIT", carrier)
        self.assertIn("'--since', started_at if initial_membership else '180s'", carrier)
        self.assertIn("'--tail', '2000' if initial_membership else '200'", carrier)
        preflight = carrier.index(
            "      - name: Validate ordinary MARK diagnostic sources and privacy before runtime startup"
        )
        self.assertLess(preflight, carrier.index("      - name: Setup Gradle", carrier.index("  e2e-auth:")))
        self.assertIn("run: python3 -B tests/e2e/fixtures/http500-diagnostic-contract.py", carrier)


suite = unittest.defaultTestLoader.loadTestsFromTestCase(MarkDiagnosticContract)
result = unittest.TextTestRunner(verbosity=1).run(suite)
print(
    json.dumps(
        {
            "status": "GREEN" if result.wasSuccessful() else "RED",
            "controls": result.testsRun,
            "failures": len(result.failures),
            "errors": len(result.errors),
            "runtimeMainExecuted": False,
            "networkOrContainerCalls": 0,
        }
    )
)
raise SystemExit(0 if result.wasSuccessful() else 1)
