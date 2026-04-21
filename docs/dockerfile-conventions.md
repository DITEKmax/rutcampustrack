# Dockerfile Conventions

Единый шаблон для всех Dockerfile'ов проекта. Введён в M06 Группа 1
(OWNER-ANSWERS P2-9/1, NEW-150).

## Применимость

- 6 Java-сервисов: `auth-service`, `academic-app`, `schedule-app`,
  `attendance-app`, `notification-app`, `api-gateway`.
- 1 Python-сервис: `notification-bot`.
- Frontend-контейнеры (`pwa`, `web-panel`, `mini-app`, `landing`) — не
  входят в scope M06 (nginx-only, без backend healthcheck). Закроет M07.

## Шаблон Java (alpine-jre)

```dockerfile
# syntax=docker/dockerfile:1.7

# Stage 1: Build
FROM eclipse-temurin:21-jdk-{alpine|jammy} AS builder
# ... (gradle build)

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-alpine AS extractor
# ... (jarmode layertools)

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER app
EXPOSE <PORT>

HEALTHCHECK --interval=30s --timeout=5s --start-period=<START>s --retries=3 \
    CMD wget -qO- http://localhost:<PORT>/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Почему wget, а не curl

`eclipse-temurin:21-jre-alpine` включает `wget` (busybox applet) из
коробки. Добавление `curl` = +~7MB и extra supply-chain surface без
compensating value. `wget -qO-` достаточен для Spring Boot
`/actuator/health`.

Решение M06 D1 — см. `docs/milestones/M06-ops-supply-chain/DECISIONS.md`.

Для debug через `docker exec` — либо `wget -qO- ...` (уже есть), либо
временный `apk add --no-cache curl` в exec-сессии.

### Параметры HEALTHCHECK

| Параметр | Значение | Почему |
|----------|----------|--------|
| `--interval=30s` | 30с | Spring Boot actuator дёшевый; 30с — не спам |
| `--timeout=5s` | 5с | /health обычно <100ms; 5с — запас на GC pause |
| `--start-period=45-60s` | 45-60с | JVM warm-up + Hibernate + Flyway: 30-45с на ЖМ; Mongo startup дольше |
| `--retries=3` | 3 | 3 × 30с = 90с окно на восстановление |

| Сервис | start-period | endpoint |
|--------|--------------|----------|
| auth-service | 45s | /actuator/health |
| academic-app | 60s | /actuator/health |
| schedule-app | 60s | /actuator/health |
| attendance-app | 45s | /actuator/health |
| notification-app | 60s | /actuator/health/liveness |
| api-gateway | 30s | /actuator/health |

`start-period` === `depends_on.condition: service_healthy` в compose:
сервис становится «healthy» только после первого успешного
HEALTHCHECK'а после `start-period`.

### notification-app → `/actuator/health/liveness`

В отличие от остальных сервисов, notification-web использует
**liveness**-подпробу, а не полный health. Причина: notification-web —
WebSocket-forwarder, здоровье Mongo/Rabbit не блокирует TCP-прием,
но блокировало бы classic `/health` (все indicator'ы `UP`). Liveness
= «JVM жив», readiness отдельно. См. M04 QA6.

## Шаблон Python (slim)

```dockerfile
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -s /bin/false app

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN chown -R app:app /app

USER app

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=5 \
    CMD curl -fsS http://localhost:<PORT>/health || exit 1

CMD ["python", "-m", "bot"]
```

### Почему curl для bot

`python:3.12-slim` на debian, `wget` отсутствует по умолчанию, а `curl`
всё равно нужен для debug и уже ставится в одну строку с
`apt-get install`. Bot имеет **реальный** HTTP health endpoint на
`0.0.0.0:8081/health` (реализован в `bot/__main__.py:38-54`),
проверяющий watchdog-task + polling-task.

## Последовательность с compose

`docker-compose.prod.yml` уже имеет identical healthcheck'и через
`wget -qO-` / `curl` (M04 QA6). Dockerfile-директива дублирует их —
специально:

- `docker run` (без compose) — сервис всё равно репортит `healthy`.
- `docker inspect <container>` — видна схема healthcheck'а даже
  если compose override удалить.
- «Метаданные образа живут вместе с образом» (OWNER-ANSWERS 4050-4051).

Если однажды compose-healthcheck уйдёт (или переедет под k8s
liveness/readiness probe), Dockerfile-директива служит fallback'ом.

## Конвенции

- `USER app` **до** HEALTHCHECK — сама проба не требует root.
- `EXPOSE` **до** HEALTHCHECK — смысловой порядок: порт → как его
  проверять.
- HEALTHCHECK **до** `ENTRYPOINT` — Docker best-practice, читается
  как «сначала healthcheck spec, потом запуск».
- `wget -qO-` а не `wget --spider` — получаем ответ body, проверяем
  что actuator отвечает валидным JSON (не только TCP).
- `|| exit 1` — явный exit code для docker daemon. Без этого `wget`
  returns 0 при non-2xx (busybox quirk).

## Проверка

```bash
docker build -f services/auth-service/Dockerfile -t auth-test .
docker inspect --format='{{json .Config.Healthcheck}}' auth-test
# Expected: {"Test":["CMD-SHELL","wget -qO- http://localhost:9090/actuator/health || exit 1"],...}

docker run -d --name auth-check -p 9090:9090 auth-test
docker inspect --format='{{.State.Health.Status}}' auth-check
# Expected: starting → healthy
```

## Не входит в scope M06

- `apk/apt add curl` для debug-needs — опционально per-service, если
  команда часто делает `docker exec -it`. Сейчас не ставим.
- SBOM-labels (`LABEL org.opencontainers.*`) — M07 / finalise release.
- `--platform=linux/amd64` pin для buildx — M06 Группа 3 (digest-пин
  рассматривает) или оставим как nit.
