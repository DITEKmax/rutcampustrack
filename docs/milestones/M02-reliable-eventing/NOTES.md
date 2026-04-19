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
