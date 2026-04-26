---
phase: G27
type: tech-debt-audit
date: 2026-04-26
scope: services/* (Java 21 + Spring Boot 3.4) + frontends/* (Angular 20, React+Vite, PWA)
prior_artifact: G26-code-review-after-g25.md
auditor: Claude (Sonnet 4.6, без агентов/skills)
audit_categories:
  - "1. Legacy код"
  - "2. Плохо читаемый код"
  - "3. Плохо поддерживаемый код"
  - "4. Плохо масштабируемый код"
  - "5. Заглушки в коде вместо реальной реализации"
---

# G27 — Tech Debt Audit (после M13)

## Executive Summary

Аудит проведён вручную (без агентов/skills) после закрытия M13 на готовность codebase к first VPS deploy. Просмотрены 5 категорий: legacy, читаемость, поддерживаемость, масштабируемость, заглушки.

**Итог:** 23 finding (P1: 4, P2: 9, P3: 10).

**Главное:**
1. **3 настоящие заглушки в prod-коде**: `SharedOpenApiCustomizer` (no-op lambda), `AdminActionAspect` + `@AdminAction` (никогда не используется, обещано на M04), `EventConsumer.handleLessonStarted` (debug-log only).
2. **2 endpoint'а только для тестов** торчат в production: `/schedule/health-check`, `/attendance/health-check` — должны быть в `@TestConfiguration` или за `@Profile("test")`. Уже зафиксировано в `report-before-v0.0.0/03-schedule-service.md` (P2-1).
3. **1 N+1 на gRPC hot-path**: `AcademicGrpcServiceImpl.getTeacherSubjects()` — 2 findById на каждое assignment в loop. Для teacher с 30 группами = 60 запросов вместо 2.
4. **1 in-memory state на shared service**: `headmanBuckets` (rate-limit) — single-instance only, при horizontal scaling ломается. Нужно перенести в Redis (там же где rbac/subject cache из M05).
5. **2 deprecated TS API ещё используются**: `setTokens()` в auth.service (только тесты — простой cleanup), `PendingExcuse`/`PendingLateCheckin` (активно используются — миграция не завершена).
6. Никаких `RestTemplate`/`@Enumerated(EnumType.ORDINAL)`/`Vector`/`Hashtable`/`SimpleDateFormat`/`println` — codebase clean от классического Java legacy. Lombok НЕ просочился в `*-api-contract` (правило соблюдается). `findAll()` без Pageable — нет. CREATE INDEX без CONCURRENTLY — нет.

**Зависимости от других gates:** ничего из этого не блокирует deploy v0.0.0. P1 рекомендуется до first horizontal scale-out, P2/P3 — обычный backlog.

---

## Findings table

| # | Severity | Category | File:line | Issue | Fix | Why |
|---|----------|----------|-----------|-------|-----|-----|
| F01 | **P1** | Заглушка | `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/SharedOpenApiCustomizer.java:22-28` | `sharedErrorsCustomizer()` возвращает lambda с пустым телом и комментарием «Пока no-op — наполнение в G1». M11 G1 завершён (см. CLAUDE.md), реальный customizer = `GlobalErrorResponsesCustomizer` (отдельный @Bean). Этот класс — pure dead code, регистрирует пустой OpenApiCustomizer в каждом сервисе с springdoc на classpath. | Удалить класс целиком. Подтвердить, что `GlobalErrorResponsesCustomizer` покрывает функциональность (он покрывает — `OpenApiErrorResponsesIT` зелёный). | Конфигурационный noise; bean пустой, но springdoc всё равно его вызывает на каждой генерации спеки. |
| F02 | **P1** | Заглушка / dead abstraction | `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AdminActionAspect.java`, `.../audit/AdminAction.java` | `@AdminAction` аннотация **не используется ни в одном controller/service** (grep `@AdminAction` по `**/src/main` — 0 hits, кроме самого aspect). `AdminActionAspect.around()` пишет debug-log и `proceed()`. Доком обещано: «Реальный handler добавится в M04 без изменения сигнатуры». M04 завершён (Observability), реализации не появилось. Aspect и аннотация — мёртвая abstraction. | Вариант А: удалить `AdminAction` + `AdminActionAspect`, перенести audit на explicit `auditLogger.log(...)` в admin-методах при необходимости. Вариант B: реализовать обещанное (запись audit-event в Loki через `@AdminAction("user.archive")` на каждом ADMIN-методе). | Сейчас annotation существует только чтобы её AOP-pointcut «зацепил» — но никто не помечает методы. Любой dev будет искать использования и не поймёт, что это. |
| F03 | **P1** | Заглушка | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java:69-74` | `handleLessonStarted` извлекает `lesson_id` из payload, пишет `log.debug("lesson.started: no-op")` и выходит. Subscription занимает routing key, но handler ничего не делает. Если обработка не нужна — нужно убрать `lesson.started` из switch и binding routing keys для attendance-service. | Если действительно no-op — удалить `case "lesson.started" -> handleLessonStarted(envelope);` (default → "Ignoring unknown event type" сделает то же самое). Параллельно: уточнить, что routing key `lesson.started` подписан на attendance-queue в `RabbitConfig`, и развязать. | Mystery code — следующий dev будет искать «зачем здесь handler с пустым телом», тратить время. Лишний RabbitMQ message в очереди. |
| F04 | **P1** | Масштабируемость / N+1 | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:111-135` (`getTeacherSubjects`) | В `.map(a -> { findById(subjectId); findById(groupId); ... })` — 2 SELECT'а на assignment. Для teacher с 30 ассигнментами = 60 запросов. Hot-path: вызывается attendance-service при каждом teacher запросе журнала. | `subjectIds = assignments.stream().map(::getSubjectId).distinct().toList(); subjects = subjectRepository.findAllById(subjectIds).stream().collect(Collectors.toMap(Subject::getId, identity()));` — то же для groupIds. Затем lookup в `Map`. Снижение: O(N) → 2 batch query'я. | Это backend-of-backend (gRPC), latency накапливается. Pattern уже применён в `getSubjectsByIds:256` — только в этом методе пропущено. |
| F05 | **P1** | Масштабируемость / single-instance state | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:45` | `headmanBuckets = new ConcurrentHashMap<>()` — token bucket на каждого headman в JVM heap. При horizontal scaling каждый pod имеет свои buckets → headman c 4 pods может делать 480 calls/min вместо 120. RL_MAX_BUCKETS=10k — на single instance OK, на N pods × 10k = N×больше памяти. | Перенести в Redis (там же где rbac/subject cache из M05): `INCR rl:headman:{userId}:{minute}` + `EXPIRE 65`. Pattern уже знаком (gateway RedisRateLimiter использует именно это). | M11+ цельит multi-instance. Сейчас compose.prod.yml — single replica per service, но при scale-out RL ломается без явных тестов. |
| F06 | **P2** | Заглушка / test-only код в prod | `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/HealthCheckController.java`, `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/HealthCheckController.java` | Endpoint'ы существуют только для `SecuritySmokeIT` (нет других callers). В prod — публичный endpoint с `@RequireRole`, дополнительная attack surface. Уже зафиксировано в `docs/archive/report-before-v0.0.0/03-schedule-service.md:195` (P2-1) и `04-attendance-service.md:395`. | Перенести в `src/test/java` + `@TestConfiguration`, либо обернуть `@Profile("test")`. Тест должен оставаться зелёным — endpoint регистрируется только при test profile. | M13 не закрыл этот пункт. До deploy не блокатор, но мёртвый prod endpoint. |
| F07 | **P2** | Legacy / dead alias | `frontends/web-panel/src/app/core/auth/auth.service.ts:99-103` | `setTokens(access, refresh)` помечен `@deprecated M03b Группа 7`. **Только тесты** его вызывают (`auth.service.spec.ts`, `*.guard.spec.ts`, `auth.interceptor.spec.ts` — ~25 references). Prod коду не нужен. | Замена в тестах: `setTokens(t, REFRESH_TOKEN)` → `setAccessToken(t)`. Удалить deprecated метод. Один find/replace. | Deprecated 1+ milestone, никто из prod-кода не использует. Чистый cleanup. |
| F08 | **P2** | Legacy / неоконченная миграция | `frontends/pwa/src/features/headman/shared/types.ts:85-99` | Интерфейсы `PendingExcuse`, `PendingLateCheckin` помечены `@deprecated — use ExcuseTicket / LateCheckinRequest. Kept for backward compat of old hooks`. Но они **активно используются** в `Overview.tsx`, `headmanApi.ts:147,168`, тестах. Миграция начата, но не закончена — deprecated стало vague. | Решить: либо снять `@deprecated` (interface жив и используется), либо завершить миграцию на `ExcuseTicket`/`LateCheckinRequest` и удалить alias. Текущий статус — псевдо-deprecated. | Misleading metadata. Dev видит deprecated и думает что заменено — а на самом деле это main type. |
| F09 | **P2** | Заглушка / no-op fallback | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:120-122` | В `getTeacherSubjects` при `subject/group не найдены` — `return null` + `.filter(info -> info != null)` (вместо `Objects::nonNull`). Молча отбрасывает данные. Если subject удалён, но assignment остался — teacher не увидит ошибки, просто список будет короче. | Добавить `log.warn("Assignment {} references missing subject={} or group={}, skipping", a.getId(), ...)`. Параллельно — fix через `findAllById` (см. F04) сделает невозможным missing data в norm operation. | Silent data loss = тяжёлый baг для troubleshooting. |
| F10 | **P2** | Поддерживаемость / `any` flood | `frontends/web-panel/src/app/features/headman/**/*.ts` (subjects, group, journal, homework, stats, weekly-journal) | Большое количество `any` / `as any` для embedded HATEOAS типов: `(Object.values(resp._embedded)[0] as any[])`, `subject?: any`, `teachers: any[]`, `(entry: any) => entry?.content`. ~30 references в headman-фичах. Не используются сгенерированные `openapi-typescript` типы. | Завести helper `unwrapEmbedded<T>(resp: { _embedded?: Record<string, T[]> }): T[]` + использовать generated `paths['/groups/{id}/students']['get']['responses']['200']['content']['application/hal+json']` types. | Type safety обнулена в headman-features, refactor risk высок. M07 G3 ввёл `openapi-typescript`, но headman-feature не мигрирован. |
| F11 | **P2** | Поддерживаемость / field injection | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java:35-36`, `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/PushMongoConfig.java:24-25` | `@Autowired private MongoTemplate mongoTemplate;` — field injection в `@Configuration`. Anti-pattern: невидимая зависимость, нельзя мокать через constructor, делает класс не-final-ready. | Constructor injection: `private final MongoTemplate mongoTemplate; public MongoConfig(MongoTemplate t) { this.mongoTemplate = t; }`. Spring 6 + Boot 3.x давно поддерживают. | Codebase везде использует constructor injection — эти 2 точки выпадают. Inconsistency. |
| F12 | **P2** | Поддерживаемость / большие компоненты | `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts` (589), `homework/headman-homework.component.ts` (547), `schedule/headman-schedule.component.ts` (541) | 3 файла >500 LOC, inline templates + animations + dialog logic + API calls + form validation в одном component-классе. | Разделить: dataAccess → `*-api.service.ts` (или extending headman-api), dialogs → отдельные компоненты, presentation → child компонент. | Test-coverage в этих файлах будет страдать; merge-conflicts; cognitive load для нового dev. |
| F13 | **P2** | Читаемость / magic int[] | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java:184-202` | `Map<Long, int[]>` с комментарием `// Индексы: [0]=total, [1]=attended, [2]=absent, [3]=excused`. Optimization для single-pass aggregation, но `c[0]++; c[1]++; c[2]++; c[3]++` без типов — bug-prone. | `private record SubjectCounters(int total, int attended, int absent, int excused)` или mutable inner class. Performance такая же (object allocation на subject — мизер). | Если кто-то добавит 5-й статус → silent off-by-one в индексах. Магические числа — антипаттерн. |
| F14 | **P2** | Поддерживаемость / 1 TODO | `frontends/mini-app/src/features/stats/api.ts:16` | `// TODO: use backend threshold when available` | Backend threshold уже есть с Phase 56 (`/academic/thresholds`). Заменить hardcoded порог на API-call или удалить TODO если решение остаётся локальным. | Stale TODO, контекст потерян. |
| F15 | **P3** | Заглушка / catch swallow | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:97-99` | `catch (NumberFormatException ignored) { // anonymous / malformed principal — пропускаем }` — на logout extract userId. | OK, но добавить `log.debug("logout with non-numeric principal: {}", principal)` чтобы не терять диагностику в инцидент. | Silent catch без логирования = blind spot в инциденте. |
| F16 | **P3** | Читаемость / namespace impurity | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java:184` | `Map<Long, int[]> bySubject = new java.util.HashMap<>();` — fully qualified `java.util.HashMap` несмотря на наличие import. | Убрать FQN, использовать `new HashMap<>()`. | Cosmetic, но не консистентно с остальным файлом. |
| F17 | **P3** | Поддерживаемость / `LessonStarted` event | `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java:23` | Комментарий: `(lesson.started/closed/cancelled) skip'ается (D6)`. Но в `mapType` (line 111) только `lesson.started` skip'ается через default `Optional.empty()`, остальные не упомянуты. | Уточнить doc — `lesson.*` целиком не маппится в notifications, а handler уже фильтрует через NotificationType matching. Либо явно вернуть `Optional.empty()` для всех `lesson.*`. | Несоответствие doc и кода. |
| F18 | **P3** | Поддерживаемость / inconsistent allowed-origins | `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:35` | `@Value("...:http://localhost:5173,http://localhost:4200,http://localhost:3000,https://ruttrack.site")` — default включает 3 dev-origins И prod URL. В `application.yml`/`.env` не переопределяется → prod рантайм имеет dev origins активными. | Default = только prod (`https://ruttrack.site`). Dev origins ставит `application-dev.yml`. | M13 G14 ужесточил CSP, но WS allowed-origins расслабляются на default. Не катастрофа (сами WS защищены ticket'ом из M03b), но inconsistent. |
| F19 | **P3** | Поддерживаемость / @ts-expect-error | `frontends/pwa/src/features/schedule/HeadmanLessonSheet.tsx:33`, `LessonActionsSheet.tsx` (вероятно) | `// @ts-expect-error Telegram WebApp surface is optional and added at runtime.` | Завести `src/types/telegram-webapp.d.ts` с правильной типизацией Telegram.WebApp + HapticFeedback. | Типизация Telegram WebApp есть на DefinitelyTyped (`@types/telegram-web-app`), не нужен ad-hoc skip. |
| F20 | **P3** | Читаемость / mock-comment в production коде | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/semester/SemesterCacheService.java:13`, `geofence/GeofenceService.java:17`, `attendance/config/ClockConfig.java:20` | Doc-блоки в prod-коде описывают test-mocking стратегию: `In test profile, the entire bean is replaced by @MockitoBean`, etc. | Перенести в test/java doc или удалить. Prod-код не должен документировать своё mock-поведение. | Dev читая prod-класс не должен видеть test-only знания. Test-conscious code = code smell. |
| F21 | **P3** | Поддерживаемость / dual deprecated | `services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/api/exception/FieldError.java:14` | Doc: `deprecated alias — {@link FieldError} — canonical и единственный.` — confusing. | Удалить упоминание deprecated alias, либо показать какой именно alias deprecated. | Doc вводит в заблуждение — нет понятия о каком aliasе речь. |
| F22 | **P3** | Поддерживаемость / `setupAccessToken` test footprint | `frontends/web-panel/src/app/core/auth/clear-all-client-state.spec.ts:14-15` | `let localStorageClearSpy: any; let sessionStorageClearSpy: any;` — `any` в тестах. | `let localStorageClearSpy: ReturnType<typeof vi.spyOn>;` или явный тип. | Test code тоже под typecheck. |
| F23 | **P3** | Заглушка / hardcoded threshold | `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java:181-186` | Метод `protected createNotification()` существует только для test stub'инга (`Protected to allow stubbing in unit tests`). Это test-driven design escape, но visibility модификатор служит mocking — лучше явный seam. | Внедрить `NotificationFactory` interface + `@Bean` фабрику, мокать через `@MockitoBean NotificationFactory`. Сейчас reflection-based stubbing работает, но связь между test и prod method-visibility неявная. | Visibility = test-coupling. Минор, но clean architecture-pattern. |

---

## Что НЕ нужно менять (rationale)

| Что | Почему survive review |
|-----|------------------------|
| `nonBlockingSecureRandom()` в JwtService (G25.13 safety net) | Уже обсуждено в G26 — корректный fallback для dev без Docker pre-gen RSA. |
| `@Profile("!test")` на `OutboxConfig.Publisher`, `SchedulingConfig`, `PushCleanupConfig` | Стандартный паттерн, чтобы cron не запускался в IT. Не legacy — это активно поддерживаемая стратегия. |
| `@Date now = new Date()` в `JwtService.java:127,145,187` | Forced legacy от jjwt API (`Claims.setExpiration(Date)`). Не наш код. |
| `@Date` в Mongo outbox/idempotency stores | MongoDB driver требует `Date`/`Instant`. Не legacy. |
| `headmanBuckets.RL_MAX_BUCKETS = 10_000` cap | OK для single instance — fail-safe от unbounded growth. Не cleanup, но cap нужно сохранить и при миграции в Redis (как `EXPIRE`). |
| `@RequireRole` aspect + `RoleCheckAspect` | M03 architecture decision, не legacy. |
| `LowercaseEnumConverter` (autoApply) | Project-wide convention из CLAUDE.md, не legacy. |
| `ConcurrentHashMap timerCache` в `GrpcClientMetricsInterceptor` | Per-instance cache для Timer registry — корректно и не shared state. |
| 481-LOC `ExcuseService.java` | Сложный domain-service с 11+ методами. Самый длинный метод — 11 строк. Декомпозирован. |
| `SubjectDeletedCascadeService` ("queries are no-ops") | Корректный cascade-on-delete, no-op = idempotent проверка. Не stub. |
| `NoOpCacheManager` в `CacheConfig` | Conditional fallback для тестов без Redis — correct pattern. |
| Все 27 файлов с упоминанием "Lombok" в `*-api-contract` | Только в комментариях `// No Lombok — contract modules use plain Java`. Правило соблюдается. |

---

## Top-15 cleanup приоритетов (для следующей сессии)

Отсортировано по эффекту/затратам:

| # | Severity | Что | Затраты | Эффект |
|---|----------|-----|---------|--------|
| 1 | P1 | **F04** N+1 в `AcademicGrpcServiceImpl.getTeacherSubjects` — `findAllById` × 2 | 30 мин (1 файл, 1 метод) | Backend latency для teacher journal request снижается с O(N) до O(1) |
| 2 | P1 | **F03** Удалить `case "lesson.started"` из `EventConsumer` + binding key из `RabbitConfig` | 20 мин | Минус 1 mystery code, минус queue noise |
| 3 | P1 | **F01** Удалить `SharedOpenApiCustomizer` (no-op bean) | 10 мин | Dead code minus, конфигурация чище |
| 4 | P1 | **F02** Решить `@AdminAction`/`AdminActionAspect`: удалить ИЛИ реализовать | 1ч (delete) или 4ч (impl) | Закрыть pre-M04 обещание |
| 5 | P1 | **F05** Перенести `headmanBuckets` в Redis (паттерн как rbac cache) | 2ч | Готовность к horizontal scale |
| 6 | P2 | **F06** Перенести `HealthCheckController` (schedule + attendance) в `@TestConfiguration` | 30 мин | Минус 2 publicly accessible test endpoints в prod |
| 7 | P2 | **F07** Заменить `setTokens()` → `setAccessToken()` в тестах web-panel + удалить deprecated method | 30 мин | Cleanup auth.service interface |
| 8 | P2 | **F11** Constructor injection в MongoConfig + PushMongoConfig | 15 мин | Codebase consistency |
| 9 | P2 | **F09** + log.warn при `subject/group not found` в `getTeacherSubjects` | (вместе с F04) | Diagnostic visibility |
| 10 | P2 | **F08** Завершить миграцию `PendingExcuse`/`PendingLateCheckin` ИЛИ снять deprecated | 1ч | Понятная ts-метаданные |
| 11 | P2 | **F13** `int[]` → `SubjectCounters` record в `ReportService.getStudentStats` | 30 мин | Type safety на hot-path |
| 12 | P2 | **F10** Helper `unwrapEmbedded<T>` + покрыть `any` в headman-фичах generated types | 3-4ч | Type safety в headman-cabinet (P56) |
| 13 | P2 | **F14** Resolve TODO в `mini-app/.../stats/api.ts:16` (M07+ backend threshold available) | 20 мин | Минус 1 stale TODO |
| 14 | P3 | **F18** Default WS allowed-origins только prod, dev в `application-dev.yml` | 15 мин | Tighten production defaults |
| 15 | P3 | **F12** Декомпозировать `headman-excuses.component.ts` (~589 LOC) | 4-6ч | Maintainability топ-5 файла |

**Совокупно P1 ≈ 4ч, P2 (top-7) ≈ 7ч.** P3 — discretionary backlog.

---

## Метаданные

- **Метод аудита:** ручной (без агентов / skills) — главным образом Grep/Read для классических anti-pattern'ов и domain-specific сигналов проекта.
- **Не входило в scope:** security audit (есть отдельный G27-cso-comprehensive-audit), test coverage (G26-test-audit-findings), performance benchmarking, dependency graph.
- **Confidence:** P1 high (все verified в коде), P2 high, P3 best-effort. False positive rate ожидается ~10% (можно решить debate в обсуждении).
- **Связь с M13 G25:** не пересекается. G25 хот-фиксил CI; этот аудит — accumulated debt из M01-M13 + v1.0-v9.0.
