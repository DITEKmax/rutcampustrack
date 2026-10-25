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


## Первый настоящий runtime — FAIL, причина найдена

Source4f48f169. Один owncontainer запущен из retainedimage, nginx-t PASS. HTTPcheck
остановился до Auth на Script MIME:web. Отдельный узкий read-only diagnostic
подтвердил: во всех3приложениях pdf.worker.min-Dkey6ZUl.mjs200/exactSHA, но
application/octet-stream; остальные43JS application/javascript. Standard image
mime.types не знаетmjs. Это ошибка runtime serving, не build или проверка текста.
[Исходный receipt](first-runtime-lifecycle.json) и [диагностика](first-script-mime-diagnostic.json)
сохранены. Auth/browser0; собственный container завершён, guard не сработал.
Повтор только после исправления MIME config; приложения не пересобираются.

Raw lifecycle original30Unchanged=false сохранён. Причина в проверке: порядок
Mounts не гарантирован, а dockerps -aq дал короткиеID против64hexbaseline.
[Независимое canonical сравнение](first-post-canonical-preservation.json) fullID/image/
state/mountsets подтверждает все30unchanged. Это квалификация ошибки harness,
не замена rawFAIL на PASS всей поставки.

Переносимый локальный Nginx получил два точечных исправления: exact2Origin нового
localrealm доfreeze и pinned отдельный CA для HTTPSdirectAPI18443.
[Source review PASS](../../review/local-nginx-REVIEW-f81d14d7-d464edf8.md).
CloudMain policies не менялись; extraIP/profile/proxytrust не добавлялись.
Forwarded external HTTPSidentity, ownerlocal combined runtime и provider gates NOT_RUN.


## Config-only MIME correction — source PASS

Интеграция0664fecbf/106aefbb9: три innerNginx сохраняют полную standard MIMEtable
и добавляютmjs→application/javascript. Mobileassetregex дополненmjs, существующий
HTTPgate проверяетimmutableworker иmissingjs/mjs404. [Независимый review](MIME-SOURCE-REVIEW.md) PASS.
Ровно3runtime nginx.conf исключены изViteartifactclosure, потому чтоvue-tsc/Vite
их не читают; preparedruntimeconfigs/Compose/mounts по-прежнему hashpinned и проверяются.
Root заново pin accepted старые3артефакта/log/sourceSHA новымfingerprintalgorithm,
сохраняя старыеreceipts; **никакой app rebuild**. Actualconfig-only повтор впереди,
не sourcePASS→runtimePASS автоматически.
