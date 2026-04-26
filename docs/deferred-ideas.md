# Deferred Ideas — отложенные идеи для будущих фаз

Сюда попадают идеи, возникшие при планировании фаз, но не включённые в текущий скоуп.
Каждая запись = короткое описание + откуда пришла идея.

---

## Homework Management (Phase 61)

### Помощник старосты публикует ДЗ

Сейчас создавать/редактировать/удалять ДЗ может только сам староста (staff-check
`publishedBy == currentUserId` + роль HEADMAN). Помощник старосты (`headman_assistants`,
делегированные права) — **не имеет доступа к CRUD ДЗ**.

В будущем: расширить role/permission-check на помощника по аналогии с `schedule_manage` /
управлением расписанием. Потребует нового permission-флага (например `homework_manage`)
в таблице `headman_assistants` и соответствующих проверок в `HomeworkService`.

Источник: Phase 61, discuss-phase 2026-04-15.

### Админ редактирует чужие ДЗ

В Phase 61 ADMIN **не участвует** в ДЗ (только староста публикует, остальные читают).
Если в будущем понадобится модерация (админ удаляет неуместные задания) — расширить
guard в `updateHomework`/`deleteHomework`, чтобы пропускать ADMIN независимо от
`publishedBy`.

Источник: Phase 61, discuss-phase 2026-04-15.

---

## v0.1+ tech debt (G27 tech-debt audit, 2026-04-26)

Источник: `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` —
23 finding (4 P1, 9 P2, 10 P3). Автор аудита явно зафиксировал
«ничего не блокирует deploy v0.0.0», поэтому ВСЕ пункты отложены.
Большинство P1 — gate'нуты на real-user signal (Grafana latency / решение
о horizontal scale), остальное — обычный backlog.

**Триаж принцип:** не платить tech debt пока не появился сигнал что
он мешает (latency спайк в Grafana, конфликт при scale-out, friction
при review).

### P1 — gate'нуты на real-user signal

#### F04: N+1 в `AcademicGrpcServiceImpl.getTeacherSubjects` (~30 мин fix)

`.map(a -> { findById(subjectId); findById(groupId); })` — 2 SELECT'а на
assignment. Для teacher с 30 ассигнментами = 60 запросов вместо 2.

**Fix:** `subjectIds = assignments.stream().map(::getSubjectId).distinct().toList();
subjects = subjectRepository.findAllById(subjectIds).stream().collect(toMap(...))` +
аналогично для groupIds. Pattern уже применён в `getSubjectsByIds:256`.

**Когда делать:** когда Grafana покажет p95 latency >100ms на teacher
journal request, либо при первой жалобе на «медленный журнал». До этого
60ms vs 2ms на запрос невидимо для users с малым числом групп.

---

#### F05: `headmanBuckets` rate-limit в JVM heap (~2ч fix)

`ConcurrentHashMap` token bucket per-headman в memory одного JVM. При
horizontal scaling каждый pod имеет свои buckets — headman c 4 pods может
делать 480 calls/min вместо 120.

**Fix:** перенести в Redis (паттерн как rbac/subject cache из M05) —
`INCR rl:headman:{userId}:{minute}` + `EXPIRE 65`. Тот же подход что
gateway RedisRateLimiter использует.

**Когда делать:** в день когда принимаем решение о horizontal scale-out
(минимум 2 replicas backend). До этого single-instance compose.prod.yml —
проблемы нет.

---

#### F02: `@AdminAction` aspect — мёртвая abstraction (~1ч delete или ~4ч impl)

См. также `future-ideas.md` MED-08 — там полный реальный план.

**Минимальный fix (path delete):** удалить `AdminAction` annotation +
`AdminActionAspect`, если решено не реализовывать audit log в pre-v0.1.

**Когда делать:** одновременно с решением по MED-08.

---

#### F03: `lesson.started` no-op consumer в attendance (~20 мин)

`EventConsumer.handleLessonStarted` извлекает `lesson_id`, пишет
`log.debug("lesson.started: no-op")` и выходит. Subscription занимает
routing key но handler ничего не делает.

**Fix:** удалить `case "lesson.started" → handleLessonStarted` (default
ветка сделает то же), отвязать routing key от attendance-queue в
`RabbitConfig`.

**Когда делать:** при следующем PR трогающем `attendance.event` пакет.
Pure cleanup.

---

#### F01: `SharedOpenApiCustomizer` no-op bean (~10 мин)

`sharedErrorsCustomizer()` возвращает lambda с пустым телом и комментарием
«Пока no-op — наполнение в G1». M11 G1 завершён, реальный customizer
живёт в `GlobalErrorResponsesCustomizer` (отдельный @Bean). Этот класс — pure dead code.

**Fix:** удалить класс целиком. Подтвердить что `OpenApiErrorResponsesIT`
зелёный.

**Когда делать:** в любом PR трогающем shared-web. Pure cleanup.

---

### P2 (9 находок) — поддерживаемость

- **F06:** `HealthCheckController` в schedule + attendance — endpoint только для `SecuritySmokeIT`, торчит в prod. Перенести в `@TestConfiguration` либо `@Profile("test")`.
- **F07:** `setTokens()` deprecated в `auth.service.ts` web-panel — используется только тестами (~25 references). Заменить → `setAccessToken()`, удалить deprecated method.
- **F08:** `PendingExcuse` / `PendingLateCheckin` в pwa — псевдо-deprecated (активно используются). Решить: снять `@deprecated` или закончить миграцию на `ExcuseTicket`/`LateCheckinRequest`.
- **F09:** `getTeacherSubjects` silent data loss — `return null` + `.filter(info -> info != null)` отбрасывает данные при missing subject/group. Добавить `log.warn`.
- **F10:** Headman web-panel features — `~30 any` / `as any` ссылок на embedded HATEOAS типы. Helper `unwrapEmbedded<T>` + использовать generated openapi-ts types.
- **F11:** `MongoConfig` + `PushMongoConfig` — `@Autowired` field injection в `@Configuration`. Перевести на constructor injection (codebase consistency).
- **F12:** `headman-excuses.component.ts` (589 LOC) + `headman-homework.component.ts` (547) + `headman-schedule.component.ts` (541) — разделить на data-access service + dialogs + presentation.
- **F13:** `Map<Long, int[]>` в `ReportService.getStudentStats` с magic indices `[0]=total [1]=attended [2]=absent [3]=excused`. Заменить на record `SubjectCounters`.
- **F14:** TODO в `mini-app/.../stats/api.ts:16` — backend threshold уже есть с Phase 56, заменить hardcoded на API call либо удалить TODO.

**Когда делать:** один tech-debt PR в начале v0.1 (~7ч overall),
если хватит мотивации. Иначе — pick'ать pункты при касании
соответствующих файлов в обычной работе.

---

### P3 (10 находок) — discretionary backlog

F15-F23: catch swallow без log, FQN `java.util.HashMap` в imported file,
inconsistent comment vs code в `NotificationHistoryConsumer`, dev origins
hardcoded в `WebSocketConfig` default, `@ts-expect-error` для Telegram WebApp,
test-mocking strategy в prod-class doc-блоках, dual-deprecated FieldError doc,
`any` в test spy types, hardcoded threshold в `WebPushDeliveryService.createNotification`.

Полный список: `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` § Findings table F15-F23.

**Когда делать:** не делать целенаправленно. Fix'ать при касании файла
в обычной работе.

---

### Что НЕ нужно менять (зафиксировано из аудита)

- `nonBlockingSecureRandom()` в `JwtService` — корректный fallback для dev без Docker pre-gen RSA.
- `@Profile("!test")` на `OutboxConfig.Publisher`, `SchedulingConfig`, `PushCleanupConfig` — стандартный паттерн, не legacy.
- `Date now = new Date()` в `JwtService:127,145,187` — forced legacy от jjwt API.
- `Date` в Mongo outbox/idempotency — required by MongoDB driver.
- `headmanBuckets.RL_MAX_BUCKETS = 10_000` cap — fail-safe от unbounded growth.
- `@RequireRole` aspect + `RoleCheckAspect` — M03 architecture decision.
- `LowercaseEnumConverter` — project convention CLAUDE.md.
- `ConcurrentHashMap timerCache` в `GrpcClientMetricsInterceptor` — per-instance cache, не shared state.
- `ExcuseService.java` 481 LOC — сложный domain-service декомпозирован, самый длинный метод 11 строк.
- `SubjectDeletedCascadeService` no-op queries — корректный idempotent cascade-on-delete.
- `NoOpCacheManager` в `CacheConfig` — conditional fallback для тестов без Redis.

Источник: G27-tech-debt-audit.md § «Что НЕ нужно менять».
