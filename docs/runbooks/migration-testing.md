# Migration testing — runbook (NEW-159)

Как писать и поддерживать migration tests для schedule_db, academic_db
и attendance_db (Mongo).

## Зачем

Flyway `validate()` на startup проверяет только checksum'ы уже
применённых миграций. Он **не** проверяет:
- freshness install (что полный набор миграций проходит с нуля);
- data preservation (что пользовательские данные переживают schema
  evolution);
- rollback semantics (Flyway Community не поддерживает,
  data-preservation — proxy-проверка);
- Mongo programmatic indexes (нет Flyway — нужна отдельная
  smoke-проверка).

`FlywayMigrationIT` per-сервис отрабатывает эти сценарии на
dedicated Testcontainer'е (fresh Postgres, без reuse — это M08 D5
exception).

## Артефакты

| Файл | Сервис | Покрытие |
|------|--------|----------|
| `services/schedule-service/.../migration/FlywayMigrationIT.java` | schedule | V1..V12, 3 template'а |
| `services/academic-service/.../migration/FlywayMigrationIT.java` | academic | V1..V17, 3 template'а |
| `services/attendance-service/.../integration/MongoIndexIT.java` | attendance | 5 Mongo индексов smoke |
| `services/academic-service/.../homework/HomeworkMigrationIT.java` | academic | V13-specific schema asserts |
| `services/shared/shared-test-containers/.../MigrationTestUtils.java` | shared | `runMigrationsUpTo(version)`, `runAllMigrations`, `clean` |

auth-service **не имеет** своих Flyway-миграций — таблица `users`
принадлежит academic (01 P0-3 accept). При M12 (Auth Contract
refactor) — выделение schema → отдельная fresh `AuthFlywayMigrationIT`.

## Три шаблона для PostgreSQL

Каждый `FlywayMigrationIT` содержит ровно 3 test-метода:

### 1. `freshInstallAppliesAllMigrations`

```java
@Test
void freshInstallAppliesAllMigrations() {
    MigrationTestUtils.clean(dataSource);
    Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();

    MigrateResult result = flyway.migrate();

    assertThat(result.migrationsExecuted).isPositive();
    assertThat(result.success).isTrue();
    assertThat(flyway.info().pending()).isEmpty();
}
```

**Что проверяет:** полный V1..VN проходит с нуля. Ловит:
- синтаксические ошибки в новой миграции (CREATE без IF NOT EXISTS,
  FK на несуществующую таблицу);
- не-идемпотентный `seed_test_data` (V2 academic) в неправильном
  порядке;
- checksum mismatch при локальном изменении applied-миграции.

### 2. `checksumConsistencyAfterFullMigrate`

```java
@Test
void checksumConsistencyAfterFullMigrate() {
    MigrationTestUtils.clean(dataSource);
    Flyway flyway = ...
    flyway.migrate();
    flyway.validate();                    // бросит FlywayValidateException
    MigrateResult second = flyway.migrate();
    assertThat(second.migrationsExecuted).isZero();
}
```

**Что проверяет:** повторный `migrate()` не делает никаких действий.
Ловит баг «миграция не попала в `flyway_schema_history`».

### 3. `dataPreservationAcrossMigrations`

```java
@Test
void dataPreservationAcrossMigrations() {
    MigrationTestUtils.clean(dataSource);
    MigrationTestUtils.runMigrationsUpTo(dataSource, "1");

    // INSERT в V1 schema
    jdbc.update("INSERT INTO ...");

    MigrationTestUtils.runAllMigrations(dataSource);

    // assert row всё ещё на месте
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM ... WHERE id = ?",
            Integer.class, id)).isEqualTo(1);
}
```

**Что проверяет:** пользовательские данные не теряются при
schema-evolution. Ловит:
- `DROP COLUMN` без миграции данных;
- `NOT NULL` добавленный без backfill'а;
- `CHECK` constraint, которым не удовлетворяют legacy rows;
- изменение enum значений без `ALTER TYPE ... ADD VALUE`.

## Процесс добавления новой миграции

1. **Написать `V{N+1}__description.sql`** в `services/<svc>-app/src/main/resources/db/migration/`.
2. **Запустить Flyway локально** через `docker compose up` + `./gradlew bootRun`. Убедиться что стартует.
3. **Запустить FlywayMigrationIT**:
   ```bash
   ./gradlew :services:<svc>:<app>:integrationTest --tests "*.migration.FlywayMigrationIT"
   ```
   Все 3 template'а должны пройти.
4. **Если `dataPreservationAcrossMigrations` упал** — новая миграция ломает existing data. Варианты:
   - Backfill в той же миграции (`UPDATE ... SET new_col = default_value WHERE new_col IS NULL`).
   - Split на 2 миграции: V{N+1} добавляет nullable column + default, V{N+2} (в следующем релизе) делает NOT NULL.
   - Если breaking change accepted by design — документировать в CHANGELOG + принять что data-preservation не проходит (но тест должен быть adjusted под новую семантику).
5. **НИКОГДА не редактировать уже applied-миграцию** (в прод'е это приведёт к checksum mismatch и break boot'а сервиса). Вместо этого — добавить новую V{N+1}.

## MongoDB (attendance)

Attendance использует programmatic indexes через
`MongoConfig.initIndexes()`, запускается `@PostConstruct` при
startup Spring-контекста. `FlywayMigrationIT` паттерн не применим.

Покрытие — `MongoIndexIT.mongoConfigInitIndexes_createsAllExpectedIndexes`:
проверяет, что все named-индексы созданы после context startup.

**При добавлении нового Mongo-индекса:**
1. Добавить `ops.ensureIndex(new Index().named("..."))` в `MongoConfig.initIndexes()`.
2. Добавить имя индекса в assert'ы `MongoIndexIT.mongoConfigInitIndexes_createsAllExpectedIndexes`.
3. Если индекс на новой коллекции — добавить `mongoTemplate.indexOps(...)` assert блок.

**Миграция существующих данных в Mongo** (rare — Mongo schema-less):
- Через `MigrationRunner` bean с `@EventListener(ApplicationReadyEvent.class)` + idempotent bulk update. Пример — `PushSubscriptionCleanupJob.backfillOnStart` (M05 G7).

## D5 exception — fresh container

`FlywayMigrationIT` использует свой **private static PostgreSQLContainer**
без `.withReuse(true)`. Причина: reuse сохраняет `flyway_schema_history`
между прогонами, и `clean() + migrate()` не перезапустит миграции с
нуля. Баг в clean-install scenario скроется — `freshInstallAppliesAllMigrations`
превратится в false positive.

Локальный DX trade-off: +10-15s на каждый прогон `FlywayMigrationIT`
(container startup). Остальные `*IT` используют reuse через
Abstract базы (см. `docs/runbooks/dev-setup.md`).

## FAQ

**Q: Почему 3 template'а а не больше?**
A: OWNER-ANSWERS P2-8/3 acceptance формулирует «three test templates»
   как минимум. Добавление специфических schema-asserts (как
   `HomeworkMigrationIT`) — параллельный pattern для конкретных
   инвариантов отдельной миграции.

**Q: Baselining existing БД?**
A: `spring.flyway.baseline-on-migrate=true` (применяется в M06/G8
   hot-patches как safety net при deploy'е с changed `flyway_schema_history`).
   В тестах Всегда clean install — не baseline.

**Q: Rollback тесты?**
A: Flyway Community не поддерживает `undo`-миграции. Rollback =
   manual hotfix миграция V{N+1}. `dataPreservationAcrossMigrations`
   — proxy-проверка что данные не теряются между N и N+1.

## Источники

- M08 PLAN.md Группа 3 — `docs/milestones/M08-test-infrastructure/PLAN.md`
- M08 DECISIONS D5 — `docs/milestones/M08-test-infrastructure/DECISIONS.md`
- M01 `MigrationTestUtils` — shared-test-containers testFixtures
- OWNER-ANSWERS P2-8/3 — `docs/report-before-v0.0.0/OWNER-ANSWERS.md`
