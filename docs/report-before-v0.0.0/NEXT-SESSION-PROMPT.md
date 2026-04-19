# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Claude.

---

Новая сессия. **Опрос владельца по всем P0/P1/P2 полностью закрыт.**
178 NEW-задач зафиксированы. Теперь нужен **цикл верификации → разметки
→ финального отчёта.**

═══════════════════════════════════════════════════════════
КОНТЕКСТ
═══════════════════════════════════════════════════════════

Рабочая папка: `C:\Users\maksd\IntelliJIDEA\rutcampustrack`

**Главные файлы для чтения первым делом (в этом порядке):**

1. `docs/report-before-v0.0.0/PROGRESS.md` — handoff-сводка со всеми
   восемью сессиями и финальный итог. **Читай весь файл.**
2. `docs/report-before-v0.0.0/OWNER-ANSWERS.md` — **все принятые
   решения, meta-решения M1/M2, NEW-задачи 1-178, audit trail**. Это
   твоя память. ~6500 строк. Читать весь файл обязательно.
3. `docs/future-ideas.md` — отложенные идеи (magic-link, PWA
   admin/teacher, JSON-LD landing, magic-byte MIME check и др.)
4. `docs/report-before-v0.0.0/00-PLAN.md` — оригинальный план аудита.

═══════════════════════════════════════════════════════════
ТЕКУЩИЙ СТАТУС (2026-04-19, конец восьмой сессии)
═══════════════════════════════════════════════════════════

**Весь опрос владельца ЗАКРЫТ.**

- **P0** (53 исходных): все закрыты (10 P0-кластеров C0-1..10, кроме
  C0-2 DISSOLVED; 6 точечных групп 13-19)
- **P1** (136 исходных): все закрыты (5 пачек A-E, 33 вопроса)
- **P2** (165 исходных): все закрыты (12 групп, 79 consolidated
  вопросов)
- **P3** (110 nits): разбираются одной пачкой через 16-nit-backlog
  (отдельный workflow, не в этом опросе)

**Meta-решения:**
- **M1:** проект не в юрисдикции РФ, 152-ФЗ не применяется.
- **M2:** весь P2 в scope v0.0.0 (не v0.1 backlog).

**Итого: 178 NEW-задач, 12 P2 групп обсуждены, ~25 новых документов
запланированы.**

═══════════════════════════════════════════════════════════
ЧТО ДЕЛАТЬ В ЭТОЙ СЕССИИ
═══════════════════════════════════════════════════════════

**Владелец хочет ВЕРИФИКАЦИЮ перед финальным отчётом:**
удостовериться, что **каждый** P0/P1/P2 пункт из отчётов 01-16 имеет
ответ в `OWNER-ANSWERS.md`. Только потом — разметка и 99.

**Порядок работы:**

## Шаг 1 — Верификация покрытия ответов (обязательный)

Для каждого отчёта 01-15 (11 пропущен) и 16-nit-backlog:
1. Выписать ВСЕ P0/P1/P2 пункты (grep `^### P[012]-`).
2. Для каждого проверить в `OWNER-ANSWERS.md`:
   - явный ответ (Q-ID или группа P2-N/M), ИЛИ
   - явный AUTO-RESOLVED через другой фикс (QX / CY / P2-N/M), ИЛИ
   - явный ACCEPTED as-is.
3. **Любой непокрытый пункт** → записать в «список для доразбора» и
   поднять вопрос владельцу.

Выход: markdown-таблица
```
| Отчёт | Пункт | Статус | Где ответ |
|-------|-------|--------|-----------|
| 01    | P0-1  | ✅ TO-FIX | Q1 (OpenAPI AuthApi interface) |
| 01    | P0-2  | ✅ ACCEPTED | Q01 (initial_password by design, M1) |
| ...   | ...   | ⚠ НЕТ ОТВЕТА | — |
```

Таблицу сохранить как
`docs/report-before-v0.0.0/COVERAGE-AUDIT.md`. Если все покрыты —
сообщить владельцу «Все 306+ пунктов закрыты, готов к разметке».

Если есть непокрытые — собрать их в пачку вопросов (6-8 штук) и
обсудить по обычной методике.

## Шаг 2 — Разметка отчётов (вариант A, после верификации)

ОДНИМ ПРОХОДОМ, когда верификация показала 100% покрытие.

Для каждого отчёта 01-16:
- Рядом с каждой проблемой ставить пометку:
  - `✅ AUTO-RESOLVED через X` — если закрывается другим фиксом
  - `🔧 TO-FIX через X` — если есть явный план
  - `✅ ACCEPTED (причина)` — если владелец принял
  - `✅ DISSOLVED (причина)` — если проблема переклассифицирована
- В блоке «Зависимости» обновить: что заблокировано, что разблокировано
- В счётчиках P0/P1/P2 сверху отчёта пометить числа ACCEPTED/TO-FIX

**Используй Edit-инструмент, не переписывай файлы через Write.**

## Шаг 3 — Написание 99-executive-summary.md

После разметки:
- TL;DR roadmap v0.0.0
- Dependency graph (копируется из 15-cross-cutting-issues.md)
- Estimate в человекоднях per кластер
- Разбивка: что блокер, что v0.1, что backlog
- Ссылки на ключевые NEW-задачи
- Сводка по архитектурным изменениям (shared-модули, alertmanager,
  notification-web stateful)

Работать с материалом 15 + OWNER-ANSWERS.md, НЕ переоткрывать 01-14.

## Шаг 4 — Финальный коммит

Все отчёты 01-16 + 99 + PROGRESS + OWNER-ANSWERS + COVERAGE-AUDIT +
NEXT-SESSION-PROMPT одним коммитом. Commit message формата:
```
docs(audit): pre-v0.0.0 architecture audit — 16 reports + executive summary

- 16 detailed audit reports (01-16)
- 99-executive-summary with v0.0.0 roadmap
- 178 NEW-tasks tracked in OWNER-ANSWERS.md
- Coverage audit (all 300+ points addressed)
- Key architectural decisions: shared-web, shared-events,
  shared-test-containers, alertmanager, notification-web stateful
```

═══════════════════════════════════════════════════════════
ПАМЯТЬ — 178 NEW-ЗАДАЧ (краткий индекс по темам)
═══════════════════════════════════════════════════════════

Полные описания — в `OWNER-ANSWERS.md`. Категории:

- **NEW-1..50:** архитектура tradeoffs, internal JWT, dual-mode deploy,
  shared-outbox, rate-limit, Grafana, pre-deploy QA, migration,
  CSRF, auth tests, legacy cleanup, licenses, CSP.
- **NEW-51..100:** bot docs, PWA/admin future-ideas, Redis keyspace,
  event versioning, CSP report-uri, observability tracing, shared-
  events, Alertmanager ранний (переработан P2-9/5), retention,
  rollback, GHCR retention.
- **NEW-101..130:** container trust, SECURITY.md, semantic-release
  v0.1+, contributing.md, ArchUnit framework, stylelint a11y,
  JSON-LD, Loki alerts, proto optional audit, current_semester_id,
  display_name_short.
- **NEW-131..150:** OpenAPI customizer docs, CI conformance, @Schema
  lint, admin-access, htpasswd, AsyncAPI, STOMP payloads,
  architecture event docs, api-error-conventions, websocket-protocol,
  back-off, DLQ recovery, empty-catch CI lint, shared-web/validation,
  format-patterns, data-retention-policy.
- **NEW-151..178:** (P2 sessions)
  - **NEW-150..157** (P2-9): dockerfile-conventions, loki-major-upgrade,
    nginx-config, alerts.md расширение, bot webhook schema migration,
    secret-rotation, testing.md, resource-limits.
  - **NEW-158..164** (P2-8): shared-test-containers, migration-testing,
    golden-tests, e2e-testing, критичные frontend units,
    load-testing + performance-baseline, SecurityContractsIT.
  - **NEW-165..169** (P2-6): logging-conventions, notification_history
    schema, notification OpenAPI, CLAUDE.md update, Promtail pipeline.
  - **NEW-170..173** (P2-7A): useSwipeHandler, useDateNavigation,
    frontend-navigation, geofencing.
  - **NEW-174..175** (P2-7B): a11y-checklist, axe-core setup.
  - **NEW-176** (P2-1): java-conventions (Lombok).
  - **NEW-177..178** (P2-5): gateway-config, notification-template-
    catalog.

═══════════════════════════════════════════════════════════
АРХИТЕКТУРНЫЕ ИЗМЕНЕНИЯ — ДЕРЖАТЬ В ГОЛОВЕ
═══════════════════════════════════════════════════════════

**Новые shared-модули:**
- `shared-web` (Q16a) — GlobalExceptionHandler RFC 7807 + validation
  custom annotations + Jackson masking + OpenApiCustomizer.
- `shared-events` (NEW-60) — общий DomainEvent base (event_version,
  trace_id, occurred_at, source).
- `shared-test-containers` (NEW-158) — Testcontainers fixtures Java.
- `shared-logback` (NEW-68) — JSON + MaskingConverter + unified labels.

**Новые infra-контейнеры:**
- `alertmanager` (P2-9/5) — unified router Prometheus + Loki → bot webhook.

**Сервисы, где произошли архитектурные изменения:**
- **notification-web** перестаёт быть «stateless event forwarder»
  (P2-6/4) — получает own MongoDB `notification_db` для notification
  history, read/unread tracking, pagination REST API. CLAUDE.md
  update (NEW-168).

**CLAUDE.md updates (планируемые):**
- NEW-168: `Notification Web | 9094 | Spring Boot WebSocket + MongoDB
  notification_db` (вместо «— stateless»).
- NEW-36: shared-DB patterns (notification-web own + push_subscriptions
  в attendance_db).

═══════════════════════════════════════════════════════════
ПРАВИЛА РАБОТЫ
═══════════════════════════════════════════════════════════

- **Русский язык.** Тех. термины и код — оригинал.
- **НЕ запускать тесты, НЕ править production-код.** Только отчёты и
  OWNER-ANSWERS.md/PROGRESS.md/99.
- **Read-перед-Edit.** Хук требует Read перед каждым Edit. Это
  normal.
- **Коммит — только один финальный** (шаг 4). До этого не коммитим.
- **Никакие отчёты 01-16 не трогать до разметки** (шаг 2).

═══════════════════════════════════════════════════════════
СТАРТ
═══════════════════════════════════════════════════════════

1. Прочитай `PROGRESS.md` (весь файл, включая все 8 handoff'ов).
2. Прочитай `OWNER-ANSWERS.md` (весь файл, ~6500 строк).
3. Прочитай `future-ideas.md`.
4. Сообщи владельцу:
   > «Готов продолжить. Весь опрос закрыт (201 вопрос, 178 NEW-задач).
   > Начинаю шаг 1 — верификация покрытия. Сверю все P0/P1/P2 из
   > отчётов 01-16 с ответами в OWNER-ANSWERS.md, результат выложу
   > в COVERAGE-AUDIT.md. Это займёт ~15 минут. Продолжать?»
5. Жди `go` / коррекции / новые инструкции.

**Не пиши 99. Не размечай отчёты до завершения шага 1. Не коммить.**
