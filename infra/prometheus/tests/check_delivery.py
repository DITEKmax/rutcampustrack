"""Isolated native Prometheus→Alertmanager→authenticated receiver acceptance.

Requires exact 2.55.1/0.27.0 binaries in PROMETHEUS_BIN / ALERTMANAGER_BIN.
Loopback only, temporary TSDB/notification state, no bot/provider delivery.
"""

import copy
import http.server
import json
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime, timedelta
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
LIMIT = 512 * 1024 * 1024


def port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def main():
    assert shutil.disk_usage("/tmp").free >= 4 * 1024**3, "4GiB temporary disk floor"
    state = {"fail": False, "deliveries": [], "rejected": 0}
    secret = "synthetic-receiver-only-no-external-delivery"

    class Receiver(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_GET(self):
            if self.path != "/metrics":
                self.send_error(404)
                return
            self.send_response(503 if state["fail"] else 200)
            self.end_headers()
            self.wfile.write(b"# TYPE synthetic_useful_operations_total counter\nsynthetic_useful_operations_total 0\n")

        def do_POST(self):
            if self.path != "/alerts":
                self.send_error(404)
                return
            if self.headers.get("Authorization") != "Bearer " + secret:
                state["rejected"] += 1
                self.send_error(401)
                return
            data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            state["deliveries"].append(data)
            self.send_response(200)
            self.end_headers()

    receiver = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Receiver)
    threading.Thread(target=receiver.serve_forever, daemon=True).start()
    am_port, prom_port = port(), port()
    receiver_url = f"http://127.0.0.1:{receiver.server_port}"
    procs = []
    peak_rss = 0
    started = time.monotonic()

    def request(url, data=None):
        payload = None if data is None else json.dumps(data).encode()
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        # Test services are explicitly loopback; avoid a configured HTTP proxy.
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=3) as response:
            body = response.read()
            return json.loads(body) if body else None

    def guard():
        nonlocal peak_rss
        rss = 0
        for proc in procs:
            assert proc.poll() is None, f"native process exited ({proc.returncode})"
            for line in Path(f"/proc/{proc.pid}/status").read_text().splitlines():
                if line.startswith("VmRSS:"):
                    rss += int(line.split()[1]) * 1024
        peak_rss = max(peak_rss, rss)
        assert rss <= LIMIT, "512MiB combined native process RSS budget exceeded"
        assert shutil.disk_usage("/tmp").free >= 4 * 1024**3

    def wait(predicate, seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            guard()
            try:
                if predicate():
                    return
            except (OSError, ValueError, IndexError, KeyError, urllib.error.URLError):
                pass
            time.sleep(0.25)
        raise AssertionError("Timed out waiting for telemetry state")

    def delivered(status):
        return any(
            a["labels"].get("alertname") == "ServiceDown" and a["status"] == status
            for d in state["deliveries"]
            for a in d.get("alerts", [])
        )

    with tempfile.TemporaryDirectory(prefix="rct-monitoring-delivery-") as tmp:
        td = Path(tmp)
        try:
            # Keep real source grouping, routes, quiet intervals, inhibition and auth.
            am = yaml.safe_load((ROOT / "infra/alertmanager/alertmanager.yml").read_text())
            am["route"].update(group_wait="1s", group_interval="2s", repeat_interval="1h")
            webhook = am["receivers"][0]["webhook_configs"][0]
            webhook["url"] = receiver_url + "/alerts"
            webhook["http_config"]["authorization"]["credentials_file"] = str(td / "secret")
            (td / "secret").write_text(secret)
            (td / "secret").chmod(0o600)
            (td / "alertmanager.yml").write_text(yaml.safe_dump(am, sort_keys=False))
            source = yaml.safe_load((ROOT / "infra/prometheus/rules/service-health.yml").read_text())
            rule = copy.deepcopy(
                next(r for g in source["groups"] for r in g["rules"] if r.get("alert") == "ServiceDown")
            )
            assert rule["for"] == "1m", "runtime preserves source outage hold time"
            (td / "rules.yml").write_text(
                yaml.safe_dump({"groups": [{"name": "delivery-acceptance", "rules": [rule]}]})
            )
            prom = {
                "global": {"scrape_interval": "1s", "evaluation_interval": "1s", "scrape_timeout": "500ms"},
                "alerting": {"alertmanagers": [{"static_configs": [{"targets": [f"127.0.0.1:{am_port}"]}]}]},
                "rule_files": [str(td / "rules.yml")],
                "scrape_configs": [
                    {
                        "job_name": "synthetic-operation",
                        "metrics_path": "/metrics",
                        "static_configs": [
                            {
                                "targets": [f"127.0.0.1:{receiver.server_port}"],
                                "labels": {"service": "synthetic-operation", "environment": "synthetic"},
                            }
                        ],
                    }
                ],
            }
            (td / "prometheus.yml").write_text(yaml.safe_dump(prom))
            env = dict(os.environ, GOMAXPROCS="1", GOMEMLIMIT="180MiB")
            cpu = min(os.sched_getaffinity(0))
            commands = [
                [
                    os.environ["ALERTMANAGER_BIN"],
                    f"--config.file={td}/alertmanager.yml",
                    f"--storage.path={td}/am",
                    f"--web.listen-address=127.0.0.1:{am_port}",
                    "--cluster.listen-address=",
                ],
                [
                    os.environ["PROMETHEUS_BIN"],
                    f"--config.file={td}/prometheus.yml",
                    f"--storage.tsdb.path={td}/prom",
                    f"--web.listen-address=127.0.0.1:{prom_port}",
                    "--storage.tsdb.retention.size=32MB",
                    "--query.max-concurrency=1",
                ],
            ]
            for index, command in enumerate(commands):
                with open(td / f"process-{index}.log", "w") as log:
                    proc = subprocess.Popen(command, stdout=log, stderr=log, env=env)
                os.sched_setaffinity(proc.pid, {cpu})
                procs.append(proc)
            wait(
                lambda: (
                    request(f"http://127.0.0.1:{prom_port}/api/v1/query?query=up")["data"]["result"][0]["value"][1]
                    == "1"
                ),
                20,
            )
            try:
                request(receiver_url + "/alerts", {})
            except urllib.error.HTTPError as e:
                assert e.code == 401
            assert state["rejected"] == 1
            state["fail"] = True
            wait(lambda: delivered("firing"), 85)
            first_count = len(state["deliveries"])
            wait_start = time.monotonic()
            wait(lambda: time.monotonic() - wait_start >= 4, 6)
            assert len(state["deliveries"]) == first_count, "unchanged firing state must deduplicate"
            state["fail"] = False
            wait(lambda: delivered("resolved"), 25)
            # A failed service inhibits its derivative readiness failure across
            # distinct scrape jobs, while unrelated services remain independent.
            labels = {
                "environment": "synthetic",
                "service": "synthetic-dependency",
                "instance": "synthetic-dependency:1",
                "job": "synthetic-dependency",
                "severity": "critical",
            }
            ends = (datetime.now(UTC) + timedelta(minutes=1)).isoformat()
            request(
                f"http://127.0.0.1:{am_port}/api/v2/alerts",
                [
                    {"labels": dict(labels, alertname="ServiceDown"), "endsAt": ends},
                    {"labels": dict(labels, alertname="HealthCheckDown", job="blackbox-readiness"), "endsAt": ends},
                ],
            )
            wait(
                lambda: any(
                    a["labels"].get("alertname") == "HealthCheckDown" and a["status"].get("inhibitedBy")
                    for a in request(f"http://127.0.0.1:{am_port}/api/v2/alerts")
                ),
                10,
            )
            assert not any(
                a["labels"].get("alertname") == "HealthCheckDown"
                for delivery in state["deliveries"]
                for a in delivery["alerts"]
            )
            assert all(d.get("receiver") == "notification-webhook" for d in state["deliveries"])
            assert not any(
                a["labels"].get("environment") != "synthetic" for d in state["deliveries"] for a in d["alerts"]
            )
            print(
                json.dumps(
                    {
                        "result": "PASS",
                        "versions": {"prometheus": "2.55.1", "alertmanager": "0.27.0"},
                        "checks": [
                            "real scrape failure→ServiceDown after1m",
                            "authenticated receiver",
                            "unchanged firing deduplicated",
                            "same alert resolved after recovery",
                            "same service readiness inhibited across jobs",
                        ],
                        "deliveries": len(state["deliveries"]),
                        "peak_native_rss_bytes": peak_rss,
                        "elapsed_seconds": round(time.monotonic() - started, 2),
                    }
                )
            )
        finally:
            for proc in reversed(procs):
                if proc.poll() is None:
                    proc.terminate()
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=5)
            receiver.shutdown()
            receiver.server_close()
            assert all(p.poll() is not None for p in procs), "all owned native processes must be reaped"


if __name__ == "__main__":
    main()
