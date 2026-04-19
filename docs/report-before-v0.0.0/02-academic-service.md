# 02. Academic Service — отчёт аудита

## Сводка

Academic Service — «мастер-домен» RutCampusTrack: пользователи, группы, семестры, предметы, назначения преподавателей, домашние задания, пороги посещаемости, помощники старосты и геозона кампуса. Стек: Spring Boot 3.4 + JPA (PostgreSQL) + Spring Data Redis (кэш) + RabbitMQ (fanout exchange) + gRPC (net.devh) + Spring HATEOAS + Spring Validation. Модуль правильно разделён на `academic-api-contract` (java-library с интерфейсами/DTO) и `academic-app` (Spring Boot). Flyway-миграции V1..V14 присутствуют, seed-данные вынесены в V2.

Сервис отвечает требованиям contract-first и RFC 7807 лучше, чем auth-service, но архитектурно несёт несколько **критичных** болячек: (1) plaintext-пароль хранится в колонке `users.initial_password` и **возвращается в REST-ответе** админу (и в gRPC-ответе боту) — нарушение ПДн; (2) `UserContextFilter` слепо доверяет HTTP-заголовкам `X-User-Id`/`X-User-Role`/`X-Is-Headman` — при прямом доступе к сервису (минуя Gateway) любой может представиться админом; (3) `@RequireRole` аспект применён на методах, но **не активирован через `@EnableAspectJAutoProxy` на классе-конфиге**; сейчас работает только потому, что `spring-boot-starter-aop` включён и Spring Boot автосконфигурит proxy — при ручной миграции конфигов легко сломать; (4) `SemesterService.activateSemester` содержит race-condition между `flush()` и последующим save — два одновременных activate могут оставить БД без активного семестра; (5) `GroupService.deleteGroup` делает физический `DELETE` с `ON DELETE SET NULL/CASCADE` вместо soft-delete, уничтожая историю; (6) events публикуются через `@TransactionalEventListener(AFTER_COMMIT)` без outbox-таблицы — падение RabbitMQ между commit-ом и send-ом приводит к потере события.

Контракт-first аккуратен: все контроллеры `implements XxxApi`, маппинги в интерфейсах. HATEOAS Level 3 применяется в Response (`UserResponse extends RepresentationModel<...>`). Но есть несогласованности: HomeworkCompletion-флаг `completed` пересчитывается через N+1 запросов; `listHomeworks` фетчит ВСЕ homeworks в память, а потом режет; `SubjectType` не имеет @Convert аннотации в entity, но V5 миграция держит каст varchar→enum; поле `initialPassword` попадает в `UserResponse` — контрактная утечка.

**Счётчики:** **P0 = 7**, **P1 = 14**, **P2 = 15**, **P3 = 11**.

## Структура модуля

```
services/academic-service/
├── academic-api-contract/              ← java-library (НЕ Spring Boot) ✓
│   └── src/main/java/ru/rutcampustrack/academic/contract/
│       ├── api/
│       │   ├── UserApi.java            ← @RequestMapping("/academic/users")
│       │   ├── GroupApi.java
│       │   ├── HomeworkApi.java
│       │   ├── SemesterApi.java
│       │   ├── SubjectApi.java
│       │   ├── AssignmentApi.java
│       │   ├── AssistantApi.java
│       │   ├── DashboardApi.java
│       │   └── ThresholdApi.java
│       ├── dto/
│       │   ├── assignment/ {AssignTeacherRequest, AssignmentResponse}
│       │   ├── assistant/  {AssignAssistantRequest, AssistantResponse, UpdateAssistantPermissionsRequest}
│       │   ├── dashboard/  {DashboardStatsResponse}
│       │   ├── group/      {CreateGroupRequest, UpdateGroupRequest, GroupResponse, GroupStatus, PromotionPreviewItem, PromotionSummary}
│       │   ├── homework/   {CreateHomeworkRequest, UpdateHomeworkRequest, HomeworkResponse}
│       │   ├── semester/   {CreateSemesterRequest, UpdateSemesterRequest, DeleteSemesterRequest, OverlapCheckResponse, SemesterResponse}
│       │   ├── subject/    {CreateSubjectRequest, UpdateSubjectRequest, SubjectResponse}
│       │   ├── threshold/  {SetThresholdRequest, ResolvedThresholdResponse, ThresholdResponse}
│       │   └── user/       {CreateUserRequest, UpdateUserRequest, PatchUserRequest, TransferStudentRequest,
│       │                    UpdateAvatarRequest, UserResponse, UserCreatedResponse}
│       ├── enums/ {AccountStatus, AssistantPermission, SubjectType, UserRole}
│       └── exception/ {ErrorResponse, ResourceNotFoundException}
│
├── academic-app/                       ← Spring Boot
│   └── src/main/java/ru/rutcampustrack/academic/
│       ├── AcademicApplication.java
│       ├── assignment/   {AssignmentController, AssignmentService, AssignmentAssembler}
│       ├── assistant/    {AssistantController, AssistantService, AssistantAssembler}
│       ├── config/
│       │   ├── CacheConfig.java        ← Redis + ObjectProvider<RedisConnectionFactory>
│       │   ├── ClockConfig.java        ← Europe/Moscow
│       │   ├── EnumConverters.java     ← 4 inner @Converter(autoApply=true)
│       │   ├── LowercaseEnumConverter.java
│       │   └── WebConfig.java          ← case-insensitive enums + query-param enum factory
│       ├── dashboard/    {DashboardController, DashboardService}
│       ├── entity/ {AttendanceThreshold, CampusSetting, Group, HeadmanAssistant,
│       │            Homework, HomeworkCompletion, Semester, StudentGroupHistory,
│       │            Subject, TeacherSubjectGroup, User}
│       ├── event/ {DomainEvent, DomainEventListener, RabbitConfig, GroupArchivedEvent,
│       │           GroupRenamedEvent, GroupUpdatedEvent, HomeworkPublishedEvent,
│       │           HomeworkUpdatedEvent, SemesterArchivedEvent, SubjectDeletedEvent}
│       ├── exception/ {GlobalExceptionHandler, BadRequestException, ConflictException,
│       │               AccessDeniedException, ScheduleServiceUnavailableException}
│       ├── group/   {GroupArchivalService, GroupAssembler, GroupController, GroupNameParser,
│       │             GroupPromotionService, GroupService, GroupSpecifications, ProgramType,
│       │             UnknownProgramTypeException}
│       ├── grpc/    {AcademicGrpcServiceImpl, AcademicReadService, GrpcAuthInterceptor,
│       │             GrpcExceptionAdvice, GrpcSecretClientInterceptor, ScheduleGrpcClient}
│       ├── homework/ {HomeworkAssembler, HomeworkController, HomeworkService}
│       ├── repository/ (11 Repository)
│       ├── security/   {RequestContext, RequireRole, RoleCheckAspect, UserContextFilter}
│       ├── semester/   {SemesterAssembler, SemesterController, SemesterService}
│       ├── subject/    {SubjectAssembler, SubjectController, SubjectService}
│       ├── threshold/  {ThresholdAssembler, ThresholdController, ThresholdService}
│       └── user/       {UserAssembler, UserController, UserService, UserSpecifications}
│   └── src/main/resources/
│       ├── application.yml             ← DEBUG-лог, DDL-validate, Flyway enabled
│       ├── application-prod.yml        ← только actuator-экспозиция, ничего больше
│       └── db/migration/V1..V14__*.sql
│   └── src/test/...                    ← ~26 файлов Java-тестов
```

Расхождения со структурой, декларированной в CLAUDE.md:
- **Совпадает** разбиение на `*-api-contract` + `*-app` — contract-first соблюдён.
- `contract/exception/ResourceNotFoundException` лежит в контрактном модуле — допустимо (чтобы клиент мог идентифицировать тип ошибки из HTTP-ответа), но не используется клиентом для десериализации; для симметрии стоит перенести в `academic-app/exception`.
- Отсутствуют: `report/` (CLAUDE.md упоминает его только для attendance), `shared/port/` — academic не имеет изолированных доменов, где порт был бы нужен.

---

## Критичные проблемы (P0)

### P0-1: ✅ ACCEPTED — Plaintext-пароль в ответе REST API `GET /academic/users` и в gRPC `GetUserByTelegramId`
**Статус:** by design (см. `OWNER-ANSWERS.md` 02-Q1 + 01-Q1 + Meta M1, 2026-04-18). Оба канала (REST и gRPC) остаются как есть — нужны admin-UI и notification-bot. Ниже — оригинальное описание для исторической ссылки.

- **Где:**
  - `academic-api-contract/.../dto/user/UserResponse.java:33` — поле `String initialPassword`.
  - `academic-app/.../user/UserAssembler.java:54-74` (`toResponse(entity, includeInitialPassword=true)`).
  - `academic-app/.../user/UserController.java:56-58, 69-73` — admin-контекст передаёт `toAdminModel` с `initialPassword`.
  - `academic-app/.../grpc/AcademicGrpcServiceImpl.java:245-253` — `GetUserByTelegramId` возвращает `initialPassword` в proto-ответе.
  - `academic-app/src/main/resources/db/migration/V1__baseline.sql:34` — колонка `initial_password VARCHAR(128)`.
- **Что:** админ через `GET /academic/users` и `GET /academic/users/{id}` получает поле `initialPassword` в JSON-ответе для каждого пользователя, у которого ещё не сменён пароль. Также бот через gRPC `GetUserByTelegramId` (notification-bot для команды `/start`) получает plaintext-пароль. Это **значительное расширение attack surface** относительно P0-2 из отчёта auth-service: кроме наличия колонки в БД, сервис активно раздаёт её наружу по двум каналам.
- **Риск:**
  - Весь список паролей утекает через один `GET /api/academic/users?size=1000`.
  - Если у админа кэш/история браузера/CSRF-прокси — пароли остаются в артефактах.
  - Логи API Gateway (особенно при `trace` уровне) содержат JSON-ответы → пароли в логах.
  - Notification-bot получает пароль через gRPC — если секрет gRPC утёк (см. P0-5), любой внешний потребитель может собрать полную базу логинов+паролей.
  - Нарушение 152-ФЗ РФ о ПДн (пароль — специальная категория ПДн).
- **Как чинить:**
  1. Немедленно убрать поле `initialPassword` из `UserResponse` (оставить только в `UserCreatedResponse` — одноразовый ответ при `POST /users`).
  2. В `UserController.getUser`/`listUsers` заменить `toAdminModel` → `toModel`.
  3. В gRPC `GetUserByTelegramId` — не возвращать `initial_password`. Бот должен либо (а) при `/start` показать одноразовый код, который пользователь получает в Telegram при создании учётки, либо (б) запросить смену пароля через `/auth/setup-password?token=...` (см. P0-2 в отчёте auth-service).
  4. Удалить колонку `users.initial_password` миграцией V15 (после того как клиенты отвыкнут).
  5. До удаления — маскировать поле в логах через `@JsonIgnore` + отдельный метод для одноразового показа при creation.
- **Зависимости:** notification-bot (команда `/start`), web-panel admin users-list, P0-2 auth-service.

### P0-2: 🔧 TO-FIX через Internal JWT — `UserContextFilter` доверяет HTTP-заголовкам без проверки источника
**Статус (2026-04-18):** будет закрыто фиксом из C0-1 (Internal JWT). См. `OWNER-ANSWERS.md` 02-Q2.


- **Где:** `academic-app/.../security/UserContextFilter.java:22-46`.
- **Что:** фильтр читает `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` **напрямую из входящего запроса** и выставляет в `RequestContext`. Единственная предпосылка корректности — что api-gateway (а) принудительно перезаписывает эти заголовки на основе валидации JWT, и (б) service-to-service network не доступен снаружи. Если злоумышленник получит прямой сетевой доступ к `academic-service:9091` (docker network открыт ошибочно, misconfigured firewall, утечка через реверс-прокси) — он может отправить запрос с `X-User-Id: 1, X-User-Role: ADMIN` и выполнить любую операцию.
- **Риск:** полный RCE через admin-права. Из docker-compose.yml сервис находится в приватной сети `rutcampustrack_private_net`, но: (а) если администратор переведёт прод на k8s и ошибочно выставит сервис в публичный SVC — авторизация пропадёт; (б) в dev/testing среде открытый доступ к порту тривиален; (в) доступ через compromised pod в той же сети (lateral movement) тоже снимает все ограничения.
- **Как чинить:** добавить mTLS между gateway и сервисами, либо передавать подпись заголовков (HMAC secret, совпадающий у gateway и сервиса: `X-Context-Signature = HMAC_SHA256(secret, userId|role|groupId|timestamp)`). Точно так, как сделано для gRPC через `x-grpc-secret`. В минимальном варианте — проверять в фильтре, что запрос пришёл с доверенного IP (gateway) через `X-Forwarded-For`. Также нужен `@ConditionalOnProperty` на фильтре: если заголовок `X-Internal-Call: true` отсутствует и `secret` не настроен — отказать.
- **Зависимости:** api-gateway (выставление подписи), attendance/schedule/notification сервисы (они тоже используют этот паттерн), общий `security-header-validator` модуль.

### P0-3: `UserContextFilter` бросает `NumberFormatException` при некорректном заголовке — 500 вместо 400
- **Где:** `academic-app/.../security/UserContextFilter.java:35-43`.
- **Что:** `Long.parseLong(userIdHeader)`, `UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase())`, `Boolean.parseBoolean(...)` — без try/catch. Если gateway по ошибке или злоумышленник (см. P0-2) пришлёт `X-User-Id: abc` или `X-User-Role: FOOBAR` — сервис бросит RuntimeException, который `GlobalExceptionHandler` поймает в `handleGeneral` и вернёт 500. Также `request.getHeader("X-User-Role").toUpperCase()` бросит NPE если заголовок отсутствует, а `X-User-Id` присутствует.
- **Риск:** прод-логи переполняются 500-ошибками на мусорный трафик, скрывая реальные ошибки. Gateway может внезапно послать некорректные заголовки при частичном апдейте. Плюс возможен crash-loop, если какой-то проксирующий компонент лишает заголовков.
- **Как чинить:** обернуть в try/catch с возвратом 401 Unauthorized + строго `@ExceptionHandler(UserContextException.class)` в `GlobalExceptionHandler`. Либо использовать `Optional.ofNullable(...).map(...).orElse(...)`. Главное — не давать запросу без корректного контекста идти дальше, и не отвечать 500.
- **Зависимости:** Gateway (чтобы не посылал мусор), `GlobalExceptionHandler`.

### P0-4: `SemesterService.activateSemester` — race condition, оставляющее систему без активного семестра
- **Где:** `academic-app/.../semester/SemesterService.java:122-148`.
- **Что:** реализация делает (1) `deactivateAllActive()` — bulk UPDATE, (2) `flush + clear`, (3) re-read target по ID, (4) `setActive(true) + saveAndFlush`. Между шагами (1) и (4) другая транзакция может успеть сделать то же самое — bulk UPDATE серриализован через row lock, но READ → WRITE между транзакциями не атомарен. Хуже — если шаг (4) провалится (нет такого id, concurrent `deleteSemester`), то шаг (1) уже совершился и commit на deactivate прошёл — активного семестра больше нет, пока админ не повторит команду. Тесты не используют concurrency (`SemesterServiceTest` — чистый mock без реальной БД). Также в `deleteSemester` (`:154-161`) нет запрета удалять активный семестр — можно удалить единственный active, не пометив никакой другой.
- **Риск:** после неудачного activate схема `teacher_subject_groups`, `schedule_items` для которых resolved активный семестр нужен по gRPC `getActiveSemester` — начинает падать с `ResourceNotFoundException("Semester","isActive",true)`. Это цепочкой роняет schedule-service, attendance-service, notification-bot. Одновременные клики админом на две активации («toggle») тоже могут оставить без active-строки.
- **Как чинить:**
  1. В `activateSemester` использовать `SERIALIZABLE`-уровень или `SELECT ... FOR UPDATE` на строке-цели + `UPDATE ... WHERE id <> :id AND is_active`.
  2. Или: заменить `EXCLUDE (is_active WITH =) WHERE is_active=TRUE` на partial unique index + `INSERT ... ON CONFLICT DO UPDATE`.
  3. Проверить, что target семестр существует и не `isActive=true` **до** deactivateAllActive — чтобы избежать пустого состояния.
  4. В `deleteSemester`: запретить удаление активного семестра; потребовать deactivate-а.
  5. В `updateSemester` запретить изменение дат, если семестр активен (не только завершён).
- **Зависимости:** schedule-service (резолвит активный семестр), attendance-service (статистика в разрезе семестра), фронт admin-SemesterPage.

### P0-5: gRPC-секрет передаётся через `${GRPC_SECRET:}` с пустым default — автоматическое отключение аутентификации
- **Где:**
  - `academic-app/.../grpc/GrpcAuthInterceptor.java:20-35` — `if (expectedSecret != null && !expectedSecret.isBlank())`.
  - `academic-app/.../grpc/GrpcSecretClientInterceptor.java:17, 28-31` — аналогично.
  - `academic-app/src/main/resources/application.yml:49` — `secret: ${GRPC_SECRET:}`.
- **Что:** если env-переменная `GRPC_SECRET` не задана (dev, misconfigured deploy), interceptor на сервере сразу `return next.startCall(call, headers)` — **любой клиент может вызывать gRPC-методы без аутентификации**. Клиентский interceptor тоже не ставит заголовок. В итоге в dev-сборке сервисов можно общаться без секрета; в проде — стоит только админу забыть выставить переменную, и любой, кто получит сетевой доступ к 19091, может читать данные users/groups/semesters через gRPC (включая `initial_password` из P0-1).
- **Риск:** compromise защиты между сервисами. Особенно опасно в комбинации с P0-2 — без gRPC-секрета notification-bot, attendance, schedule (и любой lateral attacker) могут запросить `GetUserByTelegramId(telegramId=1234)` → получить plaintext пароль админа.
- **Как чинить:**
  - В проде — падать при старте, если `GRPC_SECRET` пустой. `@Value("${grpc.auth.secret}")` без default + `@Validated @NotBlank` на `@ConfigurationProperties` → сервис не поднимется.
  - Interceptor: если `expectedSecret == null || isBlank` → всегда `UNAUTHENTICATED`, **не** auto-skip. Это пойдёт в dev — значит dev-профиль должен явно задавать fake secret через `application-dev.yml`.
  - Для симметрии — GrpcSecretClientInterceptor тоже должен при пустом secret падать на старте клиента.
  - Ротация секрета: хранить в env/vault, rotate через restart всех сервисов.
- **Зависимости:** schedule-service, notification-bot, attendance-service (gRPC клиенты), proxy Gateway → academic gRPC (в текущей архитектуре gRPC не идёт через Gateway).

### P0-6: 🔧 TO-FIX через In-app outbox — События RabbitMQ публикуются через AFTER_COMMIT без outbox — потеря при падении брокера
**Статус (2026-04-18):** будет закрыто фиксом из C0-3 (in-app outbox table). См. `OWNER-ANSWERS.md` 02-Q3.


- **Где:** `academic-app/.../event/DomainEventListener.java:31-35` (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + прямой `rabbitTemplate.convertAndSend`).
- **Что:** классический dual-write баг. Транзакция БД коммитится → appl-event-listener получает управление → пытается отправить в RabbitMQ. Если брокер недоступен (сеть потеряна, крэш, circuit breaker) — сообщение пропадает. Никакого ретрая, никакого outbox-а (`domain_events` таблицы или equivalent). Комментарий в коде «Per research decision: no channelTransacted to avoid message loss with AFTER_COMMIT» — это просто утверждение, что даже транзакционный режим AMQP не спасёт, но не предлагает альтернативы.
- **Риск:** `group.archived`/`group.renamed`/`semester.archived` теряются → schedule-service не инвалидирует свои кэши → старые групповые имена в расписании навсегда. `homework.published` потерялся → notification-bot не шлёт push в группу, студенты не узнают про ДЗ. Семантика «событие публикуется надёжно» в проекте не реализована нигде.
- **Как чинить:** реализовать transactional outbox:
  1. Таблица `domain_events(id, event_type, payload_json, occurred_at, sent_at)`.
  2. В сервисах `eventPublisher.publishEvent(...)` заменить на `outboxRepository.save(new OutboxEntry(...))` в той же транзакции.
  3. Фоновый `@Scheduled` job раз в секунду читает `WHERE sent_at IS NULL ORDER BY id LIMIT 200`, отправляет в Rabbit, помечает `sent_at = now()`.
  4. Альтернативы: Debezium CDC или `spring-cloud-stream` + transactional outbox pattern.
  5. Для простоты — at-least-once доставка + `event_id` UUID для дедупликации на consumer.
- **Зависимости:** ВСЕ сервисы (schedule, attendance, notification-*). Либо принять риск и документировать ограничение.

### P0-7: Загрузка всех ДЗ в память + N+1 запросов `HomeworkService.listHomeworks`
- **Где:** `academic-app/.../homework/HomeworkService.java:141-148`; `academic-app/.../homework/HomeworkController.java:46-55`.
- **Что:** `homeworkRepository.findByGroupIdAndSemesterId(groupId, semesterId)` возвращает `List<Homework>`, затем код делает `subList` для пагинации. Все ДЗ группы за семестр загружаются из БД в память, независимо от `Pageable.size`. Плюс в `listHomeworks` контроллер для каждого ДЗ делает `homeworkService.isCompleted(hw.getId())` → это **N+1 SQL** (`SELECT EXISTS FROM homework_completions WHERE homework_id=? AND student_id=?`). Для группы с 200 ДЗ — 200 отдельных запросов.
- **Риск:** (1) памяти O(N) на страницу N элементов (должно быть O(page_size)); (2) время ответа растёт линейно с размером семестра; (3) при массовом списке домашек (например, у старосты 500 ДЗ за семестр) — 500 SQL-запросов + полный скан в память. При 100 одновременных студентов — 50 000 запросов за секунду, вместо 100.
- **Как чинить:**
  1. Использовать существующий `findByGroupIdAndSubjectIdAndSemesterId(groupId, subjectId, semesterId, Pageable)` или добавить pageable-версию `findByGroupIdAndSemesterId`.
  2. Для `completed` — один запрос `SELECT homework_id FROM homework_completions WHERE student_id=? AND homework_id IN (?,?,...)` → Set<Long> → `response.completed = set.contains(hw.id)`.
  3. Также `listAssignments` (`assignment/AssignmentService.java:74-81`) и `getMyAssignments` (`:92-101`) имеют ту же проблему — `findAll → subList`.
- **Зависимости:** repository changes, контракт не меняется.

---

## Серьёзные проблемы (P1)

### P1-1: Физический `DELETE` группы в `GroupService.deleteGroup` вместо soft-delete
- **Где:** `academic-app/.../group/GroupService.java:141-150`; V1 миграция имеет `groups` без soft-delete поля; `archived_at` есть (V9), но только для архивного флоу через `GroupArchivalService`.
- **Что:** `DELETE FROM groups WHERE id=?`. Связанные таблицы (V1 миграция):
  - `users.group_id` → `ON DELETE SET NULL` (студенты теряют группу).
  - `student_group_history.group_id` → **`ON DELETE` по умолчанию, т.е. RESTRICT** — физическое удаление упадёт если есть история.
  - `teacher_subject_groups.group_id` → `ON DELETE CASCADE` (все назначения удалятся).
  - `subjects.group_id` → `ON DELETE` не указан (RESTRICT).
  - `attendance_thresholds.group_id` → `ON DELETE CASCADE`.
  - `homeworks.group_id` → `ON DELETE CASCADE` — ВСЕ ДЗ удалятся!
- **Риск:** (1) если в БД нет students/subjects/history — DELETE проходит, и вся цепочка домашек/назначений/порогов теряется; (2) если хоть одна запись держит RESTRICT — контроллер возвращает 500 через `DataIntegrityViolationException` (не 409, потому что мапа constraint→field его не знает); (3) в реальной прод-базе удаление приведёт к потере истории посещаемости (attendance-service держит group_id независимо). CLAUDE.md явно требует «Soft delete… никогда DELETE». Здесь правило нарушено.
- **Как чинить:** заменить на `group.setArchivedAt(now); group.setActive(false); publish(GroupArchivedEvent)` — пере-использовать `GroupArchivalService`. Эндпоинт `DELETE /groups/{id}` должен быть soft-delete, а «physical delete» — отдельная команда для админа с подтверждением (или совсем отсутствовать).
- **Зависимости:** attendance-service (держит group_id снаружи FK), schedule-service (держит group_id), UI admin page.

### P1-2: Hard delete `Semester`, `Subject`, `Homework`, `TeacherSubjectGroup` — потеря истории
- **Где:** `SemesterService.deleteSemester`, `SubjectService.deleteSubject` (каскадно через `SubjectDeletedEvent`), `HomeworkService.deleteHomework`, `AssignmentService.removeAssignment`.
- **Что:** физические `DELETE` всех этих сущностей. Удаление семестра с `ON DELETE CASCADE` на `teacher_subject_groups`/`homeworks`/`attendance_thresholds` стирает всё, что накоплено. Удаление Subject публикует `subject.deleted` — schedule-service каскадно удаляет `schedule_items` и `schedule_one_off_lessons` (по комментарию в коде), что в свою очередь триггерит удаление attendance records. Восстановление невозможно. Журнал посещаемости / статистика — обнуляются.
- **Риск:** потеря аудита. «Семестр удалён по ошибке» = минус семестр истории. «Предмет удалён старостой» = вся посещаемость по нему потеряна, включая историю прошлых семестров. Для ВУЗа это недопустимо.
- **Как чинить:** ввести soft-delete или статус «archived». Для `Semester` — поле `archived=true`, убрать физический DELETE. Для `Subject` — аналогично. Для `Homework` — оставить hard delete (ДЗ не критично для статистики) **но** с confirmation-шагом и 7-дневным undo. Для `TeacherSubjectGroup` — можно оставить hard delete, это не историческая сущность.
- **Зависимости:** attendance, schedule, notification.

### P1-3: `@EnableAspectJAutoProxy` отсутствует — AOP работает только благодаря Spring Boot auto-config
- **Где:** `academic-app/.../AcademicApplication.java` (нет `@EnableAspectJAutoProxy`); нет отдельного `@Configuration`-класса для AOP.
- **Что:** `RoleCheckAspect` помечен `@Aspect @Component`. Работает это только потому, что Spring Boot auto-config `AopAutoConfiguration` включён (зависимость `spring-boot-starter-aop`). Но если кто-то выключит auto-config (`@SpringBootApplication(exclude=AopAutoConfiguration.class)`) или сменит `spring.aop.auto=false` в `application.yml`, **все `@RequireRole` перестанут работать**, и контроллеры начнут принимать запросы от любых ролей. Без логов, без ошибок.
- **Риск:** скрытая потеря контроля доступа при рефакторе конфигов. Нет integration-теста, который бы в реальном Spring-контексте проверял, что `@RequireRole({ADMIN})` на `createUser` отклоняет STUDENT-запрос (в текущем `RoleCheckAspectTest` aspect тестируется в изоляции без Spring).
- **Как чинить:**
  1. Добавить `@EnableAspectJAutoProxy` на `AcademicApplication` или на отдельный `SecurityConfig`.
  2. Добавить integration-тест, который запускает контроллер через MockMvc с `X-User-Role: STUDENT` на admin-endpoint и ожидает 403.
  3. Или вовсе перейти на `@PreAuthorize("hasRole('ADMIN')")` (Spring Security), что избавит от custom-аспекта.
- **Зависимости:** все контроллеры с `@RequireRole`.

### P1-4: Смешанные роли в `@RequireRole({STUDENT})` — headman-bypass логика размыта + IDOR
- **Где:** `RoleCheckAspect.java:31-35` (`headmanBypass = requestContext.isHeadman() && required.contains(STUDENT)`); все `HomeworkController`/`SubjectController`/`AssistantController`/`ThresholdController` отмечают headman-операции как `@RequireRole({STUDENT})`.
- **Что:** фактически «староста может управлять ДЗ» реализовано двумя проверками: (1) `@RequireRole({STUDENT})` пропускает любого студента; (2) `HomeworkService.requireHeadmanOrManageHomework` проверяет `isHeadman`. Это разнесено: aspect не знает про headman-специфику, сервис не знает, что уже прошли role-check. В `AssistantService.requireHeadman()` (`:33-37`) тоже дублируется. Легко пропустить проверку: `HomeworkService.getHomework` (GET) вообще не требует никакой роли — любой аутентифицированный видит любую чужую ДЗ (IDOR).
- **Риск:**
  - IDOR в `GET /academic/homeworks/{id}` — студент другой группы может увидеть чужую ДЗ (потенциально с приватной ссылкой / файлом).
  - IDOR в `GET /academic/subjects/{id}` — предмет чужой группы виден всем, кто знает id.
  - `GET /academic/assignments?groupId=X&semesterId=Y` (`@RequireRole({ADMIN,STUDENT})`) — студент любой группы может прочитать teacher-subject-group других групп.
  - `GET /academic/thresholds/resolve?groupId=X` — студент может запросить threshold чужой группы.
- **Как чинить:**
  1. Ввести дополнительный слой `@RequireHeadman` (отдельная аннотация + aspect), чтобы не использовать `STUDENT` как «староста».
  2. В сервисах добавить проверку `subject.getGroupId() == requestContext.getGroupId()` для student-only операций.
  3. Для read-operations (`getHomework`, `getSubject`) добавить cross-group check: студенту нельзя читать ресурсы чужой группы.
  4. Или переехать на Spring Security + `@PreAuthorize("#subject.groupId == authentication.principal.groupId")`.
- **Зависимости:** вся security-модель academic-сервиса.

### P1-5: `HomeworkController.markComplete` возвращает 200, контракт обещает 204
- **Где:** `HomeworkController.java:73-76` — `return ResponseEntity.ok().build();`; `HomeworkApi.java:81` — `@ApiResponse(responseCode = "204", ...)`.
- **Что:** контракт декларирует 204, контроллер возвращает 200 с пустым body. Swagger-клиент будет ругаться; интеграционные тесты могут ожидать 204 и сломаться после изменения.
- **Как чинить:** `return ResponseEntity.noContent().build();`.
- **Зависимости:** PWA homework page (клиент).

### P1-6: Цепочка `SubjectService.deleteSubject` с force=true обходит `ConflictException`, но не логирует — audit gap
- **Где:** `SubjectService.java:152-177`.
- **Что:** при `force=true` сервис скипает pre-check через gRPC и публикует `SubjectDeletedEvent` → каскад удаляет attendance. Нет логирования (`log.warn("Subject forcibly deleted with attendance history ...")`), нет фиксации (who+when+why) в БД. Старост (`@RequireRole({STUDENT})` + isHeadman) может удалить предмет со всей историей посещаемости без трассировки. Админ не имеет отдельного control над force.
- **Риск:** audit trail полностью отсутствует. Злонамеренный староста стирает историю посещаемости, когда его ловят на прогулах.
- **Как чинить:**
  1. Запретить `force=true` старостам — только ADMIN.
  2. Логировать на WARN с user_id + subject_id + счётчиками из `refs`.
  3. Таблица `audit_log(user_id, action, entity, entity_id, details_json, occurred_at)` и запись в ней перед физическим delete.
- **Зависимости:** schedule-service (удаляет schedule items по subject.deleted), attendance (удаляет attendance records).

### P1-7: `HomeworkService.requireAuthor` НЕ проверяет, что автор — текущий староста группы
- **Где:** `HomeworkService.java:89-95` и вызовы `requireAuthor` в `updateHomework`/`deleteHomework`.
- **Что:** автор ДЗ = `published_by` = любой студент группы. Если текущий староста сменился (старый выпустился, назначен новый), новый староста **не может** править/удалить ДЗ старого автора. Аналогично если `published_by` — помощник, который потом был revoked, новый assistant с `manage_homework` permission не может исправить чужую ДЗ. По Phase 61 / D-05 это задумано («single owner»), но операционно проблемы.
- **Риск:** «мёртвое» ДЗ, которое невозможно удалить, пока не получить доступ к учётке бывшего старосты. Admin-панель тоже не может отредактировать (ADMIN в `@RequireRole({STUDENT})` не пройдёт). Фронт должен показать ошибку «только автор может редактировать», но это blocker для опер.
- **Как чинить:** разрешить текущему старосте группы + ADMIN edit/delete ЛЮБОГО ДЗ в своей группе. Ограничение «только автор» оставить для обычного студента-помощника или удалить вовсе.
- **Зависимости:** UI старосты.

### P1-8: `HomeworkController.listHomeworks` помечен `@RequireRole({STUDENT, ADMIN})` — TEACHER не может смотреть ДЗ
- **Где:** `HomeworkController.java:44-55`.
- **Что:** преподаватель, который ведёт предмет, не может зайти в список ДЗ по своему предмету (`@RequireRole({STUDENT, ADMIN})` отклонит TEACHER). Но teacher-панель должна показывать, какие ДЗ были заданы на пару.
- **Как чинить:** добавить TEACHER в роли + сервис фильтрует список, оставляя только ДЗ по предметам, которые ведёт этот преподаватель (через `teacher_subject_groups`).
- **Зависимости:** teacher web-panel.

### P1-9: `GroupController.getGroup` доступен всем аутентифицированным (включая студентов других групп)
- **Где:** `GroupController.java:55-59` — нет `@RequireRole`.
- **Что:** любой студент может прочитать данные любой группы по ID. Из этого он узнаёт имя группы (публично, не критично) + `createdAt` + `archivedAt`. Низкий impact, но нарушение принципа minimum-exposure.
- **Как чинить:** либо полностью публично (подтвердить задумку), либо `@RequireRole({ADMIN,TEACHER,STUDENT})` (по факту авторизованный user).
- **Зависимости:** UI.

### P1-10: `HomeworkRepository.findByGroupIdAndSemesterId` не имеет индекса `(group_id, semester_id)`
- **Где:** V1 migration `CREATE INDEX idx_hw_group_subject ON homeworks(group_id, subject_id);` + V13 `CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date);`.
- **Что:** запрос `findByGroupIdAndSemesterId` использует только существующий `idx_hw_group_subject` для prefix `group_id` — но не для semester_id, который попадает в «rest filter» после index-scan. При 100+ ДЗ в группе — full table scan по группе. Новый индекс V13 тоже не покрывает.
- **Риск:** рост latency в `GET /academic/homeworks` с ростом ДЗ.
- **Как чинить:** миграция V15 `CREATE INDEX idx_hw_group_semester ON homeworks(group_id, semester_id);`.
- **Зависимости:** —.

### P1-11: `UserService.generateLogin` — ADMIN и TEACHER делят одну sequence
- **Где:** `UserService.java:344-356` — `teacher_login_seq` используется и для admin, и для teacher.
- **Что:** если админ создал teacher#3 (`teacher3`) и тут же admin → seq → 4, login = `admin4`. Дальше teacher#4 → `teacher5`. Нумерация становится «дырявой», но unique через `existsByLogin`. Дизайн-смысл отсутствует: почему admin и teacher тратят один счётчик?
- **Риск:** (1) невозможно по логину определить тип учётки; (2) при переименовании или миграции sequence легко потерять консистентность.
- **Как чинить:** ввести `admin_login_seq` + миграция V15.
- **Зависимости:** —.

### P1-12: `UserSpecifications.matchesSearch` — функция `str()` не является стандартной Hibernate-функцией
- **Где:** `UserSpecifications.java:52-56` — `cb.function("str", String.class, ...)`.
- **Что:** в Hibernate 6 функция `str()` **не зарегистрирована по умолчанию** для PostgreSQL-диалекта. Это JPQL-устаревшая функция (была в Hibernate 4-5). В прод-сборке поиск по `telegramId` может падать с `NoSuchFunctionException`, либо неявно мапиться на что-то некорректное. Тест `UserSearchIntegrationTest` проверяет ФИО/логин, но включает ли он telegramId — сомнительно.
- **Риск:** поиск по TG ID admin-ом выдаёт 500.
- **Как чинить:** использовать `cb.function("cast", String.class, root.get("telegramId"), cb.literal(String.class))` или создать явный `@ColumnTransformer`, либо добавить кастомную функцию через `FunctionContributor`. Alternatively — кастовать в JPQL через `CAST AS VARCHAR`.
- **Зависимости:** тесты поиска.

### P1-13: `RequestContext` — request-scoped bean, но используется внутри `@Transactional`-метода сервиса
- **Где:** `UserService`, `HomeworkService`, `SubjectService`, `AssistantService`, `ThresholdService`, `AssignmentService`, `GroupService`.
- **Что:** `RequestContext` имеет `@Scope("request", proxyMode=TARGET_CLASS)`, значит каждый вызов `requestContext.getUserId()` — через CGLIB-прокси. Внутри транзакции (`@Transactional`) это работает, пока транзакция идёт в пределах одного HTTP-запроса. Но: (а) `@TransactionalEventListener(AFTER_COMMIT)` выполняется **после** commit-а, но в том же thread → request scope ещё живой, ОК; (б) `@Scheduled` методы (их нет в коде, но могут появиться) упадут — request scope не активен; (в) `@Async` тоже упадёт. На текущий момент — работает, но хрупкая архитектура.
- **Риск:** любой вызов сервиса вне HTTP-контекста (тесты, scheduled job) → `IllegalStateException` или NullPointer.
- **Как чинить:** разделить — сервис не должен знать про HTTP. Принимать `authorContext` (userId/role/groupId) явным параметром в публичных методах. Использовать `RequestContext` только на уровне контроллера.
- **Зависимости:** широкий рефактор.

### P1-14: `GlobalExceptionHandler.handleGeneral` возвращает `ex.getMessage()` в detail — утечка internal detail
- **Где:** `GlobalExceptionHandler.java:306-318`.
- **Что:** любой непойманный `Exception` → 500 с `detail = ex.getMessage()`. Для JDBC-ошибки это будет текст типа `ERROR: null value in column "employee_number" violates not-null constraint`. Внутренняя структура БД утекает в ответ клиенту.
- **Риск:** information disclosure.
- **Как чинить:** `detail = "Внутренняя ошибка сервера"` (не `ex.getMessage()`) + log.error с stacktrace.
- **Зависимости:** —.

---

## Средние (P2)

### P2-1: DEBUG-логирование в `application.yml` не переопределено в prod
- **Где:** `application.yml:64-67` — `ru.rutcampustrack: DEBUG`; `application-prod.yml` содержит только management-блок.
- **Что:** в проде DEBUG-строки включают payload-и ивентов (`DomainEventListener.log.debug`), SQL-запросы Hibernate, gRPC трейсы. Это и объём, и возможные чувствительные данные (telegramId, initialPassword).
- **Как чинить:** добавить в `application-prod.yml`:
  ```yaml
  logging:
    level:
      ru.rutcampustrack: INFO
      org.hibernate.SQL: WARN
  ```

### P2-2: `User.isHeadman` — свойство в JSON сериализуется как `headman` (не `isHeadman`)
- **Где:** `User.java:83` (boolean с именем `isHeadman`), `UserResponse.java:23` — `private boolean headman`, геттер `isHeadman()` → JSON-key `headman`.
- **Что:** в клиенте (`fetch('/users/123').json().headman`) это может путать. Request DTO `PatchUserRequest.isHeadman` (`PatchUserRequest.java:21`) имеет camelCase `isHeadman` — несовместимо с Response. Jackson может нормально читать с `is_headman` / `headman` / `isHeadman` в зависимости от `ACCEPT_CASE_INSENSITIVE_PROPERTIES`, но контракт лучше явно зафиксировать.
- **Как чинить:** добавить `@JsonProperty("is_headman")` на геттер/сеттер или переименовать поле в DTO.

### P2-3: Redis cache использует `NON_FINAL` default typing — риск десериализации произвольных классов
- **Где:** `CacheConfig.java:73-76` — `activateDefaultTyping(LaissezFaireSubTypeValidator.instance, NON_FINAL, PROPERTY)`.
- **Что:** `LaissezFaireSubTypeValidator.instance` разрешает десериализацию любого класса. Если злоумышленник получит write-доступ к Redis (shared instance с другими сервисами), он может подложить в cache-key JSON с `@class`, который при десериализации Jackson инстанциирует произвольный класс (Jackson-gadget, CVE-2019-12086 классика). По коду Redis общий (`host: redis`), пароль защищён, но защита от insider-атак нулевая.
- **Риск:** RCE при компрометации Redis.
- **Как чинить:** заменить `LaissezFaireSubTypeValidator` на `BasicPolymorphicTypeValidator` с whitelist конкретных классов (`User`, `Group`, `Semester`, `CampusSetting` и вложенные).

### P2-4: Cache `users` — key only `#userId`, отсутствует namespace сервиса
- **Где:** `AcademicReadService.java:64-67`; `CacheConfig.java:88-92`.
- **Что:** cache-names `groups`, `users`, `group_members`, `active_semester`, `campus_geofence` — без префикса `academic:`. Если attendance/schedule сервисы тоже кэшируют `users:123`, произойдёт коллизия.
- **Как чинить:** `RedisCacheManager.builder().cacheDefaults(base.prefixCacheNameWith("academic:"))` или переименовать cache-names.

### P2-5: `Semester.firstWeekType` — поле `String`, а не enum `WeekType`
- **Где:** `Semester.java:40-42` (`private String firstWeekType = "odd"`).
- **Что:** V6 миграция создаёт ENUM-тип `week_type`, но entity хранит его как строку, а не как enum. Это нарушает требование CLAUDE.md «enum в Java UPPER_CASE». Ошибки опечаток в коде не ловятся компилятором (`setFirstWeekType("ODD")` vs `"odd"` — работает только `lowercase` из-за неявного каста).
- **Как чинить:** ввести enum `WeekType { ODD, EVEN }` в контракте, `@Convert(LowercaseEnumConverter)` на поле.

### P2-6: `HeadmanAssistant.permissions` — `String[]` вместо `List<AssistantPermission>`
- **Где:** `HeadmanAssistant.java:28-31`; миграция V1 создаёт `varchar(64)[]`.
- **Что:** enum'ы проекта типизированы (`AssistantPermission`), но в entity хранится `String[]`. `AssistantService.assignAssistant` делает `p.name().toLowerCase()` → `String[]`. Ошибки (неизвестное имя permission) не обнаруживаются на уровне JPA. Проверка в `HomeworkService.requireHeadmanOrManageHomework` — `permissions.contains("manage_homework")` с magic-string.
- **Как чинить:** `@JdbcTypeCode(SqlTypes.ARRAY) @Convert(converter=AssistantPermissionArrayConverter.class) List<AssistantPermission> permissions;`. Удалить magic-string в `HomeworkService`.

### P2-7: `ThresholdService.setGlobalThreshold` без `@RequireRole`-проверки на уровне сервиса
- **Где:** `ThresholdService.java:36-42` — комментарий «ADMIN only — role check in controller via @RequireRole», но если сервис вызовут изнутри (gRPC, job, тест) — проверки нет.
- **Что:** правило CLAUDE.md фактически не нарушено (RoleCheck в контроллере — admin), но layer-boundary зыбкий: сервис полагается на caller'а.
- **Как чинить:** дополнительно проверять роль в сервисе — или explicit выделить `AdminThresholdService` с явной сиг-ограничением.

### P2-8: `Semester.createdAt` — не имеет `@PrePersist`, может быть null при save
- **Где:** `Semester.java:36-38` — `@Column(nullable = false, updatable = false)`; `SemesterService.createSemester` (`:57`) явно ставит `semester.setCreatedAt(OffsetDateTime.now())`. Всё работает, но нарушается DRY: у `Subject`, `HomeworkCompletion`, `Homework`, `HeadmanAssistant`, `AttendanceThreshold` — есть `@PrePersist`; у `Semester`, `Group`, `StudentGroupHistory` — нет.
- **Как чинить:** добавить `@PrePersist onCreate()` ко всем entity с `created_at`. Убрать ручной `setCreatedAt(now())` из сервисов.

### P2-9: `SubjectRepository.findByNameContainingIgnoreCase` — unused
- **Где:** `SubjectRepository.java:14`.
- **Что:** метод декларирован, но не вызывается нигде. Мёртвый код.

### P2-10: `CampusSettingRepository` комментарий `findAll().get(0)` — нигде не используется; вместо этого везде `findById(1L)`
- **Где:** `CampusSettingRepository.java:6-8`; `AcademicReadService.fetchCampusGeofence()` (`:58-62`) — `findById(1L)` hardcoded.
- **Что:** single-row table, но id может не быть 1, если кто-то запустит admin-panel-create (впрочем, контроллера для campus-settings нет в принципе — таблица только-read). Это технический долг «single-row table implemented as regular table».
- **Как чинить:** либо (а) ввести PRIMARY KEY проверку `CHECK (id = 1)` через миграцию, либо (б) использовать `@Id @Column(name="key") String key = "default"` с `@Entity` как key-value store, либо (в) вынести в `application.yml` как property. Последнее — проще всего, т.к. координаты кампуса не меняются часто.

### P2-11: `campus_settings` таблица — единственная возможность обновить координаты через `UPDATE` (V14) — нет контроллера
- **Где:** V14 `UPDATE campus_settings SET lat=... WHERE name='Main Campus' AND lat=55.7699`.
- **Что:** нет REST endpoint для смены координат — каждая правка = новая миграция. Для smoke в проде — неудобно. Админу нужно логинить базу и делать `UPDATE`.
- **Как чинить:** добавить `PUT /academic/campus-settings` (ADMIN only) — обновляет строку `id=1`. Кэш `campus_geofence` эвиктить при этом.

### P2-12: `GroupNameParser` regex жёстко не допускает цифры в префиксе; допускает произвольную кириллицу во 2-м символе
- **Где:** `GroupNameParser.java:26` — `^([А-ЯЁ][А-ЯЁа-яё]{1,3})-(\d)(\d)(\d)$`.
- **Что:** prefix `УИТ` / `УВПв` / `БИ` — ок. Но `АА` проходит, `АБВГД` тоже может, `Ёёёё` тоже. Логически префикс — 2-4 кириллические буквы, но реестр допустимых — не задокументирован.
- **Как чинить:** либо принять как есть (любой формат кириллица), либо whitelist префиксов в `ProgramType`.

### P2-13: `EnumConverters.AssistantPermissionConverter` объявлен, но entity использует `String[]` — конвертер не включается
- **Где:** `EnumConverters.java:30-33`, `HeadmanAssistant.java:28-31`.
- **Что:** конвертер есть, но entity хранит String[] через `@JdbcTypeCode(ARRAY)` — autoApply не трогает массивы enum'ов. Конвертер — мёртвый.
- **Как чинить:** см. P2-6.

### P2-14: `UserService.patchUser` — evict кэша `groups` по `user.getGroupId()` только когда `isHeadman` установлен
- **Где:** `UserService.java:225-234`.
- **Что:** эвикт `groups`/`group_members` для `user.getGroupId()` — но если админ через PATCH изменил `groupId` + `isHeadman=false`, старая группа не инвалидируется. Плюс если `groupId` был null, эвикт молча пропускается.
- **Как чинить:** вести `oldGroupId` и эвиктить обе старую и новую группы, как в `transferStudent`.

### P2-15: `GroupPromotionService.execute` — каждое переименование публикует `GroupRenamedEvent` внутри одной транзакции
- **Где:** `GroupPromotionService.java:205-211`.
- **Что:** массовый промоушен (например 100 групп) → 100 ApplicationEvent → 100 AMQP send после commit. Если одно send упадёт — остальные 99 уже отправлены, откатить нельзя. Плюс `save(group)` тут не вызывается (dirty checking), порядок JPA flush может перепутать публикацию с реальным сохранением.
- **Как чинить:** (а) outbox pattern (P0-6); (б) агрегировать в один `GroupPromotionCompletedEvent(ids, newNames)` вместо N event'ов.

---

## Мелкие и nit (P3)

### P3-1: `AcademicApplication.java:1-11` — нет `@EnableJpaRepositories`, `@EnableTransactionManagement`, `@EnableAsync` явно
- **Где:** `AcademicApplication.java`.
- **Что:** `@SpringBootApplication` подтягивает их auto-config'ами. OK, но не явно — ни одна из аннотаций не показывает, что именно включено.

### P3-2: `Subject.type` — нет `@Convert` / `@Enumerated`
- **Где:** `Subject.java:25-26` — `@Column(nullable = false) private SubjectType type;`.
- **Что:** надеется на `SubjectTypeConverter(autoApply=true)`. Работает, но явный `@Convert` был бы надёжнее.

### P3-3: `Homework.lessonDate` / `lessonNumber` — без `@Setter`
- **Где:** `Homework.java:31-36`.
- **Что:** намеренно (Phase 61 D-05: не меняем привязку после создания). Однако в `Homework` конструкторе эти поля выставляются только через constructor. OK, но стоит явно задокументировать.

### P3-4: `UserRepository.existsByLogin` — native query, хотя можно было бы Spring Data `existsByLogin`
- **Где:** `UserRepository.java:28-38`.
- **Что:** native query для обхода `@SQLRestriction`. Комментарий объясняет. ОК, но native-query всё-таки более уязвим к инъекциям при рефакторе (хотя параметры биндятся правильно).

### P3-5: `UserService.updateUser` — `@CacheEvict` по `#id`, но не по `groupId` — устаревает кэш group_members
- **Где:** `UserService.java:168-181`.
- **Что:** PUT меняет `groupId`, `role` — но `group_members` кэш не инвалидируется.

### P3-6: `UserService.transferStudent` — `@CacheEvict` по `#request.newGroupId()`, но не по старой группе через SpEL
- **Где:** `UserService.java:249-302`.
- **Что:** старая группа инвалидируется программно (`cacheManager.getCache(...).evict(oldGroupId)`), но можно было это через `@Caching(evict=[...])` с двумя ключами. Сейчас гибрид.

### P3-7: `ThresholdService.resolveThreshold` — `@Transactional(readOnly=true)` делает до 3 запросов подряд, не один join
- **Где:** `ThresholdService.java:77-105`.
- **Что:** при наличии subject-threshold — 1 запрос; иначе ещё 2. Можно было один `ORDER BY specificity DESC LIMIT 1`.

### P3-8: `AssignmentService.listAssignments` — full-load → subList
- **Где:** `AssignmentService.java:74-81`.
- **Что:** повторяет P0-7 в меньшем масштабе. Не в P0 из-за сравнительно небольшого N (assignments в группе x семестре обычно <30).

### P3-9: `DashboardStatsResponse` — setXxx/getXxx, mutable DTO
- **Где:** `DashboardService.java:44-48` → `response.setTotalStudents(...)` и т.д.
- **Что:** mutable, но наследует `RepresentationModel`. Не критично.

### P3-10: `V14__update_campus_coordinates.sql` — патчит V2, но V2 уже обновлён
- **Где:** V2 после исправления содержит правильные координаты; V14 обновляет только старые записи.
- **Что:** на fresh install — noop. OK, но создаёт confusion.

### P3-11: `Homework.setTitle` разрешён после создания, но `subjectId`, `groupId`, `semesterId` — нет
- **Где:** `Homework.java:21-36` — `@Setter` только на `title/description/link/updatedAt`.
- **Что:** хорошо, но неочевидно. Стоит задокументировать: «subject/group/semester immutable после create (Phase 61 D-05)».

---

## Мёртвый код

- **`SubjectRepository.findByNameContainingIgnoreCase`** — не вызывается. См. P2-9.
- **`HomeworkCompletionRepository.findByStudentId`** — нет вызовов (проверял `HomeworkService`, `HomeworkController`).
- **`HomeworkRepository.findByGroupIdAndSubjectIdAndSemesterId(Long,Long,Long,Pageable)`** — существует, но в сервисе используется только `findByGroupIdAndSemesterId` (см. P0-7).
- **`UserRepository.findByGroupIdAndRole(... Pageable)`** — нет вызовов.
- **`UserRepository.findAllArchived()`** — нет вызовов.
- **`UserRepository.existsByEmail`** — нет вызовов (в `CreateUserRequest` нет поля email).
- **`User.email`**, **`User.phone`** — entity-поля без использования в сервисе; DTO и миграции не содержат запроса `email/phone` в POST create. V1 имеет колонки — но форма создания пользователя их не заполняет.
- **`User.telegramUsername`** — хранится, нигде не используется в Academic-service (может использоваться gRPC-consumer'ами).
- **`StudentGroupHistory.reason`** — используется только при transfer, never read back. Возможно назначение — история ради истории.
- **Таблица `password_reset_tokens`** — не создаётся в миграциях academic (это auth, см. P0-3 в 01-auth-service.md).
- **Field `User.employeeNumber`** — используется только для TEACHER (при создании и `findByEmployeeNumber` в `AssignmentService.assignTeacher`), но модель разрешает STUDENT иметь emp_number (в БД unique). Подозрительная универсальность.
- **`ConflictException(String message)` legacy-конструктор** — используется только в `AssistantService`, `AssignmentService`, `SubjectService` (без field info). Можно пройтись и добавить field всюду.

---

## Костыли и TODO/FIXME

- `V11__enum_equality_operators.sql` — CREATE OPERATOR `user_role = text`: обход фундаментального ограничения Hibernate 6 / PostgreSQL enum. Костыль, но задокументированный. Альтернатива — Hibernate custom type или JPA `@Convert` с casting, что Spring-команда не поддерживает.
- `V5__add_enum_casts.sql` — `CREATE CAST (varchar AS user_role) WITH INOUT AS IMPLICIT` — тоже обход для INSERT/UPDATE.
- `V12__subjects_group_id.sql` содержит `DELETE FROM teacher_subject_groups; DELETE FROM subjects;` — разрушающая миграция, которая предполагает, что данные не использовались. Для production-upgrade с живыми данными — будет потеря. Комментарий это признаёт.
- `V13__homework_lesson_binding.sql` — `TRUNCATE homeworks, homework_completions RESTART IDENTITY CASCADE`. Аналогично V12.
- `V14__update_campus_coordinates.sql` — патчит ошибочные координаты. Создаёт зависимость «апдейт координат = миграция», неудобно.
- `AcademicGrpcServiceImpl.java:246-252` — комментарий «BUG: initial_password нужен боту для одноразовой выдачи в /start.» — фактический known-bad practice, осознанно документирован, но не исправлен.
- `HomeworkService.requireHeadmanOrManageHomework` (`:63-83`) — комментарий о том, что ADMIN явно запрещён по Phase 61 D-06, но fallback для assistant оставлен «ломает Phase 52 функционал» — технический долг между фазами.
- `RabbitConfig.java:36-40` — комментарий «Do NOT set channelTransacted=true — causes message loss with AFTER_COMMIT» — подтверждает P0-6 как известную проблему.
- Нет `// TODO` / `// FIXME` в коде. Это плюс — предыдущие «todo» снесены, но также значит, что часть known-issues (как gRPC initial_password выше) остаётся без tracker-метки.

---

## Тесты

Тесты делятся на:
- **Unit (Mockito, без Spring):** `RoleCheckAspectTest`, `GroupServiceTest`, `GroupPromotionServiceTest`, `GroupArchivalServiceTest`, `GroupNameParserTest`, `HomeworkServiceTest`, `SemesterServiceTest`, `UserServiceConflictTest`, `UserServiceListTest`, `UserServiceTelegramRequiredTest`, `GlobalExceptionHandlerTest`.
- **Integration (Testcontainers PostgreSQL + MockMvc/Spring context):** `RestApiIntegrationTest`, `EventIntegrationTest`, `GroupRenameEventTest`, `CacheIntegrationTest`, `AcademicGrpcIntegrationTest`, `ActuatorIT`, `EntityMappingIntegrationTest`, `HomeworkControllerIT`, `HomeworkMigrationIT`, `SubjectServiceIT`, `SubjectSchemaIT`, `UserSearchIntegrationTest`, `UserRepositorySearchTest`.

### Что покрыто хорошо

- **Role-aspect в изоляции** — `RoleCheckAspectTest` (7 тестов): headman-bypass для STUDENT, блокировка headman для ADMIN, nullRole, каждая роль vs свою аннотацию.
- **User pre-check conflicts** — `UserServiceConflictTest`: telegramId уже существует → 409 с field="telegramId"; login collision; employeeNumber collision.
- **Telegram required for STUDENT** — `UserServiceTelegramRequiredTest`: BUG-006-3 покрыт (STUDENT без telegramId → BadRequestException c field).
- **Semester validation** — `SemesterServiceTest`: dateFrom<today → 400; overlap → 409 dates; dateTo<today на update → 409 status.
- **Group promotion algorithm** — `GroupPromotionServiceTest` подробный с fixed clock: переименование, архивация maxCourse, name_conflict, unknown_type. `GroupArchivalServiceTest` и `GroupNameParserTest` — пограничные случаи парсинга.
- **Homework validation** — `HomeworkServiceTest`: lessonDate в прошлом → 400; wrong subject → 400; non-headman → 403; non-author update/delete → 403.
- **Cache hits/evictions** — `CacheIntegrationTest` с реальным Redis-контейнером проверяет, что второй gRPC call серверится из cache, и что mutation сбрасывает кэш.
- **Event pipeline** — `EventIntegrationTest`, `GroupRenameEventTest`: реальный RabbitMQ container, queue-binding на fanout, проверка получения message.
- **gRPC endpoints** — `AcademicGrpcIntegrationTest` покрывает все 9 RPC (getGroup/getGroupMembers/getTeacherSubjects/isHeadman/getActiveSemester/getCampusGeofence/getSubjectsByIds/getUserByTelegramId/getUserById).
- **User search ILIKE** — `UserSearchIntegrationTest`: поиск по ФИО, case-insensitive, многоязычный.
- **Actuator exposure** — `ActuatorIT`: `/health`, `/info` доступны; `/env`, `/beans`, `/heapdump` — 404.
- **REST admin happy path** — `RestApiIntegrationTest`: создать пользователя, PatchUser, TransferStudent, Threshold-set-and-resolve, semester activate, homework CRUD.

### Что покрыто плохо / не покрыто

- **`UserContextFilter` без Spring Security** — нет теста, что запрос без `X-User-Id` отклоняется. Сейчас фильтр просто не выставит RequestContext → `@RequireRole` упадёт на null → 403. Но отсутствие самого заголовка не тестируется (P0-2, P0-3).
- **Некорректный `X-User-Id`** (NumberFormatException) — нет теста (P0-3).
- **`SemesterService.activateSemester` под concurrency** — нет. Проверка race condition (P0-4).
- **`deleteSemester` активного** — нет теста.
- **`GroupService.deleteGroup` с FK-констрейнтами** — нет (удаление группы с students → SET NULL, удаление с history → RESTRICT).
- **`HomeworkService.markComplete` дважды** — ConflictException при повторном mark. Есть (в `HomeworkServiceTest`)? Нет, там только валидация creation. IT тоже не покрывает.
- **IDOR в `GET /homework/{id}` между группами** — нет.
- **`HomeworkController.listHomeworks` N+1** — не протестирован, только happy-path.
- **`SubjectService.deleteSubject` force=true старостой** — не тестируется, что WARN-лог появится (т.к. лога нет).
- **Rate-limit / DoS на большие listHomeworks** — не тестируется.
- **gRPC без секрета** — нет теста, что `GrpcAuthInterceptor` отказывает без `x-grpc-secret`. Только happy path.
- **Cache-invalidation edge-cases** — когда пользователь меняет группу, кэш старой группы инвалидируется? Нет.
- **`AssistantPermission` array round-trip через JPA** — нет unit-теста, только косвенно в IT.
- **`LowercaseEnumConverter` для неизвестного значения в БД** — нет теста на `IllegalArgumentException`.
- **`GlobalExceptionHandler.handleGeneral`** — нет теста, что для NPE возвращается 500 без `ex.getMessage()` (сейчас утекает).
- **`ScheduleGrpcClient` при UNAVAILABLE** — нет теста, что bubble-up даёт 503 (есть mock, но без network errors).
- **`DashboardService` с разными ролями** — только happy-path admin.
- **`transferStudent` для non-student пользователя** — есть BadRequestException check? Нужно проверить IT.
- **`UserSpecifications.matchesSearch` для telegramId** — под вопросом, работает ли вообще (см. P1-12).
- **`GroupPromotionService.execute` при 100+ групп** — не тестируется производительность.

### Некорректные / подозрительные тесты

- `EventIntegrationTest` использует `@MockitoBean RequestContext` — это верно для event-flow, но маскирует проблему P1-13 (сервис должен работать без HTTP-контекста).
- `AcademicGrpcIntegrationTest` использует `grpc.server.in-process-name=...` — тест идёт в обход interceptor? Проверил: `GrpcAuthInterceptor` — `@GrpcGlobalServerInterceptor`, регистрируется на любом gRPC сервере включая in-process. Но `grpc.auth.secret` в тестовом профиле не задан → `expectedSecret == null || isBlank` → auto-skip. Т.е. тесты gRPC проходят БЕЗ проверки секрета. Это подтверждает P0-5: тесты не могут поймать отключённую аутентификацию.
- `CacheIntegrationTest` использует `@DirtiesContext(classMode=AFTER_CLASS)` — это правильно, но тесты-классы БД не изолированы (все шарят Postgres container).
- `RestApiIntegrationTest.testAdminCreateUser_returnsLoginAndPassword` — **тестирует наличие `initialPassword` в ответе** (`jsonPath("$.initialPassword", notNullValue())`). Это закрепляет P0-1 как требование, а должно быть наоборот — тест должен запрещать плейнтекст.
- `HomeworkControllerIT` — seed семестр на `'2041-02-01'` (далёкое будущее, избегает exclusion-constraint с активным семестром). Нестандартно, но работает.
- `GroupServiceTest` использует Mockito `@Spy` на `GroupNameParser` — реальный парсер, чтобы тест не мокировал чистую логику. Правильно.
- `SubjectServiceIT` — `deleteSubject` вероятно с `force=false` + реальный mock schedule-service. Проверка gRPC пути — ок.

### Кандидаты на удаление/рефакторинг

- `UserServiceListTest` и `UserSearchIntegrationTest` — частично пересекаются. Первый — unit, второй — integration. Можно оставить оба, но явно разделить цели.
- `HomeworkMigrationIT` — проверяет V13? Если да, это полезно. Если просто smoke — лишнее.
- `SubjectSchemaIT` vs `SubjectServiceIT` — 2 файла. Schema-test должен быть минимальным, не дублировать сервис.
- `EntityMappingIntegrationTest` — после миграций уже гоняется `ddl-auto: validate`, entity-mapping проверяется неявно. Можно упростить.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|:------:|-------------|
| Contract-first (`*-api-contract` + `*-app`) | ✅ | Разбиение корректное; контроллеры `implements XxxApi`. |
| Request DTO = record | ✅ | Все request DTO — record. |
| Response DTO = class extends RepresentationModel | ✅ | `UserResponse`, `GroupResponse`, `HomeworkResponse`, `SemesterResponse`, `SubjectResponse`, `ThresholdResponse`, `ResolvedThresholdResponse`, `DashboardStatsResponse`, `UserCreatedResponse` — все классы. |
| Lombok запрещён в `*-api-contract` | ✅ | Контракт чист от Lombok. |
| Lombok разрешён в `*-app` entity | ✅ | Используется `@Getter @Setter @NoArgsConstructor`. |
| Java enum UPPER_CASE / PG lowercase / LowercaseEnumConverter autoApply | ✅ | `UserRole.ADMIN` ↔ `'admin'`; `EnumConverters` с autoApply. |
| Не `@Enumerated(ORDINAL)` | ✅ | Не используется. Но `Semester.firstWeekType` — `String`, не enum (P2-5). |
| Soft delete (status=archived) | ⚠ | Для `User` — через `@SQLRestriction`. Для `Group` — частично (`GroupArchivalService` делает soft, но `GroupService.deleteGroup` делает hard, см. P1-1). Для `Semester`/`Subject`/`Homework` — hard delete (P1-2). |
| TIMESTAMPTZ (UTC) | ✅ | Везде `TIMESTAMPTZ`. Но `ClockConfig` = Europe/Moscow — допустимо. |
| RFC 7807 Problem Details | ✅ | `GlobalExceptionHandler` + `ErrorResponse`. |
| HATEOAS Level 3 (`_links`, EntityModel, PagedModel) | ✅ | Используется в Assembler-ах, `PagedResourcesAssembler` на листах. |
| Swagger аннотации в интерфейсе контракта | ✅ | `@Operation`, `@ApiResponse` в `XxxApi`. |
| `@ControllerAdvice` вместо catch в контроллере | ✅ | Контроллеры только throw. |
| Маппинги в интерфейсе, НЕ в контроллере | ✅ | `@RequestMapping("/academic/...")` в интерфейсах, контроллеры без. |
| PUT = full, PATCH = partial | ✅ | `updateUser` (PUT), `patchUser` (PATCH) существуют отдельно. |
| Именование пакетов `ru.rutcampustrack.{service}.{module}` | ✅ | `ru.rutcampustrack.academic.*`. |
| REST пути `/api/{service}/...` через Gateway | ⚠ | Контроллеры отдают `/academic/...` (без `/api/`); Gateway проксирует `/api/academic/` → `/academic/`. Принято. |
| gRPC `ru.rutcampustrack.{service}.grpc` | ✅ | Proto генерируется в `ru.rutcampustrack.academic.grpc`. |
| Event types `{domain}.{action}` | ✅ | `group.archived`, `group.renamed`, `group.updated`, `homework.published`, `homework.updated`, `semester.archived`, `subject.deleted`. |
| Миграции Flyway в `src/main/resources/db/migration/V{N}__...` | ✅ | V1..V14 корректные. |
| `ddl-auto: validate` | ✅ | Всегда. |
| Никогда не редактировать применённые миграции | ⚠ | V12, V13, V14 — новые. V2 ранее правилась (по V14 комментарию: «V2 seed shipped approximate coordinates (55.7699, 37.7039)») — возможно редактировался уже применённый V2 файл. Это нарушение user MEMORY.md «Never edit applied Flyway migrations». Нужно проверить историю git log. |
| `report/` изолирован от `checkin/` | — | Не применимо к academic. |

---

## Зависимости между проблемами

- **P0-1 (plaintext пароль в ответе)** зависит от **P0-2 auth-service (колонка initial_password)**. Закрытие auth-P0-2 автоматически закрывает academic-P0-1.
- **P0-2 (ContextFilter trust)** блокирует production deployment за периметром WAF/VPN — без mTLS или HMAC любая lateral attack пробивает защиту.
- **P0-3 (NumberFormatException)** — должна исправляться в связке с P0-2 (отказ при невалидном заголовке = 401 Unauthorized, а не 500).
- **P0-4 (activateSemester race)** независим, но его следствия каскадно роняют всю систему (schedule/attendance/notification).
- **P0-5 (gRPC secret auto-skip)** критичен: позволяет external-caller читать `users` (вместе с P0-1 — полный compromise).
- **P0-6 (события без outbox)** — cross-cutting: затрагивает все сервисы. Нужно решение на уровне платформы (новый `shared/outbox` модуль).
- **P0-7 (N+1 в listHomeworks)** — производительность. Не безопасность, но существенно влияет на UX при росте данных.
- **P1-1 / P1-2 (hard deletes)** — audit/history concern, решаются вместе с soft-delete паттерном.
- **P1-3 (AOP auto-config)** + **P1-4 (смешанные роли)** — security-layer concern. Решение — Spring Security + `@PreAuthorize`.
- **P1-13 (RequestContext в сервисе)** — архитектурный долг, решается пробрасыванием caller-context через параметры.
- **P2-3 (Redis NON_FINAL typing)** — зависит от того, изолирован ли Redis; если да — P3, если нет — P1.

---

## Вопросы к владельцу проекта

1. ✅ **`initial_password` в `UserResponse` и gRPC**: это compliance-gap или намерение «старая почта»? Есть ли причина не отдавать одноразовый токен (P0-1 / auth P0-2)?
   → **ACCEPTED BY OWNER (2026-04-18)**: оба канала остаются как есть. См. `OWNER-ANSWERS.md` 02-Q1.
2. ✅ **`X-User-Id` trust model**: когда планируется ввести mTLS / signed headers? Гарантирована ли сейчас изоляция service-network от внешнего трафика в проде (P0-2)?
   → **ACCEPTED BY OWNER (2026-04-18)**: выбран **Internal JWT (Уровень 2 Zero Trust)** — RSA-подписанный короткий JWT от Gateway. См. `OWNER-ANSWERS.md` 02-Q2.
3. ✅ **Outbox events**: готовы ли принять допущение about message loss, или внедряем outbox-таблицу вскоре (P0-6)?
   → **ACCEPTED BY OWNER (2026-04-18)**: выбран **(b) In-app outbox table** — таблица `{service}_outbox` + `@Scheduled` publisher-job каждые ~5 сек. См. `OWNER-ANSWERS.md` 02-Q3.
4. **Hard delete групп/семестров/предметов**: какова политика хранения истории? Audit-requirement есть?
5. **gRPC secret**: где хранится, как ротируется, есть ли dev-profile с принудительной проверкой?
6. **Активация семестра**: как часто меняется активный семестр в проде (раз в год?) и нужна ли защита от concurrent-admin-кликов?
7. **Миграция V2 / V14**: редактировали ли V2 после applying в staging? Если да, нужна политика «add V{N+1} patch вместо правки».
8. **`SubjectType`, `AssistantPermission` как enum в entity**: готовы ли мигрировать на type-safe хранение?
9. **`RequestContext` в сервисах**: планируется ли рефактор на принятие `AuthContext` параметром?
10. **Тестовые аккаунты `student`/`teacher`/`admin` с паролем `password` (V2)**: как проверяется их отсутствие в проде?
11. **`campus_settings` update**: стоит ли вынести в config (application-prod.yml), чтобы не делать миграции на каждое изменение координат?
12. **Dashboard доступ**: `GET /academic/dashboard/stats` — только admin. Должен ли быть teacher-специфичный dashboard?

---

_Конец отчёта 02-academic-service.md_
