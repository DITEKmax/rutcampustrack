# PROGRESS — Vue web redesign

Срез2026-10-24 Europe/Moscow. Существующий draft [PR3](https://github.com/DITEKmax/rutcampustrack/pull/3), ветка `codex/web-vue-redesign`.
Goal запущен после readiness; [контракт](GOAL-CONTRACT.md) сохраняется.
Текущий опубликованный checkpoint `a6c68a63974de3f14d78147a9c990a497ada22ee`; UI/shipping sourcea81394473. Функциональные role receipts сохраняют собственные точные revisions, включая assembled3f585; test-edge/oracle deltas3fc/485 source-reviewed отдельно.

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
  закончить outer routing/build/oracle wiring, проверить изменённые
  границы поставки. Единая runtime HTML-галерея уже доступна из PR; новые принятые состояния добавляются по мере проверки.
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
Добавлены80 STUDENT,44 свежих HEADMAN,24 ADMIN и12 адресных mobile theme кадров
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
после отказа review; эти команды не запускались. Q-ENV-005 stop/privateDBsnapshot и Q-ENV-006 три historicalPROMOTEinputs также ждут явных ответов; оба отказа были до execution, no bypass. Независимая работа продолжается.
Точные причины и подготовленные сценарии — [OPEN-QUESTIONS](OPEN-QUESTIONS.md).

CI/Coverage/Security/OpenAPI **PAUSED**, только workflow_dispatch, не PASS.
Нет main writes, merge, deploy, production, firewall или глобальной очистки
Docker. Существующий PWA WebPush сохранён; web channel settings не возвращены.
Собственный student export отложен; пользователей окончательно не удаляем;
teacher grants и запрет отменённых функций сохранены.

Три настоящих Vue images и inner static nginx checks прошли: [shipping checkpoint](SHIPPING-CHECKPOINT-20261024.md). Там же initial403/readability fix и disk/VFS incident с independently reviewed exact-ID recovery. Эти проверки не означают завершение outer production wiring или удаления Angular.

Clock update: own5infra now genuinely healthy after reviewed public-file modes/Mongo key/Rabbit limits/MongoCPU corrections; existing healthcriteria retained. Phase2 stop/snapshot was rejected before execution by automatic approval review; main13 never stopped, no snapshots/import/apps/browser. Q-ENV-005 waits explicit answer; natural mainGEO window remains an alternative. CurrentPROMOTE preview409 reflects missing completedSPRING (legacy1typeNULL); no positive execution is claimed. Four fresh guest-auth render samples with original asset have been added separately.


Checkpoint04:54UTC: root4851387b7 integrates source-reviewed Vue e2e root-edge
wiring and strict served-module oracle (original05e08a2e3/8b8f3c7ac). Harness8
unittest authorPASS/config0 are narrow source/harness evidence, not outer
shipping acceptance. Current policy7 still lacks www.figma.com and curl returns
CONNECT403 after owner's promise; MCP/original asset proof remains separate.
PROMOTE3-input packetbf13 reviewed PASS, but automatic approval blocked the
operational command before execution; SQLattempts0. Q-ENV-006waiting, no bypass,
no-writerwindowCLOSED. Counts remain115/117 and110/117; gallery now200 retained
real render samples including4 latest guest-auth forms, not200 actiontests.


Gallery delivery: [portable HTML and instructions](../../validation/web-vue-pr3/runtime-gallery/PORTABLE.md)
contains200 searchable metadata records and lazy original PNG URLs pinned51e46f715.
LocalHTTP browser filters/emptysearch/history and realPNG render PASS via TLS-verified
sessionCA relay; directChromiumCA/file restrictions and prior helpercleanup failure
are explicit in portable-check.json. All200 localPNG hashes/dimensions/IDs/safe
paths independently matched manifests. No hosting/deploy/newGoal, no actioncount increase.
[PROMOTE safe denied checkpoint](../../validation/web-vue-pr3/checkpoints/promotion-20261024/SAFE-DENIAL-CHECKPOINT.md)
confirms SQLattempts0/main13healthy/foreignunchanged after automaticdenial.
