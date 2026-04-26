---
phase: 58-admin-bug-006-fixes
plan: 06
type: execute
wave: 3
depends_on: [04]
files_modified:
  - services/academic-service/academic-app/src/main/resources/db/migration/V9__groups_archived_at.sql
  - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/ProgramType.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupNameParser.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupRepository.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/GroupApi.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/PromotionSummary.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/PromotionPreviewItem.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/GroupStatus.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupNameParserTest.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceUpdateArchivedTest.java
autonomous: true
requirements:
  - BUG-006-6
  - FR-6
  - NFR-3
user_setup: []
must_haves:
  truths:
    - "ProgramType enum: BACHELOR(1, maxCourse=4), MASTER(7, maxCourse=2). Расширяется кодом (новый тип — новая константа enum)"
    - "GroupNameParser парсит 'УИТ-311' → {prefix:'УИТ', course:3, type:1, number:1}; поддерживает регистр 'УВПв'"
    - "ProgramType.fromDigit(int) бросает UnknownProgramTypeException если тип не в enum (используется и для create, и для promotion)"
    - "GroupService.create при типе вне ProgramType → 400 'Неизвестный тип программы (цифра N)'"
    - "GroupArchivalService.archive(group) — ставит суффикс ' (выпуск YYYY)' (год из clock), is_active=false, archived_at=now(). Пишет GroupArchivedEvent"
    - "GroupPromotionService.preview() возвращает {toPromote[], toArchive[], conflicts[]} без изменения БД"
    - "GroupPromotionService.execute() выполняет план: per-prefix savepoint (если конфликт в префиксе — весь префикс откатывается)"
    - "Промоушен работает по ProgramType: course+1 если course < maxCourse; архивация если course == maxCourse"
    - "Unknown program type → префикс в conflicts[] с reason='unknown_type' (не падаем, информируем админа)"
    - "Конфликт имени в префиксе (новое имя уже занято активной группой того же префикса) → весь префикс в conflicts[] с reason='name_conflict'"
    - "POST /api/academic/groups/promote/preview (ADMIN) — dry-run"
    - "POST /api/academic/groups/promote (ADMIN) — execute; publish GroupRenamedEvent для каждой переименованной и GroupArchivedEvent для каждой заархивированной (после commit)"
    - "GET /api/academic/groups?status=active|archived|all&search=... — фильтр по is_active; ILIKE по name"
    - "PUT /api/academic/groups/{id} блокируется для архивных (is_active=false) → 409 'Нельзя редактировать архивную группу'"
    - "archived_at TIMESTAMPTZ NULL — новая колонка, заполняется при архивации"
  artifacts:
    - path: services/academic-service/academic-app/src/main/resources/db/migration/V9__groups_archived_at.sql
      provides: "ALTER TABLE groups ADD COLUMN archived_at TIMESTAMPTZ NULL"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/ProgramType.java
      provides: "enum BACHELOR(1,4), MASTER(7,2) + fromDigit(int)"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupNameParser.java
      provides: "parse(name) + promote(name, type) returns new name OR archive marker"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java
      provides: "archive(group) + buildArchivedName(original, year)"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java
      provides: "preview(), execute() per-prefix savepoint"
  key_links:
    - from: GroupController.promote
      to: GroupPromotionService.execute
      via: "POST /groups/promote (ADMIN)"
      pattern: "manual trigger"
    - from: GroupController.promotePreview
      to: GroupPromotionService.preview
      via: "POST /groups/promote/preview (ADMIN)"
      pattern: "dry-run"
    - from: GroupController.list
      to: GroupRepository.search
      via: "GET /groups?status=&search="
      pattern: "status filter + ILIKE"
    - from: GroupService.update
      to: "409 if group.is_active=false"
      via: "archival guard"
      pattern: "immutable archived"
  depends_on_plan_07: "События GroupRenamedEvent, GroupArchivedEvent создаются в плане 07. В этом плане — заглушки (TODO-комментарий + @SuppressWarnings) которые план 07 заменит на реальные publish-вызовы. Либо — если план 06 идёт после 07 в волне — использовать сразу реальные классы."
---

<objective>
Реализовать промоушен групп на следующий курс ручной кнопкой ADMIN per D-22..D-27 с учётом реальной модели (типы 1/7, суффикс для архивных имён, конфликты по префиксам).

Depends on Plan 04 (формат имени `ХХ(х)-NNN` — парсер работает с новой схемой после V8).

Purpose: закрывает BUG-006 п.6 и AC-7, AC-8.
Output: ProgramType + GroupNameParser + GroupArchivalService + GroupPromotionService + preview/execute endpoints + V9 migration + status filter + PUT-guard для архивных.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@.planning/phases/58-admin-bug-006-fixes/58-04-SUMMARY.md
@CLAUDE.md
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupUpdatedEvent.java

<interfaces>
<!-- Active name: ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$ -->
<!-- Digit positions: [course][type][number] -->
<!-- ProgramType enum: BACHELOR(digit=1, maxCourse=4), MASTER(digit=7, maxCourse=2) -->
<!-- Unknown types → CreateRequest 400; Promotion → prefix skipped with reason='unknown_type' -->
<!-- Archived name: <active> + " (выпуск YYYY)" -->
<!-- Plan 07 publishes GroupRenamedEvent/GroupArchivedEvent; this plan creates the trigger points via ApplicationEventPublisher -->
-->
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ProgramType enum + GroupNameParser — чистая логика</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/ProgramType.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupNameParser.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupNameParserTest.java
  </files>
  <behavior>
    ProgramType:
    - Test 1: ProgramType.fromDigit(1) → BACHELOR (maxCourse=4)
    - Test 2: ProgramType.fromDigit(7) → MASTER (maxCourse=2)
    - Test 3: ProgramType.fromDigit(5) → throws UnknownProgramTypeException
    GroupNameParser:
    - Test 4: parse("УИТ-311") → ParsedName(prefix="УИТ", course=3, type=1, number=1)
    - Test 5: parse("УВПв-511") → prefix="УВПв", course=5, type=1, number=1
    - Test 6: parse("УИТ-311 (выпуск 2026)") → throws (не активный формат)
    - Test 7: parse("уит-311") → throws (lowercase первая)
    - Test 8: parse("УИТ-31") → throws (2 цифры)
    - Test 9: promote("УИТ-111", BACHELOR) → PromoteResult.renamed("УИТ-211")  // course 1→2
    - Test 10: promote("УИТ-411", BACHELOR) → PromoteResult.archived()       // course==maxCourse
    - Test 11: promote("УИТ-271", MASTER) → PromoteResult.archived()          // master course 2==max
    - Test 12: promote("УИТ-171", MASTER) → PromoteResult.renamed("УИТ-271")  // master 1→2
  </behavior>
  <action>
    1. `ProgramType.java`:
       ```java
       public enum ProgramType {
           BACHELOR(1, 4),
           MASTER(7, 2);
           private final int digit;
           private final int maxCourse;
           ProgramType(int digit, int maxCourse) { this.digit = digit; this.maxCourse = maxCourse; }
           public int getDigit() { return digit; }
           public int getMaxCourse() { return maxCourse; }
           public static ProgramType fromDigit(int d) {
               for (ProgramType t : values()) if (t.digit == d) return t;
               throw new UnknownProgramTypeException(d);
           }
       }
       public class UnknownProgramTypeException extends RuntimeException {
           private final int digit;
           public UnknownProgramTypeException(int d) {
               super("Unknown program type digit: " + d);
               this.digit = d;
           }
           public int getDigit() { return digit; }
       }
       ```
    2. `GroupNameParser` (@Component):
       ```java
       private static final Pattern NAME_RE =
           Pattern.compile("^([А-ЯЁ][А-ЯЁа-яё]{1,3})-(\\d)(\\d)(\\d)$");

       public record ParsedName(String prefix, int course, int type, int number) {
           public String toActive() { return prefix + "-" + course + type + number; }
       }
       public sealed interface PromoteResult permits Renamed, Archived {
           record Renamed(String newName) implements PromoteResult {}
           record Archived() implements PromoteResult {}
       }

       public ParsedName parse(String name) {
           Matcher m = NAME_RE.matcher(name);
           if (!m.matches()) throw new IllegalArgumentException("Invalid active group name: " + name);
           return new ParsedName(m.group(1),
               Integer.parseInt(m.group(2)),
               Integer.parseInt(m.group(3)),
               Integer.parseInt(m.group(4)));
       }

       public PromoteResult promote(String name, ProgramType type) {
           ParsedName p = parse(name);
           if (p.course() >= type.getMaxCourse()) return new PromoteResult.Archived();
           var next = new ParsedName(p.prefix(), p.course()+1, p.type(), p.number());
           return new PromoteResult.Renamed(next.toActive());
       }
       ```
    3. Spec — 12 тестов + edge (null, пустая строка).
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupNameParserTest*" --tests "*ProgramType*"</automated>
  </verify>
  <done>12+ тестов зелёные.</done>
</task>

<task type="auto">
  <name>Task 2: V9 миграция + Group.archivedAt + Repository search + GroupService.create type-check</name>
  <files>
    services/academic-service/academic-app/src/main/resources/db/migration/V9__groups_archived_at.sql,
    services/auth-service/src/test/resources/db/migration/V1__baseline.sql,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupRepository.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
  </files>
  <action>
    1. `V9__groups_archived_at.sql`:
       ```sql
       -- BUG-006 п.6: временная метка архивации группы.
       ALTER TABLE groups ADD COLUMN archived_at TIMESTAMPTZ NULL;
       CREATE INDEX idx_groups_is_active ON groups(is_active) WHERE is_active = true;
       ```
    2. Обновить test-baseline (auth V1 и, если есть, academic test-baseline).
    3. `Group.java`: добавить `@Column(name="archived_at") private OffsetDateTime archivedAt;`.
    4. `GroupRepository`:
       - `Optional<Group> findByName(String name)` (для конфликт-чека, если нет).
       - `List<Group> findAllByIsActiveTrue()` (для promotion).
       - `@Query` с JPA Specification или кастомный метод: `search(String query, GroupStatus status, Pageable)` — ILIKE по name + фильтр по is_active. Alternative: дополнить существующий `findAll(Specification)` — см. паттерн из плана 01.
    5. `GroupService.create`:
       - После pre-check existsByName → вызвать `parser.parse(name)` → `ProgramType.fromDigit(parsed.type())` в try/catch UnknownProgramTypeException → бросить новый `ValidationException` с human message "Неизвестный тип программы (цифра N)". Это 400.
       - Добавить тест: create с "УИТ-351" (type=5) → 400.
    6. `GroupService.update`:
       - В начале метода: если `group.getIsActive() == false` → бросить `ConflictException("archived", "Нельзя редактировать архивную группу")`.
       - Добавить тест GroupServiceUpdateArchivedTest: update архивной группы → 409.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupService*Test*"</automated>
  </verify>
  <done>Миграция проходит; 400 на неизвестный тип; 409 на edit архивной; search работает.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: GroupArchivalService + событийный hook</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java
  </files>
  <behavior>
    - Test 1: archive(group "УИТ-411") @ clock 2026-06-01 → group.name="УИТ-411 (выпуск 2026)", is_active=false, archived_at=2026-06-01T00:00Z
    - Test 2: archive публикует ApplicationEvent (TriggerArchivedEvent) — проверить через ApplicationEventPublisher mock
    - Test 3: archive идемпотентна: повторный вызов для уже архивированной группы → no-op (или throw IllegalStateException — на выбор; я бы throw, т.к. промоушен не должен архивировать дважды)
  </behavior>
  <action>
    1. `GroupArchivalService` (@Service):
       ```java
       @Service
       @RequiredArgsConstructor
       public class GroupArchivalService {
           private final ApplicationEventPublisher publisher;
           private final Clock clock;

           @Transactional
           public void archive(Group group) {
               if (!group.getIsActive()) throw new IllegalStateException("Group already archived: " + group.getId());
               int year = LocalDate.now(clock).getYear();
               group.setName(group.getName() + " (выпуск " + year + ")");
               group.setIsActive(false);
               group.setArchivedAt(OffsetDateTime.now(clock));
               publisher.publishEvent(new GroupArchivedEvent(this, group.getId()));
           }

           public String buildArchivedName(String active, int year) {
               return active + " (выпуск " + year + ")";
           }
       }
       ```
    2. **Про GroupArchivedEvent**: план 07 создаёт реальный класс. В этом плане создать минимальный placeholder:
       ```java
       // Placeholder, план 07 расширит payload-контракт через JSON Schema
       public class GroupArchivedEvent extends DomainEvent {
           public record Payload(@JsonProperty("group_id") Long groupId) {}
           public GroupArchivedEvent(Object source, Long groupId) {
               super(source, "group.archived", new Payload(groupId));
           }
       }
       ```
       Поскольку план 07 зависит от 06 (depends_on: [06]), это валидно: placeholder существует, план 07 может тестировать потребление.
    3. Аналогично — создать GroupRenamedEvent:
       ```java
       public class GroupRenamedEvent extends DomainEvent {
           public record Payload(@JsonProperty("group_id") Long groupId) {}
           public GroupRenamedEvent(Object source, Long groupId) {
               super(source, "group.renamed", new Payload(groupId));
           }
       }
       ```
    4. 3 теста.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupArchivalServiceTest*"</automated>
  </verify>
  <done>Архивация работает; событие публикуется; имя с суффиксом корректное.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: GroupPromotionService — preview + execute + per-prefix savepoint</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/PromotionSummary.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/PromotionPreviewItem.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/GroupStatus.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java
  </files>
  <behavior>
    - Test 1: preview() пустая БД → PromotionSummary(toPromote=[], toArchive=[], conflicts=[], dryRun=true)
    - Test 2: preview() с [УИТ-111 bachelor, УИТ-411 bachelor] → toPromote=[{УИТ-111→УИТ-211}], toArchive=[{УИТ-411}], conflicts=[]
    - Test 3: preview() с [УИТ-111, УИТ-211] (конфликт: 111→211 занят) → conflicts=[{prefix:УИТ, reason:name_conflict, details}], toPromote НЕ содержит УИТ
    - Test 4: preview() с [УИТ-351] (type=5 неизвестен) → conflicts=[{prefix:УИТ, reason:unknown_type, digit:5}]
    - Test 5: preview() смешанный: УИТ-111 ok, УВП-111 + УВП-211 конфликт → toPromote=[УИТ-111→УИТ-211], conflicts=[УВП name_conflict]. Префиксы независимы.
    - Test 6: execute() c [УИТ-111] → group renamed в БД; publishes GroupRenamedEvent через ApplicationEventPublisher
    - Test 7: execute() с [УИТ-411] → GroupArchivalService.archive вызывается; publishes GroupArchivedEvent
    - Test 8: execute() смешанный [УИТ-111, УВП-111, УВП-211] → УИТ promoted, УВП откатился (префикс savepoint). Проверить в БД: УИТ-211 exists, УВП-111 остался УВП-111.
    - Test 9: execute() idempotency: повторный запуск после первого → все попадают в conflicts (name_conflict на втором клике, т.к. УИТ-111 уже УИТ-211, а второй run parse найдёт УИТ-211 → promote → УИТ-311, если нет конфликта — просто двигается дальше). Конкретный тест: два execute подряд на УИТ-111 → после первого УИТ-211, после второго УИТ-311. Это нормально. Double-click protection решается UI через preview→confirm.
    - Test 10: parseErrors: группа "BAD-NAME" (невалидна) → в conflicts[] как parse_error (но префикс извлечь нельзя → просто группа пропускается с warning)
  </behavior>
  <action>
    1. `GroupStatus` enum (api-contract): `ACTIVE, ARCHIVED, ALL`.
    2. `PromotionPreviewItem` (api-contract, class не record для HATEOAS):
       ```java
       public class PromotionPreviewItem {
           private Long id;
           private String from;
           private String to; // nullable if archive
           private Action action; // PROMOTE | ARCHIVE
           // ...
           public enum Action { PROMOTE, ARCHIVE }
       }
       ```
    3. `PromotionSummary` (class, HATEOAS RepresentationModel):
       ```java
       public class PromotionSummary extends RepresentationModel<PromotionSummary> {
           private List<PromotionPreviewItem> toPromote;
           private List<PromotionPreviewItem> toArchive;
           private List<PrefixConflict> conflicts;
           private boolean dryRun;
           private boolean executed;

           public static class PrefixConflict {
               private String prefix;
               private String reason; // "name_conflict" | "unknown_type" | "parse_error"
               private String message;
               private List<Long> groupIds;
           }
       }
       ```
    4. `GroupPromotionService`:
       ```java
       @Service
       @RequiredArgsConstructor
       public class GroupPromotionService {
           private final GroupRepository groupRepository;
           private final GroupNameParser parser;
           private final GroupArchivalService archivalService;
           private final ApplicationEventPublisher publisher;

           @Transactional(readOnly = true)
           public PromotionSummary preview() { return compute(false); }

           @Transactional
           public PromotionSummary execute() { return compute(true); }

           private PromotionSummary compute(boolean apply) {
               List<Group> active = groupRepository.findAllByIsActiveTrue();
               Set<String> takenNames = active.stream().map(Group::getName).collect(Collectors.toSet());

               Map<String, List<Group>> byPrefix = new HashMap<>();
               List<PrefixConflict> conflicts = new ArrayList<>();
               List<PromotionPreviewItem> toPromote = new ArrayList<>();
               List<PromotionPreviewItem> toArchive = new ArrayList<>();

               // Group by prefix; capture parse errors
               for (Group g : active) {
                   try {
                       var parsed = parser.parse(g.getName());
                       byPrefix.computeIfAbsent(parsed.prefix(), k -> new ArrayList<>()).add(g);
                   } catch (IllegalArgumentException e) {
                       conflicts.add(new PrefixConflict("", "parse_error", "Invalid name: " + g.getName(), List.of(g.getId())));
                   }
               }

               // Per prefix compute plan, check conflicts
               for (var entry : byPrefix.entrySet()) {
                   String prefix = entry.getKey();
                   List<Group> groups = entry.getValue();
                   List<PromotionPreviewItem> prefixPromote = new ArrayList<>();
                   List<PromotionPreviewItem> prefixArchive = new ArrayList<>();
                   String abortReason = null;
                   String abortMessage = null;
                   List<Long> abortIds = new ArrayList<>();

                   for (Group g : groups) {
                       var parsed = parser.parse(g.getName());
                       ProgramType type;
                       try { type = ProgramType.fromDigit(parsed.type()); }
                       catch (UnknownProgramTypeException e) {
                           abortReason = "unknown_type";
                           abortMessage = "Неизвестный тип программы: цифра " + parsed.type();
                           abortIds.add(g.getId());
                           continue;
                       }
                       var result = parser.promote(g.getName(), type);
                       if (result instanceof PromoteResult.Renamed r) {
                           // conflict check: новое имя занято ДРУГОЙ активной группой того же префикса
                           boolean taken = groups.stream()
                               .anyMatch(other -> !other.getId().equals(g.getId()) && other.getName().equals(r.newName()));
                           if (taken) {
                               abortReason = "name_conflict";
                               abortMessage = "Имя '" + r.newName() + "' уже занято в префиксе " + prefix;
                               abortIds.add(g.getId());
                           }
                           var item = new PromotionPreviewItem(g.getId(), g.getName(), r.newName(), Action.PROMOTE);
                           prefixPromote.add(item);
                       } else {
                           var item = new PromotionPreviewItem(g.getId(), g.getName(), null, Action.ARCHIVE);
                           prefixArchive.add(item);
                       }
                   }

                   if (abortReason != null) {
                       conflicts.add(new PrefixConflict(prefix, abortReason, abortMessage, abortIds.isEmpty() ? groups.stream().map(Group::getId).toList() : abortIds));
                       // Prefix НЕ включается в toPromote/toArchive
                   } else {
                       toPromote.addAll(prefixPromote);
                       toArchive.addAll(prefixArchive);
                       if (apply) applyPrefix(groups, prefixPromote, prefixArchive);
                   }
               }

               return new PromotionSummary(toPromote, toArchive, conflicts, !apply, apply);
           }

           private void applyPrefix(List<Group> groups, List<PromotionPreviewItem> promote, List<PromotionPreviewItem> archive) {
               // ВАЖНО: переименовывать от старшего курса к младшему, чтобы не словить UNIQUE violation.
               // Сортируем промоушен по course DESC (парсер знает course, но проще — по from name).
               promote.sort((a, b) -> b.getFrom().compareTo(a.getFrom()));

               Map<Long, Group> byId = groups.stream().collect(Collectors.toMap(Group::getId, g -> g));
               for (var item : archive) {
                   archivalService.archive(byId.get(item.getId())); // публикует GroupArchivedEvent
               }
               for (var item : promote) {
                   Group g = byId.get(item.getId());
                   g.setName(item.getTo());
                   publisher.publishEvent(new GroupRenamedEvent(this, g.getId()));
               }
           }
       }
       ```
    5. **ВАЖНО про savepoint:** простой подход — считать план per-prefix ДО записи. Если в префиксе конфликт — ничего по этому префиксу не пишем. Остальные префиксы пишутся в той же транзакции. Если нужен настоящий savepoint (для отказа в середине записи) — использовать `TransactionTemplate` с PROPAGATION_NESTED, но для этого нужен Hikari + savepoint support. Пока без NESTED: логика «посчитали → либо весь префикс ок → пишем; либо в conflicts, не пишем» достаточна.
    6. 10 тестов. Использовать `Clock.fixed` для GroupArchivalService.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupPromotionServiceTest*"</automated>
  </verify>
  <done>10+ тестов зелёные; preview и execute работают корректно.</done>
</task>

<task type="auto">
  <name>Task 5: GroupApi + GroupController — preview, execute, status filter</name>
  <files>
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/GroupApi.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java
  </files>
  <action>
    1. В `GroupApi` добавить:
       ```java
       @PostMapping("/groups/promote/preview")
       @Operation(summary = "Preview промоушена групп (dry-run, ADMIN)")
       @RequireRole(UserRole.ADMIN)
       ResponseEntity<PromotionSummary> promotePreview();

       @PostMapping("/groups/promote")
       @Operation(summary = "Выполнить промоушен групп (ADMIN). Запускать после preview.")
       @RequireRole(UserRole.ADMIN)
       ResponseEntity<PromotionSummary> promote();

       @GetMapping("/groups")
       @Operation(summary = "Список групп с фильтром по статусу и поиском")
       @RequireRole({UserRole.ADMIN, UserRole.TEACHER})
       ResponseEntity<PagedModel<EntityModel<GroupResponse>>> list(
           @RequestParam(required = false) String search,
           @RequestParam(defaultValue = "ACTIVE") GroupStatus status,
           Pageable pageable);
       ```
    2. `GroupController`:
       - `promotePreview()` → `promotionService.preview()` + self link.
       - `promote()` → `promotionService.execute()` + self link.
       - `list(...)`: при `status=ACTIVE` → where is_active=true; ARCHIVED → is_active=false; ALL → без фильтра. `search` — ILIKE через Specification (паттерн из плана 01).
    3. Integration test: `@SpringBootTest` + MockMvc:
       - POST /groups/promote/preview от ADMIN → 200
       - POST /groups/promote от TEACHER → 403
       - GET /groups?status=archived&search=УИТ → возвращает только архивные с match по имени
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupControllerTest*" --tests "*GroupIntegrationTest*"</automated>
  </verify>
  <done>Endpoints отвечают правильно; роли проверены; фильтр status работает.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin → POST /groups/promote | ADMIN JWT required |
| admin → POST /groups/promote/preview | ADMIN JWT required |
| GroupArchivalService.archive | internal only (вызывается из Promotion + Plan 07 может дёрнуть вручную) |
| PUT /groups/{id} | ADMIN, с guard на is_active |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-06-01 | Tampering (partial prefix rename) | GroupPromotionService.execute | mitigate | Per-prefix вычисление ДО записи; конфликт → префикс пропущен целиком |
| T-58-06-02 | Elevation of Privilege | POST /groups/promote | mitigate | `@RequireRole(ADMIN)` + integration test |
| T-58-06-03 | Tampering (rename collision mid-transaction) | execute applyPrefix | mitigate | Сортировка rename по course DESC — сначала старшие курсы (чтобы имена освобождались перед тем как младшие их займут) |
| T-58-06-04 | Integrity (double promotion) | execute | accept | Идемпотентность через естественную защиту: после первого run все имена сдвинуты, parser корректно обработает второй клик. UI-уровень: preview→confirm блокирует случайные двойные клики |
| T-58-06-05 | Information Disclosure | PromotionSummary | accept | ADMIN-only; числа/имена — не секрет |
| T-58-06-06 | Tampering (archived group edited) | PUT /groups/{id} | mitigate | 409 guard в GroupService.update если is_active=false |
| T-58-06-07 | Denial of Service (large group list) | promotion | accept | ≤1000 групп, O(N) парсинг, одна транзакция |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — все тесты зелёные
- Миграция V9 проходит
- Manual:
  - Создать УИТ-111, УИТ-411 (BACHELOR)
  - POST /groups/promote/preview → toPromote=[УИТ-111→УИТ-211], toArchive=[УИТ-411]
  - POST /groups/promote → выполнено; БД: УИТ-211 (active), УИТ-411 (выпуск YYYY) (archived)
  - GET /groups?status=archived&search=УИТ → возвращает УИТ-411 (выпуск YYYY)
  - PUT /groups/{id архивной} с новым name → 409 "Нельзя редактировать архивную группу"
</verification>

<success_criteria>
- AC-7: POST /groups/promote возвращает PromotionSummary с promoted/archived/conflicts
- AC-8: УИТ-111 (BACHELOR) → УИТ-211 корректно; УИТ-411 → архив с суффиксом
- Unknown type обрабатывается без крэша (prefix в conflicts с reason=unknown_type)
- Per-prefix savepoint: конфликт в одном префиксе не ломает остальные
- Архивные группы неизменяемы (PUT → 409)
- GET /groups поддерживает status=active|archived|all + search ILIKE
- ProgramType enum расширяем: добавление нового типа = одна строка + пересобрать
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-06-SUMMARY.md`.

## Commit message
`feat(academic): group promotion service (preview/execute), archived suffix, status filter (BUG-006-6)`
</output>

## UAT Steps
1. Backend запущен, чистая БД
2. ADMIN создаёт УИТ-111, УИТ-411, УВП-171 (master), УВП-271 (master)
3. POST /groups/promote/preview → toPromote=[УИТ-111→УИТ-211, УВП-171→УВП-271], toArchive=[УИТ-411, УВП-271 master]
   **Ожидание:** УВП префикс в conflicts (УВП-171→УВП-271, а УВП-271 уже есть как master 2 → архив)
   На самом деле сначала архивация УВП-271, потом promote 171→271 — savepoint справится если сортировать by course DESC (задокументировано в Task 4)
4. POST /groups/promote → execute
5. GET /groups?status=archived → 2 архивных (УИТ-411 (выпуск YYYY), УВП-271 (выпуск YYYY))
6. GET /groups?status=active → УИТ-211, УВП-271 (новая)
7. Попытка PUT на архивную → 409
