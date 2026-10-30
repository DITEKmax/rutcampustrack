#!/usr/bin/env python3
"""Fixed foreground local analogue of canonical CI browser selections.

Each mode uses new source-bound cohorts. No previous acceptance receipt is read.
Independent modes are serialized by the caller and the existing 8443 lease.
"""
import argparse
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import local_e2e_async as lane

MODES = ("full", "client", "webkit", "lesson-lifecycle", "ui-batch", "special13", "homework-sensitivity", "controls", "failed-ci")
BUDGETS = {"full": 600, "client": 600, "webkit": 420, "lesson-lifecycle": 1020,
           "homework-sensitivity": 420}
CONTROLS = (
    ("python3", "-B", "fixtures/http500-diagnostic-contract.py"),
    ("node", "check-admission-retry.mjs"),
    ("node", "fixtures/semester-lifecycle-ui.control.mjs"),
    ("node", "fixtures/admin-activity-response.control.mjs"),
    ("node", "fixtures/web-panel-route.control.mjs"),
    ("node", "fixtures/registry-html-privacy.control.mjs"),
    ("node", "fixtures/semester-health-diagnostic.control.mjs"),
    ("python3", "-B", "fixtures/lesson-transfer-diagnostic.control.py"),
    ("python3", "-B", "fixtures/semester-effect-diagnostic.control.py"),
    ("node", "fixtures/diagnostic-events.control.mjs", "--with-consumer"),
)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def binding(environment):
    revision = environment.get("RCT_LOCAL_RELEASE_REVISION", "")
    nonce = environment.get("RCT_LOCAL_RELEASE_NONCE", "")
    audit = Path(environment.get("RCT_LOCAL_RELEASE_AUDIT", ""))
    lane.require(re.fullmatch("[0-9a-f]{40}", revision) and re.fullmatch("[0-9a-f]{24}", nonce), "RELEASE_IDENTITY")
    lane.require(audit.is_absolute() and audit.resolve() == audit, "RELEASE_AUDIT")
    lane.owned(audit, 0o700, directory=True)
    lane.require(not any(environment.get(k) for k in ("GITHUB_ACTIONS", "GITHUB_SHA", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "RUNNER_ENVIRONMENT")), "DO_NOT_SPOOF_CI")
    lane.require(lane.capture(["git", "rev-parse", "HEAD"]).strip() == revision, "RELEASE_REVISION")
    lane.require(not lane.capture(["git", "status", "--porcelain", "--untracked-files=normal"]).strip(), "RELEASE_SOURCE_DIRTY")
    return revision, nonce, audit


def closed_report(report, expected, project):
    lane.require(type(report) is dict and set(report) == {"status", "runnerErrors", "unsafeArtifact", "suppressedStdoutBytes", "suppressedStderrBytes", "results"}, "RELEASE_REPORT_SCHEMA")
    lane.require(report["status"] == "passed" and report["runnerErrors"] == 0 and report["unsafeArtifact"] is False
                 and report["suppressedStdoutBytes"] == 0 and report["suppressedStderrBytes"] == 0
                 and len(report["results"]) == expected, "RELEASE_REPORT_FAILED")
    for row in report["results"]:
        lane.require(row["project"] == project and row["status"] == "passed" and row["expectedStatus"] == "passed"
                     and row["errorCount"] == 0 and row["errorLocations"] == [], "RELEASE_TEST_FAILED")


def read_report(path):
    lane.owned(path, 0o600)
    lane.require(path.stat().st_size <= 8 * 1024**2, "RELEASE_REPORT_BOUND")
    return json.loads(path.read_text())


def portable36(summary):
    lane.require(summary["selected"] == 36 and summary["selectedCaseIds"] is None and summary["passed"] == 36
                 and summary["failed"] == 0 and summary["notRun"] == 0 and len(summary["lanes"]) == len(lane.BATCH_LANES), "RELEASE_PORTABLE36")
    lane.require({row["lane"] for row in summary["lanes"]} == set(lane.BATCH_LANES), "RELEASE_PORTABLE36_SELECTION")
    for row in summary["lanes"]:
        count = lane.BATCH_LANES[row["lane"]][3]
        lane.require(row["status"] == "PASS" and row["expected"] == count and row["passed"] == count
                     and row["failed"] == row["notRun"] == 0, "RELEASE_PORTABLE36_FAILED")


class SystemTrust:
    """Temporary own public localhost certificate; never overwrite global bundles."""
    def __init__(self, runner):
        self.runner = runner
        self.path = Path("/usr/local/share/ca-certificates") / ("rct-pr3-local-release-" + runner.nonce + ".crt")
        self.anchor = Path("/etc/ssl/certs") / (self.path.stem + ".pem")
        self.identity = None
        self.intent = False

    def install(self):
        r = self.runner
        cert = r.run / "certs/server.crt"
        lane.require(not self.path.exists() and not self.path.is_symlink() and not self.anchor.exists() and not self.anchor.is_symlink(), "WEBKIT_TRUST_PREEXISTS")
        lane.require(cert.is_file() and not cert.is_symlink() and cert.stat().st_size <= 16384, "WEBKIT_CERT")
        self.hash = digest(cert)
        lane.write(r.run / "webkit-trust-intent.safe.json", json.dumps({"path": str(self.path), "sha256": self.hash, "absentBefore": True}) + "\n")
        self.intent = True
        r.command("WEBKIT_TRUST_INSTALL", ["sudo", "-n", "install", "-m", "644", "--", str(cert), str(self.path)])
        self.check()
        self.identity = (self.path.lstat().st_dev, self.path.lstat().st_ino)
        r.command("WEBKIT_TRUST_UPDATE", ["sudo", "-n", "update-ca-certificates"])
        lane.require(self.anchor.is_symlink() and self.anchor.resolve() == self.path and digest(self.anchor) == self.hash, "WEBKIT_ANCHOR_IDENTITY")

    def check(self):
        info = self.path.lstat()
        lane.require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and stat.S_IMODE(info.st_mode) == 0o644
                     and not self.path.is_symlink() and digest(self.path) == self.hash, "WEBKIT_TRUST_IDENTITY")
        lane.require(self.identity is None or (info.st_dev, info.st_ino) == self.identity, "WEBKIT_TRUST_REPLACED")

    def close(self):
        if not self.intent:
            return
        # The supervised Playwright process group has already exited/reaped here.
        if self.path.exists() or self.path.is_symlink():
            self.check()
            self.runner.command("WEBKIT_TRUST_REMOVE", ["sudo", "-n", "rm", "--", str(self.path)])
        self.runner.command("WEBKIT_TRUST_RESTORE", ["sudo", "-n", "update-ca-certificates"])
        lane.require(not self.path.exists() and not self.path.is_symlink() and not self.anchor.exists() and not self.anchor.is_symlink(), "WEBKIT_TRUST_REMAINS")
        lane.write(self.runner.run / "webkit-trust-result.safe.json", json.dumps({"restored": True, "path": str(self.path)}) + "\n")


class ReplayRunner(lane.Runner):
    def __init__(self, selection, **kwargs):
        super().__init__({"webkit": "client", "homework-sensitivity": "client"}.get(selection, selection), release_lane=selection, **kwargs)
        self.env.update({key: os.environ[key] for key in ("RCT_LOCAL_RELEASE_REVISION", "RCT_LOCAL_RELEASE_NONCE", "RCT_LOCAL_RELEASE_AUDIT")})
        self.env["RCT_LOCAL_RELEASE_MODE"] = os.environ.get("RCT_LOCAL_RELEASE_MODE", "")
        self.selection = selection
        self.deadline = None
        self.source_restored = True
        if selection == "webkit":
            self.env["RCT_E2E_WEBKIT_SUBSET"] = "true"

    def command(self, phase, args, **kwargs):
        if phase == "CLIENT_REMAINING5" and self.selection == "homework-sensitivity":
            from homework_sensitivity import execute
            return execute(self)
        if phase == "CLIENT_REMAINING5" and self.selection == "webkit":
            args = ["--project=webkit" if arg == "--project=chromium" else arg for arg in args]
        if phase in {"CLIENT_REMAINING5", "LESSON_LIFECYCLE", "STAGE17"}:
            self.deadline = time.monotonic() + BUDGETS[self.selection]
        if phase.startswith("UI_BATCH_"):
            selection = phase.removeprefix("UI_BATCH_").lower().replace("_", "-")
            self.deadline = time.monotonic() + {"installation": 300, "geo": 240, "homework": 420, "map": 240, "registry-html": 420, "helper": 300}.get(selection, 600)
        if phase == "INITIAL_ADMINISTRATOR_CLI":
            self.deadline = time.monotonic() + 600
        if self.deadline:
            lane.require(time.monotonic() < self.deadline, "RELEASE_PHASE_DEADLINE")
            signal.setitimer(signal.ITIMER_REAL, max(.01, self.deadline - time.monotonic()))
        else:
            # Preparation budget is explicit and separate from CI test-step budget.
            signal.setitimer(signal.ITIMER_REAL, 1800 if phase.startswith("BUILD_") else 600)
        lane.release_alarm_pending = False
        try:
            return super().command(phase, args, **kwargs)
        finally:
            signal.setitimer(signal.ITIMER_REAL, 0)
            lane.release_alarm_pending = False

    def special_phase(self, name, phase="main", extra_env=None):
        # Keep one aggregate CI step deadline across dependent prepare/pending/resume.
        if getattr(self, "release_budget_lane", None) != name:
            self.release_budget_lane = name
            self.deadline = time.monotonic() + {"bot": 360, "production-service-worker": 300,
                "password-recovery": 600, "registry-native-pdf": 420, "semester-metadata": 540,
                "subject-deletion": 720, "assignment": 720, "today": 240,
                "semester-reconciliation": 660, "application-recovery": 720, "semester-archive": 540}[name]
        return super().special_phase(name, phase, extra_env)

    def execute_batch(self):
        try:
            super().execute_batch()
        except lane.AdmissionError as error:
            lane.require(str(error) == "UI_BATCH_REMAINING_FAILURES_OR_BLOCKED_DEPENDENCIES", "RELEASE_BATCH_FAILURE")
            report = read_report(self.run / "artifacts/ui-batch.safe.json")
            if self.failed_cases is not None:
                self.focused_counts = batch_counts(report, self.failed_cases)
                lane.require(report['selectedCaseIds'] == self.failed_cases and report['selected'] == len(self.failed_cases)
                             and report['passed'] == len(self.failed_cases) and report['failed'] == report['notRun'] == 0,
                             "RELEASE_FAILED_CI_SELECTED_CASES")
            else:
                portable36(report)
            # special13 is separately mandatory in the fixed release catalog.

    def cleanup(self):
        lane.require(self.source_restored, "RELEASE_SOURCE_UNRESTORED_HOLD")
        self.deadline = None
        if self.stopped_history is not None:
            lane.stopped_history([row["id"] for row in self.stopped_history], own_nonce=self.nonce, previous=self.stopped_history)
        return super().cleanup()


def bootstrap_hook(runner):
    revision, nonce, audit = binding(os.environ)
    config = json.loads((runner.run / "compose.json").read_text())
    images = {}
    for key, name in (("auth", "auth-service"), ("academic", "academic-service"), ("postgres", "postgres-academic")):
        tag = config["services"][name]["image"]
        if key == "postgres":
            identifier = lane.capture(["docker", "image", "inspect", "--format", "{{.Id}}", tag]).strip()
            lane.require(re.fullmatch(r"sha256:[0-9a-f]{64}", identifier), "BOOTSTRAP_POSTGRES_IDENTITY")
        else:
            identifier = runner.image_identity(tag)
        lane.require(identifier is not None, "BOOTSTRAP_IMAGE_MISSING")
        images[key] = identifier
    path = audit / "browser-client/bootstrap-provenance.safe.json"
    lane.write(path, json.dumps({"schemaVersion": 1, "event": "LOCAL_CONTROL", "revision": revision, "nonce": nonce,
                               "project": runner.project, "stackStopped": True, "images": images}) + "\n")
    runner.command("INITIAL_ADMINISTRATOR_CLI", [sys.executable, str(ROOT / "scripts/local-release/bootstrap-cli.py"),
                   "--output", str(audit / "bootstrap"), "--auth-image", images["auth"], "--academic-image", images["academic"], "--provenance", str(path)])
    receipt = read_report(audit / "bootstrap/bootstrap-safe.json")
    context = receipt.get("context", {})
    lane.require(receipt.get("schemaVersion") == 1 and receipt.get("status") == "PASS"
                 and receipt.get("cancelled") is False and receipt.get("cleanup") == "PASS" and not receipt.get("holdReasons")
                 and context.get("checkoutSha") == revision and context.get("runIdentity") == "local-" + nonce,
                 "BOOTSTRAP_RECEIPT_FAILED")



def batch_counts(report, cases):
    lane.require(report.get("selectedCaseIds") == cases and report.get("selected") == len(cases)
                 and all(type(report.get(key)) is int and 0 <= report[key] <= len(cases)
                         for key in ("passed", "failed", "notRun"))
                 and sum(report[key] for key in ("passed", "failed", "notRun")) == len(cases),
                 "RELEASE_SELECTED_COUNTS")
    return {key: report[key] for key in ("selected", "passed", "failed", "notRun")}


def focused_observations(runner):
    if runner.selection == "ui-batch":
        return {"batch": getattr(runner, "focused_counts", None)}
    rows = [row for row in runner.special_outcomes if row["lane"] == "semester-metadata"]
    metadata = None
    if rows:
        lane.require(len(rows) == 1 and rows[0]["expected"] == 1
                     and all(type(rows[0][key]) is int and rows[0][key] in (0, 1)
                             for key in ("passed", "failed", "notRun"))
                     and sum(rows[0][key] for key in ("passed", "failed", "notRun")) == 1,
                     "RELEASE_METADATA_COUNTS")
        metadata = {key: rows[0][key] for key in ("expected", "passed", "failed", "notRun")}
    prerequisite = None
    path = runner.run / "prerequisites/result.safe.json"
    if path.exists():
        report = read_report(path)
        lane.require(report.get("schema") == "RCT_LOCAL_LIFECYCLE_PREPARATION_RESULT_V1"
                     and report.get("status") in {"PREPARED", "BLOCKED"}
                     and report.get("reportStatus") in {"MISSING", "REJECTED", "PASS", "FAIL", "BLOCKED"},
                     "RELEASE_PREREQUISITE_COUNTS")
        prerequisite = {"status": report["status"], "reportStatus": report["reportStatus"],
                        "passed": int(report["status"] == "PREPARED")}
    return {"semesterMetadata": metadata, "datasetPrerequisite": prerequisite}


def focused_options(selection, remaining_two=False, headman_only=False):
    if selection == "ui-batch":
        return {"failed_cases": ["HEADMAN_TEACHER_READ"] if remaining_two or headman_only else
                ["HEADMAN_TEACHER_READ", "FULL_ADMIN_RIGHTS", "HOMEWORK_PERIOD"]}
    return {"lifecycle_only": True, "operational_until": "semester-metadata"} if selection == "special13" else {}



def d002_counts(report):
    lane.require(type(report) is dict and report.get("runnerErrors") == 0 and report.get("unsafeArtifact") is False
                 and report.get("suppressedStdoutBytes") == report.get("suppressedStderrBytes") == 0
                 and report.get("status") in {"passed", "failed"} and type(report.get("results")) is list
                 and len(report["results"]) == 1, "D002_REPORT_SCHEMA")
    row = report["results"][0]
    lane.require(row.get("caseId") == lane.D002_CASE and row.get("project") == "chromium"
                 and row.get("file") == "mobile-real-server.spec.ts" and row.get("expectedStatus") == "passed"
                 and row.get("status") in {"passed", "failed", "timedOut", "interrupted", "skipped"}, "D002_EXACT_CASE")
    return {"selected": 1, "passed": int(row["status"] == "passed"),
            "failed": int(row["status"] in {"failed", "timedOut"}),
            "notRun": int(row["status"] in {"interrupted", "skipped"}), "fullAcceptance": False}


def secondary_failure(target, selection, cohort, stage, phase, error):
    """Retain a cleanup witness separately; never replace the original failure."""
    lane.require(stage in {"TRUST_RESTORE", "LOG_CAPTURE", "CLEANUP", "PREFLIGHT_ABSENCE", "REPORT_COUNTS"},
                 "RELEASE_SECONDARY_STAGE")
    result = {"stage": stage, "diagnostic": lane.failure_diagnostic(error, operation=stage),
              "privateTraceback": "UNAVAILABLE", "cancelled": isinstance(error, KeyboardInterrupt)}
    try:
        lane.write(target / ("secondary-" + selection + "-" + (cohort or "single") + "-" + stage.lower() + ".private.log"),
                   "phase=" + phase + "\n" + "".join(traceback.format_exception(error)))
        result["privateTraceback"] = "WRITTEN"
    except BaseException as log_error:
        result["cancelled"] = result["cancelled"] or isinstance(log_error, KeyboardInterrupt)
    return result


def run_cohort(selection, target, cohort=None, *, focused=False, remaining_two=False, headman_only=False, d002_only=False, bundled_dockerfile_frontend=False):
    options = focused_options(selection, remaining_two, headman_only) if focused else {}
    runner = ReplayRunner(selection, special_cohort=cohort, operational=selection == "special13",
                          bundled_dockerfile_frontend=bundled_dockerfile_frontend, client_case=lane.D002_CASE if d002_only else None, **options)
    if focused and selection == 'special13':
        runner.partial_lifecycle_selection = True
    if selection == "client" and not d002_only:
        runner.release_bootstrap_hook = bootstrap_hook
    trust = SystemTrust(runner) if selection == "webkit" else None
    failure = None
    cancelled = False
    cleanup, restored, bootstrap_skipped = False, True, False
    cleanup_state, preallocation = "UNKNOWN", None
    diagnostic, diagnostic_log = None, "NOT_NEEDED"
    secondary = []
    try:
        runner.prepare()
        if focused or d002_only:
            for path in (runner.run / 'framework-private', runner.run / 'framework-private/failed-ci'):
                path.mkdir(mode=0o700, exist_ok=True)
                lane.owned(path, 0o700, directory=True)
        if trust:
            runner.command("WEBKIT_SUDO_AUTH", ["sudo", "-n", "true"])
            trust.install()
        runner.execute()
        if selection in {"client", "webkit", "lesson-lifecycle"}:
            closed_report(read_report(runner.run / "artifacts/safe-results.json"), 1 if d002_only else {"client": 10, "webkit": 1, "lesson-lifecycle": 4}[selection], "webkit" if trust else "chromium")
        if selection == "special13":
            expected = 1 if focused else 6 if cohort == "nonclock" else 7
            rows = [row for row in runner.special_outcomes if not focused or row['lane'] == 'semester-metadata']
            lane.require(sum(row["expected"] for row in rows) == expected and all(row["status"] == "PASS" for row in rows), "RELEASE_SPECIAL_INCOMPLETE")
    except BaseException as error:
        cancelled = isinstance(error, KeyboardInterrupt)
        failure = error
        diagnostic = lane.failure_diagnostic(error, operation="READ_ONLY_PREFLIGHT" if runner.preflight_started and not runner.allocation_started else "RUNNER")
        try:
            lane.write(target / ("failure-" + selection + "-" + (cohort or "single") + ".private.log"),
                       "phase=" + runner.phase + "\n" + "".join(traceback.format_exception(error)))
            diagnostic_log = "WRITTEN"
        except BaseException as log_error:
            cancelled = cancelled or isinstance(log_error, KeyboardInterrupt)
            diagnostic_log = "UNAVAILABLE"
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        runner.deadline = None
        if runner.run.exists():
            if trust:
                try:
                    trust.close()
                except BaseException as error:
                    cancelled = cancelled or isinstance(error, KeyboardInterrupt)
                    restored = False
                    secondary.append(secondary_failure(target, selection, cohort, "TRUST_RESTORE", runner.phase, error))
                    failure = failure or error
            try:
                lane.require(runner.preserve_logs() is not False, "RELEASE_LOGS_INCOMPLETE")
            except BaseException as error:
                cancelled = cancelled or isinstance(error, KeyboardInterrupt)
                secondary.append(secondary_failure(target, selection, cohort, "LOG_CAPTURE", runner.phase, error))
                failure = failure or error
            try:
                if selection == "client" and cancelled:
                    # CI bootstrap is !cancelled(): keep exact own cleanup, but
                    # never begin a fresh administrator/DB operation after cancel.
                    runner.release_bootstrap_hook = None
                    bootstrap_skipped = True
                runner.cleanup()
                cleanup = True
                cleanup_state = "PASS"
            except BaseException as error:
                cancelled = cancelled or isinstance(error, KeyboardInterrupt)
                secondary.append(secondary_failure(target, selection, cohort, "CLEANUP", runner.phase, error))
                failure = failure or error
        elif failure is not None and not cancelled:
            try:
                preallocation = lane.preallocation_failure_closed(runner)
                if preallocation is not None:
                    cleanup, cleanup_state = True, "NOT_REQUIRED"
            except BaseException as observation_error:
                # Failed/unknown Docker observation must retain HOLD, not replace primary.
                cancelled = cancelled or isinstance(observation_error, KeyboardInterrupt)
                secondary.append(secondary_failure(target, selection, cohort, "PREFLIGHT_ABSENCE", runner.phase, observation_error))
        observed = None
        if d002_only and runner.run.exists():
            try:
                observed = d002_counts(read_report(runner.run / "artifacts/safe-results.json"))
            except BaseException as error:
                cancelled = cancelled or isinstance(error, KeyboardInterrupt)
                secondary.append(secondary_failure(target, selection, cohort, "REPORT_COUNTS", runner.phase, error))
                failure = failure or error
        if focused:
            try:
                observed = focused_observations(runner)
            except BaseException as error:
                cancelled = cancelled or isinstance(error, KeyboardInterrupt)
                secondary.append(secondary_failure(target, selection, cohort, "REPORT_COUNTS", runner.phase, error))
                failure = failure or error
        cancelled = cancelled or any(item["cancelled"] for item in secondary)
        if failure is not None and diagnostic is None:
            diagnostic = lane.failure_diagnostic(failure, operation="RUNNER")
            try:
                lane.write(target / ("failure-" + selection + "-" + (cohort or "single") + ".private.log"),
                           "phase=" + runner.phase + "\n" + "".join(traceback.format_exception(failure)))
                diagnostic_log = "WRITTEN"
            except BaseException as log_error:
                cancelled = cancelled or isinstance(log_error, KeyboardInterrupt)
                diagnostic_log = "UNAVAILABLE"
        restored = restored and runner.source_restored
        status = "HOLD" if not restored or not cleanup else "CANCELLED" if cancelled else "FAIL" if failure else "PASS"
        result = {"bootstrap": {"status": "NOT_RUN", "reason": "CANCELLED"} if bootstrap_skipped else None, "cancelled": cancelled, "status": status, "cleanup": cleanup, "restoration": restored, "run": str(runner.run), "cohort": cohort,
                  "errorKind": (type(failure).__name__ if type(failure).__name__ in {"AdmissionError","TimeoutError","TimeoutExpired","KeyboardInterrupt","OSError","ValueError","KeyError","TypeError","CalledProcessError","FileNotFoundError","PermissionError","JSONDecodeError"} else "OTHER") if failure else None, "phase": runner.phase,
                  "cleanupState": cleanup_state, "preallocation": preallocation,
                  "failureDiagnostic": diagnostic, "privateTraceback": diagnostic_log, "secondaryFailures": secondary, "observedCounts": observed}
        lane.write(target / ("cohort-" + (selection + "-" if focused else "") + (cohort or "single") + ".safe.json"), json.dumps(result) + "\n")
    return result


def controls(target):
    for index, args in enumerate(CONTROLS):
        with (target / ("control-%02d.private.log" % index)).open("xb") as output:
            result = subprocess.run(args, cwd=ROOT / "tests/e2e", stdout=output, stderr=subprocess.STDOUT, timeout=180 if "--with-consumer" in args else 60, check=False)
        lane.require(result.returncode == 0, "RELEASE_CONTROL_FAILED")
    with (target / "clock.private.log").open("xb") as output:
        subprocess.run(["bash", "gradlew", "e2eClockFixtureJar", "e2eClockFixtureControl", "--no-daemon", "--max-workers=1"], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=600, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=MODES)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--d002-only", action="store_true",
                           help="client only: exact D002 reconnect case, partial acceptance and private raw framework log")
    selection.add_argument("--headman-only", action="store_true",
                           help="failed-ci only: HEADMAN_TEACHER_READ, without metadata or dataset prerequisite")
    selection.add_argument("--remaining-two", action="store_true",
                        help="failed-ci only: HEADMAN_TEACHER_READ and SEMESTER_METADATA plus canonical dataset prerequisite")
    parser.add_argument("--bundled-dockerfile-frontend", action="store_true",
                        help="failed-ci only: reuse the guarded Docker Engine bundled frontend")
    args = parser.parse_args()
    lane.require(not args.d002_only or args.mode == "client", "D002_CLIENT_ONLY")
    lane.require(not args.bundled_dockerfile_frontend or args.mode == "failed-ci" or args.d002_only,
                 "BUNDLED_FRONTEND_FAILED_CI_ONLY")
    lane.require(not args.remaining_two or args.mode == "failed-ci", "REMAINING_TWO_FAILED_CI_ONLY")
    lane.require(not args.headman_only or args.mode == "failed-ci", "HEADMAN_ONLY_FAILED_CI_ONLY")
    os.umask(0o077)
    revision, nonce, audit = binding(os.environ)
    target = audit / ("browser-" + args.mode)
    target.mkdir(mode=0o700)
    rows, status, cancelled = [], "FAIL", False
    try:
        if args.mode == "controls":
            controls(target)
            status = "PASS"
        else:
            lane.preflight(release=True)
            lane.RUNS.mkdir(parents=True, exist_ok=True, mode=0o700)
            lane.owned(lane.RUNS, 0o700, directory=True)
            fd = os.open(lane.RUNS / "port-8443.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, "r+") as lock:
                lane.owned(lane.RUNS / "port-8443.lock", 0o600)
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                lane.preflight(release=True)
                selections = [('ui-batch', None), ('special13', 'lifecycle')] if args.mode == 'failed-ci' else [(args.mode, cohort) for cohort in (("nonclock", "lifecycle") if args.mode == "special13" else (None,))]
                if args.headman_only:
                    selections = [('ui-batch', None)]
                for selection, cohort in selections:
                    row = run_cohort(selection, target, cohort, focused=args.mode == 'failed-ci',
                                     remaining_two=args.remaining_two, headman_only=args.headman_only, d002_only=args.d002_only,
                                     bundled_dockerfile_frontend=args.bundled_dockerfile_frontend)
                    rows.append(row)
                    cancelled = cancelled or row["cancelled"]
                    if row["status"] == "HOLD" or row["cancelled"]:
                        break
                status = "HOLD" if any(r["status"] == "HOLD" for r in rows) else "CANCELLED" if cancelled else "PASS" if all(r["status"] == "PASS" for r in rows) else "FAIL"
    except BaseException as error:
        cancelled = isinstance(error, KeyboardInterrupt)
        status = "HOLD"
        try:
            lane.write(target / "outer-failure.private.log", "".join(traceback.format_exception(error)))
            lane.write(target / "outer-failure.safe.json", json.dumps(lane.failure_diagnostic(error, operation="OUTER_PREFLIGHT_OR_LOCK")) + "\n")
        except BaseException:
            pass
        raise
    finally:
        lane.write(target / "result.safe.json", json.dumps({"schemaVersion": 1, "event": "LOCAL_CONTROL", "revision": revision, "nonce": nonce,
                   "mode": args.mode, "status": status, "cancelled": cancelled, "cleanup": (args.mode == "controls" and status == "PASS") or bool(rows) and all(row["cleanup"] for row in rows) and status != "HOLD", "cohorts": rows,
                   "remainingTwo": args.remaining_two, "headmanOnly": args.headman_only,
                   **({"selectedCaseIds": [lane.D002_CASE], "selected": 1, "fullAcceptance": False} if args.d002_only else {}),
                   "qualification": "Fresh cohorts serially; CI shared-job order/state and hosted hardware differ. No hosted CI identity."}) + "\n")
    return exit_code(status, cancelled)


def exit_code(status, cancelled):
    return 130 if cancelled else 0 if status == "PASS" else 2 if status == "HOLD" else 1


def defer_alarm(*_):
    # Never throw between Popen returning and self.child publication. The existing
    # supervisor observes the flag only after registration and reaps its process group.
    lane.release_alarm_pending = True


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    signal.signal(signal.SIGALRM, defer_alarm)
    sys.exit(main())
