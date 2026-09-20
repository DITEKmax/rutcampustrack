# SQL17/V26 exact coverage repair — compact packet

Дата: 2026-09-10. Роль: fresh bounded implementation leaf, sole writer только
двух назначенных integration-test файлов. Risk: S3. Base revision:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## 1. Goal

Закрыть два MEDIUM finding свежего Sol review `SQL17_V26_EXACT4_FAIL`: дать
точное отрицательное coverage для cross-origin identity V17 и для V26
campus-map metadata/publication/demand-dedupe. Production SQL остаётся без
изменений.

## 2. Context / evidence

Production SQL ранее признан корректным. Frozen source hashes до этой правки:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18760 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` | 17109 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2` | 23649 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B` | 51464 |

До новых assertions последний runtime был `Academic 15/15 + Schedule 7/7 +
Flyway 3/3`, aggregate `25/25`, exit `0`; он остаётся baseline evidence и не
доказывает добавленные assertions. Его артефакт находится в
`../sql17-v26-runtime-fix/runtime-pass-final-2026-09-10.md`.

## 3. Relevant scope

Product/test scope ровно:

1. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java`
2. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`

Новая evidence-only область: `.agent/student-academic-b/sql17-v26-coverage-repair/`.

## 4. Required behavior

- V17 recurring и one-off создают валидную control lesson с собственным
  origin, затем проверяют cross-origin `INSERT` и origin-only `UPDATE` с
  nested SQLSTATE `P0001` и сообщением `physical lesson identity snapshot does
  not match its occurrence`; после каждой ошибки count/origin/status прежние.
- Cross-origin V17 INSERT использует `cancelled`, чтобы included-slot unique
  index не маскировал identity trigger; update оставляет остальные поля
  неизменными.
- V26 READY width mismatch требует exact `P0001` и
  `ready campus map format metadata does not match its asset`, format count
  прежний.
- После publication V26 INSERT нового `SVG`/`ABSENT` требует exact `P0001` и
  `published campus map plan format is immutable`; counts форматов/assets и
  существующие format/asset прежние.
- V26 demand dedupe проверяет missing intent под другим owner и valid intent с
  другим UUID, но неправильным day; оба дают exact `P0001` и
  `campus map demand dedupe does not match its open intent`, без dedupe row и
  без изменения полного daily aggregate snapshot. Existing exactly-once checks
  сохраняются.

## 5. Constraints

Только tests и evidence. Не менять SQL/proto/repository/config/build,
FlywayMigrationIT, runtime или чужую работу. Не ослаблять broad assertions,
не stage/commit/reset/clean и не создавать детей.

## 6. Existing patterns

Сохранены `currentSchema`, свежая schema на каждый тест, `SAME_THREAD`,
существующие fixture helpers и race diagnostics. Для новых exact paths добавлен
узкий nested `SQLException` state/message helper; существующий broad
`assertSqlFailure` Academic не изменён.

## 7. Acceptance criteria

Все шесть V17 negative operations (recurring/one-off INSERT+UPDATE) имеют
exact state/message и post-failure invariants. V26 покрывает metadata mismatch,
published format INSERT, missing intent и mismatched intent с unchanged data.
Frozen SQL hashes совпадают; изменены только два target IT файла плюс новая
evidence directory; код проходит статическую plausibility-проверку.

## 8. Verification

До записи выполнен fail-closed guard всех четырёх frozen paths. После записи:
revision/hash/bytes guard, targeted `rg`, Java delimiter/import/trailing-space
checks и scoped `git diff --check`. Gradle, Docker, Testcontainers,
PostgreSQL и product runtime в этом leaf не запускаются; новый runtime —
следующий exclusive lease root.

## 9. Do not

Не изменять V17/V26 SQL, production code, Flyway IT, прочие tests/docs/status,
не claim-ить full-role acceptance и не эскалировать Terra: recorded defect или
complexity gate отсутствуют.
