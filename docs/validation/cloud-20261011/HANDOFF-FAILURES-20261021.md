# PR2 — OpenAPI oracle, source bindings и закрытая диагностика

**SOURCE_REVIEWED / NEW_HOSTED_RESULTS_PENDING / NOT_READY.** Разрешены corrections и normal push в существующий PR2; merge/main/deploy запрещены. Deploy257646584 disabled_manually. [Предыдущий snapshot](https://github.com/DITEKmax/rutcampustrack/blob/84baee2d2553ac608a0cd64bec985d37bd52d301/docs/validation/cloud-20261011/HANDOFF-FAILURES-20261021.md) сохраняет прежние corrections, native receipts и historical limits. Audit94DELETE/169REWRITE не возобновляется.

## Snapshot и изменения

Main/merge-base3908fad9624f97aec8891fc79338ceb6b0616bf3. Previous published84baee2d2553ac608a0cd64bec985d37bd52d301/sourcef7cf996a8c1d1437c8a2d43d1df2a67d972050fb. Новый source **bfd747577af1dc29780bb05111d8b1bd82726adc** состоит из трёх scoped commits: a5b58531 (CI bindings),360917d4 (Monitoring diagnostics),bfd74757 (OpenAPI semantic comparison). Final taskdoc HEAD — отдельно; source→final checkpoint только CURRENT/HANDOFF, остальные tracked inputs byte-identical.

Пять modified paths,0added/0deleted:

```text
.github/workflows/ci.yml
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OpenApiSnapshotIT.java
infra/prometheus/tests/check_exporters.py
infra/prometheus/tests/check_pipeline.py
tests/harness/test_prepared_probe_projection.py
```

Product/services/frontend behavior/API/schema/DB/proto/event/generated/dependencies/lockfiles/committed docs/openapi snapshot, coverage thresholds/scanners/required gates unchanged. Нет новых skip/xfail, business retries, deadline increases или broad exclusions. Root sole Git/docs integrator; авторы работают в отдельных assigned worktrees; существенные corrections reviewed свежими независимыми Sol6.1/high. Actual runtime metadata UNKNOWN.

## Доказанные corrections и пределы

| Scope | Изменение и сохранённая гарантия | Достаточная проверка |
| --- | --- | --- |
| CI EventConsumerIT source bindings | Три SHA и шесть source/frame coordinates синхронизированы с добавленными ранее import/AFTER_CLASS. Старые pins на84 доказанно останавливали control в SOURCE_ADMISSION до mutation/JUnit. Это собственный integration omission, отдельно от ordinary IT assertion. | Exact9 replacements/inverse, current source lines/hash, single strict comparison frame780 вместо778, counterexample/restoration/three restored-green guards сохранены. YAML/34embedded Python AST и source-admission static controls PASS; independent APPROVE_SOURCE. Native control на новом source здесь не запускался. |
| Attendance OpenApiSnapshotIT + two CI literals | Сравниваются parsed JSON trees вместо rendering strings: object member order эквивалентен, все fields/null-vs-missing/types/values и array length/order обязательны. Duplicate keys и trailing/malformed JSON отклоняются до normalization и при comparison; comparison parser использует BigDecimal. Единственное старое servers exception, fetch/update/writeformat остаются. | Source/classfile подтверждают, что ORDER_MAP_ENTRIES_BY_KEYS не сортирует ObjectNode.27 bounded cached-Jackson predicates PASS: old false RED/new PASS для reorder и сохранённые negative cases, включая precision/type/array differences и malformed input. Java21 parse-only/YAML/34embedded Python/whole-file inverse PASS; independent APPROVE_SOURCE. Snapshot unchanged. Actual84 named failure cause и official-candidate phase UNKNOWN. |
| Monitoring3 paths | Девять exact DURABLE invariant stages вместо одной coarse category. Background Gradle nonzero дополнительно получает closed category/source_stage из одного fixed отчёта заранее известного selected RabbitDecisionRetryIT method; existing failed-task diagnostic сохранён. |11pure controls PASS, inverse возвращает84 AST без diagnostic additions,24consumer truth cases; independent APPROVE_SOURCE v2. App/probe/Gradle/native/broker не запускались. Known test selection не менялся; S1/ordinal mapping отсутствует. |

OpenAPI source correction сохраняет прежний raw-fetch floating representation; дополнительная loss of precision не вносится в comparison operands. Это не обещание устранить все старые normalization limitations. CI меняет только два Attendance official-producer literals сверх девяти source-binding replacements; source/head/run/job/status/method/hash/single comparison-frame/wrapper/update/schema/changed-path guards не ослаблены. Пять других OpenAPI producers untouched. No snapshot generation/update запусков.

Monitoring reader: fixed known classname/method/path/source hash; один nofollow/nonblocking regular descriptor, максимум1MiB+1 read и<=1MiB admission; current-call mtime+ctime и before/after/path stability. Pinned public source<=64KiB. Exact Gradle8.12 XML: empty properties, testcase, optional ordered suite stdout/stderr; unknown fields/nesting/DTD/entities/type mismatches rejected. Missing/stale→NO_CURRENT_RESULT, malformed/unsafe/ambiguous→UNKNOWN. Только заранее закрытые enums выходят наружу; raw messages/stacks/XML/hostname/request IDs не выводятся. Это terminal signature/filesystem qualification, не authenticated provenance или гарантированная first cause. Trusted stable source checkout остаётся предпосылкой. Primary CalledProcessError/FAIL/cleanup/CLI/deadlines/oracles прежние.

Root source review обнаружил, что v1 parser отвергал обязательный empty properties реального pinned producer. Cached public Gradle8.12 writer bytecode это подтвердил. V1 evidence сохранено; v2 принимает только exact producer shape и усиливает negative controls. Это source-review finding исправлено до интеграции, не объяснение actual84 Monitoring. Native child/probe source и прежний local prepared R2 STOP не выполнялись заново.

## Published84: actual outcomes

Все ordinary attempt1 completed, существующие jobs не отменялись/не перезапускались:

| Gate | Run | Actual outcome |
| --- | --- | --- |
| Push CI |39558793498|FAILURE,21success/4failure: Attendance, Monitoring, ADMIN activity client, dependent aggregate |
| PR CI |39558804331|FAILURE,23success/2failure: Attendance/Monitoring |
| Coverage |39558804442|FAILURE,8success/1failure/1skip: Attendance test step; низкий coverage не установлен |
| Security |39558804289|SUCCESS; built-image scan SKIPPED |
| OpenAPI |39558804538|SUCCESS |

Notification: push+PR64IT PASS each; Coverage125unit+64IT PASS. Corrected groupFailureRollsBackClaimAndCommittedReplayKeepsReadState — SOURCE_METHOD/currentSHA PASS во всех relevant receipts. Прежний localf7 one-method PASS/45.21s и independent receipt сохранены в84 handoff; это не восстановленная historical root cause.

Attendance:407unit PASS,255IT PASS/2FAILED/0skip в push/PR/Coverage. Один explicit OpenApiSnapshotIT.apiDocsMatchesSnapshot, один opaque ORDINAL_ONLY; opaque identities не читаются/не сопоставляются. Closed ASSERTION известен для opaque Coverage case; named failure сам по себе не доказывает comparison frame или JSON delta. JAVA_HEAP на84 не наблюдался; это не доказательство historical OOM fix. Official candidate artifact отсутствует, exact step26 phase UNKNOWN; committed snapshot не переписывается. CI negative-control SOURCE_ADMISSION cause доказан отдельно и исправлен source bindings.

Monitoring в обоихCI: deliveryPASS; exporters PREPARED_TRANSACTION_NATIVE_CHILD_EXIT/AssertionError с DURABLE_OUTCOME_CHECK; pipeline ACTUAL_BACKGROUND_GRADLE/CalledProcessError1, native FAILED_TASK/ATTENDANCE_INTEGRATION_TEST_TASK. Exact selected background method уже известен из unchanged CLI, но assertion predicate/first cause неизвестны. CleanupPASS. probe_returncode0 относится к Mongo readiness, не к child success. Новые projections только помогут различить следующую реальную ordinary failure, не заменяют её PASS.

Schedule ordinary push+PR214PASS и ожидаемый negative-control outcome подтверждены safe receipts; старый4b3 NO_JUNIT cause UNKNOWN. PR client и оба lifecycle groups SUCCESS. Push role/logout group6PASS/1FAIL: cloud-accepted-admin-grants.spec.ts declared112/error312,8224ms, вызов activity oracle после period switch до rights edit; diagnostic UNKNOWN/httpStatus0 не устанавливает actual HTTP outcome. Safe artifact12327857193 digest/size/API/run/head проверен. Source-only review нашёл условную correlation gap first completed response vs последующий current realtime refresh, но actual supersession/predicate/period/HTTP не наблюдались. Source render/formatter согласован с oracle. Никаких quota/retry/deadline/product edits или причинного вывода из нового PASS.

## Custody, проверки и следующий шаг

Cloud environment_status running/connected + реальный exec. Измерено MemAvailable около17.9GB, workspace disk12.6GB, swap0; cleanup не нужен. Hosted JAVA_HEAP не означает нехватку cloud RAM. Все23original617 ZIP248260B,9requested4b3,13original3aa и10original84 ZIP сохранены без перепаковки; run/attempt/head/artifact_id/size/SHA/CRC/path проверены. Original84 IDs:12326672492,12327516050,12327396362,12326876507,12326942802,12327527351,12326643327,12327087809,12327546851,12327857193. PR/Coverage carrierf97c056d7b6c543aa04607dfde9403f881a6f4fa имеет tree6ffcee25d75111f467f00d24bd32374263f5606e и parents main+84; exact tree84, carrier не переименован branch SHA.

Новые27Jackson controls и11pure projection controls — executed bounded synthetic evidence, не runtime conformance приложения. Первая неисправность actual84 и opaque assertion не переименовываются в environment/flaky. Current source ещё не имеет своих hosted gates; taskdocs-only equivalence не превращает старый FAIL в новый PASS. Exact source manifest/reviews/receipts находятся в собственной /workspace/work/pr2-failure-corrections-20261021; ignored local evidence не подменяет опубликованные source и обычный CI.

Сохранены Git/index/WIP/worktrees/foreign paths/data/ZIP/evidence и все F01–F09/N1/N2/session/live admission/late generation/uncertain keys/authz/CAS/history/outbox/ACK/DLQ/privacy contracts. Frozen B013–B015/rejected Mongo/Chromium equivalents/local prepared R2 STOP не повторяются. No raw private reports/logs/real credentials/PII/signed URLs; no production/VPS/SSH/provider/Telegram/migration/secrets/firewall/backup.

Далее: factual taskdoc review, unchanged-policy full-history scanner, expected HEAD/base/Deploy readback и один normal push после завершения84 runs; читать новые ordinary attempt1 metadata/safe artifacts, исправлять только доказанные причины. Blind rerun/cancel и merge/deploy запрещены. Source approval/completed audit не означают готовность PR.
