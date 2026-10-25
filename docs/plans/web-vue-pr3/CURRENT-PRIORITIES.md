# Приоритеты существующего Goal PR3

25 октября2026. Ветка `codex/web-vue-redesign`, PR №3. CI/Coverage/Security/OpenAPI
остаются PAUSED и только workflow_dispatch. Без merge, deploy и production.

| Ось | Фактический результат |
|---|---|
| Реализация |117/117 source implementations сверены с accepted map и Vue/API. Все117 active ID сохранены |
| Поведение |116/117 accepted; ADMIN33/34. Полный group UI — Ubuntu владельца, NOT_RUN0/1 |
| Визуал |289 реальных PNG.33 WEB views и50 mobile consumers;45 mobile имеют bounded observations,5 unchecked. Общего процента нет |
| Angular |433 tracked файла удалены вместе с активными зависимостями и привязками; полезные гарантии сохранены, source review PASS |
| Сборка |WEB/TMA strict+Vite PASS; PWA `/app/` и TMA `/mini-app/` prefix builds PASS. После config-only MIME исправления приложения не пересобирались |
| Общий Nginx |Actual static/deep links/refresh/MIME/cache/CSP/404 PASS для3приложений; реальный anonymous API. PWA HTTP Auth/API/STOMP PASS; WEB bounded result с сохранённым raw FAIL теста ID |

## Облачные пакеты закрыты в указанных границах

Исправлен подтверждённый MIME PDF worker `.mjs` во всех3приложениях; отсутствующий
asset возвращает404, стандартная MIME table и кэш сохранены. Повтор HTTP gate был
обоснован этим config change. Source/runtime evidence на
`240a1b2a86a2b2ba0cbb89647a9ed8b151007007`:
[Nginx](../../validation/web-vue-pr3/checkpoints/assembled-nginx-20261025/README.md).

WEB native Auth/current canonical/schema/STOMP и own cleanup подтверждены отдельно.
Raw EXIT1/completedfalse сохранён: проверка сравнила String ID BFF с number1.
WEB не повторяли. Исправленная проверка использована только для отдельного PWA
клиента: PASS, exact own DELETE204/old Bearer401, browser closed, HOLDfalse.
Один свой Nginx остановлен; все30 исходных container ID/image/state/mountsets
сохранены, после6487257088B свободно. Early2.5GiB/hard2GiB/512MiB budget сохранены.
Пароль, LIMITED, whole-map и backend group checks не повторялись.

## Следующие результаты выполняет владелец локально

1. [LOCAL-EXECUTION](LOCAL-EXECUTION.md) и публичный launcher
   [pr3-local](../../../tests/e2e/pr3-local/README.md): новый Ubuntu checkout
   `~/rutcampustrack-pr3`, namespace `rct-pr3-local`. Старый проект/activate не трогать.
   Group preview/password/original UUID/result;6 заранее открытых WEB/PWA/TMA,
   естественный отзыв доступа и сохранность чужих accounts/credentials/roles/groups.
   Unknown → статус исходной операции, без нового удаления. До output **NOT_RUN**.
2. Remaining combined runtime gates: HTTPS Auth/WSS/forwarded identity,
   PWA service-worker install/activation/update/offline, native TMA provider и
   WebPush delivery. Static/HTTP доказательства их не заменяют. Portable Nginx
   [scripts](../../../tests/e2e/pr3-nginx/README.md) учитывают отдельный API CA,
   prefixes и два exact Origin свежего локального realm; cloud Main не менялся.
3. [Конечный визуальный список](VISUAL-REMAINDER.md): conditional helper/group/manual
   в обеих темах и применимых размерах остаются unchecked с конкретными причинами.
   Один допустимый Manual roster:7records/ticket1/manual0, PNG0; это bounded absence.
   Не выдавать права и не создавать данные/страницы ради картинки или orphan.

Облачные group start/grants/delete запрещены решением владельца. Q-ENV-010 о
добавлении места для cloud group закрыт как superseded; новый объём не требуется.
Новых продуктовых вопросов нет. Ждём фактический локальный output, не новое согласие
на уже выполненные облачные работы. Успешное неизменённое evidence переиспользуется.
