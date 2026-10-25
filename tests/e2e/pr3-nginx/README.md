# PR3: собранные Vue-приложения через общий Nginx

Этот ограниченный gate проверяет WEB `/`, PWA `/app/`, TMA `/mini-app/` через общий вход из текущего `nginx/`. Он использует один отдельный Nginx-контейнер с четырьмя внутренними static listeners, существующий API Gateway и готовые Vue distributions. Backend, базы, очереди, роли, пароли и данные gate не меняет. Это локальная проверка; deploy и production TLS не проверяются.

Полный групповой сценарий и подготовка своего Ubuntu-стенда описаны отдельно в [pr3-local](../pr3-local/README.md). Имя проекта этого gate — `rct-pr3-nginx-gate`. Ровно два опубликованных loopback адреса одного common server: HTTPS `127.0.0.1:14443` для static/anonymous API и HTTP `127.0.0.1:14240` для отдельно квалифицированных native Auth/SockJS сценариев. Они не занимают порты группового стенда. Main allowlist для HTTP14240 подтверждается отдельно по публичной конфигурации; HTTPS Auth/WSS остаётся owner-local `NOT_RUN`.

## Предварительные условия и границы

- Нужны Python3.10+, Node/npm, установленные locked frontend dependencies, Docker Compose, OpenSSL и уже имеющийся Nginx image. Скрипты ничего не устанавливают и не скачивают.
- Передай полный опубликованный SHA текущего coherent source, включая сам пакет `tests/e2e/pr3-nginx`. Dirty или untracked executable gate source не допускается до любых outputs/build effects. После изменения frontend/Nginx source или SHA подготовку выполняют заново в новом каталоге.
- Облачные build/start допускаются только после текущего Root GO и проверки source/review. Сборки строго по одной, Node old-space ограничен1536MiB до первой попытки. Прогноз дополнительного места ≤512MiB, ранняя остановка при свободном месте <2.5GiB, жёсткий резерв2GiB не снижается. `build.py` проверяет место каждые250ms, останавливает только свой process group при раннем пороге/росте512MiB/тайм-ауте180s и сохраняет исходный лог. SIGTERM/SIGINT monitor получает через установленный до запуска ребёнка handler; остановка и bounded cleanup сохраняются также в launch window.
- Nginx работает под тем же nonroot UID:GID, что владелец artifacts/TLS; запуск `prepare` от root отвергается до outputs. Режимы закрытых dist directories и TLS key не меняются. Контейнер ограничен128MiB RAM,0.5CPU,32PID, read-only root, cap_drop=ALL без добавленных capabilities и ограниченными tmpfs с явными UID/GID/mode0700 для cache/pid/log/tmp. Не запускай его одновременно с другой тяжёлой операцией; действующий guard свободного места сохраняют на время проверки.
- Ни исходники, ни старые evidence/artifacts не очищаются. Все новые outputs лежат отдельно от frontend source; существующие outputs скрипты не перезаписывают.
- `pin` записывает **принятое внешнее доказательство сборки**, а не доказывает сам факт сборки по произвольному логу. Применяй его только к уже принятому Root artifact. При несовпадении source inputs или artifact bytes reuse запрещён.

## Точный source и distributions

Команды запускаются из корня чистого checkout. Пути outputs выбираются локально; не добавляй их, TLS key, cookies или credentials в Git и source recovery ZIP.

```bash
SOURCE_SHA='<полный опубликованный SHA из40 hex символов>'
GATE_STATE="$PWD/.pr3-nginx"
mkdir -p "$GATE_STATE"

# После разрешения на последовательные matching builds:
python3 tests/e2e/pr3-nginx/build.py --expected-sha "$SOURCE_SHA" \
  --app web --out "$GATE_STATE/web-dist"
python3 tests/e2e/pr3-nginx/build.py --expected-sha "$SOURCE_SHA" \
  --app tma --out "$GATE_STATE/tma-dist"
```

Для уже принятого PWA artifact передай его точную исходную revision, сохранив original dist и build log. Для нового Ubuntu-стенда без такого artifact используй matching `build.py --app pwa`; не объявляй произвольный старый dist подходящим.

```bash
PWA_SOURCE_SHA='<полный SHA принятой PWA сборки>'
PWA_DIST='<локальный каталог принятого PWA dist>'
PWA_BUILD_LOG='<локальный исходный лог принятой PWA сборки>'
python3 tests/e2e/pr3-nginx/gate.py pin --expected-sha "$SOURCE_SHA" --app pwa \
  --fixture-mode false \
  --source-sha "$PWA_SOURCE_SHA" --dist "$PWA_DIST" \
  --build-log "$PWA_BUILD_LOG" --out "$GATE_STATE/pwa.pin.json"
```

Artifact receipt содержит Git blob fingerprint входов, SHA256 всех файлов, actual entry public base, producer config/hash и PWA version-policy hash. WEB fingerprint включает импортируемую PWA auth/recovery реализацию. `prepare` проверяет fingerprint принятой старой revision против текущей, полный file inventory, отсутствие source изменений и actual HTML prefixes: `/`, `/app/`, `/mini-app/`. Совпадающий source при собранном default-root PWA/TMA не подходит; такой artifact отвергается до подготовки outputs.

`build.py` явно задаёт `NODE_ENV=production`, app base и `VITE_MOBILE_FIXTURE_MODE=false`, удалив унаследованные `VITE_*` overrides. Version defaults совпадают с Dockerfiles: `0.0.0`, minimum fallback `0.0.0`, force=true; actual PWA `version.json` проверяется и хешируется. Для external `pin --fixture-mode false` — явное утверждение принятого producer evidence, а не извлечение compiler flag из произвольного JavaScript. Используй его только при доказанном normal-production build. WEB artifact с правильным `/` можно переиспользовать; PWA/TMA default `/assets/` требуют новой prefix-сборки. Existing strict результаты сохраняются отдельно; `build.py` вызывает штатный полный strict script, не выдавая старую strict проверку за проверку новых prefix bytes.

## Подготовка одного общего Nginx

Выбери **существующие** Docker network и Nginx image; inspection ниже выводит только безопасные идентификаторы. Gateway — имя/alias и порт своего существующего API Gateway в выбранной сети. В облаке это существующий Main gateway; не создавай новый backend. Не используй admin-isolated/group fixture realm по умолчанию.

```bash
# Явно свой локальный daemon, с сохранением DOCKER_CONFIG и registry credentials.
docker_local() {
  env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY \
    -u DOCKER_CERT_PATH docker --host=unix:///var/run/docker.sock "$@"
}
docker_local info --format '{{.Driver}} {{.OSType}}'
docker_local image ls --format '{{.Repository}}:{{.Tag}} {{.ID}}'
docker_local network ls --format '{{.Name}}'
NGINX_IMAGE_ID="$(docker_local image inspect --format '{{.Id}}' nginx:1.27-alpine)"
MAIN_NETWORK='<существующая сеть своего backend>'
MAIN_GATEWAY='<DNS alias своего gateway>:8080'
LANDING_DIST="$PWD/frontends/landing/dist"
RUN="$GATE_STATE/run"

python3 tests/e2e/pr3-nginx/gate.py prepare --expected-sha "$SOURCE_SHA" \
  --image-id "$NGINX_IMAGE_ID" --network "$MAIN_NETWORK" --gateway "$MAIN_GATEWAY" \
  --web-dist "$GATE_STATE/web-dist" --web-pin "$GATE_STATE/web.pin.json" \
  --pwa-dist "$PWA_DIST" --pwa-pin "$GATE_STATE/pwa.pin.json" \
  --tma-dist "$GATE_STATE/tma-dist" --tma-pin "$GATE_STATE/tma.pin.json" \
  --landing-dist "$LANDING_DIST" --out "$RUN"

# Только новая синтетическая локальная TLS identity; существующие keys не копируются.
openssl req -x509 -newkey rsa:2048 -noenc -days 1 \
  -keyout "$RUN/tls/privkey.pem" -out "$RUN/tls/fullchain.pem" \
  -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
chmod 600 "$RUN/tls/privkey.pem"
openssl genpkey -genparam -algorithm DH -pkeyopt group:ffdhe2048 \
  -out "$RUN/tls/dhparam.pem"
```

`prepare` не запускает Docker. Он берёт source `nginx/nginx.conf`, `nginx/conf.d/default.conf` и inner configs WEB/PWA/TMA/landing. Единственные локальные адаптации: один worker; unpublished HTTP redirect listener80 перенесён на8080 для capless nonroot; TLS listen port совпадает с опубликованным14443, чтобы absolute redirects и forwarded port оставались верными; дополнительный plain HTTP14240 listener внутри того же common server; новая certificate path; явный gateway DNS; четыре loopback upstream/listener/static roots. Все listeners8080–8084/14240/14443 непривилегированные; наружу публикуются только два loopback14240/14443. Production routes, prefix rewrites, CSP и API/WS headers остаются source-derived. Недоступный downstream должен давать настоящий502; здесь нет response stubs или fake healthy endpoints.

```bash
# Только после текущего разрешения на лёгкий runtime и проверки ресурса:
# Если14240 или14443 занят — STOP, не выбирай другой Origin автоматически.
ss -ltn '( sport = :14240 or sport = :14443 )'
docker_local compose -f "$RUN/compose.json" up -d --pull never --no-build
docker_local compose -f "$RUN/compose.json" exec -T edge nginx -t

python3 tests/e2e/pr3-nginx/gate.py check --expected-sha "$SOURCE_SHA" \
  --run-dir "$RUN" --base https://127.0.0.1:14443 \
  --api-base http://127.0.0.1:18080 --out "$RUN/http-result.json"

# Завершает только собственный frontend Nginx; volumes/images/backend не удаляет.
docker_local compose -f "$RUN/compose.json" down
```

Для local Ubuntu замени `--api-base` своим реальным опубликованным loopback gateway. Проверяющий HTTP клиент доверяет ровно созданному локальному certificate; TLS verification не отключается. Shared Main CORS/Origin policy не меняется и Origin не подменяется.

## Что означает результат

`commonNginxStatic=PASS` означает: точные bytes всех трёх distributions прошли через общий Nginx; script/CSS MIME, shell/asset cache, CSP presence, deep links и refresh, missing-asset404 и PWA worker/version/manifest bytes проверены. Для WEB применяется существующий [served Vue oracle](../../../scripts/verify-served-web-panel.py). Image ID, весь prepared Compose и configuration bytes привязаны к source receipt. До HTTP проверяются project/service/run-nonce labels, точный nonroot UID:GID без дополнительных групп/capabilities, одна выбранная external network, ровно два loopback port14240/14443 и HTTPS check base, весь набор read-only binds без лишних mounts, read-only root и128MiB RAM/без дополнительного swap/0.5CPU/32PID/tmpfs owner/mode/size bounds. Это не подтверждение поведения самого браузера или backend сценариев.

`apiForwarding=OBSERVED_ANONYMOUS_ONLY` содержит реальные сравнения статусов/MIME `/api/auth/session` и SockJS `/api/ws/info` через Nginx и напрямую.401/403 не означают успешный login или realtime connection;502 и несовпадение показываются как FAIL. Никакие bearer/cookie/ticket не записываются. Production Vue использует SockJS: base `/api/ws?ticket=...` превращается в `/info` и transport subpaths; upgrade на одном exact `/api/ws` не является доказательством работы или регрессии клиента.

Отдельные критерии по умолчанию `NOT_RUN`: WEB native auth lifecycle, PWA auth/service-worker activation/update/offline, authenticated SockJS/STOMP, TMA post-login и реальный WebPush provider. HTTP bytes `sw.js` не доказывают install/activation/update; `/mini-app/` shell не доказывает Telegram authentication. Не превращай их в общий PASS.

HTTP14240 использует те же source routes/CSP/Origin forwarding, что HTTPS14443; Main Gateway/Notification не перезапускаются и их allowlist не меняется. Возможная успешная HTTP native Auth/SockJS проверка не доказывает HTTPS Auth/WSS. Если неизменённый CSP или provider блокирует browser path, фиксируй отдельный `NOT_RUN`; не отключай CSP/CORS, не подменяй Origin и не переноси HTTP результат на TLS критерий.

Для browser/auth используй уже принятые public auth fixtures/lifecycle и свои существующие synthetic accounts после квалификации точной identity/roles/realm. Не выдавай новые права, не меняй passwords/PREF, не экспортируй cloud credentials или host sessions в Ubuntu. TMA signer/binding допустим только при доказанном существующем предназначении; иначе оставь `NOT_RUN`. Локальный сертификат должен быть доверенным выбранным browser profile: отключение CORS, подмена Origin или глобальное отключение TLS проверки сюда не входят. Если resource, fixture identity, trusted browser TLS или provider отсутствуют, передай этот независимый критерий владельцу как `NOT_RUN` с конкретным пробелом.
