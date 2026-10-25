"""Finite offline controls for shared CI/Ubuntu diagnostics; no service/runtime lane."""
import argparse
import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path)
parser.add_argument('--producer-report', type=Path, help='Actual Node-only producer report; validate explicitly projected rows')
args = parser.parse_args()
source_root = (args.source_root or Path(__file__).resolve().parents[3]).resolve()
module_path = source_root / 'tests/e2e/fixtures/http500_diagnostic.py'
spec = importlib.util.spec_from_file_location('shared_http500_diagnostic', module_path)
diag = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = diag
spec.loader.exec_module(diag)
namespace = vars(diag)
config = diag.STAGES['MARK500']
hosted_config = diag.STAGES['HOSTED_BOT_MARK500']
request_id = '00000000-0000-4000-8000-000000000001'
other_id = '00000000-0000-4000-8000-000000000002'
private = 'SYNTHETIC_PRIVATE_MARKER_NOT_FOR_OUTPUT'


def event(case, code='DATASET_ATTENDANCE_MARK_HTTP', status=500, stage='DATASET_HTTP'):
    return {'schema': diag.EVENT_SCHEMA, 'caseId': case, 'stageId': stage, 'eventCode': code, 'httpStatus': status}


def row(case='SEMESTER_ARCHIVE_RESTORE', code='DATASET_ATTENDANCE_MARK_HTTP', status=500, failed=True):
    return {'caseId': case, 'diagnosticEvents': [event(case, code, status)] if failed else [],
            'project': 'chromium', 'status': 'failed' if failed else 'passed', 'expectedStatus': 'passed',
            'durationMs': 1, 'errorCount': 1 if failed else 0, 'diagnostics': [],
            'file': 'relocated-case.spec.ts', 'line': 321,
            'errorLocations': [{'file': 'relocated-case.spec.ts', 'line': 399}] if failed else []}


def receipt(rows=None, failed=True):
    return {'status': 'failed' if failed else 'passed', 'runnerErrors': 0, 'unsafeArtifact': False,
            'suppressedStdoutBytes': 0, 'suppressedStderrBytes': 0, 'results': [row()] if rows is None else rows}


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


class SharedDiagnosticControls(unittest.TestCase):
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
                    "GITHUB_WORKSPACE": str(source_root),
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

    def test_structured_identity_and_optional_coordinates(self):
        value = receipt()
        self.assertTrue(diag.is_target_failure(value, config))
        shifted = copy.deepcopy(value)
        shifted['results'][0].update(file='renamed-scenario.spec.ts', line=900000,
                                     errorLocations=[{'file': 'moved-fixture.ts', 'line': 800000}])
        self.assertTrue(diag.is_target_failure(shifted, config))
        for key in ('file', 'line', 'errorLocations'):
            shifted['results'][0].pop(key)
        self.assertTrue(diag.is_target_failure(shifted, config))
        for target, field, wrong in [
            ('row', 'caseId', 'FORGED_CASE'), ('row', 'project', 'firefox'),
            ('row', 'status', 'passed'), ('row', 'errorCount', 0), ('row', 'raw', private),
            ('top', 'unsafeArtifact', True), ('top', 'suppressedStderrBytes', 1), ('top', 'raw', private),
            ('top', 'diagnosticScope', 'LOCAL_CLIENT_FIRST_FAILURE'),
            ('event', 'schema', 'rct-diagnostic-event.v99'), ('event', 'stageId', 'UNKNOWN'),
            ('event', 'eventCode', 'DATASET_LESSON_CREATE_HTTP'), ('event', 'httpStatus', True),
            ('event', 'httpStatus', 503), ('event', 'private', private),
        ]:
            with self.subTest(target=target, field=field):
                changed = copy.deepcopy(value)
                slot = changed if target == 'top' else changed['results'][0]
                if target == 'event': slot = slot['diagnosticEvents'][0]
                slot[field] = wrong
                self.assertFalse(diag.is_target_failure(changed, config))
        foreign = receipt([row('HOSTED_PRIVATE_BOT_AUTHORITY')])
        self.assertTrue(diag.is_target_failure(foreign, hosted_config))
        self.assertFalse(diag.is_target_failure(foreign, config))
        duplicated = receipt()
        duplicated['results'][0]['diagnosticEvents'] *= 2
        self.assertFalse(diag.is_target_failure(duplicated, config))
        # A human/legacy diagnostic cannot replace the authenticated machine event.
        legacy = receipt()
        legacy['results'][0]['diagnosticEvents'] = [event('SEMESTER_ARCHIVE_RESTORE', 'REPORTED_FRAMEWORK_ERROR', 0, 'FRAMEWORK')]
        legacy['results'][0]['diagnostics'] = [{'stage': 'DATASET_HTTP', 'cause': 'DATASET_ATTENDANCE_MARK_HTTP', 'httpStatus': 500}]
        self.assertFalse(diag.is_target_failure(legacy, config))

    def test_stats_companions_d002_and_async_identity(self):
        rows = [row(case, 'STUDENT_STATS_HTTP503', 503, failed=case == 'HEADMAN_RECURRING_SLOT_HISTORY')
                for case in sorted(diag.STATS_CASES)]
        self.assertTrue(diag.is_target_failure(receipt(rows), diag.STAGES['STUDENT_STATS503']))
        duplicate = copy.deepcopy(rows);duplicate[0] = copy.deepcopy(duplicate[1])
        self.assertFalse(diag.is_target_failure(receipt(duplicate), diag.STAGES['STUDENT_STATS503']))
        homework = row('LIMITED_ADMIN_ACCOUNTS', 'DATASET_HOMEWORK_CREATE_HTTP', 503)
        scoped = receipt([homework]);scoped['diagnosticScope'] = 'LOCAL_CLIENT_FIRST_FAILURE'
        self.assertTrue(diag.is_target_failure(scoped, diag.STAGES['HOMEWORK_CREATE503']))
        scoped['results'].append(copy.deepcopy(homework))
        self.assertFalse(diag.is_target_failure(scoped, diag.STAGES['HOMEWORK_CREATE503']))
        reconnect = row('D002_RECONNECT_ACCOUNT_AUTHORITY', 'REPORTED_FRAMEWORK_ERROR', 0)
        reconnect['diagnosticEvents'] = [event(reconnect['caseId'], 'REPORTED_FRAMEWORK_ERROR', 0, 'FRAMEWORK'),
                                         event(reconnect['caseId'], 'OBSERVATION', 0, 'D002_RECONNECT')]
        reconnect['d002ReconnectObservation'] = reconnect_observation()
        self.assertTrue(diag.is_target_failure(receipt([homework, reconnect]), diag.STAGES['HOMEWORK_CREATE503']))
        reconnect['d002ReconnectObservation']['raw'] = private
        self.assertFalse(diag.is_target_failure(receipt([homework, reconnect]), diag.STAGES['HOMEWORK_CREATE503']))
        good = receipt([row('ASYNC_DOCX_RESUME', failed=False)], failed=False)
        self.assertTrue(diag.is_async_success(good))
        good['results'][0]['caseId'] = 'LIMITED_ADMIN_ACCOUNTS'
        self.assertFalse(diag.is_async_success(good))

    def test_shared_cli_parity_relocation_and_fresh_source_manifest(self):
        with tempfile.TemporaryDirectory(prefix='rct diagnostic relocated ') as directory:
            relocated = Path(directory) / 'fresh checkout'
            fixtures = relocated / 'tests/e2e/fixtures';fixtures.mkdir(parents=True)
            copied = fixtures / module_path.name
            original = module_path.read_bytes()
            copied.write_bytes(original)
            shutil.copyfile(module_path.with_name('http-diagnostic-cases.json'), fixtures / 'http-diagnostic-cases.json')
            payload = relocated / 'input.safe.json';payload.write_text(json.dumps(receipt()));payload.chmod(0o600)
            command = [sys.executable, '-B', str(copied), '--stage', 'MARK500', '--root', str(relocated), '--validate-report', str(payload)]
            before = subprocess.run(command, cwd=directory, capture_output=True, text=True, timeout=10)
            self.assertEqual(before.returncode, 0, before.stdout)
            self.assertEqual(json.loads(before.stdout)['accepted'], diag.is_target_failure(receipt(), config))
            # New checkout/new observation, not a copied owner.json or old runtime manifest.
            self.assertFalse((relocated / 'owner.json').exists())
            def fresh_manifest():
                name = 'relocated_diagnostic_control'
                relocated_spec = importlib.util.spec_from_file_location(name, copied)
                relocated_module = importlib.util.module_from_spec(relocated_spec)
                sys.modules[name] = relocated_module
                relocated_spec.loader.exec_module(relocated_module)
                self.assertEqual(relocated_module.ROOT, relocated)
                return relocated_module.source_manifest()
            first_manifest = fresh_manifest()
            try:
                copied.write_bytes(b'# Comment-only control moves every implementation line.\n\n' + original)
                next_manifest = fresh_manifest()
                self.assertNotEqual(first_manifest, next_manifest)
                moved = subprocess.run(command, cwd=directory, capture_output=True, text=True, timeout=10)
                self.assertEqual(moved.returncode, 0, moved.stdout)
                self.assertEqual(json.loads(moved.stdout), json.loads(before.stdout))
            finally:
                copied.write_bytes(original)
            self.assertEqual(fresh_manifest(), first_manifest)
            changed = receipt();changed['results'][0]['diagnosticEvents'][0]['httpStatus'] = True
            payload.write_text(json.dumps(changed))
            rejected = subprocess.run(command, cwd=directory, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(rejected.returncode, 0)
            self.assertFalse(json.loads(rejected.stdout)['accepted'])
            unknown = subprocess.run([sys.executable, '-B', str(copied), '--stage', private], cwd=directory, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(unknown.returncode, 0)
            self.assertNotIn(private, unknown.stdout + unknown.stderr)

    @unittest.skipUnless(args.producer_report, 'Actual producer report supplied for boundary check')
    def test_actual_producer_report_unit_projections_not_whole_ci_report(self):
        actual = diag.decode(args.producer_report.read_bytes())
        stage = diag.STAGES['HOMEWORK_CREATE503']
        self.assertFalse(diag.is_target_failure(actual, stage))
        counts = {'DATASET_HTTP': 0, 'FRAMEWORK': 0}
        with tempfile.TemporaryDirectory(prefix='rct producer consumer boundary ') as directory:
            payload = Path(directory) / 'projection.safe.json'
            for original in actual['results']:
                if 'caseId' not in original:
                    continue
                projected = copy.deepcopy(original)
                projected['project'] = 'chromium'  # Node-only test runner, not CI context.
                value = receipt([projected])  # Explicit unit top-level context; original events unchanged.
                kind = original['diagnosticEvents'][0]['stageId']
                counts[kind] += 1
                expected = kind == 'DATASET_HTTP'
                self.assertEqual(diag.is_target_failure(value, stage), expected)
                self.assertEqual(projected['diagnosticEvents'], original['diagnosticEvents'])
                payload.write_text(json.dumps(value));payload.chmod(0o600)
                child = subprocess.run([sys.executable, '-B', str(module_path), '--stage', 'HOMEWORK_CREATE503',
                                        '--validate-report', str(payload)], capture_output=True, text=True, timeout=10)
                self.assertEqual(child.returncode, 0 if expected else 1)
                self.assertEqual(json.loads(child.stdout)['accepted'], expected)
                mismatch = copy.deepcopy(value)
                mismatch['results'][0]['diagnosticEvents'][0]['caseId'] = 'HOSTED_PRIVATE_BOT_AUTHORITY'
                self.assertFalse(diag.is_target_failure(mismatch, stage))
        self.assertEqual(counts, {'DATASET_HTTP': 2, 'FRAMEWORK': 4})

    def test_local_diagnostic_terminates_own_group_on_cancel_and_timeout(self):
        local_spec = importlib.util.spec_from_file_location('local_diagnostic_control', source_root / 'scripts/local_e2e_async.py')
        local = importlib.util.module_from_spec(local_spec);local_spec.loader.exec_module(local)
        from types import SimpleNamespace
        for reason in ('cancel', 'timeout'):
            with self.subTest(reason=reason), tempfile.TemporaryDirectory(prefix='rct local diagnostic control ') as directory:
                handlers, killed = {}, []
                def handler(kind, callback):
                    previous = handlers.get(kind, 'previous')
                    handlers[kind] = callback
                    return previous
                child = SimpleNamespace(pid=12345, poll=lambda: None,
                                        wait=lambda **_: (_ for _ in ()).throw(subprocess.TimeoutExpired('synthetic', 5)))
                def spawn(*_, **kwargs):
                    self.assertTrue(kwargs['start_new_session'])
                    if reason == 'cancel':handlers[local.signal.SIGTERM]()
                    return child
                times = iter((0, 31))
                # SIGKILL completes the synthetic child; no real process or Docker command is invoked.
                def kill(pid, kind):
                    killed.append((pid, kind))
                    if kind == local.signal.SIGKILL:child.wait = lambda **_: 0
                runner = SimpleNamespace(mode='client', run=Path(directory), env={})
                expected = KeyboardInterrupt if reason == 'cancel' else subprocess.TimeoutExpired
                with patch.object(local.signal, 'signal', side_effect=handler), patch.object(local.subprocess, 'Popen', side_effect=spawn), patch.object(local.time, 'monotonic', side_effect=lambda: next(times)), patch.object(local.os, 'killpg', side_effect=kill):
                    with self.assertRaises(expected):local.Runner.diagnose_http_failure(runner)
                self.assertEqual(killed, [(12345, local.signal.SIGTERM), (12345, local.signal.SIGKILL)])
                self.assertEqual(set(handlers.values()), {'previous'})

    def test_local_primary_nonzero_survives_diagnostic_failure_and_cleanup(self):
        local_spec = importlib.util.spec_from_file_location('local_primary_control', source_root / 'scripts/local_e2e_async.py')
        local = importlib.util.module_from_spec(local_spec);local_spec.loader.exec_module(local)
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory(prefix='rct local primary control ') as directory:
            run = Path(directory);run.chmod(0o700)
            calls, writes = [], {}
            original = subprocess.CalledProcessError(17, ['synthetic-child'])
            def fail():raise original
            def diagnostic():
                calls.append('diagnostic')
                raise subprocess.TimeoutExpired('synthetic-diagnostic', 30)
            runner = SimpleNamespace(run=run, phase='CLIENT', prepare=lambda: None, execute=fail,
                                     diagnose_http_failure=diagnostic, preserve_logs=lambda: calls.append('logs'),
                                     cleanup=lambda: calls.append('cleanup'))
            previous_umask = os.umask(0o077)
            with patch.object(local, 'RUNS', run), patch.object(local, 'preflight'), patch.object(local, 'Runner', return_value=runner), patch.object(local, 'emit'), patch.object(local, 'write', side_effect=lambda path, data: writes.update({path.name: json.loads(data)})), patch.object(sys, 'argv', ['local_e2e_async.py', 'client']):
                try:
                    with self.assertRaises(subprocess.CalledProcessError) as caught:local.main()
                    self.assertIs(caught.exception, original)
                finally:
                    os.umask(previous_umask)
            self.assertEqual(calls, ['diagnostic', 'logs', 'cleanup'])
            self.assertEqual(writes['result.safe.json']['child_returncode'], 17)
            self.assertEqual(writes['result.safe.json']['result'], 'FAIL')
            self.assertEqual(writes['result.safe.json']['cleanup'], 'PASS')

    def test_shared_privacy_receipt_duplicate_fields_and_schema(self):
        value = {'state': 'CONTROL_EXECUTION_ERROR', 'rawErrorSuppressed': True, 'browser': 'NOT_STARTED'}
        self.assertTrue(diag.closed_privacy_receipt(value))
        self.assertFalse(diag.closed_privacy_receipt(dict(value, raw=private)))
        with self.assertRaises(ValueError):diag.decode('{"caseId":"A","caseId":"B"}')
        with self.assertRaises(ValueError):diag.decode('{"status":NaN}')

    def test_capture_rechecks_exact_project_service_nonce_before_and_after(self):
        stage = diag.STAGES['STUDENT_STATS503']
        cid = 'a' * 64
        class ClosedPipeChild:
            def __init__(self, payload):
                reader, writer = os.pipe()
                with os.fdopen(writer, 'wb') as target:target.write(payload)
                self.stdout = os.fdopen(reader, 'rb')
            def __enter__(self):return self
            def __exit__(self, *_):self.stdout.close()
            def wait(self, **_):return 0
            def poll(self):return 0
        for mode in ('ci', 'local'):
            for mutation in (None, 'project', 'service', 'running', 'nonce'):
                with self.subTest(mode=mode, mutation=mutation):
                    nonce = 'b' * 24 if mode == 'local' else None
                    scope = diag.DiagnosticScope('STUDENT_STATS503', mode, Path('/unused-control-run'),
                                                 'synthetic-project', ('docker', 'compose'), {}, nonce)
                    inspected, captured = [], []
                    def spawn(argv, **_):
                        if argv[:2] == ['docker', 'inspect']:
                            identity = [cid, scope.project, 'attendance-service', True, nonce or '']
                            inspected.append(identity.copy())
                            if len(inspected) == 2 and mutation:
                                index = {'project': 1, 'service': 2, 'running': 3, 'nonce': 4}[mutation]
                                identity[index] = False if mutation == 'running' else 'foreign'
                            return ClosedPipeChild(json.dumps(identity).encode())
                        if argv[:2] == ['docker', 'logs']:
                            captured.append(True)
                            return ClosedPipeChild(b'')
                        return ClosedPipeChild(cid.encode())
                    with patch.object(diag.subprocess, 'Popen', side_effect=spawn):
                        if mutation:
                            with self.assertRaises(ValueError):diag.collect(stage, context=scope)
                        else:self.assertEqual(diag.collect(stage, context=scope), {'attendance-service': b''})
                    self.assertEqual(len(inspected), 2)
                    self.assertEqual(captured, [True])

    def test_local_and_ci_contexts_do_not_masquerade(self):
        with self.assertRaises(ValueError):diag.admit_scope(config, 'ci', environment={})
        # Existing local validator owns source/CA/Compose/lock/container admission.
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory(prefix='rct-local-admission-control-') as directory:
            run = Path(directory);run.chmod(0o700)
            calls = []
            def validate_local(**kwargs):
                calls.append(kwargs)
                return {'run': str(run), 'project': 'rct-local-stage17-' + 'a'*12, 'nonce': 'a'*24, 'mode': 'client'}
            module = SimpleNamespace(validate_local=validate_local)
            spec = SimpleNamespace(loader=SimpleNamespace(exec_module=lambda target: None))
            with patch.object(diag.importlib.util, 'spec_from_file_location', return_value=spec), patch.object(diag.importlib.util, 'module_from_spec', return_value=module):
                scope = diag.admit_scope(diag.STAGES['HOMEWORK_CREATE503'], 'local', run, {'CONTROL': 'LOCAL_ONLY'})
                self.assertEqual(calls, [{'environment': {'CONTROL': 'LOCAL_ONLY'}}])
                self.assertEqual(scope.project, 'rct-local-stage17-' + 'a'*12)
                self.assertEqual(scope.nonce, 'a'*24)
                self.assertNotIn('GITHUB_ACTIONS', scope.environment)
                self.assertIn(str(run / 'compose.json'), scope.compose)
                with self.assertRaises(ValueError):diag.admit_scope(config, 'local', run, {})
                with self.assertRaises(ValueError):diag.admit_scope(diag.STAGES['HOMEWORK_CREATE503'], 'local', run / 'foreign', {})


result = unittest.TextTestRunner(verbosity=1).run(unittest.defaultTestLoader.loadTestsFromTestCase(SharedDiagnosticControls))
print(json.dumps({'schema': 'rct-diagnostic-python-controls.v1', 'passed': result.wasSuccessful(),
                  'controls': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors),
                  'runtimeServiceCalls': 0, 'browserDbDocker': False}))
raise SystemExit(0 if result.wasSuccessful() else 1)
