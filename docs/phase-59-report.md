# Phase 59 Report: Excuse Tickets Backend

**Завершена:** 2026-04-14 (UAT отложен — см. Known limitations)
**Milestone:** v9.0 Frontend Unification (расширение — уважительные пропуски)
**Requirements закрыты:** AC-1..AC-8, AC-11, AC-12 (AC-9, AC-10 — manual UAT)
**Источник:** `.planning/bug-reports/BUG-008-student+pwa/excuses-backend-spec.md`
**Контекст фазы:** `.planning/phases/59-excuses-backend/59-CONTEXT.md` (D-01..D-29)

## Цель

Реализовать backend для тикетов о пропуске занятий — функционал, существование которого предусмотрено в нескольких местах системы (UI-шаблоны в headman, student, `ExcuseType` enum, `headman_alerts.py`), но сам REST API отсутствовал. Включает:

- MongoDB коллекцию `excuse_tickets` с lowercase enum converters.
- Полный REST контракт `ExcuseApi` через `/api/attendance/excuses` (5 эндпоинтов).
- gRPC `GetLessonsByIds` в schedule-service для валидации lessonIds (D-25).
- Каскадное обновление attendance-документов при одобрении тикета (D-16).
- RabbitMQ events `excuse.requested` / `excuse.decided` для бота.
- Python handler `handle_student_alert` для DM студенту о решении.
- Frontend: `ExcuseType` dropdown на форме студента + полная страница старосты с approve/reject.
- IT тесты с Testcontainers покрывающие happy path и все rejection пути.

## Результаты по планам

### Plan 59-01: Domain & Contract (Wave 1)

- `ExcuseApi` interface (attendance-api-contract) с 5 методами, полная аннотированная Swagger-документация 201/400/403/404/409.
- Records: `CreateExcuseRequest`, `UpdateExcuseStatusRequest`. Response class `ExcuseTicketResponse extends RepresentationModel` (HATEOAS).
- `@Document excuse_tickets` с `@Field` snake_case mapping, 13 полей, default status SUBMITTED.
- `ExcuseRepository` с derived-query `existsByStudentIdAndLessonIdsInAndStatusIn` (D-11) + status-фильтры.
- 4 Mongo converter'а для lowercase enum storage (`ExcuseType` / `ExcuseTicketStatus`).
- Tests: `ExcuseRepositoryTest` 3 кейса.

### Plan 59-02: Service + Controller (Wave 2)

- `ExcuseService` реализует D-10..D-18: headman-block на create, duplicate-check, self-approve-block, decision-is-final.
- `ExcuseController implements ExcuseApi` — 5 override-методов, `@RequireRole(STUDENT)` на каждом (D-09).
- `ExcuseAssembler` с HATEOAS self-link, lowercase status в response.
- gRPC `AcademicGrpcClient.getUserDisplayName` (D-26) с fallback `"Студент #id"` на пустом display_name.
- Tests: `ExcuseServiceTest` 10 кейсов (AC-1..AC-6, D-11..D-18).

### Plan 59-03: gRPC LessonsByIds (Wave 2)

- Новый rpc `GetLessonsByIds` в `proto/schedule.proto` + `LessonInfo { lesson_id, group_id, subject_id, starts_at }`.
- `ScheduleGrpcServiceImpl.getLessonsByIds` — batch `findAllById` + in-memory join по scheduleItemId, orphan tolerance.
- Client wrapper в `ScheduleGrpcClient.getLessonsByIds(List<Long>)` с deadline 3s и short-circuit на empty input.
- Tests: `LessonsByIdsGrpcIT` 3 кейса (happy / not-found-empty / empty-request).

### Plan 59-04: Attendance Cascade (Wave 3)

- `AttendanceWritePort` интерфейс в `shared/port/` + `AttendanceWritePortImpl` adapter в `checkin/` (upsert по `findByLessonIdAndUserId`).
- Новое значение enum `AttendanceSource.HEADMAN_EXCUSE`.
- `ExcuseService.updateStatus` на APPROVED — итерирует lessonIds и вызывает `attendanceWritePort.mark(...)` для каждого; mapping `FREE_ATTENDANCE → FREE_ATTENDANCE`, остальное → `EXCUSED`.
- Tests: `ExcuseServiceApproveIT` 4 кейса (ILLNESS/FREE_ATTENDANCE/REJECT-noop/overwrite-existing-PRESENT).

### Plan 59-05: Event Publisher (Wave 3)

- `ExcuseEventPublisher` — `publishRequested` / `publishDecided` с envelope `{event_type, event_id, occurred_at, payload}` на fanout `rut-uit.events`.
- Lowercase enum на publish-site (`name().toLowerCase()`), не на уровне `@JsonValue` — не ломает REST сериализацию.
- Hook-points: `createExcuse` после `save(...)`, `updateStatus` после D-16 cascade и до `return`.
- Canonical fixtures в `src/test/resources/fixtures/` + зеркало в `services/notification-bot/tests/fixtures/`.
- Tests: `ExcuseEventPublisherTest` 4 unit + `ExcuseEventContractIT` 2 IT с test-queue на fanout exchange.

### Plan 59-06: Bot Consumer excuse.decided (Wave 4)

- `bot/notifications/student_alerts.py:handle_student_alert` — новый async handler, подписка в `EventDispatcher._handlers["excuse.decided"]`.
- Resolve `telegram_id` через `AcademicGrpcClient.get_user_by_id(user_id)` (новый метод, обёртка над уже-существующим `GetUserById` proto rpc).
- Ru texts: `✅ ...одобрен.` / `❌ ...отклонён.` + строка `Комментарий: {text|без комментария}`.
- SendTask через существующий TelegramSendQueue (rate limiter + notification prefs filter).
- Tests: `test_excuse_decided.py` 6 кейсов (approved/rejected/no-comment/malformed-payload/unknown-status/no-telegram).

### Plan 59-07: Student Excuse Form UI (Wave 4)

- `student-schedule.types.ts`: `ExcuseType` union (6 lowercase), `ExcuseTicketStatus` (draft/submitted/approved/rejected), `ExcuseTicket` interface, `EXCUSE_TYPE_LABELS` Russian map.
- `StudentApiService.getExcuseTickets()` → `GET /api/attendance/excuses/me?page=0&size=20` с unwrap HATEOAS.
- `StudentApiService.submitExcuse(lessonIds, excuseType, comment)` → JSON POST (убрана FormData + files).
- `ExcuseFormDialog` — `MatSelectModule` + dropdown ExcuseType required + maxLength comment=1000. Drop-zone + file chips удалены (D-03).
- `StudentExcusesComponent` — колонки «Дата / Причина / Занятий / Статус», `statusLabels` приведены к backend (`submitted → На рассмотрении` и т.д.).
- Tests: +12 кейсов (дропдаун, validation, JSON submit, list rendering). Всего 358/358 зелёные.

### Plan 59-08: Headman Excuses Approval UI (Wave 4)

- `features/headman/excuses/excuse.types.ts` — локальные типы (параллель с 59-07; будущая консолидация в shared).
- `HeadmanApiService.getGroupExcuses(groupId, status?)` / `approveExcuse(id, comment?)` / `rejectExcuse(id, comment)`.
- `HeadmanExcusesComponent` полный rewrite: signals + computed, `pendingTickets` vs `resolvedTickets`, inline reject-comment per card, 403/404/409 Russian error mapper, empty-state `noGroup`.
- Tests: 8 кейсов — load, split sections, approve, reject-validation, reject-happy, 409 mapper, 500 banner, noGroup guard.

### Plan 59-09: gRPC Validation Wire-up + IT + Report (Wave 5, эта фаза)

- `ExcuseService.validateLessonIds()` — вызов `scheduleGrpcClient.getLessonsByIds(...)` в `createExcuse`, отбрасывает unknown ids и lessons чужой группы с `BadRequestException`.
- `ExcuseControllerIT` — 7 @SpringBootTest + Testcontainers тестов (AC-1..AC-6 + D-18): полный REST→Mongo→RabbitMQ happy path и все rejection пути.
- Стабилизация ITs под реальным Docker: `ExcuseEventContractIT` — убрали autoDelete на test queue (replace with purgeQueue), `ExcuseServiceApproveIT` — millisecond-precision compare для `createdAt` (Mongo truncation).
- `.planning/phases/59-excuses-backend/59-VALIDATION.md` заполнен, `nyquist_compliant: true`.

## AC Coverage

| AC | Описание | Покрытие | Статус |
|----|----------|----------|--------|
| AC-1 | Студент POST /excuses → 201 | `ExcuseControllerIT#createExcuse_asPlainStudent_returns201...` + `ExcuseServiceTest` | ✅ |
| AC-2 | Duplicate lessonId → 409 | `ExcuseControllerIT#createExcuse_duplicateLessonId_returns409` | ✅ |
| AC-3 | Headman create → 409 | `ExcuseControllerIT#createExcuse_asHeadman_returns409` | ✅ |
| AC-4 | Foreign student GET → 403 | `ExcuseControllerIT#getTicketById_foreignStudent_returns403` | ✅ |
| AC-5 | Approve каскад на attendance | `ExcuseControllerIT#updateStatus_approvedByHeadman...` + `ExcuseServiceApproveIT` (4 кейса) | ✅ |
| AC-6 | Headman self-approve → 409 | `ExcuseControllerIT#updateStatus_headmanSelfApprove_returns409` | ✅ |
| AC-7 | RabbitMQ event matches bot contract | `ExcuseEventContractIT` 2 кейса | ✅ |
| AC-8 | Bot consumer excuse.decided | `notification-bot/tests/test_excuse_decided.py` 6 кейсов | ✅ |
| AC-9 | Frontend student создаёт тикет | manual UAT required | ⏳ UAT |
| AC-10 | Frontend headman approve/reject | manual UAT required | ⏳ UAT |
| AC-11 | gRPC LessonsByIds | `schedule-service:LessonsByIdsGrpcIT` 3 кейса | ✅ |
| AC-12 | Все тесты зелёные | см. таблицу ниже | ✅ (caveat) |

## Tests summary

| Компонент | Тестов новых | Итого | Статус |
|---|---|---|---|
| attendance-service unit | ~20 (Excuse*Test) | ~85 | ✅ |
| attendance-service IT | 16 (ExcuseControllerIT 7, ExcuseEventContractIT 2, ExcuseServiceApproveIT 4, LessonsByIdsGrpcIT 3) | ~48 | ✅ при focused / ⚠️ full (см. ниже) |
| schedule-service | 3 (LessonsByIdsGrpcIT) | ~55 | ✅ |
| notification-bot pytest | +6 (test_excuse_decided) +1 registry | 128 | ✅ (128/128) |
| frontend web-panel vitest | +12 student, +8 headman | 358 | ✅ (358/358) |

### Verification commands run locally

| Command | Result |
|---|---|
| `./gradlew :services:attendance-service:attendance-app:test --tests "*ExcuseControllerIT"` | **7/7 green** (0.95s) |
| `./gradlew :services:attendance-service:attendance-app:test --tests "*Excuse*"` | **all Excuse-scoped green** (unit + IT + contract + approve) |
| `./gradlew :services:schedule-service:schedule-app:test` | **BUILD SUCCESSFUL** |
| `cd services/notification-bot && pytest tests/ -q` | **128 passed** |
| `cd frontends/web-panel && npm test -- --run` | **358 passed** |

## Architecture decisions (ключевые)

- **`ExcuseTicketStatus.SUBMITTED` как pending.** CONTEXT D-02 описывал «pending», но enum контракта уже содержал `DRAFT / SUBMITTED / APPROVED / REJECTED`. SUBMITTED используется как начальный статус тикета, семантически эквивалентен pending. (Решение 59-01.)
- **`AttendanceWritePort` pattern.** Чтобы `excuse/` домен не импортировал из `checkin/` (правило изоляции доменов), введён порт-интерфейс в `shared/port/` и adapter-имплементация в `checkin/`. Маппинг `ExcuseType → AttendanceStatus` живёт в `ExcuseService`, а не в порту — порт остаётся domain-agnostic. (Решение 59-04.)
- **Envelope контракт для notification-bot.** `{event_type, event_id, occurred_at, payload}` с lowercase enum в payload — идентично паттерну `AttendanceEventPublisher`, чтобы бот мог reuse существующие парсеры. `payload.user_id` дублирует `student_id` ради совместимости с уже-написанным `headman_alerts.py:29`. (Решение 59-05.)
- **Фикстуры зеркалируются между attendance-app и notification-bot.** Explicit copy вместо shared `event-schemas/` — пути относительные pytest стабильны, диффы минимальны. (Решение 59-05.)
- **`ScheduleGrpcClient` НЕ инжектится в D-16 cascade.** Cascade знает `studentId/lessonId/groupId/status` из `ExcuseTicket` — gRPC lookup был бы лишним round-trip без добавления fidelity. Инжекция только в `createExcuse` для D-25 валидации. (Решение 59-04/09.)
- **Cascade выполняется ДО event publishing в `updateStatus`.** Journal consistency требует, чтобы attendance-документы были записаны до того как downstream consumer увидит `excuse.decided`. В коде есть комментарий, предупреждающий будущих редакторов не менять порядок. (Решение 59-05.)
- **Lowercase enum в события на publish-site (не `@JsonValue`).** Zero blast radius на REST responses, локализовано в одном месте. (Решение 59-05.)

## Known limitations / UAT pending

- **AC-9 / AC-10 — manual UAT.** Требуется ручной прогон по сценариям `59-VERIFICATION.md`:
  1. STUDENT создаёт тикет через `/student/excuses` → староста получает Telegram alert.
  2. HEADMAN открывает `/headman/excuses`, одобряет → студент получает DM «✅ Ваш запрос одобрен» + статус в UI меняется.
  3. STUDENT пытается создать второй тикет на тот же урок → видит «На один из выбранных уроков уже существует активный тикет».

- **Pre-existing flaky test под полной нагрузкой.** `EventConsumerIntegrationTest#semesterArchived_refreshesSemesterCache` периодически падает при прогоне полного `attendance-app:test` из-за race между `Mockito.reset(semesterCacheService)` в `@BeforeEach` и асинхронным консьюмером, дочитывающим сообщения из предыдущего теста. Test проходит при focused-запуске (`./gradlew test --tests "*EventConsumerIntegrationTest"`). Не связан с Phase 59 — проблема изоляции тестов в модуле. Tracked как **deferred** — требует либо `Awaitility` конфигурации с `ignoreExceptions`, либо отдельного `@DirtiesContext` per test.

## Dead code / housekeeping items (из 59-08)

- `HeadmanApiService.getPendingExcuses()` — наследие Phase 55 shell, больше не вызывается. **Safe to delete** в следующей housekeeping-фазе; оставлено в 59-09, чтобы не расширять scope.
- `features/headman/excuses/excuse.types.ts` дублирует `features/student/shared/student-schedule.types.ts`. **Консолидация в `shared/excuses/`** — low-risk refactor, отложен (не в scope 59).
- Тест `test_dispatcher_has_eight_event_types` в notification-bot сохранил старое имя, хотя теперь проверяет >8 event types — cosmetic, tracked для отдельной уборки.

## Новые эндпоинты

- `POST /api/attendance/excuses` — создать тикет (STUDENT)
- `GET /api/attendance/excuses/me?page&size&status?` — мои тикеты (STUDENT, paged HATEOAS)
- `GET /api/attendance/excuses/group/{groupId}?page&size&status?` — тикеты группы (STUDENT+headman)
- `GET /api/attendance/excuses/{id}` — детали тикета (owner OR headman of same group)
- `PATCH /api/attendance/excuses/{id}/status` — approve/reject (STUDENT+headman)

## Новые gRPC rpc

- `schedule.ScheduleGrpcService.GetLessonsByIds(LessonsByIdsRequest) → LessonsByIdsResponse` — batch lesson-info (AC-11)

## Новые события RabbitMQ (fanout `rut-uit.events`)

- `excuse.requested` — publishRequested при POST /excuses (D-19)
- `excuse.decided` — publishDecided при PATCH /status (D-20)

## Файлы артефактов

- Plans: `.planning/phases/59-excuses-backend/59-{01..09}-PLAN.md`
- Summaries: `.planning/phases/59-excuses-backend/59-{01..09}-SUMMARY.md`
- Context: `.planning/phases/59-excuses-backend/59-CONTEXT.md` (D-01..D-29)
- Verification: `.planning/phases/59-excuses-backend/59-VERIFICATION.md`
- Validation: `.planning/phases/59-excuses-backend/59-VALIDATION.md` (`nyquist_compliant: true`)

## Next steps

1. **Production UAT** — прогнать AC-9 / AC-10 на deployed stack (Docker Desktop + `docker compose up -d`).
2. **Housekeeping plan** — удалить `getPendingExcuses()`, консолидировать excuse types в shared.
3. **Follow-up фаза**: Telegram attachments для excuse-тикетов (D-03, вынесено из scope).
4. **Follow-up фаза**: late check-in tickets (похожий flow для запоздалой отметки).
5. **Test isolation cleanup**: стабилизировать `EventConsumerIntegrationTest` под полной нагрузкой `./gradlew :attendance-app:test`.
