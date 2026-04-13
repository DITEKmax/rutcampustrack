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
