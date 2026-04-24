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

- [ ] `services/notification-service/notification-api-contract/build.gradle.kts`
      (java-library, БЕЗ Lombok)
- [ ] `settings.gradle.kts` — include module
- [ ] `NotificationType.java` enum (EXCUSE_APPROVED/LESSON_REMINDER/...)
- [ ] `NotificationHistoryDto.java` record
- [ ] `UnreadCountDto.java` record
- [ ] `NotificationApi.java` interface c @RequestMapping + @Operation
      + @ApiResponse (OpenAPI-ready)
- [ ] `./gradlew :notification-api-contract:build` зелёный

## Группа 3 — Backend entity + repository + consumer

- [ ] `domain/NotificationHistory.java` entity (@Document)
- [ ] Mongo index init через `@Indexed` + manual TTL index (Spring Data
      `@CompoundIndex` + init-script TTL)
- [ ] `NotificationHistoryRepository extends MongoRepository`
- [ ] `NotificationHistoryConsumer` — @RabbitListener на
      `notification.history` queue
- [ ] RabbitMQ topology: queue declaration + binding на fanout exchange
      `notification.events`
- [ ] Error handling: try/catch без rethrow, warn log с trace_id
- [ ] Denormalize logic: event → NotificationHistory mapper per type
- [ ] IT: `NotificationHistoryConsumerIT` — Testcontainers Mongo+Rabbit,
      publish event → assert persisted

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
