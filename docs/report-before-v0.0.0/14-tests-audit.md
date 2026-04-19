# 14. Кросс-сервисный аудит тестов

## Сводка

Суммарное покрытие по счётчикам вполне приличное: **444** Java `@Test` в **65** файлах (из них **17** `*IT.java` через Testcontainers), **139** Python-тестов в **23** файлах, **546** frontend-тестов (122 PWA + 389 web-panel + 35 mini-app). Landing — **0** тестов, что ожидаемо для статического сайта. Нет ни одного `@Disabled`, `@Ignore`, `it.skip`, `@pytest.mark.skip` — все тесты либо работают, либо не написаны.

Качество неравномерное. **Сильные стороны**: academic/schedule/attendance используют Testcontainers с Postgres/Mongo/Redis/RabbitMQ в `Abstract*IntegrationTest`-наследниках; есть отдельный `SecuritySmokeTest` в schedule и attendance, который целенаправленно проверяет 403 без заголовков; есть contract-тест `ExcuseEventContractIT` (RabbitMQ payload сверяется с JSON-schema); есть `MongoIndexTest` и `HomeworkMigrationIT` (проверка схемы/миграций); web-panel имеет тесты на `auth.guard`, `headman.guard`, `student.guard`, `auth.interceptor` — RBAC покрыт; PWA имеет `PWAHeadmanRole.test.tsx` и `sw-runtime-cache.test.ts`; бот покрывает все основные event-consumer'ы (`attendance_marked`, `lesson_started/closed/cancelled`, `homework_notifications` и т.д.) и JWT/Redis-клиенты.

**Слабые стороны** (идут сюда как P0/P1/P2):
1. `latecheckin/` в attendance-service — **0 тестов** при наличии полного домена (controller, service, repository, publisher, assembler, entity). Подтверждает 04 P0.
2. Callback-хендлеры бота (`handlers/excuse.py`, `handlers/late_checkin.py`, `handlers/prefs.py`) — **0 unit-тестов**. Подтверждает 06 P0.
3. Нет тестов на `LoginRateLimiter`, `OtpRateLimiter` в auth-service — брутфорс-защита полагается на конфиг.
4. Нет ни одного теста, где запрос проходит **через Gateway** до downstream — contract-тест end-to-end между сервисами отсутствует; `JwtAuthenticationFilterTest` только проверяет, что Gateway вырезает X-User-*, но не проверяет, что downstream без этих заголовков отказывает (это есть только локально в каждом сервисе через `SecuritySmokeTest`).
5. Coverage не агрегируется в CI (нет JaCoCo, нет Istanbul/Vitest coverage report) → нельзя автоматически ловить регрессию покрытия.
6. PWA: `AuthProvider.test.tsx` проверяет хеппи-кейсы, но **не** проверяет поведение на `logout` — именно там слоупоки 09 P0-4 (SW cache) и 09 P0-5 (push-subscription).
7. Web-panel: ни один тест не покрывает **три параллельных STOMP-клиента** (10 P1-5) — нет теста, что при logout все три дисконнектятся.
8. Mini-app: в `tests/` нет тестов на TMA `initData` валидацию — полагаемся на auth-service, но контрактного теста нет.
9. В auth-service нет `HomeworkMigrationIT`-аналога для проверки, что Flyway миграции применяются end-to-end с Testcontainers'ом (в academic — есть, в auth — нет, хотя у auth общая БД с academic и миграции критичны).
10. RabbitMQ: большинство тестов `@MockitoBean`-ят `RabbitTemplate`, а не проверяют реальную доставку. Исключение — `ExcuseEventContractIT`. Других **contract-тестов событий** нет.
11. gRPC: есть unit-тесты для клиентов (`AcademicGrpcClientTest`, `ScheduleGrpcClientTest`) и server-side (`ScheduleGrpcServiceImplTest`), но нет **contract-теста «proto-клиент ↔ реальный сервер»** — если в одном сервисе меняется proto-enum, второй сервис сломается только в проде.
12. Web Push: `WebPushDeliveryServiceTest` мокает `PushService` и проверяет, что вызван `sendNotification`, но не end-to-end subscribe → rabbit event → delivery.
13. Часть тестов — `*IntegrationTest.java` (не `*IT.java`) — но по содержимому настоящие интеграционные, поднимающие Testcontainers. Несогласованное именование усложняет настройку maven-failsafe / gradle-test splitting.

**Счётчики**: P0=2, P1=9, P2=15, P3=8.

## Структура тестовой базы

```
services/
├── api-gateway/src/test/                    ← 2 файла, 13 @Test
│   ├── JwtAuthenticationFilterTest          (11 @Test — JWT подпись/expiry/роли/public routes)
│   └── PublicKeyConfigTest                  (2 @Test — init fail-fast)
│
├── auth-service/src/test/                   ← 5 файлов, 27 @Test
│   ├── AbstractIntegrationTest              (Testcontainers: postgres:16 + redis:7-alpine)
│   ├── AuthIntegrationTest                  (9 @Test — login + JWT issue)
│   ├── OtpIntegrationTest                   (8 @Test — request/verify-by-link/verify-by-code)
│   ├── TmaIntegrationTest                   (6 @Test — Telegram Mini App initData)
│   └── ActuatorIT                           (health)
│
├── academic-service/src/test/               ← 21 файл, ~170 @Test
│   ├── integration/
│   │   ├── AbstractAcademicIntegrationTest  (Testcontainers: postgres + redis + rabbitmq)
│   │   ├── AbstractAcademicCacheIntegrationTest
│   │   ├── AbstractAcademicEventIntegrationTest
│   │   ├── AcademicGrpcIntegrationTest      (17 @Test — gRPC server-side smoke)
│   │   ├── CacheIntegrationTest             (10 @Test — Redis cache hit/miss)
│   │   ├── EventIntegrationTest             (6 @Test — RabbitMQ publish)
│   │   ├── EntityMappingIntegrationTest     (8 @Test — Hibernate schema)
│   │   ├── RestApiIntegrationTest           (13 @Test — REST + HATEOAS)
│   │   ├── GroupRenameEventTest             (2 @Test)
│   │   └── ActuatorIT
│   ├── group/                               (4 файла — CRUD + архивация + переименование)
│   ├── homework/                            (HomeworkControllerIT, HomeworkMigrationIT, HomeworkServiceTest)
│   ├── security/RoleCheckAspectTest         (7 @Test — @RequireRole AOP)
│   ├── semester/SemesterServiceTest         (8 @Test — CRUD)
│   ├── subject/                             (SubjectSchemaIT, SubjectServiceIT)
│   └── user/                                (4 файла — search + conflict + telegram-required)
│
├── schedule-service/src/test/               ← 17 файлов, ~100 @Test
│   ├── integration/
│   │   ├── AbstractScheduleIntegrationTest  (Testcontainers: postgres + rabbitmq)
│   │   ├── ActuatorIT, EntityMappingIntegrationTest
│   │   ├── LessonApiTest                    (14 @Test — REST)
│   │   ├── LessonCancelEventTest            (1 @Test — через RabbitMQ)
│   │   ├── LessonGenerationIntegrationTest  (7 @Test — auto-generate на основе item)
│   │   ├── ScheduleItemApiTest              (8 @Test — CRUD)
│   │   ├── ScheduleViewTest                 (5 @Test — композитный view: item+one-off+cancel)
│   │   └── SecuritySmokeTest                (2 @Test — 403 без заголовков, 200 с ADMIN)
│   ├── grpc/                                (3 файла — SеrviceImpl unit + 2 IT)
│   ├── item/                                (ScheduleItemEntityTest, ScheduleItemSecurityTest)
│   ├── lesson/                              (LessonGenerationServiceTest, LessonStatusTransitionJobTest)
│   └── oneoff/                              (3 файла — CRUD + publisher + schema IT)
│
├── attendance-service/src/test/             ← 27 файлов, ~140 @Test
│   ├── checkin/CheckinServiceTest           (9 @Test — гео-проверка + idempotency)
│   ├── event/
│   │   ├── EventConsumerTest                (6 @Test — unit с моками)
│   │   ├── LessonEventServiceTest           (6 @Test — lesson.started/closed)
│   │   ├── LessonGenerationMergeTest        (1 @Test)
│   │   └── OneOffLessonCancelledConsumerIT
│   ├── excuse/                              (5 файлов — Controller IT, Contract IT, Service, ApproveIT, Repository)
│   │   └── ExcuseEventContractIT            (!!! единственный contract-тест RabbitMQ payload vs JSON-schema)
│   ├── geofence/GeoUtilsTest                (5 @Test — Haversine формула)
│   ├── grpc/                                (AcademicGrpcClientTest, ScheduleGrpcClientTest)
│   ├── integration/
│   │   ├── AbstractAttendanceIntegrationTest (Testcontainers: mongo + redis + rabbitmq)
│   │   ├── CheckinIntegrationTest, MarkingIntegrationTest, ReportIntegrationTest
│   │   ├── EventConsumerIntegrationTest
│   │   ├── RabbitConsumerTest
│   │   ├── SecuritySmokeTest                (3 @Test — 403 без заголовков, TEACHER→403)
│   │   ├── MongoIndexTest                   (2 @Test — unique index на (lessonId, studentId))
│   │   ├── EnumSerializationTest            (2 @Test — LowercaseEnumConverter)
│   │   └── ActuatorIT
│   ├── marking/MarkingServiceTest           (7 @Test — ручная маркировка старостой)
│   ├── ratelimit/CheckinRateLimiterTest     (6 @Test — rate-limit в Redis)
│   └── report/                              (ReportServiceTest, ReportDomainIsolationTest)
│   !!! latecheckin/                         ← 0 тестов при 6 source-классах
│
├── notification-service/src/test/           ← 8 файлов, ~53 @Test
│   ├── config/
│   │   ├── JwtHandshakeInterceptorTest      (6 @Test — ticket-flow через STOMP)
│   │   └── RabbitConfigTest                 (5 @Test — exchange/queue/binding)
│   ├── event/
│   │   ├── EventConsumerTest                (15 @Test — broadcast → STOMP)
│   │   └── GroupEventTest                   (2 @Test)
│   ├── push/
│   │   ├── PushControllerTest               (8 @Test — subscribe/unsubscribe REST)
│   │   ├── PushSubscriptionRepositoryTest   (2 @Test — Mongo indexes)
│   │   └── WebPushDeliveryServiceTest       (11 @Test — мок webpush-java PushService)
│   └── security/SecurityInfrastructureTest  (4 @Test)
│
└── notification-bot/tests/                  ← 23 файла, 139 test_*
    ├── conftest.py                           (fakeredis fixture)
    ├── test_academic_client.py (5) / test_schedule_client.py (4)
    ├── test_attendance_marked.py (8), test_lesson_started.py (6), test_lesson_closed.py (7), test_lesson_cancelled.py (7)
    ├── test_one_off_created_handler.py (4), test_one_off_cancelled_handler.py (4)
    ├── test_homework_notifications.py (6)
    ├── test_excuse_decided.py (6) — ТОЛЬКО receiver pov (принята нотификация), НЕ callback_query handler
    ├── test_headman_alerts.py (7) — ТОЛЬКО отправка алерта, без callback Approve/Reject
    ├── test_group_archived.py (3), test_group_renamed.py (4)
    ├── test_start_handler.py (4), test_login_handler.py (4), test_status_handler.py (5)
    ├── test_otp_verified.py (5)
    ├── test_reminder_scheduler.py (8), test_send_queue.py (6)
    ├── test_consumer_watchdog.py (6)
    ├── test_event_dispatcher.py (16)
    ├── test_jwt_redis_client.py (8), test_redis_client.py (6)

frontends/
├── pwa/src/                                  ← 22 файла, 122 it()
│   ├── __tests__/                            (PWAHeadmanRole, sw-runtime-cache)
│   ├── features/auth/__tests__/              (AuthProvider — main + isHeadman, LoginPage)
│   ├── features/checkin/__tests__/           (CheckInButton, useStompCheckin)
│   ├── features/headman/                     (Overview, StudentsList, SubjectsList, JournalPage, StatsPage, LateCheckinPage, ExcusesPage, GroupHub)
│   ├── features/notifications/__tests__/NotificationsPage
│   ├── features/push/__tests__/              (PushPermissionCard, pushUtils)
│   ├── features/schedule/__tests__/          (SchedulePage, OfflineStaleNotice)
│   └── shared/components/__tests__/          (BottomNav, SegmentedControl)
│
├── web-panel/src/app/                        ← 57 файлов, 389 it()
│   ├── core/auth/                            (6 файлов — auth.service, interceptor, 4 guards)
│   ├── core/theme/theme.service              (8 @Test)
│   ├── layout/sidebar + student-pwa-banner
│   ├── shared/segmented-control
│   ├── features/admin/                       (dashboard, groups, group-history, promotion-preview, semesters, users)
│   ├── features/headman/                     (excuses, homework, journal, late-checkin, lessons, schedule, stats, subjects)
│   ├── features/student/                     (checkin, dashboard, excuses, homework, late-checkin, schedule, shared)
│   ├── features/teacher/                     (journal, stats)
│   └── features/login
│
├── mini-app/src/                             ← 7 файлов, 35 it()
│   ├── features/auth/AuthProvider
│   ├── features/checkin, schedule, homework, stats
│   └── shared/DevModeBanner, useBackButton
│
└── landing/                                  ← 0 тестов (статический сайт)
```

## Критичные проблемы (P0)

### P0-1: `attendance-service/latecheckin/` — полный домен без тестов

- **Где:**
  - `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinController.java`
  - `LateCheckinService.java`, `LateCheckinRepository.java`, `LateCheckinAssembler.java`, `LateCheckinEventPublisher.java`, `entity/LateCheckinRequest.java`
  - `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/LateCheckinApi.java`, `dto/latecheckin/*.java`
- **Что:** фаза добавила 6 source-файлов и 2 DTO. В `src/test/` ни одного файла `*LateCheckin*`. Вся логика запроса «разрешить опоздавшему отметиться» — controller, service, repository, event-publisher — не покрыта ни unit, ни integration тестами.
- **Риск:** при любом будущем рефакторинге (например, смена ownership — сейчас `approvedBy` ставит student'а, должен ставить headman) — не обнаружим регрессии. При деплое в прод: нет гарантии, что FSM state-transition'ы работают (`requested → approved/rejected → marked`).
- **Как чинить:**
  - Добавить `LateCheckinServiceTest` (unit): FSM-transition'ы, бросание `IllegalStateException` на недопустимый переход, IDOR (староста не из той группы).
  - Добавить `LateCheckinControllerIT` (Testcontainers + MockMvc): полный flow request → approve → марка.
  - Добавить `LateCheckinEventPublisherTest` (по аналогии с `ExcuseEventPublisherTest`).
  - Contract-тест для события `latecheckin.requested` / `latecheckin.decided` (по шаблону `ExcuseEventContractIT`).
- **Зависимости:** 04 P0 (фиксирует факт). До этого фикса — любая работа с latecheckin рискует сломать прод.

### P0-2: Бот: callback_query-хендлеры (`excuse`, `late_checkin`, `prefs`) — 0 unit-тестов

- **Где:**
  - `services/notification-bot/bot/handlers/excuse.py:24-56` — `@excuse_router.callback_query(F.data.startswith("ex:"))`
  - `services/notification-bot/bot/handlers/late_checkin.py` — аналогично, `callback_data.startswith("lc:")`
  - `services/notification-bot/bot/handlers/prefs.py` — reply-кнопки для настроек напоминаний
  - `services/notification-bot/tests/test_headman_alerts.py` — тесты **только** для отправки уведомления старосте, но не для обработки его клика Approve/Reject.
- **Что:** главный риск 06 P0-5 — «любой пользователь с подменным `callback_data=ex:123:approve` может разрешить чужой excuse-тикет, потому что роль не проверяется в callback-хендлере». **Этот тест мог бы сразу поймать баг**, но он не написан.
- **Риск:** регрессия на security-fix (когда роль старосты будет проверяться в callback'е) — без теста снова откатят на «удобный» code path.
- **Как чинить:** добавить `tests/test_excuse_callback_handler.py`:
  - callback от headman → вызван `gateway.approveExcuse(id, approved=true)` ровно 1 раз;
  - callback от student → `callback.answer("Недостаточно прав", show_alert=True)`, нет вызова gateway;
  - неверный формат callback_data → graceful error;
  - двойной клик → защита от double-submit (сейчас её нет — тоже всплывёт).
- **Зависимости:** 06 P0-5 (security-фикс). Тест + фикс идут парой.

## Серьёзные проблемы (P1)

### P1-1: Нет contract-тестов Gateway ↔ downstream сервисы

- **Где:**
  - `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` — проверяет только поведение фильтра локально.
  - В downstream-сервисах (academic/schedule/attendance/notification) есть `SecuritySmokeTest.java` — но он проверяет поведение локально (без заголовков → 403 при `@RequireRole`).
- **Что:** нет ни одного теста, который запускает Gateway + downstream в docker-compose/Testcontainers и проверяет, что:
  - Запрос с валидным JWT → downstream видит заголовки X-User-*.
  - Прямой запрос минуя Gateway (на `private_net:9091`) → downstream работает, потому что `UserContextFilter` не требует заголовков (это известный P0 в 02, 03, 04, 05).
- **Риск:** рассинхрон публичных префиксов (`PublicEndpointMatcher`) между Gateway и сервисами — никто не заметит до прода.
- **Как чинить:**
  - Либо — в корень проекта `services/integration-tests/` модуль с Testcontainers compose setup (Gateway + auth + academic) и e2e-запросом.
  - Либо — оставить в каждом сервисе `SecuritySmokeTest`, но добавить ещё один тест на «без JWT → 401 от Gateway», используя `WebTestClient` поверх Gateway.
- **Зависимости:** связано с 07 (gateway) и всеми downstream.

### P1-2: Нет тестов на `LoginRateLimiter` / OTP brute-force

- **Где:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/LoginRateLimiter.java`, `OtpRateLimitException.java` — есть. Тесты: 0.
- **Что:** `OtpIntegrationTest` проверяет request/verify-happy-path (6–8 тестов), но никто не делает 10 попыток подряд и не проверяет, что 11-я отклоняется с 429. Rate-limiter легко сломать (неверный Redis TTL, конфликт ключей) — без теста это незаметно.
- **Риск:** текущий пороговый `N попыток / minute` незащищён на регрессии. Подтверждает 01 P0-5 («DoS через login rate-limiter»).
- **Как чинить:**
  - Добавить в `OtpIntegrationTest` сценарий: 10 `POST /auth/otp/verify-by-code` с неверным кодом → 11-я получает 429 Too Many Requests.
  - Аналогично для login-endpoint.
  - Использовать fakeredis через Testcontainers Redis — не мокать `RedisTemplate`.

### P1-3: Отсутствие coverage-gate в CI

- **Где:** `.github/workflows/ci.yml`, `build.gradle.kts`, `frontends/*/package.json`, `services/notification-bot/pyproject.toml`.
- **Что:** нет `jacoco` plugin в Gradle, нет `--coverage` при `vitest`/`jest`, нет `pytest-cov` / `coverage.py` в Python CI.
- **Риск:** невозможно отслеживать, что новая фаза добавила код без тестов (а этот проект живёт в режиме «фаза за фазой»). Регрессия покрытия проходит незамеченной.
- **Как чинить:**
  - Gradle root: применить `jacoco` плагин ко всем `*-app` подпроектам, собирать `jacocoTestReport` на каждый `check`, агрегировать через `jacoco-report-aggregation`.
  - Опубликовать отчёт в PR: `madrapps/jacoco-report@v1` с минимальным порогом 60%.
  - Для фронтов: `vitest run --coverage` с `thresholds.lines: 60`.
  - Для python: `pytest --cov=bot --cov-fail-under=70 tests/`.
- **Зависимости:** после внедрения — станет видно, что attendance-latecheckin/ и handlers/excuse.py дают <20%.

### P1-4: PWA/web-panel: нет тестов logout-lifecycle

- **Где:**
  - `frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx` — не проверяет, что при logout:
    - IndexedDB / SW cache с `headman-api-cache-v1` очищается (09 P0-4);
    - push-subscription отправляется на `/api/push/unsubscribe` (09 P0-5).
  - `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` — не проверяет, что при logout:
    - Три STOMP-клиента (admin, student, headman) дисконнектятся (10 P1-5);
    - sessionStorage очищается (10 P0-4).
- **Что:** хеппи-кейсы покрыты, corner-кейс (ключевой для multi-user devices) — нет.
- **Риск:** после фикса (09 P0-4/5, 10 P0-4) — регрессия пройдёт незамеченной.
- **Как чинить:**
  - `AuthProvider.logout.test.tsx`: замокать `caches.delete`, вызвать `logout()`, проверить что `caches.delete('headman-api-cache-v1')` вызван.
  - `auth.service.logout.spec.ts`: спай на всех трёх STOMP-сервисах + `sessionStorage.removeItem`.

### P1-5: Отсутствие contract-тестов для RabbitMQ событий (кроме excuse)

- **Где:**
  - Есть: `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java` — сверяет payload JSON с `event-schemas/excuse.requested.schema.json`.
  - Нет: аналогичного теста для `lesson.started`, `lesson.closed`, `lesson.cancelled`, `attendance.marked`, `homework.*`, `group.archived`, `group.renamed`, `user.*`, `otp.verified`, `latecheckin.*`.
- **Что:** в `event-schemas/` ~15 схем, но сверяется только excuse. Если поле `lesson.started.groupId` (`int64`) подменили на `string`, downstream-сервисы сломаются в проде, тест этого не поймает.
- **Риск:** event-schema evolution без контроля — любое изменение поля = прод-инцидент.
- **Как чинить:**
  - Параметризовать `ExcuseEventContractIT` — шаблон на все события. В каждом publisher-тесте: подписаться на exchange, опубликовать событие, проверить соответствие schema.
  - Или вынести в отдельный модуль `services/contract-tests/` с матрицей (producer-сервис, событие, schema).

### P1-6: Нет contract-тестов proto-клиент ↔ proto-сервер

- **Где:**
  - client-side unit: `services/attendance-service/.../AcademicGrpcClientTest.java` (9 @Test), `ScheduleGrpcClientTest.java` (8 @Test) — мокают stub.
  - server-side unit: `services/schedule-service/.../ScheduleGrpcServiceImplTest.java` (8 @Test) — поднимает InProcessServer.
  - IT: `AcademicGrpcIntegrationTest.java` (17 @Test) + `LessonsByIdsGrpcIT.java`, `ScheduleGrpcResolveLessonIT.java`.
- **Что:** client и server тестируются **по отдельности**. Нет ни одного теста, где клиент из attendance-service делает вызов на реальный server из schedule-service. Если в `proto/schedule.proto` переименовали поле — клиент-стаб обновится после `gradle generateProto`, и тесты в attendance пройдут, но если schedule не переиздаст — прод-бага.
- **Риск:** proto-drift. Протокол ломается в одном сервисе, тест другого не замечает.
- **Как чинить:**
  - Либо общий модуль `proto-contract-tests` с docker-compose'ом (ставит обе .jar в InProcessServer/InProcessChannel).
  - Либо простой smoke: в `deploy.yml` после старта prod — пинг всех gRPC-методов через `grpcurl`. Не contract, но хотя бы sanity.

### P1-7: Mini-app: нет теста TMA initData валидации

- **Где:** `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx` (7 `it()`).
- **Что:** AuthProvider предполагает получение `Telegram.WebApp.initData` от Telegram iframe. Валидацию (подпись HMAC-SHA256 на TMA_BOT_TOKEN) делает auth-service (`services/auth-service/.../TmaIntegrationTest.java`). Но **ни одного end-to-end теста**, что mini-app корректно шлёт initData до auth-service.
- **Риск:** если на фронте забыли передать `initDataUnsafe` или `initData`-строку с подписью, auth-service отклонит; отладка в проде — неудобная (Telegram DevTools).
- **Как чинить:**
  - В mini-app добавить тест, что при `window.Telegram.WebApp.initData === 'query_id=..&user=..&hash=..'` клиент делает `POST /api/auth/tma/login` с этим initData в теле.
  - На auth-service `TmaIntegrationTest` уже проверяет валидацию — connect'им MSW / mock `fetch`.

### P1-8: CI (ci.yml) на `branches: ['**']` — тесты не гейтят деплой

- **Где:** `.github/workflows/ci.yml:3-7`, `.github/workflows/deploy.yml:3-5` (см. 13 P0-2).
- **Что:** push в `main` запускает оба workflow параллельно. `deploy.yml` не ждёт `ci.yml`. Красные тесты не блокируют выкат в прод.
- **Риск:** прод может уйти в состоянии, в котором тесты падают. Branch protection rules не настроены (нельзя проверить без доступа к GitHub UI, но косвенно — через отсутствие `required_status_checks` в деплой-флоу).
- **Как чинить:** см. 13 P0-2 — объединить workflow или включить branch protection.

### P1-9: Нет тестов на WebSocket/STOMP lifecycle (connect/auth/reconnect)

- **Где:**
  - `services/notification-service/.../JwtHandshakeInterceptorTest.java` (6 @Test) — проверяет только ticket-flow на handshake.
  - `services/notification-service/.../EventConsumerTest.java` (15 @Test) — unit-мок STOMP `messagingTemplate`.
  - Нет: end-to-end теста, где фронт-клиент подключается, получает STOMP-приветствие, отправляет SUBSCRIBE, получает сообщение.
- **Что:** heartbeat/reconnect-logic на клиенте (PWA/web-panel) — не покрыты. `frontends/pwa/.../useStompCheckin.test.ts` (5 `it()`) — покрывает только `useStompCheckin` хук, но не реальный STOMP через sockjs.
- **Риск:** при дропе соединения (VPS перезапуск) поведение reconnect'а — не гарантировано.
- **Как чинить:**
  - notification-service: `StompIntegrationTest` с RANDOM_PORT, подключение через `StandardWebSocketClient` и проверка, что событие доставляется.
  - PWA: замокать WebSocket и проверить `onclose` → `setTimeout(reconnect, ...)` цикл.

## Средние (P2)

### P2-1: Несогласованное именование `*Test.java` vs `*IT.java`

- **Где:** 17 файлов `*IT.java`, ~48 файлов `*Test.java` (из них многие — реальные integration-тесты, например, `RestApiIntegrationTest.java`, `CacheIntegrationTest.java`, `LessonStatusTransitionJobTest.java`).
- **Что:** Maven Failsafe / Gradle'овский `integrationTest` source-set различает по суффиксу. Сейчас `integrationTest`-sourceSet не настроен, все тесты идут в `test` → гоняются в `check`. На будущее, когда захочется параллелить unit vs integration — понадобится правила именования.
- **Как чинить:** договориться: `*IT.java` = обязательно требует Testcontainers / RANDOM_PORT Spring; `*Test.java` = чистый unit (JUnit + Mockito). Ручной refactor — ~30 файлов.

### P2-2: `@MockitoBean` мок-бины в integration-тестах

- **Где:** 36 вхождений `@MockBean`/`@MockitoBean` в 25 файлах (см. список выше).
- **Что:** в интеграционном тесте мокать `RabbitTemplate` или `AcademicGrpcClient` — значит тестировать только путь до вызова, а не реальный побочный эффект. Для `LessonStatusTransitionJobTest` мок `Clock` — OK; мок `AcademicGrpcClient` — обходит реальный gRPC; это ухудшает доверие к IT.
- **Как чинить:** подменять не-beans — через `TestConfiguration` с реальной реализацией поверх Testcontainers. Мокать только то, что недоступно локально (например, Telegram API).

### P2-3: Нет тестов reminders в notification-service (3 напоминания)

- **Где:** `services/notification-service/notification-app/src/test/` — нет `Reminder*`-тестов.
- **Что:** отчёт 05 P0-5 отмечает, что 3 напоминания (начало/середина/конец пары) **не реализованы** в Java-сервисе (по факту — только в python-боте). Если/когда будут реализованы — тест должен быть.
- **Как чинить:** при имплементации reminders (должен быть `@Scheduled` job с ShedLock) — добавить `ReminderSchedulerTest` с моком `Clock` (по аналогии с `LessonStatusTransitionJobTest`).

### P2-4: Нет тестов на `LessonGenerationService` week-parity drift

- **Где:** `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonGenerationServiceTest.java` (10 @Test) + `OneOffLessonSchemaIT.java`.
- **Что:** отчёт 03 P0-4 отмечает дрейф week-parity между `LessonGenerationService` (по ISO неделям) и `OneOffLessonService` (по локальным неделям семестра). Тесты LessonGenerationService есть, но не включают кросс-проверку с one-off.
- **Как чинить:** добавить `WeekParityConsistencyTest`, где создаётся item на «1-ю» неделю, one-off lesson на ту же дату, и проверяется, что обе стороны согласны какой это week (ODD/EVEN).

### P2-5: Web-panel: multi-STOMP logout не покрыт

- **Где:** `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` (25 `it()`) — обширный тест auth-сервиса, но logout только проверяет clear state.
- **Что:** 10 P1-5 — 3 параллельных STOMP-клиента (admin/student/headman). При logout они должны дисконнектиться все три, в текущей реализации нет гарантии.
- **Как чинить:** `auth.service.logout.stomp.spec.ts` — внедрить три моковых STOMP-сервиса, вызвать logout, проверить `disconnect()` у каждого.

### P2-6: Ни один frontend не тестирует CSP-compatibility

- **Где:** frontend-тесты.
- **Что:** CSP (13 P0-4) блокирует CDN лендинга. Это runtime-ошибка, которую не поймать в unit. Но минимальный smoke-тест (поднять nginx container с CSP header, curl на landing/index.html с `<script src="...">` и проверить отсутствие `script-src-elem` violations) — технически возможен.
- **Как чинить:** e2e в Playwright (никакого нет сейчас на фронтах) с проверкой `console.error` на CSP-violations.

### P2-7: `notification-bot` нет тестов на `bot/services/grpc_secret.py` или равнозначном

- **Где:** `services/notification-bot/bot/grpc/*.py` — если grpc_secret используется.
- **Что:** отчёт 06 P0-2 — `grpc_secret=""` default, без теста, который бы поймал empty-secret-boot.
- **Как чинить:** в `test_academic_client.py` / `test_schedule_client.py` добавить тест, что при пустом/None GRPC_SECRET клиент падает при старте.

### P2-8: Нет нагрузочных тестов

- **Где:** нигде.
- **Что:** `k6`/`Gatling`/`Locust` — нет. ОК для MVP, но до релиза v0.0.0 стоит прогнать хотя бы базовый сценарий (login → OTP verify → геоотметка → fetch schedule) с 100 concurrent.
- **Как чинить:** минимальный `scripts/load-test.js` на k6, запуск вручную перед релизом.

### P2-9: Flyway migrations — только academic имеет MigrationIT

- **Где:** `services/academic-service/.../HomeworkMigrationIT.java`. В auth/schedule/attendance — нет.
- **Что:** `ddl-auto: validate` — Hibernate проверит, что схема совпадает с JPA-маппингом при старте, но это не то же самое, что «миграции от V1 до Vn применяются в правильном порядке и не ломают существующие данные».
- **Как чинить:** в auth/schedule/attendance добавить по одному `MigrationIT`, который поднимает пустую Postgres/Mongo и прогоняет миграции в проверяемом порядке.

### P2-10: Коммьюнити-зависимость: `fakeredis` в Python

- **Где:** `services/notification-bot/tests/conftest.py:1` — `import fakeredis`.
- **Что:** fakeredis не поддерживает 1:1 всё API Redis (Lua-скрипты, pub/sub в редких сценариях). Для простых KV/TTL работает.
- **Риск:** тест проходит на fakeredis, падает на реальном Redis.
- **Как чинить:** рассмотреть `testcontainers-python` с `redis:7-alpine`. Для текущего объёма (KV operations) — fakeredis ОК.

### P2-11: Web-panel Angular тесты — нет e2e (Playwright/Cypress)

- **Где:** `frontends/web-panel/package.json` — `ng test` (karma/jasmine или vitest, зависит от конфига).
- **Что:** 389 unit-тестов, но ни одного e2e. Ни одного теста флоу «логин → админка → создать группу → выйти».
- **Как чинить:** добавить минимальный Playwright scenario для web-panel и PWA: login → main page → logout. Запускать в CI как отдельный job.

### P2-12: `frontends/mini-app/` тесты не мокают `window.Telegram.WebApp`

- **Где:** `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx`.
- **Что:** тесты написаны в предположении, что `window.Telegram` существует — если `test-setup.ts` его не инжектит, тесты либо пропадут тихо, либо упадут с `Cannot read properties of undefined`.
- **Как чинить:** `vitest.setup.ts` с `globalThis.Telegram = { WebApp: { initData: '...', initDataUnsafe: {...} } }`.

### P2-13: `notification-service/EventConsumerTest` мокает `SimpMessagingTemplate`

- **Где:** `services/notification-service/.../event/EventConsumerTest.java` (15 @Test).
- **Что:** проверяет, что при событии `lesson.started` вызывается `template.convertAndSendToUser(...)`, но не проверяет реальную доставку до WebSocket-клиента. Реальный end-to-end тест — отсутствует.
- **Как чинить:** добавить `StompIntegrationTest` в IT, подключиться через `WebSocketStompClient`.

### P2-14: Нет тестов на `OAuth-style TMA flow` (при невалидной HMAC-подписи)

- **Где:** `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/TmaIntegrationTest.java` (6 @Test).
- **Что:** проверяется happy-path. Тест «подменили HMAC-подпись → 401» — не очевидно, что есть.
- **Как чинить:** явно добавить `tmaLogin_invalidHmac_returns401()`.

### P2-15: Landing — ноль тестов

- **Где:** `frontends/landing/`.
- **Что:** статический HTML+CSS+JS. В конфиге `package.json` (если есть) — нет `test` скрипта.
- **Риск:** изменение GSAP-анимации или добавление интерактивной формы может сломаться без ревью.
- **Как чинить:** минимальный Playwright smoke — `page.goto('/presentation/')`, проверить отсутствие `console.error`, проверить, что hero-анимация запускается (через visibility check).

## Мелкие и nit (P3)

### P3-1: Несогласованная структура `__tests__/` vs sibling-test file

- **Где:** PWA — `features/checkin/__tests__/CheckInButton.test.tsx` vs `features/headman/excuses/ExcusesPage.test.tsx` (sibling, без `__tests__`).
- **Как чинить:** выбрать один стиль (обычно — sibling для простоты).

### P3-2: `AbstractAcademicIntegrationTest` × 3 наследника

- **Где:**
  - `AbstractAcademicIntegrationTest.java` — базовый;
  - `AbstractAcademicCacheIntegrationTest.java` — +Redis;
  - `AbstractAcademicEventIntegrationTest.java` — +RabbitMQ.
- **Что:** декомпозиция логичная, но наследование глубже одного уровня создаёт конфузы (тест, использующий Cache + Event, должен выбирать один предок).
- **Как чинить:** использовать Spring `@TestPropertySource`-композицию через interfaces, не inheritance.

### P3-3: `pwa/src/__tests__/sw-runtime-cache.test.ts` — тестирует SW через jsdom

- **Где:** 12 `it()`.
- **Что:** jsdom не поддерживает Service Worker API. Тест использует шим. Работает, но хрупко — при обновлении vitest/jsdom сломается.
- **Как чинить:** переехать на Playwright + реальный SW (в браузере).

### P3-4: `test_reminder_scheduler.py` тестирует reminder scheduler, но reminder-ы не реализованы в Java

- **Где:** `services/notification-bot/tests/test_reminder_scheduler.py` (8 @Test).
- **Что:** в notification-service (Java) нет reminder'ов (05 P0-5). В notification-bot — есть и покрыты. Значит, reminder лежит только в Python. Это архитектурно странно: «Java-сервис — stateless event-forwarder, Python-бот — scheduler».
- **Как чинить:** либо перенести reminders в Java-сервис (единая ответственность за бизнес-логику), либо задокументировать как design decision.

### P3-5: `test_academic_client.py` — 5 тестов при 10+ методах клиента

- **Где:** `services/notification-bot/tests/test_academic_client.py` (5 @Test).
- **Что:** покрывает `get_user`, `get_group`, ~50% методов. Редкие методы (`get_user_by_telegram_id`, `list_students_by_group`) — не покрыты.
- **Как чинить:** либо 1 параметризованный тест на все методы (sanity: returns non-null), либо отдельные.

### P3-6: `ScheduleViewTest` — только happy path composite view

- **Где:** `services/schedule-service/.../integration/ScheduleViewTest.java` (5 @Test).
- **Что:** тестирует три слоя (item + one-off + cancel) вместе, но только happy. Corner: конфликтующие one-off + cancel на одну дату.
- **Как чинить:** добавить corner test'ы.

### P3-7: Тесты без `should_` / `when_` / `given_` prefix

- **Где:** ~30% Java-тестов. Например, `GroupServiceTest.java`:
  ```java
  @Test void createGroup_whenValid_succeeds() { ... }  // хорошо
  @Test void simpleCase() { ... }                       // плохо
  ```
- **Что:** имена методов не всегда описывают условие/результат.
- **Как чинить:** code convention: `{method}_{condition}_{expectation}` или Kotlin-style `` `Groups can be created` ``.

### P3-8: `_test_` фикстуры без cleanup между тестами

- **Где:** `services/schedule-service/.../lesson/LessonStatusTransitionJobTest.java:77-81` — `@AfterEach cleanup: lessonRepository.deleteAll(); scheduleItemRepository.deleteAll();`
- **Что:** это хорошо (явный cleanup), но не все тесты делают так. В `EventIntegrationTest` (academic) cleanup нет — опираются на `@Transactional` + rollback. Тесты могут флейкать при параллельном выполнении.
- **Как чинить:** единая политика — либо транзакция-и-rollback (но тогда не сработает AFTER_COMMIT), либо явный cleanup.

## Мёртвый код

- `services/notification-bot/tests/test_reminder_scheduler.py` (8 @Test) — тестирует функционал, которого нет в Java-сервисе (P3-4, 05 P0-5). Если reminders останутся только в боте — тест OK. Если перенесут в Java — тесты Python становятся устаревшими.
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/config/PublicKeyConfigTest.java` (2 @Test) — 07 отмечает что publicKey init НЕ fail-fast. Тест проверяет happy-path, но не проверяет поведение при падении auth-service. То есть тест не даёт того, что предполагает.

## Костыли и TODO/FIXME

- `services/schedule-service/.../LessonStatusTransitionJobTest.java:46-49`:
  ```
  IMPORTANT: Tests are NOT @Transactional. Using @Transactional would cause rollback
  instead of commit, so the @TransactionalEventListener(AFTER_COMMIT) in DomainEventListener
  would never fire. Manual @AfterEach cleanup is used instead.
  ```
  Честный комментарий — документирует известную ловушку.
- `services/notification-service/.../WebPushDeliveryServiceTest.java:44`:
  ```
  // Stub createNotification to avoid real EC key parsing in all tests
  doReturn(mockNotification).when(service).createNotification(...);
  ```
  Спай на собственный сервис для обхода криптовалидации. Значит, реальная `createNotification` (которая парсит EC-ключ подписчика) не тестируется.
- `services/notification-bot/tests/conftest.py:8` — `fakeredis.FakeAsyncRedis(decode_responses=True)`. Документировать ограничения (P2-10).

## Тесты

### Что покрыто хорошо

- **Academic service**: CRUD users/groups/semesters, REST + HATEOAS, Redis cache hit/miss, RabbitMQ publish events, Hibernate schema validation, gRPC server-side, RoleCheck AOP — суммарно ~170 @Test, 22 integration-теста.
- **Schedule service**: LessonGenerationService (основной генератор из `ScheduleItem`), LessonStatusTransitionJob (планировщик переходов), one-off lessons (с schema IT), gRPC resolveLesson — ~100 @Test.
- **Attendance service**: checkin + report изоляция, gRPC клиенты, rate-limiter, MongoIndex (unique constraint), excuse-flow end-to-end (включая contract-тест!), enum serialization — ~140 @Test.
- **Auth service**: login flow, OTP happy-path, TMA initData — ~27 @Test.
- **Gateway**: JWT валидация (подпись, expiry, public routes, X-User-* strip) — 13 @Test.
- **Notification-bot**: все event-consumer'ы, redis/jwt/send_queue утилиты — 139 тестов.
- **Web-panel**: auth guards/interceptor, 4 ролевых guards, Angular компоненты — 389 `it()`.
- **PWA**: AuthProvider (hash + isHeadman), checkin button + STOMP hook, schedule page, push utils, SW runtime cache — 122 `it()`.

### Что покрыто плохо / не покрыто

- **`attendance-service/latecheckin/`** — 0 тестов (P0-1).
- **`notification-bot/bot/handlers/*.py` callback_query handlers** — 0 unit-тестов (P0-2).
- **LoginRateLimiter / OtpRateLimiter** (auth) — 0 тестов (P1-2).
- **Reminders в notification-service** — не реализованы, но при реализации тестов не будет (P2-3).
- **E2E флоу** (login → геоотметка → журнал) — 0 тестов.
- **Contract-тесты событий** — только `ExcuseEventContractIT`, остальные 14+ событий без проверки (P1-5).
- **gRPC proto contract client↔server** — 0 end-to-end тестов (P1-6).
- **Frontend logout lifecycle** — 0 тестов на SW cache/push/STOMP cleanup (P1-4).
- **Mini-app TMA initData валидация** — 0 end-to-end (P1-7).
- **Flyway миграции auth/schedule/attendance** — 0 migration-IT (P2-9).
- **Coverage в CI** — 0 (P1-3).
- **Нагрузочные** — 0 (P2-8).
- **Security тесты**: SQL injection / path traversal / CSRF — не прицельные; CORS — не покрыт тестами (проверен только конфиг).
- **Landing** — 0 тестов (P2-15).

### Некорректные/подозрительные тесты

- `WebPushDeliveryServiceTest` (11 @Test) — спайит `service.createNotification`, не тестирует реальный парсинг EC-ключа. Если в `PushSubscriptionDocument` придёт невалидный `p256dh`, тест это не поймает.
- `JwtAuthenticationFilterTest` (11 @Test) — проверяет, что Gateway strip'ит X-User-*, но не проверяет **что ставит** (после валидации JWT). Только косвенно.
- `SecuritySmokeTest.attendance.java` — проверяет 403 на `@RequireRole`-эндпоинте, но в attendance есть публичные endpoints без аннотации (например, `/attendance/health-check` имеет аннотацию — тест её и использует; а вот `/attendance/report/*` без аннотации — не покрыт).
- `test_reminder_scheduler.py` — тестирует scheduler, которого архитектурно не должно быть на python-стороне (05 P0-5).
- `LessonCancelEventTest.java` — 1 @Test на весь файл. Это достаточно? Скорее всего — нет.

### Кандидаты на удаление/рефакторинг

- **Пока — ни одного очевидного кандидата на удаление**. Все тесты проверяют реальное поведение (никаких «assertion-free»).
- Кандидаты на рефакторинг:
  - Объединить `Abstract*IntegrationTest` в один с composition (P3-2).
  - Перенести PWA SW-тесты из jsdom в Playwright (P3-3).
  - Унифицировать именование: `*IT.java` = integration, `*Test.java` = unit (P2-1).

## Соответствие CLAUDE.md

| Правило из CLAUDE.md                                      | Статус | Комментарий |
|-----------------------------------------------------------|:------:|-------------|
| `ddl-auto: validate` — Hibernate проверяет схему          |   ✅   | В тестах используется Testcontainers с реальной Flyway-миграцией |
| Enum lowercase в PostgreSQL                               |   ✅   | `EnumSerializationTest` в attendance покрывает |
| Request=record, Response=class                            |   ⚠   | Нет типового теста, что Request — record (скорее compile-time). @Test для Response.HATEOAS отсутствует |
| HATEOAS Level 3 — `_links`, `EntityModel`, `PagedModel`    |   ⚠   | `RestApiIntegrationTest` (academic) проверяет `_links`, но аналога нет в schedule/attendance |
| RFC 7807 Problem Details                                  |   ⚠   | `GlobalExceptionHandlerTest` (academic, 10 @Test) ОК; `SecuritySmokeTest` проверяет `$.status`; в других сервисах тесты фрагментарные |
| `@ControllerAdvice` централизация                          |   ✅   | `GlobalExceptionHandlerTest` проверяет |
| Isolation: `report/` не импортирует `checkin/`             |   ✅   | `ReportDomainIsolationTest.java` — explicit check |
| Testcontainers для IT                                     |   ✅   | 17 `*IT.java` + 8 `Abstract*IntegrationTest.java` используют Testcontainers |
| JWT подпись, expiry, role-check                           |   ✅   | `JwtAuthenticationFilterTest` (11 @Test) + OTP/auth IT |
| 3 напоминания (reminders)                                 |   ❌   | Не реализованы в Java (05 P0-5). Тесты в Python-боте покрывают там |
| Роли STUDENT / TEACHER / ADMIN / HEADMAN                   |   ✅   | `SecuritySmokeTest` + `RoleCheckAspectTest` |
| Логин `student00001`, `teacher00001`, тестовые            |   ⚠   | Не нашёл теста, фиксирующего эти логины |

## Зависимости между проблемами

- **P0-1 (latecheckin — 0 тестов)** и **04 P0 (latecheckin без тестов)** — дубль. Закрывается одной порцией работы.
- **P0-2 (callback_query без тестов)** сцеплен с **06 P0-5 (callback без проверки роли старосты)** — security-fix + тест идут парой.
- **P1-3 (no coverage gate)** — ядро. После внедрения JaCoCo/Istanbul метрики покрытия раскроют остальные P1/P2: latecheckin=0%, callback_query=0%, reminders в Java=нет, и т.д.
- **P1-5 (contract-тесты событий только для excuse)** и **08 P0-2 (нет схемы `otp.requested`)** — связаны: сначала нужна полная библиотека schema, потом contract-тесты.
- **P1-6 (gRPC proto drift)** и **08 P1 (proto ↔ OpenAPI type drift)** — связаны. Решение: единый `proto-gen` → DTO.
- **P1-1 (no gateway↔downstream contract)**, **02/03/04/05 P0 (UserContextFilter доверяет X-User-*)** — контрактный тест «прямой запрос на downstream без заголовков → 401» закрыл бы все четыре.
- **P1-8 (CI не гейтит deploy)** = **13 P0-2**. Один фикс (branch protection + needs:).
- **P2-4 (week-parity consistency)** и **03 P0-4** — тест поймает drift после фикса.

## Вопросы к владельцу проекта

1. **Coverage**: какой целевой уровень для v0.0.0 — 50%/60%/70%? (Предлагается начать с 60% по lines для Java и 70% для Python-бота.)
2. **E2E tests**: стоит ли добавлять Playwright сейчас (до релиза) или отложить на v0.1?
3. **`latecheckin/` без тестов**: фича production-ready или ещё в разработке? Если не ready — стоит ли вообще выключить endpoint'ы feature-flag'ом?
4. **Reminders в notification-service**: переносим в Java (единая ответственность за бизнес-логику) или оставляем в python-боте (тогда 05 P0-5 закрываем как "by design")?
5. **Contract-тесты событий**: писать на каждое событие отдельный IT (15 штук) или единый параметризованный?
6. **Тесты rate-limit**: ОК ли использовать fakeredis-python и Testcontainers-Redis для Java?
7. **`*IT.java` vs `*Test.java`**: готовы ли к массовому renaming для разделения source-set'ов (~30 файлов)?
8. **Load tests**: k6/Gatling/Locust — какой инструмент предпочтителен? (k6 — легче, Gatling — на Java, ближе к стеку.)
9. **`test_reminder_scheduler.py`**: если reminders всё-таки перенесём в Java — удалять python-тесты или оставить для обратной совместимости?
10. **Gateway↔downstream contract tests**: отдельный `services/e2e-tests/` модуль (Testcontainers compose) или через docker-compose up в CI?
