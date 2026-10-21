"""Finite Grafana/Loki/Tempo/Alloy contract using exact production versions and config.

Synthetic telemetry proves queries, links and persistence; it does not stand in
for the application/broker trace required by M01/M04. Only an owned CI project runs.
"""

import base64
import copy
import hashlib
import http.client
import ipaddress
import json
import math
import os
import re
import shutil
import signal
import socket
import ssl
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml
from check_formulas import long_window_fixture, record_expr, slo_budget_inventory

ROOT = Path(__file__).resolve().parents[3]
_STATE = {"phase": "PRECHECK_OR_CONFIG", "cleanup": "NOT_STARTED"}
PROM_UID = "PBFA97CFB590B2093"
PYTHON_IMAGE = "python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c"


_BACKGROUND_NATIVE_SOURCE_HASHES = {
    "build.gradle.kts": "dcdaa1dd36c43ddd8d8722060e4448aa6fccb04281618fd304087b3d533469e7",
    "settings.gradle.kts": "0da039246607505fbd6c8c3c2340345dab96f4051f6a5fc49e475c864a7b0617",
    "services/attendance-service/attendance-app/build.gradle.kts": "2c3680f5666fd30dc168ba17ef23ef60fe4f4ac5316e055d9a7ff8e429faecf2",
    "services/attendance-service/attendance-api-contract/build.gradle.kts": "e2c1d174ecd031671cbd78326566ca3bda1eea875ef3ecf1eee2d62b6c674038",
    "services/shared/shared-web/build.gradle.kts": "3b763bce4032f304d1f43fdd226c0bfbe14531f35a2a056578aa348b247e0bd3",
    "services/shared/shared-web-api/build.gradle.kts": "88c458ed5bac640ad3034b1c89b33a331e5540f51521ce7215aee28eafd7f5ee",
    "services/shared/shared-outbox/build.gradle.kts": "83f7f7084fd46ac9300a81c12ebd871c15fbb1e8647bcf95a1c80e1d4b387faf",
    "services/shared/shared-events/build.gradle.kts": "f97f06fa555c9fe561f011f511bbb9ddf24f92c2e51619fdea4097a7ef7a7396",
    "services/shared/shared-security/build.gradle.kts": "1d07782717df2ac9a82495805d45606abec1721fc536f9bc3005a22ba34ced8c",
    "services/shared/shared-observability/build.gradle.kts": "866b614d975477b5acbe96e5b07143543d34ae180a5d72606c5ee12527bb3083",
    "services/shared/shared-logback/build.gradle.kts": "c61998f894025cc45afa4206d2e7e54b389d8565059cf8ac8d58e941acabf25a",
    "services/shared/shared-test-containers/build.gradle.kts": "53def03edcae049b0adb06bb13b16ebe3cde52014f9148b0d38162ce9a01ebed"
}
_BACKGROUND_NATIVE_TASK_STAGES = {
    "Execution failed for task ':services:attendance-service:attendance-app:compileJava'.": "ATTENDANCE_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:attendance-service:attendance-api-contract:compileJava'.": "CONTRACT_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-web:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-web-api:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-outbox:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-events:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-security:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-observability:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-logback:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-test-containers:compileJava'.": "SHARED_MAIN_COMPILE_TASK",
    "Execution failed for task ':services:attendance-service:attendance-app:compileTestJava'.": "ATTENDANCE_TEST_COMPILE_TASK",
    "Execution failed for task ':services:shared:shared-test-containers:compileTestFixturesJava'.": "TEST_FIXTURE_COMPILE_TASK",
    "Execution failed for task ':services:attendance-service:attendance-app:extractIncludeProto'.": "ATTENDANCE_PROTO_TASK",
    "Execution failed for task ':services:attendance-service:attendance-app:extractProto'.": "ATTENDANCE_PROTO_TASK",
    "Execution failed for task ':services:attendance-service:attendance-app:generateProto'.": "ATTENDANCE_PROTO_TASK",
    "Execution failed for task ':services:attendance-service:attendance-app:integrationTest'.": "ATTENDANCE_INTEGRATION_TEST_TASK"
}


def background_native_failure(raw, sources):
    """Recognize a bound terminal failed-task signature, never its first cause."""
    unknown = {"category": "UNKNOWN", "source_stage": "UNKNOWN"}
    if type(raw) is not bytes or len(raw) > 65536 or type(sources) is not dict:
        return unknown
    if set(sources) != set(_BACKGROUND_NATIVE_SOURCE_HASHES):
        return unknown
    for name, expected in _BACKGROUND_NATIVE_SOURCE_HASHES.items():
        source = sources[name]
        if type(source) is not bytes or len(source) > 65536 or hashlib.sha256(source).hexdigest() != expected:
            return unknown
    try:
        text = raw.decode("utf-8").replace("\r\n", "\n")
        if not text.endswith("\n") or any(
            (ord(char) < 32 and char not in "\n\t") or char in "\x7f\x85\u2028\u2029" for char in text
        ):
            return unknown
        lines = text.rstrip("\n").split("\n")
        banners = [index for index, line in enumerate(lines) if line.startswith("FAILURE:")]
        reasons = [index for index, line in enumerate(lines) if line == "* What went wrong:"]
        failed = [index for index, line in enumerate(lines) if line.startswith("Execution failed for task ")]
        endings = [index for index, line in enumerate(lines) if line.startswith("BUILD FAILED")]
        if not (len(banners) == len(reasons) == len(failed) == len(endings) == 1):
            return unknown
        banner, reason, task, ending = banners[0], reasons[0], failed[0], endings[0]
        if not banner < reason < task < ending or lines[banner] != "FAILURE: Build failed with an exception.":
            return unknown
        if re.fullmatch(r"BUILD FAILED in [0-9]+(?:ms|h|m|s)(?: [0-9]+(?:ms|h|m|s))*", lines[ending]) is None:
            return unknown
        for line in lines[ending + 1 :]:
            if line and re.fullmatch(r"[0-9]+ actionable tasks?: [0-9]+ (?:executed|up-to-date|from-cache)(?:, [0-9]+ (?:executed|up-to-date|from-cache))*", line) is None:
                return unknown
        stage = _BACKGROUND_NATIVE_TASK_STAGES.get(lines[task])
        if stage is None:
            return unknown
        return {"category": "FAILED_TASK", "source_stage": stage}
    except (UnicodeError, ValueError, TypeError):
        return unknown


_BACKGROUND_TEST_CLASS = "ru.rutcampustrack.attendance.integration.RabbitDecisionRetryIT"
_BACKGROUND_TEST_METHOD = "transientOutageAfterThreeAttemptsAutomaticallyRedrivesOriginalAfterThirtySeconds"
_BACKGROUND_TEST_SOURCE = "services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java"
_BACKGROUND_TEST_SOURCE_HASH = "b37440ac80044742dc771b1e728d3daba7dd9aa7fe3ad10e25a5a4fbbfbe0287"
_BACKGROUND_TEST_REPORT = "services/attendance-service/attendance-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.attendance.integration.RabbitDecisionRetryIT.xml"


def background_test_failure(raw, source):
    """One declared public test's terminal XML signature; never an authenticated first cause."""
    unknown = {"category": "UNKNOWN", "source_stage": "UNKNOWN", "admission_reason": "XML_REJECTED"}
    if type(raw) is not bytes or not raw or len(raw) > 1024**2:
        return unknown
    if (type(source) is not bytes or len(source) > 65536
            or hashlib.sha256(source).hexdigest() != _BACKGROUND_TEST_SOURCE_HASH):
        return {**unknown, "admission_reason": "SOURCE_REJECTED"}
    try:
        text = raw.decode("utf-8")
        if ("<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper() or "<!--" in text
                or re.search(r"<\?(?!xml\s)", text)
                or re.search(r"encoding\s*=\s*['\"](?!UTF-8['\"])[^'\"]+['\"]", text, re.IGNORECASE)):
            return unknown
        root = ET.fromstring(text)
        required = {"name", "tests", "skipped", "failures", "errors", "time", "timestamp"}
        if (root.tag != "testsuite" or not required <= set(root.attrib)
                or set(root.attrib) - required - {"hostname"}):
            return {**unknown, "admission_reason": "SUITE_SHAPE_NOT_ADMITTED"}
        if root.attrib["name"] != _BACKGROUND_TEST_CLASS:
            return {**unknown, "admission_reason": "FIXED_SUITE_NOT_ADMITTED"}
        if any(root.attrib[key] != value for key, value in
               {"tests": "1", "skipped": "0", "failures": "1", "errors": "0"}.items()):
            return {**unknown, "admission_reason": "FIXED_COUNTS_NOT_ADMITTED"}
        if root.text and root.text.strip():
            return unknown
        # Gradle 8.12 JUnitXmlResultWriter always emits empty properties before cases,
        # followed by optional suite stdout, then stderr, in that fixed order.
        if [node.tag for node in root] not in (
                ["properties", "testcase"], ["properties", "testcase", "system-out"],
                ["properties", "testcase", "system-err"],
                ["properties", "testcase", "system-out", "system-err"]):
            return unknown
        properties = root[0]
        if properties.attrib or len(properties) or (properties.text and properties.text.strip()):
            return unknown
        for name in ("system-out", "system-err"):
            nodes = [node for node in root if node.tag == name]
            if len(nodes) > 1 or any(node.attrib or len(node) for node in nodes):
                return unknown
        if any(node.tag not in {"properties", "testcase", "system-out", "system-err"}
               or (node.tail and node.tail.strip()) for node in root):
            return unknown
        case = root[1]
        if set(case.attrib) != {"name", "classname", "time"}:
            return {**unknown, "admission_reason": "CASE_ATTRIBUTES_NOT_ADMITTED"}
        if (case.attrib["name"] != _BACKGROUND_TEST_METHOD + "()"
                or case.attrib["classname"] != _BACKGROUND_TEST_CLASS):
            return {**unknown, "admission_reason": "FIXED_CASE_NOT_ADMITTED"}
        if (case.text and case.text.strip()) or len(case) != 1:
            return {**unknown, "admission_reason": "CASE_CONTENT_NOT_ADMITTED"}
        failure = case[0]
        if (failure.tag != "failure" or set(failure.attrib) != {"message", "type"}
                or len(failure) or (failure.tail and failure.tail.strip())):
            return {**unknown, "admission_reason": "SIGNATURE_REJECTED"}
        stack = (failure.text or "").splitlines()
        if not stack or not (stack[0] == failure.attrib["type"]
                             or stack[0].startswith(failure.attrib["type"] + ":")):
            return {**unknown, "admission_reason": "SIGNATURE_REJECTED"}
        categories = {
            "java.lang.AssertionError": "ASSERTION",
            "org.opentest4j.AssertionFailedError": "ASSERTION",
            "org.awaitility.core.ConditionTimeoutException": "AWAIT_TIMEOUT",
            "java.lang.IllegalStateException": "ILLEGAL_STATE",
            "java.lang.ExceptionInInitializerError": "INITIALIZATION",
            "java.lang.NoClassDefFoundError": "CLASS_LOADING",
            "java.lang.OutOfMemoryError": "JAVA_MEMORY",
        }
        stages = {
            "<clinit>": (69, 71, "CLASS_INITIALIZATION"),
            "overrideRabbitVhost": (83, 95, "CONTEXT_PROPERTIES"),
            "cleanQueues": (99, 114, "BEFORE_EACH"),
            _BACKGROUND_TEST_METHOD: (328, 494, "SELECTED_TEST_BODY"),
            "acceptedOrigin": (559, 583, "TEST_ORIGIN"),
            "assertExportedWorker": (599, 608, "TEST_WORKER_SPAN"),
            "producer": (610, 622, "TEST_PRODUCER_SPAN"),
            "exportedSpan": (624, 629, "TEST_EXPORTED_SPAN"),
            "assertWorkerCorrelation": (631, 644, "TEST_WORKER_CORRELATION"),
            "collectorEndpoint": (646, 666, "COLLECTOR_ENDPOINT"),
            "writeCollectorReceipt": (668, 745, "COLLECTOR_RECEIPT"),
        }
        # The first exact public frame in the primary exception only. Do not promote causes,
        # suppressed failures, unknown frames or arbitrary line numbers to a public identity.
        stage = "UNKNOWN"
        for line in stack[1:]:
            if line.lstrip().startswith(("Caused by:", "Suppressed:")):
                break
            match = re.fullmatch(r"\s*at " + re.escape(_BACKGROUND_TEST_CLASS)
                                 + r"\.([A-Za-z0-9_$<>]+)\(RabbitDecisionRetryIT\.java:([1-9][0-9]{0,5})\)", line)
            if match:
                bound = stages.get(match[1])
                if bound and bound[0] <= int(match[2]) <= bound[1]:
                    stage = bound[2]
                break
        return {
            "category": categories.get(failure.attrib["type"], "UNKNOWN"), "source_stage": stage,
            "admission_reason": "SIGNATURE_CLASSIFIED"
            if categories.get(failure.attrib["type"], "UNKNOWN") != "UNKNOWN" and stage != "UNKNOWN"
            else "SIGNATURE_UNCLASSIFIED",
        }
    except (UnicodeError, ValueError, TypeError, ET.ParseError):
        return unknown


def read_background_test_failure(started_ns):
    """Read only the fixed current-call report, once, through a stable regular descriptor."""
    absent = {"category": "NO_CURRENT_RESULT", "source_stage": "UNKNOWN", "admission_reason": "NO_CURRENT_RESULT"}
    unknown = {"category": "UNKNOWN", "source_stage": "UNKNOWN", "admission_reason": "READ_ERROR"}
    path = ROOT / _BACKGROUND_TEST_REPORT
    try:
        if path.resolve() != path or path.is_symlink():
            return {**unknown, "admission_reason": "REPORT_REJECTED"}
        try:
            descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        except FileNotFoundError:
            return absent
        with os.fdopen(descriptor, "rb") as report:
            before = os.fstat(report.fileno())
            if not stat.S_ISREG(before.st_mode) or before.st_size > 1024**2:
                return {**unknown, "admission_reason": "REPORT_REJECTED"}
            ended_ns = time.time_ns()
            if not all(started_ns <= value <= ended_ns for value in (before.st_mtime_ns, before.st_ctime_ns)):
                return absent
            raw = report.read(1024**2 + 1)
            after = os.fstat(report.fileno())
            current = path.lstat()
            fields = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")
            if (any(getattr(before, key) != getattr(after, key) or getattr(before, key) != getattr(current, key)
                    for key in fields) or len(raw) != before.st_size):
                return {**unknown, "admission_reason": "REPORT_REJECTED"}
        with (ROOT / _BACKGROUND_TEST_SOURCE).open("rb") as public_source:
            source = public_source.read(65537)
        return background_test_failure(raw, source)
    except Exception:  # noqa: BLE001 -- optional diagnostics preserve the primary Gradle failure
        return unknown


def alloy_positions(text):
    """Alloy1.20.1 positions.File: map[{path,labels}]offset, not Promtail YAML."""
    root = yaml.compose(text)
    assert isinstance(root, yaml.MappingNode) and len(root.value) == 1
    name, entries = root.value[0]
    assert name.value == "positions" and isinstance(entries, yaml.MappingNode)
    result = {}
    for key, value in entries.value:
        assert isinstance(key, yaml.MappingNode) and isinstance(value, yaml.ScalarNode)
        fields = {name.value: field.value for name, field in key.value}
        assert set(fields) == {"path", "labels"} and len(key.value) == 2
        identity = (fields["path"], fields["labels"])
        assert identity not in result and re.fullmatch(r"[0-9]+", value.value)
        result[identity] = int(value.value)
    return result


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
        assert job.get("honor_labels", False) is False, "retain production target-label precedence"
        job.update(
            scrape_interval="5s",
            scrape_timeout="5s",
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
            if filename == "grpc-latency.json" and panel_id in {1, 2, 3, 4}:
                for row in rows:
                    labels = row["metric"]
                    assert labels["service"] == job and labels["grpc_service"]
                    assert "exported_service" not in labels
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
            for row in count:
                labels = row["metric"]
                assert labels["service"] == job and labels["environment"] == "synthetic"
                assert re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", labels["grpc_service"])
                assert re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", labels["method"])
                assert "exported_service" not in labels, "downstream label must not collide at ingestion"
            identities = {(row["metric"]["service"], row["metric"]["grpc_service"], row["metric"]["method"])
                          for row in count}
            # Query the retained panel's grouping against the actual ingested series.
            grouped = values('count by (service, grpc_service, method) (grpc_client_duration_seconds_count{job="'
                             + job + '"})')
            assert {(row["metric"]["service"], row["metric"]["grpc_service"], row["metric"]["method"])
                    for row in grouped} == identities
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
        probe("business-kpis.json", panel, "academic-service")
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


def e2e_gateway_openapi(docker, safe, project="rct-e2e"):
    """Seven unauthenticated GETs on the owned Gateway origin, not public ingress."""
    ids = docker(
        "ps",
        "--quiet",
        "--no-trunc",
        "--filter",
        "label=com.docker.compose.project=" + project,
        "--filter",
        "label=com.docker.compose.service=api-gateway",
    ).stdout.splitlines()
    assert len(ids) == 1 and re.fullmatch("[0-9a-f]{64}", ids[0])
    host = docker(
        "inspect", "--format", '{{(index .NetworkSettings.Networks "' + project + '_private_net").IPAddress}}', ids[0]
    ).stdout.strip()
    address = ipaddress.IPv4Address(host)
    assert address.is_private and not (address.is_loopback or address.is_unspecified or address.is_multicast)

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def unique_pairs(items):
        value = {}
        for key, item in items:
            assert key not in value
            value[key] = item
        return value

    result = {
        "config_status": None,
        "documents": {},
        "qualification": "Owned private Gateway HTTP origin; ingress BasicAuth and API authorization policy unchanged",
    }
    safe["gateway_openapi"] = result

    def get(path, observed):
        observed.update(stage="HTTP_HEADERS", error_kind=None)
        started = time.monotonic()
        request = urllib.request.Request("http://" + host + ":8080" + path, headers={"Accept": "application/json"})
        try:
            with opener.open(request, timeout=5) as response:
                observed["status"] = response.status if 100 <= response.status <= 599 else None
                assert response.status == 200
                observed["stage"] = "HTTP_BODY"
                raw = response.read(2 * 1024**2 + 1)
                assert 0 < len(raw) <= 2 * 1024**2
        except urllib.error.HTTPError as error:
            observed["status"] = error.code if type(error.code) is int and 100 <= error.code <= 599 else None
            error.close()
            observed["error_kind"] = "HTTP_STATUS"
            raise
        except (OSError, ValueError, AssertionError) as error:
            observed["error_kind"] = "TIMEOUT" if isinstance(error, TimeoutError) else "TRANSPORT_OR_BODY"
            raise
        finally:
            observed["elapsed_ms"] = round((time.monotonic() - started) * 1000)
        observed["stage"] = "JSON_SYNTAX"
        try:
            value = json.loads(raw, object_pairs_hook=unique_pairs)
            assert isinstance(value, dict)
        except (ValueError, AssertionError):
            observed["error_kind"] = "JSON_SYNTAX_OR_SHAPE"
            raise
        observed["stage"] = "CONTRACT"
        return value

    producers = {
        "auth-service",
        "academic-service",
        "schedule-service",
        "attendance-service",
        "notification-service",
        "mobile-bff",
    }
    safe["phase"] = "GATEWAY_OPENAPI_CONFIG"
    config_status = {"status": None}
    result["config"] = config_status
    try:
        config = get("/v3/api-docs/swagger-config", config_status)
    finally:
        result["config_status"] = config_status["status"]
    assert isinstance(config.get("urls"), list) and len(config["urls"]) == 6
    assert all(isinstance(item, dict) and isinstance(item.get("url"), str) for item in config["urls"])
    assert {item["url"] for item in config["urls"]} == {"/openapi/" + name for name in producers}
    for producer in sorted(producers):
        safe["phase"] = "GATEWAY_OPENAPI_" + producer.upper().replace("-", "_")
        observed = {"status": None, "contract_valid": False}
        result["documents"][producer] = observed
        doc = get("/openapi/" + producer, observed)
        observed["stage"] = "OPENAPI_VERSION"
        assert doc["openapi"] == ("3.0.1" if producer == "mobile-bff" else "3.1.0")
        observed["stage"] = "SERVERS"
        assert doc["servers"] == [{"url": "/" if producer == "mobile-bff" else "/api"}]
        observed["stage"] = "PUBLIC_PATHS"
        paths = doc["paths"]
        assert isinstance(paths, dict) and paths and all(path.startswith("/") for path in paths)
        assert not any(path.startswith("/internal/") for path in paths)
        assert isinstance(doc.get("components"), dict)
        observed["stage"] = "SECURITY_SCHEMES"
        schemes = doc["components"].get("securitySchemes")
        assert isinstance(schemes, dict) and isinstance(schemes.get("bearerAuth"), dict)
        assert schemes["bearerAuth"]["type"] == "http" and schemes["bearerAuth"]["scheme"] == "bearer"
        for item in paths.values():
            observed["stage"] = "OPERATION_SECURITY"
            assert isinstance(item, dict)
            for method, operation in item.items():
                if method not in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}:
                    continue
                assert isinstance(operation, dict)
                requirements = operation.get("security", [])
                assert isinstance(requirements, list)
                assert all(
                    isinstance(requirement, dict) and set(requirement) <= set(schemes) for requirement in requirements
                )
        if producer == "auth-service":
            observed["stage"] = "AUTH_LOGIN_SECURITY"
            assert paths["/auth/login"]["post"]["security"] == []
        if producer == "academic-service":
            observed["stage"] = "ACADEMIC_EXPORT_OPERATION"
            export = paths["/academic/export-jobs"]["post"]
            assert {"bearerAuth": []} in export["security"] and {"202", "503"} <= set(export["responses"])
            assert isinstance(doc["components"].get("schemas"), dict)
            observed["stage"] = "ACADEMIC_STATUS_REQUIRED_PROPERTIES"
            status = doc["components"]["schemas"]["Status"]
            assert isinstance(status, dict)
            fields = {"jobId", "state", "suggestedFilename", "expiresAt", "errorCode"}
            assert isinstance(status.get("properties"), dict) and isinstance(status.get("required"), list)
            assert set(status["properties"]) == fields and set(status["required"]) == fields
            error = status["properties"]["errorCode"]
            observed["stage"] = "ACADEMIC_ERROR_CODE_NULLABLE_TYPE"
            assert isinstance(error, dict)
            types = error.get("type")
            assert isinstance(types, list) and all(isinstance(item, str) for item in types)
            assert set(types) == {"string", "null"}
            observed["stage"] = "ACADEMIC_ERROR_CODE_FLAT_SCHEMA"
            # Require the actual flat3.1 String|null schema, not nullable:true.
            # Reject alternate constraints rather than pretending to validate them.
            assert not (
                {"$ref", "$dynamicRef", "enum", "const", "not", "allOf", "anyOf", "oneOf", "if", "then", "else"}
                & set(error)
            )
        observed["contract_valid"] = True
        observed["stage"] = "COMPLETE"
    return result


def e2e_export_failure_snapshot(query, baseline=None):
    """One last-scraped aggregate observation; never an acceptance or actor/job identity."""
    counters = {
        ("rct_export_jobs_accepted_total", "", "", ""): "accepted_jobs",
        ("rct_export_jobs_retries_total", "", "", "generation_failed"): "retried_jobs",
        ("rct_export_jobs_terminal_total", "", "success", "none"): "terminal_success",
        ("rct_export_jobs_terminal_total", "", "failure", "generation_failed"): "terminal_generation_failed",
        ("rct_export_jobs_terminal_total", "", "failure", "expired"): "terminal_expired",
        ("rct_export_jobs_terminal_total", "", "denied", "authority_denied"): "terminal_denied",
        ("rct_export_jobs_terminal_total", "", "rejected", "export_too_large"): "terminal_rejected",
        ("rct_export_jobs_duration_seconds_bucket", "", "success", ""): "success_within60s",
        ("attendance_report_duration_seconds_count", "", "success", ""): "successful_docx_reports",
    }
    gauges = {
        **{("rct_export_jobs_queue", state, "", ""): state for state in ["queued", "preparing", "ready", "error"]},
        ("rct_export_jobs_due", "", "", ""): "due",
        ("rct_export_jobs_waiting", "", "", ""): "waiting",
        ("rct_export_jobs_snapshot_timestamp_seconds", "", "", ""): "owner_snapshot_age_seconds",
    }
    result = {
        "counters": {key: {"value": "UNKNOWN", "delta": "UNKNOWN"} for key in counters.values()},
        "gauges": {key: "UNKNOWN" for key in gauges.values()},
    }
    expression = (
        'sum by (__name__,state,outcome,reason) ('
        '{job="academic-service",kind="HEADMAN_WEEKLY_SELECTED",__name__=~"rct_export_jobs_(accepted|retries|terminal)_total|rct_export_jobs_(queue|due|waiting)"}'
        ' or rct_export_jobs_duration_seconds_bucket{job="academic-service",kind="HEADMAN_WEEKLY_SELECTED",outcome="success",le="60.0"}'
        ' or attendance_report_duration_seconds_count{job="attendance-service",report="weekly",format="docx",outcome="success"}'
        ' or rct_export_jobs_snapshot_timestamp_seconds{job="academic-service"})'
    )
    try:
        rows = query(expression)
        if not isinstance(rows, list) or len(rows) > 32:
            return result
        values = {}
        for row in rows:
            if not isinstance(row, dict) or not isinstance(row.get("metric"), dict):
                continue
            labels = row["metric"]
            identity = tuple(labels.get(key, "") for key in ["__name__", "state", "outcome", "reason"])
            if not all(isinstance(value, str) for value in identity) or identity not in counters and identity not in gauges:
                continue
            raw = row.get("value")
            value = float(raw[1]) if isinstance(raw, list) and len(raw) == 2 and type(raw[1]) in {str, int, float} else -1
            value = value if math.isfinite(value) and value >= 0 else "UNKNOWN"
            values[identity] = "UNKNOWN" if identity in values else value
        for identity, key in counters.items():
            value = values.get(identity, "UNKNOWN")
            before = baseline["counters"][key]["value"] if baseline is not None else "UNKNOWN"
            delta = value - before if type(value) is float and type(before) is float and math.isfinite(before) and before >= 0 else -1
            result["counters"][key] = {
                "value": value,
                "delta": delta if math.isfinite(delta) and delta >= 0 else "UNKNOWN",
            }
        for identity, key in gauges.items():
            value = values.get(identity, "UNKNOWN")
            if key == "owner_snapshot_age_seconds":
                value = time.time() - value if type(value) is float and value > 0 else -1
                value = value if math.isfinite(value) and value >= 0 else "UNKNOWN"
            result["gauges"][key] = value
    except Exception:
        # Diagnostics must never replace the original browser failure or block PASS assertions.
        pass
    return result


def e2e_async_browser():
    """Observe the existing real browser scenario, never launch another app stand."""
    local = None
    if os.environ.get("RCT_LOCAL_STAGE17_MANIFEST"):
        sys.path.insert(0, str(ROOT / "scripts"))
        from local_e2e_async import validate_local
        local = validate_local()
        def interrupt_local(*_):
            raise KeyboardInterrupt
        signal.signal(signal.SIGTERM, interrupt_local)
    else:
        assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
    assert os.environ.get("RCT_E2E_ASYNC_REPORT") == "true" and os.environ.get("RCT_E2E_SYNTHETIC_SEED") == "true"
    source_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    assert source_sha == (local["source_sha"] if local else os.environ["GITHUB_SHA"]) and re.fullmatch("[0-9a-f]{40}", source_sha)
    project = local["project"] if local else "rct-e2e"
    temporary_root = str(Path(local["run"]) / "tmp") if local else os.environ["RUNNER_TEMP"]
    run = Path(os.environ["RCT_REMAINING5_RUN"])
    assert run.is_absolute() and (run == Path(local["run"]) if local else run.is_relative_to(Path(os.environ["RUNNER_TEMP"])))
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
    if local:
        safe["local_source"] = {"dirty": local["dirty"], "manifest_sha256": hashlib.sha256(
            Path(os.environ["RCT_LOCAL_STAGE17_MANIFEST"]).read_bytes()).hexdigest()}
    primary = None

    def docker(*args, check=True, seconds=15):
        return subprocess.run(["docker", *args], capture_output=True, text=True, check=check, timeout=seconds)

    with tempfile.TemporaryDirectory(prefix=name + "-", dir=temporary_root) as tmp:
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
        failure_baseline = None
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
            "accepted_jobs": 'sum(rct_export_jobs_accepted_total{job="academic-service",kind="HEADMAN_WEEKLY_SELECTED"})',
            "terminal_success": 'sum(rct_export_jobs_terminal_total{job="academic-service",kind="HEADMAN_WEEKLY_SELECTED",outcome="success",reason="none"})',
            "success_within60s": 'sum(rct_export_jobs_duration_seconds_bucket{job="academic-service",kind="HEADMAN_WEEKLY_SELECTED",outcome="success",le="60.0"})',
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
                    project + "_private_net",
                ).stdout.strip()
                == project
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
                *(["--label", "rct.local.stage17=" + local["nonce"]] if local else []),
                "--network",
                project + "_private_net",
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
            failure_baseline = e2e_export_failure_snapshot(query)
            for key, value in baseline.items():
                failure_baseline["counters"][key]["value"] = value
            safe["phase"] = "BROWSER_REAL_DOWNLOADS"
            with ((run if local else private) / "browser.private.log").open("wb") as log:
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
            # Metadata failures must never prevent the actual DOCX/browser/panel proof.
            safe["phase"] = "GATEWAY_PUBLIC_OPENAPI"
            e2e_gateway_openapi(docker, safe, project)
            safe["gateway_config_sha256"] = hashlib.sha256(
                (ROOT / "services/api-gateway/src/main/resources/application.yml").read_bytes()
            ).hexdigest()
            safe["result"] = "PASS"
            safe["phase"] = "COMPLETE"
        except (AssertionError, subprocess.SubprocessError, OSError, ValueError, KeyError) as error:
            primary = error
            safe["error_kind"] = {
                AssertionError: "AssertionError",
                subprocess.CalledProcessError: "CalledProcessError",
                subprocess.TimeoutExpired: "TimeoutExpired",
                OSError: "OSError",
                urllib.error.HTTPError: "HTTPError",
                urllib.error.URLError: "URLError",
                TimeoutError: "TimeoutError",
                ConnectionResetError: "ConnectionResetError",
                ConnectionRefusedError: "ConnectionRefusedError",
                ConnectionAbortedError: "ConnectionAbortedError",
                BrokenPipeError: "BrokenPipeError",
                http.client.RemoteDisconnected: "RemoteDisconnected",
                ssl.SSLError: "SSLError",
                ssl.SSLCertVerificationError: "SSLCertVerificationError",
                ssl.SSLEOFError: "SSLEOFError",
                socket.gaierror: "gaierror",
            }.get(type(error), "OTHER")
            if safe["phase"] == "BROWSER_REAL_DOWNLOADS" and browser is not None:
                safe["browser_failure_metrics"] = e2e_export_failure_snapshot(query, failure_baseline)
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
        "infra/alloy/config.alloy",
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
        names = ["prometheus", "alertmanager", "grafana", "loki", "tempo", "alloy", "blackbox-exporter"]
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
            if name == "alloy":
                service["environment"].update(
                    COMPOSE_PROJECT_NAME=project,
                    DEPLOYMENT_ENVIRONMENT="synthetic",
                    GOMEMLIMIT="192MiB",
                    GOMAXPROCS="1",
                )
            # These API ports exist only on loopback in the isolated test project.
            internal = {
                "prometheus": "9090",
                "alertmanager": "9093",
                "grafana": "3000",
                "loki": "3100",
                "tempo": "3200",
                "alloy": "12345",
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
        grpc_identity = copy.deepcopy(next(job for job in yaml.safe_load(
            (ROOT / "infra/prometheus/prometheus.yml").read_text())["scrape_configs"]
            if job["job_name"] == "academic-service"))
        assert grpc_identity.get("honor_labels", False) is False
        grpc_identity.update(job_name="grpc-identity-fixture", metrics_path="/metrics", scrape_interval="1s",
                             metric_relabel_configs=[{"source_labels": ["__name__"],
                                 "regex": "grpc_client_duration_seconds_(bucket|sum|count)", "action": "keep"}])
        grpc_identity["static_configs"] = [{"targets": ["notification-web:9094"],
                                            "labels": {"service": "academic-service", "environment": "synthetic"}}]
        prom["scrape_configs"].append(grpc_identity)
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
        alloy = (ROOT / "infra/alloy/config.alloy").read_text()
        # Real application processes run on the CI host; these files use their
        # production JSON encoder, not a second Docker ingestion of those apps.
        alloy += '\nloki.source.file "actual_applications" {\n  targets = [\n'
        for service in ["api-gateway", "mobile-bff", "delivery-probe"]:
            alloy += (
                '    {__path__ = "/application-logs/'
                + service
                + '.log", '
                + 'service = "'
                + service
                + '", environment = "synthetic"},\n'
            )
        alloy += '  ]\n  forward_to = [loki.write.local.receiver]\n  file_match {\n    enabled = true\n    sync_period = "5s"\n  }\n}\n'
        (td / "config.alloy").write_text(alloy)
        config["services"]["alloy"]["volumes"] = [
            x for x in config["services"]["alloy"]["volumes"] if "/etc/alloy/config.alloy" not in x
        ] + [str(td / "config.alloy") + ":/etc/alloy/config.alloy:ro"]
        config["services"]["alloy"]["volumes"].append(str(app_logs) + ":/application-logs:ro")
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
        # Alloy obtains service identity from the Compose service; use the same
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
                    "alloy": "12345",
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

        def positions(component="loki.source.docker.containers"):
            cid = docker("ps", "--quiet", "alloy").stdout.strip()
            dest = td / (component + "-positions.yml")
            subprocess.run(
                ["docker", "cp", cid + ":/var/lib/alloy/" + component + "/positions.yml", str(dest)],
                capture_output=True,
                check=True,
                timeout=15,
            )
            assert dest.stat().st_size <= 1024**2, "bounded checkpoint size exceeded"
            return alloy_positions(dest.read_text())

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
                diagnostic_started_ns = time.time_ns()
                process = subprocess.Popen(
                    command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True
                )
                try:
                    result = process.wait(timeout=seconds)
                    if result:
                        if filename == "background-gradle.private.log":
                            _STATE["background_test_failure"] = {
                                "category": "UNKNOWN", "source_stage": "UNKNOWN", "admission_reason": "READ_ERROR"
                            }
                            try:
                                _STATE["background_test_failure"] = read_background_test_failure(diagnostic_started_ns)
                            except Exception:  # noqa: BLE001 -- optional diagnostics preserve the primary failure
                                pass
                            _STATE["native_failure"] = {"category": "UNKNOWN", "source_stage": "UNKNOWN"}
                            try:
                                with (td / filename).open("rb") as native_log:
                                    native_raw = native_log.read(65537)
                                native_sources = {}
                                for name in _BACKGROUND_NATIVE_SOURCE_HASHES:
                                    with (ROOT / name).open("rb") as public_source:
                                        native_sources[name] = public_source.read(65537)
                                _STATE["native_failure"] = background_native_failure(native_raw, native_sources)
                            except Exception:  # noqa: BLE001 -- optional diagnostics must not replace the child failure
                                pass
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
            _STATE["phase"] = "ACTUAL_BACKGROUND_TEMPO_PORT_LOOKUP"
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
            _STATE["phase"] = "ACTUAL_BACKGROUND_GRADLE"
            run_application_gradle(command, env, "background-gradle.private.log", 600)
            _STATE["phase"] = "ACTUAL_BACKGROUND_TRANSPORT"
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
            docker("run", "--rm", "--no-deps", "alloy", "validate", "/etc/alloy/config.alloy")
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
            _STATE["phase"] = "GRPC_IDENTITY"
            selector = '{job="grpc-identity-fixture"}'
            wait(lambda: len(samples("grpc_client_duration_seconds_count" + selector)) == 2)
            expected_rpc = {("ScheduleGrpcService", "OK"), ("AuthGrpcService", "UNAVAILABLE")}
            raw_rpc = samples("grpc_client_duration_seconds_count" + selector)
            for row in raw_rpc:
                labels = row["metric"]
                assert labels["service"] == "academic-service" and labels["method"] == "Get"
                assert labels["environment"] == "synthetic" and "exported_service" not in labels
            assert {(row["metric"]["grpc_service"], row["metric"]["status"]) for row in raw_rpc} == expected_rpc
            # Execute the actual provisioned panel expressions with only fixed selectors substituted.
            for panel_id in [1, 2, 3, 4]:
                expression = dashboard_expression("grpc-latency.json", panel_id)
                expression = expression.replace('job=~".*"', 'job="grpc-identity-fixture"')
                wait(lambda expression=expression: bool(samples(expression)) and all(
                    math.isfinite(float(row["value"][1])) for row in samples(expression)))
                rows = samples(expression)
                downstreams = {row["metric"]["grpc_service"] for row in rows}
                assert downstreams == ({"AuthGrpcService"} if panel_id == 4 else {name for name, _ in expected_rpc})
                assert all(row["metric"]["service"] == "academic-service" for row in rows)
            checks.append({"grpc_identity": {"honor_labels": False, "source_service": "academic-service",
                           "downstream_services": sorted(name for name, _ in expected_rpc),
                           "same_method": "Get", "retained_panels": [1, 2, 3, 4], "series_preserved": 2}})
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
            wait(lambda: any(fixture_cid in key[0] for key in positions()), 30)
            checkpoint = positions()
            fixture_keys = {key for key in checkpoint if fixture_cid in key[0]}
            assert fixture_keys
            docker("up", "-d", "--force-recreate", "--no-deps", "alloy", "loki", "tempo", "grafana")
            wait(lambda: grafana("/api/health")["database"] == "ok", 120)
            wait(lambda: bool(logs()), 120)
            wait(lambda: bool(api("tempo", "/api/traces/" + trace).get("batches")), 120)
            after = positions()
            assert fixture_keys <= set(after) and all(int(after[k]) >= int(checkpoint[k]) for k in fixture_keys), (
                "persisted fixture log checkpoint did not survive"
            )
            assert grafana("/api/datasources/uid/loki")["uid"] == "loki"
            checks.append("Loki/Tempo log+trace, Alloy positions and Grafana provisioning survive owned recreate")
            # A distinct post-recreate line must arrive through the same reader.
            recreated_id = str(uuid.uuid4())
            api("notification-web", "/emit", {"traceId": trace, "spanId": span, "requestId": recreated_id})
            recreate_query = loki_query.replace(request_id, recreated_id)
            wait(
                lambda: bool(
                    api(
                        "loki",
                        "/loki/api/v1/query_range?"
                        + urllib.parse.urlencode(
                            {"query": recreate_query, "start": str(now), "end": str(time.time_ns()), "limit": "10"}
                        ),
                    )["data"]["result"]
                ),
                60,
            )
            wait(lambda: all(positions().get(key, 0) > checkpoint[key] for key in fixture_keys), 30)
            running = docker("ps", "--quiet", "alloy").stdout.strip().splitlines()
            assert len(running) == 1, "exactly one owned ingestion service required"
            checks.append("one Alloy reader delivers a distinct post-recreate marker")
            _STATE["phase"] = "ACTUAL_JAVA_TRANSPORT"
            app_result = application_transport()
            _STATE["phase"] = "ACTUAL_BACKGROUND_TRANSPORT"
            background_result = background_transport()
            _STATE["phase"] = "ALLOY_DELIVERY_AND_BUDGET"
            wait(lambda: len(samples('up{job="alloy"} == 1')) == 1, 60)
            series = api(
                "loki",
                "/loki/api/v1/series?"
                + urllib.parse.urlencode(
                    {
                        "match[]": '{service=~"api-gateway|mobile-bff|notification-web",environment="synthetic"}',
                        "start": str(now),
                        "end": str(time.time_ns()),
                    }
                ),
            )["data"]
            assert {row["service"] for row in series} == {"api-gateway", "mobile-bff", "notification-web"}
            forbidden = {"requestid", "traceid", "spanid", "eventid", "userid", "jobid", "authorization", "cookie"}
            assert all(not (forbidden & {key.replace("_", "").lower() for key in row}) for row in series)
            sent = samples('loki_write_sent_entries_total{job="alloy"}')
            assert sent and sum(float(row["value"][1]) for row in sent) > 0
            # Loki's unchanged64KiB limit rejects this controlled, non-business line.
            # Require actual terminal-drop telemetry, never a synthetic metric.
            before_drop = samples('sum(loki_write_dropped_entries_total{job="alloy"})')
            baseline = float(before_drop[0]["value"][1]) if before_drop else 0
            (app_logs / "delivery-probe.log").write_text("controlled-line-limit-probe:" + "x" * 70000 + "\n")
            wait(lambda: bool(samples('sum(loki_write_dropped_entries_total{job="alloy"}) > ' + str(baseline))), 60)
            rss = samples('process_resident_memory_bytes{job="alloy"}')
            assert len(rss) == 1 and 0 < float(rss[0]["value"][1]) <= 256 * 1024**2
            cpu = samples('rate(process_cpu_seconds_total{job="alloy"}[1m])')
            assert len(cpu) == 1 and 0 <= float(cpu[0]["value"][1]) <= 0.6
            checks.append(
                {
                    "alloy": {
                        "rss_bytes": int(float(rss[0]["value"][1])),
                        "cpu_cores_1m": float(cpu[0]["value"][1]),
                        "sent_entries_positive": True,
                        "terminal_drop_observed": True,
                    }
                }
            )
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
                        "native_failure": _STATE.get("native_failure", {"category": "UNKNOWN", "source_stage": "UNKNOWN"}),
                        **({"background_test_failure": _STATE["background_test_failure"]}
                           if "background_test_failure" in _STATE else {}),
                        "missing_host_paths": _STATE.get("missing_host_paths", []),
                    }
                )
            )
            sys.exit(1)
