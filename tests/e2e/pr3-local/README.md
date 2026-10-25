# PR3: локальное удаление группы на Ubuntu 24.04 / WSL2

Статус пакета — **NOT_RUN**. Облачный group UI gate отменён решением владельца.
Подготовлены публичные исходники локального стенда; реальный результат появится
только после твоего запуска и проверки его output. Старые accepted20 boundary cases,
55client/6Notification/2PG/1Mongo результаты не повторяются и не превращаются
в результат этого нового стенда.

Первый structural review `7f85860ef` дал **CHANGES REQUIRED**: существующий private
каталог мешал входу в case, operational inputs не были полностью связаны с freeze,
а ошибка detached readiness оставляла сервисы работающими. Эти три finding исправлены
одним пакетом. Recheck `4b9c0867` закрыл private/freeze и оставил один P2 по parent
SIGTERM/generic poll exception. Следующая узкая правка устанавливает termination
handler до launch и всегда reap своих child groups, включая launch window;
[точное сопоставление и ограничения](CORRECTIONS.md). Это source corrections,
а не фактический запуск локального gate.

Рабочая директория строго `~/rutcampustrack-pr3`, Docker namespace строго
`rct-pr3-local`. Пакет не читает и не запускает `~/rutcampustrack-ci-diagnosis`
или `~/.local/share/rct-stand/activate`. Здесь нет экспорта облачных БД, секретов,
ключей, sessions, private evidence, JAR или действующих runtime configs.

## Что потребуется

- Ubuntu 24.04, включая WSL2, с доступным Linux Docker Engine через
  `/var/run/docker.sock` и Docker Compose v2. Не требуется контейнер со всем проектом.
- JDK **21**, Gradle wrapper **8.12** из репозитория, Node **24.14.0**, npm **11.9.0**.
  Spring Boot в исходниках — **3.5.16**. Python 3.12 из Ubuntu, `python3-bcrypt`,
  OpenSSL, Git, curl, xz-utils. Java и Node запускаются на Ubuntu, сервисы — в Docker.
- Не менее 4 CPU и 12 GiB доступной машине памяти; разумно выделить WSL 16 GiB.
  Перед сервисами требуется 5 GiB свободной RAM, перед Chromium — 2 GiB.
  До сборки оставь примерно 25 GiB свободного места под зависимости и JAR;
  **после JAR** проверяется реальный floor 7 GiB. Hard reserve 2 GiB,
  early stop 2.5 GiB, sample interval 250 ms не ослаблены.
- Свободные loopback ports `14250`, `14251`, `14252`, `18443` и отсутствие
  пересечения Docker subnet `172.31.35.0/24`. Занятый ресурс не останавливается
  и не переиспользуется.

Если Docker/JDK уже установлены, используй их в отдельном новом терминале.
`JAVA_HOME` и `PATH` меняются только в этом терминале; не меняй system alternatives
и не активируй старый стенд. Для недостающих Ubuntu tools:

```bash
sudo apt-get update
sudo apt-get install openjdk-21-jdk python3-bcrypt openssl git curl xz-utils
```

Node/npm можно установить в отдельный новый tools prefix; существующий Node
и старые shell scripts не заменяются:

```bash
set -euo pipefail
PR3_TOOLS="$HOME/.local/share/rct-pr3-local/toolchain"
test ! -e "$PR3_TOOLS"
mkdir -p "$PR3_TOOLS"
case "$(uname -m)" in
  x86_64) PR3_NODE_ARCH=x64 ;;
  aarch64) PR3_NODE_ARCH=arm64 ;;
  *) echo 'Нужна проверка поддержки этой архитектуры'; exit 1 ;;
esac
cd "$PR3_TOOLS"
curl --fail --show-error --location --remote-name "https://nodejs.org/dist/v24.14.0/node-v24.14.0-linux-$PR3_NODE_ARCH.tar.xz"
curl --fail --show-error --location --remote-name https://nodejs.org/dist/v24.14.0/SHASUMS256.txt
sha256sum --check --ignore-missing SHASUMS256.txt
tar -xf "node-v24.14.0-linux-$PR3_NODE_ARCH.tar.xz"
export PATH="$PR3_TOOLS/node-v24.14.0-linux-$PR3_NODE_ARCH/bin:$PATH"
npm install --prefix "$PR3_TOOLS/npm" npm@11.9.0
export PATH="$PR3_TOOLS/npm/node_modules/.bin:$PATH"
node --version
npm --version
java -version
docker --host=unix:///var/run/docker.sock compose version
```

## Новый checkout и точный source SHA

Возьми **полный опубликованный SHA, указанный Root после интеграции Angular-пакета
и самого handoff**. Не используй автоматически HEAD/main/latest tag. Ни `f0c`,
ни reference audit SHA из `source-contract.json` не являются вечным execution pin.

```bash
set -euo pipefail
PR3_SHA='<40-символьный опубликованный SHA от Root>'
test ! -e "$HOME/rutcampustrack-pr3"
git clone --no-checkout https://github.com/DITEKmax/rutcampustrack.git "$HOME/rutcampustrack-pr3"
cd "$HOME/rutcampustrack-pr3"
git checkout --detach "$PR3_SHA"
printf '/.pr3-local/\n' >> .git/info/exclude
git status --short
git rev-parse HEAD
```

Если checkout не совпал или tracked files изменены, остановись. Не применяй
reset/clean, не переноси файлы из старого checkout и не меняй SHA посреди случая.

Сборка ровно семи нужных matching JAR из этого нового checkout, без backend tests:

```bash
./gradlew --no-daemon --max-workers=2 --system-prop=org.gradle.java.compile-classpath-packaging=true \
  :services:auth-service:auth-app:bootJar \
  :services:academic-service:academic-app:bootJar \
  :services:schedule-service:schedule-app:bootJar \
  :services:attendance-service:attendance-app:bootJar \
  :services:notification-service:notification-app:bootJar \
  :services:mobile-bff:mobile-bff-app:bootJar \
  :services:api-gateway:bootJar
npm --prefix frontends ci
npm --prefix frontends run build
npm --prefix tests/e2e ci
cd tests/e2e
npx playwright install --with-deps chromium
cd ../..
```

Это локальная подготовка текущего source, а не повтор облачных проверок или уже
принятый UI gate. Docker образы загружаются явно до freeze. После `prepare`
запрещено pull/rebuild/tag replacement во время случая: фактические image IDs
попадают в локальный pin и сверяются перед фазами.

```bash
docker --host=unix:///var/run/docker.sock pull postgres:16
docker --host=unix:///var/run/docker.sock pull redis:7-alpine
docker --host=unix:///var/run/docker.sock pull rabbitmq:3.13-management-alpine
docker --host=unix:///var/run/docker.sock pull eclipse-temurin:21-jre-alpine
docker --host=unix:///var/run/docker.sock pull nginx:1.27-alpine
docker --host=unix:///var/run/docker.sock pull bitnamilegacy/mongodb:7.0@sha256:16a57fa081f18e7acd1b4de1cd74f3e294cc6de861007e6fe3a418eb61db8a54
/usr/bin/python3 tests/e2e/pr3-local/check_handoff.py
```

Публичный control entrypoint также запускает три коротких isolated Python
signal/exception controls. SIGTERM получают только их свежие synthetic worker/
sleep-child process groups; Docker, Auth, БД, browser и настоящие сервисы не
запускаются. Old local checkout/activate в этих controls не используются.

Теги с major version здесь соответствуют существующему stand input. Локальный
immutable pin — записанный **image ID после pull**, а не обещание одинакового
mutable tag на разных машинах. Mongo manifest digest задан явно.

## Подготовка и один запуск

```bash
/usr/bin/python3 tests/e2e/pr3-local/local.py prepare --source-sha "$PR3_SHA"
/usr/bin/python3 tests/e2e/pr3-local/local.py start
/usr/bin/python3 tests/e2e/pr3-local/local.py frontends
```

`prepare` спрашивает новый текущий пароль Admin1 для **новой пустой локальной БД**.
Ввод скрыт, пароль не передаётся shell argument и не печатается. Свежий initial
Auth baseline получает bcrypt hash; private config сохраняет пароль mode0600
только для нормального login и UI current-password confirmation. Никакого reset
или изменения существующего аккаунта нет. Остальные baseline identities также
синтетические и принадлежат этому новому realm.
Для ввода обязателен интерактивный TTY: echoing fallback и password pipes запрещены.

Создаются независимые PostgreSQL databases `auth_db`, `academic_db`, `schedule_db`
с разными runtime principals/ACL, Mongo `attendance_db` и `notification_db` с
разными app principals, собственные Redis и Rabbit vhost `rct-pr3-local`.
Данные хранятся в named volumes только этого namespace. TLS CA, gRPC certificates,
Auth RSA3072 keys, directed tokens, VAPID startup material и test TMA signer
создаются локально заново. Ключи/пароль/данные ниоткуда не импортируются.
Private directories имеют mode0700; пароль/config/storage/keys — mode0600.
Только точные PostgreSQL secret mount files доступны его UID999 как mode0444
внутри контейнера; их host parent остаётся0700. Они не публикуются и не попадают
под Vite serving: `.pr3-local` явно включён в `server.fs.deny`.

Минимум — 12 сервисов: PostgreSQL, Redis, RabbitMQ, Mongo replica set, Auth,
Academic, Schedule, Attendance, Notification, Mobile BFF, Gateway и TLS edge.
Наружу у контейнеров опубликован только `127.0.0.1:18443`; базы и broker host ports
не получают. На Ubuntu работают три Vite процесса: WEB14250/PWA14251/TMA14252.
У них обычные API/physical WS через доверенный local TLS proxy, fixture transport
выключен. Это source-server acceptance; production image/common-Nginx acceptance
проверяется отдельно.

Optional common Nginx запускается позднее на HTTP14240/HTTPS14443. Эти два exact
origin (`http://127.0.0.1:14240`, `https://127.0.0.1:14443`) заранее добавлены только
в **новые generated local** Gateway/Notification allowlists перед prepare/freeze;
dev14250–52 сохранены. Group preflight проверяет свои четыре порта и не резервирует
14240/14443: занятость optional Nginx проверяет его отдельный пакет. Trusted proxy
остаётся только `172.31.35.10`, без `.11`, wildcard, static-IP profile или изменений
Main/облака. Forwarded external HTTPS identity semantics остаются отдельным
owner-local **NOT_RUN**; добавление origins не считается доказательством login/STOMP.

`start` запускается один раз, ждёт healthy в bounded срок и сохраняет exact IDs.
До действий сохранён foreign container snapshot; every phase проверяет чужие
ID/image/mount/running state и nonce ownership собственных containers/networks/
volumes. До start/frontends проверяются generated package и source/JAR hashes,
а также private input hashes, включая `local.env`, credentials, TLS и keys.
Секретные значения не выводятся; actual image ID и полный mount set каждого своего
сервиса должны совпасть с prepared pins. Перед API нужны все12 exact ready IDs.

После healthy выполняется узкий existing isolation control: Schedule CRUD в
rollback, запрет cross-DB CONNECT/DDL/TRUNCATE и Mongo own CRUD в abort с запретом
доступа к другой БД. Это только собственный новый realm. Ошибка не приводит к
restart/force-recreate/adoption. Compose, detached readiness и isolation проверяются
одним startup lifetime: 250ms guard, timeout или Ctrl-C закрывают только
metadata-qualified nonce-owned IDs и сохраняют volumes. Неизвестная ownership
остаётся HOLD, а не разрешением остановить чужой ресурс.

`frontends` остаётся в первом терминале, максимум 30 минут. Во **втором терминале**
с тем же новым Node/JDK PATH и новым checkout выполни последовательно:

```bash
cd "$HOME/rutcampustrack-pr3"
/usr/bin/python3 tests/e2e/pr3-local/local.py fixture
/usr/bin/python3 tests/e2e/pr3-local/local.py case
/usr/bin/python3 tests/e2e/pr3-local/local.py summary
```

`fixture` создаёт собственный текущий semester через обычные API на реальном
teaching clock, две новые группы, новых actors/subjects/assignments, две closed
lesson fixtures и два Attendance документа. SQL/Mongo fixture rows ограничены
точными новыми IDs; Auth SQL grant/password writes после initial empty-volume
bootstrap отсутствуют. OS clock/Auth clock/старые realm не меняются.

`case` открывает семь независимых browser contexts: Admin и шесть заранее открытых
WEB/PWA/TMA Headman/retainedTeacher views. Headman видит уникальный target subject;
Teacher держит control group/subject, реальные строки, focus и несохранённый
draft37. Перед confirm проверяются physical WS/personal subscription, normal Auth
и canonical own SID/user/role, два memberships и numeric preview со всеми восемью
count categories. Текущий пароль вводится в настоящий UI, снимок делается до ввода.

Разрешён **один исходный UUID**, созданный обычным UI. Durable intent/UUID/SID
записываются до forward. Разрешён один confirmation POST, далее только original
status GET; resolve/re-confirm/new UUID/manual refresh/new login запрещены.
Unknown ACK/error/timeout сохраняет точные handles в HOLD, а не повторяет удаление.

После confirm до passive assertions нет reload/focus/click/navigation/forced
reconnect. Headman должен естественно потерять private target. Teacher должен
получить настоящий скрытый personal `group.updated`, автоматически начать catalog
и exact control-stats GET **после** hint boundary и сохранить rows/owner/input/
focus/draft/same SID. Дальше проверяются реально обновлённые options. SQL/Mongo
digests подтверждают foreign accounts/credentials/other grants/profiles/group1/
control rows/session cores и отсутствие hidden-event history/push/audience effects.

Auth COMMIT убирает selected target grant и повышает sessionVersion; весь SID может
остаться жив для других ролей. Старый JWT401 не означает revocation. TMA здесь
**controlled host**, со свежим локальным test signer и реальным Auth/domain/WS;
native Telegram provider не проверяется. Разрешён только один естественный TMA
Headman successor; original и successor квалифицируются и очищаются раздельно.

После successful proof выполняется exact own endpoint cleanup семи browser SIDs
(восьми при указанном successor), затем SQL revocation proof и закрытие contexts.
Это не тест logout-button UI. Три переданных fixture sessions — отдельные исходные
контрольные sessions; они сохранены в realm/evidence, `cleanupComplete` относится
к browser cohort. Нет blanket revoke, смены ролей или дополнительных логинов ради
отчёта. Никакой teardown/restore не выполняется в `finally` при UNKNOWN.

## При STOP / UNKNOWN и завершение

Не повторяй prepare/start/fixture/case, не удаляй `.pr3-local`, не меняй UUID,
не запускай refresh/login/logout/resolve/reset/cleanup helper с новым intent.
Для read-only разбора сохрани первый терминал/точные handles и выполни:

```bash
/usr/bin/python3 tests/e2e/pr3-local/local.py inspect
/usr/bin/python3 tests/e2e/pr3-local/local.py summary
```

`inspect` не обращается к Auth и не пишет domain data: читает исходный operation
в собственной Academic DB и сохраняет metadata. Pending/unknown не считается
PASS. При startup STOP с ещё неготовой БД достаточно сохранённых private logs и
exact owned IDs; недоступный read-only proof не разрешает повтор.
Для **частичного known-owned startup без Auth/domain attempt** команда `stop`
доступна и без `services-ready.json`/работающей БД. Она снова проверяет nonce,
image/mount pins и чужой baseline. Partial/unknown fixture или original case
сохраняются в HOLD; ослабления их admission ради stop нет.

После known terminal result останови первый foreground terminal через Ctrl-C,
затем:

```bash
/usr/bin/python3 tests/e2e/pr3-local/local.py stop
```

Останавливаются только ID с точным namespace+nonce. При nonterminal operation
команда отказывается от штатного stop до разбора original status. `down`, volume
delete, global prune, DB drop, чужой stop, rekey/reset и изменения старого activate
в пакет не входят. Volumes/keys/fixture/evidence остаются для разбора.

## Что передать Root и что остаётся отдельно

Пришли вывод **`summary`**, exit коды actual команд и сообщение о версии Docker/
JDK/Node/npm. Summary содержит SHA/package hash/original UUID+digest/восемь numeric
preview counts/phase/checks/cleanup/
browserClosed и сохраняет FAIL/UNKNOWN. Не присылай `.pr3-local`, env/config,
storage-state/Auth handles, passwords, cookies, keys, raw logs или дампы БД.
Exact original UUID также остаётся в durable private receipt до POST; summary
содержит этот безопасный ID и его digest. Для original-status recovery используй
локальный `inspect`; его session handles/полный вывод публиковать не нужно.
PNG из private directory можно передавать только после личного просмотра, что
на них нет credentials/personal data. До проверки owner output gate остаётся NOT_RUN.

Общий production routing/static/MIME/deep-link/PWA/TMA/WebPush preservation gate:
[отдельный assembled Nginx пакет](../pr3-nginx/README.md). Он не подменяется тремя
Vite servers. Real WebPush delivery, native Telegram provider, production deploy
и CI execution этим локальным group случаем не заявляются; CI остаётся PAUSED.

`source-contract.json` фиксирует semantic provenance accepted runtime180f/cohorta23
и перечисляет local adaptations. Portable launcher/templates и эти adaptations
требуют отдельного structural review. Публичные syntax/negative ownership checks
не запускают Docker/Auth/БД/браузер и не являются local runtime acceptance.
