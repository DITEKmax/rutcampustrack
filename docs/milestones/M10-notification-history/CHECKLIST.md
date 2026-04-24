# M10 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.

## Группа 1 — MongoDB schema setup (NEW-166)

- [x] `infra/mongo/init-mongo.js` — создание `notification_user` +
      права readWrite+dbAdmin на `notification_db` (D2 PoLP; существующий
      `MONGO_USER` оставлен только для attendance_db)
- [x] `docker-compose.yml` — mount init script, env
      `MONGO_NOTIFICATION_USER/PASSWORD`, URI → `notification_db`,
      `NOTIFICATION_HISTORY_TTL_DAYS` env (D3, D4)
- [x] `docker-compose.prod.yml` — аналогично + placeholder
      `CHANGE_ME_BEFORE_DEPLOY` в `.env.prod`
- [x] `docs/runbooks/secret-rotation.md` — новая секция «MongoDB —
      notification» + запись в таблицу
- [x] `docs/future-ideas.md` — v0.1 «Notification retention collMod
      auto-reconciler»
- [x] Smoke: `docker compose down -v && up -d` (G9.2 2026-04-24) —
      все сервисы healthy, init-mongo создал обоих PoLP user'ов
      (rct_user / rct_notification_user). running notification-web
      image от 2 weeks ago (не M10) — валидация M10 кода через
      Testcontainers IT.

## Группа 2 — notification-api-contract module

- [x] Модуль УЖЕ был (D1); `build.gradle.kts` дополнен
      `spring-data-commons:3.4.1` для Pageable / PagedResourcesAssembler
- [x] `settings.gradle.kts` — include уже был
- [x] `NotificationType.java` enum (11 values: EXCUSE_*/LATE_CHECKIN_*/
      LESSON_*/ATTENDANCE_RED_ZONE)
- [x] `NotificationHistoryDto.java` — class extends RepresentationModel
      (CLAUDE.md: Response = class для HATEOAS)
- [x] `UnreadCountDto.java` — record (simple response без HATEOAS)
- [x] `NotificationApi.java` interface — GET list / GET unread-count /
      PATCH {id}/read / POST mark-all-read, @Operation + @ApiResponses
- [x] `./gradlew :services:notification-service:notification-api-contract:build`
      зелёный (commit d6c0f14+)

## Группа 3 — Backend entity + repository + consumer

- [x] `history/NotificationHistoryDocument.java` (@Document
      collection=notification_history) с полями id/user_id/type/payload/
      sent_at/read_at/trace_id
- [x] `history/NotificationHistoryMongoConfig.java` — 3 индекса через
      `IndexOperations.ensureIndex`: idx_user_sent_desc, idx_user_read,
      ttl_sent_at (env `NOTIFICATION_HISTORY_TTL_DAYS`)
- [x] `NotificationHistoryRepository extends MongoRepository` — methods:
      findByUserIdOrderBySentAtDesc (page), findByUserIdAndReadAtIsNull
      (page), countByUserIdAndReadAtIsNull, markRead, markAllRead
- [x] `NotificationHistoryConsumer` — @RabbitListener на
      `notification-web.history` (D5 naming; PLAN говорил
      `notification.history`)
- [x] `NotificationHistoryRabbitConfig` — queue + DLQ + binding на
      существующий fanout `rut-uit.events` (не `notification.events` —
      D5)
- [x] Error handling: try/catch + warn log, no rethrow (acknowledge
      идёт всё равно — persistence bug должен быть viewable в logs, не
      зацикливать requeue)
- [x] Denormalize mapper — 9 user-facing event types (D6);
      broadcast (lesson.*) и system events skip'аются
- [x] Unit tests: NotificationHistoryConsumerMapTypeTest (7) +
      NotificationHistoryConsumerTest (5). `./gradlew :...:test` зелёный
- [x] IT: `NotificationHistoryConsumerIT` — Testcontainers Mongo+Rabbit,
      publish → await persist + skip broadcast. **Verified G9 (2026-04-24):**
      2/2 tests pass; logs подтверждают `notification_history collection
      created` + `indexes ensured: idx_user_sent_desc, idx_user_read,
      ttl_sent_at (TTL 30 days)` после G9 hot-patch (`3d3eec6`).
- [x] `application.yml` — default URI `notification_db` +
      `notification.history.ttl-days`

## Группа 4 — Service + Caffeine cache

- [x] `history/CaffeineConfig.java` — `@Bean CacheManager` (Caffeine
      `maximumSize=10000`, `expireAfterWrite=30s`) на cache `unread-count`
- [x] build.gradle: `spring-boot-starter-cache` + `caffeine:3.1.8`
- [x] `NotificationHistoryService.getUnreadCount(userId)` —
      `@Cacheable(cacheNames="unread-count", key="#userId")`
- [x] `markAsRead` / `markAllRead` — `@CacheEvict` по userId
- [x] `invalidateUnreadCount(userId)` — `cacheManager.getCache().evict()`,
      вызывается `NotificationHistoryConsumer` после persist
- [x] Unit test `NotificationHistoryServiceTest` (6 tests: getUnreadCount,
      markAsRead true/false, markAllRead, invalidate — null-cache safe)
- [x] Unit test `NotificationHistoryConsumerTest` дополнен assert на
      invalidate после persist

## Группа 5 — REST controller

- [x] `history/NotificationController implements NotificationApi`
- [x] Endpoint: `GET /notifications` (Pageable + `PagedResourcesAssembler`
      → HATEOAS PagedModel)
- [x] Endpoint: `GET /notifications/unread-count` (Caffeine-cached)
- [x] Endpoint: `PATCH /notifications/{id}/read` — 204 on ok, 403 if
      not-owner / not-found (не раскрываем существование чужих docs)
- [x] Endpoint: `POST /notifications/mark-all-read` → 204
- [x] RFC 7807 error handling — доменный `AccessDeniedException` уже
      обработан `NotificationExceptionHandler` (M01 shared-web RFC 9457)
- [x] `@RequireRole({STUDENT, TEACHER, ADMIN})` на всех endpoints
- [x] api-gateway route `/api/notifications/**` → notification-web:9094
      с StripPrefix + rate-limiter 600 rps (same как /api/push/**)
- [x] Unit test `NotificationControllerTest` (5 tests: list/unread/
      markRead 204/403/markAllRead)
- [x] Integration test полного flow — `NotificationHistoryConsumerIT`
      (G3) даёт end-to-end Rabbit → Consumer → Mongo coverage; controller
      покрывается @WebMvcTest unit'ами. Полный E2E через
      Gateway+notification-web stack отложен в M11+ (требует
      Testcontainers fan-out на 5 services).

## Группа 6 — Frontend PWA migration

- [x] `features/notifications/notificationsApi.ts` — REST client
      (fetchHistoryPage / fetchUnreadCount / markNotificationRead /
      markAllNotificationsRead) через общий `apiClient`
- [x] `features/notifications/useNotificationHistory.ts` — TanStack Query
      hooks: `useNotificationHistory` (useInfiniteQuery со sort=
      sentAt,desc), `useUnreadCount`, `useMarkNotificationRead`,
      `useMarkAllRead`. Ключи экспортированы
- [x] `NotificationCenter.tsx` — **hybrid strategy**: sessionStorage
      + live STOMP остаются для broadcast events (`lesson.*`,
      `homework.*`, `group.*` — backend их не persist'ит, D6); на
      новый STOMP event invalidate backend queries
- [x] `markAllRead` — дополнительный best-effort POST на backend с
      TanStack invalidate (offline safe: locally помечено read всё равно)
- [x] unit-test `notificationsApi.test.tsx` (5 tests: HATEOAS парс,
      empty _embedded, unread-count, URL-кодирование id, mark-all-read
      POST)
- [x] `tsc --noEmit` зелёный; `vitest run features/notifications` — 10/10
- [ ] Optimistic mutations + infinite scroll UI — **отложено в v0.1**
      (v0.0.0: broadcast items показываются из sessionStorage, backend
      history доступна через отдельный hook `useNotificationHistory` для
      future server-side view; пользователь не теряет continuity)

## Группа 7 — Frontend web-panel migration

- [x] `core/notifications/notification-history.api.ts` — HttpClient
      REST client: list (Pageable + HATEOAS PagedModel parse),
      unreadCount, markRead (URL-encode), markAllRead
- [x] `core/notifications/notification-history.service.ts` — Signal-
      based façade: `serverUnreadCount`, `firstPage`, `refreshUnreadCount()`,
      `loadFirstPage()`, `markAllRead()` Promise
- [x] `NotificationCenterService` интеграция:
      * inject `NotificationHistoryService`;
      * `handleFrame` после `persist()` → `refreshUnreadCount()`;
      * `markAllRead()` + best-effort `historyService.markAllRead()`
- [x] Unit test `notification-history.api.spec.ts` (5 tests:
      HATEOAS parse, empty embedded, unread-count, URL-encode,
      mark-all-read)
- [x] Existing `notification-center.service.spec.ts` дополнен mock'ом
      NotificationHistoryService, все 6 тестов зелёные
- [ ] Full «Показать ещё» UI на server-side history — **deferred v0.1**
      (D7 hybrid: sessionStorage остаётся authoritative для live UX)

## Группа 8 — Docs + CLAUDE.md + cleanup

- [x] `docs/architecture.md` — раздел «Notification History» в M-stack
      блоке (после Observability): schema, consumer flow с DLQ, REST
      surface, Caffeine cache + STOMP invalidation, frontend hybrid
      strategy. 3.6 Notification Web role обновлён на stateful.
      Commit `c23614f`.
- [x] `docs/database-schema.md` — раздел "MongoDB: notification_db
      (Notification Web — M10)" с notification_history shape, 3 индекса,
      Mongo user separation (PoLP D2). NotificationType enum в общий
      список. Commit `c23614f`.
- [x] `docs/data-retention-policy.md` — row 5a (30d TTL +
      `NOTIFICATION_HISTORY_TTL_DAYS` env) + триггер пересмотра +
      collMod caveat. Commit `c23614f`.
- [x] `CLAUDE.md` Services table: cleanup от «до M10 stateless
      forwarder» → чистая stateful запись. Commit `c23614f`.
- [x] `CLAUDE.md` v0.0.0 milestones table: M10 marked ✅ с timestamp
      2026-04-24. Commit `c23614f`.

## Группа 9 — Финализация

- [x] `./gradlew :notification-app:test` зелёный (23+1 тесты, 6 ConsumerTest +
      11 MapTypeTest + 6 ServiceTest + 5 ControllerTest, после H1 hot-patch).
- [x] `./gradlew :notification-app:integrationTest` зелёный
      (`NotificationHistoryConsumerIT` 2/2 после G9 hot-patches —
      verified explicit createCollection + 3 индекса в логах,
      `notification_history indexes ensured: idx_user_sent_desc,
      idx_user_read, ttl_sent_at (TTL 30 days)`).
- [x] `docker compose up -d` smoke: все сервисы healthy после
      `down -v`; init-mongo.js создал обоих PoLP user'ов
      (`rct_user` на attendance_db, `rct_notification_user` на
      notification_db) — проверено через mongosh `getUsers()`.
- [N/A] `docker compose up` smoke notification-web НЕ верифицирует
      M10 код напрямую — running image (`rutcampustrack-notification-web`)
      от 2 weeks ago, без M10 changes. M10 валидирован Testcontainers
      IT (свежий код). Image rebuild — отдельный M10-orthogonal task
      (S5 в NOTES, see Post-mortem).
- [N/A] Manual UAT (headman + NotificationCenter UI) — отложено как
      smoke-driven validation; M10 G6/G7 frontend изменения покрыты
      unit tests (PWA 5/5 + web-panel 5/5) + Testcontainers IT для
      backend.
- [N/A] Manual TTL проверка через insert/wait — Mongo TTL monitor
      cycle 60s, `expireAfterSeconds=2592000` (30d) подтверждён
      в IT log `(TTL 30 days)`. Полный wait-cycle тест отложен.
- [x] G9 audit: security-auditor (NO HIGH — 4 MEDIUM defer'нуты в
      future-ideas N1/N5/N6 + .env.prod placeholder warning) +
      bug-hunter (2 HIGH H1+H2 пофикшены в `4929d5b`, 7 MEDIUM/LOW
      defer N2/N3/N4/N7/N8/N9/N10).
- [x] G9 hot-patches:
      * `3d3eec6` — explicit `createCollection` + ApplicationReadyEvent
        для index bootstrap (S4 fix, без него TTL и compound-индексы
        не материализовались, retention 30d не работал).
      * `4929d5b` — H1 `excuse.decided` REJECTED через `payload.status`
        + H2 Pageable max-size 100 (DoS guard).
- [x] Post-mortem в PLAN.md (см. ниже)
- [ ] Tag `v0.0.0-alpha.11` (G9.7)

---

_Если задача превращается в 6+ часов работы — разрежь её._
