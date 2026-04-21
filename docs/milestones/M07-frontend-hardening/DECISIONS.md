# M07 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — D{N}: короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M07 нет — scope зафиксирован в
OWNER-ANSWERS QC1-7 (строки 1809-2053), QE3/4 (2430-2510), P2-7A/1..8
(5215-5365), P2-7B/1..4 (5366-5550). Если появится micro-решение —
записать как `## YYYY-MM-DD — D{N}: заголовок`._

Ожидаемые точки разветвления (предварительно):
- CSP self-host strategy — bundle vs separate `@font-face` vs inline
  base64 (Группа 1).
- openapi-typescript code-gen output structure — single-file vs
  per-service directory (Группа 3).
- axe-core severity threshold (Группа 10).
- Lazy-loading boundary для web-panel role-split (Группа 8).
