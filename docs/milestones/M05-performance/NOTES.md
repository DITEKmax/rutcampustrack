# M05 Notes

Живой файл. Surprises, отклонения, измерения, технические долги.

---

## Pre-start snapshot (scaffold'ится при старте M05)

- M04 закрыт на `325d25d`, tag `v0.0.0-alpha.5` (локально без push).
- ~70+ коммитов ahead origin — push отложен до конца v0.0.0.
- M04 деферренные items (возможно пересечение с M05):
  - **`/actuator/**` исключить из tracing sampling** — M04 G11 backlog, уместно в M05 Группа 8 (gRPC instrumentation рядом).
  - **AlertPublisher extends AbstractEventPublisher** — M04 code-reviewer SHOULD #1. Легко зацепить при рефакторе repositories, но не scope M05. Держать в следующий milestone.
  - **Typed DTO для Alertmanager webhook** — M06 (не scope M05).

## Source of truth для M05

- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` строки 3673-4028 (P2-10/1..8).
- `docs/report-before-v0.0.0/99-executive-summary.md` строка 117 (P2-10 summary).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — пункты 02 P2-3..7, 03 P2-2/4/5/6/7/8/9, 04 P2-1/2/9, 05 P2-7, 09 P2-11, 10 P2-14.

## Открытые развилки для D1..DN

_Ни одной на старт — все решения из OWNER-ANSWERS недвусмысленны.
Писать сюда если возникнут micro-решения (например, конкретный cache-
key format, formula pool-size для более слабого VPS)._

## Правила работы (без изменений с M04)

- Русский в отчётах / NOTES / ответах.
- READ-BEFORE-EDIT hook-reminder'ы ложные после Read в сессии — игнорировать.
- Один CHECKLIST-группа = один logical коммит (`feat/fix/test/docs(<scope>): ... (M05 Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для поиска, `bug-hunter`/`security-auditor`/`code-reviewer` в Группе 9.
- Surprise → NOTES.md + спросить до продолжения.
- Закрыл пункт CHECKLIST → `[x]` через Edit.
