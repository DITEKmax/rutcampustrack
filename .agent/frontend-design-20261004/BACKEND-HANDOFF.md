# Остаток backend/runtime — передача родительскому чату

Прямое решение владельца: завершить frontend, серверные проблемы передать чату **2 · Главный оркестратор — rutcampustrackFULL**, ID01a09c09-da2a-7c51-b5a5-1c9c157851dc. Frontend готов к визуальным правкам владельца; этот handoff не объявляет серверную приёмку завершённой.

## Goal

Подключить и принять правило повторной геопроверки через5минут при pending AUTO. Родительский чат получает оставшуюся серверную/runtime ответственность; frontend код и его dev servers остаются в текущем чате. Не выполнять слепой повтор Update.

## Context / evidence

- Rule source37e32202 уже интегрирован тремя коммитами main0e557735/6976b12c/1cc91e21. Backend blobs совпадали; серверные unit15/Mongo27 и независимый review прошли.
- Main frontend536a6e97 +3c3e268f.77 сценариев для двух оболочек,36 extras,24 нижних кадра:214 визуальных снимков. Реальная PWA Student-сессия последним reload успешно открыла TodayEmpty; сегодня воскресенье4октября.215-й снимок — фактический серверный результат. Ранее были Academic/Schedule503, сейчас не воспроизводятся, причина неизвестна.
- Single Attendance bootJar на37e32202: BUILD SUCCESS44s. Версионный JAR и attendance-manifest.json находятся в runtime-artifacts этого evidence каталога. JAR SHA256 `67a682b97fff4cfc91a3e17c5b79fbc2600d80ff7ded308cb20c74d5c61c0fe6`; manifest SHA256 `60FB27EC9D7BAE9B2E18450B039130A09265CC043D0BF27C90A8E24C0BA0176A`.
- Helper r2/r3/r4 прошёл соответствующие независимые affected recheck. R3 отсутствующий Health исправлен и проверен настоящим Docker Go template на15 своих контейнерах.
- Actual r3 Update завершился `ATTENDANCE_UPDATE_FAILED_ROLLBACK_FAILED_PRIVATE_BACKUP_RETAINED` **до recreate**. Отдельное безопасное сопоставление подтвердило exact before pins, прежние ID/StartedAt Attendance, healthy; read-only Check PASS. Исходная причина потеряна nested catches. Нельзя считать код ошибки доказательством повреждённого rollback: actual old state подтверждён отдельно.
- R4 добавляет safe phase и раздельные allowlisted update/rollback causes в приватный result. Helper SHA256 `13B647C553EDCB12872AE861E7D0525EE2ED2D1EAC756C47A7B80A4F93471AE4`, Compose SHA256 `4510F86DA16129019E0A3EA519330E157FE2A70764E098161E83AFCFB8D51F0A`. Независимый review PASS. Actual read-only harness завершился на before-validated-config: DETAILS_WITHHELD, line45; это ещё не установленная причина actual Update.

## Relevant scope / ownership

В main остаются **ровно2 собственных uncommitted helper файла**: scripts/local-stand.ps1 и infra/local-stand/compose.yml. Они передаются родительскому чату для коррекции и фиксации. Автор source — attendance_local_update в managed worktree `C:/Users/maksd/.codex/worktrees/student-today-gallery/rutcampustrack`; evidence `.agent/attendance-local-update-20261004`. Root завершает helper работу без нового lifecycle вызова. Не трогать чужие dirty документы и frontend/AGENTS.md.

Родительский diagnostic sourceb13a2899 и3JAR BFF/Academic/Schedule готовы в v2-runtime-build-r3; они **не интегрированы и не подключены**. Их не следует подключать без вновь воспроизведённой503.

## Required behavior

AUTO pending → по authoritative retryAt повтор разрешён; failed retry обновляет прежнюю pending заявку, success PRESENT закрывает GEO_CONFIRMED. Manual pending блокирует повтор; исходный accepted idempotency ACK неизменяем. Actual runtime проверять после установления и исправления конкретного helper defect.

## Constraints

Стенд rct-local-persistent:14 normal services + собственный stopped private-file-loader,8 volumes, Bot отсутствует, опубликован только loopback18514. PrivateDirectory `C:/Users/maksd/AppData/Local/RutCampusTrack/local-stand`; rollback backup direct child `attendance-update-29db68b0a63043f392322fb9aea75488`. Эти настройки/backup не печатать; использовать loader и выводить только безопасные codes/metadata/hash результаты.

Старые8 backend JAR и frontend pins сохранены. Verification build временно перезаписал mounted frontend dist, затем все16 old manifest файлов реконструированы с точными SHA и восстановлены. Принятый published18514 остаётся old frontend; новые frontend verification outputs находятся отдельно в production-536a6e97 / production-login-dark. Не запускать npm build в mounted main dist.

## Existing patterns

Helper проверяет ownership, manifests/hashes/ACL,8 mixed provenance entries, other configs и ID/StartedAt/restart/mount metadata. Адресный единственный lifecycle selector: `up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 180 attendance-service`. Old pins/JAR сохранены приватно; automatic rollback и отдельный RollbackAttendance существуют, actual acceptance этих действий остаётся открытой.

## Acceptance criteria

Конкретная причина actual Update установлена безопасной диагностикой, корректировка и affected recheck приняты, только Attendance пересоздан и healthy, остальные контейнеры/конфигурации/mounts не менялись. Нужное правило принято реальным API либо явно сохранена точная граница непроверенного, без production-ready заявления.

## Verification

Root evidence: runtime-artifacts/{docker-health-template-r3.json,update-r3-safe-result.json,update-readonly-r4.jsonl}, frontend-pin-restoration.json, live-today-evidence.json. Read-only Check после неуспеха PASS. Source tests/review переиспользовать; новые проверки только по найденному defect. Диагностический harness привязан к проверенному helper; это не sandbox для произвольного файла. При чтении конфигурации не выводить env, logs, native error или секреты.

## Do not

Не full Start/Stop/Initialize/rebuild, не менять frontend, не перезаписывать frozen artifacts, не создавать новый полный стенд, не tunnel/provider/Bot, не reset/reseed/delete data/backup, не production deploy/push/merge. После передачи root этого frontend чата принимает только визуальные правки владельца.
