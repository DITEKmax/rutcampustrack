# Coverage Audit — сверка отчётов 01-16 против OWNER-ANSWERS.md

Дата: 2026-04-19 (конец фазы аудита)

Цель: удостовериться, что **каждый** пункт P0/P1/P2 из отчётов 01-16 имеет
явный ответ в `OWNER-ANSWERS.md` — через Q-ID, P2-группу, AUTO-RESOLVED
через другой фикс, или ACCEPTED by owner.

**Источники ответов** (условные коды в колонке «Где ответ»):
- `01-Q…`, `02-Q…` и т.д. — именованные вопросы в соответствующем разделе.
- `P2-N/M` — ответ в рамках группы P2-N пункт M.
- `QA1..QE5`, `QD1..QD7` и т.д. — пачки P1-A..E.
- `C0-N` / `C1-N` — кросс-кластеры из 15-cross-cutting.
- `M1`/`M2` — meta-решения.
- `AUTO` — закрывается через другой фикс (указан).

Легенда статусов:
- ✅ TO-FIX — есть явный план фикса
- ✅ ACCEPTED — принято как есть (by design / tradeoff)
- ✅ DISSOLVED — переклассифицировано / распущено
- ✅ AUTO-RESOLVED — закрывается через другой фикс

## Трекинг выполнения (live)

Таблицы ниже фиксируют **план** на момент закрытия аудита (2026-04-19).
По мере выполнения milestones из `docs/milestones/` трекинг реального
закрытия ведётся в двух местах:

1. **Commit messages** — каждый коммит, закрывающий audit-пункт, должен
   содержать footer `Closes: 01 P0-1, 02 P0-2` (аналог `Fixes: #N` для
   issues). Grep по `git log --grep='Closes:'` даёт актуальную картину.
2. **Статус milestone'а** в `docs/milestones/README.md` — когда milestone
   переходит в ✅ готов, все его audit-пункты считаются закрытыми.
   Детали — в `docs/milestones/M{N}-*/PLAN.md` → раздел Scope.

**После v0.0.0 финала** — обновить этот файл одной пачкой, добавив колонку
«Closed in» со ссылкой на commit SHA / milestone ID для каждого пункта.
Добавлять колонку сейчас (при 354 строках) — overhead без выгоды; grep
по git log работает лучше.

---

## 01-auth-service (28 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 01 | P0-1 (auth-api-contract) | ✅ TO-FIX | 01-Q-P0-1 (a) создаётся auth-api-contract |
| 01 | P0-2 (plaintext initial_password в БД) | ✅ ACCEPTED | 01-Q1 (a) ACCEPTED by owner, M1 (не РФ) |
| 01 | P0-3 (auth на чужой БД academic_db) | ✅ ACCEPTED | 01-Q-P0-3 (c) shared-DB tradeoff, future-ideas v0.1 |
| 01 | P0-4 (OTP-код в HTTP body) | ✅ TO-FIX | 01-Q-P0-4 (a) перенос в RabbitMQ event otp.requested |
| 01 | P0-5 (timing-атака на OTP) | ✅ TO-FIX | 01-Q-P0-5 (a) MessageDigest.isEqual |
| 01 | P0-6 (DoS через LoginRateLimiter) | ✅ TO-FIX | 02-Q-rate-limit (c) Gateway+Redis / C0-4 |
| 01 | P1-1 (Response DTO records vs classes) | ✅ AUTO-RESOLVED | P2-1/1 + CLAUDE.md contract-first (01-Q-P0-1) |
| 01 | P1-2 (verify-by-code без лимита попыток) | ✅ TO-FIX | 02-Q-rate-limit ключевые лимиты /otp/verify-by-code 5 req/min |
| 01 | P1-3 (JwtAuthenticationFilter глотает все исключения) | ✅ TO-FIX | QE5 (a+b частично) audit log.X + Gateway filter |
| 01 | P1-4 (refresh не проверяет истечение refresh-токена) | ✅ TO-FIX | 02-Q-frontend-security (Часть А, JWT cookie + refresh semantics) |
| 01 | P1-5 (changePassword использует Redis KEYS) | ✅ TO-FIX | NEW-45 docs/redis-keyspace.md + rewrite на SCAN/точный ключ (охват через audit P1) |
| 01 | P1-6 (TMA initData replay 24ч) | ✅ TO-FIX | P2-8/8 (a) TMA HMAC contract-тест + P2-1/6 duplicate keys reject |
| 01 | P1-7 (changePassword без MFA/history) | ✅ ACCEPTED | 01-Q1 + M1: password recovery отдельная фича v0.1 |
| 01 | P1-8 (нет flow восстановления пароля) | ✅ ACCEPTED | 01-Q1 audit trail «recovery остаётся отдельной фичей» |
| 01 | P1-9 (/auth/refresh-body дубликат /auth/refresh) | ✅ TO-FIX | NEW-16 удалить /auth/refresh-body (02-Q-frontend-security) |
| 01 | P1-10 (@EnableConfigurationProperties + EnumConverters) | ✅ TO-FIX | P2-1/3 (a) converter активация в Jpa config |
| 01 | P2-1 (DEBUG-логирование в проде) | ✅ TO-FIX | QA1 (a) INFO-дефолт |
| 01 | P2-2 (Логи JTI/UserId в WARN) | ✅ AUTO-RESOLVED | P2-6/1 (c) hybrid audit + MaskingConverter |
| 01 | P2-3 (нет валидации формата OTP-кода) | ✅ AUTO-RESOLVED | P2-4/2 (a) @Pattern("^\\d{6}$") |
| 01 | P2-4 (otp Redis без namespace) | ✅ AUTO-RESOLVED | NEW-45 (docs/redis-keyspace.md префиксы) |
| 01 | P2-5 (JWT include group_id: null) | ✅ AUTO-RESOLVED | P2-1/4 (a) @JsonInclude(NON_NULL) |
| 01 | P2-6 (@JsonIgnoreProperties source/timestamp) | ✅ AUTO-RESOLVED | P2-1/5 (a+b) DomainEvent base + ignoreUnknown |
| 01 | P2-7 (parseQueryString без duplicate-keys check) | ✅ AUTO-RESOLVED | P2-1/6 (a) reject duplicate keys |
| 01 | P2-8 (LowercaseEnumConverter throws на unknown) | ✅ AUTO-RESOLVED | P2-1/7 (a) graceful log.warn + counter |
| 01 | P2-9 (JwtService.init publish раз) | ✅ AUTO-RESOLVED | P2-12/5 (a) @Scheduled + ShedLock |
| 01 | P2-10 (expiresIn без единиц измерения) | ✅ AUTO-RESOLVED | P2-5/2 (a) rename → expiresInSeconds + audit |
| 01 | P2-11 (changePassword 200 вместо 204) | ✅ AUTO-RESOLVED | P2-5/1 (a) / P2-2/3 (a+c) |
| 01 | P2-12 (TMA_BOT_TOKEN без default) | ✅ AUTO-RESOLVED | P2-5/3 (a) @ConfigurationProperties + @NotBlank fail-fast |

## 02-academic-service (36 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 02 | P0-1 (initial_password в REST + gRPC) | ✅ ACCEPTED | 01-Q1 + 02-Q1 ACCEPTED, M1 |
| 02 | P0-2 (UserContextFilter доверяет X-User-*) | ✅ TO-FIX | 02-Q2 (d) Internal JWT / C0-1 |
| 02 | P0-3 (NumberFormatException → 500) | ✅ AUTO-RESOLVED | 02-Q-P0-3 (a) AUTO через C0-1 (Internal JWT) |
| 02 | P0-4 (race в activateSemester) | ✅ ACCEPTED | 02-Q-P0-4 (c) single-admin invariant |
| 02 | P0-5 (пустой GRPC_SECRET default) | ✅ TO-FIX | 02-Q-P0-5 (a) fail-fast, закрывает 06 P0-2 |
| 02 | P0-6 (AFTER_COMMIT без outbox) | ✅ TO-FIX | 02-Q3 (b) In-app outbox / C0-3 |
| 02 | P0-7 (findAll ДЗ в память + N+1) | ✅ TO-FIX | 02-Q-P0-7 (a) Pageable + @EntityGraph |
| 02 | P1-1 (hard-delete групп) | ✅ TO-FIX | QB1 (c) soft-delete + archived_by/archived_at |
| 02 | P1-2 (hard delete Semester/Subject/Homework/TSG) | ✅ TO-FIX | QB1 (c) NEW-71 audit всех hard-delete |
| 02 | P1-3 (@EnableAspectJAutoProxy отсутствует) | ✅ TO-FIX | QE2 (b) ArchUnit rule «@Scheduled → @SchedulerLock / @SingleInstanceOnly» + audit (NEW-109) |
| 02 | P1-4 (смешанные роли @RequireRole + IDOR) | ✅ TO-FIX | C0-1 Internal JWT + NEW-31 SecurityIdorIT |
| 02 | P1-5 (markComplete 200 vs 204) | ✅ AUTO-RESOLVED | P2-5/1 (a) + P2-2/3 (a+c) CI conformance |
| 02 | P1-6 (SubjectService.deleteSubject force без audit) | ✅ TO-FIX | QB2 (c) + NEW-72 @AdminAction aspect |
| 02 | P1-7 (HomeworkService.requireAuthor не проверяет current старосту) | ✅ TO-FIX | C0-1 + NEW-31 SecurityIdorIT + P1-4 pattern |
| 02 | P1-8 (listHomeworks без TEACHER) | ✅ TO-FIX | P1-8 расширить @RequireRole({STUDENT, TEACHER, ADMIN}) — часть audit P1 (см. 02-Q3 AUTO для outbox связь; standalone fix) — закрыто через C0-1 role-scope audit в NEW-31 |
| 02 | P1-9 (GroupController.getGroup доступен всем) | ✅ TO-FIX | C0-1 Internal JWT groupId claim + NEW-31 |
| 02 | P1-10 (HomeworkRepository без индекса group_id+semester_id) | ✅ AUTO-RESOLVED | P2-10/1 (a) composite indexes |
| 02 | P1-11 (UserService.generateLogin общая sequence admin/teacher) | ✅ TO-FIX | QB7 + NEW-80/81 audit per-role sequence |
| 02 | P1-12 (str() не стандартная Hibernate function) | ✅ TO-FIX | P2-3/4 (c) domain exceptions + repo fix (Hibernate-совместимый CAST) |
| 02 | P1-13 (RequestContext request-scoped в @Transactional) | ✅ TO-FIX | C0-1 Internal JWT упраздняет RequestContext через header + audit паттерна |
| 02 | P1-14 (GlobalExceptionHandler raw ex.getMessage()) | ✅ TO-FIX | P2-3/1 (b) «обратитесь в поддержку, correlation=<trace_id>» |
| 02 | P2-1 (DEBUG-логирование не переопределено) | ✅ TO-FIX | QA1 (a) INFO-дефолт; audit trail + P2-6/1 MaskingConverter |
| 02 | P2-2 (isHeadman JSON serialization) | ✅ AUTO-RESOLVED | P2-1/1 (a) @JsonProperty("isHeadman") |
| 02 | P2-3 (NON_FINAL default typing) | ✅ AUTO-RESOLVED | P2-1/2 (c) explicit Jackson2JsonRedisSerializer<T> |
| 02 | P2-4 (Cache users без namespace) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine + NEW-45 redis-keyspace.md |
| 02 | P2-5 (Semester.firstWeekType String) | ✅ AUTO-RESOLVED | P2-1/3 (a) String→WeekType + converter |
| 02 | P2-6 (HeadmanAssistant.permissions String[]) | ✅ AUTO-RESOLVED | P2-1/3 (a) String[]→List<AssistantPermission> |
| 02 | P2-7 (ThresholdService без @RequireRole) | ✅ AUTO-RESOLVED | P2-12/2 (a) defense-in-depth @RequireRole(ADMIN) + ArchUnit |
| 02 | P2-8 (Semester.createdAt без @PrePersist) | ✅ AUTO-RESOLVED | P2-12/3 (a) @CreationTimestamp audit |
| 02 | P2-9 (SubjectRepository.findByNameContainingIgnoreCase unused) | ✅ AUTO-RESOLVED | P2-5/4 (a+b) dead code cleanup + ArchUnit/qodana |
| 02 | P2-10 (CampusSettingRepository.findAll().get(0) unused) | ✅ AUTO-RESOLVED | P2-5/4 (a+b) dead code cleanup |
| 02 | P2-11 (campus_settings без controller) | ✅ AUTO-RESOLVED | P2-5/5 (a) admin PUT /campus-settings endpoint |
| 02 | P2-12 (GroupNameParser regex слаб) | ✅ AUTO-RESOLVED | P2-5/5 (a) tighten regex |
| 02 | P2-13 (AssistantPermissionConverter объявлен, не активирован) | ✅ AUTO-RESOLVED | P2-1/3 (a) конвертер активация |
| 02 | P2-14 (patchUser evict кэша groups не покрывает весь drift) | ✅ AUTO-RESOLVED | P2-5/6 (a) @Caching evict condition |
| 02 | P2-15 (GroupPromotionService publish внутри транзакции) | ✅ AUTO-RESOLVED | P2-5/6 / C0-3 outbox |

## 03-schedule-service (33 пункта)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 03 | P0-1 (UserContextFilter доверяет X-User-*) | ✅ TO-FIX | C0-1 Internal JWT (02-Q2) |
| 03 | P0-2 (AFTER_COMMIT без outbox) | ✅ TO-FIX | C0-3 In-app outbox (02-Q3) |
| 03 | P0-3 (NumberFormatException → 500) | ✅ AUTO-RESOLVED | C0-1 (02-Q-P0-3) |
| 03 | P0-4 (@Scheduled без ShedLock — double-publish) | ✅ TO-FIX | 03-Q-P0-4 (a) ShedLock + C1-7 / NEW-28 |
| 03 | P0-5 (дрейф week-parity) | ✅ TO-FIX | 03-Q-P0-5 (a) WeekParityResolver helper |
| 03 | P1-1 (pagination в памяти) | ✅ TO-FIX | 02-Q-P0-7 (a) Pageable + NEW-26 audit + P2-4/3 max-page-size |
| 03 | P1-2 (MassCancelRequest без dateFrom<=dateTo) | ✅ AUTO-RESOLVED | P2-4/1 (b) custom @DateRangeValid |
| 03 | P1-3 (cancelLesson допускает CLOSED) | ✅ TO-FIX | QB5 (d) запрет изменения lesson после старта |
| 03 | P1-4 (HealthCheckController публично доступен) | ✅ TO-FIX | QA6 (a) Actuator + cleanup pережитков Phase 10 |
| 03 | P1-5 (DEBUG в prod) | ✅ TO-FIX | QA1 (a) INFO-дефолт |
| 03 | P1-6 (gRPC expectedSecret nullable) | ✅ TO-FIX | 02-Q-P0-5 (a) fail-fast GRPC_SECRET |
| 03 | P1-7 (IsoParityReconciler стартап-блокировка) | ✅ TO-FIX | 03-Q-P0-4 + P2-5/10 (a) INSERT RETURNING |
| 03 | P1-8 (LessonStatusTransitionJob FK + N+1) | ✅ AUTO-RESOLVED | P2-10/2 (a+c) @EntityGraph для list-endpoints |
| 03 | P1-9 (lesson.cancelled без cancelled_by) | ✅ AUTO-RESOLVED | P2-11/5 (a+b) lesson.cancelled с full snapshot |
| 03 | P1-10 (нет валидации startTime<endTime) | ✅ AUTO-RESOLVED | P2-4/1 (b) custom @StartBeforeEnd |
| 03 | P1-11 (cancelLesson не публикует событие для повторного cancel) | ✅ TO-FIX | C0-3 outbox + audit trail publisher (P1-11 закрыт тем же outbox PR) |
| 03 | P1-12 (ScheduleItem без инжектированного Clock) | ✅ AUTO-RESOLVED | P2-8/4 (a+b) Clock-injection refactor / 04 P2-4 |
| 03 | P2-1 (LessonAssembler без WebMvcLinkBuilder) | ✅ TO-FIX | P2-1 тематика HATEOAS-недочёты ушли в P3 тема F (16-nit-backlog) — закрыто пачкой P3 (см. P2-1 preamble в OWNER-ANSWERS.md) |
| 03 | P2-2 (пропуск WeekType при no-match) | ✅ AUTO-RESOLVED | P2-12/4 (a) javadoc + golden-test |
| 03 | P2-3 (IsoParityReconciler без RETURNING) | ✅ AUTO-RESOLVED | P2-5/10 (a) INSERT RETURNING |
| 03 | P2-4 (existsBy unused) | ✅ AUTO-RESOLVED | P2-5/4 (a+b) dead code cleanup |
| 03 | P2-5 (findByScheduleItemIdAndDateBetween мёртвый) | ✅ AUTO-RESOLVED | P2-5/4 (a+b) dead code cleanup |
| 03 | P2-6 (GetLessonsByGroup не учитывает cancel/reopen шаблона) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine cache + P2-5/5 + cross-field validator |
| 03 | P2-7 (DLQ без TTL/max-length) | ✅ AUTO-RESOLVED | P2-5/7 (a) DLQ TTL 7д + x-max-length + AM alert |
| 03 | P2-8 (IsoParityReconciler только активный семестр) | ✅ AUTO-RESOLVED | P2-10/1 (a) composite index + P2-5/10 RETURNING |
| 03 | P2-9 (SubjectDeletedCascadeService Map<String,Object> не типизирован) | ✅ AUTO-RESOLVED | P2-1/5 (a+b) DomainEvent base + strong-typed events |
| 03 | P2-10 (blockLessonByHeadman не event'ит userId) | ✅ AUTO-RESOLVED | P2-5/6 (a) lesson.blocked schema расширение |
| 03 | P2-11 (updateScheduleItem не валидирует firstWeekType) | ✅ AUTO-RESOLVED | P2-5/5 (a) cross-field validator |
| 03 | P2-12 (ErrorResponse @Schema examples рассинхрон) | ✅ AUTO-RESOLVED | P2-2/5 (a) + Q16a shared-web ErrorResponse |
| 03 | P2-13 (нет индекса schedule_one_off_lessons.subject_id) | ✅ AUTO-RESOLVED | P2-10/1 (a) composite indexes (NEW-142) |
| 03 | P2-14 (V8/V9 reset-marker history) | ✅ AUTO-RESOLVED | P2-5/7 (a) historical note в database-schema.md |
| 03 | P2-15 (application.yml GRPC_SECRET пустой default) | ✅ AUTO-RESOLVED | 02-Q-P0-5 (a) fail-fast |
| 03 | P2-16 (@Profile("!test") на SchedulingConfig) | ✅ AUTO-RESOLVED | P2-5/10 (a) @Profile("prod \| dev") |

## 04-attendance-service (26 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 04 | P0-1 (UserContextFilter доверяет X-User-*) | ✅ TO-FIX | C0-1 Internal JWT |
| 04 | P0-2 (не хранятся координаты) | ✅ ACCEPTED | 04-Q2 (a) координаты НЕ сохраняем, doc-fix |
| 04 | P0-3 (@PostConstruct gRPC silent fallback) | ✅ TO-FIX | QA6 (a) health indicators + fail-fast startup audit |
| 04 | P0-4 (X-Group-Id IDOR) | ✅ TO-FIX | 04-Q-P0-4 (b) AUTO через C0-1 + SecurityIdorIT (NEW-31) |
| 04 | P0-5 (@RabbitListener → DLQ без retry) | ✅ TO-FIX | C0-3 outbox + P2-3/7 Python dispatcher requeue vs DLQ |
| 04 | P0-6 (cleanupOrphans mass-delete на старте) | ✅ TO-FIX | 04-Q-P0-6 (a) удалить @PostConstruct cleanup |
| 04 | P1-1 (MarkingService обходит AttendanceWritePort) | ✅ TO-FIX | архитектурный fix — изоляция checkin/report port usage (см. CLAUDE.md attendance package rules) + test (NEW-31 regression) |
| 04 | P1-2 (LateCheckinService импортирует checkin.AttendanceRepository) | ✅ TO-FIX | тот же архитектурный fix — shared/port разделение (CLAUDE.md) + ArchUnit NEW-109 rule |
| 04 | P1-3 (ExcuseService.updateStatus без tx boundary) | ✅ TO-FIX | C0-3 outbox гарантирует атомарность commit+event |
| 04 | P1-4 (MongoDB transactions требуют replica set) | ✅ TO-FIX | QA6 + deploy: Mongo RS single-node (документируется в docs/architecture.md или runbook) — закрыто через P2-8/2 Testcontainers Mongo with RS |
| 04 | P1-5 (@PostConstruct initIndexes + ensureIndex дублирование) | ✅ AUTO-RESOLVED | 04-Q-P0-6 (cleanup удалён) + P2-6/3 AUTO |
| 04 | P1-6 (LessonEventService без @Transactional) | ✅ TO-FIX | C0-3 outbox |
| 04 | P1-7 (EventConsumer extractLong NPE) | ✅ AUTO-RESOLVED | P2-1/5 (a+b) DomainEvent strong-typed |
| 04 | P1-8 (ExcuseEventPublisher base64 в RabbitMQ) | ✅ TO-FIX | 06 + бот flow: файлы пересылаются через Telegram, а не RabbitMQ (см. CLAUDE.md excuse-тикет flow). Backend fix: убрать file_payload_b64 из publisher. Закрыто через P2-11/8 (excuse.decision events, file out of payload) |
| 04 | P1-9 (ReportService.getJournal квадратичная) | ✅ AUTO-RESOLVED | P2-10/5 (a) SQL-aggregate + P2-10/3 cache |
| 04 | P1-10 (ReportService.filterExistingLessons gRPC N+1) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine cache + P2-10/8 параллелизация |
| 04 | P1-11 (MarkingService race на findOne после upsert) | ✅ TO-FIX | P2-8/2 Testcontainers real Mongo integration test + refactor findAndModify |
| 04 | P2-1 (MarkingService == для Long) | ✅ AUTO-RESOLVED | P2-10/8 (c) hot-path + ArchUnit rule (NEW-109) — standard Java bug, audit pass |
| 04 | P2-2 (authorizeHeadmanOrTeacher без кэша) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine rbac cache |
| 04 | P2-3 (ExcuseService.resolveLessonDetails swallow RuntimeException) | ✅ AUTO-RESOLVED | P2-3/8 (a+b) audit empty catch + ArchUnit/Checkstyle (+ P2-5/10 подтверждение) |
| 04 | P2-4 (зашитая TZ без Clock) | ✅ AUTO-RESOLVED | P2-8/4 (a+b) Clock-injection refactor |
| 04 | P2-5 (catch-all → 500 с ex.getMessage()) | ✅ AUTO-RESOLVED | P2-3/1 (b) correlation ID (+ P2-5/10 подтверждение) |
| 04 | P2-6 (AttendanceIndexInitializer дублирует cleanup) | ✅ AUTO-RESOLVED | P2-6/3 ✅ через Q15b (удалили cleanupOrphans) |
| 04 | P2-7 (magic number 10MB в двух местах) | ✅ AUTO-RESOLVED | P2-4/6 (b+a) @ValidFile + @ConfigurationProperties |
| 04 | P2-8 (нет валидации groupId в CheckinService) | ✅ AUTO-RESOLVED | C0-1 groupId из JWT claim + NEW-31 (+ P2-5/10 подтверждение) |
| 04 | P2-9 (LateCheckinRequest без composite index) | ✅ AUTO-RESOLVED | P2-10/1 (a) composite indexes |

## 05-notification-service (24 пункта)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 05 | P0-1 (UserContextFilter доверяет X-User-Id/Role) | ✅ TO-FIX | C0-1 Internal JWT |
| 05 | P0-2 (нет GlobalExceptionHandler) | ✅ TO-FIX | 05-Q-P0-2 (b) shared-web модуль (Q16a) / C1-11 |
| 05 | P0-3 (push_subscriptions в attendance_db) | ✅ TO-FIX | 05-Q-P0-3 (b) shared-DB accepted, миграции переносятся |
| 05 | P0-4 (SubscriptionAuthInterceptor IDOR) | ✅ TO-FIX | 05-Q-P0-4 (b) DELETE /push/subscriptions/me |
| 05 | P0-5 (handshake без audit) | ✅ TO-FIX | 05-Q-P0-5 (a) HandshakeInterceptor + Loki |
| 05 | P1-1 (нет Reminder-подсистемы в Java) | ✅ ACCEPTED | P2-12/7 (a) Reminders остаются в Python (Aiogram + APScheduler); Java migration → v0.1 |
| 05 | P1-2 (DEBUG + утечка push endpoint) | ✅ TO-FIX | QA1 (a) INFO + P2-6/1 MaskingConverter |
| 05 | P1-3 (EventConsumer без retry/DLQ) | ✅ TO-FIX | C0-3 outbox + P2-3/7 Python dispatcher pattern applied аналогично в Java |
| 05 | P1-4 (IllegalArgumentException рвёт STOMP) | ✅ TO-FIX | P2-3/5 (c) hybrid unauthorized→silent, malformed→ERROR frame |
| 05 | P1-5 (unsubscribe не проверяет endpoint ownership) | ✅ TO-FIX | 05-Q-P0-4 (b) DELETE /me (тот же фикс) |
| 05 | P1-6 (Origin check хрупкий) | ✅ TO-FIX | 07-Q-P0-1 (b) CORS env + C0-7 wss origin |
| 05 | P1-7 (isHeadman snapshot без re-sync) | ✅ AUTO-RESOLVED | P2-10/3 (a) rbac cache TTL 1 мин + QB6 telegram_id verification |
| 05 | P1-8 (EventConsumer не проверяет схему) | ✅ TO-FIX | QD3 (a) contract-тесты schema validation |
| 05 | P1-9 (sendToGroup N+1 sync в for-loop) | ✅ TO-FIX | P2-10/8 (c) параллелизация + P2-6/4 separate consumer |
| 05 | P2-1 (Dockerfile alpine без curl) | ✅ AUTO-RESOLVED | P2-9/1 (a) HEALTHCHECK + curl apk add |
| 05 | P2-2 (VAPID public с @RequireRole STUDENT) | ✅ AUTO-RESOLVED | P2-5/9 (a) remove @RequireRole |
| 05 | P2-3 (@Valid @RequestBody дублируется) | ✅ AUTO-RESOLVED | P2-5/9 (a) dedup |
| 05 | P2-4 (CSRF отсутствует) | ✅ AUTO-RESOLVED | P2-8/8 (a) CSRF double-submit contract-test / C0-7 |
| 05 | P2-5 (buildTitle/Body switch в одном файле) | ✅ AUTO-RESOLVED | P2-5/9 (a) Strategy-pattern NotificationTemplate |
| 05 | P2-6 (attendance_db в dev default) | ✅ AUTO-RESOLVED | P2-5/9 (a) comment + Q16b shared-DB pattern |
| 05 | P2-7 (нет @Scheduled cleanup push) | ✅ AUTO-RESOLVED | P2-10/7 (a) @Scheduled weekly cleanup + ShedLock |
| 05 | P2-8 (нет метрик push/STOMP) | ✅ AUTO-RESOLVED | P2-5/9 (a) Micrometer counters |
| 05 | P2-9 (Lombok @Data + @Builder избыточно) | ✅ AUTO-RESOLVED | P2-1/8 (a) @EqualsAndHashCode onlyExplicitlyIncluded |
| 05 | P2-10 (setRole NPE/IAE на невалидных ролях) | ✅ AUTO-RESOLVED | P2-5/9 (a) graceful → UnauthorizedException 401 |

## 06-notification-bot (21 пункт)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 06 | P0-1 (insecure_channel gRPC) | ✅ ACCEPTED | 06-Q-P0-1 (c) docker-сеть = граница доверия, NEW-41/42 |
| 06 | P0-2 (placeholder BOT_TOKEN, пустой GRPC_SECRET) | ✅ TO-FIX | 06-Q-P0-2 (a) fail-fast (закрыто через 13c + P2-5/3) |
| 06 | P0-3 (initial_password в plaintext Telegram) | ✅ ACCEPTED | 06-Q2 (a) оставляем как есть, M1 |
| 06 | P1-1 (callback не проверяет роль старосты) | ✅ TO-FIX | P2-11/8 (a+c) excuse events + 19f callback pytest |
| 06 | P1-2 (Reminder in-memory теряется при рестарте) | ✅ ACCEPTED | P2-12/7 (a) Reminders в Python / APScheduler (persistence через rehydration) |
| 06 | P1-3 (naive datetime.now() без TZ) | ✅ TO-FIX | P2-12/7 (a) pytest+freezegun TZ-aware tests + audit |
| 06 | P1-4 (исключения ACK без retry) | ✅ TO-FIX | P2-3/7 (b) requeue for transient, DLQ for permanent + prometheus counter |
| 06 | P1-5 (нет per-user rate-limit на команды бота) | ✅ TO-FIX | C0-4 rate-limit (Gateway) не покрывает Telegram-side — in-bot rate-limit per telegram_id (закрыто через P2-3/7 metrics + NEW-40 alert на подозрительную активность, v0.1 scope) |
| 06 | P1-6 (file_payload_b64 в RabbitMQ) | ✅ AUTO-RESOLVED | P2-11/8 / 04 P1-8 (event-based excuse без file в payload) |
| 06 | P1-7 (main() ждёт FIRST_EXCEPTION вместо SIGTERM) | ✅ TO-FIX | QA6 (a) health indicators + graceful shutdown (audit pass в Python bot) |
| 06 | P1-8 (/login expiry-task не отменяется при verify) | ✅ TO-FIX | P2-12/6 (a) Testcontainers Redis для pub/sub tests + fix asyncio.Task cancellation (audit pass, P2-3/8 empty-catch lint) |
| 06 | P2-1 (parse_mode=HTML без экранирования) | ✅ TO-FIX | P2-5/9 / P2-3/8 audit pass + test coverage (19f NEW-53 callback tests) |
| 06 | P2-2 (bot_token в traceback через pydantic) | ✅ AUTO-RESOLVED | P2-6/1 (c) MaskingConverter + logging-conventions whitelist (NEW-165) |
| 06 | P2-3 (EventDispatcher import inside — тесты мокают с риском) | ✅ AUTO-RESOLVED | P2-8/2 (b) hybrid real Testcontainers + NEW-53 shared conftest.py |
| 06 | P2-4 (send_queue без maxsize) | ✅ AUTO-RESOLVED | P2-10/6 (c) connection pool tuning + P2-10/7 retention |
| 06 | P2-5 (caption truncate ломает HTML entities) | ✅ TO-FIX | audit pass в рамках P2-5/9 notification-template-catalog (NEW-178) — caption safe-truncate |
| 06 | P2-6 (academic_client cache не thread-safe) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine/Redis cache strategy + asyncio.Lock guard (audit pass) |
| 06 | P2-7 (MemoryStorage FSM теряется при рестарте) | ✅ AUTO-RESOLVED | P2-12/6 (a) Testcontainers Redis — миграция FSM в Redis |
| 06 | P2-8 (healthcheck проверяет только task.done()) | ✅ AUTO-RESOLVED | P2-9/1 (a) HEALTHCHECK + curl + liveness probe |
| 06 | P2-9 (нет CSRF для callback_query) | ✅ ACCEPTED | Telegram не предоставляет token — accept через 06 P1-1 fix (role check перед approve) |
| 06 | P2-10 (Dockerfile ставит curl, HEALTHCHECK нет) | ✅ AUTO-RESOLVED | P2-9/1 (a) HEALTHCHECK directive |

## 07-api-gateway (22 пункта)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 07 | P0-1 (CORS-домен rutcampustrack.ru) | ✅ TO-FIX | 07-Q-P0-1 (b) env CORS_ALLOWED_ORIGINS |
| 07 | P0-2 (/api/ws + /api/auth/otp публичны) | ✅ TO-FIX | 07-Q-P0-2 (a+c) AUTO C0-7/C0-4 + per-telegram_id OTP guard |
| 07 | P1-1 (PublicKeyConfig.init не fail-fast) | ✅ TO-FIX | QA6 (a) health + fail-fast startup + P2-12/5 @Scheduled key refresh |
| 07 | P1-2 (rate-limiting отсутствует) | ✅ TO-FIX | 02-Q-rate-limit (c) / C0-4 |
| 07 | P1-3 (allow-credentials + широкий CORS в dev) | ✅ TO-FIX | 07-Q-P0-1 (b) — dev origin ограничен явно |
| 07 | P1-4 (DEBUG Gateway + JWT в query) | ✅ AUTO-RESOLVED | QA1 (a) INFO + P2-6/1 / C0-7 ws-ticket |
| 07 | P1-5 (JwtAuthenticationFilter catch только JwtException) | ✅ TO-FIX | P2-3/6 (a) Gateway ErrorResponses utility + catch-all |
| 07 | P1-6 (нет downstream health-check) | ✅ TO-FIX | QA6 (a) health indicators + P2-9/1 HEALTHCHECK compose depends_on |
| 07 | P1-7 (/auth/logout требует JWT) | ✅ TO-FIX | C0-7 cookie-based logout + expired-JWT-accept flag |
| 07 | P1-8 (нет application/problem+json на 401) | ✅ TO-FIX | P2-3/6 (a) RFC 7807 формат в Gateway |
| 07 | P1-9 (нет специфичных WebSocket routes) | ✅ TO-FIX | C0-7 ws-ticket + /api/ws/** generic route (audit pass) |
| 07 | P2-1 (show-details: always в dev) | ✅ AUTO-RESOLVED | P2-5/8 (a) when_authorized prod |
| 07 | P2-2 (prometheus без ACL) | ✅ AUTO-RESOLVED | P2-5/8 (a) nginx basic-auth (P2-2/6) |
| 07 | P2-3 (DedupeResponseHeader) | ✅ AUTO-RESOLVED | P2-5/8 (a) сохранить + comment |
| 07 | P2-4 (Springdoc без авторизации) | ✅ TO-FIX | P2-2/6 (b) nginx basic-auth + SWAGGER_PASSWORD |
| 07 | P2-5 (Set-Cookie для WS handshake) | ✅ AUTO-RESOLVED | P2-5/8 / C0-7 JWT cookie |
| 07 | P2-6 (нет HEALTHCHECK в Dockerfile) | ✅ AUTO-RESOLVED | P2-9/1 (a) HEALTHCHECK |
| 07 | P2-7 (max-in-memory-size 12MB) | ✅ AUTO-RESOLVED | P2-5/8 (a) оставить + comment (excuse upload) |
| 07 | P2-8 (auth-service-url дублируется) | ✅ AUTO-RESOLVED | P2-5/8 (a) GatewayRoutingProperties |
| 07 | P2-9 (@EnableScheduling только для PublicKeyConfig) | ✅ AUTO-RESOLVED | P2-5/8 (a) comment + P2-12/5 key refresh |
| 07 | P2-10 (allowed-headers "*" в dev) | ✅ AUTO-RESOLVED | P2-5/8 (a) accept dev + prod env |
| 07 | P2-11 (springdoc enable-native-support) | ✅ AUTO-RESOLVED | P2-5/8 (a) remove legacy flag |

## 08-shared-proto-events (17 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 08 | P0-1 (initial_password в proto) | ✅ ACCEPTED | 08-Q1 (a) AUTO через 01-Q1, M1 |
| 08 | P0-2 (нет схемы otp.requested) | ✅ TO-FIX | 08-Q-P0-2 / Q19a (b) JSON Schema + event_version |
| 08 | P1-1 (время/даты как строки без TZ) | ✅ TO-FIX | QA3 + NEW-100/101/60/61 (retrofit DomainEvent + trace_id/occurred_at date-time) |
| 08 | P1-2 (JSON-схемы без additionalProperties:false) | ✅ TO-FIX | QD3 (a) schema validation + NEW-120 _common.json strict |
| 08 | P1-3 (enum-значения hardcoded string в .proto) | ✅ AUTO-RESOLVED | P2-11 / NEW-100 retrofit proto enums |
| 08 | P1-4 (first_week_type — string в контракте) | ✅ AUTO-RESOLVED | P2-1/3 (a) firstWeekType String→WeekType + NEW-120 common defs |
| 08 | P1-5 (нет контракта версионирования) | ✅ TO-FIX | QA3/NEW-47/48 event_version policy + docs/event-schemas.md |
| 08 | P1-6 (excuse.requested с опциональными lessons) | ✅ AUTO-RESOLVED | P2-11/6 (b) accept roundtrip + P2-11/8 excuse.decision events |
| 08 | P1-7 (otp.verified канал без защиты от injection) | ✅ TO-FIX | P2-4/8 (c) + NEW-100 + схема otp.verified в event-schemas |
| 08 | P2-1 (proto3 без optional group_id) | ✅ AUTO-RESOLVED | P2-11/1 (a) proto3 optional group_id |
| 08 | P2-2 (GroupResponse без semester_id) | ✅ AUTO-RESOLVED | P2-11/2 (c) + NEW-116 current_semester_id |
| 08 | P2-3 (StudentInfo.display_name privacy) | ✅ AUTO-RESOLVED | P2-11/3 (a) разбиение ФИО + display_name_short |
| 08 | P2-4 (HeadmanCheckRequest избыточный group_id) | ✅ AUTO-RESOLVED | P2-11/4 (a) comment cross-group защита |
| 08 | P2-5 (lesson.deleted только lesson_id) | ✅ AUTO-RESOLVED | P2-11/5 (a+b) lesson.cancelled + removed lesson.deleted |
| 08 | P2-6 (lesson.closed мало данных) | ✅ AUTO-RESOLVED | P2-11/6 (b) accept roundtrip за group_members |
| 08 | P2-7 (lesson_number 1..8 hardcoded в 6 схемах) | ✅ AUTO-RESOLVED | P2-11/7 (b) $defs в _common.json |
| 08 | P2-8 (нет excuse.decision) | ✅ AUTO-RESOLVED | P2-11/8 (a+c) excuse.approved/rejected events |

## 09-frontend-pwa (36 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 09 | P0-1 (JWT в localStorage) | ✅ TO-FIX | 02-Q-frontend-security (Часть А) / C0-7 |
| 09 | P0-2 (JWT в query string WebSocket) | ✅ TO-FIX | 02-Q-frontend-security (Часть А) / C0-7 ws-ticket |
| 09 | P0-3 (нет ролевых guards) | ✅ TO-FIX | 09-Q-P0-3 / Q19b (b) useAuth() + RoleGuard |
| 09 | P0-4 (SW cache не чистится на logout) | ✅ TO-FIX | 02-Q-frontend-security (Часть Б) / C0-5 clearAllClientState |
| 09 | P0-5 (push-subscription не отвязывается на logout) | ✅ TO-FIX | 02-Q-frontend-security (Часть Б) / C0-5 |
| 09 | P1-1 (parseJwt без валидации подписи) | ✅ TO-FIX | QC2 openapi-ts + C0-7 HttpOnly cookie (JWT не парсится на клиенте) |
| 09 | P1-2 (axios refresh interceptor loop) | ✅ TO-FIX | C0-7 cookie-based refresh + P2-8/6 тесты |
| 09 | P1-3 (нет строгой CSP) | ✅ TO-FIX | Q19c / NEW-55 аналогичная CSP для PWA vhost |
| 09 | P1-4 (STOMP переподключение при accessToken change) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter + C0-7 |
| 09 | P1-5 (STOMP без reconnect strategy) | ✅ TO-FIX | QC1 (a) reconnectWithBackoff + visibilityState |
| 09 | P1-6 (useStompCheckin дубли подписок при reconnect) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter |
| 09 | P1-7 (push SW handler без валидации типа) | ✅ TO-FIX | QC2 (b) типизация payload + QD3 schema validation |
| 09 | P1-8 (notification.click без deep-link через SW) | ✅ TO-FIX | P2-6/4 notification persistence + front-end routing (audit pass, закрыто через Phase UI-update v0.0.0) |
| 09 | P1-9 (STOMP-сообщения без schema validation) | ✅ AUTO-RESOLVED | QD3 contract-тесты + QC2 TS types |
| 09 | P1-10 (handleToggleBlock без rollback optimistic UI) | ✅ TO-FIX | QC3 (b) interceptor + optimistic rollback pattern (NEW-93 invalidation) |
| 09 | P1-11 (excuse file upload Content-Type overwrite) | ✅ AUTO-RESOLVED | QC2 + P2-4/6 @ValidFile (unified payload handling) |
| 09 | P1-12 (ExcusesPage/LateCheckinPage swallow errors) | ✅ AUTO-RESOLVED | QC3 (b) error-interceptor + RFC 7807 parser |
| 09 | P1-13 (StatsPage N×запросов) | ✅ AUTO-RESOLVED | QC6 (b) aggregate endpoint + cache |
| 09 | P1-14 (window.confirm блокирует) | ✅ AUTO-RESOLVED | QC4 (b) ConfirmWithReasonDialog (PWA React аналог) |
| 09 | P2-1 (refetchOnWindowFocus:false) | ✅ AUTO-RESOLVED | P2-7A/1 (b) refetchOnWindowFocus:true + staleTime |
| 09 | P2-2 (нет pull-to-refresh) | ✅ AUTO-RESOLVED | P2-7A/1 (b) usePullToRefresh hook |
| 09 | P2-3 (usePrefetchSubjects дубли) | ✅ AUTO-RESOLVED | P2-7A/1 staleTime + QC6 cache |
| 09 | P2-4 (parseJwt падает на кириллице) | ✅ AUTO-RESOLVED | C0-7 HttpOnly cookie (JWT не парсится на клиенте) + QC2 |
| 09 | P2-5 (useTodayLesson переопределяет схему LessonResponse) | ✅ AUTO-RESOLVED | QC2 openapi-typescript type drift |
| 09 | P2-6 (useGroupMembers без pagination) | ✅ AUTO-RESOLVED | P2-4/3 (a) max-page-size + P2-8/6 pagination тесты |
| 09 | P2-7 (auto-scroll работает один раз) | ✅ AUTO-RESOLVED | P2-7A/2 (a) auto-scroll on [week, currentTime] |
| 09 | P2-8 (нет тестов headman/SW/Auth) | ✅ AUTO-RESOLVED | P2-8/6 (a) критичные frontend units + SW cache logout |
| 09 | P2-9 (handleDaySwipe с случайными trigger) | ✅ AUTO-RESOLVED | P2-7A/2 (a) swipe threshold 100px + velocity 0.3 |
| 09 | P2-10 (BottomNav layoutId прыгает) | ✅ TO-FIX | P2-7A/7 навигация ревизия + NEW-172 frontend-navigation.md |
| 09 | P2-11 (HeadmanLessonSheet последовательные await) | ✅ AUTO-RESOLVED | P2-10/4 (c) batch endpoints + P2-8/6 component test |
| 09 | P2-12 (NotificationCenter limit 200 без UI) | ✅ AUTO-RESOLVED | P2-6/4 (b) backend persistence + pagination |
| 09 | P2-13 (HomeworkPage selectedDate inconsistency) | ✅ AUTO-RESOLVED | P2-7A/3 (a) single source-of-truth selectedDate |
| 09 | P2-14 (SubjectsList <div role=button>) | ✅ AUTO-RESOLVED | P2-7B/1 (a) semantic HTML audit + jsx-a11y |
| 09 | P2-15 (IOSOnboardingOverlay на /login) | ✅ TO-FIX | P2-7A/7 навигация/guard (audit pass в phase-разметке UI) |
| 09 | P2-16 (DrawerMenu устаревший) | ✅ AUTO-RESOLVED | P2-7A/7 (a) убрать DrawerMenu + BottomNav |
| 09 | P2-17 (геолокация без enableHighAccuracy) | ✅ AUTO-RESOLVED | P2-7A/8 (a+c) enableHighAccuracy:true + loading indicator |

## 10-frontend-web-panel (40 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 10 | P0-1 (JWT в localStorage) | ✅ TO-FIX | 02-Q-frontend-security / C0-7 |
| 10 | P0-2 (JWT в WS query 3 места) | ✅ TO-FIX | 02-Q-frontend-security / C0-7 |
| 10 | P0-3 (нет CSP/security headers в nginx) | ✅ TO-FIX | 10-Q-P0-3 / Q19c (a) строгая CSP + HSTS |
| 10 | P0-4 (logout не чистит sessionStorage) | ✅ TO-FIX | 02-Q-frontend-security (Часть Б) / C0-5 |
| 10 | P1-1 (parseJwt без UTF-8 validation) | ✅ AUTO-RESOLVED | C0-7 cookie + QC2 |
| 10 | P1-2 (authInterceptor navigation-queue теряется) | ✅ TO-FIX | QC3 (b) error-interceptor + централизованный handle |
| 10 | P1-3 (нет централизованной обработки 403/404/5xx) | ✅ AUTO-RESOLVED | QC3 (b) error-interceptor |
| 10 | P1-4 (role.guard кольца редиректов) | ✅ TO-FIX | Q19b (b) RoleGuard / NEW-50 Angular guards audit |
| 10 | P1-5 (3 STOMP-клиента) | ✅ TO-FIX | QC1 (a) unified NotificationCenter / C1-1 |
| 10 | P1-6 (STOMP reconnect не отписывается) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter |
| 10 | P1-7 (notification-center effect-зависимость user object) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter refactor |
| 10 | P1-8 (HeadmanWeeklyJournalComponent параллельные N запросов) | ✅ AUTO-RESOLVED | P2-10/2 + QC6 aggregate |
| 10 | P1-9 (HeadmanLessonsComponent window.prompt) | ✅ AUTO-RESOLVED | QC4 (b) MatDialog ConfirmWithReasonDialog |
| 10 | P1-10 (PromotionPreview без CSRF/idempotency) | ✅ TO-FIX | C0-7 CSRF + P2-8/8 contract-test + idempotency-key |
| 10 | P1-11 (нет loading-индикатора) | ✅ TO-FIX | QC5 (a) lazy + preload + loading UX |
| 10 | P1-12 (student-notifications сортирует строку как Date) | ✅ AUTO-RESOLVED | QC2 + P2-6/4 backend typed DateTime |
| 10 | P1-13 (/groups/promote не идемпотентно) | ✅ TO-FIX | C0-7 CSRF + idempotency-key (аналогично P1-10) |
| 10 | P1-14 (enrichLessons N+M запросов) | ✅ AUTO-RESOLVED | P2-10/2 + QC6 aggregate |
| 10 | P1-15 (LateCheckinComponent без фильтра user_id) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter + filter |
| 10 | P1-16 (logout не отменяет in-flight) | ✅ TO-FIX | C0-5 clearAllClientState + abort controllers |
| 10 | P2-1 (дублирующий sidebar у старосты) | ✅ AUTO-RESOLVED | P2-7A/7 (a) убрать «Главная» у headman |
| 10 | P2-2 (/headman/homework vs /student/homework дубли) | ✅ TO-FIX | QC5 per-role modules + shared services (audit pass в v9.0) |
| 10 | P2-3 (HeadmanJournalGridComponent не валидирует lessonId) | ✅ TO-FIX | P2-4/4 (a) @PathVariable @Positive backend + frontend pre-validate |
| 10 | P2-4 (AdminDashboardComponent sparkline фейковые) | ✅ TO-FIX | QC7 (a) реальные metrics endpoint / P2-10/5 |
| 10 | P2-5 (StudentScheduleComponent nextWeek без границ) | ✅ AUTO-RESOLVED | P2-7A/4 (a+b) hard-stop на границах семестра |
| 10 | P2-6 (HeadmanScheduleComponent subjectName per render) | ✅ AUTO-RESOLVED | P2-7A/6 (a) forkJoin + Map O(1) lookup |
| 10 | P2-7 (HeadmanGroupComponent.students() any[]) | ✅ AUTO-RESOLVED | P2-7B/1 (a) типизация через QC2 |
| 10 | P2-8 (HeadmanApiService Observable<any>) | ✅ AUTO-RESOLVED | QC2 openapi-typescript generated types |
| 10 | P2-9 (theme.service не чистится на logout) | ✅ TO-FIX | C0-5 clearAllClientState / localStorage.clear |
| 10 | P2-10 (subject-cache без TTL) | ✅ AUTO-RESOLVED | P2-10/3 (a) Caffeine + invalidation |
| 10 | P2-11 (profile.updateAvatar PATCH endpoint может не существовать) | ✅ TO-FIX | P2-5/5 + P2-4/6 (audit endpoint) + P2-9/3 nginx 5m avatar |
| 10 | P2-12 (admin-dashboard hardcoded цвета) | ✅ TO-FIX | P2-7B/4 (a+c) neutral placeholder + theme-aware |
| 10 | P2-13 (UsersPageComponent initialPassword видим) | ✅ ACCEPTED | 10-Q7 (a) ACCEPTED by owner, M1 |
| 10 | P2-14 (HeadmanScheduleComponent водопад listSemesters→listSubjects→...) | ✅ AUTO-RESOLVED | P2-7A/6 (a) forkJoin + P2-10 aggregate |
| 10 | P2-15 (14-дневный window фиксирован) | ✅ AUTO-RESOLVED | P2-7A/3 (a) configurable windowDays prop |
| 10 | P2-16 (WeeklyJournal скролл теряется) | ✅ AUTO-RESOLVED | P2-7A/5 (a) scroll-position preservation |
| 10 | P2-17 (StudentExcusesComponent 90-дневное окно per open) | ✅ TO-FIX | QC6 aggregate + P2-10/3 cache (audit pass) |
| 10 | P2-18 (submitExcuseWithFile Content-Type JSON для blob) | ✅ AUTO-RESOLVED | P2-4/6 (b+a) @ValidFile + multipart fix |
| 10 | P2-19 (applyLocalUpdate не всегда получает cancelReason обновлённые даты) | ✅ AUTO-RESOLVED | QC1 unified NotificationCenter + QC3 event-driven updates |
| 10 | P2-20 (TeacherJournalPageComponent без forkJoin) | ✅ AUTO-RESOLVED | P2-7A/6 (a) forkJoin + Map |

## 11-frontend-mini-app — ПРОПУЩЕН

Пользователь ещё не доделал клиент; PWA-код будет скопирован туда в будущем.

## 12-frontend-landing (17 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 12 | P0-1 (CSP блокирует CDN лендинга) | ✅ TO-FIX | 02-Q-csp-landing (a) / C0-6 self-host |
| 12 | P0-2 («Открыть в Telegram» → web-login) | ✅ TO-FIX | 12-Q-P0-2 / Q19d (a) deep-link t.me/<bot> |
| 12 | P1-1 (нет og:image + Twitter Card) | ✅ TO-FIX | QE4 (a) полный набор meta-тегов |
| 12 | P1-2 (нет robots + canonical) | ✅ TO-FIX | QE4 (a) полный набор meta |
| 12 | P1-3 (нет preload приоритетных ассетов) | ✅ AUTO-RESOLVED | C0-6 self-host убирает preconnect к CDN + audit pass |
| 12 | P1-4 (нет SRI на CDN) | ✅ DISSOLVED | 02-Q-csp-landing (a) self-host — SRI не нужен |
| 12 | P1-5 (no-cache на index.html хрупок) | ✅ TO-FIX | P2-9/3 nginx config + P2-6/5 nginx JSON logs + audit cache-control в NEW-152 |
| 12 | P1-6 (текст excuse расходится с v9.0) | ✅ TO-FIX | QE1 (a) PR-template checklist + разовая правка |
| 12 | P2-1 (нет hreflang) | ✅ AUTO-RESOLVED | QE4 meta-теги (audit pass) + NEW-111 JSON-LD v0.1 |
| 12 | P2-2 (preconnect к fonts.googleapis.com без crossorigin) | ✅ DISSOLVED | C0-6 self-host убирает preconnect |
| 12 | P2-3 (hero-hardcoded дата и число) | ✅ AUTO-RESOLVED | P2-7B/4 (a+c) neutral-placeholder |
| 12 | P2-4 (href="#hero" не работает гладко) | ✅ TO-FIX | P2-7B/4 audit UX-фиксы вместе с content-freshness |
| 12 | P2-5 (SMIL без prefers-reduced-motion) | ✅ AUTO-RESOLVED | P2-7B/3 (b) SMIL→CSS keyframes + reduced-motion |
| 12 | P2-6 (rel=noopener без target=_blank) | ✅ TO-FIX | P2-7B/4 audit pass HTML hygiene + P2-7B/1 semantic HTML |
| 12 | P2-7 (theme-toggle без aria-pressed) | ✅ AUTO-RESOLVED | P2-7B/2 (a+c) manual ARIA audit + axe-core |
| 12 | P2-8 (hero overflow 1024-1280px без min-width:0) | ✅ AUTO-RESOLVED | P2-7B/4 (a+c) responsive overflow fix |
| 12 | P2-9 (box-shadow светится в light-theme) | ✅ TO-FIX | P2-7B/4 audit theme-aware styles (NEW-110 stylelint a11y rules повышены в v0.0.0) |

## 13-infra-docker-ci (28 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 13 | P0-1 (LE cert-name рассинхрон) | ✅ TO-FIX | 02-Q-le-cert (a) / C0-10 |
| 13 | P0-2 (CI↔deploy decoupling) | ✅ TO-FIX | 02-Q-ci-deploy-gate (a) / C0-8 branch protection |
| 13 | P0-3 (.env.prod с секретами в копии) | ✅ ACCEPTED+TO-FIX | 02-Q-secrets-rotation (гибрид) / C0-9 — .env.prod.example |
| 13 | P0-4 (CSP корневого nginx блокирует лендинг) | ✅ TO-FIX | 02-Q-csp-landing (a) / C0-6 self-host |
| 13 | P1-1 (deploy тянет :latest) | ✅ TO-FIX | QD1 (b) IMAGE_TAG=${sha} + semver / C1-3 |
| 13 | P1-2 (docker compose up -d дважды) | ✅ TO-FIX | QD1 deploy.yml cleanup + smoke (audit pass в CI refactor) |
| 13 | P1-3 (нет rate-limit на корневом nginx) | ❌ REJECTED | 02-Q-rate-limit (c) — выбран Gateway, nginx ОТКЛОНЁН |
| 13 | P1-4 (Mini-app без SHA-тега) | ✅ AUTO-RESOLVED | QD1 (b) IMAGE_TAG=${sha} |
| 13 | P1-5 (reverse-proxy nginx sleeps 5m) | ✅ TO-FIX | P2-9/1 (a) HEALTHCHECK + compose depends_on (audit pass, certbot flow) |
| 13 | P1-6 (cadvisor privileged + promtail docker.sock) | ✅ TO-FIX | QD4 (digest для cadvisor/promtail) + QD5 trivy scan |
| 13 | P1-7 (docker-compose.yml vs .prod.yml независимые копии) | ✅ TO-FIX | audit pass в deploy refactor (C0-9 .env.prod.example + override pattern) |
| 13 | P1-8 (фронт-nginx без healthcheck) | ✅ AUTO-RESOLVED | P2-9/1 (a) HEALTHCHECK + depends_on |
| 13 | P1-9 (certbot без reload nginx) | ✅ TO-FIX | 02-Q-le-cert (a) / C0-10 + NEW-23 maintenance window |
| 13 | P1-10 (base images pin только по тегу) | ✅ TO-FIX | QD4 (гибрид) digest для privileged / tag+Renovate для остального / C1-6 |
| 13 | P1-11 (CI на branches ['**']) | ✅ TO-FIX | QD1/QD7 CI config + NEW-105 ci-cd.md (audit pass) |
| 13 | P2-1 (BOT_TOKEN + TMA_BOT_TOKEN — одно значение) | ✅ TO-FIX | P2-5/3 (a) @ConfigurationProperties + audit env separation |
| 13 | P2-2 (observability на :latest) | ✅ AUTO-RESOLVED | P2-9/2 (a+b) semver+Renovate + digest для cadvisor/promtail |
| 13 | P2-3 (nginx client_max_body_size 12m глобально) | ✅ AUTO-RESOLVED | P2-9/3 (a) per-location limits |
| 13 | P2-4 (нет coverage-агрегации в CI) | ✅ AUTO-RESOLVED | QD2 (b) JaCoCo + Vitest + pytest-cov / C1-4 |
| 13 | P2-5 (.env.prod без DOMAIN + CERTBOT_EMAIL) | ✅ AUTO-RESOLVED | P2-12/1 (a) .env.prod.example add + init-letsencrypt.sh проверка |
| 13 | P2-6 (.env dev одинаковые пароли) | ✅ ACCEPTED | P2-9/8 (b) dev insecure by design, Testcontainers для RBAC |
| 13 | P2-7 (Gateway actuator show-details: always) | ✅ AUTO-RESOLVED | P2-5/8 (a) when_authorized в prod |
| 13 | P2-8 (/actuator/prometheus без auth) | ✅ AUTO-RESOLVED | P2-5/8 / P2-2/6 nginx basic-auth |
| 13 | P2-9 (Loki retention 7 дней) | ✅ ACCEPTED | P2-9/4 (c) 14д (консистентно с QA5) |
| 13 | P2-10 (Loki dangling alertmanager_url) | ✅ AUTO-RESOLVED | P2-9/5 (c) Alertmanager контейнер |
| 13 | P2-11 (init-mongo.js unused notification_db) | ✅ ACCEPTED→ACTIVATED | P2-9/6 (b) KEEP reserved → P2-6/4 ACTIVATED |
| 13 | P2-12 (shared POSTGRES_ACADEMIC_PASSWORD) | ✅ ACCEPTED | P2-9/7 (b) accept + NEW-155 rotation runbook |
| 13 | P2-13 (нет image signing / SBOM) | ✅ TO-FIX | QD5 (a) Trivy + Gitleaks + Dependabot + SECURITY.md |

## 14-tests-audit (26 пунктов)

| # | Пункт | Статус | Где ответ |
|---|-------|--------|-----------|
| 14 | P0-1 (latecheckin/ без тестов) | ✅ TO-FIX | 14-Q-P0-1 / Q19e (b) unit + IT + contract-тест / C1-9 |
| 14 | P0-2 (бот callback_query без тестов) | ✅ TO-FIX | 14-Q-P0-2 / Q19f (b) pytest + Aiogram fake-updates / C1-9 |
| 14 | P1-1 (нет contract-тестов Gateway↔downstream) | ✅ TO-FIX | NEW-5 smoke-test + NEW-31 SecurityIdorIT + P2-2/3 CI conformance |
| 14 | P1-2 (нет тестов LoginRateLimiter / OTP brute-force) | ✅ TO-FIX | 02-Q-rate-limit связка + Testcontainers Redis (P2-12/6) |
| 14 | P1-3 (нет coverage-gate в CI) | ✅ TO-FIX | QD2 (b) JaCoCo+Vitest+pytest-cov / C1-4 |
| 14 | P1-4 (PWA/web-panel нет тестов logout-lifecycle) | ✅ TO-FIX | P2-8/6 (a) критичные frontend units + SW cache logout |
| 14 | P1-5 (нет contract-тестов RabbitMQ событий, кроме excuse) | ✅ TO-FIX | QD3 (a) schema validation для всех 14+ events / C1-5 |
| 14 | P1-6 (нет contract-тестов proto) | ✅ TO-FIX | P2-8/2 gRPC in-process + QD3 pattern applied to proto |
| 14 | P1-7 (Mini-app нет теста TMA initData валидации) | ✅ TO-FIX | P2-8/8 (a) TMA HMAC contract-test — applied также для Mini-app (когда доделан) |
| 14 | P1-8 (CI на branches ['**'] не гейтит) | ✅ AUTO-RESOLVED | 02-Q-ci-deploy-gate / C0-8 branch protection |
| 14 | P1-9 (нет тестов WebSocket/STOMP lifecycle) | ✅ TO-FIX | P2-8/2 + P2-8/5 Playwright + 05 P0-5 handshake audit tests |
| 14 | P2-1 (mixed naming *Test vs *IT) | ✅ AUTO-RESOLVED | P2-8/1 (b) rename + Gradle task-split + ArchUnit |
| 14 | P2-2 (@MockitoBean мок-бины в IT) | ✅ AUTO-RESOLVED | P2-8/2 (b) Testcontainers + gRPC in-process |
| 14 | P2-3 (нет тестов reminders) | ✅ AUTO-RESOLVED | P2-12/7 (a) pytest+freezegun |
| 14 | P2-4 (нет тестов week-parity drift) | ✅ AUTO-RESOLVED | P2-8/4 (a+b) golden JSON fixtures + jqwik |
| 14 | P2-5 (web-panel multi-STOMP logout не покрыт) | ✅ AUTO-RESOLVED | P2-8/6 (a) critical frontend units |
| 14 | P2-6 (нет тестов CSP-compatibility) | ✅ AUTO-RESOLVED | P2-8/5 (a+c) Playwright e2e |
| 14 | P2-7 (нет теста GRPC_SECRET fail-fast) | ✅ AUTO-RESOLVED | P2-8/8 (a) security contract-тесты |
| 14 | P2-8 (нет нагрузочных тестов) | ✅ AUTO-RESOLVED | P2-8/7 (c+minimal a) k6 × 2 + v0.1 full load-suite |
| 14 | P2-9 (Flyway MigrationIT только в academic) | ✅ AUTO-RESOLVED | P2-8/3 (a+b) MigrationIT + data-preservation |
| 14 | P2-10 (fakeredis не поддерживает Lua/pub-sub) | ✅ AUTO-RESOLVED | P2-12/6 (a) Testcontainers Python Redis |
| 14 | P2-11 (web-panel нет e2e Playwright/Cypress) | ✅ AUTO-RESOLVED | P2-8/5 (a+c) Playwright × 4 critical flows |
| 14 | P2-12 (Mini-app тесты не мокают Telegram.WebApp) | ✅ ACCEPTED | P2-8/6 — mini-app not ready, accept |
| 14 | P2-13 (EventConsumerTest мокает SimpMessagingTemplate) | ✅ AUTO-RESOLVED | P2-8/2 (b) Testcontainers + real fixtures |
| 14 | P2-14 (нет тестов TMA flow при невалидной HMAC) | ✅ AUTO-RESOLVED | P2-8/8 (a) TMA HMAC contract-test |
| 14 | P2-15 (Landing — ноль тестов) | ✅ ACCEPTED | P2-8/5 — landing visual review через PR |

## 15-cross-cutting-issues (21 пункт — 10 P0-кластеров + 11 P1-кластеров)

| # | Кластер | Статус | Где ответ |
|---|---------|--------|-----------|
| 15 | C0-1 UserContextFilter | ✅ TO-FIX | 02-Q2 (d) Internal JWT |
| 15 | C0-2 initial_password plaintext | ✅ DISSOLVED | 01-Q1 / 15-Q2 (a) accept tradeoff, кластер распущен |
| 15 | C0-3 AFTER_COMMIT без outbox | ✅ TO-FIX | 02-Q3 (b) In-app outbox + ShedLock publisher-job |
| 15 | C0-4 Rate-limiting | ✅ TO-FIX | 02-Q-rate-limit (c) Gateway + Redis |
| 15 | C0-5 Logout lifecycle | ✅ TO-FIX | 02-Q-frontend-security (Часть Б) clearAllClientState |
| 15 | C0-6 CSP CDN лендинга | ✅ TO-FIX | 02-Q-csp-landing (a) self-host |
| 15 | C0-7 JWT localStorage + WS query | ✅ TO-FIX | 02-Q-frontend-security (Часть А) HttpOnly cookie + ws-ticket |
| 15 | C0-8 CI↔deploy decoupled | ✅ TO-FIX | 02-Q-ci-deploy-gate (a) branch protection + workflow_run |
| 15 | C0-9 .env.prod secrets | ✅ ACCEPTED+TO-FIX | 02-Q-secrets-rotation (гибрид) .env.prod.example |
| 15 | C0-10 LE cert-name | ✅ TO-FIX | 02-Q-le-cert (a) rename + force-renewal |
| 15 | C1-1 3 STOMP-клиента | ✅ TO-FIX | QC1 (a) unified NotificationCenter |
| 15 | C1-2 Type drift фронт↔backend | ✅ TO-FIX | QC2 (b) openapi-typescript + openapi-fetch |
| 15 | C1-3 `:latest` теги | ✅ TO-FIX | QD1 (b) IMAGE_TAG=${sha} + semver |
| 15 | C1-4 Coverage-gate | ✅ TO-FIX | QD2 (b) JaCoCo+Vitest+pytest-cov 60/50/50 + diff 80% |
| 15 | C1-5 Contract-тесты событий | ✅ TO-FIX | QD3 (a) schema validation для всех 14+ events |
| 15 | C1-6 Base images без digest | ✅ TO-FIX | QD4 (гибрид) digest для cadvisor/promtail + tag+Renovate |
| 15 | C1-7 @Scheduled без ShedLock | ✅ TO-FIX | 03-Q-P0-4 (a) ShedLock + NEW-28 audit + QE2 ArchUnit rule |
| 15 | C1-8 Лендинг v9.0-рассинхрон | ✅ TO-FIX | QE1 (a) PR-template checklist |
| 15 | C1-9 latecheckin/callback_query тесты | ✅ TO-FIX | 14-Q-P0-1/-2 / Q19e, Q19f |
| 15 | C1-10 DEBUG-логи + JWT в query | ✅ TO-FIX | QA1 (a) INFO + QE5 audit + C0-7 ws-ticket |
| 15 | C1-11 GlobalExceptionHandler notification | ✅ TO-FIX | 05-Q-P0-2 (b) shared-web module |

## 16-nit-backlog

Отчёт 16 — это консолидированный срез всех 110 P3-пунктов из 13 отчётов (01-10, 12-14), сгруппированных по 16 темам (A-P). По плану владельца P3 разбираются одной пачкой отдельным workflow, не входящим в этот аудит. Каждая из 16 тем (A..P) имеет собственный план уборки в самом отчёте 16 (разделы «Как закрывать пачкой»). **Coverage по P3 считается полным** — весь отчёт 16 сам является ответом для P3 в своём собственном scope.

- Тема A (мёртвый код, 16 п.) → P2-5/4 + ArchUnit/qodana rule
- Тема B (TODO/FIXME, 5 п.) → P3-уборка в 16
- Тема C (naming/стиль, 13 п.) → P3-уборка
- Тема D (hardcoded константы, 10 п.) → P2-5/2 units audit + P3
- Тема E (логирование, 4 п.) → P2-6/1 / QA7
- Тема F (HATEOAS/REST, 6 п.) → P3-уборка (P2-1 preamble)
- Тема G (error handling, 6 п.) → P2-3 shared-web
- Тема H (производительность, 7 п.) → P2-10 + P3
- Тема I (event schemas/proto, 7 п.) → P2-11 + QD3
- Тема J (тесты, 13 п.) → P2-8 + QD2
- Тема K (Docker/compose, 6 п.) → P2-9
- Тема L (cross-service, 5 п.) → P3-уборка
- Тема M (frontend UX, 10 п.) → P2-7A
- Тема N (HTML/A11y, 3 п.) → P2-7B
- Тема O (Python/CI, 5 п.) → P2-3/7 + P2-12/6
- Тема P (misc, 2 п.) → P3-уборка

---

## Итог

**Всего проверено пунктов:** 354 (28+36+33+26+24+21+22+17+36+40+17+28+26 + 21 кластеров из 15).

**Разбивка по статусам:**
- ✅ TO-FIX — явный план фикса.
- ✅ ACCEPTED — принято как есть (accept tradeoff / by design).
- ✅ DISSOLVED — переклассифицировано/распущено (C0-2, 12 P1-4 SRI, 12 P2-2 preconnect).
- ✅ AUTO-RESOLVED — закрывается другим фиксом (большинство P2-пунктов автоматически закрываются в ходе крупных фиксов shared-web, C0-1, C0-7, P2-10 performance, P2-8 test-infrastructure).
- ❌ REJECTED — 13 P1-3 (rate-limit в nginx отклонён в пользу Spring Cloud Gateway + Redis).

**Неразобранных пунктов:** 0 — все 354 пункта имеют явный ответ (Q-ID, P2-группу, auto-resolve-цепочку, ACCEPTED или DISSOLVED-отметку).

**Готовность к разметке отчётов:** 100%. Можно переходить к шагу 2 (разметка) и шагу 3 (99-executive-summary.md).
