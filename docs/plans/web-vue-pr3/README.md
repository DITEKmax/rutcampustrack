# Vue web-панель — существующий PR №3

Актуально25октября2026: source implementation117/117; accepted behavior116/117, owner Ubuntu group gate NOT_RUN. Angular433-file cleanup выполнен, independent source review PASS; общий Nginx/остальные browser/provider gates учитываются отдельно. Исторические численные срезы ниже не являются текущей готовностью. [Текущий план](CURRENT-PRIORITIES.md) · [Локальная передача](../../../tests/e2e/pr3-local/README.md).

> **Актуальный статус 2026-10-24:** владелец отдельно запустил существующий Goal. Автономная реализация, commit/push в существующую ветку и обновление PR №3 разрешены. Канон: [GOAL-CONTRACT.md](GOAL-CONTRACT.md). Readiness новой среды и архива: [READINESS-CHECK-20261024.md](READINESS-CHECK-20261024.md). CI/Coverage/Security/OpenAPI PAUSED; без main writes/merge/deploy/production.

Дата решений: 2026-10-23. Ветка `codex/web-vue-redesign`, база main
`aa98a6aabd1463ee72ce2ef09a3195fdeef7b74f`. Frontend source77fbd7a5e (toast/skeleton runtime), прежняя assembled base0421d344c,
release inputs8e280baef:116/117 implemented,114/117 locally verified.
Первоначальная подготовка ниже — исторический материал; существующий PR имеет №3.

[Ближайшие пакеты и условия завершения](CURRENT-PRIORITIES.md): визуал параллельно ADMIN; затем Angular/common Nginx. Действия, поведение, визуал и сборка учитываются отдельно.

## Читать по порядку

1. [OWNER-BRIEF.md](OWNER-BRIEF.md) — канон требований и принятых решений.
2. [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) — подробные действия и результаты; распределение агентов и граф зависимостей будущий чат выбирает сам.
3. [CI-DEVELOPMENT-POLICY.md](CI-DEVELOPMENT-POLICY.md) — автопроверки временно приостановлены только в этой ветке. Вернуть после отдельной команды владельца, до merge.
4. [OWNER-MESSAGES.md](OWNER-MESSAGES.md) — исходные формулировки по порядку, последние решения приоритетны.
5. [DESIGN-PREFERENCES-FROM-PWA-TMA.md](DESIGN-PREFERENCES-FROM-PWA-TMA.md) — прежние конкретные замечания владельца.
6. [INPUT-REVIEW.md](INPUT-REVIEW.md) и [logical-screens.csv](logical-screens.csv) — проверенный состав макетов и недостающие узкие light варианты.
7. [HOW-TO-SEND.md](HOW-TO-SEND.md) — будущая передача архива и текст GO; сам файл не разрешает запуск.
8. [BRANCH-HANDOFF.json](BRANCH-HANDOFF.json) и [WORKFLOW-ORIGINAL-TRIGGERS.json](WORKFLOW-ORIGINAL-TRIGGERS.json) — ветка/база и исходные event blocks.

Полный переносимый ZIP `web-vue-handoff-20261023.zip` подготовлен локально:
388 исходных PNG, manifests, выбранные Figma metadata/context/tokens и эти docs.
В Git не добавлены PNG/бинарный архив, secrets, node_modules, runtime БД.
Пути `handoff/`, `figma/`, `screens-manifest.*` в документах относятся к
входному ZIP; не считать отсутствующий здесь архив автоматически переданным cloud.
Получение архива в текущей среде уже подтверждено чтением и полной сверкой manifest; см. READINESS-CHECK-20261024. Это описание reference ZIP, не runtimeгалереи.

Во время разработки нужны адресные build/typecheck/runtime по изменению;
новые хрупкие E2E, проверки текста/верстки и coverage ради процента не нужны.
GitHub-проверки пока PAUSED, это не PASS. Продуктовый GO, merge и deploy
этим подготовительным commit не выдаются. Main и глобальные settings не менялись.

Текущий промежуточный срез: implemented116/117 и locally verified114/117.
Подробная [приёмка ролей](ROLE-ACCEPTANCE-20261024.md), [единый runtime HTML](../../validation/web-vue-pr3/runtime-gallery/PORTABLE.md), [план удаления Angular](ANGULAR-REMOVAL-INVENTORY.md).

Актуальное состояние после начала Goal: [PROGRESS](PROGRESS.md), [integration checkpoint](INTEGRATION-CHECKPOINT-20261024.md), [mobile followups](MOBILE-FOLLOWUPS.md).
