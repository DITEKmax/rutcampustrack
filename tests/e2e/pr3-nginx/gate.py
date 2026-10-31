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
import uuid
from pathlib import Path
from urllib.parse import urljoin, urlsplit

REPO = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
APPS = {"web": "web-vue", "pwa": "pwa-vue", "tma": "tma-vue"}
BASES = {"web": "/", "pwa": "/app/", "tma": "/mini-app/"}
EARLY = 5 * 1024**3 // 2
HARD = 2 * 1024**3
PACKAGE = "tests/e2e/pr3-nginx"
RUN_LABEL = "rct.pr3-nginx.run"
TLS_STATIC_PORT = 14443
HTTP_AUTH_PORT = 14240
LOCAL_RELEASE_PROFILE = None
RUNTIME_NGINX_INPUTS = frozenset("frontends/" + folder + "/nginx.conf" for folder in APPS.values())


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
    # These exact three server configs are consumed only by runtime Nginx, not
    # vue-tsc/Vite. prepare() separately hashes their generated runtime copies.
    entries = [item for item in entries if item and
               item.split(b"\t", 1)[1].decode() not in RUNTIME_NGINX_INPUTS and not
               ({"dist", "node_modules"} & set(item.split(b"\t", 1)[1].decode().split("/")))]
    require(entries, "No frontend source inputs")
    return sha(b"\0".join(sorted(entries)))


def checkout(commit):
    revision(commit)
    require(git("rev-parse", "HEAD").decode().strip() == commit, "Checkout HEAD differs from source pin")
    paths = ["frontends", "nginx", "scripts/verify-served-web-panel.py", PACKAGE]
    require(not git("diff", "--name-only", "HEAD", "--", *paths).strip(), "Tracked frontend/Nginx/gate inputs changed")
    # Preserve worktree dependency links; reject additional untracked source inputs.
    untracked = git("ls-files", "--others", "--exclude-standard", "-z", "--", *paths)
    require(all(p.decode().startswith("frontends/") and {"node_modules", "dist"} & set(p.decode().split("/"))
                for p in untracked.split(b"\0") if p), "Untracked frontend/Nginx/gate source inputs")


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


def producer_config(app):
    return {"NODE_ENV": "production", "VITE_PUBLIC_BASE": BASES[app], "VITE_MOBILE_FIXTURE_MODE": "false",
            "VITE_APP_VERSION": "0.0.0", "VITE_MIN_SUPPORTED_VERSION": "",
            "VITE_FORCE_UPDATE": "true"}


def served_oracle():
    spec = importlib.util.spec_from_file_location("served_vue", REPO / "scripts/verify-served-web-panel.py")
    oracle = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(oracle)
    return oracle


def artifact_contract(app, directory):
    """Source equivalence alone cannot establish Vite's producer environment."""
    directory = Path(directory)
    prefix = BASES[app]
    html = (directory / "index.html").read_bytes()
    normalized = html.replace((prefix + "assets/").encode(), b"/assets/") if prefix != "/" else html
    oracle = served_oracle()
    entry = oracle.entry_asset(normalized)
    parsed = oracle.Entrypoint()
    parsed.feed(html.decode("utf-8"))
    expected_entry = prefix.rstrip("/") + entry
    require(parsed.modules == [expected_entry], "Artifact public base differs: " + app)
    require((directory / entry.lstrip("/")).is_file(), "Artifact entry module is missing: " + app)
    for url in re.findall(r'''(?:src|href)\s*=\s*["'](/[^"']*)["']''', html.decode("utf-8")):
        require(not url.startswith("//") and url.startswith(prefix), "Artifact root-relative URL escapes its public base: " + app)
    policy_hash = None
    if app == "pwa":
        policy = (directory / "version.json").read_bytes()
        value = json.loads(policy)
        require(value["latest"] == value["minimumSupported"] == "0.0.0" and value["force"] is True,
                "PWA artifact version policy differs from the explicit Docker defaults")
        policy_hash = sha(policy)
    config = producer_config(app)
    return {"publicBase": prefix, "entryPath": expected_entry, "producerConfig": config,
            "producerConfigSha256": sha(json.dumps(config, sort_keys=True).encode()),
            "versionPolicySha256": policy_hash}


def save(path, value):
    with Path(path).open("x", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def pin(args):
    checkout(args.expected_sha)
    require(args.fixture_mode == "false", "External accepted producer must explicitly attest fixture mode false")
    revision(args.source_sha)
    save(args.out, {"app": args.app, "sourceSha": args.source_sha,
                   "gateSourceSha": args.expected_sha,
                   "sourceInputsSha256": app_inputs(args.app, args.source_sha),
                   "files": inventory(args.dist), "buildEvidenceSha256": sha(Path(args.build_log).read_bytes()),
                   "artifactContract": artifact_contract(args.app, args.dist),
                   "producerConfigEvidence": "ACCEPTED_EXTERNAL_BUILD_ASSERTION_FIXTURE_FALSE",
                   "provenance": "EXTERNAL_ACCEPTED_BUILD_EVIDENCE"})


def replace_once(text, old, new):
    require(text.count(old) == 1, "Canonical Nginx configuration changed: " + old)
    return text.replace(old, new)


def upstream(text, name, value):
    pattern = r'(set\s+\$' + name + r'\s+)"[^"]+";'
    text, count = re.subn(pattern, lambda m: m[1] + '"' + value + '";', text)
    require(count == 1, "Expected one canonical upstream: " + name)
    return text


def runtime_tmpfs(user):
    require(re.fullmatch(r"[1-9][0-9]*:[0-9]+", user), "Runtime UID must be nonroot with an explicit GID")
    uid, gid = user.split(":")
    return {path: "size=" + size + ",uid=" + uid + ",gid=" + gid + ",mode=0700"
            for path, size in [("/var/cache/nginx", "16m"), ("/var/run", "1m"),
                               ("/var/log/nginx", "8m"), ("/tmp", "1m")]}


def prepare(args):
    checkout(args.expected_sha)
    require(os.getuid() != 0, "Prepare must run as the nonroot artifact/TLS owner")
    runtime_user = str(os.getuid()) + ":" + str(os.getgid())
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", args.image_id), "Pin the retained Nginx image by full image ID")
    require(re.fullmatch(r"[a-zA-Z0-9_.-]+:[0-9]{2,5}", args.gateway), "Use one explicit Docker gateway host:port")
    require(re.fullmatch(r"[a-zA-Z0-9_.-]+", args.network), "Invalid existing network name")
    require(args.port == TLS_STATIC_PORT, "Only the reviewed TLS14443/HTTP14240 pair is admitted")
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
        require(receipt["artifactContract"] == artifact_contract(app, dist), "Artifact producer contract changed: " + app)
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
    # This redirect listener is not published, but must also be capless/nonroot.
    edge = replace_once(edge, "listen 80;", "listen 8080;")
    # Match the external loopback TLS port so redirects and forwarded port stay truthful.
    edge = replace_once(edge, "listen 443 ssl;", "listen " + str(args.port) + " ssl;\n    listen " + str(HTTP_AUTH_PORT) + ";")
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
    nonce = str(uuid.uuid4())
    compose = {"name": (LOCAL_RELEASE_PROFILE["project"] if LOCAL_RELEASE_PROFILE else "rct-pr3-nginx-gate"), "services": {"edge": {
        "labels": {RUN_LABEL: nonce}, "user": runtime_user,
        "image": args.image_id, "pull_policy": "never", "entrypoint": ["nginx"],
        "command": ["-g", "daemon off;"], "read_only": True,
        "ports": ["127.0.0.1:" + str(args.port) + ":" + str(args.port),
                  "127.0.0.1:" + str(HTTP_AUTH_PORT) + ":" + str(HTTP_AUTH_PORT)], "volumes": mounts,
        "tmpfs": [path + ":" + options for path, options in runtime_tmpfs(runtime_user).items()],
        "mem_limit": "128m", "memswap_limit": "128m", "cpus": 0.5, "pids_limit": 32,
        "cap_drop": ["ALL"],
        "security_opt": ["no-new-privileges:true"], "networks": ({"main":{"ipv4_address":LOCAL_RELEASE_PROFILE["ipv4"]}} if LOCAL_RELEASE_PROFILE else ["main"])}},
        "networks": {"main": {"external": True, "name": args.network}}}
    save(output / "compose.json", compose)
    save(output / "pins.json", {"sourceSha": args.expected_sha, "imageId": args.image_id,
        "runNonce": nonce, "runtimeUser": runtime_user, "localReleaseProfile": LOCAL_RELEASE_PROFILE,
        "availableBefore": free, "forecastMaxAdditionalBytes": 512 * 1024**2,
        "apps": receipts, "dists": {app: str(path) for app, path in dists.items()},
        "configs": {str(p.relative_to(output)): sha(p.read_bytes())
                    for p in [output / "compose.json", output / "nginx.conf", *sorted((output / "conf.d").iterdir())]},
        "verificationOrigins": {"httpsStaticAnonymous": "https://127.0.0.1:" + str(TLS_STATIC_PORT),
                                "httpAuthRealtime": "http://127.0.0.1:" + str(HTTP_AUTH_PORT)},
        "tlsAuthWss": "NOT_RUN_OWNER_LOCAL",
        "localAdaptations": ["one worker", "host nonroot UID:GID without added capabilities", "unpublished redirect listener8080", "matching external/local TLS port", "same-server HTTP14240 listener", "fresh local certificate paths", "explicit gateway DNS",
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


def direct_api_trust(base, ca_path, expected_sha):
    """Pin the direct API's separate local CA; never reuse the Nginx leaf trust."""
    if not base:
        require(not ca_path and not expected_sha, "API CA requires --api-base")
        return None, None
    if origin(base).scheme == "http":
        require(not ca_path and not expected_sha, "HTTP API does not use a TLS CA")
        return None, None
    require(ca_path and re.fullmatch(r"[0-9a-f]{64}", expected_sha or ""),
            "HTTPS API requires --api-ca and its explicit --api-ca-sha256")
    data = Path(ca_path).read_bytes()
    require(sha(data) == expected_sha, "Direct API CA differs from its pin")
    # Use the pinned snapshot itself, avoiding a second file read or TLS bypass.
    return ssl.create_default_context(cadata=data.decode("ascii")), expected_sha


def admit_container(pins, compose, container, base):
    """Only the one prepared frontend container may supply HTTP evidence."""
    require(compose["name"] == "rct-pr3-nginx-gate" and set(compose["services"]) == {"edge"}, "Unexpected prepared namespace")
    service = compose["services"]["edge"]
    runtime_user = pins["runtimeUser"]
    tmpfs = runtime_tmpfs(runtime_user)
    require(container["user"] == service["user"] == runtime_user and not container["groupAdd"],
            "Runtime UID:GID or supplementary groups differ")
    expected_ports = ["127.0.0.1:" + str(port) + ":" + str(port) for port in (TLS_STATIC_PORT, HTTP_AUTH_PORT)]
    require(service["ports"] == expected_ports, "Expected exactly the reviewed loopback TLS14443/HTTP14240 publications")
    port = str(TLS_STATIC_PORT)
    requested = origin(base)
    require(requested.scheme == "https" and requested.port == int(port), "Requested origin differs from prepared publication")
    require(container["project"] == compose["name"] and container["service"] == "edge" and
            container["nonce"] == pins["runNonce"] == service["labels"][RUN_LABEL], "Container ownership labels differ")
    require(container["running"] and container["image"] == pins["imageId"] == service["image"], "Owned Nginx image differs from its pin")
    require(container["entrypoint"] == service["entrypoint"] == ["nginx"] and
            container["command"] == service["command"] == ["-g", "daemon off;"], "Unexpected Nginx entrypoint")
    network = compose["networks"]["main"]["name"]
    if LOCAL_RELEASE_PROFILE:
        require(network == LOCAL_RELEASE_PROFILE["network"] and container["networks"][network]["IPAddress"] == LOCAL_RELEASE_PROFILE["ipv4"], "Common edge exact own IP/network changed")
    require(service["networks"] == ({"main":{"ipv4_address":LOCAL_RELEASE_PROFILE["ipv4"]}} if LOCAL_RELEASE_PROFILE else ["main"]) and set(container["networks"]) == {network} and
            container["networkMode"] == network, "Container is outside the selected existing network")
    publication = {str(value) + "/tcp": [{"HostIp": "127.0.0.1", "HostPort": str(value)}]
                   for value in (TLS_STATIC_PORT, HTTP_AUTH_PORT)}
    require({key: value for key, value in container["ports"].items() if value} == publication,
            "Container publishes a foreign port or interface")
    require(container["readonly"] and not container["privileged"] and container["memory"] == 128 * 1024**2 and
            container["memorySwap"] == 128 * 1024**2 and container["nanoCpus"] == 500_000_000 and
            container["pids"] == 32, "Container resource/root bounds differ")
    require(container["tmpfs"] == tmpfs, "Container tmpfs bounds differ")
    require(not container["capAdd"] and not service.get("cap_add") and
            container["capDrop"] == ["ALL"] and container["securityOpt"] == ["no-new-privileges:true"],
            "Container capabilities/security options differ")
    expected = {item["target"]: item for item in service["volumes"]}
    binds = [item for item in container["mounts"] if item["Type"] == "bind"]
    require(len(binds) == len(expected) and {item["Destination"] for item in binds} == set(expected),
            "Unexpected additional or missing bind mount")
    require(all(item["Source"] == expected[item["Destination"]]["source"] and not item["RW"]
                for item in binds), "Prepared bind paths or read-only flags differ")
    # Engines may report HostConfig tmpfs mounts separately or also in Mounts.
    require(all(item["Type"] == "bind" or (item["Type"] == "tmpfs" and item["Destination"] in tmpfs)
                for item in container["mounts"]), "Unexpected non-bind mount")


def check(args):
    pins = json.loads((Path(args.run_dir) / "pins.json").read_text())
    checkout(args.expected_sha)
    require(pins["sourceSha"] == args.expected_sha, "Prepared source pin differs")
    budget(args.run_dir)
    require("compose.json" in pins["configs"], "Prepared Compose has no source receipt hash")
    for relative, digest in pins["configs"].items():
        require(sha((Path(args.run_dir) / relative).read_bytes()) == digest, "Prepared Nginx bytes changed")
    api_context, api_ca_sha = direct_api_trust(args.api_base, args.api_ca, args.api_ca_sha256)
    docker_env = {key: value for key, value in os.environ.items()
                  if key not in {"DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"}}
    template = ('{"image":{{json .Image}},"running":{{json .State.Running}},'
                '"mounts":{{json .Mounts}},"entrypoint":{{json .Config.Entrypoint}},"command":{{json .Config.Cmd}},'
                '"user":{{json .Config.User}},"groupAdd":{{json .HostConfig.GroupAdd}},'
                '"project":{{json (index .Config.Labels "com.docker.compose.project")}},'
                '"service":{{json (index .Config.Labels "com.docker.compose.service")}},'
                '"nonce":{{json (index .Config.Labels "rct.pr3-nginx.run")}},'
                '"networks":{{json .NetworkSettings.Networks}},"ports":{{json .NetworkSettings.Ports}},'
                '"networkMode":{{json .HostConfig.NetworkMode}},"readonly":{{json .HostConfig.ReadonlyRootfs}},'
                '"memory":{{json .HostConfig.Memory}},"memorySwap":{{json .HostConfig.MemorySwap}},'
                '"nanoCpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},'
                '"tmpfs":{{json .HostConfig.Tmpfs}},"privileged":{{json .HostConfig.Privileged}},'
                '"capAdd":{{json .HostConfig.CapAdd}},"capDrop":{{json .HostConfig.CapDrop}},'
                '"securityOpt":{{json .HostConfig.SecurityOpt}}}')
    container = json.loads(subprocess.check_output(["docker", "--host=unix:///var/run/docker.sock", "inspect", "--format",
                                                   template, "rct-pr3-nginx-gate-edge-1"], env=docker_env, timeout=10))
    compose = json.loads((Path(args.run_dir) / "compose.json").read_text())
    admit_container(pins, compose, container, args.base)
    context = ssl.create_default_context(cafile=str(Path(args.run_dir) / "tls/fullchain.pem"))
    oracle = served_oracle()
    results = {"sourceSha": args.expected_sha, "artifactBytes": {}, "commonNginxStatic": "FAIL",
               "directApiCaSha256": api_ca_sha, "forwardedExternalHttpsIdentity": "NOT_RUN_OWNER_LOCAL",
               "apiForwarding": "NOT_RUN", "realtimeAuthenticatedTransport": "NOT_RUN",
               "webAuthLifecycle": "NOT_RUN", "pwaAuthAndUpdates": "NOT_RUN",
               "httpAuthRealtime": "NOT_RUN", "tlsAuthWss": "NOT_RUN_OWNER_LOCAL",
               "tmaPostLogin": "NOT_RUN", "webPushProvider": "NOT_RUN", "productionTLSAndDeployment": "NOT_RUN"}
    for app, prefix in BASES.items():
        dist = Path(pins["dists"][app])
        require(inventory(dist) == pins["apps"][app]["files"], "Pinned artifact bytes changed")
        require(artifact_contract(app, dist) == pins["apps"][app]["artifactContract"], "Pinned artifact producer contract changed")
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
            mime = file_headers.get("content-type", "").split(";")[0].strip().lower()
            safe_mime = mime if re.fullmatch(r"[a-z0-9.+-]+/[a-z0-9.+-]+", mime) else "missing-or-invalid"
            if relative.endswith((".js", ".mjs")):
                require(mime in {"application/javascript", "text/javascript"},
                        "Script MIME: " + app + "/" + relative + " [" + safe_mime + "]")
            if relative.startswith("assets/") and relative.endswith(".mjs"):
                require("immutable" in file_headers.get("cache-control", ""), "Module worker cache: " + app + "/" + relative)
            if relative.endswith(".css"):
                require(mime == "text/css", "CSS MIME: " + app + "/" + relative + " [" + safe_mime + "]")
        require("no-store" in headers.get("cache-control", ""), "Shell cache policy: " + app)
        require("immutable" in asset_headers.get("cache-control", ""), "Asset cache policy: " + app)
        require("default-src 'self'" in headers.get("content-security-policy", ""), "Missing common-entry CSP")
        for deep in {"web": ["/student/home", "/teacher/home", "/admin/dashboard", "/headman"],
                     "pwa": ["/app/pr3-owned-deep-link"], "tma": ["/mini-app/pr3-owned-deep-link"]}[app]:
            for _ in range(2):
                deep_status, deep_headers, deep_body = fetch(args.base, deep, context)
                require(deep_status == 200 and deep_body == expected_index and
                        "no-store" in deep_headers.get("cache-control", ""), "Deep link/refresh: " + app)
        for extension in ("js", "mjs"):
            missing, _, missing_body = fetch(args.base, prefix + "assets/pr3-owned-missing." + extension, context)
            require(missing == 404 and missing_body != expected_index, "Missing asset becomes a SPA shell: " + app + "/" + extension)
        results["artifactBytes"][app] = "PASS"
    root_status, root_headers, _ = fetch(args.base, "/", context)
    redirected = urlsplit(urljoin(args.base, root_headers.get("location", "")))
    require(root_status == 301 and redirected.path == "/login" and
            (redirected.scheme, redirected.netloc) == (urlsplit(args.base).scheme, urlsplit(args.base).netloc),
            "Common root redirect must remain on the owned origin")
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
            direct_status, direct_headers, _ = fetch(args.api_base, path, api_context)
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


def apply_local_release_profile(path):
    global TLS_STATIC_PORT, HTTP_AUTH_PORT, LOCAL_RELEASE_PROFILE
    value=json.loads(Path(path).read_text())
    require(set(value)=={'project','network','ipv4','httpPort','httpsPort','nonce'},'Closed own common profile required')
    require(re.fullmatch('[a-f0-9]{24}',value['nonce']), 'Fresh own nonce required')
    require(value['project']=='rct-pr3-common-'+value['nonce'][:12] and value['network']=='rct-pr3-native-'+value['nonce'][:12]+'_private_net','Exact new realm/project binding required')
    require(value['ipv4']=='172.31.36.11' and value['httpPort']==15440 and value['httpsPort']==15443,'Exact own proxy identity/ports required')
    LOCAL_RELEASE_PROFILE=value;HTTP_AUTH_PORT=value['httpPort'];TLS_STATIC_PORT=value['httpsPort']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--local-release-profile")
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("pin", help="Record explicitly accepted external build evidence, without building")
    for name in ["expected-sha", "source-sha", "dist", "build-log", "out"]:
        record.add_argument("--" + name, required=True)
    record.add_argument("--app", choices=APPS, required=True)
    record.add_argument("--fixture-mode", choices=["false"], required=True,
                        help="Explicit accepted producer assertion; pin never infers this flag from arbitrary bytes")
    record.set_defaults(action=pin)
    prepare_parser = commands.add_parser("prepare", help="Write isolated configuration; never start containers")
    for name in ["expected-sha", "image-id", "gateway", "network", "landing-dist", "out"]:
        prepare_parser.add_argument("--" + name, required=True)
    for app in APPS:
        prepare_parser.add_argument("--" + app + "-dist", required=True)
        prepare_parser.add_argument("--" + app + "-pin", required=True)
    prepare_parser.add_argument("--port", type=int, choices=[TLS_STATIC_PORT], default=TLS_STATIC_PORT)
    prepare_parser.set_defaults(action=prepare)
    check_parser = commands.add_parser("check", help="Read only the owned local common Nginx and optional real API gateway")
    for name in ["expected-sha", "run-dir", "base", "out"]:
        check_parser.add_argument("--" + name, required=True)
    check_parser.add_argument("--api-base")
    check_parser.add_argument("--api-ca", help="Read-only public CA certificate for the separate direct HTTPS API")
    check_parser.add_argument("--api-ca-sha256", help="Expected SHA256 of the direct API CA bytes")
    check_parser.set_defaults(action=check)
    args = parser.parse_args()
    if args.local_release_profile:
        apply_local_release_profile(args.local_release_profile)
        if hasattr(args,"port"): args.port=TLS_STATIC_PORT
    args.action(args)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(json.dumps({"state": "FAIL", "reason": str(error)}))
        raise SystemExit(1)
