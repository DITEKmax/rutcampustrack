# Phase 59: Excuse Tickets Backend — Research

**Researched:** 2026-04-14
**Domain:** Spring Boot 3.4 + MongoDB + RabbitMQ + gRPC + Angular 20 — domain-driven service-добавление, event-driven integration, contract-tests
**Confidence:** HIGH (артефакты подтверждены при разведке Фазы C багфиксов)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

D-01..D-29 зафиксированы пользователем. Ключевое:
- Файлы вложений — out of scope этой фазы.
- Староста создаёт записи о своих пропусках через журнал, а не через тикет (D-12).
- Один lessonId — один активный тикет (D-11).
- Каскад на attendance при approve — обязателен (D-16).
- Контракт-тест с notification-bot обязателен (D-19, D-27).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

### Functional

**FR-1**: STUDENT с web-panel создаёт тикет с указанием причины (`ExcuseType`), списка lessonIds (один или несколько уроков своей группы), опциональным комментарием. После создания тикет имеет статус `pending`.

**FR-2**: При создании тикета backend публикует event `excuse.requested` в RabbitMQ. notification-bot принимает event и шлёт алерт старосте группы (это уже работает — нужен только корректный контракт).

**FR-3**: STUDENT видит свои тикеты на `/student/excuses` со статусами и комментариями старосты.

**FR-4**: Староста на `/headman/excuses` видит pending-тикеты группы. Может одобрить или отклонить с комментарием.

**FR-5**: При одобрении тикета — соответствующие AttendanceRecord переводятся в `excused` (или `free_attendance` для соответствующего ExcuseType). Транзакционно.

**FR-6**: При смене статуса публикуется event `excuse.decided`. Бот уведомляет студента.

**FR-7**: Староста не может: создать тикет (D-12), одобрить/отклонить свой тикет (D-13), смотреть тикеты чужих групп (D-14).

### Non-functional

**NFR-1**: Создание тикета — атомарно (или Mongo doc + RabbitMQ event оба, или ничего). Реализация — outbox-паттерн или transactional message (RabbitMQ tx).
**NFR-2**: Одобрение — транзакционно: статус тикета + AttendanceRecord обновляются вместе.
**NFR-3**: API времена ≤ 200 мс p95 (Mongo + ~3 операции).
**NFR-4**: Все новые эндпоинты защищены `@RequireRole`.
**NFR-5**: 0% потери events (RabbitMQ persistent + ack).

### Acceptance criteria

- [ ] AC-1: STUDENT создаёт тикет → 201 Created с телом ExcuseTicketResponse.
- [ ] AC-2: Дубликат lessonId в активном тикете → 409.
- [ ] AC-3: Староста создаёт тикет → 409 «Староста проставляет через журнал».
- [ ] AC-4: STUDENT смотрит чужой тикет → 403.
- [ ] AC-5: Староста одобряет тикет → status=approved, AttendanceRecord обновлён.
- [ ] AC-6: Староста одобряет свой тикет → 409 (если он каким-то образом был создан).
- [ ] AC-7: RabbitMQ получает `excuse.requested` с правильной JSON-схемой (контракт-тест).
- [ ] AC-8: notification-bot consumer тестирован для `excuse.decided`.
- [ ] AC-9: Frontend `excuse-form-dialog` имеет dropdown причины. Список тикетов на студенте подгружается.
- [ ] AC-10: Headman UI показывает реальный список тикетов и работающие кнопки.
- [ ] AC-11: gRPC schedule.LessonsByIds реализован и используется для валидации lessonIds.
- [ ] AC-12: Все backend-тесты + frontend vitest зелёные.

</phase_requirements>

---

<artifact_inventory>
## Existing Artefacts

### attendance-service (где будет основная работа)

- `services/attendance-service/attendance-api-contract/.../enums/ExcuseType.java` ✅
- `services/attendance-service/attendance-api-contract/.../enums/ExcuseTicketStatus.java` ✅ (содержимое сверить)
- `services/attendance-service/attendance-api-contract/.../enums/AttendanceStatus.java` ✅ (есть EXCUSED, FREE_ATTENDANCE)
- Пакет `services/attendance-service/attendance-app/src/main/java/.../checkin/` — образец domain-структуры.
- Пакет `services/attendance-service/attendance-app/src/main/java/.../report/` — образец query-side.
- `services/attendance-service/attendance-app/src/main/java/.../shared/port/AttendanceReadPort.java` — порт для чтения; нужно добавить `AttendanceWritePort` для каскада из excuses-домена (изоляция доменов из CLAUDE.md).

### Что нужно создать (новый пакет `excuse/`)

```
attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/
├── ExcuseController.java
├── ExcuseService.java
├── ExcuseRepository.java
├── ExcuseEventPublisher.java
├── entity/ExcuseTicket.java
├── mapper/ExcuseAssembler.java
└── shared/ExcuseEventDto.java  (для RabbitMQ)
```

```
attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/
├── api/ExcuseApi.java
└── dto/excuse/
    ├── CreateExcuseRequest.java
    ├── ExcuseTicketResponse.java
    └── UpdateExcuseStatusRequest.java
```

### Frontend (web-panel)

**Существует** (с DEFERRED-методами):
- `frontends/web-panel/src/app/features/student/excuses/excuses-page.component.ts`
- `frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts`
- `frontends/web-panel/src/app/features/student/shared/student-api.service.ts` — `submitExcuse`, `getExcuseTickets`

**Существует** (graceful-degradation, Фаза 55):
- `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts`

**Изменения**:
- Добавить dropdown ExcuseType.
- Заменить DEFERRED-стабы на реальные вызовы.
- Добавить headman-actions (одобрить/отклонить).

### proto

- `proto/schedule.proto` — нет `LessonsByIds` ✅ (нужно добавить).
- `proto/academic.proto` — есть `GetUserById` ✅.

### notification-bot

- `services/notification-bot/bot/notifications/headman_alerts.py:53` — слушает `excuse.requested` ✅.
- `services/notification-bot/bot/consumers/event_dispatcher.py:67` — диспатч ✅.
- **Нужно добавить**: handler для `excuse.decided` (новый файл `student_alerts.py` или дополнить существующий).

### Тесты

- `services/attendance-service/attendance-app/src/test/` — ОК структура для добавления `ExcuseServiceTest`, `ExcuseControllerIT`.
- `services/notification-bot/tests/` — добавить `test_excuse_decided.py`.
- `frontends/web-panel/.../student/excuses/*.spec.ts` — обновить под новый flow.

</artifact_inventory>

---

<implementation_notes>
## Implementation Notes

### MongoDB document

```java
@Document(collection = "excuse_tickets")
public class ExcuseTicket {
    @Id private String id;
    private Long studentId;
    private Long groupId;
    private String studentName;
    private List<Long> lessonIds;
    private ExcuseType excuseType;
    private String comment;
    private ExcuseTicketStatus status; // default PENDING
    private Long decisionBy;
    private String decisionComment;
    private Instant decisionAt;
    private Instant createdAt;
    private Instant updatedAt;
}
```

Индексы:
```js
db.excuse_tickets.createIndex({ studentId: 1, status: 1 });
db.excuse_tickets.createIndex({ groupId: 1, status: 1, createdAt: -1 });
db.excuse_tickets.createIndex({ "lessonIds": 1, status: 1 }); // для D-11 проверки
```

### Запрет повторного lessonId (D-11)

В сервисе перед save:
```java
boolean conflict = repo.existsByStudentIdAndLessonIdsInAndStatusIn(
    studentId, lessonIds, List.of(PENDING, APPROVED));
if (conflict) throw new ConflictException("Тикет на этот урок уже существует");
```

### Каскад на attendance (D-16)

В пакете `excuse/` НЕ импортируем напрямую `checkin/` (правило CLAUDE.md). Вводим порт:

```java
// shared/port/AttendanceWritePort.java
public interface AttendanceWritePort {
    void mark(Long studentId, Long lessonId, AttendanceStatus status);
}

// checkin/CheckinAttendanceWriteAdapter.java (имплементация в checkin-домене)
@Component
class CheckinAttendanceWriteAdapter implements AttendanceWritePort {
    void mark(...) { ... }
}
```

### RabbitMQ event (D-19, D-20)

Использовать существующий `RabbitMQ template` из `notification-web` (уже настроен exchange).
Контракт-тест в `services/attendance-service/attendance-app/src/test/.../excuse/ExcuseEventContractTest.java`:
читает JSON-фикстуру из `notification-bot/tests/fixtures/excuse_requested.json` и сравнивает структуру.

### gRPC LessonsByIds (D-25)

```proto
// proto/schedule.proto
message LessonsByIdsRequest {
    repeated int64 lesson_ids = 1;
}
message LessonsByIdsResponse {
    repeated LessonInfo lessons = 1;
}
message LessonInfo {
    int64 lesson_id = 1;
    int64 group_id = 2;
    int64 subject_id = 3;
    string starts_at = 4; // ISO
}

service ScheduleService {
    rpc GetLessonsByIds (LessonsByIdsRequest) returns (LessonsByIdsResponse);
    // ... existing rpcs
}
```

### Roles

`@RequireRole({STUDENT})` для всех excuse-эндпоинтов. Дополнительные проверки в сервисе:
- `headman` определяется по `request.is_headman == true` из JWT claim.
- Сравнение `groupId` по тому же claim.

</implementation_notes>

---

<execution_plan_seed>
## Suggested Plan Decomposition

- **59-01**: Domain & contract — entity, DTO, контракт API, repository. Без логики.
- **59-02**: Service + controller — CRUD-логика + проверки прав.
- **59-03**: gRPC `LessonsByIds` (proto + impl в schedule-service + интеграция в attendance).
- **59-04**: Каскад на attendance + AttendanceWritePort.
- **59-05**: RabbitMQ event publisher + контракт-тесты.
- **59-06**: notification-bot — handler `excuse.decided` + тесты.
- **59-07**: Frontend student — dropdown причины, рабочий submit, список своих тикетов.
- **59-08**: Frontend headman — реальные list/approve/reject.
- **59-09**: Финальная регрессия + phase-59-report.md.

Порядок: 59-01 → 59-02 → 59-03 → 59-04 → 59-05 параллельно с 59-06 → 59-07 параллельно с 59-08 → 59-09.

</execution_plan_seed>

---

## Validation Architecture

### Test Framework & Infrastructure

| Property | Value |
|----------|-------|
| Backend Test Framework | JUnit 5 + Mockito + Spring Test + Testcontainers (MongoDB, RabbitMQ) |
| Backend Config File | `services/attendance-service/attendance-app/build.gradle.kts` (test dependencies) |
| Test Base Class | `AbstractAttendanceIntegrationTest` (Testcontainers setup) |
| Quick Unit Run | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseServiceTest"` |
| Full Backend Suite | `./gradlew :services:attendance-service:attendance-app:test` |
| Bot Test Framework | pytest + pytest-asyncio + pytest-mock |
| Bot Quick Run | `cd services/notification-bot && pytest tests/test_excuse_decided.py -v` |
| Full Bot Suite | `cd services/notification-bot && pytest tests/ -v` |
| Frontend Test Framework | Vitest + Angular Testing Module |
| Frontend Quick Run | `cd frontends/web-panel && npm test -- --run --reporter=verbose excuse` |
| Full Frontend Suite | `cd frontends/web-panel && npm test -- --run` |

### Phase AC → Test Coverage Mapping

| AC ID | Behavior | Test Type | Automated Command | File Exists? |
|-------|----------|-----------|-------------------|-------------|
| AC-1 | STUDENT создаёт тикет → 201 + response | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT.test_create_returns_201"` | ❌ Wave 0 |
| AC-2 | Дубликат lessonId → 409 Conflict | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT.test_duplicate_lesson_returns_409"` | ❌ Wave 0 |
| AC-3 | Headman создаёт → 409 | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT.test_headman_create_returns_409"` | ❌ Wave 0 |
| AC-4 | Чужой тикет → 403 | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT.test_other_student_ticket_returns_403"` | ❌ Wave 0 |
| AC-5 | Approve каскадирует на attendance | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseServiceIT.test_approve_cascades_to_attendance"` | ❌ Wave 0 |
| AC-6 | Headman не может approve свой | Integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT.test_headman_self_approve_returns_409"` | ❌ Wave 0 |
| AC-7 | RabbitMQ event valid (JSON schema) | Contract | `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseEventContractTest"` | ❌ Wave 0 |
| AC-8 | Bot handler `excuse.decided` | Unit + Integration | `cd services/notification-bot && pytest tests/test_excuse_decided.py -v` | ❌ Wave 0 |
| AC-9 | Frontend form + dropdown + list | Unit + Component | `cd frontends/web-panel && npm test -- --run --reporter=verbose excuse` | ❌ Wave 0 |
| AC-10 | Headman approve/reject UI works | E2E/Manual | Protractor/Cypress scenario or UAT | Manual (WAI-05) |
| AC-11 | gRPC LessonsByIds works | Contract | `./gradlew :services:schedule-service:schedule-app:test --tests "*LessonsByIdsTest"` | ❌ Wave 0 |
| AC-12 | All tests green | Smoke | `./gradlew :services:attendance-service:attendance-app:test && cd services/notification-bot && pytest tests/ && cd frontends/web-panel && npm test -- --run` | ❌ End of phase |

### Input Domain Sampling (Nyquist Levels)

#### Endpoint: `POST /excuses` (Create)

**Input dimensions to sample:**

| Dimension | Values | Sample (Equivalence Classes) | Test AC |
|-----------|--------|------|---------|
| **Role** | STUDENT, TEACHER, ADMIN, HEADMAN | STUDENT (plain), STUDENT+headman | AC-1, AC-3 |
| **Ownership** | own student, other student, headman trying as self | own only | AC-1; other → 403 (AC-4 variant) |
| **lessonIds validity** | all valid + same group, mix valid/invalid, all invalid, empty list, null | all valid, all invalid, one invalid in list, empty | AC-1, AC-11 |
| **Group mismatch** | lessonIds from own group, from other group | own group, other group | AC-11 |
| **ExcuseType enum** | ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, FREE_ATTENDANCE, OTHER, invalid | all 6 valid values, one invalid | AC-1, AC-9 |
| **comment field** | null, "", short (10 chars), max (1000 chars), over-max (1001) | null, 1000, 1001 | AC-1 |
| **Active ticket on lessonId** | first ticket, duplicate (exists pending/approved), rejected (allowed) | first → 201, duplicate → 409, after rejected → 201 | AC-2, AC-1 |
| **Headman flag** | true, false | true → 409, false → 201 | AC-3 |

**Boundary conditions to test:**

- 1 lessonId vs 100+ lessonIds (batch create)
- Lesson in past, today, future (D-10 allows all)
- Comment exactly 1000 chars + 1 (boundary)
- Concurrent creates on same lessonId (race condition)

#### Endpoint: `GET /excuses/me` (List Own)

**Input dimensions:**

| Dimension | Values | Sample |
|-----------|--------|--------|
| **Role** | STUDENT, headman (is_headman=true) | Both |
| **Status filter** | all, pending, approved, rejected | Sample: all, pending |
| **Pagination** | page 0, mid-range, out-of-range | 0, 1, 999 |
| **Sort order** | createdAt asc/desc | createdAt desc (spec) |

#### Endpoint: `PATCH /excuses/{id}/status` (Approve/Reject)

**Input dimensions:**

| Dimension | Values | Sample |
|-----------|--------|--------|
| **Decision** | APPROVED, REJECTED | Both |
| **decisionComment** | null, "", short, max (1000), over-max | null (allowed), 1000 |
| **Ticket status pre-change** | pending, approved, rejected | pending → 201, already approved → 409 |
| **Caller role** | headman (is_headman=true), plain student, other group headman | headman of same group, other group → 403, plain student → 403 |
| **Ticket ownership (self-approve)** | different student, same student | headman ≠ creator → 200, headman == creator → 409 |
| **Cascade to attendance required** | status=APPROVED; check all lessonIds have AttendanceRecord updated | All lessonIds → excused or free_attendance |

### Failure Modes & Resilience Tests

| Failure Scenario | Trigger | Expected Behavior | Test Type |
|------------------|---------|-------------------|-----------|
| RabbitMQ down during `excuse.requested` publish | Mock RabbitMQ exception on publish | Transactional: Mongo write rolls back OR outbox queues locally, retried | Integration (RabbitMQ container fails) |
| Mongo write succeeds, event publish fails | Exception after Mongo insert | Ticket created; event retry logic (outbox polling) retries async | Integration |
| gRPC schedule.LessonsByIds times out | Schedule service not responding | 504 Gateway Timeout OR fallback validation | Integration (mock gRPC timeout) |
| Concurrent creates for same (studentId, lessonId) pair | 2+ requests arrive simultaneously | Only 1 succeeds; other gets 409 (unique index or distributed lock) | Integration (concurrent threads) |
| Approve while attendance already marked as present | Attempt to cascade to excused when status ≠ absent | Overwrite with excused (idempotent) OR preserve if already set (depends on business rule) | Integration |
| gRPC schedule returns lessons from different group | Validation passes but lesson belongs to other group | Catch at validation step; return 400 Bad Request | Integration |

### Event Contract Verification

**RabbitMQ event `excuse.requested` (AC-7):**

1. **Schema match**: Read `notification-bot/tests/fixtures/excuse_requested.json` and validate backend publishes identical structure
   ```json
   {
     "type": "excuse.requested",
     "ticketId": "<ObjectId>",
     "studentId": <long>,
     "studentName": "<string>",
     "groupId": <long>,
     "lessonIds": [<long>, ...],
     "excuseType": "<lowercase>",
     "comment": "<string|null>",
     "createdAt": "<ISO-8601>"
   }
   ```

2. **Test implementation** (`ExcuseEventContractTest.java`):
   - Create ExcuseTicket via API
   - Capture event from RabbitMQ test queue
   - Parse JSON; compare against fixture schema
   - Verify all fields populated (no null where required)

3. **Validation**: All ExcuseType enum values appear in events (sample one event per type)

**RabbitMQ event `excuse.decided` (AC-8):**

1. **Schema**:
   ```json
   {
     "type": "excuse.decided",
     "ticketId": "<ObjectId>",
     "studentId": <long>,
     "decisionBy": <long>,
     "status": "<approved|rejected>",
     "decisionComment": "<string>",
     "decidedAt": "<ISO-8601>"
   }
   ```

2. **Bot handler test** (`test_excuse_decided.py`):
   - Mock RabbitMQ consumer receives event
   - Handler parses and calls `send_student_alert()`
   - Verify bot sends Telegram message with correct decision + decisionComment

### Invariants to Verify (Branch Coverage & State Checks)

| Invariant | How Verified | Coverage Target |
|-----------|--------------|-----------------|
| One active ticket per (studentId, lessonId) | Query DB after create; only 1 with status PENDING/APPROVED | ≥ 80% branch coverage on duplicate check logic |
| Only own tickets visible to STUDENT | Query via GET /me; verify returned tickets have studentId == JWT.sub | 100% of visibility checks |
| Cascade atomicity: status + AttendanceRecord | Transactional test: Mongo write fails → both rollback; RabbitMQ fails → outbox mechanism | ≥ 80% |
| Event ↔ DB consistency | Outbox pattern verified: event published IFF Mongo doc inserted (or vice versa) | All happy-path + failure paths |
| Headman can't self-approve | Test (headman=123 creates ticket, tries to approve as 123) → 409 | 100% of self-check logic |
| All ExcuseType enum values exercised | Unit test iterates all 6 enum values; assert each serializes/deserializes | 100% enum coverage |
| AttendanceStatus mapping (excuseType → status) | Unit test: ILLNESS → EXCUSED, FREE_ATTENDANCE → FREE_ATTENDANCE, others → EXCUSED | 100% of mapping logic |

### Test Decomposition by Wave

**Wave 0 (Unit + Fast Integration, < 5 min):**
- `ExcuseServiceTest` — CRUD logic without DB
  - Create validation: comment length, lessonIds empty check, role check
  - Duplicate lessonId detection (mocked repo)
  - Enum serialization / deserialization
  - Cascade logic: mock AttendanceWritePort, verify mark() calls
  
- `ExcuseAssemblerTest` — DTO ↔ Entity mapping
  
- `ExcuseEventPublisherTest` — event JSON structure (mock RabbitTemplate)

- `frontend/student/excuses.component.spec.ts`:
  - ExcuseType dropdown renders all 6 values
  - Form submission calls submitExcuse() with correct payload
  - List displays own tickets (mock API response)

**Wave 1 (Integration, TestContainers, ~10 min):**
- `ExcuseControllerIT` — full request/response cycle (real MongoDB, RabbitMQ container)
  - AC-1: POST → 201
  - AC-2: Duplicate → 409
  - AC-3: Headman create → 409
  - AC-4: Other student GET → 403
  - AC-5: PATCH approve → cascades, attendances updated
  - AC-6: Self-approve → 409

- `ExcuseEventContractTest` — RabbitMQ event JSON schema validation (real exchange)

- `schedule-service LessonsByIdsIT` — gRPC endpoint test (AC-11)

- `frontend/student/excuses.component.it.ts` (if time allows):
  - HTTP calls to real mock backend
  - List update after create

**Wave 2 (Bot Integration, ~5 min):**
- `pytest test_excuse_decided.py` — bot consumer integration
  - Mock RabbitMQ event received
  - Handler parses and sends Telegram alert
  - Verify message content

**Wave 3 (E2E/Smoke, ~10 min):**
- Manual UAT: STUDENT creates → HEADMAN approves → STUDENT sees status → Telegram alerts received
- Regression check: existing attendance tests still green

### Performance Gating

| Requirement | Metric | Sampling Method | Gate |
|-------------|--------|-----------------|------|
| NFR-3 (p95 ≤ 200 ms) | Latency POST /excuses | Load test 100 rqs/sec for 10s; measure p95 | p95 ≤ 200 ms |
| Database query optimization | Index usage | EXPLAIN plan on duplicate-check query | All queries use indexes on (studentId, status, lessonIds) |

### Regression Checks (Existing Features)

After implementing excuse tickets:

- [ ] `./gradlew :services:attendance-service:attendance-app:test` — all existing tests pass (CheckinServiceTest, LessonEventServiceTest, etc.)
- [ ] `./gradlew :services:schedule-service:schedule-app:test` — proto extension doesn't break existing gRPC rpcs
- [ ] `cd services/notification-bot && pytest tests/` — existing headman_alerts test + attendance_marked test still pass
- [ ] `cd frontends/web-panel && npm test -- --run` — 129 existing vitest tests pass without modification
- [ ] `cd frontends/pwa && npm test -- --run` — 63 existing PWA tests pass

### Security & Compliance Checks

| Check | Test | Method |
|-------|------|--------|
| Role-based access control | Unauthenticated, TEACHER, ADMIN access → 403 | Integration test with @RequireRole validation |
| Data isolation by group | Headman of group A cannot see group B tickets | Query DB; assert returned tickets all have groupId == own |
| XSS prevention (comment field) | Store `<script>alert('xss')</script>`; render in UI → escaped | Frontend component test: `textContent` check, not `innerHTML` |
| MongoDB injection | Pass `{"$ne": null}` in comment → treated as string, not operator | Verify Mongo doc has literal string in DB |
| Sensitive data in RabbitMQ events | Check event payloads don't contain JWT tokens, passwords, chat_ids | Code review + event capture in integration test |

### Test Data & Fixtures

**Backend (Java Testcontainers):**
- `TestData.java` — factory methods for ExcuseTicket, Student, Group, Lesson
- MongoDB indexes auto-created by Testcontainers fixture
- RabbitMQ exchange/queue declared in test setup

**Bot (Python pytest):**
- `tests/fixtures/excuse_requested.json` — canonical event schema
- `conftest.py` — async event loop, mock bot, redis client

**Frontend (Vitest):**
- Mock `StudentApiService.submitExcuse()`, `getExcuseTickets()` responses
- Mock JWT claim: `role=STUDENT, is_headman=false/true, group_id=1`

---

## Metadata

**Validation Confidence:** HIGH
- Existing Testcontainers infrastructure in place (CheckinIntegrationTest precedent)
- Event-driven patterns established (RabbitMQ + bot integration proven in v5.0)
- Role-based access control patterns well-tested in existing layers
- Test durations estimated conservatively; actual may be faster

**Sampling Rationale:**
- Input dimensions selected to cover decision boundaries (role, ownership, enum values)
- Failure modes address infrastructure risks (RabbitMQ, gRPC, race conditions)
- Invariant tests ensure domain rules (unique active ticket, isolation, atomicity)
- Contract tests guarantee bot integration (D-27 requirement)

**Valid Until:** 2026-05-14 (30 days — stable microservice architecture)
