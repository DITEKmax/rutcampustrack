# M02 Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## 2026-?? — Initial scaffold

_(Заполняется при старте milestone'а.)_

## ОТКРЫТО — shared-outbox модуль vs copy-paste (NEW-6)

**Развилка:** outbox infrastructure (Entity, Repository, PublisherJob,
CleanupJob) — один shared модуль `services/shared/shared-outbox` или
копия в каждом из 3 backend-сервисов?

**Контекст:**
- 2 из 3 сервисов — PG (JPA), 1 — Mongo. Нужны две реализации storage.
- PublisherJob / CleanupJob — одинаковые по логике, различаются только
  репозиторием и RabbitTemplate wiring.
- M01 показал что shared-* модули работают (NEW-34: без autoconfig,
  через component scan).

**Предварительная рекомендация (2026-04-19):** (a) shared-outbox модуль
со strategy interface `OutboxStorage` + 2 реализации (Jpa/Mongo).
Publisher/Cleanup jobs — shared. Entity — `abstract` + конкретные классы
per storage.

**Причина:** уменьшает drift (NEW-6 мотивация), M01 доказал паттерн
working. Overhead на abstraction минимален (2 реализации OutboxStorage).

**Альтернатива (b):** copy-paste по 3 сервисам — проще в M02 (меньше
абстракций), но при починке bugs нужно править 3 места.

**Решение:** подтвердить при старте Группы 3.

---

_Формат записи:_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
