"""Synthetic full-validator invocation; public deterministic fixtures, no real env."""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

wt = Path(__file__).resolve().parents[3]
script = wt / "scripts/validate-env-prod.sh"
names = re.search(r"REQUIRED_VARS=\((.*?)\)", script.read_text(encoding="utf-8"), re.S)[1].split()
env = dict.fromkeys(names, "synthetic-password")
env.update(MONGODB_REPLICA_SET_KEY="A" * 1024, BOT_TOKEN="123456789:" + "a" * 30,
           TMA_BOT_TOKEN="123456789:" + "a" * 30, BOT_ALERT_TOKEN="987654321:" + "b" * 30,
           VAPID_PUBLIC_KEY="A" * 87, VAPID_PRIVATE_KEY="A" * 43,
           VAPID_SUBJECT="mailto:synthetic@example.invalid", GRPC_SECRET="A" * 44,
           INTERNAL_ISSUER_SECRET="B" * 44, ALERT_WEBHOOK_SECRET="a" * 64,
           SWAGGER_HTPASSWD="swagger:$$apr1$$synthetic", ADMIN_TELEGRAM_IDS="123456789",
           IMAGE_TAG="v0.0.0", GATEWAY_PRIVATE_SUBNET="172.30.0.0/24",
           GATEWAY_NETWORK_GATEWAY="172.30.0.1", GATEWAY_NGINX_IPV4="172.30.0.10",
           GATEWAY_DYNAMIC_IP_RANGE="172.30.0.128/25")
for name in ("MINI_APP_URL", "MINI_APP_WEB_URL", "CORS_ALLOWED_ORIGIN"):
    env[name] = "https://synthetic.example.invalid"
academic = "ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN"
schedule = "SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN"
# These public test strings encode32 zero bytes and32 0xff bytes, respectively.
env[academic], env[schedule] = "A" * 43, "_" * 42 + "8"
cases = [("valid separate", {}, (), 0, None),
         ("missing academic", {}, (academic,), 2, academic),
         ("missing schedule", {}, (schedule,), 2, schedule),
         ("placeholder", {academic: "CHANGE_ME"}, (), 2, academic),
         ("padded", {academic: "A" * 43 + "="}, (), 3, academic),
         ("noncanonical pad bits", {schedule: "A" * 42 + "B"}, (), 3, schedule),
         ("wrong alphabet", {academic: "+" + "A" * 42}, (), 3, academic),
         ("wrong length", {schedule: "A" * 42}, (), 3, schedule)]
if "--before" in sys.argv:
    cases = cases[1:3]
failures = 0
for name, updates, removed, expected, diagnostic in cases:
    fixture = env | updates
    for key in removed:
        del fixture[key]
    with tempfile.TemporaryDirectory(prefix="synthetic-directed-", dir=Path(__file__).parent) as tmp:
        path = Path(tmp) / "synthetic.env"
        path.write_text("".join(f"{k}={v}\n" for k, v in fixture.items()), encoding="utf-8", newline="\n")
        run = subprocess.run([r"C:\Program Files\Git\bin\bash.exe", str(script), str(path)],
                             capture_output=True, text=True, encoding="utf-8")
    output = run.stdout + run.stderr
    value = updates.get(diagnostic) if diagnostic else None
    safe = not value or value == "CHANGE_ME" or value not in output
    passed = run.returncode == expected and (not diagnostic or diagnostic in output) and safe
    failures += not passed
    print(f"{'PASS' if passed else 'FAIL'} {name}: actual_exit={run.returncode} expected_exit={expected} diagnostic_safe={safe}")
    if diagnostic:
        for line in output.splitlines():
            if diagnostic in line:
                print(re.sub(r"\x1b\[[0-9;]*m", "", line))
print(f"Cases={len(cases)} failures={failures}; synthetic fixtures removed; no real env read")
raise SystemExit(bool(failures))
