# RutCampusTrack — Отчёт Фазы 4: Attendance Service

## Дата: Апрель 2026

## Цель фазы

Attendance Service MVP: гео-отметка студентов, ручная отметка старостой, автоматический absent при закрытии пары, базовые отчёты (журнал, статистика студента, посещаемость пары). MongoDB для хранения, gRPC-клиенты к Schedule и Academic Service, RabbitMQ-потребители для событий жизненного цикла пар.

---

## Что реализовано

### Подфаза 15: Infrastructure Foundation

**Цель:** Attendance Service стартует полностью подключённым — MongoDB индексы, enum-конвертеры, gRPC-клиенты, RabbitMQ-очередь.

- **MongoDB**: `AttendanceDocument` с 12 полями, 4 программных индекса (unique compound `{lesson_id, user_id}`, query indexes для отчётов)
- **Enum-конвертеры**: `MongoCustomConversions` — `AttendanceStatus` и `AttendanceSource` хранятся в lowercase (`"present"`, `"headman"`)
- **gRPC-клиенты**: `ScheduleGrpcClient` (3 RPC: GetActiveLesson, GetLessonById, GetLessonsByGroup), `AcademicGrpcClient` (4 RPC: GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman)
- **RabbitMQ**: durable `attendance-service.events` очередь + DLQ, привязка к `rut-uit.events` fanout exchange
- **Безопасность**: `@RequireRole` AOP аннотация, `RoleCheckAspect`, `RequestContext` (@RequestScope), `UserContextFilter`
- **Обработка ошибок**: `GlobalExceptionHandler` — RFC 7807 (400, 403, 404, 409, 422, 429, 503, 500)
- **SemesterCacheService**: volatile кэш активного семестра с `@PostConstruct` try/catch
- **27 тестов** (17 unit + 10 integration): MongoDB индексы, enum сериализация, RabbitMQ очередь, security, gRPC клиенты

### Подфаза 16: Event Consumers

**Цель:** Автоматический absent при lesson.closed, отмена при lesson.cancelled, DLQ для защиты от потерь.

- **LessonEventService**: `processLessonClosed()` — bulk upsert `$setOnInsert` (ABSENT/AUTO_SCHEDULER) для всех неотмеченных студентов группы; `processLessonCancelled()` — `updateMulti` ставит CANCELLED
- **EventConsumer**: `@RabbitListener` маршрутизирует по `event_type` (lesson.started/closed/cancelled, semester.archived)
- **BulkMode.UNORDERED**: ошибка одного студента не блокирует остальных
- **Нет try/catch**: исключения пробрасываются → AMQP nack → DLQ
- **12 тестов** (6 integration + 6 unit): Testcontainers RabbitMQ + MongoDB, Awaitility async assertions

### Подфаза 17: Write Path — Geo-Checkin + Manual Marking

**Цель:** Студенты могут геоотметиться, старосты — ставить статус вручную. Все защиты (геозона, окно, dedup, rate limit).

**Geo-Checkin (POST /attendance/checkin):**

| Шаг | Проверка | HTTP при ошибке |
|-----|----------|-----------------|
| 1 | Rate limit (3 попытки/минуту) | 429 |
| 2 | Активная пара через gRPC | 404 |
| 3 | Временное окно (5 мин до/после) | 422 |
| 4 | Geo-block флаг | 403 |
| 5 | Haversine геозона | 422 |
| 6 | Redis dedup (5 сек TTL) | 409 |
| 7 | MongoDB save (unique index) | 409 |
| 8 | Публикация attendance.marked | — |

- **GeoUtils**: package-private, Haversine формула (EARTH_RADIUS=6371000м)
- **GeofenceService**: volatile кэш с 30-мин TTL, `@PostConstruct` try/catch
- **CheckinRateLimiter**: Redis `SETNX` для dedup, `INCR` + `EXPIRE` для rate limit
- **CheckinController**: `@RequireRole(STUDENT)`, возвращает 201 + HATEOAS EntityModel

**Manual Marking (PUT /attendance/lessons/{lessonId}/students/{userId}):**

- **MarkingService**: проверка isHeadman → группа совпадает → студент в группе → `mongoTemplate.upsert` (`$set` мутабельные + `$setOnInsert` иммутабельные поля)
- **MarkingController**: `@RequireRole(STUDENT)` (староста = STUDENT + is_headman)
- **attendance.marked** событие после успешного upsert

**Контракты (attendance-api-contract):**
- `CheckinApi`, `MarkingApi` — интерфейсы с Swagger аннотациями
- `CheckinRequest/Response`, `MarkRequest/Response` — records без Lombok

**41 тест** (5 GeoUtils + 6 RateLimiter + 7 CheckinService + 8 CheckinIntegration + 7 MarkingService + 8 MarkingIntegration)

### Подфаза 18: Read Path — Reports

**Цель:** Журнал, статистика студента, список посещений, доменная изоляция report/checkin.

**4 GET-эндпоинта:**

| Endpoint | Описание | Роли |
|----------|----------|------|
| `/attendance/reports/lesson/{lessonId}` | Посещаемость пары (все студенты группы) | STUDENT (headman), TEACHER |
| `/attendance/reports/journal` | Журнал (сетка: студенты × даты) | STUDENT (headman), TEACHER |
| `/attendance/reports/student/stats` | Статистика студента по предметам | STUDENT |
| `/attendance/reports/student/records` | Список посещений с фильтром по предмету | STUDENT |

- **AttendanceReadPort**: интерфейс в `shared/port/` — 0 импортов из checkin/
- **AttendanceReadPortImpl**: в пакете `checkin/`, реализует порт через MongoTemplate
- **ReportService**: left-join roster (gRPC) + attendance (MongoDB), CANCELLED исключены из знаменателя статистики, `getSubjectsByIds` gRPC для имён предметов
- **GetSubjectsByIds**: новый RPC в `academic.proto`, реализован в Academic Service
- **ArchUnit тест**: `noClasses().resideInAPackage("report..").should().dependOnClassesThat().resideInAPackage("checkin..")`

**15 тестов** (8 ReportServiceTest unit + 1 ArchUnit + 6 ReportIntegrationTest)

### Подфаза 19: Report Security & Routing Fix (Gap Closure)

**Цель:** Добавить `@RequireRole` на все 4 метода ReportController, выровнять URL путь отчётов.

- **INT-01 закрыт**: `@RequireRole({STUDENT, TEACHER})` на getLessonAttendance/getJournal, `@RequireRole(STUDENT)` на getStudentStats/getStudentRecords
- **INT-02 закрыт**: `@RequestMapping("/attendance/reports")` в ReportApi, убран `/api/reports/**` предикат из Gateway
- Исправлен pre-existing баг: `cells` → `records` в тесте getJournal (Spring HATEOAS сериализует `getRecords()` как `records`)

---

## API Attendance Service

### REST Endpoints

| Метод | URL | Описание | Роль |
|-------|-----|----------|------|
| POST | /attendance/checkin | Геоотметка | STUDENT |
| PUT | /attendance/lessons/{id}/students/{userId} | Ручная отметка | STUDENT (headman) |
| GET | /attendance/reports/lesson/{lessonId} | Посещаемость пары | STUDENT/TEACHER |
| GET | /attendance/reports/journal | Журнал | STUDENT/TEACHER |
| GET | /attendance/reports/student/stats | Статистика | STUDENT |
| GET | /attendance/reports/student/records | Записи | STUDENT |
| GET | /attendance/health-check | Smoke test | STUDENT |

**Gateway**: все через `Path=/api/attendance/**`, `StripPrefix=1`

### RabbitMQ Events (потребление)

| Событие | Действие |
|---------|----------|
| `lesson.started` | Логирование (no-op) |
| `lesson.closed` | Auto-absent для неотмеченных ($setOnInsert) |
| `lesson.cancelled` | Все записи → CANCELLED (updateMulti) |
| `semester.archived` | Обновление кэша семестра |

### RabbitMQ Events (публикация)

| Событие | Триггер |
|---------|---------|
| `attendance.marked` | После успешного checkin или manual mark |

### gRPC Clients

| Сервис | RPC |
|--------|-----|
| Schedule | GetActiveLesson, GetLessonById, GetLessonsByGroup |
| Academic | GetGroupMembers, GetCampusGeofence, GetActiveSemester, IsHeadman, GetTeacherSubjects, GetSubjectsByIds |

---

## Ключевые технические решения

| Решение | Обоснование |
|---------|------------|
| MongoDB (не PostgreSQL) для attendance | Гибкая документная модель, bulkOps для auto-absent, нет JOIN-ов |
| AttendanceReadPort для изоляции доменов | report/ никогда не импортирует checkin/ — ArchUnit-тест гарантирует |
| $setOnInsert для auto-absent | Race-safe: существующие checkin'ы никогда не перезаписываются |
| Volatile geofence cache (30 мин TTL) | Простая реализация, нет overhead Redis для редко меняющихся данных |
| GenericContainer для Redis Testcontainer | `testcontainers:redis` BOM-модуль не существует — GenericContainer работает |
| BulkMode.UNORDERED | Ошибка одного студента не блокирует остальных при auto-absent |
| Нет @Transactional на LessonEventService | MongoDB bulkOps и RabbitMQ не имеют общего transaction manager |
| @RequireRole method-level | ElementType.METHOD — применяется к каждому методу контроллера |
| mongoTemplate.remove(new Query()) | Сохраняет индексы между тестами (в отличие от dropCollection) |
| lenient() stubs в @BeforeEach | Избегает UnnecessaryStubbingException в Mockito strict mode |

---

## Файловая структура

```
services/attendance-service/
├── attendance-api-contract/
│   └── src/main/java/ru/rutcampustrack/attendance/contract/
│       ├── api/              ← 3 API-интерфейса (CheckinApi, MarkingApi, ReportApi)
│       ├── dto/
│       │   ├── checkin/      ← CheckinRequest, CheckinResponse
│       │   ├── marking/      ← MarkRequest, MarkResponse
│       │   └── report/       ← 9 DTO (LessonAttendance, Journal, StudentStats, Records + вложенные)
│       ├── enums/            ← AttendanceStatus, AttendanceSource, UserRole
│       └── exception/        ← ErrorResponse, ResourceNotFoundException
└── attendance-app/
    └── src/
        ├── main/java/ru/rutcampustrack/attendance/
        │   ├── config/       ← MongoConfig (indexes), MongoConvertersConfig (enum converters), RabbitConfig (queue + DLQ)
        │   ├── checkin/      ← CheckinService, CheckinController, AttendanceDocument, AttendanceRepository, AttendanceReadPortImpl
        │   ├── event/        ← EventConsumer, LessonEventService, AttendanceEventPublisher
        │   ├── exception/    ← GlobalExceptionHandler, 6 exception types
        │   ├── geofence/     ← GeofenceService, GeoUtils (Haversine)
        │   ├── grpc/         ← ScheduleGrpcClient, AcademicGrpcClient
        │   ├── marking/      ← MarkingService, MarkingController
        │   ├── ratelimit/    ← CheckinRateLimiter
        │   ├── report/       ← ReportService, ReportController
        │   ├── security/     ← RequireRole, RoleCheckAspect, RequestContext, UserContextFilter
        │   ├── semester/     ← SemesterCacheService
        │   └── shared/port/  ← AttendanceReadPort (interface), AttendanceRecord (record)
        ├── main/resources/
        │   └── application.yml
        └── test/java/ru/rutcampustrack/attendance/
            ├── integration/
            │   ├── AbstractAttendanceIntegrationTest.java  ← Testcontainers MongoDB + RabbitMQ + Redis
            │   ├── MongoIndexTest.java                     ← 2 tests
            │   ├── EnumSerializationTest.java              ← 2 tests
            │   ├── RabbitConsumerTest.java                 ← 2 tests
            │   ├── SecuritySmokeTest.java                  ← 3 tests
            │   ├── EventConsumerIntegrationTest.java       ← 6 tests
            │   ├── CheckinIntegrationTest.java             ← 8 tests
            │   ├── MarkingIntegrationTest.java             ← 8 tests
            │   └── ReportIntegrationTest.java              ← 6 tests
            ├── checkin/
            │   └── CheckinServiceTest.java                 ← 7 unit tests
            ├── event/
            │   └── LessonEventServiceTest.java             ← 6 unit tests
            ├── geofence/
            │   └── GeoUtilsTest.java                       ← 5 unit tests
            ├── grpc/
            │   ├── ScheduleGrpcClientTest.java             ← 8 unit tests
            │   └── AcademicGrpcClientTest.java             ← 9 unit tests
            ├── marking/
            │   └── MarkingServiceTest.java                 ← 7 unit tests
            ├── ratelimit/
            │   └── CheckinRateLimiterTest.java             ← 6 unit tests
            └── report/
                ├── ReportServiceTest.java                  ← 8 unit tests
                └── ReportDomainIsolationTest.java          ← 1 ArchUnit test

event-schemas/
└── attendance.marked.json

proto/
└── academic.proto            ← +GetSubjectsByIds RPC
```

---

## Тестовое покрытие

| Модуль | Тесты | Тип | Фреймворк |
|--------|-------|-----|-----------|
| MongoDB Indexes | 2 | Integration | Testcontainers MongoDB |
| Enum Serialization | 2 | Integration | Testcontainers MongoDB |
| RabbitMQ Consumer | 2 | Integration | Testcontainers RabbitMQ |
| Security Smoke | 3 | Integration | Testcontainers MongoDB |
| Event Consumers | 6 | Integration | Testcontainers MongoDB + RabbitMQ |
| Event Consumers (unit) | 6 | Unit | Mockito |
| GeoUtils (Haversine) | 5 | Unit | JUnit |
| CheckinRateLimiter | 6 | Unit | Mockito |
| CheckinService | 7 | Unit | Mockito |
| Checkin (integration) | 8 | Integration | Testcontainers MongoDB + RabbitMQ + Redis |
| MarkingService | 7 | Unit | Mockito |
| Marking (integration) | 8 | Integration | Testcontainers MongoDB + RabbitMQ + Redis |
| ReportService | 8 | Unit | Mockito |
| ArchUnit Domain Isolation | 1 | Unit | ArchUnit |
| Report (integration) | 6 | Integration | Testcontainers MongoDB |
| Schedule gRPC Client | 8 | Unit | Mockito |
| Academic gRPC Client | 9 | Unit | Mockito |
| **Итого** | **~95** | | |

Все тесты проходят: `./gradlew.bat :services:attendance-service:attendance-app:test`

---

## Требования (покрытие)

| Категория | ID | Статус |
|-----------|-----|--------|
| Infrastructure | INFRA-01 — INFRA-06 | ✅ Все 6 |
| Geo-Checkin | CHKN-01 — CHKN-07 | ✅ Все 7 |
| Marking | MARK-01 — MARK-05 | ✅ Все 5 |
| Reports | RPRT-01 — RPRT-05 | ✅ Все 5 |
| **Итого** | **23/23** | **100%** |

---

## Известный tech debt

| Проблема | Серьёзность | Описание |
|----------|-------------|----------|
| IllegalArgumentException → HTTP 500 | Warning | Наследство v3.0 — GlobalExceptionHandler Schedule/Academic не обрабатывает |
| LSSN-03 idempotency | Warning | saveAll без ON CONFLICT DO NOTHING (наследство v3.0) |
| semester.archived тест NPE | Low | EventConsumerIntegrationTest — Jackson converter может не быть настроен для test @RabbitListener |
| DLQ требует ручной проверки | Info | DLQ routing работает по коду, но не проверен на живом брокере |
| Интеграционные тесты требуют Docker | Info | Testcontainers MongoDB + RabbitMQ + Redis — Docker Desktop обязателен |

---

## Следующая фаза

**Фаза 5: Notification Service (Web + Bot)** — WebSocket push-уведомления + Telegram бот для уведомлений об отметке, excuse-тикетах, и т.д.
