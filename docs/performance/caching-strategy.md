# Caching Strategy

**Версия:** v0.0.0 (M05 Группа 3, NEW-144)
**Последнее обновление:** 2026-04-20

Документ фиксирует стратегию кеширования в RutCampusTrack на момент
pre-release hardening'а v0.0.0, motivation'ы TTL и invalidation-логики,
trade-offs консистентности, и путь миграции при scale-out.

---

## TL;DR

| Namespace | TTL | Backend | Где | Invalidation |
|-----------|-----|---------|-----|--------------|
| `groups` | 5 мин | Redis | academic-service | @CacheEvict на updateGroup/deleteGroup + programmatic на смену is_headman |
| `group_members` | 5 мин | Redis | academic-service | @CacheEvict на updateGroup/deleteGroup/transferStudent + programmatic на смену is_headman |
| `users` | 5 мин | Redis | academic-service | @CacheEvict на updateUser/patchUser/archiveUser/transferStudent |
| `active_semester` | 10 мин | Redis | academic-service | @CacheEvict(allEntries=true) на activateSemester |
| `campus_geofence` | 60 мин | Redis | academic-service | не инвалидируется (статическое значение, ручная перезагрузка) |
| **`rbac`** | **1 мин** | **Redis** | **academic-service (M05 D6)** | **Programmatic evict на is_headman / group_id смену** |
| **`subject`** | **10 мин** | **Redis** | **academic-service (M05 D6)** | **@CacheEvict на updateSubject / deleteSubject** |

## Backend-выбор: Redis, не Caffeine

**OWNER-ANSWERS.md 3756-3810** предписывал Caffeine как решение v0.0.0.
В academic-service уже был внедрён Redis-кеш (ранние фазы 59/60);
M05 **оставил Redis** и добил пробелы. Обоснование — DECISIONS.md D6.

### Почему Redis лучше Caffeine для этого проекта (single-instance v0.0.0)

- **Redis уже в prod-зависимостях** (auth-service OTP / refresh-tokens,
  notification-bot reminders). Второй кеш-бэкенд (Caffeine in-proc)
  = 2 места для debug'а.
- **Cross-instance консистентность бесплатно.** OWNER-ANSWERS обещал
  миграцию на Redis при multi-instance v0.1 — уже сделано.
- **Cold-restart не обнуляет кеш.** Redis переживает restart JVM,
  hot-keys остаются тёплыми.
- **Сериализация через `Hibernate6Module` + `JavaTimeModule` отлажена**
  — см. `CacheConfig.java:66-84`. Caffeine потребовал бы новую ревизию
  detached-entity handling'а.

### Trade-offs Redis vs Caffeine

| Критерий | Redis (current) | Caffeine (отвергнут) |
|----------|-----------------|----------------------|
| Lookup latency | ~0.5ms (loopback docker network) | ~1µs (in-proc) |
| Cross-instance consistency | ✅ built-in | ❌ diverges per instance |
| Cold start | ✅ persists | ❌ cold on restart |
| SPOF | ⚠ Redis down → NoOpCacheManager fallback | ✅ no SPOF |
| Serialization complexity | Jackson + Hibernate6Module | n/a (object refs) |
| Memory footprint | offloaded to Redis | heap pressure |

В контексте v0.0.0 (single-instance deploy, 523 users, 300 subjects)
latency Redis (~0.5ms) не является bottleneck'ом. Hit-rate > 80%
даёт p50 latency `isHeadmanFor` ~0.6ms против ~15ms для uncached
RPC + DB (для gRPC hot-path).

## TTL matrix — motivation

### Короткие TTL (1-5 мин)

**`rbac` (1 мин).** RBAC-флаг `is_headman` может быть отозван
админом в любой момент (D-13 cascade revoke). 1 минута ограничивает
окно «старого права» — пользователь max 60 секунд работает с
устаревшей ролью после revoke. Programmatic evict в `UserService.patchUser`
и `transferStudent` закрывает окно мгновенно при локальной мутации;
TTL — fallback для случаев когда кеш evict пропущен (defence in depth).

**`groups` / `group_members` / `users` (5 мин).** Баланс между
read-heavy workload (teacher dashboard, student profile, gRPC lookup)
и частотой изменений. Name / role / telegramId меняются редко
(< 1 раз в час per user), но pointer-integrity критичен.

### Средние TTL (10 мин)

**`active_semester` (10 мин).** Активация нового семестра — админ-
операция, редкая (2 раза в год). `@CacheEvict(allEntries=true)` на
`SemesterService.activateSemester` инвалидирует мгновенно. 10 мин
TTL — защита от edge-case если admin меняет активный семестр без
предсказуемого flow (multi-step workflow на вебе).

**`subject` (10 мин).** Предметы создаются/меняются редко (staroste
actions). Атомарное `@CacheEvict(key="#id")` на updateSubject /
deleteSubject. 10 мин — мотивация 02 P2-5 («без TTL = memory leak»).

### Длинные TTL (60 мин)

**`campus_geofence` (60 мин).** Координаты кампуса меняются раз в
годы (физически — один корпус по адресу Образцова 9). Инвалидация
не реализована — при изменении настроек геозоны требуется перезагрузка
сервиса (acceptable per requirements).

## Invalidation triggers

### Declarative @CacheEvict

Простые cases когда ключ кеша совпадает с параметром метода:

- `updateUser(Long id, ...)` → `@CacheEvict(value="users", key="#id")`
- `archiveUser(Long id)` → `@CacheEvict(value="users", key="#id")`
- `updateGroup(Long id, ...)` → `@Caching(evict = {@CacheEvict(value="groups", key="#id"), @CacheEvict(value="group_members", key="#id")})`
- `deleteGroup(Long id)` → то же
- `activateSemester(Long id)` → `@CacheEvict(value="active_semester", allEntries=true)`
- `transferStudent(Long id, TransferStudentRequest request)` →
  `@CacheEvict(value="group_members", key="#request.newGroupId()")`
- `updateSubject(Long id, ...)` → `@CacheEvict(value="subject", key="#id")` (M05)
- `deleteSubject(Long id, boolean force)` → `@CacheEvict(value="subject", key="#id")` (M05)

### Programmatic eviction

Cases где нужно инвалидировать несколько namespace'ов с динамически
вычисленными ключами — `CacheManager.getCache(name).evict(key)`
напрямую (см. `UserService.patchUser:225-233`, `transferStudent:287-306`):

- **`patchUser` со сменой `is_headman`:** evict `groups::userGroupId`,
  `group_members::userGroupId`, `rbac::userId:oldGroupId`,
  `rbac::userId:newGroupId` (если группа тоже изменилась).
- **`patchUser` со сменой `group_id` (без flag change):** evict
  `rbac::userId:oldGroupId` + `rbac::userId:newGroupId`.
- **`transferStudent`:** evict `group_members::oldGroupId`,
  `users::userId`, `rbac::userId:oldGroupId`,
  `rbac::userId:newGroupId`. `@CacheEvict(group_members,
  key="#request.newGroupId()")` аннотацией покрывает новую группу.

### Почему не `@CacheEvict(allEntries=true)` везде

- **Redis KEYS scan дорог:** `FLUSHDB` или scan всех keys в namespace
  при каждой мутации убьёт p99 latency (O(n) per cache). Targeted
  evict через `evict(key)` — O(1) Redis DEL.
- **Точечная инвалидация точнее:** `rbac::3:1` evict затрагивает
  одну запись, не трогая `rbac::3:2` (если user 3 — староста group
  1 но не group 2).

## Consistency trade-offs

### Q13b race: activateSemester

**Сценарий:** админ активирует новый семестр. `@CacheEvict(allEntries=true)`
срабатывает локально — но если несколько инстансов academic-service
работают одновременно, **Redis evict распространяется мгновенно**
(single Redis instance shared), и окно рассинхронизации — ~ms.

Для Caffeine (отвергнутый вариант) это было бы **5 минут** без
межинстансной синхронизации. Redis as L1 **решает** эту проблему.

### Multi-instance scale-out

- **v0.0.0:** single-instance per service — no divergence.
- **v0.1+:** если понадобится horizontal scaling academic-service —
  Redis остаётся shared cache, консистентность сохраняется автоматически.
- **v0.2+ (если актуально):** L1 Caffeine + L2 Redis гибрид через
  `CompositeCacheManager` для снижения hot-path latency. Trade-off —
  L1 diverges на 30s-1min между instance'ами. Отложено до появления
  реального bottleneck'а.

## Observability

**В v0.0.0 hit/miss counter'ы для Redis cache не экспонируются.**
Spring Boot нативно поддерживает `CacheMeterBinder` только для
Caffeine/EhCache/JCache. RedisCache — no native binding.

Попытка кастомного `MetricsCacheManagerDecorator` в M05 Группе 3
ломала namespace-specific TTL (root cause не вычислен, см.
`docs/milestones/M05-performance/NOTES.md` секция «Deferred»).

**Индикативный мониторинг** вместо hit/miss:

- Redis dashboard Grafana: `redis_commands_total{cmd="get"}` vs
  `redis_commands_total{cmd="set"}` — косвенная оценка hit-rate.
- `redis_memory_used_bytes` + `redis_db_keys{db="0"}` — размер кеша.
- Application metrics: DB query counter через Hibernate statistics
  (не attribute к конкретному cache, но показывает нагрузку на БД).

**Future work** (`docs/archive/future-ideas.md` NEW-144-follow-up):

1. `@Aspect` вокруг `@Cacheable`-методов — считать hit/miss через
   proxy вместо wrapping `CacheManager`.
2. Dashboard-панели «cache hit-rate by namespace» как только metrics
   будут доступны.

## Migration plan

### Single-instance → multi-instance (v0.1 если появится)

Не требуется. Redis as shared cache обеспечивает консистентность.
Max действие — HAProxy / Sentinel для Redis HA (если нужен zero-
downtime на Redis restart).

### Сontainer Redis → managed Redis (prod hardening, v1.0+)

`application-prod.yml` `spring.data.redis.url` меняется на managed
endpoint. ACL + TLS. Никаких изменений в коде.

### Redis → Caffeine L1 + Redis L2 (если появится hot-path bottleneck)

Через `CompositeCacheManager` с Caffeine first, Redis fallback.
Invalidation — листенер на Redis pub/sub или TTL-based (30s L1,
existing TTL L2). Сложность — ADR required при активации.

## References

- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java` — `@Cacheable` в одном месте (AOP self-invocation per D-01)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java:225-306` — programmatic eviction паттерн
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIntegrationTest.java` — базовые cache-behavior тесты
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RbacCacheIT.java` — rbac namespace (M05)
- `docs/milestones/M05-performance/DECISIONS.md` D6 — обоснование отхода от OWNER-ANSWERS 3756-3810
