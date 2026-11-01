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
        value = {"nonce": "a" * 24, "run": "/private/run", "mode": "full"}
        with patch.object(lane.sys, "platform", "linux"), patch.object(lane, "manifest", return_value=value):
            with self.assertRaisesRegex(lane.AdmissionError, "LOCAL_STAGE17_ONLY"):
                lane.validate_local(environment={"RCT_LOCAL_STAGE17_MANIFEST": "/private/run/owner.json",
                                                 "RCT_LOCAL_STAGE17_NONCE": "a" * 24,
                                                 "RCT_LOCAL_STAGE17_MODE": "full",
                                                 "E2E_BASE_URL": "https://foreign.example:8443"})

    def test_manifest_lane_requires_its_own_environment_and_expected_consumer(self):
        for mode in sorted(lane.MODES):
            with self.subTest(mode=mode):
                runner = lane.Runner(mode)
                value = {"mode": mode, "nonce": runner.nonce, "run": "/private/run"}
                runner.env.update(RCT_REMAINING5_RUN=value["run"], RCT_E2E_TEACHING_CLOCK_DIR=value["run"] + "/clock")
                if mode == "lesson-lifecycle":
                    runner.env["RCT_LESSON_LIFECYCLE_RUN"] = value["run"]
                lane.validate_lane_environment(value, runner.env, mode)
                for other in lane.MODES - {mode}:
                    with self.assertRaises(lane.AdmissionError):
                        lane.validate_lane_environment(value, runner.env, other)
                for key, bad in (("RCT_LOCAL_STAGE17_MODE", "client" if mode != "client" else "full"),
                                 ("RCT_E2E_ASYNC_REPORT", "true" if mode in {"client", "lesson-lifecycle"} else "false"),
                                 ("RCT_LESSON_LIFECYCLE_RUN", "/foreign/run"),
                                 ("RCT_APP_RECOVERY_PROJECT", "rct-recovery-1-1-dst"),
                                 ("RCT_E2E_ROLE_LOGOUT", "true"),
                                 ("RCT_LOCAL_STAGE17_NONCE", "b" * 24)):
                    with self.subTest(key=key), self.assertRaises(lane.AdmissionError):
                        lane.validate_lane_environment(value, {**runner.env, key: bad}, mode)

    def test_startup_diagnostic_is_client_only_manifest_bound_and_keeps_selection(self):
        for mode in lane.MODES - {"client"}:
            with self.assertRaisesRegex(lane.AdmissionError, "CLIENT_DIAGNOSTIC_ONLY"):
                lane.Runner(mode, startup_diagnostic=True)
        for diagnostic in (False, True):
            runner = lane.Runner("client", startup_diagnostic=diagnostic)
            value = {"mode": "client", "nonce": runner.nonce, "run": "/private/run", "startup_diagnostic": diagnostic}
            runner.env.update(RCT_REMAINING5_RUN=value["run"], RCT_E2E_TEACHING_CLOCK_DIR=value["run"] + "/clock")
            lane.validate_lane_environment(value, runner.env, "client")
            with self.assertRaisesRegex(lane.AdmissionError, "LOCAL_DIAGNOSTIC_MISMATCH"):
                lane.validate_lane_environment({**value, "startup_diagnostic": not diagnostic}, runner.env, "client")
            with tempfile.TemporaryDirectory(prefix="rct-command-control-") as temporary:
                runner.run = Path(temporary)
                (runner.run / "compose.json").write_text('{"services":{}}')
                calls = []
                with patch.object(runner, "command", lambda phase, args, **_: calls.append((phase, args))), \
                        patch.object(lane, "validate_local"), patch.object(lane, "node_tls_probe"), patch.object(lane, "emit"):
                    runner.execute()
                self.assertEqual(calls[-1], ("CLIENT_REMAINING5", ["npx", "playwright", "test", "--config",
                    "playwright.real-mobile.config.ts", "--project=chromium", *(["--max-failures=1"] if diagnostic else [])]))

    @unittest.skipUnless(sys.platform == "linux", "Linux private filesystem semantics required")
    def test_future_lesson_clock_is_a_weekday_and_monotonic(self):
        with tempfile.TemporaryDirectory(prefix="rct-lesson-clock-control-") as temporary:
            runner = lane.Runner("lesson-lifecycle")
            runner.run = Path(temporary)
            clock = runner.run / "clock"
            clock.mkdir(mode=0o755)
            marker, instant = clock / "disposable-target", clock / "current-instant"
            marker.write_text("RCT_E2E_DISPOSABLE\n")
            instant.write_text("2099-10-23T06:00:00.000Z\n")
            marker.chmod(0o644)
            instant.chmod(0o644)
            runner.prepare_lesson_clock()
            future = lane.datetime.datetime.fromisoformat(instant.read_text().strip().replace("Z", "+00:00"))
            self.assertGreater(future, lane.datetime.datetime(2099, 10, 23, 6, tzinfo=lane.datetime.timezone.utc))
            self.assertLess(future.weekday(), 5)
            self.assertEqual((future.hour, future.minute), (6, 0))
            marker.write_text("FOREIGN\n")
            with self.assertRaisesRegex(lane.AdmissionError, "CLOCK_MARKER"):
                runner.prepare_lesson_clock()

    @unittest.skipUnless(sys.platform == "linux", "Linux selectors and private filesystem semantics required")
    def test_owned_container_logs_are_private_and_byte_bounded(self):
        with tempfile.TemporaryDirectory(prefix="rct-log-control-") as temporary:
            runner = lane.Runner("client")
            runner.run = Path(temporary)
            popen = lane.subprocess.Popen
            def output(args, **kwargs):
                self.assertEqual(args[-1], "owned-id")
                return popen([sys.executable, "-c", "import sys; sys.stdout.buffer.write(b'x' * 524288 + b'LATE_SENTINEL')"], **kwargs)
            rows = [{"Id": "owned-id", "Config": {"Labels": {"com.docker.compose.service": "api-gateway"}}}]
            previous = os.umask(0o077)
            try:
                with patch.object(lane, "inventory", return_value=rows), patch.object(lane.subprocess, "Popen", output):
                    runner.preserve_logs()
            finally:
                os.umask(previous)
            log = runner.run / "container-api-gateway.private.log"
            self.assertEqual(log.stat().st_size, 524288 + len(b"LATE_SENTINEL"))
            self.assertTrue(log.read_bytes().endswith(b"LATE_SENTINEL"))
            self.assertEqual(stat.S_IMODE(log.stat().st_mode), 0o600)
            receipt = json.loads((runner.run / "container-logs.safe.json").read_text())
            self.assertEqual(receipt[0]["status"], "COMPLETE_RETAINED")
            self.assertTrue(receipt[0]["complete"])
            self.assertEqual(stat.S_IMODE(log.with_name(log.name + ".meta.safe.json").stat().st_mode), 0o600)

    @unittest.skipUnless(sys.platform == "linux", "POSIX private log capture")
    def test_log_capture_limits_and_late_tail_are_explicit(self):
        popen = lane.subprocess.Popen
        with tempfile.TemporaryDirectory(prefix="rct-log-limits-") as tmp:
            root = Path(tmp)
            for kind, code, kwargs, expected in (
                ("bytes", "import sys;sys.stdout.buffer.write(b'x'*100000)", {"byte_bound": 65536}, "BYTE_BOUND"),
                ("time", "import time;time.sleep(2)", {"seconds": .05}, "TIME_BOUND"),
                ("exit", "raise SystemExit(7)", {}, "COMMAND_FAILED"),
                ("budget", "", {"deadline": time.monotonic()-1}, "BUDGET_EXHAUSTED"),
            ):
                path = root / (kind + ".log")
                def output(args, **options):
                    script = "print('LATE_TAIL_SENTINEL')" if "--tail" in args else code
                    return popen([sys.executable, "-c", script], **options)
                with self.subTest(kind=kind), patch.object(lane.subprocess, "Popen", output):
                    result = lane.retain_container_log("owned-id", path, **{"deadline": time.monotonic()+3, **kwargs})
                    self.assertEqual(result["status"], expected)
                    self.assertFalse(result["complete"])
                    self.assertEqual(result["late_tail"]["status"], "BUDGET_EXHAUSTED" if kind == "budget" else "COMPLETE_RETAINED")
                    if kind != "budget":
                        self.assertIn(b"LATE_TAIL_SENTINEL", path.with_name(path.name + ".tail").read_bytes())

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

    def isclosed(self):
        return False

    @property
    def fp(self):
        return types.SimpleNamespace(
            raw=types.SimpleNamespace(_sock=types.SimpleNamespace(settimeout=lambda seconds: None))
        )


class FinalizationControls(unittest.TestCase):
    @unittest.skipUnless(sys.platform == "linux", "Linux private filesystem/lock semantics required")
    def test_main_reports_success_failure_and_interrupt_without_masking_primary(self):
        cases = ("pass", "nonzero", "interrupt", "nonzero-cleanup-failure", "cleanup-failure", "nonzero-logs-failure", "logs-failure")
        command = lane.Runner.command
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory(prefix="rct-finalizer-control-") as temporary:
                runs = Path(temporary) / "runs"
                primary = [KeyboardInterrupt("PRIVATE_SENTINEL") if case == "interrupt" else None]
                cleanup_failure = RuntimeError("PRIVATE_SENTINEL") if "cleanup-failure" in case else None
                logs_failure = RuntimeError("PRIVATE_SENTINEL") if "logs-failure" in case else None
                calls = []
                class FakeRunner:
                    def __init__(self, mode, *, startup_diagnostic=False):
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
                    def preserve_logs(self):
                        calls.append("logs")
                        if logs_failure:
                            raise logs_failure
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
                        expected_type = lane.AdmissionError if case.startswith("nonzero") else type(primary[0] or logs_failure or cleanup_failure)
                        if case != "pass":
                            with self.assertRaises(expected_type) as raised:
                                lane.main()
                            self.assertIs(raised.exception, primary[0] or logs_failure or cleanup_failure)
                        else:
                            lane.main()
                finally:
                    os.umask(previous)
                receipt = json.loads((runs / "owned/result.safe.json").read_text())
                expected = primary[0] or logs_failure or cleanup_failure
                events = [json.loads(line) for line in stdout.getvalue().splitlines()]
                event = events[-1]
                self.assertEqual(calls, ["prepare", "execute", "logs", "cleanup"])
                self.assertEqual(event["phase"], "LOCAL_RUN_COMPLETE")
                self.assertEqual(event["stage"], receipt["phase"])
                self.assertEqual(receipt["phase"], "BUILD_API_GATEWAY" if primary[0] else "COMPLETE")
                self.assertEqual(receipt["result"], "FAIL" if expected else "PASS")
                self.assertEqual(receipt["cleanup"], "FAIL_RETAINED_FOR_OWNER" if cleanup_failure else "PASS")
                self.assertEqual(receipt["exit_code"], 130 if case == "interrupt" else 1 if expected else 0)
                self.assertEqual(receipt["child_returncode"], 23 if case.startswith("nonzero") else None)
                self.assertNotIn("PRIVATE_SENTINEL", stdout.getvalue() + json.dumps(receipt))


class OpenapiControls(unittest.TestCase):
    producers = {
        "auth-service",
        "academic-service",
        "schedule-service",
        "attendance-service",
        "notification-service",
        "mobile-bff",
    }

    def document(self, producer):
        value = {
            "openapi": "3.0.1" if producer == "mobile-bff" else "3.1.0",
            "servers": [{"url": "/" if producer == "mobile-bff" else "/api"}],
            "paths": {"/example": {"get": {"security": [{"bearerAuth": []}]}}},
            "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer"}}},
        }
        if producer == "academic-service":
            fields = {"jobId", "state", "suggestedFilename", "expiresAt", "errorCode"}
            value["paths"]["/academic/export-jobs"] = {
                "post": {"security": [{"bearerAuth": []}], "responses": {"202": {}, "503": {}}}
            }
            value["components"]["schemas"] = {
                "Status": {"properties": {name: {} for name in fields}, "required": sorted(fields)}
            }
            value["components"]["schemas"]["Status"]["properties"]["errorCode"] = {"type": ["string", "null"]}
        if producer == "auth-service":
            value["paths"]["/auth/login"] = {"post": {"security": []}}
        return value

    def execute(self, mutate=None, target="academic-service"):
        calls = []

        def request(request, timeout):
            self.assertEqual(timeout, 15)
            calls.append(request.full_url)
            if request.full_url.endswith("swagger-config"):
                value = {"urls": [{"url": "/openapi/" + name} for name in sorted(self.producers)]}
            else:
                producer = request.full_url.rsplit("/", 1)[1]
                value = self.document(producer)
                if producer == target and mutate:
                    value = mutate(value)
            return Response(value if isinstance(value, bytes) else json.dumps(value).encode())

        opener = types.SimpleNamespace(open=request)
        docker = lambda *args: types.SimpleNamespace(stdout=("a" * 64 + "\n") if args[0] == "ps" else "172.30.0.12\n")
        safe = {}
        with patch("urllib.request.build_opener", return_value=opener):
            try:
                openapi(docker, safe, "rct-local-stage17-owned")
            except (AssertionError, ValueError, KeyError, OSError):
                return safe, calls, False
        return safe, calls, True

    def test_exact_seven_gets_keep_bounded_cold_read(self):
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
        self.assertEqual(
            safe["gateway_openapi"]["documents"]["academic-service"]["stage"], "ACADEMIC_ERROR_CODE_NULLABLE_TYPE"
        )

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

    def test_terminal_status_does_not_retry_or_parse_contract(self):
        for status in (401, 403, 404, 429, 500, 503):

            def reject(_, status=status):
                raise urllib.error.HTTPError("http://PRIVATE_SENTINEL", status, "PRIVATE_SENTINEL", {}, None)

            safe, calls, passed = self.execute(reject)
            self.assertFalse(passed)
            self.assertEqual(len(calls), 2)
            observed = safe["gateway_openapi"]["documents"]["academic-service"]
            self.assertEqual((observed["status"], observed["error_kind"]), (status, "HTTP_STATUS"))
            self.assertFalse(observed["contract_valid"])
            self.assertNotIn("PRIVATE_SENTINEL", json.dumps(safe))

    def test_duplicate_keys_and_auth_contract_remain_terminal(self):
        safe, calls, passed = self.execute(lambda _: b'{"openapi":"3.1.0","openapi":"3.1.0"}')
        self.assertFalse(passed)
        self.assertEqual(len(calls), 2)

        def authenticated_login(value):
            value["paths"]["/auth/login"]["post"]["security"] = [{"bearerAuth": []}]
            return value

        safe, calls, passed = self.execute(authenticated_login, target="auth-service")
        self.assertFalse(passed)
        self.assertEqual(len(calls), 4)
        self.assertEqual(safe["gateway_openapi"]["documents"]["auth-service"]["stage"], "AUTH_LOGIN_SECURITY")

    def test_absolute_document_budget_cannot_accept_late_valid_headers(self):
        clock = [0.0]

        def late(value):
            clock[0] = 20.0
            return value

        with patch.object(time, "monotonic", lambda: clock[0]):
            safe, calls, passed = self.execute(late)
        self.assertFalse(passed)
        self.assertEqual(len(calls), 2)
        observed = safe["gateway_openapi"]["documents"]["academic-service"]
        self.assertEqual((observed["stage"], observed["error_kind"], observed["status"]), ("HTTP_HEADERS", "TIMEOUT", None))
        self.assertEqual(observed["deadline_budget_ms"], 20000)

    def test_empty_oversized_and_security_scheme_bodies_fail(self):
        def bad_scheme(value):
            value["components"]["securitySchemes"]["bearerAuth"]["scheme"] = "basic"
            return value

        for mutate in (lambda _: b"", lambda _: b"x" * (2 * 1024**2 + 1), bad_scheme):
            safe, calls, passed = self.execute(mutate)
            self.assertFalse(passed)
            self.assertEqual(len(calls), 2)
            self.assertFalse(safe["gateway_openapi"]["documents"]["academic-service"]["contract_valid"])


if __name__ == "__main__":
    unittest.main()
