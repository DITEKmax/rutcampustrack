# Проверка готовности новой облачной среды — 2026-10-24

Только подготовка по отдельному запросу владельца; запуск продукта/Goal ожидается.
Новый постоянный Goal не создавался. Продуктовый код, commits, push, PR metadata,
main, workflow triggers и production не изменялись. Документы этой проверки
сохранены локально, без commit/push. Историческое GO в старых документах не
снимает текущую паузу до отдельного запуска владельцем.

## Figma из текущего чата

PASS: после чтения `figma-design-to-code` создана новая read-only session,
`get_design_context` реально вернул reference code, tokens, описания компонентов
и inline screenshot узла `1550:3168` файла `VgVjQYWILLG9AC7Eh12VMk`.
Узел — «ПК-001 / пароль · 1440×1080 · light», шрифт Onest.
Screenshot просмотрен. Архивные metadata и старый session ID не использовались
как доказательство доступа. Figma не изменялась. Полные ответы сохранены в
истории текущего чата; это reference, не runtime новой web-панели.

## GitHub, Git и рабочая база

- MCP: PR3 прочитан, OPEN/draft, base `main`, head `codex/web-vue-redesign`.
  Независимый запрос remote ref через MCP вернул тот же HEAD. Подключённый
  пользователь DITEKmax; repository permissions pull/push/maintain/admin=true,
  collaborator permission=admin. Инструменты обновления существующего PR/ветки
  доступны; реальная запись намеренно не выполнялась до Goal.
- Машина: `gh pr view 3 --repo DITEKmax/rutcampustrack --json ...` PASS;
  отдельный `curl https://api.github.com/repos/DITEKmax/rutcampustrack/pulls/3`
  вернул HTTP200. Это отдельные проверки от MCP.
- Git: `git fetch origin codex/web-vue-redesign` PASS; `git ls-remote` подтвердил
  рабочую ветку и `refs/pull/3/head`. Затем получен local remote-tracking ref и
  создана только локальная ветка от него вместо исходного detached checkout.
- Проверенный HEAD всех трёх путей:
  `37a15451c1a5647e5267f5ebf2fa388a349e61c1`.
  Base main: `aa98a6aabd1463ee72ce2ef09a3195fdeef7b74f`.
- Cwd `/workspace/rutcampustrack`, локальная ветка `codex/web-vue-redesign`.
  Исходный checkout чистый; WIP после проверки — только перечисленная ниже
  readiness-документация. Reset/clean/force push не выполнялись.

Первые shell network/socket вызовы были запрещены sandbox; повтор с явным
`additional_permissions.network.enabled=true` прошёл. Прокси и TLS-проверка
сохранены. Доступность MCP сама по себе не использовалась для вывода о Git/API.

## Настоящая машина и сетевые границы

Новая environment
`ccarenv_b64_Y2NhcmVudl8yOTBhZTA0MWMyMGM4MTkxYTI3ZTQxNWM5MWFjMzYzZA`
дождалась ready; `environment_status` вернул current observations,
running/connected, failure=null. Реальные `pwd`, Git, версии, сборка, Docker и
браузер исполнены. Старые pending/offline относятся к другой машине.

Фактические лимиты cgroup: RAM16GiB; CPU `400000 100000`, то есть квота4CPU
(nproc показывает5). Свободно около30GiB на workspace и8.9GiB на tmp.
Пользователь agent/UID1000. Один heavy lease у root; параллельных стендов нет.

`/etc/codex/network-policy.json`: version1, restricted HTTP через наследуемый
`proxy:8080`, в списке разрешены GitHub/API/package registries; VPN=false,
TCP domains/IP ranges пусты. `environment_status.network_policy.state=unknown`,
поэтому состояние enforcement не объявляется подтверждённым. Нужные Git/API
вызовы проверены фактически. Секретные значения не читались/не выводились.

Использованы `/workspace/rct-pr3-setup/RESULT.md`, env.sh, compose.sh,
start-backend.sh, api-smoke.py, frontends.py, screenshot.cjs, browser-login.cjs
и stop.sh. Отдельный файл сохранённого `start_skill` в доступных locations не
найден; RESULT описывает его назначение, рабочие helper scripts доступны.

Версии: Temurin JDK/javac21.0.12.1, Gradle8.12, Node24.19.0/npm11.9.0,
Docker daemon28.4.0/Compose2.40.3, Chromium154.0.8037.57/Playwright1.59.1.

## Минимальный runtime

Адресная offline-команда для четырёх `bootJar` (Auth/Academic/Gateway/BFF),
`--no-daemon --max-workers=2 -x generateGitProperties`: BUILD SUCCESSFUL,
48/48 actionable tasks UP-TO-DATE. Подготовленные JAR соответствуют текущим
исходникам/входам Gradle. Зависимости не переустанавливались, tests/full suite
и GitHub workflows не запускались.

Стенд `rct-pr3-setup` поднят штатным helper: все7 контейнеров healthy —
PostgreSQL, Redis, RabbitMQ, Academic, Auth, Gateway, Mobile BFF. Network
`rct-pr3-setup_private_net`, собственные именованные data volumes; фиксированных
container_name нет. Снаружи опубликован только Gateway127.0.0.1:18080.
PWA/TMA dev-процессы подняты с ownership/PID stamps на15175/15176.

API smoke PASS: настоящий synthetic student login; authoritative roles
STUDENT/HEADMAN; запись активной STUDENT роли; BFF student session; реальный
Academic group roster; logout. Tokens/credentials не печатались.

Chromium/Playwright действительно открыл PWA; проверены login controls,
создан и визуально просмотрен PNG1440×1192, pageErrors=0. Повторная адресная
проверка браузера дождалась завершения login/bootstrap, потому что старый
helper делал screenshot через1.5s и снимал промежуточное «Проверяем…».
Браузерные login/auth session/BFF session — HTTP200; permissions — HTTP200.
Получены реальные `/api/v1/student/schedule` HTTP503 и
`/api/notifications/inbox/unread-count` HTTP500. После ожидания UI показывает
«Schedule Service временно недоступен», screenshot1440×1000 просмотрен,
pageErrors=0; собственная session отозвана HTTP204.

**Полный экран «Сегодня» не подтверждён.** Schedule/Attendance/Notification и
другие предметные сервисы отсутствуют в минимальном стенде; успех core API
не заменяет их запуск/приёмку. Исходная анонимная страница также показывает
ошибку завершённой/отсутствующей session; это зафиксированное состояние,
а не готовность интерфейса. Существующий web Angular не заменён.

Локальные evidence под `/workspace/rct-pr3-setup/`:

- `readiness-artifacts-20261024.log`, `readiness-start-20261024.log`,
  `readiness-healthy-20261024.log`, `readiness-api-20261024.log`;
- `readiness-screenshot-20261024.log`, `readiness-login-20261024.png`;
- `readiness-browser-login-20261024.log` (промежуточное состояние),
  `readiness-browser-settled-20261024.log`,
  `readiness-post-login-settled-20261024.png`;
- `readiness-stop-20261024.log` — остановка только собственных ресурсов,
  без удаления data volumes/images, без global prune.

Штатный stop завершился с exit0; собственных project containers и frontend
ownership-state после остановки нет. Повторный запуск — сохранёнными helpers.

## ZIP

PASS. Первоначальный единый Sediment download отклонён из-за лимита32MiB;
владелец передал три меньшие ZIP-обёртки, все реально скачаны. Wrapper CRC,
safe paths, одинаковые manifest/README и size/SHA256 каждой binary part
проверены. Части соединены бинарно part001 → part002 → part003.

Восстановленный файл:
`/workspace/rct-pr3-setup/inputs/handoff-20261023-validated/web-vue-handoff-20261023.zip`.
Размер **87568617 bytes**, SHA256
**0f851394e9c8031e252cbff57fbb6bd277cdcce5e59a6cff7e3b555b6654f3cc**.
CRC всех422 entries PASS; нет unsafe paths, link/special entries или collisions.
Безопасно извлечено422 файла: **388 PNG +34 handoff**; post-write hashes совпали.

Для388/388 PNG проверены JSON/CSV manifests, состав, bytes, SHA256,
PNG signature/chunk CRC/IHDR/IEND и dimensions. Отдельных expected hashes для34
handoff files нет; они защищены общим supplied ZIP SHA256 и ZIP CRC,
локальные hashes сохранены в inventory. Нельзя выдавать архивные Figma/PASS
пометки за текущую удалённую проверку.

Подтверждены33 logical screens: admin5/auth5/headman11/student8/teacher4;
31 физических folder groups, поскольку auth password/OTP/QR объединены.
Все8 ширин300/425/600/768/1024/1280/1440/1920 представлены; dark264/light124.
Ровно140 ожидаемых narrow light gaps для28 non-auth screens, а не потеря файлов.
Это визуальные варианты, не новые страницы/тесты/functional denominator.

Evidence `/workspace/rct-pr3-setup/archive-check/`: `report.md`, `report.json`,
`file-inventory.json`, `validate_handoff.py`. Распаковка:
`/workspace/rct-pr3-setup/inputs/handoff-20261023-validated/extracted`.
Q-INPUT-001 RESOLVED; дальнейших действий владельца для ZIP не требуется.

## Контракт, прогресс и ожидание

Прочитаны актуальные root/frontend/services/tests AGENTS, RULES/CURRENT/SLOTS,
ENVIRONMENT-HANDOFF и весь заданный комплект docs/plans/web-vue-pr3, включая CSV.
План не пересоздавался. Сохранены bell/history/toasts без web channel settings;
student own export отложен; пользователи archive/restore без final deletion;
teacher group AND subject и остальные действующие grants; отменённые операции
не возвращаются. Новых существенных продуктовых вопросов нет.

Карта123 уникальных actions:117 active (AUTH6,SHARED15,STUDENT16,HEADMAN38,
TEACHER8,ADMIN34),1 candidate,1 deferred,4 excluded. Новая реализация этой
итерации implemented0/117 (0%), locally verified0/117 (0%); подготовка среды
не увеличивает эти показатели. Знаменатель остаётся provisional.

CI/Coverage/Security/OpenAPI по содержимому четырёх workflow — только
workflow_dispatch, PAUSED, не PASS. Они не запускались и не включались.
Root/agents не выполняли продуктовую реализацию. Для contract/archive agents
явно запрошены gpt-6.1-sol/high; tools не выдают runtime model telemetry,
поэтому фактическая модель не заявляется проверенной. Главная модель будущего
Goal по решению владельца — GPT-6.1 Sol ultra; продуктовая команда не запущена.

После проверки — ожидать отдельного запуска Goal владельцем. Новый Goal/PR,
merge/deploy/production и автоматическая активация workflows не разрешены.

## Локальный inventory изменений

Создан только документ [READINESS-CHECK-20261024.md](READINESS-CHECK-20261024.md).
Изменены только readiness/checkpoint документы:

- [README.md](README.md) — текущий статус и ссылка на эту проверку;
- [ENVIRONMENT-HANDOFF.md](ENVIRONMENT-HANDOFF.md) — отделён старый снимок
  pending/offline от фактов новой машины;
- [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) — Q-ENV-001/Q-INPUT-001 закрыты по evidence;
- [CURRENT.md](../../../.agent/orchestration-v2/CURRENT.md) — ожидание отдельного
  Goal, фактические доступы/runtime/ZIP, отсутствие реализации;
- [SLOTS.md](../../../.agent/orchestration-v2/SLOTS.md) — ownership и освобождённые
  ресурсы readiness вместо старого состояния connector-only.

Удалённых repository files нет. Product/tests/manifest/lockfile/workflow files
не изменены. Helpers/logs/screenshots/ZIP и результаты распаковки находятся
вне репозитория под `/workspace/rct-pr3-setup/`. `git diff --check` PASS;
ссылки readiness-документов существуют; четыре workflow по-прежнему имеют
только workflow_dispatch. После stop собственных containers нет, порты15175,
15176,18080 свободны, три data volumes сохранены. Commit/push не выполнялись.
