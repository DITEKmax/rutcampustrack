---
phase: 58-admin-bug-006-fixes
plan: 07
type: execute
wave: 4
depends_on: [06]
files_modified:
  - event-schemas/group.renamed.json
  - event-schemas/group.archived.json
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupRenamedEvent.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupArchivedEvent.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/GroupRenameEventTest.java
  - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java
  - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/GroupEventTest.java
  - services/notification-bot/bot/notifications/group_renamed.py
  - services/notification-bot/bot/notifications/group_archived.py
  - services/notification-bot/bot/consumers/event_dispatcher.py
  - services/notification-bot/tests/test_group_renamed.py
  - services/notification-bot/tests/test_group_archived.py
autonomous: true
requirements:
  - BUG-006-6
  - FR-6
  - NFR-3
user_setup: []
must_haves:
  truths:
    - "Событие group.renamed публикуется из academic-service через ApplicationEventPublisher → DomainEventListener (существующий) → RabbitMQ fanout 'rut-uit.events'"
    - "Событие group.archived аналогично"
    - "Payload минимальный: {group_id}. Consumers делают gRPC GetGroup для деталей"
    - "JSON Schema для обоих событий создана в event-schemas/"
    - "notification-service EventConsumer автоматически маршрутизирует group.* в /topic/group/{groupId} (работает по общему пути, т.к. payload содержит group_id)"
    - "WebPushDeliveryService.shouldPush расширен: group.renamed и group.archived возвращают true"
    - "notification-bot имеет handler'ы group_renamed и group_archived, зарегистрированные в EventDispatcher"
    - "Handler-ы шлют push/telegram всем студентам группы через AcademicGrpcClient.getGroup + getStudentsByGroupId"
    - "GroupService.update публикует GroupRenamedEvent если name изменилось"
    - "GroupService.update при ручной архивации (is_active true→false) публикует GroupArchivedEvent — ПРИМЕЧАНИЕ: пока PUT блокирует переключение is_active (нет UI кнопки архивации вручную в этой фазе); архивация через промоушен покрыта планом 06"
  artifacts:
    - path: event-schemas/group.renamed.json
      provides: "JSON Schema envelope + payload.group_id"
    - path: event-schemas/group.archived.json
      provides: "JSON Schema envelope + payload.group_id"
    - path: services/notification-bot/bot/notifications/group_renamed.py
      provides: "handle_group_renamed(event) → gRPC GetGroup + send telegram"
  key_links:
    - from: GroupPromotionService (plan 06)
      to: GroupRenamedEvent / GroupArchivedEvent
      via: ApplicationEventPublisher
      pattern: "AFTER_COMMIT → RabbitMQ"
    - from: GroupService.update (manual rename)
      to: GroupRenamedEvent
      via: ApplicationEventPublisher (if name changed)
      pattern: "manual trigger"
    - from: RabbitMQ fanout rut-uit.events
      to: notification-service EventConsumer
      via: "queue notification-web.events"
      pattern: "existing routing"
    - from: RabbitMQ fanout
      to: notification-bot EventDispatcher
      via: existing EventConsumer
      pattern: "existing routing"
---

<objective>
Подключить события промоушена и ручного переименования к уже существующей notification-инфраструктуре (RabbitMQ fanout + notification-service Java для Web Push/STOMP + notification-bot Python для Telegram).

План 06 уже создал placeholder-классы `GroupRenamedEvent` и `GroupArchivedEvent` и вызывает их. Этот план:
1. Формализует JSON Schema (event-schemas/).
2. Регистрирует события в WebPushDeliveryService.shouldPush().
3. Добавляет handler-ы в notification-bot.
4. Добавляет публикацию GroupRenamedEvent при ручном PUT /groups/{id} если name изменилось.

Purpose: BUG-006-6 (уведомления об изменении группы), AC-7.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-06-SUMMARY.md
@CLAUDE.md
@event-schemas/group.updated.json
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupUpdatedEvent.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java
@services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java
@services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java
@services/notification-bot/bot/consumers/event_dispatcher.py
@services/notification-bot/bot/notifications/lesson_cancelled.py

<interfaces>
<!-- Existing envelope: {event_type, event_id, occurred_at, payload} -->
<!-- payload MUST contain group_id for EventConsumer auto-routing to /topic/group/{groupId} -->
<!-- Consumers do gRPC GetGroup for full info (name, isActive, studentIds) -->
<!-- Plan 06 creates event classes; this plan formalizes schemas + downstream consumers -->
-->
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: JSON Schemas для group.renamed и group.archived</name>
  <files>
    event-schemas/group.renamed.json,
    event-schemas/group.archived.json
  </files>
  <action>
    1. `event-schemas/group.renamed.json`:
       ```json
       {
         "$schema": "https://json-schema.org/draft/2020-12/schema",
         "title": "group.renamed",
         "description": "Группа переименована (ручное редактирование админом или автопромоушен). Генерируется Academic Service.",
         "type": "object",
         "required": ["event_type", "event_id", "occurred_at", "payload"],
         "properties": {
           "event_type": { "const": "group.renamed" },
           "event_id": { "type": "string", "format": "uuid" },
           "occurred_at": { "type": "string", "format": "date-time" },
           "payload": {
             "type": "object",
             "required": ["group_id"],
             "properties": {
               "group_id": { "type": "integer" }
             }
           }
         }
       }
       ```
    2. `event-schemas/group.archived.json` — аналогично, с title/description "Группа архивирована (промоушен выпускного курса или ручная архивация)".
  </action>
  <verify>
    <automated>python -c "import json; json.load(open('event-schemas/group.renamed.json')); json.load(open('event-schemas/group.archived.json'))"</automated>
  </verify>
  <done>Оба файла валидный JSON, следуют паттерну group.updated.json.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: GroupService.update — публикация GroupRenamedEvent</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/GroupRenameEventTest.java
  </files>
  <behavior>
    - Test 1: GroupService.update(id, {name:"УИТ-311"}) на группе с name="УИТ-312" → publisher.publishEvent(GroupRenamedEvent(id)) вызван
    - Test 2: GroupService.update(id, {name:"УИТ-312"}) на группе с тем же name → НЕ публикует (имя не изменилось)
    - Test 3: GroupService.update(id, {isActive:true}) без изменения name → НЕ публикует GroupRenamedEvent
    - Test 4 (integration): update + @TransactionalEventListener AFTER_COMMIT → RabbitMQ получает сообщение (через test-containers RabbitMQ; smoke check — есть существующий AbstractAcademicEventIntegrationTest для примера)
  </behavior>
  <action>
    1. В `GroupService.update(Long id, UpdateGroupRequest req)`:
       ```java
       String oldName = group.getName();
       // ... apply changes
       if (req.name() != null && !req.name().equals(oldName)) {
           // pre-check conflict (per Plan 02/04 pattern)
           if (groupRepository.existsByName(req.name())) throw new ConflictException("name", ...);
           group.setName(req.name());
           publisher.publishEvent(new GroupRenamedEvent(this, group.getId()));
       }
       ```
    2. Integration test: скопировать паттерн из `AbstractAcademicEventIntegrationTest` + добавить case на rename.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupRename*" --tests "*GroupService*Test*"</automated>
  </verify>
  <done>События публикуются только при реальном изменении name.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: WebPushDeliveryService.shouldPush расширить</name>
  <files>
    services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java,
    services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/GroupEventTest.java
  </files>
  <behavior>
    - Test 1: shouldPush("group.renamed") → true
    - Test 2: shouldPush("group.archived") → true
    - Test 3: EventConsumer получает envelope {event_type:"group.renamed", payload:{group_id:42}} → SimpMessagingTemplate шлёт на /topic/group/42 + sendToGroup(42, "group.renamed", ...) вызывается
    - Test 4: PushPayload для group.renamed содержит понятный title ("Группа переименована") и body (gRPC GetGroup возвращает new name)
  </behavior>
  <action>
    1. В `WebPushDeliveryService.shouldPush(String eventType)` добавить в whitelist:
       ```java
       private static final Set<String> PUSH_EVENTS = Set.of(
           "lesson.started", "lesson.cancelled", "lesson.closed",
           "attendance.marked",  // если был
           "homework.published", "homework.updated",
           "group.renamed", "group.archived"  // NEW
       );
       ```
       (Точный список существующих — смотреть grep'ом в `WebPushDeliveryService`, не переписывать; только добавить два новых.)
    2. В методе, который формирует PushPayload (title/body по event_type) — добавить:
       ```java
       case "group.renamed" -> new PushPayload(
           "Группа переименована",
           "Ваша группа получила новое название: " + groupName, // groupName через gRPC
           "/student"
       );
       case "group.archived" -> new PushPayload(
           "Группа архивирована",
           "Группа " + groupName + " архивирована (выпуск).",
           "/student"
       );
       ```
       Если текущий сервис формирует payload через switch — дополнить. Если через strategy-map — добавить запись. Паттерн определяется в Task по грепу.
    3. Test: `GroupEventTest` — MockMvc/integration не нужен; достаточно unit на EventConsumer + shouldPush.
  </action>
  <verify>
    <automated>./gradlew.bat :services:notification-service:notification-app:test --tests "*GroupEventTest*" --tests "*WebPushDeliveryServiceTest*"</automated>
  </verify>
  <done>Оба события маршрутизируются через STOMP и Web Push с корректным payload.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: notification-bot handler-ы + регистрация в EventDispatcher</name>
  <files>
    services/notification-bot/bot/notifications/group_renamed.py,
    services/notification-bot/bot/notifications/group_archived.py,
    services/notification-bot/bot/consumers/event_dispatcher.py,
    services/notification-bot/tests/test_group_renamed.py,
    services/notification-bot/tests/test_group_archived.py
  </files>
  <behavior>
    - Test 1: handle_group_renamed({event_type, payload:{group_id:42}}) → AcademicGrpcClient.get_group(42) called → send_queue.send(chat_id, text) для каждого студента группы
    - Test 2: Текст сообщения содержит новое название (формируется из gRPC response)
    - Test 3: handle_group_archived аналогично, с текстом о выпуске
    - Test 4: EventDispatcher dispatch({event_type:"group.renamed", ...}) → routes to handle_group_renamed
    - Test 5: EventDispatcher dispatch({event_type:"group.archived", ...}) → routes to handle_group_archived
    - Test 6: Exceptions в handler'ах ловятся (как все остальные) — не падает pipeline
  </behavior>
  <action>
    1. `bot/notifications/group_renamed.py`:
       ```python
       import logging
       from bot.grpc_client.academic_client import AcademicGrpcClient
       from bot.services.send_queue import TelegramSendQueue

       logger = logging.getLogger(__name__)

       async def handle_group_renamed(event: dict, *, bot, academic_client: AcademicGrpcClient, send_queue: TelegramSendQueue) -> None:
           payload = event.get("payload") or {}
           group_id = payload.get("group_id")
           if group_id is None:
               logger.warning("group.renamed without group_id: %s", event)
               return
           group = await academic_client.get_group(group_id)
           students = await academic_client.get_students_by_group(group_id)
           text = f"📝 Ваша группа переименована: <b>{group.name}</b>"
           for s in students:
               if s.telegram_id:
                   await send_queue.send(s.telegram_id, text, parse_mode="HTML")
       ```
       (Если `AcademicGrpcClient` не имеет `get_students_by_group` — посмотреть grep, скорее всего есть; если нет — добавить в contract и proto. Но это уже реализовано по существующим flow `lesson.started`.)
    2. `bot/notifications/group_archived.py` — аналогично, текст «🎓 Группа <b>{name}</b> архивирована (выпуск).»
    3. `bot/consumers/event_dispatcher.py` — добавить в registry:
       ```python
       from bot.notifications.group_renamed import handle_group_renamed
       from bot.notifications.group_archived import handle_group_archived

       # в __init__:
       self._handlers["group.renamed"] = lambda event: handle_group_renamed(event, bot=self._bot, academic_client=self._academic_client, send_queue=self._send_queue)
       self._handlers["group.archived"] = lambda event: handle_group_archived(event, bot=self._bot, academic_client=self._academic_client, send_queue=self._send_queue)
       ```
    4. Тесты на pytest — скопировать паттерн `test_lesson_cancelled.py` / `test_event_dispatcher.py`. Мокать `academic_client` и `send_queue`.
  </action>
  <verify>
    <automated>cd services/notification-bot && pytest tests/test_group_renamed.py tests/test_group_archived.py tests/test_event_dispatcher.py -v</automated>
  </verify>
  <done>Handler-ы зелёные; EventDispatcher корректно маршрутизирует.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| RabbitMQ fanout | internal, все сервисы доверяют событиям academic-service |
| Telegram API через send_queue | external, rate-limited очередью (существующий паттерн) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-07-01 | Tampering (event forged) | RabbitMQ | accept | fanout internal only, нет внешнего доступа |
| T-58-07-02 | Integrity (event sent if tx rolls back) | DomainEventListener | mitigate | `@TransactionalEventListener(AFTER_COMMIT)` — существующая гарантия (см. RabbitConfig.java D-04) |
| T-58-07-03 | Information Disclosure (push shows other group's data) | EventConsumer routing | mitigate | Events маршрутизируются на `/topic/group/{groupId}`; подписчики — студенты ТОЙ группы; STOMP-auth уже проверяет subscribe (работает для lesson.* — тот же путь) |
| T-58-07-04 | Denial of Service (bot flood) | bot handle_group_renamed | mitigate | TelegramSendQueue уже rate-limit'ит (существующий; см. NFR из v5.0) |
| T-58-07-05 | Integrity (handler exception breaks RabbitMQ consumer loop) | EventDispatcher.dispatch | mitigate | Существующий `try/except` в EventDispatcher ловит и логирует все handler exceptions |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — зелёные
- `./gradlew.bat :services:notification-service:notification-app:test` — зелёные
- `cd services/notification-bot && pytest` — зелёные
- Manual end-to-end:
  1. Запустить docker-compose + все Java + bot
  2. ADMIN через curl/UI переименовывает группу через PUT /groups/{id}
  3. Telegram бот присылает студенту сообщение "Ваша группа переименована: ..."
  4. В браузере PWA получает Web Push "Группа переименована"
  5. WebSocket подписчик на /topic/group/{id} видит JSON {type:"group.renamed", payload:{group_id:N}}
</verification>

<success_criteria>
- AC-7 частично: промоушен и ручное переименование триггерят уведомления
- Payload минимальный ({group_id}) — паттерн D-01 соблюдён
- JSON schemas добавлены
- 4 канала доставки работают: STOMP, Web Push, Telegram (+ WebSocket через notification-service)
- Идемпотентность не требуется — события информационные
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-07-SUMMARY.md`.

## Commit message
`feat(notifications): group.renamed/group.archived events + web push + telegram handlers (BUG-006-6)`
</output>

## UAT Steps
1. Backend + notification-service + bot запущены, docker-compose up
2. ADMIN создаёт УИТ-111, 1 студент в группе с telegram_id
3. ADMIN переименовывает УИТ-111 → УИТ-112 через PUT
4. Студент получает Telegram "Ваша группа переименована: УИТ-112"
5. PWA студента (если залогинен) получает Web Push
6. ADMIN запускает промоушен (план 06) → УИТ-111 → УИТ-211 — студент получает оба push + telegram
7. Если группа попадает в архив (4 курс) — получает group.archived с текстом о выпуске
