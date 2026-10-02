# Backup/restore PostgreSQL, MongoDB и файлов

Этап Ж — серверная эксплуатационная подготовка. Скрипты создают неизменяемый
комплект и проверяют восстановление на свежем изолированном окружении. Deploy,
production restore, миграции и удаление существующих backup здесь не выполняются.

## Контракт комплекта

`academic.dump` и `schedule.dump` — PostgreSQL custom archives без владельцев/ACL.
`attendance.archive` и `notification.archive` — логические MongoDB archives
только соответствующих БД, без admin/users исходного окружения. `files.tar` —
регулярные файлы и каталоги в режиме `--files-dir`, пустой архив в режиме `--no-files`.
`inventory.json` — точные данные, SQL schema и состояния
sequences PostgreSQL; документы, индексы/options MongoDB; пути/байты файлов.
`manifest.json` появляется последним и содержит SHA256 всех шести компонентов.
Отсутствие manifest означает неполный backup. Backup никогда не перезаписывается.
Manifest явно фиксирует `files_mode=files|database-only`. Restore требует такой же
явный режим; несовпадение отклоняется до доступа к целевым БД или записи файлов.
Прежние format-1 комплекты без `files_mode` считаются файловыми и принимаются
только с `--files-dir`, даже если дерево было пустым. `database-only` с заявленными
файлами или непустым tar отклоняется; он не может скрыть пропуск файлового payload.

## Реальные сохраняемые stores backend

В текущем backend пользовательские вложения находятся внутри БД. Отдельного
файлового store нет; production backup выбирает `--no-files` без фиктивной папки.

| Данные | Источник и persistent mount | Компонент backup |
|---|---|---|
| ДЗ: title/description/link, completion и binding; изображения PNG/SVG карт | `academic_db`: `homeworks`, `campus_map_asset.content BYTEA`; `pg-academic-data:/var/lib/postgresql/data` | `academic.dump` |
| Расписание, уроки, binding/lifecycle/outbox | `schedule_db`; `pg-schedule-data:/var/lib/postgresql/data` | `schedule.dump` |
| Заявки, журнал и вложения: `request_attachments.data` BSON Binary | `attendance_db`; `mongo-data:/bitnami/mongodb` | `attendance.archive` |
| Notifications/subscriptions/receipts/outbox | `notification_db` в том же Mongo volume | `notification.archive` |

Критичные оригиналы: [Homework.java](../../../services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java),
[CampusMapAdminRepository.java](../../../services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminRepository.java),
[V26__campus_map.sql](../../../services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql),
[RequestAttachmentDocument.java](../../../services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDocument.java),
[StudentRequestService.java](../../../services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java),
[AttendanceAttachmentService.java](../../../services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/AttendanceAttachmentService.java).
Ссылка ДЗ хранится как строка; внешнее содержимое по ссылке не принадлежит этому
backup. Экспорты создаются из БД; renderer использует временные каталоги с cleanup.
Bot отправляет attachment bytes в памяти; отдельного дискового payload нет.

Остальные persistent stores из `docker-compose.prod.yml` учитываются отдельно:

| Store | Mount | Ограничение текущего recovery |
|---|---|---|
| Redis | `redis-data:/data` | Настройки уведомлений, OTP/rate limits, одноразовые tickets и bot dedup; этот комплект не сохраняет Redis snapshot |
| RabbitMQ | `rabbitmq-data:/var/lib/rabbitmq` | Broker state не сохраняется; DB outbox сохранён, но сохранность всех очередей этим drill не подтверждается |
| JWT keys | `jwt-keys:/keys` у Auth, read-only у Notification | `private.key`, `public.key`, `kid.txt` — отдельное защищённое восстановление оператором; не включать в generic file backup |
| Academic/Schedule gRPC TLS | Явные read-only binds из `ACADEMIC_GRPC_TLS_DIR`/`SCHEDULE_GRPC_TLS_DIR` | Secret manager/защищённое внешнее хранение; значения и ключи не входят в комплект |
| HTTPS certificates/challenge | `certbot-conf:/etc/letsencrypt`, `certbot-www:/var/www/certbot` | Не входят в комплект; recovery сертификатов планируется отдельно |
| Prometheus/Alertmanager/Tempo/Grafana/Loki | `prometheus-data:/prometheus`, `alertmanager-data:/alertmanager`, `tempo-data:/var/tempo`, `grafana-data:/var/lib/grafana`, `loki-data:/loki` | Operational history/configuration state не входят в этот DB backup |

Named volumes уже защищают соответствующие данные при обычном пересоздании
контейнеров в **том же Compose project**. Смена project name/новые пустые volumes
не является восстановлением. Их удаление не часть backup/restore. Отсутствующего
attachment mount добавлять не требуется. Если появится реальный файловый store,
его путь нужно включить через `--files-dir` и согласованно остановить writers.

Контрольные суммы обнаруживают повреждение; это не подпись и не шифрование.
Комплект содержит пользовательские данные: только закрытое хранилище (Linux umask
077), ограниченный доступ и отдельно управляемая защищённая offsite-копия. Секреты
и `.env` в комплект не входят; их восстанавливает оператор из secret manager.
Операции не удаляют backup и не реализуют retention. Сохранение backup в единственном
локальном диске не обеспечивает восстановление после утраты этого диска.

Требования: Python 3.9+, Docker CLI/Compose v2, работающие DB containers с
`pg_dump`/`pg_restore`/`psql` и `mongodump`/`mongorestore`/`mongosh`, совместимые
версии исходных и целевых DB tools. Linux/Bash wrappers используют `python3`;
на Windows можно запускать `.py` напрямую или задать `PYTHON=python`.

## Backup: явный источник и остановленные writers

Перед backup останови **всех** writers PostgreSQL, обеих MongoDB и файлов (если есть),
включая фоновые задачи/consumers; дождись завершения выполняющихся транзакций.
Держи их остановленными до конца backup, затем возобнови и проверь сервисы.
`--quiesced` — явное подтверждение этого условия оператором. Скрипт сравнивает
inventory до/после, но эта сверка сама по себе не даёт глобального point-in-time
snapshot при работающих writers. Без остановки writers согласованность между
БД и файлами не гарантирована; нельзя передавать `--quiesced` автоматически.

Файл credentials задаётся явно и читается без shell eval. Нужны
`POSTGRES_ACADEMIC_PASSWORD`, `POSTGRES_SCHEDULE_PASSWORD`, `MONGO_ROOT_PASSWORD`.

```bash
python3 scripts/recovery.py backup \
  --env-file /secure/recovery-source.env \
  --academic-container rct-postgres-academic \
  --schedule-container rct-postgres-schedule \
  --mongo-container rct-mongo-attendance \
  --no-files \
  --output /secure/backups/2026-10-02T030000Z \
  --quiesced --dry-run
# После остановки writers — та же команда без --dry-run.
```

Выбирай новый output для каждого запуска. При ошибке частичный каталог остаётся,
следующий запуск требует другого output. Nonzero exit должен остановить cron/job;
проверяй exit code и наличие валидного manifest. Cron template отключён до отдельной
активации операторского wrapper, который обеспечивает quiescence и возобновление
writers даже при ошибке. Старый однодневный формат `.sql.gz` автоматически не
принимается; проверять/конвертировать его следует в отдельной изолированной процедуре.

`--no-files` и `--files-dir /actual/payload-directory` взаимоисключающие и требуют
явного выбора. Файловый режим остаётся для реального дополнительного store;
`--no-files` применим к доказанному выше DB-only backend. Не использовать его,
если заявленные пользовательские данные действительно находятся вне БД.
Offsite-копия и отдельное восстановление секретов остаются эксплуатационными
ограничениями; рабочий synthetic drill не подтверждает эти внешние процедуры.

## Redis/Rabbit: preflight и границы восстановления

Ниже — операторский порядок для текущей конфигурации, а не проверенный production
DR. Команды discovery только читают состояние; в рамках подготовки они не
выполнялись. Запускать их можно лишь на явно выбранном окружении. Не выводить
`.env`, Docker `Config.Env`, cookie или содержимое Redis keys.

Текущий Compose задаёт `rct-redis`, Redis 7 Alpine, `redis-data:/data`, 96 MiB и
`allkeys-lru`, без явного включения AOF. RabbitMQ 3.13 management использует
`rct-rabbitmq`, `rabbitmq-data:/var/lib/rabbitmq`; hostname/nodename в Compose
не закреплён. `container_name` не доказывает прежний nodename. Фактические имена
volumes зависят от Compose project; сначала установи их, image digest и hostname:

```bash
docker inspect --format '{{.Name}} {{.Config.Image}} {{.Config.Hostname}} {{index .Config.Labels "com.docker.compose.project"}}' rct-redis rct-rabbitmq
docker inspect --format '{{json .Mounts}}' rct-redis rct-rabbitmq
# REDISCLI_AUTH заранее предоставлен оператору защищённым способом, не аргументом -a.
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning INFO persistence
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET dir
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET dbfilename
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET save
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET appendonly
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning INFO memory
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning DBSIZE
docker exec rct-rabbitmq rabbitmq-diagnostics -q status
docker exec rct-rabbitmq rabbitmqctl list_vhosts name
docker exec rct-rabbitmq rabbitmqctl list_queues -p / name durable messages_ready messages_unacknowledged consumers
docker exec rct-rabbitmq rabbitmqctl list_bindings -p / source_name destination_name destination_kind routing_key
```

Повтори queue/binding inventory для каждого фактически обнаруженного vhost.
Запиши nodename/hostname, версии/digests, volume/project identity, topology и
счётчики основного backlog/DLQ; cookie сохраняется отдельно как secret, его значение
не попадает в журнал. Нет прежней identity, версии, состава persistence или
инвентаря checkpoint — **STOP**, не поднимать приложение на пустом store.

Redis нельзя целиком объявлять disposable cache. `notif:prefs:user:*` и legacy
`bot:notif:*`/`bot:notif:cat:*` хранят настройки уведомлений без TTL; отсутствие
означает включённую доставку. Потеря/eviction меняет выбор пользователя, а
`allkeys-lru` не гарантирует его сохранность. Отдельно существуют OTP/rate counters,
одноразовые `ws_ticket:*`/`report_download_ticket:*`, bot dedup leases/completion
и message trackers. Возврат старого snapshot может вернуть уже использованную
capability или потерять dedup; массовый `FLUSHDB` также не является безопасным
восстановлением. Проверяй настройки в изоляции без внешней доставки. Политика
утраты, исключения ephemeral keys и восстановления защит от replay ещё не принята;
до её согласования Auth/выдача tickets и consumers остаются остановленными.

Durable Rabbit queues и экспорт definitions не содержат гарантии сохранности
сообщений. DB outbox повторяет `PENDING`, а не автоматически все `SENT`; у bot
есть отдельный publisher и очередь Telegram send в памяти. Нулевые broker unacked
не доказывают доставку пользователю. Не менять `SENT` на `PENDING`, не purge/DLQ
replay и не создавать новый event ID ради восстановления без отдельного
согласованного плана reconciliation.

### Новый согласованный checkpoint

1. После отдельного разрешения перекрой ingress и останови всех writers,
   schedulers, publishers и consumers: Auth, Academic, Schedule, Attendance,
   notification-web и notification-bot; Gateway/BFF не должны принимать новые
   команды. Дождись завершения транзакций и зафиксируй broker ready/unacked/DLQ.
2. Сохрани принятый PG/Mongo/files bundle с `--quiesced` по процедуре выше.
   Не менять его manifest и не добавлять туда secrets/оперативные stores.
3. Для Redis выбран native persistence checkpoint: отдельно разрешённый `BGSAVE`,
   затем `INFO persistence` с `rdb_bgsave_in_progress=0`,
   `rdb_last_bgsave_status=ok` и подтверждённым новым `LASTSAVE`. Сохранять завершённый
   RDB из фактических `dir`/`dbfilename`, вместе с TTL и защищённой контрольной суммой.
   Если фактически включён AOF, сначала согласовать сохранение полного его native
   набора/manifest; одного произвольного файла недостаточно. Здесь BGSAVE/copy
   не выполнялись; reset TTL и преобразование key values не допускаются этим планом.
4. Для Rabbit выбран полный **cold** state: после остановки publishers/consumers
   отдельно разрешённо остановить broker, сохранить весь фактический volume
   `/var/lib/rabbitmq` при остановленном broker. Зафиксировать прежние nodename,
   version/image, vhosts/topology/counters и защищённую cookie identity. Definitions
   пригодны для сверки topology, но не заменяют message store. Cold state содержит
   чувствительные данные: хранить с теми же ограничениями, что secrets.
5. Связать DB bundle, Redis и Rabbit state с одним checkpoint и остановленными
   writers; определить защищённое внешнее хранение и проверить доступ к ранее
   сохранённой копии отдельно. Writers не возобновлять по одному до завершения
   capture и проверки всех компонентов. Не назначать RPO/RTO/retention по умолчанию.

### После катастрофы: изоляция, восстановление, возврат

После утраты источника восстанавливай **последний ранее согласованный checkpoint**.
Новый BGSAVE/cold snapshot потерянного источника создать нельзя. Если сохранился
только DB bundle, Redis/Rabbit восстановление этим комплектом не доказано:
зафиксировать утрату и STOP до решения владельца, а не молча принять пустые stores.

1. Подготовить отдельную изолированную цель с известными compatible versions,
   новым resource mapping и закрытым ingress/egress providers. Проверить bundle
   принятым restore `--dry-run`; фактический restore сохраняет fresh/disposable
   guards ниже. Не менять исходные backup или прежние volumes.
2. Оператор восстанавливает конфигурацию/signing keys/TLS из защищённого источника
   до запуска Auth: отсутствие JWT keys вызывает генерацию новой identity в
   `JwtService`. Directed credentials обоих направлений и остальные required vars
   проверяются существующим `scripts/validate-env-prod.sh` отдельно; в evidence
   сохранять санитарный результат/exit code, не raw credentials/вывод validator.
3. Восстановить PG/Mongo/files принятой процедурой и сверить inventory. Затем
   восстановить согласованное Redis native state и полный cold Rabbit state только
   в свои изолированные цели, с прежним nodename/cookie identity, permissions и
   совместимой версией. Exact Rabbit nodename mapping — обязательная часть отдельного
   операторского плана; текущий Compose его автоматически не обеспечивает.
4. Сначала поднять только инфраструктуру, без application writers/consumers.
   Повторить discovery, сверить persistence/topology/backlog/DLQ с checkpoint,
   настройки уведомлений и TTL с прошедшим временем. Несовпадение, неизвестные
   snapshots, ошибка integrity или отсутствие authority secrets — STOP.
5. До запуска Auth/delivery согласовать последствия rollback для одноразовых
   capabilities, rate limits/dedup, DB sessions и broker delivery; проверить
   целевые read/auth и отказ replay в изоляции. Затем по отдельному разрешению
   включать authority/domain services, consumers/publishers, BFF/Gateway и ingress.
   Внешнюю доставку включать после reconciliation preferences/backlog/DLQ.

При отказе держать цель изолированной, сохранить логи и checkpoint artifacts,
не повторять restore поверх частичного состояния и не переключать ingress.
Возврат возможен только на отдельно проверенный прежний комплект/authority;
возврат на утраченный или изменённый источник не обещается. Удаление любых volumes,
backup/целей и production переключение требуют отдельной операции и разрешения.

Нерешённые решения владельца: допустимая утрата Redis preferences и ephemeral
состояния, предотвращение rollback/replay capabilities/dedup, broker message loss
и reconciliation, checkpoint cadence/RPO/RTO, offsite provider/доступ/шифрование,
retention и disaster secrets/key recovery. Read-only preflight и этот порядок
не принимают эти решения и не подтверждают actual production DR.

## Проверяемое восстановление: свежая изолированная цель

`restore` требует явный `--target-project=rct-recovery-*`. Все три контейнера
должны иметь labels `io.rutcampustrack.recovery=disposable` и
`com.docker.compose.project`, равный этому project. PostgreSQL user relations и
обе MongoDB должны быть пустыми, files target в файловом режиме должен отсутствовать. Скрипт
сначала проверяет полный manifest и безопасность tar, затем labels/пустоту **всех**
целей, потом пишет. DROP/--drop и overwrite существующих файлов не используются.
PostgreSQL restore выполняется одной транзакцией на каждую БД с exit-on-error;
ошибка любого tool или несовпадение inventory возвращает nonzero.

```bash
python3 scripts/recovery.py restore \
  --bundle /secure/backups/2026-10-02T030000Z \
  --env-file /secure/recovery-target.env \
  --academic-container <isolated-academic-container> \
  --schedule-container <isolated-schedule-container> \
  --mongo-container <isolated-mongo-container> \
  --no-files \
  --target-project rct-recovery-dr-check --dry-run
# dry-run проверяет bundle; labels/пустота требуют runtime команды без --dry-run.
```

Для файлового bundle используй `--files-dir /isolated/restored-files` вместо
`--no-files`. После restore успех означает точное совпадение data/schema/sequences
PostgreSQL, документов/индексов/options **обеих** MongoDB, включая BSON Binary
вложений, и путей/байтов всех заявленных файлов в файловом режиме. Инструмент
проверки сохраняет BSON types. Files должны быть regular files/directories;
symlinks, devices и небезопасные archive paths отклоняются. Права/владельцы/mtime
файлов не являются частью приёмки: после переноса оператор задаёт необходимые
права процессам приложения. Views MongoDB пока не входят в поддерживаемый scope.

Rollback при отказе: держи цель изолированной, сохрани bundle/log, не переключай
сервисы на неё и не повторяй restore поверх частичного результата. Создай новую
свежую цель и устрани причину. Удаление disposable DB/файлов — только отдельной
явной операцией после проверки ownership. Production переключение и восстановление
секретов/прав требуют отдельного плана, проверки и разрешения; данный инструмент
не заменяет такой план.

## Повторяемый synthetic drill

Входные образы должны уже существовать локально. Drill использует официальные
PostgreSQL 16 и MongoDB 7; image refs передаются явно. Никаких pull/build, host
ports, глобальных container/network names или persistent DB volumes.

```bash
python3 scripts/test-recovery.py \
  --work-dir /isolated/recovery-drill-20261002 \
  --project-base rct-recovery-drill-20261002 \
  --pg-image postgres:16 --mongo-image mongo:7-jammy --dry-run
# После назначения ресурса — без --dry-run.
```

Drill создаёт исходные synthetic таблицы/sequence/index, по две записи с
кириллицей в обеих MongoDB с unique index, двоичный файл, вложенный файл и пустой
каталог. Создаёт backup, останавливает свой source, поднимает свежий target с
**другими** credentials, проверяет точное восстановление и защитные отказы:
повторный backup, повреждённая копия, неверный project label, непустая БД.
Не более трёх активных DB containers; memory limits суммарно 1 GiB, tmpfs данные.
При PASS появляется `PASS.json`; при ошибке owned resources/artifacts остаются.
Это доказательство рабочего пути на synthetic данных, не production DR/RPO/RTO.

Очистка отдельной командой с тем же work-dir/project-base проверяет ownership
marker и labels всех контейнеров, удаляет только эти два Compose проекта,
**сохраняет** backup/данные файлов/log artifacts:

```bash
python3 scripts/test-recovery.py \
  --work-dir /isolated/recovery-drill-20261002 \
  --project-base rct-recovery-drill-20261002 --cleanup
```

Перед очисткой нужна явная авторизация на удаление именно этих disposable
ресурсов. Existing backup и чужие контейнеры не затрагиваются. При аварийном
завершении та же cleanup команда применяется только к записанным собственным
проектам; не использовать общий `docker system prune` или `down -v` чужого стенда.
