"""Disposable Ubuntu browser lanes. No hosted CI identity and no private stand environment.

Run with the activated local-stand Python: python3 scripts/local_e2e_async.py openapi
All child output is private; only closed phase/status receipts reach the terminal.
"""

import argparse
import datetime
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import selectors
import shutil
import signal
import socket
import ssl
import stat
import subprocess
import sys
import ast
import traceback
import time
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
RUNS = ROOT / ".agent/evidence/local-runs"
LABEL = "rct.local.stage17"
BASELINE = "browser-v2-split-20261018-v1"
APPS = {"auth-service", "academic-service", "schedule-service", "attendance-service",
        "document-renderer-service", "mobile-bff", "api-gateway", "notification-web"}
SERVICES = APPS | {"postgres-academic", "postgres-schedule", "mongo-attendance", "redis",
                   "rabbitmq", "web-nginx", "pwa-nginx", "mini-app-nginx", "nginx"}
TOKEN_NAMES = {"ACADEMIC_TO_ATTENDANCE_SERVICE_TOKEN", "SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN",
               "ATTENDANCE_TO_ACADEMIC_SERVICE_TOKEN"}
COMPOSE = ["docker-compose.e2e.yml", "tests/e2e/infra/docker-compose.clock-fixture.yml",
           "tests/e2e/infra/docker-compose.synthetic-baseline.yml"]
MODES = {"prepare", "openapi", "full", "client", "lesson-lifecycle", "ui-batch", "special13"}
# Named remaining scenarios only. No passed client10/lesson4/DOCX1 replay.
# config, one extended flag, private output variable, expected count, safe result filenames.
BATCH_LANES = {
    "auth-smoke": ("playwright.config.ts", None, None, 9, {"auth-token-lifecycle.spec.ts", "mini-app.spec.ts"}),
    "first3": ("playwright.first3.config.ts", None, "RCT_FOCUS3_RUN", 3, {"mobile-real-server.spec.ts"}),
    "admin": ("playwright.real-mobile.config.ts", "RCT_E2E_ADMIN_LIFECYCLE", None, 1, {"mobile-real-server.spec.ts"}),
    "role-security-export": ("playwright.real-mobile.config.ts", "RCT_E2E_ROLE_LOGOUT", None, 6, {"mobile-real-server.spec.ts", "cloud-accepted-role-authority.spec.ts", "cloud-accepted-admin-grants.spec.ts", "cloud-accepted-auth-security.spec.ts"}),
    "installation": ("playwright.real-mobile.config.ts", "RCT_E2E_SW_LIFECYCLE", None, 2, {"mobile-real-server.spec.ts"}),
    "geo": ("playwright.teaching-clock.config.ts", None, "RCT_E2E_GEO_RUN", 1, {"teaching-clock-geo.spec.ts"}),
    "homework": ("playwright.homework-lifecycle.config.ts", None, "RCT_HOMEWORK_LIFECYCLE_RUN", 4, {"mobile-homework-lifecycle.spec.ts"}),
    "map": ("playwright.map-lifecycle.config.ts", None, "RCT_MAP_LIFECYCLE_RUN", 1, {"mobile-map-lifecycle.spec.ts"}),
    "registry-html": ("playwright.registry-export.config.ts", None, "RCT_REGISTRY_EXPORT_RUN", 3, {"cloud-accepted-registry-export.spec.ts"}),
    "helper": ("playwright.helper-capabilities.config.ts", None, "RCT_HELPER_CAPABILITIES_RUN", 6, {"cloud-accepted-helper-capabilities.spec.ts"}),
}
BATCH_BLOCKED = [
    {"lane": "bot", "count": 1, "reason": "HOSTED_BOT_IDENTITY_ADAPTER_REQUIRED"},
    {"lane": "production-service-worker", "count": 1, "reason": "HOSTED_STATIC_CONTAINER_ADAPTER_REQUIRED"},
    {"lane": "password-recovery", "count": 1, "reason": "NONCE_OWNED_OTP_BROKER_ADAPTER_REQUIRED"},
    {"lane": "registry-native-pdf", "count": 3, "reason": "NONCE_OWNED_RENDERER_AUDIT_AND_OUTAGE_RESTORE_REQUIRED"},
    {"lane": "semester-assignment-recovery-today", "count": 7, "reason": "SEPARATE_LIFECYCLE_CLOCK_AND_RESTORE_ADMISSION_REQUIRED"},
]


FAILED_CASES = {
    'TMA_SIGNED_ENTRY': ('auth-smoke', 'signed teacher entry loads the real session and shared profile with host Back'),
    'TMA_TAMPERED_ENTRY': ('auth-smoke', 'tampered Telegram proof cannot expose role data or navigation'),
    'HEADMAN_TEACHER_READ': ('first3', 'HEADMAN marks the real roster and TEACHER reads without acquiring write permission'),
    'ADMIN_HISTORICAL_MARK': ('admin', 'ADMIN creates a multirole headman and restores the archived account without old authority'),
    'FULL_ADMIN_RIGHTS': ('role-security-export', 'FULL multirole ADMIN assigns updates and revokes LIMITED rights through Vue without foreign-role calls @cloud-accepted'),
    'OWN_PROFILE_THEME': ('role-security-export', 'own profile security changes password and replacement login rejects old authority @cloud-accepted'),
    'HOMEWORK_NOTIFICATION': ('homework', 'MANAGE_HOMEWORK non-author edits preserve student completion and revoked authority cannot overwrite it @real-mobile'),
    'HELPER_PERMISSIONS': ('helper', 'HEADMAN edits helper capabilities through member row and retains VIEW_STATS @cloud-accepted'),
}


def selected_batch_cases(cases):
    if cases is None:
        return None
    require(type(cases) is list and 0 < len(cases) <= len(FAILED_CASES)
            and all(type(case) is str and case in FAILED_CASES for case in cases)
            and len(set(cases)) == len(cases), "BATCH_INVALID_CASE_SELECTION")
    return [case for case in FAILED_CASES if case in cases]


def selected_batch_lanes(cases):
    return [lane for lane in BATCH_LANES if cases is None or any(FAILED_CASES[case][0] == lane for case in cases)]


def batch_result(report, code, lane, cases=None):
    """Only a complete safe named test result may be an ordinary collectable FAIL."""
    require(type(report) is dict, "BATCH_INVALID_REPORT")
    rows = report.get("results")
    expected, files = BATCH_LANES[lane][3:]
    lane_cases = [case for case in cases or [] if FAILED_CASES[case][0] == lane]
    if cases is not None:
        expected = len(lane_cases)
    require(type(rows) is list and len(rows) <= expected and all(type(row) is dict for row in rows)
            and type(report.get("runnerErrors")) is int and report["runnerErrors"] == 0
            and report.get("unsafeArtifact") is False, "BATCH_INCOMPLETE_OR_UNSAFE_REPORT")
    require(len({(row.get("file"), row.get("line"), row.get("project")) for row in rows}) == len(rows)
            and all(row.get("file") in files and type(row.get("line")) is int and row["line"] > 0
                    and row.get("project") == "chromium"
                    and (row.get("expectedStatus") == "passed" or row.get("expectedStatus") == "skipped" and row.get("status") == "skipped")
                    and row.get("status") in {"passed", "failed", "timedOut", "skipped"} for row in rows),
            "BATCH_UNEXPECTED_OR_UNSELECTED_TEST")
    if cases is not None:
        require(all(row.get("failedCaseId") in lane_cases for row in rows)
                and len({row["failedCaseId"] for row in rows}) == len(rows), "BATCH_UNSELECTED_CASE")
    failed = sum(row["status"] in {"failed", "timedOut"} for row in rows)
    passed = sum(row["status"] == "passed" for row in rows)
    not_run = expected - passed - failed
    require((code == 0 and failed == 0 and report.get("status") == "passed")
            or (code == 1 and (failed > 0 or not_run > 0) and report.get("status") in {"failed", "timedout"}),
            "BATCH_EXIT_REPORT_MISMATCH")
    return {"lane": lane, "status": "FAIL" if failed else "INCOMPLETE" if not_run else "PASS", "expected": expected,
            "passed": passed, "failed": failed, "notRun": not_run,
            "failures": [{key: row[key] for key in ("file", "line", "project", "status")}
                         for row in rows if row["status"] != "passed"]}


def batch_health(value):
    """One post-lane readiness snapshot; Running alone does not prove a declared healthcheck."""
    config = json.loads((Path(value["run"]) / "compose.json").read_text())
    for row in inventory(value["project"], value["nonce"], complete=True):
        name = row["Config"]["Labels"]["com.docker.compose.service"]
        check = config["services"][name].get("healthcheck")
        if check and not check.get("disable", False) and check.get("test") != ["NONE"]:
            require(row["State"].get("Health", {}).get("Status") == "healthy", "BATCH_COHORT_NOT_HEALTHY")


def bundled_dockerfiles(config):
    """Prepare local inline copies; never rewrite tracked Dockerfiles or custom syntax."""
    records = {}
    for name, service in config["services"].items():
        build = service.get("build")
        if build is None:
            continue
        require(type(build) is dict and not build.get("dockerfile_inline")
                and "BUILDKIT_SYNTAX" not in build.get("args", {}), "BUNDLED_FRONTEND_BUILD_UNSUPPORTED")
        context = Path(os.path.abspath(ROOT / build["context"]))
        path = Path(os.path.abspath(context / build.get("dockerfile", "Dockerfile")))
        require(context.is_relative_to(ROOT) and context.resolve() == context and context.is_dir()
                and path.is_relative_to(ROOT) and path.resolve() == path, "BUNDLED_DOCKERFILE_OUTSIDE_SOURCE")
        info = path.lstat()
        require(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= 2 * 1024**2,
                "BUNDLED_DOCKERFILE_NOT_REGULAR_OR_BOUNDED")
        relative = path.relative_to(ROOT).as_posix()
        require(capture(["git", "ls-files", "--error-unmatch", "-z", "--", relative]).rstrip("\0") == relative,
                "BUNDLED_DOCKERFILE_NOT_TRACKED")
        for ignore in (Path(str(path) + ".dockerignore"), context / "Dockerfile.dockerignore"):
            require(not ignore.exists() and not ignore.is_symlink(), "BUNDLED_DOCKERFILE_SPECIFIC_IGNORE")
        with path.open("rb") as stream:
            original = stream.read(2 * 1024**2 + 1)
        require(0 < len(original) <= 2 * 1024**2 and not original.startswith(b"\xef\xbb\xbf"),
                "BUNDLED_DOCKERFILE_NOT_BOUNDED_UTF8")
        text = original.decode("utf-8", errors="strict")
        lines = text.splitlines(keepends=True)
        removed = lines[0].rstrip("\r\n") == "# syntax=docker/dockerfile:1.7"
        effective = "".join(lines[1:]) if removed else text
        require(effective.strip() and not re.search(r"(?im)^[ \t]*#[ \t]*syntax[ \t]*=", effective),
                "BUNDLED_DOCKERFILE_UNKNOWN_SYNTAX")
        # compose.json is parsed again before each build: preserve every literal dollar.
        build["dockerfile_inline"] = effective.replace("$", "$$")
        build.pop("dockerfile", None)
        records[name] = {"path": relative, "original_sha256": hashlib.sha256(original).hexdigest(),
                         "effective_sha256": hashlib.sha256(effective.encode("utf-8")).hexdigest(),
                         "syntax_1_7_removed": removed}
    require(records, "BUNDLED_DOCKERFILES_MISSING")
    return records


def validate_bundled_dockerfiles(config, records):
    """Compose config re-escapes dollars on output; equality proves its parse round trip."""
    for name, record in records.items():
        build = config["services"][name]["build"]
        inline = build.get("dockerfile_inline")
        require(type(inline) is str and "dockerfile" not in build,
                "BUNDLED_DOCKERFILE_ROUNDTRIP")
        effective = inline.replace("$$", "$")
        require(inline == effective.replace("$", "$$")
                and hashlib.sha256(effective.encode("utf-8")).hexdigest() == record["effective_sha256"],
                "BUNDLED_DOCKERFILE_ROUNDTRIP")

NODE_TLS_PROBE = r"""
const fs=require('fs'),https=require('https'),{X509Certificate}=require('crypto');
const expected=new X509Certificate(fs.readFileSync(process.argv[1]));
let finished=false;
function done(ok){if(finished)return;finished=true;process.stdout.write(JSON.stringify({verified:ok})+'\n');process.exitCode=ok?0:1;}
const request=https.get(process.argv[2],{agent:false,rejectUnauthorized:true},response=>{
  try{const peer=response.socket.getPeerCertificate();done(response.socket.authorized===true&&Buffer.isBuffer(peer.raw)&&peer.raw.equals(expected.raw));}
  catch{done(false);} finally{response.destroy();}
});
request.setTimeout(5000,()=>{done(false);request.destroy();});
request.on('error',()=>done(false));
"""


def validate_node_ca(certificate, environment, expected_digest):
    info = certificate.lstat()
    require(certificate.resolve() == certificate and not certificate.is_symlink()
            and stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and not info.st_mode & 0o022
            and 0 < info.st_size <= 16384 and environment.get("NODE_EXTRA_CA_CERTS") == str(certificate)
            and hashlib.sha256(certificate.read_bytes()).hexdigest() == expected_digest, "NODE_CA_IDENTITY")


def node_tls_probe(certificate, environment, url="https://localhost:8443/app/"):
    observed = capture(["node", "-e", NODE_TLS_PROBE, str(certificate), url], env=environment, seconds=10)
    require(json.loads(observed) == {"verified": True}, "NODE_TLS_PEER_IDENTITY")


def retain_container_log(identifier, path, *, deadline, byte_bound=16 * 1024**2, seconds=15):
    """All currently retained Docker logs, not history already lost to Docker rotation."""
    owned(path.parent, 0o700, directory=True)
    scope = "CURRENT_RETAINED_DOCKER_LOGS_ROTATION_MAY_HAVE_DROPPED_HISTORY"

    def collect(target, args, limit, end):
        count, status, eof = 0, "COMPLETE_RETAINED", False
        with os.fdopen(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), "wb") as stream:
            child = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                with selectors.DefaultSelector() as ready:
                    ready.register(child.stdout, selectors.EVENT_READ)
                    while True:
                        remaining = end - time.monotonic()
                        if remaining <= 0 or not ready.select(remaining):
                            status = "TIME_BOUND"
                            break
                        block = os.read(child.stdout.fileno(), min(65536, limit - count + 1))
                        if not block:
                            eof = True
                            break
                        available = min(len(block), limit - count)
                        stream.write(block[:available])
                        count += available
                        if available != len(block):
                            status = "BYTE_BOUND"
                            break
                if eof:
                    try:
                        if child.wait(timeout=max(0.01, end - time.monotonic())) != 0:
                            status = "COMMAND_FAILED"
                    except subprocess.TimeoutExpired:
                        status = "TIME_BOUND"
            finally:
                if child.poll() is None:
                    child.kill()
                child.wait(timeout=1)
                child.stdout.close()
        return {"status": status, "bytes": count, "complete": status == "COMPLETE_RETAINED"}

    started = time.monotonic()
    if started >= deadline:
        result = {"status": "BUDGET_EXHAUSTED", "bytes": 0, "complete": False}
    else:
        result = collect(path, ["docker", "logs", "--timestamps", identifier], byte_bound, min(deadline, started + seconds))
    result.update(scope=scope, byte_bound=byte_bound, time_bound_seconds=seconds)
    if not result["complete"]:
        if time.monotonic() < deadline:
            result["late_tail"] = collect(path.with_name(path.name + ".tail"),
                    ["docker", "logs", "--timestamps", "--tail", "2000", identifier], 1024**2, min(deadline, time.monotonic() + 5))
        else:
            result["late_tail"] = {"status": "BUDGET_EXHAUSTED", "bytes": 0, "complete": False}
    result["elapsed_ms"] = round((time.monotonic() - started) * 1000)
    write(path.with_name(path.name + ".meta.safe.json"), json.dumps(result, indent=2) + "\n")
    return result


class AdmissionError(RuntimeError):
    """Only authored, bounded classification codes cross the console boundary."""

    def __init__(self, code, *, returncode=None):
        super().__init__(code)
        self.returncode = returncode if type(returncode) is int and -128 <= returncode <= 255 else None


def require(condition, code):
    if not condition:
        raise AdmissionError(code)


def capture(args, *, env=None, seconds=20):
    return subprocess.run(args, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=True, check=True, timeout=seconds).stdout


def capture_before(args, deadline=None):
    if deadline is None:
        return capture(args)
    remaining = deadline - time.monotonic()
    require(remaining > 0, "SPECIAL_RENDERER_HEALTH_WAIT_EXPIRED")
    return capture(args, seconds=min(20, remaining))


def emit(phase, **values):
    print(json.dumps({"phase": phase, **values}), flush=True)


def write(path, value, mode=0o600):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, mode), "w") as file:
        file.write(value)


def owned(path, mode, *, directory=False):
    info = path.lstat()
    require((stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode))
            and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == mode
            and not path.is_symlink() and (directory or info.st_nlink == 1), "UNSAFE_OWNED_PATH")
    return info


def manifest(path):
    require(path.is_absolute() and path.parent.parent == RUNS and path.name == "owner.json", "MANIFEST_PATH")
    owned(RUNS, 0o700, directory=True)
    owned(path.parent, 0o700, directory=True)
    info = owned(path, 0o600)
    require(0 < info.st_size < 8 * 1024**2, "MANIFEST_BOUND")
    value = json.loads(path.read_text())
    require(value["schema"] == 1 and value["uid"] == os.getuid()
            and value["root"] == str(ROOT) and value["run"] == str(path.parent)
            and re.fullmatch(r"[0-9a-f]{24}", value["nonce"])
            and value["project"] == "rct-local-stage17-" + value["nonce"][:12]
            and path.parent.name == value["project"], "MANIFEST_IDENTITY")
    require(value.get("mode") in MODES, "MANIFEST_MODE")
    require(value["source_sha"] == capture(["git", "rev-parse", "HEAD"]).strip(), "SOURCE_REVISION_CHANGED")
    return value


def inventory(project, nonce, *, complete=False, deadline=None):
    ids = capture_before(["docker", "ps", "-aq", "--no-trunc", "--filter",
                   "label=com.docker.compose.project=" + project], deadline).splitlines()
    rows = json.loads(capture_before(["docker", "inspect", *ids], deadline)) if ids else []
    names = set()
    for row in rows:
        labels = row["Config"].get("Labels", {})
        name = labels.get("com.docker.compose.service")
        require(labels.get(LABEL) == nonce and labels.get("com.docker.compose.project") == project
                and name in SERVICES and name not in names, "FOREIGN_CONTAINER")
        names.add(name)
        if complete:
            require(row["State"]["Running"] is True, "COHORT_NOT_RUNNING")
    if complete:
        require(names == SERVICES and len(rows) == len(SERVICES), "COHORT_NOT_COMPLETE")
    return rows


def validate_local(*, complete=True, environment=None, expected_mode=None, expected_batch_lane=None, expected_special_lane=None, expected_special_phase=None):
    """Called before pipeline startup and again immediately before the seed write."""
    environment = os.environ if environment is None else environment
    require(sys.platform == "linux" and not environment.get("GITHUB_ACTIONS")
            and not environment.get("RUNNER_ENVIRONMENT"), "LOCAL_NOT_HOSTED")
    value = manifest(Path(environment.get("RCT_LOCAL_STAGE17_MANIFEST", "")))
    if value["mode"] == "special13":
        validate_special_environment(value, environment, expected_special_lane, expected_special_phase)
    else:
        require(expected_special_lane is None and expected_special_phase is None, "SPECIAL_MODE_REQUIRED")
        validate_lane_environment(value, environment, expected_mode, expected_batch_lane)
    require(expected_mode is None or expected_mode == value["mode"], "LOCAL_LANE_MISMATCH")
    for name, expected in value["sources"].items():
        require(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected, "SOURCE_BYTES_CHANGED")
    run = Path(value["run"])
    validate_node_ca(run / "certs/server.crt", environment, value["node_ca_sha256"])
    if value["mode"] == "special13":
        require(environment.get("SSL_CERT_FILE") == str(run / "certs/server.crt"), "SPECIAL_PYTHON_TLS_CA")
    require(hashlib.sha256((run / "compose.json").read_bytes()).hexdigest() == value["compose_sha256"], "COMPOSE_CHANGED")
    require((run / "clock/disposable-target").read_text() == "RCT_E2E_DISPOSABLE\n", "CLOCK_MARKER")
    owned(run / "artifacts", 0o700, directory=True)
    # The launcher retains this exclusive lock throughout children and cleanup.
    with (RUNS / "port-8443.lock").open("r+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass
        else:
            raise AdmissionError("LAUNCHER_LOCK_MISSING")
    if value["mode"] == "special13":
        special_inventory(value, complete=complete, expected_lane=environment.get("RCT_LOCAL_SPECIAL_LANE"), expected_phase=environment.get("RCT_LOCAL_SPECIAL_PHASE"))
        return value
    rows = inventory(value["project"], value["nonce"], complete=complete)
    if complete:
        config = json.loads((run / "compose.json").read_text())
        for row in rows:
            name = row["Config"]["Labels"]["com.docker.compose.service"]
            require(row["Config"]["Image"] == config["services"][name]["image"], "COHORT_IMAGE_CHANGED")
            require(set(row["NetworkSettings"]["Networks"]) == {value["project"] + "_private_net"}, "COHORT_NETWORK_CHANGED")
        edge = next(row for row in rows if row["Config"]["Labels"]["com.docker.compose.service"] == "nginx")
        require(edge["HostConfig"]["PortBindings"] == {
            "80/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8088"}],
            "443/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8443"}]}, "EDGE_BINDINGS_CHANGED")
        network = json.loads(capture(["docker", "network", "inspect", value["project"] + "_private_net"]))[0]
        require(network["Labels"].get(LABEL) == value["nonce"]
                and network["Labels"].get("com.docker.compose.project") == value["project"], "FOREIGN_NETWORK")
        for row in rows:
            name = row["Config"]["Labels"]["com.docker.compose.service"]
            if name in APPS:
                actual = dict(item.split("=", 1) for item in row["Config"]["Env"])
                expected = config["services"][name]["environment"]
                require(all(actual.get(key) == str(item) for key, item in expected.items()), "COHORT_ENV_CHANGED")
            for mount in config["services"][name].get("volumes", []):
                if mount.get("type") == "bind":
                    require(any(item.get("Source") == mount["source"] and item.get("Destination") == mount["target"]
                                and item.get("RW") is False for item in row["Mounts"]), "COHORT_BIND_CHANGED")
    return value


def validate_lane_environment(value, environment, expected_mode=None, expected_batch_lane=None):
    """Mode and environment are a single manifest-bound admission decision."""
    mode = value["mode"]
    require(mode in MODES, "MANIFEST_MODE")
    require(environment.get("RCT_LOCAL_STAGE17_MODE") == mode
            and (expected_mode is None or mode == expected_mode), "LOCAL_LANE_MISMATCH")
    lane = environment.get("RCT_LOCAL_UI_BATCH_LANE")
    expected_run = value["run"]
    if mode == "ui-batch":
        cases = selected_batch_cases(value.get("batch_cases"))
        require(value.get("batch_cases") == cases and value.get("batch_lanes") == selected_batch_lanes(cases), "LOCAL_BATCH_MANIFEST")
        require(environment.get("RCT_LOCAL_UI_BATCH_CASES", "") == (json.dumps(cases) if cases is not None else ""), "LOCAL_BATCH_CASE_BINDING")
        require((lane is None and expected_batch_lane is None and expected_mode is None)
                or (lane in value["batch_lanes"] and expected_batch_lane in (None, lane)), "LOCAL_BATCH_LANE")
        if lane:
            expected_run += "/lanes/" + lane
            owned(Path(expected_run), 0o700, directory=True)
            owned(Path(expected_run) / "artifacts", 0o700, directory=True)
        for name in {row[2] for row in BATCH_LANES.values() if row[2]}:
            require(environment.get(name, "") == (expected_run if lane and BATCH_LANES[lane][2] == name else ""), "LOCAL_BATCH_OUTPUT_BINDING")
        require(environment.get("RCT_FOCUS3_OBSERVATIONS", "") ==
                (expected_run + "/supervision/observations.safe.json" if lane == "first3" else ""), "LOCAL_BATCH_OBSERVATION_BINDING")
    else:
        require(lane is None and expected_batch_lane is None and not environment.get("RCT_LOCAL_UI_BATCH_CASES"), "LOCAL_BATCH_MODE_REQUIRED")
    diagnostic = value.get("startup_diagnostic", False)
    require(type(diagnostic) is bool and (not diagnostic or mode == "client")
            and environment.get("RCT_LOCAL_CLIENT_STARTUP_DIAGNOSTIC", "false") == ("true" if diagnostic else "false"),
            "LOCAL_DIAGNOSTIC_MISMATCH")
    require(environment.get("RCT_LOCAL_STAGE17_NONCE") == value["nonce"]
            and environment.get("RCT_REMAINING5_RUN") == expected_run
            and environment.get("RCT_E2E_TEACHING_CLOCK_DIR") == value["run"] + "/clock"
            and environment.get("E2E_BASE_URL") == "https://localhost:8443"
            and all(environment.get(name) == "true" for name in
                    ("RCT_E2E_REMAINING5", "RCT_E2E_SYNTHETIC_SEED"))
            and environment.get("RCT_E2E_ASYNC_REPORT") == ("false" if mode in {"client", "lesson-lifecycle", "ui-batch", "special13"} else "true")
            and environment.get("RCT_LESSON_LIFECYCLE_RUN", "") == (value["run"] if mode == "lesson-lifecycle" else ""),
            "LOCAL_STAGE17_ONLY")
    for flag in ("RCT_E2E_ADMIN_LIFECYCLE", "RCT_E2E_ROLE_LOGOUT", "RCT_E2E_WEBKIT_SUBSET",
                 "RCT_E2E_SW_LIFECYCLE", "RCT_E2E_BOT_ROLE_MENU", "RCT_APP_RECOVERY_PROJECT"):
        expected_flag = BATCH_LANES[lane][1] if mode == "ui-batch" and lane else None
        require(not environment.get(flag) if flag == "RCT_APP_RECOVERY_PROJECT"
                else environment.get(flag) == "true" if flag == expected_flag
                else environment.get(flag) != "true", "LOCAL_STAGE17_ONLY")


DATABASES = {"postgres-academic", "postgres-schedule", "mongo-attendance"}
SPECIAL_NONCLOCK = ("bot", "production-service-worker", "password-recovery", "registry-native-pdf")
SPECIAL_LIFECYCLE = ("semester-metadata", "subject-deletion", "assignment", "today", "semester-reconciliation", "application-recovery", "semester-archive")
# Existing config, output variable, exact original titles; phases keep one logical story.
SPECIAL_LANES = {
    "bot": ("playwright.real-mobile.config.ts", None, ["actual private bot menu roster and teacher Today use hosted current authority"]),
    "production-service-worker": ("playwright.real-mobile.config.ts", None, ["production worker update activates through the update gate and retains a warm launch"]),
    "password-recovery": ("playwright.real-mobile.config.ts", None, ["password recovery consumes its own delivered OTP and admits the replacement password @cloud-accepted"]),
    "registry-native-pdf": ("playwright.registry-export.config.ts", "RCT_REGISTRY_EXPORT_RUN", ["FULL native user PDF preserves selected ordered fields and its committed audit @cloud-accepted", "LIMITED native user PDF preserves selected ordered fields and its committed audit @cloud-accepted", "renderer outage terminates its PDF job safely and a fresh job downloads after recovery @cloud-accepted"]),
    "semester-metadata": ("playwright.semester-lifecycle.config.ts", "RCT_SEMESTER_LIFECYCLE_RUN", ["FULL admin manages owned semester metadata and historical group promotion through the ready UI"]),
    "subject-deletion": ("playwright.semester-lifecycle.config.ts", "RCT_SEMESTER_LIFECYCLE_RUN", ["HEADMAN deletes one subject through real proof and participants while mixed EXCUSE keeps the control subject"]),
    "assignment": ("playwright.assignment-lifecycle.config.ts", "RCT_ASSIGNMENT_LIFECYCLE_RUN", ["assignment lifecycle API phase"]),
    "today": ("playwright.teaching-clock.config.ts", "RCT_E2E_GEO_RUN", ["student uses Today quick request after the genuine first lesson closes @cloud-accepted"]),
    "semester-reconciliation": ("playwright.semester-lifecycle.config.ts", "RCT_SEMESTER_LIFECYCLE_RUN", ["FULL admin reconciles one exact stranded close before archive completion"]),
    "application-recovery": ("playwright.application-recovery.config.ts", "RCT_APP_RECOVERY_RUN", ["application recovery API phase"]),
    "semester-archive": ("playwright.semester-lifecycle.config.ts", "RCT_SEMESTER_LIFECYCLE_RUN", ["FULL admin archives and restores the disposable CI semester through real participants"]),
}
SPECIAL_FLAGS = {"bot": "RCT_E2E_BOT_ROLE_MENU", "production-service-worker": "RCT_E2E_SW_LIFECYCLE", "password-recovery": "RCT_E2E_ROLE_LOGOUT"}
SPECIAL_ACTIONS = {
    ("assignment", "pending", "ASSIGNMENT_RESTART"): {"schedule-service"},
    ("registry-native-pdf", "main", "RENDERER_OUTAGE"): {"document-renderer-service"},
    ("today", "main", "CLOCK_ADVANCE"): set(),
    ("semester-reconciliation", "main", "CLOCK_ADVANCE"): set(),
    ("semester-reconciliation", "main", "RECONCILIATION_OUTAGE"): {"schedule-service", "attendance-service"},
    ("application-recovery", "pending", "RECOVERY_QUIESCE"): SERVICES,
}
SPECIAL_REBIND = {"ASSIGNMENT_RESTART": {"schedule-service"},
                  "RECONCILIATION_OUTAGE": {"schedule-service", "attendance-service"},
                  "RECOVERY_QUIESCE": SERVICES - DATABASES - {"nginx"}}


def special_phases(lane):
    return {"assignment": ("prepare", "pending", "resume"), "application-recovery": ("seed", "pending", "admit", "attachment")}.get(lane, ("main",))


def operational_selection(admitted, until, *, mode, lifecycle_only):
    require(type(admitted) is bool and (not admitted or mode == "special13"), "OPERATIONAL_SPECIAL13_ONLY")
    require(mode != "special13" or admitted, "EXPLICIT_OPERATIONAL_ADMISSION_REQUIRED")
    require(until is None or admitted and lifecycle_only and until in SPECIAL_LIFECYCLE, "OPERATIONAL_PREFIX_REQUIRES_LIFECYCLE_ONLY")
    return SPECIAL_LIFECYCLE[:SPECIAL_LIFECYCLE.index(until) + 1] if until else SPECIAL_LIFECYCLE


def local_docker_binding():
    require(not any(os.environ.get(name) for name in ("DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH")), "LOCAL_DOCKER_CONTEXT_OR_TLS_REFUSED")
    host = os.environ.get("DOCKER_HOST", "unix:///var/run/docker.sock")
    require(re.fullmatch(r"unix:///[A-Za-z0-9_./-]+", host) and ".." not in Path(host[7:]).parts
            and Path(host[7:]).is_absolute(), "LOCAL_DOCKER_UNIX_BINDING_REQUIRED")
    return host


def selected_cohorts(mode, lifecycle_only=False):
    require(type(lifecycle_only) is bool and (not lifecycle_only or mode == "special13"), "LIFECYCLE_ONLY_SPECIAL13")
    return ("lifecycle",) if lifecycle_only else ("nonclock", "lifecycle") if mode == "special13" else (None,)


def validate_playwright_list_source():
    library = ROOT / "tests/e2e/node_modules/playwright"
    require(json.loads((library / "package.json").read_text())["version"] == "1.59.1", "SPECIAL_DISCOVERY_PLAYWRIGHT_VERSION")
    for file, digest in {
        "lib/runner/testRunner.js": "6adc6df6bbde5fdb97c70459a53364612776e927bdc3c2d50c3b3c48e21da21b",
        "lib/runner/tasks.js": "de2c75366370cf0fcfa5a1a175467ad15ca25e2600afdfb43b4d7d8416e56006",
        "lib/runner/reporters.js": "9cbf16ef7705b5e4a47e8d76816bbcd9d1d685f8c86dee78a7f86c793505ba3b"}.items():
        require(hashlib.sha256((library / file).read_bytes()).hexdigest() == digest, "SPECIAL_DISCOVERY_PLAYWRIGHT_SOURCE")


SPECIAL_FRAME_SOURCES = {Path(name).name: "tests/e2e/" + name for name in (
    "fixtures/safe-reporter.ts", "fixtures/local-special.ts", "fixtures/local-stage17.ts", "fixtures/mobile-dataset.ts",
    "fixtures/artifact-privacy.ts", "fixtures/teaching-clock.ts", "fixtures/semester-lifecycle.spec.ts",
    "fixtures/assignment-lifecycle.spec.ts", "fixtures/application-recovery.spec.ts", "specs/mobile-real-server.spec.ts",
    "specs/cloud-accepted-auth-security.spec.ts", "specs/cloud-accepted-registry-export.spec.ts", "clock-specs/teaching-clock-geo.spec.ts",
    "playwright.local-special.config.ts", "playwright.semester-lifecycle.config.ts", "playwright.assignment-lifecycle.config.ts",
    "playwright.application-recovery.config.ts", "playwright.teaching-clock.config.ts", "playwright.real-mobile.config.ts",
    "playwright.registry-export.config.ts", "playwright.config.ts",
    "playwright.local-lifecycle-prerequisites.config.ts", "local-lifecycle-prerequisites.spec.ts")}


def special_public_frame(frame, value):
    if frame is None:
        return None
    require(type(frame) is dict and set(frame) == {"file", "line", "column"} and frame["file"] in SPECIAL_FRAME_SOURCES
            and type(frame["line"]) is int and 1 <= frame["line"] <= 1000000
            and type(frame["column"]) is int and 1 <= frame["column"] <= 4096, "SPECIAL_FRAMEWORK_FRAME")
    name = SPECIAL_FRAME_SOURCES[frame["file"]]
    raw = (ROOT / name).read_bytes()
    require(len(raw) <= 2 * 1024**2 and hashlib.sha256(raw).hexdigest() == value["sources"][name], "SPECIAL_FRAMEWORK_SOURCE")
    lines = raw.decode().splitlines()
    require(frame["line"] <= len(lines) and frame["column"] <= len(lines[frame["line"] - 1]) + 1, "SPECIAL_FRAMEWORK_FRAME_RANGE")
    return {key: frame[key] for key in ("file", "line", "column")}


def closed_framework_result(report, value):
    require(type(report) is dict and set(report) == {"schema", "kind", "sourceBound", "sourceRevision", "beginSeen", "runnerStatus", "globalErrors", "privateRetention", "discovery"}
            and report["schema"] == "RCT_LOCAL_FRAMEWORK_DIAGNOSTIC_V1" and report["kind"] in {"DISCOVERY", "EXECUTION"}
            and type(report["sourceBound"]) is bool and type(report["beginSeen"]) is bool
            and (report["sourceRevision"] is None or type(report["sourceRevision"]) is str and re.fullmatch("[a-f0-9]{40}", report["sourceRevision"]))
            and report["runnerStatus"] in {"passed", "failed", "timedout", "interrupted", "UNKNOWN"}, "SPECIAL_FRAMEWORK_SCHEMA")
    errors, retention, discovery = (report[name] for name in ("globalErrors", "privateRetention", "discovery"))
    def counter(number, bound):
        require(type(number) is int and 0 <= number <= bound, "SPECIAL_FRAMEWORK_COUNTER")
        return number
    require(type(errors) is dict and set(errors) == {"observed", "retained", "truncated", "items"}
            and type(errors["truncated"]) is bool and type(errors["items"]) is list, "SPECIAL_FRAMEWORK_ERRORS")
    counter(errors["observed"], 1000000); counter(errors["retained"], 16)
    require(errors["retained"] == len(errors["items"]) <= errors["observed"]
            and (errors["truncated"] or errors["observed"] == errors["retained"]), "SPECIAL_FRAMEWORK_ERROR_COUNT")
    items = []
    for item in errors["items"]:
        require(type(item) is dict and set(item) == {"errorClass", "frameworkCode", "sourceFrame"}
                and item["errorClass"] in {"Error", "TypeError", "RangeError", "ReferenceError", "SyntaxError", "TimeoutError", "UNKNOWN"}
                and item["frameworkCode"] in {"DUPLICATE_TITLE", "UNEXPECTED_TEST", "UNEXPECTED_HOOK", "UNEXPECTED_USE", "UNKNOWN_FIXTURE", "NO_TESTS", "MODULE_NOT_FOUND", "SYNTAX_ERROR", "CONFIG_ERROR", "ADMISSION_ERROR", "OTHER", "UNKNOWN"}, "SPECIAL_FRAMEWORK_ERROR")
        items.append({"errorClass": item["errorClass"], "frameworkCode": item["frameworkCode"], "sourceFrame": special_public_frame(item["sourceFrame"], value)})
    require(type(retention) is dict and set(retention) == {"status", "retainedErrors", "bytes", "truncated"}
            and retention["status"] in {"WRITTEN", "REFUSED", "FAILED"} and type(retention["truncated"]) is bool, "SPECIAL_FRAMEWORK_RETENTION")
    counter(retention["retainedErrors"], 16); counter(retention["bytes"], 65536)
    require(type(discovery) is dict and set(discovery) == {"observed", "retained", "truncated", "rejected", "identities"}
            and type(discovery["truncated"]) is bool and type(discovery["identities"]) is list, "SPECIAL_FRAMEWORK_DISCOVERY")
    counter(discovery["observed"], 1000000); counter(discovery["retained"], 256); counter(discovery["rejected"], 1000000)
    require(discovery["retained"] == len(discovery["identities"]) <= discovery["observed"]
            and (discovery["truncated"] or discovery["observed"] == discovery["retained"] + discovery["rejected"]), "SPECIAL_FRAMEWORK_DISCOVERY_COUNT")
    identities = []
    for identity in discovery["identities"]:
        require(type(identity) is dict and set(identity) == {"file", "line", "project"}
                and identity["project"] == "chromium", "SPECIAL_FRAMEWORK_IDENTITY")
        frame = special_public_frame({"file": identity["file"], "line": identity["line"], "column": 1}, value)
        identities.append({"file": frame["file"], "line": frame["line"], "project": "chromium"})
    return {"schema": report["schema"], "kind": report["kind"], "sourceBound": report["sourceBound"], "sourceRevision": report["sourceRevision"],
        "beginSeen": report["beginSeen"], "runnerStatus": report["runnerStatus"], "globalErrors": {"observed": errors["observed"], "retained": errors["retained"], "truncated": errors["truncated"], "items": items},
        "privateRetention": {key: retention[key] for key in ("status", "retainedErrors", "bytes", "truncated")},
        "discovery": {"observed": discovery["observed"], "retained": discovery["retained"], "truncated": discovery["truncated"], "rejected": discovery["rejected"], "identities": identities}}


def special_log_hints(path, value):
    info = owned(path, 0o600)
    require(info.st_size <= 16 * 1024**2, "SPECIAL_DISCOVERY_LOG_BOUND")
    markers = {code: False for code in ("DUPLICATE_TITLE", "UNEXPECTED_TEST", "UNKNOWN_FIXTURE", "NO_TESTS", "MODULE_NOT_FOUND", "SYNTAX_ERROR", "CONFIG_ERROR", "ADMISSION_ERROR")}
    patterns = {"DUPLICATE_TITLE": r"duplicate test title", "UNEXPECTED_TEST": r"Playwright Test did not expect test", "UNKNOWN_FIXTURE": r"unknown (?:test|worker) fixture|unknown parameter",
        "NO_TESTS": r"No tests found", "MODULE_NOT_FOUND": r"Cannot find module|ERR_MODULE_NOT_FOUND", "SYNTAX_ERROR": r"SyntaxError:", "CONFIG_ERROR": r"Error while loading config",
        "ADMISSION_ERROR": r"Local special resource admission failed|Local special context was rejected"}
    frames = []
    for line in path.read_text(errors="replace").splitlines():
        if len(line) > 65536:
            continue
        for code, pattern in patterns.items():
            markers[code] |= bool(re.search(pattern, line, re.I))
        if not line.startswith("    at ") or len(frames) >= 8:
            continue
        for basename, name in SPECIAL_FRAME_SOURCES.items():
            match = re.fullmatch(r"    at (?:[^()\r\n]{1,128} \()?" + re.escape(str(ROOT / name)) + r":([1-9][0-9]{0,5}):([1-9][0-9]{0,3})\)?", line)
            if match:
                try:
                    frame = special_public_frame({"file": basename, "line": int(match[1]), "column": int(match[2])}, value)
                    if frame not in frames:
                        frames.append(frame)
                except AdmissionError:
                    pass
    return {"status": "PROJECTED", "qualification": "UNTRUSTED_REPORTED_LOG_HINTS", "markers": markers, "sourceFrames": frames}


def validate_discovery_receipt(report, value, lane, native_exit):
    expected = {(file, line, "chromium") for file, line in Runner.special_expected_locations(None, lane)}
    observed = {(item["file"], item["line"], item["project"]) for item in report["discovery"]["identities"]}
    require(report["kind"] == "DISCOVERY" and report["sourceBound"] is True and report["sourceRevision"] == value["source_sha"], "SPECIAL_DISCOVERY_SOURCE")
    require(report["globalErrors"]["observed"] == 0 and report["globalErrors"]["retained"] == 0 and not report["globalErrors"]["truncated"], "SPECIAL_DISCOVERY_GLOBAL_ERRORS")
    require(report["beginSeen"] and report["discovery"]["observed"] == report["discovery"]["retained"] == len(expected)
            and report["discovery"]["rejected"] == 0 and not report["discovery"]["truncated"] and observed == expected
            and len(observed) == len(report["discovery"]["identities"]), "SPECIAL_DISCOVERY_TEST_IDENTITIES")
    require(type(native_exit) is int and native_exit == 0 and report["runnerStatus"] == "passed"
            and report["privateRetention"]["status"] == "WRITTEN", "SPECIAL_DISCOVERY_EXIT_OR_RETENTION")
    return True


def private_json(path, bound=8 * 1024**2):
    info = owned(path, 0o600)
    require(0 < info.st_size <= bound and path.resolve() == path, "SPECIAL_PRIVATE_JSON")
    def unique(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value, "SPECIAL_DUPLICATE_JSON_KEY")
            value[key] = item
        return value
    return json.loads(path.read_text(), object_pairs_hook=unique)


def prerequisite_locations():
    path = ROOT / "tests/e2e/local-lifecycle-prerequisites.spec.ts"
    title = "fresh local lifecycle prerequisites use the canonical FIRST3 dataset"
    lines = path.read_text().splitlines()
    matches = [index + 1 for index, line in enumerate(lines) if "test(" in line and ("'" + title + "'") in line]
    require(len(matches) == 1, "SPECIAL_PREPARATION_TITLE")
    return {(path.name, matches[0])}


def closed_preparation_result(report, value):
    require(type(report) is dict and set(report) == {"schema", "sourceRevision", "calendar", "preview", "clockUnchanged", "status", "datasetInitialized"}
            and report["schema"] == "RCT_LOCAL_LIFECYCLE_PREREQUISITES_V1" and report["sourceRevision"] == value["source_sha"]
            and report["status"] == "PREPARED" and report["clockUnchanged"] is True and report["datasetInitialized"] is True
            and type(report["calendar"]) is dict and report["calendar"] == {"active": True, "dateFromYesterday": True, "dateToTomorrow": True}
            and all(type(flag) is bool for flag in report["calendar"].values())
            and type(report["preview"]) is dict and report["preview"] == {"nonzero": True}
            and type(report["preview"]["nonzero"]) is bool, "SPECIAL_PREPARATION_RECEIPT")
    return {"schema": report["schema"], "sourceRevision": report["sourceRevision"], "calendar": dict(report["calendar"]),
            "preview": dict(report["preview"]), "clockUnchanged": True, "status": "PREPARED", "datasetInitialized": True}


def canonical_mount_records(value):
    """Complete JSON records; only the outer Docker Mounts order is insignificant."""
    require(type(value) is list and len(value) <= 512 and all(type(item) is dict for item in value), "SPECIAL_MOUNT_RECORDS_SHAPE")
    def check(item, depth=0):
        require(depth <= 16, "SPECIAL_MOUNT_RECORDS_BOUND")
        if type(item) is dict:
            require(all(type(key) is str for key in item), "SPECIAL_MOUNT_RECORDS_KEY")
            for child in item.values(): check(child, depth + 1)
        elif type(item) is list:
            for child in item: check(child, depth + 1)
        else:
            require(item is None or type(item) in (str, bool, int, float), "SPECIAL_MOUNT_RECORDS_VALUE")
    records = []
    for item in value:
        check(item)
        try:
            encoded = json.dumps(item, sort_keys=True, separators=(",", ":"), allow_nan=False)
        except (ValueError, TypeError):
            raise AdmissionError("SPECIAL_MOUNT_RECORDS_VALUE") from None
        require(len(encoded) <= 1024**2, "SPECIAL_MOUNT_RECORDS_BOUND")
        records.append(encoded)
    return sorted(records)


def special_resource_digest(path):
    path = Path(path)
    info = path.lstat()
    require(path.is_absolute() and path.resolve() == path and stat.S_ISREG(info.st_mode)
            and not path.is_symlink() and info.st_nlink == 1 and 0 < info.st_size <= 1024**2, "SPECIAL_RESOURCE_FILE_BOUND")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def special_records(value):
    directory = Path(value["run"]) / "transitions"
    owned(directory, 0o700, directory=True)
    rows = []
    previous = hashlib.sha256((Path(value["run"]) / "owner.json").read_bytes()).hexdigest()
    for index, path in enumerate(sorted(directory.glob("*.json"))):
        require(path.name == f"{index:04d}.json", "SPECIAL_TRANSITION_SEQUENCE")
        row = private_json(path)
        require(row.get("index") == index and row.get("previous") == previous
                and row.get("nonce") == value["nonce"] and row.get("kind") in {"intent", "outcome", "recovery-register", "recovery-seal"}, "SPECIAL_TRANSITION_BINDING")
        base = {"index", "previous", "nonce", "kind"}
        fields = {"intent": {"lane", "phase", "action", "services", "extra"}, "outcome": {"intent", "success", "restored", "network_bindings"},
                  "recovery-register": {"file", "sha256", "project", "images", "source_ids", "source_mounts", "allowed_binds"},
                  "recovery-seal": {"target_ids", "receipt", "sha256"}}
        require(set(row) == base | fields[row["kind"]], "SPECIAL_TRANSITION_FIELDS")
        if row["kind"] == "intent":
            key = (row["lane"], row["phase"], row["action"])
            require(key in SPECIAL_ACTIONS and row["services"] == sorted(SPECIAL_ACTIONS[key])
                    and type(row["extra"]) is dict and len(json.dumps(row["extra"])) <= 16384, "SPECIAL_TRANSITION_INTENT")
        elif row["kind"] == "outcome":
            require(type(row["intent"]) is int and 0 <= row["intent"] < index and rows[row["intent"]]["kind"] == "intent"
                    and type(row["success"]) is bool and type(row["restored"]) is bool
                    and type(row["network_bindings"]) is dict
                    and set(row["network_bindings"]) <= SPECIAL_REBIND.get(rows[row["intent"]]["action"], set())
                    and not any(item["kind"] == "outcome" and item["intent"] == row["intent"] for item in rows), "SPECIAL_TRANSITION_OUTCOME")
            require(row["restored"] or not row["network_bindings"], "SPECIAL_TRANSITION_UNRESTORED_BINDING")
            for binding in row["network_bindings"].values():
                require(type(binding) is dict and set(binding) == {"IPAddress", "IPPrefixLen", "Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "IPv6Gateway"}
                        and type(binding["IPPrefixLen"]) is int and 1 <= binding["IPPrefixLen"] <= 32
                        and type(binding["IPAddress"]) is str and ipaddress.ip_address(binding["IPAddress"]).version == 4
                        and type(binding["Gateway"]) is str and ipaddress.ip_address(binding["Gateway"]).version == 4
                        and binding["GlobalIPv6Address"] in (None, "") and binding["GlobalIPv6PrefixLen"] in (None, 0)
                        and binding["IPv6Gateway"] in (None, ""), "SPECIAL_TRANSITION_BINDING_SHAPE")
        elif row["kind"] == "recovery-register":
            require(row["project"] == value["special_intent"]["recovery_project"] and type(row["images"]) is dict
                    and set(row["images"]) == DATABASES and set(row["source_ids"]) == DATABASES
                    and set(row["allowed_binds"]) == DATABASES and type(row["source_mounts"]) is list
                    and re.fullmatch(r"[a-f0-9]{64}", row["sha256"]), "SPECIAL_TRANSITION_RECOVERY_REGISTER")
        else:
            require(type(row["target_ids"]) is dict and set(row["target_ids"]) == DATABASES
                    and all(re.fullmatch(r"[a-f0-9]{64}", item) for item in row["target_ids"].values())
                    and re.fullmatch(r"[a-f0-9]{64}", row["sha256"]), "SPECIAL_TRANSITION_RECOVERY_SEAL")
        previous = hashlib.sha256(path.read_bytes()).hexdigest()
        rows.append(row)
    return rows, previous


def append_special(value, kind, **fields):
    rows, previous = special_records(value)
    row = {"index": len(rows), "previous": previous, "nonce": value["nonce"], "kind": kind, **fields}
    write(Path(value["run"]) / "transitions" / f"{len(rows):04d}.json", json.dumps(row, sort_keys=True) + "\n")
    return row


def validate_special_environment(value, environment, lane=None, phase=None):
    if value.get("operational_admitted"):
        require(environment.get("DOCKER_HOST") == value.get("docker_host") and value.get("docker_host") == local_docker_binding(), "OPERATIONAL_DOCKER_BINDING_CHANGED")
    cohort = value.get("special_cohort")
    preparation = environment.get("RCT_LOCAL_SPECIAL_PREPARATION", "")
    require(preparation in {"", "true"} and (not preparation or cohort == "lifecycle"
            and environment.get("RCT_LOCAL_SPECIAL_LANE") == "semester-metadata"
            and environment.get("RCT_LOCAL_SPECIAL_PHASE") == "main"
            and not environment.get("RCT_LOCAL_SPECIAL_DISCOVERY")), "SPECIAL_PREPARATION_BINDING")
    if preparation:
        for directory in (Path(value["run"]) / "prerequisites", Path(value["run"]) / "prerequisites/artifacts"):
            owned(directory, 0o700, directory=True)
    discovery = environment.get("RCT_LOCAL_SPECIAL_DISCOVERY", "")
    discovery_lane = environment.get("RCT_LOCAL_SPECIAL_LANE")
    require(discovery in {"", "true"} and (not discovery or cohort == "lifecycle"
            and discovery_lane in SPECIAL_LIFECYCLE and environment.get("RCT_LOCAL_SPECIAL_PHASE") == special_phases(discovery_lane)[0]), "SPECIAL_DISCOVERY_BINDING")
    require(cohort in {"nonclock", "lifecycle"} and value.get("special_lanes") == list(SPECIAL_NONCLOCK if cohort == "nonclock" else SPECIAL_LIFECYCLE), "SPECIAL_MANIFEST_COHORT")
    require(environment.get("RCT_LOCAL_SPECIAL_COHORT") == cohort
            and environment.get("RCT_LOCAL_STAGE17_MODE") == "special13"
            and environment.get("RCT_LOCAL_STAGE17_NONCE") == value["nonce"]
            and environment.get("E2E_BASE_URL") == "https://localhost:8443"
            and environment.get("RCT_E2E_TEACHING_CLOCK_DIR") == value["run"] + "/clock"
            and environment.get("RCT_E2E_SYNTHETIC_SEED") == "true"
            and environment.get("RCT_E2E_REMAINING5") == "true"
            and environment.get("RCT_E2E_ASYNC_REPORT") == "false"
            and not environment.get("RCT_LOCAL_UI_BATCH_LANE"), "SPECIAL_ENVIRONMENT")
    actual_lane, actual_phase = environment.get("RCT_LOCAL_SPECIAL_LANE"), environment.get("RCT_LOCAL_SPECIAL_PHASE")
    require((actual_lane is None and actual_phase is None and lane is None and phase is None)
            or (actual_lane in value["special_lanes"] and actual_phase in special_phases(actual_lane)
                and lane in (None, actual_lane) and phase in (None, actual_phase)), "SPECIAL_LANE_PHASE_BINDING")
    if actual_lane:
        lane_run = Path(value["run"]) / "lanes" / actual_lane
        owned(lane_run, 0o700, directory=True)
        owned(lane_run / "artifacts", 0o700, directory=True)
        if actual_phase != "main":
            owned(lane_run / "artifacts" / actual_phase, 0o700, directory=True)
        require(environment.get("RCT_REMAINING5_RUN") == str(lane_run)
                and environment.get("RCT_DIAGNOSTIC_RUN_DIR") == str(lane_run), "SPECIAL_OUTPUT_BINDING")
        for name in {row[1] for row in SPECIAL_LANES.values() if row[1]}:
            expected_output = str(lane_run) if name == SPECIAL_LANES[actual_lane][1] else ""
            if actual_lane == "semester-archive" and name == "RCT_APP_RECOVERY_RUN":
                expected_output = value["run"] + "/lanes/application-recovery"
            require(environment.get(name, "") == expected_output, "SPECIAL_OUTPUT_BINDING")
        for name in ("RCT_E2E_ADMIN_LIFECYCLE", "RCT_E2E_ROLE_LOGOUT", "RCT_E2E_WEBKIT_SUBSET", "RCT_E2E_SW_LIFECYCLE", "RCT_E2E_BOT_ROLE_MENU"):
            require(environment.get(name) == "true" if name == SPECIAL_FLAGS.get(actual_lane) else environment.get(name) != "true", "SPECIAL_FLAG_BINDING")
        for selected, variable in (("assignment", "RCT_ASSIGNMENT_LIFECYCLE_PHASE"), ("application-recovery", "RCT_APP_RECOVERY_PHASE")):
            require(environment.get(variable, "") == (actual_phase if selected == actual_lane else ""), "SPECIAL_PHASE_BINDING")
    require(environment.get("RCT_APP_RECOVERY_PROJECT", "") in ("", value["special_intent"]["recovery_project"]), "SPECIAL_RECOVERY_PROJECT")


def special_topology(value):
    rows, _ = special_records(value)
    registrations = [row for row in rows if row["kind"] == "recovery-register"]
    require(len(registrations) <= 1, "SPECIAL_RECOVERY_DUPLICATE")
    if not registrations:
        return None
    registration = registrations[0]
    path = Path(registration["file"])
    require(path.is_relative_to(Path(value["run"])) and path.resolve() == path
            and hashlib.sha256(path.read_bytes()).hexdigest() == registration["sha256"], "SPECIAL_RECOVERY_CONFIG_CHANGED")
    return registration


def special_inventory(value, *, complete=True, expected_lane=None, expected_phase=None, deadline=None):
    config = json.loads((Path(value["run"]) / "compose.json").read_text())
    validate_special_network_config(config, value["special_network"])
    rows = inventory(value["project"], value["nonce"], complete=False, deadline=deadline)
    require(not complete or len(rows) == 17, "SPECIAL_ORIGINAL_INVENTORY")
    frozen_path = Path(value["run"]) / "runtime-identity.json"
    frozen = private_json(frozen_path) if frozen_path.exists() else None
    topology = special_topology(value)
    require(deadline is None or topology is None, "SPECIAL_RENDERER_WAIT_TOPOLOGY")
    records, _ = special_records(value)
    sealed = {row["intent"] for row in records if row["kind"] == "outcome"}
    active = [row for row in records if row["kind"] == "intent" and row["index"] not in sealed]
    for intent in active:
        phases = {"assignment": {"pending", "resume"}, "application-recovery": {"seed", "pending", "admit", "attachment"}}.get(intent["lane"], {intent["phase"]})
        require(expected_lane is None or expected_lane == intent["lane"] and expected_phase in phases, "SPECIAL_ACTIVE_INTENT_CONTEXT")
    permitted = {service for row in active for service in row["services"]}
    rebind_permitted = {service for row in active for service in SPECIAL_REBIND.get(row["action"], set())}
    network = json.loads(capture_before(["docker", "network", "inspect", value["project"] + "_private_net"], deadline))[0]
    rebound = {name: binding for record in records if record["kind"] == "outcome" and record["restored"]
               for name, binding in record["network_bindings"].items()}
    for row in rows:
        service = row["Config"]["Labels"]["com.docker.compose.service"]
        require(row["Config"]["Image"] == config["services"][service]["image"], "SPECIAL_CONFIGURED_IMAGE")
        if frozen:
            pin = frozen["services"][service]
            require(row["Id"] == pin["id"] and row["Image"] == pin["image"]
                    and row["Config"]["Labels"] == pin["labels"] and row["Config"]["Image"] == pin["configured_image"]
                    and canonical_mount_records(row["Mounts"]) == canonical_mount_records(pin["mounts"])
                    and row["HostConfig"].get("ReadonlyRootfs", False) == pin["readonly"], "SPECIAL_ORIGINAL_IDENTITY_CHANGED")
        networks = set(row["NetworkSettings"]["Networks"])
        require(networks == {value["project"] + "_private_net"}
                or topology and service in DATABASES and networks == set(), "SPECIAL_NETWORK_CHANGED")
        if frozen and networks:
            attachment = row["NetworkSettings"]["Networks"][value["project"] + "_private_net"]
            require(attachment["NetworkID"] == frozen["network_id"]
                    and set(attachment.get("Aliases") or []) == set(frozen["services"][service]["aliases"]), "SPECIAL_NETWORK_ID_CHANGED")
            fields = ("IPAddress", "IPPrefixLen", "Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "IPv6Gateway")
            if row["State"]["Running"]:
                expected_binding = rebound.get(service, frozen["services"][service]["network_binding"])
                stable = all(attachment.get(key) == expected_binding.get(key) for key in fields)
                controlled_rebind = service in rebind_permitted
                require(stable or controlled_rebind, "SPECIAL_NETWORK_ADDRESS_CHANGED")
                membership = network.get("Containers", {}).get(row["Id"], {})
                require(membership.get("IPv4Address") == attachment.get("IPAddress", "") + "/" + str(attachment.get("IPPrefixLen")), "SPECIAL_NETWORK_MEMBERSHIP")
            else:
                require(service in permitted or topology and service in DATABASES, "SPECIAL_STOPPED_ENDPOINT_UNPLANNED")
                require(all(attachment.get(key) in (None, "", 0) for key in ("IPAddress", "IPPrefixLen", "Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "IPv6Gateway")), "SPECIAL_STOPPED_ENDPOINT_SHAPE")
        if topology and service in DATABASES and any(record["kind"] == "recovery-seal" for record in records):
            require(not row["State"]["Running"] and not networks, "SPECIAL_SEALED_SOURCE_DATABASE_ACTIVE")
        require(not complete or service in permitted or topology and service in DATABASES
                or row["State"]["Running"] and not row["State"].get("Paused", False), "SPECIAL_UNPLANNED_OUTAGE")
        if service in APPS:
            actual = dict(item.split("=", 1) for item in row["Config"]["Env"])
            require(all(actual.get(key) == str(item) for key, item in config["services"][service]["environment"].items()), "SPECIAL_APPLICATION_ENV_CHANGED")
        if service == "nginx":
            require(row["HostConfig"]["PortBindings"] == {"80/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8088"}], "443/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8443"}]}, "EDGE_BINDINGS_CHANGED")
    require(network["Labels"].get(LABEL) == value["nonce"] and network["Labels"].get("com.docker.compose.project") == value["project"], "FOREIGN_NETWORK")
    require(not frozen or network["Id"] == frozen["network_id"], "SPECIAL_NETWORK_REPLACED")
    if topology:
        targets = special_recovery_inventory(value, topology, complete=complete)
        rows += targets
    return rows


def special_recovery_inventory(value, topology, *, complete):
    project = value["special_intent"]["recovery_project"]
    ids = capture(["docker", "ps", "-aq", "--no-trunc", "--filter", "label=com.docker.compose.project=" + project]).splitlines()
    rows = json.loads(capture(["docker", "inspect", *ids])) if ids else []
    config = private_json(Path(topology["file"]))
    seen = set()
    for row in rows:
        labels = row["Config"].get("Labels", {})
        service = labels.get("com.docker.compose.service")
        require(service in DATABASES and service not in seen and labels.get(LABEL) == value["nonce"]
                and labels.get("com.docker.compose.project") == project and labels.get("io.rutcampustrack.recovery") == "disposable"
                and row["Image"] == topology["images"][service] and row["Config"]["Image"] == config["services"][service]["image"], "SPECIAL_RECOVERY_IDENTITY")
        seen.add(service)
        require(set(row["NetworkSettings"]["Networks"]) == {value["project"] + "_private_net"}, "SPECIAL_RECOVERY_NETWORK")
        frozen = private_json(Path(value["run"]) / "runtime-identity.json")
        attachment = row["NetworkSettings"]["Networks"][value["project"] + "_private_net"]
        require(attachment["NetworkID"] == frozen["network_id"] and service in attachment.get("Aliases", []), "SPECIAL_RECOVERY_NETWORK_ID")
        for mount in row["Mounts"]:
            expected = topology["allowed_binds"][service]
            require(mount["Source"] not in topology["source_mounts"]
                    and (mount["Type"] != "bind" or mount["RW"] is False
                         and any(item["source"] == mount["Source"] and item["target"] == mount["Destination"]
                                 and special_resource_digest(item["source"]) == item["sha256"] for item in expected)), "SPECIAL_RECOVERY_DATA_REUSE")
        require(not complete or row["State"]["Running"] and not row["State"].get("Paused", False), "SPECIAL_RECOVERY_NOT_RUNNING")
    require(not complete or seen == DATABASES, "SPECIAL_RECOVERY_COUNT")
    records, _ = special_records(value)
    seals = [row for row in records if row["kind"] == "recovery-seal"]
    require(len(seals) <= 1, "SPECIAL_RECOVERY_RESEALED")
    if seals:
        require({row["Config"]["Labels"]["com.docker.compose.service"]: row["Id"] for row in rows} == seals[0]["target_ids"], "SPECIAL_RECOVERY_TARGET_REPLACED")
    return rows


def special_context(value, lane, phase):
    topology = special_topology(value)
    database_project = value["project"]
    if topology and (lane == "semester-archive" or lane == "application-recovery" and phase in {"admit", "attachment"}):
        special_recovery_inventory(value, topology, complete=True)
        database_project = value["special_intent"]["recovery_project"]
    image = None
    pin = Path(value["run"]) / "bot-image.json"
    if pin.exists():
        bot_pin = private_json(pin)
        require(bot_pin["tag"] == value["special_intent"]["bot_image_tag"], "SPECIAL_BOT_TAG_BINDING")
        image = bot_pin["id"]
        observed = json.loads(capture(["docker", "image", "inspect", image]))[0]
        require(observed["Id"] == image and observed["Config"]["Labels"].get(LABEL) == value["nonce"], "SPECIAL_BOT_IMAGE")
    return {"project": value["project"], "nonce": value["nonce"], "root": value["root"], "run": value["run"],
            "laneRun": value["run"] + "/lanes/" + lane, "networkName": value["project"] + "_private_net",
            "certsDir": value["run"] + "/certs", "composeFile": value["run"] + "/compose.json",
            "recoveryProject": value["special_intent"]["recovery_project"], "databaseProject": database_project, "botImage": image}


def special_service(value, service):
    rows = special_inventory(value)
    topology = special_topology(value)
    project = value["special_intent"]["recovery_project"] if topology and service in DATABASES else value["project"]
    matches = [row for row in rows if row["Config"]["Labels"].get("com.docker.compose.service") == service
               and row["Config"]["Labels"].get("com.docker.compose.project") == project]
    require(len(matches) == 1, "SPECIAL_SERVICE_COUNT")
    row = matches[0]
    return {"id": row["Id"], "image": row["Image"], "configuredImage": row["Config"]["Image"],
            "labels": row["Config"]["Labels"], "running": row["State"]["Running"], "paused": row["State"].get("Paused", False),
            "health": row["State"].get("Health", {}).get("Status"), "readonly": row["HostConfig"].get("ReadonlyRootfs", False),
            "mounts": row["Mounts"], "networks": row["NetworkSettings"]["Networks"]}


def special_result(report, code, lane, phase, expected_locations):
    """Complete named producer rows only; skips and missing rows remain NOT_RUN."""
    expected = 3 if lane == "registry-native-pdf" else 1
    require(type(report) is dict and report.get("unsafeArtifact") is False
            and type(report.get("runnerErrors")) is int and report["runnerErrors"] == 0, "SPECIAL_UNTRUSTED_REPORT")
    rows = report.get("results")
    require(type(rows) is list and len(rows) <= expected and all(type(row) is dict for row in rows), "SPECIAL_REPORT_COUNT")
    identities = [(row.get("file"), row.get("line"), row.get("project")) for row in rows]
    require(len(set(identities)) == len(rows)
            and all((row.get("file"), row.get("line")) in expected_locations and row.get("project") == "chromium"
                    and row.get("expectedStatus") in {"passed", "skipped"} and row.get("status") in {"passed", "failed", "timedOut", "skipped"}
                    and not (row.get("status") == "passed" and row.get("expectedStatus") != "passed") for row in rows), "SPECIAL_REPORT_IDENTITY")
    passed = sum(row["status"] == "passed" for row in rows)
    failed = sum(row["status"] in {"failed", "timedOut"} for row in rows)
    not_run = expected - passed - failed
    require((code == 0 and failed == 0 and report.get("status") == "passed")
            or (code == 1 and report.get("status") in {"failed", "timedout"} and failed + not_run > 0), "SPECIAL_REPORT_EXIT")
    # Privacy-safe diagnostics remain separately in the trusted reporter file; this boundary exports enums/counts only.
    return {"lane": lane, "phase": phase, "status": "FAIL" if failed else "NOT_RUN" if not_run else "PASS",
            "expected": expected, "passed": passed, "failed": failed, "notRun": not_run}


RETAINED_PROJECT = "rct-local-stage17-8aad5d587e5d"
RETAINED_SOURCE = "b8722534a0649491e10be9a9a832b6c782332806"
RETAINED_PDF_PROJECT = "rct-local-stage17-ee1df05be60a"
RETAINED_PDF_SOURCE = "3c906d20cf260e08ae265ab04027f66d7465d402"
RETAINED_RECOVERY_PROJECT = "rct-local-stage17-43589e12d603"
RETAINED_RECOVERY_SOURCE = "2d2fa75b6454b70c39406e8f9e837d7cf5c3dcbb"
SPECIAL_PRIVATE_POOL = tuple(f"172.{value}.0.0/24" for value in (30, 31, *range(29, 15, -1))) + tuple(f"10.{value}.0.0/24" for value in range(240, 248))


def validate_retained_cohort(path, *, root=None, stopped=True, pdf=False, recovery=False):
    """Only the explicitly closed BOOT cohort may remain beside a new special13 run."""
    root = ROOT if root is None else Path(root)
    path = Path(path)
    require(path.is_absolute() and path.resolve() == path
            and path.is_relative_to(Path.home() / ".local/share/rct-pr3-local/checks"), "RETAINED_RECEIPT_PATH")
    owned(path.parent, 0o700, directory=True)
    receipt = private_json(path)
    require(not (pdf and recovery), "RETAINED_FIXED_SELECTION")
    project, source = ((RETAINED_RECOVERY_PROJECT, RETAINED_RECOVERY_SOURCE) if recovery else
                       (RETAINED_PDF_PROJECT, RETAINED_PDF_SOURCE) if pdf else (RETAINED_PROJECT, RETAINED_SOURCE))
    run = root / ".agent/evidence/local-runs" / project
    fields = {"schema", "uid", "root", "run", "project", "source_sha", "nonce", "files", "services", "network_id", "status"}
    require(set(receipt) == fields | ({"transition_files"} if pdf or recovery else set())
            and receipt["schema"] == ("RCT_RETAINED_RECOVERY_CLOSURE_V1" if recovery else "RCT_RETAINED_PDF_CLOSURE_V1" if pdf else "RCT_RETAINED_BOOT_CLOSURE_V1") and receipt["uid"] == os.getuid()
            and receipt["root"] == str(root) and receipt["run"] == str(run) and receipt["project"] == project
            and receipt["source_sha"] == source and receipt["status"] == ("STOPPED" if stopped else "PRE_STOP"), "RETAINED_RECEIPT_BINDING")
    owned(run, 0o700, directory=True)
    files = {"owner.json", "runtime-identity.json", "compose.json", "result.safe.json"}
    if pdf:
        files.add("lanes/registry-native-pdf/main.result.safe.json")
    if recovery:
        files |= {"lanes/application-recovery/seed.result.safe.json", "lanes/application-recovery/logical.result.safe.json",
                  "lanes/application-recovery/pending.failure.safe.json", "clock/current-instant",
                  "lanes/application-recovery/lifecycle-input.private.json"}
    require(set(receipt["files"]) == files, "RETAINED_FILES_CHANGED")
    for name, digest in receipt["files"].items():
        info = owned(run / name, 0o644 if name == "clock/current-instant" else 0o600)
        require(info.st_size <= 8 * 1024**2 and hashlib.sha256((run / name).read_bytes()).hexdigest() == digest, "RETAINED_FILES_CHANGED")
    value, frozen, config, result = (private_json(run / name) for name in ("owner.json", "runtime-identity.json", "compose.json", "result.safe.json"))
    require(value["root"] == str(root) and value["run"] == str(run) and value["project"] == project
            and value["mode"] == "special13" and value["special_cohort"] == ("lifecycle" if recovery else "nonclock") and value["source_sha"] == source
            and value["nonce"] == receipt["nonce"] and project == "rct-local-stage17-" + receipt["nonce"][:12]
            and re.fullmatch(r"[a-f0-9]{24}", receipt["nonce"])
            and set(frozen["services"]) == SERVICES and set(config["services"]) == SERVICES
            and result["mode"] == "special13" and result["phase"] == ("SPECIAL_APPLICATION_RECOVERY_PENDING" if recovery else "SPECIAL_REGISTRY_NATIVE_PDF_MAIN" if pdf else "BOOT")
            and result["result"] == "FAIL", "RETAINED_BOOT_IDENTITY")
    rebound = {}
    if recovery:
        require(result["cleanup"] == "FAIL_RETAINED_FOR_OWNER" and result["container_logs"] == "PASS", "RETAINED_RECOVERY_RESULT")
        require(private_json(run / "lanes/application-recovery/seed.result.safe.json") == {
            "lane": "application-recovery", "phase": "seed", "status": "PASS", "expected": 1, "passed": 1, "failed": 0, "notRun": 0}, "RETAINED_RECOVERY_SEED")
        require(private_json(run / "lanes/application-recovery/logical.result.safe.json") == {
            "lane": "application-recovery", "status": "FAIL", "expected": 1, "passed": 0, "failed": 1, "notRun": 0,
            "phases": [{"phase": phase, "status": "PASS" if phase == "seed" else "NOT_RUN"}
                       for phase in ("seed", "pending", "admit", "attachment")], "errorClass": "RuntimeError"}, "RETAINED_RECOVERY_LOGICAL")
        failure = private_json(run / "lanes/application-recovery/pending.failure.safe.json")
        require(failure.get("schema") == "RCT_LOCAL_SPECIAL_FAILURE_V1" and failure.get("lane") == "application-recovery"
                and failure.get("phase") == "pending" and failure.get("errorClass") == "FileNotFoundError"
                and failure.get("admissionCode") is None and failure.get("sourceFrame") == {
                    "source": "scripts/local_e2e_async.py", "line": 308,
                    "sha256": "9adb985fa55eb80546899aa6db2db86d65578ab3ef61c293e84d8e61f96f196c"}, "RETAINED_RECOVERY_FAILURE")
        clock_input = private_json(run / "lanes/application-recovery/lifecycle-input.private.json")
        require(set(clock_input) == {"lessonId", "semesterId", "expectedInstant", "clockSha256"}
                and type(clock_input["expectedInstant"]) is str and re.fullmatch(r"\d{4}-\d{2}-\d{2}T09:00:00\.000Z", clock_input["expectedInstant"])
                and (run / "clock/current-instant").read_bytes() == (clock_input["expectedInstant"] + "\n").encode()
                and hashlib.sha256((run / "clock/current-instant").read_bytes()).hexdigest() == clock_input["clockSha256"], "RETAINED_RECOVERY_CLOCK")
        require(not any((run / "lanes/application-recovery" / name).exists() or (run / "lanes/application-recovery" / name).is_symlink() for name in (
            "bundle", "capture.private.json", "verified.private.json", "pending.json", "history-before.json", "admit.result.safe.json", "attachment.result.safe.json")), "RETAINED_RECOVERY_UNEXPECTED_PROGRESS")
        records, _ = special_records(value)
        intents = [row for row in records if row["kind"] == "intent" and row["action"] == "RECOVERY_QUIESCE"]
        require(len(intents) == 1 and not any(row["kind"] in {"recovery-register", "recovery-seal"} for row in records), "RETAINED_RECOVERY_NO_TOPOLOGY")
        intent = intents[0]
        require(intent["lane"] == "application-recovery" and intent["phase"] == "pending"
                and intent["services"] == sorted(SERVICES) and records[-2] == intent
                and records[-1]["kind"] == "outcome" and records[-1]["intent"] == intent["index"]
                and records[-1]["success"] is False and records[-1]["restored"] is False, "RETAINED_RECOVERY_ORIGINAL_INTENT")
        require(all(any(outcome["kind"] == "outcome" and outcome["intent"] == row["index"] and outcome["restored"] is True
                        for outcome in records) for row in records if row["kind"] == "intent" and row is not intent), "RETAINED_RECOVERY_PRIOR_RESTORATION")
        rebound = {name: binding for row in records if row["kind"] == "outcome" and row["restored"]
                   for name, binding in row["network_bindings"].items()}
        transitions = {entry.name: entry for entry in (run / "transitions").iterdir()}
        require(set(transitions) == set(receipt["transition_files"]) and len(transitions) == len(records), "RETAINED_RECOVERY_TRANSITIONS")
        for name, entry in transitions.items():
            owned(entry, 0o600)
            require(hashlib.sha256(entry.read_bytes()).hexdigest() == receipt["transition_files"][name], "RETAINED_RECOVERY_TRANSITION_CHANGED")
    elif pdf:
        require(result["cleanup"] == "FAIL_RETAINED_FOR_OWNER", "RETAINED_PDF_RESULT")
        phase = private_json(run / "lanes/registry-native-pdf/main.result.safe.json")
        require(phase == {"lane": "registry-native-pdf", "phase": "main", "status": "FAIL", "expected": 3, "passed": 2, "failed": 1, "notRun": 0}, "RETAINED_PDF_PHASE")
        records, _ = special_records(value)
        require(len(records) == 1 and records[0]["kind"] == "intent" and records[0]["action"] == "RENDERER_OUTAGE"
                and records[0]["lane"] == "registry-native-pdf" and records[0]["phase"] == "main", "RETAINED_PDF_ORIGINAL_INTENT")
        transitions = {path.name: path for path in (run / "transitions").iterdir()}
        require(set(transitions) == set(receipt["transition_files"]) == {"0000.json"}, "RETAINED_PDF_TRANSITION_FILES")
        for name, path in transitions.items():
            owned(path, 0o600)
            require(hashlib.sha256(path.read_bytes()).hexdigest() == receipt["transition_files"][name], "RETAINED_PDF_TRANSITION_CHANGED")
    else:
        for directory in (run / "transitions", run / "lanes"):
            owned(directory, 0o700, directory=True)
            require(not list(directory.iterdir()), "RETAINED_SCENARIO_OR_INTENT_PRESENT")
    require(receipt["network_id"] == frozen["network_id"] and set(receipt["services"]) == SERVICES
            and all(receipt["services"][name] == {"id": pin["id"], "image": pin["image"]} for name, pin in frozen["services"].items()), "RETAINED_FROZEN_BINDING")
    ids = capture(["docker", "ps", "-aq", "--no-trunc", "--filter", "label=com.docker.compose.project=" + project]).splitlines()
    require(len(ids) == 17 and len(set(ids)) == 17, "RETAINED_INVENTORY_COUNT")
    rows = json.loads(capture(["docker", "inspect", *ids]))
    seen = set()
    network_name = project + "_private_net"
    network = json.loads(capture(["docker", "network", "inspect", network_name]))[0]
    require(network["Id"] == frozen["network_id"] and network["Labels"].get(LABEL) == value["nonce"]
            and network["Labels"].get("com.docker.compose.project") == project, "RETAINED_NETWORK_CHANGED")
    for row in rows:
        labels = row["Config"].get("Labels", {})
        name = labels.get("com.docker.compose.service")
        require(name in SERVICES and name not in seen and labels.get(LABEL) == value["nonce"]
                and labels.get("com.docker.compose.project") == project, "RETAINED_FOREIGN_CONTAINER")
        seen.add(name)
        pin = frozen["services"][name]
        require(row["Id"] == pin["id"] and row["Image"] == pin["image"] and labels == pin["labels"]
                and row["Config"]["Image"] == pin["configured_image"] == config["services"][name]["image"]
                and canonical_mount_records(row["Mounts"]) == canonical_mount_records(pin["mounts"])
                and row["HostConfig"].get("ReadonlyRootfs", False) == pin["readonly"], "RETAINED_IMMUTABLE_CHANGED")
        expected_running = not stopped and not (recovery and name == "rabbitmq")
        require(row["State"]["Running"] is expected_running and not row["State"].get("Paused", False), "RETAINED_STATE_CHANGED")
        if pdf and not stopped:
            healthcheck = config["services"][name].get("healthcheck")
            if healthcheck and not healthcheck.get("disable", False) and healthcheck.get("test") != ["NONE"]:
                require(row["State"].get("Health", {}).get("Status") == "healthy", "RETAINED_PDF_NOT_HEALTHY")
            if name == "document-renderer-service":
                ready = json.loads(capture(["docker", "exec", row["Id"], "wget", "-T", "2", "-qO-", "http://localhost:9095/actuator/health/readiness"], seconds=5))
                require(type(ready) is dict and ready.get("status") == "UP", "RETAINED_PDF_NOT_READY")
        require(set(row["NetworkSettings"]["Networks"]) == {network_name}, "RETAINED_NETWORK_SET")
        binding = row["NetworkSettings"]["Networks"][network_name]
        require(binding["NetworkID"] == frozen["network_id"] and set(binding.get("Aliases") or []) == set(pin["aliases"]), "RETAINED_NETWORK_BINDING")
        fields = ("IPAddress", "IPPrefixLen", "Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "IPv6Gateway")
        require(all(binding.get(key) in (None, "", 0) if not expected_running else binding.get(key) == rebound.get(name, pin["network_binding"]).get(key) for key in fields), "RETAINED_ENDPOINT_CHANGED")
        require(expected_running or binding.get("EndpointID") in (None, ""), "RETAINED_STOPPED_ENDPOINT")
        if name in APPS:
            environment = dict(item.split("=", 1) for item in row["Config"]["Env"])
            require(all(environment.get(key) == str(item) for key, item in config["services"][name]["environment"].items()), "RETAINED_ENVIRONMENT_CHANGED")
    running_ids = {row["Id"] for row in rows if row["State"]["Running"]}
    require(seen == SERVICES and set(network.get("Containers", {})) == running_ids, "RETAINED_NETWORK_MEMBERSHIP")
    if recovery:
        target = "rct-recovery-" + value["nonce"]
        require(value["special_intent"]["recovery_project"] == target, "RETAINED_RECOVERY_TARGET_BINDING")
        for command in (["docker", "ps", "-aq", "--no-trunc"], ["docker", "volume", "ls", "-q"], ["docker", "network", "ls", "-q"]):
            require(not capture([*command, "--filter", "label=com.docker.compose.project=" + target]).strip(), "RETAINED_RECOVERY_TARGET_PRESENT")
    return {"ids": ids, "network_id": network["Id"]}


def special_network_choice(selected=None):
    occupied = []
    ids = capture(["docker", "network", "ls", "-q"]).splitlines()
    for network in json.loads(capture(["docker", "network", "inspect", *ids])) if ids else []:
        for item in network.get("IPAM", {}).get("Config") or []:
            if item.get("Subnet"):
                occupied.append(ipaddress.ip_network(item["Subnet"]))
    for row in json.loads(capture(["ip", "-j", "-4", "route", "show", "table", "all"])):
        destination = row.get("dst", "default")
        if destination not in ("default", "0.0.0.0/0"):
            occupied.append(ipaddress.ip_network(destination, strict=False))
    candidates = (selected["subnet"],) if selected else SPECIAL_PRIVATE_POOL
    for candidate in candidates:
        require(candidate in SPECIAL_PRIVATE_POOL, "SPECIAL_NETWORK_POOL")
        subnet = ipaddress.ip_network(candidate)
        if any(other.version == 4 and subnet.overlaps(other) for other in occupied):
            continue
        value = {"subnet": str(subnet), "gateway": str(subnet.network_address + 1),
                 "nginx": str(subnet.network_address + 10), "dynamic_range": str(next(subnet.subnets(new_prefix=25)).network_address + 128) + "/25"}
        require(selected is None or selected == value, "SPECIAL_NETWORK_PROOF_CHANGED")
        return value
    raise AdmissionError("SPECIAL_NETWORK_NO_FREE_CANDIDATE")


def validate_special_network_config(config, selected):
    require(type(selected) is dict and selected["subnet"] in SPECIAL_PRIVATE_POOL, "SPECIAL_NETWORK_PROOF")
    network = config["networks"]["private_net"]
    expected = {"subnet": selected["subnet"], "gateway": selected["gateway"], "ip_range": selected["dynamic_range"]}
    require(network["ipam"]["config"] == [expected]
            and config["services"]["nginx"]["networks"]["private_net"]["ipv4_address"] == selected["nginx"]
            and config["services"]["api-gateway"]["environment"]["GATEWAY_TRUSTED_PROXY_ADDRESSES"] == selected["nginx"]
            and ipaddress.ip_address(selected["nginx"]) in ipaddress.ip_network(selected["subnet"])
            and ipaddress.ip_address(selected["nginx"]) not in ipaddress.ip_network(selected["dynamic_range"]), "SPECIAL_NETWORK_CONFIG_BINDING")


def validate_observed_special_network(selected, network, nginx_binding):
    configs = network.get("IPAM", {}).get("Config")
    require(type(configs) is list and len(configs) == 1 and type(configs[0]) is dict and configs[0].get("Subnet") == selected["subnet"]
            and configs[0].get("Gateway") == selected["gateway"] and configs[0].get("IPRange") == selected["dynamic_range"]
            and nginx_binding.get("IPAddress") == selected["nginx"]
            and type(nginx_binding.get("IPAMConfig")) is dict
            and nginx_binding.get("IPAMConfig", {}).get("IPv4Address") == selected["nginx"], "SPECIAL_OBSERVED_NETWORK_BINDING")


def retained_inputs_check(condition, code, relative=None, actual=None, expected=None):
    if condition:
        return
    error = AdmissionError(code)
    if relative is not None:
        def closed(row):
            if not isinstance(row, dict): return None
            fields = {'dev', 'ino', 'uid', 'gid', 'mode', 'nlink', 'size', 'mtime_ns', 'ctime_ns', 'canonical', 'symlink', 'type'}
            return {key: value for key, value in row.items() if key in fields and (type(value) in (int, bool) or key == 'type' and value in ('REGULAR', 'DIRECTORY', 'OTHER'))}
        named = {'relativePath': relative} if len(relative) <= 512 and all(c.isprintable() and c != '\\' for c in relative) else {
            'relativeNameSha256': hashlib.sha256(relative.encode('utf-8', 'surrogateescape')).hexdigest()}
        error.preserved_input = {**named, 'actualMetadata': closed(actual), 'expectedMetadata': closed(expected)}
    raise error


def retained_inputs_stat(path, relative=None):
    try:
        info = path.lstat()
    except OSError:
        retained_inputs_check(False, 'PRESERVED_INPUT_METADATA_UNAVAILABLE', relative)
    symlink = stat.S_ISLNK(info.st_mode)
    canonical = not symlink and path.resolve(strict=True) == path
    row = {'dev': info.st_dev, 'ino': info.st_ino, 'uid': info.st_uid, 'gid': info.st_gid,
            'mode': stat.S_IMODE(info.st_mode), 'nlink': info.st_nlink, 'size': info.st_size,
            'mtime_ns': info.st_mtime_ns, 'ctime_ns': info.st_ctime_ns, 'type': 'REGULAR' if stat.S_ISREG(info.st_mode) else 'OTHER',
            'canonical': canonical, 'symlink': symlink}
    retained_inputs_check(stat.S_ISREG(info.st_mode) and not symlink and info.st_nlink == 1 and canonical,
                          'PRESERVED_INPUT_REGULAR_CANONICAL_SINGLE_LINK', relative, row)
    return row


def retained_inputs_source(root, owner):
    name = 'tests/e2e/infra/scripts/generate-test-certs.sh'
    expected = 'b230097df5adb974bd7fda16d32abe07b72506578ffb2ebf98bc86ee0405c01b'
    file = root / name
    info = file.lstat()
    mode = stat.S_IMODE(info.st_mode)
    retained_inputs_check(owner['sources'].get(name) == expected and stat.S_ISREG(info.st_mode)
                          and info.st_uid == os.getuid() and info.st_nlink == 1 and not file.is_symlink()
                          and file.resolve(strict=True) == file and mode & 0o022 == 0 and mode & 0o7000 == 0
                          and bool(mode & 0o400) and 0 < info.st_size <= 512 * 1024, 'PRESERVED_INPUT_PRODUCER_SOURCE', name)
    raw = file.read_bytes()
    retained_inputs_check(len(raw) == info.st_size and hashlib.sha256(raw).hexdigest() == expected,
                          'PRESERVED_INPUT_PRODUCER_BYTES', name)
    entries = capture(['git', 'ls-tree', '-r', '-l', '-z', 'HEAD']).split('\0')
    matching = [entry.split('\t', 1)[0].split() for entry in entries if '\t' in entry and entry.split('\t', 1)[1] == name]
    retained_inputs_check(len(matching) == 1 and len(matching[0]) == 4 and matching[0][0] in ('100644', '100755')
                          and matching[0][1] == 'blob' and re.fullmatch('[a-f0-9]{40}', matching[0][2])
                          and matching[0][3] == str(len(raw)), 'PRESERVED_INPUT_PRODUCER_GIT_ENTRY', name)
    blob = capture(['git', 'cat-file', 'blob', matching[0][2]]).encode('utf-8')
    retained_inputs_check(blob == raw, 'PRESERVED_INPUT_PRODUCER_GIT_BYTES', name)
    return {'path': name, 'sha256': expected, 'git_blob': matching[0][2]}


def retained_inputs_contract(root, run, owner, frozen, topology):
    producer = retained_inputs_source(root, owner)
    certs = run / 'certs'
    cert_info = certs.lstat()
    retained_inputs_check(stat.S_IMODE(cert_info.st_mode) in (0o700, 0o755), 'PRESERVED_INPUT_CERT_PARENT_MODE', 'certs')
    owned(certs, stat.S_IMODE(cert_info.st_mode), directory=True)
    owned(certs / 'auth-secrets', 0o700, directory=True)
    specs = {
        'academic': ('academic-service', 10001, 10001, 0o600, '/run/rct-academic-tls/academic-server.key'),
        'schedule': ('schedule-service', 10002, 10002, 0o600, '/run/rct-schedule-tls/schedule-server.key'),
        'attendance': ('attendance-service', 10003, 10003, 0o600, '/run/rct-attendance-tls/attendance-server.key'),
        'mobile-bff': ('mobile-bff', 10007, 10007, 0o600, '/run/rct-mobile-bff-tls/mobile-bff-server.key'),
        'auth': ('auth-service', 0, 10004, 0o640, '/run/rct-auth-tls/auth-server.key')}
    names = ('bot-to-read-token mobile-bff-to-auth-token academic-to-auth-token attendance-to-auth-token '
             'auth-to-academic-presentation-token mobile-bff-to-academic-token mobile-bff-to-attendance-token '
             'mobile-bff-to-schedule-token attendance-bot-read-to-academic-token attendance-bot-read-to-schedule-token '
             'academic-report-to-attendance-token academic-report-to-schedule-token notification-to-academic-token '
             'bot-to-auth-token pg-legacy-runtime-password pg-migration-password pg-monitor-password '
             'pg-auth-runtime-password pg-auth-migration-password pg-academic-runtime-password').split()
    readable = {'certs/auth-secrets/' + name for name in names}
    retained_inputs_check(sorted(file.name for file in (certs / 'auth-secrets').iterdir()) == sorted(names),
                          'PRESERVED_INPUT_EXACT_SECRET_NAMES', 'certs/auth-secrets')
    for name in sorted(readable):
        row = retained_inputs_stat(run / name, name)
        retained_inputs_check((row['uid'], row['gid'], row['mode']) == (os.getuid(), os.getgid(), 0o444),
                              'PRESERVED_INPUT_READABLE_ROLE_OWNER_MODE', name, row)
    opaque = {}
    for stem, (service, uid, gid, mode, destination) in specs.items():
        name = 'certs/' + stem + '-server.key'
        path = run / name
        row = retained_inputs_stat(path, name)
        retained_inputs_check((row['uid'], row['gid'], row['mode']) == (uid, gid, mode) and 0 < row['size'] <= 1024**2,
                              'PRESERVED_INPUT_OPAQUE_ROLE_METADATA', name, row, {'uid': uid, 'gid': gid, 'mode': mode})
        matches = [(alias, mount) for alias, pin in frozen['services'].items() for mount in pin['mounts'] if mount.get('Source') == str(path)]
        retained_inputs_check(len(matches) == 1 and matches[0][0] == service and matches[0][1]['Type'] == 'bind'
                              and matches[0][1]['Destination'] == destination and matches[0][1]['RW'] is False,
                              'PRESERVED_INPUT_OPAQUE_READONLY_SOURCE_MOUNT', name)
        opaque[name] = {'stat': row, 'service': service, 'source_mount': canonical_mount_records([matches[0][1]])}
    retained_inputs_reject_target_keys(run, topology, {}, ())
    return {'producer': producer, 'opaque': opaque, 'readable': sorted(readable)}


def retained_inputs_reject_target_keys(run, topology, target_config, rows=()):
    keys = [run / ('certs/' + stem + '-server.key') for stem in ('academic', 'schedule', 'attendance', 'mobile-bff', 'auth')]
    sources = [item['source'] for binds in topology['allowed_binds'].values() for item in binds]
    sources += [item['source'] for service in target_config.get('services', {}).values() for item in service.get('volumes', []) if item.get('type') == 'bind']
    sources += [item['file'] for item in target_config.get('secrets', {}).values()]
    sources += [mount['Source'] for row in rows for mount in row['Mounts'] if mount['Type'] == 'bind']
    for source in sources:
        path = Path(source)
        retained_inputs_check(path.is_absolute() and '..' not in path.parts and not any(key == path or key.is_relative_to(path) for key in keys),
                              'PRESERVED_INPUT_OPAQUE_FORBIDDEN_IN_TARGET', 'restored-database-bind')


def retained_inputs_inventory(run, roles):
    files, directories, opaque = {}, {}, {}
    total = 0
    pending = [run]
    while pending:
        directory = pending.pop()
        directory_info = directory.lstat()
        directory_mode = stat.S_IMODE(directory_info.st_mode)
        name = directory.relative_to(run).as_posix()
        directory_stat = {key: getattr(directory_info, 'st_' + key) for key in ('dev', 'ino', 'uid', 'gid', 'nlink', 'size', 'mtime_ns', 'ctime_ns')}
        retained_inputs_check(directory_mode in (0o700, 0o755) and not directory.is_symlink() and directory.resolve(strict=True) == directory,
                              'PRESERVED_INPUT_DIRECTORY_CANONICAL_MODE', name, {**directory_stat, 'mode': directory_mode, 'type': 'DIRECTORY'})
        try:
            owned(directory, directory_mode, directory=True)
        except AdmissionError:
            retained_inputs_check(False, 'PRESERVED_INPUT_DIRECTORY_OWNER_MODE', name,
                                  {**directory_stat, 'mode': directory_mode, 'type': 'DIRECTORY'}, {'uid': os.getuid()})
        entries = sorted(directory.iterdir())
        directories[name] = {'mode': directory_mode, 'names': [file.name for file in entries], 'stat': directory_stat}
        require(len(directories) <= 1024, 'PRESERVED_INPUT_DIRECTORY_BOUND')
        for file in entries:
            name = file.relative_to(run).as_posix()
            info = file.lstat()
            if stat.S_ISDIR(info.st_mode):
                retained_inputs_check(not file.is_symlink() and file.resolve(strict=True) == file,
                                      'PRESERVED_INPUT_DIRECTORY_CANONICAL_MODE', name)
                pending.append(file)
                continue
            row = retained_inputs_stat(file, name)
            if name in roles['opaque']:
                retained_inputs_check(row == roles['opaque'][name]['stat'], 'PRESERVED_INPUT_OPAQUE_CHANGED_DURING_ADMISSION', name, row, roles['opaque'][name]['stat'])
                opaque[name] = roles['opaque'][name]
                continue
            mode = row['mode']
            expected_mode = 0o444 if name in roles['readable'] else mode
            retained_inputs_check(mode == expected_mode and (name in roles['readable'] or mode in (0o600, 0o644)),
                                  'PRESERVED_INPUT_READABLE_MODE', name, row)
            if name in roles['readable']:
                retained_inputs_check(row['uid'] == os.getuid() and row['gid'] == os.getgid(), 'PRESERVED_INPUT_READABLE_ROLE_OWNER', name, row)
            try:
                owned(file, expected_mode)
            except AdmissionError as error:
                retained_inputs_check(False, 'PRESERVED_INPUT_ORDINARY_OWNER_MODE', name, row, {'uid': os.getuid(), 'mode': expected_mode})
            total += row['size']
            require(0 <= row['size'] <= 2 * 1024**3 and total <= 4 * 1024**3 and len(files) < 4096, 'PRESERVED_INPUT_READABLE_BOUND')
            digest = hashlib.sha256()
            with file.open('rb') as stream:
                for block in iter(lambda: stream.read(1024**2), b''):
                    digest.update(block)
            retained_inputs_check(retained_inputs_stat(file, name) == row, 'PRESERVED_INPUT_READABLE_CHANGED_DURING_HASH', name)
            files[name] = {'mode': mode, 'size': row['size'], 'sha256': digest.hexdigest(), 'stat': row}
        after = directory.lstat()
        require(stat.S_ISDIR(after.st_mode) and stat.S_IMODE(after.st_mode) == directory_mode
                and all(getattr(after, 'st_' + key) == value for key, value in directory_stat.items()), 'PRESERVED_INPUT_DIRECTORY_CHANGED_DURING_SCAN')
    retained_inputs_check(set(opaque) == set(roles['opaque']) and set(opaque).isdisjoint(files)
                          and set(roles['readable']) <= set(files), 'PRESERVED_INPUT_MANDATORY25_COVERAGE')
    return files, directories, opaque, {'producer': roles['producer'], 'readable': roles['readable'], 'opaqueRoles': sorted(roles['opaque'])}


def validate_retained_restored_cohort(path, root=None):
    """Exact fourth closure: observe already-stopped d8fa source17 + restored3."""
    root = ROOT if root is None else root
    source = 'daf71da9d5623ac34d4bb3afe3e4ea1d88ddec2f'
    project = 'rct-local-stage17-d8fa51971e13'
    snapshot_sha = '5c6b69a26af839d6a0222e4d9e323c7ffc3a77e536410f11a698bbe164aea479'
    chain = 'c490c6a07b6e134874694f8f76e009913e09c53f8bbee12542b93e861226e851'
    checks = Path.home() / '.local/share/rct-pr3-local/checks'
    require(path.is_absolute() and path.resolve() == path and path.is_relative_to(checks)
            and path.name == 'retained-restored-closure.private.json', 'RETAINED20_RECEIPT_PATH')
    owned(path.parent, 0o700, directory=True)
    value = private_json(path)
    run = root / '.agent/evidence/local-runs' / project
    require(value['schema'] == 'RCT_RETAINED_PRESERVED_INPUTS_CLOSURE_V1' and value['status'] == 'OBSERVED_ALREADY_STOPPED'
            and value['opaque_content_qualification'] == 'METADATA_ONLY_NOT_READ_OR_HASHED'
            and value['operation'] == 'READ_ONLY_OBSERVATION' and value['stop_commands_executed'] == 0
            and value['uid'] == os.getuid() and value['root'] == str(root) and value['run'] == str(run)
            and value['project'] == project and value['source_sha'] == source
            and value['snapshot_sha256'] == snapshot_sha and value['transition_chain'] == chain
            and value['diagnostic_sha256'] == '555e030bacbe96c27d2bc2db94a873d7a21a7e7d4d3db6d89f707480d25d5010'
            and value['metadata_sha256'] == '24b11736c5e5d22052489cdf897e9b4b32bfe044bb0da1f255394f143b0d0861'
            and re.fullmatch(r'd8fa51971e13[a-f0-9]{12}', value['nonce'])
            and value['target_project'] == 'rct-recovery-' + value['nonce'], 'RETAINED20_BINDING')
    observation_path = path.parent / 'observed-already-stopped.private.json'
    owned(observation_path, 0o600)
    require('intent_sha256' not in value and not (path.parent / 'pre-stop.private.json').exists()
            and not (path.parent / 'pre-stop.private.json').is_symlink(), 'RETAINED20_NO_FICTITIOUS_STOP_INTENT')
    require(hashlib.sha256(observation_path.read_bytes()).hexdigest() == value['observation_sha256'], 'RETAINED20_OBSERVATION_SHA')
    observation = private_json(observation_path)
    expected = dict(value)
    expected.pop('observation_sha256')
    require(observation == expected, 'RETAINED20_OBSERVATION_MATCH')
    owned(run, 0o700, directory=True)
    required = {'owner.json', 'runtime-identity.json', 'compose.json', 'result.safe.json',
                'clock/current-instant', 'lanes/application-recovery/pending.json',
                'lanes/application-recovery/capture.private.json',
                'lanes/application-recovery/pending.result.safe.json',
                'lanes/application-recovery/seed.result.safe.json',
                'lanes/application-recovery/lifecycle-input.private.json',
                'lanes/application-recovery/fresh-target.compose.json'}
    require(required <= set(value['files']) and 'lanes/application-recovery/bundle' in value['directories']
            and 1 <= len(value['files']) <= 4096 and 1 <= len(value['directories']) <= 1024,
            'RETAINED20_ARTIFACT_INVENTORY')
    owner = private_json(run / 'owner.json')
    frozen = private_json(run / 'runtime-identity.json')
    failed = private_json(run / 'result.safe.json')
    require(failed.get('result') == 'FAIL' and failed.get('cleanup') == 'FAIL_RETAINED_FOR_OWNER'
            and failed.get('container_logs') == 'PASS', 'RETAINED20_OLD_FAILURE_PRESERVED')
    require(value['files']['owner.json']['sha256'] == '0ab0e385e339a51f976c7a949462f94d0d523f9351fe2c90388af73900ee2bd7'
            and value['files']['runtime-identity.json']['sha256'] == '656220d21aee437ee80591d528e2063359a3d53ff46308100949419f671b4b20'
            and value['files']['compose.json']['sha256'] == '1adff2427d90211caed454a09c62ef57ee8474ed7dff303002e58e271e1aa5ef',
            'RETAINED20_ACCEPTED_SNAPSHOT_BINDINGS')
    require(all(hashlib.sha256((run / name).read_bytes()).hexdigest() == value['files'][name]['sha256']
                for name in ('owner.json', 'runtime-identity.json', 'compose.json')), 'RETAINED20_CURRENT_MANIFEST_BYTES')
    require(value['files']['lanes/application-recovery/seed.result.safe.json']['sha256'] == 'bf6862068edde12fd1f1b3066b37d956149ea70391e1f109e767c07d270aa806'
            and value['files']['lanes/application-recovery/pending.result.safe.json']['sha256'] == 'afe5013a917288939b0d683d764877a24ad0569a55f033627ecada0c17f1c81a',
            'RETAINED20_ACCEPTED_SEED_PENDING')
    require(owner['uid'] == os.getuid() and owner['root'] == str(root) and owner['run'] == str(run)
            and owner['source_sha'] == source and owner['project'] == project and owner['nonce'] == value['nonce']
            and owner['special_intent']['recovery_project'] == value['target_project'], 'RETAINED20_OWNER')
    records, digest = special_records(owner)
    require(digest == chain and len(records) == 11 and records[-1]['kind'] == 'outcome'
            and records[-1]['success'] is False and records[-1]['restored'] is False
            and not any(row['kind'] == 'recovery-seal' for row in records), 'RETAINED20_CHAIN')
    registration = [row for row in records if row['kind'] == 'recovery-register']
    require(len(registration) == 1 and registration[0]['project'] == value['target_project'], 'RETAINED20_REGISTRATION')
    topology = registration[0]
    target_file = run / 'lanes/application-recovery/fresh-target.compose.json'
    require(topology['file'] == str(target_file) and hashlib.sha256(target_file.read_bytes()).hexdigest() == topology['sha256'],
            'RETAINED20_REGISTERED_TARGET_CONFIG')
    target_config = private_json(target_file)
    roles = retained_inputs_contract(root, run, owner, frozen, topology)
    retained_inputs_reject_target_keys(run, topology, target_config)
    current_files, current_directories, current_opaque, current_roles = retained_inputs_inventory(run, roles)
    difference = set(current_files) ^ set(value['files']) | set(current_directories) ^ set(value['directories'])
    retained_inputs_check(not difference, 'PRESERVED_INPUT_FULL_TREE_COVERAGE', sorted(difference)[0] if difference else None)
    for name, actual in current_files.items():
        retained_inputs_check(actual == value['files'][name], 'PRESERVED_INPUT_READABLE_DIGEST_OR_METADATA_CHANGED', name,
                              actual['stat'], value['files'][name].get('stat'))
    for name, actual in current_directories.items():
        retained_inputs_check(actual == value['directories'][name], 'PRESERVED_INPUT_DIRECTORY_GRAPH_CHANGED', name,
                              actual['stat'], value['directories'][name].get('stat'))
    require(current_roles == value['input_roles'] and set(value['opaque_inputs']) == set(current_opaque), 'PRESERVED_INPUT_EXACT_ROLE_LEDGERS')
    for name, actual in current_opaque.items():
        retained_inputs_check(actual == value['opaque_inputs'][name], 'PRESERVED_INPUT_OPAQUE_METADATA_CHANGED', name,
                              actual['stat'], value['opaque_inputs'][name].get('stat'))
    clock = (run / 'clock/current-instant').read_bytes()
    clock_input = private_json(run / 'lanes/application-recovery/lifecycle-input.private.json')
    require(re.fullmatch(rb'\d{4}-\d\d-\d\dT09:00:00\.000Z\n', clock)
            and clock == (clock_input['expectedInstant'] + '\n').encode()
            and hashlib.sha256(clock).hexdigest() == clock_input['clockSha256'], 'RETAINED20_CLOCK12_INPUT')
    # Both approved helpers perform only immutable file validation in these two calls.
    # Bind their source before import; never invoke capture/verify/restore entry points.
    import importlib.util
    for name in ('scripts/recovery.py', 'scripts/test-application-recovery.py'):
        file = root / name
        require(file.resolve() == file and not file.is_symlink()
                and hashlib.sha256(file.read_bytes()).hexdigest() == owner['sources'][name], 'RETAINED20_BACKUP_VALIDATOR_SOURCE')
    import sys
    previous_bytecode = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        spec = importlib.util.spec_from_file_location('retained20_backup_validation', root / 'scripts/test-application-recovery.py')
        backup = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(backup)
    finally:
        sys.dont_write_bytecode = previous_bytecode
    work = run / 'lanes/application-recovery'
    operation = backup.validate_capture(private_json(work / 'capture.private.json'),
                backup.r.verify_bundle(work / 'bundle', 'database-only'), backup.r.digest(work / 'bundle/manifest.json'))
    require(operation['operation_id'] == private_json(work / 'pending.json')['operationId'], 'RETAINED20_PENDING_PLAN_BINDING')
    records, digest = special_records(owner)
    require(digest == chain and len(records) == 11 and records[-1]['kind'] == 'outcome'
            and records[-1]['success'] is False and records[-1]['restored'] is False
            and not any(row['kind'] == 'recovery-seal' for row in records), 'RETAINED20_CHAIN')
    require(sorted(file.name for file in (run / 'transitions').iterdir()) == [f'{index:04d}.json' for index in range(11)],
            'RETAINED20_EXACT_TRANSITION_FILES')
    registration = [row for row in records if row['kind'] == 'recovery-register']
    require(len(registration) == 1 and registration[0]['project'] == value['target_project'], 'RETAINED20_REGISTRATION')
    for suffix in ('verified.private.json', 'admit.result.safe.json', 'attachment.result.safe.json'):
        file = run / 'lanes/application-recovery' / suffix
        require(not file.exists() and not file.is_symlink(), 'RETAINED20_NO_SEAL_VERIFY_ADMIT')
    services = value['services']
    require(set(services) == {'source:' + name for name in SERVICES} | {'restored:' + name for name in DATABASES},
            'RETAINED20_SERVICE_SET')
    retained_inputs_reject_target_keys(run, topology, target_config,
        [{'Mounts': pin['mounts']} for name, pin in services.items() if name.startswith('restored:')])
    saved = {}
    native = run / 'lanes/application-recovery/native-logs'
    for file in sorted(native.glob('*.safe.json')):
        receipt = private_json(file)
        if receipt['action'] == 'RESOURCE_INSPECT' and receipt['exitStatus'] == 0:
            raw = file.with_name(file.name.replace('.safe.json', '.private.log')).read_bytes()
            try:
                rows = json.loads(raw)
            except (ValueError, UnicodeDecodeError):
                continue
            if type(rows) is list and len(rows) == 1 and type(rows[0]) is dict and type(rows[0].get('Config')) is dict:
                row = rows[0]
                labels = row['Config']['Labels']
                cohort = labels.get('com.docker.compose.project')
                service = labels.get('com.docker.compose.service')
                key = ('source:' if cohort == project else 'restored:') + str(service)
                require(cohort in (project, value['target_project']) and key in services, 'RETAINED20_SAVED_FOREIGN')
                saved[key] = row
    require(set(saved) == set(services), 'RETAINED20_ALL_SAVED_INSPECTS')
    retained_inputs_reject_target_keys(run, topology, target_config,
        [row for name, row in saved.items() if name.startswith('restored:')])
    for name, pin in services.items():
        row = saved[name]
        require(pin['id'] == row['Id'] and pin['image'] == row['Image'] and pin['config'] == row['Config']
                and canonical_mount_records(pin['mounts']) == canonical_mount_records(row['Mounts'])
                and pin['readonly'] == row['HostConfig'].get('ReadonlyRootfs', False),
                'RETAINED20_SAVED_IDENTITY')
        service = name.split(':', 1)[1]
        network_name = project + '_private_net'
        if name.startswith('source:'):
            if service in DATABASES:
                require(pin['networks'] == {}, 'RETAINED20_PIN_SOURCE_DATABASE_DETACHED')
            else:
                require(set(pin['networks']) == {network_name}, 'RETAINED20_PIN_SOURCE_NETWORK')
                binding = pin['networks'][network_name]
                frozen_pin = frozen['services'][service]
                require(binding['NetworkID'] == frozen['network_id']
                        and set(binding.get('Aliases') or []) == set(frozen_pin['aliases'])
                        and all(binding.get(key) == frozen_pin['network_binding'].get(key)
                                for key in ('IPAMConfig', 'Links', 'DriverOpts')), 'RETAINED20_PIN_SOURCE_ALIASES')
                if service == 'nginx':
                    config = private_json(run / 'compose.json')
                    expected_static = config['services']['nginx']['networks']['private_net']['ipv4_address']
                    require(binding.get('IPAMConfig') == frozen_pin['network_binding'].get('IPAMConfig')
                            and type(binding.get('IPAMConfig')) is dict
                            and binding['IPAMConfig'].get('IPv4Address') == expected_static == frozen_pin['network_binding']['IPAddress'],
                            'RETAINED20_PIN_FIXED_NGINX')
        if name.startswith('restored:'):
            require(set(pin['networks']) == {network_name}, 'RETAINED20_PIN_TARGET_NETWORK')
            saved_binding = row['NetworkSettings']['Networks'][network_name]
            binding = pin['networks'][network_name]
            require(binding['NetworkID'] == saved_binding['NetworkID'] == frozen['network_id']
                    and set(binding.get('Aliases') or []) == set(saved_binding.get('Aliases') or [])
                    and all(binding.get(key) == saved_binding.get(key) for key in
                            ('IPAMConfig', 'Links', 'DriverOpts')),
                    'RETAINED20_PIN_TARGET_SAVED_NETWORK')
            for mount in pin['mounts']:
                require(mount['Source'] not in registration[0]['source_mounts'], 'RETAINED20_NO_SOURCE_MOUNT')
                if mount['Type'] == 'bind':
                    require(mount['RW'] is False and any(mount['Source'] == item['source']
                            and mount['Destination'] == item['target']
                            and special_resource_digest(item['source']) == item['sha256']
                            for item in registration[0]['allowed_binds'][service]), 'RETAINED20_TARGET_BIND')
    ids = [pin['id'] for pin in services.values()]
    require(len(ids) == len(set(ids)) == 20 and all(re.fullmatch('[a-f0-9]{64}', x) for x in ids), 'RETAINED20_IDS')
    for cohort, names in ((project, SERVICES), (value['target_project'], DATABASES)):
        wanted = {pin['id'] for name, pin in services.items() if name.startswith('source:' if cohort == project else 'restored:')}
        actual = capture(['docker', 'ps', '-aq', '--no-trunc', '--filter', 'label=com.docker.compose.project=' + cohort]).splitlines()
        require(len(actual) == len(wanted) and set(actual) == wanted, 'RETAINED20_FOREIGN_OR_MISSING')
    rows = json.loads(capture(['docker', 'inspect', *sorted(ids)]))
    require(type(rows) is list and len(rows) == 20 and {row['Id'] for row in rows} == set(ids), 'RETAINED20_INSPECT_COUNT')
    pins_by_id = {pin['id']: (name, pin) for name, pin in services.items()}
    six = ('IPAddress', 'IPPrefixLen', 'Gateway', 'GlobalIPv6Address', 'GlobalIPv6PrefixLen', 'IPv6Gateway')
    network_name = project + '_private_net'
    for row in rows:
        name, pin = pins_by_id[row['Id']]
        require(row['Image'] == pin['image'] and row['Config'] == pin['config']
                and canonical_mount_records(row['Mounts']) == canonical_mount_records(pin['mounts'])
                and row['HostConfig'].get('ReadonlyRootfs', False) == pin['readonly']
                and row['State']['Running'] is False and row['State']['Paused'] is False, 'RETAINED20_IMMUTABLE_STOPPED')
        labels = pin['config']['Labels']
        service = name.split(':', 1)[1]
        cohort = project if name.startswith('source:') else value['target_project']
        require(labels['com.docker.compose.service'] == service and labels['com.docker.compose.project'] == cohort
                and labels[LABEL] == value['nonce'], 'RETAINED20_CONFIG_LABELS')
        if name.startswith('source:'):
            require(pin['id'] == frozen['services'][service]['id'] and pin['image'] == frozen['services'][service]['image'],
                    'RETAINED20_FROZEN_ID')
            require(pin['config']['Labels'] == frozen['services'][service]['labels']
                    and pin['config']['Image'] == frozen['services'][service]['configured_image']
                    and canonical_mount_records(pin['mounts']) == canonical_mount_records(frozen['services'][service]['mounts'])
                    and pin['readonly'] == frozen['services'][service]['readonly'], 'RETAINED20_FROZEN_FULL_PIN')
        else:
            require(pin['image'] == registration[0]['images'][service]
                    and labels.get('io.rutcampustrack.recovery') == 'disposable', 'RETAINED20_REGISTERED_IMAGE')
        nets = row['NetworkSettings']['Networks']
        require(set(nets) == set(pin['networks']), 'RETAINED20_NETWORK_SET')
        if name.startswith('source:') and service in DATABASES:
            require(not nets, 'RETAINED20_SOURCE_DATABASE_DETACHED')
        else:
            require(set(nets) == {network_name}, 'RETAINED20_NETWORK_NAME')
            binding = nets[network_name]
            saved = pin['networks'][network_name]
            require(binding['NetworkID'] == saved['NetworkID'] == frozen['network_id']
                    and set(binding.get('Aliases') or []) == set(saved.get('Aliases') or [])
                    and all(binding.get(key) == saved.get(key) for key in ('IPAMConfig', 'Links', 'DriverOpts'))
                    and all(binding.get(field) in (None, '', 0) for field in six)
                    and binding.get('EndpointID') in (None, ''), 'RETAINED20_STOPPED_NETWORK')
    networks = json.loads(capture(['docker', 'network', 'inspect', network_name]))
    require(type(networks) is list and len(networks) == 1 and networks[0].get('Containers', {}) == {}, 'RETAINED20_NETWORK_EMPTY')
    network = networks[0]
    require({key: network.get(key) for key in value['network']} == value['network']
            and network['Id'] == frozen['network_id']
            and network['Labels'].get(LABEL) == value['nonce']
            and network['Labels'].get('com.docker.compose.project') == project, 'RETAINED20_NETWORK_IDENTITY')
    attached = capture(['docker', 'ps', '-aq', '--no-trunc', '--filter', 'network=' + network_name]).splitlines()
    require(len(attached) == len(set(attached)) and set(attached) == {pin['id'] for pin in services.values() if pin['networks']},
            'RETAINED20_NETWORK_CONNECTIONS')
    for volume, wanted in value['volume_connections'].items():
        require(re.fullmatch('[A-Za-z0-9][A-Za-z0-9_.-]{0,255}', volume), 'RETAINED20_VOLUME_NAME')
        expected = sorted(pin['id'] for pin in services.values() if any(
            mount['Type'] == 'volume' and mount['Name'] == volume for mount in pin['mounts']))
        require(wanted == expected and bool(expected), 'RETAINED20_VOLUME_PIN_CONSUMERS')
        actual = capture(['docker', 'ps', '-aq', '--no-trunc', '--filter', 'volume=' + volume]).splitlines()
        require(len(actual) == len(set(actual)) and sorted(actual) == wanted, 'RETAINED20_VOLUME_CONNECTIONS')
    require(set(value['volume_connections']) == {mount['Name'] for pin in services.values()
            for mount in pin['mounts'] if mount['Type'] == 'volume'}, 'RETAINED20_ALL_VOLUME_CONNECTIONS')
    config = private_json(run / 'compose.json')
    require(value['project_resources'][value['target_project']] == {'volume': [], 'network': []}
            and value['project_resources'][project]['network'] in ([frozen['network_id']], [frozen['network_id'][:12]])
            and value['project_resources'][project]['volume'] == sorted(item['name'] for item in config.get('volumes', {}).values()),
            'RETAINED20_FROZEN_PROJECT_RESOURCES')
    for cohort in (project, value['target_project']):
        for kind in ('volume', 'network'):
            actual = capture(['docker', kind, 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + cohort]).splitlines()
            require(sorted(actual) == value['project_resources'][cohort][kind], 'RETAINED20_PROJECT_RESOURCES')
    prior = value['prior_closure_sha256']
    require(set(prior) == {'boot-correction-QuIxvmeI', 'nonclock-correction-MiifvCjI', 'lifecycle-prerequisites-clock-m6UsYDsR'},
            'RETAINED20_PRIOR_CLOSURES')
    for directory, digest in prior.items():
        older = checks / directory / 'retained-closure.private.json'
        owned(older.parent, 0o700, directory=True)
        owned(older, 0o600)
        require(hashlib.sha256(older.read_bytes()).hexdigest() == digest, 'RETAINED20_PRIOR_CLOSURE_SHA')
    return {'ids': sorted(ids)}


def preflight(*, special=False, retained_receipt=None, retained_pdf_receipt=None, retained_recovery_receipt=None, retained_restored_receipt=None, selected_network=None):
    require(sys.platform == "linux" and os.getuid() != 0, "UBUNTU_NONROOT_REQUIRED")
    require(RUNS.resolve() == RUNS, "LINKED_RUNTIME_PARENT")
    for name in ("GITHUB_ACTIONS", "RUNNER_ENVIRONMENT", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_SHA"):
        require(not os.environ.get(name), "DO_NOT_SPOOF_CI")
    for name in ("docker", "git", "node", "npm", "openssl", "certutil", "java", "sudo", "ip", "bash"):
        require(shutil.which(name), "MISSING_LOCAL_DEPENDENCY")
    require(shutil.disk_usage(ROOT).free >= 8 * 1024**3, "DISK_BELOW_8_GIB")
    require(int(next(line.split()[1] for line in Path("/proc/meminfo").read_text().splitlines()
                     if line.startswith("MemAvailable:"))) >= 6 * 1024**2, "MEMORY_BELOW_6_GIB")
    version = capture(["docker", "compose", "version", "--short"]).strip().lstrip("v").split(".")
    require(tuple(map(int, version[:2])) >= (2, 24), "COMPOSE_VERSION")
    for port in (8443, 8088):
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", port))
    require(not capture(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=rct-e2e"]).strip(), "FOREIGN_E2E_COHORT")
    local_ids = capture(["docker", "ps", "-aq", "--no-trunc", "--filter", "label=" + LABEL]).splitlines()
    if special:
        retained = validate_retained_cohort(retained_receipt) if retained_receipt else {"ids": []}
        retained_pdf = validate_retained_cohort(retained_pdf_receipt, pdf=True) if retained_pdf_receipt else {"ids": []}
        retained_recovery = validate_retained_cohort(retained_recovery_receipt, recovery=True) if retained_recovery_receipt else {"ids": []}
        retained_restored = validate_retained_restored_cohort(retained_restored_receipt) if retained_restored_receipt else {"ids": []}
        require(retained_restored_receipt is None or retained_recovery_receipt is not None, "RETAINED_RESTORED_REQUIRES_OLDER_RECEIPTS")
        require(retained_recovery_receipt is None or retained_pdf_receipt is not None, "RETAINED_RECOVERY_REQUIRES_OLDER_RECEIPTS")
        require(retained_pdf_receipt is None or retained_receipt is not None, "RETAINED_PDF_REQUIRES_BOOT_RECEIPT")
        require(set(local_ids) == set(retained["ids"]) | set(retained_pdf["ids"]) | set(retained_recovery["ids"]) | set(retained_restored["ids"]), "PREVIOUS_LOCAL_COHORT")
        selected = special_network_choice(selected_network)
        require(capture(["git", "ls-files", "--error-unmatch", "tests/e2e/.env.ci"]).strip(), "TRACKED_SYNTHETIC_ENV_REQUIRED")
        return selected
    require(not local_ids and retained_receipt is None and retained_pdf_receipt is None and retained_recovery_receipt is None and retained_restored_receipt is None and selected_network is None, "PREVIOUS_LOCAL_COHORT")
    # Refuse collision rather than changing Gateway's trusted fixed-edge subnet.
    subnet = ipaddress.ip_network("172.30.0.0/24")
    networks = capture(["docker", "network", "ls", "-q"]).splitlines()
    for network in json.loads(capture(["docker", "network", "inspect", *networks])) if networks else []:
        for item in network.get("IPAM", {}).get("Config") or []:
            if item.get("Subnet"):
                other = ipaddress.ip_network(item["Subnet"])
                require(other.version != 4 or not subnet.overlaps(other), "DOCKER_SUBNET_COLLISION")
    for row in json.loads(capture(["ip", "-j", "route", "show"])):
        destination = row.get("dst", "default")
        if destination != "default":
            other = ipaddress.ip_network(destination, strict=False)
            require(not subnet.overlaps(other), "HOST_SUBNET_COLLISION")
    require(capture(["git", "ls-files", "--error-unmatch", "tests/e2e/.env.ci"]).strip(), "TRACKED_SYNTHETIC_ENV_REQUIRED")


class Runner:
    canonical_mount_records = staticmethod(canonical_mount_records)
    def __init__(self, mode, *, startup_diagnostic=False, bundled_dockerfile_frontend=False, failed_cases=None, special_cohort=None, lifecycle_only=False, operational=False, operational_until=None):
        require(not startup_diagnostic or mode == "client", "CLIENT_DIAGNOSTIC_ONLY")
        require(not bundled_dockerfile_frontend or mode in {"ui-batch", "special13"}, "BUNDLED_FRONTEND_UI_BATCH_ONLY")
        require(failed_cases is None or mode == "ui-batch", "BATCH_CASES_UI_BATCH_ONLY")
        require((mode == "special13" and special_cohort in {"nonclock", "lifecycle"})
                or (mode != "special13" and special_cohort is None), "SPECIAL_COHORT_REQUIRED")
        self.special_cohort = special_cohort
        require(type(lifecycle_only) is bool and (not lifecycle_only or mode == "special13" and special_cohort == "lifecycle"), "LIFECYCLE_ONLY_SPECIAL13")
        self.lifecycle_only = lifecycle_only
        self.operational_admitted = operational
        self.operational_until = operational_until
        if operational_until is not None:
            operational_selection(operational, operational_until, mode=mode, lifecycle_only=lifecycle_only)
        self.operational_lanes = SPECIAL_LIFECYCLE[:SPECIAL_LIFECYCLE.index(operational_until) + 1] if operational_until else SPECIAL_LIFECYCLE
        self.special_network = None
        self.special_retained_receipt = None
        self.special_retained_pdf_receipt = None
        self.special_retained_recovery_receipt = None
        self.special_retained_restored_receipt = None
        self.special_outcomes = []
        self.failed_cases = selected_batch_cases(failed_cases)
        self.startup_diagnostic = startup_diagnostic
        self.bundled_dockerfile_frontend = bundled_dockerfile_frontend
        self.mode, self.phase, self.child = mode, "PREPARE", None
        self.nonce = secrets.token_hex(12)
        self.project = "rct-local-stage17-" + self.nonce[:12]
        self.run = RUNS / self.project
        self.image_targets, self.image_ids, self.image_admitted = [], {}, set()
        allowed = {"PATH", "USER", "LOGNAME", "LANG", "LC_ALL", "TZ", "SHELL", "JAVA_HOME",
                   "VIRTUAL_ENV", "SSL_CERT_FILE", "SSL_CERT_DIR", "REQUESTS_CA_BUNDLE", "DOCKER_HOST"}
        self.env = {key: value for key, value in os.environ.items() if key in allowed}
        # Explicit synthetic file is the only source of Compose credentials.
        self.env.update(RCT_E2E_SYNTHETIC_SEED="true", RCT_E2E_ASYNC_REPORT="false" if mode in {"client", "lesson-lifecycle", "ui-batch", "special13"} else "true", RCT_E2E_REMAINING5="true",
                        RCT_E2E_SYNTHETIC_BASELINE=BASELINE, E2E_BASE_URL="https://localhost:8443",
                        RCT_E2E_TEACHING_CLOCK_DIR=str(self.run / "clock"), RCT_REMAINING5_RUN=str(self.run),
                        RCT_LOCAL_STAGE17_MANIFEST=str(self.run / "owner.json"), RCT_LOCAL_STAGE17_NONCE=self.nonce,
                        RCT_LOCAL_STAGE17_MODE=mode,
                        RCT_DIAGNOSTIC_CONTEXT="local", RCT_DIAGNOSTIC_RUN_DIR=str(self.run),
                        RCT_DIAGNOSTIC_CHECKOUT_ROOT=str(ROOT),
                        RCT_LOCAL_CLIENT_STARTUP_DIAGNOSTIC="true" if startup_diagnostic else "false",
                        PLAYWRIGHT_NO_COPY_PROMPT="1", NODE_OPTIONS="--max-old-space-size=512",
                        COMPOSE_PARALLEL_LIMIT="1", BUILDKIT_MAX_PARALLELISM="1")
        if mode == "special13":
            self.env["RCT_LOCAL_SPECIAL_COHORT"] = special_cohort
            self.env["CI"] = "true"  # Fixture noninteractive guard, never a hosted identity.
        if self.failed_cases is not None:
            self.env["RCT_LOCAL_UI_BATCH_CASES"] = json.dumps(self.failed_cases)
        if mode == "lesson-lifecycle":
            self.env["RCT_LESSON_LIFECYCLE_RUN"] = str(self.run)
        if bundled_dockerfile_frontend:
            self.env["BUILDX_BUILDER"] = "default"

    def command(self, phase, args, *, cwd=ROOT, stdin=None):
        if self.mode == "special13":
            return self.special_command(phase, args, cwd=cwd, stdin=stdin)
        self.phase = phase
        emit(phase, state="RUNNING")
        with (self.run / (phase.lower() + ".private.log")).open("xb") as log:
            self.child = subprocess.Popen(args, cwd=cwd, env=self.env, stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                                          stdout=log, stderr=subprocess.STDOUT,
                                          # Retain the caller's controlling tty for sudo -v's timestamp,
                                          # but retain a distinct group for scoped cancellation.
                                          start_new_session=phase != "CERTIFICATES",
                                          process_group=0 if phase == "CERTIFICATES" else None,
                                          # Only these public read-only fixture inputs cross container UIDs.
                                          # Private logs and the enclosing run retain the launcher's 077 mask.
                                          umask=0o022 if phase == "CLOCK_PREPARE" else -1)
            try:
                if stdin:
                    self.child.stdin.write(stdin)
                    self.child.stdin.close()
                while True:
                    try:
                        code = self.child.wait(timeout=15)
                        break
                    except subprocess.TimeoutExpired:
                        emit(phase, state="RUNNING")
                if code != 0:
                    raise AdmissionError("CHILD_FAILED", returncode=code)
            finally:
                if self.child.poll() is None:
                    os.killpg(self.child.pid, signal.SIGTERM)
                    try:
                        self.child.wait(timeout=45)
                    except subprocess.TimeoutExpired:
                        os.killpg(self.child.pid, signal.SIGKILL)
                        self.child.wait(timeout=10)
                self.child = None

    def special_command(self, phase, args, *, cwd=ROOT, stdin=None):
        """Bound only the new local lanes; old launch modes retain their exact command path."""
        self.phase = phase
        emit(phase, state="RUNNING")
        seconds = 120 if phase.startswith("DISCOVERY_") else 1800 if phase.startswith("BUILD_") else 900 if phase.startswith("SPECIAL_") or phase == "CLOCK_BUILD" else 600 if phase == "BOOT" else 300
        deadline, heartbeat, count = time.monotonic() + seconds, time.monotonic() + 15, 0
        limit = 16 * 1024**2
        require(stdin is None or type(stdin) is bytes and len(stdin) <= 65536, "SPECIAL_STDIN_BOUND")
        cancelled = False
        def cancel(*_):
            nonlocal cancelled
            cancelled = True
        path = self.run / (phase.lower() + ".private.log")
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), "wb") as log:
            previous = {kind: signal.signal(kind, cancel) for kind in (signal.SIGTERM, signal.SIGINT)}
            try:
                self.child = subprocess.Popen(args, cwd=cwd, env=self.env, stdin=subprocess.PIPE if stdin else subprocess.DEVNULL,
                                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=phase != "CERTIFICATES",
                                              process_group=0 if phase == "CERTIFICATES" else None,
                                              umask=0o022 if phase == "CLOCK_PREPARE" else -1)
                if cancelled:
                    raise KeyboardInterrupt()
                if stdin:
                    self.child.stdin.write(stdin)
                    self.child.stdin.close()
                with selectors.DefaultSelector() as ready:
                    ready.register(self.child.stdout, selectors.EVENT_READ)
                    while True:
                        if cancelled:
                            raise KeyboardInterrupt()
                        now = time.monotonic()
                        require(now < deadline, "SPECIAL_CHILD_TIME_BOUND")
                        if now >= heartbeat:
                            emit(phase, state="RUNNING")
                            heartbeat = now + 15
                        if not ready.select(min(0.2, deadline - now)):
                            continue
                        block = os.read(self.child.stdout.fileno(), min(65536, limit - count + 1))
                        if not block:
                            break
                        available = min(len(block), limit - count)
                        log.write(block[:available])
                        count += available
                        require(available == len(block), "SPECIAL_CHILD_OUTPUT_BOUND")
                remaining = max(0.01, deadline - time.monotonic())
                try:
                    code = self.child.wait(timeout=remaining)
                except subprocess.TimeoutExpired:
                    raise AdmissionError("SPECIAL_CHILD_TIME_BOUND") from None
                if code != 0:
                    raise AdmissionError("CHILD_FAILED", returncode=code)
            finally:
                try:
                    if self.child is not None:
                        try:
                            os.killpg(self.child.pid, signal.SIGTERM)
                        except ProcessLookupError:
                            pass
                        try:
                            self.child.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            try:
                                os.killpg(self.child.pid, signal.SIGKILL)
                            except ProcessLookupError:
                                pass
                            self.child.wait(timeout=5)
                        # A leader may have exited while another owned group member still holds the pipe.
                        try:
                            os.killpg(self.child.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        self.child.stdout.close()
                        self.child = None
                finally:
                    for kind, handler in previous.items():
                        signal.signal(kind, handler)
                    if cancelled:
                        raise KeyboardInterrupt()

    def prepare(self):
        require(self.mode != "special13" or self.operational_admitted, "EXPLICIT_OPERATIONAL_ADMISSION_REQUIRED")
        if self.mode == "special13":
            self.special_network = preflight(special=True, retained_receipt=self.special_retained_receipt, retained_pdf_receipt=self.special_retained_pdf_receipt, retained_recovery_receipt=self.special_retained_recovery_receipt, retained_restored_receipt=self.special_retained_restored_receipt)
        self.run.mkdir(mode=0o700)
        for name in ("artifacts", "home", "tmp"):
            (self.run / name).mkdir(mode=0o700)
        self.env.update(HOME=str(self.run / "home"), TMPDIR=str(self.run / "tmp"),
                        GRADLE_USER_HOME=os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")),
                        NPM_CONFIG_CACHE=os.environ.get("NPM_CONFIG_CACHE", str(Path.home() / ".npm")),
                        PLAYWRIGHT_BROWSERS_PATH=os.environ.get("PLAYWRIGHT_BROWSERS_PATH", str(Path.home() / ".cache/ms-playwright")))
        if self.mode in {"ui-batch", "special13"}:
            (self.run / "lanes").mkdir(mode=0o700)
        # Preserve exact tracked .env.ci bytes except three fresh directed tokens.
        source = capture(["git", "show", "HEAD:tests/e2e/.env.ci"])
        for name in sorted(TOKEN_NAMES):
            token = secrets.token_urlsafe(32)
            source, count = re.subn(r"(?m)^" + name + r"=.*$", name + "=" + token, source)
            require(count <= 1, "DUPLICATE_SYNTHETIC_TOKEN")
            if not count:
                source += "\n" + name + "=" + token + "\n"
        if self.mode == "special13":
            network_values = {"GATEWAY_PRIVATE_SUBNET": self.special_network["subnet"],
                              "GATEWAY_NETWORK_GATEWAY": self.special_network["gateway"],
                              "GATEWAY_NGINX_IPV4": self.special_network["nginx"],
                              "GATEWAY_DYNAMIC_IP_RANGE": self.special_network["dynamic_range"]}
            for name, value in network_values.items():
                source, count = re.subn(r"(?m)^" + name + r"=.*$", name + "=" + value, source)
                require(count <= 1, "SPECIAL_NETWORK_ENV_SOURCE")
                if not count:
                    source += "\n" + name + "=" + value + "\n"
        write(self.run / "synthetic.env", source)
        # Reuse the canonical generator, but give it a new owned destination.
        self.env["RCT_E2E_NEW_CERTS_DIR"] = str(self.run / "certs")
        self.command("CERTIFICATES", ["bash", "tests/e2e/infra/scripts/generate-test-certs.sh"])
        self.command("CLOCK_BUILD", ["bash", "gradlew", "e2eClockFixtureJar", "--no-daemon", "--max-workers=1"])
        self.command("CLOCK_PREPARE", ["node", "tests/e2e/backend-clock/prepare-clock.mjs", str(self.run / "clock"), str(ROOT / "build/e2e-clock/clock-fixture.jar")])
        if self.mode == "lesson-lifecycle":
            self.prepare_lesson_clock()
        # Compose interpolation receives no ambient credential variables.
        args = ["docker", "compose", "--env-file", str(self.run / "synthetic.env"), "-p", self.project]
        for name in COMPOSE:
            args += ["-f", name]
        config = json.loads(capture([*args, "config", "--format", "json"], env=self.env))
        require(set(config["services"]) == SERVICES, "COMPOSE_SERVICE_DRIFT")
        if self.mode == "special13":
            validate_special_network_config(config, self.special_network)
        config["name"] = self.project
        certs = str(self.run / "certs")
        original = str(ROOT / "tests/e2e/infra/certs")
        sha = capture(["git", "rev-parse", "HEAD"]).strip()
        for name, service in config["services"].items():
            service["labels"] = {**service.get("labels", {}), LABEL: self.nonce}
            service["logging"] = {"driver": "json-file", "options": {"max-size": "5m", "max-file": "2"}}
            if "build" in service:
                service["image"] = "rct-local-stage17/" + name + ":" + self.nonce
                service["build"]["labels"] = {**service["build"].get("labels", {}), LABEL: self.nonce}
                self.image_targets.append(service["image"])
            if name in APPS:
                service["environment"].update(DEPLOYMENT_ENVIRONMENT="synthetic", SERVICE_VERSION=sha,
                                             MANAGEMENT_METRICS_TAGS_ENVIRONMENT="synthetic", MANAGEMENT_METRICS_TAGS_VERSION=sha)
                # Explicit per-JVM bounds on the 17-container local cohort.
                service["environment"]["JAVA_TOOL_OPTIONS"] = "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx512m -XX:+UseG1GC"
                service["mem_limit"] = "850m"
                service["cpus"] = 2.0
            for volume in service.get("volumes", []):
                if volume.get("type") == "bind" and volume["source"].startswith(original):
                    volume["source"] = certs + volume["source"][len(original):]
                    volume["bind"] = {"create_host_path": False}
            if name == "nginx":
                service["ports"] = [{"target": 80, "published": "8088", "host_ip": "127.0.0.1", "protocol": "tcp"},
                                    {"target": 443, "published": "8443", "host_ip": "127.0.0.1", "protocol": "tcp"}]
        for value in config["secrets"].values():
            require(value["file"].startswith(original + "/"), "UNEXPECTED_SECRET_PATH")
            value["file"] = certs + value["file"][len(original):]
        for name, value in config["networks"].items():
            value["name"] = self.project + "_" + name
            value["labels"] = {LABEL: self.nonce}
        for name, value in config["volumes"].items():
            value["name"] = self.project + "_" + name
            value["labels"] = {LABEL: self.nonce}
        frontend = None
        if self.bundled_dockerfile_frontend:
            inspection = capture(["docker", "buildx", "inspect", "default"], env=self.env)
            require(re.search(r"(?m)^Driver:\s+docker\s*$", inspection), "BUNDLED_FRONTEND_DOCKER_DRIVER_REQUIRED")
            engine = capture(["docker", "version", "--format", "{{.Server.Version}}"], env=self.env).strip()
            require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9_.-]+)?", engine),
                    "BUNDLED_FRONTEND_ENGINE_VERSION")
            frontend = {"requested": True, "builder": "default", "driver": "docker",
                        "engine_version": engine,
                        "frontend": "Docker Engine bundled Dockerfile frontend",
                        "qualification": "LOCAL_OPT_IN_NOT_EXTERNAL_SYNTAX_1_7_EQUIVALENCE",
                        "dockerfiles": bundled_dockerfiles(config)}
        write(self.run / "compose.json", json.dumps(config, indent=2) + "\n")
        if frontend is not None:
            parsed = json.loads(capture([*self.compose("config"), "--format", "json"], env=self.env))
            validate_bundled_dockerfiles(parsed, frontend["dockerfiles"])
        tracked = capture(["git", "ls-files", "-z"]).split("\0")
        # Include untracked authored harness files; omit evidence and runtime files.
        paths = {name for name in tracked if name and (ROOT / name).is_file()
                 and (name.startswith(("services/", "frontends/", "proto/", "tests/e2e/", "infra/", "scripts/", "gradle/"))
                      or name in {".dockerignore", "docker-compose.e2e.yml", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew"})
                 and not (".env" in Path(name).name and name != "tests/e2e/.env.ci")}
        paths |= {"scripts/local_e2e_async.py", "tests/e2e/fixtures/local-stage17.ts"}
        paths.add("tests/e2e/fixtures/failed-case-diagnostics.ts")
        if self.mode == "ui-batch":
            paths.add("tests/e2e/playwright.local-batch.config.ts")
        if frontend is not None:
            paths |= {record["path"] for record in frontend["dockerfiles"].values()}
        if self.mode == "special13":
            paths |= {"tests/e2e/fixtures/local-special.ts", "tests/e2e/playwright.local-special.config.ts",
                      "scripts/local_e2e_lifecycle.py"}
        hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in sorted(paths)}
        require(frontend is None or all(hashes[record["path"]] == record["original_sha256"]
                for record in frontend["dockerfiles"].values()), "BUNDLED_DOCKERFILE_SOURCE_CHANGED")
        if self.special_cohort == "nonclock":
            self.image_targets.append("rct-local-stage17/notification-bot:" + self.nonce)
        value = {"schema": 1, "uid": os.getuid(), "root": str(ROOT), "run": str(self.run), "nonce": self.nonce, "mode": self.mode,
                 **({"lifecycle_only": True} if self.lifecycle_only else {}),
                 "project": self.project, "source_sha": sha, "sources": hashes, "startup_diagnostic": self.startup_diagnostic,
                 "batch_lanes": selected_batch_lanes(self.failed_cases) if self.mode == "ui-batch" else None,
                 "batch_cases": self.failed_cases,
                 "special_cohort": self.special_cohort,
                 "special_network": self.special_network,
                 "special_lanes": list(SPECIAL_NONCLOCK if self.special_cohort == "nonclock" else SPECIAL_LIFECYCLE) if self.mode == "special13" else None,
                 **({"operational_admitted": True, "docker_host": self.env["DOCKER_HOST"],
                     "operational_selected": list(self.operational_lanes)} if self.mode == "special13" else {}),
                 "special_intent": {"recovery_project": "rct-recovery-" + self.nonce,
                                    "bot_image_tag": "rct-local-stage17/notification-bot:" + self.nonce,
                                    "database_services": sorted(DATABASES), "network": self.project + "_private_net"} if self.mode == "special13" else None,
                 "bundled_dockerfile_frontend": self.bundled_dockerfile_frontend,
                 "dockerfile_frontend": frontend,
                 "node_ca_sha256": hashlib.sha256((self.run / "certs/server.crt").read_bytes()).hexdigest(),
                 "images": sorted(self.image_targets),
                 "compose_sha256": hashlib.sha256((self.run / "compose.json").read_bytes()).hexdigest(),
                 "dirty": bool(capture(["git", "status", "--porcelain", "--untracked-files=normal"]).strip()),
                 "diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD", "--binary"], cwd=ROOT)).hexdigest(),
                 "qualification": "Fresh isolated local lane; does not reproduce preceding CI steps. Source bytes recorded; HEAD identity does not assert a clean checkout"}
        if self.mode == "special13":
            (self.run / "transitions").mkdir(mode=0o700)
        write(self.run / "owner.json", json.dumps(value, indent=2) + "\n")
        self.env["NODE_EXTRA_CA_CERTS"] = str(self.run / "certs/server.crt")
        if self.mode == "special13":
            self.env["SSL_CERT_FILE"] = str(self.run / "certs/server.crt")
        self.trust()
        # Reuse the stand's pinned install. Install only when absent or version-mismatched.
        lock = json.loads((ROOT / "tests/e2e/package-lock.json").read_text())
        package = ROOT / "tests/e2e/node_modules/@playwright/test/package.json"
        if not package.is_file() or json.loads(package.read_text())["version"] != lock["packages"]["node_modules/@playwright/test"]["version"]:
            self.command("NPM_CI", ["npm", "ci", "--ignore-scripts"], cwd=ROOT / "tests/e2e")
        emit("PREPARED", mode=self.mode, run=str(self.run), source_sha=sha, dirty=value["dirty"],
             startup_diagnostic=self.startup_diagnostic, bundled_dockerfile_frontend=self.bundled_dockerfile_frontend,
             qualification="FRESH_ISOLATED_LANE")

    def prepare_lesson_clock(self):
        """Before boot only: the final CI lane uses a future weekday at 09:00 Moscow."""
        directory = self.run / "clock"
        owned(directory, 0o755, directory=True)
        marker, clock = directory / "disposable-target", directory / "current-instant"
        owned(marker, 0o644)
        owned(clock, 0o644)
        require(marker.read_bytes() == b"RCT_E2E_DISPOSABLE\n", "CLOCK_MARKER")
        original = clock.read_text()
        require(re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z\n", original), "CLOCK_INSTANT")
        current = datetime.datetime.fromisoformat(original.strip().replace("Z", "+00:00"))
        moscow = ZoneInfo("Europe/Moscow")
        date = max(current.astimezone(moscow).date(), datetime.datetime.now(moscow).date()) + datetime.timedelta(days=1)
        while date.weekday() >= 5:
            date += datetime.timedelta(days=1)
        future = datetime.datetime.combine(date, datetime.time(9), moscow).astimezone(datetime.timezone.utc)
        require(future > current, "CLOCK_NOT_FORWARD")
        # No services have started and this new directory is exclusively owned.
        clock.write_text(future.isoformat(timespec="milliseconds").replace("+00:00", "Z") + "\n")

    def trust(self):
        database = self.run / "home/.local/share/pki/nssdb"
        database.mkdir(parents=True, mode=0o700)
        args = ["certutil", "-d", "sql:" + str(database)]
        capture([*args, "-N", "--empty-password"], env=self.env)
        cert = self.run / "certs/server.crt"
        capture([*args, "-A", "-t", "P,,", "-n", self.project, "-i", str(cert)], env=self.env)
        exported = capture([*args, "-L", "-n", self.project, "-a"], env=self.env)
        pem = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", exported, re.S)
        require(pem and ssl.PEM_cert_to_DER_cert(pem[0]) == ssl.PEM_cert_to_DER_cert(cert.read_text()), "TLS_PEER_IDENTITY")

    def compose(self, *args):
        return ["docker", "compose", "-p", self.project, "-f", str(self.run / "compose.json"), *args]

    def execute(self):
        self.command("ARTIFACT_PRIVACY_CONTROLS", ["npm", "run", "test:artifact-privacy"], cwd=ROOT / "tests/e2e")
        # Separate sequential builds keep one heavy writer; up never starts another build.
        config = json.loads((self.run / "compose.json").read_text())
        for name, service in config["services"].items():
            if "build" in service:
                require(self.image_identity(service["image"]) is None, "IMAGE_TAG_ALREADY_EXISTS")
                self.image_admitted.add(service["image"])
                self.command("BUILD_" + name.upper().replace("-", "_"), self.compose("build", name))
                identifier = self.image_identity(service["image"])
                require(identifier is not None, "BUILT_IMAGE_MISSING")
                self.image_ids[service["image"]] = identifier
        if self.mode == "special13":
            self.phase = "BOOT_NETWORK_ADMISSION"
            emit(self.phase, state="RUNNING")
            preflight(special=True, retained_receipt=self.special_retained_receipt, retained_pdf_receipt=self.special_retained_pdf_receipt, retained_recovery_receipt=self.special_retained_recovery_receipt, retained_restored_receipt=self.special_retained_restored_receipt, selected_network=self.special_network)
        self.command("BOOT", self.compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "480"))
        if self.mode == "special13":
            self.freeze_special_inventory()
        validate_local(environment=self.env)
        node_tls_probe(self.run / "certs/server.crt", self.env)
        self.command("NOTIFICATION_KEY_ISOLATION", self.compose("exec", "-T", "notification-web", "sh", "-c", "test ! -e /keys/private.key && test ! -L /keys/private.key"))
        if self.mode == "special13":
            return self.execute_special()
        if self.mode == "ui-batch":
            return self.execute_batch()
        if self.mode in {"client", "lesson-lifecycle"}:
            config = "playwright.real-mobile.config.ts" if self.mode == "client" else "playwright.lesson-lifecycle.config.ts"
            self.command("CLIENT_REMAINING5" if self.mode == "client" else "LESSON_LIFECYCLE",
                         ["npx", "playwright", "test", "--config", config, "--project=chromium",
                          *(["--max-failures=1"] if self.startup_diagnostic else [])], cwd=ROOT / "tests/e2e")
            emit("BROWSER_LANE_RESULT", mode=self.mode, result="PASS", qualification="FRESH_ISOLATED_LANE")
            return
        if self.mode == "openapi":
            sys.path.insert(0, str(ROOT / "infra/prometheus/tests"))
            import check_pipeline
            self.phase = "GATEWAY_OPENAPI"
            emit(self.phase, state="RUNNING", strict_timeout_seconds=5)
            safe = {"schema": 1, "result": "FAIL", "phase": "GATEWAY_OPENAPI_PRECHECK"}
            try:
                check_pipeline.e2e_gateway_openapi(lambda *args: subprocess.run(["docker", *args], capture_output=True, text=True, check=True, timeout=20), safe, self.project)
                safe["result"] = "PASS"
            finally:
                write(self.run / "artifacts/openapi.safe.json", json.dumps(safe, indent=2) + "\n")
                emit("OPENAPI_RESULT", result=safe["result"], stage=safe["phase"])
                if safe["result"] != "PASS":
                    self.diagnose()
        else:
            try:
                self.command("STAGE17", [sys.executable, str(ROOT / "infra/prometheus/tests/check_pipeline.py"), "--e2e-async-browser"], cwd=ROOT / "tests/e2e")
            finally:
                report = self.run / "artifacts/metrics.safe.json"
                if report.is_file():
                    safe = json.loads(report.read_text())
                    emit("STAGE17_RESULT", result=safe["result"], stage=safe["phase"], actual_applications=safe["actual_applications"])
                    if safe["phase"] == "GATEWAY_OPENAPI_ACADEMIC_SERVICE" and safe["result"] == "FAIL":
                        self.diagnose()

    def freeze_special_inventory(self):
        rows = inventory(self.project, self.nonce, complete=True)
        config = json.loads((self.run / "compose.json").read_text())
        for row in rows:
            name = row["Config"]["Labels"]["com.docker.compose.service"]
            service = config["services"][name]
            expected = [(mount["source"], mount["target"], not mount.get("read_only", False))
                        for mount in service.get("volumes", []) if mount.get("type") == "bind"]
            for secret in service.get("secrets", []):
                target = secret.get("target", secret["source"])
                target = target if target.startswith("/") else "/run/secrets/" + target
                expected.append((config["secrets"][secret["source"]]["file"], target, False))
            actual = [(mount["Source"], mount["Destination"], mount["RW"]) for mount in row["Mounts"] if mount["Type"] == "bind"]
            require(sorted(actual) == sorted(expected), "SPECIAL_BOOT_BIND_CONFIG")
            for mount in service.get("volumes", []):
                if mount.get("type") == "volume" and mount.get("source"):
                    volume_name = config["volumes"][mount["source"]]["name"]
                    require(any(actual.get("Type") == "volume" and actual.get("Name") == volume_name
                                and actual["Destination"] == mount["target"] for actual in row["Mounts"]), "SPECIAL_BOOT_VOLUME_CONFIG")
            require(row["HostConfig"].get("ReadonlyRootfs", False) == service.get("read_only", False), "SPECIAL_BOOT_ROOTFS_CONFIG")
        pins = {row["Config"]["Labels"]["com.docker.compose.service"]:
                {"id": row["Id"], "image": row["Image"], "mounts": row["Mounts"],
                 "labels": row["Config"]["Labels"], "configured_image": row["Config"]["Image"],
                 "aliases": row["NetworkSettings"]["Networks"][self.project + "_private_net"].get("Aliases") or [],
                 "network_binding": row["NetworkSettings"]["Networks"][self.project + "_private_net"],
                 "readonly": row["HostConfig"].get("ReadonlyRootfs", False)} for row in rows}
        network = json.loads(capture(["docker", "network", "inspect", self.project + "_private_net"]))[0]
        nginx = next(row for row in rows if row["Config"]["Labels"]["com.docker.compose.service"] == "nginx")
        validate_observed_special_network(self.special_network, network, nginx["NetworkSettings"]["Networks"][self.project + "_private_net"])
        write(self.run / "runtime-identity.json", json.dumps({"services": pins, "network_id": network["Id"]}, sort_keys=True) + "\n")

    def special_value(self):
        return manifest(self.run / "owner.json")

    def special_context(self, lane, phase="main"):
        require(lane in SPECIAL_LANES and phase in special_phases(lane), "SPECIAL_LANE_PHASE")
        directory = self.run / "lanes" / lane
        for target in (directory, directory / "artifacts", *((directory / "artifacts" / phase,) if phase != "main" else ())):
            target.mkdir(mode=0o700, exist_ok=True)
            owned(target, 0o700, directory=True)
        return special_context(self.special_value(), lane, phase)

    def special_intent(self, lane, phase, action, services, extra=None):
        value = self.special_value()
        require(self.special_cohort == "lifecycle" or lane == "registry-native-pdf", "SPECIAL_MUTATION_COHORT")
        key = (lane, phase, action)
        require(lane in value["special_lanes"] and key in SPECIAL_ACTIONS
                and type(services) in {list, tuple, set} and set(services) == SPECIAL_ACTIONS[key], "SPECIAL_MUTATION_INTENT")
        require(extra is None or type(extra) is dict and len(json.dumps(extra)) <= 16384, "SPECIAL_INTENT_BOUND")
        special_inventory(value)
        intent = append_special(value, "intent", lane=lane, phase=phase, action=action, services=sorted(services), extra=extra or {})
        if action == "RECOVERY_QUIESCE":
            self.special_preserve_recovery = True
        return intent

    def special_outcome(self, intent, success, restored=False):
        require(type(success) is bool and type(restored) is bool and type(intent) is dict, "SPECIAL_OUTCOME_SHAPE")
        value = self.special_value()
        rows, _ = special_records(value)
        require(intent in rows and intent["kind"] == "intent" and not any(row["kind"] == "outcome" and row["intent"] == intent["index"] for row in rows), "SPECIAL_OUTCOME_INTENT")
        if restored:
            self.special_health()
        bindings = {}
        if restored:
            for row in inventory(self.project, self.nonce):
                name = row["Config"]["Labels"]["com.docker.compose.service"]
                attachment = row["NetworkSettings"]["Networks"].get(self.project + "_private_net")
                if name in SPECIAL_REBIND.get(intent["action"], set()) and attachment and row["State"]["Running"]:
                    bindings[name] = {key: attachment.get(key) for key in ("IPAddress", "IPPrefixLen", "Gateway", "GlobalIPv6Address", "GlobalIPv6PrefixLen", "IPv6Gateway")}
        return append_special(value, "outcome", intent=intent["index"], success=success, restored=restored, network_bindings=bindings)

    def special_register_recovery(self, target_file, project, source_ids):
        value = self.special_value()
        path = Path(target_file)
        require(self.special_cohort == "lifecycle" and project == value["special_intent"]["recovery_project"]
                and path.is_absolute() and path.is_relative_to(self.run) and not special_topology(value), "SPECIAL_RECOVERY_REGISTRATION")
        config = private_json(path)
        require(set(config["services"]) == DATABASES, "SPECIAL_RECOVERY_SERVICE_SET")
        original = inventory(self.project, self.nonce)
        originals = {row["Config"]["Labels"]["com.docker.compose.service"]: row for row in original}
        require(type(source_ids) is dict and set(source_ids) == DATABASES
                and all(source_ids[name] == originals[name]["Id"] for name in DATABASES), "SPECIAL_RECOVERY_SOURCE_IDS")
        source_mounts = [mount["Source"] for name in DATABASES for mount in originals[name]["Mounts"] if mount["RW"]]
        images, allowed_binds = {}, {}
        source_config = json.loads((self.run / "compose.json").read_text())
        for name, service in config["services"].items():
            require(set(service) <= {"image", "environment", "healthcheck", "volumes", "secrets", "labels", "networks"}
                    and service.get("environment") == source_config["services"][name].get("environment"), "SPECIAL_RECOVERY_CONFIG_FIELDS")
            images[name] = originals[name]["Image"]
            require(service["image"] == images[name] and not service.get("build") and not service.get("ports")
                    and service.get("labels", {}).get(LABEL) == self.nonce
                    and service.get("labels", {}).get("io.rutcampustrack.recovery") == "disposable", "SPECIAL_RECOVERY_IMAGE_OR_LABEL")
            init = {"postgres-academic": ("infra/postgres/init-roles.sh", "/docker-entrypoint-initdb.d/10-rct-roles.sh"),
                    "postgres-schedule": ("", ""),
                    "mongo-attendance": ("infra/mongo/init-mongo.js", "/docker-entrypoint-initdb.d/init-mongo.js")}[name]
            allowed_binds[name] = []
            for mount in service.get("volumes", []):
                init_allowed = mount.get("source") == str(ROOT / init[0]) and mount.get("target") == init[1]
                if init_allowed:
                    require(init[0] in value["sources"] and hashlib.sha256((ROOT / init[0]).read_bytes()).hexdigest() == value["sources"][init[0]], "SPECIAL_RECOVERY_INIT_SOURCE")
                require(mount.get("type") == "bind" and mount.get("read_only") is True
                        and (Path(mount["source"]).is_relative_to(self.run / "certs") or init_allowed)
                        and mount["source"] not in source_mounts, "SPECIAL_RECOVERY_TARGET_MOUNT")
                allowed_binds[name].append({"source": mount["source"], "target": mount["target"], "sha256": special_resource_digest(mount["source"])})
            require(service.get("secrets", []) == source_config["services"][name].get("secrets", []), "SPECIAL_RECOVERY_SECRET_BINDING")
            for secret in service.get("secrets", []):
                require(type(secret) is dict and secret["source"] in source_config.get("secrets", {})
                        and config.get("secrets", {}).get(secret["source"]) == source_config["secrets"][secret["source"]], "SPECIAL_RECOVERY_SECRET_SOURCE")
                secret_path = Path(config["secrets"][secret["source"]]["file"])
                target = secret.get("target", secret["source"])
                target = target if target.startswith("/") else "/run/secrets/" + target
                require(secret_path.is_relative_to(self.run / "certs") and secret_path.resolve() == secret_path
                        and any(mount["Type"] == "bind" and mount["RW"] is False and mount["Source"] == str(secret_path)
                                and mount["Destination"] == target for mount in originals[name]["Mounts"]), "SPECIAL_RECOVERY_SECRET_MOUNT")
                allowed_binds[name].append({"source": str(secret_path), "target": target, "sha256": special_resource_digest(secret_path)})
            require(set(service.get("networks", {})) == {"private_net"}, "SPECIAL_RECOVERY_TARGET_NETWORK")
        require(config.get("networks", {}).get("private_net", {}).get("external") is True
                and config["networks"]["private_net"].get("name") == self.project + "_private_net", "SPECIAL_RECOVERY_EXTERNAL_NETWORK")
        return append_special(value, "recovery-register", file=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                              project=project, images=images, source_ids=source_ids, source_mounts=source_mounts, allowed_binds=allowed_binds)

    def special_seal_recovery(self, target_ids, receipt_path):
        value = self.special_value()
        topology = special_topology(value)
        require(topology is not None, "SPECIAL_RECOVERY_NOT_REGISTERED")
        rows = special_recovery_inventory(value, topology, complete=True)
        actual = {row["Config"]["Labels"]["com.docker.compose.service"]: row["Id"] for row in rows}
        path = Path(receipt_path)
        require(target_ids == actual and path.is_relative_to(self.run) and path.resolve() == path, "SPECIAL_RECOVERY_SEAL_IDENTITY")
        receipt = private_json(path)
        require(type(receipt) is dict and receipt.get("result") == "PASS" and receipt.get("target_project") == value["special_intent"]["recovery_project"], "SPECIAL_RECOVERY_VERIFIER_RECEIPT")
        self.special_health()
        return append_special(value, "recovery-seal", target_ids=target_ids, receipt=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest())

    def preserve_special_health(self, rows=None, *, first=False, unavailable=False):
        """Only admitted identity rows; health stdout/error bodies never cross this boundary."""
        path = self.run / ("health-first.safe.json" if first else "health.safe.json")
        if path.exists():
            return
        try:
            require(not unavailable, "SPECIAL_HEALTH_ADMISSION_UNAVAILABLE")
            if rows is None:
                rows = special_inventory(self.special_value(), complete=False)
            services = []
            for row in rows:
                labels, state = row["Config"]["Labels"], row["State"]
                service = labels["com.docker.compose.service"]
                require(service in SERVICES, "SPECIAL_HEALTH_SERVICE")
                attempts = []
                for item in state.get("Health", {}).get("Log", [])[-5:]:
                    start, end, code = item.get("Start"), item.get("End"), item.get("ExitCode")
                    require(all(type(text) is str and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?(?:Z|[+-]\d\d:\d\d)", text) for text in (start, end))
                            and type(code) is int and -255 <= code <= 255, "SPECIAL_HEALTH_ATTEMPT")
                    attempts.append({"start": start, "end": end, "exitCode": code})
                require(type(state["Running"]) is bool and type(state.get("Paused", False)) is bool, "SPECIAL_HEALTH_STATE")
                health = state.get("Health", {}).get("Status", "none")
                services.append({"service": service, "role": "ORIGINAL" if labels["com.docker.compose.project"] == self.project else "RECOVERY",
                    "running": state["Running"], "paused": state.get("Paused", False),
                    "health": health if health in {"none", "starting", "healthy", "unhealthy"} else "OTHER", "attempts": attempts})
            require(len(services) <= 20, "SPECIAL_HEALTH_COUNT")
            receipt = {"schema": "RCT_SPECIAL_HEALTH_V1", "status": "READ", "services": services}
        except BaseException:
            receipt = {"schema": "RCT_SPECIAL_HEALTH_V1", "status": "UNAVAILABLE", "services": []}
        write(path, json.dumps(receipt, sort_keys=True) + "\n")

    def special_health(self, renderer_intent=None):
        rows = None
        try:
            value = self.special_value()
            if renderer_intent is not None:
                records, _ = special_records(value)
                require(type(renderer_intent) is dict and type(renderer_intent.get("index")) is int
                        and 0 <= renderer_intent["index"] < len(records), "SPECIAL_RENDERER_WAIT_INTENT")
                index = renderer_intent["index"]
                intent = records[index]
                require(intent == renderer_intent, "SPECIAL_RENDERER_WAIT_INTENT")
                require(intent["kind"] == "intent" and intent["lane"] == "registry-native-pdf" and intent["phase"] == "main"
                        and intent["action"] == "RENDERER_OUTAGE" and intent["services"] == ["document-renderer-service"]
                        and not any(row["kind"] == "outcome" and row["intent"] == index for row in records), "SPECIAL_RENDERER_WAIT_INTENT")
            deadline = time.monotonic() + 35
            config = json.loads((self.run / "compose.json").read_text())
            while True:
                rows = None
                rows = special_inventory(value, deadline=deadline if renderer_intent is not None else None)
                topology = special_topology(value)
                pending = None
                for row in rows:
                    service = row["Config"]["Labels"]["com.docker.compose.service"]
                    project = row["Config"]["Labels"]["com.docker.compose.project"]
                    if topology and service in DATABASES and project == self.project:
                        require(not row["State"]["Running"] and not row["NetworkSettings"]["Networks"], "SPECIAL_SOURCE_DATABASE_NOT_QUIESCENT")
                        continue
                    require(row["State"]["Running"] and not row["State"].get("Paused", False), "SPECIAL_RESTORATION_NOT_RUNNING")
                    check = config["services"][service].get("healthcheck")
                    if check and not check.get("disable", False) and check.get("test") != ["NONE"] and row["State"].get("Health", {}).get("Status") != "healthy":
                        require(renderer_intent is not None and service == "document-renderer-service", "SPECIAL_RESTORATION_NOT_HEALTHY")
                        pending = row
                if pending is None:
                    return True
                remaining = deadline - time.monotonic()
                require(remaining > 0, "SPECIAL_RENDERER_HEALTH_WAIT_EXPIRED")
                ready = json.loads(capture(["docker", "exec", pending["Id"], "wget", "-T", "2", "-qO-", "http://localhost:9095/actuator/health/readiness"], seconds=min(3, remaining)))
                require(type(ready) is dict and ready.get("status") == "UP", "SPECIAL_RENDERER_READINESS_NOT_RESTORED")
                try:
                    self.preserve_special_health(rows, first=True)
                except BaseException:
                    emit("SPECIAL_HEALTH_DIAGNOSTIC", state="UNAVAILABLE")
                remaining = deadline - time.monotonic()
                require(remaining > 0, "SPECIAL_RENDERER_HEALTH_WAIT_EXPIRED")
                time.sleep(min(1, remaining))
        except BaseException:
            try:
                self.preserve_special_health(rows, unavailable=rows is None)
            except BaseException:
                pass
            raise

    def special_verified_networks(self, service, original_id):
        """Concrete current binding proof for a same-container planned restart only."""
        value = self.special_value()
        records, _ = special_records(value)
        closed = {row["intent"] for row in records if row["kind"] == "outcome"}
        active = [row for row in records if row["kind"] == "intent" and row["index"] not in closed and service in SPECIAL_REBIND.get(row["action"], set())]
        require(len(active) == 1 and service in SERVICES and service != "nginx", "SPECIAL_NETWORK_REBIND_INTENT")
        rows = special_inventory(value, expected_lane=active[0]["lane"], expected_phase=active[0]["phase"])
        matches = [row for row in rows if row["Id"] == original_id and row["Config"]["Labels"]["com.docker.compose.service"] == service
                   and row["Config"]["Labels"]["com.docker.compose.project"] == self.project]
        require(len(matches) == 1 and matches[0]["State"]["Running"] and not matches[0]["State"].get("Paused", False), "SPECIAL_NETWORK_REBIND_IDENTITY")
        row = matches[0]
        config = json.loads((self.run / "compose.json").read_text())["services"][service]
        if config.get("healthcheck") and not config["healthcheck"].get("disable", False):
            require(row["State"].get("Health", {}).get("Status") == "healthy", "SPECIAL_NETWORK_REBIND_HEALTH")
        return row["NetworkSettings"]["Networks"]

    def special_phase(self, lane, phase="main", extra_env=None):
        require(lane in self.special_value()["special_lanes"] and phase in special_phases(lane), "SPECIAL_PHASE_SELECTION")
        directory = self.run / "lanes" / lane
        directory.mkdir(mode=0o700, exist_ok=True)
        (directory / "artifacts").mkdir(mode=0o700, exist_ok=True)
        output = directory / "artifacts" / phase if phase != "main" else directory / "artifacts"
        output.mkdir(mode=0o700, exist_ok=True)
        self.special_framework_directory(lane, phase)
        base = self.env.copy()
        self.env.update(RCT_LOCAL_SPECIAL_LANE=lane, RCT_LOCAL_SPECIAL_PHASE=phase,
                        RCT_REMAINING5_RUN=str(directory), RCT_DIAGNOSTIC_RUN_DIR=str(directory))
        flag = SPECIAL_FLAGS.get(lane)
        if flag:
            self.env[flag] = "true"
        variable = SPECIAL_LANES[lane][1]
        if variable:
            self.env[variable] = str(directory)
        if lane == "assignment":
            self.env["RCT_ASSIGNMENT_LIFECYCLE_PHASE"] = phase
        if lane == "application-recovery":
            self.env.update(RCT_APP_RECOVERY_PHASE=phase, RCT_APP_RECOVERY_PROJECT="rct-recovery-" + self.nonce)
        if lane == "semester-metadata":
            self.env["RCT_ADMIN_SEMESTER_UI_RUN"] = str(directory)
        if lane == "semester-archive":
            self.env.update(RCT_APP_RECOVERY_PROJECT="rct-recovery-" + self.nonce,
                            RCT_APP_RECOVERY_RUN=str(self.run / "lanes/application-recovery"))
        if extra_env:
            require(type(extra_env) is dict and all(type(key) is str and key.startswith("RCT_") and key not in {
                "RCT_LOCAL_STAGE17_MANIFEST", "RCT_LOCAL_STAGE17_NONCE", "RCT_LOCAL_STAGE17_MODE", "RCT_LOCAL_SPECIAL_COHORT", "RCT_LOCAL_SPECIAL_LANE", "RCT_LOCAL_SPECIAL_PHASE"} for key in extra_env), "SPECIAL_EXTRA_ENV")
            self.env.update({key: str(value) for key, value in extra_env.items()})
        code = None
        try:
            validate_local(environment=self.env, expected_mode="special13", expected_special_lane=lane, expected_special_phase=phase)
            expected_locations = self.special_expected_locations(lane)
            try:
                self.command("SPECIAL_" + lane.upper().replace("-", "_") + "_" + phase.upper(),
                             ["npx", "playwright", "test", "--config", "playwright.local-special.config.ts", "--project=chromium"], cwd=ROOT / "tests/e2e")
                code = 0
            except AdmissionError as error:
                require(str(error) == "CHILD_FAILED" and error.returncode in {1}, "SPECIAL_CHILD_INFRASTRUCTURE_FAILURE")
                code = error.returncode
            require((output / "safe-results.json").exists(), "SPECIAL_REPORT_NOT_PRODUCED")
            report = private_json(output / "safe-results.json")
            outcome = special_result(report, code, lane, phase, expected_locations)
            validate_local(environment=self.env, expected_mode="special13", expected_special_lane=lane, expected_special_phase=phase)
            write(directory / (phase + ".result.safe.json"), json.dumps(outcome, sort_keys=True) + "\n")
            event = {key: value for key, value in outcome.items() if key != "phase"}
            emit("SPECIAL_PHASE_RESULT", stage=outcome["phase"], **event)
            return outcome
        except BaseException as error:
            try:
                self.preserve_special_error(lane, phase, error)
            except BaseException:
                emit("SPECIAL_FAILURE_DIAGNOSTIC", state="UNAVAILABLE")
            raise
        finally:
            self.env = base

    def preserve_special_error(self, lane, phase, error, *, discovery=False):
        require(lane in SPECIAL_LIFECYCLE and (phase is None or phase in special_phases(lane)), "SPECIAL_FAILURE_DIAGNOSTIC_BINDING")
        value = self.special_value()
        root = self.run / "discovery" / lane if discovery else self.run / "lanes" / lane
        path = root / (phase + ".failure.safe.json") if phase else self.run / "lifecycle.failure.safe.json"
        if path.exists():
            return
        frame, codes, sources = None, set(), {}
        for file in ("scripts/local_e2e_async.py", "scripts/local_e2e_lifecycle.py"):
            raw = (ROOT / file).read_bytes()
            require(hashlib.sha256(raw).hexdigest() == value["sources"][file], "SPECIAL_DIAGNOSTIC_SOURCE_CHANGED")
            parsed = ast.parse(raw)
            sources[str(ROOT / file)] = (file, len(raw.splitlines()))
            for node in ast.walk(parsed):
                if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "require" and len(node.args) == 2 and isinstance(node.args[1], ast.Constant) and type(node.args[1].value) is str:
                    codes.add(node.args[1].value)
        for candidate in traceback.extract_tb(error.__traceback__):
            source = sources.get(candidate.filename)
            if source and candidate.name != "require" and 1 <= candidate.lineno <= source[1]:
                frame = {"source": source[0], "line": candidate.lineno, "sha256": value["sources"][source[0]]}
        kind = type(error).__name__
        code = str(error) if isinstance(error, AdmissionError) and str(error) in codes else None
        receipt = {"schema": "RCT_LOCAL_SPECIAL_FAILURE_V1", "lane": lane, "phase": phase,
            "errorClass": kind if kind in {"AdmissionError", "RuntimeError", "TimeoutExpired", "CalledProcessError", "FileNotFoundError", "PermissionError", "JSONDecodeError", "KeyboardInterrupt", "ValueError", "TypeError", "KeyError"} else "OTHER",
            "admissionCode": code, "sourceFrame": frame}
        write(path, json.dumps(receipt, sort_keys=True) + "\n")

    def special_framework_directory(self, lane, phase):
        require(lane in SPECIAL_LANES and phase in special_phases(lane), "SPECIAL_FRAMEWORK_PRIVATE_BINDING")
        root = self.run / "framework-private"
        for path in (self.run, root, root / lane, root / lane / phase):
            if path != self.run:
                path.mkdir(mode=0o700, exist_ok=True)
            owned(path, 0o700, directory=True)

    def special_expected_locations(self, lane):
        files = {"bot": "specs/mobile-real-server.spec.ts", "production-service-worker": "specs/mobile-real-server.spec.ts",
                 "password-recovery": "specs/cloud-accepted-auth-security.spec.ts", "registry-native-pdf": "specs/cloud-accepted-registry-export.spec.ts",
                 "today": "clock-specs/teaching-clock-geo.spec.ts", "assignment": "fixtures/assignment-lifecycle.spec.ts",
                 "application-recovery": "fixtures/application-recovery.spec.ts"}
        path = ROOT / "tests/e2e" / files.get(lane, "fixtures/semester-lifecycle.spec.ts")
        source = path.read_text().splitlines()
        found = set()
        for title in SPECIAL_LANES[lane][2]:
            matches = [index + 1 for index, line in enumerate(source) if ("test(" in line or "test.skip(" in line) and ("'" + title + "'") in line]
            require(len(matches) == 1, "SPECIAL_ORIGINAL_STORY_TITLE")
            found.add((path.name, matches[0]))
        return found

    def discover_lifecycle(self):
        """Load every original first phase, without setup/fixtures/test bodies, before any story."""
        require(self.mode == "special13" and self.special_cohort == "lifecycle", "SPECIAL_DISCOVERY_SELECTION")
        validate_playwright_list_source()
        clock = self.run / "clock/current-instant"
        owned(clock, 0o644)
        original_clock = hashlib.sha256(clock.read_bytes()).hexdigest()
        root = self.run / "discovery"
        root.mkdir(mode=0o700)
        matrix = {"schema": "RCT_LOCAL_LIFECYCLE_DISCOVERY_V1", "status": "BLOCKED", "expected": 7,
            "lanes": [{"lane": lane, "phase": special_phases(lane)[0], "status": "BLOCKED", "nativeExit": None,
                       "frameworkReceipt": "NOT_RUN"} for lane in SPECIAL_LIFECYCLE]}
        try:
            for row in matrix["lanes"]:
                lane, phase = row["lane"], row["phase"]
                directory = root / lane
                directory.mkdir(mode=0o700)
                (directory / "artifacts").mkdir(mode=0o700)
                base = self.env.copy()
                ordinary = False
                try:
                    context = self.special_context(lane, phase)
                    self.env.update(RCT_LOCAL_SPECIAL_DISCOVERY="true", RCT_LOCAL_SPECIAL_LANE=lane, RCT_LOCAL_SPECIAL_PHASE=phase,
                        RCT_REMAINING5_RUN=context["laneRun"], RCT_DIAGNOSTIC_RUN_DIR=context["laneRun"])
                    variable = SPECIAL_LANES[lane][1]
                    if variable:
                        self.env[variable] = context["laneRun"]
                    if lane == "assignment":
                        self.env["RCT_ASSIGNMENT_LIFECYCLE_PHASE"] = phase
                    if lane == "application-recovery":
                        self.env.update(RCT_APP_RECOVERY_PHASE=phase, RCT_APP_RECOVERY_PROJECT="rct-recovery-" + self.nonce)
                    if lane == "semester-metadata":
                        self.env["RCT_ADMIN_SEMESTER_UI_RUN"] = context["laneRun"]
                    if lane == "semester-archive":
                        self.env.update(RCT_APP_RECOVERY_PROJECT="rct-recovery-" + self.nonce,
                                        RCT_APP_RECOVERY_RUN=str(self.run / "lanes/application-recovery"))
                    validate_local(environment=self.env, expected_mode="special13", expected_special_lane=lane, expected_special_phase=phase)
                    require(hashlib.sha256(clock.read_bytes()).hexdigest() == original_clock, "SPECIAL_DISCOVERY_CLOCK_CHANGED")
                    self.special_health()
                    self.special_framework_directory(lane, phase)
                    try:
                        self.command("DISCOVERY_" + lane.upper().replace("-", "_"),
                            ["npx", "playwright", "test", "--list", "--config", "playwright.local-special.config.ts", "--project=chromium"], cwd=ROOT / "tests/e2e")
                        row["nativeExit"] = 0
                    except AdmissionError as error:
                        require(str(error) == "CHILD_FAILED" and type(error.returncode) is int and error.returncode == 1,
                                "SPECIAL_DISCOVERY_COMMAND_FAILURE")
                        row["nativeExit"] = error.returncode
                    ordinary = True
                    path = directory / "artifacts/framework.safe.json"
                    row["frameworkReceipt"] = "MISSING" if not path.exists() else "REJECTED"
                    report = closed_framework_result(private_json(path), self.special_value())
                    row["frameworkReceipt"] = "READ"
                    validate_discovery_receipt(report, self.special_value(), lane, row["nativeExit"])
                    row["status"] = "DISCOVERED"
                except Exception as error:
                    try:
                        self.preserve_special_error(lane, phase, error, discovery=True)
                    except BaseException:
                        emit("SPECIAL_FAILURE_DIAGNOSTIC", state="UNAVAILABLE")
                    if not ordinary:
                        raise
                finally:
                    try:
                        if row["nativeExit"] is not None:
                            try:
                                validate_local(environment=self.env, expected_mode="special13", expected_special_lane=lane, expected_special_phase=phase)
                                require(hashlib.sha256(clock.read_bytes()).hexdigest() == original_clock, "SPECIAL_DISCOVERY_CLOCK_CHANGED")
                                self.special_health()
                            except Exception as error:
                                row["status"] = "BLOCKED"
                                try:
                                    self.preserve_special_error(lane, phase, error, discovery=True)
                                except BaseException:
                                    pass
                                raise
                        if row["frameworkReceipt"] in {"MISSING", "REJECTED", "NOT_RUN"}:
                            try:
                                hints = special_log_hints(self.run / ("discovery_" + lane.replace("-", "_") + ".private.log"), self.special_value())
                                write(directory / "launch.safe.json", json.dumps(hints, sort_keys=True) + "\n")
                            except BaseException:
                                pass
                    finally:
                        self.env = base
            matrix["status"] = "DISCOVERED" if all(row["status"] == "DISCOVERED" for row in matrix["lanes"]) else "BLOCKED"
        finally:
            write(self.run / "discovery.safe.json", json.dumps(matrix, sort_keys=True) + "\n")
        return matrix["status"] == "DISCOVERED"

    def prepare_lifecycle_prerequisites(self):
        """Canonical FIRST3 dataset preparation; never an accepted logical story."""
        require(self.mode == "special13" and self.special_cohort == "lifecycle", "SPECIAL_PREPARATION_SELECTION")
        root = self.run / "prerequisites"
        for directory in (root, root / "artifacts", self.run / "framework-private",
                          self.run / "framework-private/lifecycle-prerequisites",
                          self.run / "framework-private/lifecycle-prerequisites/main"):
            directory.mkdir(mode=0o700, exist_ok=True)
            owned(directory, 0o700, directory=True)
        context = self.special_context("semester-metadata")
        base = self.env.copy()
        clock = self.run / "clock/current-instant"
        owned(clock, 0o644)
        clock_sha = hashlib.sha256(clock.read_bytes()).hexdigest()
        result = {"schema": "RCT_LOCAL_LIFECYCLE_PREPARATION_RESULT_V1", "status": "BLOCKED", "nativeExit": None,
                  "reportStatus": "MISSING", "frameworkStatus": "MISSING", "preparationStatus": "MISSING"}
        ordinary = False
        try:
            self.env.update(RCT_LOCAL_SPECIAL_PREPARATION="true", RCT_LOCAL_SPECIAL_LANE="semester-metadata",
                            RCT_LOCAL_SPECIAL_PHASE="main", RCT_REMAINING5_RUN=context["laneRun"],
                            RCT_DIAGNOSTIC_RUN_DIR=context["laneRun"], RCT_ADMIN_SEMESTER_UI_RUN=context["laneRun"])
            self.env[SPECIAL_LANES["semester-metadata"][1]] = context["laneRun"]
            validate_local(environment=self.env, expected_mode="special13", expected_special_lane="semester-metadata", expected_special_phase="main")
            self.special_health()
            try:
                self.command("LIFECYCLE_PREREQUISITES", ["npx", "playwright", "test", "--config",
                    "playwright.local-lifecycle-prerequisites.config.ts", "--project=chromium"], cwd=ROOT / "tests/e2e")
                result["nativeExit"] = 0
            except AdmissionError as error:
                require(str(error) == "CHILD_FAILED" and type(error.returncode) is int and error.returncode == 1,
                        "SPECIAL_PREPARATION_COMMAND_FAILURE")
                result["nativeExit"] = error.returncode
            ordinary = True
            value = self.special_value()
            result["frameworkStatus"] = "REJECTED" if (root / "artifacts/framework.safe.json").exists() else "MISSING"
            framework = closed_framework_result(private_json(root / "artifacts/framework.safe.json"), value)
            result["frameworkStatus"] = "READ"
            expected = {(file, line, "chromium") for file, line in prerequisite_locations()}
            observed = {(row["file"], row["line"], row["project"]) for row in framework["discovery"]["identities"]}
            require(framework["kind"] == "EXECUTION" and framework["sourceBound"] and framework["sourceRevision"] == value["source_sha"]
                    and framework["beginSeen"] and framework["runnerStatus"] == "passed" and framework["globalErrors"]["observed"] == 0
                    and not framework["globalErrors"]["truncated"]
                    and framework["privateRetention"]["status"] == "WRITTEN" and observed == expected
                    and framework["discovery"]["observed"] == framework["discovery"]["retained"] == len(expected)
                    and framework["discovery"]["rejected"] == 0 and not framework["discovery"]["truncated"], "SPECIAL_PREPARATION_FRAMEWORK")
            result["reportStatus"] = "REJECTED" if (root / "artifacts/safe-results.json").exists() else "MISSING"
            native_report = private_json(root / "artifacts/safe-results.json")
            case = special_result(native_report, result["nativeExit"],
                                  "semester-metadata", "main", prerequisite_locations())
            result["reportStatus"] = case["status"]
            require(case["status"] == "PASS" and all(type(row.get("errorCount")) is int and row["errorCount"] == 0
                    for row in native_report["results"]), "SPECIAL_PREPARATION_CASE_FAILED")
            result["preparationStatus"] = "REJECTED" if (root / "preparation.safe.json").exists() else "MISSING"
            closed_preparation_result(private_json(root / "preparation.safe.json"), value)
            result["preparationStatus"] = "READ"
            result["status"] = "PREPARED"
        except Exception as error:
            try:
                self.preserve_special_error("semester-metadata", None, error)
            except BaseException:
                pass
            if not ordinary:
                raise
        finally:
            try:
                require(hashlib.sha256(clock.read_bytes()).hexdigest() == clock_sha, "SPECIAL_PREPARATION_CLOCK_CHANGED")
                if result["nativeExit"] is not None:
                    validate_local(environment=self.env, expected_mode="special13", expected_special_lane="semester-metadata", expected_special_phase="main")
                    self.special_health()
                if result["frameworkStatus"] != "READ":
                    try:
                        write(root / "launch.safe.json", json.dumps(special_log_hints(self.run / "lifecycle_prerequisites.private.log", self.special_value()), sort_keys=True) + "\n")
                    except BaseException:
                        pass
            except BaseException:
                result["status"] = "BLOCKED"
                raise
            finally:
                self.env = base
                primary = sys.exc_info()[0]
                try:
                    write(root / "result.safe.json", json.dumps(result, sort_keys=True) + "\n")
                except BaseException:
                    if primary is None:
                        raise
        return result["status"] == "PREPARED"

    def build_special_bot(self):
        tag = "rct-local-stage17/notification-bot:" + self.nonce
        require(self.image_identity(tag) is None, "SPECIAL_BOT_TAG_COLLISION")
        value = self.special_value()
        require(tag in value["images"] and tag in self.image_targets, "SPECIAL_BOT_FROZEN_INTENT")
        self.image_admitted.add(tag)
        args = ["docker", "build", "--label", LABEL + "=" + self.nonce, "--tag", tag]
        source = ROOT / "services/notification-bot"
        if self.bundled_dockerfile_frontend:
            if self.mode == "special13" and self.special_cohort == "nonclock":
                # Only admitted public Python source receives portable image code modes.
                # Directed capabilities remain host-owned0600 readonly mounts outside /app/bot.
                dockerfile = source / "Dockerfile"
                info = dockerfile.lstat()
                require(dockerfile.resolve() == dockerfile and stat.S_ISREG(info.st_mode)
                        and not dockerfile.is_symlink() and info.st_nlink == 1 and 0 < info.st_size <= 16384,
                        "SPECIAL_BOT_DOCKERFILE_SOURCE")
                original = dockerfile.read_bytes()
                require(hashlib.sha256(original).hexdigest() == value["sources"].get("services/notification-bot/Dockerfile"),
                        "SPECIAL_BOT_DOCKERFILE_SOURCE")
                text = original.decode()
                marker = "RUN chown -R app:app /app\n"
                require(text.count(marker) == 1 and text.count("USER app\n") == 1
                        and text.count("COPY . .\n") == 1
                        and text.index("COPY . .\n") < text.index(marker) < text.index("USER app\n"),
                        "SPECIAL_BOT_DOCKERFILE_LAYOUT")
                prefix = "services/notification-bot/bot/"
                public = {name[len(prefix):]: digest for name, digest in value["sources"].items()
                          if name.startswith(prefix) and name.endswith(".py")}
                require(0 < len(public) <= 512 and "__init__.py" in public
                        and len(repr(public).encode()) <= 65536
                        and all(len(name) <= 256 and re.fullmatch(r"(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*\.py", name)
                                and isinstance(digest, str) and re.fullmatch(r"[a-f0-9]{64}", digest)
                                for name, digest in public.items()),
                        "SPECIAL_BOT_PUBLIC_SOURCE_SET")
                public = dict(sorted(public.items()))
                program = "file_hashes = " + repr(public) + "\n" + """import hashlib, re, stat
from pathlib import Path
def valid(condition):
    if not condition:
        raise RuntimeError("SPECIAL_BOT_PUBLIC_CODE_GUARD")
root = Path("/app/bot")
valid(root.resolve() == root and 0 < len(file_hashes) <= 512 and "__init__.py" in file_hashes
      and len(repr(file_hashes).encode()) <= 65536)
directories = {root}
for name, digest in file_hashes.items():
    valid(isinstance(name, str) and len(name) <= 256
          and re.fullmatch(r"(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*[.]py", name)
          and isinstance(digest, str) and re.fullmatch(r"[a-f0-9]{64}", digest))
    path = root / name
    directories.update(parent for parent in path.parents if parent == root or root in parent.parents)
for directory in sorted(directories, key=lambda path: (len(path.parts), str(path))):
    info = directory.lstat()
    valid(directory.resolve() == directory and stat.S_ISDIR(info.st_mode) and not directory.is_symlink())
files = []
total = 0
for name, digest in file_hashes.items():
    path = root / name
    info = path.lstat()
    total += info.st_size
    valid(path.resolve() == path and stat.S_ISREG(info.st_mode) and not path.is_symlink()
          and info.st_nlink == 1 and 0 <= info.st_size <= 2 * 1024**2 and total <= 16 * 1024**2)
    valid(hashlib.sha256(path.read_bytes()).hexdigest() == digest)
    files.append(path)
# Validate the full admitted set before the first permission mutation.
for directory in directories:
    directory.chmod(0o755)
for path in files:
    path.chmod(0o644)
valid(all(stat.S_IMODE(directory.lstat().st_mode) == 0o755 for directory in directories)
      and all(stat.S_IMODE(path.lstat().st_mode) == 0o644 for path in files))
"""
                instruction = "RUN " + json.dumps(["python", "-c", program]) + "\n"
                text = text.replace(marker, marker + instruction)
            else:
                text = (source / "Dockerfile").read_text()
            lines = text.splitlines(keepends=True)
            if lines[0].rstrip("\r\n") == "# syntax=docker/dockerfile:1.7":
                lines.pop(0)
            require(not re.search(r"(?im)^\s*#\s*syntax\s*=", "".join(lines)), "SPECIAL_BOT_DOCKERFILE_SYNTAX")
            self.command("BUILD_NOTIFICATION_BOT", [*args, "--file", "-", str(source)], stdin="".join(lines).encode())
        else:
            self.command("BUILD_NOTIFICATION_BOT", [*args, str(source)])
        identifier = self.image_identity(tag)
        require(identifier is not None, "SPECIAL_BOT_IMAGE_MISSING")
        self.image_ids[tag] = identifier
        write(self.run / "bot-image.json", json.dumps({"id": identifier, "tag": tag}) + "\n")

    def execute_special(self):
        require(self.operational_admitted, "EXPLICIT_OPERATIONAL_ADMISSION_REQUIRED")
        if self.special_cohort == "nonclock":
            self.build_special_bot()
            clock = self.run / "clock/current-instant"
            clock_sha = hashlib.sha256(clock.read_bytes()).hexdigest()
            for lane in SPECIAL_NONCLOCK:
                self.special_health()
                intent = self.special_intent(lane, "main", "RENDERER_OUTAGE", ["document-renderer-service"]) if lane == "registry-native-pdf" else None
                outcome = self.special_phase(lane)
                self.special_health(renderer_intent=intent)
                require(hashlib.sha256(clock.read_bytes()).hexdigest() == clock_sha, "SPECIAL_NONCLOCK_CHANGED_CLOCK")
                if intent:
                    self.special_outcome(intent, outcome["status"] == "PASS", restored=True)
                self.special_outcomes.append(outcome)
                write(self.run / "lanes" / lane / "logical.result.safe.json", json.dumps(outcome, sort_keys=True) + "\n")
        else:
            from local_e2e_lifecycle import execute_lifecycle
            try:
                discovered = self.discover_lifecycle()
                if discovered:
                    discovered = self.prepare_lifecycle_prerequisites()
            except BaseException as error:
                try:
                    self.preserve_special_error(SPECIAL_LIFECYCLE[0], None, error)
                except BaseException:
                    pass
                raise
            if discovered:
                execute_lifecycle(self)
            else:
                from local_e2e_lifecycle import lifecycle_reporting_directory
                for lane in SPECIAL_LIFECYCLE:
                    phases = special_phases(lane)
                    directory = lifecycle_reporting_directory(self, lane, phases)
                    row = {"lane": lane, "status": "NOT_RUN", "expected": 1, "passed": 0, "failed": 0, "notRun": 1,
                           "phases": [{"phase": phase, "status": "NOT_RUN"} for phase in phases]}
                    self.special_outcomes.append(row)
                    write(directory / "logical.result.safe.json", json.dumps(row, sort_keys=True) + "\n")
        require(len(self.special_outcomes) == (4 if self.special_cohort == "nonclock" else 7), "SPECIAL_LOGICAL_STORY_COUNT")
        write(self.run / "artifacts/special13.safe.json", json.dumps({"cohort": self.special_cohort, "stories": self.special_outcomes}, indent=2) + "\n")
        selected = set(self.operational_lanes) if self.special_cohort == "lifecycle" else set(SPECIAL_NONCLOCK)
        require(all(row["status"] == "PASS" for row in self.special_outcomes if row["lane"] in selected), "SPECIAL_COHORT_TEST_FAILURE")
        if self.special_cohort == "lifecycle":
            self.special_health()
            records, _ = special_records(self.special_value())
            require(("application-recovery" not in selected or any(row["kind"] == "recovery-seal" for row in records))
                    and all(any(result["kind"] == "outcome" and result["intent"] == row["index"]
                                and result["success"] is True and result["restored"] is True for result in records)
                            for row in records if row["kind"] == "intent"), "SPECIAL_RECOVERY_FINAL_QUALIFICATION")
            self.special_preserve_recovery = False

    def cleanup_special_extras(self):
        if not (self.run / "owner.json").exists():
            return
        value = self.special_value()
        topology = special_topology(value)
        if topology:
            for row in special_recovery_inventory(value, topology, complete=False):
                capture(["docker", "rm", "--force", "--volumes", row["Id"]], seconds=30)
            # No named data volumes are admitted for fresh recovery targets.
        ids = capture(["docker", "ps", "-aq", "--no-trunc", "--filter", "label=rct.local.special.bot=" + self.nonce]).splitlines()
        for identifier in ids:
            row = json.loads(capture(["docker", "inspect", identifier]))[0]
            pin = private_json(self.run / "bot-image.json")
            require(row["Name"] == "/rct-local-bot-" + self.nonce and row["Image"] == pin["id"]
                    and row["Config"]["Labels"].get(LABEL) == self.nonce and not row["Config"]["Labels"].get("com.docker.compose.project"), "SPECIAL_FOREIGN_BOT")
            capture(["docker", "rm", "--force", "--volumes", identifier], seconds=30)


    def execute_batch(self):
        """One isolated stack, serial named lanes, original cleanup after aggregate failure."""
        outcomes, blocked = [], None
        base_environment = self.env.copy()
        clock = self.run / "clock/current-instant"
        clock_sha = hashlib.sha256(clock.read_bytes()).hexdigest()
        for lane in selected_batch_lanes(self.failed_cases):
            _, flag, output_variable, count, _ = BATCH_LANES[lane]
            if self.failed_cases is not None:
                count = sum(FAILED_CASES[case][0] == lane for case in self.failed_cases)
            if blocked:
                outcomes.append({"lane": lane, "status": "BLOCKED", "expected": count,
                                 "passed": 0, "failed": 0, "notRun": count, "reason": blocked})
                continue
            directory = self.run / "lanes" / lane
            directory.mkdir(mode=0o700)
            (directory / "artifacts").mkdir(mode=0o700)
            self.env = base_environment.copy()
            self.env.update(RCT_LOCAL_UI_BATCH_LANE=lane, RCT_REMAINING5_RUN=str(directory),
                            RCT_DIAGNOSTIC_RUN_DIR=str(directory))
            if flag:
                self.env[flag] = "true"
            if output_variable:
                self.env[output_variable] = str(directory)
            if lane == "first3":
                (directory / "supervision").mkdir(mode=0o700)
                self.env["RCT_FOCUS3_OBSERVATIONS"] = str(directory / "supervision/observations.safe.json")
            code = 0
            try:
                validate_local(environment=self.env, expected_mode="ui-batch", expected_batch_lane=lane)
                try:
                    self.command("UI_BATCH_" + lane.upper().replace("-", "_"),
                                 ["npx", "playwright", "test", "--config", "playwright.local-batch.config.ts",
                                  "--project=chromium", "--max-failures=0", "--retries=0", "--workers=1"], cwd=ROOT / "tests/e2e")
                except AdmissionError as error:
                    if str(error) != "CHILD_FAILED":
                        raise
                    code = error.returncode
                report = directory / "artifacts/safe-results.json"
                owned(report, 0o600)
                require(report.stat().st_size <= 8 * 1024**2, "BATCH_REPORT_BOUND")
                outcome = batch_result(json.loads(report.read_text()), code, lane, self.failed_cases)
                value = validate_local(environment=self.env, expected_mode="ui-batch", expected_batch_lane=lane)
                # All selected36 lanes read the shared clock. Advancing lifecycle7 remains blocked.
                batch_health(value)
                require(hashlib.sha256(clock.read_bytes()).hexdigest() == clock_sha, "BATCH_UNEXPECTED_CLOCK_CHANGE")
                outcomes.append(outcome)
                write(directory / "result.safe.json", json.dumps(outcome, indent=2) + "\n")
                emit("UI_BATCH_LANE", **outcome)
            except (AdmissionError, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
                blocked = str(error) if isinstance(error, AdmissionError) else "BATCH_REPORT_OR_INFRASTRUCTURE_FAILURE"
                outcome = {"lane": lane, "status": "BLOCKED", "expected": count,
                           "passed": 0, "failed": 0, "notRun": count, "reason": blocked,
                           "child_returncode": code}
                outcomes.append(outcome)
                write(directory / "blocked.safe.json", json.dumps(outcome, indent=2) + "\n")
                emit("UI_BATCH_LANE", **outcome)
            finally:
                self.env = base_environment
        selected_count = len(self.failed_cases) if self.failed_cases is not None else 36
        summary = {"scope": "REMAINING49_FAILED_CASE_SELECTION" if self.failed_cases is not None else "REMAINING49_FIRST_PORTABLE36",
                   "expected": 49, "selected": selected_count, "selectedCaseIds": self.failed_cases, "unselected": 36 - selected_count,
                   "passed": sum(row["passed"] for row in outcomes),
                   "failed": sum(row["failed"] for row in outcomes),
                   "notRun": sum(row["notRun"] for row in outcomes),
                   "deferred": 13, "lanes": outcomes, "blockedDependencies": BATCH_BLOCKED,
                   "all49Complete": False, "result": "FAIL" if any(row["status"] != "PASS" for row in outcomes) else "PARTIAL_BLOCKED"}
        write(self.run / "artifacts/ui-batch.safe.json", json.dumps(summary, indent=2) + "\n")
        emit("UI_BATCH_RESULT", **summary)
        raise AdmissionError("UI_BATCH_REMAINING_FAILURES_OR_BLOCKED_DEPENDENCIES")

    def diagnose(self):
        """Read-only 30s follow-ups never replace the primary strict 5s result."""
        rows = inventory(self.project, self.nonce, complete=True)
        result = {"qualification": "Follow-up only; primary strict five-second result retained", "probes": {}}
        for name, path, port in (("academic-service", "/api-docs", 9091), ("api-gateway", "/openapi/academic-service", 8080)):
            emit("OPENAPI_FOLLOWUP", target=name, timeout_seconds=30, state="RUNNING")
            row = next(row for row in rows if row["Config"]["Labels"]["com.docker.compose.service"] == name)
            host = row["NetworkSettings"]["Networks"][self.project + "_private_net"]["IPAddress"]
            observed = {"status": None, "stage": "HTTP_HEADERS", "error_kind": None}
            started = time.monotonic()
            class NoRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, *args, **kwargs):
                    return None
            try:
                opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
                with opener.open("http://" + host + ":" + str(port) + path, timeout=30) as response:
                    observed["status"] = response.status
                    observed["stage"] = "HTTP_BODY"
                    raw = response.read(2 * 1024**2 + 1)
                    require(0 < len(raw) <= 2 * 1024**2, "BODY_BOUND")
                    observed["stage"] = "JSON_SYNTAX"
                    require(isinstance(json.loads(raw), dict), "JSON_SHAPE")
                    observed["stage"] = "COMPLETE"
            except urllib.error.HTTPError as error:
                observed.update(status=error.code, error_kind="HTTP_STATUS")
                error.close()
            except Exception as error:
                observed["error_kind"] = "TIMEOUT" if isinstance(error, TimeoutError) else "TRANSPORT_BODY_OR_JSON"
            observed["elapsed_ms"] = round((time.monotonic() - started) * 1000)
            result["probes"][name] = observed
        write(self.run / "artifacts/openapi-followup.safe.json", json.dumps(result, indent=2) + "\n")
        emit("OPENAPI_FOLLOWUP", **result)

    def diagnose_http_failure(self):
        """Same closed CI implementation, before existing log retention and cleanup."""
        stage = {"client": "HOMEWORK_CREATE503", "lesson-lifecycle": "STUDENT_STATS503"}.get(self.mode)
        if stage is None:
            return
        args = [sys.executable, "-B", str(ROOT / "tests/e2e/fixtures/http500_diagnostic.py"),
                "--context", "local", "--stage", stage, "--root", str(ROOT), "--run-dir", str(self.run)]
        cancelled = False
        def cancel(*_):
            nonlocal cancelled
            cancelled = True
        previous = {kind: signal.signal(kind, cancel) for kind in (signal.SIGTERM, signal.SIGINT)}
        child = None
        try:
            with (self.run / "http500-diagnostic.safe.log").open("xb") as log:
                # Install non-raising handlers before spawn: a signal cannot orphan a new session.
                child = subprocess.Popen(args, cwd=ROOT, env=self.env, stdout=log, stderr=subprocess.STDOUT,
                                         stdin=subprocess.DEVNULL, start_new_session=True)
                deadline = time.monotonic() + 30
                while child.poll() is None:
                    if cancelled:
                        raise KeyboardInterrupt()
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise subprocess.TimeoutExpired(args, 30)
                    try:
                        child.wait(timeout=min(0.2, remaining))
                    except subprocess.TimeoutExpired:
                        pass
                emit("HTTP_DIAGNOSTIC", stage=stage, child_returncode=child.returncode)
        finally:
            try:
                if child is not None and child.poll() is None:
                    os.killpg(child.pid, signal.SIGTERM)
                    try:
                        child.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        child.wait(timeout=5)
            finally:
                for kind, handler in previous.items():
                    signal.signal(kind, handler)

    def image_identity(self, tag):
        if not capture(["docker", "image", "ls", "--quiet", "--no-trunc", tag]).strip():
            return None
        value = capture(["docker", "image", "inspect", "--format",
                         '{{.Id}} {{index .Config.Labels "' + LABEL + '"}}', tag]).strip().split()
        require(len(value) == 2 and re.fullmatch(r"sha256:[0-9a-f]{64}", value[0])
                and value[1] == self.nonce, "FOREIGN_IMAGE_TAG")
        return value[0]

    def preserve_logs(self):
        """Nonce-owned container output stays private, bounded, and precedes removal."""
        rows = inventory(self.project, self.nonce)
        deadline = time.monotonic() + 120
        receipt = []
        for row in rows:
            service = row["Config"]["Labels"]["com.docker.compose.service"]
            result = retain_container_log(row["Id"], self.run / ("container-" + service + ".private.log"), deadline=deadline)
            receipt.append({"service": service, **result})
        write(self.run / "container-logs.safe.json", json.dumps(receipt, indent=2) + "\n")
        return all(item["complete"] for item in receipt)

    def cleanup(self):
        # Never use broad down/prune. Fresh ownership checks precede every removal.
        if self.mode == "special13":
            require(not getattr(self, "special_preserve_recovery", False), "SPECIAL_RECOVERY_RETAINED_FOR_OWNER")
            if (self.run / "owner.json").exists():
                records, _ = special_records(self.special_value())
                require(all(any(result["kind"] == "outcome" and result["intent"] == intent["index"] and result["restored"] is True for result in records)
                            for intent in records if intent["kind"] == "intent"), "SPECIAL_MUTATION_UNRESTORED_RETAINED_FOR_OWNER")
            if (self.run / "runtime-identity.json").exists():
                special_inventory(self.special_value(), complete=False)
            self.cleanup_special_extras()
        rows = inventory(self.project, self.nonce)
        for row in rows:
            capture(["docker", "rm", "--force", "--volumes", row["Id"]], seconds=30)
        # A cancelled pipeline normally removes its own Prometheus; recover only its exact nonce label.
        scrapers = capture(["docker", "ps", "-aq", "--filter", "label=" + LABEL + "=" + self.nonce]).splitlines()
        for identifier in scrapers:
            value = json.loads(capture(["docker", "inspect", identifier]))[0]
            owner = value["Config"].get("Labels", {}).get("rct.acceptance.owner", "")
            require(re.fullmatch(r"rct-e2e-scrape-[0-9a-f]{12}", owner)
                    and value["Name"] == "/" + owner and value["Config"]["Labels"].get(LABEL) == self.nonce,
                    "FOREIGN_SCRAPER")
            capture(["docker", "rm", "--force", "--volumes", identifier], seconds=30)
        for resource in ("network", "volume"):
            ids = capture(["docker", resource, "ls", "-q", "--filter", "label=com.docker.compose.project=" + self.project]).splitlines()
            for identifier in ids:
                value = json.loads(capture(["docker", resource, "inspect", identifier]))[0]
                require(value.get("Labels", {}).get(LABEL) == self.nonce, "FOREIGN_RESOURCE")
                capture(["docker", resource, "rm", identifier], seconds=30)
        require(not inventory(self.project, self.nonce), "CLEANUP_REMAINS")
        image_receipt = []
        for tag in getattr(self, "image_targets", []):
            if tag not in self.image_admitted:
                image_receipt.append({"tag": tag, "id": None, "cleanup": "NOT_ADMITTED_UNTOUCHED"})
                continue
            identifier = self.image_identity(tag)
            if identifier is not None:
                recorded = self.image_ids.get(tag)
                require(recorded is None or recorded == identifier, "IMAGE_ID_CHANGED")
                capture(["docker", "image", "rm", tag], seconds=30)
                require(self.image_identity(tag) is None, "IMAGE_TAG_REMAINS")
            image_receipt.append({"tag": tag, "id": identifier, "cleanup": "PASS_REMOVED" if identifier else "PASS_ABSENT"})
        if hasattr(self, "run") and self.run.is_dir():
            write(self.run / "images.safe.json", json.dumps(image_receipt, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("preflight", *sorted(MODES), "admit"), nargs="?", default="openapi")
    parser.add_argument("--expected-mode", choices=sorted(MODES))
    parser.add_argument("--expected-batch-lane", choices=list(BATCH_LANES))
    parser.add_argument("--expected-special-lane", choices=list(SPECIAL_LANES))
    parser.add_argument("--expected-special-phase", choices=["main", "prepare", "pending", "resume", "seed", "admit", "attachment"])
    parser.add_argument("--special-service", choices=sorted(SERVICES))
    parser.add_argument("--operational", action="store_true", help="Explicit admission to disposable outage/clock/backup tests; ordinary regression: scripts/lifecycle_regression.py")
    parser.add_argument("--operational-until", choices=SPECIAL_LIFECYCLE, help="Lifecycle-only: execute the explicitly printed original dependency prefix; later stories remain NOT_RUN")
    parser.add_argument("--lifecycle-only", action="store_true", help="Special13 only: discover then run only the original lifecycle7")
    parser.add_argument("--retained-cohort-receipt", type=Path, help="Special13 only: explicit private closure of the preserved BOOT cohort")
    parser.add_argument("--retained-pdf-cohort-receipt", type=Path, help="Special13 only: exact preserved ee1df PDF cohort closure")
    parser.add_argument("--retained-recovery-cohort-receipt", type=Path, help="Special13 only: exact preserved 43589 recovery cohort closure")
    parser.add_argument("--retained-restored-cohort-receipt", type=Path, help="Special13 only: exact stopped original17 plus restored3 closure")
    parser.add_argument("--diagnose-startup", action="store_true", help="Client only: same ten scenarios, stop after first failure with closed setup diagnostics")
    parser.add_argument("--bundled-dockerfile-frontend", action="store_true",
                        help="UI batch only: inline tracked Dockerfiles using the Docker Engine bundled frontend")
    parser.add_argument("--failed-case", action="append", choices=list(FAILED_CASES),
                        help="UI batch only: select a confirmed failed case ID; repeat for multiple cases")
    args = parser.parse_args()
    selected = operational_selection(args.operational, args.operational_until, mode=args.mode, lifecycle_only=args.lifecycle_only)
    if args.mode == "special13":
        os.environ["DOCKER_HOST"] = local_docker_binding()
        emit("OPERATIONAL_SELECTION", selected=list(selected), remaining=[name for name in SPECIAL_LIFECYCLE if name not in selected], fullLifecycleSelection=args.operational_until is None)
    selected_cohorts(args.mode, args.lifecycle_only)
    require(args.retained_cohort_receipt is None or args.mode == "special13", "RETAINED_RECEIPT_SPECIAL_ONLY")
    require(args.retained_recovery_cohort_receipt is None or args.mode == "special13" and args.retained_pdf_cohort_receipt is not None, "RETAINED_RECOVERY_RECEIPT_SPECIAL_ONLY")
    require(args.retained_restored_cohort_receipt is None or args.mode == "special13" and args.lifecycle_only and args.retained_recovery_cohort_receipt is not None, "RETAINED_RESTORED_RECEIPT_LIFECYCLE_ONLY")
    require(args.retained_pdf_cohort_receipt is None or args.mode == "special13" and args.retained_cohort_receipt is not None, "RETAINED_PDF_RECEIPT_SPECIAL_ONLY")
    require(args.failed_case is None or args.mode == "ui-batch", "BATCH_CASES_UI_BATCH_ONLY")
    selected_batch_cases(args.failed_case)
    require(not args.diagnose_startup or args.mode == "client", "CLIENT_DIAGNOSTIC_ONLY")
    require(not args.bundled_dockerfile_frontend or args.mode in {"ui-batch", "special13"}, "BUNDLED_FRONTEND_UI_BATCH_ONLY")
    if args.mode == "admit":
        require(args.expected_batch_lane is None or args.expected_mode == "ui-batch", "LOCAL_BATCH_MODE_REQUIRED")
        require(args.expected_mode != "ui-batch" or args.expected_batch_lane in BATCH_LANES, "LOCAL_BATCH_LANE_REQUIRED")
        value = validate_local(expected_mode=args.expected_mode, expected_batch_lane=args.expected_batch_lane,
                               expected_special_lane=args.expected_special_lane, expected_special_phase=args.expected_special_phase)
        if args.expected_mode == "special13":
            require(args.expected_special_lane in SPECIAL_LANES and args.expected_special_phase in special_phases(args.expected_special_lane), "SPECIAL_LANE_REQUIRED")
            if args.special_service:
                emit("LOCAL_SPECIAL_SERVICE", service=special_service(value, args.special_service))
            else:
                emit("LOCAL_SPECIAL_ADMITTED", **special_context(value, args.expected_special_lane, args.expected_special_phase))
            return
        if args.expected_mode is None:
            emit("LOCAL_STAGE17_ADMITTED")
        elif args.expected_mode == "ui-batch":
            emit("LOCAL_STAGE17_ADMITTED", mode=value["mode"], project=value["project"], nonce=value["nonce"], batchLane=args.expected_batch_lane)
        else:
            emit("LOCAL_STAGE17_ADMITTED", mode=value["mode"], project=value["project"], nonce=value["nonce"])
        return
    require(args.expected_mode is None and args.expected_batch_lane is None and args.expected_special_lane is None
            and args.expected_special_phase is None and args.special_service is None, "EXPECTED_MODE_ADMIT_ONLY")
    preflight(special=args.mode == "special13", retained_receipt=args.retained_cohort_receipt, retained_pdf_receipt=args.retained_pdf_cohort_receipt, retained_recovery_receipt=args.retained_recovery_cohort_receipt, retained_restored_receipt=args.retained_restored_cohort_receipt)
    if args.mode == "preflight":
        emit("PREFLIGHT", result="PASS", containers_started=0)
        return
    os.umask(0o077)
    RUNS.mkdir(parents=True, exist_ok=True, mode=0o700)
    owned(RUNS, 0o700, directory=True)
    descriptor = os.open(RUNS / "port-8443.lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "r+") as lock:
        owned(RUNS / "port-8443.lock", 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        preflight(special=args.mode == "special13", retained_receipt=args.retained_cohort_receipt, retained_pdf_receipt=args.retained_pdf_cohort_receipt, retained_recovery_receipt=args.retained_recovery_cohort_receipt, retained_restored_receipt=args.retained_restored_cohort_receipt)  # Shared fixed-port lease held.
        cohorts = selected_cohorts(args.mode, args.lifecycle_only)
        results = []
        for cohort in cohorts:
            results.append(run_cohort(args, cohort))
        if args.mode == "special13":
            rows = [row for result in results for row in result["stories"]]
            expected = 7 if args.lifecycle_only else 13
            require(len(rows) == (7 if args.lifecycle_only else 11) and sum(row["expected"] for row in rows) == expected, "SPECIAL_SUMMARY_COUNT")
            emit("SPECIAL13_RESULT", expected=expected, passed=sum(row["passed"] for row in rows),
                 failed=sum(row["failed"] for row in rows), notRun=sum(row["notRun"] for row in rows),
                 cohorts=results, actualRuntime="OWNER_LOCAL_ONLY", selectedLifecycle=list(selected),
                 fullLifecycleAcceptance=all(row["status"] == "PASS" for row in rows if row["lane"] in SPECIAL_LIFECYCLE))
            require(all(result["result"] == "PASS" for result in results), "SPECIAL13_INCOMPLETE")


def run_cohort(args, cohort):
        runner = Runner(args.mode, special_cohort=cohort, startup_diagnostic=args.diagnose_startup,
                        bundled_dockerfile_frontend=args.bundled_dockerfile_frontend, failed_cases=args.failed_case, lifecycle_only=args.lifecycle_only,
                        operational=args.operational, operational_until=args.operational_until)
        runner.special_retained_receipt = args.retained_cohort_receipt
        runner.special_retained_pdf_receipt = args.retained_pdf_cohort_receipt
        runner.special_retained_recovery_receipt = args.retained_recovery_cohort_receipt
        runner.special_retained_restored_receipt = args.retained_restored_cohort_receipt
        result, cleanup = "FAIL", "NOT_STARTED"
        try:
            if args.mode == "special13":
                runner.phase = "SUDO_AUTHENTICATION"
                emit(runner.phase, state="RUNNING")
                # Foreground controlling TTY only; sudo handles its own prompt and timestamp.
                subprocess.run(["sudo", "-v"], cwd=ROOT, env=runner.env, stdout=subprocess.DEVNULL, check=True)
            runner.prepare()
            if args.mode != "prepare":
                runner.execute()
            result = "PASS"
        except AdmissionError as error:
            if args.mode != "special13" or str(error) != "SPECIAL_COHORT_TEST_FAILURE":
                raise
        finally:
            primary = sys.exc_info()[1]
            cleanup_error, logs_error, logs_complete = None, None, True
            if runner.run.exists():
                if args.mode == "special13" and (primary is not None or result != "PASS") and (runner.run / "owner.json").exists():
                    try:
                        runner.preserve_special_health()
                    except BaseException:
                        emit("SPECIAL_HEALTH_DIAGNOSTIC", state="UNAVAILABLE")
                if primary is not None:
                    try:
                        runner.diagnose_http_failure()
                    except BaseException:
                        # This follow-up cannot replace the original failure or skip cleanup.
                        emit("HTTP_DIAGNOSTIC", state="UNAVAILABLE")
                try:
                    logs_complete = runner.preserve_logs() is not False
                except BaseException as error:
                    logs_error = error
                try:
                    runner.cleanup()
                    cleanup = "PASS"
                except BaseException as error:
                    cleanup = "FAIL_RETAINED_FOR_OWNER"
                    cleanup_error = error
                failure = primary or logs_error or cleanup_error
                error_kind = ("INTERRUPTED" if isinstance(failure, KeyboardInterrupt) else
                              type(failure).__name__ if type(failure).__name__ in {
                                  "AdmissionError", "CalledProcessError", "TimeoutExpired", "OSError",
                                  "ValueError", "KeyError", "AssertionError", "RuntimeError", "TypeError"} else
                              "OTHER" if failure is not None else None)
                code = getattr(failure, "returncode", None)
                receipt = {"result": "FAIL" if failure else result, "phase": runner.phase, "cleanup": cleanup, "mode": args.mode,
                           "startup_diagnostic": args.diagnose_startup,
                           "container_logs": "FAIL" if logs_error else "PASS" if logs_complete else "PARTIAL",
                           "run": str(runner.run), "error_kind": error_kind,
                           "exit_code": 130 if isinstance(failure, KeyboardInterrupt) else 1 if failure or result != "PASS" else 0,
                           "child_returncode": code if type(code) is int and -128 <= code <= 255 else None,
                           "containers_started": 0 if args.mode == "prepare" else "SEE_PRIVATE_LOGS"}
                try:
                    write(runner.run / "result.safe.json", json.dumps(receipt, indent=2) + "\n")
                    # Console event identity and the failed operation are distinct fields.
                    event = {key: value for key, value in receipt.items() if key != "phase"}
                    emit("LOCAL_RUN_COMPLETE", stage=receipt["phase"], **event)
                except BaseException:
                    if failure is None:
                        raise
            if primary is None and (logs_error or cleanup_error) is not None:
                raise logs_error or cleanup_error
        return {"cohort": cohort, "result": result, "run": str(runner.run), "stories": runner.special_outcomes}


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        main()
    except BaseException as error:
        # No exception strings, argv, response bodies or secret-bearing subprocess output.
        emit("LOCAL_RUN_FAILED", error_kind="INTERRUPTED" if isinstance(error, KeyboardInterrupt) else type(error).__name__,
             admission_code=str(error) if isinstance(error, AdmissionError) else None)
        sys.exit(130 if isinstance(error, KeyboardInterrupt) else 1)
