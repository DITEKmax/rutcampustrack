# 03. Schedule Service — отчёт аудита

## Сводка

Schedule Service — владелец расписания: шаблоны (`schedule_items`), материализованные экземпляры пар (`lessons`), разовые пары (`schedule_one_off_lessons`), автоматические переходы статуса (`LessonStatusTransitionJob`) и one-shot reconciler для выравнивания ISO-чётности (`IsoParityReconciler`). Стек: Spring Boot 3.4 + Spring Data JPA (PostgreSQL `schedule_db`) + Spring AMQP (fanout `rut-uit.events`) + gRPC (net.devh, клиент к academic-service + собственный сервер) + Spring HATEOAS + Flyway V1..V9. Contract-first соблюдён: `schedule-api-contract` — чистый `java-library` без Spring Boot; Request = record, Response = class extends `RepresentationModel`. Controllers `implements XxxApi`, маппинги — только в интерфейсах. AccountStatus/UserRole/WeekType/LessonStatus — без `@Enumerated(EnumType.ORDINAL)`, используется `LowercaseEnumConverter` с `autoApply=true`. `ddl-auto: validate`, Flyway включён. RFC 7807 через `GlobalExceptionHandler`. `@TransactionalEventListener(AFTER_COMMIT)` аккуратно разорвал сервис от AMQP. DLQ описана. Тестов ~16 файлов (integration + unit), covers все ключевые сценарии.

Но архитектурно сервис несёт несколько **критичных** болячек. (1) `UserContextFilter` слепо доверяет заголовкам `X-User-Id`/`X-User-Role`/`X-Is-Headman` — аналог известного риска из academic-service; при прямом доступе к порту 9092 любой может представиться старостой или ADMIN-ом, а в `application-prod.yml` нет ни mTLS, ни allowlist-а клиентов. (2) `NumberFormatException`/`IllegalArgumentException` в `UserContextFilter` при битом заголовке превращается в 500 Internal Server Error (RFC 7807 не соблюдён) и не логируется аккуратно. (3) Role-matrix `@RequireRole` на write-endpoint`ах содержит `UserRole.STUDENT` вместо именного права — расчёт на флаг `is_headman` в `requireHeadmanForGroup`, но для чисто ADMIN-эндпоинтов `STUDENT` лишний: любой студент получит 200 в `requireHeadmanForGroup`, только если у него выставлен `X-Is-Headman: true`, что, впрочем, полностью контролирует клиент/атакующий. (4) `IsoParityReconciler` вычитывает `ScheduleItem` одного семестра через **raw JPQL** без fetch-size: при 1000+ активных slot`ах блокирует loader-транзакцию и ломает startup-health ≥ 60с (CI/deploy зависит). (5) Race between two-phase `LessonStatusTransitionJob`: `@Scheduled(fixedDelay=60_000)` + отсутствие distributed lock → при двух инстансах (запланированный масштабинг) оба одновременно опубликуют `lesson.started` / `lesson.closed` — двойные push-уведомления и двойные `attendance.session.closed`. (6) `OneOffLessonService.computeWeekTypeForDate` использует **старый** (семестр-относительный) алгоритм чётности, а `LessonGenerationService.computeLessonDates` после фикса 5e139b3 работает **от ISO week** — при конфликте шаблона и разовой пары получается ложное 409 / ложный «ОК». (7) Flyway V8/V9 — два последовательных «reset marker» — это «миграция-патч» над багом; приемлемо в дев-цикле, но в отчёте перед релизом v0.0.0 лежит как симптом незакрытой до конца истории выравнивания чётности. (8) `channel.transacted=false` в `RabbitConfig` + отсутствие outbox-таблицы: падение RabbitMQ между commit и `convertAndSend` (Jackson-сериализация, connection timeout) теряет событие безвозвратно — отчёт academic-service указал этот же недостаток; в schedule он ещё опаснее, т.к. `lesson.started`/`lesson.closed` — единственный сигнал, по которому attendance создаёт ведомость.

Мелких проблем — россыпь: `listScheduleItems` читает весь `Page<ScheduleItem>` без fetch join; `LessonService.getLessonsForGroup` считает pagination **in-memory** (`PageImpl` над вычислением `subList`) — при 10k+ пар для группы это OOM; `MassCancelLessons` не проверяет интервал (`dateFrom <= dateTo`); `CancelLessonRequest` позволяет отмену CLOSED-пары, что по CLAUDE.md «cancelled → не влияет на статистику» — то есть выкидывание посещаемости задним числом; `HealthCheckController` — мёртвый кусок из Phase 10 (`GET /schedule/health-check` доступен любому аутентифицированному); `applicaton.yml` логирует `ru.rutcampustrack` в DEBUG в проде — утечка SQL-параметров и идентификаторов учёток в stdout контейнера; gRPC secret опционален (`expectedSecret` nullable → bypass в деве легко перетечёт в прод если забыли `${GRPC_SECRET}` задать).

**Счётчики:** **P0 = 5**, **P1 = 12**, **P2 = 16**, **P3 = 10**.

## Структура модулей

```
services/schedule-service/
├── Dockerfile                           ← (only in schedule-app)
├── schedule-api-contract/               ← java-library (НЕ Spring Boot) ✓
│   └── src/main/java/ru/rutcampustrack/schedule/contract/
│       ├── api/
│       │   ├── LessonApi.java           ← @RequestMapping("/schedule")
│       │   ├── ScheduleItemApi.java     ← @RequestMapping("/schedule/items")
│       │   └── OneOffLessonApi.java     ← @RequestMapping("/schedule/one-off-lessons")
│       ├── dto/
│       │   ├── item/     {CreateScheduleItemRequest, UpdateScheduleItemRequest, ScheduleItemResponse}
│       │   ├── lesson/   {CancelLessonRequest, GeoBlockRequest, MassCancelRequest,
│       │   │              MassCancelResponse, LessonResponse}
│       │   └── oneoff/   {CreateOneOffLessonRequest, OneOffLessonResponse}
│       ├── enums/ {LessonStatus, UserRole, WeekType}
│       └── exception/ {ErrorResponse}
│
├── schedule-app/                         ← Spring Boot
│   └── src/main/java/ru/rutcampustrack/schedule/
│       ├── ScheduleApplication.java
│       ├── config/
│       │   ├── ClockConfig.java          ← Clock.system(Europe/Moscow)
│       │   ├── EnumConverters.java       ← 2 inner @Converter(autoApply=true)
│       │   └── SchedulingConfig.java     ← @Profile("!test") @EnableScheduling
│       ├── event/
│       │   ├── DomainEvent.java          ← abstract + eventType/eventId/occurredAt
│       │   ├── DomainEventListener.java  ← @TransactionalEventListener(AFTER_COMMIT)
│       │   ├── EventConsumer.java        ← @RabbitListener(queues="schedule-service.events")
│       │   ├── Lesson*/OneOff*Event.java (7 classes)
│       │   └── RabbitConfig.java         ← FanoutExchange + DLQ
│       ├── exception/                    ← GlobalExceptionHandler + 5 custom exceptions
│       ├── grpc/
│       │   ├── AcademicGrpcClient.java   ← 3-sec deadlines, translate StatusRuntimeException
│       │   ├── GrpcAuthInterceptor.java  ← IMP-09 x-grpc-secret header
│       │   ├── GrpcSecretClientInterceptor.java
│       │   ├── GrpcExceptionAdvice.java
│       │   └── ScheduleGrpcServiceImpl.java  ← 6 RPC (GetActiveLesson, GetLessonById,
│       │                                         GetLessonsByGroup, GetLessonsByIds,
│       │                                         ResolveLesson, CountSubjectReferences)
│       ├── item/
│       │   ├── entity/ScheduleItem.java
│       │   ├── repository/ScheduleItemRepository.java
│       │   ├── ScheduleItemAssembler.java
│       │   ├── ScheduleItemController.java
│       │   └── ScheduleItemService.java  ← @Transactional default
│       ├── lesson/
│       │   ├── entity/Lesson.java
│       │   ├── repository/LessonRepository.java  ← все запросы native + status::text cast
│       │   ├── IsoParityReconciler.java   ← @EventListener(ApplicationReadyEvent) one-shot
│       │   ├── LessonAssembler.java
│       │   ├── LessonController.java
│       │   ├── LessonGenerationService.java  ← ISO-based parity; 4 publics: compute/generate/regenerate*/delete
│       │   ├── LessonService.java            ← cancel/restore/massCancel/toggleGeoBlock/block/unblock/view
│       │   ├── LessonStatusTransitionJob.java  ← @Scheduled(fixedDelay=60_000)
│       │   └── LessonWithItem.java           ← internal record
│       ├── oneoff/
│       │   ├── entity/OneOffLesson.java
│       │   ├── repository/OneOffLessonRepository.java
│       │   ├── OneOffLessonAssembler.java
│       │   ├── OneOffLessonController.java
│       │   └── OneOffLessonService.java       ← computeWeekTypeForDate — stale algorithm!
│       ├── security/
│       │   ├── HealthCheckController.java    ← «placeholder endpoint for Phase 10»
│       │   ├── RequestContext.java           ← @Scope("request") proxyMode=TARGET_CLASS
│       │   ├── RequireRole.java              ← annotation, runtime
│       │   ├── RoleCheckAspect.java          ← @Around("@annotation(requireRole)")
│       │   └── UserContextFilter.java        ← doverchiv — ничего не верифицирует
│       └── subject/
│           └── SubjectDeletedCascadeService.java  ← RabbitMQ subject.deleted cascade
│   └── src/main/resources/
│       ├── application.yml              ← DEBUG ru.rutcampustrack, ddl-auto: validate
│       ├── application-prod.yml         ← только actuator, ничего не переопределяет
│       └── db/migration/V1..V9__*.sql   ← baseline + 8 патчей
│   └── src/test/java/...                ← 16 тестов (integration + unit)
```

Расхождения со структурой, декларированной в CLAUDE.md:

- **Совпадает** contract-first: `schedule-api-contract` + `schedule-app`. Lombok присутствует только в `schedule-app/entity/*` — контракт чист.
- `contract/exception/ErrorResponse` продублирован из academic-api-contract — допустимо (микросервисная изоляция), но создаёт дрейф: поле `FieldError.message` в примере говорит «must be between 0 and 5», хотя реальная проверка `dayOfWeek` — `@Min(1) @Max(7)` (см. P3-1).
- Пакет `security/HealthCheckController` — мёртвый класс из фазы 10, выставляет публичный `GET /schedule/health-check` (см. P2-1).

---

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через Internal JWT — `UserContextFilter` слепо доверяет HTTP-заголовкам (IDOR/privilege-escalation)
**Статус (2026-04-18):** будет закрыто фиксом из C0-1 (Internal JWT). См. `OWNER-ANSWERS.md` 02-Q2 + 03-Q1. После фикса — `UserContextFilter` парсит `Authorization: Internal <jwt>`, валидирует RSA-подпись, читает claims вместо заголовков. Ниже — оригинальное описание.


- **Где:**
  - `security/UserContextFilter.java:32-41` — читает `X-User-Id`/`X-User-Role`/`X-Group-Id`/`X-Is-Headman` напрямую, без проверки источника.
  - `application-prod.yml` — не задаёт ни mTLS, ни IP-allowlist, ни аутентификации межсервисного канала.
  - `application.yml:40-54` — Swagger UI открыт на порту 9092 (springdoc endpoints включены by default).
- **Что:** схема защиты строится на договорённости «только Gateway отправляет эти заголовки». В Docker private network это работает, **если** сеть действительно изолирована. Но (а) в текущем `docker-compose.yml`/VPS-layout сам порт 9092 экспонится (через `expose`, не `ports`) — атакующий, получивший SSH на хосте или контейнер в той же `rutcampustrack_private_net`, может напрямую открыть `POST http://schedule-service:9092/schedule/items` с заголовком `X-User-Role: ADMIN` и создать/удалить любое расписание. (б) В тестах (см. `LessonApiTest:92-105`) прямо показано, что достаточно трёх строк `.header("X-User-Role","ADMIN")`, чтобы обойти RBAC.
- **Риск:** полный обход авторизации при любом lateral-movement внутри докер-сети, внутреннем туннеле по ошибке, misconfigured nginx или утечке IP. `ScheduleItemService.createScheduleItem` → `requireHeadmanForGroup` возвращает `true` немедленно для роли ADMIN (без gRPC-верификации). Злоумышленник может массово отменять пары (`massCancelLessons`), блокировать геоотметку (`blockLesson`), подменить расписание на стороне academic, вынудить attendance-service принять фейковые `lesson.started`.
- **Как чинить:**
  - (a) ввести shared-secret header `X-Internal-Token` — jwt/HMAC от Gateway, сервис верифицирует подпись. То же, что для gRPC (`GrpcAuthInterceptor`), но для REST. Аналогично academic-service P0-2.
  - (b) либо — заверять JWT в каждом сервисе (reverse-JWT-validation), но это дублирует работу Gateway.
  - (c) вариант «enough for internal»: nginx на VPS должен явно `location ~ /schedule/` проксировать только с `X-Forwarded-For` из API Gateway; проверить, что нет публичных bridge‑портов.
- **Зависимости:** api-gateway (добавить подпись); academic/attendance/notification-web — у всех аналогичная проблема; infra (nginx/docker network).

### P0-2: 🔧 TO-FIX через In-app outbox — `@TransactionalEventListener(AFTER_COMMIT)` без outbox → потеря `lesson.started`/`lesson.closed`
**Статус (2026-04-18):** будет закрыто фиксом из C0-3. См. `OWNER-ANSWERS.md` 02-Q3.


- **Где:**
  - `event/DomainEventListener.java:31-35` — `@TransactionalEventListener(phase=AFTER_COMMIT)` + `rabbitTemplate.convertAndSend(EXCHANGE, "", event)`.
  - `event/RabbitConfig.java:80-86` — `channelTransacted` не включён (по комментарию «Pitfall 1»).
  - `lesson/LessonStatusTransitionJob.java:53-86` — коммитит статус, после чего слушатель должен опубликовать событие.
- **Что:** транзакция закоммитилась → Spring вызвал listener → listener сделал сетевой вызов к RabbitMQ. Между коммитом и успешной доставкой возможны: (а) RabbitMQ недоступен/рестарт; (б) broker connection timeout; (в) ObjectMapper падает с сериализацией (экзотические кейсы LocalTime/OffsetDateTime); (г) сам слушатель падает из-за out-of-memory. В каждом из этих случаев событие **теряется безвозвратно**: коммит в БД уже был. Для `lesson.started` это означает: пара в БД ACTIVE, но notification-bot/notification-web не узнают → студенты не получат push «отметьтесь», а attendance-service не откроет окно checkin. Восстановление требует либо replay (которого нет), либо хирургической операции вручную.
- **Риск:** в нормальной работе вероятность мала, в deploy/restart RabbitMQ — 100% на активных парах попавших в момент рестарта (в РУТ МИИТ это каждые 1.5 часа с 8:30 до 18:00).
- **Как чинить:** transactional outbox. Завести таблицу `schedule_outbox (id, event_type, payload, status, created_at, sent_at)`, в `DomainEventListener` вместо `convertAndSend` вызывать `outboxRepository.save(...)` в той же транзакции (или `@TransactionalEventListener(BEFORE_COMMIT)`), а отдельный `@Scheduled` job каждые 5-10 с сканирует `status='pending'` и шлёт в RabbitMQ с `ack`-подтверждением. При успехе → `status='sent'`. Либо (гибче) publisher confirms + `RabbitTemplate.setConfirmCallback` с retry очередью.
- **Зависимости:** academic-service (тот же антипаттерн — см. 02 P0-6); attendance-service; добавить migration V10 с таблицей outbox; выделить dedicated scheduler thread-pool.

### P0-3: `NumberFormatException`/`NullPointerException` в `UserContextFilter` на битом заголовке → 500 вместо 400/403
- **Где:** `security/UserContextFilter.java:34-40` — `Long.parseLong(userIdHeader)`, `UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase())`, `Boolean.parseBoolean(request.getHeader("X-Is-Headman"))`, `Long.parseLong(groupIdHeader)` — все бросают неперехваченные исключения на любом невалидном входе.
- **Что:** если атакующий прислал `X-User-Id: abc` или `X-User-Role: GOD`, фильтр кидает `NumberFormatException` / `IllegalArgumentException` / `NullPointerException`. Эти исключения не обрабатываются `GlobalExceptionHandler` (он — `@RestControllerAdvice`, фильтр выполняется **до** DispatcherServlet). Клиент получит голый `500 Internal Server Error` без RFC 7807 body, stack trace в логе, загрязнение логов, возможный DoS через массовую рассылку плохих заголовков.
- **Риск:** (a) нарушение контракта RFC 7807 в error-path; (b) потенциальный log-flood (DEBUG логирование включено в обоих конфигах → stack-trace на каждый неправильный запрос); (c) разница между dev и prod в поведении: в staging можно спровоцировать 500 и получить JSON-детали исключения, помогающие разведке.
- **Как чинить:** обернуть в try/catch с trace-level log + `response.sendError(HttpStatus.FORBIDDEN.value())` или просто не выставлять контекст и дать RoleCheckAspect-у бросить AccessDenied. Можно проверить заранее:
  ```java
  try {
      requestContext.setUserId(Long.parseLong(userIdHeader));
      String roleHeader = request.getHeader("X-User-Role");
      if (roleHeader == null) { chain.doFilter(request, response); return; }
      requestContext.setRole(UserRole.valueOf(roleHeader.trim().toUpperCase()));
      ...
  } catch (NumberFormatException | IllegalArgumentException e) {
      log.debug("Malformed auth headers; treating as anonymous: {}", e.getMessage());
      // fall through — RoleCheckAspect throws 403 later
  }
  ```
- **Зависимости:** ничто; чистый рефакторинг.

### P0-4: `@Scheduled` без distributed lock — двойная публикация `lesson.started` при масштабировании
- **Где:** `lesson/LessonStatusTransitionJob.java:53-86`; `config/SchedulingConfig.java` (просто `@EnableScheduling`, никакого ShedLock).
- **Что:** задача `runTransitions()` запускается каждые 60 секунд на **каждом инстансе** schedule-service. В `docker-compose.prod.yml` один инстанс — риск скрыт. Но как только добавится second instance (для HA или blue-green), оба одновременно прогонят `findPlannedDueForActivation(now)`, оба увидят одни и те же lesson'ы (один из них выиграет race на `UPDATE status='active' WHERE id=...`), но **оба опубликуют `LessonStartedEvent`**. В итоге notification-bot разошлёт двойные push-уведомления, attendance откроет окно дважды.
- **Риск:** уведомительный спам, двойные записи в attendance при race → потенциальные дубли `attendance.session.closed`. Актуально при любом scaling/rolling-restart (если старый инстанс ещё жив, пока новый уже тикнул).
- **Как чинить:** ShedLock + Spring Integration (`net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`), таблица `shedlock(name, lock_until, locked_at, locked_by)`; оборачивать метод `@SchedulerLock(name="lesson-status-transition")`. Альтернатива — `SELECT ... FOR UPDATE SKIP LOCKED` в find-запросе, но пусть distributed-lock будет явный.
- **Зависимости:** Flyway V10 (создание shedlock таблицы); dependency bump.

### P0-5: Дрейф алгоритма чётности между `LessonGenerationService` и `OneOffLessonService.computeWeekTypeForDate`
- **Где:**
  - `lesson/LessonGenerationService.java:79-112` — после коммита 5e139b3: `WeekType currentParity = (isoWeek % 2 == 0) ? WeekType.ODD : WeekType.EVEN;` (ISO-чёт → ODD → 1-я неделя; перевёрнутый маппинг).
  - `oneoff/OneOffLessonService.java:176-186` — `computeWeekTypeForDate(...)` использует **старый** алгоритм: `anchor = semesterStart.previousOrSame(MONDAY)`, `weeks = WEEKS.between(anchor, targetWeekMonday)`, чётная неделя → `firstWeekType`, нечётная → противоположное. Это **до-ISO** вариант, который был отменён в 5e139b3.
  - `lesson/IsoParityReconciler.java:93-122` — вызывает `regenerateFromDateForReconciliation`, что дергает `computeLessonDates` — правильный ISO-алгоритм. Несогласованность только в one-off.
- **Что:** при создании разовой пары `OneOffLessonService.createOneOffLesson` проверяет, не занят ли (group, date, lessonNumber) активным шаблоном. Для этого вычисляет `WeekType` даты и зовёт `scheduleItemRepository.existsActiveTemplateSlot(..., weekType)`. Алгоритм `computeWeekTypeForDate` **расходится** с тем, которым `LessonGenerationService` материализовал `lessons`. В итоге для даты X староста может увидеть две противоположные картины: материализованная пара на дате есть (генератор считает её нечётной неделей), но one-off-конфликт не сработал (one-off считает её чётной) → он создаст one-off поверх, получит 409 (на уникальный ключ `schedule_one_off_lessons`) или проскочит без реального конфликта, породив дублирующую пару.
- **Риск:** (a) дубли пар в расписании группы на одну и ту же дату → путаница для студентов, два отдельных attendance документа; (b) «слот занят» при создании при отсутствии реального конфликта; (c) тесты (`OneOffLessonControllerIT`) проходят только потому, что используют `TARGET_DATE = 2026-04-20` (понедельник, ISO 17 — нечётная → `EVEN` по новому маппингу; по старому anchor=2026-02-02, weeks=11 — нечётная → `EVEN` при firstWeekType=ODD) — **совпадение случайное**. При другой дате тест упал бы или проходил бы при ошибочной логике.
- **Как чинить:** заменить `OneOffLessonService.computeWeekTypeForDate` на единый метод — вынести чистый (static, pure) `WeekParityResolver` в общий пакет и зовущий его из обоих мест. Добавить regression test с датой ISO-6 (2026-02-03 вторник) — должно быть ODD везде.
- **Зависимости:** ничто в контракте; один рефактор + 1-2 теста.

---

## Серьёзные проблемы (P1)

### P1-1: `LessonService.getLessonsForGroup` делает pagination **в памяти**
- **Где:** `lesson/LessonService.java:212-246` — `findByGroupId(groupId)` → читает ВСЕ активные и неактивные шаблоны; `findByScheduleItemIdInAndDateBetweenAndStatusIn(itemIds, from, to, statuses)` — все пары за период; затем `all.subList(offset, end)` + `PageImpl<>(sublist, pageable, total)`.
- **Что:** вся выборка materialized в List и обрезается вручную. Для группы с 8 парами × 5 дней × 16 недель ≈ 640 пар — ещё терпимо; но в году 32 недели × 8 пар = 256 × 7 дней = ~1800 пар (всех статусов). На `?size=50&page=3` сервер всё равно читает все 1800 + 20 ScheduleItem, строит map и вручную режет.
- **Риск:** O(N) память на каждый запрос + лишний bandwidth PostgreSQL; при ежесекундном polling'е PWA — существенная нагрузка. OOM теоретический, производительность практическая.
- **Как чинить:** добавить в `LessonRepository` метод c `Pageable` на уровне БД (`@Query(value="...", countQuery="...") Page<Lesson> findPageByGroupIdAndDateBetweenAndStatusIn(...)`) с `JOIN schedule_items` + `LEFT JOIN FETCH` (или проекцией), возвращать `Page<LessonWithItem>` прямо из DAO. Убрать промежуточный `PageImpl` hack.
- **Зависимости:** contract стабилен (ответ уже `PagedModel`), только внутренности.

### P1-2: `MassCancelRequest` не валидирует `dateFrom <= dateTo`
- **Где:** `contract/dto/lesson/MassCancelRequest.java` — только `@NotNull` и `@NotBlank(reason)`; `LessonService.massCancelLessons:135-158` — нет проверки диапазона.
- **Что:** при `dateFrom=2030-01-01, dateTo=2026-01-01` репозиторий вернёт пустой список (SQL `BETWEEN` — всегда false), staff гарантированно получит `cancelledCount=0` без предупреждения. Хуже — при `dateFrom=2020-01-01, dateTo=2030-12-31` староста случайно «массово отменит» всё расписание группы за 10 лет (в т.ч. закрытые пары, с потерей посещаемости в attendance через `lesson.cancelled` event).
- **Риск:** разрушение исторических данных; UX bug → staff звонит в саппорт.
- **Как чинить:** добавить валидацию в сервисе: `if (request.dateFrom().isAfter(request.dateTo())) throw new BadRequestException(...)`; ограничить максимальный диапазон (например, одним семестром — gRPC `getActiveSemester()` уже зовётся в других местах). Бонус: Bean Validation `@AssertTrue` на уровне record — кроссполе не поддерживается, только кастомный ConstraintValidator.
- **Зависимости:** ничто.

### P1-3: `cancelLesson` допускает отмену CLOSED-пары → выкидывание посещаемости задним числом
- **Где:** `lesson/LessonService.java:95-109` — `if (lesson.getStatus() == LessonStatus.CANCELLED) throw ...;` — остальные переходы разрешены (PLANNED/ACTIVE/CLOSED → CANCELLED).
- **Что:** пользовательская фича (комментарий в коде: «UX-требование старосты ретроспективно отменить прошедшую пару»). Но CLAUDE.md:151 однозначно: `cancelled` → не влияет на статистику. Отсюда: староста может «починить» плохую посещаемость студента, массово отменяя закрытые пары задним числом — и статистика волшебно улучшается. Никакого audit trail кроме `cancel_reason`; нет подписи `cancelled_by`, нет timestamp `cancelled_at` (поле `closed_at` тоже не перезаписывается — staff видит старый close-time, а lesson CANCELLED).
- **Риск:** манипуляция статистикой. Для учебной системы — чувствительный этический момент: посещаемость влияет на стипендию/допуск к сессии.
- **Как чинить:**
  - (a) Запретить cancel CLOSED (снять фичу, обсудить с ownership-ом).
  - (b) Либо оставить фичу и добавить колонки `lessons.cancelled_by BIGINT`, `cancelled_at TIMESTAMPTZ`, логирование в `audit_log` (нужен новый модуль). Показывать в UI, что пара была ретроспективно отменена.
  - (c) Только ADMIN может отменить CLOSED; HEADMAN — только PLANNED/ACTIVE.
- **Зависимости:** контракт (возможно, новая DTO с `cancelledBy`), ветка в миграциях, attendance-service (как обрабатывать ретроспективную отмену — сейчас он просто переведёт статусы, что тоже дырявое место).

### P1-4: `HealthCheckController` — пережиток Phase 10, публично доступен
- **Где:** `security/HealthCheckController.java:14-22` — `@GetMapping("/schedule/health-check")` + `@RequireRole({ADMIN, TEACHER, STUDENT})`.
- **Что:** endpoint из фазы 10, smoke-testовый. Сам комментарий: «Will be superseded by real schedule endpoints in Phase 11» — но не удалён. Доступен любой роли. Потенциальная поверхность атаки (информация о состоянии приложения, можно использовать для таймирования атак на RequestContext). Плюс путаница — есть actuator `/actuator/health`, есть этот «внутренний» health-check.
- **Риск:** низкий, но gotta-go: мёртвый код.
- **Как чинить:** удалить класс + его тест (`SecuritySmokeTest`).
- **Зависимости:** обновить тесты.

### P1-5: `logging.level.ru.rutcampustrack: DEBUG` в prod-конфиге
- **Где:** `application.yml:56-58` — base; `application-prod.yml:12-15` — **явно** `logging.level.ru.rutcampustrack: DEBUG`, `org.hibernate.SQL: DEBUG`.
- **Что:** в проде логируется DEBUG с нашим пакетом + `org.hibernate.SQL`. `Hibernate.SQL = DEBUG` выводит все SQL-запросы с привязанными параметрами (через `org.hibernate.type.descriptor.sql.BasicBinder` — нужно ещё `TRACE`, но всё равно запросы видны). В логах контейнера лежат `group_id`, `user_id`, `teacher_id` — если логи collected в ELK/Grafana, персональные идентификаторы утекают в ops-систему. Для академической среды — нарушение ПДн.
- **Риск:** (а) утечка чувствительных данных; (б) замедление работы в 2-3× из-за String-конкатенации; (в) переполнение диска/S3 log-ом.
- **Как чинить:** в `application-prod.yml`: `logging.level.ru.rutcampustrack: INFO`, `logging.level.org.hibernate.SQL: WARN`. Оставить DEBUG только в `application.yml` (dev-дефолт).
- **Зависимости:** ничто; одна строка.

### P1-6: gRPC `expectedSecret` nullable — по умолчанию выключенная аутентификация
- **Где:** `grpc/GrpcAuthInterceptor.java:20-34` — `@Value("${grpc.auth.secret:#{null}}")` + `if (expectedSecret != null && !expectedSecret.isBlank())`; `application.yml:50` — `grpc.auth.secret: ${GRPC_SECRET:}`.
- **Что:** если переменная `GRPC_SECRET` не задана (например, в staging или dev), interceptor становится **no-op** и принимает любой call без проверки. В коде нет предупреждения/failfast «secret not configured — rejecting all calls». Аналогично клиент (`GrpcSecretClientInterceptor.java:16`) — если secret пуст, не добавляет header. Комбинация: забыли задать `GRPC_SECRET` в docker-compose.prod.yml → все вызовы проходят, но аутентификация де-факто отключена; staff уверен, что защищено.
- **Риск:** false sense of security. При наличии P0-1 (прямой доступ к сети) — вдвойне критично.
- **Как чинить:**
  - (a) failfast при старте в prod-профиле: `@PostConstruct void validate() { if (profile.isProd() && (secret == null || secret.isBlank())) throw new IllegalStateException("GRPC_SECRET must be configured in prod"); }`.
  - (b) сменить default с «permissive» на «deny» — `if (expectedSecret == null) throw UNAUTHENTICATED`.
- **Зависимости:** academic/attendance — у них тот же паттерн (см. 02-academic P1).

### P1-7: `IsoParityReconciler` — стартап-блокировка при больших объёмах
- **Где:** `lesson/IsoParityReconciler.java:65-82` — `@EventListener(ApplicationReadyEvent.class)` — синхронный, блокирующий; `:100-121` — цикл по всем активным `ScheduleItem` + `regenerateFromDateForReconciliation` → delete + computeLessonDates + saveAll × N.
- **Что:** reconciler запускается один раз после применения новой версии. Для 200 групп × 8 пар × 32 недели ≈ 50 000 lesson-inserts — в одной транзакции на одном connection. ApplicationReadyEvent синхронен → actuator `/health/liveness` вернёт `DOWN` всё время выполнения, Kubernetes/nginx убьёт контейнер ещё до того, как пройдёт reconcile. Плюс — при любом IOException/timeout до `markExecuted` marker не запишется, rotation deploys → всё повторяется каждый старт.
- **Риск:** неудачный rolling deploy; блокированный liveness probe; возможная потеря всех PLANNED пар в момент перекатывания (delete-часть уже прошла, insert ещё нет, транзакция откатывается).
- **Как чинить:**
  - (a) вынести reconcile в фоновый поток — `@Async` + dedicated executor, сам `@EventListener` только планирует.
  - (b) Chunk-обработка: по `ScheduleItem` отдельной транзакцией каждый, `markExecuted` в отдельной транзакции после всех. Сейчас используется `txTemplate.execute(status -> doReconcile())` на весь цикл → one big transaction.
  - (c) метаданные `iso_parity_reconciliation` поддержать `status` (pending/running/done) вместо булева marker-а, позволяя resume.
- **Зависимости:** опционально — добавить столбец в `iso_parity_reconciliation`; actuator-health customizer, чтобы не падать по liveness.

### P1-8: `LessonStatusTransitionJob` держит FK `scheduleItemId` и делает N+1 `findById` на каждую пару
- **Где:** `lesson/LessonStatusTransitionJob.java:59-70` и `73-83` — цикл: `for (Lesson lesson : toActivate) { ... scheduleItemRepository.findById(lesson.getScheduleItemId())... }`.
- **Что:** N-переходов → N запросов `SELECT * FROM schedule_items WHERE id=?`. На 100 одновременных lesson `started` в 08:30 — 100 запросов + 100 updates. Плюс `saveAll(toActivate)` в конце — дополнительный batch. Могло бы быть single JOIN-запрос.
- **Риск:** нагрузка на БД в пиковые минуты. Не критично, но хуже read-performance в 10× в 8:30, 10:10, 11:55 (старт пар).
- **Как чинить:** в `LessonRepository` добавить проекцию с JOIN: `findActivationCandidates(now)` возвращает `List<LessonWithItemProjection>` (lesson + groupId + subjectId + lessonNumber + startTime + endTime + room). Избавит от `findById` внутри цикла.
- **Зависимости:** ничто.

### P1-9: `lesson.cancelled` event для CLOSED-перехода не содержит `cancelled_by`
- **Где:** `event/LessonCancelledEvent.java:22-27`; `event-schemas/lesson.cancelled.json:13-21`.
- **Что:** схема события: `{lesson_id, group_id, subject_id, date, cancel_reason}`. Для ретроспективной отмены (P1-3) и audit trail потребители хотят знать, **кто** отменил. Сейчас attendance-service обрабатывает `lesson.cancelled` и сбрасывает статусы отметок в `cancelled` — без аудит-контекста. Добавление `cancelled_by` (user_id старосты/admin) — минимальная cost.
- **Риск:** средний — invisible changes в статистике; если кто-то обнаружит манипуляцию, нет возможности recover «кто и когда».
- **Как чинить:** добавить `cancelled_by` в event-schemas/lesson.cancelled.json (опционально) и в `Payload`; source: `requestContext.getUserId()`.
- **Зависимости:** consumer'ы (attendance, notification) — полагаться на опциональность.

### P1-10: Отсутствует валидация `startTime < endTime` в Create/UpdateScheduleItemRequest
- **Где:** `contract/dto/item/CreateScheduleItemRequest.java:35-39`; `UpdateScheduleItemRequest.java:30-34` — только `@NotNull LocalTime startTime/endTime`.
- **Что:** ничто не запрещает POST с `startTime=10:00, endTime=08:30`. Сервис сохранит → `LessonStatusTransitionJob` попытается перевести в ACTIVE (время уже прошло), потом в CLOSED через 5 минут (endtime в прошлом). Результат: пара моментально открывается и закрывается — студенты не успеют отметиться.
- **Риск:** логическая ошибка в данных; сломанный lesson.started/closed flow.
- **Как чинить:** кастомный cross-field validator или `@AssertTrue boolean isValidTimeOrder() { return startTime.isBefore(endTime); }` на уровне `CreateScheduleItemRequest`.
- **Зависимости:** ничто.

### P1-11: `LessonService.cancelLesson` не публикует событие для уже отменённого пересчёта
- **Где:** `lesson/LessonService.java:104-108` — публикация `LessonCancelledEvent` только в happy-path; `toggleGeoBlock`, `blockLessonByHeadman`, `unblockLessonByHeadman` — **НЕ публикуют событий вообще**.
- **Что:** PWA/web-panel должны знать, когда пара заблокирована старостой (изменяется поведение check-in для студентов — им показывается «староста проведёт вручную»). Эта телеметрия сейчас приходит только по next fetch, не через WebSocket push. Аналогично geo-block toggle — студент/teacher не узнают, что надо обновить UI.
- **Риск:** UX-разрыв: студенты видят устаревшее состояние, давят «отметиться», получают 403 от attendance — неинформативный UX.
- **Как чинить:** добавить `LessonBlockedEvent` / `LessonGeoBlockToggledEvent` (или reuse `LessonUpdatedEvent`), publish в соответствующих методах.
- **Зависимости:** event-schemas/; notification-web (дополнить handler).

### P1-12: В `ScheduleItem` и `OneOffLesson` — `created_at` устанавливается через `OffsetDateTime.now()` без инжектированного `Clock`
- **Где:**
  - `item/ScheduleItemService.java:91` — `item.setCreatedAt(OffsetDateTime.now())`.
  - `oneoff/entity/OneOffLesson.java:66-70` — `@PrePersist` + `OffsetDateTime.now()`.
  - `lesson/LessonService.java:189` — `lesson.setBlockedAt(OffsetDateTime.now())` (не использует инжектированный Clock).
  - `event/DomainEvent.java:39` — `this.occurredAt = OffsetDateTime.now()`.
- **Что:** при наличии `@Bean Clock` (см. `config/ClockConfig.java`), только `LessonGenerationService`, `LessonStatusTransitionJob` и `IsoParityReconciler` его используют. Остальные компоненты зовут статический `OffsetDateTime.now()`. Это (а) несогласованность TZ (вдруг JVM default TZ, а не Europe/Moscow); (б) тесты с `Clock.fixed(...)` не могут отмокать эти места.
- **Риск:** тесты проходят только в условиях, когда JVM default-TZ == Europe/Moscow; timestamp в `created_at` может не совпадать с тем, что генерирует `buildLessons(clock.withZone(UTC))`.
- **Как чинить:** везде инжектить `Clock` (в entity — через factory-method на сервисе, а не `@PrePersist`), включая `DomainEvent.occurredAt` (добавить параметр или setter).
- **Зависимости:** переработка abstract `DomainEvent` (больше не будет sealed final в pass-through).

---

## Средние проблемы (P2)

### P2-1: `LessonAssembler` не использует `WebMvcLinkBuilder` — ссылки собираются конкатенацией строк
- **Где:** `lesson/LessonAssembler.java:58-79` — `Link.of("/schedule/lessons/" + l.getId()).withSelfRel()`.
- **Что:** CLAUDE.md требует HATEOAS Level 3 с _links; но `linkTo(methodOn(...))` не применяется, значит при смене базового path (через `contextPath` или proxy) ссылки выйдут битыми. В `ScheduleItemAssembler.java:37-43` — правильно через `linkTo(methodOn(...))`. Непоследовательно.
- **Риск:** средний — при reverse-proxy с `prefix stripping` (`/api/schedule/*` → `/schedule/*`) клиент получит self-link на несуществующий ресурс. Сейчас, возможно, работает потому, что Gateway делает rewrite с совпадающими путями.
- **Как чинить:** переписать `LessonAssembler` на `linkTo(methodOn(LessonController.class).cancelLesson(...))`; задействовать `UriComponentsBuilder`.

### P2-2: `LessonService.getLessonsForGroup` пропускает `WeekType`/schedule_item когда нет совпадения
- **Где:** `lesson/LessonService.java:236-238` — `.map(l -> new LessonWithItem(l, itemMap.get(l.getScheduleItemId())))` — если `itemMap.get()` вернёт null (orphan), `LessonWithItem` получит `scheduleItem=null`, после чего `LessonAssembler.toResponse` упадёт на `si.getGroupId()` NPE.
- **Что:** по FK `ON DELETE CASCADE` orphan быть не должен — но `SubjectDeletedCascadeService.cascade` делает physical delete обоих (ScheduleItem удаляется, lessons каскадятся), так что риск низкий. Но inconsistent-window: если transaction между двумя selects покажет lesson без ScheduleItem, будет 500.
- **Как чинить:** `.filter(l -> itemMap.containsKey(l.getScheduleItemId()))` как в `ScheduleGrpcServiceImpl:112`.

### P2-3: `IsoParityReconciler` использует `createNativeQuery("INSERT ... ON CONFLICT DO NOTHING")` — без RETURNING, нет способа узнать, что marker записан
- **Где:** `lesson/IsoParityReconciler.java:124-132`.
- **Что:** `executeUpdate` вернёт 0 или 1. Код игнорирует возврат. Если параллельный инстанс уже записал marker, этот инстанс не узнает, что его `doReconcile` был no-op и логгирует «regenerated X items» неверно.
- **Как чинить:** `int affected = q.executeUpdate(); if (affected == 0) log.info("Marker already present — someone else reconciled concurrently");`.

### P2-4: `OneOffLessonRepository.findByGroupIdAndDateAndLessonNumber` и `existsByGroupIdAndDateAndLessonNumber` — оба объявлены, но `existsBy` не используется
- **Где:** `oneoff/repository/OneOffLessonRepository.java:13-16`.
- **Что:** `existsByGroupIdAndDateAndLessonNumber` — мёртвый метод. Проверка дубликата one-off делается через DataIntegrityViolationException (`GlobalExceptionHandler.handleDataIntegrity` — fallback). Это допустимо, но метод в репозитории вводит в заблуждение.
- **Как чинить:** либо использовать (проверять пре-insert и давать user-friendly сообщение — чище UX), либо удалить.

### P2-5: `LessonRepository.findByScheduleItemIdAndDateBetween` — мёртвый метод
- **Где:** `lesson/repository/LessonRepository.java:17`.
- **Что:** `List<Lesson> findByScheduleItemIdAndDateBetween(...)` — не используется ни в одном сервисе/тесте.
- **Как чинить:** удалить.

### P2-6: gRPC `GetLessonsByGroup` ограничивает только активными шаблонами, но cancel/reopen шаблона не учитывается
- **Где:** `grpc/ScheduleGrpcServiceImpl.java:94-95` — `findByGroupIdAndSemesterIdAndIsActiveTrue`.
- **Что:** если шаблон soft-deleted (is_active=false), его исторические lessons не возвращаются attendance-у. Но attendance-у нужны **все** lessons для построения журнала по студенту за семестр. Сейчас отчёт после удаления шаблона будет неполным.
- **Как чинить:** либо отдавать все schedule_items независимо от is_active, либо завести отдельный `GetLessonsByGroupIncludingInactive`.

### P2-7: `RabbitConfig.scheduleDlqQueue` без TTL и без max-length — DLQ растёт бесконтрольно
- **Где:** `event/RabbitConfig.java:56-58`.
- **Что:** `QueueBuilder.durable("schedule-service.events.dlq").build()` — никаких аргументов. Poison-messages накапливаются бесконечно.
- **Как чинить:** `.withArgument("x-message-ttl", 7 * 24 * 3600_000L)` + alert на длину очереди в Prometheus.

### P2-8: `IsoParityReconciler` нацелен только на **активный** семестр — что если их нет или старый ещё считает?
- **Где:** `lesson/IsoParityReconciler.java:93-97` — `academicGrpcClient.getActiveSemester()` → если бросит NOT_FOUND, reconcile пропускается с логом. Marker не записан, при следующем старте повторится → infinity loop.
- **Что:** корректное поведение, но при долгом периоде без активного семестра (между семестрами) каждый деплой будет долго висеть на попытке reconcile + лог-шум.
- **Как чинить:** записать marker с `note='no active semester — skipped'` и всё равно marker-нуть.

### P2-9: `SubjectDeletedCascadeService` вызывается из `EventConsumer.onEvent(Map<String,Object>)` — нет типобезопасности
- **Где:** `event/EventConsumer.java:34-62`; `SubjectDeletedCascadeService.java:59`.
- **Что:** payload парсится вручную, `rawId instanceof Number id` — fragile. Событие `subject.deleted` задокументировано в academic-api-contract, но `schedule-service` не имеет на него типизированного DTO.
- **Как чинить:** создать `contract` JSON-schema → Java record (через jackson `@JsonProperty`), использовать `Jackson2JsonMessageConverter` с target-type headers. Либо объявить `SubjectDeletedEvent` в общем event-contract модуле.

### P2-10: `LessonService.blockLessonByHeadman` сохраняет userId в БД, но не event'ит его
- **Где:** `lesson/LessonService.java:177-191`.
- **Что:** админ/староста блокирует, но downstream-сервисы (attendance: нужно показать студентам «заблокирован старостой»; notification: push) не получают событие. Плюс отсутствие audit trail старостиного действия.
- **Как чинить:** добавить `LessonBlockedByHeadmanEvent`.

### P2-11: `ScheduleItemService.updateScheduleItem` не валидирует `firstWeekType` параметр — может разойтись с `getActiveSemester`
- **Где:** `item/ScheduleItemService.java:152-160`; `AcademicGrpcClient.parseSemesterFirstWeekType:80-87`.
- **Что:** `parseSemesterFirstWeekType` кидает IllegalStateException если `getFirstWeekType()` пустой. После фикса в `LessonGenerationService` параметр **фактически игнорируется**. Тем не менее, если academic-service вернёт `first_week_type=""`, update-пара упадёт с 500. Это легаси-взаимодействие, пора чистить.
- **Как чинить:** убрать требование `first_week_type` из контракта gRPC (`@SuppressWarnings("unused")` уже стоит на параметре `firstWeekType`) — обновить `academic.proto`, убрать из SemesterResponse.

### P2-12: `ErrorResponse` из `schedule-api-contract` примеры (`@Schema example=...`) не отражают реальных валидаций
- **Где:** `contract/exception/ErrorResponse.java:44-49` — пример `"message": "must be between 0 and 5"` для `dayOfWeek`. Но актуальная валидация — `@Min(1) @Max(7)` (см. `CreateScheduleItemRequest:29-30`).
- **Что:** Swagger выдаёт вводящее в заблуждение сообщение.
- **Как чинить:** поправить `example="must be between 1 and 7"`.

### P2-13: Отсутствуют индексы по `schedule_one_off_lessons.subject_id`
- **Где:** `db/migration/V4__one_off_lessons.sql` — индекс только `idx_one_off_group_date`.
- **Что:** `SubjectDeletedCascadeService.cascade` вызывает `oneOffLessonRepository.findBySubjectId` и `countBySubjectId` — оба делают `SELECT ... WHERE subject_id = ?` без индекса. При 10k one-off — full scan.
- **Как чинить:** V10 — `CREATE INDEX idx_one_off_subject ON schedule_one_off_lessons(subject_id);`

### P2-14: V8/V9 — последовательные «reset reconciler marker» миграции, симптом незакрытой до конца истории
- **Где:** `db/migration/V8__reset_iso_parity_reconciler.sql`, `V9__rerun_iso_parity_reconciler.sql`.
- **Что:** V8 сбрасывает marker → reconciler запустится при следующем старте. V9 (через два дня) сбрасывает снова — потому что reconciler упал на UNIQUE collision. Оба файла — патчи над багом; допустимо в разработке, но перед релизом v0.0.0 хочется это переделать в **один cleanup**: после того как фикс закоммичен и все окружения подняты, свести V7/V8/V9 в V7 (marker + статус). Но правило из памяти «не трогать уже применённые миграции» — строже: оставить как есть, просто задокументировать в migration-notes.
- **Как чинить:** не трогать V7/V8/V9 (правило «не редактировать применённые миграции»), но в будущем использовать чекпойнтное поле `status` в `iso_parity_reconciliation` вместо последовательных DELETE-миграций.

### P2-15: `application.yml` — `grpc.auth.secret: ${GRPC_SECRET:}` — пустая строка как дефолт
- **Где:** `application.yml:50`.
- **Что:** при отсутствии env, secret = "", `isBlank()` = true, аутентификация отключена. Нет FailFast. См. P1-6.
- **Как чинить:** прокомментировано выше в P1-6; тут — просто дублирование.

### P2-16: `@Profile("!test")` на `SchedulingConfig` — если тест запустит `@SpringBootTest` **без** явного `@ActiveProfiles("test")`, cron запустится
- **Где:** `config/SchedulingConfig.java:13`; `AbstractScheduleIntegrationTest.java:25` — есть `@ActiveProfiles("test")`.
- **Что:** все интеграционные тесты наследуются от `Abstract...` — OK. Но если добавить SpringBootTest без неё, скедулер активируется и наследит в логах. Защита — соглашение, не контроль.
- **Как чинить:** добавить `@DisabledIfEnvironmentVariable(named="SCHEDULING_DISABLED")` в job-сам или проверить `!env.activeProfilesContains("test")` в `@Conditional`.

---

## Мелкие и nit (P3)

### P3-1: Пример `FieldError.message` не синхронизирован с валидацией
- См. P2-12. Просто пример Swagger, низкий приоритет.

### P3-2: `HealthCheckController` возвращает `Map.of("status", "ok")` без HATEOAS
- Нарушение уровня 3 HATEOAS (CLAUDE.md). Неважно, т.к. endpoint планируется удалить (P1-4).

### P3-3: `ScheduleItemAssembler.toModel(item)` — второй link с `pageable=null, assembler=null`
- `item/ScheduleItemAssembler.java:40-42` — `methodOn(...).listScheduleItems(..., null, null)` — Spring превратит в `/schedule/items?groupId=&semesterId=`, без `pageable`. Link неработоспособен как URL (не откроет list endpoint корректно).
- **Как чинить:** передавать `Pageable.unpaged()` и корректный `PagedResourcesAssembler` (обычно None → runtime создаёт default).

### P3-4: `DomainEvent` — `@JsonIgnoreProperties({"source","timestamp"})` + `@JsonTypeInfo(NONE)` — эвристика
- `event/DomainEvent.java:19-20` — работает, но хрупко. `ApplicationEvent.source` — lombok/Spring-specific, при сериализации по-другому могло бы выдавать `@class`.
- **Как чинить:** использовать отдельный envelope-record, а не наследование ApplicationEvent (но тогда теряется Spring событийный механизм — trade-off).

### P3-5: В `LessonStatusTransitionJob.runTransitions` — `log.info("Cron tick: activated={}, closed={}")` — каждую минуту даже при пустых результатах
- `lesson/LessonStatusTransitionJob.java:85` — 60 × 24 × 365 = 525k лог-строк/год. Большинство — `activated=0, closed=0`.
- **Как чинить:** `log.debug` при нулевых результатах, `log.info` только при non-zero.

### P3-6: `EnumConverters.LessonStatusConverter.convertToEntityAttribute` не обрабатывает неизвестные значения из БД
- `config/EnumConverters.java:31-33` — `Enum.valueOf(db.toUpperCase())` бросит IllegalArgumentException. Учитывая, что enum DB-миграцией ограничен `('planned','active','closed','cancelled')`, теоретически unreachable, но при мануальной вставке новых значений (или backup restore со старой схемой) сервис упадёт.
- **Как чинить:** log warning + fallback null.

### P3-7: `Lesson.isGeoBlocked` — булево поле с приставкой `is` — getter называется `isGeoBlocked` (Lombok генерит корректно), но поле в JSON названо `geoBlocked` — consistent.
- `Lesson.java:47-48`; `LessonResponse:33`. Незначительный nit, всё совпадает.

### P3-8: Отсутствует `@ResponseStatus` на контроллерных методах с 201/204
- Контроллеры возвращают `ResponseEntity.status(HttpStatus.CREATED)` и `noContent()` — работает, но Swagger может не подхватить статусы. В самих контрактных интерфейсах есть `@ApiResponse(responseCode="201")` — OK.

### P3-9: `GlobalExceptionHandler.handleDataIntegrity` полагается на substring `"uq_one_off_slot"` в message
- `exception/GlobalExceptionHandler.java:77-79` — message-based parsing. Хрупко (Postgres JDBC driver может менять формат).
- **Как чинить:** проверять `ex.getMostSpecificCause()` через `PSQLException.getServerErrorMessage().getConstraint()`.

### P3-10: Бессмысленный `@GetMapping` в `HealthCheckController` — `security` — не его место
- `security/HealthCheckController.java` лежит в пакете `security`, тогда как это health-endpoint. Moved from `main` в `security` во время фазы 10.

---

## Мёртвый код

- `security/HealthCheckController.java` — см. P1-4.
- `oneoff/repository/OneOffLessonRepository.existsByGroupIdAndDateAndLessonNumber` — P2-4.
- `lesson/repository/LessonRepository.findByScheduleItemIdAndDateBetween` — P2-5.
- Параметр `WeekType firstWeekType` во всех методах `LessonGenerationService` (`computeLessonDates`, `generateLessons`, `regenerateFromDate`, `regenerateFromDateForReconciliation`) — помечен `@SuppressWarnings("unused")`, но всё ещё передаётся вверх по цепочке (см. `ScheduleItemService.createScheduleItem:96`, `updateScheduleItem:153`, `IsoParityReconciler.doReconcile:97`). Нужно убрать и подчистить контракт gRPC (`SemesterResponse.first_week_type`).

---

## Костыли и TODO/FIXME

- **V8/V9 — reset reconciler marker** — два последовательных «откатить maркер» патча. Истинно — костыли над багом, см. P2-14.
- **V5 — DROP CONSTRAINT + partial UNIQUE INDEX + DO $$...$$** — обход проблемы авто-именования constraint'ов. Работает, но DO-блок — индикатор, что в Phase 60 была неожиданность с именованием. Допустимо в проде.
- **V2 — `CREATE CAST (varchar AS lesson_status) WITH INOUT AS IMPLICIT`** — технический долг, стандартный workaround для JPA-enum. Видно, что все репозитории потом вручную делают `status::text IN (...)` — т.е. cast используется только на assignment, но на comparison требует explicit cast. Противоречие, но уже решено.
- **`LessonGenerationService` параметр `firstWeekType`** — «retained for signature backwards compatibility» (комментарий в коде). Легаси после 5e139b3, нужна финальная зачистка через 1-2 спринта.
- **Комментарий в `ScheduleItem.java:41-42`:** «D-16: teacherId удалён. Препод видит журнал через JOIN...» — хорошая документация решения, не костыль.
- Нет явных `TODO/FIXME/HACK/XXX` в исходниках (grep подтвердил).

---

## Тесты

### Что покрыто хорошо
- **Parity algorithm** — `LessonGenerationServiceTest` (9 тестов): ALL/ODD/EVEN на коротком семестре Feb 2 – Mar 2 2026, Sunday-start edge case, пустой диапазон, regression on firstWeekType-ignored.
- **Cron transitions** — `LessonStatusTransitionJobTest` (5 тестов): PLANNED→ACTIVE, ACTIVE→CLOSED с 5-мин grace, catch-up после рестарта, не-переход до grace, не-переход CANCELLED.
- **gRPC happy + sad paths** — `ScheduleGrpcServiceImplTest`, `LessonsByIdsGrpcIT`, `ScheduleGrpcResolveLessonIT`: все 6 RPC методов; multiple-active conflict, date-range validation.
- **REST API** — `LessonApiTest` (10+), `ScheduleItemApiTest` (7), `OneOffLessonControllerIT` (6), `ScheduleViewTest` (4) — покрывают cancel/restore/mass-cancel/block/unblock/geoBlock + CRUD шаблонов + view.
- **Security guard** — `ScheduleItemSecurityTest` (4): admin bypass + headman-correct-group + headman-wrong-group + non-headman-student.
- **Event publishing** — `LessonCancelEventTest`, `OneOffLessonEventPublisherIT`: проверка `verify(rabbitTemplate).convertAndSend(...)` после AFTER_COMMIT.
- **Schema validation** — `EntityMappingIntegrationTest`, `OneOffLessonSchemaIT`: ddl-auto validate + unique constraint.
- **Actuator** — `ActuatorIT`: проверено, что env/beans/heapdump возвращают 404.

### Что покрыто плохо / не покрыто
- **`IsoParityReconciler`** — НЕТ тестов. Reconciler одноразовый, критичный путь, — полностью без покрытия. При фиксах (V7→V8→V9) баги ловились только в проде.
- **`OneOffLessonService.computeWeekTypeForDate`** — НЕТ тестов алгоритма; см. P0-5 — именно здесь дрейф с ISO-алгоритмом.
- **`UserContextFilter`** — НЕТ тестов на malformed headers (NumberFormatException/NullPointerException); security smoke test покрывает только happy-path и «no headers» (см. `SecuritySmokeTest.java`).
- **Time-zone edge cases** — не тестируется поведение при смене DST (Europe/Moscow не переходит, но ISO-неделя на год-переходе может выдать week 52/1 — Jan 1 edge). В тестах используется только 2026-02 ... 2026-04.
- **Distributed/concurrency** — не тестируется масштабирование `LessonStatusTransitionJob`. При введении 2nd instance — двойная публикация (P0-4).
- **`SubjectDeletedCascadeService`** — НЕТ интеграционного теста RabbitMQ-флоу (только класс). Консьюмер парсит raw Map → риск регрессии.
- **`massCancelLessons`** — только happy path; нет теста на невалидный диапазон (P1-2), на большое кол-во lesson'ов, на частичный failure.
- **Race conditions в cancel/restore** — 409/422 покрыты, но не concurrent-modification (два admin одновременно cancel + restore).
- **N+1 в `LessonStatusTransitionJob`** (P1-8) — не замечено тестами, т.к. все юзают in-memory DB.

### Некорректные / подозрительные тесты
- **`LessonApiTest.withAdminHeaders`** (строки 100-105) — метод определён, но не используется в тестах файла. Мёртвый helper.
- **`LessonGenerationIntegrationTest.updateScheduleFieldsReGenerates`** (линия 191-220) — тест **проходит** потому, что `LocalDate.now()` в JVM-default TZ = 2026-04-02, а тестовый семестр Feb 2-22; `regenerateFromDate(fromDate=today)` уходит за границу, новых lesson-ов не создаёт, старые не удаляются. Тест валидирует **отрицательный** кейс, но это выглядит как «работает потому, что пересечение пустое» — при запуске в другой день года тест может поломаться. Проблема CLOCK-INJECTION: тест не мокает Clock в сервисе.
- **`ScheduleItemSecurityTest.requireHeadmanForGroup_adminBypasses`** — ловит «любой RuntimeException» как «прошёл guard». Хрупко, легко маскирует другие баги. Лучше мокнуть `academicGrpcClient.getActiveSemester` → вернуть валид, и проверить, что `scheduleItemRepository.save` был вызван.

### Кандидаты на рефакторинг/удаление
- `HealthCheckController` + `SecuritySmokeTest` — старая Phase 10 инфраструктура, можно снести вместе.
- Перенести helpers `createScheduleItem`/`createLesson` в общий `TestDataBuilder` — дублируются в 6+ тестах.
- Мокировать `Clock` в `ScheduleItemService.updateScheduleItem` (сейчас юзается только `LocalDate.now(clock)` и `OffsetDateTime.now()` без clock) для устойчивого regression теста на перегенерацию.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|-------|-------------|
| Contract-first (`*-api-contract` + `*-app`) | ✅ | Разделение соблюдено; Lombok отсутствует в contract. |
| Request DTO = record | ✅ | Все `Create*/Update*/Cancel*/GeoBlock*/MassCancel*/...Request` — records. |
| Response DTO = class `extends RepresentationModel` | ✅ | `ScheduleItemResponse`, `LessonResponse`, `OneOffLessonResponse`. |
| Enum: UPPER_CASE в Java, lowercase в БД, `LowercaseEnumConverter(autoApply=true)` | ✅ | `EnumConverters` — 2 inner `@Converter(autoApply=true)`; БД ENUM custom types `week_type`, `lesson_status`. |
| Не используется `@Enumerated(EnumType.ORDINAL)` | ✅ | Нет ни одного instance (grep подтвердил). |
| `ddl-auto: validate` | ✅ | В `application.yml:22` + `application-prod.yml:4`. |
| Flyway миграции в `src/main/resources/db/migration/V{N}__*.sql` | ✅ | V1..V9 на месте. |
| Не редактировать применённые миграции | ✅ | git log подтверждает — V1 коммит = Phase 0, последующие — только новые V{N+1}. |
| PK `BIGSERIAL` (Long) | ✅ | `schedule_items.id`, `lessons.id`, `schedule_one_off_lessons.id`. |
| `TIMESTAMPTZ` (UTC) | ⚠ | Поля — `TIMESTAMPTZ`. Но `hibernate.jdbc.time_zone=Europe/Moscow` (application.yml:28) — pre-stores в Moscow-offset; значения в БД UTC через postgres-конвертацию. При TIMESTAMPTZ рассинхронизация не страшна, но читать внимательно. |
| REST HATEOAS Level 3 (_links, self) | ⚠ | Соблюдено в ScheduleItemAssembler; LessonAssembler использует string concat (P2-1). |
| Ошибки: RFC 7807 через `@ControllerAdvice` | ⚠ | Есть `GlobalExceptionHandler` + `ErrorResponse` record. Но ошибки в `UserContextFilter` не перехватываются (P0-3). |
| Swagger `@Operation`, `@ApiResponse` в контрактных интерфейсах | ✅ | LessonApi, ScheduleItemApi, OneOffLessonApi — все аннотированы. |
| PUT = полный апдейт (иммутабельные поля исключены из `Update*Request`) | ✅ | `UpdateScheduleItemRequest` без `groupId`, `semesterId` — D-09. |
| Пакетная структура `ru.rutcampustrack.{service}.{module}` | ✅ | `ru.rutcampustrack.schedule.{item,lesson,oneoff,grpc,event,security,subject,config,exception}`. |
| REST пути `/api/schedule/...` | ⚠ | Сам сервис слушает `/schedule/...`, префикс `/api` добавляет Gateway. Это норма, но контракты не указывают `/api/` в `@RequestMapping` — потенциальная путаница для клиентов. |
| gRPC `ru.rutcampustrack.{service}.grpc` | ✅ | `java_package = "ru.rutcampustrack.schedule.grpc"`. |
| Event types `{domain}.{action}` | ✅ | `lesson.started`, `lesson.closed`, `lesson.cancelled`, `lesson.deleted`, `lesson.one_off.created`, `lesson.one_off.cancelled`. |
| Доверие `X-User-*` заголовкам | ❌ | `UserContextFilter` не верифицирует подпись (P0-1). Известная кросс-сервисная проблема. |
| Soft delete (для пользователей) | N/A | Нет users в этой БД; soft-delete для ScheduleItem (`is_active=false`) реализован. |
| Outbox для AMQP | ❌ | Нет. `@TransactionalEventListener(AFTER_COMMIT)` + прямой `convertAndSend` — риск потери событий (P0-2). |
| Distributed lock для `@Scheduled` | ❌ | Нет. Проблема при scaling (P0-4). |

---

## Зависимости между проблемами

```
P0-1 (trusted headers)
  └─ усугубляет P1-6 (gRPC secret default)
  └─ проявляется в тестах ScheduleItemApiTest (happy-path test stubs prove the trust model works;
     security test covers only role propagation, not authenticity)

P0-2 (AFTER_COMMIT без outbox)
  └─ academic-service P0 (дубль) → кросс-сервисная проблема, фиксить одновременно
  └─ зависит от P0-4 (duplicate publish при race — ухудшает outbox-проблему)

P0-3 (NumberFormatException → 500)
  └─ независимо, простой patch

P0-4 (scheduled без lock)
  └─ блокирует horizontal-scale deployment
  └─ после фикса: можно убрать ручные tests-cleanup (race with test cron)

P0-5 (dissonant parity algorithms)
  └─ скрытый risk дублирования lesson (существует partial uq_one_off_slot — на уровне БД сохраняет от дублей, но бизнес-логика конфликта неверна)
  └─ НЕТ теста, поэтому проявляется только в проде

P1-3 (cancel CLOSED)
  └─ связан с P1-9 (нет cancelled_by в событии) → если оставить фичу, нужен audit-контекст

P1-4 (HealthCheckController)
  └─ косвенно P2-1 (HATEOAS inconsistency — будет easier убрать вместе)

P1-7 (reconciler блокирует startup)
  └─ связан с P2-3 (marker race), P2-8 (no-semester race)

P1-10 (startTime<endTime)
  └─ вместе с P0-4: неверный startTime→endTime может запустить аномальный cron-тик
```

---

## Вопросы к владельцу проекта

1. ✅ **P0-1: trust model.** Планируется ли внедрение sign-ed internal token (HMAC/JWT от Gateway) для defense-in-depth, или vs network-segmentation считается достаточной защитой для v0.0.0?
   → **AUTO-RESOLVED через 02-Q2 (2026-04-18)**: выбран **Internal JWT (Уровень 2 Zero Trust)**. Gateway после валидации внешнего JWT генерирует короткоживущий внутренний JWT (RSA, ~5 мин), сервисы валидируют публичным ключом. Старые `X-User-*` уходят. См. `OWNER-ANSWERS.md` 02-Q2.
2. ✅ **P0-2: outbox.** Готовы ли внедрить transactional outbox (новая таблица `schedule_outbox` + dedicated publisher job) до релиза, или согласны принять риск потери событий при RabbitMQ outage с планом фикса в v0.1?
   → **AUTO-RESOLVED через 02-Q3 (2026-04-18)**: выбран **(b) In-app outbox** — таблица `schedule_outbox` + publisher-job (~5 сек). См. `OWNER-ANSWERS.md` 02-Q3.
3. **P0-4: scaling.** Планируется ли запуск двух+ инстансов schedule-service в проде (HA/blue-green)? Если да — нужен ShedLock сейчас; если нет — доавить minimum deploy note «strict single instance».
4. **P1-3: cancel CLOSED.** Это intentional feature (UX-требование старосты) или legacy? Если feature — добавить `cancelled_by/cancelled_at` + ADMIN-only для CLOSED; если не нужно — вернуть правило «только PLANNED/ACTIVE».
5. **P0-5: parity drift.** Почему `OneOffLessonService.computeWeekTypeForDate` не мигрировал на ISO-алгоритм вместе с `LessonGenerationService`? Тесты проходят из-за случайного совпадения — нужен unit-test с датами на каждую ISO-чётность.
6. **V8/V9 reset marker.** Это окончательное состояние reconciler-а, или планируется ещё один фикс? Можно ли зафиксировать «marker — истина, сервис не будет reconcile заново»?
7. **Scaling `LessonStatusTransitionJob`.** Готов ли вы отказаться от `@Scheduled` в пользу PostgreSQL `LISTEN/NOTIFY` + worker-paradigm, или ShedLock закрывает все кейсы?
8. **`first_week_type`.** В коде параметр игнорируется. Планируется ли удалить поле из academic.proto `SemesterResponse`, или оставить ради контрактной совместимости?

