# PR2 — remaining failure corrections after cloud recovery, 2026-10-21

**SOURCE_REVIEWED / NEW_HOSTED_RESULTS_PENDING / NOT_READY.** Текущее поручение разрешает исправления и публикацию в PR2, запрещает merge/main/deploy. Deploy257646584 disabled_manually. История и подробности прежних corrections сохранены в Git на4b377fdf: [предыдущий handoff](https://github.com/DITEKmax/rutcampustrack/blob/4b377fdf8c4dfdfae2f91b536edba9855bb7b9f9/docs/validation/cloud-20261011/HANDOFF-FAILURES-20261021.md). Новые FAIL не скрываются старым APPROVE.

## Revision и custody

Main/merge-base3908fad9624f97aec8891fc79338ceb6b0616bf3. Исходный checkpoint617e1e334c87483fb27819f7e5b0a299f53374c9. Последний published HEAD4b377fdf8c4dfdfae2f91b536edba9855bb7b9f9; tested executable7bd0a5276545dc9a26e7aabbe45da24e928ccad0. Новый source **0202665d351612f54e4056bd0f17550a1d2c5534**, commits3ea446fb +0202665d; final taskdoc HEAD отдельный. Root sole Git/docs integrator, назначенные sole writers в независимых сохранённых worktrees, независимые read-only reviewers. Requested Sol6.1/high; фактические model/effort metadata UNKNOWN.

Cloud environment_status running/connected и реальный pwd/exec подтверждены; queued thread не считался выполнением. HTTP policy enforced, штатный additional-network grant для Docker/Gradle/loopback, без изменения daemon/privileges/network policy. Сохранены все23original ZIP248260B/40strictJSON и9current originals.9/9 current ZIP сверены с GitHub artifact_id/run/attempt/head, размером и SHA256:5точно переиспользованы,4скачаны доexpiry. Никаких подписанных URL, private raw reports/logs/XML/HTML, реальных credentials/PII в Git или safe summaries.

Current exact artifacts: pushAttendance12316043046, pushNotification12316116764, PRNotification12315663411, pushSchedule ordinary12315547459, PRSchedule changed-domain12316135815, CoverageAttendance12315708558, PRAttendance12315958602, pushMonitoring12315843382, PRMonitoring12316576459. Все относятся к4b3/attempt1. Отдельный push oneoff12316106579 существует; changed-domain Schedule у push отсутствует. PR changed-domain controls не заменяют push oneoff proof.

## Минимальная новая delta

| Input | Изменение и сохранённая гарантия | Verification / независимость |
| --- | --- | --- |
| NotificationHistoryConsumerIT.java | Только class-local `spring.data.redis.password=`; ContainerTestBase поднимает настоящий Redis без auth, application default иначе непустой. Реальный Redis сохранён, production config неизменён. | Entire file минус1строка byte-identical; все9methods/target/helper bodies byte+AST identical. Независимый APPROVE_SOURCE. Native outcome ниже; исправление не названо причиной green. |
| collect-ci-test-outcomes.py + test_ci_test_outcomes.py | Optional closed ATTENDANCE_OOM_RESOURCE_V1/family при прежнем точном java-coverage/attendance/integrationTest/ORDINAL_ONLY null-identity OOM admission. Старые fields/count/status/source/schema/NOT_ASSESSED и15codes неизменны. | 60cheap harness cases PASS после correction только неверного нового CR/LF vector; первоначальный1FAIL сохранён. Exact native OOM types/anchored headers, bounds/ambiguity/privacy/negative gates проверены. Независимый APPROVE_SOURCE; семейство ещё UNKNOWN в actual hosted evidence. |
| check_pipeline.py |3static phases разделяют Tempo port lookup, вызов Gradle и прежнюю post-return receipt boundary. | File byte/AST identity minus3;18synthetic consumer cases. Независимый APPROVE_SOURCE; существующие commands/oracles/timeouts/cleanup неизменны. |
| check_exporters.py |7static parent phases: topology, child run, child exit, receipt, row contract, no-primary cleanup, прежняя coarse success phase. | File/function byte+AST identity minus7;42synthetic consumer cases; independent APPROVE_SOURCE. Child/probe/protocol не менялись и локально не запускались. Primary failure сохраняет phase при cleanup failure; probe_returncode не переиспользован под child exit. |
| .github/workflows/ci.yml | Только existing oneoff diagnostics heredoc: bounded64KiB window, static task/category/report-presence enums. |18synthetic controls; YAML/AST/diff checks, независимый APPROVE_SOURCE. Строгий exactly-one named assertion expected1/observed0 negative oracle/restore trap/pins/gates неизменны. NO_JUNIT не PASS. Старый fallback cause/frame search сужен1MiB→64KiB; диагностическая полнота меньше, admission/allowlists не расширены. Tail — VISIBLE_WINDOW, не гарантированная первая ошибка полного лога. |

Ни product поведения/API/auth, ни migration/proto/dependencies/lockfiles, coverage thresholds/scanrules/required gates не менялись в новой delta. Новых skip/xfail/blanket exceptions/retries нет. No added/deleted source paths.

## Notification: finite native evidence

Существующий selector `NotificationHistoryConsumerIT.groupFailureRollsBackClaimAndCommittedReplayKeepsReadState`, настоящие собственные Mongo/Rabbit/Redis/Ryuk, один heavy lease. External helper после independent review даёт только bounded known exception classes и actual source ranges/stages; не читает raw report/log/message. CPU2, ownRSS3GiB, wall600s, private128MiB, disk4GiB, shared working-set reserve1GiB, exact session/image/ownership cleanup. Ryuk включён/unprivileged. Исходники clean и byte-checked до/после.

Первый helper прекратился из-за предположения TC1.20.4/Ryuk0.11; это NOT_PRODUCT_FAILURE. Configuration-only inspection реального testRuntimeClasspath выявил7modules TC1.21.4 (Boot3.5.16 BOM) и Ryuk0.12.0; selected classpath повторно проверяется доJUnit. Это correction внешней диагностической обвязки, не изменение dependency/продукта. Initial sandbox daemon socket EPERM устранён штатным additional-network grant, не обходом или изменением daemon.

На4b3 actual native target FAILURE/AssertionFailedError, source232 SUCCESS_HISTORY_CLAIM;55.25s/exit1/no guard stop/cleanupErrors0. После единственной Redis fixture correction source3ea exact target снова **FAILURE/AssertionFailedError, source233 SUCCESS_HISTORY_CLAIM**. Run55.64s/exit143/guard STRUCTURED_COMMAND_FAILED, cleanupErrors0,4expected owned containers removed; normal native process completion не заявляется. Admitted assertion не выводит numeric expected/actual из raw message. OwnRSSpeak1.399GB, owned container memory266.2MB, minimum working reserve12.87GB; resource bound не установлен. Stub только2public-key503, unknown/provider calls0. Frozen single-use claims/previous attempts сохранены; идентичного повтора нет.

Redis fixture contradiction доказана исходниками и исправлена, но native failure сохранился: causal closure не заявлена. Delivery/history общий Mongo write path и legacy Redis preference read — достижимые связи, не доказанный driver/transport cause. Hosted assertion stage не приравнен автоматически локальному source233. Дальше нужен cause-directed source/finite closed listener witness; слепой retry/увеличение deadline запрещены.

## Последние published gates:4b3, attempt1/completed

- pushCI39531860626 FAILURE: AttendanceIT, Monitoring, Notification1assertion, Schedule oneoff.
- PRCI39531865077 FAILURE: AttendanceIT иMonitoring. Notification64ITPASS; Schedule ordinary иbrowser groups SUCCESS.
- Coverage39531865112 FAILURE толькоAttendance. Unit407PASS; ordinary IT255PASS2ORDINAL_FAIL1SKIP в обоихCI иCoverage. GRADLE_SUITE/OOM иASSERTION; subtype/JVM role/effectiveheap/firststage UNKNOWN.
- Security39531865151 SUCCESS: filesystem/config/Gitleaks passed; built-image scan SKIPPED. OpenAPI39531865108 SUCCESS.
- Browser push+PR/client+lifecycle иPython SUCCESS. Academic full current source receipts:584unitPASS,540ITPASS/1отдельныйexpectedCLI SKIP вCoverage; current source hashes подтверждены. Отдельный локальный повтор этих green областей не выполнялся.

Новые020 bytes и будущий docs HEAD пока не имеют собственных ordinary hosted результатов. Ни source review, ни синтетические controls не заменяют их.

Remaining Monitoring: deliveryPASS; exporters coarse PREPARED_TRANSACTION_NATIVE_BOUNDARIES/AssertionError; pipeline ACTUAL_BACKGROUND_TRANSPORT/CalledProcessError1. Уточнённые passive phases предназначены для следующего штатного gate, не называют cause и не ослабляют FAIL. Local prepared R2 START_FAILURE_OTHER ранее остановлен до child и остаётся STOP, здесь не повторялся. Shared MongoPUT500/B013–15/Chromium equivalents FROZEN.

Schedule: push ordinary214ITPASS, PRwholeIT/Coverage SUCCESS. Push oneoff NO_JUNIT/UNEXPECTED_ONEOFF_RED exit1; followup changed-domain producerSKIP и missing uploadFAIL — следствия, не отдельный product дефект. Нужен native compile/dependency/startup/worker boundary; новые finite enums не делают arbitrary exit1 ожидаемым regression proof.

Исторические D002/SETUPPUT500/activity429/transfer-at-deadline/worker/olderAttendance причины UNKNOWN не объявлены исправленными из новых green. Audit94DELETE/169REWRITE остаётся historical source implementation, не готовностью продукта. Старые F01–F09/N1/N2/ownership/live admission/late generation/uncertain command keys/CAS/outbox/ACK/DLQ/privacy сохранены.

## Inventory / следующий шаг

От4b3:6modified paths — `.github/workflows/ci.yml`, `infra/prometheus/tests/check_pipeline.py`, `infra/prometheus/tests/check_exporters.py`, `scripts/collect-ci-test-outcomes.py`, `tests/harness/test_ci_test_outcomes.py`, `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java`. Taskdoc checkpoint меняет только CURRENT иэтотHANDOFF; остальные tracked inputs byte-identical020source.

Exact source manifests, independent reviews,9artifact API custody и native safe summaries сохранены в собственном `/workspace/work/pr2-failure-corrections-20261021/`, originals не перепакованы/удалены. Измеренной необходимости очистки нет; чужие WIP/worktrees/processes/БД/volumes/backups не тронуты. Дальше: unchanged pinned full-history Gitleaks policy, expected remote/head/Deploy readback, один normalpush в существующую PRbranch и анализ новых safe receipts. Не rerun/cancel CI, не merge/main/deploy/VPS/SSH/provider/Telegram/production migration/grants/secrets/firewall/backup.
