#!/usr/bin/env python3
"""Bounded isolated CI acceptance. stdout is one safe receipt, never subprocess logs."""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import yaml

ROOT = Path(__file__).resolve().parents[2]
MIB = 1024**2
GIB = 1024**3
SERVICES = {"glitchtip-db", "glitchtip", "pyroscope", "homepage", "observer-proxy"}
MARKER = "synthetic-private@example.invalid"
CURRENT_STAGE = "source"
DIAGNOSTIC_REASONS = {"INVALID_MOUNT", "PERMISSION_DENIED", "UNHEALTHY_DEPENDENCY",
                      "OUT_OF_MEMORY", "READ_ONLY_FS", "NGINX_CONFIG", "PYRO_CONFIG", "DATABASE"}


def closed_reason(text, service=None):
    patterns = [
        ("INVALID_MOUNT", r"invalid mount (?:path|config)|mount path must be absolute"),
        ("READ_ONLY_FS", r"read-only file system|\berrno 30\b"),
        ("PERMISSION_DENIED", r"permission denied|operation not permitted"),
        ("UNHEALTHY_DEPENDENCY", r"dependency failed to start|container .+ is unhealthy"),
        ("OUT_OF_MEMORY", r"java\.lang\.outofmemoryerror|cannot allocate memory"),
    ]
    if service == "observer-proxy":
        patterns.append(("NGINX_CONFIG", r"nginx: \[(?:emerg|alert)\]|nginx: configuration file .+ test failed"))
    elif service == "pyroscope":
        patterns.append(("PYRO_CONFIG", r"yaml: unmarshal errors|field .+ not found in type|(?:failed|error).*(?:parsing|loading) config"))
    elif service in {"glitchtip", "glitchtip-db"}:
        patterns.append(("DATABASE", r"django\.db\.(?:utils\.)?(?:operationalerror|programmingerror|integrityerror)|psycopg[23]?\.operationalerror|fatal:.*database"))
    return next((label for label, pattern in patterns if re.search(pattern, text.lower())), "UNKNOWN")


def safe_failure(error):
    result = {"failure_class": type(error).__name__}
    if isinstance(error, subprocess.CalledProcessError):
        kind = error.cmd if isinstance(error.cmd, str) and error.cmd in {"docker", "java", "node", "sudo", "openssl"} else "other"
        reason = error.output if isinstance(error.output, str) and error.output in DIAGNOSTIC_REASONS else "UNKNOWN"
        result.update({"command_kind": kind, "exit_code": error.returncode if type(error.returncode) is int else None, "reason": reason})
    return result


def stage(name):
    global CURRENT_STAGE
    CURRENT_STAGE = name
    print(json.dumps({"stage": name, "state": "started"}), file=sys.stderr, flush=True)


def require(condition, reason):
    if not condition:
        raise AssertionError(reason)


def source_check():
    source = yaml.safe_load((ROOT / "docker-compose.observability-apps.yml").read_text())
    require(set(source["services"]) == SERVICES, "Unexpected telemetry dependencies")
    for name, service in source["services"].items():
        require("@sha256:" in service["image"], "An image is not immutable")
        require("docker.sock" not in json.dumps(service), "Docker control socket is forbidden")
        if name != "observer-proxy":
            require(not service.get("ports"), "Private application port published")
    require(source["services"]["observer-proxy"]["ports"][0].startswith("127.0.0.1:"), "Operator ingress must be loopback only")
    home = source["services"]["homepage"]
    require(home["user"] == "1000:1000" and home["read_only"], "Homepage machine privileges changed")
    require(home["cap_drop"] == ["ALL"] and not home.get("privileged"), "Homepage capabilities changed")
    require(home.get("tmpfs") == ["/app/.next/cache:uid=1000,gid=1000,size=16m",
                                  "/tmp:uid=1000,gid=1000,size=8m"], "Homepage bounded writable mounts differ")
    ingress = (ROOT / "nginx/conf.d/default.conf").read_text()
    marker = "# RCT_BROWSER_ERROR_INGEST_"
    public = ingress.split(marker + "BEGIN", 1)[1].split(marker + "END", 1)[0]
    private = (ROOT / "infra/observability-apps/nginx.conf").read_text()
    require(public == private.split(marker + "BEGIN", 1)[1].split(marker + "END", 1)[0], "CI and business ingest routes differ")
    memory_mib = sum(int(service["mem_limit"].removesuffix("m")) for service in source["services"].values())
    cpu_cores = sum(float(service["cpus"]) for service in source["services"].values())
    require(memory_mib <= 1536 and cpu_cores <= 2.5, "Sidecar effective limits exceed the accepted source budget")
    return {"result": "PASS", "images": {n: s["image"] for n, s in source["services"].items()},
            "memory_limit_mib": memory_mib, "cpu_limit_cores": cpu_cores, "disk_floor_bytes": 4 * GIB}


def run(command, env=None, timeout=120, check=True):
    result = subprocess.run(command, cwd=ROOT, env=env, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout)
    if check and result.returncode:
        # No stdout, stderr or argument echo: an SDK may include synthetic credentials.
        kind = Path(command[0]).name
        if kind not in {"docker", "java", "node", "sudo", "openssl"}:
            kind = "other"
        reason = closed_reason(result.stderr)
        # CalledProcessError carries only closed fields, never arguments or raw output.
        raise subprocess.CalledProcessError(result.returncode, kind, output=reason)
    return result


def wait_for(check, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (AssertionError, ValueError, RuntimeError, OSError, urllib.error.URLError):
            pass
        time.sleep(0.5)
    raise TimeoutError("Isolated acceptance condition timed out")


def request(url, body=None, auth=False, method=None, headers=None):
    values = dict(headers or {})
    if auth:
        values["Authorization"] = "Basic " + base64.b64encode(b"operator:synthetic-operator-only").decode()
    if isinstance(body, dict):
        body = json.dumps(body).encode()
        values["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=values, method=method)
    # Loopback traffic must never use the inherited outbound HTTP proxy.
    try:
        response = urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=5)
        return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()



def validate_resource_labels(kind, labels, project, revision, run_identity, allowed):
    """Reject foreign resources before any cleanup mutation; no secret-bearing inspect dump."""
    require(isinstance(labels, dict), "Resource identity labels missing")
    require(labels.get("com.docker.compose.project") == project, "Foreign Compose project")
    require(labels.get("ru.rutcampustrack.apps.source") == revision, "Foreign source revision")
    require(labels.get("ru.rutcampustrack.apps.run") == run_identity, "Foreign CI run")
    require(labels.get("ru.rutcampustrack.apps.scope") == "isolated-observability-apps", "Foreign runtime scope")
    field = {"containers": "service", "networks": "network", "volumes": "volume"}[kind]
    logical_name = labels.get("com.docker.compose." + field)
    require(logical_name in allowed, "Unexpected resource outside finite app allowlist")
    return logical_name


def main():
    static = source_check()
    if "--source" in sys.argv:
        print(json.dumps(static))
        return
    require(os.environ.get("RCT_OBSERVABILITY_APPS_RUNTIME") == "isolated-ci" and os.environ.get("GITHUB_ACTIONS") == "true", "Native acceptance requires its isolated CI lease")
    require(shutil.disk_usage("/tmp").free >= 6 * GIB, "6GiB startup floor; preserve 4GiB during runtime")
    revision = os.environ["GITHUB_SHA"]
    require(re.fullmatch("[a-f0-9]{40}", revision), "Exact source revision required")
    project = "rct-apps-" + os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"]
    require(re.fullmatch(r"rct-apps-[0-9]+-[0-9]+", project), "Invalid disposable ownership")
    classpath = (ROOT / "services/attendance-service/attendance-app/build/observability-probe-classpath.txt").read_text().strip()
    output = ROOT / "build/monitoring"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=project + "-", dir=os.environ.get("RUNNER_TEMP", "/tmp")) as directory:
        work = Path(directory)
        environment = dict(os.environ)
        environment.update({"RCT_OBSERVER_PROJECT": project, "RCT_GLITCHTIP_ALLOWED_HOSTS": "localhost,127.0.0.1,observer-proxy,glitchtip"})
        for key, value in [("RCT_GLITCHTIP_DB_PASSWORD_FILE", secrets.token_hex(24)),
                           ("RCT_GLITCHTIP_SECRET_KEY_FILE", secrets.token_hex(32)),
                           ("RCT_OBSERVER_HTPASSWD_FILE", "")]:
            path = work / key
            if key.endswith("HTPASSWD_FILE"):
                # Explicit stdin generation avoids credentials in process arguments.
                password = subprocess.run(["openssl", "passwd", "-apr1", "-stdin"], input="synthetic-operator-only\n", text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
                value = "operator:" + password.stdout.strip()
            path.write_text(value + "\n")
            path.chmod(0o400)
            if key != "RCT_OBSERVER_HTPASSWD_FILE":
                # The official GlitchTip image runs as UID5000; keep plaintext files private.
                run(["sudo", "chown", "5000:5000", str(path)])
            else:
                # Compose file secrets retain source ownership. The proxy's existing
                # root entrypoint needs to read this 0400 file without DAC capabilities.
                run(["sudo", "chown", "0:0", str(path)])
            environment[key] = str(path)
        envfile = work / "compose.env"
        envfile.write_text("\n".join(k + "=" + v for k, v in environment.items() if k.startswith("RCT_OBSERVER_") or k.startswith("RCT_GLITCHTIP_")) + "\n")
        original = ["docker", "compose", "--project-name", project, "--env-file", str(envfile), "-f", str(ROOT / "docker-compose.observability-apps.yml")]
        config = json.loads(run(original + ["config", "--format", "json"]).stdout)
        config.pop("name", None)
        run_identity = os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"]
        identity = {"ru.rutcampustrack.apps.source": revision,
                    "ru.rutcampustrack.apps.run": run_identity,
                    "ru.rutcampustrack.apps.scope": "isolated-observability-apps"}
        for section in ["services", "networks", "volumes"]:
            for item in config[section].values():
                item.setdefault("labels", {}).update(identity)
        for name in SERVICES:
            config["services"][name].pop("restart", None)
        # Only disposable CI adds loopback ports for the host JVM driver.
        config["services"]["observer-proxy"]["ports"] = [{"target": 8080, "published": "0", "host_ip": "127.0.0.1", "protocol": "tcp"}]
        for name, port in [("glitchtip", 8000), ("pyroscope", 4040)]:
            config["services"][name]["ports"] = [{"target": port, "published": "0", "host_ip": "127.0.0.1", "protocol": "tcp"}]
        # Homepage host validation includes the selected ephemeral port after creation.
        config["services"]["homepage"]["environment"]["HOMEPAGE_ALLOWED_HOSTS"] = "localhost,127.0.0.1,homepage:3000"
        composefile = work / "compose.json"
        composefile.write_text(json.dumps(config))
        compose = ["docker", "compose", "--project-name", project, "-f", str(composefile)]
        browser = None
        browser_log = None
        thread = None
        receipt = None
        primary_failure = None
        stop_sampler = threading.Event()
        sample_error = []
        measurements = {}

        def docker(*args, **kwargs):
            return run(compose + list(args), env=environment, **kwargs)

        resource_kinds = {
            "containers": (["docker", "ps", "-a", "--quiet", "--filter", "label=com.docker.compose.project=" + project],
                           ["docker", "inspect"], SERVICES),
            "networks": (["docker", "network", "ls", "--quiet", "--filter", "label=com.docker.compose.project=" + project],
                         ["docker", "network", "inspect"], set(config["networks"])),
            "volumes": (["docker", "volume", "ls", "--quiet", "--filter", "label=com.docker.compose.project=" + project],
                        ["docker", "volume", "inspect"], set(config["volumes"])),
        }

        def resources():
            return {kind: run(command).stdout.split() for kind, (command, _, _) in resource_kinds.items()}

        def guarded_resources():
            snapshot = resources()
            for kind, identifiers in snapshot.items():
                _, inspect, allowed = resource_kinds[kind]
                require(len(identifiers) <= len(allowed), "Unexpected disposable resource count")
                logical_names = set()
                for identifier in identifiers:
                    if kind == "volumes":
                        require(identifier in {item["name"] for item in config["volumes"].values()}, "Foreign named volume")
                    else:
                        require(re.fullmatch("[a-f0-9]{12,64}", identifier), "Invalid Docker resource ID")
                    field = "{{json .Config.Labels}}" if kind == "containers" else "{{json .Labels}}"
                    labels = json.loads(run(inspect + ["--format", field, identifier]).stdout)
                    logical_name = validate_resource_labels(kind, labels, project, revision, run_identity, allowed)
                    require(logical_name not in logical_names, "Duplicate disposable resource identity")
                    logical_names.add(logical_name)
            return snapshot

        def pid(name):
            value = docker("ps", "--all", "--quiet", name).stdout.strip()
            require(bool(re.fullmatch("[a-f0-9]{12,64}", value)), "Owned container missing")
            return value

        def port(name, value):
            address = docker("port", name, str(value)).stdout.strip()
            require(re.fullmatch(r"127\.0\.0\.1:[0-9]+", address), "Non-loopback test port")
            return "http://" + address

        def manage(code):
            command = ["exec", "-T", "glitchtip", "/bin/sh", "/observer/start.sh", "python", "manage.py", "shell", "-c", code]
            response = docker(*command, timeout=60).stdout
            marker = "RCT_OBSERVER_JSON="
            return json.loads(next(line[len(marker):] for line in response.splitlines() if line.startswith(marker)))

        def probe(mode, address=None, dsn=None):
            process_env = dict(environment)
            process_env.update({"RCT_PYROSCOPE_ADDRESS": address or "http://127.0.0.1:9/pyroscope", "RCT_JAVA_GLITCHTIP_DSN": dsn or ""})
            resultfile = work / (mode + ".json")
            run(["java", "-Xms64m", "-Xmx192m", "-XX:MaxDirectMemorySize=32m", "-cp", classpath,
                 "ru.rutcampustrack.attendance.report.ObservabilityProbe", mode, str(resultfile)], env=process_env, timeout=100)
            result = json.loads(resultfile.read_text())
            require(result["result"] == "PASS" and result["operation"]["operations"] > 0, "Actual report operation failed")
            require(0 < result["operation"]["peak_rss_bytes"] < 384 * MIB, "Java process memory budget exceeded")
            return result

        def unit(value):
            match = re.fullmatch(r"([0-9.]+)([kMGT]?i?B)", value.strip())
            require(match is not None, "Unknown Docker memory unit")
            scale = {"B": 1, "kB": 1000, "MB": 1000**2, "GB": 1000**3, "KiB": 1024, "MiB": MIB, "GiB": GIB}
            return float(match[1]) * scale[match[2]]

        def sampler():
            try:
                ids = [pid(name) for name in sorted(SERVICES)]
                names = dict(zip(ids, sorted(SERVICES)))
                while not stop_sampler.is_set():
                    require(shutil.disk_usage("/tmp").free >= 4 * GIB, "Runtime 4GiB disk floor crossed")
                    data = run(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids], timeout=15).stdout
                    for line in data.splitlines():
                        row = json.loads(line)
                        name = next(names[cid] for cid in ids if cid.startswith(row["ID"]))
                        state = measurements.setdefault(name, {"peak_working_set_bytes": 0, "peak_cpu_cores": 0, "samples": 0})
                        state["peak_working_set_bytes"] = max(state["peak_working_set_bytes"], unit(row["MemUsage"].split("/")[0]))
                        state["peak_cpu_cores"] = max(state["peak_cpu_cores"], float(row["CPUPerc"].rstrip("%")) / 100)
                        state["samples"] += 1
                        phase = (work / "phase").read_text().strip() or "transition"
                        bucket = state.setdefault("phases", {}).setdefault(phase, {"peak_working_set_bytes": 0, "peak_cpu_cores": 0, "samples": 0})
                        bucket["peak_working_set_bytes"] = max(bucket["peak_working_set_bytes"], unit(row["MemUsage"].split("/")[0]))
                        bucket["peak_cpu_cores"] = max(bucket["peak_cpu_cores"], float(row["CPUPerc"].rstrip("%")) / 100)
                        bucket["samples"] += 1
                    stop_sampler.wait(1)
            except Exception as error:
                sample_error.append(type(error).__name__)

        def volumes():
            result = {}
            for name in SERVICES:
                mounts = json.loads(run(["docker", "inspect", "--format", "{{json .Mounts}}", pid(name)]).stdout)
                for mount in mounts:
                    if mount["Type"] != "volume":
                        continue
                    require(mount["Name"].startswith(project + "_"), "Foreign volume encountered")
                    # GitHub hosted Linux Docker: no extra measuring image or database grants.
                    size = run(["sudo", "du", "-s", "-B1", mount["Source"]]).stdout.split()[0]
                    result[mount["Name"].removeprefix(project + "_")] = int(size)
            return result

        # Claim a fresh namespace before creating any resource. Never adopt an old stand.
        stage("fresh-owned-namespace")
        require(not any(resources().values()), "Disposable namespace is not empty")
        try:
            stage("java-baseline")
            baseline = probe("baseline")
            present_images = set(run(["docker", "image", "ls", "--quiet", "--no-trunc"]).stdout.split())
            stage("isolated-images")
            docker("pull", timeout=240)
            image_disk = {}
            new_image_ids = set()
            new_image_bytes = 0
            for image in static["images"].values():
                details = json.loads(run(["docker", "image", "inspect", "--format", "{{json .}}", image]).stdout)
                image_disk[image] = details["Size"]
                if details["Id"] not in present_images and details["Id"] not in new_image_ids:
                    new_image_ids.add(details["Id"])
                    new_image_bytes += details["Size"]
            require(shutil.disk_usage("/tmp").free >= 4 * GIB, "Image extraction violated disk floor")
            stage("isolated-startup")
            docker("up", "-d", timeout=120)
            stage("operator-loopback-port")
            origin = port("observer-proxy", 8080)
            # Exact host:port allowlist; wildcard is never used as authentication.
            config["services"]["homepage"]["environment"]["HOMEPAGE_ALLOWED_HOSTS"] = origin.removeprefix("http://") + ",homepage:3000"
            composefile.write_text(json.dumps(config))
            stage("homepage-exact-host-restart")
            docker("up", "-d", "--no-deps", "homepage", timeout=60)
            stage("homepage-readiness")
            wait_for(lambda: request(origin, auth=True)[0] == 200, 120)
            stage("glitchtip-readiness")
            wait_for(lambda: request(origin + "/glitchtip/", auth=True)[0] == 200, 180)
            pyroscope = port("pyroscope", 4040)
            wait_for(lambda: request(pyroscope + "/ready")[0] == 200, 120)
            glitchtip = port("glitchtip", 8000)
            keys = manage("from apps.projects.models import ProjectKey; import json; print('RCT_OBSERVER_JSON='+json.dumps({str(k.project_id):k.public_key_hex for k in ProjectKey.objects.order_by('project_id')}))")
            require(set(keys) == {"1", "2"}, "Fixed project allowlist missing")
            dsn = glitchtip.replace("://", "://" + keys["2"] + "@") + "/2"
            stage("finite-browser-ingest-boundary")
            route = origin + "/telemetry/errors/api/1/envelope/?sentry_key=" + keys["1"]
            require(request(route, method="GET")[0] == 405, "Ingest allowed GET")
            require(request(origin + "/telemetry/errors/api/2/envelope/", b"", method="POST")[0] == 404, "Unselected public project exposed")
            require(request(origin + "/telemetry/errors/api/1/envelope/", b"", method="POST")[0] == 400, "Missing public key accepted")
            require(request(route, b"x" * 65537, method="POST")[0] == 413, "Browser payload limit ineffective")
            with ThreadPoolExecutor(max_workers=8) as workers:
                limited = list(workers.map(lambda _: request(route, b"", method="POST")[0], range(10)))
            require(429 in limited, "Browser ingest rate budget ineffective")
            time.sleep(3)  # Refill the finite burst before the legitimate Vue captures.
            (work / "phase").write_text("apps-idle")
            thread = threading.Thread(target=sampler, daemon=True)
            thread.start()
            stage("measured-apps-idle-baseline")
            def idle_baseline_ready():
                require(not sample_error, "Sidecar sampler failed during idle baseline")
                return all(measurements.get(name, {}).get("phases", {}).get("apps-idle", {}).get("samples", 0) >= 3 for name in SERVICES)
            wait_for(idle_baseline_ready, 25)
            disk_before = volumes()
            profile_start = int(time.time() * 1000)
            stage("actual-java-profile")
            profile = probe("profile", pyroscope)
            stage("actual-java-and-vue-errors")
            java = probe("errors", dsn=dsn)
            browser_env = dict(environment)
            browser_log = (work / "browser.log").open("w")
            browser = subprocess.Popen(["node", str(ROOT / "infra/observability-apps/browser.mjs"), origin, keys["1"], str(work)], cwd=ROOT, env=browser_env, stdout=browser_log, stderr=subprocess.STDOUT)
            wait_for(lambda: (work / "browser-ready.json").exists() and browser.poll() is None, 120)

            def groups():
                # Evaluate structured stored frames inside the isolated receiver; export booleans only.
                code = """
from apps.issue_events.models import Issue, IssueEvent
import json
events = list(IssueEvent.objects.select_related('issue', 'release'))
def useful_stack(event):
    exception = event.data.get('exception', {})
    values = exception.get('values', []) if isinstance(exception, dict) else []
    frames = [frame for value in values if isinstance(value, dict)
              for frame in value.get('stacktrace', {}).get('frames', []) if isinstance(frame, dict)]
    def located(frame):
        return isinstance(frame.get('lineno'), int) and frame['lineno'] > 0
    if event.issue.project_id == 1:
        return any(located(frame) and frame.get('filename') == 'vue-fixture.ts'
                   and 'onClick' in frame.get('function', '') for frame in frames)
    if event.issue.project_id == 2:
        return any(located(frame) and frame.get('filename') == 'ObservabilityProbe.java'
                   and frame.get('function') == 'errorForExport'
                   and frame.get('module', '').endswith('.ObservabilityProbe') for frame in frames)
    return False
stacks = {str(project): bool([event for event in events if event.issue.project_id == project])
          and all(useful_stack(event) for event in events if event.issue.project_id == project)
          for project in [1, 2]}
print('RCT_OBSERVER_JSON=' + json.dumps({
    'issues': [{'project': issue.project_id, 'count': issue.count} for issue in Issue.objects.select_related('index')],
    'event_count': len(events),
    'release_ok': all(event.release and event.release.version == %r for event in events),
    'stack_ok_by_project': stacks,
    'marker_absent': all(%r not in json.dumps(event.data) for event in events),
}))
""" % (revision, MARKER)
                return manage(code)

            stage("glitchtip-group-stack-release-privacy")
            received = wait_for(lambda: (lambda data: data if sorted((x["project"], x["count"]) for x in data["issues"]) == [(1, 4), (2, 2)] else None)(groups()), 90)
            require(received["event_count"] == 6 and received["release_ok"] and all(received["stack_ok_by_project"].values()) and received["marker_absent"], "Error grouping, stack, release or privacy failed")
            query = {"start": profile_start - 1000, "end": int(time.time() * 1000) + 1000,
                     "labelSelector": '{service_name="rct.attendance"}', "profileTypeID": "process_cpu:cpu:nanoseconds:cpu:nanoseconds", "maxNodes": 16384}
            def flamegraph():
                code, body = request(pyroscope + "/querier.v1.QuerierService/SelectMergeStacktraces", query)
                return body if code == 200 and b"DocxRenderer" in body else None
            stage("actual-flamegraph-budget")
            flame = wait_for(flamegraph, 90)
            disk_after = volumes()
            require(sum(disk_after.values()) < 1024 * MIB, "Disposable data exceeds 1GiB measured budget")
            require(profile["operation"]["peak_rss_bytes"] - baseline["operation"]["peak_rss_bytes"] <= 64 * MIB, "Profiler RSS delta exceeds 64MiB")
            before_cpu = baseline["operation"]["process_cpu_ns"] / baseline["operation"]["operations"]
            after_cpu = profile["operation"]["process_cpu_ns"] / profile["operation"]["operations"]
            # Short CI is a bounded acceptance sample, not a production sizing benchmark.
            require(after_cpu <= before_cpu * 1.20, "Profiler normalized CPU overhead exceeds 20% CI budget")
            require(profile["idle"]["cpu_cores"] - baseline["idle"]["cpu_cores"] <= 0.05, "Idle profiler CPU overhead exceeds 0.05 core")
            effective = manage("from django.conf import settings; import json; print('RCT_OBSERVER_JSON='+json.dumps({'event_days':settings.GLITCHTIP_EVENT_RETENTION_DAYS,'file_days':settings.GLITCHTIP_FILE_RETENTION_DAYS,'release_days':settings.GLITCHTIP_RELEASE_RETENTION_DAYS,'payload_bytes':settings.GLITCHTIP_MAX_UNZIPPED_PAYLOAD_SIZE,'valkey_disabled':not bool(settings.VALKEY_URL)}))")
            require(effective == {"event_days": 7, "file_days": 7, "release_days": 30, "payload_bytes": 65536, "valkey_disabled": True}, "Effective GlitchTip budget differs")
            stage("persistent-restart")
            # Restart without replacing volumes; verify error data and profile query remain.
            # Graceful shutdown flushes the current v1 head before restarting it.
            docker("stop", "--timeout", "30", "glitchtip", "pyroscope", timeout=80)
            docker("start", "glitchtip", "pyroscope", timeout=60)
            wait_for(lambda: request(origin + "/glitchtip/", auth=True)[0] == 200, 90)
            wait_for(flamegraph, 90)
            require(groups()["event_count"] == 6, "Error data lost on restart")
            stage("telemetry-outage")
            # Error consumer outage affects neither the Vue action nor actual Java report.
            docker("stop", "glitchtip", "pyroscope", timeout=60)
            (work / "outage").touch()
            outage = probe("outage", "http://127.0.0.1:9/pyroscope", "http://" + keys["2"] + "@127.0.0.1:9/glitchtip/2")
            browser.wait(timeout=40)
            browser_log.close()
            require(browser.returncode == 0, "Browser app acceptance failed")
            browser_result = json.loads((work / "browser.json").read_text())
            stop_sampler.set(); thread.join(timeout=20)
            require(not sample_error and set(measurements) == SERVICES, "Resource measurements incomplete")
            for name, values in measurements.items():
                for phase, minimum in {"apps-idle": 3, "profile-idle": 3, "profile-operation": 5}.items():
                    require(values.get("phases", {}).get(phase, {}).get("samples", 0) >= minimum, "Required sidecar measurement phase incomplete")
                limit = int(run(["docker", "inspect", "--format", "{{.HostConfig.Memory}}", pid(name)]).stdout)
                require(values["peak_working_set_bytes"] <= limit, "Sidecar memory budget exceeded")
            receipt = {"result": "PASS", "source_sha": revision, "images": static["images"],
                       "glitchtip": {"issues": received["issues"], "events": received["event_count"], "stack_release_privacy": "PASS", "useful_stack_by_project": received["stack_ok_by_project"], "public_ingest_method_size_key_project_rate": "PASS", "effective_retention": effective, "restart": "PASS"},
                       "homepage": browser_result, "pyroscope": {"flamegraph_sha256": hashlib.sha256(flame).hexdigest(), "actual_frame": "DocxRenderer", "retention_hours": 24, "restart": "PASS", "sampler": "itimer/10ms/upload10s/depth256/CPU-only/one-JVM"},
                       "resources": {"baseline": baseline, "profile": profile, "sidecars": measurements, "disk_before_bytes": disk_before, "disk_after_bytes": disk_after, "image_logical_bytes": image_disk, "new_images_logical_bytes_upper_bound": new_image_bytes, "operation_cpu_ratio": after_cpu / before_cpu, "sidecar_memory_limit_mib": static["memory_limit_mib"], "disk_floor_bytes": 4 * GIB, "sampling": "Docker working set and CPU every ~2s; JVM RSS every50ms/process CPU time"},
                       "telemetry_outage": outage["result"], "pmm": {"selected": False, "reason": "Existing PostgreSQL/Mongo exporters and Grafana cover the selected workload; query analytics adds statement/profiler access and another resource budget without a demonstrated extra diagnosis."},
                       "qualification": "Actual isolated GlitchTip/Java+Vue SDKs/CPU flamegraph/operator links. Synthetic Vue operations exercise the shared adapter under PWA/TMA prefixes; not a full authenticated business journey. Effective retention is checked; seven-day wall-clock expiry is not elapsed. 16GiB Cloud capacity is not a 32GiB VPS benchmark."}
        except BaseException as error:
            primary_failure = {**safe_failure(error), "stage": CURRENT_STAGE}
            # Diagnose only this already guarded synthetic namespace, before cleanup.
            # No State.Error, environment, IDs, log text, arguments or URLs leave memory.
            try:
                states = {}
                for identifier in guarded_resources()["containers"]:
                    fields = ('{"service":{{json (index .Config.Labels "com.docker.compose.service")}},'
                              '"status":{{json .State.Status}},"exit_code":{{.State.ExitCode}},'
                              '"oom_killed":{{.State.OOMKilled}},"health":'
                              '{{if .State.Health}}{{json .State.Health.Status}}{{else}}"none"{{end}}}')
                    raw_state = json.loads(run(["docker", "inspect", "--format", fields, identifier], timeout=10).stdout)
                    service = raw_state.get("service")
                    state = {key: raw_state.get(key) for key in ["status", "exit_code", "oom_killed", "health"]}
                    require(service in SERVICES and service not in states, "Unexpected diagnostic service")
                    require(state["status"] in {"created", "running", "paused", "restarting", "removing", "exited", "dead"}, "Unexpected diagnostic state")
                    require(type(state["exit_code"]) is int and type(state["oom_killed"]) is bool, "Unexpected diagnostic types")
                    require(state["health"] in {"none", "starting", "healthy", "unhealthy"}, "Unexpected diagnostic health")
                    logs = run(["docker", "logs", "--tail", "40", identifier], timeout=10, check=False)
                    state["log_capture"] = "PASS" if logs.returncode == 0 else "FAIL"
                    state["reason"] = closed_reason(logs.stdout + "\n" + logs.stderr, service) if logs.returncode == 0 else "UNKNOWN"
                    states[service] = state
                primary_failure["container_states"] = {"result": "PASS", "services": states}
            except BaseException as diagnostic_error:
                primary_failure["container_states"] = {"result": "FAIL", **safe_failure(diagnostic_error)}
        finally:
            stop_sampler.set()
            stage("verified-owned-cleanup")
            cleanup = {"result": "FAIL"}
            try:
                if thread is not None:
                    thread.join(timeout=20)
                    require(not thread.is_alive(), "Owned sampler did not stop")
                if browser is not None and browser.poll() is None:
                    browser.terminate()
                    try: browser.wait(timeout=5)
                    except subprocess.TimeoutExpired: browser.kill(); browser.wait(timeout=5)
                if browser_log is not None and not browser_log.closed:
                    browser_log.close()
                owned = guarded_resources()
                removed = {kind: len(values) for kind, values in owned.items()}
                require(docker("down", "--volumes", "--remove-orphans", timeout=60, check=False).returncode == 0, "Owned Compose cleanup failed")
                require(not any(resources().values()), "Owned resources remain after cleanup")
                cleanup = {"result": "PASS", "verified_removed_counts": removed}
            except BaseException as error:
                cleanup = {"result": "FAIL", **safe_failure(error)}
            if primary_failure is not None:
                receipt = {"result": "FAIL", "source_sha": revision, **primary_failure, "cleanup": cleanup}
            elif cleanup["result"] != "PASS":
                receipt = {"result": "FAIL", "source_sha": revision, "failure_class": cleanup["failure_class"], "stage": "verified-owned-cleanup", "cleanup": cleanup}
            else:
                require(receipt is not None, "Acceptance receipt missing")
                receipt["cleanup"] = cleanup
            # PASS becomes observable only after exact namespace and absence verification.
            (output / "apps.json").write_text(json.dumps(receipt, indent=2) + "\n")
            print(json.dumps(receipt))
        return 0 if receipt["result"] == "PASS" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main() or 0)
    except Exception as error:
        failure = {"result": "FAIL", "source_sha": os.environ.get("GITHUB_SHA"), **safe_failure(error), "stage": CURRENT_STAGE}
        output = ROOT / "build/monitoring"
        output.mkdir(parents=True, exist_ok=True)
        (output / "apps.json").write_text(json.dumps(failure, indent=2) + "\n")
        print(json.dumps(failure), file=sys.stderr)
        raise SystemExit(1)
