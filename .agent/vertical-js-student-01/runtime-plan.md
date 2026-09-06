# Task-isolated runtime gate — JS-STUDENT-01-r1

Date: 2026-09-06. This is the remaining S3 runtime gate. It uses fresh,
disposable resources and never attaches to the normal `rct-*` containers or named
volumes. Do not run the Telegram bot or notification-web. RabbitMQ itself is the
fake outbound sink.

The executable owner is `runtime/run.ps1`. Run `runtime/run.ps1 -ValidateOnly`
first, then run it without switches from the BE worktree. It refuses to start if
any exact task container, network, key directory or host port already exists. It
records every resource it creates and its `finally` cleanup removes only that
recorded set. There is deliberately no force-clean mode for unverified leftovers.

## Resources

| Resource | Task name | Host port |
|---|---|---:|
| Docker network | `rct-js-student-01` | — |
| Academic Postgres | `rct-js-student-01-pg-academic` | 25431 |
| Schedule Postgres | `rct-js-student-01-pg-schedule` | 25432 |
| Mongo replica set | `rct-js-student-01-mongo` | 27027 |
| Redis | `rct-js-student-01-redis` | 26379 |
| RabbitMQ | `rct-js-student-01-rabbit` | 25672 / 35672 |
| auth HTTP | local JVM | 29090 |
| academic HTTP / gRPC | local JVM | 29091 / 29191 |
| schedule HTTP / gRPC | local JVM | 29092 / 29192 |
| attendance HTTP / gRPC | local JVM | 29093 / 29193 |
| mobile-bff HTTP | local JVM | 29080 |
| gateway HTTP | local JVM | 28080 |

Generate process-local random values for `RCT_JS_DB_PASSWORD`,
`RCT_JS_REDIS_PASSWORD`, `RCT_JS_RABBIT_PASSWORD`, `RCT_JS_GRPC_SECRET`,
`RCT_JS_ISSUER_SECRET`, and `RCT_JS_TMA_TOKEN`. Never print or persist their
values. All `java -jar` processes inherit them through the service-specific Spring
environment variables.

## Build and infrastructure

Build all jars once before starting any JVM, so concurrent Gradle processes cannot
corrupt the shared cache:

```powershell
.\gradlew.bat :services:auth-service:auth-app:bootJar :services:academic-service:academic-app:bootJar :services:schedule-service:schedule-app:bootJar :services:attendance-service:attendance-app:bootJar :services:mobile-bff:mobile-bff-app:bootJar :services:api-gateway:bootJar --console=plain
```

Create the isolated network and containers (each `--env` receives the value from
the current process; do not log `docker inspect` environment output):

```powershell
docker network create rct-js-student-01
docker run -d --name rct-js-student-01-pg-academic --network rct-js-student-01 -p 127.0.0.1:25431:5432 --env POSTGRES_DB=academic_db --env POSTGRES_USER=rct_user --env POSTGRES_PASSWORD=$env:RCT_JS_DB_PASSWORD postgres:16
docker run -d --name rct-js-student-01-pg-schedule --network rct-js-student-01 -p 127.0.0.1:25432:5432 --env POSTGRES_DB=schedule_db --env POSTGRES_USER=rct_user --env POSTGRES_PASSWORD=$env:RCT_JS_DB_PASSWORD postgres:16
docker run -d --name rct-js-student-01-redis --network rct-js-student-01 -p 127.0.0.1:26379:6379 redis:7-alpine redis-server --requirepass $env:RCT_JS_REDIS_PASSWORD
docker run -d --name rct-js-student-01-rabbit --network rct-js-student-01 -p 127.0.0.1:25672:5672 -p 127.0.0.1:35672:15672 --env RABBITMQ_DEFAULT_USER=rct_user --env RABBITMQ_DEFAULT_PASS=$env:RCT_JS_RABBIT_PASSWORD rabbitmq:3.13-management-alpine
docker run -d --name rct-js-student-01-mongo --network rct-js-student-01 -p 127.0.0.1:27027:27017 mongo:7.0 --replSet rs0 --bind_ip_all
docker exec rct-js-student-01-mongo mongosh --quiet --eval "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})"
```

Wait for both `pg_isready`, Redis `PONG`, Rabbit `rabbitmq-diagnostics -q ping`,
Mongo `db.hello().isWritablePrimary`, then start the services. Use unified exec
sessions rather than background windows; record every session and PID. Common
settings are `SPRING_PROFILES_ACTIVE=local`, tracing disabled, the task ports above,
and these addresses:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:25431/academic_db  (academic/auth)
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:25432/schedule_db  (schedule)
SPRING_DATA_MONGODB_URI=mongodb://127.0.0.1:27027/attendance_js_student_01?replicaSet=rs0&directConnection=true
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=26379
SPRING_RABBITMQ_HOST=127.0.0.1
SPRING_RABBITMQ_PORT=25672
AUTH_SERVICE_URL=http://127.0.0.1:29090
ACADEMIC_GRPC_ADDRESS=static://127.0.0.1:29191
SCHEDULE_GRPC_ADDRESS=static://127.0.0.1:29192
ATTENDANCE_GRPC_ADDRESS=static://127.0.0.1:29193
MOBILE_BFF_URL=http://127.0.0.1:29080
```

Start academic first so Flyway creates the shared academic/auth schema. Start auth,
schedule, attendance, mobile-bff, then gateway. Map the generated secrets to each
service's documented variables (`POSTGRES_*_PASSWORD`, `REDIS_PASSWORD`, Rabbit
password, `GRPC_SECRET`, `INTERNAL_ISSUER_SECRET`, `TMA_BOT_TOKEN`) without writing
their values to commands or evidence. Each service must reach `health=UP`; capture
only status, port, PID, start time and trace/correlation IDs.

The executable harness sets every JVM HTTP and gRPC listener to loopback and passes
the six random task secrets through service-specific process environments. The
expanded commands are intentionally not duplicated here; `runtime/run.ps1` is the
executable source.

Copy and execute the SQL without interpolating it into a shell string:

```powershell
docker cp .agent/vertical-js-student-01/runtime/seed-academic.sql rct-js-student-01-pg-academic:/tmp/seed.sql
docker exec rct-js-student-01-pg-academic psql -v ON_ERROR_STOP=1 -U rct_user -d academic_db -f /tmp/seed.sql
docker cp .agent/vertical-js-student-01/runtime/seed-schedule.sql rct-js-student-01-pg-schedule:/tmp/seed.sql
docker exec rct-js-student-01-pg-schedule psql -v ON_ERROR_STOP=1 -U rct_user -d schedule_db -f /tmp/seed.sql
```

## Isolated seed

After academic and schedule Flyway complete, use `psql` inside the two task
containers. In academic DB: make semester id 1 active for
`CURRENT_DATE - 30 .. CURRENT_DATE + 30`, set seeded login `student` to
`is_headman=false, group_id=1`, and insert one subject if absent. In schedule DB:
insert two task-only schedule items for group 1, subject 1, semester 1 and two
concrete ACTIVE lessons on `CURRENT_DATE`; set their time window around current
Europe/Moscow local time. Return the two lesson IDs as `pendingLessonId` and
`presentLessonId`. No existing database is touched because both Postgres containers
have anonymous writable layers.

## Probes and evidence

1. Login through gateway `POST http://127.0.0.1:28080/api/auth/login`; retain the
   access token only in memory and never print it.
2. Stop only `rct-js-student-01-rabbit`. POST UNAVAILABLE/TIMEOUT for
   `pendingLessonId` through gateway with an opaque new `Idempotency-Key`. Expect
   HTTP 200 `PENDING_CONFIRMATION`, request `PENDING/AUTO_GEO_FAILURE`, and
   `retryAt`. Query task Mongo and record counts only: one request, one receipt,
   one pair state and one `attendance_outbox` row with status `pending`.
3. Re-send the same command/key. Expect byte-equivalent ACK and unchanged counts.
4. Start Rabbit. After at least one five-second publisher tick, expect the same
   outbox row to be `sent` with a non-null `sent_at`. Rabbit logs must show the
   `rut-uit.events` publish; no Telegram or external notification process exists.
5. POST campus coordinates from the academic seed for `presentLessonId`. Expect
   HTTP 200 PRESENT. Query task Mongo: attendance is PRESENT/STUDENT_GEO with
   `marked_by=null`; receipt, pair state and `attendance.marked` outbox are present.
6. Send invalid-signature, expired, wrong-issuer and wrong-audience external JWTs
   through gateway; expect 401 before BFF. The three claim-negative tokens are
   RS256-signed with the disposable task auth key; token material stays in memory.
   Send client `X-User-*` spoof headers with the valid token and verify the resulting
   records still use the signed student id.
7. Assert the path with observable state: gateway HTTP response, byte-identical BFF
   replay, the same idempotency key in the attendance receipt, signed student ID,
   schedule lesson ID, academic subject metadata, outbox trace ID and all six live
   service processes. Retain gateway/BFF/attendance/schedule/academic logs beside
   `runtime-result.json` as supporting evidence without persisting credentials.

PASS requires all response assertions, persisted counts/provenance, pending-to-sent
recovery and the cross-service trace. A startup or mandatory probe failure is
BLOCKED and must retain logs. Unit/IT results are supporting evidence, not a
substitute for this gate.

## Cleanup

Stop the six recorded JVM PIDs, then remove only the five exact task containers and
the exact task network. Verify `docker ps -a --filter name=rct-js-student-01` is
empty. Never use a wildcard removal command and never prune images, volumes or
networks.
