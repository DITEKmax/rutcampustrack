# API Error & Batch Conventions

**Версия:** v0.0.0 (M05 Группа 4 NEW-145 + M11 G0 унификация ErrorResponse)
**Последнее обновление:** 2026-04-24

Документ фиксирует соглашения об ошибках и batch-операциях REST API
RutCampusTrack. Большая часть error-schema уже существовала до M05 —
документ её описывает + добавляет раздел «Batch endpoints».

---

## Error Response Schema (RFC 9457 Problem Details)

**M11 G0 (2026-04-24):** все сервисы возвращают ошибки через **единый**
`ErrorResponse` record из `shared-web-api/exception/ErrorResponse.java`
(раньше было 5 дублей: shared-web, 3 `*-api-contract`, auth/dto).
Обработка централизована в:

- **shared `GlobalExceptionHandler`** (`@Order(LOWEST_PRECEDENCE)`) —
  catch-all Spring MVC exceptions (validation, noHandler, accessDenied,
  generic). Приходит в сервис через `shared-web` dependency +
  `@AutoConfiguration`.
- **per-service `GlobalExceptionHandler`** (`@Order(HIGHEST_PRECEDENCE)`)
  — только domain exceptions (ConflictException, GeofenceViolation,
  InvalidCredentials и т.п.).

Content-Type: `application/problem+json`.

```json
{
  "status": 400,
  "type": "https://api.rutcampustrack.ru/problems/validation-failed",
  "title": "Ошибка валидации",
  "detail": "Одно или несколько полей не прошли проверку",
  "instance": "/api/attendance/marks/batch",
  "timestamp": "2026-04-24T15:30:00Z",
  "traceId": "abc-trace-123",
  "fieldErrors": [
    {
      "field": "items[3].status",
      "rejectedValue": null,
      "message": "не может быть null"
    }
  ]
}
```

Extension fields (RFC 9457 «Extension Members»):

- `traceId` — correlation ID из MDC. Сквозной в логи backend'а через
  P2-3/1 (OpenTelemetry). Присутствует во всех ответах при наличии MDC.
- `fieldErrors[]` — ошибки валидации body DTO. Только для `400`
  (`MethodArgumentNotValidException`, `ConstraintViolationException`,
  batch индексированные пути `items[3].lessonId`).
- `field` — имя DTO-поля для conflict (`409`, BUG-006-2). Frontend
  использует для highlight конкретного поля формы.
- `extras` — дополнительный payload (`scheduleItemsCount`, retry-after
  и т.п.).

Все extension fields — `@JsonInclude(NON_NULL)`, не попадают в body
если `null`.

## Global error responses (NEW-122, M11 G1)

**M11 G1 (2026-04-24):** `GlobalErrorResponsesCustomizer` в
`shared-web` автоматически добавляет 7 стандартных error responses
ко всем OpenAPI operations всех сервисов. `@ApiResponse` в
`*-api-contract` интерфейсах можно **не дублировать** — customizer
покрывает default case.

Стандартные статусы + default descriptions:

| Status | Description (RU) | Когда возвращается |
|--------|------------------|--------------------|
| 400 | Ошибка валидации запроса | `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` |
| 401 | Требуется аутентификация | `InvalidCredentialsException`, `TokenRefreshException`, `OtpExpiredException`, `TmaValidationException` (auth-service); отсутствие JWT |
| 403 | Доступ запрещён | Spring Security `AccessDeniedException`, per-service `AccessDeniedException`, `GeofenceBlockedException` |
| 404 | Ресурс не найден | `ResourceNotFoundException`, `NoHandlerFoundException`, `NoResourceFoundException` |
| 409 | Конфликт данных | `ConflictException`, `DataIntegrityViolationException`, `DuplicateKeyException` |
| 429 | Превышен лимит запросов | `RateLimitException`, `OtpRateLimitException` |
| 500 | Внутренняя ошибка сервера | generic `Exception` catch-all (не включает stack trace в body) |

Все responses имеют `content: application/problem+json` +
`schema: $ref #/components/schemas/ErrorResponse` (единая schema из
`shared-web-api`).

Per-endpoint override работает:

```java
@Operation(summary = "Создать пользователя")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Пользователь создан"),
    // Если определить 409 здесь с custom description — customizer
    // сохранит description, но добавит content schema автоматически.
    @ApiResponse(responseCode = "409", description = "Логин уже используется")
})
```

Customizer подключается автоматически через
`SharedWebAutoConfiguration` (M11 G0.2). Сервису достаточно
`implementation(project(":shared-web"))` + springdoc starter.

## Batch Endpoint Conventions (NEW-145)

### Transactional semantics

Два варианта семантики для batch:

| Семантика | Когда применима | HTTP status | Response |
|-----------|-----------------|-------------|----------|
| **Atomic / pseudo-atomic** | Все items связаны одной бизнес-операцией (headman отмечает пару) | 200 OK | `{items, processed}` |
| **Partial-success** | Items независимы (admin-импорт homework) | 207 Multi-Status или 200 с per-item error field | `[{index, status, error?}, ...]` |

Выбор — **per-endpoint**, фиксируется в OpenAPI (`@ApiResponses`).

### Pseudo-atomic pattern (attendance marks/batch)

Реализован в `POST /attendance/marks/batch` (M05 D7).

**Инвариант:** все authorization/validation checks выполняются **до**
любого write'а в БД. При любой ошибке весь batch отклонён, БД не
тронута.

**Зачем «pseudo»:** настоящая DB transaction требует replica-set
(Mongo) или distributed tx coordinator (cross-DB). В v0.0.0
infrastructure — standalone DB — transactions недоступны. Validation-
first приближает atomic semantics без транзакции:

- 100% authorization-ошибок детектируются pre-check (validation-first).
- Post-check failure (Mongo upsert падает mid-batch) — edge case,
  idempotent upsert переживает retry. Следующий batch от клиента
  завершит начатое.

**Request/response shape:**

```http
POST /attendance/marks/batch
Content-Type: application/json
Authorization: Bearer <jwt>

{
  "items": [
    { "lessonId": 42, "userId": 99, "status": "present" },
    { "lessonId": 42, "userId": 100, "status": "absent" },
    { "lessonId": 42, "userId": 101, "status": "excused" }
  ]
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/hal+json

{
  "items": [
    { "status": "present", "lessonId": 42, "userId": 99, "timestamp": "..." },
    { "status": "absent", "lessonId": 42, "userId": 100, "timestamp": "..." },
    { "status": "excused", "lessonId": 42, "userId": 101, "timestamp": "..." }
  ],
  "processed": 3,
  "_links": { "self": { "href": "/attendance/marks/batch" } }
}
```

**Size limits:** `@Valid @Size(min=1, max=100)`. OWNER-ANSWERS P2-10/4
suggested 100; типичный headman batch — 25-30 students. `min=1`
защищает от no-op requests.

**Optimization:** для одного `lessonId` — один gRPC `getLessonById` +
один `getGroupMembers` на весь batch, N upsert'ов Mongo. Для N=30
students: 3 gRPC round-trip + 30 Mongo writes против 90 gRPC + 30
writes в N single-mark scenarios (~10× latency reduction).

### Partial-success pattern (зарезервировано)

Предназначен для future-endpoints (`POST /academic/homeworks/batch`
для admin-импорта CSV, etc.). Отложен в M05 (см. D8).

**Ожидаемая форма:**

```http
HTTP/1.1 207 Multi-Status
Content-Type: application/hal+json

{
  "items": [
    { "index": 0, "status": "created", "id": 123 },
    { "index": 1, "status": "conflict", "error": {
        "field": "title",
        "message": "ДЗ с этим title уже существует"
    }},
    { "index": 2, "status": "created", "id": 125 }
  ],
  "stats": { "total": 3, "succeeded": 2, "failed": 1 }
}
```

Клиент разбирает `items[]` → отображает список: «2 из 3 импортированы,
1 конфликт (row 2)».

### Client conventions

- **PWA / web-panel:** один HTTP-запрос + один progress indicator для
  всего batch. Нет per-item UI update в процессе; update после
  response.
- **Invalidation TanStack Query:** invalidate affected cache keys
  один раз после success (не per-item).
- **Error handling:** 200 — всё ок; 400 → fieldErrors показать на
  конкретных строках; 403/404 → общий toast «Нет прав» / «Пара не
  найдена»; 500 → retry весь batch (idempotent upsert безопасен).

### References

- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/MarkingApi.java` — batch endpoint contract
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/marking/MarkBatch*.java` — DTOs
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:markBatch` — реализация
- `frontends/pwa/src/features/schedule/headmanSheetApi.ts:useHeadmanMarkBatch` — client
- `docs/milestones/M05-performance/DECISIONS.md` D7, D8 — обоснование semantics и scope
