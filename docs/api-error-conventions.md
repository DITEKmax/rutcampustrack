# API Error & Batch Conventions

**Версия:** v0.0.0 (M05 Группа 4, NEW-145)
**Последнее обновление:** 2026-04-20

Документ фиксирует соглашения об ошибках и batch-операциях REST API
RutCampusTrack. Большая часть error-schema уже существовала до M05 —
документ её описывает + добавляет раздел «Batch endpoints».

---

## Error Response Schema (RFC 7807-inspired)

Все сервисы возвращают ошибки через единый `ErrorResponse` record
(`*-api-contract/exception/ErrorResponse.java`). Обработка
централизована в `GlobalExceptionHandler` (`@ControllerAdvice` в каждом
`*-app`).

```json
{
  "status": 400,
  "type": "https://ruttrack.site/errors/validation",
  "title": "Invalid input",
  "detail": "Поле status не может быть пустым",
  "instance": "/attendance/marks/batch",
  "timestamp": "2026-04-20T15:30:00Z",
  "fieldErrors": [
    {
      "field": "items[3].status",
      "rejectedValue": null,
      "message": "не может быть null"
    }
  ]
}
```

- `fieldErrors[]` присутствует только при `status == 400`
  (validation failure).
- Для batch-endpoints индексированные пути: `items[3].lessonId` —
  показывает конкретный item и поле.
- Остальные статусы (403, 404, 409, 500) — `fieldErrors` пустой или
  отсутствует.

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
