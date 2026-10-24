# Передача в другую облачную среду — PR3

Актуальный checkpoint2026-10-24 05:07UTC: существующий Goal запущен владельцем,
машина работает; PR3/codex/web-vue-redesign опубликован наa6c68a639. Это текущий
срез; ниже явно сохранена история прежней машины/readiness, а не действующийSTOP.
Всегда fetch актуальный remoteHEAD, не откатывать ветку к указанномуSHA.

- Реально проверены shell/Git fetch/push, gh/GitHub API и MCP по отдельности.
  Main13healthy: Auth/Academic/Gateway/BFF/Schedule/Attendance/Notification/
  Renderer и инфраструктура. Настоящие login/Today/data/live updates/Renderer
  и runtime screenshots подтверждены в [приёмке ролей](ROLE-ACCEPTANCE-20261024.md).
- ZIP целиком восстановлен из3wrapperfiles:87568617bytes, ожидаемыйSHA256,
  CRC/safe paths/manifest422entries,388PNG+34,33screens/8width и140 ожидаемых
  узких light gaps PASS. Наличие только имени архива не использовалось как proof.
- Figma read-only MCP design context/screenshot и originalauthbytes работают.
  Owner обещалwww.figma.com; фактическая enforcedpolicy7 его пока не содержит,
  настоящий curl через наследуемыйproxy даётCONNECT403. Это разные доступы.
- Implemented115/117 и locallyverified110/117. Общий HTML200runtimeкадров
  [доступен из PR](../../validation/web-vue-pr3/runtime-gallery/PORTABLE.md),
  оригинальные PNG/IDs/hash/revision сохранены; это не200functionaltests.
- SourceUIa813:3реальных images/innerstaticWEB12/PWA6/TMA4 PASS. Source-reviewed
  Vue e2e rootedge и строгий artifactoracle интегрированы. Angular ещё активен:
  outeredge/legacycontrols/окончательная замена остаются deliverygates.
- Ownclock5infrahealthy после reviewedboundedкоррекций. Stop/privateDBsnapshot
  отклонён automaticapproval ДО исполнения; main13 не останавливались,
  dump/restore/apps/14250/browser отсутствуют. snapshotFALSE,QENV005pending.
  HistoricalPROMOTE3inputsetup также отклонён доSQL,QENV006pending, attempts0.
- QPROD004 membership/grants и QENV003/004 exactограниченные проверки ждут
  владельца. Не обходить отказ/не запускать другимexecutor. NaturalmainGEO
  допускает обычную проверку05:25..06:55UTC при actualACTIVE/allowed.
- Root docs/coordination/Git; writers/worktrees/ports — [CURRENT/SLOTS](../../../.agent/orchestration-v2/CURRENT.md).
  ОдинHEAVY,16GiB/4CPU. После VFSdiskfull exact30ownunusedcache recovery
  сохраненыforeignstate/images/volumes; около5GiBfree послеclockprepare.

Канон: [GOAL-CONTRACT](GOAL-CONTRACT.md), [PROGRESS](PROGRESS.md),
[вопросы](OPEN-QUESTIONS.md), [shipping/инцидент](SHIPPING-CHECKPOINT-20261024.md).
CI/Coverage/Security/OpenAPI PAUSED/workflow_dispatch, неPASS. Без main writes,
merge/deploy/production/firewall/globalDockercleanup. Текущая работа не объявлена
завершённой по результатам настройки среды.

## Исторический handoff прежней машины и стартовой readiness

Снимок: **2026-10-24 00:11:25 Europe/Moscow**. Это только подготовка переноса:
новая среда/задача/Goal/PR не создавались, старые ресурсы не удалялись.
Продолжать существующую задачу по [GOAL-CONTRACT.md](GOAL-CONTRACT.md).

## Git и подтверждённый результат

- Repo: https://github.com/DITEKmax/rutcampustrack
- Рабочая ветка: `codex/web-vue-redesign`; существующий **draft PR3 → main**:
  https://github.com/DITEKmax/rutcampustrack/pull/3
- HEAD перед добавлением этого handoff:
  `1a1546cc3beecfaaf0e345b0670073c1fc7790ac`.
  Подготовительный HEAD: `4ae4e69ec42d3856c971db6727388eb2540c2434`.
  Base main, подтверждённая PR на этом срезе:
  `aa98a6aabd1463ee72ce2ef09a3195fdeef7b74f`.
  После fetch брать **актуальный HEAD рабочей ветки**, включая этот документ;
  указанные SHA — provenance, не повод потерять более новые commits.
- В Git сохранены новое GO, карта действий, решения владельца, Figma node refs,
  результаты API/theme/realtime исследования, план продолжения и диагностика.
  **Продуктовый код этой итерации не изменён. Checkout не проверен и не создан
  агентом; install/build/typecheck/backend/browser/runtime не выполнялись.**
- [SCREEN-ACTION-MAP.csv](SCREEN-ACTION-MAP.csv):123 строки/16 полей;
  **117 active** (AUTH6, SHARED15, STUDENT16, HEADMAN38, TEACHER8, ADMIN34),
  1 candidate,1 deferred,4 excluded;121 planned,2 blocked. Связаны147 canonical
  story IDs; ACTION IDs уникальны, code paths проверены,34 screen references
  связаны с прочитанными Figma IDs. Это provisional denominator.
- [PROGRESS.md](PROGRESS.md): **implemented0/117; locally verified0/117**.
  Итоговый процент пока не вычисляется. Vue runtime HTML-галереи нет;
  source inspection/Figma screenshots не являются runtime evidence.
- Завершённые агенты `api_inventory` и `theme_analysis` работали на
  gpt-6.1-sol/high, вернули ownership root. Первый сохранил карту и source
  resolution; второй — исследование77 mobile PCSS и device-theme proposal,
  сохранённый в [THEME-CONTRACT.md](THEME-CONTRACT.md). Продуктового независимого
  review ещё не было. Активных дочерних writers/heavy процессов нет.

## Решения и оставшиеся технические задачи

| ID | Решение владельца |
|---|---|
| Q-PROD-001 | Web: колокольчик, история и всплывающие уведомления; без настройки каналов. Mobile preferences сохраняются. |
| Q-PROD-002 | Собственный student attendance export — отдельная идея позже. |
| Q-PROD-003 | Пользователи: только обратимая архивация/восстановление; без окончательного удаления. |

Канон ответов: [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md). Неразрешённый candidate —
дополнительные поля разовой правки пары сверх room/transfer; не изобретать их.
Teacher только read/export: текущая разрешённая группа **AND назначенный предмет**
(D023). Headman/helper/admin действуют по текущим grants. Login tokens и
authoritative context разделены (D020). QR: web выдаёт, PWA/TMA подтверждают.
Отменённые bulk marks/cancel, thresholds/red-zone/free-attendance и standalone
снятие старосты не возвращать из legacy.

Reuse API/domain: `frontends/mobile-core/src/api/student-client.ts`,
`src/features/{headman-*,teacher,admin-*,notifications}/`,
`src/shared/report-download-client.ts`, `src/shared/identity-operation.ts`;
auth adapter сейчас `frontends/pwa-vue/src/auth-client.ts`.
Все `src/` в перечислении mobile-core относятся к `frontends/mobile-core/`.

Существенные gaps:

- **ACT-ADMIN-GROUP-FINAL-DELETE**: принятая операция, legacy DELETE не имеет
  точного preview/password/durable cascade. Требуется совместимое дополнение.
- **ACT-ADMIN-REGENERATE-PASSWORD**: принятая вторая recovery операция;
  отдельный современный контракт генерации пока не найден.
- Subject deletion уже имеет preview/challenge/idempotency/status (D029/D030);
  не переносить старый force-delete. Unknown mutation сначала reconcile/readback.
- Realtime transport существует: STOMP/SockJS `/api/ws` +
  `/api/auth/ws-ticket`, private user/group/headman/admin topics, reconnect.
  Invalidation всех journal/homework/schedule reads между клиентами не доказана;
  toast/unread refresh не заменяют domain refresh.
- Theme controller/localStorage существуют, но PWA/TMA locked dark, MapScreen
  получает dark явно, light aliases ограничены Appearance, Telegram source
  отсутствует. Нужны нейтральные Onest/tokens/type/responsive PCSS mixins,
  mobile light/SVG audit и небольшой device persistence API. GET/PUT
  `/api/auth/device-preferences` в THEME-CONTRACT — **proposal, не готовый API**.
  Изменения API затем в API-CHANGES.md с реальной DB проверкой/compatibility.
- Новые web функции не переносить молча в mobile; фиксировать MOBILE-FOLLOWUPS.
  API-CHANGES/MOBILE-FOLLOWUPS/VALIDATION ещё не созданы: реальных изменений/проверок нет.

## Карта документов в Git

Все ссылки ниже — `docs/plans/web-vue-pr3/`, если не указано иное:

- [README](README.md), [GOAL-CONTRACT](GOAL-CONTRACT.md),
  [OWNER-BRIEF](OWNER-BRIEF.md), [OWNER-MESSAGES](OWNER-MESSAGES.md),
  [IMPLEMENTATION-PLAN](IMPLEMENTATION-PLAN.md).
- [PROGRESS](PROGRESS.md), [SCREEN-ACTION-MAP](SCREEN-ACTION-MAP.csv),
  [OPEN-QUESTIONS](OPEN-QUESTIONS.md), [START-REPORT](START-REPORT.md),
  [THEME-CONTRACT](THEME-CONTRACT.md), [CLOUD-ENV-DIAGNOSTICS](CLOUD-ENV-DIAGNOSTICS.md).
- [CI-DEVELOPMENT-POLICY](CI-DEVELOPMENT-POLICY.md),
  [WORKFLOW-ORIGINAL-TRIGGERS](WORKFLOW-ORIGINAL-TRIGGERS.json),
  [DESIGN-PREFERENCES-FROM-PWA-TMA](DESIGN-PREFERENCES-FROM-PWA-TMA.md),
  [INPUT-REVIEW](INPUT-REVIEW.md), [logical-screens](logical-screens.csv),
  [BRANCH-HANDOFF](BRANCH-HANDOFF.json), [HOW-TO-SEND](HOW-TO-SEND.md).
- [AGENTS](../../../AGENTS.md), [.agent RULES](../../../.agent/orchestration-v2/RULES.md),
  [CURRENT](../../../.agent/orchestration-v2/CURRENT.md),
  [SLOTS](../../../.agent/orchestration-v2/SLOTS.md),
  [frontends/AGENTS](../../../frontends/AGENTS.md), [agent-workflow](../../agent-workflow.md).
  Читать применимые services/tests AGENTS и skills перед соответствующими правками.
- Canonical product: [READINESS](../../product/READINESS.md),
  [copied147-story source](../../research/reference-rutcampustrack-design/knowledge/job-stories.md)
  и [dated decisions](../../product/decisions/).
  Старые PREPARATION/STOP/model settings заменены новым GO этой задачи.

## Figma и ZIP: отдельно от Git

Figma: https://www.figma.com/design/VgVjQYWILLG9AC7Eh12VMk/ui-kit-rct
Roots: auth1474:4; student1614:7095; headman1902:253474;
teacher2438:1198; admin2500:4187; icons4159:142.
Реально получены auth password1550:3168/1548:1936, OTP1550:3506, QR1550:3773,
recovery1550:4062, new password1550:4351, student attendance1740:231839
design contexts со screenshots. Большие root metadata читались один раз;
student/headman для реализации разбирать на small child nodes.
Историческая Figma session: `73f7cb28-7446-4b7d-ab44-1bfe809da1d4`.
**Этот ID не доказывает доступ нового чата.** Проверить собственный connector
read/session по Figma skill; runtime cache/полные ответы этого чата не лежат в Git.
React/Tailwind reference адаптировать в Vue/strict TS/PCSS, временные asset URLs
не использовать в runtime. Figma read-only.

ZIP **прикреплён только к текущему чату**, не в Git/LFS:
`web-vue-handoff-20261023.zip`, Sediment
`sediment://file_00000000647c81f4b89679ed330c6603`.
Ожидаемый revision3: **87 568 617 bytes**, SHA256
`0f851394e9c8031e252cbff57fbb6bd277cdcce5e59a6cff7e3b555b6654f3cc`.
Ожидаются422 entries:388 PNG +34 handoff files.
**Bytes/manifest/CRC/SHA агентом не проверены: download_file требует workspace.**
33 вида ×8 widths ×2 themes=528 visual сочетаний;140 узких light экспортов
для non-auth отсутствуют по дизайну, строить по dark композиции.
Clone Git не доставляет вложение. Новый чат должен отдельно получить доступ
к ZIP/его повторному вложению и проверить фактические size/hash/safe paths/manifest;
доступ к Sediment pointer из другого чата не гарантирован. PNG в Git ради обхода
executor не переносить. Не искать Windows origin path внутри /workspace.

## Среда, Goal и безопасное продолжение

На00:11:25 МСК `environment_status`:
ID `ccarenv_b64_Y2NhcmVudl9mYmQ1MDA5ZDk3NTg4MTkxYTEyZTk2ZTZmYWU3MmZlZA`,
desired=running, observed=pending, connectivity=offline, observations_current=true,
spec/observed revisions17/17, failure=null; network policy unrestricted/state unknown.
Diagnostics history_status=not_found, records=[], network_diagnostics=null;
operation ID/reason/recommended recovery не предоставлены.
`pwd`: **No usable execution environment is available. Wait for an environment
to become available before using this tool.**
Прежний wait того же ID (`cell_id=9`) повторно вернул Script running; это локальный
handle ожидания, **не portable provider operation ID**.
Публикация владельцем ещё не подтверждена; по одной кнопке Publish причину
не выводить. На screenshots конфигурация сохранена, статуса VM нет.

Goal в этом чате подтверждён автоматическим продолжением; последнее подтверждение
API — outcome blocked из предыдущего turn по Q-ENV-001. В этой подготовке
состояние Goal не изменялось. Goal lifecycle, Figma auth/session и вложение ZIP
**не переносятся автоматически вместе с Git**; не заявлять новую активацию
по наличию GOAL-CONTRACT.md и не создавать дубликат цели/PR.

При рабочем shell: проверить actual cwd/files/WIP/network policy/tool versions;
fetch и checkout актуальной рабочей ветки без reset/clean/force; получить ZIP;
выделить worktrees с sole writers и owners contracts/types/tokens/configs/lockfile/docs.
Измерить ресурсы до heavy lease; изолировать DB/queues/topics/ports/volumes,
использовать synthetic data и подходящий существующий standalone harness.
В этой итерации стенды/worktrees/volumes не создавались; фактическое старое
состояние VM не осмотрено, удалять/очищать его нельзя.

Далее: Vue auth/shared shell + neutral foundation; первый законченный
student homework read/completion путь с backend, обеими темами/адаптивом,
local evidence и независимым review; затем независимые role/mobile пакеты.
Root/lead/reviewer не пишут продуктовый код. Главная модель по GO Sol6.1/ultra;
выбор детей делегирован владельцем, runtime slots и независимость review обязательны.
**CI/Coverage/Security/OpenAPI PAUSED (только workflow_dispatch), не PASS.
Без merge/deploy/production.** Сохранять паузу при любой интеграции/Angular removal.
