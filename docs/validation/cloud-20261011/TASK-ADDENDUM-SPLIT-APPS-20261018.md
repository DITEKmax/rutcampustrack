# Активная объединённая цель: separate DB / AAA / Telegram / apps

18.10.2026. Нормализованная сохраняемая запись прямого GO владельца, переданного
из 01a12bea-f8eb-7c43-afc1-242d08755c13 в этот Cloud-чат. Исходное сообщение
владельца приоритетно. Это delta к TASK-CONTRACT-20261018.md, TASK17 и ASYNC,
не отмена незавершённого scope. После compaction читать этот файл и CURRENT.
Q031/D037: TASK-CONTRACT/CURRENT — persisted Goal ACTIVE; отдельного Goal API нет.
Нет дедлайна, false complete старой цели, увеличения account budget/лимитов.

## 1. Goal / роль

Root доставляет объединённый выбранный scope через назначенных developers,
интеграцию, достаточные проверки и независимый S3 review. Результат: рабочая
Cloud-среда, сохранённый WIP, устранённые причины красных CI/Coverage, отдельные
логические auth_db и academic_db, Auth только AAA, согласованные быстрые действия
Telegram, работающие изолированные GlitchTip/Pyroscope/Homepage с измеренной
памятью, обоснование PMM, совместимые HTTP/gRPC/clients и наблюдаемость.
Код и изолированная приёмка поручены Cloud; локальный главный только анализирует.
Продолжать до реального DONE либо доказанного WAITING_ENV/OWNER после исчерпания
независимой работы; не заканчивать из-за длины turn/смены этапа.

## 2. Lineage / starting checkpoint

Существующие workspace /workspace/rutcampustrack, codex/server-validation-20261011,
draft PR2 https://github.com/DITEKmax/rutcampustrack/pull/2; main не merged.
Starting HEAD 26cbe5912f6683a81780a831609dbddbc26feba4. Не reset/force/newbranch/PR.
Root CURRENT/attempts/resources/meta WIP и чужие25untracked сохраняются.

Историческая v1: **23/25,110/123=89.43%**, не процент новой цели. U15 принят на26c:
synthetic same-DB повтор Flyway сохраняет IDs/initial flags/grants/history/sessions;
review45b5989aa030a425e1d2621294b7a6f2828e6d341a3fc06c443772644f315ca9,
SAFE12123575983 SHA50821c16a990bc6bf974c3ef41d705d83099f985013af02cd51c893b4cd154a0.
Не VPS/containerrestart и не две новые DB. На опубликованном26c CSV ещё22/25,
PR body сохраняет23/25: синхронизировать без выдуманного повторного PASS.
TASK17 функционально9/9; начальные6/9 остаются в истории.

На26c Academic485 executed:476PASS/9FAIL/0skip; Coverage повторяет9FAIL,
diffcoverage skipped вследствие тестов. EventIT6 AccessDenied228, MapRead1
AdminAccess316, UserOwnershipLifecycle2 SQLGrammar/PSQL193 и rollbackassert266.
Оба browser stand Boot SUCCESS, затем POST /api/academic/users500 до login/UI/DOCX.
CI39091941238/Coverage39091941236 FAIL; Security39091941198/OpenAPI39091941265 SUCCESS.
Не объявлять все9 fixture-дефектами, UI-регрессией или coverage-порогом без evidence.
Кандидат500: Hibernate6.6.53/pgJDBC42.7.12 inferred generated keys → RETURNING*,
несовместимый с ограниченным Academic SELECT. Фактический SQLSTATE UNKNOWN.
Academic-only jdbc.use_get_generated_keys=false → explicit RETURNING id был
подготовлен, не записан/не проверен. Не выдавать Academic SELECT секретов.
Историческая причина syncWORD crash остаётся UNKNOWN.

Cloud восстановился: observed running/connected/current, exec работает. /tmp
оказался пуст: прежние4WT/2fixtureWIP/receipts/cache утрачены; не заявлять их
сохранёнными. Восстановить известные Event context-before-subject и один Map
migrateDeletion из точного source; formatter/compile старого WIP UNKNOWN/NOT_RUN.
Критичное опубликованное evidence получать адресно по IDs/SHA, без полного setup.
Root Git pack a0e5040f regular SHAefd2f1558a9cc94ff21ca4b99c0f714c9082bd362f75a949ac07c87cc0dd4705
и index сохранены; старые metadata worktrees не prune. Не удалять среду/caches/
objects/volumes/keys как догадку. Диск не заполнен: начально4294959104 bytes,
1258405 inodes; RAMmax17179869184/current546213888; CPU quota4/cpuset0–4;
TMP9466073088 bytes. Новые WTs в TMP, база26c, Root objects readonly alternate.

## 3. Обязательный старый остаток

U14/U25, usercreate500, реальные9FAIL и CI/Coverage обязательны к завершению без
участия владельца там, где оно не нужно. U14 получает сильнее target ниже:
не надо объявлять прежнюю sharedDB/commonTX архитектуру окончательным DONE.
Связанные три corrections допустимы для диагностики/сохранения критериев;
конечный create/login/credential/history/rollback/recovery доказать в новой схеме.
Нельзя скрыть FAIL удалением/skip/переименованием endpoint/ослаблением assertions.
При технической замене пути — явная связь прежнего acceptance с эквивалентом.
Все прежние выбранные A/M/U/328T границы сохраняются; это не328 новых аудитов.

## 4. Variant Б: окончательная граница

Выбор **утверждён**, повторно не спрашивать. Разные logical auth_db/academic_db,
разные runtime principals/migration rights; можно один PG cluster/process/host.
Runtime подключается только к своей DB. Нет shared users table/cross-service
SQL joins/commands/foreignDML/FDW/dblink как конечного решения; один authority факта.

Auth: identity/stableUID/login/BCrypt и initial/reset lifecycle, OTP/QR/nativeTMA,
Telegram binding, sessions/refresh/revoke, credential/role/session versions,
issuer/verification/security capabilities/live admission/permissionprofile и
security audit. Academic: profile/groups/assignments/membership/transfers/
headman/helpers/учебная история и eligibility. Auth хранит только необходимый
admission state, не независимое членство. Направление/revisions и порядок
grant/revoke/transfer должны исключать stale расширение прав. Конечные services
проверяют object/group/subject policy. BFF только композиция; Gateway routing/
admission/limits, не отчётная доменная система.

Генерацию credentials из Academic UserService перенести Auth. Сохранить BCrypt,
password_changed/reset/binding generations и разрешённую выдачу initial/adminreset
до смены пользователем; не обычный профиль/log/URL/trace/artifact. Не вводить
RememberMe/magiclink/IdP, не менять Web/PWA/TMA транспорт без причины.

Обязательные touched исходные точки: User entity/UserService/UserRoleGrantWriter/
AcademicAuthDataCommands/shared AuthDataCommands/V62/V63; Auth session adapter
joins users/grants/groups; lock_group_state/projection_session/lock_current_session/
read_export_selector/subject_proof_admitted; grant/transfer/headman versions/locks.
Заменить гарантии интерфейсом, не удалить guards. Published SQL не переписывать.

Auth **не хранит/не оркестрирует** exportjobs/artifacts/domainselectors. Перенести
ExportJobService/jobs/idempotency/leases/retries/bytea/expiry/cleanup/outbox/audit,
TeacherReportAuthorityJdbc и Gateway ExportJobWorker к доменным владельцам.
Existing renderers Academic AdminRegistry/AdminAttendance и Attendance teacher/
headman reports переиспользовать. Коротко закрепить owner/protocol до правок.
Auth ticket подтверждает live session/ограниченную capability; READY/состав/право
на artifact подтверждает доменный owner. Current-access checks при claim/complete/
download не вырезать. Step-up password/OTP/expiry/replay — Auth; preview/учебные
последствия/eligibility/delete — Academic. Сохранить purpose/actor/session/binding/
credential generations/digest/operation identity. Preferences остаются Notification,
dashboard/cache Academic; общий новый audit не открывать.

REST outside/gRPC inside, короткие синхронные вызовы/deadlines/error mapping/
batch по пользе. Не каждый CRUD в очередь; не Auth→Academic→Auth cycles и не RPC
под долгими DB locks. Auth не запрашивает весь учебный контекст на каждый admission.
RPC не distributedTX: минимальный durable operationId/idempotency/state/result +
recovery для create/role/transfer/archive и unknown outcome. Pending account не
получает working access/credentials до готовности двух сторон; lost response не
создаёт дубль. Safe retries/компенсация по предметному контракту, не universalSaga.
Сужение/отзыв безопасны при частичном отказе; missing/stale/outage не authorization.
Длинные jobs/notification/outbox/retry/DLQ сохраняются durable, не syncHTTP.

Нет прежних production data — не нужна кампания livelegacy переносов. Это не reset
synthetic DB/volumes/WIP и не rewrite published migrations/create-drop startup.
Новые DB/bootstrap/migrations как code; fresh/repeat сохраняют UID/credentials/
sessions/grants/history/jobstate. Stable cross-service IDs/ссылки и явная integrity,
не crossDBFK. Подготовить порядок переключения/rollback для будущего deploy;
после новых записей не предлагать старыйbackup с их потерей.

## 5. Telegram: точный сокращённый состав

Общие4 сверху один раз по доступности: «Данные для входа», «Сайт и PWA»,
«Получить код для входа» (OTP), «Настройки уведомлений».
STUDENT: существующие «ДЗ на сегодня/неделю». HEADMAN: единственное extra
«Учёт группы». TEACHER: единственное extra «Мои пары сегодня». ADMIN: NO extras.
Mixed roles — union действующих grants; teacher/admin-only без ДЗ.
Не добавлять Myjournal/stats/summary/users/groups/requests/studentSchedule/reports.
Это не удаление функций системы, только состав быстрых кнопок.

Учёт группы: в ЛИЧНОМ Telegram ответить текущей парой своей группы, attendance
таблицей и статистикой этой пары/context/asof. Не edit и не link-only. Existing
серверные формулы/статусы/EducationalTime Europe/Moscow; unmarked≠absent. No lesson
понятно; несколько допустимых — safechoice без чужой группы. Teacher today —
непосредственно своё расписание/empty state через existing dayAPI/directed minrights
adapter, не broad machineJWT и не ссылка вместо результата. RichTables только если
pinned SDK/BotAPI поддерживают; readable text fallback обязателен.
Fresh TG binding/grants/rolesVersion/isolation/revoke/oldcallbacks/dependency failure
защищены; replytext не authority, группы не в общийчат. /start/bind/credentials/OTP/
durablepreferences сохраняются. Synthetic normal/no/multiple/revoked/mixed/teacher/
admin/unknown-binding tests; реальный token/manual DEFERRED_LOCAL.

## 6. GlitchTip / Pyroscope / Homepage / PMM

Три приложения теперь **обязательная реализация + isolated proof**, U23 oldassessment
не integration. Дублирование Grafana/нет hotspot/navigation не основание UNSELECTED.
GlitchTip: Vue+Java errors, release/полезный stack/repeat grouping, scrub secrets/
tokens/PII, private access/retention, telemetryoutage не businessblock. Сверить current
версию: docs all-in-one+PG, Valkey optional; не старую heavyarchitecture.
Pyroscope: профиль настоящей выбранной Java operation + flamegraph, измеренные
CPU/RAM/disk/sampler overhead, retention/access; малый scoped сбор. Не требуется
сначала доказать деградацию. Homepage: рабочие полезныеlinks/managementauth/
minimal servercredentials; allowed_hosts не auth, никаких public соседних APIs,
никакого unrestrictedDockerSocket дажеro. Для links socket не нужен.
PMM **условен после обоснования**: конкретная дополнительная польза PG/Mongo vs
existingexporters/Grafana, resources/access/queryprivacy; не запускать ради4галочки.
Обоснованный невыбор PMM отдельно; существенный новый fork спросить.

Память обязательна: before/after baseline idle/operation/peak RAM/CPU/disk,
dependencyPG/Valkey и sampler Java. FutureVPS32GiB не Cloud capacity. Pinnedversions,
boundedconcurrency/ingest/buffers/retention/effective limits с headroom; не рекламные
минимумы/произвольный OOM-limit. Actual isolated Cloud/ordinaryCI measurements;
если runtime blocked — ENV_BLOCKED, не compose-onlyPASS. Production install/deploy/
реальныеcredentials не входят. Первичные docs: glitchtip.com/documentation/install/
и /error-tracking/, grafana.com/docs/pyroscope/latest/, gethomepage.dev/installation/;
решения по реально выбранной версии, не обновление стека по latest major.

## 7. Отчёты / contracts / гарантии

18weeks — исходный пример, не весь диапазон: weekStarts1..64 в учебном периоде и
прочие reports сохраняются. Domainjob move сохраняет correct2DOCX/navigation/lost202/
reconnect/idempotentrequestId/currentrights/nonblockingUI; короткий другой период
проверить адресно, не каждое число. D035: **global active2 / attempts3 / backoff5–10s /
lease90s / retention10min / user10 / global256**; не умножить по services. Rabbitbody
не большиефайлы, job не хранит bearer. ExistingDLQ/recovery/usefulmonitoring сохраняются.

HTTP/OpenAPI existinggeneration/publicinternalclassification/errors/externalprefix/
Java+TSgeneratedclients согласованы при route/auth/export/DB изменениях. Proto stable
wire numbers, Swagger не замена gRPC, не второй manualDTO source. Не переоткрывать
остальные auth/notification/Alloy/files/cache/enum/DI без изменённой зависимости;
изменённая цепочка после split/apps должна реально работать.

## 8. Организация / verification / checkpoints

Repo RULES + принятые Cloud exceptions. Новые developers/search/review Sol6.1/high/
forknone, actualmetadataUNKNOWN допустим если не выдаётся; без молчаливой подмены.
Root soleGit/taskdocs/matrix; one writer sharedcontracts/configs/generated/lockfiles;
отдельные knownbaseWT/ports/DB/queues/build outputs; reviewer не исправляет findings.
Никакого второго CLI/controlplane. Полезная параллельность по slots/реальной памяти,
не числоагентов=heavyconcurrency. Сначала закончить coherent corrections; API owner
один до fanout. Все вопросы/отчёты сюда; другие чаты не читать/не писать без поручения.

КТ0: platformstatus/exec, narrow bytes/inodes/RAM/processes/ownsizes, HEAD/index/WIP/WT;
нет guessed diskfull/repair/reset. При transient автоматически продолжить; при внешнем
blocker точный checkpoint/нужное действие, не endless same retries.
КТ1: сохранённые23/25 + versioned newmatrix/count/weights/owners/redcases.
КТ2: schema/ownership/transport/provision/grantrevoke/jobs/failure+rollback/touched
consumers — короткий frozencontract + независимый S3review, затем implementation.
КТ3: точные bot handlers и3apps, protection/resourceproof; можно параллельно КТ2.
КТ4: changedchain correctDOCX/recovery/rights/notifications/monitoring/API/gRPC/generated,
twoDBpersistence/minrights, actualapps. Не fullstand наWT/не fullsuiteкаждыйdiff.
КТ5: CI/Coverage реально SUCCESS вместе Security/OpenAPI/monitoring/othermandatorygates
на finalsource; source/executable/PRhead различимы. Q009 finaldocs-only sourceequivalence
разрешена с обоимиSHA; product/workflow не docs-only. DraftPR/currentmatrix/handoff.

Матрицаv1 сохраняет реальные23/25/110из123 исторически. V2 stable IDs/lineage; U14
parent expanded без двойного зачёта children, U20 oldprotections vsnewhandlers,
U23 oldassessment vs3integrations+PMM, U15 oldsameDB vsnewtwoDB, U25 currentredgates.
Fields ID/parents/observableoutcome/owner/deps/acceptance/risk/weight/status/revisions/
evidence/blocker/next. Freezeweights/inventory до реализации; versioneddelta дляscope.
DONE_CHANGED/ALREADY_SATISFIED только sufficientactualproof; source/runtimepending/
ENV_BLOCKED/OWNER_MANUAL_PENDING/DEFERRED_LOCAL не completed. Deferred вне denominator
с причиной; обязательный envblocked остаётся. Общий и checkpoint count/weighted%,
не production-readiness/tests/coverage/files. При потере применимости PASS recheck
затронутое, старое evidence не стирать. После каждойКТ/значимогоresult/blocker коротко
чтоработает/revision/evidence/boundaries/count-weight/остаток/next+CI/Coverage; безpollnoise.
Не ждать финала, не выдавать source/config/containerUP за end-to-endPASS.

## 9. Do not / исключения / DONE

No mainpush/merge/deploy/productionmigration/grants/realaccounts/secrets/rotation/
firewall/SSH/VPS hardening/purchase/provider/country/tunnel/topology/offsitebackup/
S3/realrestore/multiworker/replica/capacitycampaign/K8s/K3s/KEDA/Headlamp/ArgoCD/
Unleash/canary/landing/Umami/Angularretirement/newweb/newdesign/Docmost/docsrefactor.
Не удалить Angular/данные/backup/чужойWIP. Ownerstand/realTelegram/push/install/Safari
OWNER_MANUAL_PENDING/DEFERRED_LOCAL, не PASS и не blocker независимого Cloudscope.
Repository/sourceconfigs/newmigrations/compose/envexamples разрешены, не actualdeploy.
Минимальные task/API/reference docs разрешены, не переорганизация architecture docs.
Syntheticdataonly, no secrets/PII in code/logs/artifacts, no realcredential acquisition.
**B013/B014/B015 PLATFORM_REJECTED/FROZEN**: никаких эквивалентов caller/mutation/
evidence через code/CI/agent/model/launcher/формулировку. Новая цель не размораживает.
No falsegreen/assertion/timeouts/oracle/gate weakening/wiringcoveragecampaign.
Standingnormalpush currentbranch/draftPR/ordinaryCI действует; PRattach еслиtoolдоступен,
прошлыйfailedattach не потеря PR. Goal complete только при всех mandatory actual
criteria/review/currentgates; старыйостаток/существенныйnewscope не исчезает изотчёта.
