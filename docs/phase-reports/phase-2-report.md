# RutCampusTrack — Отчёт Фазы 2: Academic Service

## Дата: Март 2026

## Цель фазы

Полноценный CRUD структуры вуза: пользователи, группы, семестры, предметы, привязки преподавателей, помощники старосты, домашние задания, пороги красной зоны. gRPC-сервер для внутренних вызовов. Redis-кэширование hot-path'ов. RabbitMQ-события при мутациях.

---

## Что реализовано

### Подфаза 5: Entity and Repository Foundation

**Цель:** JPA-сущности соответствуют Flyway-схеме, репозитории готовы к запросам.

- **11 JPA-сущностей**: Group, User, Semester, Subject, TeacherSubjectGroup, HeadmanAssistant, AttendanceThreshold, Homework, HomeworkCompletion, CampusSetting, StudentGroupHistory
- **7 Spring Data репозиториев** с кастомными запросами
- **Flyway V3**: PostgreSQL sequences для автогенерации логинов (`student_login_seq`, `teacher_login_seq`)
- **Flyway V4**: Фикс `campus_settings.id` SERIAL→BIGINT для совместимости с Long в Java
- **Soft delete**: `@SQLRestriction("status <> 'archived'")` на User — архивированные пользователи исключаются из запросов автоматически
- **Без JPA-ассоциаций**: FK-колонки как Long IDs — предотвращение N+1 и cascade-проблем
- **7 интеграционных тестов** (Testcontainers PostgreSQL) — все 11 сущностей валидируются против live-схемы

### Подфаза 6: REST API + HATEOAS

**Цель:** Каждая роль может выполнять свои операции через REST с HATEOAS, пагинацией и RFC 7807 ошибками.

- **Contract-first**: 7 API-интерфейсов в `academic-api-contract` (UserApi, GroupApi, SemesterApi, SubjectApi, AssignmentApi, HomeworkApi, ThresholdApi)
- **DTO**: Request = Java record, Response = класс с `RepresentationModel` для HATEOAS
- **Авторизация**: `@RequireRole` AOP аспект — проверка роли через `X-User-Role` заголовок от Gateway
- **RequestContext**: `@RequestScope` bean, извлекает `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`
- **GlobalExceptionHandler**: `@ControllerAdvice` с RFC 7807 Problem Details

**Endpoints по ролям:**

| Роль | Операции |
|------|----------|
| ADMIN | CRUD users (автогенерация логинов), CRUD groups, CRUD semesters (с подтверждением удаления), назначение/снятие старосты, перевод студентов, dashboard |
| HEADMAN | CRUD subjects, назначение преподавателей, управление помощниками, CRUD homework, настройка порогов |
| STUDENT | Профиль, состав группы, домашние задания, трекер выполнения ДЗ |
| TEACHER | Свои предметы и группы |

- **HATEOAS Level 3**: `EntityModel<T>` с `_links.self`, `PagedModel<EntityModel<T>>` с пагинацией
- **24 интеграционных теста** (Testcontainers PostgreSQL) — полное покрытие REST endpoints
- **Flyway V5**: implicit casts для PostgreSQL enum-колонок

### Подфаза 7: gRPC Server

**Порт:** 19091 | **Proto:** `academic.proto`

- **7 RPC**: GetGroup, GetGroupMembers, GetTeacherSubjects, IsHeadman, GetActiveSemester, GetCampusGeofence, GetUserById
- **AcademicGrpcServiceImpl**: инжектит репозитории напрямую (не REST-сервисы) — обход проблем с RequestContext scope в gRPC-потоках
- **GrpcExceptionAdvice**: маппинг исключений в gRPC Status коды
- **grpc-spring-boot-starter** с protobuf Gradle плагином
- **7 интеграционных тестов** (Testcontainers PostgreSQL, in-process gRPC server)

### Подфаза 8: Redis Caching

**5 кэшируемых путей** через `@Cacheable`:

| Кэш-ключ | Метод | TTL (по умолчанию) |
|-----------|-------|--------------------|
| `groups` | GetGroup | 30 мин |
| `group_members` | GetGroupMembers | 30 мин |
| `active_semester` | GetActiveSemester | 1 час |
| `campus_geofence` | GetCampusGeofence | 1 час |
| `users` | GetUserById | 30 мин |

- **AcademicReadService**: отдельный `@Service` bean — обход self-invocation AOP proxy
- **Каскадная инвалидация**: перевод студента инвалидирует кэш обеих групп; смена старосты инвалидирует group + group_members
- **CacheConfig**: `GenericJackson2JsonRedisSerializer` с NON_FINAL default typing, программная конфигурация TTL через `ObjectProvider<RedisConnectionFactory>`
- **10 интеграционных тестов** (Testcontainers PostgreSQL + Redis)

### Подфаза 9: RabbitMQ Events

**Exchange:** `rut-uit.events` (fanout, durable)

| Событие | Триггер | Payload |
|---------|---------|---------|
| `group.updated` | updateGroup, deleteGroup, transferStudent | `{group_id}` |
| `semester.archived` | activateSemester (деактивация предыдущего) | `{semester_id}` |
| `homework.published` | createHomework | `{homework_id, group_id, subject_id, title, has_link}` |
| `homework.updated` | updateHomework | `{homework_id, group_id, title}` |

- **DomainEvent**: абстрактный класс, extends `ApplicationEvent`, envelope: `event_type`, `event_id` (UUID), `occurred_at`, `payload` (вложенный объект)
- **@TransactionalEventListener(AFTER_COMMIT)**: гарантия — нет событий при rollback
- **Сервисы инжектят `ApplicationEventPublisher`** — zero AMQP imports в service layer
- **Jackson2JsonMessageConverter** с shared Spring ObjectMapper (без @class полей)
- **3 JSON Schema** в `event-schemas/`: group.updated, semester.archived, homework.updated
- **6 интеграционных тестов** (Testcontainers PostgreSQL + RabbitMQ)

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| Без JPA-ассоциаций (@ManyToOne и т.д.) | FK как Long IDs — предотвращение N+1, cascade, lazy loading проблем |
| Contract-first (api-contract + app модули) | Swagger/OpenAPI в интерфейсах контракта, контроллер только implements |
| @RequireRole AOP вместо Spring Security | Проще — Gateway уже валидирует JWT, сервис только проверяет роль |
| gRPC запрашивает репозитории напрямую | Обход проблем RequestContext scope в gRPC-потоках |
| AcademicReadService как отдельный @Service | @Cacheable через AOP proxy — self-invocation не работает |
| ObjectProvider<RedisConnectionFactory> | Обход timing bug: user @Configuration оценивается до Redis autoconfiguration |
| @TransactionalEventListener(AFTER_COMMIT) + non-transacted RabbitTemplate | channelTransacted=true вызывает потерю сообщений с AFTER_COMMIT |
| @MockitoBean RabbitTemplate в тестовых базах | DomainEventListener не ломает тесты без RabbitMQ |
| Flyway V5 implicit casts | JPA шлёт varchar, PostgreSQL требует CAST для custom enum колонок |

---

## Файловая структура

```
services/academic-service/
├── academic-api-contract/
│   └── src/main/java/ru/rutcampustrack/academic/contract/
│       ├── api/              ← 7 API-интерфейсов (UserApi, GroupApi, ...)
│       ├── dto/              ← Request (record) + Response (HATEOAS)
│       ├── enums/            ← UserRole, UserStatus, SubjectType, ...
│       └── exception/        ← ResourceNotFoundException, ErrorResponse
└── academic-app/
    └── src/
        ├── main/java/ru/rutcampustrack/academic/
        │   ├── config/       ← SecurityConfig, CacheConfig, SwaggerConfig
        │   ├── entity/       ← 11 JPA-сущностей
        │   ├── repository/   ← 7 Spring Data репозиториев
        │   ├── event/        ← DomainEvent, 4 concrete events, RabbitConfig, DomainEventListener
        │   ├── grpc/         ← AcademicGrpcServiceImpl, GrpcExceptionAdvice, AcademicReadService
        │   ├── security/     ← RequestContext, RequireRole, RoleCheckAspect
        │   ├── user/         ← UserController, UserService, UserAssembler
        │   ├── group/        ← GroupController, GroupService, GroupAssembler
        │   ├── semester/     ← SemesterController, SemesterService, SemesterAssembler
        │   ├── subject/      ← SubjectController, SubjectService, SubjectAssembler
        │   ├── assignment/   ← AssignmentController, AssignmentService, AssignmentAssembler
        │   ├── assistant/    ← AssistantController, AssistantService, AssistantAssembler
        │   ├── homework/     ← HomeworkController, HomeworkService, HomeworkAssembler
        │   ├── threshold/    ← ThresholdController, ThresholdService, ThresholdAssembler
        │   ├── dashboard/    ← DashboardController, DashboardService
        │   └── exception/    ← GlobalExceptionHandler, BadRequestException
        ├── main/resources/
        │   ├── application.yml
        │   └── db/migration/ ← V1-V5
        └── test/java/ru/rutcampustrack/academic/integration/
            ├── AbstractAcademicIntegrationTest.java       ← PostgreSQL only
            ├── AbstractAcademicCacheIntegrationTest.java   ← PostgreSQL + Redis
            ├── AbstractAcademicEventIntegrationTest.java   ← PostgreSQL + RabbitMQ
            ├── RestApiIntegrationTest.java                 ← 24 REST tests
            ├── GrpcIntegrationTest.java                    ← 7 gRPC tests
            ├── CacheIntegrationTest.java                   ← 10 cache tests
            └── EventIntegrationTest.java                   ← 6 event tests

event-schemas/
├── group.updated.json
├── semester.archived.json
├── homework.published.json
└── homework.updated.json

proto/
└── academic.proto          ← 7 RPC definitions
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| Entity + Repository | 7 | Integration | Testcontainers PostgreSQL |
| REST API (all roles) | 24 | Integration | Testcontainers PostgreSQL |
| gRPC Server (7 RPCs) | 7 | Integration | Testcontainers PostgreSQL + in-process gRPC |
| Redis Caching | 10 | Integration | Testcontainers PostgreSQL + Redis |
| RabbitMQ Events | 6 | Integration | Testcontainers PostgreSQL + RabbitMQ |
| **Итого** | **~50** | | |

Все тесты проходят: `./gradlew.bat :services:academic-service:academic-app:test`

---

## Требования (покрытие)

| Категория | ID | Статус |
|-----------|-----|--------|
| User Management | USER-01 — USER-08 | ✅ Все 8 |
| Groups & Semesters | GSEM-01 — GSEM-04 | ✅ Все 4 |
| Subjects & Assignments | SUBJ-01 — SUBJ-03 | ✅ Все 3 |
| Headman Assistants | ASST-01 — ASST-03 | ✅ Все 3 |
| Homeworks | HW-01 — HW-03 | ✅ Все 3 |
| Red Zone Thresholds | THRSH-01 — THRSH-04 | ✅ Все 4 |
| Admin Dashboard | DASH-01 | ✅ |
| gRPC Server | GRPC-01 — GRPC-07 | ✅ Все 7 |
| Redis Caching | CACHE-01 — CACHE-02 | ✅ Все 2 |
| RabbitMQ Events | EVENT-01 — EVENT-03 | ✅ Все 3 |
| **Итого** | **37/37** | **100%** |

---

## Следующая фаза

**Фаза 3: Schedule Service + Attendance Service** — расписание, автогенерация пар, геоотметка, ручная отметка, excuse-тикеты, автоматический absent, отчёты.