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
MODES = {"prepare", "openapi", "full", "client", "lesson-lifecycle"}
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


def inventory(project, nonce, *, complete=False):
    ids = capture(["docker", "ps", "-aq", "--no-trunc", "--filter",
                   "label=com.docker.compose.project=" + project]).splitlines()
    rows = json.loads(capture(["docker", "inspect", *ids])) if ids else []
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


def validate_local(*, complete=True, environment=None, expected_mode=None):
    """Called before pipeline startup and again immediately before the seed write."""
    environment = os.environ if environment is None else environment
    require(sys.platform == "linux" and not environment.get("GITHUB_ACTIONS")
            and not environment.get("RUNNER_ENVIRONMENT"), "LOCAL_NOT_HOSTED")
    value = manifest(Path(environment.get("RCT_LOCAL_STAGE17_MANIFEST", "")))
    validate_lane_environment(value, environment, expected_mode)
    for name, expected in value["sources"].items():
        require(hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected, "SOURCE_BYTES_CHANGED")
    run = Path(value["run"])
    validate_node_ca(run / "certs/server.crt", environment, value["node_ca_sha256"])
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


def validate_lane_environment(value, environment, expected_mode=None):
    """Mode and environment are a single manifest-bound admission decision."""
    mode = value["mode"]
    require(mode in MODES, "MANIFEST_MODE")
    require(environment.get("RCT_LOCAL_STAGE17_MODE") == mode
            and (expected_mode is None or mode == expected_mode), "LOCAL_LANE_MISMATCH")
    diagnostic = value.get("startup_diagnostic", False)
    require(type(diagnostic) is bool and (not diagnostic or mode == "client")
            and environment.get("RCT_LOCAL_CLIENT_STARTUP_DIAGNOSTIC", "false") == ("true" if diagnostic else "false"),
            "LOCAL_DIAGNOSTIC_MISMATCH")
    require(environment.get("RCT_LOCAL_STAGE17_NONCE") == value["nonce"]
            and environment.get("RCT_REMAINING5_RUN") == value["run"]
            and environment.get("RCT_E2E_TEACHING_CLOCK_DIR") == value["run"] + "/clock"
            and environment.get("E2E_BASE_URL") == "https://localhost:8443"
            and all(environment.get(name) == "true" for name in
                    ("RCT_E2E_REMAINING5", "RCT_E2E_SYNTHETIC_SEED"))
            and environment.get("RCT_E2E_ASYNC_REPORT") == ("false" if mode in {"client", "lesson-lifecycle"} else "true")
            and environment.get("RCT_LESSON_LIFECYCLE_RUN", "") == (value["run"] if mode == "lesson-lifecycle" else ""),
            "LOCAL_STAGE17_ONLY")
    for flag in ("RCT_E2E_ADMIN_LIFECYCLE", "RCT_E2E_ROLE_LOGOUT", "RCT_E2E_WEBKIT_SUBSET",
                 "RCT_E2E_SW_LIFECYCLE", "RCT_E2E_BOT_ROLE_MENU", "RCT_APP_RECOVERY_PROJECT"):
        require(not environment.get(flag) if flag == "RCT_APP_RECOVERY_PROJECT" else environment.get(flag) != "true", "LOCAL_STAGE17_ONLY")


def preflight():
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
    require(not capture(["docker", "ps", "-aq", "--filter", "label=" + LABEL]).strip(), "PREVIOUS_LOCAL_COHORT")
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
    def __init__(self, mode, *, startup_diagnostic=False):
        require(not startup_diagnostic or mode == "client", "CLIENT_DIAGNOSTIC_ONLY")
        self.startup_diagnostic = startup_diagnostic
        self.mode, self.phase, self.child = mode, "PREPARE", None
        self.nonce = secrets.token_hex(12)
        self.project = "rct-local-stage17-" + self.nonce[:12]
        self.run = RUNS / self.project
        self.image_targets, self.image_ids, self.image_admitted = [], {}, set()
        allowed = {"PATH", "USER", "LOGNAME", "LANG", "LC_ALL", "TZ", "SHELL", "JAVA_HOME",
                   "VIRTUAL_ENV", "SSL_CERT_FILE", "SSL_CERT_DIR", "REQUESTS_CA_BUNDLE"}
        self.env = {key: value for key, value in os.environ.items() if key in allowed}
        # Explicit synthetic file is the only source of Compose credentials.
        self.env.update(RCT_E2E_SYNTHETIC_SEED="true", RCT_E2E_ASYNC_REPORT="false" if mode in {"client", "lesson-lifecycle"} else "true", RCT_E2E_REMAINING5="true",
                        RCT_E2E_SYNTHETIC_BASELINE=BASELINE, E2E_BASE_URL="https://localhost:8443",
                        RCT_E2E_TEACHING_CLOCK_DIR=str(self.run / "clock"), RCT_REMAINING5_RUN=str(self.run),
                        RCT_LOCAL_STAGE17_MANIFEST=str(self.run / "owner.json"), RCT_LOCAL_STAGE17_NONCE=self.nonce,
                        RCT_LOCAL_STAGE17_MODE=mode,
                        RCT_DIAGNOSTIC_CONTEXT="local", RCT_DIAGNOSTIC_RUN_DIR=str(self.run),
                        RCT_DIAGNOSTIC_CHECKOUT_ROOT=str(ROOT),
                        RCT_LOCAL_CLIENT_STARTUP_DIAGNOSTIC="true" if startup_diagnostic else "false",
                        PLAYWRIGHT_NO_COPY_PROMPT="1", NODE_OPTIONS="--max-old-space-size=512",
                        COMPOSE_PARALLEL_LIMIT="1", BUILDKIT_MAX_PARALLELISM="1")
        if mode == "lesson-lifecycle":
            self.env["RCT_LESSON_LIFECYCLE_RUN"] = str(self.run)

    def command(self, phase, args, *, cwd=ROOT, stdin=None):
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

    def prepare(self):
        self.run.mkdir(mode=0o700)
        for name in ("artifacts", "home", "tmp"):
            (self.run / name).mkdir(mode=0o700)
        self.env.update(HOME=str(self.run / "home"), TMPDIR=str(self.run / "tmp"),
                        GRADLE_USER_HOME=os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")),
                        NPM_CONFIG_CACHE=os.environ.get("NPM_CONFIG_CACHE", str(Path.home() / ".npm")),
                        PLAYWRIGHT_BROWSERS_PATH=os.environ.get("PLAYWRIGHT_BROWSERS_PATH", str(Path.home() / ".cache/ms-playwright")))
        # Preserve exact tracked .env.ci bytes except three fresh directed tokens.
        source = capture(["git", "show", "HEAD:tests/e2e/.env.ci"])
        for name in sorted(TOKEN_NAMES):
            token = secrets.token_urlsafe(32)
            source, count = re.subn(r"(?m)^" + name + r"=.*$", name + "=" + token, source)
            require(count <= 1, "DUPLICATE_SYNTHETIC_TOKEN")
            if not count:
                source += "\n" + name + "=" + token + "\n"
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
        write(self.run / "compose.json", json.dumps(config, indent=2) + "\n")
        tracked = capture(["git", "ls-files", "-z"]).split("\0")
        # Include untracked authored harness files; omit evidence and runtime files.
        paths = {name for name in tracked if name and (ROOT / name).is_file()
                 and (name.startswith(("services/", "frontends/", "proto/", "tests/e2e/", "infra/", "scripts/", "gradle/"))
                      or name in {".dockerignore", "docker-compose.e2e.yml", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew"})
                 and not (".env" in Path(name).name and name != "tests/e2e/.env.ci")}
        paths |= {"scripts/local_e2e_async.py", "tests/e2e/fixtures/local-stage17.ts"}
        hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in sorted(paths)}
        value = {"schema": 1, "uid": os.getuid(), "root": str(ROOT), "run": str(self.run), "nonce": self.nonce, "mode": self.mode,
                 "project": self.project, "source_sha": sha, "sources": hashes, "startup_diagnostic": self.startup_diagnostic,
                 "node_ca_sha256": hashlib.sha256((self.run / "certs/server.crt").read_bytes()).hexdigest(),
                 "images": sorted(self.image_targets),
                 "compose_sha256": hashlib.sha256((self.run / "compose.json").read_bytes()).hexdigest(),
                 "dirty": bool(capture(["git", "status", "--porcelain", "--untracked-files=normal"]).strip()),
                 "diff_sha256": hashlib.sha256(subprocess.check_output(["git", "diff", "HEAD", "--binary"], cwd=ROOT)).hexdigest(),
                 "qualification": "Fresh isolated local lane; does not reproduce preceding CI steps. Source bytes recorded; HEAD identity does not assert a clean checkout"}
        write(self.run / "owner.json", json.dumps(value, indent=2) + "\n")
        self.env["NODE_EXTRA_CA_CERTS"] = str(self.run / "certs/server.crt")
        self.trust()
        # Reuse the stand's pinned install. Install only when absent or version-mismatched.
        lock = json.loads((ROOT / "tests/e2e/package-lock.json").read_text())
        package = ROOT / "tests/e2e/node_modules/@playwright/test/package.json"
        if not package.is_file() or json.loads(package.read_text())["version"] != lock["packages"]["node_modules/@playwright/test"]["version"]:
            self.command("NPM_CI", ["npm", "ci", "--ignore-scripts"], cwd=ROOT / "tests/e2e")
        emit("PREPARED", mode=self.mode, run=str(self.run), source_sha=sha, dirty=value["dirty"],
             startup_diagnostic=self.startup_diagnostic, qualification="FRESH_ISOLATED_LANE")

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
        self.command("BOOT", self.compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "480"))
        validate_local(environment=self.env)
        node_tls_probe(self.run / "certs/server.crt", self.env)
        self.command("NOTIFICATION_KEY_ISOLATION", self.compose("exec", "-T", "notification-web", "sh", "-c", "test ! -e /keys/private.key && test ! -L /keys/private.key"))
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
    parser.add_argument("--diagnose-startup", action="store_true", help="Client only: same ten scenarios, stop after first failure with closed setup diagnostics")
    args = parser.parse_args()
    require(not args.diagnose_startup or args.mode == "client", "CLIENT_DIAGNOSTIC_ONLY")
    if args.mode == "admit":
        value = validate_local(expected_mode=args.expected_mode)
        if args.expected_mode is None:
            emit("LOCAL_STAGE17_ADMITTED")
        else:
            emit("LOCAL_STAGE17_ADMITTED", mode=value["mode"], project=value["project"], nonce=value["nonce"])
        return
    require(args.expected_mode is None, "EXPECTED_MODE_ADMIT_ONLY")
    preflight()
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
        preflight()  # Race guard after owning the shared fixed-port lease.
        runner = Runner(args.mode, startup_diagnostic=args.diagnose_startup)
        result, cleanup = "FAIL", "NOT_STARTED"
        try:
            runner.prepare()
            if args.mode != "prepare":
                runner.execute()
            result = "PASS"
        finally:
            primary = sys.exc_info()[1]
            cleanup_error, logs_error, logs_complete = None, None, True
            if runner.run.exists():
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
                           "exit_code": 130 if isinstance(failure, KeyboardInterrupt) else 1 if failure else 0,
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


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        main()
    except BaseException as error:
        # No exception strings, argv, response bodies or secret-bearing subprocess output.
        emit("LOCAL_RUN_FAILED", error_kind="INTERRUPTED" if isinstance(error, KeyboardInterrupt) else type(error).__name__,
             admission_code=str(error) if isinstance(error, AdmissionError) else None)
        sys.exit(130 if isinstance(error, KeyboardInterrupt) else 1)
