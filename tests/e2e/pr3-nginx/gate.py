"""Finite PR3 assembled-Nginx handoff. Preparation never starts Docker or an API session."""
import argparse
import hashlib
import http.client
import importlib.util
import json
import os
import re
import shutil
import ssl
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

REPO = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
APPS = {"web": "web-vue", "pwa": "pwa-vue", "tma": "tma-vue"}
BASES = {"web": "/", "pwa": "/app/", "tma": "/mini-app/"}
EARLY = 5 * 1024**3 // 2
HARD = 2 * 1024**3


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(REPO), *args], timeout=15)


def revision(value):
    require(re.fullmatch(r"[0-9a-f]{40}", value), "Use an exact full published source SHA")
    require(git("rev-parse", value + "^{commit}").decode().strip() == value, "Missing source commit")
    return value


def budget(path):
    free = shutil.disk_usage(path).free
    require(free >= HARD, "Disk hard-stop threshold: less than 2 GiB available")
    require(free >= EARLY, "Disk early-stop threshold: less than 2.5 GiB available")
    return free


def sha(data):
    return hashlib.sha256(data).hexdigest()


def app_inputs(app, commit):
    paths = ["frontends/" + APPS[app], "frontends/mobile-core", "frontends/shared",
             "frontends/package.json", "frontends/package-lock.json", "frontends/postcss.config.mjs",
             "frontends/tsconfig.foundation.json", "frontends/dev-server.ts"]
    if app == "web":
        # WEB imports the PWA auth client and password recovery implementation.
        paths.append("frontends/pwa-vue")
    entries = git("ls-tree", "-r", "-z", commit, "--", *paths).split(b"\0")
    # Distribution files and installed dependencies cannot establish source equivalence.
    entries = [item for item in entries if item and not
               ({"dist", "node_modules"} & set(item.split(b"\t", 1)[1].decode().split("/")))]
    require(entries, "No frontend source inputs")
    return sha(b"\0".join(sorted(entries)))


def checkout(commit):
    revision(commit)
    require(git("rev-parse", "HEAD").decode().strip() == commit, "Checkout HEAD differs from source pin")
    require(not git("diff", "--name-only", "HEAD", "--", "frontends", "nginx",
                    "scripts/verify-served-web-panel.py").strip(), "Tracked frontend/Nginx inputs changed")
    # Preserve worktree dependency links; reject additional untracked source inputs.
    untracked = git("ls-files", "--others", "--exclude-standard", "-z", "--", "frontends", "nginx")
    require(all({"node_modules", "dist"} & set(p.decode().split("/"))
                for p in untracked.split(b"\0") if p), "Untracked frontend/Nginx source inputs")


def inventory(directory):
    directory = Path(directory).resolve(strict=True)
    require(directory.is_dir() and (directory / "index.html").is_file(), "Missing built index.html")
    result = {}
    for path in sorted(directory.rglob("*")):
        require(not path.is_symlink(), "Distribution symlinks are not accepted")
        if path.is_file():
            data = path.read_bytes()
            require(len(data) <= 32 * 1024**2, "Distribution file exceeds finite oracle limit")
            result[path.relative_to(directory).as_posix()] = {"bytes": len(data), "sha256": sha(data)}
    require(sum(v["bytes"] for v in result.values()) <= 64 * 1024**2, "Distribution exceeds 64 MiB budget")
    return result


def save(path, value):
    with Path(path).open("x", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def pin(args):
    revision(args.source_sha)
    save(args.out, {"app": args.app, "sourceSha": args.source_sha,
                   "sourceInputsSha256": app_inputs(args.app, args.source_sha),
                   "files": inventory(args.dist), "buildEvidenceSha256": sha(Path(args.build_log).read_bytes()),
                   "provenance": "EXTERNAL_ACCEPTED_BUILD_EVIDENCE"})


def replace_once(text, old, new):
    require(text.count(old) == 1, "Canonical Nginx configuration changed: " + old)
    return text.replace(old, new)


def upstream(text, name, value):
    pattern = r'(set\s+\$' + name + r'\s+)"[^"]+";'
    text, count = re.subn(pattern, lambda m: m[1] + '"' + value + '";', text)
    require(count == 1, "Expected one canonical upstream: " + name)
    return text


def prepare(args):
    checkout(args.expected_sha)
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", args.image_id), "Pin the retained Nginx image by full image ID")
    require(re.fullmatch(r"[a-zA-Z0-9_.-]+:[0-9]{2,5}", args.gateway), "Use one explicit Docker gateway host:port")
    require(re.fullmatch(r"[a-zA-Z0-9_.-]+", args.network), "Invalid existing network name")
    require(1024 <= args.port <= 65535, "Use a nonprivileged loopback port")
    output = Path(args.out).resolve()
    require(output.parent.is_dir() and not output.exists(), "Output must be a fresh directory with an existing parent")
    free = budget(output.parent)
    require(free - 512 * 1024**2 >= EARLY, "512 MiB forecast would cross the early threshold")
    dists, receipts = {}, {}
    for app in APPS:
        dist = Path(getattr(args, app + "_dist")).resolve(strict=True)
        receipt = json.loads(Path(getattr(args, app + "_pin")).read_text())
        require(receipt["app"] == app, "Wrong app receipt")
        revision(receipt["sourceSha"])
        require(receipt["sourceInputsSha256"] == app_inputs(app, args.expected_sha), "Artifact inputs do not match: " + app)
        require(receipt["files"] == inventory(dist), "Artifact bytes changed: " + app)
        dists[app], receipts[app] = dist, receipt
    landing = Path(args.landing_dist).resolve(strict=True)
    require((landing / "index.html").is_file(), "Missing existing landing artifact")
    output.mkdir()
    (output / "conf.d").mkdir()
    (output / "tls").mkdir(mode=0o700)
    config = (REPO / "nginx/nginx.conf").read_text()
    config = replace_once(config, "worker_processes auto;", "worker_processes 1;")
    (output / "nginx.conf").write_text(config)
    edge = (REPO / "nginx/conf.d/default.conf").read_text()
    edge = replace_once(edge, "/etc/letsencrypt/live/ruttrack.site/fullchain.pem", "/etc/nginx/certs/fullchain.pem")
    edge = replace_once(edge, "/etc/letsencrypt/live/ruttrack.site/privkey.pem", "/etc/nginx/certs/privkey.pem")
    edge = upstream(edge, "upstream_gateway", args.gateway)
    # Keep source routes, rewrites, security headers, CSP and API/WS handling intact.
    for name, port in [("upstream_web_panel", 8081), ("upstream_pwa", 8082),
                       ("upstream_mini_app", 8083), ("upstream_landing", 8084)]:
        edge = upstream(edge, name, "127.0.0.1:" + str(port))
    (output / "conf.d/default.conf").write_text(edge)
    for index, (app, folder) in enumerate([*APPS.items(), ("landing", "landing")], 1):
        inner = (REPO / "frontends" / folder / "nginx.conf").read_text()
        # APPS iteration is web,pwa,tma: one process, four faithful inner servers.
        inner = replace_once(inner, "listen 80;", "listen " + str(8080 + index) + ";")
        inner = replace_once(inner, "root /usr/share/nginx/html;", "root /srv/" + app + ";")
        (output / "conf.d" / (app + ".conf")).write_text(inner)
    mounts = [{"type": "bind", "source": str(output / "nginx.conf"), "target": "/etc/nginx/nginx.conf", "read_only": True},
              {"type": "bind", "source": str(output / "conf.d"), "target": "/etc/nginx/conf.d", "read_only": True},
              {"type": "bind", "source": str(output / "tls"), "target": "/etc/nginx/certs", "read_only": True},
              {"type": "bind", "source": str(output / "tls/dhparam.pem"), "target": "/etc/nginx/dhparam.pem", "read_only": True}]
    mounts += [{"type": "bind", "source": str(path), "target": "/srv/" + app, "read_only": True}
               for app, path in {**dists, "landing": landing}.items()]
    compose = {"name": "rct-pr3-nginx-gate", "services": {"edge": {
        "image": args.image_id, "pull_policy": "never", "entrypoint": ["nginx"],
        "command": ["-g", "daemon off;"], "read_only": True,
        "ports": ["127.0.0.1:" + str(args.port) + ":443"], "volumes": mounts,
        "tmpfs": ["/var/cache/nginx:size=16m", "/var/run:size=1m", "/var/log/nginx:size=8m", "/tmp:size=1m"],
        "mem_limit": "128m", "cpus": 0.5, "pids_limit": 32,
        "cap_drop": ["ALL"], "cap_add": ["NET_BIND_SERVICE", "SETUID", "SETGID", "CHOWN"],
        "security_opt": ["no-new-privileges:true"], "networks": ["main"]}},
        "networks": {"main": {"external": True, "name": args.network}}}
    save(output / "compose.json", compose)
    save(output / "pins.json", {"sourceSha": args.expected_sha, "imageId": args.image_id,
        "availableBefore": free, "forecastMaxAdditionalBytes": 512 * 1024**2,
        "apps": receipts, "dists": {app: str(path) for app, path in dists.items()},
        "configs": {str(p.relative_to(output)): sha(p.read_bytes())
                    for p in [output / "nginx.conf", *sorted((output / "conf.d").iterdir())]},
        "localAdaptations": ["one worker", "fresh local certificate paths", "explicit gateway DNS",
                             "four loopback inner upstreams/listeners and read-only artifact roots"],
        "productionTLSAndDeployment": "NOT_RUN"})
    print(json.dumps({"state": "PREPARED_NOT_STARTED", "sourceSha": args.expected_sha}))


def origin(value):
    parsed = urlsplit(value)
    require(parsed.hostname in {"127.0.0.1", "localhost"} and parsed.scheme in {"http", "https"}
            and not parsed.username and not parsed.password and parsed.path in {"", "/"}
            and not parsed.query and not parsed.fragment, "Only a local origin is accepted")
    return parsed


def fetch(base, path, context):
    parsed = origin(base)
    connection = (http.client.HTTPSConnection(parsed.hostname, parsed.port, timeout=8, context=context)
                  if parsed.scheme == "https" else http.client.HTTPConnection(parsed.hostname, parsed.port, timeout=8))
    try:
        connection.request("GET", path, headers={"Accept-Encoding": "identity"})
        response = connection.getresponse()
        body = response.read(32 * 1024**2 + 1)
        require(len(body) <= 32 * 1024**2, "Oversized HTTP response")
        return response.status, {k.lower(): v for k, v in response.getheaders()}, body
    finally:
        connection.close()


def check(args):
    pins = json.loads((Path(args.run_dir) / "pins.json").read_text())
    checkout(args.expected_sha)
    require(pins["sourceSha"] == args.expected_sha, "Prepared source pin differs")
    budget(args.run_dir)
    for relative, digest in pins["configs"].items():
        require(sha((Path(args.run_dir) / relative).read_bytes()) == digest, "Prepared Nginx bytes changed")
    docker_env = {key: value for key, value in os.environ.items()
                  if key not in {"DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"}}
    template = ('{"image":{{json .Image}},"running":{{json .State.Running}},'
                '"mounts":{{json .Mounts}},"entrypoint":{{json .Config.Entrypoint}},"command":{{json .Config.Cmd}}}')
    container = json.loads(subprocess.check_output(["docker", "--host=unix:///var/run/docker.sock", "inspect", "--format",
                                                   template, "rct-pr3-nginx-gate-edge-1"], env=docker_env, timeout=10))
    require(container["running"] and container["image"] == pins["imageId"], "Owned running Nginx image differs from its pin")
    require(container["entrypoint"] == ["nginx"] and container["command"] == ["-g", "daemon off;"], "Unexpected Nginx entrypoint")
    mounts = {item["Destination"]: item for item in container["mounts"]}
    expected_mounts = {"/etc/nginx/nginx.conf": str(Path(args.run_dir).resolve() / "nginx.conf"),
                       "/etc/nginx/conf.d": str(Path(args.run_dir).resolve() / "conf.d")}
    expected_mounts.update({"/srv/" + app: path for app, path in pins["dists"].items()})
    require(all(destination in mounts and mounts[destination]["Source"] == source and not mounts[destination]["RW"]
                for destination, source in expected_mounts.items()), "Running Nginx mounts do not match prepared configuration/artifacts")
    context = ssl.create_default_context(cafile=str(Path(args.run_dir) / "tls/fullchain.pem"))
    require(origin(args.base).scheme == "https", "Use the local TLS common entry")
    spec = importlib.util.spec_from_file_location("served_vue", REPO / "scripts/verify-served-web-panel.py")
    oracle = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(oracle)
    results = {"sourceSha": args.expected_sha, "artifactBytes": {}, "commonNginxStatic": "FAIL",
               "apiForwarding": "NOT_RUN", "realtimeAuthenticatedTransport": "NOT_RUN",
               "webAuthLifecycle": "NOT_RUN", "pwaAuthAndUpdates": "NOT_RUN",
               "tmaPostLogin": "NOT_RUN", "webPushProvider": "NOT_RUN", "productionTLSAndDeployment": "NOT_RUN"}
    for app, prefix in BASES.items():
        dist = Path(pins["dists"][app])
        require(inventory(dist) == pins["apps"][app]["files"], "Pinned artifact bytes changed")
        expected_index = (dist / "index.html").read_bytes()
        # Adapt only the public base for the existing root-only served Vue oracle.
        normalized = expected_index.replace((prefix + "assets/").encode(), b"/assets/") if prefix != "/" else expected_index
        entry = oracle.entry_asset(normalized)
        real_entry = prefix.rstrip("/") + entry
        expected_asset = (dist / entry.lstrip("/")).read_bytes()
        route = {"web": "/login", "pwa": "/app/", "tma": "/mini-app/"}[app]
        status, headers, index = fetch(args.base, route, context)
        require(index == expected_index, "Wrong SPA shell: " + app)
        asset_status, asset_headers, body = fetch(args.base, real_entry, context)
        normalized_served = index.replace((prefix + "assets/").encode(), b"/assets/") if prefix != "/" else index
        oracle.verify(normalized, expected_asset, (status, headers.get("content-type", ""), normalized_served),
                      (asset_status, asset_headers.get("content-type", ""), body))
        for relative, expected in pins["apps"][app]["files"].items():
            file_status, file_headers, file_body = fetch(args.base, prefix + relative, context)
            require(file_status == 200 and sha(file_body) == expected["sha256"], "Served artifact mismatch: " + app + "/" + relative)
            if relative.endswith((".js", ".mjs")):
                require(file_headers.get("content-type", "").split(";")[0] in
                        {"application/javascript", "text/javascript"}, "Script MIME: " + app)
            if relative.endswith(".css"):
                require(file_headers.get("content-type", "").split(";")[0] == "text/css", "CSS MIME: " + app)
        require("no-store" in headers.get("cache-control", ""), "Shell cache policy: " + app)
        require("immutable" in asset_headers.get("cache-control", ""), "Asset cache policy: " + app)
        require("default-src 'self'" in headers.get("content-security-policy", ""), "Missing common-entry CSP")
        for deep in {"web": ["/student/home", "/teacher/home", "/admin/dashboard", "/headman"],
                     "pwa": ["/app/pr3-owned-deep-link"], "tma": ["/mini-app/pr3-owned-deep-link"]}[app]:
            for _ in range(2):
                deep_status, deep_headers, deep_body = fetch(args.base, deep, context)
                require(deep_status == 200 and deep_body == expected_index and
                        "no-store" in deep_headers.get("cache-control", ""), "Deep link/refresh: " + app)
        missing, _, missing_body = fetch(args.base, prefix + "assets/pr3-owned-missing.js", context)
        require(missing == 404 and missing_body != expected_index, "Missing asset becomes a SPA shell: " + app)
        results["artifactBytes"][app] = "PASS"
    root_status, root_headers, _ = fetch(args.base, "/", context)
    require(root_status == 301 and root_headers.get("location", "").endswith("/login"), "Common root route")
    for name in ["sw.js", "sw-assets.js", "version.json", "manifest.webmanifest"]:
        status, headers, body = fetch(args.base, "/app/" + name, context)
        require(status == 200 and body == (Path(pins["dists"]["pwa"]) / name).read_bytes(), "PWA policy artifact mismatch")
        if name != "manifest.webmanifest":
            require("no-store" in headers.get("cache-control", ""), "PWA policy caching")
    results["commonNginxStatic"] = "PASS"
    # Real anonymous read forwarding is distinct from a successful authenticated API flow.
    if args.api_base:
        results["apiObservations"] = []
        for path in ["/api/auth/session", "/api/ws/info"]:
            edge_status, edge_headers, _ = fetch(args.base, path, context)
            direct_status, direct_headers, _ = fetch(args.api_base, path, context)
            results["apiObservations"].append({"path": path, "edgeStatus": edge_status,
                "directStatus": direct_status, "sameStatus": edge_status == direct_status,
                "edgeMime": edge_headers.get("content-type", ""), "directMime": direct_headers.get("content-type", "")})
        available = all(item["sameStatus"] and item["edgeStatus"] in {200, 401, 403} and
                        "text/html" not in item["edgeMime"] for item in results["apiObservations"])
        results["apiForwarding"] = "OBSERVED_ANONYMOUS_ONLY" if available else "FAIL_UNAVAILABLE_OR_DIFFERENT"
    results["state"] = "STATIC_CRITERIA_ONLY_OTHER_CRITERIA_NOT_RUN"
    save(args.out, results)
    print(json.dumps(results))
    if results["apiForwarding"].startswith("FAIL"):
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("pin", help="Record explicitly accepted external build evidence, without building")
    for name in ["source-sha", "dist", "build-log", "out"]:
        record.add_argument("--" + name, required=True)
    record.add_argument("--app", choices=APPS, required=True)
    record.set_defaults(action=pin)
    prepare_parser = commands.add_parser("prepare", help="Write isolated configuration; never start containers")
    for name in ["expected-sha", "image-id", "gateway", "network", "landing-dist", "out"]:
        prepare_parser.add_argument("--" + name, required=True)
    for app in APPS:
        prepare_parser.add_argument("--" + app + "-dist", required=True)
        prepare_parser.add_argument("--" + app + "-pin", required=True)
    prepare_parser.add_argument("--port", type=int, default=14443)
    prepare_parser.set_defaults(action=prepare)
    check_parser = commands.add_parser("check", help="Read only the owned local common Nginx and optional real API gateway")
    for name in ["expected-sha", "run-dir", "base", "out"]:
        check_parser.add_argument("--" + name, required=True)
    check_parser.add_argument("--api-base")
    check_parser.set_defaults(action=check)
    args = parser.parse_args()
    args.action(args)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(json.dumps({"state": "FAIL", "reason": str(error)}))
        raise SystemExit(1)
