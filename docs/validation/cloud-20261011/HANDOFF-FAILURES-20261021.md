# PR2 — оставшиеся отказы: fixture phases и lifecycle, 2026-10-21

**SOURCE_REVIEWED / NEW_HOSTED_RESULTS_PENDING / NOT_READY.** Только исправления и normal push в существующий PR2. Merge/main/deploy запрещены; Deploy257646584 disabled_manually. Previous published checkpoint и его evidence: [3aa handoff](https://github.com/DITEKmax/rutcampustrack/blob/3aa1e423415151b9e711a76c5d78806a3b073799/docs/validation/cloud-20261011/HANDOFF-FAILURES-20261021.md). Исторические причины не переписываются из новых PASS.

## Snapshot и custody

Main/merge-base3908fad9624f97aec8891fc79338ceb6b0616bf3. Исходный checkpoint617e1e334c87483fb27819f7e5b0a299f53374c9; предыдущий published HEAD3aa1e423415151b9e711a76c5d78806a3b073799/source0202665d351612f54e4056bd0f17550a1d2c5534. Новый executable source **f7cf996a8c1d1437c8a2d43d1df2a67d972050fb**. Final docs HEAD — отдельно; этот checkpoint допускает только CURRENT/HANDOFF, остальные tracked inputs byte-identical sourcef7.

Cloud environment_status running/connected и реальный exec подтверждены. Все23original617 ZIP248260B и9requested4b3 originals сохранены без перепаковки. Дополнительно13original3aa ZIP сверены с GitHub API artifact_id/run/attempt/head/size/digest, исходными SHA256/CRC/путями.12ZIP/20strictJSON aggregate plus отдельный D002 ZIP; class hashes сверены с текущими originals. PR/Coverage checkout carrier c9eff6bbd5f9aeabad6a2f2cb1cb822a27f1ee71 имеет exact tree3aa и parent3aa; carrier SHA не переименован branch SHA. Raw private XML/HTML/logs/messages, real credentials/PII и signed URLs не публикуются.

Latest3aa artifact IDs: pushNotification12324125847, pushoneoff12323546380, pushSchedulechanged12323372834, pushScheduleordinary12324045827, pushAttendance12323581597, pushMonitoring12323304101; CoverageNotification12324290750/CoverageAttendance12324091482; PRMonitoring12324032757/PRNotification12324156433/PRAttendance12324077349/PRScheduleordinary12323692024; PRremaining512324970446. Originals/evidence находятся в собственной `/workspace/work/pr2-failure-corrections-20261021/`, сохраняются при дальнейшей работе.

## Новые изменения:15 modified paths, no product changes

| Scope | Correction / сохраняемая гарантия | Достаточная проверка / независимость |
| --- | --- | --- |
| Attendance11 IT classes | Только import + class-local `@DirtiesContext(AFTER_CLASS)` для уникальных contexts со своим DynamicPropertySource. После класса graph закрывается; внутри класса reuse сохранён. | +22/-0, inverse11/11 и112method bodies/AST прежние; JDK parse-only, diff check. Независимый APPROVE_SOURCE. Test DB/vhosts/RedisDB/static containers, authz/CAS/outbox/HTTP assertions и сроки неизменны. |
| NotificationHistoryConsumerIT.groupFailureRollsBackClaimAndCommittedReplayKeepsReadState | В существующем10s wait обе initial DLQ==1 до смены shared audience mock; извлечение обеих initial messages. После второй неизменённой fanout publication тот же10s wait требует оба exact consumer/event durable claims до markRead/global replay snapshot. | Exact four-edit inverse возвращает whole3aa bytes;8other methods/helper byte+AST identical. Все прежние global rows/claims/AUTOACK/read-state assertions,4awaits/deadlines/retries/fresh fanout concurrency сохранены. Independent APPROVE_SOURCE. |
| check_exporters.py / check_pipeline.py + existing test_prepared_probe_projection.py | FAIL-only parent projection уже существующих child stderr/background output в два закрытых поля category/source_stage. Unknown/malformed/overflow/source mismatch не меняют primary FAIL. | Original bytes/AST после удаления только additions прежние;13source pins/consumer24truth cases;7cheap controls PASS. Independent APPROVE_SOURCE. Native child/probe/protocol и весь cleanup/status/oracle без изменений; старый local prepared R2 STOP не запускался. |

Attendance source finding:11 context graphs больше не нужны после своих классов, но удерживались Spring test cache. Это доказанная граница lifecycle, **не измеренный heap dominator** и не доказательство устранения actual OOM. Static private Mongo/Rabbit namespaces не удаляются; управляемые Spring executors/listeners/gRPC servers закрываются штатно. Другие shared/inherited contexts не затронуты. Ни heap/cache limits, ни forks/dependencies/production не менялись.

Notification source finding: прежняя test phase ждала только history DLQ перед снятием общей outage, затем только history claim перед snapshot **всех** claims. Delivery мог ещё retry или законно commit между snapshot/equality. Новые barriers завершают соответствующие фазы обоих consumers, не сериализуют fresh processing. Counterparts `authorityFailureRetriesBeforeClaimThenSealedIntentCommitsOnce` и `exhaustedAuthorityFailureRollsBackClaimAndRetainsOriginalEventInDlq` сохраняют transient retry/manual retained DLQ coverage. Actual изменившийся claim owner в старом FAIL не наблюдался; historical causal attribution UNKNOWN.

Monitoring projection читает максимум65537B и допускает только полный output<=65536B. Python terminal signatures→10known exception categories/9pinned source stages; Gradle→16exact public tasks/7static stages. Arbitrary raw messages/frames/tasks/request IDs/credentials не выводятся. Incomplete/ambiguous/unreadable/hash-mismatched input→UNKNOWN. Это **terminal signature**, не authenticated execution/guaranteed first cause: probe finally может скрыть более раннюю ошибку, gather(return_exceptions=True) может скрыть task failure. Source limits и первоначальный primary FAIL сохранены.

Полный source inventory (все15modified,0added/0deleted):

```text
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/CheckinIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OutboxAtomicityIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/LateCheckinPerformanceIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/LegacyLateDecisionPersistenceIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/EventIdempotentIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java
infra/prometheus/tests/check_exporters.py
infra/prometheus/tests/check_pipeline.py
tests/harness/test_prepared_probe_projection.py
services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java
```

Вся новая15path delta — tests/diagnostics, без added/deleted files, product/auth/API/schema/DB/proto/events/generated/dependencies/lockfiles/workflows/coverage thresholds/scanner rules/required gates. Новых skip/xfail/business retries/deadline increases нет. Exact source manifest и три независимых reports сохранены вне Git; root sole integrator, authors работали в назначенных worktrees. Requested Sol6.1/high; actual runtime metadata UNKNOWN.

## Native Notification: факты и пределы

Старые immutable attempts сохранены. На4b3 baseline FAILURE232; после Redis property source3ea FAILURE233, normal native completion во втором не достигнуто (STRUCTURED_COMMAND_FAILED). Они не превращены в PASS. Последний closed claim-witness на3aa выполнился42.0s: actual AssertionError REPLAY_ALL_CLAIMS_UNCHANGED helper584/caller249, claimComparison=null/NOT_ADMITTED; затем guardStop STRUCTURED_COMMAND_FAILED/STATS/MONITOR, exit143. Это другой assertion stage, не наблюдение numeric claim delta/handler cause. Все4own containers cleanup, cleanupErrors0, source clean.

На новом sourcef7 **один выбранный SOURCE_METHOD прошёл**:45.21s, естественный nativeexit0, ровно1SUCCESS/no exception/no frames/all overflowfalse, guardStopnull/cleanupErrors0, trackedSourceUnchanged/clean true. Реальные собственные Mongo/Rabbit/Redis/Ryuk; сохранены обе phase barriers/global replay equality/read-state/AUTOACK. OwnRSSpeak1.405GB, ownedcontainerpeak272.8MB, minimumworkingreserve12.77GB; public-key5032/unknown0, privateoutput205391B. ClaimComparison=null/NOT_ADMITTED на SUCCESS. Новый terminal-cohort exception в этом проходе **не понадобился** (tagnull); реальная причина старого STATS nonzero по-прежнему UNKNOWN. Strict normal PASS подтверждён, один метод не подменяет полный hosted suite. Frozen summary SHA256 `b98fc263d186cf788f8d72d313f9874db12e7bac3e6144d0267d793e54baaf6d`;32inputs/source заранее независимо SAFE_TO_RUN_ONE и проверены root, прежние попытки не повторялись.

Один heavy lease и прежние ресурсы: CPU2, ownRSS3GiB, wall600s, private128MiB, disk4GiB/sharedworkingreserve1GiB. Exact Testcontainers1.21.4/Ryuk0.12.0/image/session/ownership; Ryuk enabled/unprivileged. Supported additional network grant для local Docker/Gradle/loopback, без изменения daemon/security/network policy. Только собственный public-key503 stub, no provider. Новая source correction — причина отдельного verification, идентичного blind rerun нет.

## Published3aa: actual first boundaries

- pushCI39550573166 FAILURE: AttendanceIT иMonitoring; Notification64PASS, Schedule214PASS и точный oneoff expected1/observed0. Changed-domain control ожидаемо RED, следом ordinary214PASS; старый4b3push NO_JUNIT cause UNKNOWN.
- PRCI39550576692 FAILURE (21success/4failure, включая dependent aggregate): Attendance/Monitoring и D002 client failure. Notification64PASS, Schedule214PASS. Parent browser aggregate — следствие, не новая причина.
- Coverage39550576568 FAILURE: Attendance +Notification test step, **низкий процент не установлен**. Notification125unitPASS/63ITPASS1namedASSERTION; source method/hash current. Attendance407unitPASS; IT255PASS2opaqueORDINAL_FAIL1SKIP во всех3runs, GRADLE_SUITE/OOM JAVA_HEAP плюс ASSERTION; role/effectiveheap/retention cause UNKNOWN. Ordinals не сопоставляются с frozen/protected identities.
- Monitoring обеихCI: deliveryPASS; exporters PREPARED_TRANSACTION_NATIVE_CHILD_EXIT/AssertionError, pipeline ACTUAL_BACKGROUND_GRADLE/CalledProcessError1. probe_returncode0 относится к Mongo readiness, не child success. CleanupPASS; внутренний first cause ещё UNKNOWN.
- Security39550576610/OpenAPI39550576536 SUCCESS, built-image scan SKIPPED. Новый sourcef7/finaldocs HEAD ещё не имеют своих hosted outcomes. Source approval/cheap controls/native target не заменяют ordinary gates.

D002 PR artifact:9PASS/1FAIL,11773ms; refresh CALL pre-online, REJECTED к snapshot, page requestfailed/no response; current и post-online auth calls отсутствуют. Rejection/auth-promise settlement относительно online UNKNOWN. Независимый source read подтвердил: D002 ждёт automatic current200 без manual click; оба отдельных N2 cases purposefully late reject и manual current-only «Перезагрузить» PASS. Observer фиксирует epoch начала fetch, не состояние производного auth promise при online. Нужно различить reuse того же in-flight refresh→NETWORK/safe manual recovery от полностью settled chain до online→не начатый automatic bootstrap. Новый product/test defect не доказан; не добавлены blind POST refresh, quota/retry/status relaxations. Исторические D002/PUT500/activity429/transfer-at-deadline/olderAttendance/worker причины остаются отдельно UNKNOWN.

## Ограничения / следующий шаг

Сохранены F01–F09/N1/N2, ownership/live admission/late generation/uncertain command keys/authz/CAS/history/outbox/ACK/DLQ/privacy. Browser/Python/Academic успешные области отдельно локально не повторяются без новой причины; новый D002 counter-failure разобран только по safe artifact/source. B013–B015/rejected Mongo/Chromium equivalents и прежний local prepared R2 STOP не возобновлялись. No production/provider/Telegram/VPS/SSH/migration/grants/secrets/firewall/backup.

Cleanup0: измеренный cloud working reserve около14.7GB, диск12.6GB; hosted JAVA_HEAP не доказывает нехватку RAM cloud host. Git/worktrees/WIP/foreign paths/исходныеZIP/diagnostics/БД/volumes/data/backups сохранены. Далее: factual taskdocs/scoped full-history scanner/expected head+Deploy readback/один normal push в существующую PRbranch; чтение новых ordinary attempt1 outcomes и cause-directed corrections. Ни старый APPROVE, ни completed audit, ни исчезновение старого имени не означают готовность PR; merge/deploy не выполняются.
