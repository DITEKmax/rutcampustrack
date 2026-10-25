# Подготовка первого production release

Канонический Windows checkout: `C:/Users/maksd/IntelliJIDEA/rutcampustrack`, ветка `main`.
Ubuntu checkout ниже остаётся отдельной рабочей копией; автоматической синхронизации нет.

Этот пакет готовит файлы и доставку. Deploy остаётся отключённым. Новая repository variable
`PRODUCTION_DEPLOY_ENABLED` по умолчанию отсутствует: обе стартовые job требуют значение
`true`. **Сейчас не создавать её со значением true и не включать workflow.** Существующий
автоматический trigger после CI сохранён; добавлен отдельный закрытый по умолчанию gate.
Включение, первый запуск и переход с ACME bootstrap требуют отдельного разрешения.

## Приватные файлы внутри проекта

Текущий layout: `.secrets/production`, `.secrets/local`, `.secrets/observer-production`,
`.secrets/observer-local`. Корневые `.env.prod` и `.env` — редактируемые конфигурации;
одноимённые файлы внутри vault — производные копии. `/.secrets/` исключён и из Git,
и из Docker context; `.env`/`.env.prod` тоже не коммитятся. Example-файлы содержат только
placeholders. Production env хранит **серверные** `/opt/rutcampustrack-secrets/active/...`
пути, local — переносимые `.secrets/local/files/...` относительно корня проекта.

Существующие четыре набора переносятся целиком, с проверкой равенства содержимого.
`imported-env.original`, GHCR token, SSH key pair и другие исторические файлы сохраняются.
Команды ниже не перемещают старые каталоги: оператор сначала выполняет проверенный перенос,
создаёт `.secrets` с mode0700 и кладёт актуальные env в корень. Затем, **из native Ubuntu
checkout**, один раз принимает существующие наборы без генерации значений:

```bash
cd /home/maksd/rutcampustrack-local-stand
python3 scripts/deployment-secrets.py adopt-project --project-root "$PWD" --all
```

Adoption сверяет env с сохранённым inventory, 41 файл с bundle, TLS и старые operator
пароли с APR1 hash. При несовпадении отказывает до записи. Обновляет пути и комментарии,
сохраняет все credential bytes и salt, извлекает существующие hash/map-only пароли в
именованные `editable/` файлы. Повторная adoption проверяет уже принятый набор как refresh.
`prepare`/`prepare-observer` оставлены только для **первичной генерации нового отсутствующего
набора во внешнем staging-каталоге**; для этих уже подготовленных наборов их не вызывай.

Linux private directories имеют mode0700 и принадлежат владельцу. Local исходные `files/`
имеют0644, чтобы ты мог их редактировать, а явно назначенный Docker UID мог читать bind mount;
производные ACL/maps/htpasswd имеют0444. Приватность на host обеспечивается parent0700.
Production source files, env, editable и inventory имеют0600. Серверная установка отдельно
применяет0444 для bind mounts. Целый vault в контейнер не монтируется.

Требуются Linux, Python3, bash, OpenSSL и git. Команды работают офлайн, без запуска сервисов,
сети, отправки в Ubuntu/GitHub/VPS. Не используй `set -x`, не печатай env, bundle, inventory
или обычный `docker compose config`; для проверки используй `config --quiet`.

## Где менять пароль и как обновить payload

Самый короткий путь — открыть **Ubuntu-проект** в Проводнике:
`\\wsl.localhost\Ubuntu-24.04\home\maksd\rutcampustrack-local-stand`.
Редактируй именно эти файлы, затем запускай команды в Ubuntu. Windows checkout — отдельная
копия; автоматической синхронизации между ними нет. Не запускай adopt/refresh для `/mnt/c/...`:
Windows linked worktree имеет Windows Git pointer, а его приватность задаётся ACL owner+SYSTEM,
не Linux chmod. Скрипт проверяет native Git index, владельца и приватные Linux permissions.

| Что меняешь до первого запуска | Единственный редактируемый источник |
|---|---|
| Mongo, Redis default, RabbitMQ, Grafana, Telegram/VAPID и другие inline значения | Корневой `.env.prod` либо `.env`, соответствующая переменная |
| PostgreSQL роли, Redis cache runtime, направленные file tokens | `.secrets/<set>/files/`: точное имя указано комментарием над `*_FILE` в env |
| Mongo monitor | `files/mongo-monitor-env`, строка `MONGODB_PASSWORD=...` |
| Operator BasicAuth для production/local | `editable/operator-password`; `SWAGGER_HTPASSWD` — производный hash |
| Redis monitor и Redis cache monitor | `editable/redis-monitor-password` и `editable/redis-cache-monitor-password` |
| TLS key/certificate | Именованные пары `files/<service>-tls/`; должны соответствовать друг другу, SAN и сроку |
| Observer DB/signing key | `.secrets/observer-<mode>/files/glitchtip-db-password` и `glitchtip-secret-key` |
| Observer proxy/native admin | `editable/operator-password` и `editable/glitchtip-admin-password` внутри observer set |
| Observer URL/порт и будущий admin email | `observer.env`, включая `RCT_GLITCHTIP_ADMIN_EMAIL`; изменение email не создаёт аккаунт |

Пароли в именованных plaintext files: одна строка UTF-8 без BOM, перенос LF,
32–128 символов `A-Z a-z 0-9 _ -`, разные для разных ролей. Направленные tokens сохраняют существующий формат32 random bytes
в base64url без padding. Inline значения проходят действующий validator; Mongo/Rabbit URI
userinfo не допускает пробелы и reserved symbols. BOT_TOKEN и TMA_BOT_TOKEN должны совпадать.
Local Telegram tokens намеренно нерабочие, VAPID — synthetic placeholders: provider-клиенты
этой конфигурацией не запускаются.

После правки выбранного набора:

```bash
cd /home/maksd/rutcampustrack-local-stand
python3 scripts/deployment-secrets.py refresh-project --project-root "$PWD" --mode production
# Вместо production выбери local для локального набора.
# Для отдельного observer набора:
python3 scripts/deployment-secrets.py refresh-project --project-root "$PWD" --mode production --observer
```

Скрипт пересобирает ACL/maps, htpasswd со старой salt, `PROD_ENV`, `PROD_SECRET_FILES_B64`,
cached env и inventory. Не редактируй эти производные файлы вручную. Новые случайные значения
не создаются. Все выбранные наборы (`--all` доступен) проверяются до первой замены; старые
версии сохраняются в `.secrets/history/`. При ошибке записи выполняется rollback; history
остаётся приватной резервной копией и автоматически не удаляется. При отказе проверь
диагностический код, исправь исходный файл и повтори refresh.

Это подготовка **до установки**, не смена пароля работающей БД. Уже установленный production
vault остаётся immutable: delivery откажет при отличии credentials. Изменение БД, аккаунта,
GitHub Secrets или production требует отдельной согласованной операции. GlitchTip admin
password подготовлен, но аккаунт по-прежнему не создан.

### Если правка сделана в Windows checkout

Предпочитай прямое редактирование Ubuntu-файлов выше. Если менял Windows-копию, вручную
перенеси только authority выбранного набора следующими командами **в Ubuntu**. Закрой
редакторы обеих копий. Windows `.secrets` уже должна иметь приватный ACL owner+SYSTEM.
Команды не удаляют файлы и сохраняют перезаписываемые версии; другие наборы не затрагиваются.

```bash
set -eu
umask 077
windows=/mnt/c/Users/maksd/IntelliJIDEA/rutcampustrack
project=/home/maksd/rutcampustrack-local-stand
set_name=production  # production | local | observer-production | observer-local
case "$set_name" in
  production) mode=production; env=.env.prod ;;
  local) mode=local; env=.env ;;
  observer-production) mode=production; env=.secrets/$set_name/observer.env ;;
  observer-local) mode=local; env=.secrets/$set_name/observer.env ;;
  *) exit 1 ;;
esac
source_set="$windows/.secrets/$set_name"
target_set="$project/.secrets/$set_name"
test -d "$source_set/editable" && test -d "$target_set/editable"
test -z "$(find "$source_set/files" "$source_set/editable" -type l -print -quit)"
stamp=$(date -u +%Y%m%dT%H%M%S)-$$
backup="$project/.secrets/history/manual-import-$stamp"
mkdir -m 700 -p "$backup"
rsync -r --no-links --chmod=D700,F600 --backup --backup-dir="$backup/files" \
  --exclude=/redis-server-acl --exclude=/redis-cache-server-acl \
  --exclude=/redis-monitor-password-map --exclude=/redis-cache-monitor-password-map \
  --exclude=/observer-htpasswd "$source_set/files/" "$target_set/files/"
rsync -r --no-links --chmod=D700,F600 --backup --backup-dir="$backup/editable" \
  "$source_set/editable/" "$target_set/editable/"
rsync --no-links --chmod=F600 --backup --backup-dir="$backup/env" "$windows/$env" "$project/$env"
case "$set_name" in
  observer-*) python3 "$project/scripts/deployment-secrets.py" refresh-project --project-root "$project" --mode "$mode" --observer ;;
  *) python3 "$project/scripts/deployment-secrets.py" refresh-project --project-root "$project" --mode "$mode" ;;
esac
```

После успешного refresh можно вернуть snapshot выбранного набора в Windows. Используй те же
переменные из блока выше; при прямой правке Ubuntu задай только переменные и case-блок,
не выполняя Windows→Ubuntu rsync. ACL каталога Windows должен уже быть закрыт. Не включай `--delete`.
`rsync` здесь сохраняет Windows ACL наследованием, не пытается назначать Linux владельца:

```bash
backup_windows="$windows/.secrets/history/from-ubuntu-$stamp"
mkdir -p "$backup_windows"
rsync -r --no-links --no-perms --no-owner --no-group --backup --backup-dir="$backup_windows/files" \
  "$target_set/files/" "$source_set/files/"
rsync -r --no-links --no-perms --no-owner --no-group --backup --backup-dir="$backup_windows/editable" \
  "$target_set/editable/" "$source_set/editable/"
for name in owner-inventory.json project-vault.json; do
  rsync --no-perms --backup --backup-dir="$backup_windows/metadata" "$target_set/$name" "$source_set/$name"
done
rsync --no-perms --backup --backup-dir="$backup_windows/env" "$project/$env" "$windows/$env"
case "$set_name" in
  observer-*) : ;;
  *) for name in PROD_ENV PROD_SECRET_FILES_B64 "$env"; do
       rsync --no-perms --backup --backup-dir="$backup_windows/payload" "$target_set/$name" "$source_set/$name"
     done ;;
esac
```

Проверь Windows ACL после копирования. Исторические imported inputs, SSH/GHCR credentials,
чужие наборы и history целиком этими командами не синхронизируются. Для GitHub upload затем
используются только обновлённые `production/PROD_ENV` и `PROD_SECRET_FILES_B64` и ранее
сохранённые независимые CI credentials. Сам refresh ничего не отправляет.

## Поддержанный локальный data-only запуск

```bash
cd /home/maksd/rutcampustrack-local-stand
bash scripts/local-data.sh .env config
# Только после отдельного решения запустить локальные БД:
bash scripts/local-data.sh .env up
```

Это шесть datastores с проектом `rct-local-data`, отдельными volumes/container names и
сетью 172.31.0.0/24. Порты совпадают с operator-портами ниже: проверь, что они свободны.
Существующий локальный стенд автоматически не меняется. Для полного приложения продолжай
использовать его принятый launcher. **Обычный `docker compose up` с новым `.env` не поддержан:**
корневой `docker-compose.yml` содержит старую схему ролей. Для data-only выбран production
bootstrap плюс `docker-compose.local-data.yml`; notification-bot и приложения не запускаются.
Исполняй shell/init scripts из Linux checkout с LF, а не из NTFS checkout с CRLF.

## GitHub Secrets и закрытый gate

Значения загружать как содержимое файлов, без дополнительных кавычек и без base64 поверх
уже готового `PROD_SECRET_FILES_B64`. Каждый payload проверяется до любых writes; предел
48 KiB на Secret. Bundle — base64 JSON с фиксированными 41 путями, без архивов и произвольных
имён. Не дели его вручную и не добавляй лишние entries.

| GitHub Secret | Источник |
|---|---|
| `PROD_ENV` | `.secrets/production/PROD_ENV` |
| `PROD_SECRET_FILES_B64` | `.secrets/production/PROD_SECRET_FILES_B64` |
| `GHCR_TOKEN` | Сохранённый файл `GHCR_TOKEN`; нужен pull/push нужных GHCR packages |
| `SSH_PRIVATE_KEY` | Новая отдельная CI пара; сначала вручную установить соответствующий public key |
| `SSH_KNOWN_HOSTS` | Подтверждённые host keys этого VPS; не получать автоматически через ssh-keyscan при deploy |
| `VPS_HOST` | `212.237.218.244` после сверки с владельцем |
| `VPS_USER` | `maksd`, если именно ему установлен CI public key |

Адрес: [Actions Secrets](https://github.com/DITEKmax/rutcampustrack/settings/secrets/actions).
Upload и включение gate — разные действия; подготовка файлов не даёт разрешения на них.
Ключ CI не заменяет личный SSH key и не требует password SSH/root SSH/NOPASSWD sudo.
Можно добавить public key в authorized_keys с `restrict` (без forwarding/PTY), сохраняя
существующие ключи. CI требуется членство в Docker group, которое уже даёт привилегированный
доступ к Docker; новая политика sudo не нужна.

## Одноразовая подготовка VPS — пока не выполнять

После отдельного GO администратор подготавливает каталоги (замени пользователя только
после явного решения):

```bash
sudo install -d -m 700 -o maksd -g maksd /opt/rutcampustrack-secrets
sudo install -d -m 700 -o maksd -g maksd /opt/rutcampustrack/releases /opt/rutcampustrack/incoming
```

Payload атомарно устанавливается в `/opt/rutcampustrack-secrets/active`. Сам `active` до
первой установки не создаётся. Parent и active принадлежат CI user, mode0700. Credential
files и TLS keys имеют mode0444: это необходимо для реальных разных UID процессов при
Docker bind mounts. Приватность на host обеспечивается owner-only parent0700; контейнер
видит только явно назначенные файлы. Каждый TLS private key монтируется только своему
серверу, клиент получает только `.crt`. Compose `uid/gid/mode` для file secrets не исправляет
host permissions, поэтому на него не полагаемся. `.env.prod` остаётся0600. Не меняй parent
на0755 и не монтируй весь vault в контейнер. Если установленный набор отличается хотя бы
одним файлом или permission, deploy откажет; смена паролей/сертификатов — отдельная операция.

До первого запуска нужно подтвердить серверное состояние, сертификаты и права доступа.
Существующие `rutcampustrack_certbot-conf` и `rutcampustrack_certbot-www` сохраняются.
`ruttrack-acme-bootstrap` сейчас держит80: его согласованный останов и возможность быстрого
возврата должны быть частью отдельного плана cutover. Deploy откажет, пока он запущен.
При наличии старых datastore volumes без managed vault он тоже откажет: нельзя подменять
credentials существующих БД новым набором. Ни volumes, ни backups автоматически не удаляются.

Workflow проверяет exact40-char SHA, checkout/build/образы привязаны к нему. `git archive`
доставляет те же исходники в отдельный `releases/SHA-RUN_ID-ATTEMPT`; `git pull main` нет.
Retry того же SHA создаёт новый каталог, старый не перезаписывается. Existing JWT volume
сохраняется; пустой инициализируется один раз, частичный набор отвергается. `volume-nocopy`
исключает копирование build-time JWT из image. Public JWT остаётся доступен Notification
(directory0755/public0644), private/kid0600 для Auth UID100:GID101. После первого release
rollback к старым образам не является rollback данных; отдельный GO и совместимость схем обязательны.

Remote и runner traps очищают payload/token после ошибки. При потере SSH связи проверь
private `incoming/RUN_ID-ATTEMPT` вручную; не публикуй его содержимое. Источники release и
устойчивый vault сохраняются. TLS проверяется по SAN, паре key/cert, server purpose и
остатку срока >=30 дней. Автоматической ротации нет.

**Существующий release blocker:** web-panel остаётся в build/sign/deploy и root/login nginx.
Его deferred status не снят этой подготовкой; build Dockerfile и UI приёмку нужно закрыть
перед включением Deploy. Ни работающий VPS, ни готовый публичный release здесь не заявлены.

## Доступ владельца к БД и веб-инструментам

Production command обязательно включает `docker-compose.operator.yml`. Все его ports
привязаны к127.0.0.1; снаружи доступен только SSH. После фактического запуска:

```bash
ssh -N -L 15432:127.0.0.1:15432 -L 15433:127.0.0.1:15433 \
  -L 27018:127.0.0.1:27018 -L 16379:127.0.0.1:16379 \
  -L 16380:127.0.0.1:16380 -L 15672:127.0.0.1:15672 \
  -L 15673:127.0.0.1:15673 maksd@212.237.218.244
```

Подключай клиент к localhost/порту из таблицы. Пароль бери из inventory/указанного файла,
вводи в password prompt клиента; не помещай его в history, URI или process arguments.

| Данные | Порт | Пользователь / пароль | Реальные права |
|---|---:|---|---|
| PostgreSQL academic_db |15432|`rct_academic_runtime` / PG_ACADEMIC_SERVICE_RUNTIME_PASSWORD_FILE|Прикладные DML в своей БД после миграций|
| PostgreSQL auth_db |15432|`rct_auth_runtime` / PG_AUTH_RUNTIME_PASSWORD_FILE|Прикладные DML в своей БД после миграций|
| PostgreSQL schedule_db |15433|`rct_runtime` / PG_SCHEDULE_RUNTIME_PASSWORD_FILE|Runtime своей БД|
| PostgreSQL оба процесса |15432/15433|`rct_bootstrap` / соответствующий PG_*_BOOTSTRAP_PASSWORD_FILE|Superuser; административный доступ ко всем данным процесса|
| Mongo attendance_db |27018|MONGO_USER / MONGO_PASSWORD, authSource=admin, directConnection=true|readWrite+dbAdmin attendance_db|
| Mongo notification_db |27018|MONGO_NOTIFICATION_USER / MONGO_NOTIFICATION_PASSWORD, authSource=admin, directConnection=true|readWrite+dbAdmin notification_db|
| Mongo оба DB |27018|`root` / MONGO_ROOT_PASSWORD, authSource=admin, directConnection=true|Root|
| Redis critical |16379|`default` / REDIS_PASSWORD|Все команды/ключи; осторожно с изменениями|
| Redis cache |16380|`rct_cache` / REDIS_CACHE_RUNTIME_PASSWORD_FILE|Только разрешённые cache-команды и rct:cache:*|
| Rabbit management |15672|RABBITMQ_USER / RABBITMQ_PASSWORD|Существующий initial администратор; UI по http://localhost:15672|
| Rabbit AMQP |15673|Та же пара|Прикладное подключение|

`rct_monitor` PostgreSQL/Mongo/Redis предназначен для метрик, не обещает чтение прикладных
таблиц/документов/ключей. Отдельные read-only operator accounts этим bootstrap не создаются.
Для Redis cache подходят CLI SCAN/GET разрешённого prefix; административный GUI может
не работать, поскольку INFO и административные команды для rct_cache не разрешены.
PG_ACADEMIC_RUNTIME_PASSWORD_FILE — legacy `rct_runtime`, у которого отозван доступ к
academic_db; для просмотра используй активные роли из таблицы.

[Grafana](https://ruttrack.site/grafana/), [Prometheus](https://ruttrack.site/prometheus/),
[Swagger](https://ruttrack.site/swagger-ui/) защищены общим operator BasicAuth из inventory.
Grafana после него имеет собственный login `admin` и GRAFANA_PASSWORD; edge удаляет operator
Authorization header перед Grafana, чтобы разные credentials не конфликтовали. Открытый
Swagger не отменяет авторизацию API. Неподтверждённые внешние Telegram/VAPID и доступ к VPS
остаются отдельными acceptance checks после разрешённого запуска.

## Все остальные production-хранилища и инструменты

Для HTTP query/API портов добавь второй SSH tunnel (или добавь эти `-L` к команде выше):

```bash
ssh -N -L 19090:127.0.0.1:19090 -L 19093:127.0.0.1:19093 \
  -L 13100:127.0.0.1:13100 -L 13200:127.0.0.1:13200 \
  -L 13000:127.0.0.1:13000 maksd@212.237.218.244
```

| Сервис и данные | Через SSH к localhost | Вход и примеры чтения | Где сохраняется |
|---|---|---|---|
| Prometheus, метрики TSDB |19090 →9090|Нативный API без password в текущем config: GET `/api/v1/query?query=up`, `/api/v1/targets`|`rutcampustrack_prometheus-data`, `/prometheus`|
| Alertmanager, silences/notification state |19093 →9093|Нативный API без password: GET `/api/v2/status`, `/api/v2/alerts`, `/api/v2/silences`|`rutcampustrack_alertmanager-data`, `/alertmanager`|
| Loki, логи/индекс/WAL |13100 →3100|`auth_enabled: false`; GET `/loki/api/v1/labels`, `/loki/api/v1/query_range` с URL-encoded LogQL|`rutcampustrack_loki-data`, `/loki`|
| Tempo, traces/blocks/WAL |13200 →3200|Нативный HTTP API без password: GET `/api/traces/<traceId>`, `/api/search`; UI — Grafana Explore|`rutcampustrack_tempo-data`, `/var/tempo`|
| Grafana, dashboards/users/settings SQLite |13000 →3000|Собственный `admin` / GRAFANA_PASSWORD; API с prefix `/grafana/api/`, например `/grafana/api/search`|`rutcampustrack_grafana-data`, `/var/lib/grafana/grafana.db`|
| Alloy, collector checkpoint/state |Отдельного DB port нет|Читай состояние через Grafana/логи контейнера. Внутренний diagnostic HTTP12345 не опубликован этим override|`rutcampustrack_alloy-data`, `/var/lib/alloy`|

SSH key защищает доступ к этим loopback портам. У Prometheus, Alertmanager, Loki и Tempo
в данном config нет отдельного логина/пароля: не вводи туда operator password и не открывай
порты во внешний интерфейс. Прямой API может допускать изменения, не только просмотр;
публичный nginx для Prometheus/Alertmanager дополнительно ограничивает методы GET/HEAD.
Ingestion/OTLP ports4317/4318 этим override не публикуются. Loki/Tempo не имеют отдельного
SQL login: данные просматриваются через их API или настроенные Grafana datasources.

Предпочтительный браузерный вход: [Grafana](https://ruttrack.site/grafana/),
[Prometheus](https://ruttrack.site/prometheus/), [Alertmanager](https://ruttrack.site/alertmanager/)
через HTTPS и operator BasicAuth. Grafana затем требует собственную учётную запись.
Её `root_url=https://ruttrack.site/grafana/` и `serve_from_sub_path=true` сохраняются:
прямой `http://localhost:13000/` не обещает полноценную навигацию, ссылки/редиректы могут
увести на production hostname. Для диагностического API сохраняй `/grafana/` prefix;
не меняй root_url ради туннеля. Prometheus/Alertmanager также формируют UI links с внешним
prefix; прямой API обслуживается от `/` согласно `--web.route-prefix=/`.

Grafana использует SQLite по умолчанию, поскольку внешняя `[database]` здесь не настроена.
Отдельного DB TCP port, PostgreSQL password для неё и network login к Alloy state нет.
Для прямого анализа SQLite нужна согласованная консистентная копия/backup, не редактирование
живого grafana.db. Остальные persistent файлы production: `rutcampustrack_jwt-keys`
(private/public/kid, не БД), `rutcampustrack_certbot-conf` (TLS account/certificates) и
`rutcampustrack_certbot-www` (ACME challenge files). Доступ к ним — через SSH и Docker/backup
процедуру; они не публикуются как файловый сервер. Redis cache хранится в памяти, отдельного
persistent volume нет. RabbitMQ state находится в `rutcampustrack_rabbitmq-data` и доступен
через management UI из предыдущей таблицы. Docker logs ограничены logging policy, отдельной
БД для них нет. **ClickHouse в текущем production и observer Compose отсутствует.**

## GlitchTip, Pyroscope, Homepage — отдельный существующий scope

Это реализованные и ранее принятые инструменты, а не план новых сервисов. На текущем VPS
их запуск этим пакетом не подтверждён и не выполняется. `docker-compose.observability-apps.yml`
не входит в production release command. Новый `docker-compose.observability-operator.yml`
добавляет только optional loopback PostgreSQL port15434 к этому отдельному scope.

Отдельные наборы уже подготовлены в `.secrets/observer-production` и
`.secrets/observer-local`: `observer.env`, `owner-inventory.json`, три файла `files/` и
редактируемые пароли `editable/` после adoption. Используй описанный выше refresh с
`--observer`; он не устанавливает сервисы и не создаёт аккаунт. Этот набор **не добавляется**
к фиксированному41-file `PROD_SECRET_FILES_B64`.

| Env reference | Файл | Назначение |
|---|---|---|
|RCT_GLITCHTIP_DB_PASSWORD_FILE|glitchtip-db-password|Пароль роли `glitchtip`, БД `glitchtip` отдельного PostgreSQL|
|RCT_GLITCHTIP_SECRET_KEY_FILE|glitchtip-secret-key|Signing key приложения; не пароль для входа|
|RCT_OBSERVER_HTPASSWD_FILE|observer-htpasswd|Отдельный operator BasicAuth для Homepage/GlitchTip/Pyroscope proxy|

В inventory отдельно подготовлен сильный `glitchtip_admin.password`; `email=null`, status
`not-created`. Email для этого входа выбираешь ты. Аккаунта до будущего разрешённого запуска
нет: не используй BasicAuth login `operator` как выдуманный native GlitchTip email.
Не меняй эти credentials у уже существующего observer stack — для него нужны существующие
секреты и отдельный план adoption/rotation.

После отдельного разрешения на установку оператор доставляет только эти три файла в
`/opt/rutcampustrack-secrets/observer`, directory0700 owner CI user, files0444 (та же private
parent policy), а observer.env хранит0600. Сначала проверь, что target отсутствует; не
перезаписывай его автоматически. Генерируемый production observer.env ссылается именно на
эти server paths; local observer.env ссылается на собственные local files. Шаблон команды
проверки конфигурации, которая сама ничего не запускает:

```bash
docker compose --env-file /private/observer.env \
  -f docker-compose.observability-apps.yml -f docker-compose.observability-operator.yml config --quiet
```

Будущий доступ к уже разрешённому и запущенному scope:

```bash
ssh -N -L 18880:127.0.0.1:18880 -L 15434:127.0.0.1:15434 maksd@212.237.218.244
```

| Что смотреть | Адрес после tunnel | Авторизация и состояние |
|---|---|---|
|Homepage, каталог ссылок|`http://localhost:18880/`|Observer BasicAuth из отдельного inventory; собственного логина у links-only Homepage нет|
|GlitchTip UI|`http://localhost:18880/glitchtip/`|Observer BasicAuth, затем отдельно созданный native GlitchTip account|
|GlitchTip PostgreSQL|localhost15434, DB=`glitchtip`, user=`glitchtip`|Пароль из RCT_GLITCHTIP_DB_PASSWORD_FILE; это dedicated bootstrap superuser, не read-only роль|
|Pyroscope UI/query|`http://localhost:18880/pyroscope/`|Observer BasicAuth; отдельный native пароль не настроен. Proxy допускает чтение и конкретные query RPC; ingestion/debug/admin routes закрыты|

Внешних production browser routes для Homepage/GlitchTip/Pyroscope здесь не добавлено.
Существующий `/telemetry/errors/...` — приём browser events, не operator UI. Его наличие
не доказывает, что отдельный observer stack соединён и запущен на VPS. Homepage содержит
ссылки на внешний Grafana/Prometheus/Alertmanager; у них собственный production operator login.
Observer proxy снимает свой Authorization перед приложениями. GlitchTip self-registration
отключён, bootstrap создаёт проекты, но не пароль администратора. После будущего GO и
startup для создания native account используй интерактивный prompt (выбранный email и
подготовленный пароль не передавай в CLI arguments):

```bash
docker compose --env-file /private/observer.env -f docker-compose.observability-apps.yml \
  exec glitchtip /bin/sh /observer/start.sh python manage.py createsuperuser
# После создания свяжи native admin с существующими фиксированными проектами:
docker compose --env-file /private/observer.env -f docker-compose.observability-apps.yml \
  exec -T glitchtip /bin/sh /observer/start.sh python manage.py shell < infra/glitchtip/bootstrap-projects.py
```

Это будущие команды изменения состояния, сейчас они не выполнялись. Observer PostgreSQL
сохраняется в `<RCT_OBSERVER_PROJECT>_glitchtip-db`, uploads — в `..._glitchtip-uploads`.
Pyroscope использует filesystem volumes `..._pyroscope-data`, `..._pyroscope-compactor`,
`..._pyroscope-metastore`, не ClickHouse и не отдельный SQL endpoint. Homepage читает
репозиторные config-файлы и использует временный cache; своей persistent БД нет. Valkey
в текущем GlitchTip config отключён и отдельного Redis для него не создаётся.

## Первый FULL администратор

Для отдельного default-off ручного шага Deploy см. [runbook первого администратора](initial-administrator-deploy.md). Подготовка запроса не создаёт аккаунт и не запускает выпуск.
