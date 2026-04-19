# M03a Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «p95 latency до: 450ms, после: 120ms»
- **Surprises:** «обнаружил, что Gateway уже держит RSA keypair, а я думал нужно из auth-service тянуть»
- **Вопросы к владельцу:** «fail-open при Redis down — логировать WARN или ERROR?»
- **Технические долги:** «оставил TODO про X — закрою в M{X}»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md и 99-executive-summary.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-19 — Старт M03a

- M03 (original, ~14-18д) разделён на M03a (Internal JWT + rate-limit, 5-8д)
  и M03b (JWT cookie + ws-ticket + logout, 8-12д). Решение владельца —
  промежуточный тег v0.0.0-alpha.3 между ними, снижение риска breaking change.
- M02 закрыт коммитом `da38ef3` (2026-04-19), tag `v0.0.0-alpha.2` ещё не поставлен
  (нужно проверить и сделать).
- PLAN/CHECKLIST заполнены из аудита, dual-mode flag default = `true` в M03a
  (breaking переключение — последний commit перед тегом).
