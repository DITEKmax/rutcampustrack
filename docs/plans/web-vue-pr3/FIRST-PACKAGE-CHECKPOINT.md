# Первый сквозной пакет — 2026-10-24

Это промежуточная поставка в существующий PR3 после отдельного запуска Goal.
Полная замена Angular ещё не готова; работа продолжается по сохранённому контракту.
Исторический срез первого пакета ниже сохранён. Последующие проверки закрыли
homework browse gate (теперь verified5/117); original auth PNG получен через
read-only Figma image bytes; stand имеет13healthy и настоящий Renderer.
Актуальное состояние: [integration checkpoint](INTEGRATION-CHECKPOINT-20261024.md).
Source snapshot root072edf183; web authora2ef210623; backend device3c02a3241;
neutral/mobile theme throughcaf3789ab. Каждый runtime sample хранит свою точную
revision; позднейший source не приписывается старым screenshots.

## Результат пользователя и счётчики

Пользователь входит по паролю через настоящий Auth, выбирает доступную роль,
читает ДЗ из настоящего Mobile BFF, меняет собственную отметку и видит сохранённый
результат после чтения; выходит из текущей сессии. Shell/Onest/semantic tokens/
rem PCSS переиспользуются. Light/dark/system хранится за устройством, локальный
выбор имеет приоритет, серверное зеркало переживает logout/login/смену аккаунта.

Implemented5/117(4,27%); locallyverified4/117(3,42%). Учтены ROLE-SELECT, LOGOUT,
THEME-SELECT, STUDENT-HOMEWORK-BROWSE и STUDENT-HOMEWORK-COMPLETE. Для BROWSE
достаточный runtime gate day/month/navigation/link/keyboard ещё ожидается;
source implementation учтена, local verification пока не учтена. AUTH password/
bootstrap работают, но не закрыты целиком: missing original background и полный
role-context gate. Карта сохраняет все117active, candidate/deferred/excluded
не преобразуются в удобный меньший знаменатель.

## Проверки и независимое review

- Web strict TypeScript/build/lint и meaningful async regressions PASS: role/
  refresh race, stale homework response, accountA→B late401 без записи с bearerB,
  normal same-identity refresh, late response ownership. Scoped transport guards
  проверяют identity до/после response; consumer assertCurrent после decode.
- Авторский actual UI behavior smoke: `/workspace/rct-pr3-runtime/web-foundation/behavior-smoke.log`:
  HEADMAN↔STUDENT200, wrong-role denial, completion/reload/readback и logout204.
  Это предыдущий behavioral evidence, не screenshot latestrevision.
- Actual cab5 gallery: UI themeGET/PUT200, completion200/undo200/logout204, восемь
  ширин/две темы без horizontal overflow. Targeted a2ef hover sample исправляет
  обнаруженный контраст, не перезаписывая исторические20PNG.
- Auth device mirror: реальные PostgreSQL/Redis HTTP6, SameSite4, Authfilter9,
  Gatewayfilter57, snapshot1 PASS; CAS/expiry/commitrollback/concurrentlogout и
  rejected bearer/context cases. Narrow schema correction не требовала повтора HTTP.
- Actual browser Secure-cookie GET/PUT/readback/CAS409/logout PASS; PWA app
  light→dark→logout→teacherlogin same device→localoverride/reload PASS,0JS errors.
  Page.request/APIRequestContext дал иной HTTP Securecookie context; harness
  исправлен на page.evaluate(fetch), product security не ослаблялась.
- Shared theme meaningful unit17/3files, foundation/PWA/TMA types и обе сборки
  PASS. Actual PWA student/teacher/admin representative runtime и SVG contrast
  проверены. TMA host-theme adapter проверен controlled harness; настоящий signed
  Telegram admission и полная matrix всех мобильных состояний не подтверждены.

[Immutable независимые отчёты](../../validation/web-vue-pr3/review/manifest.json)
содержат originalpath/SHA256, включая ранние findings и исправления. Latest web
cab5+a2ef PASS, device source+schema PASS, mobile6de+00fc PASS. Позднейшие маленькие
mobile deltas отдельно ожидают review; общий полный PR не объявляется принятым.

## Галерея и стенд

[Открыть runtime HTML](../../validation/web-vue-pr3/runtime-gallery/index.html),
[скачать комплект HTML/PNG](../../validation/web-vue-pr3/runtime-gallery/runtime-gallery.zip),
[hover correction](../../validation/web-vue-pr3/runtime-gallery/hover-a2ef210623/index.html).
Записи содержат ID, screen, state, theme, width, frontend/backend revisions,
название, описание, PNG hash/dimensions. Все изображения получены из настоящего
runtime; archive/Figma reference не являются implemented screenshots.

Реальные Auth/Academic/Gateway/BFF/Schedule/Attendance/Notification и synthetic
PostgreSQL/Redis/RabbitMQ/MongoDB изолированы проектом rct-pr3-setup,12healthy.
Actual login/session/homework/schedule/Today/unread200. Это API Today gate,
полный новый web Today ещё не принят. Renderer добавляется для настоящих exports.
Auth/Gateway JARы в stand неизменяемы в artifacts/<hash>, а не author build/libs.
Runtime summary `/workspace/rct-pr3-runtime/SUMMARY.md`; known captured failures
сохранены, PAUSED/failed попытки не записаны как PASS.

## Ограничения

Original Figma auth asset недоступен: www.figma.com CONNECT403, owner обещал
разрешить; MCP context/screenshot работает. Q-PROD-004 группа/member/grants
ожидает owner. Angular остаётся до полной функциональной замены. Нет CI runs,
merge/deploy/main/production;4workflows workflow_dispatch и PAUSED.
