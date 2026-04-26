# M02 Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## 2026-04-19 — Старт M02

**Выбрано:** Начало milestone'а M02 по PLAN.md (ShedLock → outbox → contract-тесты).
**Причина:** M01 закрыт, dependency graph разрешает старт. Внешних блокеров нет.

## 2026-04-19 — shared-outbox модуль (NEW-6)

**Выбрано:** (a) shared-outbox модуль — `services/shared/shared-outbox` с
  strategy interface `OutboxStorage` + 2 реализации (Jpa для
  academic/schedule, Mongo для attendance). `OutboxPublisherJob` и
  `OutboxCleanupJob` — общие. `OutboxEntity` — `abstract` + конкретные
  классы per storage.
**Отвергнуто:** (b) copy-paste по 3 сервисам.
**Причина:** M01 доказал что shared-* pattern работает (component scan
  без autoconfig). Drift от копипасты дороже, чем 1 абстракция с двумя
  реализациями. PublisherJob и CleanupJob — идентичны по логике.
**Последствия:** Группа 3 CHECKLIST начнётся с scaffold'а `shared-outbox`
  (build.gradle.kts, settings.gradle.kts include) + определение
  `OutboxStorage` API. Attendance integration будет тестироваться тщательно
  (Mongo-specific path).

---

_Формат записи:_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
