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
    parser.add_argument("--web-origin", default="http://127.0.0.1:14210")
    args = parser.parse_args()
    base = yaml.safe_load((args.repo / "docker-compose.e2e.yml").read_text())
    paths = {
        "schedule-service": "services/schedule-service/schedule-app/build/libs/schedule-app-0.1.0.jar",
        "attendance-service": "services/attendance-service/attendance-app/build/libs/attendance-app-0.1.0.jar",
        "notification-web": "services/notification-service/notification-app/build/libs/notification-app-0.1.0.jar",
    }
    services = {}
    for name in args.services:
        jar = args.jar_root / paths[name]
        if not jar.is_file():
            parser.error(f"Build the assigned artifact first: {jar}")
        mounts = [f"{jar}:/app/app.jar:ro"]
        for mount in base["services"][name].get("volumes", []):
            _, target, _ = mount.rsplit(":", 2)
            if target.endswith((".crt", ".key")):
                source = args.prepared_root / "private/certs" / Path(target).name
                if not source.is_file():
                    parser.error(f"Missing prepared local TLS fixture: {source}")
                mounts.append(f"{source}:{target}:ro")
        port = {"schedule-service": 9092, "attendance-service": 9093, "notification-web": 9094}[name]
        services[name] = {
            "image": "eclipse-temurin:21-jre-alpine",
            "user": "1000:1000",
            "entrypoint": ["java", "-jar", "/app/app.jar"],
            "environment": {"JAVA_TOOL_OPTIONS": "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx512m"},
            "volumes": Override(mounts),
            "healthcheck": {"test": ["CMD-SHELL", f"wget -Y off -qO- http://127.0.0.1:{port}/actuator/health/liveness || exit 1"]},
        }
    # Persist only this project's newly introduced databases; no foreign volumes.
    volumes = {}
    if "schedule-service" in args.services:
        services["postgres-schedule"] = {"volumes": ["setup-schedule:/var/lib/postgresql/data"]}
        volumes["setup-schedule"] = {}
    if {"attendance-service", "notification-web"}.intersection(args.services):
        services["mongo-attendance"] = {"volumes": ["setup-mongo:/bitnami/mongodb"]}
        volumes["setup-mongo"] = {}
    origins = ["http://127.0.0.1:15175", "http://127.0.0.1:15176", args.web_origin]
    services["api-gateway"] = {"environment": {"SPRING_APPLICATION_JSON": json.dumps({"spring.cloud.gateway.server.webflux.globalcors.cors-configurations.[/**].allowed-origins": origins})}}
    args.output.write_text(yaml.safe_dump({"services": services, "volumes": volumes}, sort_keys=False))
    print("Prepared disposable runtime override; credential values were not inspected.")


if __name__ == "__main__":
    main()
