"""Synthetic receipt redaction checks; no processes, containers or network."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest


spec = importlib.util.spec_from_file_location("check_apps", Path(__file__).with_name("check_apps.py"))
apps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apps)
MARKER = "synthetic-private@example.invalid"


class OutageDiagnostics(unittest.TestCase):
    def observe(self, error=None, measurements=None, sample_errors=None, outage=None,
                configured=None, inspected=None):
        return apps.closed_outage_observation(
            error, measurements, sample_errors, outage, configured, inspected)

    def test_only_six_exact_assertions_are_labeled(self):
        reasons = {
            "Actual report operation failed": "REPORT_OPERATION",
            "Java process memory budget exceeded": "JAVA_MEMORY",
            "Resource measurements incomplete": "RESOURCE_MEASUREMENTS",
            "Required sidecar measurement phase incomplete": "PHASE_SAMPLES",
            "Owned container missing": "OWNED_CONTAINER_MISSING",
            "Sidecar memory budget exceeded": "SIDECAR_MEMORY",
        }
        for text, expected in reasons.items():
            with self.subTest(reason=expected):
                self.assertEqual(self.observe(AssertionError(text))["failure_reason"], expected)
        for error in (None, AssertionError(), AssertionError(MARKER), AssertionError([]),
                      AssertionError("Actual report operation failed", MARKER),
                      RuntimeError("Actual report operation failed")):
            self.assertEqual(self.observe(error)["failure_reason"], "UNKNOWN")

    def test_sampler_retains_closed_original_cause(self):
        cases = [(AssertionError("Runtime 4GiB disk floor crossed"), "AssertionError", "DISK_FLOOR"),
                 (AssertionError("Unknown Docker memory unit"), "AssertionError", "MEMORY_UNIT"),
                 (AssertionError("Owned container missing"), "AssertionError", "OWNED_CONTAINER_MISSING"),
                 (subprocess.CalledProcessError(1, MARKER, output="OUT_OF_MEMORY"),
                  "CalledProcessError", "OUT_OF_MEMORY"),
                 (subprocess.TimeoutExpired(MARKER, 15), "TimeoutExpired", "UNKNOWN"),
                 (AssertionError(MARKER), "AssertionError", "UNKNOWN"),
                 (subprocess.CalledProcessError(1, MARKER, output=MARKER),
                  "CalledProcessError", "UNKNOWN")]
        for error, expected_class, expected_reason in cases:
            with self.subTest(expected_class=expected_class, expected_reason=expected_reason):
                closed = apps.closed_sampler_error(error)
                result = self.observe(sample_errors=[closed])["sampler_error"]
                self.assertEqual(result, {"failure_class": expected_class, "reason": expected_reason})
                self.assertNotIn(MARKER, json.dumps(result))
        secret_class = type(MARKER, (Exception,), {})
        self.assertEqual(apps.closed_sampler_error(secret_class(MARKER)),
                         {"failure_class": "UNKNOWN", "reason": "UNKNOWN"})

    def test_snapshot_is_fixed_and_under_4096_bytes(self):
        # A 23-byte positive float representation is longer than any accepted integer.
        longest_number = 1.2345678901234568e-300
        phases = {phase: {"samples": 2**31 - 1, MARKER: MARKER}
                  for phase in ("apps-idle", "profile-idle", "profile-operation", MARKER)}
        measurements = {name: {"phases": phases, "peak_working_set_bytes": longest_number,
                               MARKER: MARKER} for name in apps.SERVICES | {MARKER}}
        limits = {name: longest_number for name in apps.SERVICES | {MARKER}}
        longest_reason = max(apps.DIAGNOSTIC_REASONS, key=len)
        result = self.observe(AssertionError("Owned container missing"), measurements,
                              [{"failure_class": "CalledProcessError", "reason": longest_reason}] * 5000,
                              {"operation": {"operations": 2**31 - 1, "peak_rss_bytes": longest_number,
                                             MARKER: MARKER}, MARKER: MARKER}, limits, limits)
        encoded = json.dumps(result, indent=2, allow_nan=False)
        self.assertLessEqual(len(encoded.encode()), 4096)
        self.assertNotIn(MARKER, encoded)
        self.assertEqual(set(result["sidecars"]), apps.SERVICES)
        for sidecar in result["sidecars"].values():
            self.assertEqual(set(sidecar["phase_samples"]),
                             {"apps-idle", "profile-idle", "profile-operation"})
            self.assertEqual(sidecar["peak_working_set_bytes"], longest_number)
        self.assertEqual(result["outage_operation"]["operations"], 2**31 - 1)

    def test_invalid_types_and_numeric_bounds_are_unknown(self):
        for value in (True, MARKER, None, [], {}, float("nan"), float("inf"), -1, 2**63):
            with self.subTest(value_type=type(value).__name__):
                result = self.observe(measurements={"homepage": {
                    "phases": {"apps-idle": {"samples": value}}, "peak_working_set_bytes": value}},
                    sample_errors=[{"failure_class": value, "reason": value}],
                    outage={"operation": {"operations": value, "peak_rss_bytes": value}},
                    configured={"homepage": value}, inspected={"homepage": value})
                sidecar = result["sidecars"]["homepage"]
                self.assertEqual(sidecar["peak_working_set_bytes"], "UNKNOWN")
                self.assertEqual(sidecar["configured_memory_limit_bytes"], "UNKNOWN")
                self.assertEqual(sidecar["inspected_memory_limit_bytes"], "UNKNOWN")
                self.assertEqual(sidecar["phase_samples"]["apps-idle"], "UNKNOWN")
                self.assertEqual(result["outage_operation"], {"operations": "UNKNOWN", "peak_rss_bytes": "UNKNOWN"})
                self.assertEqual(result["sampler_error"], {"failure_class": "UNKNOWN", "reason": "UNKNOWN"})
                json.dumps(result, allow_nan=False)
        for value in (True, [], MARKER, None):
            result = self.observe(measurements=value, sample_errors=value, outage=value,
                                  configured=value, inspected=value)
            self.assertFalse(result["sidecars"]["homepage"]["present"])
        for value in (0.5, 2**31):
            result = self.observe(outage={"operation": {"operations": value, "peak_rss_bytes": 1024.0}})
            self.assertEqual(result["outage_operation"]["operations"], "UNKNOWN")
            self.assertEqual(result["outage_operation"]["peak_rss_bytes"], 1024.0)


if __name__ == "__main__":
    unittest.main()
