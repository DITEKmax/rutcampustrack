#!/usr/bin/env python3
"""One synthetic native Redis/Rabbit checkpoint drill; default is plan only.

Uses accepted recovery.py stdlib guards/runner/hash/immutable JSON helpers.
No application/outbox replay, external delivery, source/volume deletion or PG/Mongo restore.
Cold tar includes a generated synthetic cookie: runtime/ is ignored and never printed.
"""
import argparse
import importlib.util
import io
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
spec = importlib.util.spec_from_file_location("accepted_recovery", ROOT / "scripts/recovery.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
BASE = "rct-recovery-redis-rabbit-20261002-r3"
HOST = "rct-recovery-rabbit-1002"
NODE = "rabbit@" + HOST
IMAGES = {
    "redis": "redis:7-alpine@sha256:7aec734b2bb298a1d769fd8729f13b8514a41bf90fcdd1f38ec52267fbaa8ee6",
    "rabbit": "rabbitmq:3.13-management-alpine@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281",
}
QUEUE = "rct.recovery.1002.q"
EXCHANGE = "rct.recovery.1002"


def native(container, *command):
    # docker exec bypasses the image entrypoint's su-exec; root CLI can race
    # broker initialization by creating a root-only cookie. Use the pinned
    # image's verified rabbitmq uid/gid for every Rabbit CLI, including readiness.
    user = ["--user", "100:101"] if container.endswith("-rabbit") else []
    return r.run(["docker", "exec", *user, container, *command]).decode("utf-8").strip()


def inspect(kind, name):
    # Deliberately never inspect Config.Env, cookies or secret file contents.
    template = '{{json .Labels}}' if kind == "volume" else '{{json .Config.Labels}}'
    return json.loads(r.run(["docker", kind, "inspect", "--format", template, name]))


def wait_for(callback, description, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = callback()
            if result:
                return result
        except r.RecoveryError:
            pass
        time.sleep(1)
    raise r.RecoveryError("Timed out: " + description)


def http(container, method, path, body=None):
    # Use the broker's native OTP HTTP client on loopback only; no host ports/tools/install.
    # guest/guest is this isolated image's synthetic default, never a production credential.
    url = json.dumps("http://127.0.0.1:15672/api/" + path)
    headers = '[{"authorization","Basic Z3Vlc3Q6Z3Vlc3Q="}]'
    request = "{" + url + "," + headers
    if body is not None:
        request += ',"application/json",' + json.dumps(json.dumps(body, separators=(",", ":")))
    request += "}"
    expression = (
        'application:ensure_all_started(inets), '
        '{ok,{{_,Code,_},_,Body}}=httpc:request(' + method.lower() + ',' + request +
        ',[],[{body_format,binary}]), '
        'io:format("RCTL_STATUS ~B~nRCTL_BODY ~s~n",[Code,Body]), ok.'
    )
    output = native(container, "rabbitmqctl", "-q", "eval", expression)
    status = re.search(r"^RCTL_STATUS (\d+)$", output, re.M)
    payload = re.search(r"^RCTL_BODY (.*)$", output, re.M)
    r.require(status and payload, "Missing native HTTP response framing")
    r.require(200 <= int(status.group(1)) < 300, "Native management HTTP failed: " + status.group(1))
    return json.loads(payload.group(1)) if payload.group(1) else None


def queue_state(container):
    output = native(container, "rabbitmqctl", "-q", "list_queues", "-p", "/",
                    "name", "durable", "messages_ready", "messages_unacknowledged")
    found = [line.split() for line in output.splitlines() if line.startswith(QUEUE + "\t")]
    r.require(len(found) == 1 and len(found[0]) == 4, "Exact synthetic queue missing")
    row = found[0]
    r.require(row[1] == "true", "Synthetic queue is not durable")
    return {"ready": int(row[2]), "unacked": int(row[3])}


def validate_tar(path, redis=False):
    with tarfile.open(path, "r") as archive:
        members = archive.getmembers()
        for member in members:
            parsed = PurePosixPath(member.name)
            r.require(not parsed.is_absolute() and ".." not in parsed.parts, "Unsafe archive path")
            r.require(member.isfile() or member.isdir(), "Archive link/special file refused")
        if redis:
            r.require([m.name for m in members if m.isfile()] == ["dump.rdb"], "Unexpected RDB archive")
        else:
            r.require(any(m.name.endswith(".erlang.cookie") for m in members), "Cold cookie identity missing")
            r.require(any(NODE in m.name for m in members), "Cold node identity missing")


def execute():
    runtime = (HERE / "runtime-r3").resolve()
    r.require(runtime.is_relative_to(ROOT.resolve()), "Runtime target outside assigned workspace")
    r.require(not runtime.exists(), "Runtime already exists; no overwrite/resume/deletion")
    names = {(side, store): BASE + "-" + side + "-" + store
             for side in ("src", "dst") for store in IMAGES}
    volumes = {key: name + "-data" for key, name in names.items()}
    # Global discovery is names only, never env/data; exact collisions fail before writes.
    containers = set(r.run(["docker", "ps", "-a", "--format", "{{.Names}}"]).decode().splitlines())
    existing_volumes = set(r.run(["docker", "volume", "ls", "--format", "{{.Name}}"]).decode().splitlines())
    r.require(not (set(names.values()) & containers), "Container name already exists")
    r.require(not (set(volumes.values()) & existing_volumes), "Volume name already exists")
    images = {store: json.loads(r.run(["docker", "image", "inspect", "--format",
                    '{"id":{{json .Id}},"digests":{{json .RepoDigests}}}', image]))
              for store, image in IMAGES.items()}
    runtime.mkdir()
    token = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    created = {}
    labels = {key: {"io.rutcampustrack.recovery": "disposable",
                   "com.docker.compose.project": BASE + "-" + key[0],
                   "io.rutcampustrack.recovery.token": token}
              for key in names}
    r.write_json(runtime / "ownership.json", {"containers": list(names.values()), "volumes": list(volumes.values()),
                                              "images": images, "token": token, "eventId": event_id})

    def owned(key):
        for kind, name in (("container", names[key]), ("volume", volumes[key])):
            actual = inspect(kind, name)
            r.require(all(actual.get(label) == value for label, value in labels[key].items()), "Foreign resource label mismatch")
        actual_id = r.run(["docker", "inspect", "--format", "{{.Id}}", names[key]]).decode().strip()
        r.require(actual_id == created[key], "Container identity changed")
        mount = json.loads(r.run(["docker", "inspect", "--format", "{{json .Mounts}}", names[key]]))
        r.require(len(mount) == 1 and mount[0]["Name"] == volumes[key], "Foreign/unexpected mount")
        expected_path = "/data" if key[1] == "redis" else "/var/lib/rabbitmq"
        r.require(mount[0]["Destination"] == expected_path, "Unexpected data destination")
        network = r.run(["docker", "inspect", "--format", "{{.HostConfig.NetworkMode}}", names[key]]).decode().strip()
        ports = json.loads(r.run(["docker", "inspect", "--format", "{{json .HostConfig.PortBindings}}", names[key]]))
        r.require(network == "none" and not ports, "External network/host port refused")

    def stop(key):
        owned(key)
        r.run(["docker", "stop", "--time", "60", names[key]])
        running = r.run(["docker", "inspect", "--format", "{{.State.Running}}", names[key]]).decode().strip()
        r.require(running == "false", "Own container did not stop")

    def start(side):
        for store in IMAGES:
            key = (side, store)
            owned(key)
            r.run(["docker", "start", names[key]])
        wait_for(lambda: native(names[side, "redis"], "redis-cli", "PING") == "PONG", "Redis startup")
        wait_for(lambda: native(names[side, "rabbit"], "rabbitmq-diagnostics", "-q", "ping"), "Rabbit startup")
        wait_for(lambda: http(names[side, "rabbit"], "GET", "overview"), "Rabbit management startup")
        node = native(names[side, "rabbit"], "rabbitmqctl", "-q", "eval", "node().")
        r.require(node.strip("'") == NODE, "Rabbit node identity mismatch")

    result = "FAIL"
    try:
        for key in names:
            label_args = [arg for pair in labels[key].items() for arg in ("--label", "=".join(pair))]
            r.run(["docker", "volume", "create", *label_args, volumes[key]])
            r.require(inspect("volume", volumes[key]) == labels[key], "Volume creation collision")
            command = ["docker", "create", "--pull", "never", "--name", names[key], *label_args,
                       "--network", "none", "--cpus", "0.5", "--memory", "512m" if key[1] == "rabbit" else "128m",
                       "--mount", "type=volume,source=" + volumes[key] + ",target=" + ("/data" if key[1] == "redis" else "/var/lib/rabbitmq")]
            if key[1] == "rabbit":
                command += ["--hostname", HOST, "--add-host", HOST + ":127.0.0.1"]
            command += [IMAGES[key[1]]]
            if key[1] == "redis":
                command += ["redis-server", "--appendonly", "no", "--maxmemory", "96mb", "--maxmemory-policy", "allkeys-lru"]
            created[key] = r.run(command).decode().strip()
            owned(key)
        r.write_json(runtime / "created.json", {names[key]: value for key, value in created.items()})
        start("src")
        src_redis, src_rabbit = names["src", "redis"], names["src", "rabbit"]
        choice_key, ttl_key = "recovery:probe:choice:" + event_id, "recovery:probe:ttl:" + event_id
        native(src_redis, "redis-cli", "SET", choice_key, "false")
        native(src_redis, "redis-cli", "SET", ttl_key, "synthetic-ephemeral", "PX", "600000")
        expires_at = int(native(src_redis, "redis-cli", "PEXPIRETIME", ttl_key))
        http(src_rabbit, "PUT", "exchanges/%2F/" + EXCHANGE, {"type": "direct", "durable": True})
        http(src_rabbit, "PUT", "queues/%2F/" + QUEUE, {"durable": True, "auto_delete": False, "arguments": {}})
        http(src_rabbit, "POST", "bindings/%2F/e/" + EXCHANGE + "/q/" + QUEUE, {"routing_key": "probe", "arguments": {}})
        payload = json.dumps({"event_id": event_id, "type": "recovery.synthetic", "value": "probe-only"})
        publish = http(src_rabbit, "POST", "exchanges/%2F/" + EXCHANGE + "/publish",
                       {"properties": {"delivery_mode": 2, "message_id": event_id, "content_type": "application/json"},
                        "routing_key": "probe", "payload": payload, "payload_encoding": "string"})
        r.require(publish["routed"] is True and queue_state(src_rabbit) == {"ready": 1, "unacked": 0}, "Source message not durable/ready")
        cookie_before = native(src_rabbit, "sha256sum", "/var/lib/rabbitmq/.erlang.cookie").split()[0]
        lastsave = int(native(src_redis, "redis-cli", "LASTSAVE"))
        while int(time.time()) <= lastsave:
            time.sleep(0.1)
        native(src_redis, "redis-cli", "BGSAVE")
        wait_for(lambda: "rdb_bgsave_in_progress:0" in native(src_redis, "redis-cli", "INFO", "persistence")
                 and "rdb_last_bgsave_status:ok" in native(src_redis, "redis-cli", "INFO", "persistence")
                 and int(native(src_redis, "redis-cli", "LASTSAVE")) > lastsave, "Completed new RDB")
        rdb = runtime / "redis-rdb.tar"
        with rdb.open("xb") as stream:
            r.run(["docker", "cp", "--archive", src_redis + ":/data/dump.rdb", "-"], stdout=stream)
        stop(("src", "redis"))
        stop(("src", "rabbit"))
        cold = runtime / "rabbit-cold.tar"
        with cold.open("xb") as stream:
            r.run(["docker", "cp", "--archive", src_rabbit + ":/var/lib/rabbitmq/.", "-"], stdout=stream)
        validate_tar(rdb, redis=True)
        validate_tar(cold)
        hashes = {"redis-rdb.tar": r.digest(rdb), "rabbit-cold.tar": r.digest(cold)}
        r.write_json(runtime / "checkpoint.json", {"hashes": hashes, "node": NODE, "eventId": event_id,
                                                  "syntheticTtlExpiresAt": expires_at, "sourceStopped": True})
        for store, archive in (("redis", rdb), ("rabbit", cold)):
            key = ("dst", store)
            owned(key)
            destination = "/data" if store == "redis" else "/var/lib/rabbitmq"
            empty = r.run(["docker", "cp", names[key] + ":" + destination + "/.", "-"])
            with tarfile.open(fileobj=io.BytesIO(empty)) as fresh:
                r.require(all(m.isdir() for m in fresh.getmembers()), "Nonempty fresh target refused")
            r.require(r.digest(archive) == hashes[archive.name], "Checkpoint integrity changed")
            with archive.open("rb") as stream:
                r.run(["docker", "cp", "--archive", "-", names[key] + ":" + destination], stdin=stream)
        start("dst")
        dst_redis, dst_rabbit = names["dst", "redis"], names["dst", "rabbit"]
        r.require(native(dst_redis, "redis-cli", "GET", choice_key) == "false", "Synthetic native Redis state lost")
        restored_expiry = int(native(dst_redis, "redis-cli", "PEXPIRETIME", ttl_key))
        now_ms = int(time.time() * 1000)
        r.require(restored_expiry == expires_at if now_ms < expires_at else restored_expiry == -2, "Synthetic TTL reset/resurrection")
        r.require(native(dst_rabbit, "sha256sum", "/var/lib/rabbitmq/.erlang.cookie").split()[0] == cookie_before, "Cookie identity changed")
        r.require(queue_state(dst_rabbit) == {"ready": 1, "unacked": 0}, "Cold message inventory changed")
        deliveries = []
        for ackmode in ("ack_requeue_true", "ack_requeue_true", "ack_requeue_false"):
            messages = http(dst_rabbit, "POST", "queues/%2F/" + QUEUE + "/get",
                            {"count": 1, "ackmode": ackmode, "encoding": "auto", "truncate": 50000})
            r.require(len(messages) == 1, "Expected one persistent synthetic message")
            message = messages[0]
            r.require(message["properties"]["delivery_mode"] == 2 and message["properties"]["message_id"] == event_id
                      and message["payload"] == payload, "Persistent event identity/content changed")
            deliveries.append(message["redelivered"])
        # Recovery itself may conservatively mark a message redelivered; the explicit
        # first requeue must make subsequent same-event deliveries redelivered.
        r.require(all(isinstance(value, bool) for value in deliveries) and deliveries[1:] == [True, True], "Native redelivery/ack not proven")
        wait_for(lambda: queue_state(dst_rabbit) == {"ready": 0, "unacked": 0}, "Native ack drained exact queue")
        r.require(all(r.digest(runtime / name) == digest for name, digest in hashes.items()), "Checkpoint overwritten")
        r.write_json(runtime / "PASS.json", {"result": "PASS", "eventId": event_id, "redelivery": deliveries,
                     "nativeAck": True, "nativeRedisProbePreserved": True, "ttlNotReset": True, "sameNodeCookie": True,
                     "limits": "not current Mongo preference authority/app replay/provider/offsite/RPO/RTO"})
        result = "PASS"
    finally:
        # No removal: own source/checkpoints/volumes remain for comparison, always stop our processes.
        stopped = []
        cleanup_errors = []
        for key in created:
            try:
                stop(key)
                stopped.append(names[key])
            except Exception as error:
                # Continue every other owned stop; never serialize subprocess/native diagnostics.
                cleanup_errors.append({"container": names[key], "errorType": type(error).__name__})
        r.write_json(runtime / "terminal.json", {"result": "FAIL" if cleanup_errors else result,
                     "criteriaResult": result, "stopped": stopped, "cleanupErrors": cleanup_errors,
                     "containersVolumesCheckpointsRetained": True,
                     "cleanup": "no deletion; all own stop attempts completed"})
        r.require(not cleanup_errors, "Cleanup failed for own containers; see terminal.json")
    print("PASS native RDB/cold broker restore; exact persistent event redelivery/ack; synthetic Redis probe/TTL preserved; own processes stopped")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Root-reviewed explicit execution; needs root Docker lease")
    args = parser.parse_args()
    if not args.execute:
        print(json.dumps({"mode": "PLAN ONLY, no Docker/files mutation", "base": BASE, "maxActiveContainers": 2,
                          "images": IMAGES, "node": NODE, "network": "none", "ports": [],
                          "runtime": str(HERE / "runtime-r3"), "source": "retained stopped; no data deletion"}, indent=2))
        return
    try:
        execute()
    except (r.RecoveryError, OSError, ValueError, KeyError, tarfile.TarError) as error:
        # Never print subprocess diagnostics, cookie/archive contents or arbitrary native response bodies.
        reason = str(error) if isinstance(error, r.RecoveryError) else type(error).__name__
        print("FAIL native rehearsal: " + reason)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
