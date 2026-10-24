# PROGRESS — Vue web redesign

Срез 2026-10-24 Europe/Moscow. Существующий draft [PR3](https://github.com/DITEKmax/rutcampustrack/pull/3), ветка `codex/web-vue-redesign`.
Владелец отдельно запустил Goal после readiness; [контракт](GOAL-CONTRACT.md) сохраняется.

## Функциональный прогресс Vue web

**Implemented 26/117 (22,22%). Locally verified 26/117 (22,22%).**
Знаменатель 117 active не уменьшен. Counts относятся к принятым действиям,
а не к ширинам, темам, screenshots или числу компонентов. Source implementation
и достаточное локальное подтверждение показаны отдельно. Это промежуточный
пакет; полная замена Angular и новая реализация целиком ещё не готовы.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH | 6 | 6/6 (100%) | 6/6 (100%) |
| SHARED | 15 | 10/15 (66,67%) | 10/15 (66,67%) |
| STUDENT | 16 | 2/16 (12,50%) | 2/16 (12,50%) |
| HEADMAN | 38 | 0/38 (0%) | 0/38 (0%) |
| TEACHER | 8 | 8/8 (100%) | 8/8 (100%) |
| ADMIN | 34 | 0/34 (0%) | 0/34 (0%) |

Учтены AUTH6, роль/logout/тема, профиль/история/аватар/пароль/сессии7,
просмотр/собственная отметка ДЗ и TEACHER8. Просмотр ДЗ
дополнительно подтверждён для дня/недели/месяца, навигации, ссылки, клавиатуры
и root20. Реальная публикация STOMP появляется при hover и сохранённом focus.
AUTH/shared приняты после независимого af00 recheck; TEACHER8 — полного
review и узких210/840 rechecks. Root combined strict build PASS.
HEADMAN/ADMIN ещё проходят оставшиеся gates. Актуальные revisions,
evidence и оставшиеся gates — [checkpoint](INTEGRATION-CHECKPOINT-20261024.md).

## Текущая работа

Первый shared foundation/password/session/homework пакет интегрирован,
независимые source findings исправлены и recheck PASS. Общая
light/dark/system основа PWA/TMA и Auth device mirror интегрированы. Настоящий
PWA app подтвердил сохранение темы между logout/login и сменой аккаунта.
Полная матрица состояний существующих мобильных экранов и signed Telegram
admission пока не подтверждены; это не скрывается под общим PASS.

Далее: оставшиеся SHARED — web_foundation; HEADMAN38 — headman_web;
ADMIN34 — admin_web; оставшиеся STUDENT14 — teacher_web в новом student-web worktree. Отдельные worktrees, ownership в
[SLOTS](../../../.agent/orchestration-v2/SLOTS.md). Common registry/config/lock —
web owner; tokens/mobile — theme owner; API/generated — backend owner; docs — root.
Новые source-ready role пакеты не засчитываются до достаточной приёмки.

## Настоящий стенд и галерея

В изолированный стенд добавлены настоящие Schedule, Attendance, Notification
с нужными synthetic DB/queue зависимостями; 13 контейнеров healthy. Auth,
Academic, Gateway, Mobile BFF сохранены. Реальные login/session/homework write/
readback, schedule/Today/unread API теперь отвечают успешно. API Today200 не
означает подтверждение полного нового web экрана «Сегодня». Renderer с настоящим
LibreOffice/Poppler выдаёт PDF, HTML и XLSX; bytes и содержимое проверены.

[HTML-галерея](../../validation/web-vue-pr3/runtime-gallery/index.html) содержит
20 runtime samples с точными revisions: auth, homework populated/completed,
8 ширин и обе темы. [2 targeted hover samples](../../validation/web-vue-pr3/runtime-gallery/hover-a2ef210623/index.html)
добавлены отдельно после исправления контраста, старые изображения сохранены.
[Преподаватель:14runtime samples](../../validation/web-vue-pr3/runtime-gallery/teacher-20261024/index.html)
содержит8 исправленных кадров журнала/пары и6 адресных кадров review corrections.
В каждой записи ID, screen/state/theme/width/revision/описание; рядом portable
ZIP для открытия HTML со всеми PNG без deploy. Reference/Figma не выдаётся за runtime.

## Блокеры и вопросы

- Q-ENV-002: владелец ответил «Разрешу www.figma.com». В последнем фактическом
  probe policy revision7 host ещё отсутствует; proxy CONNECT403. Figma MCP
  context/screenshot работает. Original auth PNG получен read-only через
  Figma image bytes, SHA1 совпадает с image hash; auth asset больше не блокер.
- Q-PROD-004: окончательное удаление группы — судьба участников и групповых
  STUDENT/HEADMAN grants. Ответ ожидается; независимые действия продолжаются.
- Q-ENV-003: ожидается отдельный ответ на конкретный проверяемый сценарий user8
  после отказа automatic approval review. До ответа временный FULL/self-reset
  и неизвестный результат генерации в этом сценарии не запускаются.
- Admin password generation backend прошёл независимое review; web UI ещё
  не принят целиком. Group final deletion остаётся implementation gap.
- Server groupId/status filter и async export исправление интегрированы и
  независимо проверены; immutable rollout и настоящий filtered async export PASS. Helper CAS
  и минимальное lookup дополнение без расширения GROUPS_VIEW в работе.
- Полное удаление схемы требует проверки cascade всех версий и GC файлов;
  опубликование ABSENT не считается окончательным удалением.

Figma read-only; ZIP полностью проверен; MCP/gh/API/Git доступны. Старые
pending/offline сведения не относятся к этой машине. CI/Coverage/Security/
OpenAPI **PAUSED**, только workflow_dispatch, не PASS. Нет main writes, merge,
deploy, production или глобальной очистки Docker. Angular остаётся до полной
замены возможностей и будет удалён из активной поставки после неё.
