"""Isolated CI datastore/exporter contracts; never targets an existing stack or production.

Run only on the normal CI VM with RCT_MONITORING_RUNTIME=isolated-ci.
Pinned images/configuration are read from docker-compose.prod.yml, credentials are
synthetic, and only the unique generated Compose project is removed on exit.
"""

import copy
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import uuid
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]


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
                "MONGODB_REPLICA_SET_KEY": "synthetic-replica-key-only",
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
        config["services"]["redis"] = {
            "image": production["services"]["redis"]["image"],
            "command": [
                "redis-server",
                "--aclfile",
                "/run/redis/users.acl",
                "--maxmemory",
                "96mb",
                "--maxmemory-policy",
                "allkeys-lru",
            ],
            "volumes": [str(td / "redis.acl") + ":/run/redis/users.acl:ro"],
            "networks": ["private_net"],
            "mem_limit": "128m",
            "cpus": 0.25,
        }
        exporter_names = [
            "postgres-academic-exporter",
            "postgres-schedule-exporter",
            "mongodb-exporter",
            "redis-exporter",
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
                        else "synthetic-monitor-only"
                    )
                    config["secrets"][secret] = {"file": str(f)}
            config["services"][name] = service
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
            docker("up", "-d")
            for name, db in [("academic", "academic_db"), ("schedule", "schedule_db")]:
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
            wait(
                lambda: (
                    execute("mongo-attendance", mongo_cli + ["db.adminCommand({ping:1}).ok"], check=False).returncode
                    == 0
                )
            )
            execute(
                "mongo-attendance",
                mongo_cli
                + ['db.getSiblingDB("attendance_db").credential_fixture.insertOne({value:"synthetic-private-marker"})'],
            )
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
            assert denied.returncode != 0 and ("not authorized" in denied.stderr or "not authorized" in denied.stdout)
            wait(lambda: "mongodb_up 1" in metrics("mongodb-exporter"))
            wait(
                lambda: (
                    execute(
                        "redis", ["redis-cli", "--no-auth-warning", "-a", "synthetic-admin-only", "PING"], check=False
                    ).stdout.strip()
                    == "PONG"
                )
            )
            execute(
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
            )
            redis_cli = ["redis-cli", "--no-auth-warning", "--user", "rct_monitor", "-a", "synthetic-monitor-only"]
            for command in [
                ["GET", "credential_fixture"],
                ["SET", "forbidden", "x"],
                ["SCAN", "0"],
                ["EVAL", "return 1", "0"],
                ["CONFIG", "GET", "requirepass"],
                ["SLOWLOG", "GET", "1"],
            ]:
                denied = execute("redis", redis_cli + command, check=False)
                assert "NOPERM" in denied.stdout or "NOPERM" in denied.stderr, command[0]
            wait(lambda: "redis_up 1" in metrics("redis-exporter"))
            assert "--config-command=-" in config["services"]["redis-exporter"]["command"]
            assert all("synthetic-private-marker" not in metrics(n) for n in exporter_names)
            print(
                json.dumps(
                    {
                        "result": "PASS",
                        "source_sha": subprocess.check_output(
                            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
                        ).strip(),
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
                    }
                )
            )
        finally:
            primary = sys.exc_info()[0]
            cleanup = docker("down", "--volumes", "--remove-orphans", check=False)
            remaining = docker("ps", "--quiet", check=False)
            if primary is None:
                assert cleanup.returncode == 0 and remaining.returncode == 0 and not remaining.stdout.strip(), (
                    "owned Compose project must be stopped"
                )


if __name__ == "__main__":
    main()
