"""One-off evidence check; no product test suite or runtime claim."""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[3]
load = lambda path: yaml.safe_load((root / path).read_text(encoding="utf-8"))
prometheus = load("infra/prometheus/prometheus.yml")
compose = load("docker-compose.prod.yml")
application = load("services/mobile-bff/mobile-bff-app/src/main/resources/application.yml")
rules = load("infra/prometheus/rules/service-health.yml")
jobs = prometheus["scrape_configs"]
assert len({j["job_name"] for j in jobs}) == len(jobs), "duplicate jobs"
job = next(j for j in jobs if j["job_name"] == "mobile-bff")
assert job["metrics_path"] == "/actuator/prometheus"
assert job["static_configs"] == [{"targets": ["mobile-bff:9080"]}]
assert set(job) == {"job_name", "metrics_path", "static_configs"}
bff = compose["services"]["mobile-bff"]
assert "ports" not in bff
assert "9080" in bff["expose"]
assert "private_net" in bff["networks"]
assert "private_net" in compose["services"]["prometheus"]["networks"]
assert not any(k.startswith("MANAGEMENT_") or k == "MOBILE_BFF_PORT" for k in bff["environment"])
assert application["server"]["port"] == "${MOBILE_BFF_PORT:9080}"
assert "server" not in application["management"]
assert "prometheus" in application["management"]["endpoints"]["web"]["exposure"]["include"].split(",")
prod = load("services/mobile-bff/mobile-bff-app/src/main/resources/application-prod.yml")
assert "server" not in prod and "management" not in prod
service_down = next(r for g in rules["groups"] for r in g["rules"] if r.get("alert") == "ServiceDown")
assert service_down["expr"] == "up == 0"
assert service_down["for"] == "1m"
assert service_down["labels"]["severity"] == "critical"
build = (root / "services/mobile-bff/mobile-bff-app/build.gradle.kts").read_text(encoding="utf-8")
assert 'runtimeOnly("io.micrometer:micrometer-registry-prometheus")' in build
print(f"PASS YAML/config/source consistency; {len(jobs)} unique jobs; mobile-bff:9080/actuator/prometheus; private_net; ServiceDown up == 0 for 1m")
print("NOT RUN: Prometheus-specific promtool validation, resolved dependency/build, HTTP scrape, evaluated alert, external delivery")
