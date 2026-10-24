# PROGRESS — Vue web redesign

Срез2026-10-24 Europe/Moscow. Существующий draft [PR3](https://github.com/DITEKmax/rutcampustrack/pull/3), ветка `codex/web-vue-redesign`.
Goal запущен после readiness; [контракт](GOAL-CONTRACT.md) сохраняется.
Собранный source revision `3f5852b9524245e98b16110b8fb4fde4bd68f389`.

## Функциональный прогресс Vue web

**Implemented115/117 (98,29%). Locally verified110/117 (94,02%).**
Исходные117 active действий сохранены. Ширины, темы, assertions и screenshots
не являются отдельными действиями. Это промежуточная приёмка ролей, а не
завершение полной замены Angular/поставки.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH | 6 | 6/6 (100%) | 6/6 (100%) |
| SHARED | 15 | 15/15 (100%) | 15/15 (100%) |
| STUDENT | 16 | 16/16 (100%) | 15/16 (93,75%) |
| HEADMAN | 38 | 38/38 (100%) | 38/38 (100%) |
| TEACHER | 8 | 8/8 (100%) | 8/8 (100%) |
| ADMIN | 34 | 32/34 (94,12%) | 28/34 (82,35%) |

[Приёмка ролей и семь оставшихся action gates](ROLE-ACCEPTANCE-20261024.md)
содержит точный scope, revisions, ограничения и ссылки на независимое review.
[Карта](SCREEN-ACTION-MAP.csv) и [безопасные runtime receipts](../../validation/web-vue-pr3/checkpoints/roles-20261024/manifest.json)
обновлены вместе с этим срезом. Предыдущие26/117 остаются историей первого
пакета в [integration checkpoint](INTEGRATION-CHECKPOINT-20261024.md).

Подтверждён сквозной native Late: STUDENT15 отправляет одну заявку200,
HEADMAN12 одобряет одной командой200, сервер меняет ABSENT на PRESENT с
late_checkin. В двух открытых студентских представлениях реальные STOMP/REST
показывают решение и отметку без refresh; focused/hovered DOM сохранён.
Metadata53, весь64 и остальные участники не изменены. Исправленный common
revoke удаляет и navigation, и уже открытые таблицы после отзыва grant.

## Оставшаяся работа

- STUDENT: положительная геоотметка64 в допустимом окне. Сейчас настоящие
  PLANNED/TOO_EARLY, основной стенд и browser clock не подменялись.
- ADMIN: запись прав, выполнение promotion, реальная активация семестра,
  FULL/self password generation unknown result, whole-map final deletion,
  group final deletion. Чтение/preview/cancel не засчитываются как эти записи.
- Global delivery: удалить активный Angular после замены возможностей;
  закончить Vue image/static routing/build/oracle wiring, проверить изменённые
  границы поставки и завершить доступную из PR runtime HTML-галерею.
- Mobile themes: source24 families и существующие140 visual samples проверены
  отдельно. Два P2 исправлены09e7, независимый narrow recheck12 кадров PASS.
  Общий helper завершился exit1 при последующем cleanup; это не command PASS.
  Signed native Telegram и внешняя Push delivery не заявлены подтверждёнными.

## Настоящий стенд и материалы

13healthy контейнеров изолированного `rct-pr3-setup`: Auth/Academic/Gateway/BFF,
Schedule/Attendance/Notification/Renderer и PostgreSQL/Redis/RabbitMQ/MongoDB.
Настоящий LibreOffice выдаёт PDF/HTML/XLSX; bytes и содержимое проверены.
Auth f3c03bd81 и Academic950 immutable artifacts сопоставлены исходникам;
CAS/GroupOptions950 реально развернуты. Map usage204/replay204 даёт0→1→1.
Нет mocks для отсутствующих сервисов или фиктивного успеха.

[HTML-галерея](../../validation/web-vue-pr3/runtime-gallery/index.html) сохраняет
исторические20 auth/homework,2 hover и14 teacher samples с точными revisions.
Добавлены80 STUDENT,44 свежих HEADMAN и12 адресных mobile theme кадров
с отдельными IDs/source/state/theme/width и оригинальными PNG. Старый отсутствовавший auth asset отмечен только у
исторических кадров; настоящий оригинал теперь восстановлен через read-only
Figma bytes/hash. Reference/Figma не выдаётся за runtime.

ZIP87568617bytes/SHA256/CRC/safe paths/422 entries/388PNG manifests полностью
проверен. GitHub MCP, gh/API и Git доступны независимо. Figma MCP context и
screenshot работают; `www.figma.com` пока отсутствует в фактической policyrev7,
повторный curl CONNECT403. Ответ владельца «Разрешу» получен, повторного
разрешения не требуется; сетевое применение ещё не подтверждено.

## Вопросы и границы

Q-PROD-004 ждёт решения о membership/grants при final group deletion.
Q-ENV-003 ждёт разрешения конкретного own8 FULL/self/unknown-result сценария
после отказа automatic approval review;8 заморожен.
Q-ENV-004 ждёт разрешения scoped map formatter/HTTP PostgreSQL/regression/JAR
после отказа review; эти команды не запускались. Независимая работа продолжается.
Точные причины и подготовленные сценарии — [OPEN-QUESTIONS](OPEN-QUESTIONS.md).

CI/Coverage/Security/OpenAPI **PAUSED**, только workflow_dispatch, не PASS.
Нет main writes, merge, deploy, production, firewall или глобальной очистки
Docker. Существующий PWA WebPush сохранён; web channel settings не возвращены.
Собственный student export отложен; пользователей окончательно не удаляем;
teacher grants и запрет отменённых функций сохранены.
