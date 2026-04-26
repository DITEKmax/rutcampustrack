---
phase: 58-admin-bug-006-fixes
plan: 06
subsystem: academic-service
tags: [bug-006, groups, promotion, archival, flyway, program-type, hateoas, rfc7807]
dependency-graph:
  requires:
    - "Plan 04: единое поле Group.name в активном формате ХХ(х)-NNN"
    - "Plan 02: ConflictException(field,value,msg) + BadRequestException(field,msg)"
  provides:
    - "ProgramType enum (BACHELOR/MASTER) + UnknownProgramTypeException"
    - "GroupNameParser (parse + promote без БД)"
    - "GroupArchivalService (archive → суффикс (выпуск YYYY) + GroupArchivedEvent)"
    - "GroupPromotionService.preview()/execute() с per-prefix savepoint"
    - "GroupRenamedEvent / GroupArchivedEvent (минимальный payload group_id)"
    - "GroupStatus enum (ACTIVE/ARCHIVED/ALL) + GroupSpecifications"
    - "POST /academic/groups/promote[/preview] (ADMIN), GET ?status=&search="
    - "Архивная guard на PUT /groups/{id} (409)"
  affects:
    - "Plan 07 (group-rename-archive-events): placeholder-события уже созданы, достаточно обогатить payload"
    - "Plan 08 (frontend archive/promotion): бэкенд готов, фронтенд подключается к готовым endpoints"
tech-stack:
  added:
    - "Flyway V9__groups_archived_at.sql (archived_at TIMESTAMPTZ + partial index on is_active)"
    - "ClockConfig @Bean Clock.systemUTC() (инжектируемое время)"
    - "JpaSpecificationExecutor<Group> + GroupSpecifications.statusAndSearch"
  patterns:
    - "Sealed interface PromoteResult permits Renamed, Archived — типобезопасный return"
    - "ArgumentCaptor<ApplicationEvent> — Mockito-капчура событий по базовому типу"
    - "Per-prefix planning ДО записи — простая альтернатива PROPAGATION_NESTED savepoint"
    - "Apply order: archive→rename-по-курсу-DESC — детерминированное избегание UNIQUE-коллизии"
key-files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V9__groups_archived_at.sql
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/ProgramType.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/UnknownProgramTypeException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupNameParser.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupSpecifications.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/ClockConfig.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupArchivedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupRenamedEvent.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupStatus.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionSummary.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionPreviewItem.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupNameParserTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java
  modified:
    - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java
decisions:
  - "Package routing: plan указывал GroupRepository в .group пакете, фактический — .repository. Оставил в .repository (историческое место), чтобы не ломать 10+ импортов"
  - "ProgramType fromDigit — статический lookup O(2), не HashMap: для 2 констант overhead HashMap выше чем линейный поиск"
  - "name_conflict в реальных данных недостижим: цепочки курсов детерминированно разрешаются сортировкой DESC. Бранч оставлен на случай corruption"
  - "Event classes — минимальный payload (group_id) согласно D-01; план 07 расширит JSON Schema и/или payload при необходимости"
  - "Clock bean — @Configuration отдельно от существующих (RabbitConfig, CacheConfig), чтобы не смешивать domains"
  - "Архивная guard возвращает ConflictException(field='archived') — переиспользует FIELD_MAP frontend'а (план 02)"
metrics:
  duration: "~18 min executor time"
  completed: "2026-04-14"
  tests_added: "30 Java unit (14 GroupNameParser + 4 GroupArchivalService + 12 GroupPromotionService) + 2 GroupServiceTest"
  files_changed: 23
  commits: 5
---

# Phase 58 Plan 06: Group Promotion Service Summary

BUG-006 п.6 закрыт. Academic-сервис научился переводить группы на следующий курс (per-prefix с конфликт-детекцией) и архивировать их с суффиксом `(выпуск YYYY)` при достижении maxCourse. Модель программы вынесена в `ProgramType` enum, расширяется одной строкой. Все поля, события и REST-endpoints готовы к использованию фронтендом (план 08) и scheduled-триггером (план 07).

## What Changed

### Contract (api-contract)

- **`ProgramType` enum** перенесён в app (не в contract, т.к. чистая бизнес-логика, не DTO).
- **`GroupStatus`** (ACTIVE / ARCHIVED / ALL) — фильтр lifecycle.
- **`PromotionPreviewItem`** — `{id, from, to, action: PROMOTE|ARCHIVE}`. `to=null` для архивации.
- **`PromotionSummary` extends RepresentationModel** — `{toPromote[], toArchive[], conflicts[], dryRun, executed}`, nested `PrefixConflict {prefix, reason, message, groupIds}`.
- **`GroupApi`** расширен тремя endpoints: `POST /promote/preview`, `POST /promote`, `GET ?status=&search=`.

### Backend (academic-service)

- **V9 миграция** — `archived_at TIMESTAMPTZ NULL` + partial index `idx_groups_is_active WHERE is_active=true`. Тестовый baseline `auth-service/V1__baseline.sql` синхронизирован.
- **`Group.archivedAt`** — новое поле с Lombok `@Setter`.
- **`GroupRepository`** — расширен `JpaSpecificationExecutor<Group>` + добавлен `findAllByIsActiveTrue()`.
- **`GroupSpecifications.statusAndSearch`** — JPA Criteria: `is_active` + `LOWER(name) LIKE %search%`.
- **`ClockConfig`** — `@Bean Clock.systemUTC()`: инжектируется в сервисы архивации/промоушена; тесты передают `Clock.fixed(…)`.
- **`GroupNameParser`** — regex `^([А-ЯЁ][А-ЯЁа-яё]{1,3})-(\\d)(\\d)(\\d)$`, sealed interface `PromoteResult (Renamed|Archived)`.
- **`GroupArchivalService`**:
  - `archive(group)`: `name += " (выпуск "+year+")"`, `is_active=false`, `archived_at=now(clock)`, publish `GroupArchivedEvent`.
  - Повторная архивация → `IllegalStateException`.
  - `buildArchivedName(active, year)` — публичная утилита.
- **`GroupPromotionService`**:
  - `preview()` — `@Transactional(readOnly=true)`.
  - `execute()` — `@Transactional`.
  - Алгоритм:
    1. Получить все активные группы.
    2. Группировать по префиксу; `parse_error` → группа в `conflicts[]` с reason=`parse_error`.
    3. Per-prefix `planPrefix(...)`: если есть `unknown_type` → весь префикс в conflicts. Если два источника мапятся в одно новое имя ИЛИ новое имя занято не-освобождающейся группой → `name_conflict` (бранч достижим только при data corruption, оставлен для защиты).
    4. `applyPrefix(...)`: сначала архив (освобождает УИТ-4XX), затем rename по `from` DESC — 3→4, 2→3, 1→2. UNIQUE-коллизия в транзакции исключена.
  - После каждого rename/archive — `publishEvent(GroupRenamedEvent | GroupArchivedEvent)`.
- **`GroupService`**:
  - `createGroup` — try/catch на `ProgramType.fromDigit(parsed.type())` → `BadRequestException("name", "Неизвестный тип программы (цифра N)")` = 400.
  - `updateGroup` — guard в начале: `!group.isActive()` → `ConflictException("archived", id, "Нельзя редактировать архивную группу")` = 409.
  - Новый overload `listGroups(GroupStatus, search, Pageable)` — использует `GroupSpecifications.statusAndSearch`.
- **`GroupController`**:
  - `listGroupsByStatus(status, search, pageable, assembler)` — `@RequireRole({ADMIN, TEACHER})`.
  - `promotePreview()` — `@RequireRole({ADMIN})`.
  - `promote()` — `@RequireRole({ADMIN})`.

### Events

- **`GroupArchivedEvent extends DomainEvent`** — payload `{group_id}`, type=`group.archived`.
- **`GroupRenamedEvent extends DomainEvent`** — payload `{group_id}`, type=`group.renamed`.
- Оба — минимальный payload per D-01 (consumers вызывают gRPC `GetGroup` для актуального имени). План 07 может обогатить payload старым/новым именем через JSON Schema.

### Tests

| Test class | Count | Coverage |
|-----------|-------|----------|
| `GroupNameParserTest` | 14 | ProgramType fromDigit (1/7/5); parse happy/sad paths; promote bachelor/master up/max |
| `GroupArchivalServiceTest` | 4 | suffix+flags+timestamp; event publish; idempotency; buildArchivedName |
| `GroupPromotionServiceTest` | 12 | preview empty/happy/unknown/chain/independence; execute rename/archive/savepoint/twice/full-chain; parse_error detection |
| `GroupServiceTest` (+2 новых) | 14 | `createGroup_unknownProgramType → 400`, `updateGroup_archivedGroup → 409` |

**Total: 44 group unit-тестов зелёные.**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] GroupRepository package mismatch**
- **Found during:** Task 2 (планирование).
- **Issue:** План `<files_modified>` указывал `ru.rutcampustrack.academic.group.GroupRepository`. Фактический код держит `GroupRepository` в `ru.rutcampustrack.academic.repository` (все 10+ импортов по коду).
- **Fix:** Оставил в `.repository`, добавил `JpaSpecificationExecutor` + `findAllByIsActiveTrue` туда. Ни один существующий импорт не сломан. `GroupSpecifications` — отдельный helper в `.group` пакете (где логика промоушена).
- **Commit:** `c4d3424`.

**2. [Rule 2 — Missing critical functionality] ClockConfig не был в плане**
- **Found during:** Task 3 (service нужен Clock bean).
- **Issue:** `GroupArchivalService`/`GroupPromotionService` инжектят `Clock`, но бин нигде не зарегистрирован — приложение упадёт на старте с `NoSuchBeanDefinitionException`.
- **Fix:** Создал `ClockConfig` с `@Bean Clock.systemUTC()`. Тесты передают `Clock.fixed(2026-09-01)` напрямую в конструктор.
- **Commit:** `25e1dd6`.

**3. [Rule 1 — Bug] `ArgumentCaptor<Object>` не матчит `publishEvent(ApplicationEvent)`**
- **Found during:** Task 4 (первый прогон тестов).
- **Issue:** Mockito `captor.capture()` не срабатывал — argument matcher по `Object` не резолвится для overloaded `publishEvent(ApplicationEvent)` / `publishEvent(Object)`.
- **Fix:** Заменил `ArgumentCaptor.forClass(Object.class)` → `ArgumentCaptor.forClass(ApplicationEvent.class)`. Тесты Task 4 позеленели.
- **Commit:** `ea2275d`.

**4. [Rule 1 — Bug] Plan test scenarios не соответствовали реальной семантике**
- **Found during:** Task 4 (тесты `preview_nameConflict_abortsPrefix` / `preview_prefixesAreIndependent` / `execute_perPrefixSavepoint_conflictedPrefixRolledBack`).
- **Issue:** План ожидал что цепочка `УИТ-111 + УИТ-211` → name_conflict (наивный алгоритм). Но threat model T-58-06-03 мандатит «сортировка по курсу DESC чтобы имена освобождались» — то есть цепочка ДОЛЖНА разрешаться. Моя реализация разрешает корректно, поэтому name_conflict в bachelor-1..4 недостижим в валидных данных.
- **Fix:** Переписал 3 теста: `preview_nameConflictDueToUnknownBlockingFreeing` (name_conflict → эффективно unknown_type), `preview_courseChain_resolvesWithoutConflict` (positive-case чейн), `preview_prefixesAreIndependent` переведён на unknown_type. Добавил `execute_renameChain_bachelor1to4_resolvesOrderingNoCollision` — проверяет что полная цепочка 1→2→3→4→archived применяется без UNIQUE-конфликта. Задокументировано в decisions: name_conflict достижим только в corrupted data (бранч остался защитой).
- **Commit:** `ea2275d`.

## Commits

| Hash    | Title |
|---------|-------|
| 5e2d43d | feat(academic-58-06): ProgramType enum + GroupNameParser (BUG-006-6) |
| c4d3424 | feat(academic-58-06): V9 migration + archivedAt + type guard + archived guard (BUG-006-6) |
| 25e1dd6 | feat(academic-58-06): GroupArchivalService + Group{Archived,Renamed}Event (BUG-006-6) |
| ea2275d | feat(academic-58-06): GroupPromotionService preview/execute per-prefix (BUG-006-6) |
| 93d9b62 | feat(academic-58-06): GroupApi promote + listByStatus endpoints (BUG-006-6) |

## Verification

- `./gradlew.bat :services:academic-service:academic-app:compileJava :...:compileTestJava` — **BUILD SUCCESSFUL**.
- `./gradlew.bat :services:academic-service:academic-app:test --tests "ru.rutcampustrack.academic.group.*"` — **BUILD SUCCESSFUL**; 44/44 unit tests green (14 parser + 14 service + 4 archival + 12 promotion).
- `./gradlew.bat :services:academic-service:academic-app:test` — полный прогон даёт 65 ошибок, все категории `NoClassDefFoundError … DockerClientProviderStrategy` — TestContainers требуют Docker Desktop, который на этом хосте выключен (`docker ps` → «system cannot find the file specified»). **Pre-existing env issue, не относится к этому плану.**

## Threat Model Coverage

| Threat | Disposition | Status |
|--------|-------------|--------|
| T-58-06-01 — partial prefix rename | mitigate | ✅ Per-prefix compute-then-apply; планирование до любой записи |
| T-58-06-02 — EoP through /promote | mitigate | ✅ `@RequireRole(ADMIN)` + AOP `RoleCheckAspect` |
| T-58-06-03 — rename collision mid-TX | mitigate | ✅ Архив первый, затем `from` DESC; проверено `execute_renameChain_bachelor1to4_resolvesOrderingNoCollision` |
| T-58-06-04 — double promotion | accept | ✅ Естественная идемпотентность (`execute_twiceAdvancesCourseByOne`); UI preview→confirm добавляется в плане 08 |
| T-58-06-05 — InfoDisclosure в Summary | accept | ✅ ADMIN-only; payload не содержит PII |
| T-58-06-06 — archived group edit | mitigate | ✅ 409 в `GroupService.updateGroup` + unit-тест |
| T-58-06-07 — DoS через большой список | accept | ✅ ≤1000 групп, O(N) алгоритм, одна транзакция |

## Success Criteria

- [x] **AC-7**: POST /groups/promote возвращает PromotionSummary с toPromote/toArchive/conflicts.
- [x] **AC-8**: УИТ-111 (BACHELOR) → УИТ-211 корректно; УИТ-411 → архив с суффиксом `(выпуск YYYY)`.
- [x] Unknown type обрабатывается без крэша: префикс попадает в conflicts с reason=`unknown_type`.
- [x] Per-prefix savepoint: конфликт в одном префиксе не ломает остальные (тест `preview_prefixesAreIndependent`).
- [x] Архивные группы неизменяемы (PUT → 409, unit-тест `updateGroup_archivedGroup`).
- [x] GET /groups?status=active|archived|all + search ILIKE реализован через Specification.
- [x] ProgramType расширяем одной строкой + пересборка.

## Known Stubs

**События `GroupArchivedEvent` / `GroupRenamedEvent` несут минимальный payload (group_id)** — по D-01 это валидная публикация, но план 07 может расширить payload старым/новым именем или JSON Schema для консьюмеров, которые не хотят делать gRPC-лукап. Не блокирующий стаб: текущий payload соответствует паттерну `GroupUpdatedEvent` и пропускается через существующий `DomainEventListener` → RabbitMQ без изменений.

## Threat Flags

Нет новых threat surface-ов. Все 7 угроз из `<threat_model>` учтены (см. таблицу выше). Scheduled-триггер (cron-выполнение) в этот план НЕ включён — plan 07 подключит `@Scheduled` с тем же `Clock` bean, поэтому оба пути (ручной ADMIN / авто 14-дней-до-семестра) используют одну и ту же сервис-логику.

## Cross-Plan Notes

- **Plan 07 (group-rename-archive-events)**: Placeholder-события `GroupRenamedEvent` / `GroupArchivedEvent` уже существуют. План 07 может:
  1. Обогатить payload (old_name / new_name) — DomainEvent base class это поддерживает.
  2. Добавить JSON Schema в `event-schemas/` для downstream консьюмеров.
  3. Подключить `@Scheduled(cron = "0 0 0 */1 * *")` + проверку `14 дней до semester.startDate` для авто-промоушена; использовать тот же `GroupPromotionService.execute()` + `Clock` bean.
- **Plan 08 (groups-frontend-archive-promotion)**: REST-контракт (`GroupApi` + DTO) готов. Фронтенд может реализовать `preview → confirm dialog → execute` без backend-правок.
- **Plan 05 (semester-validation)**: Работал параллельно с этим планом; Semester домен не трогался (наш промоушен только читает Group). Конфликтов миграций нет — 05 не взял V9 (мы использовали V9), 05 возьмёт V10+.

## Self-Check: PASSED

Artefact verification (absolute paths):

- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\resources\db\migration\V9__groups_archived_at.sql` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\group\ProgramType.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\group\GroupNameParser.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\group\GroupArchivalService.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\group\GroupPromotionService.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\group\GroupSpecifications.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\config\ClockConfig.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\event\GroupArchivedEvent.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\java\ru\rutcampustrack\academic\event\GroupRenamedEvent.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-api-contract\src\main\java\ru\rutcampustrack\academic\contract\dto\group\GroupStatus.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-api-contract\src\main\java\ru\rutcampustrack\academic\contract\dto\group\PromotionSummary.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-api-contract\src\main\java\ru\rutcampustrack\academic\contract\dto\group\PromotionPreviewItem.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\group\GroupNameParserTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\group\GroupArchivalServiceTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\group\GroupPromotionServiceTest.java` — **FOUND**.

Commits (verified via `git log --oneline`):

- `5e2d43d` — FOUND in git log.
- `c4d3424` — FOUND in git log.
- `25e1dd6` — FOUND in git log.
- `ea2275d` — FOUND in git log.
- `93d9b62` — FOUND in git log.
