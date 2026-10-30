"""Pure controls for finite host/bot diagnostics; no Docker, Java or network."""
import builtins
from contextlib import contextmanager
import copy
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import urllib.error
import urllib.parse

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location("pipeline_host_bot_control", HERE / "check_pipeline.py")
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)
CANARY = "PRIVATE_CANARY_NEVER_PUBLIC"


def fixture_api(name, path, **kwargs):
    if name == "notification-bot":
        raise urllib.error.HTTPError(CANARY, 503, CANARY, {}, None)
    expression = urllib.parse.parse_qs(urllib.parse.urlsplit(path).query)["query"][0]
    match = re.search(r'job="([^"]+)"', expression)
    job = match.group(1)
    port = {"node-exporter": 9100, "cadvisor": 8080, "notification-bot": 8081}[job]
    metric = {"instance": job + ":" + str(port), "service": job,
              "environment": "synthetic", "private_label": CANARY}
    value = expression.rsplit(" == ", 1)[-1] if " == " in expression else "123"
    return {"status": "success", "data": {"result": [{"metric": metric, "value": [CANARY, value]}]}}


class HostBotControls(unittest.TestCase):
    def setUp(self):
        pipeline._STATE.clear()
        pipeline._STATE.update(phase="PRECHECK_OR_CONFIG", cleanup="NOT_STARTED")

    def run_metrics(self, api=fixture_api, waiter=None):
        self.timeouts = []

        def once(predicate, seconds=100):
            self.timeouts.append(seconds)
            assert predicate(), CANARY

        with tempfile.TemporaryDirectory() as directory:
            control = Path(directory)
            (control / "done").touch()
            return pipeline.host_bot_metrics(api, waiter or once, control, "rct-" + CANARY)

    def test_real_shared_oracles_and_finite_observations(self):
        result = self.run_metrics()
        self.assertEqual(result["checkpoint"], "COMPLETE")
        self.assertEqual(set(result["observations"]), set(pipeline._HOST_BOT_QUERY_IDS))
        self.assertEqual(result["health_status"], 503)
        self.assertEqual(self.timeouts, [180] * 3 + [100] * 9)
        self.assertNotIn(CANARY, json.dumps(result, allow_nan=False))
        self.assertTrue(all(result["observations"]["UP_" + job]["expected_labels"]["instance"]
                            for job in ("NODE", "CADVISOR", "BOT")))

    def test_identity_failure_retains_oracle_and_safe_own_frame(self):
        def mismatch(*args, **kwargs):
            response = fixture_api(*args, **kwargs)
            response["data"]["result"][0]["metric"]["instance"] = CANARY
            return response
        try:
            self.run_metrics(mismatch)
        except AssertionError as error:
            frame = pipeline.own_source_frame(error)
        else:
            self.fail("Identity mismatch must fail")
        diagnostics = pipeline._STATE["host_bot_diagnostics"]
        self.assertEqual(diagnostics["checkpoint"], "IDENTITY_NODE")
        self.assertFalse(diagnostics["observations"]["UP_NODE"]["expected_labels"]["instance"])
        self.assertIsInstance(frame["line"], int)
        self.assertRegex(frame["sha256"], r"^[0-9a-f]{64}$")
        self.assertNotIn(CANARY, json.dumps([diagnostics, frame]))

    def test_empty_samples_timeout_and_private_error(self):
        def empty(*args, **kwargs):
            return {"status": "success", "data": {"result": []}}
        with self.assertRaises(AssertionError):
            self.run_metrics(empty)
        diagnostics = pipeline._STATE["host_bot_diagnostics"]
        self.assertEqual(diagnostics["checkpoint"], "UP_NODE")
        self.assertEqual(diagnostics["error_code"], "WAIT_TIMEOUT")
        self.assertEqual(diagnostics["observations"]["UP_NODE"]["sample_count"], 0)

        def unavailable(*args, **kwargs):
            raise OSError(CANARY)
        def original_wait_behavior(predicate, seconds=100):
            try:
                predicate()
            except (OSError, subprocess.CalledProcessError, ValueError, IndexError, KeyError):
                pass
            raise AssertionError(CANARY)
        with self.assertRaises(AssertionError):
            self.run_metrics(unavailable, original_wait_behavior)
        diagnostics = pipeline._STATE["host_bot_diagnostics"]
        self.assertEqual(diagnostics["observations"]["UP_NODE"]["error_code"], "IO_ERROR")
        self.assertNotIn(CANARY, json.dumps(diagnostics))

    def test_numeric_output_is_bounded_and_nonfinite_is_removed(self):
        rows = [{"value": [CANARY, value], "metric": {CANARY: CANARY}}
                for value in ("NaN", "Inf", "-Inf", "1e100", CANARY, "-12", "7")]
        result = pipeline.host_bot_observation(rows * 6)
        self.assertEqual(result["sample_count"], 42)
        self.assertTrue(result["values_truncated"])
        self.assertEqual((result["value_min"], result["value_max"]), (-12.0, 7.0))
        self.assertNotIn(CANARY, json.dumps(result, allow_nan=False))

    def test_worker_import_with_only_mounted_tests_and_no_host_helpers(self):
        # Reproduce the declared /repository/{infra/prometheus/tests} mount
        # shape under a private temporary root, with scripts genuinely absent.
        with tempfile.TemporaryDirectory() as directory:
            tests = Path(directory) / "repository/infra/prometheus/tests"
            tests.mkdir(parents=True)
            for name in ("check_pipeline.py", "check_formulas.py"):
                shutil.copyfile(HERE / name, tests / name)
            original_import = builtins.__import__
            def mounted_import(name, *args, **kwargs):
                if name in {"local_release_context", "local_release_evidence"}:
                    raise ModuleNotFoundError("Host helper is outside worker mounts")
                return original_import(name, *args, **kwargs)
            clone_spec = importlib.util.spec_from_file_location("worker_mount_control", tests / "check_pipeline.py")
            clone = importlib.util.module_from_spec(clone_spec)
            with patch("builtins.__import__", side_effect=mounted_import), patch.object(sys, "path", [str(tests), *sys.path]):
                clone_spec.loader.exec_module(clone)
            self.assertEqual(clone.ROOT, Path(directory) / "repository")
            self.assertFalse((clone.ROOT / "scripts").exists())
            self.assertTrue(callable(clone.bot_metrics_worker))
            # Both host entry points still resolve their original lazy helpers.
            with patch.dict(sys.modules, local_release_context=SimpleNamespace(runtime_context=lambda x: x),
                            local_release_evidence=SimpleNamespace(evidence_directory=lambda **kw: kw)):
                self.assertEqual(clone.runtime_context("HOST"), "HOST")
                self.assertEqual(clone.evidence_directory(prefix="host-"), {"prefix": "host-"})

    def mocked_main(self, failure=None, cleanup_fail=False):
        operations = []
        selected = []
        temporary = tempfile.TemporaryDirectory()
        directory = Path(temporary.name)

        @contextmanager
        def evidence(**kwargs):
            yield str(directory)

        def run(command, **kwargs):
            # Every subprocess in partial mode must be an exact-owned Docker
            # action. Any promtool/Gradle/Java/new subprocess is a hard failure.
            self.assertEqual(command[:2], ["docker", "compose"])
            action = command[command.index("-f") + 2:]
            operations.append(action)
            if action == ["up", "-d"]:
                config = pipeline.yaml.safe_load((directory / "compose.yml").read_text())
                selected.extend(config["services"])
            if action[:1] == ["logs"]:
                return SimpleNamespace(stdout=b"private worker tail", stderr=b"", returncode=0)
            return SimpleNamespace(stdout="", stderr="", returncode=int(cleanup_fail and action[:1] == ["down"]))

        original_exists = Path.exists
        original_read = Path.read_text
        host_paths = {"/proc", "/sys", "/var/run/dbus", "/var/lib/docker", "/dev/disk", "/dev/kmsg"}
        def exists(path):
            return True if str(path) in host_paths else original_exists(path)
        def read(path, *args, **kwargs):
            return "MemAvailable: 4194304 kB\n" if str(path) == "/proc/meminfo" else original_read(path, *args, **kwargs)
        def metrics(*args):
            if failure:
                raise failure
            pipeline._STATE["host_bot_diagnostics"] = {"checkpoint": "COMPLETE", "observations": {}, "error_code": "NONE"}

        try:
            with patch.object(pipeline, "runtime_context", return_value={"event": "LOCAL_CONTROL", "checkoutSha": "a" * 40}), \
                 patch.object(pipeline, "evidence_directory", evidence), \
                 patch.object(pipeline.shutil, "disk_usage", return_value=SimpleNamespace(free=5 * 1024**3)), \
                 patch.object(pipeline.subprocess, "run", side_effect=run), \
                 patch.object(pipeline, "host_bot_metrics", side_effect=metrics) as oracle, \
                 patch.object(Path, "exists", exists), patch.object(Path, "read_text", read):
                try:
                    result = pipeline.main(host_and_bot_only=True)
                except Exception as error:
                    result = error
            self.assertEqual(oracle.call_count, 1)
            self.assertEqual(set(selected), {"prometheus", "node-exporter", "cadvisor", "notification-bot"})
            self.assertIn(["down", "--volumes", "--remove-orphans"], operations)
            self.assertEqual(operations[-1], ["ps", "--quiet"])
            self.assertEqual((directory / "host-bot.private.log").stat().st_mode & 0o777, 0o600)
            return result
        finally:
            temporary.cleanup()

    def test_partial_selection_returns_qualified_result_after_cleanup(self):
        result = self.mocked_main()
        self.assertEqual(result["result"], "PARTIAL_PASS")
        self.assertEqual(result["scope"], "HOST_AND_BOT_METRICS")
        self.assertFalse(result["full_monitoring"])
        self.assertNotIn("actual_application_transport", result)
        self.assertEqual(pipeline._STATE["cleanup"], "PASS")

    def test_primary_failure_survives_cleanup_failure(self):
        failure = OSError(CANARY)
        result = self.mocked_main(failure=failure, cleanup_fail=True)
        self.assertIs(result, failure)
        self.assertEqual(pipeline._STATE["cleanup"], "FAIL")

    def test_partial_mode_rejects_ci_before_subprocess(self):
        with patch.object(pipeline, "runtime_context", return_value={"event": "push"}), \
             patch.object(pipeline.subprocess, "run") as run:
            with self.assertRaises(AssertionError):
                pipeline.main(host_and_bot_only=True)
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
