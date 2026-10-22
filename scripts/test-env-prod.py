#!/usr/bin/env python3
"""Synthetic-only preflight regression against the actual Compose input contract."""

import base64
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-config", action="store_true", help="Also validate the synthetic file with real Compose (no containers)")
    args = parser.parse_args()
    compose = (ROOT / "docker-compose.prod.yml").read_text()
    mandatory = {
        match[1]
        for match in re.finditer(r"(?<!\$)\$\{([A-Z][A-Z0-9_]*)([^}]*)\}", compose)
        if not match[2].startswith(":-")
    }
    template = dict(re.findall(r"^([A-Z][A-Z0-9_]*)=(.*)$", (ROOT / ".env.prod.example").read_text(), re.M))
    assert mandatory <= template.keys(), "Template misses mandatory Compose input names"
    values = {name: "synthetic-only-value" for name in template}
    values.update({name: "/synthetic-only/" + name.lower() for name in mandatory if name.endswith(("_FILE", "_DIR"))})
    values.update(
        GATEWAY_PRIVATE_SUBNET="172.30.0.0/24", GATEWAY_NETWORK_GATEWAY="172.30.0.1",
        GATEWAY_NGINX_IPV4="172.30.0.10", GATEWAY_DYNAMIC_IP_RANGE="172.30.0.128/25",
        VAPID_PUBLIC_KEY="A" * 87, VAPID_PRIVATE_KEY="A" * 43,
        VAPID_SUBJECT="mailto:synthetic@example.invalid", MONGODB_REPLICA_SET_KEY="A" * 1024,
        GRPC_SECRET="A" * 44, INTERNAL_ISSUER_SECRET="B" * 44,
        SWAGGER_HTPASSWD="synthetic:$$apr1$$synthetic-only", ALERT_WEBHOOK_SECRET="a" * 64,
        ADMIN_TELEGRAM_IDS="1", COMPOSE_PROJECT_NAME="rct-synthetic-preflight",
    )
    for name in ("BOT_TOKEN", "TMA_BOT_TOKEN", "BOT_ALERT_TOKEN"):
        values[name] = "123456789:" + "A" * 35
    for name in ("MINI_APP_URL", "MINI_APP_WEB_URL", "CORS_ALLOWED_ORIGIN", "NOTIFICATION_WS_ALLOWED_ORIGINS"):
        values[name] = "https://synthetic.example.invalid/"
    for index, name in enumerate(sorted(name for name in values if name.endswith("_SERVICE_TOKEN")), 1):
        values[name] = base64.urlsafe_b64encode(bytes([index]) * 32).decode().rstrip("=")

    with tempfile.TemporaryDirectory(prefix="rct-env-prod-synthetic-") as directory:
        path = Path(directory) / "synthetic.env"
        for name in mandatory:
            if name.endswith("_FILE"):
                source = Path(directory) / name.lower()
                source.write_text("SYNTHETIC_ONLY=value\n" if name == "MONGO_MONITOR_ENV_FILE" else "synthetic-only\n")
                values[name] = str(source)
            elif name.endswith("_DIR"):
                source = Path(directory) / name.lower()
                source.mkdir()
                values[name] = str(source)

        def validate(inputs):
            path.write_text("".join(name + "=" + value + "\n" for name, value in sorted(inputs.items())))
            # Never publish validator output: even unexpected diagnostics stay local.
            return subprocess.run(["bash", str(ROOT / "scripts/validate-env-prod.sh"), str(path)], capture_output=True)

        assert validate(values).returncode == 0, "Complete synthetic environment rejected"
        if args.compose_config:
            # Compose only resolves the model: no create/up/pull, no datastore access.
            # Ambient variables must not override the synthetic env-file contract.
            environment = {key: value for key, value in os.environ.items() if key not in values}
            result = subprocess.run(
                ["docker", "compose", "--env-file", str(path), "-f", str(ROOT / "docker-compose.prod.yml"), "config", "--quiet"],
                capture_output=True, env=environment, cwd=directory,
            )
            assert result.returncode == 0, "Complete synthetic environment rejected by actual Compose"
        for name in sorted(mandatory):
            incomplete = dict(values)
            del incomplete[name]
            result = validate(incomplete)
            assert result.returncode == 2, "Missing mandatory Compose input accepted: " + name
            assert (name + " is not set").encode() in result.stderr
        # Reproduce the original omission as a single incomplete operator file.
        legacy = {name: value for name, value in values.items()
                  if not name.startswith(("PG_", "REDIS_CACHE_"))
                  and name not in {"MONGO_MONITOR_ENV_FILE", "REDIS_SERVER_ACL_FILE", "REDIS_MONITOR_PASSWORD_MAP_FILE"}}
        assert validate(legacy).returncode == 2, "Legacy incomplete environment accepted"
    print("PASS synthetic production environment: complete input and all " + str(len(mandatory)) + " missing-input cases; Compose="
          + ("PASS" if args.compose_config else "NOT_RUN"))


if __name__ == "__main__":
    main()
