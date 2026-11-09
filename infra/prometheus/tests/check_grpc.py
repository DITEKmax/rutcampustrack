"""gRPC dashboard rule semantics; optional bounded production-cardinality replay.

PROMTOOL must point at the production-version executable (2.55.1).
The runtime replay additionally needs PROMETHEUS and an explicit --runtime flag.
It starts only one owned loopback server on19094 and never touches Docker.
"""

import argparse
import json
import math
import os
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
RULE = ROOT / "infra/prometheus/rules/grpc-latency.yml"
DASH = ROOT / "infra/grafana/provisioning/dashboards/grpc-latency.json"
IDENTITY = 'job="academic-service",instance="a:9091",environment="synthetic",service="academic-service",grpc_service="ScheduleGrpcService",method="Get"'


def panels():
    return {p["id"]: p for p in json.loads(DASH.read_text(encoding="utf-8"))["panels"]}


def expression(panel, target=0):
    return (panels()[panel]["targets"][target]["expr"]
            .replace("$environment", ".*").replace("$job", ".*").replace("$instance", ".*"))


def unit():
    subprocess.run([os.environ.get("PROMTOOL", "promtool"), "check", "rules", str(RULE)], check=True)
    inputs = []
    # Opposite status distributions expose averaging quantiles or losing status
    # buckets. Two statuses must sum to50% at10ms, not two separate quantiles.
    for status, low in [("OK", 90), ("UNAVAILABLE", 10)]:
        labels = IDENTITY + ',status="' + status + '"'
        for edge, slope in [("0.01", low), ("0.1", 100), ("+Inf", 100)]:
            inputs.append({"series": 'grpc_client_duration_seconds_bucket{' + labels + ',le="' + edge + '"}',
                           "values": "0+" + str(slope) + "x24"})
        inputs.append({"series": 'grpc_client_duration_seconds_count{' + labels + '}', "values": "0+100x24"})
    idle = IDENTITY.replace('a:9091', 'idle:9091')
    for edge in ["0.01", "0.1", "+Inf"]:
        inputs.append({"series": 'grpc_client_duration_seconds_bucket{' + idle + ',status="OK",le="' + edge + '"}',
                       "values": "0x24"})
    inputs.append({"series": 'grpc_client_duration_seconds_count{' + idle + ',status="OK"}', "values": "0x24"})
    inputs.extend([
        {"series": 'up{job="academic-service",instance="a:9091",environment="synthetic"}', "values": "1x24"},
        {"series": 'up{job="attendance-service",instance="down:9093",environment="synthetic"}', "values": "0x24"},
    ])
    tests = []
    active = lambda e: e.replace('instance=~".*"', 'instance="a:9091"')
    for panel, quantile, expected in [(1, "0.5", .01), (2, "0.95", .091), (3, "0.99", .0982)]:
        e = expression(panel)
        # Compare to microsecond precision; 2.55 promtool compares float values
        # bit-for-bit and histogram interpolation differs at the final bit.
        tests.append({"expr": "round((" + active(e) + ") * 1e6)", "eval_time": "5m", "exp_samples": [{
            "labels": '{' + IDENTITY + ',quantile="' + quantile + '"}',
            "value": round(expected * 1e6)}]})
        raw = expression(7, panel - 1)
        tests.append({"expr": "count(abs((" + active(e) + ") - ignoring(quantile) (" + active(raw) + ")) > 1e-12)",
                      "eval_time": "5m", "exp_samples": []})
        tests.append({"expr": "count((" + e + ") != (" + e + "))", "eval_time": "5m",
                      "exp_samples": [{"labels": "{}", "value": 1}]})
        tests.append({"expr": e.replace('job=~".*"', 'job="missing"'), "eval_time": "5m", "exp_samples": []})
    tests.append({"expr": active(expression(4)), "eval_time": "5m", "exp_samples": [{
        "labels": 'rct_grpc:client_requests:rate5m{' + IDENTITY + ',status="UNAVAILABLE"}', "value": 100 / 15}]})
    tests.append({"expr": expression(8).replace('instance=~".*"', 'instance="idle:9091"'), "eval_time": "5m",
                  "exp_samples": [{"labels": '{' + idle + '}', "value": 0}]})
    tests.append({"expr": expression(9), "eval_time": "5m", "exp_samples": [
        {"labels": 'up{job="academic-service",instance="a:9091",environment="synthetic"}', "value": 1},
        {"labels": 'up{job="attendance-service",instance="down:9093",environment="synthetic"}', "value": 0},
    ]})
    # New recordings have no fabricated historical samples before warm-up.
    tests.append({"expr": expression(1), "eval_time": "0m", "exp_samples": []})
    for panel in [1, 2, 3, 4]:
        assert "grpc_client_duration_seconds_bucket" not in expression(panel)
    assert all(t["instant"] and not t["range"] for t in panels()[7]["targets"])
    with tempfile.TemporaryDirectory(prefix="rct-grpc-unit-") as tmp:
        fixture = Path(tmp) / "cases.yml"
        fixture.write_text(yaml.safe_dump({"rule_files": [str(RULE)], "evaluation_interval": "30s", "tests": [{
            "interval": "15s", "input_series": inputs, "promql_expr_test": tests}]}, sort_keys=False))
        subprocess.run([os.environ.get("PROMTOOL", "promtool"), "test", "rules", str(fixture)], check=True)
    print("gRPC aggregation, identity/filter, error-rate, idle NaN and warm-up: PASS")


def runtime(evidence):
    """Replay24h at2415 raw buckets/69 bounds and the unchanged500k cap.

    Recordings are offline analytic fixtures for a uniform histogram, not a
    production backfill claim. Actual rule evaluation is covered by unit();
    short raw-query equivalence is additionally checked by the running engine.
    """
    evidence = evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    assert shutil.disk_usage(evidence).free > 5 * 1024**3, "need5GiB for owned synthetic seed"
    end = int(time.time()) // 30 * 30 - 3 * 3600
    start = end - 86400
    seed = evidence / "grpc.openmetrics"
    db = evidence / "tsdb"
    env = dict(os.environ, GOMEMLIMIT="192MiB", GOMAXPROCS="2")
    records = []
    for i in range(35):
        job = ["academic-service", "attendance-service", "schedule-service"][i % 3]
        records.append('job="' + job + '",instance="caller' + str(i // 5) + ':9091",environment="synthetic",'
                       'service="' + job + '",grpc_service="Target' + str(i % 2) + '",method="Method' + str(i) + '"')
    seed_samples = 0
    with seed.open("w", encoding="utf-8", newline="\n") as output:
        def write(metric, labels, step, value):
            nonlocal seed_samples
            line = metric + "{" + labels + "} "
            for timestamp in range(start - 300, end + 1, step):
                output.write(line + format(value(timestamp - start + 300), ".12g") + " " + str(timestamp) + "\n")
                seed_samples += 1

        output.write("# TYPE grpc_client_duration_seconds histogram\n")
        for i, labels in enumerate(records):
            status = "UNAVAILABLE" if i % 5 == 0 else "OK"
            for bucket in range(1, 70):
                edge = format(bucket * .005, ".3f") if bucket < 69 else "+Inf"
                fraction = min(bucket / 68, 1)
                write("grpc_client_duration_seconds_bucket", labels + ',status="' + status + '",le="' + edge + '"',
                      15, lambda seconds, f=fraction: seconds * f)
            write("grpc_client_duration_seconds_count", labels + ',status="' + status + '"', 15, lambda seconds: seconds)
        output.write("# TYPE rct_grpc:client_duration_seconds:quantile5m gauge\n")
        for labels in records:
            for q in ["0.5", "0.95", "0.99"]:
                write("rct_grpc:client_duration_seconds:quantile5m", labels + ',quantile="' + q + '"',
                      30, lambda seconds, q=q: .34 * float(q))
        output.write("# TYPE rct_grpc:client_requests:rate5m gauge\n")
        for i, labels in enumerate(records):
            write("rct_grpc:client_requests:rate5m", labels + ',status="' + ("UNAVAILABLE" if i % 5 == 0 else "OK") + '"',
                  30, lambda seconds: 1)
        output.write("# EOF\n")
    with (evidence / "seed.log").open("w") as log:
        subprocess.run([os.environ.get("PROMTOOL", "promtool"), "tsdb", "create-blocks-from", "openmetrics", "--quiet",
                        str(seed), str(db)], stdout=log, stderr=log, env=env, check=True, timeout=300)
    # Retain the TSDB and logs; remove only this task's verified large text seed.
    assert seed.parent == evidence and seed.name == "grpc.openmetrics"
    seed.unlink()
    config = evidence / "prometheus.yml"
    config.write_text(yaml.safe_dump({"global": {"scrape_interval": "15s"}, "scrape_configs": []}))
    result = {"raw_bucket_series": 2415, "bounds": 69, "scrape_seconds": 15,
              "record_seconds": 30, "seed_samples": seed_samples, "max_samples": 500000,
              "qualification": "Offline analytic recordings; raw equivalence + production rule unit tests, no production history backfill",
              "queries": []}
    with (evidence / "prometheus.log").open("w") as log:
        process = subprocess.Popen([os.environ.get("PROMETHEUS", "prometheus"), "--config.file=" + str(config),
                                    "--storage.tsdb.path=" + str(db), "--storage.tsdb.retention.time=48h",
                                    "--web.listen-address=127.0.0.1:19094", "--query.max-samples=500000",
                                    "--query.max-concurrency=4", "--query.timeout=30s"], stdout=log, stderr=log, env=env)
        def request(path, params=None):
            url = "http://127.0.0.1:19094" + path
            if params:
                url += "?" + urllib.parse.urlencode(params)
            with urllib.request.urlopen(url, timeout=40) as response:
                return json.load(response)
        try:
            for attempt in range(60):
                if process.poll() is not None:
                    raise AssertionError("owned Prometheus exited before readiness")
                try:
                    with urllib.request.urlopen("http://127.0.0.1:19094/-/ready", timeout=2) as ready:
                        assert ready.status == 200
                    break
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(.5)
            else:
                raise AssertionError("owned Prometheus readiness timeout")
            assert request("/api/v1/status/flags")["data"]["query.max-samples"] == "500000"
            for hours in [1, 24]:
                for panel in [1, 2, 3, 4]:
                    step = max(30, hours * 3600 // 1440)
                    then = time.monotonic()
                    response = request("/api/v1/query_range", {"query": expression(panel), "start": end - hours * 3600,
                                                               "end": end, "step": step})
                    assert response["status"] == "success", response
                    rows = response["data"]["result"]
                    assert len(rows) == (7 if panel == 4 else 35), (panel, len(rows))
                    assert all(math.isfinite(float(v)) for row in rows for _, v in row["values"])
                    result["queries"].append({"hours": hours, "panel": panel, "step": step, "series": len(rows),
                                               "seconds": round(time.monotonic() - then, 3)})
            for panel in [1, 2, 3]:
                raw = request("/api/v1/query", {"query": expression(7, panel - 1), "time": end})["data"]["result"]
                recorded = request("/api/v1/query", {"query": expression(panel), "time": end})["data"]["result"]
                def index(rows):
                    return {tuple(sorted((k, v) for k, v in row["metric"].items() if k not in {"__name__", "quantile"})):
                            float(row["value"][1]) for row in rows}
                a, b = index(raw), index(recorded)
                assert a.keys() == b.keys() and all(math.isclose(a[k], b[k], rel_tol=1e-8) for k in a)
            filtered = expression(1).replace('job=~".*"', 'job="attendance-service"').replace(
                'instance=~".*"', 'instance="caller0:9091"')
            rows = request("/api/v1/query", {"query": filtered, "time": end})["data"]["result"]
            assert len(rows) == 2 and all(row["metric"]["job"] == "attendance-service" for row in rows)
            assert request("/api/v1/query", {"query": expression(1).replace('job=~".*"', 'job="missing"'),
                                             "time": end})["data"]["result"] == []
            try:
                request("/api/v1/query", {"query": "sum(", "time": end})
            except urllib.error.HTTPError as error:
                assert error.code == 400
            else:
                raise AssertionError("invalid query must stay an error")
            # Reproduce the former24h failure with identical cardinality and cap.
            try:
                request("/api/v1/query_range", {"query": expression(7), "start": start, "end": end, "step": 60})
            except urllib.error.HTTPError as error:
                body = json.load(error)
                assert "too many samples" in body.get("error", ""), body
                result["original_raw24h"] = "sample_limit_error_reproduced"
            else:
                raise AssertionError("representative original query did not reproduce sample-limit failure")
            result["status"] = "PASS"
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            result["owned_process_stopped"] = process.poll() is not None
            (evidence / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", type=Path, help="explicit new owned evidence directory; requires runtime lease")
    args = parser.parse_args()
    unit()
    if args.runtime:
        runtime(args.runtime)
