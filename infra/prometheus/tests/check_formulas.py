"""Run actual dashboard/rule expressions against diagnostic PromQL samples (Prometheus 2.55.1)."""

import json
import os
import subprocess
import tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
DASH = ROOT / "infra/grafana/provisioning/dashboards"


def dashboard_expr(name, panel_id):
    data = json.loads((DASH / name).read_text())
    return (
        next(p for p in data["panels"] if p["id"] == panel_id)["targets"][0]["expr"]
        .replace("$environment", ".*")
        .replace("$job", ".*")
        .replace("$instance", ".*")
    )


def alert_expr(filename, name):
    data = yaml.safe_load((ROOT / "infra/prometheus/rules" / filename).read_text())
    return next(r for g in data["groups"] for r in g["rules"] if r.get("alert") == name)["expr"]


def record_expr(name, operation=None, filename="service-objectives.yml"):
    data = yaml.safe_load((ROOT / "infra/prometheus/rules" / filename).read_text())
    return next(
        r
        for g in data["groups"]
        for r in g["rules"]
        if r.get("record") == name and (operation is None or r.get("labels", {}).get("operation") == operation)
    )["expr"]


def sample(labels, value):
    return {"labels": labels, "value": value}


def series(name, values):
    return {"series": name, "values": values}


def case(inputs, expr, expected, at="5m"):
    return {
        "interval": "1m",
        "input_series": inputs,
        "promql_expr_test": [{"expr": expr.replace("$interval", "5m"), "eval_time": at, "exp_samples": expected}],
    }


def slo_budget_inventory():
    jobs = [
        "api-gateway",
        "auth-service",
        "mobile-bff",
        "academic-service",
        "schedule-service",
        "attendance-service",
        "notification-web",
        "document-renderer-service",
    ]
    operations = ["read", "write", "admission", "file_export", "gateway_ingress"]
    return [f'job="{job}",environment="production",operation="{op}"' for job in jobs for op in operations]


def long_window_fixture():
    data = yaml.safe_load((ROOT / "infra/prometheus/rules/service-objectives.yml").read_text())
    group = next(g for g in data["groups"] if g["name"] == "http-slo-window-rollup")
    assert group["interval"] == "30m"
    assert {r["labels"]["operation"] for r in group["rules"] if "labels" in r} == {
        "read",
        "write",
        "admission",
        "file_export",
        "gateway_ingress",
    }
    long = next(g for g in data["groups"] if g["name"] == "http-slo-long-window")
    assert all("http_server_requests" not in r["expr"] and "[30d:" not in r["expr"] for r in long["rules"])
    command = yaml.safe_load((ROOT / "docker-compose.prod.yml").read_text())["services"]["prometheus"]["command"]
    cap = int(next(x.split("=", 1)[1] for x in command if x.startswith("--query.max-samples=")))
    assert cap == 500000
    inventory = slo_budget_inventory()
    points = 30 * 24 * 2
    # Six full-sized range/intermediate arrays is deliberately more than any
    # single production long-window expression retains; the exact engine cap
    # is also exercised against offline-seeded30d data in the normal CI cohort.
    budget = {
        "series": len(inventory),
        "samples_per_series": points,
        "production_max_samples": cap,
        "conservative_peak_samples": 6 * len(inventory) * points,
    }
    inputs = []
    for labels in inventory:
        for name, values in [
            ("http_eligible_events30m", "1000x1440"),
            ("http_error_events30m", "1x1440"),
            ("http_window_timestamp_seconds", "0+1800x1440"),
            ("http_source_coverage_seconds30d", "2590200x1440"),
            ("http_source_samples30d", "1440x1440"),
        ]:
            inputs.append(series("rct_sli:" + name + "{" + labels + "}", values))
    return {
        "interval": "30m",
        "input_series": inputs,
        "promql_expr_test": [
            {
                "expr": record_expr("rct_sli:http_availability30d"),
                "eval_time": "30d",
                "exp_samples": [sample("{" + labels + "}", 0.999) for labels in inventory],
            },
            {
                "expr": record_expr("rct_sli:http_source_coverage_seconds30d"),
                "eval_time": "30d",
                "exp_samples": [sample("{" + labels + "}", 2590200) for labels in inventory],
            },
            {
                "expr": record_expr("rct_sli:http_source_samples30d"),
                "eval_time": "30d",
                "exp_samples": [sample("{" + labels + "}", 1440) for labels in inventory],
            },
        ],
    }, budget


def main():
    tests = []
    disk = dashboard_expr("docker-monitoring.json", 26)
    tests.append(
        case(
            [
                series(
                    'node_filesystem_size_bytes{job="node-exporter",instance="host:9100",fstype="ext4",mountpoint="/"}',
                    "100x6",
                ),
                series(
                    'node_filesystem_avail_bytes{job="node-exporter",instance="host:9100",fstype="ext4",mountpoint="/"}',
                    "20x6",
                ),
                series(
                    'node_filesystem_size_bytes{job="node-exporter",instance="host:9100",fstype="xfs",mountpoint="/logs"}',
                    "100x6",
                ),
                series(
                    'node_filesystem_avail_bytes{job="node-exporter",instance="host:9100",fstype="xfs",mountpoint="/logs"}',
                    "5x6",
                ),
            ],
            disk,
            [sample('{instance="host:9100"}', 0.95)],
        )
    )
    cpu = dashboard_expr("docker-monitoring.json", 5)
    tests.append(
        case(
            [
                series(
                    'node_cpu_seconds_total{job="node-exporter",instance="host:9100",cpu="0",mode="idle"}', "0+30x6"
                ),
                series('process_cpu_seconds_total{job="academic-service",instance="academic:9091"}', "0+600x6"),
            ],
            cpu,
            [sample('{instance="host:9100"}', 50)],
        )
    )
    remaining = dashboard_expr("docker-monitoring.json", 35)
    tests.append(
        case(
            [
                series('container_memory_usage_bytes{name="rct-worker",instance="cadvisor:8080"}', "75x6"),
                series('container_spec_memory_limit_bytes{name="rct-worker",instance="cadvisor:8080"}', "100x6"),
                series('container_memory_usage_bytes{name="rct-unbounded",instance="cadvisor:8080"}', "75x6"),
                series('container_spec_memory_limit_bytes{name="rct-unbounded",instance="cadvisor:8080"}', "0x6"),
            ],
            remaining,
            [sample('{instance="cadvisor:8080",container_name="rct-worker"}', 25)],
        )
    )
    heap = dashboard_expr("business-kpis.json", 6)
    heap_inputs = [
        series(
            f'jvm_memory_{kind}_bytes{{job="academic-service",instance="{instance}",environment="synthetic",area="heap",id="{pool}"}}',
            f"{value}x6",
        )
        for instance, pool, used, maximum in [
            ("a:9091", "old", 60, 100),
            ("a:9091", "young", 35, -1),
            ("b:9091", "old", 10, 100),
        ]
        for kind, value in [("used", used), ("max", maximum)]
    ]
    tests.append(
        case(
            heap_inputs,
            heap,
            [
                sample('{job="academic-service",instance="a:9091",environment="synthetic"}', 0.95),
                sample('{job="academic-service",instance="b:9091",environment="synthetic"}', 0.1),
            ],
        )
    )
    # Absence and an explicit failed scrape are distinct; zero traffic still has up=1.
    missing = alert_expr("expected-targets.yml", "ExpectedTargetMissing")
    tests.append(
        case(
            [
                series('rct_expected_target{job="removed",instance="a:1",environment="synthetic"}', "1x6"),
                series('rct_expected_target{job="failed",instance="b:1",environment="synthetic"}', "1x6"),
                series('up{job="failed",instance="b:1",environment="synthetic"}', "0x6"),
                series('rct_expected_target{job="idle",instance="c:1",environment="synthetic"}', "1x6"),
                series('up{job="idle",instance="c:1",environment="synthetic"}', "1x6"),
            ],
            missing,
            [sample('rct_expected_target{job="removed",instance="a:1",environment="synthetic"}', 1)],
        )
    )
    tests.append(case([], heap, []))
    # A failed readiness producer is not an invented health_status gauge.
    readiness = alert_expr("service-health.yml", "HealthCheckDown")
    tests.append(
        case(
            [series('probe_success{job="blackbox-readiness",instance="http://academic:9091/actuator/health"}', "0x6")],
            readiness,
            [sample('probe_success{job="blackbox-readiness",instance="http://academic:9091/actuator/health"}', 0)],
        )
    )
    dlq = alert_expr("rabbitmq.yml", "DLQBacklog")
    tests.append(
        case(
            [
                series(
                    'rabbitmq_detailed_queue_messages{job="rabbitmq-queues",queue="attendance-service.events.dlq",vhost="/"}',
                    "11x6",
                ),
                series(
                    'rabbitmq_detailed_queue_messages{job="rabbitmq-queues",queue="attendance-service.events",vhost="/"}',
                    "500x6",
                ),
            ],
            dlq,
            [
                sample(
                    'rabbitmq_detailed_queue_messages{job="rabbitmq-queues",queue="attendance-service.events.dlq",vhost="/"}',
                    11,
                )
            ],
        )
    )
    ratio = record_expr("rct_sli:http_error_ratio5m")
    labels = (
        'job="api-gateway",instance="gateway:8080",environment="synthetic",method="GET",uri="/api/mobile/student/today"'
    )
    tests.append(
        case([series("rct_sli:http_eligible_rate5m{" + labels + "}", "1x6")], ratio, [sample("{" + labels + "}", 0)])
    )
    tests.append(case([], ratio, []))
    tests.append(
        case(
            [series("rct_sli:http_eligible_rate5m{" + labels + "}", "0x6")],
            "count((" + ratio + ") != (" + ratio + "))",
            [sample("{}", 1)],
        )
    )
    burn = alert_expr("service-objectives.yml", "HttpAvailabilityBudgetBurnFast")
    common = [
        series("rct_sli:http_error_ratio5m{" + labels + "}", ".02x6"),
        series("rct_sli:http_error_ratio1h{" + labels + "}", ".02x6"),
    ]
    # One failed request must not page as a statistically qualified ratio burn.
    tests.append(
        case(
            common + [series("http_server_requests_seconds_count{" + labels + ',status="500"}', "0 1 1 1 1 1")],
            burn,
            [],
        )
    )
    tests.append(
        case(
            common + [series("http_server_requests_seconds_count{" + labels + ',status="200"}', "0+10x6")],
            burn,
            [sample("rct_sli:http_error_ratio5m{" + labels + "}", 0.02)],
        )
    )
    availability = record_expr("rct_sli:http_availability30d")
    source = 'job="api-gateway",environment="production",operation="gateway_ingress"'
    counters = [
        series("rct_sli:http_eligible_events30m{" + source + "}", "1000x6"),
        series("rct_sli:http_error_events30m{" + source + "}", "1x6"),
    ]
    for age, windows, expected in [
        (300, 1, []),
        (2590200, 1439, []),
        (2590200, 1440, [sample("{" + source + "}", 0.999)]),
    ]:
        tests.append(
            case(
                counters
                + [
                    series("rct_sli:http_source_coverage_seconds30d{" + source + "}", str(age) + "x6"),
                    series("rct_sli:http_source_samples30d{" + source + "}", str(windows) + "x6"),
                ],
                availability,
                expected,
            )
        )
    # Complete-window eligibility uses actual120sample coverage AND all expected successful scrapes.
    scrape = record_expr("rct_sli:http_scrape_window_complete30m")
    target = 'job="academic-service",instance="academic:9091",environment="production"'

    def window_case(inputs, expr, expected):
        t = case(inputs, expr, expected, at="30m")
        t["interval"] = "15s"
        return t

    expected_target = series("rct_expected_target{" + target + "}", "1x120")
    tests.append(window_case([expected_target, series("up{" + target + "}", "0x120")], scrape, []))
    tests.append(
        window_case(
            [expected_target, series("up{" + target + "}", "1x120")],
            scrape,
            [sample('{job="academic-service",environment="production"}', 1)],
        )
    )
    tests.append(
        window_case(
            [
                expected_target,
                series("up{" + target + "}", "1x120"),
                series(
                    'rct_expected_target{job="academic-service",instance="missing:9091",environment="production"}',
                    "1x120",
                ),
            ],
            scrape,
            [],
        )
    )
    op_window = record_expr("rct_sli:http_window_complete30m", "read")
    producer = "http_server_requests_seconds_count{" + target + ',method="GET",uri="/api/subjects",status="200"}'
    good_scrape = series(
        'rct_sli:http_scrape_window_complete30m{job="academic-service",environment="production"}', "1x120"
    )
    tests.append(window_case([good_scrape], op_window, []))
    tests.append(window_case([good_scrape, series(producer, "_x115 1+1x4")], op_window, []))
    tests.append(
        window_case(
            [good_scrape, series(producer, "0+1x120")],
            op_window,
            [sample('{job="academic-service",environment="production"}', 1)],
        )
    )
    tests.append(window_case([series(producer, "0+1x120")], op_window, []))
    # Full production30d window and40class/service inventory, without shortening the query.
    full, budget = long_window_fixture()
    tests.append(full)
    assert budget["conservative_peak_samples"] < budget["production_max_samples"]
    # Actual panel ratio excludes4xx, has a healthy zero only alongside eligible traffic,
    # preserves idle NaN, and has no fabricated result for missing telemetry.
    panel_ratio = dashboard_expr("business-kpis.json", 23)
    traffic = [
        series("http_server_requests_seconds_count{" + labels + ',status="200"}', "0+90x6"),
        series("http_server_requests_seconds_count{" + labels + ',status="500"}', "0+10x6"),
        series("http_server_requests_seconds_count{" + labels + ',status="400"}', "0+900x6"),
        series("http_server_requests_seconds_count{" + labels + ',status="200",termination="cancelled"}', "0+900x6"),
    ]
    outlabels = '{job="api-gateway",instance="gateway:8080",environment="synthetic"}'
    tests.append(case(traffic, panel_ratio, [sample(outlabels, 0.1)]))
    tests.append(case(traffic[:1], panel_ratio, [sample(outlabels, 0)]))
    tests.append(case([], panel_ratio, []))
    tests.append(
        case(
            [series("http_server_requests_seconds_count{" + labels + ',status="200"}', "1x6")],
            "count((" + panel_ratio + ") != (" + panel_ratio + "))",
            [sample("{}", 1)],
        )
    )
    dlq_age = dashboard_expr("business-kpis.json", 44)
    qlabels = 'job="rabbitmq-queues",instance="rabbitmq:15692",environment="synthetic",vhost="/",queue="attendance-service.events.dlq"'
    tests.append(
        case(
            [
                series("rabbitmq_detailed_queue_head_message_timestamp{" + qlabels + "}", "60x6"),
                series("rabbitmq_detailed_queue_messages{" + qlabels + "}", "1x6"),
            ],
            dlq_age,
            [sample("{" + qlabels + "}", 240)],
        )
    )
    tests.append(case([series("rabbitmq_detailed_queue_messages{" + qlabels + "}", "1x6")], dlq_age, []))
    tests.append(
        case(
            [
                series("rabbitmq_detailed_queue_head_message_timestamp{" + qlabels + "}", "0x6"),
                series("rabbitmq_detailed_queue_messages{" + qlabels + "}", "1x6"),
            ],
            dlq_age,
            [],
        )
    )
    health = yaml.safe_load((ROOT / "infra/prometheus/rules/service-health.yml").read_text())
    assert all(r.get("alert") != "HighRequestLatency" for g in health["groups"] for r in g["rules"])
    # A normal10s export belongs to30s class, not the removed global2s warning.
    export_labels = 'job="attendance-service",instance="attendance:9094",environment="synthetic",method="POST",uri="/api/attendance/reports/headman-weekly/export",status="200"'
    export_hist = [
        series("http_server_requests_seconds_bucket{" + export_labels + ',le="' + edge + '"}', values)
        for edge, values in [("2", "0x6"), ("3", "0x6"), ("10", "0+3x6"), ("30", "0+3x6"), ("+Inf", "0+3x6")]
    ]
    export_expr = record_expr("rct_sli:http_operation_p95_seconds5m")
    tests.append(case(export_hist, "(" + export_expr + ") > 30", []))
    tests.append(case(export_hist, "count((" + export_expr + ") > 2)", [sample("{}", 1)]))
    # Actual exportSDK bucketedge60.0; terminal attempts/expecteddenials are not the denominator.
    producer_labels = 'job="auth-service",instance="auth:9090",environment="production",kind="HEADMAN_WEEKLY_SELECTED"'
    export_ratio = record_expr("rct_sli:export_bad_ratio5m", filename="telemetry-pipeline.yml")
    terminal_inputs = [
        series("rct_export_jobs_terminal_total{" + producer_labels + ',outcome="success",reason="none"}', "0+90x6"),
        series(
            "rct_export_jobs_terminal_total{" + producer_labels + ',outcome="failure",reason="generation_failed"}',
            "0+10x6",
        ),
        series(
            "rct_export_jobs_terminal_total{" + producer_labels + ',outcome="denied",reason="authority_denied"}',
            "0+900x6",
        ),
        series("rct_export_jobs_retries_total{" + producer_labels + ',reason="generation_failed"}', "0+900x6"),
        series(
            "rct_export_jobs_duration_seconds_bucket{" + producer_labels + ',outcome="success",le="60.0"}', "0+90x6"
        ),
    ]
    tests.append(
        case(terminal_inputs, "round((" + export_ratio + "),0.001)", [sample("{" + producer_labels + "}", 0.1)])
    )
    tests.append(case([], export_ratio, []))
    zero_inputs = [dict(row, values="0x6") for row in terminal_inputs]
    tests.append(case(zero_inputs, "count((" + export_ratio + ") != (" + export_ratio + "))", [sample("{}", 1)]))
    failed = alert_expr("telemetry-pipeline.yml", "ExportJobTerminalFailure")
    tests.append(
        case(
            [
                series(
                    'rct_export_jobs_last_failure_timestamp_seconds{job="auth-service",instance="auth:9090",environment="production"}',
                    "299x6",
                )
            ],
            "count(" + failed + ")",
            [sample("{}", 1)],
        )
    )
    tests.append(
        case(
            [
                series("rct_export_jobs_failures{" + producer_labels + ',reason="generation_failed"}', "1x6"),
                series(
                    'rct_export_jobs_snapshot_timestamp_seconds{job="auth-service",instance="auth:9090",environment="production"}',
                    "0x6",
                ),
            ],
            failed,
            [],
        )
    )
    # Full30d12finite exportkinds including unknown; largerHTTP40series budgetproof dominates.
    kinds = [
        "TEACHER_JOURNAL",
        "TEACHER_STATS",
        "HEADMAN_WEEKLY_CURRENT",
        "HEADMAN_WEEKLY_SELECTED",
        "HEADMAN_STATS",
        "HEADMAN_STATS_TREND",
        "HEADMAN_GROUP_COMPOSITION",
        "HEADMAN_SUBJECT_JOURNAL",
        "ADMIN_USERS",
        "ADMIN_GROUPS",
        "ADMIN_GROUP_ATTENDANCE",
        "unknown",
    ]
    export_series = []
    export_expected = []
    for kind in kinds:
        k = 'job="auth-service",environment="production",kind="' + kind + '"'
        for metric, values in [
            ("export_eligible_events30m", "1000x1440"),
            ("export_good_events30m", "995x1440"),
            ("export_source_coverage_seconds30d", "2590200x1440"),
            ("export_source_samples30d", "1440x1440"),
        ]:
            export_series.append(series("rct_sli:" + metric + "{" + k + "}", values))
        export_expected.append(sample("{" + k + "}", 0.995))
    tests.append(
        {
            "interval": "30m",
            "input_series": export_series,
            "promql_expr_test": [
                {
                    "expr": record_expr("rct_sli:export_good_fraction30d", filename="telemetry-pipeline.yml"),
                    "eval_time": "30d",
                    "exp_samples": export_expected,
                }
            ],
        }
    )
    assert len(kinds) * 1440 * 6 < 500000
    terminal = alert_expr("telemetry-pipeline.yml", "ReportProcessingFailed")
    tests.append(
        case(
            [series('attendance_report_last_failure_timestamp_seconds{report="weekly",format="docx"}', "299x6")],
            terminal,
            [sample('attendance_report_last_failure_timestamp_seconds{report="weekly",format="docx"}', 299)],
        )
    )
    with tempfile.TemporaryDirectory(prefix="rct-promql-") as td:
        fixture = Path(td) / "formulas.yml"
        fixture.write_text(yaml.safe_dump({"evaluation_interval": "1m", "tests": tests}, sort_keys=False))
        subprocess.run([os.environ.get("PROMTOOL", "promtool"), "test", "rules", str(fixture)], check=True)


if __name__ == "__main__":
    main()
