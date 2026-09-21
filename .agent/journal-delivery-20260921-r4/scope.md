# Journal delivery r4 scope

Дата: 2026-09-21. Writer: `/root/journal_delivery` в main checkout на базе
`1daa5b0337a2f538b86a09f7e8dfa5958e62af81` с сохранением чужого WIP.

## Goal

Для реальной пары действие старосты проходит через mark/change/clear и
authorized file download; переходы из `EXCUSED` не оставляют сиротские journal
bytes и не затрагивают request-owned evidence. Истёкшее journal-вложение не
публикуется в report. Frontend показывает дату и write-controls по московскому
календарю и подтверждает изменения чтением после ACK.

## Relevant evidence

- `.agent/orchestration-v2/evidence/journal-acceptance-review-20260921.md`
  (четыре свежих finding после принятых time/semester/history fixes).
- `.agent/worktrees/v2-l5b-recurring-writer/.agent/headman-journal-ui/diff.md`
  (замороженный список 11 frontend-путей).
- `CURRENT.md`, `RULES.md` SHA256
  `4E05153BFAE604D6882805D37DD89011CCC7301B4641DEF3FFDCEB22D9D6F364`.

## Boundaries

Изменены только affected journal lifecycle/report paths, focused existing IT
и перечисленные frontend-пути. Предыдущие accepted HIGH/semester/snapshot
checks не повторяются. Maps/Homework scope не затрагивается.
