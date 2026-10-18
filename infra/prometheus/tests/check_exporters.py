"""Isolated CI datastore/exporter contracts; never targets an existing stack or production.

Run only on the normal CI VM with RCT_MONITORING_RUNTIME=isolated-ci.
Pinned images/configuration are read from docker-compose.prod.yml, credentials are
synthetic, and only the unique generated Compose project is removed on exit.
"""

import asyncio
import base64
import copy
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid
from datetime import UTC, datetime
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
_STATE = {"phase": "PRECHECK_OR_CONFIG", "cleanup": "NOT_STARTED"}


def mongo_monitor_witness(probe):
    """Closed projection only; shell authentication happens before --eval."""
    witness = {
        "probe_exit": probe.returncode if -128 <= probe.returncode <= 255 else None,
        "authenticated": False,
        "diagnostic_ok": False,
        "category": "UNKNOWN",
        "observed_code": None,
    }
    if probe.returncode == 0:
        try:
            data = json.loads(probe.stdout)
        except (ValueError, TypeError):
            return witness
        if not isinstance(data, dict) or set(data) != {"authenticated", "diagnostic_ok", "observed_code"}:
            return witness
        if type(data["authenticated"]) is not bool or type(data["diagnostic_ok"]) is not bool:
            return witness
        if data["observed_code"] is not None and data["observed_code"] not in {13, 18}:
            return witness
        witness.update(data)
        witness["category"] = "READY" if data["authenticated"] and data["diagnostic_ok"] else "DIAGNOSTIC_DENIED"
    elif "MongoServerError: Authentication failed." in probe.stderr:
        # A named CLI category is not an observed numeric Mongo code18.
        witness["category"] = "AUTHENTICATION_FAILED"
    return witness


def redis_probe_witness(probe):
    """Project an existing synthetic CLI response to fixed categories only."""
    lines = (probe.stdout + "\n" + probe.stderr).splitlines()
    category = "UNKNOWN"
    for candidate in ["WRONGPASS", "NOAUTH", "NOPERM", "ERR"]:
        prefixes = (candidate + " ", "(error) " + candidate + " ", "AUTH failed: " + candidate + " ")
        if any(line.startswith(prefixes) for line in lines):
            category = candidate
            break
    else:
        if probe.stdout.strip() in {"PONG", "OK"}:
            category = probe.stdout.strip()
    code = probe.returncode
    return {
        "probe_exit": code if type(code) is int and -128 <= code <= 255 else None,
        "category": category,
    }


async def rabbit_metrics_contract(docker):
    """Actual AMQP acknowledgement states and production plugin→Prometheus scrape."""
    import aio_pika

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    prom_address = docker("port", "rabbit-prometheus", "9090").stdout.strip()
    amqp_address = docker("port", "rabbitmq", "5672").stdout.strip()

    def query(expression):
        url = "http://" + prom_address + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
        with opener.open(url, timeout=5) as response:
            data = json.load(response)
        assert data["status"] == "success"
        return data["data"]["result"]

    async def observed(expression, predicate):
        end = time.monotonic() + 45
        while time.monotonic() < end:
            try:
                rows = await asyncio.to_thread(query, expression)
                if len(rows) == 1 and predicate(float(rows[0]["value"][1])):
                    return float(rows[0]["value"][1])
            except (OSError, ValueError):
                pass
            await asyncio.sleep(1)
        raise AssertionError("actual RabbitMQ metric state not observed: " + expression.split("{")[0])

    end = time.monotonic() + 90
    while True:
        try:
            connection = await aio_pika.connect(
                "amqp://synthetic-monitor:synthetic-rabbit-only@" + amqp_address + "/", timeout=5
            )
            break
        except (TimeoutError, OSError, aio_pika.exceptions.AMQPConnectionError):
            assert time.monotonic() < end, "isolated RabbitMQ AMQP readiness timeout"
            await asyncio.sleep(1)
    async with connection:
        channel = await connection.channel(publisher_confirms=True)
        await channel.set_qos(prefetch_count=1)
        dlq = await channel.declare_queue("rct.metrics.dlq", durable=True)
        queue = await channel.declare_queue(
            "rct.metrics.main",
            durable=True,
            arguments={"x-dead-letter-exchange": "", "x-dead-letter-routing-key": dlq.name},
        )
        published = int(time.time()) - 5
        for _ in range(2):
            await channel.default_exchange.publish(
                aio_pika.Message(
                    body=b"synthetic-private-message",
                    timestamp=datetime.fromtimestamp(published, UTC),
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                ),
                routing_key=queue.name,
            )
        ready = 'rabbitmq_detailed_queue_messages_ready{job="rabbitmq-queues",queue="rct.metrics.main"}'
        unacked = 'rabbitmq_detailed_queue_messages_unacked{job="rabbitmq-queues",queue="rct.metrics.main"}'
        dead = 'rabbitmq_detailed_queue_messages_ready{job="rabbitmq-queues",queue="rct.metrics.dlq"}'
        await observed('up{job="rabbitmq"}', lambda value: value == 1)
        await observed('up{job="rabbitmq-queues"}', lambda value: value == 1)
        await observed(ready, lambda value: value == 2)
        await observed(unacked, lambda value: value == 0)
        message = await queue.get(timeout=5, no_ack=False)
        await observed(ready, lambda value: value == 1)
        await observed(unacked, lambda value: value == 1)
        await message.reject(requeue=False)
        await observed(unacked, lambda value: value == 0)
        await observed(dead, lambda value: value == 1)
        head = await observed(
            'rabbitmq_detailed_queue_head_message_timestamp{job="rabbitmq-queues",queue="rct.metrics.dlq"}',
            lambda value: value == published,
        )
        version = await observed(
            'count(rabbitmq_build_info{job="rabbitmq",rabbitmq_version=~"3[.]13[.].+"})', lambda value: value == 1
        )
        assert version == 1 and head > 0
        return {
            "ready_before": 2,
            "ready_held": 1,
            "unacked_held": 1,
            "unacked_after_reject": 0,
            "dlq_ready": 1,
            "head_timestamp_unit": "seconds",
            "head_matches_publisher_timestamp": True,
            "qualification": "Synthetic AMQP publish/get/reject; real plugin families and Prom scrape. Publisher age is not DLQ residence; no application zero-loss claim.",
        }


def main():
    assert os.environ.get("RCT_MONITORING_RUNTIME") == "isolated-ci", "dedicated CI runtime required"
    assert shutil.disk_usage("/tmp").free >= 4 * 1024**3, "4GiB disk floor required"
    production = yaml.safe_load((ROOT / "docker-compose.prod.yml").read_text())
    project = "rct-obsv-" + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix=project + "-") as tmp:
        td = Path(tmp)
        config = {"services": {}, "networks": {"private_net": {}}, "volumes": {"mongo-test": {}}, "secrets": {}}
        for name, db in [("academic", "academic_db"), ("schedule", "schedule_db")]:
            pg_service = copy.deepcopy(production["services"]["postgres-" + name])
            for key in ["container_name", "healthcheck", "depends_on"]:
                pg_service.pop(key, None)
            pg_service["volumes"] = [
                str(ROOT / "infra/postgres/init-roles.sh") + ":/docker-entrypoint-initdb.d/10-rct-roles.sh:ro"
            ]
            for secret in pg_service["secrets"]:
                f = td / secret["source"]
                role = secret["target"].replace("rct-", "").replace("-password", "")
                f.write_text("synthetic-" + role + "-only")
                config["secrets"][secret["source"]] = {"file": str(f)}
            config["services"]["postgres-" + name] = pg_service
            sql = td / (name + "-sql")
            sql.mkdir()
            application_table = "users" if name == "academic" else "lessons"
            (sql / "V1__synthetic_application.sql").write_text(
                "CREATE EXTENSION IF NOT EXISTS btree_gist; CREATE EXTENSION IF NOT EXISTS pgcrypto; CREATE TABLE "
                + application_table
                + "(id bigserial PRIMARY KEY,value text); CREATE TABLE credential_fixture(value text); INSERT INTO credential_fixture VALUES ('synthetic-private-marker');"
            )
            migration = td / (name + "-flyway.conf")
            migration.write_text(
                "flyway.url=jdbc:postgresql://postgres-"
                + name
                + ":5432/"
                + db
                + "\nflyway.user=rct_migrator\nflyway.password=synthetic-migration-only\nflyway.locations=filesystem:/flyway/sql,filesystem:/flyway/callbacks\n"
            )
            config["services"]["migrate-" + name] = {
                "image": "redgate/flyway:11.7.2@sha256:b40d022ed6728a400eb46b84e1dd12d7468cdf63a4b83e6094a1768165fc67c6",
                "profiles": ["migration-contract"],
                "networks": ["private_net"],
                "mem_limit": "256m",
                "cpus": 0.5,
                "environment": {"JAVA_ARGS": "-Xmx128m -XX:MaxMetaspaceSize=64m"},
                "volumes": [
                    str(sql) + ":/flyway/sql:ro",
                    str(ROOT / "infra/postgres/flyway-callbacks") + ":/flyway/callbacks:ro",
                    str(migration) + ":/flyway/conf/flyway.conf:ro",
                ],
            }
        mongo = copy.deepcopy(production["services"]["mongo-attendance"])
        mongo.update(
            environment={
                "MONGODB_ROOT_USER": "root",
                "MONGODB_ROOT_PASSWORD": "synthetic-root-only",
                "MONGODB_REPLICA_SET_MODE": "primary",
                "MONGODB_REPLICA_SET_NAME": "rs0",
                "MONGODB_REPLICA_SET_KEY": base64.b64encode(b"synthetic-replica-key-only").decode(),
                "MONGODB_ADVERTISED_HOSTNAME": "mongo-attendance",
                "MONGO_USER": "fixture_attendance",
                "MONGO_PASSWORD": "synthetic-application-only",
                "MONGO_NOTIFICATION_USER": "fixture_notification",
                "MONGO_NOTIFICATION_PASSWORD": "synthetic-notification-only",
            },
            volumes=[
                "mongo-test:/bitnami/mongodb",
                str(ROOT / "infra/mongo/init-mongo.js") + ":/docker-entrypoint-initdb.d/init-mongo.js:ro",
            ],
            mem_limit="512m",
            cpus=1,
        )
        for key in ["container_name", "healthcheck", "depends_on"]:
            mongo.pop(key, None)
        config["services"]["mongo-attendance"] = mongo
        acl = (ROOT / "infra/prometheus/exporters/redis-users.acl.template").read_text()
        acl = acl.replace(
            "__APPLICATION_PASSWORD_SHA256__", hashlib.sha256(b"synthetic-admin-only").hexdigest()
        ).replace("__MONITOR_PASSWORD_SHA256__", hashlib.sha256(b"synthetic-monitor-only").hexdigest())
        (td / "redis.acl").write_text(acl)
        for name, filename in [("redis", "redis.acl"), ("redis-cache", "redis-cache.acl")]:
            service = copy.deepcopy(production["services"][name])
            for key in ["container_name", "healthcheck", "depends_on", "environment", "secrets", "volumes"]:
                service.pop(key, None)
            command = service["command"]
            command = shlex.split(command) if isinstance(command, str) else list(command)
            acl_index = command.index("--aclfile") + 1
            command[acl_index] = "/run/redis/users.acl"
            service.update(command=command, volumes=[str(td / filename) + ":/run/redis/users.acl:ro"])
            config["services"][name] = service
        cache_acl = (ROOT / "infra/prometheus/exporters/redis-cache-users.acl.template").read_text()
        cache_acl = cache_acl.replace(
            "__CACHE_PASSWORD_SHA256__", hashlib.sha256(b"synthetic-cache-only").hexdigest()
        ).replace("__MONITOR_PASSWORD_SHA256__", hashlib.sha256(b"synthetic-cache-monitor-only").hexdigest())
        (td / "redis-cache.acl").write_text(cache_acl)
        exporter_names = [
            "postgres-academic-exporter",
            "postgres-schedule-exporter",
            "mongodb-exporter",
            "redis-exporter",
            "redis-cache-exporter",
        ]
        for name in exporter_names:
            service = copy.deepcopy(production["services"][name])
            service.pop("container_name", None)
            internal = service["expose"][0]
            service["ports"] = [f"127.0.0.1::{internal}"]
            if name == "mongodb-exporter":
                (td / "mongo.env").write_text("MONGODB_PASSWORD=synthetic-mongo-monitor-only-0123456789abcdef\n")
                config["secrets"]["mongo-monitor-env"] = {"file": str(td / "mongo.env")}
                service["env_file"] = [str(td / "mongo.env")]
            for secret in service.get("secrets", []):
                f = td / secret
                if secret not in config["secrets"]:
                    f.write_text(
                        json.dumps({"redis://redis:6379": "synthetic-monitor-only"})
                        if secret == "redis-monitor-password-map"
                        else json.dumps({"redis://redis-cache:6379": "synthetic-cache-monitor-only"})
                        if secret == "redis-cache-monitor-password-map"
                        else "synthetic-monitor-only"
                    )
                    config["secrets"][secret] = {"file": str(f)}
            config["services"][name] = service
        rabbit = copy.deepcopy(production["services"]["rabbitmq"])
        for key in ["container_name", "healthcheck", "depends_on"]:
            rabbit.pop(key, None)
        rabbit.update(
            environment={
                "RABBITMQ_DEFAULT_USER": "synthetic-monitor",
                "RABBITMQ_DEFAULT_PASS": "synthetic-rabbit-only",
                "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS": "+S 2:2",
            },
            volumes=[str(ROOT / "infra/prometheus/rabbitmq-enabled_plugins") + ":/etc/rabbitmq/enabled_plugins:ro"],
            ports=["127.0.0.1::5672"],
            cpus=0.5,
            profiles=["rabbit-metrics-contract"],
        )
        config["services"]["rabbitmq"] = rabbit
        prom = yaml.safe_load((ROOT / "infra/prometheus/prometheus.yml").read_text())
        jobs = [
            copy.deepcopy(job)
            for job in prom["scrape_configs"]
            if job["job_name"] in ["rabbitmq", "rabbitmq-queues", "redis-exporter", "redis-cache-exporter"]
        ]
        assert len(jobs) == 4 and next(job for job in jobs if job["job_name"] == "rabbitmq-queues")["params"][
            "family"
        ] == ["queue_coarse_metrics", "queue_metrics"]
        for job in jobs:
            job.update(scrape_interval="1s", scrape_timeout="1s", sample_limit=10000, body_size_limit="8MB")
            for target in job["static_configs"]:
                target["labels"]["environment"] = "synthetic"
        (td / "rabbit-prometheus.yml").write_text(
            yaml.safe_dump({"global": {"scrape_interval": "1s"}, "scrape_configs": jobs})
        )
        config["services"]["rabbit-prometheus"] = {
            "image": production["services"]["prometheus"]["image"],
            "command": [
                "--config.file=/etc/prometheus/prometheus.yml",
                "--storage.tsdb.path=/prometheus",
                "--storage.tsdb.retention.time=1h",
                "--storage.tsdb.retention.size=32MB",
                "--storage.tsdb.wal-segment-size=16MB",
                "--query.max-samples=500000",
            ],
            "volumes": [str(td / "rabbit-prometheus.yml") + ":/etc/prometheus/prometheus.yml:ro"],
            "networks": ["private_net"],
            "ports": ["127.0.0.1::9090"],
            "mem_limit": "192m",
            "cpus": 0.25,
            "profiles": ["rabbit-metrics-contract"],
            "logging": {"driver": "json-file", "options": {"max-size": "1m", "max-file": "1"}},
        }
        compose = td / "compose.yml"
        compose.write_text(yaml.safe_dump(config, sort_keys=False))
        prefix = ["docker", "compose", "--project-name", project, "-f", str(compose)]

        def docker(*args, check=True, input=None):
            return subprocess.run(prefix + list(args), input=input, text=True, capture_output=True, check=check)

        def execute(service, args, input=None, check=True):
            return docker("exec", "-T", service, *args, input=input, check=check)

        def wait(fn, seconds=120):
            end = time.monotonic() + seconds
            while time.monotonic() < end:
                try:
                    if fn():
                        return
                except (OSError, subprocess.CalledProcessError, ValueError):
                    pass
                time.sleep(1)
            raise AssertionError("CI datastore/exporter did not become ready")

        def metrics(name):
            internal = config["services"][name]["expose"][0]
            address = docker("port", name, internal).stdout.strip()
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
                "http://" + address + "/metrics", timeout=15
            ) as response:
                return response.read().decode()

        started = time.monotonic()
        try:
            _STATE["phase"] = "DATASTORE_START"
            docker("up", "-d")
            for name, db in [("academic", "academic_db"), ("schedule", "schedule_db")]:
                _STATE["phase"] = "POSTGRES_" + name.upper()
                pg = "postgres-" + name
                wait(
                    lambda pg=pg, db=db: (
                        execute(
                            pg,
                            [
                                "psql",
                                "-U",
                                "rct_bootstrap",
                                "-d",
                                db,
                                "-Atc",
                                "SELECT count(*) FROM pg_roles WHERE rolname='rct_migrator'",
                            ],
                            check=False,
                        ).stdout.strip()
                        == "1"
                    )
                )
                bootstrap = execute(
                    pg,
                    [
                        "psql",
                        "-U",
                        "rct_bootstrap",
                        "-d",
                        db,
                        "-Atc",
                        "SELECT rolsuper FROM pg_roles WHERE rolname=current_user",
                    ],
                ).stdout.strip()
                assert bootstrap == "t", "fresh PostgreSQL bootstrap is privileged, never the application identity"
                docker("run", "--rm", "migrate-" + name, "migrate")
                application_table = "users" if name == "academic" else "lessons"
                runtime = ["psql", "-U", "rct_runtime", "-d", db, "-v", "ON_ERROR_STOP=1"]
                flags = execute(
                    pg,
                    runtime
                    + [
                        "-Atc",
                        "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname=current_user",
                    ],
                ).stdout.strip()
                assert flags == "f"
                execute(
                    pg,
                    runtime,
                    input="BEGIN; INSERT INTO "
                    + application_table
                    + "(value) VALUES ('synthetic-allowed'); UPDATE "
                    + application_table
                    + " SET value='synthetic-updated'; SELECT * FROM "
                    + application_table
                    + "; DELETE FROM "
                    + application_table
                    + "; COMMIT;",
                )
                for query in [
                    "CREATE TABLE forbidden_runtime_ddl(id integer)",
                    "DROP TABLE " + application_table,
                    "SELECT * FROM flyway_schema_history",
                    "SELECT passwd FROM pg_shadow",
                    "SELECT * FROM credential_fixture",
                ]:
                    denied = execute(pg, runtime + ["-c", query], check=False)
                    assert denied.returncode != 0 and (
                        "permission denied" in denied.stderr or "must be owner" in denied.stderr
                    ), query
                for query in [
                    "SELECT * FROM credential_fixture",
                    "SELECT * FROM " + application_table,
                    "SELECT passwd FROM pg_shadow",
                ]:
                    denied = execute(
                        pg, ["psql", "-U", "rct_monitor", "-d", db, "-v", "ON_ERROR_STOP=1", "-c", query], check=False
                    )
                    assert denied.returncode != 0 and "permission denied" in denied.stderr
                # Existing non-allowlisted tables and future migration data remain denied.
                execute(
                    pg,
                    ["psql", "-U", "rct_migrator", "-d", db, "-v", "ON_ERROR_STOP=1"],
                    input="CREATE TABLE future_private_fixture(value text);",
                )
                denied = execute(pg, runtime + ["-c", "SELECT * FROM future_private_fixture"], check=False)
                assert denied.returncode != 0 and "permission denied" in denied.stderr
                wait(lambda pg=pg: "pg_up 1" in metrics(pg + "-exporter"))
            _STATE["phase"] = "MONGO_ROOT_READINESS"
            mongo_cli = [
                "mongosh",
                "--quiet",
                "--username",
                "root",
                "--password",
                "synthetic-root-only",
                "--authenticationDatabase",
                "admin",
                "--eval",
            ]

            def mongo_root_ready():
                probe = execute("mongo-attendance", mongo_cli + ["db.adminCommand({ping:1}).ok"], check=False)
                _STATE["probe_returncode"] = probe.returncode
                return probe.returncode == 0

            wait(mongo_root_ready)
            _STATE["phase"] = "MONGO_ROLE_AND_EXPORTER"
            execute(
                "mongo-attendance",
                mongo_cli
                + ['db.getSiblingDB("attendance_db").credential_fixture.insertOne({value:"synthetic-private-marker"})'],
            )

            def mongo_monitor_ready():
                probe = execute(
                    "mongo-attendance",
                    [
                        "mongosh",
                        "--quiet",
                        "--username",
                        "rct_monitor",
                        "--password",
                        "synthetic-mongo-monitor-only-0123456789abcdef",
                        "--authenticationDatabase",
                        "admin",
                        "--eval",
                        (
                            "let authenticated=false,diagnostic_ok=false,observed_code=null;"
                            "try {const users=db.runCommand({connectionStatus:1}).authInfo.authenticatedUsers;"
                            'authenticated=users.length===1&&users[0].user==="rct_monitor"&&users[0].db==="admin";'
                            "const status=db.adminCommand({serverStatus:1});diagnostic_ok=status.ok===1;"
                            "if(status.code===13||status.code===18)observed_code=status.code;"
                            "}catch(e){if(e.code===13||e.code===18)observed_code=e.code;}"
                            "print(JSON.stringify({authenticated,diagnostic_ok,observed_code}));"
                        ),
                    ],
                    check=False,
                )
                witness = mongo_monitor_witness(probe)
                _STATE["mongo_monitor"] = witness
                if witness["category"] == "AUTHENTICATION_FAILED":
                    return False
                assert witness["category"] == "READY", "monitor authentication/diagnostic capability failed"
                return True

            # Root ping can precede completion of custom monitor-user initialization.
            # Only an authentication-not-ready observation retries; wrong-role/unknown fails.
            _STATE["phase"] = "MONGO_MONITOR_AUTH_READINESS"
            wait(mongo_monitor_ready)
            _STATE["phase"] = "MONGO_APPLICATION_READ_DENIAL"
            denied = execute(
                "mongo-attendance",
                [
                    "mongosh",
                    "--quiet",
                    "--username",
                    "rct_monitor",
                    "--password",
                    "synthetic-mongo-monitor-only-0123456789abcdef",
                    "--authenticationDatabase",
                    "admin",
                    "--eval",
                    'db.getSiblingDB("attendance_db").credential_fixture.findOne()',
                ],
                check=False,
            )
            _STATE["mongo_monitor"]["app_read_probe_exit"] = (
                denied.returncode if -128 <= denied.returncode <= 255 else None
            )
            unauthorized = "not authorized" in denied.stderr or "not authorized" in denied.stdout
            _STATE["mongo_monitor"]["app_read_category"] = "UNAUTHORIZED" if unauthorized else "UNKNOWN"
            assert denied.returncode != 0 and unauthorized
            _STATE["phase"] = "MONGO_EXPORTER_READINESS"
            wait(lambda: 'mongodb_up{cluster_role="mongod"} 1' in metrics("mongodb-exporter").splitlines())
            _STATE["phase"] = "REDIS_ADMIN_READINESS"

            def redis_admin_ready():
                probe = execute(
                    "redis", ["redis-cli", "--no-auth-warning", "-a", "synthetic-admin-only", "PING"], check=False
                )
                _STATE["redis_probe"] = redis_probe_witness(probe)
                return probe.returncode == 0 and _STATE["redis_probe"]["category"] == "PONG"

            wait(redis_admin_ready)
            _STATE["phase"] = "REDIS_ADMIN_FIXTURE"
            fixture = execute(
                "redis",
                [
                    "redis-cli",
                    "--no-auth-warning",
                    "-a",
                    "synthetic-admin-only",
                    "SET",
                    "credential_fixture",
                    "synthetic-private-marker",
                ],
                check=False,
            )
            _STATE["redis_probe"] = redis_probe_witness(fixture)
            assert fixture.returncode == 0 and _STATE["redis_probe"]["category"] == "OK"
            redis_cli = ["redis-cli", "--no-auth-warning", "--user", "rct_monitor", "-a", "synthetic-monitor-only"]
            for command in [
                ["GET", "credential_fixture"],
                ["SET", "forbidden", "x"],
                ["SCAN", "0"],
                ["EVAL", "return 1", "0"],
                ["CONFIG", "GET", "requirepass"],
                ["SLOWLOG", "GET", "1"],
            ]:
                _STATE["phase"] = "REDIS_DENIAL_" + command[0]
                denied = execute("redis", redis_cli + command, check=False)
                _STATE["redis_probe"] = redis_probe_witness(denied)
                assert "NOPERM" in denied.stdout or "NOPERM" in denied.stderr, command[0]
            _STATE["phase"] = "REDIS_EXPORTER_READINESS"

            def redis_exporter_ready():
                _STATE["redis_exporter"] = "ERROR"
                up = "redis_up 1" in metrics("redis-exporter")
                _STATE["redis_exporter"] = "UP" if up else "NOT_UP"
                return up

            wait(redis_exporter_ready)
            _STATE["phase"] = "REDIS_EXPORTER_CONFIG_GUARD"
            assert "--config-command=-" in config["services"]["redis-exporter"]["command"]
            _STATE["phase"] = "REDIS_CACHE_READINESS"
            cache_cli = ["redis-cli", "--no-auth-warning", "-e", "--user", "rct_cache", "-a", "synthetic-cache-only"]

            def cache_ready():
                probe = execute("redis-cache", cache_cli + ["PING"], check=False)
                _STATE["redis_probe"] = redis_probe_witness(probe)
                return probe.returncode == 0 and _STATE["redis_probe"]["category"] == "PONG"

            wait(cache_ready)
            _STATE["phase"] = "REDIS_CACHE_HANDSHAKE_AND_OPERATIONS"
            cache_key = "rct:cache:synthetic-startup:semester::fixture"
            for command in [
                ["HELLO", "3"],
                ["SELECT", "0"],
                ["CLIENT", "SETINFO", "LIB-NAME", "rct-isolation-contract"],
                ["CLIENT", "SETNAME", "academic-disposable-cache"],
                ["SET", cache_key, "synthetic-private-marker", "PX", "60000", "NX"],
            ]:
                assert execute("redis-cache", cache_cli + command, check=False).returncode == 0
            assert execute("redis-cache", cache_cli + ["GET", cache_key]).stdout.strip() == "synthetic-private-marker"
            assert (
                cache_key
                in execute("redis-cache", cache_cli + ["SCAN", "0", "MATCH", "rct:cache:*", "COUNT", "128"]).stdout
            )
            _STATE["phase"] = "REDIS_CACHE_NAMESPACE_AND_ADMIN_DENIALS"
            for command in [
                ["GET", "critical:otp:fixture"],
                ["SET", "critical:otp:fixture", "forbidden"],
                ["KEYS", "*"],
                ["FLUSHDB"],
                ["EVAL", "return 1", "0"],
                ["CONFIG", "GET", "maxmemory"],
                ["INFO"],
            ]:
                denied = execute("redis-cache", cache_cli + command, check=False)
                assert denied.returncode != 0 and redis_probe_witness(denied)["category"] == "NOPERM"
            assert execute("redis-cache", cache_cli + ["DEL", cache_key]).stdout.strip() == "1"
            expiring_key = "rct:cache:synthetic-startup:semester::expires"
            execute("redis-cache", cache_cli + ["SET", expiring_key, "synthetic-expiring", "PX", "25"])
            wait(lambda: execute("redis-cache", cache_cli + ["GET", expiring_key]).stdout.strip() == "")
            _STATE["phase"] = "REDIS_CACHE_CRITICAL_ISOLATION"
            # Cache connection identity cannot authenticate to the critical process.
            denied = execute("redis", cache_cli + ["PING"], check=False)
            assert denied.returncode != 0 and redis_probe_witness(denied)["category"] == "WRONGPASS"
            # Existing critical fixture survives independent cache operations.
            assert (
                execute(
                    "redis",
                    ["redis-cli", "--no-auth-warning", "-a", "synthetic-admin-only", "GET", "credential_fixture"],
                ).stdout.strip()
                == "synthetic-private-marker"
            )
            _STATE["phase"] = "REDIS_CAPACITY_POLICIES_AND_EXPORTERS"
            observed = {}
            for name, policy, maximum, password in [
                ("redis", "noeviction", 64 * 1024**2, "synthetic-monitor-only"),
                ("redis-cache", "allkeys-lru", 48 * 1024**2, "synthetic-cache-monitor-only"),
            ]:
                monitor = ["redis-cli", "--no-auth-warning", "-e", "--user", "rct_monitor", "-a", password]
                info = execute(name, monitor + ["INFO", "memory"]).stdout
                memory = dict(line.split(":", 1) for line in info.splitlines() if ":" in line)
                assert memory["maxmemory_policy"] == policy and int(memory["maxmemory"]) == maximum
                assert 0 < int(memory["used_memory"]) < maximum
                for command in [
                    ["GET", cache_key],
                    ["SET", cache_key, "forbidden"],
                    ["SCAN", "0"],
                    ["EVAL", "return 1", "0"],
                    ["CONFIG", "GET", "requirepass"],
                    ["SLOWLOG", "GET", "1"],
                ]:
                    denied = execute(name, monitor + command, check=False)
                    assert denied.returncode != 0 and redis_probe_witness(denied)["category"] == "NOPERM"
                exporter = name + "-exporter"
                wait(lambda exporter=exporter: "redis_up 1" in metrics(exporter).splitlines())
                assert "--config-command=-" in config["services"][exporter]["command"]
                rows = metrics(exporter).splitlines()
                rss = [float(row.split()[1]) for row in rows if row.startswith("process_resident_memory_bytes ")]
                assert len(rss) == 1 and 0 < rss[0] <= (32 if name == "redis-cache" else 64) * 1024**2
                observed[name] = {
                    "maxmemory_bytes": maximum,
                    "policy": policy,
                    "used_memory_bytes": int(memory["used_memory"]),
                    "exporter_rss_bytes": int(rss[0]),
                }
            # Reuse the same bounded Prometheus later used for Rabbit metrics.
            # The exact production scrape jobs must attach distinct finite roles.
            _STATE["phase"] = "REDIS_CAPACITY_PROMETHEUS_IDENTITY"
            docker("up", "-d", "rabbit-prometheus")

            def redis_collector_ready():
                address = docker("port", "rabbit-prometheus", "9090").stdout.strip()
                expression = 'redis_up{job=~"redis(-cache)?-exporter"} == 1'
                url = "http://" + address + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
                with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(url, timeout=5) as response:
                    payload = json.load(response)
                assert payload["status"] == "success"
                rows = payload["data"]["result"]
                if len(rows) != 2:
                    return False
                for row in rows:
                    labels = row["metric"]
                    name = "redis-cache" if labels["job"] == "redis-cache-exporter" else "redis"
                    assert labels["instance"] == name + "-exporter:9121"
                    assert labels["service"] == name and labels["environment"] == "synthetic"
                    assert labels["redis_role"] == ("cache" if name == "redis-cache" else "critical")
                assert {row["metric"]["redis_role"] for row in rows} == {"critical", "cache"}
                return True

            wait(redis_collector_ready)
            assert len(docker("ps", "--quiet", "redis", "redis-cache").stdout.strip().splitlines()) == 2
            redis_isolation = {
                "processes": 2,
                "prometheus_role_identity_verified": True,
                "cache_namespace_and_admin_denials": True,
                "cache_identity_denied_on_critical": True,
                "critical_fixture_preserved": True,
                "observed": observed,
                "qualification": "Bounded actual Redis roles/operations/policies; no memory pressure, durability or production capacity claim",
            }
            _STATE["phase"] = "EXPORTER_FIXTURE_PRIVACY"
            assert all("synthetic-private-marker" not in metrics(n) for n in exporter_names)
            # Sequential reuse of this owned project: release databases first,
            # then576MiB combined Rabbit/Prometheus caps; no second app stand.
            docker(
                "stop",
                *[
                    name
                    for name in config["services"]
                    if name not in ["rabbitmq", "rabbit-prometheus"] and not name.startswith("migrate-")
                ],
            )
            assert shutil.disk_usage("/tmp").free >= 4 * 1024**3, "4GiB floor before Rabbit cohort required"
            docker("up", "-d", "rabbitmq", "rabbit-prometheus")
            _STATE["phase"] = "RABBIT_PLUGIN_METRICS"
            rabbit_result = asyncio.run(rabbit_metrics_contract(docker))
            result = {
                "result": "PASS",
                "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "config_sha256": hashlib.sha256((ROOT / "docker-compose.prod.yml").read_bytes()).hexdigest(),
                "project": project,
                "checks": [
                    "PostgreSQL16 bootstrap privileged; Flyway11.7.2 migration identity owns schema and runs actual afterMigrate allowlist callback",
                    "Runtime shared Auth/Academic logical database role permits application CRUD/identity sequences but denies DDL/history/pg_shadow/unlisted tables",
                    "PostgreSQL pg_monitor can export but cannot read application credential fixture or pg_shadow",
                    "MongoDB clusterMonitor can export without local read or application-document access",
                    "Redis exports INFO metrics while GET/SET/SCAN/EVAL/CONFIG/SLOWLOG GET denied",
                    "no credential fixture in exporter output",
                ],
                "elapsed_seconds": round(time.monotonic() - started, 2),
                "actual_rabbit_plugin": rabbit_result,
                "redis_capacity_isolation": redis_isolation,
                "mongo_monitor": _STATE["mongo_monitor"],
                "redis_probe": _STATE["redis_probe"],
                "redis_exporter": _STATE["redis_exporter"],
            }
        finally:
            primary = sys.exc_info()[0]
            _STATE["cleanup"] = "STARTED"
            try:
                cleanup = docker(
                    "--profile", "rabbit-metrics-contract", "down", "--volumes", "--remove-orphans", check=False
                )
                remaining = docker("--profile", "rabbit-metrics-contract", "ps", "--quiet", check=False)
                assert cleanup.returncode == 0 and remaining.returncode == 0 and not remaining.stdout.strip(), (
                    "owned cohort cleanup failed"
                )
            except Exception:
                _STATE["cleanup"] = "FAIL"
                if primary is None:
                    _STATE["phase"] = "CLEANUP"
                    raise
            else:
                _STATE["cleanup"] = "PASS"
        result["cleanup"] = _STATE["cleanup"]
        print(json.dumps(result))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # noqa: BLE001 -- closed runtime boundary must never serialize exception text
        allowed = {
            "AssertionError",
            "CalledProcessError",
            "TimeoutExpired",
            "OSError",
            "RuntimeError",
            "ValueError",
            "KeyError",
            "HTTPError",
        }
        exception = type(error).__name__
        code = getattr(error, "returncode", None)
        print(
            json.dumps(
                {
                    "result": "FAIL",
                    "phase": _STATE["phase"],
                    "cleanup": _STATE["cleanup"],
                    "exception_class": exception if exception in allowed else "OtherError",
                    "returncode": code if type(code) is int and -128 <= code <= 255 else None,
                    "probe_returncode": _STATE.get("probe_returncode"),
                    "mongo_monitor": _STATE.get("mongo_monitor"),
                    "redis_probe": _STATE.get("redis_probe"),
                    "redis_exporter": _STATE.get("redis_exporter"),
                }
            )
        )
        sys.exit(1)
