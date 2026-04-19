# Прогресс аудита

Старт: 2026-04-17

| # | Файл | Статус | Заметки |
|---|------|--------|---------|
| 00 | PLAN.md | ✅ готов | план зафиксирован, язык русский |
| -- | PROGRESS.md | ✅ живой | обновляется по ходу |
| 01 | 01-auth-service.md | ✅ готов | P0=6, P1=10, P2=12, P3=9. Главное: нет contract-модуля, общая БД с academic, initial_password в plaintext, OTP-код в теле HTTP, timing-атака, DoS через login rate-limiter |
| 02 | 02-academic-service.md | ✅ готов | P0=7, P1=14, P2=15, P3=11. initial_password отдаётся через REST+gRPC, UserContextFilter доверяет X-User-Id, @RequireRole аспект не активирован явно, race в activateSemester, hard-delete групп, AFTER_COMMIT без outbox |
| 03 | 03-schedule-service.md | ✅ готов | P0=5, P1=12, P2=16, P3=10. UserContextFilter доверяет X-User-*, AFTER_COMMIT без outbox, @Scheduled без ShedLock (double-publish), дрейф week-parity между LessonGenerationService и OneOffLessonService, NumberFormatException → 500 |
| 04 | 04-attendance-service.md | ✅ готов | P0=6, P1=11, P2=9, P3=7. UserContextFilter доверяет X-User-*, координаты геоотметки не сохраняются (теряется anti-spoof след), @RabbitListener падает в DLQ без retry при падении gRPC, @PostConstruct gRPC silent fallback, latecheckin/ БЕЗ тестов, изоляция report/checkin — ОК |
| 05 | 05-notification-service.md | ✅ готов | P0=5, P1=9, P2=10, P3=7. UserContextFilter доверяет X-User-* (можно угнать чужие push'ы), нет GlobalExceptionHandler (500 вместо RFC 7807), push_subscriptions в attendance_db, STOMP-interceptor blacklist а не whitelist, reminders (3 штуки) НЕ реализованы в Java |
| 06 | 06-notification-bot.md | ✅ готов | P0=3, P1=8, P2=10, P3=8. gRPC к academic/schedule через insecure_channel без TLS, grpc_secret="" default, initial_password идёт plaintext в Telegram без self-destruct, reminders in-memory (теряются при рестарте), excuse/late-checkin callback'и НЕ проверяют роль старосты (любой может approve), 0% теста callback-хендлеров |
| 07 | 07-api-gateway.md | ✅ готов | P0=2, P1=9, P2=11, P3=7. Gateway вырезает X-User-* заголовки (единственная защита downstream), но прод-CORS настроен на rutcampustrack.ru (реальный домен ruttrack.site), /api/auth/otp/** и /api/ws/** публичны без JWT, rate-limiting ОТСУТСТВУЕТ везде, publicKey init не fail-fast при падении auth |
| 08 | 08-shared-proto-events.md | ✅ готов | P0=2, P1=7, P2=8, P3=5. initial_password в UserByTelegramIdResponse.proto:155 — канал утечки, отсутствует схема otp.requested, везде string вместо google.protobuf.Timestamp и enum, ни одна json-схема не имеет additionalProperties:false, нет версионирования |
| 09 | 09-frontend-pwa.md | ✅ готов | P0=5, P1=14, P2=17, P3=11. JWT в localStorage (XSS-вектор), JWT в WS query string, нет ролевых guards в роутере, SW cache `headman-api-cache-v1` не чистится на logout (cross-user leak на общих устройствах), push-subscription не отвязывается на logout, StatsPage N×2 запросов без агрегата |
| 10 | 10-frontend-web-panel.md | ✅ готов | P0=4, P1=16, P2=20, P3=12. Те же JWT/WS issues что и PWA, нет CSP/security headers в nginx, sessionStorage notifications не чистится на logout (cross-user leak), 3 параллельных STOMP-клиента, window.prompt для cancel-reason, admin-dashboard sparklines — псевдо-данные, initialPassword показывается в admin-таблице |
| 11 | 11-frontend-mini-app.md | ⏭ пропущен | пользователь ещё не доделал; PWA-код будет скопирован туда в будущем |
| 12 | 12-frontend-landing.md | ✅ готов | P0=2, P1=6, P2=9, P3=7. CSP корневого nginx блокирует все CDN лендинга (Fontshare/Google Fonts/unpkg/jsdelivr GSAP) — страница в проде сломана визуально; «Открыть в Telegram» ведёт на /login web-panel, а не в бота; нет og:image/twitter:card/robots/canonical; нет SRI на CDN; SMIL-анимация hero не уважает prefers-reduced-motion; excuse-тикеты описаны по старому flow (v9.0 Phase 59 перевёл на backend) |
| 13 | 13-infra-docker-ci.md | ✅ готов | P0=4, P1=11, P2=13, P3=8. init-letsencrypt.sh выпускает сертификат под `--cert-name rutcampustrack`, а `default.conf:24-25` ожидает в `/live/ruttrack.site/` → несоответствие путей. CI и `deploy.yml` — независимые push-триггеры (красный CI не блокирует выкат). `.env.prod` с реальными prod-секретами лежит в рабочей копии (гитигнорирован). CSP корневого nginx ломает лендинг. `:latest` теги в prod-compose делают откат невозможным |
| 14 | 14-tests-audit.md | ✅ готов | P0=2, P1=9, P2=15, P3=8. `attendance-service/latecheckin/` — полный домен без тестов. Callback_query-хендлеры бота (`excuse`, `late_checkin`, `prefs`) — 0 unit-тестов. Нет coverage-gate в CI (ни JaCoCo, ни vitest --coverage, ни pytest-cov). Нет contract-тестов Gateway↔downstream (UserContextFilter bypass не гарантирован тестом). Contract-тест RabbitMQ есть только для excuse; остальные 14+ событий без сверки со схемами |
| 15 | 15-cross-cutting-issues.md | ✅ готов | 10 P0-кластеров (C0-1..10), 11 P1-кластеров (C1-1..11), dependency graph, порядок исполнения, метрики после фиксов (~30 P0 закрывается кластерами из 53, ~18 P1 из 136). 510 строк. |
| 16 | 16-nit-backlog.md | ✅ готов | Полный срез всех 110 P3 по 16 тематическим группам (A-P). P4 нет в классификации проекта. Оценка уборки: 3-4 человеко-дня одной сессией. 244 строки. |
| 99 | 99-executive-summary.md | ✅ готов | TL;DR roadmap v0.0.0, 6 фаз, estimate 72-96 чел.-дней, dependency graph, архитектурные изменения (4 shared-модуля + alertmanager + notification-web stateful), список ACCEPTED/DISSOLVED/REJECTED |
| -- | COVERAGE-AUDIT.md | ✅ готов | Сверка 354 пунктов (P0/P1/P2) отчётов 01-16 против OWNER-ANSWERS.md. 100% покрытие — неразобранных gaps нет. 1 REJECTED (13 P1-3 rate-limit в nginx), 3 DISSOLVED, ~2/3 AUTO-RESOLVED через крупные фиксы |

Легенда: ⬜ не начат · ⏳ в работе · ✅ готов · ⚠ с пометкой · ⏭ пропущен

---

## 🎉 Handoff — 2026-04-19 (девятая сессия, ФИНАЛ АУДИТА)

**Цель сессии:** верификация покрытия ответов + 99-executive-summary.md.

**Шаги выполнены:**
1. ✅ **Coverage verification.** Сверены все **354 пункта** (28+36+33+26+24+21+22+17+36+40+17+28+26 + 21 кластер из 15) отчётов 01-16 против `OWNER-ANSWERS.md`. Результат — `COVERAGE-AUDIT.md` (~430 строк). **100% покрытие:** каждый пункт имеет явный ответ (Q-ID, P2-группу, AUTO-RESOLVED через другой фикс, ACCEPTED или DISSOLVED). Неразобранных gaps нет.
2. ⏭ **Разметка отчётов 01-16 построчно — ПРОПУЩЕНА по решению владельца** (экономия токенов). `COVERAGE-AUDIT.md` служит единой точкой правды статусов. Частичная разметка 01-auth-service.md (P0-1..P0-3 счётчик + P1-1..P1-10 + P2-1..P2-12 + Зависимости + Вопросы 2-5) остаётся как образец формата — откат не делался.
3. ✅ **99-executive-summary.md написан.** 6 фаз roadmap v0.0.0 (Инфра-подготовка → Shared-модули → P0-кластеры → Точечные P0 → P1-пачки → P2-группы → P3-уборка). Estimate 72-96 человеко-дней для одного разработчика (~3-4 месяца). Полный dependency graph, список ACCEPTED/DISSOLVED/REJECTED, индекс 178 NEW-задач по категориям, список 30+ новых документов.

**Следующий шаг:** финальный коммит со всеми артефактами (01-10 + 12-16 + 99 + COVERAGE-AUDIT + PROGRESS + OWNER-ANSWERS + NEXT-SESSION-PROMPT + future-ideas).

---

## 🎉 Handoff — 2026-04-19 (восьмая сессия, ФИНАЛ — ВЕСЬ ОПРОС ЗАКРЫТ)

**Прогресс опроса владельца:** 194 + 7 P2-12 = **201 / ~234** (исходная оценка 306 была завышенной — реальный счёт после consolidated пачек ~234). **Весь P0+P1+P2 разобран.** NEW-задач: **178** (неизменно — P2-12 расширял existing).

**Группа P2-12 (Misc / финальная метла) — ЗАКРЫТА:**
- P2-12/1 → **(a)** `.env.prod.example` add DOMAIN + CERTBOT_EMAIL, init-letsencrypt.sh явный error. Связка NEW-20+23.
- P2-12/2 → **(a)** defense-in-depth `@RequireRole(ADMIN)` на service-layer для admin-mutating методов. ArchUnit +1 rule. NEW-31 тест расширяется.
- P2-12/3 → **(a)** `@CreationTimestamp`/`@UpdateTimestamp` audit × 5 сервисов. ArchUnit +1 rule.
- P2-12/4 → **(a)** LessonService WeekType no-match: javadoc + golden-test (P2-8/4) + architecture.md раздел.
- P2-12/5 → **(a)** JWT public key `@Scheduled(cron="0 */5 * * * *")` + ShedLock (NEW-28) + 1-мин local TTL = propagation ≤6 мин. NEW-155 (secret-rotation) +section.
- P2-12/6 → **(a)** Testcontainers Python для Redis (fakeredis replacement, Lua/pub-sub support). NEW-53+158 parallel.
- P2-12/7 → **(a)** Reminders остаются в Python (APScheduler + in-memory). pytest + freezegun tests. Migration в Java → v0.1.

**Суммарный estimate P2-12:** ~10 часов. +2 ArchUnit rules. Auto-resolves: 01 P2-9, 02 P2-7/8, 03 P2-2, 13 P2-5, 14 P2-3/10.

---

## 🎉 ВЕСЬ ОПРОС ВЛАДЕЛЬЦА ЗАКРЫТ

**Итоги:**
- **P0** (53): все закрыты (10 P0-кластеров C0-1..10, кроме распущенного C0-2 DISSOLVED; 6 точечных групп 13-19)
- **P1** (136): все закрыты (5 пачек P1-A..E, 33 вопроса)
- **P2** (165): все закрыты (12 групп, 79 consolidated вопросов)
- **Всего NEW-задач:** 178 (NEW-1..178)
- **Meta-решения:** M1 (не юрисдикция РФ, 152-ФЗ не применяется), M2 (весь P2 в v0.0.0, не backlog)

**Ключевые архитектурные изменения:**
- Новый модуль `shared-web` (Q16a) — GlobalExceptionHandler + validation + masking + OpenApi customizer
- Новый модуль `shared-events` (NEW-60) — общий DomainEvent base
- Новый модуль `shared-test-containers` (NEW-158) — Testcontainers fixtures
- Новый модуль `shared-logback` (NEW-68) — JSON + MaskingConverter
- **Новый контейнер `alertmanager`** (P2-9/5) — unified router Prometheus + Loki
- **notification-web stateful** (P2-6/4) — own MongoDB `notification_db` (ACTIVATED)
- `C0-3` outbox pattern (`shared-outbox` или per-service)
- `C0-7` JWT HttpOnly cookie + ws-ticket
- `C0-6` CSP self-host лендинга
- `QC2` openapi-typescript type-gen
- `QC1` unified NotificationCenter (Angular + React)
- `QD2` coverage-gate 60/50/50 + diff 80%
- `QD3` contract-тесты 14+ events

**Суммарный estimate всех фиксов:** v0.0.0 требует ~60-80 человеко-дней сверх уже готового v9.0 (25-35 P0-кластеры + ~15 P1 пачки + ~30 P2 + 3-4 дня P3-уборка).

---

## Следующие шаги

1. **Разметка отчётов 01-16** (вариант A — один проход, строго ПОСЛЕ всех ответов):
   - Галочки `✅ AUTO-RESOLVED`, `🔧 TO-FIX через X`, `✅ ACCEPTED`, `✅ DISSOLVED` на каждой проблеме.
   - Обновление счётчиков в PROGRESS.md.
2. **99-executive-summary.md** — финальный отчёт:
   - TL;DR roadmap до v0.0.0.
   - Dependency graph между кластерами (уже в 15-cross-cutting).
   - Estimate в человекоднях.
   - Приоритизация: что блокер, что в v0.1+, что в backlog.
3. **Финальный коммит** — все отчёты 01-16 + 99 + PROGRESS + OWNER-ANSWERS одним коммитом.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-5)

**Прогресс опроса владельца:** 184 + 10 P2-5 = **194 / 306** разобрано. NEW-задач: **178** (было 176).

**Группа P2-5 (Hardcoded constants / Dead code / Cleanup) — ЗАКРЫТА:**
- P2-5/1 → **(a)** 204 No Content для void endpoints × 5 сервисов. Связка NEW-123 conformance test.
- P2-5/2 → **(a)** rename `expiresIn` → `expiresInSeconds` + audit всех time-fields (units в имени/@Schema).
- P2-5/3 → **(a)** TMA_BOT_TOKEN через @ConfigurationProperties + @NotBlank fail-fast. Consistency с Q13c.
- P2-5/4 → **(a)+(b)** удалить dead-code repository methods + ArchUnit/qodana CI inspection.
- P2-5/5 → **(a)** admin `PUT /campus-settings` endpoint, GroupNameParser regex tighten, firstWeekType cross-field validator (P2-4/1 pattern).
- P2-5/6 → 02 P2-14 расширить cache evict + **02 P2-15 AUTO-RESOLVED через C0-3 outbox** + 03 P2-10 event schema `lesson.blocked.blocked_by_user_id`.
- P2-5/7 → **(a)** DLQ x-message-ttl 7d + x-max-length 10k + Alertmanager alert. V8/V9 historical note в database-schema.md.
- P2-5/8 → **(a)** Gateway config cleanup одной пачкой: show-details=when_authorized (prod), prometheus basic-auth (консистентно с swagger P2-2/6), auth-service-url через ConfigurationProperties, 07 P2-5 AUTO через C0-7 cookie, remove legacy springdoc flag. NEW-177 (gateway-config.md).
- P2-5/9 → **(a)** Notification ops: VAPID public (remove @RequireRole), dedup @Valid, Strategy-pattern NotificationTemplate per EventType, Micrometer counters, setRole graceful. NEW-178 (notification-template-catalog.md).
- P2-5/10 → **(a)** IsoParityReconciler `INSERT ... RETURNING` + Optional<Long>, `@Profile("prod|dev")` вместо inverse. **04 P2-3/5/8 AUTO-RESOLVED** через P2-3/8, P2-3/1, C0-1.

**Суммарный estimate P2-5:** ~20 часов (~2.5 человеко-дня). Новые доки: gateway-config.md, notification-template-catalog.md. +1 ArchUnit/qodana rule для dead code.

**Auto-resolves (много):** 01 P2-10/11/12, 02 P2-9/10/11/12/14/15, 03 P2-3/4/5/7/10/11/14/16, 04 P2-3/5/8, 05 P2-2/3/5/6/8/10, 07 P2-1/2/3/5/7/8/9/10/11.

**Остаток: ~25 вопросов** — только **P2-12 (Misc / остаточная метла)**. После неё — разметка отчётов + 99-executive-summary.md.

**Следующая группа:** **P2-12 (Misc)** — заключительная.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-1)

**Прогресс опроса владельца:** 176 + 8 P2-1 = **184 / 306** разобрано. NEW-задач: **176** (было 175).

**Группа P2-1 (Contract quality / Type-safety / Serialization) — ЗАКРЫТА:**

Примечание: исходная тема «P2-1 HATEOAS» оказалась почти пустой — HATEOAS-недочёты ушли в P3 (тема F в 16-nit-backlog, 6 пунктов, закроются пачкой P3). Группа P2-1 получила другую тематику — contract quality / type-safety из audit-пунктов разных сервисов.

- P2-1/1 → **(a)** `@JsonProperty("isHeadman")` на boolean getter + audit `boolean is*` в response DTO × 5 сервисов + ArchUnit rule.
- P2-1/2 → **(c)** explicit `Jackson2JsonRedisSerializer<T>` per-cache, удалить `activateDefaultTyping(NON_FINAL)`. Связка с NEW-45 (redis-keyspace.md).
- P2-1/3 → **(a)** `Semester.firstWeekType` String→WeekType, `HeadmanAssistant.permissions` String[]→List<AssistantPermission>, активация converter. CLAUDE.md enum-правила — regression guard.
- P2-1/4 → **(a)** `@JsonInclude(NON_NULL)` на JWT claims — smaller payload + no null-vs-undefined ambiguity в TS.
- P2-1/5 → **(a)+(b)** remove `@JsonIgnoreProperties({source,timestamp})`, добавить common DomainEvent base fields (event_version, trace_id, occurred_at, source) + `FAIL_ON_UNKNOWN_PROPERTIES=false` globally. Связка NEW-100/101/60/61.
- P2-1/6 → **(a)** reject duplicate keys в parseQueryString (TMA). Связка P2-8/8 security contract-test.
- P2-1/7 → **(a)** LowercaseEnumConverter graceful: log.warn + Prometheus counter `unknown_enum_total{enum_class,value}` + return null. Consistent с P2-4/8 Jackson strategy. Alert через P2-9/5.
- P2-1/8 → **(a)** Lombok conventions для entity/document: `@EqualsAndHashCode(onlyExplicitlyIncluded=true) + @Include на @Id`, удалить `@Data` из entity × 5 сервисов. NEW-176 (java-conventions.md). +1 ArchUnit rule «@Data banned на @Entity/@Document».

**Суммарный estimate P2-1:** ~10-12 часов (~1.5 человеко-дня). +3 ArchUnit rules. Auto-resolves: 01 P2-5/6/7/8, 02 P2-2/3/5/6/13, 05 P2-9.

**Остаток: ~50 вопросов** (исходная оценка 306 скорректирована — реально P0+P1+P2 ≈ 234 + metas):
- ~25 вопросов в P2-5 (Hardcoded constants / dead code / cleanup)
- ~25 вопросов в P2-12 (Misc — security-specific, operational, cross-cutting)

**Следующая группа:** **P2-5 (Hardcoded constants / dead code)** — рефакторинг.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-7)

**Прогресс опроса владельца:** 164 + 8 P2-7A + 4 P2-7B = **176 / 306** разобрано. NEW-задач: **175** (было 169).

**Группа P2-7 (Frontend UX / a11y) — ЗАКРЫТА (обе подгруппы):**

**P2-7A (UX/flow, 8 вопросов):**
- P2-7A/1 → **(b)** `refetchOnWindowFocus: true` + staleTime 30s + pull-to-refresh hook на 5 страницах PWA.
- P2-7A/2 → **(a)** auto-scroll on `[week, currentTime]` + swipe threshold 100px + velocity 0.3. NEW-170 (useSwipeHandler).
- P2-7A/3 → **(a)** single source-of-truth `selectedDate` + configurable `windowDays` prop (default 14). NEW-171 (useDateNavigation hook/service).
- P2-7A/4 → **(a)+(b)** hard-stop на границах семестра + empty-state. Связка NEW-116.
- P2-7A/5 → **(a)** scroll-position preservation через Angular signal per-week key.
- P2-7A/6 → **(a)** forkJoin параллельно + Map для O(1) subject lookup. Связка QC6+P2-10/3.
- P2-7A/7 → **(a)** убрать «Главная» у headman (single entry-point), убрать DrawerMenu из PWA (2 пункта в BottomNav). NEW-172 (frontend-navigation.md).
- P2-7A/8 → **(a)+(c)** `enableHighAccuracy: true` + timeout 15s + loading indicator «Уточняем геолокацию…» + error-specific messages. NEW-173 (geofencing.md).

**P2-7B (a11y, 4 вопроса):**
- P2-7B/1 → **(a)** полный audit `<div role="button">` → `<button>` в PWA и web-panel (~25 мест) + `any[]` → typed DTO через QC2. ESLint jsx-a11y + @angular-eslint/template-accessibility. NEW-174 (a11y-checklist.md).
- P2-7B/2 → **(a)+(c)** manual ARIA audit (theme-toggle, icon-only, live-regions, dialogs, forms) + axe-core в Playwright (P2-8/5) + vitest-axe в unit. NEW-175 (axe-core setup).
- P2-7B/3 → **(b)** SMIL → CSS keyframes + `@media (prefers-reduced-motion)`. Замыкает QE3 полностью. NEW-110 повышен в v0.0.0 scope.
- P2-7B/4 → **(a)+(c)** neutral-placeholder вместо hardcoded дат/процентов + `min-width: 0` grid-children + breakpoint single-column на 1024-1280px.

**Суммарный estimate P2-7:** ~5.5-7 человеко-дней (P2-7A ~3д, P2-7B ~3-4д). Новые hooks/services: pull-to-refresh, useSwipeHandler, useDateNavigation. Новые правила линта: eslint-plugin-jsx-a11y, @angular-eslint/template-accessibility, stylelint prefers-reduced-motion rule. Axe-core integration в Playwright+vitest. Auto-resolves: 09 P2-1/2/7/9/13/14/16/17, 10 P2-1/5/6/7/14/15/16, 12 P2-3/5/7/8.

**Изменения в ранее принятых:** NEW-110 (stylelint a11y) повышен из v0.1 в v0.0.0 scope.

**Остаток: 130 вопросов** по 3 группам (P2-1, P2-5, P2-12).

**Следующая группа:** **P2-1 (HATEOAS)** — большая, относительно изолированная.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-6)

**Прогресс опроса владельца:** 125 + 8 P2-4 + 8 P2-10 + 9 P2-9 + 8 P2-8 + 6 P2-6 = **164 / 306** разобрано. NEW-задач: **169** (было 164).

**Группа P2-6 (Логи-нюансы) — ЗАКРЫТА:**
- P2-6/1 → **(c) hybrid** — ручной audit (01 P2-2, 05 P2-1, 07 P1-4: whitelisted kv-поля) + Logback MaskingConverter в shared-logback (NEW-68) как safety-net. Regex для Bearer/telegram_id/FCM endpoint. NEW-165 (logging-conventions.md).
- P2-6/2 → **(a)** Auth-failure WARN + `AuthFailureReason` enum (EXPIRED/INVALID_SIGNATURE/MALFORMED/MISSING/REVOKED/UNSUPPORTED) + ip + path. Loki rule в Alertmanager (P2-9/5) для brute-force detection по ip-rate.
- P2-6/3 → ✅ **AUTO-RESOLVED через Q15b** (удаление `@PostConstruct cleanupOrphans`).
- P2-6/4 → **(b) FULL + АРХИТЕКТУРНОЕ ИЗМЕНЕНИЕ** — notification-web становится stateful: MongoDB `notification_db` (ACTIVATED, отменяет P2-9/6 reserved), коллекция `notification_history` с TTL 30д. Read/unread tracking (PATCH single + bulk mark-all-read). Separate `NotificationHistoryConsumer` (decoupled от delivery). Frontend мигрирует с sessionStorage на backend pagination + unread badge через Caffeine cache (P2-10/3). NEW-166 (schema), NEW-167 (OpenAPI spec), NEW-168 (CLAUDE.md update).
- P2-6/5 → **(b)** nginx JSON log_format (time/method/path/status/rt/urt) + Promtail pipeline + Grafana dashboard «Nginx latency & errors». Consistency с QA7. NEW-169 (Promtail pipeline docs).
- P2-6/6 → ✅ **AUTO-RESOLVED через P2-9/4** (14д retention ACCEPTED).

**Суммарный estimate P2-6:** ~5-6 человеко-дней (основная часть — P2-6/4 notification persistence 4-5д).

**Архитектурные изменения (КРИТИЧНО):**
- **notification-web** перестаёт быть «stateless event forwarder» — получает свою MongoDB. CLAUDE.md обновляется (NEW-168).
- **P2-9/6 ПЕРЕОПРЕДЕЛЕНО:** notification_db reserved → **ACTIVATED**.
- **NEW-36** (shared-DB patterns в architecture.md) расширяется: notification-web ВЛАДЕЕТ notification_db, push_subscriptions остаются в attendance_db (Q16b).

**Auto-resolves:** 01 P2-2, 05 P2-1, 07 P1-4, 07 P3-1, 13 P3-6, 13 P2-9, 04 P2-6, 09 P2-13, 10 P2-13.

**Остаток: 142 вопроса** по 4 группам (P2-7, P2-1, P2-5, P2-12). После P2 — разметка отчётов + 99-executive-summary.md.

**Следующая группа:** **P2-7 (Frontend UX / a11y)** — большая группа ~30 пунктов, разбивается на 2 подгруппы.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-4+P2-10+P2-9+P2-8)

**Прогресс опроса владельца:** 125 + 8 P2-4 + 8 P2-10 + 9 P2-9 + 8 P2-8 = **158 / 306** разобрано. NEW-задач: **164** (было 157).

**Группа P2-8 (Test gaps) — ЗАКРЫТА:**
- P2-8/1 → **(b)** переименование + Gradle task-split (`test` = unit, `integrationTest` = *IT) + ArchUnit rule. NEW-109 +1 правило.
- P2-8/2 → **(b) hybrid** Testcontainers для БД/Rabbit (real), gRPC in-process + stub, WireMock для HTTP. Refactor 36 @MockitoBean мест. NEW-158 (shared-test-containers модуль).
- P2-8/3 → **(a)+(b) частично** MigrationIT в schedule (fresh install + checksum consistency) + data-preservation тесты для P1-миграций (QB1 soft-delete). Memory `feedback_flyway_no_edit` → regression guard. NEW-159 (migration-testing.md).
- P2-8/4 → **(a)+(b)** golden JSON fixtures × 104 кейса (week-parity) + jqwik property-based для corner-cases + Clock-injection refactor для CheckinService. Auto-resolve 04 P2-4. ФИО golden (NEW-117). NEW-160 (golden-tests.md).
- P2-8/5 → **(a)+(c)** Playwright × 4 critical flows (auth, headman-mark, student-excuse, admin-create-user) в CI + shell smoke-scripts post-deploy. Landing accept. NEW-161 (e2e-testing.md).
- P2-8/6 → **(a) частично** Vitest critical hooks (useAuth, useErrorInterceptor, useNotificationCenter, useConfirmWithReason, useGroupMembers) + critical components (HeadmanLessonSheet, CheckInButton) + SW cache logout purge. Mini-app accept. NEW-162 (критичные frontend units).
- P2-8/7 → **(c)+minimal (a)** k6 × 2 scenarios (bulk-mark, geolocation flood) manual pre-release + baseline файл. Полный load-suite → v0.1. NEW-163 (load-testing.md + performance-baseline.md).
- P2-8/8 → **(a)** 3 security contract-теста: GRPC_SECRET fail-fast startup (Q13c), TMA HMAC invalid signature (06 P0-5), CSRF double-submit (C0-7). Расширяет SecurityIdorIT до SecurityContractsIT. NEW-164.

**Суммарный estimate P2-8:** ~10-12 человеко-дней (~80-100 часов). Новый модуль `shared-test-containers`. Новые test-suites: Playwright e2e, k6 load, SecurityContractsIT. Auto-resolves: 04 P2-4, 09 P2-6/11. ACCEPT: 14 P2-12/15.

**Остаток: 148 вопросов** по 5 группам (P2-6, P2-7, P2-1, P2-5, P2-12). После P2 — разметка отчётов + 99-executive-summary.md.

**Следующая группа:** **P2-6 (Логи-нюансы)** — связка с QA7 structured logs, NEW-113/114.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-4+P2-10+P2-9)

**Прогресс опроса владельца:** 125 + 8 P2-4 + 8 P2-10 + 9 P2-9 = **150 / 306** разобрано. NEW-задач: **157** (было 149).

**Группа P2-9 (Docker/compose nits) — ЗАКРЫТА:**
- P2-9/1 → **(a)** HEALTHCHECK в Dockerfile + curl в alpine для всех 5 backend-сервисов + compose `depends_on.service_healthy`. NEW-150 (dockerfile-conventions.md).
- P2-9/2 → **(a+b) mixed** semver+Renovate для Prometheus/Grafana/Loki (auto-merge patch), digest для cadvisor/promtail (QD4). NEW-151 (loki-major-upgrade runbook).
- P2-9/3 → **(a)** nginx per-location client_max_body_size: default 2m, excuse 25m, avatar 5m. Связка с P2-4/6 @ValidFile. NEW-152 (nginx-config.md checklist).
- P2-9/4 → **(c) ACCEPT** 14д Loki retention (консистентно с QA5). Grafana alert на аномальные скачки объёма.
- P2-9/5 → **(c)** НОВЫЙ КОНТЕЙНЕР `alertmanager:v0.27.0` как unified router. Prometheus→AM→bot webhook, Loki ruler→AM→bot webhook. Grouping/silencing/inhibition в AM, не в боте. **Меняет QA4+NEW-62** (bot webhook payload schema = Alertmanager format). NEW-113 (JWT-в-логах) реализуется как Loki-rule. NEW-153 (alerts.md расширение), NEW-154 (bot webhook schema migration).
- P2-9/6 → **(b) KEEP** notification_db init оставить как reserved для v0.1+ own-DB миграции. Только комментарий.
- P2-9/7 → **(b) ACCEPT** shared POSTGRES_ACADEMIC_PASSWORD для auth/academic/schedule, ротация = одновременный deploy (раз в квартал). Blast-radius mitigation через docker network isolation, не через per-user GRANTs. NEW-155 (secret-rotation runbook).
- P2-9/8 → **(b) ACCEPT** dev `.env` одинаковые пароли (developer convenience), RBAC тестирование через Testcontainers с уникальными creds per-run. NEW-156 (testing.md).
- P2-9/9 → **(c)** JVM `MaxRAMPercentage=75` + `deploy.resources.limits` (backend 512M/256M, БД accept self-tune), `restart: unless-stopped` везде. VPS budget ~4GB tight. Alertmanager → memory usage alerts. NEW-157 (resource-limits.md).

**Суммарный estimate P2-9:** ~4-5 человеко-дней (~32-40 часов). Новый контейнер `alertmanager`. Auto-resolves: 05 P2-1, 07 P2-6, 13 P2-2/3/6/9/10/11/12. ACCEPT: 13 P2-6/9/11/12. Изменения в ранее принятых: QA4+NEW-62 payload теперь Alertmanager webhook format, NEW-64 «тихий час» → AM `mute_time_intervals`, NEW-65 baseline → AM silencing.

**Следующая группа:** **P2-8 (Test gaps)** — связка с QD2 coverage-gate.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-4+P2-10)

**Прогресс опроса владельца:** 125 + 8 P2-4 + 8 P2-10 = **141 / 306** разобрано. NEW-задач: **149** (было 137).

**Группа P2-10 (Performance hotspots) — ЗАКРЫТА:**
- P2-10/1 → **(a)** composite indexes миграциями (schedule `idx_lessons_group_dates`, unique oneoff dedup, attendance late-checkin index, academic user_groups). EXPLAIN before/after документируется. NEW-142 (performance-indexes.md).
- P2-10/2 → **(a+c) mixed** `@EntityGraph` для list-endpoints + DTO projection для single-detail screens. Связка с 02 P0-7. NEW-143 (ArchUnit-правило «repo collection → Pageable/EntityGraph/projection»).
- P2-10/3 → **(a)** Caffeine `@Cacheable` (semester 5мин, subject/group 10мин, rbac 1мин). Single-instance ok для v0.0.0, миграция на Redis при scale-out (v0.1). Watch-out: Q13b race — TTL короче admin-action. NEW-144 (caching-strategy.md).
- P2-10/4 → **(c)** backend batch endpoints + frontend один вызов. `POST /api/attendance/marks/batch` атомарный, homework batch partial-success (207 Multi-Status). NEW-145 (batch conventions в api-error-conventions.md).
- P2-10/5 → **(a)** SQL-aggregate вместо in-memory stream для всех отчётов. DTO projection через JPQL `@Query("SELECT new StatsDto(...) GROUP BY...")`. Связка с QC6/QC7. NEW-146 (audit checklist `.collect(toList())` → aggregate).
- P2-10/6 → **(c)** HikariCP=20 для academic/schedule/attendance (явный yml), Mongo/Redis defaults ok. Prometheus alert на pool_usage>80%. NEW-147 (connection-pool-tuning.md).
- P2-10/7 → **(a)** @Scheduled weekly cleanup push_subscriptions + `last_seen` tracking, ShedLock (NEW-28). Audit refresh-token TTL=7d. NEW-148 (data-retention-policy.md).
- P2-10/8 → **(c)** cache (P2-10/3) + CompletableFuture для CheckinService + grpc-micrometer метрики. NEW-149 (audit gRPC deadlines, CI-lint required).

**Суммарный estimate P2-10:** ~5-6 человеко-дней (~40-48 часов). Новые доки: `performance-indexes.md`, `caching-strategy.md`, `connection-pool-tuning.md`, `data-retention-policy.md`. +1 ArchUnit rule, +1 CI-lint. Auto-resolves: 02 P2-3..7, 03 P2-2/4/5/6/7/8/9, 04 P2-1/2/9, 05 P2-7, 09 P2-11, 10 P2-14.

**Следующая группа:** **P2-9 (Docker/compose nits)** — надёжность прода.

---

## Handoff — 2026-04-19 (восьмая сессия, P2-4)

**Прогресс опроса владельца:** 125 + 8 P2-4 = **133 / 306** разобрано. NEW-задач: **141** (было 137).

**Группа P2-4 (Validation constraints) — ЗАКРЫТА:**
- P2-4/1 → **(b)** custom class-level аннотации (`@StartBeforeEnd`, `@DateRangeValid`) в новом пакете `shared-web/validation/`. Role-specific валидация `CreateUserRequest` остаётся в service-слое (вариант c). Фиксит 03 P1-10, 03 P1-2. NEW-138 (пакет shared-web/validation/).
- P2-4/2 → **(a)** полный audit format-patterns во всех request-DTO (OTP-code `^\d{6}$`, login pattern, telegram_id `@Positive @Max`, group/subject names `@Size`). Фиксит 01 P2-3. NEW-139 (checklist в api-error-conventions.md).
- P2-4/3 → **(a)** глобальный `spring.data.web.pageable.max-page-size: 100` во всех 5 yml. Связка с 02 P0-7, NEW-26.
- P2-4/4 → **(a)** `@Validated` на controller + `@Positive @PathVariable Long id` во всех 4 сервисах. ConstraintViolationException handler уже в P2-3/3.
- P2-4/5 → **(a)** Jakarta element constraints: `@NotEmpty @Size(max=100) List<@NotNull @Positive Long>` на lessonIds/teacherIds. DoS-защита.
- P2-4/6 → **(b)+(a) unified** — custom `@ValidFile(maxSizeBytes, allowedMediaTypes)` в shared-web + `@ConfigurationProperties("attendance.excuse")`. Убирает magic number из 04 P2-7. NEW-140 (magic-byte MIME check в future-ideas.md).
- P2-4/7 → **(a)+(b)** явные `@FutureOrPresent` где явная семантика (MassCancel dateFrom, OneOffLesson.date), accept retrofit для CreateSemester. Связка с QB5.
- P2-4/8 → **(c)** audit `@Valid` на nested DTO + `READ_UNKNOWN_ENUM_VALUES_AS_NULL` в shared-web ObjectMapper (**КРИТИЧНО:** требует `@NotNull` на всех enum-полях). NEW-141 (audit checklist).

**Суммарный estimate P2-4:** ~17.5 часов (~2.5 человеко-дня). Новый пакет `shared-web/validation/`. Auto-resolves: 01 P2-3, 03 P1-2, 03 P1-10, 04 P2-7.

**Следующая группа:** **P2-10 (Performance hotspots)** — индексы, критичные для прода.

---

## Handoff — 2026-04-19 (седьмая сессия, прогресс)

**Прогресс опроса владельца:** 117 + 8 P2-3 = **125 / 306** разобрано. NEW-задач: **137**.

**Группа P2-3 (Error handling edge-cases) — ЗАКРЫТА:**
- P2-3/1 → **(b)** catch-all `Exception` → detail="Обратитесь в поддержку, correlation=<trace_id>" + поле `traceId` в ErrorResponse. NEW-130 (incident lookup dashboard).
- P2-3/2 → **(b)** RFC 7807 B.2 `invalid-params[]` для validation (breaking change для существующих фронтов, ловится QC2 type-gen). NEW-131 (form validation mapping docs).
- P2-3/3 → **(a)** полный набор `@ExceptionHandler` в shared-web (9 стандартных Spring исключений). NEW-132 (api-error-conventions.md).
- P2-3/4 → **(c)** domain-specific exceptions в service-слое (DuplicateXxx...), handler маппит → 409. NEW-133 (audit всех DIVE).
- P2-3/5 → **(c) hybrid** STOMP: unauthorized → silent null, malformed → ERROR frame с RFC 7807. NEW-134 (websocket-protocol.md contract).
- P2-3/6 → **(a)** gateway ручная копия формата (WebFlux не подключать к shared-web). Utility `ErrorResponses.write(...)`.
- P2-3/7 → **(b)** Python-бот: TRANSIENT → requeue, PERMANENT → DLQ, prometheus counter. NEW-135 (back-off strategy), NEW-136 (DLQ recovery runbook).
- P2-3/8 → **(a)+(b)** audit пустых catch + ArchUnit/Checkstyle+ruff/flake8. NEW-137 (CI lint-empty-catch job).

**Следующая группа:** **P2-4 (Validation constraints)** — дешёвое покрытие, ловит данные на входе. Связка с QD2 coverage-gate.

---

## Handoff — 2026-04-19 (шестая сессия, прогресс)

**Прогресс опроса владельца:** 109 + 8 P2-2 = **117 / 306** разобрано.

**Группа P2-2 (OpenAPI аннотации) — ЗАКРЫТА:**
- P2-2/1 → **(a)** `OpenApiCustomizer` в shared-web: RFC 7807 ответы 4xx/5xx автоматом. NEW-122 (docs).
- P2-2/2 → **(a)** OpenAPI-аннотации в `AuthApi` interface (часть 01 P0-1).
- P2-2/3 → **(a) + (c)** правим runtime под контракт (204 для empty-body) + CI-тест «OpenAPI spec ↔ runtime» через swagger-request-validator. NEW-123 (CI job).
- P2-2/4 → **(a)** полный проход `@Schema(description, example)` по всем DTO (~60-80, 1 день). NEW-124 (ArchUnit/Checkstyle lint).
- P2-2/5 → **(a)** убрать конкретные `@Schema(example)` в `ErrorResponse` → generic placeholder. Входит в Q16a, отдельного estimate нет. 03 P2-12 → AUTO-RESOLVED.
- P2-2/6 → **(b)** nginx basic-auth для `/swagger-ui/**` и `/v3/api-docs`, `SWAGGER_PASSWORD` из `.env.prod` (владелец использует онлайн с телефона, аналог Grafana). NEW-125 (admin-access.md), NEW-126 (deploy regenerates .htpasswd). 13-Q9 → TO-FIX.
- P2-2/7 → **(a)** `@Tag(name, description)` на всех контрактных interface-ах, описания на русском. ~45 мин.
- P2-2/8 → **(c)** AsyncAPI spec для RabbitMQ + STOMP, static UI за nginx basic-auth (та же что Swagger). NEW-127 (manual vs auto-gen), NEW-128 (architecture.md event docs), NEW-129 (STOMP payloads = forward из Rabbit).

---

## Handoff — 2026-04-18 (пятая сессия, прогресс)

**Прогресс опроса владельца:** 101 / 141 исходных + 8 P2-11 = 109 разобрано (по факту больше, т.к. ответы порождают детализацию).

**Meta M2:** весь P2 (165 вопросов) в v0.0.0, не в backlog. Порядок групп: 11 → 2 → 3 → 4 → 10 → 9 → 8 → 6 → 7 → 1 → 5 → 12.

**Группа P2-11 (Event schemas) — ЗАКРЫТА:**
- P2-11/1 → (a) proto3 optional group_id. NEW-115.
- P2-11/2 → (c) current_semester_id optional. NEW-116.
- P2-11/3 → (a) разбиение ФИО + display_name_short. NEW-117 (формат).
- P2-11/4 → (a) комментарий в proto про cross-group.
- P2-11/5 → (a+b) `lesson.cancelled` с полным snapshot, `lesson.deleted` удалён. NEW-118 (lifecycle docs), NEW-119 (UI cancel-dialog через QC4).
- P2-11/6 → (b) accept roundtrip за group_members.
- P2-11/7 → (b) $defs в `_common.json`, все 14+ схем reference'ят. NEW-120.
- P2-11/8 → (a+c) `excuse.decision/approved/rejected` events, миграция с REST. NEW-121 (audit других asymmetric flows).

**Пачка P1-E (Remaining infra) — ЗАКРЫТА:**
- QE1 (landing revision process) → **(a)** PR-template checklist. NEW-108 (contributing.md).
- QE2 (ShedLock audit) → **(b)** ArchUnit-правило. NEW-109 (ArchUnit framework).
- QE3 (prefers-reduced-motion) → **(a)** CSS + JS. NEW-110 (stylelint a11y-rules v0.1).
- QE4 (meta-теги лендинга) → **(a)** og:image + canonical + robots + twitter-card. NEW-111 (JSON-LD v0.1), NEW-112 (og update при редизайне).
- QE5 (JWT в логах) → **(a+b частично)** Gateway filter + audit existing. NEW-113 (Loki-alert v0.1), NEW-114 (security-model.md logging hygiene).

**ВСЕ P1 ЗАКРЫТЫ (пачки A-E, 33 вопроса).**

---

## Следующая фаза — P2 bulk-обход

Владелец хочет пробежаться по темам P2 (~165 вопросов), отметить критичные для корректности/чистоты v0.0.0, остальное → v0.1 backlog. Без детализации — bulk-группы с recommendation'ами.

**Пачка P1-D (CI/CD) — ЗАКРЫТА:**
- QD1 (`:latest`) → **(b)** SHA + semver. NEW-96 (rollback runbook), NEW-97 (GHCR retention).
- QD2 (coverage) → **(b)** total gate 60/50/50 + diff-coverage ≥ 80%. NEW-98/99.
- QD3 (contract events) → **(a)** schema-validation тесты для всех 14+ events. NEW-100 (retrofit), NEW-101 (CI drift-guard).
- QD4 (digest pin) → **Гибрид** — digest для cadvisor/promtail, tag+Renovate для остального. NEW-102 (container-trust policy).
- QD5 (supply-chain) → **(a)** Trivy + Gitleaks + Dependabot. NEW-103 (SECURITY.md), NEW-104 (disclosure policy).
- QD6 (Renovate) → **(a)** auto-merge patch, manual minor/major. NEW-105 (docs/ci-cd.md).
- QD7 (release process) → **(b)** manual CHANGELOG + git-теги. NEW-106 (v0.1+ triggered migration to semantic-release), NEW-107 (retroactive [v0.0.0] entries).

**Пачка P1-C (Frontend reuse) — ЗАКРЫТА:**
- QC1 (3 STOMP) → **(a)** unified NotificationCenter (Angular + React отдельно). NEW-82/83.
- QC2 (type-gen) → **(b)** openapi-typescript + openapi-fetch. NEW-84 (CI check), NEW-85 (YAML snapshot).
- QC3 (errors) → **(b)** interceptor + RFC 7807 parser. NEW-86/87 (toast-service).
- QC4 (window.prompt) → **(b)** ConfirmWithReasonDialog + аналог в PWA. NEW-88 (ConfirmDialog), NEW-89 (showcase v0.1+).
- QC5 (lazy) → **(a)** per-role modules. NEW-90 (bundle size check), NEW-91 (preload strategy).
- QC6 (N+1 Stats) → **(b)** aggregate endpoint + 60s cache. NEW-92 (audit list+detail), NEW-93 (invalidation pattern).
- QC7 (sparklines) → **(a)** real metrics endpoint. NEW-94 (SQL vs Prometheus), NEW-95 (backend cache).

**Пачка P1-B (Data integrity) — ЗАКРЫТА:**
- QB1 (hard-delete групп) → **(c)** soft-delete + audit-поля `archived_by/archived_at`. NEW-71: audit всех hard-delete.
- QB2 (общий audit-log) → **(c) ACCEPTED** — Loki JSON-логи достаточны. NEW-72: `@AdminAction` aspect. NEW-73: LogQL examples.
- QB3 (Flyway rollback) → **(b)** expand/contract паттерн в runbook. NEW-74: PR-template. NEW-75: runbook шаблон.
- QB4 (homework_submissions без soft-delete) → **НЕ ПРОБЛЕМА** — это UI-трекер студента для самоконтроля, переклассифицировано, hard-delete accepted.
- QB5 (lesson change без истории) → **(d)** запрет изменения после старта пары. NEW-76: temporal history → future-ideas. NEW-77: admin-override при необходимости.
- QB6 (telegram_id change без verification) → **(a)** двухшаговая верификация через `/start <token>`. NEW-78: Redis vs таблица. NEW-79: self-service QR в PWA.
- QB7 (uniqueness login/telegram_id) → **(a)** immutable identity, unique across all statuses. NEW-80: `/admin/users/archived`. NEW-81: data-cleanup перед включением ограничения.

**Пачка P1-A (Observability) — ЗАКРЫТА:**
- QA1 (DEBUG default) → **(a)** INFO-дефолт. NEW-57: CI-check.
- QA2 (tracing) → **(b)** Sleuth/OTel + Tempo. NEW-58/59.
- QA3 (trace_id в events) → **(a)** body-поле. Связка с NEW-47 (event_version retrofit). NEW-60/61.
- QA4 (бизнес-метрики) → **(b)** `@Counted` + Telegram-алерты через bot-webhook. NEW-62 (`POST /internal/alert`), NEW-63 (runbook), NEW-64 («тихий час»), NEW-65 (2 недели baseline).
- QA5 (retention) → **(c)** 14д. NEW-66: триггер пересмотра.
- QA6 (health-check) → **(a)** show-details + custom indicators. NEW-67: `/actuator/info` с git-sha.
- QA7 (JSON-логи) → **(b)** unified структурированные логи. NEW-68 (shared logback), NEW-69 (рефакторинг logs), NEW-70 (bot promtail).

**Группа 8 (оставшиеся P0) — ЗАКРЫТА:**
- Q19a (08 P0-2, схема `otp.requested`) → **(b) TO-FIX** — JSON Schema + `event_version: 1` + contract-тест. NEW-47: retrofit версионирования в остальные 14+ events (v0.1). NEW-48: `docs/event-schemas.md` с versioning policy.
- Q19b (09 P0-3, ролевые guards PWA) → **(b) TO-FIX** — `useAuth()` + `RoleGuard`. PWA scope = STUDENT + headman. Admin/teacher → `docs/future-ideas.md` (новый раздел добавлен). NEW-50: audit Angular guards в web-panel.
- Q19c (10 P0-3, CSP web-panel) → **(a) TO-FIX** — строгая CSP + HSTS + остальные headers в nginx. NEW-54: report-uri (v0.1). NEW-55: аналогичный CSP для PWA vhost. NEW-56: CI-check на headers.
- Q19d (12 P0-2, «Открыть в Telegram») → **(a) TO-FIX** — `https://t.me/<bot_username>`. NEW-51: документировать username.
- Q19e (14 P0-1, `latecheckin/` без тестов) → **(b) TO-FIX** — unit + IT + contract-тест `late_checkin.approved`. NEW-52: event-schemas для всех late-checkin events.
- Q19f (14 P0-2, бот callback_query без тестов) → **(b) TO-FIX** — pytest + Aiogram fake-updates harness. Закрывает также регрессию 06 P0-5. NEW-53: shared conftest.py.

---

## ВСЕ P0 ЗАКРЫТЫ

**Итоговый статус P0:**
- 10 P0-кластеров (C0-1..10) — все закрыты (большинство TO-FIX, C0-2 DISSOLVED, C0-9 accept)
- 6 групп точечных P0 — все закрыты
- **Остаточные P0:** нет в scope v0.0.0

**Следующая фаза:** **P1 группами по темам** → потом P2/P3 целиком помечаются «v0.1 backlog» → потом 99-executive-summary.md.

**План групп P1 (draft):**
- **Группа P1-A (observability):** логирование DEBUG, metrics retention, tracing, Grafana dashboards.
- **Группа P1-B (data integrity):** soft-delete везде, audit-trail, migrations patterns.
- **Группа P1-C (frontend reuse):** унифицированный NotificationCenter (C1-1), OpenAPI→TS type-gen (C1-2).
- **Группа P1-D (CI/CD):** IMAGE_TAG=${sha} (C1-3), coverage-gate (C1-4), contract-тесты событий (C1-5), base images digest (C1-6).
- **Группа P1-E (remaining infra):** ShedLock (C1-7 уже повышен в P0-зависимости C0-3), лендинг синхронизация (C1-8), DEBUG-логи (C1-10).

**Решение:** пройти P1 той же методикой (группы по 3-4 вопроса)? Или владелец хочет крупнее группы (по целому кластеру)? Жду указания.

**Группа 7 (gateway P0) — ЗАКРЫТА:**
- Q18a (07 P0-1, CORS) → **(b) TO-FIX** — env `CORS_ALLOWED_ORIGINS`. NEW-43: пункт в release runbook. NEW-44: CI-lint на `rutcampustrack.ru`.
- Q18b (07 P0-2, публичные ws/otp) → **(a) AUTO через C0-7/C0-4 + (c) TO-FIX** — per-telegram_id лимит «один активный OTP / 5 мин» в Redis. NEW-45: `docs/redis-keyspace.md`. NEW-46: UX countdown на 429.

**Текущая группа:** **Группа 8 — оставшиеся P0 (6 вопросов)**:
- 08 P0-2 (схема `otp.requested` — обязательна после 01 P0-4).
- 09 P0-3 (ролевые guards PWA).
- 10 P0-3 (CSP web-panel).
- 12 P0-2 (landing «Открыть в Telegram»).
- 14 P0-1 (`latecheckin/` без тестов).
- 14 P0-2 (бот callback_query без тестов).

**Группа 6 (notification-bot P0) — ЗАКРЫТА:**
- Q17 (06 P0-1, insecure gRPC) → **(c) ACCEPTED** — docker-сеть = граница доверия. Документируется в `docs/security-model.md`. NEW-41: future-ideas «mTLS при разнесении хостов». NEW-42: CI-check «backend gRPC ports только в `expose:`».
- 06 P0-2 auto-closed через 13c (fail-fast GRPC_SECRET) — подтверждено явно.

**Текущая группа:** **Группа 7 — gateway P0 (2 вопроса)**:
- 07 P0-1 (CORS домен rutcampustrack.ru vs ruttrack.site).
- 07 P0-2 (`/api/ws/**` и `/api/auth/otp/**` публичны без JWT — частично покрыто C0-4 rate-limit + C0-7 ws-ticket, уточнить).

**Группа 5 (notification-service P0) — ЗАКРЫТА:**
- Q16a (05 P0-2, `GlobalExceptionHandler`) → **(b) TO-FIX** — новый `shared-web` модуль для 4 сервисов. Поглощает C1-11 (повышение из P1 в P0). NEW-34: правила shared-web (без autoconfig). NEW-35: подключить auth-service позже.
- Q16b (05 P0-3, `push_subscriptions` в `attendance_db`) → **(b) TO-FIX** — shared-DB accepted, миграции переносятся в notification. NEW-36: раздел «Shared-DB паттерны» в architecture.md. NEW-37: runbook переноса Flyway-миграции.
- Q16c (05 P0-4, IDOR на `DELETE /{id}`) → **(b) TO-FIX** — endpoint `DELETE /push/subscriptions/me` (без id). Связано с 02-Q-frontend-security Часть Б. NEW-38: IDOR-тест для push.
- Q16d (05 P0-5, handshake без audit) → **(a) TO-FIX** — HandshakeInterceptor + Loki. NEW-39: gateway ws-log (или accept nginx). NEW-40: grafana-алерт на подозрительную активность (v0.1).

**Текущая группа:** **Группа 6 — notification-bot P0 (1 оставшийся вопрос)**:
- 06 P0-1 (gRPC insecure_channel без TLS). 06 P0-2 уже закрыт через 13c (пустой GRPC_SECRET).

**Группа 4 (attendance-service P0) — ЗАКРЫТА:**
- Q15a (04 P0-4, `X-Group-Id` IDOR) → **(b) AUTO через C0-1 + contract-тест**. NEW-31: test-suite `SecurityIdorIT` для всех backend-сервисов. NEW-32: политика 404 vs 403.
- Q15b (04 P0-6, `cleanupOrphans` mass-delete) → **(a) TO-FIX** — удалить `@PostConstruct` cleanup. Auto-resolves 04-Q5. NEW-33: `docs/admin-scripts.md` для разовых задач.

**Текущая группа:** **Группа 5 — notification-service P0 (4 вопроса)**:
- 05 P0-2 (нет GlobalExceptionHandler — связано с C1-11).
- 05 P0-3 (push_subscriptions в чужой БД).
- 05 P0-4 (SubscriptionAuthInterceptor IDOR).
- 05 P0-5 (handshake без audit trail).

---

**Группа 3 (schedule-service P0) — ЗАКРЫТА:**
- Q14a (03 P0-3, `NumberFormatException`) → **AUTO-RESOLVED через C0-1** (подтверждено). Плюс 04 P0-3 закрыт тем же.
- Q14b (03 P0-4, `@Scheduled` без ShedLock) → **(a) TO-FIX** — ShedLock сейчас. C1-7 повышен до P0-зависимости от C0-3. NEW-28: audit `@Scheduled` в academic/attendance.
- Q14c (03 P0-5, дрейф week-parity) → **(a) TO-FIX** — единый `WeekParityResolver` helper + параметризованные unit-тесты. NEW-29: golden-test таблица дат. NEW-30: smoke diff реальных семестров.

**Текущая группа:** **Группа 4 — attendance-service P0 (2 вопроса — 04 P0-3 закрыт auto-resolve)**:
- 04 P0-4 (UserContextFilter не проверяет `X-Group-Id`) — вероятно AUTO через C0-1.
- 04 P0-6 (`cleanupOrphans` mass-delete на старте — связано с 04-Q5).

---

**Группа 2 (academic-service P0) — ЗАКРЫТА:**
- Q13a (02 P0-3, `NumberFormatException`) → **(a) AUTO-RESOLVED через C0-1**. Закрывает также 03 P0-3, 04 P0-3.
- Q13b (02 P0-4, race в `activateSemester`) → **(c) ACCEPTED** — single-admin invariant. Требует javadoc + запись в `docs/architecture.md`.
- Q13c (02 P0-5, пустой `${GRPC_SECRET:}`) → **(a) TO-FIX** — убрать default, fail-fast. Закрывает также 06 P0-2. Требует правку CI workflow (env `GRPC_SECRET: test-secret`). NEW-24: audit всех `${VAR:}` паттернов. NEW-25: CI smoke-тест «compose без .env.prod должен падать».
- Q13d (02 P0-7, ДЗ в память + N+1) → **(a) TO-FIX** — Pageable + `@EntityGraph`. Breaking change для фронтов (`Homework[]` → `PagedModel`). NEW-26: audit всех `findAll()` без Pageable. NEW-27: связка с C1-2.

**Текущая группа:** **Группа 3 — schedule-service P0 (3 вопроса)**, ждёт ответа владельца.
- 03 P0-3 → уже AUTO-RESOLVED через C0-1 (закрыто в Q13a), пометить в отчёте.
- 03 P0-4 (`@Scheduled` без ShedLock) — связано с C1-7.
- 03 P0-5 (дрейф week-parity).

---

## Handoff — 2026-04-18 (четвёртая сессия, конец)

**Сделано в эту сессию:** отчёты 15-cross-cutting-issues.md (~510 строк) и 16-nit-backlog.md (244 строки, полный срез всех 110 P3 по запросу владельца — «хочу за раз всё сделать потом»). Осталось **один** отчёт: 99-executive-summary.md.

**16-nit-backlog.md — содержание:**
- Все 110 P3 из 13 отчётов (01-10, 12-14). P4 в классификации проекта нет (00-PLAN.md определяет только P0/P1/P2/P3).
- Сгруппированы по 16 темам: A — мёртвый код (16 п.), B — TODO/FIXME (5 п.), C — naming/стиль (13 п.), D — hardcoded константы (10 п.), E — логирование (4 п.), F — HATEOAS/REST (6 п.), G — error handling (6 п.), H — производительность (7 п.), I — event schemas/proto (7 п.), J — тесты (13 п.), K — Docker/compose (6 п.), L — cross-service (5 п.), M — frontend UX (10 п.), N — HTML/A11y (3 п.), O — Python/CI (5 п.), P — misc (2 п.).
- Оценка уборки: 3-4 человеко-дня одной сессией.
- Рекомендация: делать между C0-8 (CI-gate) и C0-7 (JWT cookie) в середине кластерной работы.
- В конце — остаток после P3-уборки: ~18 точечных P0 (02/03/04/05/06/08/10 — не попавшие в кластеры), ~118 P1, 165 P2.

**15-cross-cutting-issues.md — содержание:**
- **P0-кластеры (10):** C0-1 UserContextFilter (5 P0 + gateway strip); C0-2 initial_password-цепочка (4 P0); C0-3 AFTER_COMMIT→outbox (3 P0); C0-4 rate-limiting (2 P0 + 3 P1); C0-5 logout lifecycle (3 P0); C0-6 CSP self-host лендинга (2 P0); C0-7 JWT HttpOnly cookie + ws-ticket (4 P0); C0-8 CI→deploy gate (1 P0); C0-9 ротация секретов `.env.prod` (1 P0); C0-10 LE cert-name (1 P0).
- **P1-кластеры (11):** C1-1 unified NotificationCenter (3 STOMP); C1-2 OpenAPI→TS type-gen; C1-3 IMAGE_TAG=${sha}; C1-4 coverage-gate; C1-5 contract-тесты событий; C1-6 base images digest; C1-7 ShedLock; C1-8 лендинг v9.0-синхронизация; C1-9 latecheckin/callback_query тесты; C1-10 DEBUG-логи + JWT в query; C1-11 GlobalExceptionHandler notification.
- **Dependency graph** с правильным порядком: C0-9 → C0-10 → C0-8 → C0-2 → C0-3 → C0-1 → C0-4 → C0-5 → C0-6 → C0-7, далее P1.
- **Метрики:** ~30 P0 закрывается кластерами из 53 (остаётся ~18 специфичных), ~18 P1 из 136. P2/P3 (275) — в v0.1 backlog.
- **Процессные фиксы:** branch protection, supply-chain scan (trivy/gitleaks/audit), Renovate, процесс ревизии лендинга, ротация секретов поквартально, release checklist, observability retention 30-45д, load-тест.
- **13 вопросов к владельцу** по приоритетам, выбору между альтернативами (magic-link vs разовый пароль, nginx vs Gateway RL, self-host vs whitelist CSP, Debezium vs in-app outbox, PostgreSQL vs Redis ShedLock).

**Следующее по плану:**
- **99-executive-summary.md** — последний отчёт. Финальная приоритезация. Roadmap до v0.0.0: что блокер (≈30 P0), что в v0.1 (~136 P1), что в backlog (275 P2/P3). Estimate в человекоднях: C0-9 (1д), C0-10 (0.5д), C0-8 (0.5д), C0-2 (3-4д), C0-3 (5-7д), C0-1 (3-5д), C0-4 (1-2д), C0-5 (2д), C0-6 (1-2д), C0-7 (8-12д) = **25-35 человеко-дней только на P0-кластеры**. Dependency graph между кластерами уже готов в 15, можно копировать. Executive summary — это TL;DR для владельца: «если сделать X в таком порядке — получишь релиз к дате Y». Ориентироваться на сводку 15, не переоткрывать 01-14 повторно.

**После 99 — финальный коммит (один) со всеми 14 отчётами (01-10, 12-15, 99 + PROGRESS + PLAN).**

**Важные контекстные заметки:**
- Язык всех отчётов — русский (`feedback_language_russian.md`).
- 15 использует материал 01-14 без повторного чтения кода.
- 99 должен тоже работать с материалом 15 и суммарных счётчиков, без переоткрытия отчётов.
- `.env.prod` в рабочей копии — **не показывать** содержимое в коммите/публичном issue (факт зафиксирован в 13 P0-3 и C0-9).

---

## Handoff — 2026-04-18 (третья сессия, конец)

**Сделано в эту сессию:** отчёты 13 (infra/docker/ci, ~370 строк) и 14 (tests-audit, ~410 строк). Осталось **два** отчёта: 15-cross-cutting-issues.md и 99-executive-summary.md.

**Суммарные счётчики по завершённым разделам (01–10, 12, 13, 14):**
- P0 (блокеры релиза): **53**  (было 47 + 4 из 13 + 2 из 14)
- P1 (серьёзные):       **136** (было 116 + 11 + 9)
- P2 (средние):         **165** (было 137 + 13 + 15)
- P3 (мелкие/nit):      **110** (было 94 + 8 + 8)

**Статус Telegram Mini App (11):** ПРОПУЩЕН по просьбе пользователя — он ещё не доделал клиент. Задумано копирование дизайна/логики из PWA в mini-app.

**Повторяющиеся сквозные P0-паттерны для 15-cross-cutting-issues.md** (обновлённый список с новыми из 13/14):

### Backend-паттерны
1. **`UserContextFilter` доверяет `X-User-*`** — academic, schedule, attendance, notification-service. Защита только на Gateway (07 strip в `JwtAuthenticationFilter.java:65-69`). Прямой доступ к портам 9091-9094 в docker-сети = полный bypass RBAC. Подтверждено тестами: `SecuritySmokeTest` ловит только `@RequireRole`-эндпоинты, не публичные.
2. **`initial_password` в plain text цепочка**: БД → REST `/academic/users` → gRPC `GetUserByTelegramId` (proto:155) → Telegram-чат. Один фикс → 01 P0-2, 02 P0-1, 06 P0-3, 08 P0-1, 10 P2-13.
3. **`@TransactionalEventListener(AFTER_COMMIT)` без outbox** — academic, schedule, attendance. Падение RabbitMQ между commit и send = потеря `lesson.started/closed`, `attendance.marked`, `excuse.*`, `homework.*`.
4. **`@Scheduled` без ShedLock** в schedule-service → double-publish при scaling.
5. **Rate-limiting отсутствует везде**: нет в Gateway (07 P1), нет в nginx (13 P1-3), нет тестов (14 P1-2). OTP verify-by-code брутфорсится за ~3 часа.
6. **DEBUG-логи в default-конфигах**, переопределяются только в prod-профиле. Если `SPRING_PROFILES_ACTIVE=prod` не выставлен, JWT в query лог'ируется.

### Frontend-паттерны
7. **JWT в localStorage** (09 P0-1, 10 P0-1) — XSS-вектор, единый фикс HttpOnly cookie.
8. **JWT в WS query string** (09 P0-2, 10 P0-2) — short-lived ws-ticket endpoint.
9. **Logout lifecycle ничего не чистит** — 09 P0-4 (SW cache headman-api-cache-v1), 09 P0-5 (push-subscription), 10 P0-4 (sessionStorage notifications). И нет тестов на эти сценарии (14 P1-4).
10. **Три STOMP-клиента в web-panel + дубликаты в PWA** (10 P1-5, 09 P1-4/P1-6) → единый NotificationCenter.
11. **Type drift фронт ↔ backend** (08 P1 + 09 P2-5 + 10 P2-8) → OpenAPI-генератор типов.
12. **CSP политика фронтов** (12 P0-1 landing, 09 P1-3 PWA, 10 P0-3 web-panel, 13 P0-4 корневой nginx) → единая стратегия (self-host или whitelist) + SRI.
13. **Лендинговый контент рассинхронизирован с v9.0** (12 P1-6): excuse-тикеты по старому TG-flow. Нужен процесс «ревизия лендинга при изменении бизнес-логики».

### Infra/CI/тесты-паттерны (НОВОЕ из 13/14)
14. **CI и deploy — независимые workflow'ы** (13 P0-2, 14 P1-8). Красный CI не блокирует выкат. Фикс: `needs:` или branch protection.
15. **`.env.prod` с реальными секретами в рабочей копии** (13 P0-3). Гитигнорирован, но утечёт при share/backup. Нет `.env.prod.example` (удалён в 2185bec). Ротация требуется: BOT_TOKEN, GHCR_TOKEN, VAPID_PRIVATE_KEY, все DB-пароли.
16. **`:latest` теги в prod-compose** (13 P1-1) — невозможен воспроизводимый откат. Фикс: `IMAGE_TAG=${{ github.sha }}`.
17. **Нет coverage-gate в CI** (14 P1-3) — регрессия покрытия проходит незамеченной. Фикс: JaCoCo + `madrapps/jacoco-report` + vitest --coverage + pytest-cov.
18. **Contract-тесты RabbitMQ событий есть только для excuse** (14 P1-5) — остальные 14+ событий без schema-verification. Плюс нет contract-тестов proto (14 P1-6) и Gateway↔downstream (14 P1-1).
19. **Base images не pin'нутся по digest** (13 P1-10) — supply-chain risk на `grafana/loki:latest`, `prom/prometheus:latest`, `cadvisor:latest`. Особенно критично для `cadvisor` с `privileged: true` и `promtail` с docker.sock.
20. **`latecheckin/` без тестов** (04 P0, 14 P0-1) и **callback_query-хендлеры бота без тестов** (06 P0-5, 14 P0-2) — код в проде без сети безопасности.

**Следующее по плану (в порядке):**
- ~~13-infra-docker-ci.md~~ — готово.
- ~~14-tests-audit.md~~ — готово.
- **15-cross-cutting-issues.md** — СЛЕДУЮЩИЙ. Структурировать 20 паттернов выше. Для каждого: затронутые отчёты, корневой фикс, примерный estimate, ordering (что сначала).
  - **P0-кластеры (6 штук), которые закрываются одним фиксом:**
    1. `UserContextFilter` + Gateway headers (паттерны 1, 14 P1-1)
    2. `initial_password` обрезать на уровне auth (паттерны 2)
    3. Outbox для всех AFTER_COMMIT (паттерн 3)
    4. Rate-limiting: либо Spring Cloud Gateway redis-rate-limiter, либо nginx limit_req (паттерн 5 + 13 P1-3)
    5. Logout lifecycle: общий `clearAllClientState()` для PWA/web-panel (паттерн 9)
    6. CSP: self-host CDN лендинга (паттерн 12)
  - **P1-кластеры** — JWT в localStorage + WS query (паттерны 7,8), три STOMP клиента (10), type drift (11), CI+deploy связка (14), `.env.prod` ротация (15), coverage gate (17), contract tests (18), base image pinning (19).
- **99-executive-summary.md** — финальная приоритезация. Roadmap до v0.0.0: что блокер, что в v0.1, что в backlog. Estimate в человекоднях. Dependency graph между фиксами.

**После 15 и 99 — финальный коммит (один) со всеми отчётами.**

**Важные контекстные заметки:**
- Язык всех отчётов — русский (`feedback_language_russian.md`).
- Формат — шаблон в `00-PLAN.md`. Эталон глубины — 01-auth-service.md (1340 строк), 02-academic-service.md (585). 13 и 14 по ~370-410 строк — норма для кросс-сервисных отчётов.
- Тесты не запускаем, только читаем.
- Для 15 и 99 — работать с материалом уже написанных отчётов (+ перекрёстные ссылки), не нужно заново читать код.
- `.env.prod` действительно лежит в рабочей копии с реальными prod-секретами — **не показывать** содержимое в коммите или публичном issue (но в отчёте 13 P0-3 зафиксирован факт и список ротаций).
