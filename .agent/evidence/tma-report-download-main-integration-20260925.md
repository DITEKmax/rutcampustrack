# Report downloads + PK-113 — MAIN integration evidence

## Scope and criteria

Интегрировать в `main` только готовые и reviewed пакеты: PK-113 statistics и permission correction; frontend native report-download boundary и финальное подключение teacher, headman weekly, headman stats; Auth/Gateway one-time ticket flow и его security correction. Пользователь получает существующий отчёт через Telegram `downloadFile`; PWA сохраняет Blob/filename flow. Критерии реализации и авторские проверки находятся в перечисленных ниже packet evidence.

MAIN до переноса: `cfcbd12f874ee5e094f64d24219c5da22e7f2b33` (включая Admin semester correction). `a626c1b7` и импортированная stats baseline `6d90d2b0` повторно не переносились. Каждый source SHA и его родитель сверены через `git show --no-patch`; все команды завершились с exit 0.

## Перенос и diff

Выполнены ровно эти scoped cherry-pick; каждый завершился с exit 0, конфликтов не было:

| Source commit | Новый MAIN commit |
|---|---|
| `d223c8e9d606aab9ddbc72c9e59aa2c24246bb5c` | `3f04d936` |
| `721547b9c57fca4bf34b536e20503ca7e9dd0602` | `7ce47e93` |
| `86edbe1089277a22b70c8a69baf0ecd7466fb5cd` | `25866dd6` |
| `63da0435968b3e2b2d41056a6e4aed4684bd58b7` | `3932cf2b` |
| `7a5af1d8ed4d9aebe04cb96c5c97a141a693e028` | `3b28db12` |
| `517876ac87fb80d2f0bac66ea42cac239011e306` | `2bcde708` |
| `db4c80c8cf5d2883d017f6ac93f9f6860118214f` | `2cd38e2b` |
| `9dff49d8f41a7a53d5d86dbb77112a9a7f800b0e` | `02bd70fc` |

До добавления этой integration note команда `git diff --name-status cfcbd12f874ee5e094f64d24219c5da22e7f2b33..HEAD` завершилась с exit 0 и показала 75 product/evidence paths: frontend mobile-core/PWA/TMA, Attendance API/service, Auth API/service, Gateway, NGINX и packet evidence. Соответствующий `git diff --stat` завершился с exit 0: 75 files changed, 7,508 insertions, 54 deletions. Эта note добавляет один отдельный `.agent/evidence` path.

## Evidence и проверки

- Frontend acceptance, exact product inventory и checks: `.agent/evidence/tma-report-download-frontend-20260925/result.md`. Записаны mobile-core/PWA/TMA TypeScript, focused native/session и slice/reset checks (31/31), scoped lint и TMA Vite build — все exit 0; TMA download adapter прошёл independent Sol review после UI recovery correction.
- Ticket/Auth/Gateway behavior и checks: `.agent/tma-report-ticket-backend-20260925/EVIDENCE.md`. Записаны targeted Auth и Gateway tests, isolated NGINX syntax check и independent security recheck — PASS.
- PK-113 criteria и Attendance checks: `.agent/headman-stats-packet-evidence-2026-09-25.md`. Записаны mobile-core/TMA/PWA typechecks и targeted `HeadmanStatsServiceTest` — exit 0.
- Источники до переноса проверялись командой `git diff-tree --no-commit-id --name-status -r <source-sha>` (все восемь вызовов exit 0). После переноса `git status --short -- frontends services nginx` завершился с exit 0 и показал только ранее зафиксированные чужие изменения в `frontends/AGENTS.md`, `services/AGENTS.md` и две inactive-копии. Статус task-specific evidence paths завершился с exit 0 и был пустым перед добавлением этой note.
- Независимый whole-source Sol review после final frontend correction передан root как PASS; в MAIN integration изменённые product файлы повторно не редактировались.

## Runtime и ограничения

MAIN integration не запускала приложения и не повторяла frontend, Gradle или Docker проверки. В author evidence нет genuine Telegram host download/save callback и нет end-to-end report fetch; unit/fake-host tests и TMA bundle этого не доказывают. Auth/Gateway evidence содержит targeted service tests и изолированный NGINX parse, но не live NGINX request. Отдельный runtime task `SOURCE938` не затрагивался. Push, deploy и production changes не выполнялись.

До передачи исходный dirty state в целевых каталогах ограничивался чужими AGENTS/inactive-файлами, перечисленными выше; они сохранены. Cherry-pick завершились без конфликтов. Итоговую MAIN revision интегратор сообщает отдельно, потому что она включает commit этой evidence note.
