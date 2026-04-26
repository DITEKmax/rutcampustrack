# Dev setup — runbook для новых разработчиков

Быстрый чеклист для старта разработки после `git clone`.

## 1. Prerequisites

- **JDK 21** — Microsoft Build of OpenJDK 21 или Temurin 21.
  Переменная `JAVA_HOME` должна указывать на JDK (не JRE).
- **Docker Desktop** (Windows/Mac) или Docker Engine (Linux). Для
  Windows — WSL2 backend.
- **Node 22** — для `frontends/`.
- **Python 3.12** — только если собираетесь редактировать
  `notification-bot`.

## 2. Gradle build

```bash
# Windows PowerShell
$env:JAVA_HOME = "C:\Users\<you>\.jdks\ms-21.0.9"
.\gradlew.bat build

# Linux/Mac
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

Первый build скачивает зависимости (~5-10 мин), последующие — ~30s.

## 3. Testcontainers reuse (M08 D5, NEW-158)

**Default behavior:** `ContainerTestBase` + все `Abstract*IntegrationTest`
имеют `.withReuse(true)` для контейнеров (Postgres, Mongo, Redis,
RabbitMQ). Это даёт ~5× speedup при повторных прогонах IT локально
— контейнер не перезапускается между test runs.

**Что нужно сделать разработчику:**

Создайте файл `~/.testcontainers.properties` (в home-директории):

```properties
testcontainers.reuse.enable=true
```

- **Windows:** `C:\Users\<you>\.testcontainers.properties`
- **Linux/Mac:** `$HOME/.testcontainers.properties`

После этого `./gradlew integrationTest` переиспользует контейнеры
между прогонами в пределах одной dev-сессии.

**Как проверить что reuse работает:**

```bash
./gradlew :services:auth-service:integrationTest --tests AuthIT
# ... первый прогон ~30s (containers starting)

./gradlew :services:auth-service:integrationTest --tests AuthIT
# ... второй прогон ~8s (containers reused)

docker ps
# Должны видеть reused контейнеры с label
# org.testcontainers.hash=<same hash between runs>
```

**Что делать если reuse не работает:**

- `testcontainers.reuse.enable=true` — проверить что файл читается:
  `cat ~/.testcontainers.properties` (или `type` на Windows).
- Docker Desktop сборки перезаписывают labels — после `docker system
  prune -a` reuse сломается, нужен первый «свежий» прогон.
- При изменении image/ports/env контейнера — reuse-hash меняется,
  контейнер создаётся заново (expected behavior).

**Что reuse НЕ делает:**

- **Не переживает рестарт Docker Desktop** — контейнеры остановятся.
- **Не синхронизирует state между тестами** — каждый тест должен
  сам очищать БД через `@BeforeEach` / `@Sql(scripts="/cleanup.sql")` /
  `mongoTemplate.dropCollection(...)`.
- **Не активен на CI** — GitHub Actions runners не имеют
  `~/.testcontainers.properties`, контейнеры создаются fresh каждый
  job. Это намеренно (CI = production-like, fresh state).

## 4. FlywayMigrationIT exception (M08 Группа 3)

`FlywayMigrationIT` (будет добавлен в M08 Группа 3) использует
**fresh container** (не reuse) для валидации `freshInstallAppliesAll
Migrations` template. Если этот тест почему-то reuse'ит контейнер —
он может пропустить bug в миграциях (Flyway видит существующую
schema, skip'ает migrate). В таком случае — проверить что класс
создаёт свой собственный контейнер с `.withReuse(false)`.

## 5. Infrastructure для frontend dev (без gradle bootRun)

Если нужна только инфра (docker-compose.yml), без запуска Java-сервисов:

```bash
docker compose up -d postgres-academic postgres-schedule mongo-attendance redis rabbitmq
docker compose ps
```

Для запуска всех 5 Java-сервисов одновременно см.
`scripts/m07-g3-launch-services.sh` (linux/mac) или аналог для
Windows.

## 6. Частые проблемы

- **`POSTGRES container failed to start`** — занят порт 5432 на
  хосте. Убить: `docker ps` + `docker stop <id>`, либо положить в
  `spring.datasource.url` явный random-port (Testcontainers сам
  маппит).
- **`Could not find a valid Docker environment`** — запустить
  Docker Desktop.
- **`ryuk container is not ready`** — Testcontainers ожидает
  `testcontainers/ryuk:0.8.x` для автоматической cleanup. При
  повторной проблеме — pull вручную:
  `docker pull testcontainers/ryuk:0.8.1`.

## Источники

- M08 DECISIONS D5 — `docs/milestones/M08-test-infrastructure/DECISIONS.md`
- M01 ContainerTestBase — `services/shared/shared-test-containers/src/testFixtures/`
- Testcontainers reuse docs — https://java.testcontainers.org/features/reuse/
