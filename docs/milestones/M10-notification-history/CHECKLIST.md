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
- [ ] Smoke: `docker compose up notification-web` → **отложено до G9**
      (требует `down -v` existing mongo volume — delaying breaking
      change до финального stack test; compose config валиден)

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
      publish → await persist + skip broadcast (запуск отложен в G9
      full-stack run; awaitility:4.2.2 добавлен)
- [x] `application.yml` — default URI `notification_db` +
      `notification.history.ttl-days`

## Группа 4 — Service + Caffeine cache

- [ ] `CaffeineConfig.java` — bean `CacheManager` с `unread-count` cache
      (maximumSize=10000, expireAfterWrite=30s)
- [ ] `NotificationHistoryService.getUnreadCount(userId)` —
      `@Cacheable(cacheNames="unread-count", key="#userId")`
- [ ] `NotificationHistoryService.markAsRead(id)` — evict cache для userId
- [ ] STOMP publisher: при publish new notification →
      `cacheManager.getCache("unread-count").evict(userId)`
- [ ] Test: Caffeine cache hit/miss + STOMP invalidation

## Группа 5 — REST controller

- [ ] `NotificationController` implements NotificationApi
- [ ] Endpoint: GET list (pagination + HATEOAS PagedModel)
- [ ] Endpoint: GET unread-count (Caffeine cached)
- [ ] Endpoint: PATCH {id}/read
- [ ] Endpoint: POST mark-all-read
- [ ] RFC 7807 error handling (shared-web из M01)
- [ ] api-gateway route `/api/notifications/**` → lb://notification-web
- [ ] Integration test: полный flow login → publish event → GET list →
      PATCH read → unread-count decrements

## Группа 6 — Frontend PWA migration

- [ ] `frontends/pwa/src/api/notifications.ts` — TanStack Query hooks:
      `useInfiniteQuery(['notifications'])`,
      `useQuery(['unread-count'])`, `useMutation(markAsRead)`,
      `useMutation(markAllRead)`
- [ ] `frontends/pwa/src/components/NotificationCenter.tsx`:
      убрать sessionStorage read, использовать useInfiniteQuery
- [ ] Optimistic mutations: setQueryData для instant UI update
- [ ] sessionStorage остаётся только для local optimistic patch
      (не authoritative)
- [ ] STOMP new-event → `queryClient.invalidateQueries(['unread-count'])`
- [ ] Infinite scroll / «Показать ещё» UI
- [ ] unit-test `NotificationCenter.test.tsx` (MSW mock) — в M08
      Группа 6, здесь smoke

## Группа 7 — Frontend web-panel migration

- [ ] `frontends/web-panel/src/app/shared/notification-center/
      notification.service.ts` — HttpClient pagination
- [ ] Signal `unreadCount` с 30s staleTime
- [ ] `mark-as-read` / `mark-all-read` через HttpClient + optimistic
- [ ] `notification-center.component.ts` — инжект service, template
      с `*ngFor` + «показать ещё»
- [ ] STOMP invalidate через WebSocket service (QC1 unified в M07)

## Группа 8 — Docs + CLAUDE.md + cleanup

- [ ] `docs/architecture.md` — раздел «Notification History»:
      schema, consumer flow, Caffeine cache strategy, pagination API
- [ ] `docs/database-schema.md` — Mongo `notification_db` раздел
      (NEW-166)
- [ ] `docs/data-retention-policy.md` — раздел 30d TTL для
      notification_history
- [ ] `CLAUDE.md` Services table: `Notification Web | 9094 | Spring
      Boot WebSocket (STOMP) + Caffeine | MongoDB (notification_db)`
- [ ] `CLAUDE.md` примечание убрать «становится stateful после M04
      — см. NEW-168»

## Группа 9 — Финализация

- [ ] `./gradlew :notification-web:build` зелёный
- [ ] `./gradlew :notification-web:integrationTest` зелёный
- [ ] PWA build + web-panel build зелёные
- [ ] `docker compose up -d` → все 5 сервисов + notification-web + mongo
      стартуют healthy
- [ ] Manual UAT: login headman, открыть NotificationCenter, увидеть
      историю за 30d, mark-as-read работает, unread-count обновляется
- [ ] TTL проверка: insert фиктивный doc с `sent_at = now - 31d`,
      подождать TTL monitor cycle, doc удаляется
- [ ] Post-mortem в PLAN.md
- [ ] Tag `v0.0.0-alpha.10`

---

_Если задача превращается в 6+ часов работы — разрежь её._
