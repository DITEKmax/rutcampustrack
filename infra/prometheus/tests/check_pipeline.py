"""Finite Grafana/Loki/Tempo/Promtail contract using exact production versions and config.

Synthetic telemetry proves queries, links and persistence; it does not stand in
for the application/broker trace required by M01/M04. Only an owned CI project runs.
"""

import base64
import copy
import hashlib
import json
import math
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

import yaml
from check_formulas import long_window_fixture, record_expr, slo_budget_inventory

ROOT = Path(__file__).resolve().parents[3]
_STATE = {"phase": "PRECHECK_OR_CONFIG", "cleanup": "NOT_STARTED"}
PROM_UID = "PBFA97CFB590B2093"
PYTHON_IMAGE = "python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c"


async def bot_metrics_worker():
    """Real production HTTP/SDK/queue, local controlled provider; no Bot/Telegram client."""
    import asyncio
    from types import SimpleNamespace

    from bot.__main__ import run_health_server
    from bot.observability import METRICS_REGISTRY, SendQueueCollector
    from bot.services.send_queue import SendTask, TelegramSendQueue

    async def enabled(*args, **kwargs):
        return True

    calls = 0

    async def local_provider():
        nonlocal calls
        calls += 1

    queue = TelegramSendQueue(prefs_client=SimpleNamespace(is_enabled=enabled))
    METRICS_REGISTRY.register(SendQueueCollector(queue))
    stopped = asyncio.Event()
    asyncio.get_running_loop().add_signal_handler(signal.SIGTERM, stopped.set)
    queue.start()
    try:
        await run_health_server()
        control = Path("/control")
        while not (control / "start").exists():
            await asyncio.sleep(0.1)
        await queue.put(
            SendTask(
                coroutine_factory=local_provider,
                user_id=10,
                chat_id=555,
                category="reminders",
                notification_type="LESSON_REMINDER",
            )
        )
        await asyncio.wait_for(queue._queue.join(), timeout=10)
        assert calls == 1 and queue._total_sent == 1 and queue._total_failed == 0
        (control / "done").write_text("PASS")
        await stopped.wait()
    finally:
        await queue.shutdown()


def e2e_scrape_config():
    """Only the eight real application targets already deployed by client E2E."""
    expected = {
        "auth-service": 9090,
        "academic-service": 9091,
        "schedule-service": 9092,
        "attendance-service": 9093,
        "document-renderer-service": 9095,
        "mobile-bff": 9080,
        "api-gateway": 8080,
        "notification-web": 9094,
    }
    source = yaml.safe_load((ROOT / "infra/prometheus/prometheus.yml").read_text())
    production = yaml.safe_load((ROOT / "docker-compose.prod.yml").read_text())["services"]["prometheus"]
    jobs = []
    # Only retained-panel families;16k retained series and5s cadence keep the
    # existing192MiB Prometheus/512MiB whole-TSDB guard, without a second stand.
    families = [
        "process_(start_time|uptime)_seconds",
        "process_files_(open_files|max_files)",
        "process_cpu_usage",
        "system_(cpu_(usage|count)|load_average_1m)",
        "jvm_memory_(used|committed|max)_bytes",
        "jvm_classes_(loaded_classes|unloaded_classes_total)",
        "jvm_threads_(daemon_threads|live_threads|peak_threads)",
        "jvm_buffer_(memory_used|total_capacity)_bytes",
        "jvm_gc_(memory_(allocated|promoted)_bytes_total|pause_seconds_(count|sum))",
        "logback_events_total",
        "hikaricp_connections(_(active|idle|pending))?",
        "tomcat_(global_(error_total|sent_bytes_total|received_bytes_total)|sessions_active_current_sessions|threads_(current_threads|busy_threads))",
        "http_server_requests_seconds_(count|sum)",
        "grpc_client_duration_seconds_(bucket|sum|count)",
        "auth_login_total",
        "otp_verify_total",
        "attendance_checkin_total",
        "notification_active_ws_sessions",
        "internal_jwt_fallback_total",
        "outbox_(lag_seconds|published_total|failed_total)",
        "attendance_report_duration_seconds_(count|sum|bucket)",
        "rct_export_jobs_(accepted|terminal|retries)_total",
        "rct_export_jobs_duration_seconds_bucket",
        "rct_export_jobs_(snapshot_timestamp_seconds|queue|due|waiting|oldest_(nonterminal|running|due)_age_seconds)",
    ]
    allow = "|".join(families)
    for name, port in expected.items():
        job = copy.deepcopy(next(j for j in source["scrape_configs"] if j["job_name"] == name))
        assert job["metrics_path"] == "/actuator/prometheus"
        assert job["static_configs"] == [
            {"targets": [name + ":" + str(port)], "labels": {"environment": "production", "service": name}}
        ]
        job.update(
            scrape_interval="5s",
            scrape_timeout="5s",
            honor_labels=True,
            sample_limit=3000,
            body_size_limit="8MB",
            metric_relabel_configs=[{"source_labels": ["__name__"], "regex": allow, "action": "keep"}],
        )
        job["static_configs"][0]["labels"]["environment"] = "synthetic"
        jobs.append(job)
    return (
        {
            "global": {"scrape_interval": "5s", "scrape_timeout": "5s", "evaluation_interval": "5s"},
            "scrape_configs": jobs,
        },
        production["image"],
        expected,
    )


def e2e_panel_rules(expected):
    """Reuse actual record expressions; only the validation environment changes."""
    groups = []
    short = {"rct_sli:http_eligible_rate5m", "rct_sli:http_error_ratio5m", "rct_sli:report_p95_seconds5m"}
    for filename in ["expected-targets.yml", "service-objectives.yml", "telemetry-pipeline.yml"]:
        source = yaml.safe_load((ROOT / "infra/prometheus/rules" / filename).read_text())
        for group in source["groups"]:
            rules = []
            for original in group["rules"]:
                if "record" not in original:
                    continue
                if original["record"] == "rct_expected_target":
                    if original["labels"]["job"] not in expected:
                        continue
                elif original["record"] not in short and group["name"] not in {
                    "http-slo-window-rollup",
                    "http-slo-long-window",
                    "export-slo-window-rollup",
                    "export-slo-long-window",
                }:
                    continue
                rule = copy.deepcopy(original)
                rule["expr"] = rule["expr"].replace('environment="production"', 'environment="synthetic"')
                if rule.get("labels", {}).get("environment") == "production":
                    rule["labels"]["environment"] = "synthetic"
                rules.append(rule)
            if rules:
                groups.append(dict(group, rules=rules))
    return {"groups": groups}


def e2e_live_panel_queries(query, expected, safe):
    """Fixed retained panels against actual apps; no substitute telemetry."""
    deadline = time.monotonic() + 45
    checks = []

    def values(expression):
        assert time.monotonic() < deadline, "live panel query deadline"
        return query(expression)

    def probe(
        filename,
        panel_id,
        job,
        *,
        pool="",
        heap=".*",
        nonheap=".*",
        absent=None,
        counter=None,
        counter_operator="rate",
        coverage=False,
    ):
        panel = next(
            p
            for p in json.loads((ROOT / "infra/grafana/provisioning/dashboards" / filename).read_text())["panels"]
            if p["id"] == panel_id
        )
        for index, target in enumerate(panel["targets"]):
            safe["panel_probe"] = {"dashboard": filename, "panel": panel_id, "job": job, "target": index}
            expression = target["expr"]
            for name, replacement in [
                ("${memory_pool_heap:regex}", heap),
                ("${memory_pool_nonheap:regex}", nonheap),
                ("$application", job),
                ("$hikaricp", pool),
                ("$instance", re.escape(job + ":" + str(expected[job]))),
                ("$environment", "synthetic"),
                ("$job", re.escape(job)),
                ("$interval", "5m"),
            ]:
                expression = expression.replace(name, json.dumps(replacement)[1:-1])
            assert "$" not in expression
            rows = values(expression)
            numbers = [float(row["value"][1]) for row in rows]
            target_absent = absent.get(index) if isinstance(absent, dict) else absent
            if target_absent is not None:
                assert not rows or all(math.isnan(value) for value in numbers), (
                    "no-event panel must not invent healthy zero"
                )
                state = target_absent
            elif coverage:
                assert all(math.isfinite(value) and 0 <= value < 2590200 for value in numbers)
                state = "INSUFFICIENT_30D_COVERAGE"
            else:
                assert rows and all(math.isfinite(value) or math.isnan(value) for value in numbers), (
                    "expected panel family/selector unavailable"
                )
                finite = [value for value in numbers if math.isfinite(value)]
                if any(math.isnan(value) for value in numbers):
                    assert counter is not None, "unexpected nonfinite panel value"
                    assert counter_operator in {"rate", "irate"}
                    rates = values(counter_operator + "(" + counter + '{job="' + job + '"}[5m])')
                    for row, value in zip(rows, numbers, strict=True):
                        if not math.isnan(value):
                            continue
                        matches = [
                            float(rate["value"][1])
                            for rate in rates
                            if all(
                                rate["metric"].get(key) == val
                                for key, val in row["metric"].items()
                                if key != "__name__"
                            )
                        ]
                        assert matches and all(rate == 0 for rate in matches), "NaN requires observed idle denominator"
                assert finite or counter is not None, "expected operation has no finite panel value"
                state = (
                    "IDLE_NO_RECENT_SAMPLE"
                    if not finite
                    else "FINITE_WITH_IDLE_SERIES"
                    if len(finite) < len(numbers)
                    else "FINITE"
                )
            checks.append(
                dict(
                    safe["panel_probe"],
                    state=state,
                    samples=len(rows),
                    minimum=min(value for value in numbers if math.isfinite(value))
                    if state.startswith("FINITE")
                    else None,
                    maximum=max(value for value in numbers if math.isfinite(value))
                    if state.startswith("FINITE")
                    else None,
                )
            )

    for job in expected:
        memory = values('jvm_memory_used_bytes{job="' + job + '"}')
        ids = {
            area: sorted({r["metric"]["id"] for r in memory if r["metric"].get("area") == area})
            for area in ["heap", "nonheap"]
        }
        assert all(ids.values()), "both actual JVM memory areas required"
        pools = {area: "|".join(re.escape(name) for name in names) for area, names in ids.items()}
        for panel in [52, 58, 60, 66, 56, 95, 96, 85, 88, 50, 80, 82, 83, 68, 78, 6, 10, 14, 16, 20]:
            probe("springboot-apm.json", panel, job, heap=pools["heap"], nonheap=pools["nonheap"])
        pause = values('jvm_gc_pause_seconds_count{job="' + job + '"}')
        # Micrometer1.15.12 creates pause timers on notifications, not at bind.
        # The eager allocated/promoted counters were checked by panel78 above.
        for panel, suffix in [(74, "count"), (76, "sum")]:
            samples = values("count_over_time(jvm_gc_pause_seconds_" + suffix + '{job="' + job + '"}[5m])')
            assert bool(samples) == bool(pause), "incomplete GC pause timer family"
            mature = any(float(row["value"][1]) >= 2 for row in samples)
            probe(
                "springboot-apm.json",
                panel,
                job,
                absent="NO_GC_PAUSE_OBSERVED" if not pause else None if mature else "INSUFFICIENT_GC_SAMPLES",
            )
        http = values('http_server_requests_seconds_count{job="' + job + '",uri!~".*actuator.*"}')
        for panel in [4, 2]:
            probe(
                "springboot-apm.json",
                panel,
                job,
                absent="NO_NON_ACTUATOR_HTTP_SAMPLE" if not http else None,
                counter="http_server_requests_seconds_count" if panel == 2 else None,
                counter_operator="irate" if panel == 2 else "rate",
            )
        for panel in [21, 22, 6]:
            probe(
                "business-kpis.json",
                panel,
                job,
                absent="NO_NON_ACTUATOR_HTTP_SAMPLE" if panel == 22 and not http else None,
            )
        probe("business-kpis.json", 20, job, absent="NO_EXPECTED_TARGET_MISSING")
        eligible = values(
            'sum(rate(http_server_requests_seconds_count{job="'
            + job
            + '",status=~"2..|3..|5..",termination!="cancelled",uri!~"/actuator.*|/api-docs.*|/swagger.*"}[5m]))'
        )
        has_traffic = eligible and float(eligible[0]["value"][1]) > 0
        probe(
            "business-kpis.json",
            23,
            job,
            absent=None if has_traffic else "NO_ELIGIBLE_HTTP_TRAFFIC",
            counter="http_server_requests_seconds_count",
        )
        if job != "api-gateway":
            for panel in [28, 24, 26, 30]:
                probe("springboot-apm.json", panel, job)
        if job in {"auth-service", "academic-service", "schedule-service"}:
            pools = values('hikaricp_connections{job="' + job + '"}')
            assert 1 <= len(pools) <= 4
            for row in pools:
                pool = row["metric"]["pool"]
                assert 0 < len(pool) <= 128
                for panel in [44, 36]:
                    probe("springboot-apm.json", panel, job, pool=pool)
        if job in {"academic-service", "schedule-service", "attendance-service"}:
            count = values('grpc_client_duration_seconds_count{job="' + job + '"}')
            assert count and sum(float(r["value"][1]) for r in count) > 0
            recent = values('sum(rate(grpc_client_duration_seconds_count{job="' + job + '"}[5m]))')
            assert len(recent) == 1 and math.isfinite(float(recent[0]["value"][1]))
            for panel in [1, 2, 3]:
                probe(
                    "grpc-latency.json",
                    panel,
                    job,
                    absent="NO_RECENT_RPC" if float(recent[0]["value"][1]) == 0 else None,
                    counter="grpc_client_duration_seconds_count",
                )
            errors = values('grpc_client_duration_seconds_count{job="' + job + '",status!="OK"}')
            probe("grpc-latency.json", 4, job, absent=None if errors else "NO_NON_OK_RPC_SAMPLE")
            probe("grpc-latency.json", 5, job)
    for job, panel, family in [
        ("attendance-service", 1, "attendance_checkin_total"),
        ("notification-web", 4, "notification_active_ws_sessions"),
        *[(job, 5, "outbox_lag_seconds") for job in ["auth-service", "academic-service", "schedule-service"]],
        *[(job, 8, "internal_jwt_fallback_total") for job in expected],
    ]:
        # No event producer is synthesized merely to make an idle panel nonempty.
        present = values('{__name__=~"' + family + '",job="' + job + '"}')
        probe("business-kpis.json", panel, job, absent=None if present else "NO_PRODUCER_SAMPLE_YET")
    no_event = {
        index: "NO_PRODUCER_SAMPLE_YET"
        for index, family in enumerate(["auth_login_total", "otp_verify_total"])
        if not values(family + '{job="auth-service"}')
    }
    probe("business-kpis.json", 2, "auth-service", absent=no_event)
    for panel in [50, 51, 52]:
        probe("business-kpis.json", panel, "auth-service")
    for panel in [33, 34, 43]:
        probe(
            "business-kpis.json",
            panel,
            "attendance-service",
            counter="attendance_report_duration_seconds_count" if panel in {34, 43} else None,
        )
    probe("business-kpis.json", 41, "auth-service", coverage=True)
    for panel in [40, 53]:
        probe("business-kpis.json", panel, "auth-service", absent="INSUFFICIENT_30D_COVERAGE")
    safe["live_panel_queries"] = checks
    safe.pop("panel_probe", None)


def e2e_async_browser():
    """Observe the existing real browser scenario, never launch another app stand."""
    assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
    assert os.environ.get("RCT_E2E_ASYNC_REPORT") == "true" and os.environ.get("RCT_E2E_SYNTHETIC_SEED") == "true"
    source_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    assert source_sha == os.environ["GITHUB_SHA"] and re.fullmatch("[0-9a-f]{40}", source_sha)
    run = Path(os.environ["RCT_REMAINING5_RUN"])
    assert run.is_absolute() and run.is_relative_to(Path(os.environ["RUNNER_TEMP"]))
    for directory in [run, run / "artifacts"]:
        assert directory.is_dir() and not directory.is_symlink() and directory.stat().st_mode & 0o777 == 0o700
    report = run / "artifacts/metrics.safe.json"
    assert not report.exists() and not (run / "artifacts/safe-results.json").exists()
    config, image, expected = e2e_scrape_config()
    name = "rct-e2e-scrape-" + uuid.uuid4().hex[:12]
    safe = {
        "schema": 1,
        "source_sha": source_sha,
        "result": "FAIL",
        "phase": "PRECHECK",
        "cleanup": "NOT_STARTED",
        "expected_applications": 8,
        "actual_applications": 0,
        "metric_deltas": {},
        "resources": {
            "memory_limit_bytes": 192 * 1024**2,
            "cpus": 0.25,
            "retention_blocks_bytes": 32 * 1024**2,
            "total_tsdb_allocated_guard_bytes": 512 * 1024**2,
            "peak_tsdb_allocated_bytes": 0,
        },
        "qualification": "Actual existing E2E applications and two real18week DOCX downloads; renderer identity only, DOCX renders inside Attendance. No renderer conversion/bot/external delivery claim.",
    }
    primary = None

    def docker(*args, check=True, seconds=15):
        return subprocess.run(["docker", *args], capture_output=True, text=True, check=check, timeout=seconds)

    with tempfile.TemporaryDirectory(prefix=name + "-", dir=os.environ["RUNNER_TEMP"]) as tmp:
        private = Path(tmp)
        data = private / "data"
        data.mkdir()
        cfg = private / "prometheus.yml"
        panel_rules = private / "panel-rules.yml"
        panel_rules.write_text(yaml.safe_dump(e2e_panel_rules(expected), sort_keys=False))
        config["rule_files"] = ["/etc/prometheus/panel-rules.yml"]
        cfg.write_text(yaml.safe_dump(config, sort_keys=False))
        safe["scrape_config_sha256"] = hashlib.sha256(cfg.read_bytes()).hexdigest()
        safe["panel_rules_sha256"] = hashlib.sha256(panel_rules.read_bytes()).hexdigest()
        safe["resources"].update(scrape_interval_seconds=5, sample_limit_per_target=3000, retained_series_guard=16000)
        browser = None
        attempted = False
        address = None

        def guard_disk():
            used = sum(p.stat().st_blocks * 512 for p in data.rglob("*") if p.is_file())
            safe["resources"]["peak_tsdb_allocated_bytes"] = max(safe["resources"]["peak_tsdb_allocated_bytes"], used)
            assert used <= 512 * 1024**2 and shutil.disk_usage(private).free >= 4 * 1024**3

        def api(path):
            request = urllib.request.Request("http://" + address + path, headers={"Accept": "application/json"})
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=5) as response:
                return json.load(response)

        def query(expression):
            response = api("/api/v1/query?" + urllib.parse.urlencode({"query": expression}))
            assert response["status"] == "success" and response["data"]["resultType"] == "vector"
            return response["data"]["result"]

        def number(expression, allow_absent=False):
            rows = query(expression)
            if not rows and allow_absent:
                return 0.0
            assert len(rows) == 1
            value = float(rows[0]["value"][1])
            assert math.isfinite(value)
            return value

        def wait(predicate, seconds=30):
            end = time.monotonic() + seconds
            while time.monotonic() < end:
                guard_disk()
                try:
                    if predicate():
                        return
                except (OSError, ValueError, KeyError, AssertionError):
                    pass
                time.sleep(1)
            raise AssertionError("Actual E2E telemetry did not reach the required state")

        def identities():
            targets = api("/api/v1/targets")["data"]["activeTargets"]
            safe["target_up"] = {
                job: any(
                    target.get("labels", {}).get("job") == job and target.get("health") == "up" for target in targets
                )
                for job in expected
            }
            assert len(targets) == 8
            for target in targets:
                job = target["labels"]["job"]
                assert job in expected and target["labels"]["instance"] == job + ":" + str(expected[job])
                assert target["health"] == "up" and target["lastError"] == ""
            rows = query("process_start_time_seconds")
            safe["actual_applications"] = len(rows)
            assert len(rows) == 8 and {r["metric"]["job"] for r in rows} == set(expected)
            processes = {}
            for row in rows:
                labels = row["metric"]
                job = labels["job"]
                assert labels["application"] == labels["service"] == job
                assert labels["instance"] == job + ":" + str(expected[job])
                # Version comes from the actual application's common metric tag,
                # never from a Prometheus target/relabel replacement.
                assert labels["version"] == source_sha and labels["environment"] == "synthetic"
                assert not (
                    {"requestId", "traceId", "spanId", "jobId", "userId", "request_id", "trace_id"} & set(labels)
                )
                processes[job] = float(row["value"][1])
            safe["actual_applications"] = len(rows)
            return processes

        counters = {
            "accepted_jobs": 'sum(rct_export_jobs_accepted_total{job="auth-service",kind="HEADMAN_WEEKLY_SELECTED"})',
            "terminal_success": 'sum(rct_export_jobs_terminal_total{job="auth-service",kind="HEADMAN_WEEKLY_SELECTED",outcome="success",reason="none"})',
            "success_within60s": 'sum(rct_export_jobs_duration_seconds_bucket{job="auth-service",kind="HEADMAN_WEEKLY_SELECTED",outcome="success",le="60.0"})',
            "successful_docx_reports": 'sum(attendance_report_duration_seconds_count{job="attendance-service",report="weekly",format="docx",outcome="success"})',
        }
        try:
            assert shutil.disk_usage(private).free >= 4 * 1024**3
            available = next(
                int(line.split()[1]) * 1024
                for line in Path("/proc/meminfo").read_text().splitlines()
                if line.startswith("MemAvailable:")
            )
            assert available >= 256 * 1024**2
            assert (
                docker(
                    "network",
                    "inspect",
                    "--format",
                    '{{index .Labels "com.docker.compose.project"}}',
                    "rct-e2e_private_net",
                ).stdout.strip()
                == "rct-e2e"
            )
            safe["phase"] = "START_PROMETHEUS"
            attempted = True
            docker(
                "run",
                "--detach",
                "--name",
                name,
                "--label",
                "rct.acceptance.owner=" + name,
                "--network",
                "rct-e2e_private_net",
                "--publish",
                "127.0.0.1::9090",
                "--memory",
                "192m",
                "--memory-reservation",
                "128m",
                "--cpus",
                "0.25",
                "--read-only",
                "--cap-drop",
                "ALL",
                "--security-opt",
                "no-new-privileges",
                "--user",
                str(os.getuid()) + ":" + str(os.getgid()),
                "--log-driver",
                "json-file",
                "--log-opt",
                "max-size=1m",
                "--log-opt",
                "max-file=1",
                "--volume",
                str(cfg) + ":/etc/prometheus/prometheus.yml:ro",
                "--volume",
                str(panel_rules) + ":/etc/prometheus/panel-rules.yml:ro",
                "--volume",
                str(data) + ":/prometheus",
                image,
                "--config.file=/etc/prometheus/prometheus.yml",
                "--storage.tsdb.path=/prometheus",
                "--storage.tsdb.retention.time=1h",
                "--storage.tsdb.retention.size=32MB",
                "--storage.tsdb.wal-segment-size=16MB",
                "--query.max-samples=100000",
                "--query.max-concurrency=1",
                seconds=60,
            )
            address = docker("port", name, "9090/tcp").stdout.strip()
            assert re.fullmatch(r"127\.0\.0\.1:[0-9]{2,5}", address)
            safe["phase"] = "BASELINE_REAL_TARGETS"
            wait(identities, 45)
            before_processes = identities()
            baseline = {
                key: number(expr, allow_absent=key == "successful_docx_reports") for key, expr in counters.items()
            }
            safe["phase"] = "BROWSER_REAL_DOWNLOADS"
            with (private / "browser.private.log").open("wb") as log:
                browser = subprocess.Popen(
                    ["npx", "playwright", "test", "--config", "playwright.real-mobile.config.ts", "--project=chromium"],
                    cwd=ROOT / "tests/e2e",
                    stdout=log,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                )
                deadline = time.monotonic() + 390
                while browser.poll() is None:
                    guard_disk()
                    assert time.monotonic() < deadline
                    time.sleep(1)
                assert browser.returncode == 0
            result_path = run / "artifacts/safe-results.json"
            assert result_path.is_file() and not result_path.is_symlink() and result_path.stat().st_size <= 1024 * 1024
            outcome = json.loads(result_path.read_text())
            assert outcome["status"] == "passed" and outcome["runnerErrors"] == 0 and outcome["unsafeArtifact"] is False
            assert len(outcome["results"]) == 1
            case = outcome["results"][0]
            assert case["file"] == "mobile-real-server.spec.ts" and case["project"] == "chromium"
            assert case["status"] == case["expectedStatus"] == "passed" and case["errorCount"] == 0
            safe["phase"] = "POST_DOWNLOAD_METRIC_DELTAS"

            def observed_deltas():
                ready = True
                for key, expression in counters.items():
                    try:
                        actual = number(expression) - baseline[key]
                    except (OSError, ValueError, KeyError, AssertionError):
                        actual = None
                    safe["metric_deltas"][key] = {"expected": 2, "actual": actual}
                    ready = ready and actual is not None and actual >= 2
                return ready

            wait(observed_deltas, 30)
            assert identities() == before_processes, "A real application restarted during the observed scenario"
            for key, expression in counters.items():
                delta = number(expression) - baseline[key]
                safe["metric_deltas"][key] = {"expected": 2, "actual": delta}
                assert math.isclose(delta, 2, abs_tol=1e-9)
            safe["phase"] = "LIVE_PANEL_QUERIES"
            wait(lambda: bool(query('rct_sli:report_p95_seconds5m{job="attendance-service",format="docx"}')), 30)
            e2e_live_panel_queries(query, expected, safe)
            retained = api(
                "/api/v1/series?" + urllib.parse.urlencode({"match[]": '{job=~"' + "|".join(expected) + '"}'})
            )["data"]
            assert 8 <= len(retained) <= 16000
            forbidden = {
                "requestid",
                "traceid",
                "spanid",
                "jobid",
                "userid",
                "studentid",
                "email",
                "token",
                "authorization",
                "password",
            }
            for labels in retained:
                assert not (forbidden & {key.replace("_", "").lower() for key in labels})
                for key, value in labels.items():
                    if key not in {"version", "__name__"}:
                        assert not re.search(
                            r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])",
                            value,
                            re.IGNORECASE,
                        )
            safe["retained_contract_series"] = len(retained)
            safe["identities"] = [
                {"job": job, "application": job, "instance": job + ":" + str(port), "version": source_sha}
                for job, port in expected.items()
            ]
            guard_disk()
            safe["result"] = "PASS"
            safe["phase"] = "COMPLETE"
        except (AssertionError, subprocess.SubprocessError, OSError, ValueError, KeyError) as error:
            primary = error
            safe["error_kind"] = (
                type(error).__name__
                if type(error).__name__
                in {"AssertionError", "CalledProcessError", "TimeoutExpired", "OSError", "HTTPError", "URLError"}
                else "OTHER"
            )
        finally:
            try:
                if browser is not None and browser.poll() is None:
                    try:
                        os.killpg(browser.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
                    try:
                        browser.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(browser.pid, signal.SIGKILL)
                        browser.wait(timeout=10)
            except (OSError, subprocess.SubprocessError) as error:
                safe["cleanup"] = "FAIL_BROWSER_PROCESS"
                if primary is None:
                    primary = error
            try:
                if attempted:
                    owned = docker(
                        "inspect", "--format", '{{index .Config.Labels "rct.acceptance.owner"}}', name, check=False
                    )
                    if owned.returncode == 0 and owned.stdout.strip() == name:
                        assert docker("rm", "--force", name, check=False).returncode == 0
                        safe["cleanup"] = "PASS"
                    else:
                        missing = docker("ps", "--all", "--quiet", "--filter", "label=rct.acceptance.owner=" + name)
                        assert missing.stdout.strip() == ""
                        safe["cleanup"] = "PASS_ALREADY_ABSENT"
                else:
                    safe["cleanup"] = "NOT_STARTED"
            except (AssertionError, OSError, subprocess.SubprocessError) as error:
                safe["cleanup"] = "FAIL"
                if primary is None:
                    primary = error
            if primary is not None:
                safe["result"] = "FAIL"
            report.write_text(json.dumps(safe, indent=2) + "\n")
    print(
        json.dumps(
            {
                "result": safe["result"],
                "actual_applications": safe["actual_applications"],
                "phase": safe["phase"],
                "cleanup": safe["cleanup"],
            }
        )
    )
    if primary is not None:
        raise SystemExit(1)


def main():
    assert os.environ.get("RCT_MONITORING_RUNTIME") == "isolated-ci", "dedicated CI VM required"
    assert shutil.disk_usage("/tmp").free >= 4 * 1024**3
    source = yaml.safe_load((ROOT / "docker-compose.prod.yml").read_text())
    project = "rct-pipeline-" + uuid.uuid4().hex[:12]
    auth = "Basic " + base64.b64encode(b"admin:synthetic-grafana-only").decode()
    checks = []
    files = [
        "docker-compose.prod.yml",
        "infra/prometheus/prometheus.yml",
        "infra/prometheus/rules/service-objectives.yml",
        "infra/prometheus/tests/check_formulas.py",
        "infra/alertmanager/alertmanager.yml",
        "infra/loki/loki.yml",
        "infra/tempo/tempo.yml",
        "infra/promtail/promtail.yml",
        "infra/grafana/provisioning/datasources/loki.yml",
        "infra/grafana/provisioning/datasources/tempo.yml",
        "services/notification-bot/bot/__main__.py",
        "services/notification-bot/bot/observability.py",
        "services/notification-bot/bot/services/send_queue.py",
        "services/notification-bot/requirements.txt",
    ]
    hashes = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in files}
    with tempfile.TemporaryDirectory(prefix=project + "-") as tmp:
        td = Path(tmp)
        config = {"services": {}, "networks": {"private_net": {}}, "volumes": {}, "secrets": {}}
        names = ["prometheus", "alertmanager", "grafana", "loki", "tempo", "promtail", "blackbox-exporter"]
        for name in names:
            service = copy.deepcopy(source["services"][name])
            service.pop("container_name", None)
            dependencies = service.pop("depends_on", None)
            if isinstance(dependencies, dict):
                service["depends_on"] = {key: value for key, value in dependencies.items() if key in names}
            elif dependencies:
                service["depends_on"] = [key for key in dependencies if key in names]
            service["cpus"] = 0.5
            service["environment"] = (
                {
                    "GF_SECURITY_ADMIN_PASSWORD": "synthetic-grafana-only",
                    "GF_PATHS_PROVISIONING": "/etc/grafana/provisioning",
                }
                if name == "grafana"
                else {}
            )
            mounts = []
            for volume in service.get("volumes", []):
                host, target, *options = volume.split(":")
                if host.startswith("./"):
                    host = str(ROOT / host[2:])
                elif not host.startswith("/"):
                    config["volumes"][host] = {}
                mounts.append(":".join([host, target] + options))
            service["volumes"] = mounts
            if name == "promtail":
                service["environment"]["COMPOSE_PROJECT_NAME"] = project
            # These API ports exist only on loopback in the isolated test project.
            internal = {
                "prometheus": "9090",
                "alertmanager": "9093",
                "grafana": "3000",
                "loki": "3100",
                "tempo": "3200",
                "promtail": "9080",
                "nginx": "8080",
                "blackbox-exporter": "9115",
            }[name]
            service["ports"] = [f"127.0.0.1::{internal}"]
            if name == "tempo":
                service["ports"].append("127.0.0.1::4318")
            config["services"][name] = service
        # Use production images, collectors, host mounts and capabilities exactly.
        # Host capability absence is a failed precondition, never synthetic data.
        _STATE["phase"] = "HOST_EXPORTER_PRECONDITIONS"
        host_paths = ["/proc", "/sys", "/var/run/dbus", "/var/lib/docker", "/dev/disk", "/dev/kmsg"]
        missing_host_paths = [path for path in host_paths if not Path(path).exists()]
        _STATE["missing_host_paths"] = missing_host_paths
        assert not missing_host_paths, "host-exporter required mounts/devices unavailable: " + ",".join(
            missing_host_paths
        )
        available_kib = int(
            next(
                line.split()[1]
                for line in Path("/proc/meminfo").read_text().splitlines()
                if line.startswith("MemAvailable:")
            )
        )
        assert available_kib >= 2 * 1024**2, "2GiB available before telemetry cohort required"
        for name in ["node-exporter", "cadvisor"]:
            service = copy.deepcopy(source["services"][name])
            service.pop("container_name", None)
            service["cpus"] = 0.25
            config["services"][name] = service
        control = td / "bot-control"
        control.mkdir(mode=0o700)
        config["services"]["notification-bot"] = {
            "image": PYTHON_IMAGE,
            "working_dir": "/application",
            "command": [
                "sh",
                "-ec",
                "python -m pip install --target /tmp/bot-deps --no-cache-dir --disable-pip-version-check -r requirements.txt PyYAML==6.0.3 >/control/install.private.log 2>&1; exec python /repository/infra/prometheus/tests/check_pipeline.py --bot-metrics-worker",
            ],
            "environment": {
                "PYTHONPATH": "/tmp/bot-deps:/application:/repository/infra/prometheus/tests",
                "PYTHONDONTWRITEBYTECODE": "1",
                "OTEL_SDK_DISABLED": "true",
                "DEPLOYMENT_ENVIRONMENT": "synthetic",
                "SERVICE_VERSION": os.environ["GITHUB_SHA"],
            },
            "volumes": [
                str(ROOT / "services/notification-bot") + ":/application:ro",
                str(ROOT / "infra/prometheus/tests") + ":/repository/infra/prometheus/tests:ro",
                str(control) + ":/control",
            ],
            "networks": ["private_net"],
            "ports": ["127.0.0.1::8081"],
            "user": str(os.getuid()) + ":" + str(os.getgid()),
            "mem_limit": "256m",
            "cpus": 0.5,
            "cap_drop": ["ALL"],
            "security_opt": ["no-new-privileges:true"],
            "logging": {"driver": "json-file", "options": {"max-size": "1m", "max-file": "1"}},
        }
        # Seed exactly the retained finite30d rollup inventory. The running
        # production-version engine enforces its unchanged500000sample cap.
        _, budget = long_window_fixture()
        seed_end = (int(time.time()) // 1800) * 1800 - 4 * 3600
        seed = td / "slo-budget.openmetrics"
        with seed.open("w") as output:
            for name, value in [
                ("http_eligible_events30m", 1000),
                ("http_error_events30m", 1),
                ("http_window_timestamp_seconds", None),
                ("http_source_coverage_seconds30d", 2590200),
                ("http_source_samples30d", 1440),
            ]:
                metric = "rct_sli:" + name
                output.write("# TYPE " + metric + " gauge\n")
                for labels in slo_budget_inventory():
                    for i in range(1441):
                        timestamp = seed_end - 30 * 86400 + i * 1800
                        output.write(
                            metric
                            + "{"
                            + labels
                            + "} "
                            + str(timestamp if value is None else value)
                            + " "
                            + str(timestamp)
                            + "\n"
                        )
            output.write("# EOF\n")
        seed_db = td / "slo-budget-tsdb"
        seed_db.mkdir(mode=0o777)
        seed_db.chmod(0o777)  # Synthetic-only owned data, writable by image UID65534.
        subprocess.run(
            [
                os.environ.get("PROMTOOL", "promtool"),
                "tsdb",
                "create-blocks-from",
                "openmetrics",
                "--quiet",
                str(seed),
                str(seed_db),
            ],
            capture_output=True,
            check=True,
            timeout=180,
        )
        config["services"]["prometheus"]["volumes"] = [
            mount for mount in config["services"]["prometheus"]["volumes"] if mount.split(":")[1] != "/prometheus"
        ] + [str(seed_db) + ":/prometheus"]
        # Match the owned bind-directory UID; this cohort does not claim OS-user isolation.
        config["services"]["prometheus"]["user"] = str(os.getuid()) + ":" + str(os.getgid())
        slo_results = []
        (td / "alert-token").write_text("synthetic-alert-receiver-only")
        config["secrets"]["alert-webhook-token"] = {"file": str(td / "alert-token")}
        # Only cohort targets are retained; no application target is impersonated.
        prom = yaml.safe_load((ROOT / "infra/prometheus/prometheus.yml").read_text())
        prom["scrape_configs"] = [
            x
            for x in prom["scrape_configs"]
            if x["job_name"] in names + ["node-exporter", "cadvisor", "notification-bot"]
        ]
        for job in prom["scrape_configs"]:
            if job["job_name"] in ["node-exporter", "cadvisor", "notification-bot"]:
                job.update(scrape_interval="1s", scrape_timeout="1s", sample_limit=30000, body_size_limit="16MB")
        prom["scrape_configs"].append(
            {
                "job_name": "synthetic-fixture",
                "honor_labels": True,
                "scrape_interval": "1s",
                "static_configs": [{"targets": ["notification-web:9094"]}],
            }
        )
        prom["rule_files"] = []
        for job in prom["scrape_configs"]:
            for target in job.get("static_configs", []):
                target.get("labels", {}).update(environment="synthetic")
        (td / "prometheus.yml").write_text(yaml.safe_dump(prom, sort_keys=False))
        config["services"]["prometheus"]["volumes"] = [
            x for x in config["services"]["prometheus"]["volumes"] if "/etc/prometheus/prometheus.yml" not in x
        ] + [str(td / "prometheus.yml") + ":/etc/prometheus/prometheus.yml:ro"]
        app_logs = td / "application-logs"
        app_logs.mkdir(mode=0o700)
        promtail = yaml.safe_load((ROOT / "infra/promtail/promtail.yml").read_text())
        for relabel in promtail["scrape_configs"][0]["relabel_configs"]:
            if relabel.get("target_label") == "environment":
                relabel["replacement"] = "synthetic"
        for service in ["api-gateway", "mobile-bff"]:
            promtail["scrape_configs"].append(
                {
                    "job_name": "actual-" + service,
                    "static_configs": [
                        {
                            "targets": ["localhost"],
                            "labels": {
                                "service": service,
                                "environment": "synthetic",
                                "__path__": "/application-logs/" + service + ".log",
                            },
                        }
                    ],
                }
            )
        (td / "promtail.yml").write_text(yaml.safe_dump(promtail, sort_keys=False))
        config["services"]["promtail"]["volumes"] = [
            x for x in config["services"]["promtail"]["volumes"] if "/etc/promtail/promtail.yml" not in x
        ] + [str(td / "promtail.yml") + ":/etc/promtail/promtail.yml:ro"]
        config["services"]["promtail"]["volumes"].append(str(app_logs) + ":/application-logs:ro")
        config["services"]["notification-web"] = {
            "image": PYTHON_IMAGE,
            "command": ["python", "/fixture.py"],
            "volumes": [str(ROOT / "infra/prometheus/tests/fixture.py") + ":/fixture.py:ro"],
            "environment": {"SYNTHETIC_RECEIVER_TOKEN": "synthetic-alert-receiver-only"},
            "networks": ["private_net"],
            "ports": ["127.0.0.1::9094"],
            "mem_limit": "64m",
            "cpus": 0.25,
            "logging": {"driver": "json-file", "options": {"max-size": "1m", "max-file": "2"}},
        }
        # Exercise the exact changed external query/mutation boundaries, without
        # importing deployment TLS keys or starting the public ingress.
        nginx_source = (ROOT / "nginx/conf.d/default.conf").read_text()
        location_blocks = []
        for location in ["prometheus", "alertmanager"]:
            start = nginx_source.index("    location /" + location + "/ {")
            end = start
            depth = 0
            for end in range(start, len(nginx_source)):
                if nginx_source[end] == "{":
                    depth += 1
                elif nginx_source[end] == "}":
                    depth -= 1
                    if depth == 0:
                        break
            location_blocks.append(nginx_source[start : end + 1])
        (td / "htpasswd").write_text(
            "operator:{SHA}" + base64.b64encode(hashlib.sha1(b"synthetic-operator-only").digest()).decode() + "\n"
        )
        (td / "nginx.conf").write_text(
            "events {}\nhttp { access_log off; resolver 127.0.0.11 valid=5s; server { listen 8080; set $upstream_prometheus prometheus:9090; set $upstream_alertmanager alertmanager:9093;\n"
            + "\n".join(location_blocks)
            + "\n} }\n"
        )
        config["services"]["nginx"] = {
            "image": source["services"]["nginx"]["image"],
            "volumes": [
                str(td / "nginx.conf") + ":/etc/nginx/nginx.conf:ro",
                str(td / "htpasswd") + ":/etc/nginx/.htpasswd:ro",
            ],
            "networks": ["private_net"],
            "ports": ["127.0.0.1::8080"],
            "mem_limit": "64m",
            "cpus": 0.25,
        }
        # Promtail obtains service identity from the Compose service; use the same
        # service.name in the synthetic trace below, not a request/trace ID label.
        compose = td / "compose.yml"
        compose.write_text(yaml.safe_dump(config, sort_keys=False))
        prefix = ["docker", "compose", "--project-name", project, "-f", str(compose)]

        def docker(*args, check=True):
            return subprocess.run(prefix + list(args), text=True, capture_output=True, check=check)

        def api(name, path, body=None, headers=None, port=None):
            internal = (
                port
                or {
                    "grafana": "3000",
                    "prometheus": "9090",
                    "loki": "3100",
                    "tempo": "3200",
                    "notification-web": "9094",
                    "promtail": "9080",
                    "nginx": "8080",
                }[name]
            )
            address = docker("port", name, internal).stdout.strip()
            req = urllib.request.Request(
                "http://" + address + path,
                data=None if body is None else json.dumps(body).encode(),
                headers=dict({"Content-Type": "application/json", "Accept": "application/json"}, **(headers or {})),
            )
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=15) as response:
                payload = response.read()
                return json.loads(payload) if payload else None

        def wait(predicate, seconds=100):
            end = time.monotonic() + seconds
            while time.monotonic() < end:
                try:
                    if predicate():
                        return
                except (OSError, subprocess.CalledProcessError, ValueError, IndexError, KeyError):
                    pass
                time.sleep(1)
            raise AssertionError("Telemetry query/persistence did not reach expected state")

        def grafana(path, body=None):
            return api("grafana", "/grafana" + path, body, {"Authorization": auth})

        def dashboard_expression(filename, panel):
            data = json.loads((ROOT / "infra/grafana/provisioning/dashboards" / filename).read_text())
            expr = next(x for x in data["panels"] if x["id"] == panel)["targets"][0]["expr"]
            for name, value in [("$interval", "1m"), ("$environment", ".*"), ("$job", ".*"), ("$instance", ".*")]:
                expr = expr.replace(name, value)
            return expr

        def query(expr):
            result = grafana(
                "/api/ds/query",
                {
                    "from": str(int(time.time() * 1000) - 60000),
                    "to": str(int(time.time() * 1000)),
                    "queries": [
                        {
                            "refId": "A",
                            "datasource": {"uid": PROM_UID, "type": "prometheus"},
                            "expr": expr,
                            "instant": True,
                            "range": False,
                            "intervalMs": 1000,
                            "maxDataPoints": 1000,
                        }
                    ],
                },
            )["results"]["A"]
            assert not result.get("error"), "Grafana query error is not healthy zero"
            return [
                value
                for frame in result.get("frames", [])
                for field, values in zip(frame["schema"]["fields"], frame["data"]["values"])
                if field["type"] == "number"
                for value in values
                if value is not None
            ]

        def positions():
            cid = docker("ps", "--quiet", "promtail").stdout.strip()
            dest = td / "positions-copy.yml"
            subprocess.run(
                ["docker", "cp", cid + ":/var/lib/promtail/positions.yaml", str(dest)],
                capture_output=True,
                check=True,
            )
            return yaml.safe_load(dest.read_text())["positions"]

        def identifier(value, length):
            if value == "":
                return ""
            if re.fullmatch("[0-9a-f]{" + str(length * 2) + "}", value):
                return value
            decoded = base64.b64decode(value, validate=True)
            assert len(decoded) == length
            return decoded.hex()

        def collector_spans(trace):
            result = []
            for batch in api("tempo", "/api/traces/" + trace).get("batches", []):
                attributes = {
                    a["key"]: a["value"].get("stringValue") for a in batch.get("resource", {}).get("attributes", [])
                }
                for scope in batch.get("scopeSpans", batch.get("instrumentationLibrarySpans", [])):
                    for span in scope.get("spans", []):
                        result.append(
                            (
                                attributes.get("service.name"),
                                identifier(span["spanId"], 8),
                                identifier(span.get("parentSpanId", ""), 8).lstrip("0"),
                            )
                        )
            return set(result)

        def run_application_gradle(command, env, filename, seconds):
            with (td / filename).open("wb") as log:
                process = subprocess.Popen(
                    command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True
                )
                try:
                    result = process.wait(timeout=seconds)
                    if result:
                        raise subprocess.CalledProcessError(result, command)
                finally:
                    if process.poll() is None:
                        os.killpg(process.pid, signal.SIGTERM)
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            os.killpg(process.pid, signal.SIGKILL)
                            process.wait(timeout=10)

        def application_transport():
            receipt = td / "application-receipt.json"
            address = docker("port", "tempo", "4318").stdout.strip()
            env = dict(
                os.environ,
                RCT_TEST_OTLP_ENDPOINT="http://" + address + "/v1/traces",
                RCT_TEST_APP_LOG_DIR=str(app_logs),
                RCT_TEST_TELEMETRY_RECEIPT=str(receipt),
                JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2",
                DEPLOYMENT_ENVIRONMENT="synthetic",
                SERVICE_VERSION=os.environ["GITHUB_SHA"],
            )
            command = [
                "bash",
                str(ROOT / "gradlew"),
                ":services:api-gateway:compileJava",
                ":services:api-gateway:compileTestJava",
                ":services:api-gateway:integrationTest",
                "--tests",
                "*GatewayBffTracePropagationIT",
                "--no-daemon",
                "--max-workers=2",
                "-Dorg.gradle.jvmargs=-Xmx512m -XX:ActiveProcessorCount=2",
            ]
            run_application_gradle(command, env, "application-gradle.private.log", 300)
            assert receipt.is_file() and receipt.stat().st_size <= 1024 * 1024
            evidence = json.loads(receipt.read_text())
            assert evidence["schema"] == 1 and evidence["kind"] == "actual_application_transport"
            assert evidence["source_sha"] == os.environ["GITHUB_SHA"]
            assert {case["outcome"] for case in evidence["cases"]} == {"success", "error", "cancelled", "recovery"}
            for row in evidence["logs"]:
                assert row["file"] in {"api-gateway.log", "mobile-bff.log"}
                assert hashlib.sha256((app_logs / row["file"]).read_bytes()).hexdigest() == row["sha256"]

            span_count = 0
            for case in evidence["cases"]:
                trace = case["traceId"]
                request = case["requestId"]
                assert re.fullmatch("[0-9a-f]{32}", trace)
                assert str(uuid.UUID(request)) == request
                expected = {
                    (row["service"], row["spanId"], row["parentSpanId"].lstrip("0"))
                    for row in evidence["spans"]
                    if row["traceId"] == trace
                }
                assert expected
                wait(lambda expected=expected, trace=trace: expected <= collector_spans(trace), 120)
                span_count += len(expected)
                if case["outcome"] == "cancelled":
                    continue  # The actual cancellation graph is required; response logs may be absent.
                for service in ["api-gateway", "mobile-bff"]:
                    expression = (
                        '{service="'
                        + service
                        + '",environment="synthetic"} | json | requestId="'
                        + request
                        + '" | traceId="'
                        + trace
                        + '" | __error__=""'
                    )

                    def actual_logs(query=expression):
                        return api(
                            "loki",
                            "/loki/api/v1/query_range?"
                            + urllib.parse.urlencode(
                                {
                                    "query": query,
                                    "start": str(time.time_ns() - 900_000_000_000),
                                    "end": str(time.time_ns()),
                                    "limit": 20,
                                }
                            ),
                        )["data"]["result"]

                    wait(lambda: bool(actual_logs()), 120)
                    line = actual_logs()[0]["values"][0][1]
                    derived = grafana("/api/datasources/uid/loki")["jsonData"]["derivedFields"][0]
                    assert re.search(derived["matcherRegex"], line).group(1) == trace
                    links = grafana("/api/datasources/uid/tempo")["jsonData"]["tracesToLogsV2"]
                    expression = (
                        links["query"]
                        .replace('${__span.tags["service.name"]}', service)
                        .replace("${__trace.traceId}", trace)
                    )
                    assert actual_logs(expression)
            checks.append(
                "actual Gateway/BFF/TCPgRPC SDK spans→Tempo parent graph; requestId JSON→Loki; provisioned Grafana navigation"
            )
            return {
                "cases": len(evidence["cases"]),
                "collector_spans": span_count,
                "log_services": 2,
                "qualification": "Actual application transport with synthetic auth/generated Academic contract fixture; no business database or renderer proof",
            }

        def background_transport():
            receipt = td / "background-receipt.json"
            address = docker("port", "tempo", "4318").stdout.strip()
            env = dict(
                os.environ,
                RCT_TEST_OTLP_ENDPOINT="http://" + address + "/v1/traces",
                RCT_TEST_TELEMETRY_RECEIPT=str(receipt),
                JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2",
                DEPLOYMENT_ENVIRONMENT="synthetic",
                SERVICE_VERSION=os.environ["GITHUB_SHA"],
            )
            command = [
                "bash",
                str(ROOT / "gradlew"),
                ":services:attendance-service:attendance-app:integrationTest",
                "--tests",
                "*RabbitDecisionRetryIT.transientOutageAfterThreeAttemptsAutomaticallyRedrivesOriginalAfterThirtySeconds",
                "--no-daemon",
                "--max-workers=1",
                "-Dorg.gradle.jvmargs=-Xmx512m -XX:ActiveProcessorCount=2",
            ]
            run_application_gradle(command, env, "background-gradle.private.log", 600)
            assert receipt.is_file() and receipt.stat().st_size <= 1024 * 1024
            evidence = json.loads(receipt.read_text())
            assert evidence["schema"] == 1 and evidence["kind"] == "actual_application_background"
            assert evidence["source_sha"] == os.environ["GITHUB_SHA"]
            assert {case["outcome"] for case in evidence["cases"]} == {"recovered", "ordinary"}
            span_count = 0
            for case in evidence["cases"]:
                trace = case["traceId"]
                assert re.fullmatch("[0-9a-f]{32}", trace)
                assert str(uuid.UUID(case["requestId"])) == case["requestId"]
                expected = {
                    (row["service"], row["spanId"], row["parentSpanId"].lstrip("0"))
                    for row in evidence["spans"]
                    if row["traceId"] == trace
                }
                assert expected and any(row[1] == case["workerSpanId"] for row in expected)
                wait(lambda expected=expected, trace=trace: expected <= collector_spans(trace), 120)
                span_count += len(expected)
            checks.append(
                "actual RabbitTemplate/listener30s delayed retry+Mongo dedup spans→Tempo parent graph; independent next event"
            )
            return {
                "cases": len(evidence["cases"]),
                "collector_spans": span_count,
                "qualification": "Real Rabbit/Mongo/Redis transport and dedup; synthetic origin and mocked StudentRequestService effect, not business PASS",
            }

        started = time.monotonic()
        try:
            _STATE["phase"] = "PIPELINE_START"
            docker("up", "-d")
            wait(lambda: grafana("/api/health")["database"] == "ok", 120)

            def samples(expression):
                response = api("prometheus", "/api/v1/query?" + urllib.parse.urlencode({"query": expression}))
                assert response["status"] == "success"
                return response["data"]["result"]

            _STATE["phase"] = "HOST_AND_BOT_METRICS"
            for job in ["node-exporter", "cadvisor", "notification-bot"]:
                wait(lambda job=job: len(samples('up{job="' + job + '"} == 1')) == 1, 180)
                identity = samples('up{job="' + job + '"} == 1')[0]["metric"]
                port = {"node-exporter": 9100, "cadvisor": 8080, "notification-bot": 8081}[job]
                assert identity["instance"] == job + ":" + str(port)
                assert identity["service"] == job and identity["environment"] == "synthetic"
            try:
                api("notification-bot", "/health", port="8081")
                raise AssertionError("bot polling is absent; health must not be fabricated UP")
            except urllib.error.HTTPError as error:
                assert error.code == 503
            for expression in [
                'node_memory_MemTotal_bytes{job="node-exporter"} > 0',
                'node_filesystem_avail_bytes{job="node-exporter",mountpoint="/"} > 0',
                'node_cpu_seconds_total{job="node-exporter",mode="idle"} > 0',
                'container_memory_usage_bytes{job="cadvisor",container_label_com_docker_compose_project="'
                + project
                + '"} > 0',
                'notification_bot_send_tasks_completed_total{job="notification-bot"} == 0',
                'notification_bot_send_workers_alive{job="notification-bot"} == 2',
                'notification_bot_task_alive{job="notification-bot",task="polling"} == 0',
            ]:
                wait(lambda expression=expression: bool(samples(expression)))
            # Polling/consumer were deliberately never started: health remains503,
            # while the real metrics endpoint and queue execution are observable.
            (control / "start").write_text("GO")
            wait(lambda: (control / "done").exists())
            wait(lambda: len(samples('notification_bot_send_tasks_completed_total{job="notification-bot"} == 1')) == 1)
            assert len(samples('notification_bot_send_tasks_exhausted_total{job="notification-bot"} == 0')) == 1
            assert len(samples('notification_bot_send_queue_depth{job="notification-bot"} == 0')) == 1
            checks.append(
                {
                    "actual_host_exporters": ["node-exporter", "cadvisor"],
                    "available_memory_kib_before_start": available_kib,
                    "bot_actual_queue_completion_delta": 1,
                    "qualification": "Production HTTP/SDK/send queue with a local provider; no Telegram receipt or broker consumer claim",
                }
            )
            # Release640MiB limits before the existing Java/broker cohort peaks.
            docker("stop", "notification-bot", "node-exporter", "cadvisor")
            _STATE["phase"] = "SLO_ENGINE_AND_GRAFANA"
            flags = api("prometheus", "/api/v1/status/flags")["data"]
            assert int(flags["query.max-samples"]) == budget["production_max_samples"] == 500000
            for name, expected in [
                ("rct_sli:http_availability30d", 0.999),
                ("rct_sli:http_source_coverage_seconds30d", 2590200),
                ("rct_sli:http_source_samples30d", 1440),
            ]:
                response = api(
                    "prometheus",
                    "/api/v1/query?" + urllib.parse.urlencode({"query": record_expr(name), "time": seed_end}),
                )
                assert response["status"] == "success"
                values = [float(row["value"][1]) for row in response["data"]["result"]]
                assert len(values) == budget["series"] == 40
                assert all(math.isclose(value, expected, rel_tol=1e-10, abs_tol=1e-10) for value in values)
                slo_results.append(
                    {
                        "record": name,
                        "series": len(values),
                        "expected": expected,
                        "actual_min": min(values),
                        "actual_max": max(values),
                    }
                )
            checks.append("actual500000sample engine: full30d40class rollup queries and coverage")
            for uid in [PROM_UID, "loki", "tempo"]:
                assert grafana("/api/datasources/uid/" + uid)["uid"] == uid
            try:
                api("grafana", "/grafana/api/datasources")
            except urllib.error.HTTPError as error:
                assert error.code == 401
            else:
                raise AssertionError("Grafana datasource API requires authentication")
            for uid in ["business-kpis-m04", "grpc-latency-m05", "rct-logs-overview"]:
                assert grafana("/api/dashboards/uid/" + uid)["meta"]["provisioned"]
            operator_auth = "Basic " + base64.b64encode(b"operator:synthetic-operator-only").decode()
            wait(
                lambda: (
                    api("nginx", "/prometheus/api/v1/query?query=up", headers={"Authorization": operator_auth})[
                        "status"
                    ]
                    == "success"
                ),
                60,
            )
            assert isinstance(
                api("nginx", "/alertmanager/api/v2/alerts", headers={"Authorization": operator_auth}), list
            )
            for path, body, status in [
                ("/prometheus/api/v1/query", {"query": "up"}, 403),
                ("/alertmanager/api/v2/silences", {}, 403),
                ("/prometheus/api/v1/query?query=up", None, 401),
            ]:
                try:
                    api("nginx", path, body, {} if status == 401 else {"Authorization": operator_auth})
                except urllib.error.HTTPError as error:
                    assert error.code == status
                else:
                    raise AssertionError("external monitoring mutation/auth boundary accepted forbidden request")
            checks.append(
                {
                    "external_monitoring_status": {
                        "authenticated_query": 200,
                        "unauthenticated_query": 401,
                        "query_post": 403,
                        "silence_mutation": 403,
                    }
                }
            )
            checks.append("datasource UIDs/provisioned dashboards and unauthenticated401")
            expected = [
                ("docker-monitoring.json", 26, [0.95]),
                ("docker-monitoring.json", 35, [25]),
                ("business-kpis.json", 6, [0.1, 0.95]),
            ]
            for filename, panel, values in expected:
                wait(
                    lambda filename=filename, panel=panel, values=values: (
                        len(query(dashboard_expression(filename, panel))) == len(values)
                    )
                )
                actual = sorted(query(dashboard_expression(filename, panel)))
                assert all(math.isclose(a, b, rel_tol=1e-6) for a, b in zip(actual, sorted(values))), (
                    filename,
                    panel,
                    actual,
                )
                checks.append({"query_panel": panel, "expected": sorted(values), "actual": actual})
            assert query("missing_synthetic_metric") == [], "missing input must remain no data"
            try:
                query("sum(")
            except (AssertionError, urllib.error.HTTPError):
                pass
            else:
                raise AssertionError("invalid query must produce a query error")
            trace = uuid.uuid4().hex
            span = uuid.uuid4().hex[:16]
            request_id = str(uuid.uuid4())
            now = time.time_ns()
            trace_body = {
                "resourceSpans": [
                    {
                        "resource": {
                            "attributes": [{"key": "service.name", "value": {"stringValue": "notification-web"}}]
                        },
                        "scopeSpans": [
                            {
                                "scope": {"name": "synthetic-ci-only"},
                                "spans": [
                                    {
                                        "traceId": trace,
                                        "spanId": span,
                                        "name": "synthetic observability contract",
                                        "kind": 2,
                                        "startTimeUnixNano": str(now - 10000000),
                                        "endTimeUnixNano": str(now),
                                        "attributes": [{"key": "requestId", "value": {"stringValue": request_id}}],
                                        "status": {"code": 2},
                                    }
                                ],
                            }
                        ],
                    }
                ]
            }
            api("tempo", "/v1/traces", trace_body, port="4318")
            api("notification-web", "/emit", {"traceId": trace, "spanId": span, "requestId": request_id})
            loki_query = (
                '{service="notification-web",environment="synthetic"} | json | requestId="'
                + request_id
                + '" | __error__=""'
            )

            def logs():
                return api(
                    "loki",
                    "/loki/api/v1/query_range?"
                    + urllib.parse.urlencode(
                        {
                            "query": loki_query,
                            "start": str(now - 60000000000),
                            "end": str(time.time_ns()),
                            "limit": "10",
                        }
                    ),
                )["data"]["result"]

            wait(lambda: bool(logs()), 120)
            wait(lambda: bool(api("tempo", "/api/traces/" + trace).get("batches")), 120)
            line = logs()[0]["values"][0][1]
            derived = grafana("/api/datasources/uid/loki")["jsonData"]["derivedFields"][0]
            assert re.search(derived["matcherRegex"], line).group(1) == trace
            assert derived["datasourceUid"] == "tempo"
            to_logs = grafana("/api/datasources/uid/tempo")["jsonData"]["tracesToLogsV2"]
            assert to_logs["datasourceUid"] == "loki" and to_logs["customQuery"]
            # Check the actual provisioned custom link interpolates into a query
            # which finds the same emitted log line, including dotted service.name.
            actual_query = (
                to_logs["query"]
                .replace('${__span.tags["service.name"]}', "notification-web")
                .replace("${__trace.traceId}", trace)
            )
            linked = api(
                "loki",
                "/loki/api/v1/query_range?"
                + urllib.parse.urlencode(
                    {"query": actual_query, "start": str(now - 60000000000), "end": str(time.time_ns()), "limit": "10"}
                ),
            )["data"]["result"]
            assert linked and json.loads(linked[0]["values"][0][1])["requestId"] == request_id
            checks.append("requestId JSON search and actual provisioned logs↔trace queries")
            fixture_cid = docker("ps", "--quiet", "notification-web").stdout.strip()
            wait(lambda: any(fixture_cid in key for key in positions()), 30)
            checkpoint = positions()
            fixture_keys = {key for key in checkpoint if fixture_cid in key}
            assert fixture_keys
            docker("up", "-d", "--force-recreate", "--no-deps", "promtail", "loki", "tempo", "grafana")
            wait(lambda: grafana("/api/health")["database"] == "ok", 120)
            wait(lambda: bool(logs()), 120)
            wait(lambda: bool(api("tempo", "/api/traces/" + trace).get("batches")), 120)
            after = positions()
            assert fixture_keys <= set(after) and all(int(after[k]) >= int(checkpoint[k]) for k in fixture_keys), (
                "persisted fixture log checkpoint did not survive"
            )
            assert grafana("/api/datasources/uid/loki")["uid"] == "loki"
            checks.append("Loki/Tempo log+trace, Promtail positions and Grafana provisioning survive owned recreate")
            _STATE["phase"] = "ACTUAL_JAVA_TRANSPORT"
            app_result = application_transport()
            _STATE["phase"] = "ACTUAL_BACKGROUND_TRANSPORT"
            background_result = background_transport()
            result = {
                "result": "PASS",
                "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "config_sha256": hashes,
                "checks": checks,
                "slo_30d_budget": dict(budget, queries=slo_results),
                "actual_application_transport": app_result,
                "actual_background_transport": background_result,
                "elapsed_seconds": round(time.monotonic() - started, 2),
            }
        finally:
            primary = sys.exc_info()[0]
            _STATE["cleanup"] = "STARTED"
            try:
                cleanup = docker("down", "--volumes", "--remove-orphans", check=False)
                remaining = docker("ps", "--quiet", check=False)
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
    if sys.argv[1:] == ["--bot-metrics-worker"]:
        import asyncio

        asyncio.run(bot_metrics_worker())
    elif sys.argv[1:] == ["--e2e-async-browser"]:
        e2e_async_browser()
    else:
        assert not sys.argv[1:]
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
                        "missing_host_paths": _STATE.get("missing_host_paths", []),
                    }
                )
            )
            sys.exit(1)
