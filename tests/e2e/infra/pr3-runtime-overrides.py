#!/usr/bin/env python3
"""Extend the disposable PR3 prepared stand with real host-built services.

Does not inspect credentials or generate product responses. Layer its output after
the prepared host-jars.yml in the same compose project; never use in production.
"""
import argparse
import json
from pathlib import Path

import yaml


class Override(list):
    pass


yaml.SafeDumper.add_representer(
    Override, lambda dumper, value: dumper.represent_sequence("!override", value)
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--jar-root", type=Path, required=True)
    parser.add_argument("--prepared-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--services", nargs="+", choices=["schedule-service", "attendance-service", "notification-web"], required=True)
    parser.add_argument("--auth-jar", type=Path, help="Explicit source-matched Auth artifact for local rollout")
    parser.add_argument("--gateway-jar", type=Path, help="Explicit source-matched gateway artifact for local rollout")
    parser.add_argument("--web-origin", default="http://127.0.0.1:14210")
    args = parser.parse_args()
    base = yaml.safe_load((args.repo / "docker-compose.e2e.yml").read_text())
    paths = {
        "schedule-service": "services/schedule-service/schedule-app/build/libs/schedule-app-0.1.0.jar",
        "attendance-service": "services/attendance-service/attendance-app/build/libs/attendance-app-0.1.0.jar",
        "notification-web": "services/notification-service/notification-app/build/libs/notification-app-0.1.0.jar",
    }
    services = {}
    artifacts = {name: args.jar_root / paths[name] for name in args.services}
    if args.auth_jar:
        artifacts["auth-service"] = args.auth_jar
    if args.gateway_jar:
        artifacts["api-gateway"] = args.gateway_jar
    for name, jar in artifacts.items():
        if not jar.is_file():
            parser.error(f"Build the assigned artifact first: {jar}")
        mounts = [f"{jar}:/app/app.jar:ro"]
        for mount in base["services"][name].get("volumes", []):
            source_and_target, suffix = mount.rsplit(":", 1)
            target = source_and_target.rsplit(":", 1)[1] if suffix in {"ro", "rw"} else suffix
            if target.endswith((".crt", ".key")):
                source = args.prepared_root / "private/certs" / Path(target).name
                if not source.is_file():
                    parser.error(f"Missing prepared local TLS fixture: {source}")
                mounts.append(f"{source}:{target}:ro")
            elif target == "/keys":
                mounts.append(f"{args.prepared_root / 'private/keys'}:/keys")
        if name == "auth-service":
            mounts.append(f"{args.repo / 'tests/e2e/infra/baseline/auth'}:/run/rct-e2e-baseline:ro")
        port = {"auth-service": 9090, "api-gateway": 8080, "schedule-service": 9092, "attendance-service": 9093, "notification-web": 9094}[name]
        health = "/actuator/health" if name in {"auth-service", "api-gateway"} else "/actuator/health/liveness"
        services[name] = {
            "image": "eclipse-temurin:21-jre-alpine",
            "user": "1000:1000",
            "entrypoint": ["java", "-jar", "/app/app.jar"],
            "environment": {"JAVA_TOOL_OPTIONS": "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx512m"},
            "volumes": Override(mounts),
            "healthcheck": {"test": ["CMD-SHELL", f"wget -Y off -qO- http://127.0.0.1:{port}{health} || exit 1"]},
        }
    # Persist only this project's newly introduced databases; no foreign volumes.
    volumes = {}
    if "schedule-service" in args.services:
        services["postgres-schedule"] = {"volumes": ["setup-schedule:/var/lib/postgresql/data"]}
        volumes["setup-schedule"] = {}
    if {"attendance-service", "notification-web"}.intersection(args.services):
        # Managed checkout files can be mode0600; Mongo runs as a different UID.
        # Copy the unchanged public initializer, never credentials, into this stand.
        mongo_init = args.output.parent / "init-mongo.js"
        mongo_init.write_bytes((args.repo / "infra/mongo/init-mongo.js").read_bytes())
        mongo_init.chmod(0o644)
        services["mongo-attendance"] = {"volumes": Override([f"{mongo_init}:/docker-entrypoint-initdb.d/init-mongo.js:ro", "setup-mongo:/bitnami/mongodb"])}
        volumes["setup-mongo"] = {}
    origins = ["http://127.0.0.1:15175", "http://127.0.0.1:15176", args.web_origin]
    services.setdefault("api-gateway", {}).setdefault("environment", {})["SPRING_APPLICATION_JSON"] = json.dumps({"spring.cloud.gateway.server.webflux.globalcors.cors-configurations.[/**].allowed-origins": origins})
    args.output.write_text(yaml.safe_dump({"services": services, "volumes": volumes}, sort_keys=False))
    print("Prepared disposable runtime override; credential values were not inspected.")


if __name__ == "__main__":
    main()
