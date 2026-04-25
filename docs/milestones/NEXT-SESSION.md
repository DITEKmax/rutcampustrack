# Промпт для следующей сессии — после M13 + alpha.14

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M13 Pre-Deploy Hardening ✅ ЗАВЕРШЁН 2026-04-25
(tag `v0.0.0-alpha.14` локально). Все 24 группы закрыты.**

**Сейчас перед нами 4 шага до production v0.0.0 GA:**

1. **Push на origin/dev** — 31 коммит ahead, tag `v0.0.0-alpha.14`.
2. **Запустить CI/CD pipeline** через GitHub Actions — owner может
   trigger вручную либо через push.
3. **Live VPS dry-run** (M13 G23 owner-driven) — пройти
   `docs/prod-deploy-checklist.md` на VPS, зафиксировать findings
   в `docs/milestones/M13-pre-deploy-hardening/NOTES.md` G23 секции.
4. **Финальный tag `v0.0.0`** — после успешного dry-run + любые
   follow-up patches.

## Состояние M13 на 2026-04-25 (конец сессии)

**24 из 24 групп закрыто** (G1-G24). **31 коммит** ahead origin/dev.
Push отложен до явного `go` от владельца. Tag `v0.0.0-alpha.14`
локально, не push'ен.

| G | Commit | Тема |
|---|--------|------|
| 1-7 | (см. предыдущий NEXT-SESSION) | Foundations |
| 8 | `494821f` | Consumer dedup (G8) |
| 9 | `465b9c9` | 12 IDOR fixes (G9) |
| 10 | `9574d91`+`595b6e1` | Actuator tracing exclude (G10) |
| 11 | `472263a` | mem_limit 9 aux (G11) |
| 12 | `8948825` | Health degradation IT (G12) |
| 13 | `5e5aad4` | .env.prod validator (G13) |
| 14 | `1f836da` | Prometheus/Alertmanager lockdown (G14) |
| 15 | `3c92807`+`c5d8dc2` | Backup infrastructure (G15) |
| 16 | `153de6a`+`3e9566a` | CSP report endpoint (G16) |
| 17 | `10a213a` | Grafana dashboards validator (G17) |
| 18 | `93f20ec` | STOMP heartbeat 10s/10s (G18) |
| 19 | `37cad43` | RabbitMQ Prometheus + 4 new alerts (G19) |
| 20 | `a5b91ca` | blackbox-exporter + 3 SSL alerts (G20) |
| 21 | `3fa6f79` | Flyway CONCURRENTLY guard (G21) |
| 22 | `d340755` | Playwright auth lifecycle E2E + CI (G22) |
| 23 | `48055b5` | preflight + verify-deploy scripts (G23) |
| 24 | `c9861f7` | BLOCKERS B1-B5 + HIGH H2-H5 fixes (G24) |

**Tag `v0.0.0-alpha.14` →** `c9861f7`.

## Старт новой сессии — дословно

> Читаю в порядке:
>
> 1. `docs/milestones/NEXT-SESSION.md` — этот файл
> 2. `docs/milestones/M13-pre-deploy-hardening/CHECKLIST.md` —
>    24 группы (все ✅)
> 3. `docs/milestones/M13-pre-deploy-hardening/NOTES.md` — все
>    surprises + decisions, в том числе G24-fix-1..G24-fix-7
> 4. `docs/prod-deploy-checklist.md` — runbook для VPS deploy
> 5. `scripts/preflight-deploy.sh` + `scripts/verify-deploy.sh` —
>    automation для dry-run
> 6. `CLAUDE.md` — правила кодирования
>
> Спрашиваю владельца: **что делаем сейчас?**
>
> A. Push на origin/dev + tags (если ещё не сделано). Запустить
>    CI/CD. Если CI зелёный → переходим к B.
> B. Live VPS dry-run по `prod-deploy-checklist.md`. Я могу
>    помочь parsing'ом любых ошибок которые ты увидишь.
> C. Если dry-run прошёл без issues → tag `v0.0.0` GA.

## Pending-действия (требуют явного `go`)

1. **`git push origin dev`** — 31 commit ahead.
2. **`git push origin --tags`** — 14 tags локально (alpha.1-14).
3. **CI/CD прогон** через push trigger либо manual `gh workflow run`.
4. **VPS deploy** по `docs/prod-deploy-checklist.md`.
5. **Tag `v0.0.0`** GA после успешного dry-run.

## Что произошло в этой сессии (G17-G24)

### Группы 17-23 — feature work
8 групп закрыто атомарными коммитами. Подробности в commit messages
+ M13 NOTES.md.

### Группа 24 — финальная верификация
1. **gradle clean build + integrationTest** — зелёные.
2. **code-reviewer agent** запущен на M13 diff (commits
   `v0.0.0-alpha.13..HEAD`). Найдено **5 BLOCKERS** + 9 NOTABLE.
3. **security-auditor agent** — найдено **0 CRITICAL** + **5 HIGH**.

**Все 5 BLOCKERS + 5 HIGH исправлены в едином commit `c9861f7`:**

- **B1** (notification-bot fail-open + handler swallow): Redis error
  → exception (fail-closed); handler exception → re-raise → DLQ.
  `idempotency_guard.py` + `event_consumer.py` обновлены, тест
  `test_redis_error_fails_closed` под новое поведение.
- **B2** (CRITICAL — `MongoIdempotencyStore` обходил
  `MongoTransactionManager`): `mongoTemplate.getCollection().insertOne()`
  не участвует в Spring session binding — handler exception оставлял
  claim коммитнутым → event lost при redelivery (нивелирует G7+G8
  atomicity guarantee). Заменён на `mongoTemplate.insert(Document,
  String)`. Regression guard через `MongoIdempotencyStoreTest` с
  `verify(template, never()).getCollection(anyString())`.
- **B3** (`bitnami/mongodb:7.0` без digest-pin): Bitnami в Aug 2025
  убрали versioned tags из `bitnami/`. **Image больше не существует
  на DockerHub** — это блокировало бы first VPS pull. Switch на
  `bitnamilegacy/mongodb:7.0@sha256:16a57fa0...` (Bitnami official
  fallback). TODO v0.1: оценить миграцию на `mongo:7-jammy` + custom
  rs.initiate entrypoint script — возврат к Option A из M13 G7.
- **B4** (RS key placeholder enforcement): `validate-env-prod.sh`
  явный value-check против dev placeholder в `docker-compose.yml`
  (length-only check уже catch'ил, но явный check даёт actionable
  message).
- **B5** (schedule LockProvider invariant): false-positive в prod
  (LockProvider есть через SchedulingConfig), но code-reviewer прав
  про **architectural inconsistency** (academic имеет LockProvider
  в OutboxConfig.Storage, schedule зависит от внешнего
  SchedulingConfig). Документировано + `SchedulingConfigStructureTest`
  reflection-based regression guard.

- **H2** (CSP body cap): nginx `client_max_body_size 8k` на
  `location /api/csp-report` (DoS защита).
- **H3** (Java + Python idempotency missing event_id fail-closed):
  раньше fail-open пропускал legacy events. После M13 G8 все
  publisher'ы обязаны заполнять event_id; missing = bug либо forged.
  Caller `@Transactional` rollback → DLQ.
- **H4** (mongodump password в URL): через `MONGO_ROOT_PASS` env var
  в `docker exec` вместо `--uri="mongodb://root:PASS@..."` string
  (snitch на `/proc/<pid>/cmdline`).
- **H5** (.backup-passphrase permissions): explicit `stat -c '%a'`
  check на 600/400 в `backup.sh`.

**H1** (rate-limit burst 60 tok cold bucket) — **не fix'нул**, это
UX-affecting design decision владельца (требует подтверждения). H1
remains в TODO как **reconsider в v0.1** если bruteforce attempts
будут видны в `validate-env-prod.sh` audit logs.

### Test infrastructure fixes (G24-fix-7)

**Flaky `InternalJwtIssuerClientTest`**: `setTimeoutMillis 2_000 → 10_000`.
WebClient + WireMock startup на холодном CI > 2s в первом тесте
suite давал `InternalIssuerUnavailableException` вместо expected
token. Production timeout (3s) — отдельный property в
`application.yml`, не затронут.

**EventConsumerTest fixes** в attendance + notification + GroupEventTest +
NotificationHistoryConsumerTest: добавлен `@Mock IdempotencyGuard` +
`when(tryClaim).thenReturn(true)`. До G24-fix-6 эти тесты использовали
real `IdempotencyGuard` с NOOP_STORE, fail-open пропускал missing
event_id. После G24-fix-6 fail-closed throws IllegalStateException
→ тесты падали. Mock guard обходит fail-closed для unit-test focus
на routing (не на event_id semantics).

## Surprises зафиксированы в NOTES (для CONTEXT в будущем)

### Из M13 G24

- **`MongoIdempotencyStore` была сломанная M13 G7+G8** — claim row не
  откатывалась при handler rollback. **Не отлавливалось тестами**
  потому что `EventIdempotentIT` проверял только positive case
  (claim успех → no duplicate), не negative (claim + handler throw
  → claim откатился). M13 G24-fix-1 покрывает unit-test'ом, но IT
  на rollback flow — TODO v0.1.

- **Bitnami namespace migration Aug 2025** — `bitnami/mongodb:7.0` не
  существует на DockerHub. Это уже годовалое изменение, мы только
  сейчас наткнулись. Если другие Bitnami images используются в
  будущем — проверять `bitnamilegacy/` или Bitnami Secure
  (платная).

- **EventConsumerTest infrastructure was broken since M13 G8** —
  attendance EventConsumerTest имел `@InjectMocks EventConsumer` +
  `@Mock LessonEventService` + `@Mock SemesterCacheService`, но
  не `@Mock IdempotencyGuard` (новое поле от G8). NPE на
  `idempotencyGuard.tryClaim` (Mockito не auto-mock'ает
  unannotated final fields). **Никто не запускал** `attendance:test`
  с момента G8 — это означает что `./gradlew build` без
  `-x integrationTest` ни разу не прогонялся в M13. Issue вылез
  только в G24 final verification.

### Из commit history (для будущих чатов)

- M13 G18 STOMP heartbeat был **off (0/0)** до G18 — comment в
  WebSocketConfig утверждал «Spring default 10s/10s», но без
  явного `setTaskScheduler` heartbeat silently disabled.
- M13 G19 DLQBacklog был **silent dangling 6 milestone'ов** —
  `rabbitmq:3.13-alpine` не имел `rabbitmq_prometheus` plugin,
  метрика не экспортировалась.
- M13 G20 certbot renewal hook **не нужен** — M13 G14 уже добавил
  nginx auto-reload каждые 5 мин в `entrypoint.sh:57`.

## Known TODOs (для v0.1)

1. **MongoIdempotencyStore IT** — integration test на rollback flow
   (handler throws → claim row откатывается через
   MongoTransactionManager).
2. **mongo:7-jammy migration** — оценить переход с
   `bitnamilegacy/mongodb:7.0` (frozen) на официальный
   `mongo:7-jammy` + custom `rs.initiate.sh` entrypoint.
3. **Rate-limit burst 60 tok** (H1) — reconsider если будут видны
   bruteforce attempts через login/OTP.
4. **MongoIdempotencyStore.deleteProcessedBefore** использует raw
   `getCollection().deleteMany()` — для cleanup-job это OK
   (ShedLock-locked), но если когда-нибудь понадобится транзакция —
   refactor.
5. **CspViolationSpike alert rule** — `csp_violations_total` rate
   threshold (deferred из M13 G16).
6. **Offsite backup** (S3/B2 rclone) — deferred из M13 G15.
7. **Bitnami digest update cadence** — `bitnamilegacy/` не получает
   security updates после Aug 2025. Через 6-12 месяцев должен быть
   plan миграции либо подписка на Bitnami Secure.

## После v0.0.0 GA (для контекста)

После успешного VPS dry-run + `v0.0.0` tag:
1. CHANGELOG.md `[Unreleased]` → `[0.0.0]` с сегодняшней датой.
2. Version bump: root `build.gradle.kts` + `frontends/*/package.json`
   на `0.0.0`.
3. `docker-compose.prod.yml` image tags на `:v0.0.0` (если используется).
4. `git push origin dev` + `git push origin --tags`.
5. Создать v0.1 milestone директорию + roadmap для следующей итерации.

## Pending decisions (для new conversation)

1. **Push сейчас?** — если CI/CD прогон нужен сразу, push origin
   triggers `ci.yml` workflow. Можно посмотреть зелёный/красный.
2. **Live VPS dry-run когда?** — owner-driven, не automate'ится.
3. **`v0.0.0` GA тэг?** — только после dry-run + любые follow-up.

---

## История milestone'ов (архив)

M01-M08 ✅ (см. git tags `v0.0.0-alpha.1..alpha.9`).
M09 Prod Release Blockers ✅ 2026-04-24 (`v0.0.0-alpha.10`).
M10 Notification History ✅ 2026-04-24 (`v0.0.0-alpha.11`).
M11 OpenAPI Polish ✅ 2026-04-24 (`v0.0.0-alpha.12`).
M12 Auth Contract-first Refactor ✅ 2026-04-24 (`v0.0.0-alpha.13`).
**M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.14`).**

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
