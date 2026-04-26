# 04. Attendance Service — отчёт аудита

## Сводка

Attendance Service — самый «мясистый» из бизнес-сервисов RutCampusTrack: 5 доменов (`checkin`, `report`, `marking`, `excuse`, `latecheckin`), 2 коллекции MongoDB (`attendances`, `excuse_tickets`, `late_checkin_requests`), 2 gRPC-клиента (Schedule, Academic), 1 RabbitMQ-консьюмер (8 типов событий) + 3 паблишера (attendance, excuse, late-checkin). Контракт-first соблюдён: `attendance-api-contract` — чистый `java-library` без Lombok, Request = `record`, Response = `class extends RepresentationModel`, маппинги в интерфейсах. RFC 7807 Problem Details через `GlobalExceptionHandler`, централизованная обработка exception-ов, единый `RequestContext` + AOP-аспект `RoleCheckAspect`. Основной хэппи-пас (геоотметка, ручная отметка старостой, отчёты, excuse-тикеты, late-checkin) покрыт тестами с MongoDB Testcontainers + RabbitMQ Testcontainers.

Однако обнаружено множество проблем безопасности, архитектурных долгов и дыр покрытия. **Главные P0**: (1) `UserContextFilter` слепо доверяет HTTP-заголовкам `X-User-*` без проверки, что запрос реально пришёл от Gateway — прямой доступ к сервису на `:9093` позволяет выдать себя за любого; (2) `@PostConstruct` в `GeofenceService`/`SemesterCacheService` вызывает gRPC в конструкторе Spring — если academic недоступен, сервис стартует с пустым кэшем и первый check-in-запрос лезет с N+1 gRPC на горячем пути; (3) RabbitMQ-консьюмер **без DLQ-обработки ошибок**: при падении `processLessonClosed` (из-за schedule-недоступности) сообщение уйдёт в DLQ, привет «залипшие» attendance-записи; (4) `CheckinService.checkin` сохраняет ВСЕ coordinate-payload-поля (lat/lng/accuracy) **в воздухе** — они принимаются валидатором, но в `AttendanceDocument` НЕТ поля `checkin_location`, которое описано в `docs/architecture/database-schema.md`. Локация студента теряется — ни шифрования, ни трассировки, ни защиты от spoofing.

Изоляция `report/` ↔ `checkin/` формально прошла ArchUnit-тест (`ReportDomainIsolationTest`). Однако `marking/`, `latecheckin/`, `event/`, `config/` импортируют `checkin.AttendanceDocument` и `AttendanceRepository` напрямую — это ослабляет замысел «домены общаются через порт». При этом `AttendanceReadPort` и `AttendanceWritePort` в `shared/port/` — удачное решение. Тестов ~25 классов, ~110+ тестовых методов, Mongo Testcontainers работает, но `Clock` не абстрагирован, поэтому невозможно детерминированно тестировать окно геоотметки (всегда Instant.now), а TZ `Europe/Moscow` жёстко зашита в `CheckinService`.

**Счётчики:** **P0=6, P1=11, P2=9, P3=7**.

---

## Структура модулей

### `attendance-api-contract` (`java-library`)
- Нет Spring Boot, нет Lombok ✅
- Есть `spring-web` + `spring-hateoas` + `spring-data-commons` в dependencies — используется для `@RequestMapping`, `EntityModel`, `RepresentationModel`, `Pageable` в контракте ✅
- Пакеты: `api/` (5 интерфейсов), `dto/{checkin,excuse,latecheckin,marking,report}/`, `enums/`, `exception/` (только `ErrorResponse` record + `ResourceNotFoundException`)
- **Замечание**: `ErrorResponse` — record (соответствует академик/schedule); остальные exception только в `*-app`.
- **Замечание P3**: дублирование enum-ов — в контракте есть `AttendanceStatus`, `AttendanceSource`, `ExcuseType`, `ExcuseTicketStatus`, `LateCheckinRequestStatus`, `UserRole`. В других сервисах те же enum-ы переопределены в своих contract-модулях. Это гарантирует независимость контрактов, но плодит дубли.

### `attendance-app` (Spring Boot)
- 50 классов в `main/java` + 25 тестовых
- Зависимости: Spring Boot Web/Hateoas/AMQP/Data-MongoDB/Data-Redis/Validation/AOP/Actuator
- gRPC через `net.devh:grpc-client/server-spring-boot-starter:3.1.0.RELEASE`
- protobuf-plugin компилирует `proto/*.proto` из корня репо

### Пакеты `attendance-app/ru/rutcampustrack/attendance/`
```
├── AttendanceApplication.java           — @SpringBootApplication
├── HealthCheckController.java           — /attendance/health-check (только для SecuritySmokeTest)
├── checkin/                             — геоотметка (домен CHKN)
│   ├── AttendanceDocument.java          — MongoDB document
│   ├── AttendanceRepository.java
│   ├── AttendanceReadPortImpl.java      — реализация порта для report/, marking/
│   ├── AttendanceWritePortImpl.java     — upsert для excuse/, latecheckin/
│   ├── CheckinController.java
│   └── CheckinService.java
├── report/                              — отчёты (домен RPRT, ИЗОЛИРОВАН)
│   ├── ReportController.java
│   └── ReportService.java
├── shared/port/                         — межмодульные интерфейсы
│   ├── AttendanceReadPort.java
│   ├── AttendanceWritePort.java
│   └── AttendanceRecord.java (record)
├── marking/                             — ручная отметка старостой (домен MARK)
│   ├── MarkingController.java
│   └── MarkingService.java              — импортирует checkin.AttendanceDocument напрямую
├── excuse/                              — excuse-тикеты (Phase 59)
│   ├── ExcuseController.java
│   ├── ExcuseService.java
│   ├── ExcuseRepository.java
│   ├── ExcuseEventPublisher.java
│   ├── ExcuseAssembler.java
│   └── entity/ExcuseTicket.java         — Mongo document
├── latecheckin/                         — поздняя отметка (Phase 59, «забыл отметиться»)
│   ├── LateCheckinController.java
│   ├── LateCheckinService.java          — импортирует checkin.AttendanceRepository
│   ├── LateCheckinRepository.java
│   ├── LateCheckinEventPublisher.java
│   ├── LateCheckinAssembler.java
│   └── entity/LateCheckinRequest.java
├── geofence/                            — радиус кампуса
│   ├── GeofenceService.java             — @PostConstruct gRPC в academic
│   └── GeoUtils.java                    — Haversine
├── grpc/
│   ├── AcademicGrpcClient.java
│   ├── ScheduleGrpcClient.java
│   └── GrpcSecretClientInterceptor.java — IMP-09 shared secret в outgoing metadata
├── semester/
│   └── SemesterCacheService.java        — volatile кэш + @PostConstruct refresh
├── ratelimit/
│   └── CheckinRateLimiter.java          — Redis SETNX + INCR
├── security/
│   ├── UserContextFilter.java           — читает X-User-* → RequestContext
│   ├── RequestContext.java              — @Scope(request, proxyMode=TARGET_CLASS)
│   ├── RequireRole.java                 — annotation
│   └── RoleCheckAspect.java             — @Around-aspect
├── event/
│   ├── EventConsumer.java               — @RabbitListener, генерик-свитч по event_type
│   ├── LessonEventService.java          — processLessonClosed/Cancelled/One_Off/Deleted
│   └── AttendanceEventPublisher.java    — publish attendance.marked
├── config/
│   ├── MongoConfig.java                 — @PostConstruct индексы
│   ├── MongoConvertersConfig.java       — Enum → lowercase string
│   ├── RabbitConfig.java                — fanout exchange, queue, DLQ
│   ├── WebConfig.java                   — Jackson case-insensitive enums, path-variable converter
│   └── AttendanceIndexInitializer.java  — ApplicationRunner: dedup + orphan-cleanup
└── exception/                            — 9 runtime exception + GlobalExceptionHandler
```

Сервис логически стройный, но имеет слишком много cross-package импортов `checkin.AttendanceDocument` (8 файлов), что подтачивает идею «домены изолированы». См. раздел **Изоляция доменов** ниже.

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через Internal JWT — `UserContextFilter` слепо доверяет `X-User-*` заголовкам — обход авторизации
**Статус (2026-04-18):** будет закрыто фиксом из C0-1 (Internal JWT). См. `OWNER-ANSWERS.md` 02-Q2 + 04-Q1.



- **Где:** `security/UserContextFilter.java:34-43`
- **Что:** фильтр читает `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` без:
  1. проверки, что запрос пришёл из gateway (MTLS / shared secret / allowlisted IP);
  2. валидации подписи JWT (её нет в этом сервисе — вся логика JWT в gateway);
  3. проверки, что `X-User-Id` валиден как Long (можно передать `1;DROP`, но `Long.parseLong` бросит NumberFormatException → 500);
  4. проверки обязательности `X-User-Role` (если Id есть, а Role — нет → `request.getHeader(...).toUpperCase()` → NPE → 500, хотя должен быть 401/403).
- **Риск:** контейнер `rct-attendance` слушает порт `9093` во внутренней docker-сети. Любой другой сервис в этой же сети (скомпрометированный notification-bot, промежуточный sidecar, прокси при неправильной настройке) может отправить запрос с `X-User-Id: 1, X-User-Role: ADMIN, X-Is-Headman: true` и получить **полный контроль** над любым студентом/отчётом. Также если кто-то по ошибке выставит `:9093` наружу через nginx (или оставит debug-порт-маппинг), это катастрофа.
- **Подтверждение**: эта же проблема обнаружена в academic-service и schedule-service (кросс-сервисная).
- **Как чинить:**
  1. добавить фильтр-предкаскад, проверяющий header `X-Internal-Secret` (shared secret между gateway и сервисами), или mTLS, или по крайней мере IP allowlist gateway (в production).
  2. либо оставить gateway как единственный маршрут, но хотя бы сделать явный чек в фильтре: `userIdHeader != null → всё остальное обязательно, иначе 401`.
  3. обернуть `Long.parseLong` / `UserRole.valueOf` в try/catch → возвращать 401.
- **Зависимости:** кросс-сервисная проблема, фиксить одновременно в auth-service/academic/schedule/attendance.

### P0-2: ✅ ACCEPTED (переклассифицировано в DOC-FIX) — `AttendanceDocument` **не хранит** координаты геоотметки
**Статус:** by design (см. `OWNER-ANSWERS.md` 04-Q2 + Meta M1, 2026-04-18). Координаты студента НЕ сохраняются осознанно — только проверка «в радиусе кампуса». Anti-spoof расследование через лог координат не предусмотрено. Исходная P0 переклассифицируется как **DOC-FIX**: правка `docs/architecture/database-schema.md` (убрать описание `checkin_location` или явно пометить «не используется»). Ниже — оригинальное описание.



- **Где:** `checkin/AttendanceDocument.java:30-73`, `checkin/CheckinService.java:79-149`, `docs/architecture/database-schema.md:296-302`
- **Что:** схема в проектной документации описывает вложенный объект:
  ```
  checkin_location: { lat, lng, accuracy_m, distance_from_campus_m }
  ```
  Но в `AttendanceDocument` полей `@Field("checkin_location")` НЕТ — ни флэт, ни вложенный. Сервис принимает `CheckinRequest(lat, lng)`, валидирует диапазон, проверяет `geofenceService.isWithinCampus(lat, lng)` и… **выбрасывает координаты**. Ни один save не содержит lat/lng.
- **Риск:** (1) потеряна расследовательская возможность — если студент оспаривает отметку, нельзя доказать где он был; (2) теряется функциональность защиты от spoofing — невозможно постфактум увидеть аномалию (distance=5м, но все одновременно из разных точек); (3) нарушена описанная схема БД → любой код, который делает `db.attendances.aggregate({$match: {"checkin_location.distance_from_campus_m": {$gt: 200}}})` вернёт 0 (как отчёт о нарушениях геофенса).
- **Как чинить:** добавить в `AttendanceDocument` nested type `CheckinLocation(lat, lng, accuracyM, distanceFromCampusM)` с `@Field("checkin_location")`. В `CheckinService.checkin` при создании документа посчитать расстояние через `GeoUtils.distanceMeters` и сохранить. Убрать поле с `CheckinRequest(accuracy)` — клиент его не шлёт, и ок, можно хранить null.
- **Зависимости:** нужно согласовать с PWA/mini-app — хочет ли UI отдавать `accuracy` в payload. Сейчас `CheckinRequest` не принимает accuracy.

### P0-3: `@PostConstruct` в `GeofenceService` и `SemesterCacheService` — gRPC на старте + silent fallback

- **Где:** `geofence/GeofenceService.java:34-42`, `semester/SemesterCacheService.java:27-35`
- **Что:** оба сервиса в `@PostConstruct` вызывают `academicGrpcClient.getCampusGeofence()` и `academicGrpcClient.getActiveSemester()`. Если academic-service недоступен на старте (порядок старта в docker-compose, transient network), catch глотает ошибку и оставляет кэш null:
  ```java
  } catch (Exception e) {
      log.warn("Could not load campus geofence at startup: {}", e.getMessage());
  }
  ```
  Далее при первом check-in запросе `GeofenceService.isWithinCampus` вызывает `getOrRefresh` → `refresh` → gRPC. Если academic всё ещё лежит → запрос пользователя падает с 503. Но хуже: **все запросы** в `CheckinService.checkin` на горячем пути получат 503, пока academic не поднимется.
- **Риск:** (1) каскадный отказ: academic моргает → все check-in падают → backoff на клиенте → бот DDoS-ит academic восстановлением; (2) нет circuit breaker / fallback-значения для геофенса (можно было бы держать копию в Redis с TTL 1 час); (3) в логе просто WARN, алертов нет; (4) `SemesterCacheService.getActiveSemesterId()` возвращает null, если cache пуст после неудачного refresh — `MarkingService` и `CheckinService` пишут в Mongo `semesterId=null`, ломая отчёты по семестру (они фильтруют по semesterId).
- **Как чинить:**
  1. применить circuit breaker (Resilience4j) + retry;
  2. кешировать geofence/semester в Redis с TTL 1 час (как описано в architecture.md — academic уже это кэширует, но attendance дублирует кэш в памяти);
  3. при `semesterId==null` в `CheckinService`/`MarkingService` бросать `ServiceUnavailableException`, а не сохранять null;
  4. добавить Actuator health indicator для `GeofenceService`/`SemesterCacheService` — `/actuator/health` должен быть DOWN, если кэш не загружен.
- **Зависимости:** academic-service должен быть стартовать раньше; либо реализовать lazy init с жёсткими 503.

### P0-4: `UserContextFilter` не проверяет `X-Group-Id` на совпадение с реальной группой пользователя

- **Где:** `security/UserContextFilter.java:38-41`, `security/RequestContext.java`
- **Что:** `X-Group-Id` просто парсится из заголовка и кладётся в `RequestContext`. Никакой gRPC-проверки в academic, что этот `user_id` действительно в этой `group_id`, нет. `CheckinService.checkin` использует `requestContext.getGroupId()` чтобы найти активную пару (`scheduleGrpcClient.getActiveLesson(groupId, ...)`) — а значит атакующий, получив JWT к другой группе через валидный gateway (через `X-Group-Id`-override в manipulated JWT или если gateway когда-то получит баг), попадёт в чужую группу.
- **Но в реальности** Gateway формирует `X-Group-Id` из JWT-claim `group_id`, который подписан auth-service — атаки через ручное подкидывание headers отсекаются P0-1. Если gateway — единственный доступ, то это не уязвимость. Однако **defense-in-depth** рекомендует всё-таки перепроверить.
- **Риск:** средний при валидном gateway, высокий при P0-1.
- **Как чинить:** в критичных местах (check-in, marking, reports) вызывать `academicGrpcClient.isHeadman(userId, groupId)` или аналогичный `userBelongsToGroup` и сверять.
- **Зависимости:** требует добавления gRPC-метода `userBelongsToGroup` в academic, либо использовать `getGroupMembers` (но это медленнее).

### P0-5: 🔧 TO-FIX через retry + outbox — `@RabbitListener` в `EventConsumer` — падение gRPC при обработке `lesson.closed` отправит всё в DLQ, без retry-механизма
**Статус (2026-04-18):** будет закрыто связкой **(1) C0-3 In-app outbox** (продьюсер не теряет события) **+ retry на консьюмере** через `RetryInterceptorBuilder` (3 попытки, экспоненциальный backoff). См. `OWNER-ANSWERS.md` 02-Q3 + 04-Q6. DLQ-handler (что делать с улетевшим в DLQ) — отдельная задача 05-Q8.



- **Где:** `event/EventConsumer.java:33`, `event/LessonEventService.java:46-89`, `config/RabbitConfig.java:38-67`
- **Что:** `handleLessonClosed` вызывает `scheduleGrpcClient.getLessonById(lessonId)` и `academicGrpcClient.getGroupMembers(groupId)`. Оба метода бросают `ScheduleServiceUnavailableException`/`AcademicServiceUnavailableException` при transient-недоступности. Spring AMQP при throw по дефолту делает **NACK без requeue** (AckMode.AUTO) → сообщение уходит в `attendance-service.events.dlq`. В DLQ нет автоматического повторителя — сообщение лежит вечно, пока кто-то не глянет.
- **Последствие бизнеса:** при кратком moргании academic-service (5 сек деплой) все `lesson.closed` события потеряют auto-ABSENT шаг → журнал неполный → студентам выставлено "не отмечен" вручную вместо "auto-absent" → staroste не видит, что нужно проставить отметки.
- **Дополнительно:** нет `x-max-retries` / `x-retry-delay`. Даже если бы был retry, нет idempotency key в самом событии (только `event_id`, никто его не проверяет — возможны повторные auto-ABSENT при дупликате).
- **Как чинить:**
  1. добавить retry-interceptor в listener container (`SimpleRabbitListenerContainerFactory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(3).backOff(exponentialBackOff).build())`);
  2. DLQ consumer (operator tool для прогона DLQ после восстановления);
  3. idempotency: при `processLessonClosed` проверить `event_id` (хранить в attendance-events-processed collection с TTL 7 дней);
  4. альтернативно — при gRPC-недоступности сохранять «pending» ABSENT-запись и retry через @Scheduled.
- **Зависимости:** кросс-сервисная проблема паттерна, но здесь она проявляется критично из-за auto-ABSENT.

### P0-6: `AttendanceIndexInitializer.cleanupOrphans` стартует при каждом боте сервиса и делает mass-delete

- **Где:** `config/AttendanceIndexInitializer.java:116-175`
- **Что:** `ApplicationRunner` при каждом старте:
  1. делает `findDistinct("lesson_id")` по всей коллекции;
  2. батч-запрашивает Schedule-service `getLessonsByIds` для ВСЕХ distinct ids (может быть десятки тысяч);
  3. удаляет attendance-документы, у которых lesson_id не "жив".
- **Есть safety guards** (не удалять если >50% orphans; не удалять если schedule вернул 0). Это хорошо, но:
  - (1) **cold-start**: если attendance-service стартовал первым и schedule ещё не готов → guard сработает → ок.
  - (2) если schedule частично ответил (например, batch-ограничение на 1000 id в одном gRPC-запросе), часть документов может оказаться "orphan" ложно → будет удалено `MAX_DELETE_RATIO = 0.5`.
  - (3) **N+1/огромный batch**: distinct lesson_ids при 10k студентов × 100 пар = 100k пар. В одном gRPC-запросе `LessonsByIdsRequest`: вероятно превысит default max-message-size (4 МБ) → gRPC упадёт → silent skip (catch RuntimeException).
  - (4) **конкурентные старты**: если поднимается N инстансов (horizontal scaling), каждый делает свой cleanup, можно получить гонку удаления + duplicate-key при восстановлении.
- **Риск:** потенциальная потеря attendance данных (даже 50% — это катастрофа).
- **Как чинить:**
  1. вынести cleanup в operator-скрипт / admin endpoint, а не в startup;
  2. если оставить в startup — сделать batch по 1000 lesson_ids, сделать ShedLock (только один инстанс делает cleanup);
  3. dry-run флаг через env, дефолт — только логировать.
- **Зависимости:** нет; можно чинить независимо.

---

## Серьёзные проблемы (P1)

### P1-1: `MarkingService` напрямую пишет в MongoDB через `MongoTemplate.upsert`, обходя `AttendanceWritePort`

- **Где:** `marking/MarkingService.java:96-114`
- **Что:** `marking/` домен использует `MongoTemplate` и импортирует `checkin.AttendanceDocument`. При этом в `shared/port/AttendanceWritePort` уже есть `mark(studentId, lessonId, groupId, status, source)`, который делает upsert. Если бы `MarkingService` использовал порт, `checkin.AttendanceDocument` был бы inaccessible → изоляция сильнее.
- **Но** `MarkingService` добавляет `marked_by`, `subject_id`, `semester_id`, `lesson_number`, `lesson_date` — это расширение того, что `AttendanceWritePort.mark` умеет. Так что либо расширяем порт, либо в domain нужно явное нарушение.
- **Риск:** нарушение изоляции; потенциальное расхождение логики между `MarkingService` и `AttendanceWritePortImpl` (у них разный набор `setOnInsert` полей).
- **Как чинить:** расширить `AttendanceWritePort` до `markWithLessonContext(...)` или передавать `LessonContext` struct; убрать прямой импорт `AttendanceDocument` из `marking/`.

### P1-2: `LateCheckinService` импортирует `checkin.AttendanceRepository` — нарушение изоляции

- **Где:** `latecheckin/LateCheckinService.java:8,49,63-64,87-91`
- **Что:** `LateCheckinService` делает `attendanceRepository.findByLessonIdAndUserId(lessonId, userId)` чтобы проверить "вы уже отмечены". Это прямой импорт `checkin.AttendanceRepository` — именно того, что CLAUDE.md запрещает делать для `report/`.
- **Порт решение:** уже существует `AttendanceReadPort.findByLessonIdAndUserId` (см. `AttendanceReadPort:29`) — можно заменить.
- **Риск:** те же риски изоляции; `AttendanceRepository` возвращает `AttendanceDocument` (с Lombok, с mutable setter-ами), а порт — иммутабельный `AttendanceRecord`. Если кто-то случайно вызовет `doc.setStatus()` в late-checkin, это прямой write в БД.
- **Как чинить:** использовать `attendanceReadPort.findByLessonIdAndUserId(...).map(AttendanceRecord::status)` вместо `attendanceRepository.findByLessonIdAndUserId`.

### P1-3: Transaction boundary отсутствует в `ExcuseService.updateStatus` — cascade-to-attendance + event publish не атомарны

- **Где:** `excuse/ExcuseService.java:256-313`
- **Что:** метод делает:
  1. `excuseRepository.save(ticket)` — сохраняет решение в MongoDB
  2. в цикле для каждого lessonId: `attendanceWritePort.mark(...)` — upsert в attendances
  3. `excuseEventPublisher.publishDecided(saved)` — RabbitMQ publish
- Если шаг 2 упадёт между lessonId #1 и #2, тикет уже APPROVED, но только для первой пары есть запись attendance. Повторный вызов `updateStatus` упадёт с `ConflictException` (D-18 "решение уже принято"). Студент получит письмо "excused", а половина пар всё ещё ABSENT.
- MongoDB поддерживает transactions в replica set-режиме (7.0), но `@Transactional` нигде не стоит. RabbitMQ — отдельный транзакционный менеджер, который всё равно нельзя слить.
- **Риск:** частичное применение cascade, неконсистентный журнал.
- **Как чинить:**
  1. оборачивать шаги 1+2 в `@Transactional` с `MongoTransactionManager` (требует replica set в проде, см. P1-4).
  2. publish делать после commit через outbox pattern (хранить событие в Mongo, отдельный publisher читает и пушит).
  3. альтернативно: если cascade падает, откатить тикет через `ticket.setStatus(SUBMITTED)` + save → но это новая гонка.

### P1-4: MongoDB transactions требуют replica set — production deployment использует standalone?

- **Где:** `application.yml:14`, проектная `docker-compose.yml`
- **Что:** MongoDB URL `mongodb://.../attendance_db?authSource=admin` без `replicaSet=rs0`. Spring Data `@Transactional` не будет работать на standalone MongoDB. Если P1-3 решить через @Transactional — не заработает в dev/prod.
- **Как чинить:** проверить docker-compose.prod.yml (VPS memory), убедиться что MongoDB в replica set, иначе — закладывать outbox.

### P1-5: `@PostConstruct` в `MongoConfig.initIndexes` + `AttendanceIndexInitializer.ensureIndex` — дублирование

- **Где:** `config/MongoConfig.java:25-54`, `config/AttendanceIndexInitializer.java:177-186`, `checkin/AttendanceDocument.java:23-29`
- **Что:** unique index `(lesson_id, user_id)` объявлен тремя способами:
  1. `@CompoundIndexes` на `AttendanceDocument` (Spring Data auto-index creation)
  2. `MongoConfig.initIndexes` в `@PostConstruct`
  3. `AttendanceIndexInitializer.ensureIndex` в `ApplicationRunner`
- Spring Boot по дефолту **не создаёт** индексы из `@CompoundIndexes` (нужно `spring.data.mongodb.auto-index-creation: true`). Но если бы флаг был включён, мы бы получили 3 попытки создать один и тот же индекс.
- **Риск:** путаница при изменении индекса; копипаста логики.
- **Как чинить:** оставить один источник правды — либо `@CompoundIndexes` + `spring.data.mongodb.auto-index-creation: true`, либо `IndexOps` в `@PostConstruct`. Удалить `AttendanceIndexInitializer.ensureIndex`.

### P1-6: Нет `@Transactional` в `LessonEventService.processLessonClosed` (комментарий обосновывает это некорректно)

- **Где:** `event/LessonEventService.java:33-34,46-89`
- **Что:** документация метода гласит:
  > `No @Transactional — MongoDB and RabbitMQ do not share a transaction manager.`
- Это верно в части RabbitMQ, но **`bulkOps.execute()`** в середине метода пишет в Mongo. Если gRPC-вызов `getLessonById` или `getGroupMembers` падает **между** `scheduleGrpcClient.getLessonById(lessonId)` и `academicGrpcClient.getGroupMembers(groupId)` — часть работы не сделана, и `@RabbitListener` nack-ает. Следующий retry сделает всё заново, но так как bulk ещё не исполнился, проблем нет. Однако если падение **посередине bulk** (partial-insert в Mongo, что невозможно для bulkOps.execute() — он атомарен на уровне одной операции, но могут быть отдельные документы) → конкретные upsert-ы не применятся частично.
- В целом `BulkOperations.UNORDERED` + idempotent `$setOnInsert` — безопасен. Проблема именно в том, что комментарий неверно объясняет отсутствие транзакции: «нет общего транзакционного менеджера» — верно, но если важен atomic commit в Mongo + RabbitMQ publish — нужен outbox.
- **Риск:** при padении между Mongo и пубишем события `attendance.session.closed` (которого сейчас нет, хотя architecture.md обещает его) — inconsistency.
- **Как чинить:** (1) поправить комментарий; (2) добавить outbox для публикации; (3) если оставлять как есть — документировать, что at-least-once-delivery на Rabbit компенсирует отсутствие atomicity.

### P1-7: `EventConsumer` — extractLong бросает NPE при `value` instanceof String вместо Number

- **Где:** `event/EventConsumer.java:170-174`
- **Что:** `return ((Number) value).longValue();` — если producer сериализует numeric как строку (старый messenger, bug в payload), получаем `ClassCastException` → сообщение в DLQ. Никаких defensive guards. Также `extractLong` возвращает null для null, но далее `lessonEventService.processLessonClosed(lessonId, groupId)` — метод не проверяет null и падает на `Criteria.where("lesson_id").is(null)` (возможно, но пусто).
- **Как чинить:** в `extractLong` поддержать `String → Long.parseLong`, фолбэк `null`, и в handler-ах делать `if (lessonId == null) { log.warn; return; }`.

### P1-8: `ExcuseEventPublisher.publishRequestedWithFile` кладёт всё тело файла base64 в RabbitMQ payload

- **Где:** `excuse/ExcuseEventPublisher.java:80-93`
- **Что:** клиент шлёт файл до 10 МБ, сервис читает `file.getBytes()` в heap, base64-encode (≈13.3 МБ) и публикует в RabbitMQ как часть JSON envelope. RabbitMQ по дефолту лимит на сообщение — нет жёсткого, но >10 МБ = плохая практика.
- **Риск:** (1) heap overflow при множественных параллельных запросах; (2) RabbitMQ memory pressure; (3) notification-bot должен декодировать и форвардить в Telegram — 13 МБ через Aiogram может быть медленно.
- **Как чинить:**
  1. сохранить файл в S3/MinIO с presigned URL, в событии передавать только URL (архитектурное решение нужно);
  2. или передавать через HTTP multipart напрямую в notification-bot, минуя RabbitMQ;
  3. или понизить лимит файла до 2 МБ.
- **Дополнительно:** `file.getBytes()` без проверки что InputStream не больше лимита — можно защититься `MultipartResolver.maxUploadSize`, но это не предотвращает чтение всей памяти.

### P1-9: `ReportService.getJournal` — квадратичная сложность по students × records

- **Где:** `report/ReportService.java:140-157`
- **Что:**
  ```java
  List<JournalStudentRow> studentRows = members.getStudentsList().stream()
      .map(student -> {
          ...
          List<JournalCell> cells = records.stream()
              .filter(r -> r.userId().equals(uid))
              ...
      })
  ```
  При 30 студентах × 1000 attendance-записях — 30k сравнений. Приемлемо. Но при 100 студентах × 5k записей за семестр — 500k сравнений + создание 5k списков на каждой итерации. Неэффективно.
- **Как чинить:** сгруппировать заранее: `Map<Long, List<AttendanceRecord>> recordsByUser = records.stream().collect(Collectors.groupingBy(AttendanceRecord::userId));` и делать `recordsByUser.getOrDefault(uid, List.of())`.

### P1-10: `ReportService.filterExistingLessons` выполняет gRPC-lookup при каждом вызове отчёта — N+1-паттерн

- **Где:** `report/ReportService.java:417-426`
- **Что:** все endpoints отчётов делают `filterExistingLessons(records)` → `scheduleGrpcClient.getLessonsByIds(ids)`. При `getStudentStats` на 5k записей — один gRPC-запрос с 5k distinct lesson_ids. Payload ограничен (по умолчанию 4 МБ) — при 5k long-ов это ~40 КБ, ок. Но latency ~30-100 мс даже при хорошей сети — на **каждый** отчёт.
- **Кэширование нулевое.** Если студент обновляет dashboard раз в минуту, gRPC кидается каждый раз.
- **Риск:** нагрузка на schedule; latency отчётов.
- **Как чинить:**
  1. в schedule-service добавить простой in-memory cache "existing lesson_ids" (TTL 5 мин);
  2. или сделать отдельную стратегию: подписка на `lesson.deleted` event → поддерживать локальный Set<Long> "deleted_lessons" с 30-дневным TTL, фильтровать по нему без gRPC.

### P1-11: `MarkingService.markAttendance` — race на `findOne` после `upsert`

- **Где:** `marking/MarkingService.java:114-117`
- **Что:**
  ```java
  mongoTemplate.upsert(filter, update, AttendanceDocument.class);
  AttendanceDocument doc = mongoTemplate.findOne(filter, AttendanceDocument.class);
  ```
  Между upsert и findOne другой thread может изменить документ (например, параллельный late-checkin approve). `doc` будет отражать чужую запись, не ту что сделал headman. Event `attendance.marked` будет содержать чужой статус.
- **Риск:** edge case, но реальный при активном конкуренте.
- **Как чинить:** использовать `findAndModify` с `ReturnDocument.AFTER` вместо `upsert + findOne`.

---

## Средние (P2)

### P2-1: `MarkingService` использует `==` для сравнения Long

- **Где:** `marking/MarkingService.java:90`
- **Что:**
  ```java
  boolean studentInGroup = members.getStudentsList().stream()
          .anyMatch(s -> s.getUserId() == userId);
  ```
  `s.getUserId()` — primitive long (protobuf), `userId` — `Long` (параметр). Java auto-unboxing работает, но если `userId == null`, получаем NPE. Валидация `@PathVariable Long userId` не помечена `@NotNull`, хотя при валидном URL `/attendance/lessons/{lessonId}/students/{userId}` Spring всегда парсит Long.
- **Риск:** теоретический NPE; нечитаемость — лучше `.equals(userId)` или `s.getUserId() == userId.longValue()`.
- **Как чинить:** `Objects.equals(s.getUserId(), userId)` либо `s.getUserId() == userId.longValue()`.

### P2-2: `ReportService.authorizeHeadmanOrTeacher` — gRPC-вызов внутри запроса, нет кэша

- **Где:** `report/ReportService.java:428-453`
- **Что:** при каждом `/attendance/reports/journal` + `/lesson/{id}` делается `academicGrpcClient.getTeacherSubjects(teacher_id, semester_id)`. Это тяжёлый lookup, который нужен только для авторизации. Academic-service его кэширует в Redis (10 мин), но gRPC-round-trip сам по себе ощутим.
- **Как чинить:** локальный micro-cache (Caffeine) в attendance на 60 сек.

### P2-3: `ExcuseService.resolveLessonDetails` проглатывает все `RuntimeException`

- **Где:** `excuse/ExcuseService.java:455-459`
- **Что:**
  ```java
  } catch (RuntimeException e) {
      // Обогащение не критично...
      return List.of();
  }
  ```
  Catch `RuntimeException` без логирования (есть комментарий, но нет `log.debug`). Это скрывает баги в proto-десериализации или academic-service-outage.
- **Как чинить:** `log.debug("Enrichment failed, falling back to bare payload", e);`.

### P2-4: `CheckinService` — зашитая TZ `Europe/Moscow`, нет `Clock`, нет `@ConfigurationProperties`

- **Где:** `checkin/CheckinService.java:41-42,151-157`
- **Что:** `ZoneId.of("Europe/Moscow")` и `Duration.ofMinutes(5)` — константы без конфига. Невозможно:
  1. тестировать с фиксированным временем (`Clock` не инжектится);
  2. переехать в другой регион;
  3. изменить buffer окна (например, сделать 10 мин);
- **Как чинить:** инжектировать `Clock` (в тестах `Clock.fixed(...)`); вынести в `@ConfigurationProperties attendance.checkin.buffer-minutes`/`attendance.checkin.zone`.

### P2-5: `GlobalExceptionHandler` — обработчик `Exception.class` возвращает 500 с `ex.getMessage()` в теле

- **Где:** `exception/GlobalExceptionHandler.java:256-269`
- **Что:** catch-all возвращает `ex.getMessage()` в `detail` поле. Это может раскрыть внутренние детали (SQL error, stacktrace через exception chain). В production нужен generic "Internal Server Error" + correlation ID + logger call.
- **Как чинить:** `log.error("Unexpected error", ex); body.detail = "Обратитесь в техподдержку, correlation=" + UUID`.

### P2-6: `AttendanceIndexInitializer` — отдельный orphan-cleanup, дублирует `ReportService.filterExistingLessons`

- **Где:** `config/AttendanceIndexInitializer.java:116-175`, `report/ReportService.java:417-426`
- **Что:** логика идентична — спросить schedule-service о живых lesson_ids и отфильтровать. В одном месте это делается постфактум при отчёте (slow), в другом — при старте (mass-delete). Нужно одно.
- **Как чинить:** удалить startup-cleanup, использовать `lesson.deleted` event для каскадного удаления (оно УЖЕ обрабатывается в `EventConsumer.handleLessonDeleted`).

### P2-7: `ExcuseService.createExcuseWithFile` — magic number `10MB` в двух местах

- **Где:** `excuse/ExcuseService.java:117`, `application.yml:10-11` (`max-file-size: 10MB`)
- **Что:** константа `MAX_BYTES = 10L * 1024 * 1024` и Spring property `spring.servlet.multipart.max-file-size: 10MB` должны совпадать, но не связаны. Если кто-то увеличит в yml до 20 МБ — сервис будет принимать файл, но валидация в сервисе всё равно отклонит.
- **Как чинить:** использовать `@Value("${spring.servlet.multipart.max-file-size}")` или свойство `attendance.excuse.max-file-size`.

### P2-8: Нет валидации `groupId` на совпадение `requestContext.getGroupId()` в `CheckinService.checkin`

- **Где:** `checkin/CheckinService.java:88`
- **Что:** при `scheduleGrpcClient.getActiveLesson(requestContext.getGroupId(), ...)` — группа берётся из хидера. Если студент изменил `X-Group-Id` (и gateway это пропустил — см. P0-1), он получит чужую пару.
- Частично отсекается P0-1, но defense-in-depth: после `getActiveLesson` проверить `lesson.getGroupId().equals(requestContext.getGroupId())` — сейчас проверки нет.
- **Как чинить:** добавить явную проверку `if (!lesson.getGroupId().equals(requestContext.getGroupId())) throw new AccessDeniedException(...)`.

### P2-9: `LateCheckinRequest.entity` — нет индекса на `(group_id, status, created_at)` для быстрой выборки PENDING

- **Где:** `latecheckin/entity/LateCheckinRequest.java`, `MongoConfig.initIndexes`
- **Что:** `findByGroupIdAndStatusOrderByCreatedAtAsc(groupId, PENDING)` — полный скан `late_checkin_requests` без индекса. При сотнях запросов в день на группу — O(n).
- **Как чинить:** добавить compound index в `MongoConfig.initIndexes` или `@CompoundIndex` на entity.

---

## Мелкие и nit (P3)

### P3-1: `HealthCheckController` — test-only endpoint в production-коде

- **Где:** `HealthCheckController.java`
- **Что:** endpoint `/attendance/health-check` существует только для `SecuritySmokeTest`. В production он открыт и доступен любому с STUDENT-role. Лучше перенести в тестовый source set через `@TestConfiguration`, либо защитить профилем `@Profile("test")`.

### P3-2: `ExcuseService.EXCUSE_TYPE_LABELS` — hardcoded i18n в сервисе

- **Где:** `excuse/ExcuseService.java:60-67`
- **Что:** русские строки ("Болезнь", "Повестка") зашиты в Java-код. Если в будущем добавим английскую локализацию — придётся рефакторить. Лучше `MessageSource` или держать в frontend.

### P3-3: `CheckinResponse` — extends `RepresentationModel`, но `id` поле `Long lessonId` (а не `id`) — minor naming

- **Где:** `attendance-api-contract/dto/checkin/CheckinResponse.java`
- **Что:** нет критичной проблемы, просто замечание.

### P3-4: `GeofenceService.GeofenceData` — nested record package-private

- **Где:** `geofence/GeofenceService.java:81`
- **Что:** OK, но можно сделать `private` вместо package-private — никто не импортирует.

### P3-5: `ExcuseAssembler.toPagedModel` не добавляет PagedLinks для навигации по страницам

- **Где:** `excuse/ExcuseAssembler.java:48-59`
- **Что:** создаёт PagedModel с metadata, но БЕЗ `Link` prev/next/first/last. Клиент не может пролистать без явного знания `?page=2`. HATEOAS Level 3 предполагает наличие навигации.
- **Как чинить:** использовать `PagedResourcesAssembler` или вручную добавить ссылки.

### P3-6: `CheckinService` использует `LocalDate.parse(lesson.getDate())` — бросает `DateTimeParseException` без wrapping

- **Где:** `checkin/CheckinService.java:134,152`, `latecheckin/LateCheckinService.java:115`
- **Что:** если schedule-service вернёт кривую дату ("" или "tomorrow"), получаем неперехваченный DateTimeParseException → 500. Должен быть BadRequestException / ScheduleServiceUnavailable.

### P3-7: `GlobalExceptionHandler` — дублирование ErrorResponse-конструкции в каждом handler-е

- **Где:** `exception/GlobalExceptionHandler.java`
- **Что:** 12+ почти одинаковых блоков `new ErrorResponse(...)`. Можно вынести factory-method `ErrorResponse.of(status, type, title, detail, request)`.

---

## Изоляция доменов `checkin/ ↔ report/`

**Формальный результат:** прошла. ArchUnit-тест `ReportDomainIsolationTest` (строка 22-23):
```java
noClasses().that().resideInAPackage("ru.rutcampustrack.attendance.report..")
    .should().dependOnClassesThat().resideInAPackage("ru.rutcampustrack.attendance.checkin..");
```

Подтверждено grep-ами:
- `grep "import ru.rutcampustrack.attendance.checkin" …/report/` — **пусто** ✅
- `grep "import ru.rutcampustrack.attendance.report" …/checkin/` — **пусто** ✅

`ReportService` использует только `shared/port/AttendanceReadPort` + `shared/port/AttendanceRecord`. Это соответствует CLAUDE.md.

**Но архитектурное замечание:** изоляция `checkin/` vs `report/` — только одна ось. Остальные домены **не изолированы от `checkin/`**:

| Домен | Импортирует из `checkin/` |
|-------|---------------------------|
| `marking/` | `checkin.AttendanceDocument` (`MarkingController.java`, `MarkingService.java`) |
| `latecheckin/` | `checkin.AttendanceRepository` (`LateCheckinService.java`) |
| `event/` | `checkin.AttendanceDocument` (`AttendanceEventPublisher.java`, `LessonEventService.java`) |
| `config/` | `checkin.AttendanceDocument` (`AttendanceIndexInitializer.java`) |

Это частично объяснимо: `marking/latecheckin/event` пишут именно в ту же коллекцию, что `checkin/` — их логичная общая артефакт-точка это `AttendanceDocument`. Но `AttendanceWritePort` уже существует именно для этого use case; его просто не используют везде, где могли бы.

**Рекомендация:** расширить `AttendanceWritePort` до:
```java
void mark(MarkAttendanceCommand command);   // лесный struct со всеми полями

record MarkAttendanceCommand(
    Long lessonId, Long userId, Long groupId, Long subjectId,
    Long semesterId, Integer lessonNumber, LocalDate lessonDate,
    AttendanceStatus status, AttendanceSource source, Long markedBy, String excuseReason) {}
```
И перевести `MarkingService`, `LateCheckinService`, `ExcuseService` на порт. Тогда они вообще не должны знать о `AttendanceDocument`. Оставляем импорты `checkin.*` только в `event/LessonEventService` (там нужна массовая bulk-операция, что через порт сделать неудобно).

---

## Мёртвый код

- `HealthCheckController` — только для теста, production не нужен (см. P3-1).
- `AcademicGrpcClient.isHeadman()` — никто не вызывает из кода (вся логика isHeadman берётся из `RequestContext`). Можно удалить.
- `AcademicGrpcClient.getActiveSemester()` — используется только `SemesterCacheService.refresh()` и `ReportService.resolveSemesterStart()`. Небольшая duplication не мертва, но можно унифицировать через один фасад.
- В enum `AttendanceSource` есть `AUTO_SCHEDULER`, `HEADMAN_EXCUSE`, `LATE_CHECKIN`, `STUDENT_GEO`, `HEADMAN`. Документация `database-schema.md` (строка 304) упоминает также `teacher_override` — ЕГО НЕТ в коде. Либо схема устарела, либо функционал не реализован.

## Костыли и TODO/FIXME

- **Grep `TODO|FIXME|HACK|XXX`** по `main/` — **пусто** (кода без явных заметок). Это хорошо на бумаге, но означает что рефакторинг-долг не трекается явно.
- **Неявные костыли**:
  - `SemesterCacheService.getActiveSemesterId` — lazy-refresh на каждый null — fallback на случай неудачного старта, но без backoff.
  - `ExcuseEventPublisher.publishRequested` — дублирует `student_id` под ключом `user_id` "потому что бот читает `user_id`" (комментарий D-27). Это contract-drift между producer и consumer — надо либо синхронизировать имена, либо документировать как public API в event-schemas/.
  - `LateCheckinService.applyDecisionFromWeb` → `applyDecision(requestId, requestContext.getUserId(), approved)` — после сохранения делает второй find:
    ```java
    applyDecision(requestId, requestContext.getUserId(), approved);
    return repository.findById(requestId).orElse(request);
    ```
    Лишний findById после void apply. Лучше вернуть saved из applyDecision.

---

## Тесты

**Всего ~25 файлов тестов, ~110+ тестовых методов.**

### Что покрыто хорошо
- **Checkin happy path + negative paths**: `CheckinServiceTest` (7 unit-тестов), `CheckinIntegrationTest` (8 интеграционных с Mongo+Rabbit+Redis Testcontainers). Проверяются: happy path, rate limit, geo-block, outside-geofence, dedup, outside-time-window, headman-exempt, event published.
- **Isolation**: `ReportDomainIsolationTest` (ArchUnit).
- **Mongo unique index**: `MongoIndexTest` — проверяет, что дубликат `(lesson_id, user_id)` отклоняется.
- **Enum-lowercase persistence**: `EnumSerializationTest` — raw MongoDB-документ содержит `"free_attendance"` вместо `"FREE_ATTENDANCE"`. Хороший контрактный тест.
- **Rate limiter**: `CheckinRateLimiterTest` (6 unit-тестов) — покрывает счётчик, expire, edge cases (null от Redis).
- **gRPC clients**: `AcademicGrpcClientTest`, `ScheduleGrpcClientTest` — проверяют mapping `StatusRuntimeException` → domain-exceptions.
- **Events**: `EventConsumerTest` (7 unit), `LessonEventServiceTest` (6 unit), `EventConsumerIntegrationTest`, `OneOffLessonCancelledConsumerIT`. Отличное покрытие lesson.closed/cancelled/deleted/one_off_cancelled + semester.archived.
- **Excuse full flow**: `ExcuseServiceTest` + `ExcuseControllerIT` + `ExcuseServiceApproveIT` + `ExcuseEventContractIT` + `ExcuseRepositoryTest` + `ExcuseEventPublisherTest` — покрывает D-11..D-18, cascade, event payload, duplicate detection.
- **Marking**: `MarkingServiceTest` + `MarkingIntegrationTest` — happy, not-headman, wrong-group, wrong-student, CANCELLED rejected, second mark.
- **Reports**: `ReportServiceTest` + `ReportIntegrationTest` — journal shape, student stats, CANCELLED exclusion, orphan filter.
- **Actuator**: `ActuatorIT` — только health/info/prometheus expose, env/beans/heapdump закрыты.
- **Security smoke**: `SecuritySmokeTest` — AOP-enforcement TEACHER vs STUDENT vs no-headers.

### Что покрыто плохо / не покрыто
- **Late-checkin**: нет ни одного теста в `latecheckin/` (нет файла `LateCheckinServiceTest.java`, нет `LateCheckinControllerIT.java`). Присутствует только `ScheduleGrpcClientTest` и `EventConsumerTest`, но они не проверяют `LateCheckinService.createRequest`, `applyDecision`, `applyDecisionFromWeb`, idempotency duplicate-RabbitMQ-delivery. **Критичная дыра**.
- **UserContextFilter**: нет теста на поведение с невалидным header (`X-User-Id: not-a-number` → какой статус?). Не покрыто поведение при отсутствии `X-User-Role` с присутствующим `X-User-Id` — там будет NPE (см. P0-1).
- **Time window edge cases**: `CheckinServiceTest.checkin_outsideTimeWindow` проверяет прошлую дату ("2020-01-01"), но НЕ проверяет:
  - момент за 5 минут 1 секунду до start — должен отклонить
  - момент ровно в start - 5мин — должен принять
  - момент ровно в end + 5мин — принять
  - момент ровно в end + 5мин 1сек — отклонить
- **Clock-based testing невозможен**: `Instant.now()` zash используется напрямую, `Clock` не инжектируется.
- **`AttendanceIndexInitializer` orphan-cleanup**: нет теста на safety guards (MAX_DELETE_RATIO, SMALL_COLLECTION_THRESHOLD, empty-alive-response).
- **MongoDB transaction**: нет теста, что excuse-approve cascade атомарен (см. P1-3).
- **RabbitMQ retry / DLQ behavior**: нет теста, что при падении `processLessonClosed` сообщение уходит в DLQ и можно recover.
- **Rate limit concurrent access**: `CheckinRateLimiter` не тестирован под concurrent пулом (хотя для Redis операций `INCR` + `EXPIRE` это race).
- **GeofenceService caching**: есть только `GeoUtilsTest` на Haversine. Нет тестов на TTL-refresh, на cold-start при недоступном academic (как поведёт себя `isWithinCampus` если кэш пуст — вызовет refresh синхронно и упадёт если academic тоже лежит).
- **`@PostConstruct` behavior**: нет теста, что при `@MockitoBean` на gRPC-клиента `@PostConstruct` не вызывает реальный gRPC.
- **Excuse file path**: нет теста для `createExcuseWithFile` с файлом >10 МБ (должен отклонять).
- **IDOR**: нет теста, что student A не может получить `/attendance/reports/student/stats` с `X-User-Id` студента B (проверка `requestContext.getUserId()` vs path-param). Впрочем, этот endpoint использует только RequestContext для userId, так что IDOR предотвращён на уровне API shape.

### Некорректные / подозрительные тесты
- **`LessonGenerationMergeTest`**: это "placeholder coverage" для AC-09, которое помечено как "known limitation (phase 60-05)". Тест пинает только shape `LessonResponse`, но ничего реально не тестирует. Должен быть либо `@Disabled`, либо расширен, либо удалён (если фича deprecated).
- **`CheckinIntegrationTest.checkin_happyPath`**: использует `startTime: 00:00` и `endTime: 23:59` — это **всегда** проходит проверку time window. В реальной жизни пары длятся 1.5 часа; тест этого не моделирует. Что, если bug в `isWithinCheckinWindow` работает только для широких окон?
- **Integration-tests reset mocks**: `Mockito.reset(scheduleGrpcClient, ...)` в `@BeforeEach` — это знак "context shared between tests, mocks leak". Работает, но хрупко — если кто-то в `@BeforeAll` настроит мок, `reset` его снесёт.

### Кандидаты на рефакторинг/удаление
- `LessonGenerationMergeTest` — placeholder, нет value.
- `RabbitConsumerTest` — проверяет только что queue/dlq declared, дублируется с `EventConsumerIntegrationTest`. Можно оставить один.

---

## Соответствие CLAUDE.md

| Пункт | Статус | Комментарий |
|-------|:------:|-------------|
| Contract-first: `*-api-contract` как java-library | ✅ | `attendance-api-contract/build.gradle.kts` — plugins `java-library`, без Spring Boot |
| Request DTO = Java `record` | ✅ | `CheckinRequest`, `CreateExcuseRequest`, `MarkRequest`, `UpdateExcuseStatusRequest`, `LateCheckinDecisionRequest` — все records |
| Response DTO = class extends RepresentationModel | ✅ | `CheckinResponse` extends, остальные DTO — records (некоторые response тоже records, например `LessonAttendanceResponse`). В целом контрактная граница соблюдена. |
| Без Lombok в contract | ✅ | Проверено, Lombok-аннотаций в `attendance-api-contract` нет |
| Lombok в app (entity) | ✅ | `AttendanceDocument`, `ExcuseTicket`, `LateCheckinRequest` — @Data/@Builder |
| Enum в Java UPPER_CASE | ✅ | Все enum-ы в UPPER_CASE |
| Enum в MongoDB lowercase | ✅ | `MongoConvertersConfig` — writer/reader converters для AttendanceStatus/Source/ExcuseType/ExcuseTicketStatus. Тест `EnumSerializationTest` подтверждает. |
| Без `@Enumerated(ORDINAL)` | N/A | MongoDB, не JPA. Ordinal неактуален. |
| Soft delete users | N/A | Attendance не владеет таблицей users. |
| Flyway для PostgreSQL | N/A | MongoDB без миграций. Нет migration скриптов. |
| HATEOAS Level 3 | ⚠️ | `EntityModel` используется, но только `withSelfRel()`. Нет prev/next в PagedModel (P3-5). Нет rels типа `attendance:approve` для excuse (хотя endpoint PATCH есть). |
| RFC 7807 Problem Details | ✅ | `ErrorResponse` record с `type`, `title`, `detail`, `instance`, `status`, `timestamp`, `fieldErrors` |
| `@ControllerAdvice` — централизованная обработка | ✅ | `GlobalExceptionHandler` покрывает ~12 exception типов |
| Request=record, Response=class | ✅ | См. выше |
| Controller implements XxxApi | ✅ | Все контроллеры implements из контракта. Маппинги в интерфейсе. |
| Пакетная структура `checkin/report/shared/port` | ✅ | Формально. Расширение до marking/latecheckin — см. "Изоляция доменов" |
| `report/` НЕ импортирует `checkin/` | ✅ | Grep пустой. ArchUnit-тест проходит. |
| REST пути `/api/{service}/...` | ⚠️ | В контракте пути `/attendance/...` без префикса `/api/`. Префикс `/api/` добавляет Gateway. В swagger-ui приложения URI отображаются без `/api/` — это может путать разработчиков. |
| gRPC пакет `ru.rutcampustrack.{service}.grpc` | ✅ | Код в `grpc/` |
| Event types `{domain}.{action}` | ✅ | `attendance.marked`, `excuse.requested`, `excuse.decided`, `late_checkin.requested`, `late_checkin.decided` |
| Окно check-in 5 мин до/5 мин после | ✅ | Реализовано в `isWithinCheckinWindow` |
| Автоматический ABSENT при lesson.closed | ✅ | `processLessonClosed` делает bulk upsert с `$setOnInsert` (см. P1-6) |
| Статусы: present/absent/excused/free_attendance/cancelled | ✅ | Все статусы в enum + реализации |
| Cancelled НЕ влияет на статистику | ✅ | `ReportService.getStudentStats` фильтрует `r.status() != CANCELLED` |

---

## Зависимости между проблемами

- **P0-1 → P0-4, P2-8**: если UserContextFilter залатан (требует shared secret от gateway), defense-in-depth-чеки groupId становятся менее критичны. Но их всё равно рекомендуется добавить.
- **P0-3 → P1-3**: если SemesterCacheService иногда возвращает null, `@Transactional` cascade в excuse-approve сохранит тикет APPROVED, но upsert в attendance будет с semesterId=null → нарушит `idx_user_semester_date` индекс-использование.
- **P1-3 → P1-4**: если фиксить через `@Transactional`, нужен replica-set в MongoDB. Иначе outbox-pattern.
- **P0-2 → P1-8**: решая хранение локации, можно также сохранить accuracy (которое влияет на надёжность геоотметки). Это параллельная задача.
- **P0-5 → P1-6**: retry-infra в RabbitListener одновременно решает идемпотентность при recover lesson.closed. Можно решать вместе.
- **P2-2 + P2-6**: кэш "existing_lesson_ids" + кэш teacher-subjects can share infrastructure (Caffeine + TTL).
- **P1-1 + P1-2**: оба про изоляцию доменов. Решаются одним расширением `AttendanceWritePort`.

---

## Вопросы к владельцу проекта

1. ✅ **Доверие заголовкам**: планируется ли внедрить shared-secret между gateway и downstream, mTLS, или оставить только network-level-isolation? От ответа зависит приоритет P0-1.
   → **AUTO-RESOLVED через 02-Q2 (2026-04-18)**: выбран **Internal JWT (Уровень 2 Zero Trust)** — RSA-подписанный короткий JWT от Gateway, валидация публичным ключом в downstream. См. `OWNER-ANSWERS.md` 02-Q2.

2. ✅ **Локация студента**: нужно ли вернуться к сохранению координат в `AttendanceDocument.checkin_location` (как описано в `docs/architecture/database-schema.md`)? Если нет — обновить документацию. Если да — нужна policy по GDPR/персональным данным (сколько хранить, кто видит).
   → **ACCEPTED BY OWNER (2026-04-18)**: координаты НЕ сохраняем, только проверка. Запланирована правка `docs/architecture/database-schema.md`. См. `OWNER-ANSWERS.md` 04-Q2.

3. **MongoDB deployment**: какая топология в production — standalone или replica set? От этого зависит возможность `@Transactional` (P1-3, P1-4).

4. **Excuse attachments**: согласны ли вы с тем, что 10 МБ-файлы идут в RabbitMQ envelope base64? Альтернатива — S3/MinIO. Вопрос бюджета / инфры.

5. **Autostart cleanup**: `AttendanceIndexInitializer.cleanupOrphans` — страшная mass-delete-операция. Можно ли превратить её в optional admin-endpoint с dry-run по умолчанию?

6. ✅ **Retry-стратегия для RabbitMQ**: должен ли `lesson.closed` пытаться apply N раз с backoff перед DLQ? Это ключевое решение для надёжности auto-ABSENT.
   → **AUTO-RESOLVED через 02-Q3 (2026-04-18)**: фикс C0-3 (in-app outbox) гарантирует доставку события **на стороне продьюсера** (schedule-service). Со стороны консьюмера (attendance) retry делается через `RabbitListenerContainerFactory` с `RetryInterceptorBuilder` (3 попытки с экспоненциальным backoff, затем DLQ). См. `OWNER-ANSWERS.md` 02-Q3. Сам DLQ-handler — отдельная задача (см. 05-Q8).

7. **Horizontal scaling**: будет ли запускаться >1 инстанса attendance-service? Если да — нужны ShedLock на `@PostConstruct` cleanup и на любые `@Scheduled` (которых сейчас в сервисе нет, но могут появиться).

8. **Геоотметка headman**: по коду (`CheckinService:110-112`) староста может делать check-in без проверки геолокации. Это правильный бизнес-правило или баг?

9. **Изоляция `marking/` и `latecheckin/`**: принимаем ли мы расширение `AttendanceWritePort` до полного Command-объекта ради убирания `checkin.AttendanceDocument` из других пакетов? Или фактическая изоляция только `checkin/↔report/` нас устраивает?

10. **Teacher_override**: `database-schema.md:304` упоминает `marked_by: "teacher_override"` — нужен ли этот workflow (преподаватель переопределяет статус)? Сейчас в коде его нет.
