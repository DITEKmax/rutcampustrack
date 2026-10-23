# PROGRESS — Vue web redesign

Срез 2026-10-24 Europe/Moscow. Существующий draft [PR3](https://github.com/DITEKmax/rutcampustrack/pull/3), ветка `codex/web-vue-redesign`.
Владелец отдельно запустил Goal после readiness; [контракт](GOAL-CONTRACT.md) сохраняется.

## Функциональный прогресс Vue web

**Implemented 5/117 (4,27%). Locally verified 4/117 (3,42%).**
Знаменатель 117 active не уменьшен. Counts относятся к принятым действиям,
а не к ширинам, темам, screenshots или числу компонентов. Source implementation
и достаточное локальное подтверждение показаны отдельно. Это промежуточный
пакет; полная замена Angular и новая реализация целиком ещё не готовы.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH | 6 | 0/6 (0%) | 0/6 (0%) |
| SHARED | 15 | 3/15 (20%) | 3/15 (20%) |
| STUDENT | 16 | 2/16 (12,50%) | 1/16 (6,25%) |
| HEADMAN | 38 | 0/38 (0%) | 0/38 (0%) |
| TEACHER | 8 | 0/8 (0%) | 0/8 (0%) |
| ADMIN | 34 | 0/34 (0%) | 0/34 (0%) |

Учтены роль, logout, выбор темы, просмотр/собственная отметка ДЗ. Для просмотра
ДЗ ещё ожидается адресная runtime проверка day/month/navigation/link/keyboard;
week уже подтверждён. Password login/session работают в настоящем браузере,
но AUTH пока не закрыт: original auth asset и полный role bootstrap acceptance
ожидаются. Подробности и evidence — [первый пакет](FIRST-PACKAGE-CHECKPOINT.md).

## Текущая работа

Первый shared foundation/password/session/homework пакет интегрирован до
072edf183, независимые source findings исправлены и recheck PASS. Общая
light/dark/system основа PWA/TMA и Auth device mirror интегрированы. Настоящий
PWA app подтвердил сохранение темы между logout/login и сменой аккаунта.
Полная матрица состояний существующих мобильных экранов и signed Telegram
admission пока не подтверждены; это не скрывается под общим PASS.

Далее параллельно: AUTH/SHARED/STUDENT — web_foundation; HEADMAN38 — headman_web;
ADMIN34 — admin_web; TEACHER8 — teacher_web. Отдельные worktrees, ownership в
[SLOTS](../../../.agent/orchestration-v2/SLOTS.md). Common registry/config/lock —
web owner; tokens/mobile — theme owner; API/generated — backend owner; docs — root.
Новые source-ready role пакеты не засчитываются до достаточной приёмки.

## Настоящий стенд и галерея

В изолированный стенд добавлены настоящие Schedule, Attendance, Notification
с нужными synthetic DB/queue зависимостями; 12 контейнеров healthy. Auth,
Academic, Gateway, Mobile BFF сохранены. Реальные login/session/homework write/
readback, schedule/Today/unread API теперь отвечают успешно. API Today200 не
означает подтверждение полного нового web экрана «Сегодня». Renderer для
настоящих exports добавляется; фиктивных PDF/успехов нет.

[HTML-галерея](../../validation/web-vue-pr3/runtime-gallery/index.html) содержит
20 runtime samples с точными revisions: auth, homework populated/completed,
8 ширин и обе темы. [2 targeted hover samples](../../validation/web-vue-pr3/runtime-gallery/hover-a2ef210623/index.html)
добавлены отдельно после исправления контраста, старые изображения сохранены.
В каждой записи ID, screen/state/theme/width/revision/описание; рядом portable
ZIP для открытия HTML со всеми PNG без deploy. Reference/Figma не выдаётся за runtime.

## Блокеры и вопросы

- Q-ENV-002: владелец ответил «Разрешу www.figma.com». В последнем фактическом
  probe policy revision7 host ещё отсутствует; proxy CONNECT403. Figma MCP
  context/screenshot работает, загрузка original assets пока блокируется.
- Q-PROD-004: окончательное удаление группы — судьба участников и групповых
  STUDENT/HEADMAN grants. Ответ ожидается; независимые действия продолжаются.
- Admin password generation: backend source/tests готовы к независимому review,
  web UI не принят. Принятый group final deletion остаётся implementation gap.
- Server groupId filter пользователей отсутствовал; совместимое дополнение в работе.

Figma read-only; ZIP полностью проверен; MCP/gh/API/Git доступны. Старые
pending/offline сведения не относятся к этой машине. CI/Coverage/Security/
OpenAPI **PAUSED**, только workflow_dispatch, не PASS. Нет main writes, merge,
deploy, production или глобальной очистки Docker. Angular остаётся до полной
замены возможностей и будет удалён из активной поставки после неё.
