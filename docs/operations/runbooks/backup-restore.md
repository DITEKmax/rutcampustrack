# Backup/restore PostgreSQL, MongoDB и файлов

Этап Ж — серверная эксплуатационная подготовка. Скрипты создают неизменяемый
комплект и проверяют восстановление на свежем изолированном окружении. Deploy,
production restore, миграции и удаление существующих backup здесь не выполняются.

## Контракт комплекта

`academic.dump` и `schedule.dump` — PostgreSQL custom archives без владельцев/ACL.
`attendance.archive` и `notification.archive` — логические MongoDB archives
только соответствующих БД, без admin/users исходного окружения. `files.tar` —
регулярные файлы и каталоги. `inventory.json` — точные данные, SQL schema и состояния
sequences PostgreSQL; документы, индексы/options MongoDB; пути/байты файлов.
`manifest.json` появляется последним и содержит SHA256 всех шести компонентов.
Отсутствие manifest означает неполный backup. Backup никогда не перезаписывается.

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

Перед backup останови **всех** writers PostgreSQL, обеих MongoDB и файлов,
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
  --files-dir /srv/rutcampustrack-files \
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

**Незакрытый production blocker:** `docker-compose.prod.yml` пока не определяет
устойчивый mount для загруженных файлов. Прежде чем принять production recovery,
нужно зафиксировать реальные persistent paths/mounts всех файловых stores и
подставить их в `--files-dir` (или собрать согласованное дерево). Пустое фиктивное
дерево вместо реальных вложений не является подтверждением готовности.

## Проверяемое восстановление: свежая изолированная цель

`restore` требует явный `--target-project=rct-recovery-*`. Все три контейнера
должны иметь labels `io.rutcampustrack.recovery=disposable` и
`com.docker.compose.project`, равный этому project. PostgreSQL user relations и
обе MongoDB должны быть пустыми, files target должен отсутствовать. Скрипт
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
  --files-dir /isolated/restored-files \
  --target-project rct-recovery-dr-check --dry-run
# dry-run проверяет bundle; labels/пустота требуют runtime команды без --dry-run.
```

После restore успех означает точное совпадение data/schema/sequences PostgreSQL,
документов/индексов/options **обеих** MongoDB и путей/байтов всех файлов. Инструмент
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
