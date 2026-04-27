#!/usr/bin/env bash
# Launch all 5 backend services for M07 G3 openapi-typescript spec harvest.
# Infra (postgres/mongo/redis/rabbitmq) должна быть up через docker compose
# с docker-compose.override.yml (проброс портов на host).
#
# Логи: /tmp/rct-logs/{svc}.log, PID: /tmp/rct-logs/{svc}.pid.
# Остановить: scripts/m07-g3-stop-services.sh

set -u
mkdir -p /tmp/rct-logs
cd "$(dirname "$0")/.." || exit 1

export JAVA_HOME="C:\\Users\\maksd\\.jdks\\ms-21.0.9"

# Common env: application.yml ссылается на container-names (redis, rabbitmq,
# postgres-*, mongo-attendance). Для gradle bootRun на host эти имена не
# резолвятся → override через Spring env-переменные. Все env LOCAL-ONLY;
# они НЕ попадают на VPS (там docker-network резолвит container-names штатно).
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_RABBITMQ_HOST=localhost
export SPRING_DATA_MONGODB_URI="mongodb://rct_user:rct_dev_pass@localhost:27017/attendance_db?authSource=admin"
export SPRING_PROFILES_ACTIVE=dev

# Inter-service URLs — academic/schedule/attendance/gateway обращаются друг
# к другу по container-names. На host надо через localhost + exposed ports.
export AUTH_SERVICE_URL="http://localhost:9090"
export NOTIFICATION_WEB_URL="http://localhost:9094"

# gRPC inter-service: academic(19091), schedule(19092), attendance(19093).
# Spring Boot env → `grpc.client.{name}.address`.
export GRPC_CLIENT_SCHEDULE_SERVICE_ADDRESS="static://localhost:19092"
export GRPC_CLIENT_ACADEMIC_SERVICE_ADDRESS="static://localhost:19091"
export GRPC_CLIENT_ATTENDANCE_SERVICE_ADDRESS="static://localhost:19093"

# OTel: tempo недоступен (мы не поднимаем observability stack для G3) —
# отправим в noop endpoint, warnings в логах приемлемы. Sampling 0.0
# отключает tracing полностью независимо от endpoint'а.
# M16 G1: Spring Micrometer шлёт HTTP/protobuf (port 4318), а не gRPC (4317).
export OTEL_EXPORTER_OTLP_ENDPOINT="http://127.0.0.1:4318/v1/traces"
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.0  # disable tracing

# CRITICAL: выключаем Prometheus metrics exporter. Prometheus Exemplars
# autoconfiguration deadlock'ится с Lettuce Redis connection на host-сетапе —
# Netty event loop пытается резолвить tracer bean в середине Lettuce.connect(),
# одновременно main thread держит BeanFactory lock → deadlock. Подтверждено
# через jstack: main ждёт CompletableFuture в Lettuce, lettuce-nioEventLoop
# ждёт BeanFactory lock в PrometheusExemplarsAutoConfiguration.LazyTracingSpanContext.
# Это bug только для host-local startup (в docker не воспроизводится — возможно
# потому что в docker-сети connection resolve'ится быстрее чем tracer bean).
export MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=false
export MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=false

# M07 G3 safety net: если локальный volume разойдётся с migrations,
# baseline-on-migrate создаст history от текущего state вместо hard-fail.
# ВНИМАНИЕ: только local dev! НЕ включать на VPS — там нужна strict-Flyway.
export SPRING_FLYWAY_BASELINE_ON_MIGRATE=true

start_svc() {
  local name="$1"
  local module="$2"
  local datasource_url="${3:-}"
  echo "[launch] $name → /tmp/rct-logs/$name.log (ds=${datasource_url:-none})"
  (
    [ -n "$datasource_url" ] && export SPRING_DATASOURCE_URL="$datasource_url"
    exec ./gradlew.bat ":services:$module:bootRun" --no-daemon
  ) > "/tmp/rct-logs/$name.log" 2>&1 &
  echo $! > "/tmp/rct-logs/$name.pid"
}

start_svc auth       "auth-service"                      "jdbc:postgresql://localhost:5432/academic_db"
start_svc academic   "academic-service:academic-app"     "jdbc:postgresql://localhost:5432/academic_db"
start_svc schedule   "schedule-service:schedule-app"     "jdbc:postgresql://localhost:5433/schedule_db"
start_svc attendance "attendance-service:attendance-app" ""
start_svc gateway    "api-gateway"                       ""

echo "[launch] all 5 services spawned, waiting for bootRun to exit…"
wait
echo "[launch] all bootRun processes exited"
