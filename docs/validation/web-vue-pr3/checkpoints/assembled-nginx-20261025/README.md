# Собранный Vue через общий Nginx — пакет25октября2026

Пока **runtime NOT_RUN**. Достаточный результат пакета: actual source-derived
commonNginx с matching WEB/PWA/TMA, прямые ссылки/refresh/static bytes/MIME/cache/CSP/404,
настоящий API, затем независимый Auth/realtime если неизменённые Origin/CSP допускают.

[PWA prefix build](pwa-prefix-build.json) и [TMA prefix build](tma-prefix-build.json)
PASS на source524f44a5a4f27491b3abe73417a004b62e7dad18, строго последовательно,
production/fixturefalse/heap1536MiB/early2.5GiB/180s. Причина новых builds:
прежние default-root distributions не подходят для `/app/` и `/mini-app/`.
WEB matching `/` artifact ddfca530 переиспользуется; повторной сборки нет.
Это source/artifact PASS, не общий runtime PASS.

[Текущая Origin qualification](main-origin-proof.json) извлечена из реально работающих
Main Gateway/Notification без печати других env/config значений. Оба разрешают
HTTP12714240 и не разрешают HTTPS12714443. Main/allowlist не меняется.
TLS static/anonymous forwarding и HTTP nativeAuth/API/SockJS учитывать отдельно.
HTTPSAuth/WSS, реальный Telegram provider и WebPush delivery по этому пакету не заявлены.

Forecast дополнительных512MiB,128MiB/0.5CPU/32PID один retainedNginximage,
read-only/matchinghostnonrootuid1000:1000/dropALL/noNewPrivileges/privateTLS.
Group7GiB не применяется к этому лёгкому пакету. CI PAUSED, без deploy/production.
