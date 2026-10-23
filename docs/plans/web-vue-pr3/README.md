# Подготовка новой Vue web-панели — будущий PR №3

Дата решений: 2026-10-23. Ветка `codex/web-vue-redesign`, база main
`aa98a6aabd1463ee72ce2ef09a3195fdeef7b74f`. Это подготовка, реализация не начата.
PR/облачный чат ещё не созданы; фактический номер PR назначит GitHub.

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
Получение архива в целевой среде нужно подтвердить чтением и сверкой manifest.

Во время разработки нужны адресные build/typecheck/runtime по изменению;
новые хрупкие E2E, проверки текста/верстки и coverage ради процента не нужны.
GitHub-проверки пока PAUSED, это не PASS. Продуктовый GO, merge и deploy
этим подготовительным commit не выдаются. Main и глобальные settings не менялись.
