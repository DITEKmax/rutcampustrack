# M02 Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «outbox_lag p95: 850ms, после tuning fixedDelay: 120ms»
- **Surprises:** «attendance использует Mongo — shedlock-provider-mongo API отличается, требует kmongo»
- **Вопросы к владельцу:** «retention 7 дней ок или оставляем sent навсегда для audit?»
- **Технические долги:** «закрыл TODO из NOTES M01 о telegram_id capture-group — отложен в M04»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-19

- Milestone инициализирован. PLAN + CHECKLIST + DECISIONS (skeleton) написаны
  на основе 99-executive-summary.md (Фаза 2 кластеры C1-7 + C0-3 + C1-5)
  и OWNER-ANSWERS.md (02-Q3 outbox, Q-P0-4 ShedLock, P2-11/7 common $defs).
- Открытый вопрос до старта Группы 3: **shared-outbox модуль vs copy-paste**
  (NEW-6). Рекомендация в DECISIONS.md — shared модуль (меньше drift, M01
  показал что паттерн работает для shared-web). Подтвердить при старте.
- Stubs для остальных NOTES: measurements + surprises заполняются по ходу.

## 2026-04-19 — Surprise: @Scheduled в schedule-service (Группа 1)

PLAN.md упоминает `LessonGenerationService.regenerateUpcoming()` и
`OneOffLessonReconciler.reconcile()` как цели `@SchedulerLock`, но в
реальном коде этих методов нет (grep подтвердил).

**Реальное состояние schedule-service:**
- Единственный `@Scheduled` метод — `LessonStatusTransitionJob.runTransitions()`
  (fixedDelay=60_000, `services/schedule-service/schedule-app/src/main/java/
  ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java:53`).
- `SchedulingConfig` гардится через `@Profile("!test")` — тесты не
  триггерят job.
- `LessonGenerationService` существует, но генерация вызывается
  reactively (при создании ScheduleItem), не по расписанию.
- `IsoParityReconciler` существует, но запускается через Flyway
  migration marker (V7/V8/V9), не через `@Scheduled`.

**Вывод:** PLAN.md устарел относительно кода (вероятно написан до
финальной реализации v3.0 Schedule). Корректирую scope Группы 1 на
реальный метод `LessonStatusTransitionJob.runTransitions()`. NEW-28
аудит academic/attendance — без изменений.

**Решение:** продолжаю с `@SchedulerLock` на `runTransitions()`.
Acceptance criteria PLAN.md не меняются (они по outbox/events, не
по конкретным методам).

## 2026-04-19 — NEW-28 аудит результат (Группа 2)

Grep `@Scheduled` по всем backend-сервисам:

| Сервис | `@Scheduled` методы | Решение |
|---|---|---|
| schedule-service | `LessonStatusTransitionJob.runTransitions()` | ShedLock ✅ (Группа 1) |
| api-gateway | `PublicKeyConfig.refresh()` | `@SuppressWarnings("SingleInstance")` |
| academic-service | 0 | — |
| attendance-service | 0 | — |
| auth-service | 0 | — |
| notification-web | 0 | — |

**`PublicKeyConfig.refresh()` — intentionally SingleInstance:** каждый
gateway-инстанс держит свою копию PublicKey в памяти
(`AtomicReference`). ShedLock-координация означала бы «один тянет,
остальные не тянут» → кэш остальных инстансов протухает после
ротации ключа в Auth (C0-1, M03) → отвержение валидных JWT. Это баг
который ShedLock создаёт, а не решает. Маркер `@SuppressWarnings(
"SingleInstance")` явно документирует дизайн и делает ArchUnit rule
(Группа 9) корректной.

**Отклонение от PLAN.md (Группа 2):** PLAN подразумевал добавление
ShedLock деталей в academic + attendance build.gradle/Flyway. Реально
это не требуется пока — `@Scheduled` там нет. ShedLock infra
(libs + Flyway + EnableSchedulerLock) добавляется **в Группу 3**,
когда появится OutboxPublisherJob (первый `@Scheduled` в этих
сервисах). Закрываю пункты Группы 2 как N/A со ссылкой сюда.

## 2026-04-19 — Состояние publisher'ов перед Группой 3/5

Grep `RabbitTemplate|convertAndSend` — две архитектуры параллельно:

**schedule + academic — Spring ApplicationEvent паттерн:**
- Свой `ru.rutcampustrack.{service}.event.DomainEvent extends ApplicationEvent`
  (envelope: `event_type`, `event_id UUID`, `occurred_at`, `payload`).
- Service-layer делает `ApplicationEventPublisher.publishEvent(event)`.
- `DomainEventListener` с `@TransactionalEventListener(AFTER_COMMIT)`
  получает event и шлёт `rabbitTemplate.convertAndSend(EXCHANGE, "", event)`.
- AFTER_COMMIT гарантирует что при rollback события не летят — **pseudo-outbox**,
  но без persist/retry: если Rabbit down ПОСЛЕ commit БД — событие
  потеряно (тот самый P0-6 message loss).

**attendance — direct publisher:**
- Три публикатора (`AttendanceEventPublisher`, `ExcuseEventPublisher`,
  `LateCheckinEventPublisher`) строят `Map<String,Object>` envelope и
  шлют напрямую `rabbitTemplate.convertAndSend`.
- Нет ApplicationEvent слоя — publisher вызывается из service в той же
  транзакции.

**`shared-events/DomainEvent` (M01) — НЕ adopted сервисами.** Он
спроектирован с другой envelope-схемой (`event_version`, `trace_id`,
`occurred_at`, `source` + поля события напрямую, без nested `payload`).
Это конфликт моделей, который M02 **не будет решать** — рефакторинг
envelope'а = breaking change для всех 14+ JSON-схем. Оставляем как есть:
outbox будет хранить уже сериализованный JSON payload (строку) и
event_type как отдельный столбец для таргетинга. Миграцию на
`shared-events/DomainEvent` откладываем (не scope M02).

**Стратегия Группы 5 (refactor publisher'ов):**
1. Вместо `rabbitTemplate.convertAndSend(...)` в `DomainEventListener` /
   `AttendanceEventPublisher` — вызов `OutboxStorage.save(eventType,
   jsonPayload)` в **той же @Transactional** что и доменная операция.
   Т.е. для schedule/academic — переносим write в outbox из AFTER_COMMIT
   в BEFORE_COMMIT (обычный listener без фазы), чтобы сохранить outbox
   atomically с доменной записью.
2. `OutboxPublisherJob` читает batch `pending` → шлёт в Rabbit →
   помечает `sent`.
3. При Rabbit-недоступности — rows остаются `pending`, next tick job
   подхватывает. Это и есть guarantee'д retry.
