# RutCampusTrack — Отчёт Фазы 3: Schedule Service

## Дата: Апрель 2026

## Цель фазы

Полный жизненный цикл расписания: шаблоны (CRUD) → автогенерация пар на семестр с учётом чётности недель → cron-переходы статусов (planned→active→closed) → RabbitMQ события → gRPC сервер для Attendance Service.

---

## Что реализовано

### Подфаза 10: Foundation

**Цель:** JPA-сущности, репозитории, безопасность, тестовая инфраструктура.

- **2 JPA-сущности**: ScheduleItem (шаблон расписания), Lesson (конкретная пара)
- **2 Spring Data репозитория** с кастомными запросами
- **Flyway V1**: baseline (schedule_items + lessons), UNIQUE constraint (schedule_item_id, date)
- **Flyway V2**: implicit casts для PostgreSQL enum-колонок
- **SecurityConfig**: @RequireRole AOP, RequestContext (@RequestScope), UserContextFilter
- **GlobalExceptionHandler**: RFC 7807 Problem Details
- **ClockConfig**: `Clock.system(ZoneId.of("Europe/Moscow"))` — единый источник времени
- **SchedulingConfig**: `@Profile("!test")` — cron-задачи отключены в тестах
- **Testcontainers PostgreSQL** базовый класс + smoke tests

### Подфаза 11: REST API + gRPC Client

**Цель:** Полный REST API для шаблонов, операций с парами, просмотра расписания. gRPC-клиент к Academic Service для валидации.

- **Contract-first**: 2 API-интерфейса в `schedule-api-contract` (ScheduleItemApi, LessonApi)
- **DTO**: 3 Request records (Create/Update ScheduleItem, Cancel/MassCancel/GeoBlock) + 3 Response (ScheduleItemResponse, LessonResponse, MassCancelResponse)
- **gRPC-клиент** (`AcademicGrpcClient`): вызывает Academic Service для валидации subject/teacher/semester при создании шаблона
- **ScheduleItemService**: CRUD шаблонов с headman-авторизацией
- **LessonService**: отмена/восстановление/массовая отмена/блокировка геоотметки
- **Schedule View**: расписание группы на период с обогащённым ответом (статус, комната, преподаватель, предмет)

**Endpoints:**

| Роль | Операции |
|------|----------|
| HEADMAN | CRUD schedule_items, cancel/restore/mass-cancel lessons, toggle geo-block |
| ALL (authenticated) | GET расписание группы на период |

- **HATEOAS Level 3**: `EntityModel<T>` с `_links.self`
- **15 интеграционных тестов**: ScheduleItemApiTest (8), LessonApiTest (7), ScheduleViewTest (5)

### Подфаза 12: Lesson Auto-Generation

**Цель:** Автоматическая генерация пар на все даты семестра при создании шаблона с учётом чётности недель.

- **LessonGenerationService**: алгоритм генерации с week parity (odd/even/all), anchored к `first_week_type` семестра
- **Интеграция с ScheduleItemService**: POST создаёт шаблон и генерирует все пары; PUT обнаруживает изменения в расписании и перегенерирует будущие planned пары
- **gRPC расширение**: `GetActiveSemester` из Academic Service для получения `first_week_type`
- **Idempotency**: UNIQUE constraint (schedule_item_id, date) защищает от дублей (без ON CONFLICT DO NOTHING — retry даёт 409)
- **10 unit-тестов** (LessonGenerationServiceTest) + **7 интеграционных тестов** (LessonGenerationIntegrationTest)

### Подфаза 13: Status Transitions + RabbitMQ Events

**Цель:** Cron-переходы статусов пар и публикация событий в RabbitMQ.

**Exchange:** `rut-uit.events` (fanout, durable)

| Событие | Триггер | Payload |
|---------|---------|---------|
| `lesson.started` | Cron: planned→active (start_time ≤ now) | `{lesson_id, group_id, subject_id, teacher_id, room, lesson_number}` |
| `lesson.closed` | Cron: active→closed (end_time + 5 min ≤ now) | `{lesson_id, group_id, subject_id, teacher_id}` |
| `lesson.cancelled` | Headman отменяет пару | `{lesson_id, group_id, reason}` |

- **LessonStatusTransitionJob**: `@Scheduled(cron = "0 * * * * *")` — каждую минуту, Moscow TZ
- **Catch-up**: при рестарте сервиса обрабатывает пропущенные переходы
- **DomainEvent**: абстрактный класс с envelope (event_type, event_id, occurred_at, payload)
- **@TransactionalEventListener(AFTER_COMMIT)**: нет событий при rollback
- **6 unit-тестов** (LessonStatusTransitionJobTest) + **1 интеграционный тест** (LessonCancelEventTest)

### Подфаза 14: gRPC Server

**Порт:** 19092 | **Proto:** `schedule.proto`

- **3 RPC**: GetActiveLesson, GetLessonById, GetLessonsByGroup
- **ScheduleGrpcServiceImpl**: инжектит репозитории напрямую
- **GrpcExceptionAdvice**: маппинг исключений в gRPC Status коды (NOT_FOUND, INVALID_ARGUMENT)
- **grpc-spring-boot-starter** с protobuf Gradle плагином
- **8 интеграционных тестов** (in-process gRPC server)

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| Clock.system(Moscow) + hibernate.jdbc.time_zone | Все TIME/TIMESTAMPTZ колонки интерпретируются в московском часовом поясе |
| @Profile("!test") на SchedulingConfig | Cron-задачи не запускаются в тестах, но тестируются через прямой вызов Job |
| LessonGenerationService с week parity | Алгоритм: чётность недели = (weeksBetween(semesterStart, date) % 2), привязка к first_week_type семестра |
| Eager generation (все пары при создании шаблона) | Проще чем lazy — пары сразу видны в расписании; UNIQUE constraint для защиты от дублей |
| gRPC client к Academic Service для валидации | Проверка subject/teacher/semester существования перед созданием шаблона |
| LessonWithItem projection | JOIN ScheduleItem+Lesson для обогащённого ответа (room, subject, teacher) без N+1 |
| gRPC server запрашивает репозитории напрямую | Обход проблем RequestContext scope в gRPC-потоках (как в v2.0) |

---

## Файловая структура

```
services/schedule-service/
├── schedule-api-contract/
│   └── src/main/java/ru/rutcampustrack/schedule/contract/
│       ├── api/              ← 2 API-интерфейса (ScheduleItemApi, LessonApi)
│       ├── dto/
│       │   ├── item/         ← Create/Update ScheduleItemRequest, ScheduleItemResponse
│       │   └── lesson/       ← Cancel/MassCancel/GeoBlock Request, LessonResponse, MassCancelResponse
│       ├── enums/            ← LessonStatus, WeekType, UserRole
│       └── exception/        ← ErrorResponse
└── schedule-app/
    └── src/
        ├── main/java/ru/rutcampustrack/schedule/
        │   ├── config/       ← ClockConfig, EnumConverters, SchedulingConfig
        │   ├── event/        ← DomainEvent, 3 события (Started/Closed/Cancelled), RabbitConfig, DomainEventListener
        │   ├── exception/    ← GlobalExceptionHandler, ResourceNotFound, AccessDenied, InvalidLessonState, AcademicServiceUnavailable
        │   ├── grpc/         ← AcademicGrpcClient, ScheduleGrpcServiceImpl, GrpcExceptionAdvice
        │   ├── item/         ← ScheduleItemController, ScheduleItemService, ScheduleItemAssembler, entity/, repository/
        │   ├── lesson/       ← LessonController, LessonService, LessonGenerationService, LessonStatusTransitionJob, LessonAssembler, LessonWithItem, entity/, repository/
        │   └── security/     ← RequestContext, RequireRole, RoleCheckAspect, UserContextFilter, HealthCheckController
        ├── main/resources/
        │   ├── application.yml
        │   └── db/migration/ ← V1 (baseline), V2 (enum casts)
        └── test/java/ru/rutcampustrack/schedule/
            ├── integration/
            │   ├── AbstractScheduleIntegrationTest.java    ← Testcontainers PostgreSQL
            │   ├── EntityMappingIntegrationTest.java       ← 1 test
            │   ├── ScheduleItemApiTest.java                ← 8 tests
            │   ├── LessonApiTest.java                      ← 7 tests
            │   ├── ScheduleViewTest.java                   ← 5 tests
            │   ├── LessonGenerationIntegrationTest.java    ← 7 tests
            │   ├── LessonCancelEventTest.java              ← 1 test
            │   └── SecuritySmokeTest.java                  ← 2 tests
            ├── lesson/
            │   ├── LessonGenerationServiceTest.java        ← 10 unit tests
            │   └── LessonStatusTransitionJobTest.java      ← 6 unit tests
            └── grpc/
                └── ScheduleGrpcServiceImplTest.java        ← 8 tests

event-schemas/
├── lesson.started.json
├── lesson.closed.json
└── lesson.cancelled.json

proto/
└── schedule.proto          ← 3 RPC definitions
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| Entity Mapping | 1 | Integration | Testcontainers PostgreSQL |
| ScheduleItem CRUD (TMPL-01..05) | 8 | Integration | Testcontainers PostgreSQL |
| Lesson Operations (LSSN-04..07) | 7 | Integration | Testcontainers PostgreSQL |
| Schedule View (VIEW-01..02) | 5 | Integration | Testcontainers PostgreSQL |
| Lesson Generation (LSSN-01..03) | 7 | Integration | Testcontainers PostgreSQL |
| Lesson Generation (unit) | 10 | Unit | Mockito |
| Cancel Event (EVNT-03) | 1 | Integration | Testcontainers PostgreSQL + RabbitMQ |
| Status Transitions (CRON-01..03) | 6 | Unit | Mockito |
| Security Smoke | 2 | Integration | Testcontainers PostgreSQL |
| gRPC Server (GRPC-01..03) | 8 | Integration | Testcontainers PostgreSQL + in-process gRPC |
| **Итого** | **55** | | |

Все тесты проходят: `./gradlew.bat :services:schedule-service:schedule-app:test`

---

## Требования (покрытие)

| Категория | ID | Статус |
|-----------|-----|--------|
| Schedule Templates | TMPL-01 — TMPL-05 | ✅ Все 5 |
| Lesson Management | LSSN-01 — LSSN-07 | ✅ Все 7 |
| Schedule Viewing | VIEW-01 — VIEW-02 | ✅ Все 2 |
| Status Automation | CRON-01 — CRON-04 | ✅ Все 4 |
| Events | EVNT-01 — EVNT-04 | ✅ Все 4 |
| gRPC Server | GRPC-01 — GRPC-03 | ✅ Все 3 |
| **Итого** | **25/25** | **100%** |

---

## Известный tech debt

| Проблема | Серьёзность | Описание |
|----------|-------------|----------|
| IllegalArgumentException → HTTP 500 | Warning | `GlobalExceptionHandler` не обрабатывает `IllegalArgumentException` — REST возвращает 500 вместо 400. В gRPC обработка есть |
| LSSN-03 idempotency | Warning | `saveAll()` без `ON CONFLICT DO NOTHING` — retry даёт 409 вместо тихого пропуска дублей |
| GRPC-03 cancelled lessons | Info | `GetLessonsByGroup` включает cancelled пары — Attendance Service должен фильтровать на своей стороне |
| Flush behavior comment | Info | Комментарий в `LessonStatusTransitionJob` неточно описывает механизм commit/flush |

---

## Следующая фаза

**Фаза 4: Attendance Service** ✅ — MongoDB, гео-отметка, ручная отметка, автоматический absent, отчёты и статистика посещаемости. Отчёт: `docs/phase-4-report.md`
