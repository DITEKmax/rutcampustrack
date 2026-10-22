# RutCampusTrack — объединённая цель и выбранный scope, 18.10.2026

Источник: прямое поручение владельца, переданное в текущий чат из
01a12bea-f8eb-7c43-afc1-242d08755c13. GO: «окей, передавай облачному чату новую цель и роль».
Это нормализованная сохраняемая запись принятого контракта и всего отбора54IDEA,
не byte-for-byte копия сообщения. При расхождении исходное сообщение владельца приоритетно.

## 1. Goal и роль

Роль: ответственный за доведение выбранных сценариев и адресный рефакторинг
RutCampusTrack с сохранением контрактов, данных и прав; Root остаётся интегратором,
продуктовые изменения — назначенным sole writers. Не перепроектировать весь продукт
по учебному шаблону. Цель без дедлайна: совместимое проверенное приложение в текущем
cloud checkout и разрешённых synthetic/CI ресурсах: HTTP-контракты, доменные типы/DI,
владение/сохранность данных, transaction/cache/file boundaries, фон/уведомления,
ролевые PWA/TMA/Telegram действия и полный связный monitoring существующего стека.
Готовое переиспользовать, отсутствие старого runtime не считать дефектом.

Совместить с TASK-CONTRACT-20261017.md и TASK-ADDENDUM-ASYNC-20261017.md,
сохраняя прежние M-ID/критерии. На приёме расширения HEAD e66b1711b3d93faee625b50a2e2f785020a0f43a,
A01–A04/M01/M05 DONE_CHANGED: 6/9, ALREADY_SATISFIED0. M05 фактически прошёл
полную группу и независимое review; не начинать заново без изменённого риска.
M02–M04 DOCX/CI/job metrics/panels продолжаются, WIP не терять.
Приём сообщения и изменение persisted Goal — разные факты. Сначала читать
сохранённую цель/состояние. Если платформа не позволяет заменить активный Goal,
сообщить это; сначала честно довести пересекающийся прежний остаток, затем
по его реальному завершению закрыть прежнюю и создать расширенную цель.
Это продолжение/создание заранее разрешено; false complete/обход запрещены.
Не вводить дедлайн и не увеличивать лимиты/бюджет аккаунта.

## 2. Context, checkpoint и матрица

Продолжать существующие workspace, codex/server-validation-20261011 и draftPR2;
main645f8a36 другого локального чата не является текущей базой.
Зафиксировать branch/HEAD/dirty, фактический Goal/критерии, текущий WIP и CI.
Новые статические кандидаты сверять с актуальным кодом/evidence, не переделывать
уже исправленное. Сохранить настоящее M04 DLQ/replay/samearchive/dedup/cleanup;
причину прежнего500 не выдумывать.

Одна объединённая матрица результатов с ID, checkpoint/пользовательским исходом,
исходными M/IDEA, конкретным acceptance, owner/agent, зависимостями, весом,
статусом, revision, достаточным evidence, blocker/question и nextstep.
Не дублировать один исход под разными IDs. Статусы:
TODO, IN_PROGRESS, REVIEW, DONE_CHANGED, ALREADY_SATISFIED,
BLOCKED_ENV, BLOCKED_QUESTION, DEFERRED_OWNER.
Закрыты лишь DONE_CHANGED/ALREADY_SATISFIED с применимым доказательством.
Веса1/2/3/5/8 по объёму/риску до реализации новых строк; после короткихКТ0/КТ1
зафиксировать состав/acceptance/веса. Не менять ради процента и не дробить закрытое.
Счётчик закрыто/всего; взвешенный%=100*вес подтверждённых закрытых/
вес обязательных выбранных. Это выполнение принятого плана, не production-readiness.
Обязательный blocker остаётся в знаменателе; DEFERRED_OWNER и невыбранные
условные варианты отдельно вне знаменателя. Изменение scope — новая версия,
изменение знаменателя и причина. Исторические6/9 отдельно, не механические6/new.

Полный исходный архив328карточек/Windows md/skills/CURRENT не нужны.
При конкретном source gap спросить точное имя/фрагмент, зачем/какая строка зависит;
не запрашивать весь архив автоматически. Новые вопросы: неизвестное→2–3варианта→
рекомендация→зависимая строка. Ответ может быть позже; независимое продолжать,
молчание не согласие. Все вопросы/отчёты в этом главном Cloud-чате.

## 3. Организация и авторизация

GO на разрешённый кодовый scope уже дан; второго общего «начинай» не ждать.
Применимые RULES/инструкции checkout сохраняются; модельные/аккаунтные настройки
не менять. Implementation/search/review: gpt-6.1-sol/high, fresh compact packet,
fork_turns=none, проверить доступную actualmetadata; UNKNOWN не выдавать за измеренное,
молчаливого fallback/обхода неподдерживаемой пары нет. Прежнее Cloud-исключение actualUNKNOWN
не отменено. Нативных агентов столько, сколько ускоряет независимые направления
в реальных slots/RAM/CPU, не искусственно один и не заполнение ради числа.
Root/Git/docs, solewriter общих API/configs/generated/lockfiles; независимый reviewer
не исправляет своё review. Writers в независимых worktrees с известным baseline/
раздельными runtime resources; build/integration координировать.
Второй CLI/control plane/daemon/orchestrator не создавать.
Standing authorizations своей ветки сохраняются; новое разрешение на main/merge/deploy
этим сообщением не добавлено. Не обходить approval/FROZEN, не стирать WIP.

Перед правкой — принятый RCT сценарий и документация фактически используемой версии;
лекция/latestдругогоmajor не основание менять стек.

## 4. Выбранные необходимые результаты

### A. HTTP/типы/DI — IDEA035/046/049/050/051/054

Swagger/OpenAPI старого/нового HTTP: inventory public/service/internal endpoints и
обоснованные исключения; paths/methods, внешнийGatewayprefix, parameters/JSON/multipart,
DTO/nullability/enums/validation/status/errors/pagination/HATEOAS/security schemes.
SwaggerUI и rawspec имеют отдельный доступ, examples без секретов.
Один существующий contract source/generation/CI; связь spec→Java/TS/generatedclients→
реальные PWA/TMA вызовы. Генерировать только изменённое, generatedtypes вручную не править;
OpenAPI gate не доказывает весь endpoint inventory. gRPC остаётся proto, события schema/
version/producer/consumer; Swagger их не заменяет.

ConstructorDI AssignmentClosureService и согласованный PasswordEncoder — адресные кандидаты.
Сохранить BCrypt/hash/lifecycle. Закрытые String состояния типизировать там, где ещё
актуально (Assignment.lifecycleState ACTIVE/PREPARED/CLOSED); DB прежние читаемые строки/
регистр/смысл, без ordinal/numericSQL/proto renumber. String→enum сам не требует миграции;
unknown/null обязательного не даёт разрешение.

Records/sealed уже есть. Адресно DTO/value/result: validation/nullableкомбинации/
mutablecollections-arrays/serialization/diagnostics. LoginRequest.toString.password —
кандидат, не доказанная утечка; TokenResponse redacted сохранить. Record не deepimmutable;
sealed лишь реально закрытый набор. Не переписывать JPA/proxy/generated массово.
DTO/явные mappings/JSON-multipart/validation/errorhandlers/status сохраняются;
исправлять конкретные mapping/message/input-loss. ДЗ completion/types/assignments
batch текущей страницы при оставшемся реальном fanout, не объявлять latency измеренной.
Pagination только при реальном разрыве; scalarIDs допустимы, не universalcascade.
ArchUnit/frontend import checks дополнять лишь под конкретный cycle/wrong dependency/
feature bypass, не новый framework/учебный стек.

### B. Данные/transaction/cache/files — IDEA038/042/047/052/053

Auth authoritative owner identity/credentials/sessions/rolegrants; Academic — учебного
профиля/академических фактов, сохраняются права/stableUserID. Проверить актуальный остаток
sharedDB/schema/principal. Один владелец факта, явные API/events, минимальные DBgrants;
физический сервер каждойслужбе не обязателен. Совместимые code/NEW migrations,
synthetic fresh/upgrade/datapreservation. ПрименённыеSQL/V1 не переписывать/не переносить,
чужойFlyway не выключать, productionmigration запрещена.

Обычный startup/restart/recreate сохраняет persistentDB: проверить profiles/overrides/
ddl-auto/Flyway/seeds/resethelpers/addresses/volumes; никакого auto create/create-drop/
clean/drop/truncate/reseed на persistentlocal/prod. update не замена migrationhistory.
ОдноразовыйDB лишь явноизолированный testlifecycle.

Requestscope/revision/CAS/locks/constraints/idempotency/history сохраняются;
не synchronized/@Version повсюду. На выбранной записи назвать commit/proxy/propagation/
rollback/flush/REQUIRES_NEW, не подавлять существенную ошибку. History/delivery/meter
разныефакты. LocalTX не откатывает соседнююDB/Redis/RPC; existingdurable outbox/recovery/
допустимая компенсация, не universalSaga.

Redis/TTL ужеесть: полезное дорогоеповторноечтение имеет scope/key/version/TTL/freshness/
size/afterCommit invalidation-update/coldmiss/concurrentfill/staleresponse/retry/fallback
кauthority. РеальноеGroupServiceокно неправильногокэша→existingafterCommit, не глобальный
transactionaware. Durablepreferences сохраняются, PGsessionauthority.
RAM: maxmemory/eviction/keysize-count/headroom/containerbudget и hit/miss/evictions;
TTL/logicalDB не изолируют глобальнуюeviction: disposablecache не вытесняет критичные
tickets/limits. Cache не обходитliveadmission/objectauthz. Логи вLoki; queryaggregatecache
лишь при пользе, не весь поток вобщийRedis. Нет автоматическойRAMpressureкампании/AOF/replica.

Файлы: owner/size/type/create/issue/linkexpiry/revoke/replace/delete/orphans/
metadatarestore; streamingmaxBytes/release/413 и ratelimit существующегопути сохраняются,
это не доказательство всехbody/headerpaths. Контент+права вместе; PDFcounter не файл.
Операционно реальныефайлы/retention не удалять/не менять.

### C. Доступ/session/config — IDEA008/009/013/017/036/037, разрешённые части

AccessJWT→livePGadmission SID/revoke/expiry/versions/roleavailability→signedinternalJWT→
downstreamobjectrights; signature не право начужуюгруппу. InternalJWT≤300s/≤accessexpiry
не дефектсампосебе; TTL безпричины не менять, отмену ужеидущейзаписи не обещать.
Сохранить cookies/session/OTP/logout/revoke/nativeTMA/accessinmemory/personalcache/
TMAJSONrefresh/issue-clear flags/401-403-503/same-origin/CORS/CSRF. Сам disableCSRF
не доказывает уязвимость. Новые servletsession/RememberMe/magiclink/IdP не выбраны.
Начальный/админсброшенныйпароль виден разрешённомуадмину дочеловеческойсмены; это не
обычныйпрофиль/чужиеправа/изменённыйпароль/URL/log/trace/baggage. Тесты syntheticonly.
Исправлять конкретныйlate response/cleanup/revoke/user-rolechange/cache/callback,
не повторять полныйauthaudit. Abort не rollback. FORUPDATE/conditionalJTIrotation/
scopedinternal/socketticket/admission/revoke/consumercontracts сохранить.
Стараяэкспозиция/методы/audience UNKNOWN не публичность. JwtServiceRS256profile сверить
актуальныйбеззаявленияexploit. Trustedforwarding/fixedupstream/canonicalIP сохранить;
roleprefix не allowlistnewhandler.
Loginlimiter/BCryptguard ужеесть; safeRedis miss/eviction/failure policy tickets/limits.
Multi-instance сравнениеотложено. Limiter неbreaker; skew/deadlines/startup/isolation
по реальномупути/проблеме.
Раздельныйserviceaccess безненужнойдвойнойавторизации, safeenvexamples иoperator/machine
Grafana/Prometheus/Alertmanager/actuator/rawstorage/receiver/exporter APIs. Grafanalogin
не защищаетсоседниеAPI; skipfilter не публичность. Scrape безuserJWT, не весьactuator.
Реальныеaccounts/secretvalues/rotation/VPS не трогать. Redaction учитываетrawunitIT
XML/HTML/Playwrightreports/dockerlogsupload: safesummary ихнеочищает. Настоящие
credentials/artifacts специальнонеполучать.

### D. UI/фон/уведомления — IDEA020/021/039/043/048

Сопоставить stats/marks/reports/bulkactions→nonblockingread/optimisticUI/durablejob.
Не все быстрыезапросы вRabbit. Optimisticmark различаетpending/serverconfirmed/error/
unknown, не блокируетвсё/не повторяетмутациювслепую.
18week report — текущийasync код, не дублироватьM04; прежнийJavaDOCX былsyncHTTP,
не обвинятьRabbit безevidence. Status/другойUI/correctfile/rights/boundedconcurrency/
timeouts/memory/normalrequest/transientfailure и завершениеИСХОДНОЙзадачи безloss/duplicates.
УвеличениеHTTPtimeout не job и не снижаетcost.
Actualisolatedbroker ack/nack/confirms/returns/dedup/retry/DLQ; boundedbackoff/redrive,
poisonisolated+reasonalert, неhotrequeue/quietdiscard. Rejected-beforeaccept publish
не обязанвDLQ: publisher/outbox/job correctoutcome. ПринятыйM04переиспользовать,
нескольковоркеров/реплик не входит.

Catalog/history/fanout/serverpreferences уже переработаны: конкретныйостаток
recovery/reconnect/audience/duplicate/channel/TTL/postcommitsignal, не переписывать;
multi-replicabootstrap отложен.
Telegrammenu поролям: общиедействиясверху бездублей, ролевыеобъединены;
teacherбезstudentrole не видитДЗ. «ДЗнасегодня/неделю» подходящимролям;
«Данныедлявхода», «СайтиPWA», «OTP», «Настройкиуведомлений» по полномочиям/
существующемубезопасномусценарию. Schedule/requests/reportstatus/openTMA/RichMessages
лишь при пользе+API; не выдумыватьproduct. РеальныйTelegram/owneracceptance позже,
mock/syntheticproof допустим.
BackendHATEOAS в подходящихPWA/TMA путях сactualexternalprefix/прежнимиправами;
_links вgeneratedtypes не доказательствопотребления, не HALвезде.
Vueescaping/safeURL/loading-empty-error-retry/input/doubleclick/keyboard/reducedmotion
на затронутомэкране сохранить, безновогодизайна/лендинга/webrewrite.

### E. Monitoring — IDEA001–006; IDEA007/025/026/030 условно

Обязательный productionуровеньGrafana какcode+изолированнаяпроверка, не новаяустановка
радигалочки/не реальнаяproductionприёмка. Usefulfunction/resources/latencyerrors/
DB/Redis/Rabbit/outbox/job/retention/disk/inodes/headroom/security/freshness.
Gatewayproductionexposure/scrape/RED-SLI/renderer актуальносверить; host/container/JVM
иHikari/serverDB различать. Oldcandidate diskmin/processCPUhostlabel/Remainingusedformula
исправлятьтолькоостаток; selectors/units/instance/nonpositive limits/UID/DNS/Gitprovisioning.
Counters/timers/outboxgauges сохранять; published beforecommit означаетпопытку/confirm/
committedпоactualcode, не доказаннаяпотеря. ID/URL/unboundedmessage неmetriclabels;
business/job/requestIDs вsafe logs/traces.
Inventoryjobs/instances/missingtargets, SLI-SLO/windows/histogramunits-buckets,
rate/ratio/notraffic/missing/queryerror. Числа имеютоснование (принятыйD033 сохраняется).
ПолезныеauthHTTPJVMDBcachequeue/inflightcleanup/duration/backlogageDLQ/lastsuccess,
не всевозможныеметрики/увеличениеpools.
Correlation: earlyrequestID/extract-inject-propagate-cleanup/distincttrace-span-request-
business-message/asynccausality безвечногоHTTPtrace. Actualexecutor/BFFeffectiveSERVICE_NAME/
tracingdeps проверять; отсутствиеdecorator/однойdependency неruntimeproof.
JSONfields/LogQL/redactionformatted/structured/MDC/args/stack; completion/outcome
duration/status/category бездублейэтапов.
**Перейти Promtail→выбранныйAlloy**, одинподдерживаемыйingestionpath, persistentcheckpoint/
recreate/изолированнаяпроверка. Старыйpositions/tmpкандидат, не доказаннаяпотеря.
Logs↔traces обе стороны; actualtraceId/requestIDsearch/sampling/queues/attributes/logsize/
series/storagebudgets, SLI не изsampledtraces.
Baggage/Collector/exemplars/servicegraph условнопопользе, directTempo допустим.
Selfdiagnostics ingest/drop/queues/storage/evaluation/freshness/notificationerrors;
telemetryoutage не останавливаетбизнесзапись. Health/readiness/liveness различать,
проверятьизсетипотребителя+usefulfunction.
Alerts producer/labels/impact-owner-action-runbook, boundedfiring→testreceiver→resolved/
groupingdedupinhibitionquiethours; oldhealth_statusUNKNOWN не доказаннаяполомка.
Samehostprobe не доказываетVPShostloss; внешнийprobe/channel какcontract,
реальныйexternalrun/ownerbot DEFERRED.
PMM/GlitchTip/Pyroscope/Homepage: оценитьполезныйминимальныйintegrationscope,
ресурсы/перекрытия/доступ. Не забыть/не ставить4productionсервисаавтоматически.
Реализуемоеизолированно подтвердить; actualserverinstallation/credentialsотложены;
нерешённыйсущественныйвыбор остаётсяостатком, неDONE.

### F. Достаточные checks — IDEA024/032/040, контекст033/034/045

Actualrevision/result/exclusions/evidence, separateunit/IT; reuse sufficientproof,
risk-basedchecks, неfullsuiteпослекаждойправки/wiringcoverage.
SyntheticDB/queues/volumes/identity, неpersistentownerstand. Fresh/upgrade примиграции,
не из-заmonitoringdiff. Meters/rules delta/labels/type/units икороткиеfixtures
notraffic/missing/recovery; YAMLlint неарифметика, registryunit неpipeline.
Старыеk6актуализироватьподcurrentAPI/workload/assertions/cleanup; лишьboundedcloudsmoke
навыделенномtargetпринеобходимости. Не300users/VPS/capacityPASS.
Использоватьтолькоразрешённыеобезличенныеданные, noreallogsзначитчестныйостаток,
не добываниеuserdata. Существенныеdeny/revoke/stale/duplicate/uncertain/failure;
browser актуальныеVue/PWA/TMA, Angular не новыйweb.
Meaningfulassertions/wirecodes сохранять. B013FROZEN/platformrejected ни черезCI/
код/модель/эквивалентнуюmutation. B014/B015 ограничениятакжесохранены.
Нетучебныхfaultendpoints/массовойmutationcoverageкампании.

## 5. Явно отложено / do not

Реальныйdeploy/release; VPSpurchase/selection/RU-EUtunnel/topology; SSH/firewall/sudo/
hostupdates/Certbot/livedomains; Portainerserver; offsiterealbackuprestore/provider/RPO-RTO;
локальныеDBclients→realserver; multiworker/replica/scaleexperiments; K8s/K3s/Headlamp/
ArgoCD/KEDA; Unleash/canary; landing/Umami; новаяuserinstruction/Docmost/screenshots;
projectdocsrefactor/перенос/актуализация включаяarchitecture.md; Angularretirement.
Ownerpersistentstand не менять; ownerreal/manual/visualacceptance отложена,
не обязательныйgate независимогоCloudscope и неPASS.
Swagger/OpenAPIartifacts +минимальныйstatus/evidence изменённойработы — разрешённые
исключенияизdocsfreeze, не поводпересобиратьdocs.
Wazuhотменён, multiplecampusesотложены. Java/Spring/VuePCSS/RESTJSON/gRPC/events/ownAuth
сохранить, безThymeleaf/GraphQL/Keycloakмиграции.
Нореalsecrets/source-log/prompt; no productionmigration/destructivereset/volumedelete/
secretrotation/mainpush/defaultmerge. Старое standingbranchpush/draftPR разрешение
действует; новыхпубликационных/deployполномочий нет.

## 6. Контрольные точки / acceptance

КТ0: текущийGoal/revision/WIP/CI сохранён, готовоеотделено, стараяцельнеfalsecomplete.
КТ1: scopeсопоставлентекущейветке, конечныеостатки/dependencies/checks,
матрица/weightsзафиксированы; не новыйширокийаудит.
КТ2: contracts/DTO/enums/DI/records иstartupdatapreservation выбранныхпутей подтверждены.
КТ3: authAcademicownership/transaction/cache/file syntheticmigrationcompatibility.
КТ4: actualreport/background/notificationrecovery/role-rightsPWA/TMA/bot,
reuseM04, безmultiworker/realTelegram.
КТ5: function→signals→truthfulpanels→logs↔traces→testalert/resolved;
conditionaltoolsdisposition.
КТ6: finalindependentreview/securitycompatibility/sufficientCI/exactremaining.
Порядок менятьпополезнойнезависимости, однамиграциянебарьернесвязанныхпакетов.
Не начинатьвесьscopeсoverlappingwriters.

ПослекаждойКТ/связанногопакета/существенногоблокера:
matrixversion/revision→closed/total+weighted%→принято/evidence→activeagents/results→
blockers/questions→nextstep. Не narrateодинаковыеCIpolls. Содержательныеобновления
приlongwork; нет«заночь»/обязательногоownerstand.
Первыйответ: роль/цельилиточныйstatusприменения, фактическийGoal/HEAD/прежнийостаток,
initialmatrix/percentmethod/usefulagents/questions. Принятие ≠ persistedGoalupdate.
Всеизменениявыбранногоscope implemented+verified наactualrevision, contracts/data/rights
сохранены, independentreview closed, NOTRUN/DEFERRED явны. Одинgreengate/YAML/container/
нетошибкилогов не весьрезультат. Sufficientexistingмеханизм «сохранён, переделканенужна».
Неcompleteвременем/бюджетом/планом/формальнымистроками; blockerспрямымevidence/
условием, независимоепродолжать, nosamefailretryбезновыхданных.
Goal/paused/blocked/budget менятьтолькоштатнымtoolcontract/полномочиями, необходить.

## 7. Полный отбор54IDEA

| IDEA | Название | Отбор | Точный объём / отложенная часть |
|---|---|---|---|
| IDEA-001 | Полезные панели Grafana | Включить | Панели/формулы вrepo+isolatedcloud; ownerstandacceptanceотложена. |
| IDEA-002 | Метрики Prometheus | Включить | Metrics/scrape/rules/SLI/safeaccess; noproductionoperations. |
| IDEA-003 | Логи Loki | Включить | Structuredsafe logs/ingestion/checkpoint/retentioncode+isolatedproof. |
| IDEA-004 | Трассировки Tempo | Включить | HTTP/gRPC/backgroundcorrelation/logs↔traces/resources/sampling. |
| IDEA-005 | Алёрты и будущий бот | Включить | Rules/receiver/firingresolved; actualtokens/recipients/ownerbotlater. |
| IDEA-006 | Переход на Grafana Alloy | Включить | One supportedingestion/repochange+isolatedproof; nodeploy. |
| IDEA-007 | PMM и диагностика БД | Условно | UsefulminimalPMM scope/capacityassessment; serverrolloutнеразрешён. |
| IDEA-008 | Лёгкая защита от перебора и контроль безопасности | Частично | App login/securityevents; noSSH/fail2ban/firewall/VPS/Wazuh. |
| IDEA-009 | Rate limit и Haltdos Community WAF | Частично | ExistingGateway/loginlimits/failurepolicy; WAFappliance/serverlater. |
| IDEA-010 | Управляемые обновления | Отложить | ОС/serverupdates относятсякdeploy. |
| IDEA-011 | Сертификаты Certbot | Отложить | Realdomains/VPSissuance-renewal. |
| IDEA-012 | Portainer | Отложить | Futureoperationalserverinstallation. |
| IDEA-013 | Входы в служебные сервисы и env | Частично | Reporoutes/accesscontracts/envexample; realaccounts/passwords/VPS/deployexcluded. |
| IDEA-014 | Будущий лендинг на Vue | Отложить | Landing/address/animation. |
| IDEA-015 | Доступ из России без VPN и связь двух серверов | Отложить | Purchase/serverchoice/RU-EUtopology/tunnel. |
| IDEA-016 | Внешние резервные копии | Отложить | Offsite/provider/RPO-RTO/realbackuprestore; retainexistingguards. |
| IDEA-017 | Секреты в GitHub Actions и доставка настроек | Частично | Nosecretsincode/log/artifacts; runtimeSecrets/deploydelivery/rotation/accountlater. |
| IDEA-018 | Подготовка и защита VPS | Отложить | SSH/firewall/sudo/realVPS. |
| IDEA-019 | Безопасные контейнеры и воспроизводимый запуск | Частично | Health/readiness/liveness/datapreservationcode; realbootstrap/reboot/deliverylater. |
| IDEA-020 | Кнопки Telegram и назначение бота | Включить | Rolemenu/rights/adaptersmocksyntheticproof; realbot/ownerlater. |
| IDEA-021 | Асинхронные операции и быстрый интерфейс | Включить | Responsive/durablejobs/currentreport; nomultiworker. |
| IDEA-022 | Масштабирование микросервисов и обработчиков | Отложить | Multiinstance/worker/comparisonruns. |
| IDEA-023 | Kubernetes и условные Headlamp и Argo CD | Отложить | K8s/K3s/Headlamp/ArgoCD/KEDA. |
| IDEA-024 | Актуальные сценарии k6 и измерения | Частично | Updatek6/ifneededboundedcloudsmoke; noVPS/300usercapacityproof. |
| IDEA-025 | Ошибки приложения в GlitchTip | Условно | Justifiedintegration/isolatedscenario, notmandatorynewproductionservice. |
| IDEA-026 | Профилирование и визуализация Pyroscope | Условно | Usefulprofile/budgetinavailableisolation; noVPSdeploy. |
| IDEA-027 | Условная оценка Unleash без канареечного развёртывания | Отложить | Unleash/flags/canarynotchosen. |
| IDEA-028 | Аналитика лендинга Umami | Отложить | Dependsdeferredlanding. |
| IDEA-029 | Пользовательская инструкция с реальными скриншотами | Отложить | Roleguide/screenshots/Docmosttooearly. |
| IDEA-030 | Homepage и безопасность панели владельца | Условно | Saferepopreparation/isolatedproofonly; VPSinstallationlater. |
| IDEA-031 | Локальное администрирование баз данных | Отложить | RealDBclientconnections/serveradministration. |
| IDEA-032 | Тесты по реальным пользовательским сценариям | Частично | Currentsyntheticregressions; don'tautomaticallyacquirerealuserlogs/data. |
| IDEA-033 | Адресный аудит практик vibe и оценка внешних skills | Есть / контекст | Existingaddressedrefactorprocess; nonewskills/install/instructionrewrite. |
| IDEA-034 | Результат первого выпуска | Есть / контекст | Outcome/boundaries/evidencepercheckpoint; nonewtemplate. |
| IDEA-035 | Автоматические архитектурные границы | Включить | ConcretegapsviaexistingArchUnit/frontendchecks. |
| IDEA-036 | Отзыв доступа и устаревшие ответы | Включить | Session/role/objectauthz/lateresponses; reusevalid/nofullauthaudit. |
| IDEA-037 | Конфигурация, proxy и лимиты под реплики | Частично | Trustedproxy/singleRediscounters/failurepolicy; VPS/multiinstancelater. |
| IDEA-038 | Жизненный цикл файлов | Включить | Files/results/lifecycle/rights/syntheticdata; no destructive realfiles/DB. |
| IDEA-039 | Состояния и доступность интерфейса | Включить | CurrentPWA/TMAstates/a11y; nolanding/newdesign/ownermanualgate. |
| IDEA-040 | Изолированные данные и воспроизводимый выпуск | Частично | Isolateddata/migrationscompatibility/CIrevision; norealrelease/deploy. |
| IDEA-041 | Навигация и актуализация Markdown | Отложить | Projectdocsrefactor/move/update includingarchitecture.md. |
| IDEA-042 | Разделить владение данными Auth и Academic | Включить | Code/newmigrations/syntheticupgrade; noproductionmigration. |
| IDEA-043 | Надёжность уведомлений после существующего рефакторинга | Включить | History/preferences/fanout/retryDLQ/singleconfiguration; multireplicalater. |
| IDEA-044 | Вывести старый Angular web | Отложить | KeepAngular; retirewithfuturewebiteration. |
| IDEA-045 | Связать выбранные облачные требования с общим планом | Есть / контекст | 328IDs alreadytracked, not328newcampaigns. |
| IDEA-046 | Enum в приложении, текстовые коды в БД | Включить | Enums/explicitmappings/textDB/frozenprotonumbers. |
| IDEA-047 | Транзакции и восстановление операции | Включить | Localcommit/history/cache/outbox/domainrecovery; nouniversalSaga/SQLundo. |
| IDEA-048 | HATEOAS в подходящих PWA/TMA сценариях | Включить | Actualexternalprefix/rights/suitablelinkconsumption. |
| IDEA-049 | DTO, API, безопасные представления и стоимость чтений | Включить | DTO/errors/LoginRequestredaction/justifiedcurrentpagebatchreads. |
| IDEA-050 | Явные зависимости и владение lifecycle | Включить | ConstructorDI AssignmentClosureService/PasswordEncoder/retainBCrypt. |
| IDEA-051 | Адресно пересмотреть records и sealed interfaces | Включить | Benefit/immutableboundaries/safetoString/APIcompatibility; nomassrewrite. |
| IDEA-052 | Обычный запуск сохраняет данные; схема меняется миграциями | Включить | PersistentDBnostartupreset/migrations/isolatedproofonly. |
| IDEA-053 | Кэши данных и агрегатов с обновлением и бюджетом RAM | Включить | Addressedcache/invalidation/RAM/ticket-limitisolation; Lokiquerycacheifuseful. |
| IDEA-054 | Swagger/OpenAPI как действующий контракт HTTP API | Включить | Old/newHTTPcontractactuallyconsumedbyclients. |
