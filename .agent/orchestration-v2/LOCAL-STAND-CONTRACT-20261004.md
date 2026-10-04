# Постоянный локальный стенд — frozen contract 2026-10-04

## Goal
Пользователь открывает локальные PWA/TMA/API, входит в каждую предусмотренную роль; созданные данные переживают штатные stop/start. Этим закрываем конкретный оставшийся пункт серверного этапа, не повторяя принятую бизнес-приёмку.

## Context / evidence
Владелец GO2026-10-04. Main6cf3d7aa после source review и интеграции e39786b0. Предыдущий checkpoint6a058770 содержит принятые реальные Telegram/WebPush/native+application recovery. Read-only bounded сверка known server criteria не выявила нового конкретного backend implementation gap; это не147-story audit/100% продукта. Root проверил Docker28.5.2, runningcontainers0, localhost18514 свободен, доверенные mkcert-файлы существуют внеGit. Старые tmpfs DB нельзя считать живой сохранённой базой после перезапуска Docker; их backup остаётся отдельным источником.

## Relevant scope
Config developer persistent_local_stand_1004: WTv2-runtime-build-r2 branchcodex/local-persistent-1004 baseline6a058770; новая ограниченная Compose/start-status-stop конфигурация. Frontend builder role_control_completion_1004 owns mainfrontends/pwa-vue/dist,tma-vue/dist на source6cf3d7aa. Root sole mainintegrator/common contract/docs. Independent review config/S3 перед runtime. Один heavy lease.

## Required behavior
- Отдельный local project rct-local-persistent, named persistent volumes для реально используемых PG/Mongo/Redis/Rabbit/JWT данных; maps/binary используют существующее реальное хранилище.
- Статические URL https://127.0.0.1:18514/app/ и /mini-app/, весь /api идёт к полноценному Gateway. Не применять student-only public edge к localhost.
- Trusted mkcert и private generated credentials/keys внеGit; собственные volumes имеют явную ownership. Start не создаёт новые credentials/key identities при каждом запуске; stop не удаляет данные.
- Чистый synthetic baseline всех ролей через существующий seed/публичные product API. Назначение старосты и grants не обходить legacySQL. Предыдущий nativebundle не импортировать и не запускать повторное recovery acceptance.
- Reuse8JavaJAR/backendimages; одна новая согласованная frontend build с process-only VITE_PUBLIC_BASE=/app/ и /mini-app/, version6cf3d7aa. Manifest {sourceRevision,pwa:{path,base,version,files:[{relativePath,sha256}]},tma:{path,base,files:[{relativePath,sha256}]}} и егоSHA; файлы отсортированы, confines/identity проверены. Backend manifest отдельно закреплён.

## Constraints
Старые26resources/backup/keys сохранить; no down-v/rm/prune/foreignkill. Никакого publictunnel, botmenuchange, повторной provider-доставки, deploy/productionmigration/secretrotation. Optional bot profile до отдельного запуска; не требует повторить уже принятую реальную доставку. Не читать/печатать secrets/rawenv/config/logs; только нужные generated values в памяти approvedprivate loader, sanitized metadata/result.

## Existing patterns
Текущие serviceCompose/environment/init-mongo.js/seed, принятый runner в WTv2-requests-harness и его verified8JARmanifest. Новый wrapper ограничен удобным локальным lifecycle, не скрытым orchestrationplane. Gateway routes /api/v1/student|teacher|map сохраняют prefix, /api/auth|academic|schedule|attendance|notifications|ws StripPrefix=1. Existing roleACK/sessionVersion/generation и allrole grant checks не меняются.

## Acceptance criteria
1. Health/start и actual synthetic bootstrap через localhost без student-only404. Одна минимальная матрица ниже на нужном fresh token/group/semester, не все CRUD заново.
2. Создать один synthetic object через действующий API; запомнитьID/существенные данные и namedvolume/key identities; graceful stop/start и прочитать тот же object без потери/дублирования.
3. Одна последовательная STUDENT→HEADMAN→STUDENT роль через PUT/serverACK; после нового authoritative token ticket200. Stale-token409 ожидаемо допустим, ошибка по свежему token требует конкретного расследования. Не глушить guards.
4. Ресурсы принадлежат только новому project, localhost-onlyport, DBports не опубликованы; минимальный отказ безauth/admin scope достаточен для новой edge/config границы, существующие authsuite не повторять.

| Роль/этап | Минимальный метод и путь | Данные / граница |
|---|---|---|
| PWA login/session | POST /api/auth/login; GET /api/auth/session | Cookie/Bearer по текущему клиентскому контракту; responseprivate |
| Все роли | PUT /api/auth/session/active-role | role, expectedSessionVersion; новыйtoken +canonicalsession послеACK |
| Student/assistant | GET /api/v1/student/session → GET /api/academic/assistants/me/permissions | permissions толькоесли есть положительныйgroupId; assistant остаётсяSTUDENT |
| Student home | GET /api/v1/student/today; GET /api/v1/student/homework | Currentgroup/semester coherence |
| Headman home | GET /api/schedule/groups/{groupId}/lessons | dateFrom/dateTo/page/size/status; groupизactivegrant, HALpagination |
| Headman schedule | GET /api/academic/semesters → /api/academic/assignments, /api/schedule/items, /api/schedule/one-off-lessons | ВыбранныйsemId/groupId/dateperiod; реальныеquery adapter |
| Teacher home | GET /api/v1/teacher/semester → /assignments и /day | semesterId/dateFrom/dateTo/date, activeTeacher |
| Admin home | GET /api/academic/dashboard/stats | ADMIN, толькоактивныеroles/действующиеgroups |
| Notifications | POST /api/auth/ws-ticket; GET /api/notifications, /unread-count, /preferences | admittedBearer; SockJS /api/ws routes доступенлокально |
| TMA mount | GET /mini-app/ иassets, POST /api/auth/tma безinitData | UI200, malformedauthотказ; genuineTGauth принят ранее и безновогоpublic не повторять |

## Verification
Перед runtime: minimal parser/config/ownership/source review новойriskboundary; не новую массовую кампанию тестов. Native frontend Vue typechecks прошли в первомbuild (обаVite достигнуты); sandboxEPERMrealpath source mains — environmentfailure, logged. Разрешён один Vite-only escalated retry без измененияконфига/typechecks/install. Послеfreeze/configreview один grouped actualstand start/API/lifecycle/readback. Логи не содержатcredentials; oldacceptedprovider/restorechecksreuse.

## Do not
Не объявлять все147stories/всеPWA/TMA/Figma/production готовыми по этому стенду. Известные UI/manual/platform и deployment/offsite/RPO-RTO требования сохраняются отдельно. Не создавать дополнительные worktrees/agents радизанятости; не заполнять unknown процентом.

## Runtime amendment — незавершённый Initialize
Первый actual Initialize отказал на numericSID icacls: до credentials/TLS/Docker. Исправление *SID source-reviewed и интегрировано1b0662d0; только созданная этим запуском пустая папка удалена после проверки точного пути/0children/no-reparse. Следующий Initialize сохранил четыре TLS cert/key файла и отказал на PowerShell nested type ECCurve.NamedCurves. Docker effects0; private env/pins/rs0/VAPID ещё не созданы.

Root разрешил адресное исправление `[ECCurve+NamedCurves]::nistP256` и явный resume только этой принадлежащей задаче TLS-only стадии. Default existing-directory отказ сохраняется. Resume требует protected current-user-only ACL, точный allowlist двух каталогов/четырёх файлов, no-reparse, валидные matching certificate/key pairs, нужные authorities/SAN и срок. Существующие четыре файла сохраняются; создаются только ещё не созданные настройки/ключи. Любой иной или полный inventory — отказ. Перед actual resume — affected independent review. Это продолжение разрешённого первичного создания стенда, без rotation, удаления backup или повторной генерации TLS.

## Runtime amendment — Windows AppData bind boundary
Initialize resume и Check прошли. Start создал только новую инфраструктуру: PG2/Redis/Rabbit healthy; Mongo отказал сначала из-за CRLF, затем cp увидел каталог вместо файла. Main entrypoint материализован из точных уже принятых HEAD bytes: 240bytes/0CRLF/6LF, semantic diff пуст. Metadata-only probes подтвердили: обычный и user-only ACL файл в repo виден Docker как FILE, actual AppData private key и публичный mkcert certificate — DIRECTORY. Поэтому ACL не ослаблять; hostsharing требует другого транспорта.

Разрешён восьмой own named volume `local-private-files`. Семь точных существующих файлов (Mongo rs0key, Academic cert/key, Schedule cert/key, mkcert cert/key) передаются Docker CLI через собственный cached helper с network none, read-only root и RW только этой volume. Каждый service получает только нужный отдельный FILE-subpath read-only; клиенты публичного certificate не получают server key. Сначала фактически подтвердить file-subpath на nonsecret fixture. Несовпадающие existing files, foreign ownership или unsupported subpath — отказ/решение root. Исходные host файлы и identities сохраняются, contents/errors не выводятся; сравнение SHA в памяти только boolean. Helper после передачи остановлен и сохранён с ownership labels; normal services14/noBot, отдельно один stopped helper. Никаких удалений контейнеров/volumes/backup. Source delta вместе с исправлением local recovery URL `/app/password-reset` проходит affected independent review перед private injection/Start.

## Runtime amendment — initial directed credential encoding

Main9b5d0aab исправил6health commands: отсутствующийbash заменёнinstalledwget при тех жеports/endpoints/timings. Actual11serviceshealthy. Notification завершает старт с IllegalArgumentException: BOT_TO_NOTIFICATION_SERVICE_TOKEN должен быть canonical32byte base64url. Initial wrapper ошибочно создал directed tokens HEX64. Root открыл original services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/DirectedServiceCredential.java: canonical unpaddedbase64url43, decode32bytes, exact re-encode match; валидатор не ослаблять.

Author разрешена minimal scripts/local-stand.ps1 correction: canonical generator и early Checkguard только для BOT_TO_NOTIFICATION_SERVICE_TOKEN, ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN, SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN. После affected independent review/mainintegration разрешено исправить только encoding этих трёх exactowned initial HEX64 значений в собственном private stand.env: SAMEdecoded32bytes, noRNG/regeneration/productionrotation, exactpath/protectedcurrentuserACL/noReparse, остальныестроки/ключи/файлы неизменны. Private backup и atomicreplacement сохраняютrollback внеGit; никакихvalues/hashes в output. Затем targeted recreate только Academic/Schedule/Notification с сохранённымиvolumes/identities; остальныеhealthy безповторногозапуска, Gateway/Nginx стартуют поdependencies. ExistingNotification stopped для прекращения restartloop. FullStart/rebuild/provider/recovery не повторять без новой причины.

## Final acceptance ACK — 2026-10-04

main42cbc547: frozen local criteria C1-C4 accepted4/4. One startup API matrix + one Stop/Start + selected exact record readback, no provider/recovery/business rerun. Evidence: local-persistent-20261004/runtime-cycle-after.json and R3/local-api-20261004/runtime-final.json SHA32960A62D568D4CE87464E83702C351F5489406D63CA490CFD45EF703898AE5F. Actual PWA chooser/Back and postrestart sameHEADMAN reload accepted separately in UI-ACCEPTANCE.md. Stand left running, old65containers/backup preserved. Full147stories/frontends/Figma/realallroleTMA/production remain outside this ACK.
