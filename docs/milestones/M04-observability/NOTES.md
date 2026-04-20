# M04 Notes

Живой файл. Surprises, отклонения, измерения, технические долги.

---

## 2026-04-20 — старт milestone

- M03b закрыт `eb125c4` + tag `v0.0.0-alpha.4` (локально без push).
- 58 коммитов ahead origin — push отложен до конца v0.0.0 (по решению владельца, эта сессия).
- Выбран M04 первым по dependency graph (рекомендация из hand-off `e85081a`).
- В DECISIONS.md 3 открытых развилки требуют подтверждения до старта кода:
  1. shared-observability модуль vs duplication по сервисам.
  2. Alert receiver: новый endpoint `/internal/alert` в notification-service vs прямой Aiogram bot endpoint.
  3. Тихий час: фиксированный 22:00-08:00 (MSK) vs configurable per-alert.
