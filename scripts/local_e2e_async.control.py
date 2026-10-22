"""Pure safety/diagnostic controls; no Docker, application or browser is started."""

import ast
import copy
from contextlib import redirect_stdout
import importlib.util
import io
import ipaddress
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import time
import types
import unittest
from unittest.mock import patch
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
if sys.platform != "linux":
    sys.modules.setdefault("fcntl", types.SimpleNamespace())
spec = importlib.util.spec_from_file_location("local_lane", ROOT / "scripts/local_e2e_async.py")
lane = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lane)

# Load only the HTTP contract function, without importing monitoring dependencies.
source = ast.parse((ROOT / "infra/prometheus/tests/check_pipeline.py").read_text())
function = next(node for node in source.body if isinstance(node, ast.FunctionDef) and node.name == "e2e_gateway_openapi")
namespace = {"ipaddress": ipaddress, "json": json, "re": __import__("re"), "time": time,
             "urllib": __import__("urllib")}
exec(compile(ast.Module(body=[function], type_ignores=[]), "check_pipeline.py", "exec"), namespace)
openapi = namespace["e2e_gateway_openapi"]


class OwnershipControls(unittest.TestCase):
    @unittest.skipUnless(sys.platform == "linux" and shutil.which("node"), "Linux file modes and Node required")
    def test_clock_public_inputs_remain_readable_below_private_run(self):
        with tempfile.TemporaryDirectory(prefix="rct-clock-control-") as temporary:
            run = Path(temporary)
            run.chmod(0o700)
            jar = run / "input.jar"
            jar.write_bytes(b"CONTROL_NOT_AN_EXECUTED_JAR")
            runner = lane.Runner.__new__(lane.Runner)
            runner.run, runner.child = run, None
            runner.env = {**os.environ, "RCT_E2E_SYNTHETIC_SEED": "true", "E2E_BASE_URL": "https://localhost:8443"}
            previous = os.umask(0o077)
            try:
                runner.command("CLOCK_PREPARE", ["node", str(ROOT / "tests/e2e/backend-clock/prepare-clock.mjs"),
                                                  str(run / "clock"), str(jar)])
            finally:
                os.umask(previous)
            self.assertEqual(stat.S_IMODE(run.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE((run / "clock").stat().st_mode), 0o755)
            for name in ("clock-fixture.jar", "disposable-target", "current-instant"):
                self.assertEqual(stat.S_IMODE((run / "clock" / name).stat().st_mode), 0o644)
            self.assertEqual(stat.S_IMODE((run / "clock_prepare.private.log").stat().st_mode), 0o600)

    def rows(self, services=None, nonce="a" * 24):
        return [{"Id": str(index), "Config": {"Labels": {
            "com.docker.compose.service": name, lane.LABEL: nonce}}, "State": {"Running": True}}
            for index, name in enumerate(services or sorted(lane.SERVICES))]

    def capture(self, rows):
        def fake(args, **_):
            return "one\n" if args[1] == "ps" else json.dumps(rows)
        return fake

    def test_complete_owned_cohort(self):
        with patch.object(lane, "capture", self.capture(self.rows())):
            self.assertEqual(len(lane.inventory("owned", "a" * 24, complete=True)), 16)

    def test_foreign_nonce_duplicate_and_missing_services_rejected(self):
        examples = [self.rows(nonce="b" * 24), self.rows(["nginx", "nginx"]), self.rows(["nginx"]), self.rows(["production-db"])]
        for rows in examples:
            with self.subTest(count=len(rows)), patch.object(lane, "capture", self.capture(rows)):
                with self.assertRaises(lane.AdmissionError):
                    lane.inventory("owned", "a" * 24, complete=True)

    def test_local_admission_rejects_hosted_and_non_stage17(self):
        with self.assertRaises(lane.AdmissionError):
            lane.validate_local(environment={"GITHUB_ACTIONS": "true"})
        value = {"nonce": "a" * 24, "run": "/private/run"}
        with patch.object(lane.sys, "platform", "linux"), patch.object(lane, "manifest", return_value=value):
            with self.assertRaisesRegex(lane.AdmissionError, "LOCAL_STAGE17_ONLY"):
                lane.validate_local(environment={"RCT_LOCAL_STAGE17_MANIFEST": "/private/run/owner.json",
                                                 "RCT_LOCAL_STAGE17_NONCE": "a" * 24,
                                                 "E2E_BASE_URL": "https://foreign.example:8443"})

    def test_cleanup_does_not_remove_foreign_network(self):
        runner = lane.Runner.__new__(lane.Runner)
        runner.project, runner.nonce = "owned", "a" * 24
        calls = []
        def fake(args, **_):
            calls.append(args)
            if args[1:3] == ["network", "ls"]:
                return "foreign\n"
            if args[1:3] == ["network", "inspect"]:
                return json.dumps([{"Labels": {lane.LABEL: "b" * 24}}])
            return ""
        with patch.object(lane, "inventory", return_value=[]), patch.object(lane, "capture", fake):
            with self.assertRaisesRegex(lane.AdmissionError, "FOREIGN_RESOURCE"):
                runner.cleanup()
        self.assertFalse(any("rm" in args for args in calls))

    def test_image_cleanup_removes_only_admitted_nonce_tag_without_force(self):
        for scenario in ("owned", "foreign", "changed", "not-admitted"):
            with self.subTest(scenario=scenario):
                runner = lane.Runner.__new__(lane.Runner)
                runner.project, runner.nonce = "owned", "a" * 24
                tag, identifier = "rct-local-stage17/academic-service:" + runner.nonce, "sha256:" + "c" * 64
                runner.image_targets, runner.image_ids = [tag], {}
                runner.image_admitted = set() if scenario == "not-admitted" else {tag}
                if scenario == "changed":
                    runner.image_ids[tag] = "sha256:" + "d" * 64
                calls, exists = [], [True]
                def fake(args, **_):
                    calls.append(args)
                    if args[1:3] == ["image", "ls"]:
                        return identifier if exists[0] else ""
                    if args[1:3] == ["image", "inspect"]:
                        return identifier + " " + ("b" * 24 if scenario == "foreign" else runner.nonce)
                    if args[1:3] == ["image", "rm"]:
                        self.assertEqual(args, ["docker", "image", "rm", tag])
                        exists[0] = False
                    return ""
                with patch.object(lane, "inventory", return_value=[]), patch.object(lane, "capture", fake):
                    if scenario in {"foreign", "changed"}:
                        with self.assertRaises(lane.AdmissionError):
                            runner.cleanup()
                    else:
                        runner.cleanup()
                removed = [args for args in calls if args[1:3] == ["image", "rm"]]
                self.assertEqual(len(removed), 1 if scenario == "owned" else 0)


class Response(io.BytesIO):
    status = 200


class FinalizationControls(unittest.TestCase):
    @unittest.skipUnless(sys.platform == "linux", "Linux private filesystem/lock semantics required")
    def test_main_reports_success_failure_and_interrupt_without_masking_primary(self):
        cases = ("pass", "nonzero", "interrupt", "nonzero-cleanup-failure", "cleanup-failure")
        command = lane.Runner.command
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory(prefix="rct-finalizer-control-") as temporary:
                runs = Path(temporary) / "runs"
                primary = [KeyboardInterrupt("PRIVATE_SENTINEL") if case == "interrupt" else None]
                cleanup_failure = RuntimeError("PRIVATE_SENTINEL") if "cleanup-failure" in case else None
                calls = []
                class FakeRunner:
                    def __init__(self, mode):
                        self.run, self.phase = runs / "owned", "PREPARE"
                        self.env, self.child = {}, None
                    def prepare(self):
                        self.run.mkdir(mode=0o700)
                        calls.append("prepare")
                    def execute(self):
                        self.phase = "BUILD_API_GATEWAY" if primary[0] or case.startswith("nonzero") else "COMPLETE"
                        calls.append("execute")
                        if case.startswith("nonzero"):
                            try:
                                command(self, "BUILD_API_GATEWAY", [sys.executable, "-c", "import sys; sys.exit(23) # PRIVATE_SENTINEL"])
                            except BaseException as error:
                                primary[0] = error
                                raise
                        if primary[0]:
                            raise primary[0]
                    def cleanup(self):
                        calls.append("cleanup")
                        if cleanup_failure:
                            raise cleanup_failure
                stdout = io.StringIO()
                lock = types.SimpleNamespace(flock=lambda *_: None, LOCK_EX=1, LOCK_NB=2)
                previous = os.umask(0o077)
                try:
                    with patch.object(lane, "RUNS", runs), patch.object(lane, "Runner", FakeRunner), \
                            patch.object(lane, "preflight"), patch.object(lane, "owned"), \
                            patch.object(lane, "fcntl", lock), patch.object(lane.sys, "argv", ["runner", "openapi"]), \
                            patch.object(lane.os, "O_NOFOLLOW", getattr(os, "O_NOFOLLOW", 0), create=True), redirect_stdout(stdout):
                        expected_type = lane.AdmissionError if case.startswith("nonzero") else type(primary[0] or cleanup_failure)
                        if case != "pass":
                            with self.assertRaises(expected_type) as raised:
                                lane.main()
                            self.assertIs(raised.exception, primary[0] or cleanup_failure)
                        else:
                            lane.main()
                finally:
                    os.umask(previous)
                receipt = json.loads((runs / "owned/result.safe.json").read_text())
                expected = primary[0] or cleanup_failure
                events = [json.loads(line) for line in stdout.getvalue().splitlines()]
                event = events[-1]
                self.assertEqual(calls, ["prepare", "execute", "cleanup"])
                self.assertEqual(event["phase"], "LOCAL_RUN_COMPLETE")
                self.assertEqual(event["stage"], receipt["phase"])
                self.assertEqual(receipt["phase"], "BUILD_API_GATEWAY" if primary[0] else "COMPLETE")
                self.assertEqual(receipt["result"], "FAIL" if expected else "PASS")
                self.assertEqual(receipt["cleanup"], "FAIL_RETAINED_FOR_OWNER" if cleanup_failure else "PASS")
                self.assertEqual(receipt["exit_code"], 130 if case == "interrupt" else 1 if expected else 0)
                self.assertEqual(receipt["child_returncode"], 23 if case.startswith("nonzero") else None)
                self.assertNotIn("PRIVATE_SENTINEL", stdout.getvalue() + json.dumps(receipt))


class OpenapiControls(unittest.TestCase):
    producers = {"auth-service", "academic-service", "schedule-service", "attendance-service", "notification-service", "mobile-bff"}

    def document(self, producer):
        value = {"openapi": "3.0.1" if producer == "mobile-bff" else "3.1.0",
                 "servers": [{"url": "/" if producer == "mobile-bff" else "/api"}],
                 "paths": {"/example": {"get": {"security": [{"bearerAuth": []}]}}},
                 "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer"}}}}
        if producer == "academic-service":
            fields = {"jobId", "state", "suggestedFilename", "expiresAt", "errorCode"}
            value["paths"]["/academic/export-jobs"] = {"post": {"security": [{"bearerAuth": []}], "responses": {"202": {}, "503": {}}}}
            value["components"]["schemas"] = {"Status": {"properties": {name: {} for name in fields}, "required": sorted(fields)}}
            value["components"]["schemas"]["Status"]["properties"]["errorCode"] = {"type": ["string", "null"]}
        if producer == "auth-service":
            value["paths"]["/auth/login"] = {"post": {"security": []}}
        return value

    def execute(self, mutate=None):
        calls = []
        def request(request, timeout):
            self.assertEqual(timeout, 5)
            calls.append(request.full_url)
            if request.full_url.endswith("swagger-config"):
                value = {"urls": [{"url": "/openapi/" + name} for name in sorted(self.producers)]}
            else:
                producer = request.full_url.rsplit("/", 1)[1]
                value = self.document(producer)
                if producer == "academic-service" and mutate:
                    value = mutate(value)
            return Response(value if isinstance(value, bytes) else json.dumps(value).encode())
        opener = types.SimpleNamespace(open=request)
        docker = lambda *args: types.SimpleNamespace(stdout=("a" * 64 + "\n") if args[0] == "ps" else "172.30.0.12\n")
        safe = {}
        with patch("urllib.request.build_opener", return_value=opener):
            try:
                openapi(docker, safe, "rct-local-stage17-owned")
            except (AssertionError, ValueError, KeyError, TimeoutError):
                return safe, calls, False
        return safe, calls, True

    def test_exact_seven_gets_keep_strict_timeout(self):
        safe, calls, passed = self.execute()
        self.assertTrue(passed)
        self.assertEqual(len(calls), 7)
        self.assertEqual(len(safe["gateway_openapi"]["documents"]), 6)

    def test_academic_nullable_contract_diagnostic(self):
        def malformed(value):
            value["components"]["schemas"]["Status"]["properties"]["errorCode"] = {"type": "string", "nullable": True}
            return value
        safe, _, passed = self.execute(malformed)
        self.assertFalse(passed)
        self.assertEqual(safe["gateway_openapi"]["documents"]["academic-service"]["stage"], "ACADEMIC_ERROR_CODE_NULLABLE_TYPE")

    def test_json_failure_classified_without_raw_body(self):
        safe, _, passed = self.execute(lambda _: b"PRIVATE_SENTINEL not JSON")
        self.assertFalse(passed)
        self.assertEqual(safe["gateway_openapi"]["documents"]["academic-service"]["error_kind"], "JSON_SYNTAX_OR_SHAPE")
        self.assertNotIn("PRIVATE_SENTINEL", json.dumps(safe))

    def test_timeout_before_headers_has_no_status_and_keeps_primary_failure(self):
        def timeout(_):
            raise TimeoutError("PRIVATE_SENTINEL")
        safe, _, passed = self.execute(timeout)
        self.assertFalse(passed)
        observed = safe["gateway_openapi"]["documents"]["academic-service"]
        self.assertIsNone(observed["status"])
        self.assertEqual((observed["stage"], observed["error_kind"]), ("HTTP_HEADERS", "TIMEOUT"))
        self.assertNotIn("PRIVATE_SENTINEL", json.dumps(safe))


if __name__ == "__main__":
    unittest.main()
