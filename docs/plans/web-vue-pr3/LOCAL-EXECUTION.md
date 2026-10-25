# Передача владельцу: PR3 на WSL Ubuntu24.04

Полный group UI **NOT_RUN** до твоего выполнения и разбора output. В облаке его
start/grants/delete запрещены. Cloud runtime-qualified source:
`240a1b2a86a2b2ba0cbb89647a9ed8b151007007`; финальный опубликованный SHA документации
смотри в описании PR3/последнем ответе. Ветка `codex/web-vue-redesign`; main не менять.
Для запуска выбери полный финальный опубликованный SHA, не main/latest tag.
Все launchers/templates лежат в Git, машинных private путей для их работы нет.

## Новый каталог, версии, ресурсы

Старые `~/rutcampustrack-ci-diagnosis` и `~/.local/share/rct-stand/activate` не трогай
и не активируй. Новый каталог строго `~/rutcampustrack-pr3`, Docker namespace строго
`rct-pr3-local`. Ubuntu выполняет команды/JDK/Node/Chromium, Docker —12сервисов.
Не нужен контейнер со всем проектом. Агенты на твоей машине ничего не запускают.

Ubuntu24.04/WSL2, JDK21, Gradle wrapper8.12, Node24.14.0, npm11.9.0,
Python3.12 +python3-bcrypt, OpenSSL/Git/curl/xz, LinuxDocker unixsocket +Composev2.
Установка отдельного Node/npm prefix и все команды:
[tests/e2e/pr3-local/README.md](../../../tests/e2e/pr3-local/README.md).
Перед dependencies/JAR нужно около25GiB диска; послеJAR actualfloor7GiB,
early2.5GiB/hard2GiB/250ms.4CPU/12GiB memory minimum,16GiB WSL разумно;
передсервисами5GiB RAM, передbrowser2GiB. Совпадение ресурса проверяется, не предполагается.

Порты нового group realm: WEB14250/PWA14251/TMA14252 на Ubuntu,
Docker TLSedge127.0.0.1:18443. PG/Redis/Rabbit/Mongo не публикуют hostports.
Network172.31.35.0/24 не должен пересекаться с имеющимися ресурсами.
Отдельные auth_db/academic_db/schedule_db, Mongoattendance_db/notification_db,
разные runtimeprincipals/ACL, Rabbitvhostrct-pr3-local, свои namedvolumes/noncelabels.
Минимальные12: Postgres/Redis/Rabbit/Mongo +Auth/Academic/Schedule/Attendance/
Notification/MobileBFF/Gateway/TLSedge. ImageIDs фиксируются после явного pull;
полный список шести baseimages и Mongo digest — в README.

## Один group gate

В новом checkout задай `PR3_SHA` полным опубликованным SHA из PR/ответа и следуй
README: freshclone/detachedcheckout;7matchingbootJAR из ЭТОГОsource; npmci/frontends
build/E2Edependencies/Chromium; явный pull; затем public `check_handoff.py`.
Это подготовка локального source, не повтор cloudbackend тестов и не runtime PASS.

```bash
set -euo pipefail
cd "$HOME/rutcampustrack-pr3"
/usr/bin/python3 tests/e2e/pr3-local/local.py prepare --source-sha "$PR3_SHA"
/usr/bin/python3 tests/e2e/pr3-local/local.py start
/usr/bin/python3 tests/e2e/pr3-local/local.py frontends
```

`prepare` с TTY запросит новый пароль только свежего синтетического Admin1. Все
secrets/CA/keys/TMA signer создаются локально, directories0700/files0600;
никаких cloudDB/секретов/privatekeys/старого runtimeconfig не переносить.
Dirtysource, чужой ресурс, неправильные images/mounts/nonce или нехватка ресурса
дают STOP. Не применяй reset/clean/adoption/rekey или ослабление guard.

Второй новый Ubuntu terminal с тем же Node/JDK PATH, без старого activate:

```bash
set -euo pipefail
cd "$HOME/rutcampustrack-pr3"
/usr/bin/python3 tests/e2e/pr3-local/local.py fixture
/usr/bin/python3 tests/e2e/pr3-local/local.py case
/usr/bin/python3 tests/e2e/pr3-local/local.py summary
```

Критерии group acceptance:

1. Numericpreview всех8категорий совпадает с точным собственным targetfixture;
   текущий пароль вводится в настоящий UI, не argv/logs.
2. Один originalUUID durable ДО POST; подтверждён результат/status именно этой операции.
   Unknown/pending→`inspect` исходной записи, **без нового удаления вслепую**.
3. Естественный отзыв targetдоступа в6заранее открытых WEB/PWA/TMA страницах;
   реальные personalWS hints и causalboundary, без ручногоrefresh/поддельныхответов.
4. Другие accounts/credentials/roles/groups, retainedTeachercontrol/draft37 сохраняются.
   Удаляется членство/назначения/полномочия targetgroup; аккаунты не архивируются.
5. Own sessions/browsercleanup подтверждены. ControlledTMA signer явно synthetic,
   это не nativeTelegramproviderPASS.

Пришли только `summary`, exitcodes команд и версии. Без `.pr3-local`, rawlogs,
storage-state/cookies/password/keys/DB dumps. OriginalUUID и numericcounts в safe summary
разрешены; credentials/sessionhandles не передавать. PrivatePNG только после личного
просмотра на отсутствие credentials. HOLD сохраняет state и запрещает blindretry.

## Общий Nginx: что уже проверено и что выполнить локально

[Cloud evidence](../../validation/web-vue-pr3/checkpoints/assembled-nginx-20261025/README.md):
actualbuiltall3/static/deeplinks/refresh/MIME/cache/CSP/404 PASS; PWA HTTPAuth/API/STOMP PASS;
WEB — отдельно boundedAuth/schema/STOMP/cleanup, rawtestIDtypeFAIL сохранён.
Это не HTTPSAuth/WSS, TMAprovider, SWlifecycle или WebPushdeliveryPASS.
Успешные неизменённые cloud slices не нужно повторять ради числа проверок.

Если проверяешь assembledNginx локально, оставь свои12backendservices работающими
после **knownterminalgroup**. Сначала Ctrl-C первого `frontends` terminal завершит
только свои3Vites; после этого можно последовательно собрать strictdistributions.
Не запускай groupcase ещё раз. Полные portablecommands:
[tests/e2e/pr3-nginx/README.md](../../../tests/e2e/pr3-nginx/README.md).

```bash
set -euo pipefail
GATE_STATE="$PWD/.pr3-nginx"
mkdir -p "$GATE_STATE"
python3 tests/e2e/pr3-nginx/build.py --expected-sha "$PR3_SHA" --app web --out "$GATE_STATE/web-dist"
python3 tests/e2e/pr3-nginx/build.py --expected-sha "$PR3_SHA" --app pwa --out "$GATE_STATE/pwa-dist"
python3 tests/e2e/pr3-nginx/build.py --expected-sha "$PR3_SHA" --app tma --out "$GATE_STATE/tma-dist"
```

Freshprefixbuilds необходимы на другой машине без matchingartifacts; прежний npmrunbuild
с defaultbase не подходит. Helper ограничивает heap1536MiB/180s/512MiBdelta/early2.5GiB.
Нужен ещё один128MiB Nginx, image уже pullнут; ports14240/14443 должны быть свободны.
Используй network `rct-pr3-local_private_net`, gateway `api-gateway:8080`,
loopbackAPI `https://127.0.0.1:18443` и отдельный pinnedAPI-CA
`.pr3-local/runtime/private/tls/ca.crt` через `--api-ca/--api-ca-sha256`.
Новый generatedlocalrealm заранее разрешает ровноOriginsHTTP14240/HTTPS14443;
это не изменение старого/Mainстенда. CommonNginx получает свою новуюTLSidentity,
CA directAPI нельзя заменять ею или отключатьTLSverification.

Для нового локального realm используй следующую последовательность. Она **заменяет**
cloud-пример с портом18080 из общего README. К старому стенду на18080 не обращаемся.
Двенадцать новых backend-сервисов должны продолжать работать; три Vite уже остановлены.

```bash
set -euo pipefail
SOURCE_SHA="$PR3_SHA"
PWA_DIST="$GATE_STATE/pwa-dist"
RUN="$GATE_STATE/run"
API_CA="$PWD/.pr3-local/runtime/private/tls/ca.crt"
API_CA_SHA="$(sha256sum "$API_CA" | cut -d ' ' -f 1)"

docker_local() {
  env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY \
    -u DOCKER_CERT_PATH docker --host=unix:///var/run/docker.sock "$@"
}
# Не принимать существующий чужой или прежний Nginx namespace.
test -z "$(docker_local ps -aq --filter label=com.docker.compose.project=rct-pr3-nginx-gate)"
python3 - <<'PORT_CHECK'
import socket
for port in (14240, 14443):
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', port))
PORT_CHECK
NGINX_IMAGE_ID="$(docker_local image inspect --format '{{.Id}}' nginx:1.27-alpine)"
python3 tests/e2e/pr3-nginx/gate.py prepare --expected-sha "$SOURCE_SHA" \
  --image-id "$NGINX_IMAGE_ID" --network rct-pr3-local_private_net \
  --gateway api-gateway:8080 --landing-dist "$PWD/frontends/landing/dist" \
  --web-dist "$GATE_STATE/web-dist" --web-pin "$GATE_STATE/web.pin.json" \
  --pwa-dist "$PWA_DIST" --pwa-pin "$GATE_STATE/pwa.pin.json" \
  --tma-dist "$GATE_STATE/tma-dist" --tma-pin "$GATE_STATE/tma.pin.json" \
  --out "$RUN"
openssl req -x509 -newkey rsa:2048 -noenc -days 1 \
  -keyout "$RUN/tls/privkey.pem" -out "$RUN/tls/fullchain.pem" \
  -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
chmod 600 "$RUN/tls/privkey.pem"
openssl genpkey -genparam -algorithm DH -pkeyopt group:ffdhe2048 \
  -out "$RUN/tls/dhparam.pem"
docker_local compose -f "$RUN/compose.json" up -d --pull never --no-build
docker_local compose -f "$RUN/compose.json" exec -T edge nginx -t
python3 tests/e2e/pr3-nginx/gate.py check --expected-sha "$SOURCE_SHA" \
  --run-dir "$RUN" --base https://127.0.0.1:14443 \
  --api-base https://127.0.0.1:18443 --api-ca "$API_CA" \
  --api-ca-sha256 "$API_CA_SHA" --out "$RUN/http-result.json"
```

Ошибку не продолжай как PASS; сохрани результат. Пока собственный Nginx работает,
можно выполнить доступные браузерные критерии ниже. Только после известного
результата и закрытия собственных сессий/браузеров:

```bash
set -euo pipefail
docker_local compose -f "$RUN/compose.json" down
/usr/bin/python3 tests/e2e/pr3-local/local.py stop
```

В этих командах нет обращения к18080 или старому проекту. Занятый порт/namespace
останавливает подготовку; чужие процессы, контейнеры и данные не останавливаются.
При неизвестном результате group/Auth сначала разбор исходного состояния, не stop/retry.

Местные remaining gates до фактического результата **NOT_RUN**:

- SamecommonentryHTTPS: trustedbrowserprofile с локальным сертификатом, normalAuth,
  APIdata и physicalWSS/STOMP; нельзя отключать CORS/CSP/TLS или подменятьOrigin.
  ForwardedexternalHTTPSidentity отдельнонеподтверждена: Gateway доверяет только
  существующемуTLSedge.10; дополнительныйIP/profile/subnettrust в пакет не добавлялись.
- PWA `/app/`: реальный serviceworker install/activation/update/offline/recovery,
  авторизация после обновления; static sw.js/manifest/versionbytes это не доказывают.
- TMA `/mini-app/`: реальный nativeTelegramadmission +Auth/data/realtime;
  controlledlocalgroup signer не является проверкойprovider.
- ExistingWebPush: регистрация/доставка в принадлежащем локальном browser/provider,
  без productionVAPID/secrets; обычныйbell/STOMP не равен WebPushdelivery.

Для отсутствующего provider/trustedprofile допускается конкретный NOT_RUN, неPASS.
Это конечные пользовательские критерии, не новая инфраструктура или массоваяE2Eкампания.
Сначала пришли actualgroup summary; непроверенные критерии не засчитываются заранее.

После knownterminal завершения ownNginx выполни его scopedcompose down, затем
`local.py stop` для12nonce-ownedservices. Не down/prunevolumes/images, не удаляй
локальные data/keys/evidence. Если outcome неизвестен — сначала original `inspect`.
CI/Coverage/Security/OpenAPI PAUSED; без merge/deploy/production.
