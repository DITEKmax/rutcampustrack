# M10 — Notification History (Stateful notification-web)

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 4-5 человеко-дней

---

## Scope

Переводит `notification-web` из **stateless event forwarder** в
**stateful сервис** с собственной MongoDB `notification_db`, коллекцией
`notification_history`, pagination REST API и Caffeine кэшем для
unread-count. Закрывает P2-6/4 — главное архитектурное изменение
релиза v0.0.0 (упущенное в M04).

Источники:
- `99-executive-summary.md` — строки 120, 170 (P2-6/4 как главное
  арх. изменение)
- `05-notification-service.md` P2-6/4
- `OWNER-ANSWERS.md`:
  - P2-6/4 (строки 5021-5131) — вариант (b) FULL активирован
  - P2-10/3 (Caffeine cache pattern для unread-count)
  - P2-10/4 (batch endpoints)
- `CLAUDE.md` — ремарка «становится stateful после M04 — см. NEW-168»
  **исправлена на «в M10 — NEW-168, NEW-166, NEW-167»**
- NEW-166, NEW-167, NEW-168 из аудита

**Включено:**

### MongoDB schema (NEW-166)
- **Database:** `notification_db` (новая, P2-9/6 ACTIVATED)
- **Collection:** `notification_history`
- **Document schema:**
  ```
  {
    _id: ObjectId,
    user_id: Long,
    type: String (enum EXCUSE_APPROVED/LESSON_REMINDER/...),
    payload: { /* denormalized from event */ },
    sent_at: ISODate,
    read_at: ISODate | null,
    trace_id: String
  }
  ```
- **Indexes:**
  - `{ user_id: 1, sent_at: -1 }` — list per user DESC
  - `{ user_id: 1, read_at: 1 }` — unread badge count
  - `{ sent_at: 1 }` TTL `expireAfterSeconds: 2592000` (30 дней)
- **Mongo user:** `notification_user` (создан в M05 как reserved,
  активируется здесь)
- Init script в `infra/mongo-init/` или Spring auto-init

### NotificationHistoryConsumer (RabbitMQ)
- **Отдельная queue** `notification.history` (decoupled от
  delivery-queue `notification.delivery`)
- **Binding:** fanout exchange `notification.events` → оба queue
- **Consumer:** `@RabbitListener(queues = "notification.history")`
  persist через `NotificationHistoryRepository`
- **Error handling:** `try/catch` — failed save НЕ rethrow, лог warn
  с `trace_id`; delivery consumer не страдает
- Events to persist (denormalize payload):
  `excuse.requested`, `excuse.approved`, `excuse.rejected`,
  `late_checkin.requested`, `late_checkin.approved`, `late_checkin.rejected`,
  `lesson.started`, `lesson.closed`, `lesson.cancelled`,
  `attendance.marked` (selective — только red-zone hits)

### REST API (notification-api-contract — NEW-167)
- **`notification-api-contract`** модуль (новый, contract-first rule)
- Endpoints:
  - `GET /api/notifications?page=N&size=20&unreadOnly=false`
    → `PagedModel<NotificationHistoryDto>` (HATEOAS)
  - `GET /api/notifications/unread-count` → `{ count: int }`
  - `PATCH /api/notifications/{id}/read` → 204
  - `POST /api/notifications/mark-all-read` → 204
- Integrated в api-gateway routing (`/api/notifications/**` → 9094)

### Caffeine cache (P2-10/3 pattern)
- `@Cacheable(cacheNames = "unread-count", key = "#userId")` на
  `getUnreadCount(Long userId)`
- Config: Caffeine `maximumSize=10000`, `expireAfterWrite=30s`
- Invalidation: STOMP new-event publisher также вызывает
  `cacheManager.getCache("unread-count").evict(userId)` — моментальный
  инвалидация при новом событии

### Frontend NotificationCenter migration
- **Общий компонент** `NotificationCenter` (QC1 unified в M07 —
  thin-client на sessionStorage; M10 подменяет data source)
- **PWA** (`frontends/pwa/src/components/NotificationCenter.tsx`):
  - Заменить `sessionStorage.getItem('notifications')` на TanStack Query
    `useInfiniteQuery(['notifications'], ({pageParam}) => fetchPage(pageParam))`
  - Optimistic UI: `mutate` на read/mark-all-read с
    `queryClient.setQueryData`
  - Unread badge: `useQuery(['unread-count'], fetchUnreadCount,
    {staleTime: 30_000})`
- **web-panel** (`frontends/web-panel/src/app/shared/notification-center/`):
  - Angular HttpClient pagination + Signal для unread-count
  - STOMP `/topic/notifications` invalidate `unread-count` query
- sessionStorage используется только для optimistic local patch (не
  authoritative source)

### Tests
- Testcontainers Mongo + Rabbit IT для `NotificationHistoryConsumerIT`
- Contract-тест: NotificationHistoryConsumer подписан на все 14+ events
  (schema validation обязательна, QD3 — частично в M08 scope)
- REST endpoint tests: PagedModel shape, unread-count correctness
- Caffeine cache test: hit/miss + STOMP invalidation
- Frontend unit test: `NotificationCenter.test.tsx` (TanStack Query
  mock — в M08 Группа 6)

**Исключено (другие milestones):**
- Unified NotificationCenter component (QC1 structural) — **M07**
  (M10 только подменяет data source)
- openapi-typescript generation для 4 новых endpoint'ов —
  **M07** generator запускается после M10 merge
- Contract tests для всех 14+ events — **M08 Группа 9**
- NotificationHistoryConsumer Testcontainers reuse pattern — **M08**
  (hybrid refactor)
- CLAUDE.md table update (notification-web stateful, Mongo DB) —
  **в этом milestone** как финальный шаг

## Модули / изменения

### Backend — notification-service
- `services/notification-service/notification-api-contract/` (NEW модуль)
  - `NotificationApi.java` (interface с @RequestMapping)
  - `NotificationHistoryDto.java` (record)
  - `UnreadCountDto.java` (record)
  - `NotificationType.java` (enum)
- `services/notification-service/notification-app/` (переименование
  `notification-web` → выравнивание с contract-first pattern, или
  оставить `notification-web-app` — **уточнить в NOTES**)
  - `domain/NotificationHistory.java` (entity с `@Document`)
  - `repository/NotificationHistoryRepository.java` extends
    `MongoRepository`
  - `consumer/NotificationHistoryConsumer.java` (RabbitListener)
  - `controller/NotificationController.java` implements NotificationApi
  - `service/NotificationHistoryService.java` (Caffeine cache,
    STOMP invalidation)
  - `config/CaffeineConfig.java` (cache manager bean)
  - `config/MongoConfig.java` (if needed, auto-configure)

### Migration / init
- `infra/mongo-init/notification-db-init.js` — user creation + indexes
  (если уже не делается Spring auto-init)
- `docker-compose.yml` + `docker-compose.prod.yml` — envs для
  `MONGO_URI_NOTIFICATION`, mount init script

### api-gateway
- `services/api-gateway/src/main/resources/application.yml` —
  новый route `/api/notifications/**` → lb://notification-web

### Frontend
- `frontends/pwa/src/api/notifications.ts` (NEW — TanStack Query hooks)
- `frontends/pwa/src/components/NotificationCenter.tsx` (migration)
- `frontends/web-panel/src/app/shared/notification-center/*.ts`
  (Angular migration)
- `frontends/*/src/api/generated/` — regenerate после OpenAPI commit

### Docs
- `docs/architecture/architecture.md` — раздел «Notification History» (NEW)
- `docs/architecture/database-schema.md` — раздел Mongo `notification_db`
  (NEW-166)
- `docs/security/data-retention-policy.md` — обновление: notification_history
  30d TTL (NEW-148 расширение)

### CLAUDE.md
- Таблица Services: `Notification Web | 9094 | Spring Boot WebSocket
  (STOMP) + Caffeine | MongoDB (notification_db)`
- Убрать примечание «stateless event forwarder, становится stateful
  после M04 — см. NEW-168» → **заменить на** «stateful history store
  с MongoDB persistence, см. M10»

## Acceptance criteria

- [ ] `notification-api-contract` модуль создан (java-library, без Lombok)
- [ ] MongoDB `notification_db` + collection `notification_history`
      + 3 индекса (user_id+sent_at, user_id+read_at, sent_at TTL 30d)
- [ ] `notification_user` Mongo user active в prod docker-compose
- [ ] `NotificationHistoryConsumer` подписан на отдельную queue
      `notification.history`; binding с fanout exchange
- [ ] Failed save НЕ rethrow — delivery continues; warn log с trace_id
- [ ] 4 REST endpoints работают:
  - `GET /api/notifications` — HATEOAS PagedModel
  - `GET /api/notifications/unread-count`
  - `PATCH /api/notifications/{id}/read`
  - `POST /api/notifications/mark-all-read`
- [ ] Caffeine cache на unread-count, TTL 30s, STOMP invalidation работает
- [ ] PWA `NotificationCenter` использует TanStack Query
      `useInfiniteQuery` + optimistic mutations
- [ ] web-panel `NotificationCenter` использует Angular HttpClient
      pagination + Signal
- [ ] TTL проверен: документ старше 30 дней автоматически удаляется
      (integration test)
- [ ] Testcontainers Mongo + Rabbit IT проходит:
      `NotificationHistoryConsumerIT` с полным flow
- [ ] `docs/architecture/architecture.md` раздел «Notification History»
- [ ] `docs/architecture/database-schema.md` обновлён (NEW-166)
- [ ] `CLAUDE.md` обновлён (NEW-168): notification-web stateful +
      MongoDB
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.10`

## Dependencies

- **Блокируется:** M01 (shared-web, shared-events ✅), M02 (outbox
  pattern для publisher ✅), M05 (notification_user pre-reserved ✅)
- **Блокирует:** M07 final merge (NotificationCenter thin-client в
  M07 ожидает backend data source) — но **parallel safe**: M07 делает
  sessionStorage thin-client, M10 подменяет
- **Parallel safe:** M08 (Test Infra), M09 (Prod Release Blockers),
  M11 (OpenAPI Polish)

## Artifacts

- `services/notification-service/notification-api-contract/` — новый
  Gradle модуль
- `infra/mongo-init/notification-db-init.js` — Mongo schema/user
- `docs/architecture/architecture.md` — Notification History section
- `docs/architecture/database-schema.md` — Mongo schema (NEW-166)
- `docs/security/data-retention-policy.md` — 30d TTL раздел
- Frontend: TanStack Query hooks + Angular services для notifications
- CLAUDE.md: обновлён service table + URL layout если нужно

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (P2-6/4 строки 5021-5131). Здесь только WHAT и DONE._

---

## Post-mortem (M10 завершён 2026-04-24, tag `v0.0.0-alpha.11`)

### Что пошло по плану

- **Структура модуля (D1):** существующий `notification-api-contract`
  переиспользован — нулевые трения.
- **PoLP user (D2):** init-mongo создал отдельного `notification_user`
  с правами только на `notification_db`. Подтверждено в G9 smoke.
- **Caffeine cache + STOMP invalidation (D6/D7):** unread-count
  invalidate через consumer работает; PWA hybrid (sessionStorage
  + backend-history hook) минимизирует frontend churn.
- **Backend coverage:** 23 unit + 2 IT тестов прошли с первой попытки
  (после H1 hot-patch).
- **Documentation atomic:** G8 один commit, G9 два hot-patch коммита
  + один defer-only коммит — лента читаема.

### Что сломалось / какие сюрпризы

- **S2 (G1):** PLAN говорил «notification_user уже зарезервирован
  в M05» — по факту в M05 кода не было, только намерение. Создал
  D2 явно. Time cost: 30 мин на discovery + декларация в init-mongo.js.
- **S3 (G1):** `SPRING_DATA_MONGODB_URI` в docker-compose'ах указывал
  на `attendance_db` (legacy, т.к. notification-web был stateless).
  Переключён на `notification_db`. Прозрачно.
- **S4 (G9.2):** **HIGH severity surprise.**
  `NotificationHistoryMongoConfig.@PostConstruct` + `@Lazy MongoTemplate`
  silently no-op'нул на пустом namespace в Mongo 7. TTL и compound-
  индексы НЕ создавались — retention 30d не работал, unread-count
  делал бы COLLSCAN при росте. Hot-patch `3d3eec6`: explicit
  `createCollection` + переход на `ApplicationReadyEvent`. Verified
  через IT logs.
- **S5 (G9.2):** running `rutcampustrack-notification-web` image —
  2 weeks old, без M10 кода. `docker-compose.yml` build context
  `./services/notification-service/notification-app` неправильный —
  Dockerfile референсит файлы относительно monorepo root. Это
  **pre-existing M10-orthogonal проблема**, не блокирует M10
  (валидация через Testcontainers IT). Defer'ить в `future-ideas.md`
  как «docker-compose build context fix» отдельным минор-task'ом.
- **G9 audit findings:**
  - **H1 (HIGH):** `excuse.decided` / `late_checkin.decided` всегда
    маппились на APPROVED, даже при `payload.status="rejected"`.
    Юридически значимый bug — staroste/студент видели «уважительная
    одобрена» при реальном rejection. Hot-patch `4929d5b`: payload-
    aware маппер + regression test.
  - **H2 (HIGH):** Pageable без max-size cap → DoS через `?size=1M`.
    Hot-patch `4929d5b`: `spring.data.web.pageable.max-page-size=100`.
  - 11 MEDIUM/LOW findings (N1-N11) defer'нуты в future-ideas.md.

### Lessons learned

1. **Mongo index bootstrap всегда требует explicit `createCollection`.**
   `IndexOperations.ensureIndex` не материализует индексы на
   несуществующем namespace в Mongo 7. Pattern для всех будущих
   `MongoConfig.@PostConstruct` — `if (!collectionExists) createCollection`
   ПЕРЕД ensureIndex. Применить retroactive audit к `PushMongoConfig`
   и любым новым Mongo-коллекциям (open ticket в N9-bundle).

2. **Decision events с binary payload — обязательны payload-aware
   маппинги.** При следующем добавлении `*.decided`-style event
   (homework.graded, lesson.rescheduled?) — switch case ВСЕГДА должен
   читать payload.status (или эквивалент). Юнит-тест rejected-path
   обязателен с самого начала.

3. **Pageable cap — глобальный prep-step для любого нового REST с
   pagination.** Добавить в M11 OpenAPI Polish чек: каждый эндпоинт
   возвращающий PagedModel ≤100 default cap.

4. **Hot-patch verification через Testcontainers IT > smoke на running
   compose.** IT тестирует свежий код, smoke — собранный image.
   Когда image stale (S5), smoke даёт false confidence. M11+
   integration tests пилить от Testcontainers, не от docker-compose.

5. **Bug-hunter обнаружил **функциональный** bug (H1), которого не
   было в security-auditor scope. Запускать оба в G9 — must, не «or».
   Различные модели угроз дают непересекающееся покрытие.

### Метрики milestone

- **Время:** ~12 часов фактического workflow (G1 → G9), spread по
  сессиям 2026-04-24 (G1-G7 утро, G8-G9 вечер).
- **Коммитов:** 9 (G1-G7 + docs + 2 hot-patch).
- **Тесты добавлены:** 23 unit + 2 IT (backend) + 5 (PWA) + 5 (web-panel).
- **Hot-patches required в G9:** 2 (S4 index bootstrap, H1+H2 bundle).
- **Defer в v0.1:** 11 пунктов (N1-N11) — bundled scope ~5-7 человеко-дней.

### Hand-off для M11 OpenAPI Polish

- 4 новых endpoint'а в `NotificationApi` уже имеют `@Operation` +
  `@ApiResponses` — M11 нужно только убедиться что они попадают в
  `SharedOpenApiCustomizer` enrichment.
- `@Schema(description, example)` для `NotificationHistoryDto` /
  `UnreadCountDto` — сделать в M11 (не блокер).
- nginx basic-auth на prod `/swagger-ui` — M11 G2/G3 scope.
